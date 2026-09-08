package com.my.amali.system

import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.provider.MediaStore
import android.provider.Settings
import com.my.amali.data.model.ChatMessage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowimport java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * Интенты системного управления — «руки» Амалии без root.
 *
 * Каждая функция возвращает [SystemAction]: либо конкретный интент,
 * который запускается UI-слоем, либо голосовой ответ. Действия не требуют
 * AccessorService и работают на всех поддерживаемых версиях Android.
 */
sealed class SystemAction {
    /** Готовый к запуску системный интент. */
    data class Launch(val intent: Intent, val description: String) : SystemAction()

    /** Ответ текстом — произносится и показывается. */
    data class Reply(val text: String) : SystemAction()
}

/**
 * Исполнитель голосовых команд системного уровня.
 *
 * Работает как прослойка над [AIOrchestrator]: перехватывает команды
 * вида «включи Wi-Fi», «открой настройки», «поставь таймер» и превращает
 * их в [SystemAction]. Неизвестные команды передаются обычному LLM-пайплайну.
 */
class SystemIntentExecutor(private val context: Context) {

    /**
     * Пытается сопоставить текст команды с известным системным действием.
     * null — команда не системная, обрабатывайте её LLM.
     */
    fun resolve(command: String): SystemAction? {
        val normalized = command.trim().lowercase()
        return when {
            containsAny(normalized, "открой настройки", "open settings") ->
                SystemAction.Launch(
                    Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    "Открываю настройки",
                )

            containsAny(normalized, "включи wi-fi", "включи вайфай", "turn on wi-fi", "wifi on") ->
                SystemAction.Launch(
                    Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    "Открываю панель подключения к сети",
                )

            containsAny(normalized, "включи bluetooth", "включи блютуз", "turn on bluetooth") ->
                SystemAction.Launch(
                    Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    "Bluetooth управляется через панель быстрых настроек",
                )

            containsAny(normalized, "яркость", "brightness") ->
                SystemAction.Launch(
                    Intent(Settings.ACTION_DISPLAY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    "Открываю настройки экрана",
                )

            containsAny(normalized, "громкость", "volume") ->
                SystemAction.Launch(
                    Intent(Settings.ACTION_SOUND_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    "Открываю настройки звука",
                )

            containsAny(normalized, "таймер", "timer") -> {
                val minutes = extractMinutes(normalized) ?: 1
                SystemAction.Launch(
                    Intent(AlarmClock.ACTION_SET_TIMER)
                        .putExtra(AlarmClock.EXTRA_LENGTH, minutes * 60)
                        .putExtra(AlarmClock.EXTRA_SKIP_UI, false)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    "Ставлю таймер на $minutes минут",
                )
            }

            containsAny(normalized, "будильник", "alarm") -> {
                val hour = extractHour(normalized) ?: 7
                SystemAction.Launch(
                    Intent(AlarmClock.ACTION_SET_ALARM)
                        .putExtra(AlarmClock.EXTRA_HOUR, hour)
                        .putExtra(AlarmClock.EXTRA_MINUTES, 0)
                        .putExtra(AlarmClock.EXTRA_SKIP_UI, false)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    "Ставлю будильник на $hour:00",
                )
            }

            containsAny(normalized, "какой час", "сколько времени", "what time") -> {
                val now = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))
                SystemAction.Reply("Сейчас $now")
            }

            containsAny(normalized, "позвони", "call ") -> {
                val number = extractPhone(normalized)
                if (number != null) {
                    SystemAction.Launch(
                        Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number"))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        "Открываю набор номера",
                    )
                } else {
                    SystemAction.Reply("Скажите номер телефона или имя контакта")
                }
            }

            containsAny(normalized, "найди в интернете", "search for", "поиск") -> {
                val query = extractSearchQuery(normalized)
                SystemAction.Launch(
                    Intent(Intent.ACTION_WEB_SEARCH)
                        .putExtra(SearchManager.QUERY, query)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    "Ищу: $query",
                )
            }

            containsAny(normalized, "открой ютуб", "open youtube") ->
                SystemAction.Launch(
                    packageIntent("com.google.android.youtube"),
                    "Открываю YouTube",
                )

            containsAny(normalized, "открой камеру", "open camera") ->
                SystemAction.Launch(
                    Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    "Открываю камеру",
                )

            containsAny(normalized, "новое событие", "календарь", "calendar event") ->
                SystemAction.Launch(
                    Intent(Intent.ACTION_INSERT)
                        .setData(CalendarContract.Events.CONTENT_URI)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    "Создаю новое событие в календаре",
                )

            containsAny(normalized, "новое сообщение", "напиши sms", "new sms") ->
                SystemAction.Launch(
                    Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    "Открываю новое SMS",
                )

            else -> null
        }
    }

    /** Запускает [SystemAction.Launch], безопасно перехватывая сбои. */
    fun execute(action: SystemAction): Boolean = when (action) {
        is SystemAction.Launch -> runCatching {
            context.startActivity(action.intent)
        }.isSuccess
        is SystemAction.Reply -> true
    }

    // ── Хелперы ──────────────────────────────────────────────────────────

    private fun containsAny(text: String, vararg keys: String): Boolean =
        keys.any { text.contains(it) }

    private fun extractMinutes(text: String): Int? =
        Regex("(\\d+)\\s*(мин|min)").find(text)?.groupValues?.get(1)?.toIntOrNull()

    private fun extractHour(text: String): Int? =
        Regex("(\\d{1,2})\\s*час").find(text)?.groupValues?.get(1)?.toIntOrNull()
            ?.coerceIn(0, 23)

    private fun extractPhone(text: String): String? =
        Regex("(\\+?[\\d\\s\\-()]{6,})").find(text)?.groupValues?.get(1)?.trim()

    private fun extractSearchQuery(text: String): String {
        val markers = listOf("найди в интернете", "поиск", "search for")
        return markers.firstNotNullOfOrNull { marker ->
            text.split(marker).getOrNull(1)?.trim()?.takeIf { it.isNotBlank() }
        } ?: text
    }

    private fun packageIntent(packageName: String): Intent =
        context.packageManager.getLaunchIntentForPackage(packageName)
            ?: Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
