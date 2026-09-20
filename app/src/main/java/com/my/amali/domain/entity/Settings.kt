package com.my.amali.domain.entity

import com.my.amali.ui.theme.AmaliaMotif
import com.my.amali.ui.theme.AmaliaVisualTheme
import com.my.amali.ui.theme.DarkModePreference

/**
 * Holds all user-adjustable settings for the Amalia voice assistant.
 *
 * The instance is persisted via DataStore (as JSON) and injected manually
 * through the app's DI graph; UI and feature layers treat it as immutable
 * and emit a new copy whenever a setting changes.
 *
 * @property visualTheme overall visual style of the assistant's orb and surfaces.
 * @property darkModePref how the app picks between light and dark appearance.
 * @property useBioTime enable adaptive theming based on the time of day.
 * @property glassIntensity glassmorphism blur strength, expected in [0.0, 1.0].
 * @property speechRate TTS speaking rate multiplier, expected in [0.5, 2.0].
 * @property speechPitch TTS pitch multiplier, expected in [0.5, 2.0].
 * @property autoListen automatically start listening when the app comes to the foreground.
 * @property wakeWordEnabled keep the wake-word detector active in the background.
 * @property dataRetentionDays conversation retention window; one of 7, 30, 90,
 *   or -1 meaning "keep forever".
 * @property selectedLanguage language used for STT/TTS and UI localization.
 * @property resumeLastSession continue the most recent conversation on launch
 *   instead of a blank one, so the compressed context survives a restart.
 * @property motif decorative layer over the living background: falling petals,
 *   leaves, snow, stars or fireflies. [AmaliaMotif.OFF] keeps the screen bare.
 */
/**
 * API-ключи и выбранные модели трёх провайдеров конвейера.
 *
 * ## Почему ключи живут в настройках, а не только в сборке
 *
 * Зашитый в APK ключ — это общий ресурс: он принадлежит сборке, а не человеку,
 * который этой сборкой пользуется. Отсюда три проблемы, которые решаются
 * ровно одним способом — дать ввести свой ключ:
 *
 *  1. **Квота.** Ключ из сборки может быть исчерпан («недостаточно средств»),
 *     и тогда приложение замолкает у всех сразу. Свой ключ возвращает голос
 *     немедленно и не требует пересборки.
 *  2. **Модели.** Линейка моделей у провайдера меняется чаще, чем выходит
 *     новая версия приложения. Имя модели — такая же настройка, как язык.
 *  3. **Доверие.** Пользователь вправе видеть, куда уходят его данные и чьим
 *     ключом оплачивается обращение.
 *
 * Пустое значение поля = «взять из сборки». Это не то же самое, что «выключить
 * провайдера»: конвейер обязан оставаться рабочим на дефолтной конфигурации,
 * иначе первый запуск приложения без ключей выглядел бы сломанным.
 *
 * @property groqKey ключ Groq: он же и мозг (генерация текста и вызов
 *   инструментов), и слух (распознавание речи через Whisper). Один ключ на
 *   две роли — так задумано: пользователю не нужно заводить второй аккаунт,
 *   а бесплатный лимит распознавания (2000 запросов в сутки) расходуется
 *   из общей квоты, которую видно в одном месте.
 * @property fishAudioKey ключ Fish Audio (синтез голоса).
 * @property llmModel идентификатор модели Groq; пусто → рекомендованная.
 * @property sttModel идентификатор модели Whisper; пусто → рекомендованная.
 * @property ttsModel идентификатор модели Fish Audio; пусто → рекомендованная.
 * @property fishVoiceId reference_id голоса Амалии в библиотеке Fish Audio.
 *   Вынесен сюда потому, что голос — это тоже выбор пользователя: у Fish
 *   Audio можно клонировать свой голос и подставить его идентификатор.
 */
