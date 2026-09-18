package com.my.amali.data.ai

import android.util.Log

/**
 * Центральный логгер всего AI-конвейера Амалии.
 *
 * Каждый модуль пишет через этот объект — тег фиксирован, уровень DEBUG,
 * фильтр в Logcat: `tag:AMALIA`.
 *
 * Форматы:
 *   [TTS]   — FishAudioTTS
 *   [PCM]   — AudioPlayer
 *   [LLM]   — GroqLLM
 *   [ORC]   — AIOrchestrator / pipeline
 *   [STT]   — GroqWhisperStt
 *   [VM]    — AssistantViewModel
 *   [TOOL]  — AmaliaTools / ToolRegistry
 */
object AmaliaLog {
    const val TAG = "AMALIA"

    fun d(module: String, msg: String) = Log.d(TAG, "[$module] $msg")
    fun i(module: String, msg: String) = Log.i(TAG, "[$module] $msg")
    fun w(module: String, msg: String) = Log.w(TAG, "[$module] $msg")
    fun e(module: String, msg: String, t: Throwable? = null) =
        if (t != null) Log.e(TAG, "[$module] $msg", t)
        else Log.e(TAG, "[$module] $msg")

    /**
     * Идентификатор текущего прогона.
     *
     * ## Зачем он нужен
     *
     * Циклов одновременно может быть несколько: пользователь задал новый
     * вопрос, пока старый ещё доигрывает; hands-free запустил ещё один;
     * «Стоп» отменил предыдущий. Без сквозного идентификатора строки
     * `[TTS]`, `[PCM]`, `[ORC]`, `[VM]` невозможно связать между собой —
     * и на вопрос «кто убил эту озвучку» ответа нет.
     *
     * С идентификатором достаточно отфильтровать logcat по четырём символам:
     * ```
     * adb logcat -s AMALIA | grep a3f1
     * ```
     */
    @Volatile
    var currentRunId: String = "-"
        private set

    /**
     * Заводит новый идентификатор прогона.
     *
     * Четыре символа из 16 возможных: достаточно, чтобы различать два-три
     * одновременных цикла, и достаточно коротко, чтобы не мешать читать лог.
     */
    fun newRunId(): String {
        val id = (1..4)
            .map { ALPHABET.random() }
            .joinToString("")
        currentRunId = id
        return id
    }

    /** Лог с меткой прогона: `[TTS a3f1] …`. */
    fun tagWith(module: String, runId: String = currentRunId): String = "$module $runId"

    /** Отмечает вход в прогон — видно, с какого места пошёл новый цикл. */
    fun enter(module: String, runId: String) {
        currentRunId = runId
        i(tagWith(module, runId), "──── run $runId started ────")
    }

    private const val ALPHABET = "abcdefghijklmnopqrstuvwxyz0123456789"
}
