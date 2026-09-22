package com.my.amali.data.ai

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import com.my.amali.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Распознавание речи: микрофон → громкость определяет конец фразы → Whisper.
 *
 * ════════════════════════════════════════════════════════════════════════
 *  ВСЯ ЛОГИКА В ТРЁХ ПРАВИЛАХ
 * ════════════════════════════════════════════════════════════════════════
 *
 * ```
 *  1. После нажатия микрофона пишем ВСЁ, не разбирая, речь это или нет.
 *  2. Первую секунду конец фразы не проверяем — человек только собирается.
 *  3. Дальше: если стало тихо на 600 мс — фраза кончилась, отправляем.
 * ```
 *
 * Никаких нейросетей, чанков, промежуточных гипотез и дообрезки. Громкость
 * считается по кадру в 32 мс, тишина — по часам. Всё остальное берёт на
 * себя Whisper: он и так делает распознавание лучше любого «умного»
 * препроцессинга, была бы запись целиком.
 *
 * ════════════════════════════════════════════════════════════════════════
 *  ПОЧЕМУ ЭТО НАДЁЖНЕЕ НЕЙРОСЕТЕВОГО ДЕТЕКТОРА
 * ════════════════════════════════════════════════════════════════════════
 *
 * Предыдущая версия несла в себе Silero VAD: модель на 2 МБ внутри APK,
 * рантайм ONNX, отдельная нативная библиотека и правила R8, чтобы её не
 * вырезали. Модель различает речь и шум тоньше — и ровно на этом
 * проваливается в тех случаях, ради которых её ставили:
 *
 *  — **шёпот** обученная модель считает шумом: он не похож на «среднюю
 *    речь». Человек шепчет — и ассистент молчит;
 *  — **нестандартная интонация** (медленно, растягивая, с придыханием)
 *    не проходит порог уверенности;
 *  — **разная громкость записи** на разных телефонах: модель обучена на
 *    нормализованном звуке, а микрофон конкретной модели может писать
 *    заметно тише.
 *
 * Громкость всего этого не знает и знать не хочет. Она сравнивает кадр с
 * тем, что было в этой же комнате секунду назад, поэтому одинаково честно
 * работает и с шёпотом, и с криком, и в тишине, и в кафе.
 *
 * ════════════════════════════════════════════════════════════════════════
 *  ЧТО ВИДИТ ЧЕЛОВЕК
 * ════════════════════════════════════════════════════════════════════════
 *
 * Уровень микрофона уходит в UI каждый кадр, поэтому волна на экране дышит
 * всё время записи — и когда человек говорит, и когда молчит. Текст
 * появляется один раз, целиком, когда фраза распознана: промежуточных
 * гипотез здесь нет, и это осознанно — они стоили дополнительного запроса
 * к Whisper каждые несколько секунд, а человек всё равно читает только
 * итог.
 */
class GroqWhisperStt(private val context: Context) : SpeechToTextEngine {

    private val client = GroqSttClient()

    /** Прогрев делать нечего: соединение открывается самим запросом. */
    override suspend fun preconnect() = Unit

    override suspend fun initialize() = Unit

    override suspend fun close() {
        client.shutdown()
    }

    override fun transcribe(options: EngineOptions): Flow<SttEvent> = callbackFlow {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            throw EngineException(ERROR_NO_PERMISSION)
        }

        val apiKey = resolveKey(options)
        val mic: AudioRecord = openRecorder()
        val model = ModelCatalog.resolveForRequest(
            ModelCatalog.Provider.GROQ_STT,
            options.api.sttModel,
        )
        val language = options.languageCode.trim()
        val prompt = recognitionPrompt(options)
        // Пауза, означающая конец фразы: приходит из настроек, чтобы человек
        // мог подстроить её под свою манеру речи, не пересобирая приложение.
        val silenceMs = (options.api.sttSilenceSeconds * 1000f)
            .toLong()
            .coerceIn(MIN_SILENCE_MS, MAX_SILENCE_MS)

