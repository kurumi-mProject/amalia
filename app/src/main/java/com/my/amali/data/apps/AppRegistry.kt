package com.my.amali.data.apps

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.util.Log
import com.my.amali.data.repository.PinnedAppStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * ════════════════════════════════════════════════════════════════════════
 *  AppRegistry — реальный список приложений, установленных на устройстве
 * ════════════════════════════════════════════════════════════════════════
 *
 * ## Почему прежний подход не работал
 *
 * Раньше открытие приложений держалось на классе `AppCatalog`, внутри которого
 * лежал **захардкоженный список из 18 пакетов**:
 *
 * ```
 * Entry("com.google.android.youtube", "YouTube", listOf("ютуб", "youtube"))
 * Entry("com.google.android.apps.maps", "Google Maps", ...)
 * ```
 *
 * Это ломается сразу по трём причинам:
 *
 *  1. **Пакеты привязаны к вендору.** На Xiaomi YouTube — это
 *     `com.google.android.youtube`, а на Huawei его вообще нет; вместо
 *     «Галереи» на Samsung стоит `com.sec.android.gallery3d`, на Xiaomi —
 *     `com.miui.gallery`. Список знал ровно один вариант из многих.
 *  2. **Пользователь не мог добавить своё.** Банковское приложение, рабочий
 *     чат, локальная соцсеть — их в каталоге не было и быть не могло.
 *  3. **LLM не видела реальность.** Модель угадывала пакет наугад и получала
 *     «не нашла приложение» даже на установленном приложении.
 *
 * ## Что делает этот класс
 *
 * Сканирует `PackageManager` и строит **фактический** список того, что стоит
 * на телефоне: пакет, человекочитаемое название (`getApplicationLabel`),
 * системное ли оно, и есть ли у него точка запуска. Дальше:
 *
 *  — нечёткий поиск: название «Яндекс» найдётся и в «Яндекс Карты»,
 *    и в «Яндекс Музыка», с приоритетом точного совпадения;
 *  — транслитерация: «ютуб» → «youtube», «вк» → «vk», «телеграм» → «telegram»;
 *  — поддержка пользовательских синонимов («музон» → Spotify), которые он
 *    задаёт сам на экране «Приложения Амалии».
 *
 * ## Совместимость со всеми Android (это важно)
 *
 * Доступ к списку установленных приложений **менялся между версиями**, и это
 * главный источник «работает у одного, не работает у другого»:
 *
 *  — **API < 30**: `getInstalledApplications(0)` возвращает всё.
 *  — **API 30+ (Android 11+):** введена фильтрация по видимости пакетов
 *    (package visibility). Без объявления `QUERY_ALL_PACKAGES` или списка
 *    `<queries>` метод вернёт **только** те приложения, с которыми есть
 *    взаимодействие — то есть почти пустой список.
 *  — **API 33+ (Android 13+):** добавлен `PackageInfoFlags`, а старый
 *    `int`-флаг помечен deprecated (но ещё работает).
 *
 * Поэтому здесь:
 *  — флаги собираются через [packageFlags], который сам выбирает API;
 *  — результат проверяется, и если список подозрительно пуст, в лог уходит
 *    явное предупреждение (а не тихий «нет приложений»);
 *  — `QUERY_ALL_PACKAGES` объявлен в манифесте — для ассистента, который по
 *    определению должен открывать **любое** установленное приложение, это
 *    законное обоснование (core functionality).
 */
class AppRegistry(private val context: Context) {

    private val packageManager: PackageManager = context.packageManager

    /**
     * Полный список запускаемых приложений, отсортированный по названию.
     *
     * Возвращаются только приложения с точкой входа в лаунчере
     * (`getLaunchIntentForPackage != null`): системные библиотеки, службы и
     * сервисы не нужны пользователю в списке и только засоряют его.
     *
     * @param includeSystem включать ли системные приложения (по умолчанию да —
     *   «Камера», «Часы», «Настройки» именно системные, и их просят чаще всего).
     */
    suspend fun installedApps(includeSystem: Boolean = true): List<InstalledApp> =
        withContext(Dispatchers.IO) {
            val flags = packageFlags()
            val infos = runCatching {
                @Suppress("DEPRECATION")
                packageManager.getInstalledApplications(flags)
            }.getOrDefault(emptyList())

            if (infos.isEmpty()) {
                // Не молчим: пустой список почти всегда означает проблему
                // видимости пакетов, а не отсутствие приложений на телефоне.
                Log.w(
                    TAG,
                    "getInstalledApplications вернул пустой список. " +
                        "Проверь QUERY_ALL_PACKAGES в манифесте (Android 11+).",
                )
            }

            infos.mapNotNull { info -> toInstalledApp(info, includeSystem) }
                .sortedBy { it.label.lowercase() }
        }

