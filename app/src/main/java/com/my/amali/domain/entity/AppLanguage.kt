package com.my.amali.domain.entity

import java.util.Locale

/**
 * Разбор языка интерфейса в код для распознавания речи.
 *
 * Живёт рядом с перечислением, а не внутри движка: язык выбирается в
 * настройках ([AppLanguage]), а уходит в запрос уже обычной строкой, и
 * превращение одного в другое должно быть видно в одном месте.
 */
object SpeechLanguage {
    /**
     * Код языка для Whisper.
     *
     * @param language выбор пользователя; [AppLanguage.SYSTEM] разрешается в
     *   язык устройства — человек, у которого телефон на русском, ожидает
     *   русское распознавание и без явной настройки.
     * @return двухбуквенный код или пустая строка, если Whisper должен
     *   определить язык сам (так бывает, когда система говорит на языке,
     *   которого нет в списке).
     */
    fun resolve(language: AppLanguage): String {
        if (language.isSystem) {
            val system = Locale.getDefault().language.lowercase(Locale.ROOT)
            // Язык системы может быть любым из сотни: если он поддержан,
            // отдаём его, иначе молчим и разрешаем модели решить самой.
            return if (system in SUPPORTED) system else ""
        }
        return language.whisperCode
    }

    /**
     * Языки, для которых подсказка заведомо полезна.
     *
     * Список повторяет языки интерфейса: приложение переведено ровно на них,
     * и подсказывать Whisper язык, на котором пользователь даже не может
     * прочитать меню, смысла нет.
     */
    private val SUPPORTED = setOf("ru", "en", "es", "de", "fr", "hi", "ja", "zh", "ar")
}

/**
 * Supported application and speech languages for the Amalia voice assistant.
 *
 * Each language carries its BCP-47-style [code], an English [displayName],
 * a [nativeName] written in the language itself, and an [isRTL] flag used
 * for correct text layout direction.
 */
enum class AppLanguage(
    val code: String,
    val displayName: String,
    val nativeName: String,
    val isRTL: Boolean = false
) {
    /** Follow the device system language. */
    SYSTEM("", "System", "Системный"),

    RUSSIAN("ru", "Russian", "Русский"),

    ENGLISH("en", "English", "English"),

    SPANISH("es", "Spanish", "Español"),

    ARABIC("ar", "Arabic", "العربية", isRTL = true),

    GERMAN("de", "German", "Deutsch"),

    FRENCH("fr", "French", "Français"),

    HINDI("hi", "Hindi", "हिन्दी"),

    JAPANESE("ja", "Japanese", "日本語"),

    CHINESE("zh", "Chinese", "中文");

    /** Whether this entry resolves dynamically to the device language. */
    val isSystem: Boolean
        get() = this == SYSTEM

    /**
     * Код языка для Whisper (`language` в multipart-запросе распознавания).
     *
     * Отдельно от [code], потому что у модели свой список: она понимает
     * около сотни языков, но названия у них ISO-639-1 — те же два символа,
     * только без региональных надстроек. Пустая строка означает «не
     * подсказывать язык»: тогда Whisper определит его сам.
     *
     * Отдельное свойство, а не переиспользование [code], нужно ещё и
     * потому, что подсказка языка — сильный рычаг качества: с ней модель
     * не тратит первые слова на угадывание и не «переключается» на
     * английский посреди русской фразы.
     */
    val whisperCode: String
        get() = code

    companion object {
        /**
         * Resolves an [AppLanguage] from a language [code] such as "ru" or "ru-RU".
         * Matching is case-insensitive and tolerant of region suffixes
         * (e.g. "ru_RU" and "en-US" both resolve). Unknown or empty codes
         * fall back to [SYSTEM].
         */
        fun fromCode(code: String): AppLanguage {
            if (code.isBlank()) return SYSTEM
            val normalized = code.trim().lowercase().replace('_', '-')
            return entries.firstOrNull { lang ->
                lang.code.isNotEmpty() &&
                    (normalized == lang.code || normalized.startsWith("${lang.code}-"))
            } ?: SYSTEM
        }

        /**
         * Resolves an [AppLanguage] from a JVM/Android locale tag,
         * falling back to [SYSTEM] when the tag is not recognized.
         */
        fun fromLocaleTag(tag: String): AppLanguage = fromCode(tag)
    }
}