        val job = launch(Dispatchers.IO) {
            val gate: SpeechGate = SpeechGate()
            // Именно `take`, а не `recorder`: рядом живёт `mic` — системный
            // AudioRecord, и два «рекордера» в одном цикле читались бы как
            // ошибка. Здесь — наша запись фразы, там — железо.
            val take: VoiceRecorder = VoiceRecorder(silenceMs = silenceMs)

            try {
                mic.startRecording()
                if (mic.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                    close(EngineException(ERROR_MIC_BUSY))
                    return@launch
                }

                AmaliaLog.i(
                    AmaliaLog.tagWith("STT"),
                    "listening | frame=${VoiceAudio.FRAME_MS}ms guard=${VoiceRecorder.START_GUARD_MS}ms silence=${VoiceRecorder.END_SILENCE_MS}ms",
                )

                val frame = ShortArray(VoiceAudio.FRAME_SAMPLES)
                while (isActive) {
                    val read = mic.read(frame, 0, frame.size)
                    if (read <= 0) continue
                    val chunk = if (read == frame.size) frame else frame.copyOf(read)

                    val level = VoiceAudio.level(chunk)
                    // Уровень отправляем всегда — но волна получает
                    // отрисовочную шкалу ([displayLevel]), а не сырой RMS:
                    // делитель «громкая речь вплотную» оставлял полосы
                    // почти неподвижными при обычном разговоре с расстояния.
                    // Решение о речи ([SpeechGate]) принимает исходный
                    // уровень — усиление касается только картинки.
                    trySend(SttEvent.Level(VoiceAudio.displayLevel(chunk)))

                    // Решение о речи принимается по уровню и измеренному
                    // порогу: одна строка, один источник правды.
                    val speech = gate.isSpeech(chunk, level)

                    val done = take.accept(chunk, speech, VoiceAudio.FRAME_MS)
                    if (done) {
                        AmaliaLog.i(
                            AmaliaLog.tagWith("STT"),
                            "end of phrase | speech=${take.hasSpeech} samples=${take.length} " +
                                "duration=${take.durationMs}ms silentFor=${take.silentForMs}ms",
                        )
                        break
                    }
                }
            } catch (e: SecurityException) {
                close(EngineException(ERROR_PERMISSION_REVOKED, e))
                return@launch
            } catch (e: IllegalStateException) {
                close(EngineException(ERROR_MIC_BUSY, e))
                return@launch
            } catch (e: Throwable) {
                close(EngineException(e.message ?: ERROR_MIC_UNAVAILABLE, e))
                return@launch
            } finally {
                runCatching {
                    if (mic.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                        mic.stop()
                    }
                }
                runCatching { mic.release() }
            }

            if (!take.hasSpeech || take.length == 0) {
                // Ничего не расслышали: закрываем поток без текста, и
                // оркестратор скажет «не услышала ни слова».
                AmaliaLog.w(AmaliaLog.tagWith("STT"), "no speech detected")
                channel.close()
                return@launch
            }

            val seconds = take.length.toFloat() / VoiceAudio.SAMPLE_RATE
            AmaliaLog.i(
                AmaliaLog.tagWith("STT"),
                "sending to whisper | ${"%.2f".format(seconds)}s | model=$model",
            )

            val text = runCatching {
                client.transcribe(
                    wav = take.toWav(),
                    languageCode = language,
                    model = model,
                    apiKey = apiKey,
                    prompt = prompt,
                )
            }.getOrElse { error ->
                AmaliaLog.e(AmaliaLog.tagWith("STT"), "whisper failed: ${error.message}", error)
                close(EngineException(error.message ?: ERROR_RECOGNITION, error))
                return@launch
            }

            AmaliaLog.i(
                AmaliaLog.tagWith("STT"),
                "recognized | \"${text.take(80)}\" | ${text.length} chars",
            )
            if (text.isNotBlank()) {
                trySend(SttEvent.Final(text))
            }
            channel.close()
        }

