package com.my.amali.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.my.amali.core.di.ServiceLocator
import com.my.amali.data.apps.AppRegistry
import com.my.amali.data.apps.InstalledApp
import com.my.amali.data.repository.AppPreferencesRepository
import com.my.amali.data.repository.PinnedApp
import com.my.amali.data.repository.PinnedAppStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Состояние экрана «Приложения Амалии».
 *
 * @property apps полный список установленных приложений на устройстве.
 * @property visibleApps то, что показывается прямо сейчас: результат фильтра
 *   по [query] и сортировки (избранные — первыми).
 * @property pinned отмеченные приложения.
 * @property aliases синонимы «как говорю» → пакет.
 * @property query текущий поисковый запрос (нормализованный).
 * @property isLoading идёт сканирование `PackageManager`.
 */
data class AppPickerUiState(
    val apps: List<InstalledApp> = emptyList(),
    val visibleApps: List<InstalledApp> = emptyList(),
    val pinned: List<PinnedApp> = emptyList(),
    val aliases: Map<String, String> = emptyMap(),
    val query: String = "",
    val isLoading: Boolean = true,
    /**
     * Пакеты из избранного, которые установлены, но не имеют экрана запуска.
     *
     * Отдельное множество, потому что такие приложения не попадают в [apps]
     * (там только запускаемые), но и «удалёнными» их называть нельзя.
     */
    val missingPackages: Set<String> = emptySet(),
) {
    /** Отмечено ли приложение. */
    fun isPinned(packageName: String): Boolean =
        pinned.any { it.packageName == packageName }

    /**
     * Актуальный статус отмеченного приложения.
     *
     * Считается из уже отсканированного списка, без обращения к
     * `PackageManager`: в [apps] попадают только запускаемые приложения,
     * поэтому:
     *
     *  — приложение есть в списке → [PinnedAppStatus.AVAILABLE];
     *  — нет в списке, но пакет присутствует в [missingPackages] →
     *    [PinnedAppStatus.NO_LAUNCHER] (установлено, но открыть нельзя);
     *  — иначе → [PinnedAppStatus.MISSING] (удалено).
     *
     * Разделение важно: первое сообщение объясняет, почему приложение не
     * открывается, второе — предлагает убрать запись.
     */
    fun statusOf(packageName: String): PinnedAppStatus = when {
        apps.any { it.packageName == packageName } -> PinnedAppStatus.AVAILABLE
        packageName in missingPackages -> PinnedAppStatus.NO_LAUNCHER
        else -> PinnedAppStatus.MISSING
    }
}

/**
 * ViewModel экрана выбора приложений.
 *
 * ## Ключевые решения
 *
 * **Скан — один раз при создании, а не на каждый ввод.** `PackageManager`
 * обходит сотни пакетов и читает метки через ресурсы; делать это на каждый
 * набранный символ невозможно. Поэтому список сканируется один раз, а фильтр
 * работает по уже нормализованным строкам в памяти.
 *
 * **Нормализация считается один раз при сканировании.** Транслитерация
 * кириллицы — операция не бесплатная; для 150 приложений она выполняется
 * 150 раз при загрузке вместо 150 × N на каждый поисковый запрос.
 *
 * **Фильтр с debounce.** Даже по памяти проход по списку на каждый символ
 * даёт заметное мигание при быстром наборе: список перестраивается, позиции
 * прыгают. Небольшая задержка делает ввод плавным.
 *
 * **Избранные — первыми в результатах.** Пользователь в 90% случаев ищет
 * то, что уже отметил (например, чтобы добавить синоним), поэтому они
 * поднимаются наверх независимо от алфавита.
 */
