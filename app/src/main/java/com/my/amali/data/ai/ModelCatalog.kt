package com.my.amali.data.ai

/**
 * Каталог известных моделей по провайдерам.
 *
 * ## Зачем он нужен
 *
 * Провайдеры выпускают и снимают модели чаще, чем обновляется приложение.
 * Раньше имя модели было зашито в код (`GroqLLM.MODEL`, `nova-3`, `drama-3-preview`),
 * и когда провайдер менял линейку, пользователь видел «Модель недоступна для
 * этого ключа» — без единого способа что-то исправить. Пересобрать APK ради
 * одной строки нельзя.
 *
 * Поэтому теперь работает связка из трёх частей:
 *
 *  1. **Готовый список** — то, что точно существует на момент выпуска. Он
 *     подставляется в выпадающий список: пользователю не нужно ничего знать
 *     про идентификаторы моделей.
 *  2. **Ручной ввод** — [CUSTOM_SENTINEL] в том же списке. Если провайдер
 *     выкатил новую модель, её достаточно вписать руками, и она уйдёт в запрос
 *     как есть. Приложение не спорит с пользователем: он знает свою линейку
 *     лучше, чем выпущенный APK.
 *  3. **Проверка на пустоту** — пустая модель недопустима, поэтому всегда есть
 *     [defaultFor]: движок никогда не уйдёт в запрос без имени модели.
 *
 * ## Почему у каждой модели есть описание
 *
 * Список из одних идентификаторов (`qwen/qwen3.8-27b`) ничего не говорит
 * человеку. [ModelOption.note] отвечает на вопрос «а зачем мне эта»: быстрая
 * она или умная, дешёвая или точная. Без этого выбор превращается в угадывание.
 */
object ModelCatalog {

    /**
     * Значение, означающее «модель введёт сам пользователь».
     *
     * Выбрано так, чтобы его невозможно было спутать с настоящим
     * идентификатором: настоящие не содержат угловых скобок и пробелов.
     */
    const val CUSTOM_SENTINEL = "<своя модель>"

    /** Провайдеры, для которых настраиваются ключ и модель. */
    enum class Provider(
        val key: String,
        val displayName: String,
        /** Где пользователь берёт ключ — показывается на экране настройки. */
        val consoleUrl: String,
    ) {
        GROQ(
            key = "groq",
            displayName = "Groq",
            consoleUrl = "console.groq.com/keys",
        ),
        DEEPGRAM(
            key = "deepgram",
            displayName = "Deepgram",
            consoleUrl = "console.deepgram.com",
        ),
        FISH_AUDIO(
            key = "fish_audio",
            displayName = "Fish Audio",
            consoleUrl = "fish.audio/go-api",
        ),
        ;

        companion object {
            fun fromKey(raw: String?): Provider? =
                entries.firstOrNull { it.key == raw?.trim()?.lowercase() }
        }
    }

    /**
     * Один вариант модели в списке.
     *
     * @param id идентификатор, который уходит в API запроса.
     * @param title как это называется по-человечески.
     * @param note одна строка «зачем эта модель» — цена/скорость/качество.
     * @param recommended помечает выбор, который стоит взять по умолчанию
     *   (звёздочка в списке и подпись «рекомендую» под заголовком).
     */
    data class ModelOption(
        val id: String,
        val title: String,
        val note: String,
        val recommended: Boolean = false,
    )

    /** Модели для распознавания речи (Deepgram). */
    val sttModels: List<ModelOption> = listOf(
        ModelOption(
            id = "nova-3",
            title = "Nova-3",
            note = "Точнее всех на живом голосе, лучшая пунктуация",
            recommended = true,
        ),
        ModelOption(
            id = "nova-3-general",
            title = "Nova-3 General",
            note = "То же качество, но без мультиязычных надстроек",
        ),
        ModelOption(
            id = "nova-2",
            title = "Nova-2",
            note = "Предыдущее поколение: чуть дешевле, чуть медленнее",
        ),
        ModelOption(
            id = "enhanced",
            title = "Enhanced",
            note = "Старая модель: работает там, где Nova недоступна",
        ),
        ModelOption(
            id = "base",
            title = "Base",
            note = "Самая дешёвая, для черновиков и тестов",
        ),
    )

