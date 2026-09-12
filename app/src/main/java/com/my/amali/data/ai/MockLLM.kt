package com.my.amali.data.ai

import com.my.amali.data.model.ChatMessage
import com.my.amali.data.model.MessageRole
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.random.Random

/**
 * Mock language model for development and demos.
 *
 * Selects a realistic Russian response for common query categories using
 * keyword matching (greetings, self-description, help, Wi-Fi/Bluetooth
 * device commands, time, weather, thanks) and falls back to a polite
 * generic answer. The selected response is streamed word by word with
 * small delays to mimic real token-by-token generation.
 */
class MockLanguageModel : LanguageModel {

    private val random = Random(System.currentTimeMillis())

    private var initialized: Boolean = false

    override suspend fun initialize() {
        initialized = true
    }

    override suspend fun close() {
        initialized = false
    }

    override fun generateResponse(
        prompt: String,
        history: List<ChatMessage>,
        options: EngineOptions,
    ): Flow<String> = flow {
        if (!initialized) initialize()

        val normalized = prompt.trim().lowercase()
        val response = pickResponse(normalized, history)

        // Stream the answer word by word, as a real LLM would emit tokens.
        val words = response.split(Regex("\\s+")).filter { it.isNotBlank() }
        for ((index, word) in words.withIndex()) {
            emit(if (index == 0) word else " $word")
            delay(WORD_DELAY_MS + random.nextLong(WORD_DELAY_JITTER_MS))
        }
    }

    /** Chooses a response template for the normalized [query]. */
    private fun pickResponse(query: String, history: List<ChatMessage>): String = when {
        matchesGreeting(query) -> greeting()
        matchesAbout(query) -> about()
        matchesHelp(query) -> help()
        matchesWifi(query) -> wifiResponse(query)
        matchesBluetooth(query) -> bluetoothResponse(query)
        matchesTime(query) -> timeResponse()
        matchesWeather(query) -> weatherResponse()
        matchesThanks(query) -> thanksResponse()
        else -> fallback(history)
    }

    // region Keyword matchers -------------------------------------------------

    private fun matchesGreeting(q: String): Boolean =
        listOf("привет", "здравствуй", "здравствуйте", "доброе утро", "добрый день", "добрый вечер", "хай").any(q::contains)

    private fun matchesAbout(q: String): Boolean =
        listOf("кто ты", "расскажи о себе", "что ты такое", "твое имя", "твоё имя", "как тебя зовут").any(q::contains)

    private fun matchesHelp(q: String): Boolean =
        listOf("помощь", "помоги", "что ты умеешь", "какие команды", "возможности", "справка").any(q::contains)

    private fun matchesWifi(q: String): Boolean =
        listOf("вайфай", "вай-фай", "wi-fi", "wifi", "ви-фи").any(q::contains)

    private fun matchesBluetooth(q: String): Boolean =
        listOf("блютуз", "блю-туз", "bluetooth", "синезуб").any(q::contains)

    private fun matchesTime(q: String): Boolean =
        listOf("время", "который час", "сколько времени").any(q::contains)

    private fun matchesWeather(q: String): Boolean =
        listOf("погода", "погоду", "на улице холодно", "дождь", "прогноз").any(q::contains)

    private fun matchesThanks(q: String): Boolean =
        listOf("спасибо", "благодарю", "thanks", "благодарность").any(q::contains)

    // endregion

    // region Responses --------------------------------------------------------

    private fun greeting(): String = listOf(
        "Привет! Я Амалия, твой голосовой помощник. Чем могу помочь?",
        "Здравствуйте! Рада тебя слышать. Что будем делать?",
        "Привет-привет! Слушаю тебя внимательно."
    ).random(random)

    private fun about(): String =
        "Меня зовут Амалия — я голосовой ассистент для Android. " +
            "Я умею слушать твои команды, отвечать на вопросы, управлять настройками " +
            "устройства и помнить контекст нашего разговора."

    private fun help(): String =
        "Вот что я умею: отвечать на вопросы и болтать с тобой, " +
            "говорить точное время, рассказывать о погоде, " +
            "включать и выключать Wi-Fi и Bluetooth, " +
            "менять яркость экрана и громкость. " +
            "Просто скажи команду обычными словами."

    private fun wifiResponse(query: String): String = when {
        listOf("включи", "запусти", "активируй").any(query::contains) ->
            "Хорошо, включаю Wi-Fi. Подключение установлено, сеть стабильная."
        listOf("выключи", "отключи", "выруби").any(query::contains) ->
            "Выключаю Wi-Fi. Мобильный интернет продолжит работать как раньше."
        listOf("статус", "состояние", "как").any(query::contains) ->
            "Wi-Fi сейчас активен, сигнал отличный, скорость соединения высокая."
        else -> "Wi-Fi управляется: скажи «включи вайфай» или «выключи вайфай», и я всё сделаю."
    }

    private fun bluetoothResponse(query: String): String = when {
        listOf("включи", "запусти", "активируй").any(query::contains) ->
            "Включаю Bluetooth. Ищу ближайшие устройства для подключения."
        listOf("выключи", "отключи", "выруби").any(query::contains) ->
            "Bluetooth выключен. Энергия батареи скажет тебе спасибо."
        else -> "Могу включить или выключить Bluetooth — просто скажи, что сделать."
    }

    private fun timeResponse(): String {
        val now = LocalTime.now()
        val formatted = now.format(DateTimeFormatter.ofPattern("HH:mm"))
        val partOfDay = when (now.hour) {
            in 5..11 -> "Доброе утро"
            in 12..17 -> "Добрый день"
            in 18..22 -> "Добрый вечер"
            else -> "Ночь на дворе"
        }
        return "$partOfDay! Сейчас $formatted."
    }

    private fun weatherResponse(): String = listOf(
        "По моим данным, сейчас ясно, около восемнадцати градусов. Отличный день для прогулки!",
        "На улице переменная облачность, примерно пятнадцать градусов тепла. Возьми куртку на всякий случай.",
        "Ожидается небольшой дождь и семнадцать градусов. Зонт не помешает."
    ).random(random)

    private fun thanksResponse(): String = listOf(
        "Всегда пожалуйста! Обращайся ещё.",
        "Рада помочь! Если что-то понадобится — я здесь.",
        "Не за что! Хорошего тебе дня."
    ).random(random)

    private fun fallback(history: List<ChatMessage>): String {
        val previousTurns = history.count { it.role == MessageRole.USER }
        return if (previousTurns > 0) {
            "Я тебя услышала, но пока учусь отвечать на такие вопросы. " +
                "Попробуй спросить про время, погоду или попросить включить Wi-Fi."
        } else {
            "Интересная мысль! Я всё ещё mock-модель, поэтому отвечаю общими фразами. " +
                "Спроси меня про время, погоду, Wi-Fi или просто поздоровайся."
        }
    }

    // endregion

    private companion object {
        /** Base delay between streamed words, in ms. */
        const val WORD_DELAY_MS = 60L

        /** Random jitter added to the word delay, in ms. */
        const val WORD_DELAY_JITTER_MS = 60L
    }
}