data class UserApiSettings(
    val groqKey: String = "",
    val fishAudioKey: String = "",
    val llmModel: String = "",
    val sttModel: String = "",
    val ttsModel: String = "",
    val fishVoiceId: String = "",
    /**
     * Сколько тишины считать концом фразы, секунды.
     *
     * Это единственная настройка распознавания, которую человек способен
     * оценить на слух: она решает, как быстро ассистент понимает, что вы
     * договорили. Меньше — реагирует проворнее, но рискует оборвать фразу
     * на паузе между словами. Больше — надёжнее на длинных фразах, но
     * после каждой приходится ждать.
     *
     * Границы заданы константами [STT_SILENCE_MIN_SECONDS] и
     * [STT_SILENCE_MAX_SECONDS]: ниже 0.4 с ассистент начинает резать фразы
     * на дыхании, выше 1.5 с ожидание становится заметным раздражителем.
     */
    val sttSilenceSeconds: Float = STT_SILENCE_DEFAULT_SECONDS,
    /**
     * Своя точка подключения: адрес OpenAI-совместимого эндпоинта.
     *
     * Профиль «Своя модель» ходит не в Groq, а туда, куда скажет человек:
     * self-hosted vLLM, корпоративный шлюз, локальный llama.cpp, любой
     * прокси. Приложение не гадает про такие адреса и не подставляет
     * провайдеров по умолчанию — если поле пустое, профиль неактивен.
     *
     * Ожидается полный URL метода, а не база: `https://host/v1/chat/completions`.
     * Так пользователю не нужно угадывать, какой суффикс дописывает
     * приложение, — он копирует адрес из документации своего сервера.
     */
    val customEndpoint: String = "",

    /**
     * Модель для своего эндпоинта.
     *
     * Идентификаторы у self-hosted сборок произвольные (`qwen3-30b-local`,
     * `my-finetune-v4`), поэтому здесь нет ни списка, ни значения по
     * умолчанию: поле осмысленно только вместе с [customEndpoint].
     */
    val customModel: String = "",

    /**
     * Ключ для своего эндпоинта.
     *
     * Может быть пустым: локальный сервер часто не проверяет авторизацию.
     * В этом случае заголовок `Authorization` не отправляется вовсе, а не
     * уходит с пустым Bearer — некоторые серверы на пустой заголовок
     * отвечают ошибкой.
     */
    val customKey: String = "",
) {

    /** Задан ли хотя бы один собственный ключ. */
    val hasAnyKey: Boolean
        get() = groqKey.isNotBlank() ||
            fishAudioKey.isNotBlank() || customEndpoint.isNotBlank()

    /**
     * Сколько провайдеров настроено своими ключами (для строки-сводки).
     *
     * Считаются три: мозг и слух живут на одном ключе Groq, поэтому дают
     * одну строку, а не две — иначе сводка обещала бы пользователю больше
     * независимых сервисов, чем у него есть.
     */
    val configuredProviders: Int
        get() = listOf(groqKey, fishAudioKey, customEndpoint)
            .count { it.isNotBlank() }

    /**
     * Готова ли своя точка подключения к работе.
     *
     * Нужны и адрес, и модель: без модели запрос провайдер отклонит, и
     * пользователь получит непонятную ошибку вместо ответа. Ключ не
     * входит в проверку осознанно — локальные серверы часто открыты.
     */
    val customReady: Boolean
        get() = customEndpoint.isNotBlank() && customModel.isNotBlank()

    companion object {
        /**
         * Сколько позиций считается «настроено своими ключами».
         *
         * Три: Groq (мозг и слух на одном ключе), Fish Audio (голос) и своя
         * точка подключения. Число живёт рядом с [configuredProviders], чтобы
         * подпись на экране и подсчёт не разъезжались.
         */
        const val PROVIDER_COUNT: Int = 3

        /**
         * Минимальная пауза, означающая конец фразы.
         *
         * 0.4 с — предел, за которым ассистент начинает обрывать человека
         * на вдохе между словами: «включи… вайфай» превращается в две
         * команды. Ниже опускать нечего.
         */
        const val STT_SILENCE_MIN_SECONDS: Float = 0.4f

        /**
         * Максимальная пауза, означающая конец фразы.
         *
         * 1.5 с — предел терпения: дольше ждать ответа после того, как
         * человек уже договорил, становится раздражающим.
         */
        const val STT_SILENCE_MAX_SECONDS: Float = 1.5f

        /**
         * Значение по умолчанию: 0.6 с.
         *
         * Выбрано как компромисс между двумя ошибками: заметить конец фразы
         * вовремя и не оборвать человека на паузе между словами. Именно это
         * значение стоит в требованиях к поведению ассистента.
         */
        const val STT_SILENCE_DEFAULT_SECONDS: Float = 0.6f
    }
}

