package com.my.amali.data.ai

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Распознавание речи на Groq Whisper с локальным детектором голоса.
 *
 * ════════════════════════════════════════════════════════════════════════
 *  ЧЕМ ЭТО ОТЛИЧАЕТСЯ ОТ ПРЕДЫДУЩЕГО ДВИЖКА
 * ════════════════════════════════════════════════════════════════════════
 *
 * Раньше распознавание было потоковым: веб-сокет, аудио уходило по 100 мс,
 * текст возвращался в реальном времени, а конец фразы определял сервер.
 * Схема давала живые субтитры, но упиралась в три вещи, которые человек
 * чувствует каждый раз:
 *
 *  1. **Холодный старт.** Пока сокет открывается (TCP, TLS, рукопожатие),
 *     микрофон уже пишет — и первые слова улетают в ещё не открытое
 *     соединение. Отсюда «задержка после нажатия» и «связь».
 *  2. **Цена обрыва.** Пропала сеть на середине фразы — потеряна вся фраза:
 *     сервер не получил остаток, финальный текст не пришёл.
 *  3. **Отдельный аккаунт.** Свой ключ, свой счёт, свой лимит — ещё одна
 *     сущность, которую пользователю нужно завести и оплатить.
 *
 * Здесь всё иначе: **короткие обычные HTTP-запросы**, а конец фразы
 * определяет детектор голоса на устройстве. Ничего не нужно ждать заранее,
 * ничего не теряется при обрыве, и всё живёт на том же ключе Groq, что и мозг.
 *
 * ════════════════════════════════════════════════════════════════════════
 *  КАК УСТРОЕН ЦИКЛ
 * ════════════════════════════════════════════════════════════════════════
 *
 * ```
 * AudioRecord 16 кГц mono
 *        │  кадры по 32 мс
 *        ▼
 *   Silero VAD ── речь? ── нет ──▶ копим тишину
 *        │ да                         │
 *        │                            └─ 600 мс тишины после речи → ФИНАЛ
 *        ▼
 *   пишем в буфер речевого сегмента
 *        │
 *        ├─ каждые N секунд (настройка) ─▶ turbo Whisper ─▶ Partial (субтитры)
 *        │
 *        └─ тишина 600 мс ─▶ Whisper ─▶ Final ─▶ конец потока
 * ```
 *
 * За одну длинную фразу уходит три запроса: два промежуточных ради субтитров
 * и один финальный ради точности. Это в разы меньше, чем отправлять всю фразу
 * целиком снова и снова, и с большим запасом укладывается в бесплатный лимит
 * 7200 секунд аудио в сутки.
 *
 * ════════════════════════════════════════════════════════════════════════
 *  ПОЧЕМУ ФИНАЛ ОТПРАВЛЯЕТСЯ ОТДЕЛЬНО, А НЕ БЕРЁТСЯ ИЗ ПОСЛЕДНЕГО ЧАНКА
 * ════════════════════════════════════════════════════════════════════════
 *
 * Промежуточный чанк — обрезанный кусок: он начинается с середины фразы и
 * заканчивается не на её конце. Распознавание такого куска даёт текст с
 * потерянным началом. Для субтитров это незаметно (человек видит, как текст
 * достраивается), но отправлять его в модель как команду нельзя: «включи
 * яркость на восемьдесят» превратилось бы в «яркость на восемьдесят».
 * Поэтому финальный запрос несёт полный речевой буфер с самого первого слова.
 *
 * ════════════════════════════════════════════════════════════════════════
 *  ПОЧЕМУ В БУФЕР ПОПАДАЕТ ТОЛЬКО РЕЧЬ
 * ════════════════════════════════════════════════════════════════════════
 *
 * В буфер пишутся кадры, которые VAD признал речью, плюс короткий хвост
 * тишины после последнего слова. Долгая тишина в запрос не уходит вообще:
 * человек может думать десять секунд после нажатия кнопки, и это не стоит
 * ни одного лишнего байта квоты. Тишина между словами внутри фразы остаётся —
 * иначе речь звучала бы как склейка обрывков и распознавалась бы хуже.
 */
class GroqWhisperStt(private val context: android.content.Context) : SpeechToTextEngine {

    private val client = GroqSttClient(context)

    /** Детектор создаётся один раз: загрузка модели ONNX занимает десятки мс. */
    @Volatile
    private var detector: VoiceActivityDetector? = null

