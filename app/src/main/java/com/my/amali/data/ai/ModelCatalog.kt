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
        /**
         * Groq — он же отдаёт и слух.
         *
         * Раньше распознавание шло в Deepgram, и это был отдельный ключ,
         * отдельный аккаунт и отдельный счёт. Теперь слух живёт там же, где
         * мозг: одна строка ключа, один бесплатный тариф, один лимит.
         * Провайдер остаётся отдельной записью каталога, потому что у него
         * свой список моделей и своя подпись на экране, но ключ — общий с
         * [GROQ].
         */
        GROQ_STT(
            key = "groq_stt",
            displayName = "Groq Whisper",
            consoleUrl = "console.groq.com/keys",
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

    /**
     * Модели распознавания речи (Groq Whisper).
     *
     * Обе модели — большие Whisper, но с разной ценой внимания:
     *
     *  — `turbo` — та же архитектура large-v3, но с ускоренным декодером.
     *    На живом замере 1 секунды русской речи отвечает за **0.17–0.22 с**
     *    против 0.33 с у полной модели. Именно она стоит по умолчанию:
     *    в голосовом ассистенте задержка важнее последнего процента
     *    точности, а разница в качестве на коротких командах не слышна.
     *  — `large-v3` — чуть точнее на длинных сложных фразах, но в два раза
     *    медленнее. Разумный выбор, если человек диктует длинные тексты.
     *
     * Лимит у обеих одинаковый (2000 запросов и 7200 секунд аудио в сутки
     * на бесплатном тарифе) и общий с моделью текста, поэтому список
     * намеренно короткий: каждая лишняя строка — соблазн выбрать модель,
     * которой потом не хватит квоты.
     */
    val sttModels: List<ModelOption> = listOf(
        ModelOption(
            id = "whisper-large-v3-turbo",
            title = "Whisper Large v3 Turbo",
            note = "Основная: 0.2 с на фразу, отличный русский",
            recommended = true,
        ),
        ModelOption(
            id = "whisper-large-v3",
            title = "Whisper Large v3",
            note = "Точнее на длинных фразах, но вдвое медленнее",
        ),
    )

    /**
     * Модели для генерации текста (Groq).
     *
     * Список сверен с живым `GET /openai/v1/models` на нашем ключе, а не
     * взят по памяти: линейка Groq меняется чаще, чем выходит новая версия
     * приложения, и модель, которой вчера не было, сегодня уже отдаётся
     * всем. Всё, что здесь перечислено, доступно на бесплатном тарифе.
     *
     * Из прежнего списка ушли Llama 3.3 70B, Llama 3.1 8B и Mistral Saba —
     * их Groq больше не отдаёт. Если оставить их в списке, пользователь
     * выбрал бы рабочее на вид имя и получил ошибку вместо ответа.
     */
    val llmModels: List<ModelOption> = listOf(
        ModelOption(
            id = "qwen/qwen3.8-27b",
            title = "Qwen 3.8 · 27B",
            note = "Основная: понимает команды, держит JSON, отвечает за доли секунды",
            recommended = true,
        ),
        ModelOption(
            id = "openai/gpt-oss-20b",
            title = "GPT-OSS · 20B",
            note = "Компактная и быстрая: дешёвый расход лимита",
        ),
        ModelOption(
            id = "openai/gpt-oss-120b",
            title = "GPT-OSS · 120B",
            note = "Крупная: умнее в сложных вопросах, но ест лимит заметно больше",
        ),
        ModelOption(
            id = "groq/compound-mini",
            title = "Compound Mini",
            note = "Умеет сама искать в интернете, когда нужен свежий факт",
        ),
        ModelOption(
            id = "groq/compound",
            title = "Compound",
            note = "То же, но крупнее: несколько инструментов подряд в одном ответе",
        ),
    )

    /** Модели синтеза речи (Fish Audio). */
    val ttsModels: List<ModelOption> = listOf(
        ModelOption(
            id = "drama-3-preview",
            title = "Drama 3 (preview)",
            note = "Живая интонация и эмоции, ~1 с до первого звука",
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
            note = "Основная: бесплатный тариф, встаёт в общую очередь, есть паузы",
            recommended = true,
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
        Provider.GROQ_STT -> sttModels
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
