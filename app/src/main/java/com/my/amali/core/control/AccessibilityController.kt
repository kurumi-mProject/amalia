package com.my.amali.core.control

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Path
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.util.DisplayMetrics
import android.view.WindowManager
import android.view.accessibility.AccessibilityNodeInfo
import com.my.amali.core.accessibility.AmaliaAccessibilityService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import kotlin.coroutines.resume

/**
 * Контроллер специального доступа: превращает [AmaliaAccessibilityService]
 * в набор операций, которые может вызвать LLM.
 *
 * ## Зачем отдельный слой
 * Сервис живёт в системном процессе жизненного цикла и не знает ни про
 * инструменты, ни про JSON. Контроллер — единственное место, где
 * «системные возможности» переводятся в «результат для модели»:
 * структурированный снимок экрана, успех/провал жеста, путь к скриншоту.
 * Благодаря этому слой инструментов остаётся декларативным, а всю грязную
 * работу с узлами и жестами можно править в одном файле.
 *
 * ## Почему здесь используется базовый AccessibilityService, а не наш подкласс
 * Kotlin **не наследует Java static-поля через подкласс**: выражение
 * `AmaliaAccessibilityService.GLOBAL_ACTION_BACK` компилируется, но в
 * рантайме ссылается на nested-классы Kotlin-обёртки, а не на реальные
 * `int`-константы `android.accessibilityservice.AccessibilityService`.
 * Результат — `IllegalArgumentException: illegal global action` или
 * невоспроизводимое «сделано как-то не так». Поэтому обращаемся напрямую
 * через [AccessibilityService.GLOBAL_ACTION_*].
 *
 * ## Честность перед моделью
 * Если специальный доступ не включён, контроллер не «делает вид»,
 * а возвращает [ControlOutcome.ServiceDisabled] — инструмент сообщает
 * модели причину, и Амалия предлагает пользователю выдать доступ.
 */
class AccessibilityController(private val context: Context) {

    /** Специальный доступ включён и сервис жив. */
    val isEnabled: Boolean
        get() = AmaliaAccessibilityService.isRunning && AmaliaAccessibilityService.instance != null

    private val service: AmaliaAccessibilityService?
        get() = AmaliaAccessibilityService.instance?.takeIf { AmaliaAccessibilityService.isRunning }

    // ══════════════════════════════════════════════════════════════════
    //  Глобальные действия
    // ══════════════════════════════════════════════════════════════════

    /** Выполняет глобальное системное действие (назад, домой, шторка…). */
    suspend fun globalAction(action: GlobalAction): ControlOutcome = onService { service ->
        val performed = withContext(Dispatchers.Main) {
            service.performGlobalAction(action.systemCode())
        }
        if (performed) {
            ControlOutcome.Done(JSONObject().put("action", action.toolKey).toString())
        } else {
            ControlOutcome.Failed("Система отклонила действие «${action.toolKey}».")
        }
    }

