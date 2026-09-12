package com.my.amali.data.ai

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.math.abs
import kotlin.math.sin

/**
 * Оффлайн-заглушка STT: имитирует уровень микрофона, промежуточные
 * гипотезы и финальный текст. Используется в превью, тестах и на
 * устройствах без сети, чтобы UI можно было проверить целиком.
 */
class MockSpeechToTextEngine(
    private val scriptedPhrase: String = DEFAULT_PHRASE,
) : SpeechToTextEngine {

    private var initialized = false

    override suspend fun initialize() {
        initialized = true
    }

    override suspend fun close() {
        initialized = false
    }

    override fun transcribe(options: EngineOptions): Flow<SttEvent> = flow {
        if (!initialized) initialize()

        val words = scriptedPhrase.split(' ').filter { it.isNotBlank() }
        val spoken = StringBuilder()

        repeat(WARMUP_TICKS) { tick ->
            emit(SttEvent.Level(levelAt(tick)))
            delay(TICK_MS)
        }

        words.forEachIndexed { index, word ->
            if (spoken.isNotEmpty()) spoken.append(' ')
            spoken.append(word)
            emit(SttEvent.Level(levelAt(WARMUP_TICKS + index)))
            emit(SttEvent.Partial(spoken.toString()))
            delay(WORD_MS)
        }

        emit(SttEvent.Final(spoken.toString()))
        emit(SttEvent.Level(0f))
    }

    /** Плавно «дышащий» уровень, чтобы волна выглядела живой. */
    private fun levelAt(tick: Int): Float =
        (0.25f + 0.6f * abs(sin(tick * 0.6)).toFloat()).coerceIn(0f, 1f)

    private companion object {
        const val DEFAULT_PHRASE = "Привет, Амалия, как погода сегодня"
        const val TICK_MS = 90L
        const val WORD_MS = 220L
        const val WARMUP_TICKS = 4
    }
}