    /** Модели для генерации текста (Groq). */
    val llmModels: List<ModelOption> = listOf(
        ModelOption(
            id = "qwen/qwen3.8-27b",
            title = "Qwen 3.8 · 27B",
            note = "Баланс скорости и ума: понимает команды, держит JSON",
            recommended = true,
        ),
        ModelOption(
            id = "llama-3.3-70b-versatile",
            title = "Llama 3.3 · 70B",
            note = "Умнее в сложных вопросах, но отвечает медленнее",
        ),
        ModelOption(
            id = "llama-3.1-8b-instant",
            title = "Llama 3.1 · 8B",
            note = "Мгновенная реакция: для коротких команд идеальна",
        ),
        ModelOption(
            id = "openai/gpt-oss-120b",
            title = "GPT-OSS · 120B",
            note = "Крупная открытая модель, хорошо держит инструменты",
        ),
        ModelOption(
            id = "openai/gpt-oss-20b",
            title = "GPT-OSS · 20B",
            note = "Компактная: быстрая и дешёвая",
        ),
        ModelOption(
            id = "mistral-saba-24b",
            title = "Mistral Saba · 24B",
            note = "Мультиязычная: аккуратно работает с редкими языками",
        ),
    )

    /** Модели синтеза речи (Fish Audio). */
    val ttsModels: List<ModelOption> = listOf(
        ModelOption(
            id = "drama-3-preview",
            title = "Drama 3 (preview)",
            note = "Живая интонация и эмоции, ~1 с до первого звука",
            recommended = true,
        ),
        ModelOption(
            id = "s2.1-pro",
            title = "S2.1 Pro",
            note = "Стабильное качество студийного уровня",
        ),
        ModelOption(
            id = "s2-pro",
            title = "S2 Pro",
            note = "Предыдущее поколение S2: надёжно и предсказуемо",
        ),
        ModelOption(
            id = "s2.1-pro-free",
            title = "S2.1 Pro (free)",
            note = "Бесплатный тариф: встаёт в общую очередь, есть паузы",
        ),
        ModelOption(
            id = "speech-1.6",
            title = "Speech 1.6",
            note = "Классическая модель: работает на любом аккаунте",
        ),
        ModelOption(
            id = "speech-1.5",
            title = "Speech 1.5",
            note = "Самая совместимая: минимальные требования к тарифу",
        ),
    )

    /** Готовый список моделей для провайдера. */
    fun modelsFor(provider: Provider): List<ModelOption> = when (provider) {
        Provider.GROQ -> llmModels
        Provider.DEEPGRAM -> sttModels
        Provider.FISH_AUDIO -> ttsModels
    }

    /**
     * Готовый список **с добавленным пунктом «своя модель»**.
     *
     * Отправлять [CUSTOM_SENTINEL] в API нельзя — это служебное значение
     * только для списка, поэтому в запрос уходит выбор пользователя из
     * [resolveForRequest].
     */
    fun modelsWithCustom(provider: Provider): List<ModelOption> =
        modelsFor(provider) + ModelOption(
            id = CUSTOM_SENTINEL,
            title = "Своя модель",
            note = "Впиши идентификатор вручную, если знаешь его точно",
        )

    /**
     * Модель по умолчанию для провайдера.
     *
     * Рекомендованная, если она есть; иначе первая в списке. Возврат
     * не-null гарантирует, что движок никогда не уйдёт в запрос с пустым
     * именем модели.
     */
    fun defaultFor(provider: Provider): String =
        modelsFor(provider).firstOrNull { it.recommended }?.id
            ?: modelsFor(provider).first().id

    /** Значение по умолчанию для списка (с учётом «своей модели»). */
    fun defaultSelectionFor(provider: Provider): String =
        modelsFor(provider).firstOrNull { it.recommended }?.id ?: CUSTOM_SENTINEL

    /** Идентификатор модели подписан по-человечески, если он есть в каталоге. */
    fun titleFor(provider: Provider, modelId: String): String =
        modelsFor(provider).firstOrNull { it.id == modelId }?.title ?: modelId

    /**
     * Что реально уходит в API.
     *
     * Если пользователь вписал свою модель — уходит она. Если список пуст
     * или в поле оказался служебный маркер, подставляется рекомендованная:
     * запрос с пустой моделью провайдер отклонит, и пользователь увидит
     * ошибку вместо ответа.
     */
    fun resolveForRequest(provider: Provider, selection: String?): String {
        val trimmed = selection?.trim().orEmpty()
        if (trimmed.isEmpty() || trimmed == CUSTOM_SENTINEL) {
            return defaultFor(provider)
        }
        return trimmed
    }

    /**
     * Похоже ли значение на ручной ввод, а не на выбор из списка.
     *
     * Нужно экрану: если пользователь вписал «my-model-2», список должен
     * открыться на пункте «Своя модель», а не показывать пункт, которого нет.
     */
    fun isCustom(provider: Provider, selection: String?): Boolean {
        val trimmed = selection?.trim().orEmpty()
        if (trimmed.isEmpty()) return false
        return modelsFor(provider).none { it.id == trimmed }
    }
}