/**
 * Настройки живой волны: количество полос, их размер и реакция на голос.
 *
 * ## Границы диапазонов — не произвол
 *
 * Каждое поле ограничено так, чтобы результат оставался читаемым:
 *
 *  — [spikeCount] от 5 до 31. Меньше пяти — уже не волна, а три точки;
 *    больше тридцати одного при ширине 3dp и зазоре 4dp не влезает в
 *    экран 360dp и полосы становятся сплошной заливкой.
 *  — [spikeWidth] от 2 до 8dp. 1dp на плотных экранах превращается в
 *    волосок и мерцает при движении; шире 8dp волна перестаёт читаться
 *    как «много полос» и становится столбиками.
 *  — [spikeGap] от 1 до 10dp. При нулевом зазоре полосы сливаются.
 *  — [maxHeight] от 16 до 80dp. Это высота самой высокой полосы при
 *    максимальной громкости; 80dp — предел, после которого волна начинает
 *    конкурировать с главным текстом экрана.
 *  — [cornerRadius] от 0 до 8dp в половину ширины полосы: больше — и
 *    прямоугольник превращается в капсулу, теряя направление роста.
 *  — [sensitivity] от 0.5 до 4. Волна должна реагировать заметно тише или
 *    заметно громче реального сигнала: у разных микрофонов разная АРУ.
 *  — [smoothing] от 0.05 до 0.6. Это доля пути, которую полоса проходит к
 *    новой цели за кадр. Меньше — плавно и «текуче», больше — резко и
 *    «дёргано». Оба края имеют право на существование.
 *
 * @property spikeCount сколько полос рисуется.
 * @property spikeWidth ширина одной полосы, dp.
 * @property spikeGap зазор между полосами, dp.
 * @property maxHeight максимальная высота полосы при полной громкости, dp.
 * @property cornerRadius скругление концов полос, dp.
 * @property sensitivity множитель реакции на уровень громкости.
 * @property smoothing сглаживание движения, [0.05, 0.6].
 * @property filled true — полосы заливкой; false — контуром двойной толщины.
 */
data class WaveSettings(
    val spikeCount: Int = 11,
    val spikeWidth: Float = 3f,
    val spikeGap: Float = 4f,
    val maxHeight: Float = 44f,
    val cornerRadius: Float = 1.5f,
    val sensitivity: Float = 1.6f,
    val smoothing: Float = 0.22f,
    val filled: Boolean = true,
) {
    /** Полная ширина волны в dp — используется для превью и расчётов. */
    val totalWidthDp: Float
        get() = spikeCount * spikeWidth + (spikeCount - 1) * spikeGap

    companion object {
        const val COUNT_MIN = 5
        const val COUNT_MAX = 31
        const val WIDTH_MIN = 2f
        const val WIDTH_MAX = 8f
        const val GAP_MIN = 1f
        const val GAP_MAX = 10f
        const val HEIGHT_MIN = 16f
        const val HEIGHT_MAX = 80f
        const val RADIUS_MIN = 0f
        const val RADIUS_MAX = 8f
        const val SENSITIVITY_MIN = 0.5f
        const val SENSITIVITY_MAX = 4f
        const val SMOOTHING_MIN = 0.05f
        const val SMOOTHING_MAX = 0.6f
    }
}