    /**
     * Прогрев: готовит детектор заранее, пока палец ещё на кнопке.
     *
     * Раньше здесь открывался веб-сокет. Теперь сокета нет, но есть модель
     * VAD: её загрузка — единственная часть, которую можно сделать заранее,
     * и она того стоит, потому что от неё зависит, услышим ли мы первое слово.
     */
    override suspend fun preconnect() {
        ensureDetector()
    }

    override suspend fun initialize() {
        ensureDetector()
    }

    override suspend fun close() {
        detector?.close()
        detector = null
        client.shutdown()
    }

    /**
     * Записывает одну фразу и возвращает её текст.
     *
     * Поток закрывается сам, когда срабатывает одно из условий: человек
     * замолчал на 600 мс после речи, он так и не заговорил за 6 секунд, или
     * запись достигла жёсткого лимита длительности.
     */
    override fun transcribe(options: EngineOptions): Flow<SttEvent> = callbackFlow {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            throw EngineException(ERROR_NO_PERMISSION)
        }

        val apiKey = options.api.groqKey.trim().ifBlank { BuildConfigKey }
        if (apiKey.isBlank()) throw EngineException(ERROR_NO_KEY)

        val vad = ensureDetector()
        val frameSamples = vad.frameSamples
        val frameMs = VoiceActivityDetector.FRAME_MS.toLong()

        val minBuffer = AudioRecord.getMinBufferSize(
            SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
        )
        if (minBuffer <= 0) throw EngineException(ERROR_MIC_UNAVAILABLE)

        val recorder = openRecorder(minBuffer)
        val model = ModelCatalog.resolveForRequest(ModelCatalog.Provider.GROQ_STT, options.api.sttModel)
        val whisperLanguage = options.languageCode.trim()
        val prompt = recognitionPrompt(options)

        // Буфер всей фразы — растёт пока говорит
        val utterance = ShortAccumulator(MAX_UTTERANCE_SAMPLES)
        var speechStarted = false
        var silenceMs = 0L
        val startedAt = System.currentTimeMillis()

        val job = launch(Dispatchers.IO) {
            val raw = ShortArray(frameSamples)
            try {
                recorder.startRecording()
                if (recorder.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                    close(EngineException(ERROR_MIC_BUSY))
                    return@launch
                }

                AmaliaLog.i("STT", "recording started | frameSamples=$frameSamples frameMs=${frameMs}ms")

                while (isActive) {
                    val read = recorder.read(raw, 0, frameSamples)
                    if (read <= 0) continue

                    val frame = if (read == frameSamples) raw else raw.copyOf(read)

                    // Уровень громкости — всегда, независимо от VAD
                    trySend(SttEvent.Level(VoiceSegmenter.level(frame)))

                    val isSpeech = vad.isSpeech(frame)

                    if (isSpeech) {
                        if (!speechStarted) {
                            AmaliaLog.i("STT", "★ speech started")
                        }
                        speechStarted = true
                        silenceMs = 0
                        utterance.append(frame)
                    } else {
                        if (speechStarted) {
                            // Дописываем хвост тишины — последний согласный часто тише порога
                            utterance.append(frame)
                            silenceMs += frameMs

                            if (silenceMs >= END_OF_SPEECH_MS) {
                                AmaliaLog.i("STT", "★ end of speech | utteranceSamples=${utterance.size} silenceMs=${silenceMs}ms")
                                break
                            }
                        } else {
                            // Речь не началась — ждём NO_SPEECH_TIMEOUT_MS
                            if (System.currentTimeMillis() - startedAt > NO_SPEECH_TIMEOUT_MS) {
                                AmaliaLog.w("STT", "no speech timeout")
                                channel.close()
                                return@launch
                            }
                        }
                    }

                    // Жёсткий лимит длины
                    if (utterance.size >= MAX_UTTERANCE_SAMPLES) {
                        AmaliaLog.w("STT", "max utterance length reached")
                        break
                    }
                }
            } catch (e: SecurityException) {
                close(EngineException(ERROR_PERMISSION_REVOKED, e)); return@launch
            } catch (e: IllegalStateException) {
                close(EngineException(ERROR_MIC_BUSY, e)); return@launch
            } finally {
                runCatching {
                    if (recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) recorder.stop()
                }
            }

            if (!speechStarted || utterance.size == 0) {
                AmaliaLog.w("STT", "no speech in buffer")
                channel.close()
                return@launch
            }

            // Отправляем ВЕСЬ буфер — никаких хвостов, никаких обрезков
            AmaliaLog.i("STT", "sending to Whisper | samples=${utterance.size} (~${utterance.size / SAMPLE_RATE}s)")
            val finalText = runCatching {
                client.transcribe(
                    wav = VoiceSegmenter.toWav(utterance.toArray()),
                    languageCode = whisperLanguage,
                    model = model,
                    apiKey = apiKey,
                    prompt = prompt,
                )
            }.getOrElse { error ->
                AmaliaLog.e("STT", "whisper error: ${error.message}", error)
                close(EngineException(error.message ?: ERROR_RECOGNITION, error))
                return@launch
            }

            val cleaned = VoiceSegmenter.collapseRepeats(finalText)
            AmaliaLog.i("STT", "whisper result: \"${cleaned.take(80)}\"")

            if (cleaned.isNotBlank()) {
                trySend(SttEvent.Final(cleaned))
            }
            channel.close()
        }