    /**
     * Блокировка экрана отдельным методом: действие доступно с API 28,
     * и на старых устройствах нужно честно сказать модели об этом,
     * а не вернуть «не получилось».
     */
    suspend fun lockScreen(): ControlOutcome {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            return ControlOutcome.Failed("Блокировка экрана доступна с Android 9.")
        }
        return globalAction(GlobalAction.LOCK_SCREEN)
    }

    // ══════════════════════════════════════════════════════════════════
    //  Чтение экрана
    // ══════════════════════════════════════════════════════════════════

    /**
     * Снимок активного окна: заголовок, пакет, интерактивные элементы
     * и сплошной текст. Модель использует его, чтобы «видеть» чужой UI
     * и осознанно выбирать, куда нажать.
     */
    suspend fun readScreen(maxElements: Int = 48): ControlOutcome = onService { service ->
        val snapshot = withContext(Dispatchers.Main) {
            val root = service.rootInActiveWindow
            if (root == null) {
                null
            } else {
                ScreenSnapshot.collect(
                    root = root,
                    fallbackPackage = AmaliaAccessibilityService.lastWindowPackage,
                    maxElements = maxElements,
                )
            }
        }
        if (snapshot == null) {
            ControlOutcome.Failed("Не удалось прочитать окно — система не отдала содержимое.")
        } else {
            ControlOutcome.Done(snapshot.toJson().toString())
        }
    }

    // ══════════════════════════════════════════════════════════════════
    //  Нажатия и жесты
    // ══════════════════════════════════════════════════════════════════

    /**
     * Нажимает элемент, чей текст / contentDescription / id содержит [query].
     *
     * Поиск идёт обходом дерева; приоритет у кликабельных узлов и у точных
     * совпадений. Если найден некликабельный узел (например TextView внутри
     * строки списка) — поднимаемся вверх к первому кликабельному родителю,
     * это стандартное поведение «нажать на строку».
     */
    suspend fun tapOnText(query: String, longPress: Boolean = false): ControlOutcome =
        onService { service ->
            val result = withContext(Dispatchers.Main) {
                val root = service.rootInActiveWindow
                    ?: return@withContext TapAttempt.NoWindow
                val node = findBestNode(root, query)
                if (node == null) {
                    TapAttempt.NotFound(collectSimilarLabels(root, query, max = 5))
                } else {
                    val target = clickableSelfOrAncestor(node) ?: node
                    val bounds = Rect().also { target.getBoundsInScreen(it) }
                    val label = target.describe()
                    val action = if (longPress) {
                        AccessibilityNodeInfo.ACTION_LONG_CLICK
                    } else {
                        AccessibilityNodeInfo.ACTION_CLICK
                    }
                    val performed = target.performAction(action)
                    if (performed) TapAttempt.Tapped(label, bounds)
                    else TapAttempt.Rejected(label)
                }
            }
            when (result) {
                is TapAttempt.Tapped -> ControlOutcome.Done(
                    JSONObject()
                        .put("tapped", result.label)
                        .put("long_press", longPress)
                        .put("bounds", result.bounds.toShortString())
                        .toString(),
                )

                is TapAttempt.Rejected -> ControlOutcome.Failed(
                    "Нашла «${result.label}», но система не дала нажать.",
                )

                TapAttempt.NoWindow -> ControlOutcome.Failed("Активное окно недоступно.")
                is TapAttempt.NotFound -> ControlOutcome.Failed(
                    "Не нашла «$query» на экране." +
                        if (result.candidates.isEmpty()) ""
                        else " Похожее: ${result.candidates.take(5).joinToString(", ")}",
                )
            }
        }

    /** Свайп по экрану в одну из четырёх сторон. */
    suspend fun swipe(direction: SwipeDirection, durationMs: Long = 320L): ControlOutcome =
        onService { service ->
            val (width, height) = screenSize()
            val centerX = width / 2f
            val centerY = height / 2f
            val margin = 0.18f
            val path = Path().apply {
                when (direction) {
                    SwipeDirection.UP -> {
                        moveTo(centerX, height * (1f - margin))
                        lineTo(centerX, height * margin)
                    }

                    SwipeDirection.DOWN -> {
                        moveTo(centerX, height * margin)
                        lineTo(centerX, height * (1f - margin))
                    }

                    SwipeDirection.LEFT -> {
                        moveTo(width * (1f - margin), centerY)
                        lineTo(width * margin, centerY)
                    }

                    SwipeDirection.RIGHT -> {
                        moveTo(width * margin, centerY)
                        lineTo(width * (1f - margin), centerY)
                    }
                }
            }
            val ok = dispatchGesture(service, path, durationMs.coerceIn(80L, 3_000L))
            if (ok) {
                ControlOutcome.Done(
                    JSONObject().put("swipe", direction.toolKey).put("duration_ms", durationMs).toString(),
                )
            } else {
                ControlOutcome.Failed("Свайп не выполнен.")
            }
        }

    /** Тап по экранным координатам — для случаев, когда текст не читается. */
    suspend fun tapAt(x: Int, y: Int): ControlOutcome = onService { service ->
        val (width, height) = screenSize()
        if (x !in 0..width || y !in 0..height) {
            return@onService ControlOutcome.Failed("Координаты вне экрана (${width}x${height}).")
        }
        val path = Path().apply { moveTo(x.toFloat(), y.toFloat()) }
        val ok = dispatchGesture(service, path, 60L)
        if (ok) {
            ControlOutcome.Done(JSONObject().put("tap_x", x).put("tap_y", y).toString())
        } else {
            ControlOutcome.Failed("Тап не выполнен.")
        }
    }

    /** Вставляет текст в активное поле ввода (без клавиатуры). */
    suspend fun typeIntoFocusedField(text: String): ControlOutcome = onService { service ->
        val ok = withContext(Dispatchers.Main) {
            val root = service.rootInActiveWindow ?: return@withContext false
            val focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
                ?: findFirstEditable(root)
            focused?.let { node ->
                val args = android.os.Bundle().apply {
                    putCharSequence(
                        AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                        text,
                    )
                }
                node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
            } ?: false
        }
        if (ok) {
            ControlOutcome.Done(JSONObject().put("typed", text).toString())
        } else {
            ControlOutcome.Failed("Не нашла активное поле ввода.")
        }
    }

    // ══════════════════════════════════════════════════════════════════
    //  Скриншот
    // ══════════════════════════════════════════════════════════════════

    /** Делает скриншот активного окна и сохраняет PNG в кэш приложения. */
    suspend fun takeScreenshot(): ControlOutcome {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return ControlOutcome.Failed("Скриншоты через специальный доступ доступны с Android 11.")
        }
        return onService { service ->
            val result = withTimeoutOrNull(SCREENSHOT_TIMEOUT_MS) { captureScreenshot(service) }
                ?: ControlOutcome.Failed("Скриншот не успел сохраниться.")
            result
        }
    }

    private suspend fun captureScreenshot(
        service: AmaliaAccessibilityService,
    ): ControlOutcome = suspendCancellableCoroutine { continuation ->
        val callback = object : AccessibilityService.TakeScreenshotCallback {
            override fun onSuccess(screenshot: AccessibilityService.ScreenshotResult) {
                val outcome = runCatching { saveScreenshot(screenshot) }
                    .getOrElse { ControlOutcome.Failed("Не удалось сохранить снимок: ${it.message}") }
                if (continuation.isActive) continuation.resume(outcome)
            }

            override fun onFailure(errorCode: Int) {
                if (continuation.isActive) {
                    continuation.resume(ControlOutcome.Failed("Скриншот отклонён системой (код $errorCode)."))
                }
            }
        }
        val started = runCatching {
            service.takeScreenshot(
                android.view.Display.DEFAULT_DISPLAY,
                context.mainExecutor,
                callback,
            )
        }
        if (started.isFailure && continuation.isActive) {
            continuation.resume(ControlOutcome.Failed("Скриншот недоступен на этом устройстве."))
        }
    }

    private fun saveScreenshot(
        screenshot: AccessibilityService.ScreenshotResult,
    ): ControlOutcome {
        val hardware = screenshot.hardwareBuffer
        val wrapped = Bitmap.wrapHardwareBuffer(hardware, screenshot.colorSpace)
        val bitmap = wrapped?.copy(Bitmap.Config.ARGB_8888, false)
        hardware.close()
        wrapped?.recycle()
        if (bitmap == null) return ControlOutcome.Failed("Пустой снимок экрана.")

        val dir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: context.filesDir
        if (!dir.exists()) dir.mkdirs()
        val file = File(dir, "amalia_screenshot_${System.currentTimeMillis()}.png")
        file.outputStream().use { out -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, out) }
        bitmap.recycle()

        return ControlOutcome.Done(
            JSONObject()
                .put("saved_to", file.absolutePath)
                .put("uri", Uri.fromFile(file).toString())
                .put("size_kb", file.length() / 1024)
                .toString(),
        )
    }

    // ══════════════════════════════════════════════════════════════════
    //  Служебное
    // ══════════════════════════════════════════════════════════════════

    /** Открывает системный экран включения специальных возможностей. */
    fun openAccessibilitySettings() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    /**
     * Общая обёртка: проверяет доступность сервиса и выполняет [block]
     * в главном потоке там, где это требуется системным API.
     */
    private suspend fun onService(
        block: suspend (AmaliaAccessibilityService) -> ControlOutcome,
    ): ControlOutcome {
        val current = service
            ?: return ControlOutcome.ServiceDisabled
        return block(current)
    }

    private suspend fun dispatchGesture(
        service: AmaliaAccessibilityService,
        path: Path,
        durationMs: Long,
    ): Boolean = withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { continuation ->
            val stroke = android.accessibilityservice.GestureDescription.StrokeDescription(
                path,
                0L,
                durationMs,
            )
            val gesture = android.accessibilityservice.GestureDescription.Builder()
                .addStroke(stroke)
                .build()
            val callback = object : AccessibilityService.GestureResultCallback() {
                override fun onCompleted(gestureDescription: android.accessibilityservice.GestureDescription?) {
                    if (continuation.isActive) continuation.resume(true)
                }

                override fun onCancelled(gestureDescription: android.accessibilityservice.GestureDescription?) {
                    if (continuation.isActive) continuation.resume(false)
                }
            }
            val accepted = runCatching { service.dispatchGesture(gesture, callback, null) }
                .getOrDefault(false)
            if (!accepted && continuation.isActive) continuation.resume(false)
        }
    }

    private fun screenSize(): Pair<Int, Int> {
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = windowManager.maximumWindowMetrics.bounds
            bounds.width() to bounds.height()
        } else {
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getRealMetrics(metrics)
            metrics.widthPixels to metrics.heightPixels
        }
    }

    /** Ищет узел, лучше всего подходящий под [query]. */
    private fun findBestNode(root: AccessibilityNodeInfo, query: String): AccessibilityNodeInfo? {
        val needle = query.trim().lowercase()
        if (needle.isEmpty()) return null
        val candidates = mutableListOf<ScoredNode>()
        collectCandidates(root, needle, candidates, depth = 0)
        return candidates
            .sortedWith(compareByDescending<ScoredNode> { it.score }.thenBy { it.depth })
            .firstOrNull()
            ?.node
    }

    private fun collectCandidates(
        node: AccessibilityNodeInfo,
        needle: String,
        out: MutableList<ScoredNode>,
        depth: Int,
    ) {
        if (depth > MAX_TREE_DEPTH) return
        val text = node.text?.toString()?.lowercase().orEmpty()
        val description = node.contentDescription?.toString()?.lowercase().orEmpty()
        val viewId = node.viewIdResourceName?.lowercase().orEmpty()
        val score = scoreMatch(needle, text, description, viewId)
        if (score > 0) out += ScoredNode(node, score, depth)
        for (index in 0 until node.childCount) {
            val child = node.getChild(index) ?: continue
            collectCandidates(child, needle, out, depth + 1)
        }
    }

    /**
     * Подбирает «похожие» подписи, чтобы модель понимала, что вообще есть
     * на экране, когда её запрос не нашёл точное совпадение.
     */
    private fun collectSimilarLabels(
        root: AccessibilityNodeInfo,
        needle: String,
        max: Int,
    ): List<String> {
        if (needle.isEmpty()) return emptyList()
        val out = mutableListOf<String>()
        walkLabels(root, needle, out, depth = 0)
        return out
            .sortedByDescending { similarity(it, needle) }
            .take(max)
    }

    private fun walkLabels(
        node: AccessibilityNodeInfo,
        needle: String,
        out: MutableList<String>,
        depth: Int,
    ) {
        if (depth > MAX_TREE_DEPTH || out.size >= 32) return
        val label = listOf(
            node.text?.toString().orEmpty(),
            node.contentDescription?.toString().orEmpty(),
        ).firstOrNull { it.isNotBlank() }.orEmpty()
        if (label.isNotBlank() &&
            (label.lowercase().contains(needle.take(3)) || similarity(label.lowercase(), needle) > 0.45)
        ) {
            out += label.take(40)
        }
        for (index in 0 until node.childCount) {
            val child = node.getChild(index) ?: continue
            walkLabels(child, needle, out, depth + 1)
        }
    }

    /** Грубая мера близости строк (Jaccard по биграммам) — 0..1. */
    private fun similarity(a: String, b: String): Double {
        if (a.isEmpty() || b.isEmpty()) return 0.0
        val aSet = (a.windowed(2).toSet() + a.toSet())
        val bSet = (b.windowed(2).toSet() + b.toSet())
        val inter = aSet.intersect(bSet).size
        val union = aSet.union(bSet).size
        return if (union == 0) 0.0 else inter.toDouble() / union.toDouble()
    }

    private fun scoreMatch(needle: String, text: String, description: String, viewId: String): Int {
        fun scoreOf(value: String): Int = when {
            value.isEmpty() -> 0
            value == needle -> 100
            value.startsWith(needle) -> 70
            value.contains(needle) -> 50
            needle.contains(value) && value.length > 3 -> 30
            else -> 0
        }

        val idScore = if (viewId.isNotEmpty() && viewId.endsWith(needle.replace(' ', '_'))) 40 else 0
        return maxOf(scoreOf(text), scoreOf(description), idScore)
    }

    private fun clickableSelfOrAncestor(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var current: AccessibilityNodeInfo? = node
        var hops = 0
        while (current != null && hops < MAX_ANCESTOR_HOPS) {
            if (current.isClickable && current.isEnabled) return current
            current = current.parent
            hops++
        }
        return null
    }

    private fun findFirstEditable(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (root.isEditable) return root
        for (index in 0 until root.childCount) {
            val child = root.getChild(index) ?: continue
            val found = findFirstEditable(child)
            if (found != null) return found
        }
        return null
    }

    private fun AccessibilityNodeInfo.describe(): String =
        (text?.toString() ?: contentDescription?.toString() ?: viewIdResourceName ?: className?.toString())
            ?.take(80)
            .orEmpty()
            .ifEmpty { "элемент" }

    private class ScoredNode(
        val node: AccessibilityNodeInfo,
        val score: Int,
        val depth: Int,
    )

    private sealed interface TapAttempt {
        data class Tapped(val label: String, val bounds: Rect) : TapAttempt
        data class Rejected(val label: String) : TapAttempt
        data class NotFound(val candidates: List<String>) : TapAttempt
        data object NoWindow : TapAttempt
    }
}