data class UserSettings(
    val visualTheme: AmaliaVisualTheme = AmaliaVisualTheme.LIQUID_GLASS,
    val darkModePref: DarkModePreference = DarkModePreference.SYSTEM,
    val useBioTime: Boolean = true,
    val glassIntensity: Float = 0.6f,
    val speechRate: Float = 1.0f,
    val speechPitch: Float = 1.0f,
    val autoListen: Boolean = false,
    val wakeWordEnabled: Boolean = false,
    val dataRetentionDays: Int = 30,
    val selectedLanguage: AppLanguage = AppLanguage.SYSTEM,
    val resumeLastSession: Boolean = true,
    val motif: AmaliaMotif = AmaliaMotif.AUTO,
    val motifDensity: Float = 0.85f,
    /**
     * Геометрия и поведение живой волны главного экрана.
     *
     * Вынесено в настройки сознательно: волна — единственный объект, который
     * пользователь видит каждую секунду разговора, и «правильной» её формы не
     * существует. Кому-то нужно 9 широких полос, кому-то — 21 тонкая; у кого-то
     * тихий микрофон, и без поднятой чувствительности волна почти не движется.
     * Всё это — вопрос привычки и устройства, а не дизайна.
     */
    val wave: WaveSettings = WaveSettings(),
    /**
     * Ключи и модели провайдеров, которые пользователь задал сам.
     *
     * Собственные ключи **перебивают** зашитые в сборку: если человек вписал
     * свой Groq-ключ, запросы идут с ним и за его счёт — иначе он платил бы
     * за чужую квоту, не понимая, почему у него «недостаточно средств».
     */
    val api: UserApiSettings = UserApiSettings(),
    /**
     * Каким мозгом пользуется Амалия.
     *
     * Развилка появилась из живых замеров лимитов, а не из желания
     * «сделать настройку». Бесплатный Groq даёт 7 000 входных токенов в
     * минуту, а полный промпт Амалии — почти 8 000: он не отправляется
     * вообще, ни разу. Поэтому вариантов ровно два и они честно разные:
     *
     *  — [AiProfile.GROQ] — урезанный промпт (~1 000 токенов) на ключе
     *    Groq из сборки или пользователя. Работает бесплатно у всех,
     *    характер сжат до минимума: приоритет у того, чтобы всё работало.
     *  — [AiProfile.CUSTOM] — полный промпт (~8 000 токенов) на своём
     *    эндпоинте. Здесь ограничений приложения нет: если у человека
     *    свой сервер или платный тариф, он получает Амалию целиком —
     *    с лором, характером и всеми правилами.
     *
     * Промежуточного варианта «полный промпт на бесплатном Groq» не
     * существует: он физически не проходит по лимиту, и предлагать его
     * значило бы продавать нерабочее.
     */
    val aiProfile: AiProfile = AiProfile.GROQ,
) {
    /** Returns a copy with [motifDensity] clamped to the valid [0.0, 1.0] range. */
    fun withClampedMotif(): UserSettings = copy(motifDensity = motifDensity.coerceIn(0f, 1f))

    /** Returns a copy with [glassIntensity] clamped to the valid [0.0, 1.0] range. */
    fun withClampedGlass(): UserSettings = copy(glassIntensity = glassIntensity.coerceIn(0f, 1f))

    /** Returns a copy with speech [rate] clamped to the valid [0.5, 2.0] range. */
    fun withRate(rate: Float): UserSettings = copy(speechRate = rate.coerceIn(0.5f, 2.0f))

    /** Returns a copy with speech [pitch] clamped to the valid [0.5, 2.0] range. */
    fun withPitch(pitch: Float): UserSettings = copy(speechPitch = pitch.coerceIn(0.5f, 2.0f))

    companion object {
        /** Forever retention sentinel used by [dataRetentionDays]. */
        const val RETENTION_FOREVER: Int = -1

        /** Valid retention windows in days. */
        val RETENTION_OPTIONS: List<Int> = listOf(7, 30, 90, RETENTION_FOREVER)

        /** Default settings instance used before persistence is loaded. */
        val DEFAULT: UserSettings = UserSettings()

        /** Normalizes [days] to one of [RETENTION_OPTIONS], defaulting to 30. */
        fun normalizeRetention(days: Int): Int =
            if (days in RETENTION_OPTIONS) days else 30
    }
}

/**
 * Профиль «мозга» Амалии: какой промпт и через какой эндпоинт.
 *
 * Сделан перечислением, а не парой булевых флагов, потому что у профиля
 * есть обязательные спутники (адрес, модель) — их нельзя забыть проверить
 * при добавлении нового варианта: компилятор заставит обойти все ветки.
 */
enum class AiProfile {
    /** Бесплатный Groq с урезанным промптом: работает у всех и всегда. */
    GROQ,

    /** Свой OpenAI-совместимый эндпоинт с полным промптом и характером. */
    CUSTOM,
    ;

    companion object {
        /** Разбор сохранённого значения; неизвестное → безопасный Groq. */
        fun fromName(raw: String?): AiProfile =
            entries.firstOrNull { it.name == raw?.trim()?.uppercase() } ?: GROQ
    }
}
