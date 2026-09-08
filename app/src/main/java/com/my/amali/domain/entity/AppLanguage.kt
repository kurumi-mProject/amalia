package com.my.amali.domain.entity

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