/** Результат операции специального доступа. */
sealed interface ControlOutcome {
    /** Успех; [json] — машинно-читаемый ответ для модели. */
    data class Done(val json: String) : ControlOutcome

    /** Операция не удалась по понятной причине. */
    data class Failed(val reason: String) : ControlOutcome

    /** Специальный доступ выключен — действие в принципе недоступно. */
    data object ServiceDisabled : ControlOutcome
}

/** Глобальные системные действия, доступные модели. */
enum class GlobalAction(val toolKey: String, val title: String) {
    BACK("back", "Назад"),
    HOME("home", "Домой"),
    RECENTS("recents", "Недавние приложения"),
    NOTIFICATIONS("notifications", "Шторка уведомлений"),
    QUICK_SETTINGS("quick_settings", "Быстрые настройки"),
    LOCK_SCREEN("lock_screen", "Заблокировать экран"),
    SCREENSHOT("screenshot", "Скриншот"),
    ;

    /**
     * Код [AccessibilityService] для действия.
     *
     * Используем именно базовый класс [AccessibilityService] — Kotlin не
     * подхватывает Java-static поля через подклассы (`MyService.GLOBAL_ACTION_BACK`
     * указывает на nested-класс, а не на родительскую константу).
     */
    fun systemCode(): Int = when (this) {
        BACK -> AccessibilityService.GLOBAL_ACTION_BACK
        HOME -> AccessibilityService.GLOBAL_ACTION_HOME
        RECENTS -> AccessibilityService.GLOBAL_ACTION_RECENTS
        NOTIFICATIONS -> AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS
        QUICK_SETTINGS -> AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS
        LOCK_SCREEN -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN
        } else {
            -1
        }

