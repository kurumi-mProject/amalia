package com.my.amali.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.my.amali.data.apps.InstalledApp
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

/**
 * ════════════════════════════════════════════════════════════════════════
 *  AppPreferencesRepository — какие приложения «свои» у Амалии
 * ════════════════════════════════════════════════════════════════════════
 *
 * Пользователь заходит в настройки, видит **реальный список приложений,
 * установленных на его телефоне**, и отмечает нужные. Отдельно можно задать
 * синоним — как он это приложение называет голосом.
 *
 * ## Зачем это вообще нужно
 *
 * Автоматический поиск по названию хорошо справляется с «открой ютуб», но
 * плохо — с тем, как люди говорят на самом деле:
 *
 *  — **«открой музон»** — модель не знает, что это Spotify;
 *  — **«включи телегу»** — «телега» не является подстрокой «Telegram»,
 *    транслитерация даёт `telega`, и точного совпадения нет;
 *  — **омонимы**: на телефоне три приложения со словом «Банк», и угадывание
 *    приводит к открытию не того, что опасно (банковское приложение).
 *
 * Синоним решает всё это разом: человек один раз сказал «музон — это Spotify»,
 * и дальше работает навсегда. Приоритет синонима — **выше** любого
 * автоматического поиска: если пользователь задал соответствие явно, машина
 * не имеет права его перебивать.
 *
 * ## Почему два отдельных хранилища
 *
 *  — **pinned** — список отмеченных пакетов. Он же идёт в промпт LLM: модель
 *    видит только то, что пользователь реально использует, а не 180 пакетов.
 *  — **aliases** — карта «синоним → пакет». Отдельно, потому что у одного
 *    приложения может быть несколько названий и они живут независимо.
 *
 * Оба хранятся как JSON-строки в одном Preferences-ключе каждый: список
 * небольшой (десятки записей), а DataStore Preferences не умеет
 * коллекции — поэтому сериализация здесь, а не отдельная база.
 */
class AppPreferencesRepository(private val dataStore: DataStore<Preferences>) {

    private object Keys {
        val PINNED_APPS = stringPreferencesKey("pref_pinned_apps")
        val APP_ALIASES = stringPreferencesKey("pref_app_aliases")
        val ALIAS_PRIORITY = stringPreferencesKey("pref_alias_priority")
    }

    // ── Чтение ───────────────────────────────────────────────────────────

    /** Отмеченные пользователем приложения (в порядке добавления). */
    val pinnedApps: Flow<List<PinnedApp>> = dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { prefs -> parsePinned(prefs[Keys.PINNED_APPS]) }

    /**
     * Синонимы: «как человек называет» → пакет.
     *
     * Ключи хранятся в нормализованном виде (нижний регистр, без пробелов),
     * потому что сравнение при разрешении идёт по нормализованной строке.
     */
    val aliases: Flow<Map<String, String>> = dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { prefs -> parseAliases(prefs[Keys.APP_ALIASES]) }

    /**
     * Приоритетное приложение для синонима.
     *
     * Нужно для случая, когда у одного слова несколько приложений: «музон» →
     * Spotify, но пользователь иногда хочет YouTube Music. Тогда у синонима
     * есть основной пакет ([PinTarget.Primary]) и альтернативы, а это поле
     * хранит выбранный основной.
     */
    val aliasPriority: Flow<Map<String, String>> = dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { prefs -> parseAliases(prefs[Keys.ALIAS_PRIORITY]) }

    // ── Запись: отмеченные приложения ────────────────────────────────────

    /** Добавляет приложение в избранные Амалии. Идемпотентно. */
    suspend fun pinApp(app: InstalledApp) {
        dataStore.edit { prefs ->
            val current = parsePinned(prefs[Keys.PINNED_APPS]).toMutableList()
            if (current.none { it.packageName == app.packageName }) {
                current += PinnedApp(
                    packageName = app.packageName,
                    label = app.label,
                    isSystem = app.isSystem,
                )
            }
            prefs[Keys.PINNED_APPS] = serializePinned(current)
        }
    }

    /** Убирает приложение из избранных и заодно чистит его синонимы. */
    suspend fun unpinApp(packageName: String) {
        dataStore.edit { prefs ->
            val current = parsePinned(prefs[Keys.PINNED_APPS])
                .filterNot { it.packageName == packageName }
            prefs[Keys.PINNED_APPS] = serializePinned(current)

            // Синонимы без приложения — мусор: они бы указывали в никуда.
            val cleanedAliases = parseAliases(prefs[Keys.APP_ALIASES])
                .filterValues { it != packageName }
            prefs[Keys.APP_ALIASES] = serializeAliases(cleanedAliases)
        }
    }