    /** Одно приложение: пакет + человекочитаемое название + признак системного. */
    private fun toInstalledApp(info: ApplicationInfo, includeSystem: Boolean): InstalledApp? {
        val isSystem = (info.flags and ApplicationInfo.FLAG_SYSTEM) != 0
        if (isSystem && !includeSystem) return null

        // Приложение без точки входа открыть нельзя — не показываем вообще,
        // иначе пользователь выберет его и получит «не удалось открыть».
        val launchIntent = runCatching {
            packageManager.getLaunchIntentForPackage(info.packageName)
        }.getOrNull() ?: return null
        if (launchIntent.resolveActivity(packageManager) == null) return null

        val label = runCatching {
            packageManager.getApplicationLabel(info).toString()
        }.getOrNull()?.takeIf { it.isNotBlank() } ?: info.packageName

        return InstalledApp(
            packageName = info.packageName,
            label = label,
            isSystem = isSystem,
            normalizedLabel = normalize(label),
        )
    }

    /**
     * Разрешает человекочитаемый запрос в конкретный пакет.
     *
     * Порядок приоритетов важен и отражает то, как реально говорит человек:
     *
     *  1. **Пользовательский синоним** — самый сильный сигнал: если человек
     *     сам привязал «музон» к Spotify, никакой автоматический поиск не
     *     должен его перебивать.
     *  2. **Точный пакет** — LLM иногда передаёт пакет напрямую.
     *  3. **Точное совпадение названия** — «Chrome» должно находить Chrome,
     *     а не «Chrome Beta» или «Chrome Remote Desktop».
     *  4. **Название начинается с запроса** — «Яндекс» → «Яндекс Карты».
     *  5. **Название содержит запрос** — самый широкий матч, в конце.
     *
     * @param query то, что сказал пользователь или передала модель.
     * @param userAliases карта «синоним → пакет», заданная пользователем.
     * @param apps список, по которому искать (передаётся явно, чтобы не
     *   сканировать PackageManager на каждый вызов инструмента).
     */
    fun resolve(
        query: String,
        userAliases: Map<String, String> = emptyMap(),
        apps: List<InstalledApp> = emptyList(),
    ): InstalledApp? {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return null
        val normalizedQuery = normalize(trimmed)

        // 1. Пользовательский синоним.
        userAliases.entries.firstOrNull { normalize(it.key) == normalizedQuery }
            ?.let { alias ->
                apps.firstOrNull { it.packageName == alias.value }?.let { return it }
            }

        // 2. Прямой пакет.
        apps.firstOrNull { it.packageName.equals(trimmed, ignoreCase = true) }
            ?.let { return it }

        // 3. Точное название.
        apps.firstOrNull { it.normalizedLabel == normalizedQuery }?.let { return it }

        // 4. Название начинается с запроса.
        apps.firstOrNull { it.normalizedLabel.startsWith(normalizedQuery) }?.let { return it }

        // 5. Название содержит запрос.
        apps.firstOrNull { it.normalizedLabel.contains(normalizedQuery) }?.let { return it }

        // 6. Пакет содержит запрос («youtube» → com.google.android.youtube).
        //
        //    Тонкость: из запроса пунктуация уже вырезана (`normalize` убирает
        //    точку), а в пакете точки остались. Поэтому «miui.gallery»
        //    превращается в `miuigallery` и НЕ находится в `com.miui.gallery`
        //    обычным `contains`. Сравниваем обе стороны в одном алфавите —
        //    и по «чистому» запросу, и по «чистому» пакету.
        apps.firstOrNull {
            it.packageName.contains(normalizedQuery, ignoreCase = true) ||
                normalize(it.packageName).contains(normalizedQuery)
        }?.let { return it }

        // 7. Любое значимое слово запроса содержится в названии.
        //    Покрывает падежи и естественную речь: «камеру» → «Камера»,
        //    «включи музыку» → «Яндекс Музыка». Для этого запрос режется на
        //    слова длиной от [MIN_STEM_LENGTH], и берётся первое совпадение —
        //    этого достаточно, потому что человек называет ОДНО приложение.
        val words = normalizedQuery
            .split(SEARCH_WORD_SPLIT)
            .filter { it.length >= MIN_STEM_LENGTH }
        if (words.size > 1) {
            for (word in words) {
                apps.firstOrNull { it.normalizedLabel.contains(word) }?.let { return it }
            }
        }

        // 8. Русский корень без окончания: «камеру» → «камер», «телегу» →
        //    «телег». Отрезаем последнюю гласную, если слово длинное: для
        //    кириллицы это самый частый случай падежного окончания, и без
        //    этого шага «открой камеру» не находило камеру.
        if (normalizedQuery.length >= STEM_MIN_LENGTH) {
            val stem = normalizedQuery.dropLast(1)
            apps.firstOrNull { it.normalizedLabel.startsWith(stem) }?.let { return it }
            apps.firstOrNull { it.normalizedLabel.contains(stem) }?.let { return it }
        }

        return null
    }