@OptIn(FlowPreview::class)
class AppPickerViewModel(
    private val registry: AppRegistry = ServiceLocator.appRegistry,
    private val repository: AppPreferencesRepository = ServiceLocator.appPreferencesRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(AppPickerUiState())
    val uiState: StateFlow<AppPickerUiState> = _state.asStateFlow()

    /** Поток поискового запроса с задержкой против мигания списка. */
    private val queryFlow = MutableStateFlow("")

    init {
        // Сканируем установленные приложения один раз.
        viewModelScope.launch {
            val apps = withContext(Dispatchers.IO) {
                runCatching { registry.installedApps(includeSystem = true) }
                    .getOrDefault(emptyList())
            }
            // Отдельно вычисляем, какие из отмеченных приложений установлены,
            // но не имеют экрана запуска: их не видно в `apps`, однако это не
            // значит, что приложение удалено.
            val pinnedNow = runCatching { repository.pinnedApps.first() }
                .getOrDefault(emptyList())
            val noLauncher = withContext(Dispatchers.IO) {
                pinnedNow.mapNotNull { pinned ->
                    val status = runCatching { registry.statusOf(pinned.packageName) }
                        .getOrDefault(PinnedAppStatus.MISSING)
                    pinned.packageName.takeIf { status == PinnedAppStatus.NO_LAUNCHER }
                }.toSet()
            }

            _state.update { current ->
                current.copy(
                    apps = apps,
                    isLoading = false,
                    missingPackages = noLauncher,
                    visibleApps = filterAndSort(
                        apps = apps,
                        query = current.query,
                        pinned = current.pinned,
                    ),
                )
            }
        }

        // Подписка на избранное и синонимы: экран обязан обновляться сразу,
        // как только пользователь что-то отметил, без ручного refresh.
        viewModelScope.launch {
            combine(
                repository.pinnedApps,
                repository.aliases,
                queryFlow.debounce(SEARCH_DEBOUNCE_MS),
            ) { pinned, aliases, query ->
                Triple(pinned, aliases, query)
            }.collect { (pinned, aliases, query) ->
                _state.update { current ->
                    current.copy(
                        pinned = pinned,
                        aliases = aliases,
                        query = query,
                        visibleApps = filterAndSort(
                            apps = current.apps,
                            query = query,
                            pinned = pinned,
                        ),
                    )
                }
            }
        }
    }

    // ── Публичное API ────────────────────────────────────────────────────

    /** Обновляет поисковый запрос. */
    fun setQuery(value: String) {
        queryFlow.value = value
    }

    /** Переключает отметку приложения. */
    fun togglePin(app: InstalledApp) {
        viewModelScope.launch {
            if (_state.value.isPinned(app.packageName)) {
                repository.unpinApp(app.packageName)
            } else {
                repository.pinApp(app)
            }
        }
    }

    /** Убирает приложение из избранного (в том числе «мёртвую» запись). */
    fun unpin(packageName: String) {
        viewModelScope.launch { repository.unpinApp(packageName) }
    }

    /** Сохраняет голосовое название. */
    fun addAlias(alias: String, packageName: String) {
        viewModelScope.launch { repository.setAlias(alias, packageName) }
    }

    /** Удаляет голосовое название. */
    fun removeAlias(alias: String) {
        viewModelScope.launch { repository.removeAlias(alias) }
    }

    /** Все синонимы конкретного приложения — для показа в диалоге. */
    fun aliasesFor(packageName: String): List<String> =
        _state.value.aliases
            .filterValues { it == packageName }
            .keys
            .sorted()

    // ── Фильтрация и сортировка ──────────────────────────────────────────

    /**
     * Фильтрует по запросу и поднимает избранные наверх.
     *
     * Поиск идёт по **нормализованной** метке и по пакету одновременно:
     * «галерея» — по названию, «miui.gallery» — по пакету. Это важно, потому
     * что на кастомных прошивках название может быть переведено неточно, и
     * пакет остаётся единственной надёжной зацепкой.
     */
    private fun filterAndSort(
        apps: List<InstalledApp>,
        query: String,
        pinned: List<PinnedApp>,
    ): List<InstalledApp> {
        val normalizedQuery = registry.normalize(query)
        val pinnedPackages = pinned.mapTo(HashSet()) { it.packageName }

        val filtered = if (normalizedQuery.isEmpty()) {
            apps
        } else {
            apps.filter { app ->
                app.normalizedLabel.contains(normalizedQuery) ||
                    app.packageName.contains(query.trim(), ignoreCase = true)
            }
        }

        // Сортировка: избранные → обычные → системные, внутри — по названию.
        return filtered.sortedWith(
            compareBy(
                { if (it.packageName in pinnedPackages) 0 else 1 },
                { if (it.isSystem) 1 else 0 },
                { it.label.lowercase() },
            ),
        )
    }

    private companion object {
        /**
         * Задержка поиска.
         *
         * 120 мс — ниже порога, на котором человек замечает «тормозит», но
         * достаточно, чтобы поглотить промежуточные состояния при быстром
         * наборе и не перестраивать список на каждой букве.
         */
        const val SEARCH_DEBOUNCE_MS = 120L
    }
}