    /** Полная замена списка — для экрана с мультивыбором. */
    suspend fun setPinnedApps(apps: List<InstalledApp>) {
        dataStore.edit { prefs ->
            prefs[Keys.PINNED_APPS] = serializePinned(
                apps.map {
                    PinnedApp(it.packageName, it.label, it.isSystem)
                },
            )
        }
    }

    // ── Запись: синонимы ─────────────────────────────────────────────────

    /**
     * Привязывает синоним к приложению.
     *
     * Синоним нормализуется перед сохранением: «Музон», «музон », «МУЗОН» —
     * это одно и то же слово, и хранить три записи бессмысленно.
     */
    suspend fun setAlias(alias: String, packageName: String) {
        val key = alias.trim().lowercase()
        if (key.isEmpty()) return
        dataStore.edit { prefs ->
            val current = parseAliases(prefs[Keys.APP_ALIASES]).toMutableMap()
            current[key] = packageName
            prefs[Keys.APP_ALIASES] = serializeAliases(current)
        }
    }

    /** Убирает синоним. */
    suspend fun removeAlias(alias: String) {
        val key = alias.trim().lowercase()
        dataStore.edit { prefs ->
            val current = parseAliases(prefs[Keys.APP_ALIASES]).toMutableMap()
            current.remove(key)
            prefs[Keys.APP_ALIASES] = serializeAliases(current)
        }
    }

    /** Убирает все синонимы конкретного приложения. */
    suspend fun removeAliasesFor(packageName: String) {
        dataStore.edit { prefs ->
            val current = parseAliases(prefs[Keys.APP_ALIASES])
                .filterValues { it != packageName }
            prefs[Keys.APP_ALIASES] = serializeAliases(current)
        }
    }

    /** Полная очистка — используется при сбросе настроек. */
    suspend fun clearAll() {
        dataStore.edit { prefs ->
            prefs.remove(Keys.PINNED_APPS)
            prefs.remove(Keys.APP_ALIASES)
            prefs.remove(Keys.ALIAS_PRIORITY)
        }
    }

    // ── Сериализация ─────────────────────────────────────────────────────

    private fun parsePinned(raw: String?): List<PinnedApp> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { index ->
                val obj = array.optJSONObject(index) ?: return@mapNotNull null
                val packageName = obj.optString("package")
                if (packageName.isBlank()) return@mapNotNull null
                PinnedApp(
                    packageName = packageName,
                    label = obj.optString("label").ifBlank { packageName },
                    isSystem = obj.optBoolean("system", false),
                )
            }
        }.getOrDefault(emptyList())
    }

    private fun serializePinned(apps: List<PinnedApp>): String {
        val array = JSONArray()
        apps.forEach { app ->
            array.put(
                JSONObject()
                    .put("package", app.packageName)
                    .put("label", app.label)
                    .put("system", app.isSystem),
            )
        }
        return array.toString()
    }

    private fun parseAliases(raw: String?): Map<String, String> {
        if (raw.isNullOrBlank()) return emptyMap()
        return runCatching {
            val obj = JSONObject(raw)
            obj.keys().asSequence().associateWith { key -> obj.optString(key) }
                .filterValues { it.isNotBlank() }
        }.getOrDefault(emptyMap())
    }

    private fun serializeAliases(aliases: Map<String, String>): String {
        val obj = JSONObject()
        aliases.forEach { (key, value) -> obj.put(key, value) }
        return obj.toString()
    }

    private fun emptyPreferences(): Preferences =
        androidx.datastore.preferences.core.emptyPreferences()
}

/**
 * Отмеченное пользователем приложение.
 *
 * @property packageName имя пакета — единственный стабильный идентификатор.
 *   Название может измениться при обновлении приложения, пакет — нет.
 * @property label название на момент добавления. Хранится, чтобы список
 *   избранного отрисовывался мгновенно, без повторного сканирования
 *   `PackageManager` при каждом открытии экрана.
 * @property isSystem системное ли приложение.
 */
data class PinnedApp(
    val packageName: String,
    val label: String,
    val isSystem: Boolean = false,
)

/**
 * Что делать, если приложение в избранном, но его больше нет на телефоне.
 *
 * Хранить «мёртвые» записи нельзя: LLM получит пакет, которого нет, и
 * пообещает пользователю открыть то, что не откроется. Экран настроек
 * обязан показать такие записи как «удалено» и дать убрать их одним тапом.
 */
enum class PinnedAppStatus {
    /** Приложение установлено и запускается. */
    AVAILABLE,

    /** Пакет есть, но нет точки входа — открыть нельзя. */
    NO_LAUNCHER,

    /** Приложение удалено с устройства. */
    MISSING,
}