        awaitClose {
            job.cancel()
            runCatching { mic.stop() }
            runCatching { mic.release() }
        }
    }

    /**
     * Ключ: свой из настроек, иначе зашитый в сборку.
     *
     * Слух и мозг работают на одном ключе Groq — распознавание идёт в том же
     * аккаунте, поэтому отдельного поля в настройках не существует.
     */
    private fun resolveKey(options: EngineOptions): String {
        val user = options.api.groqKey.trim()
        val key = user.ifBlank { BuildConfig.GROQ_API_KEY }
        if (key.isBlank()) throw EngineException(ERROR_NO_KEY)
        return key
    }

    /**
     * Открывает микрофон под распознавание речи.
     *
     * `VOICE_RECOGNITION` вместо `MIC`: он отключает агрессивную обработку
     * (эхоподавление, автоматическую регулировку усиления), которая помогает
     * в звонках, но «сглаживает» тихие слова. Для распознавания нужен
     * честный звук, даже если он тихий.
     *
     * Если источник недоступен (редкие устройства), пробуем `MIC` — лучше
     * записать с обработкой, чем не записать вовсе.
     */
    private fun openRecorder(): AudioRecord {
        val minBuffer = AudioRecord.getMinBufferSize(
            VoiceAudio.SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        if (minBuffer <= 0) throw EngineException(ERROR_MIC_UNAVAILABLE)

        val size = minBuffer * BUFFER_MULTIPLIER
        val sources = intArrayOf(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            MediaRecorder.AudioSource.MIC,
        )
        for (source in sources) {
            val candidate = runCatching {
                @Suppress("DEPRECATION")
                AudioRecord(
                    source,
                    VoiceAudio.SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    size,
                )
            }.getOrNull() ?: continue
            if (candidate.state == AudioRecord.STATE_INITIALIZED) return candidate
            candidate.release()
        }
        throw EngineException(ERROR_MIC_UNAVAILABLE)
    }

    /**
     * Подсказка для Whisper — короткий словарь ожидаемых слов.
     *
     * Модель не знает предметной области и на слух пишет «вай фай» или
     * «ютуп». Список слов смещает вероятности при выборе токенов и снимает
     * почти все такие ошибки. Это не инструкция, а словарь: он не мешает
     * распознать фразу, которой в списке нет.
     */
    private fun recognitionPrompt(options: EngineOptions): String {
        val builder = StringBuilder(BASE_PROMPT)
        options.knownApps.take(APPS_IN_PROMPT).forEach { app ->
            builder.append(", ").append(app.label)
            app.aliases.take(APPS_IN_PROMPT).forEach { alias ->
                builder.append(", ").append(alias)
            }
        }
        options.appAliases.keys.take(APPS_IN_PROMPT).forEach { alias ->
            builder.append(", ").append(alias)
        }
        return builder.toString()
    }

    private companion object {
        /** Запас над минимальным буфером AudioRecord — против щелчков. */
        const val BUFFER_MULTIPLIER = 4

        /** Перестраховка на случай, если настройка пришла битой. */
        const val MIN_SILENCE_MS = 300L
        const val MAX_SILENCE_MS = 2_000L

        /** Сколько названий приложений и синонимов вмещать в подсказку. */
        const val APPS_IN_PROMPT = 8

        /**
         * Базовый словарь. Собран из того, что Амалия реально умеет делать, —
         * чтобы «включи блютуз» распознавалось одинаково у всех.
         */
        const val BASE_PROMPT =
            "Амалия, вайфай, блютуз, яркость, громкость, фонарик, будильник, " +
                "таймер, погода, ютуб, телеграм, вкл, выкл"

        const val ERROR_NO_PERMISSION =
            "Нужен доступ к микрофону, чтобы я могла тебя слышать."
        const val ERROR_PERMISSION_REVOKED = "Доступ к микрофону отозван системой."
        const val ERROR_NO_KEY =
            "Нет ключа Groq — распознавание речи недоступно. Впиши ключ в настройках «API и модели»."
        const val ERROR_MIC_UNAVAILABLE = "Микрофон недоступен на этом устройстве."
        const val ERROR_MIC_BUSY = "Микрофон занят другим приложением."
        const val ERROR_RECOGNITION = "Не удалось распознать речь."
    }
}