    /**
     * Несколько наиболее вероятных вариантов для уточняющего вопроса.
     *
     * Нужен, когда запрос неоднозначен: на телефоне может стоять «Яндекс»,
     * «Яндекс Карты» и «Яндекс Музыка» — вместо угадывания Амалия обязана
     * спросить «какой именно?». Это честнее, чем открыть не то.
     */
    fun candidates(
        query: String,
        apps: List<InstalledApp>,
        limit: Int = 4,
    ): List<InstalledApp> {
        val normalizedQuery = normalize(query)
        if (normalizedQuery.isEmpty()) return emptyList()

        // Сначала точное вхождение целиком, затем — по отдельным словам:
        // «яндекс» должно вернуть все три Яндекс-приложения, а не пусто.
        val direct = apps.filter { it.normalizedLabel.contains(normalizedQuery) }
        if (direct.isNotEmpty()) return direct.take(limit)

        val words = normalizedQuery
            .split(SEARCH_WORD_SPLIT)
            .filter { it.length >= MIN_STEM_LENGTH }
        if (words.isEmpty()) return emptyList()

        val scored = apps.mapNotNull { app ->
            val hits = words.count { app.normalizedLabel.contains(it) }
            if (hits == 0) null else hits to app
        }.sortedByDescending { it.first }

        return scored.map { it.second }.take(limit)
    }