        awaitClose {
            job.cancel()
            runCatching { recorder.stop() }
            runCatching { recorder.release() }
        }
    }

    /**
     * Детектор создаётся лениво и потокобезопасно.
     *
     * `synchronized` здесь не для красоты: [preconnect] вызывается из UI
     * (касание кнопки), а [transcribe] — из корутины разговора, и оба могут
     * прийти почти одновременно. Без синхронизации модель загрузилась бы
     * дважды, а лишняя сессия ONNX — это десятки мегабайт памяти.
     */
    private fun ensureDetector(): VoiceActivityDetector {
        detector?.let { return it }
        synchronized(this) {
            detector?.let { return it }
            val created = VoiceActivityDetector.create(context)
            detector = created
            return created
        }
    }

    private fun openRecorder(minBuffer: Int): AudioRecord {
        val readBytes = minBuffer.coerceAtLeast(READ_CHUNK_BYTES)
        val recorder = try {
            AudioRecord(
                // VOICE_RECOGNITION отключает агрессивную обработку речи
                // (эхоподавление, AGC): она помогает звонкам, но «сглаживает»
                // тихие слова и портит распознавание.
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                readBytes * 4,
            )
        } catch (e: Exception) {
            throw EngineException(ERROR_MIC_UNAVAILABLE, e)
        }
        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            recorder.release()
            throw EngineException(ERROR_MIC_BUSY)
        }
        return recorder
    }

    /**
     * Подсказка для Whisper.
     *
     * Модель не знает предметной области и на слух пишет «вай фай» или
     * «включи ютуп». Короткий список ожидаемых слов снимает почти все такие
     * ошибки: имена устройств, команд и приложений, которыми человек реально
     * пользуется. Это не инструкция, а словарь — Whisper применяет его как
     * смещение вероятностей при выборе токенов.
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

    private fun samplesFor(seconds: Float): Int =
        (seconds * VoiceSegmenter.SAMPLE_RATE).toInt()

    private companion object {
        const val SAMPLE_RATE = VoiceSegmenter.SAMPLE_RATE

        /** Сколько читать за раз; кратно кадру VAD (512 сэмплов = 1024 байта). */
        const val READ_CHUNK_BYTES = 3_200

        /** Тишина после речи → конец фразы. 600мс — баланс между «обрезает» и «тормозит». */
        const val END_OF_SPEECH_MS = 600L

        /** Сколько ждать первого слова до таймаута. */
        const val NO_SPEECH_TIMEOUT_MS = 8_000L

        /** Сколько сэмплов речи максимум держим в буфере одной фразы (30 сек). */
        val MAX_UTTERANCE_SAMPLES: Int = (30 * SAMPLE_RATE)

        /** Сколько названий приложений и синонимов вмещать в подсказку. */
        const val APPS_IN_PROMPT = 8

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

        val BuildConfigKey: String
            get() = com.my.amali.BuildConfig.GROQ_API_KEY
    }
}

/**
 * Накопитель сэмплов речи с жёстким потолком.
 *
 * Отдельный класс, а не `MutableList<Short>`: речь идёт о десятках тысяч
 * сэмплов за фразу, и рост списка объектами `Short` дал бы тысячи аллокаций
 * на ровном месте. Массив с ручным размером держит одну непрерывную область.
 */
private class ShortAccumulator(private val capacity: Int) {
    val buffer = ShortArray(capacity.coerceAtLeast(1))
    var size: Int = 0
        private set

    /** Дописывает кадр; при переполнении молча отбрасывает лишнее. */
    fun append(frame: ShortArray) {
        val room = buffer.size - size
        if (room <= 0) return
        val count = minOf(room, frame.size)
        System.arraycopy(frame, 0, buffer, size, count)
        size += count
    }

    /** Копия ровно набранной длины — то, что уходит в запрос распознавания. */
    fun toArray(): ShortArray = buffer.copyOf(size)
}