        SCREENSHOT -> -1 // обрабатывается отдельным API, не глобальным действием
    }

    companion object {
        fun fromToolKey(key: String?): GlobalAction? =
            entries.firstOrNull { it.toolKey == key?.trim()?.lowercase() }

        val toolKeys: List<String> get() = entries.map { it.toolKey }
    }
}

/** Направление свайпа. */
enum class SwipeDirection(val toolKey: String) {
    UP("up"),
    DOWN("down"),
    LEFT("left"),
    RIGHT("right"),
    ;

    companion object {
        fun fromToolKey(key: String?): SwipeDirection? =
            entries.firstOrNull { it.toolKey == key?.trim()?.lowercase() }

        val toolKeys: List<String> get() = entries.map { it.toolKey }
    }
}

/**
 * Структурированный снимок активного окна.
 *
 * Модель получает и «плоский» текст (чтобы понять контекст), и список
 * интерактивных элементов (чтобы выбрать цель для нажатия).
 */
data class ScreenSnapshot(
    val packageName: String,
    val windowTitle: String,
    val elements: List<UiElement>,
    val fullText: String,
) {

    fun toJson(): JSONObject = JSONObject().apply {
        put("package", packageName)
        put("window", windowTitle)
        val arr = JSONArray()
        elements.forEach { element -> arr.put(element.toJson()) }
        put("interactive_elements", arr)
        put("text", fullText)
        put("element_count", elements.size)
    }

    companion object {
        private const val MAX_TEXT_LENGTH = 3_500

        /** Обходит дерево узлов и собирает снимок. */
        fun collect(
            root: AccessibilityNodeInfo,
            fallbackPackage: String?,
            maxElements: Int,
        ): ScreenSnapshot {
            val elements = mutableListOf<UiElement>()
            val textBuilder = StringBuilder()
            var windowTitle = ""
            var packageName = fallbackPackage.orEmpty()

            fun walk(node: AccessibilityNodeInfo, depth: Int) {
                if (depth > 40) return
                node.text?.let { value ->
                    if (value.isNotBlank()) {
                        if (textBuilder.isNotEmpty()) textBuilder.append('\n')
                        textBuilder.append(value)
                    }
                }
                node.contentDescription?.let { value ->
                    if (value.isNotBlank() && node.text.isNullOrBlank()) {
                        if (textBuilder.isNotEmpty()) textBuilder.append('\n')
                        textBuilder.append(value)
                    }
                }
                if (windowTitle.isEmpty()) {
                    val title = node.text?.toString()?.takeIf { it.isNotBlank() && depth <= 3 }
                    if (title != null) windowTitle = title
                }

                val interactive = node.isClickable || node.isLongClickable ||
                    node.isScrollable || node.isEditable
                if (interactive && elements.size < maxElements) {
                    val label = node.text?.toString()
                        ?: node.contentDescription?.toString()
                        ?: node.viewIdResourceName
                        ?: ""
                    if (label.isNotBlank()) {
                        val bounds = Rect().also { node.getBoundsInScreen(it) }
                        elements += UiElement(
                            label = label.take(60),
                            viewId = node.viewIdResourceName.orEmpty(),
                            clickable = node.isClickable,
                            longClickable = node.isLongClickable,
                            scrollable = node.isScrollable,
                            editable = node.isEditable,
                            checked = node.isChecked,
                            bounds = "${bounds.left},${bounds.top},${bounds.right},${bounds.bottom}",
                        )
                    }
                }
                for (index in 0 until node.childCount) {
                    val child = node.getChild(index) ?: continue
                    walk(child, depth + 1)
                }
            }

            walk(root, 0)
            if (packageName.isEmpty()) {
                packageName = AmaliaAccessibilityService.lastWindowPackage.orEmpty()
            }
            return ScreenSnapshot(
                packageName = packageName,
                windowTitle = windowTitle,
                elements = elements,
                fullText = textBuilder.toString().take(MAX_TEXT_LENGTH),
            )
        }
    }
}

/** Один интерактивный элемент снимка экрана. */
data class UiElement(
    val label: String,
    val viewId: String,
    val clickable: Boolean,
    val longClickable: Boolean,
    val scrollable: Boolean,
    val editable: Boolean,
    val checked: Boolean,
    val bounds: String,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("label", label)
        if (viewId.isNotEmpty()) put("id", viewId)
        put("clickable", clickable)
        if (longClickable) put("long_clickable", true)
        if (scrollable) put("scrollable", true)
        if (editable) put("editable", true)
        if (checked) put("checked", true)
        put("bounds", bounds)
    }
}

private const val MAX_TREE_DEPTH = 40
private const val MAX_ANCESTOR_HOPS = 6
private const val SCREENSHOT_TIMEOUT_MS = 4_000L