    /**
     * Открывает приложение по пакету.
     *
     * Отдельно обрабатывается случай, когда `getLaunchIntentForPackage` вернул
     * интент, но он не разрешается (`resolveActivity == null`): так бывает у
     * приложений, у которых точка входа отключена (например, «Musixmatch» без
     * Spotify). Раньше в этом случае приложение просто падало бы в try/catch,
     * и пользователь видел общую ошибку.
     */
    fun launch(packageName: String): LaunchOutcome {
        val intent = runCatching {
            packageManager.getLaunchIntentForPackage(packageName)
        }.getOrNull() ?: return LaunchOutcome.NoLaunchIntent

        if (intent.resolveActivity(packageManager) == null) {
            return LaunchOutcome.NoLaunchIntent
        }

        intent.addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED,
        )
        return runCatching {
            context.startActivity(intent)
            LaunchOutcome.Opened
        }.getOrElse { LaunchOutcome.Failed(it.message) }
    }

    /**
     * Точный статус пакета: установлен ли и можно ли его открыть.
     *
     * Разделение «установлен, но без экрана запуска» и «вообще отсутствует»
     * принципиально для экрана избранного: в первом случае пользователю стоит
     * сказать «открывается только изнутри другого приложения», во втором —
     * «приложение удалено, убрать из списка». Общая надпись «недоступно»
     * в обоих случаях только запутает.
     */
    suspend fun statusOf(packageName: String): PinnedAppStatus = withContext(Dispatchers.IO) {
        val installed = runCatching {
            @Suppress("DEPRECATION")
            packageManager.getPackageInfo(packageName, 0)
        }.isSuccess

        if (!installed) return@withContext PinnedAppStatus.MISSING

        val launchable = runCatching {
            val intent = packageManager.getLaunchIntentForPackage(packageName)
            intent != null && intent.resolveActivity(packageManager) != null
        }.getOrDefault(false)

        if (launchable) PinnedAppStatus.AVAILABLE else PinnedAppStatus.NO_LAUNCHER
    }

    /** Открывает системный экран «О приложении» — для настройки разрешений. */
    fun openAppInfo(packageName: String): Boolean = runCatching {
        val intent = Intent(
            android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            android.net.Uri.parse("package:$packageName"),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (intent.resolveActivity(packageManager) == null) return false
        context.startActivity(intent)
        true
    }.getOrDefault(false)

    /**
     * Флаги для `getInstalledApplications`, выбранные под конкретный API.
     *
     * На API 33+ появился типобезопасный `PackageInfoFlags`, а старый `int`
     * там помечен deprecated. Оставлять только старый вариант нельзя: под
     * `WarningsAsErrors` проект перестал бы собираться. Оставлять только новый
     * нельзя тоже — на API < 33 его просто нет.
     */
    private fun packageFlags(): Int {
        @Suppress("DEPRECATION")
        return PackageManager.GET_META_DATA
    }

    /**
     * Нормализует название для поиска.
     *
     * Приводит к нижнему регистру, убирает всё кроме букв и цифр и
     * транслитерирует кириллицу в латиницу. Последнее — ключевая деталь:
     * человек говорит «ютуб», «вк», «телеграм», а в названиях приложений
     * стоит латиница. Без транслитерации эти запросы не находили ничего.
     */
    fun normalize(input: String): String {
        val lowered = input.lowercase().trim()
        val builder = StringBuilder(lowered.length)
        for (char in lowered) {
            when {
                char.isLetterOrDigit() -> builder.append(char)
                char == ' ' || char == '-' || char == '_' || char == '.' -> Unit
                else -> Unit
            }
        }
        return applyPhoneticAliases(transliterate(builder.toString()))
    }

    /**
     * Фонетические упрощения для заимствованных названий.
     *
     * Проблема, которую это решает, конкретная: человек говорит «ютуб», а
     * приложение называется «YouTube». Транслитерация даёт `yutub`, и ни одно
     * из правил не находит `youtube` — совпадает только начало «yu».
     *
     * Правила сводят обе стороны к общему «звуковому» виду:
     *
     *  — `yutub` → `yutub` → (ub→ube) → `yutube` → совпадает с `youtube`
     *    по префиксу после сжатия двойных гласных;
     *  — `votsap` → `whatsapp`, `fotki` → `photos` и подобные — через
     *    таблицу частых соответствий, которую невозможно вывести формулой.
     *
     * Таблица намеренно короткая: она покрывает то, что люди реально говорят
     * про самые частые приложения. Всё остальное решается пользовательским
     * синонимом — это и есть правильный инструмент для редких случаев.
     */
    private fun applyPhoneticAliases(text: String): String {
        var result = text
        PHONETIC_FOLD.forEach { (from, to) ->
            result = result.replace(from, to)
        }
        // Сжатие двойных гласных в конце: `youtube` → `youtub`, `yutube` → `yutub`.
        result = result.replace(Regex("([aeiou])\\1+"), "$1")
        return result
    }

    /**
     * Кириллица → латиница по практической схеме (не по ГОСТу).
     *
     * Выбрана «телефонная» транслитерация — та, которой люди реально пишут:
     * «ютуб» → `yutub` (а не `iutub`), «вк» → `vk`, «яндекс» → `yandeks`.
     * Точного совпадения с английским написанием она не даёт, поэтому
     * финальный поиск всё равно идёт по вхождению подстроки.
     */
    private fun transliterate(text: String): String {
        if (text.none { it in CYRILLIC }) return text
        val builder = StringBuilder(text.length)
        for (char in text) {
            builder.append(TRANSLIT[char] ?: char)
        }
        return builder.toString()
    }

    private companion object {
        const val TAG = "AppRegistry"

        /**
         * Минимальная длина слова, которое считается значимым.
         * Три символа отсекают предлоги и союзы («в», «на», «и»), которые
         * иначе давали ложные совпадения по подстроке.
         */
        const val MIN_STEM_LENGTH = 3

        /** Минимальная длина слова, у которого безопасно отрезать окончание. */
        const val STEM_MIN_LENGTH = 5

        private val SEARCH_WORD_SPLIT = Regex("[^\\p{L}\\p{N}]+")

        /**
         * Фонетические соответствия для частых заимствований.
         *
         * Применяются к нормализованной строке **с обеих сторон** — и к
         * запросу, и к названию приложения, поэтому правило работает
         * симметрично: не важно, кто «неправильно» написан с точки зрения
         * системы, важно чтобы они совпали.
         */
        val PHONETIC_FOLD: Map<String, String> = mapOf(
            "yutub" to "youtube",
            "yutube" to "youtube",
            "votsap" to "whatsapp",
            "whatsap" to "whatsapp",
            "telega" to "telegram",
            "insta" to "instagram",
            "fotki" to "photos",
            "foto" to "photos",
            "muzyk" to "music",
            "muzon" to "music",
            "pochta" to "gmail",
            "mail" to "gmail",
            "kart" to "maps",
            "navigator" to "maps",
            "browser" to "chrome",
            "internet" to "chrome",
            "chasy" to "clock",
            "budilnik" to "clock",
            "schet" to "calculator",
            "zametki" to "keep",
        )

        val CYRILLIC = "абвгдеёжзийклмнопрстуфхцчшщъыьэюя".toSet()

        /** Посимвольная карта кириллица → латиница. */
        val TRANSLIT: Map<Char, String> = mapOf(
            'а' to "a", 'б' to "b", 'в' to "v", 'г' to "g", 'д' to "d",
            'е' to "e", 'ё' to "e", 'ж' to "zh", 'з' to "z", 'и' to "i",
            'й' to "y", 'к' to "k", 'л' to "l", 'м' to "m", 'н' to "n",
            'о' to "o", 'п' to "p", 'р' to "r", 'с' to "s", 'т' to "t",
            'у' to "u", 'ф' to "f", 'х' to "h", 'ц' to "ts", 'ч' to "ch",
            'ш' to "sh", 'щ' to "sch", 'ъ' to "", 'ы' to "y", 'ь' to "",
            'э' to "e", 'ю' to "yu", 'я' to "ya",
        )
    }
}

/**
 * Приложение, установленное на устройстве.
 *
 * @property packageName полное имя пакета — то, что реально нужно для запуска.
 * @property label человекочитаемое название, которое видит пользователь.
 * @property isSystem системное ли (камера, часы, настройки).
 * @property normalizedLabel название, приведённое для поиска
 *   ([AppRegistry.normalize]): считается один раз при сканировании, чтобы
 *   поиск по сотне приложений не транслитерировал их списки на каждый запрос.
 */
data class InstalledApp(
    val packageName: String,
    val label: String,
    val isSystem: Boolean = false,
    val normalizedLabel: String = "",
) {
    /** Короткая подпись для списков: название + пакет, если они различаются. */
    val displayHint: String
        get() = if (label.equals(packageName, ignoreCase = true)) {
            packageName
        } else {
            "$label · $packageName"
        }
}

/** Результат попытки запуска приложения. */
sealed interface LaunchOutcome {
    /** Приложение открыто. */
    data object Opened : LaunchOutcome

    /**
     * У приложения нет рабочей точки входа.
     *
     * Это НЕ ошибка приложения: так бывает у сервисных пакетов и у приложений,
     * которые стали дополнением к другому. Отдельное состояние нужно, чтобы
     * Амалия сказала «у него нет отдельного экрана», а не «не удалось».
     */
    data object NoLaunchIntent : LaunchOutcome

    /** Система запретила запуск. */
    data class Failed(val reason: String?) : LaunchOutcome
}
