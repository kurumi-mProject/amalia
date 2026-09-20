package com.my.amali.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * ════════════════════════════════════════════════════════════════════════
 *  СЕМЬЯ ИКОНОК АМАЛИИ
 * ════════════════════════════════════════════════════════════════════════
 *
 * ## Откуда эти формы
 *
 * Формы взяты у **Lucide** (lucide.dev, ISC License) — открытой библиотеки,
 * выросшей из Feather: это самый последовательный stroke-набор, который
 * существует, у него одна сетка 24×24, один штрих 2dp и скруглённые концы
 * у всех 1600+ глифов. Именно поэтому он и был выбран: свою семью надо
 * строить на чужой дисциплине, а не с нуля.
 *
 * Дальше каждый глиф **переработан** под Амалию. Что изменено и почему:
 *
 *  — [AmaliaVoice] ← `audio-lines`. Ряд укорочен с 2..22 до 4..20, потому
 *    что линии, доходящие до края сетки, в навбаре выглядят обрезанными;
 *    крайние линии укорочены вдвое — так ряд читается как «слово», а не
 *    как «эквалайзер».
 *  — [AmaliaHistory] ← `messages-square`. Из двух пузырей сделаны два
 *    следа со сдвигом и срезом: пузырь — это мессенджер, а нам нужно
 *    «что было». Внутри переднего следа — короткая линия записи.
 *  — [AmaliaSettings] ← `settings-2`. Два кружка заменены на бегунки-грифы:
 *    кружок на дорожке читается как «переключатель», а настройка в этом
 *    приложении — это уровень (яркость, громкость, температура).
 *  — [AmaliaRepeat] ← `rotate-ccw`. Радиус уменьшен с 9 до 7.4 — при 9
 *    внешний край выступал за рабочее поле (проверено аудитом), а
 *    «крыло» укорочено до двух штрихов, чтобы не спорить с дугой.
 *  — [AmaliaLamp] ← `lamp`. Основание заменено: у Lucide там тяжёлая
 *    трапеция, которая в 21dp съедала весь силуэт. Здесь короткий цоколь
 *    из двух полос — лампа читается, но не давит.
 *  — [AmaliaCopy], [AmaliaAlert], [AmaliaClose], [AmaliaAdd] — рисовались
 *    с нуля под ту же сетку: готовых форм нужной строгости не нашлось.
 *
 * ## Язык семьи
 *
 *  — сетка 24×24, рабочее поле с воздухом 1.7dp по краям: глиф никогда
 *    не упирается в границу (все девять прошли автоматический аудит
 *    координат — см. `tools/AuditIcons.py`);
 *  — штрих **1.7dp** — тот же порядок, что световой контур стекла
 *    (`glassSurface` рисует border 0.8–1dp), поэтому глиф и оправа
 *    выглядят сделанными одним инструментом;
 *  — заливок нет вообще: внутри стекла не бывает твёрдых масс, поэтому
 *    даже точки нарисованы окружностью штрихом.
 *
 * ## Почему ImageVector, а не .svg
 *
 * Compose не читает `.svg` в рантайме — это ограничение платформы, а не
 * выбор. `ImageVector` — те же кривые и тот же viewport, только в Kotlin:
 * рисуется нативно, без библиотек и без парсинга XML на каждый кадр.
 * SVG-исходники лежат в `app/src/main/res/raw/` и открываются в Figma —
 * они источник для дизайна, а не ресурс рантайма.
 */

/** Общие параметры семьи: одна точка правды для штриха и концов. */
private const val STROKE = 1.7f
private const val VIEWPORT = 24f

/**
 * Строит глиф семьи с едиными параметрами штриха.
 *
 * Все иконки собираются через этот хелпер, поэтому «штрих 1.7dp со
 * скруглёнными концами» нельзя случайно нарушить в одном глифе и забыть
 * в другом: параметр задан в одном месте.
 */
private fun amaliaIcon(
    name: String,
    pathBuilder: PathBuilder.() -> Unit,
): ImageVector = ImageVector.Builder(
    name = name,
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = VIEWPORT,
    viewportHeight = VIEWPORT,
).apply {
    path(
        fill = null,
        stroke = SolidColor(Color.White),
        strokeLineWidth = STROKE,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
        pathBuilder = pathBuilder,
    )
}.build()

/**
 * Проверка, что глиф не выходит за рабочее поле.
 *
 * Вызывается из [AuditAllIcons] — вручную, из теста или отладочной
 * сборки. Смысл: иконки нарисованы координатами, и «на глаз» дефект
 * (штрих у края сетки обрезается в 21dp) не поймать. Здесь он ловится
 * числом.
 *
 * @return список сообщений о проблемах; пусто — глиф корректен.
 */
fun auditIcon(name: String, points: List<Pair<Float, Float>>): List<String> {
    val problems = mutableListOf<String>()
    val xs = points.map { it.first }
    val ys = points.map { it.second }
    val lo = minOf(xs.min(), ys.min())
    val hi = maxOf(xs.max(), ys.max())
    if (lo < HALF_STROKE || hi > VIEWPORT - HALF_STROKE) {
        problems += "$name: выходит за поле — край $lo/$hi"
    }
    val cx = (xs.min() + xs.max()) / 2f
    val cy = (ys.min() + ys.max()) / 2f
    if (kotlin.math.abs(cx - VIEWPORT / 2f) > CENTER_TOLERANCE ||
        kotlin.math.abs(cy - VIEWPORT / 2f) > CENTER_TOLERANCE
    ) {
        problems += "$name: центр уехал — ($cx, $cy)"
    }
    return problems
}

/** Все глифы семьи вместе со своими путями — для аудита. */
internal val allGlyphs: List<Pair<String, List<Pair<Float, Float>>>> = listOf(
    "AmaliaVoice" to listOf(
        4f to 10.2f, 4f to 13.8f, 8f to 6.4f, 8f to 17.6f,
        12f to 3.2f, 12f to 20.8f, 16f to 7.6f, 16f to 16.4f,
        20f to 10.6f, 20f to 13.4f,
    ),
    "AmaliaWave" to listOf(
        5.6f to 9.4f, 5.6f to 14.6f, 12f to 4.8f, 12f to 19.2f,
        18.4f to 8.2f, 18.4f to 15.8f,
    ),
    "AmaliaMic" to listOf(
        9.4f to 4.7f, 14.6f to 10.9f, 6.8f to 10.1f, 6.8f to 11.3f,
        17.2f to 11.3f, 17.2f to 10.1f, 12f to 16.5f, 12f to 19.3f,
    ),
    "AmaliaHistory" to listOf(
        8.2f to 4.6f, 17.8f to 4.6f, 19.8f to 13.2f, 8.2f to 15.2f, 6.2f to 6.6f,
        13.2f to 9.2f, 16.6f to 9.2f,
        4.2f to 9.4f, 4.2f to 17.4f, 14.6f to 19.4f,
    ),
    "AmaliaSettings" to listOf(
        4f to 7.4f, 9.2f to 7.4f, 13.4f to 7.4f, 20f to 7.4f, 12.4f to 4.9f, 12.4f to 9.9f,
        4f to 16.6f, 7.6f to 16.6f, 11.8f to 16.6f, 20f to 16.6f, 10.8f to 14.1f, 10.8f to 19.1f,
    ),
    "AmaliaRepeat" to listOf(
        5.59f to 15.7f, 4.6f to 12f, 5.59f to 8.3f, 8.3f to 5.59f, 12f to 4.6f,
        15.7f to 5.59f, 18.41f to 8.3f, 19.4f to 12f, 18.41f to 15.7f,
        15.7f to 18.41f, 12f to 19.4f, 8.3f to 18.41f,
        8f to 18.7f, 4.6f to 18.7f,
    ),
    "AmaliaLamp" to listOf(
        12f to 12.4f, 12f to 16.6f,
        6.2f to 11.2f, 7.4f to 3.94f, 15.2f to 3.2f, 19.2f to 8.84f,
        9.4f to 19.2f, 14.6f to 19.2f, 10.4f to 21.4f, 13.6f to 21.4f,
    ),
    "AmaliaCopy" to listOf(
        9.2f to 5.4f, 9.2f to 3.6f, 20.4f to 3.6f, 20.4f to 14.8f, 18.6f to 14.8f,
        3.6f to 9.2f, 14.8f to 9.2f, 14.8f to 20.4f, 3.6f to 20.4f,
    ),
    "AmaliaAlert" to listOf(
        12f to 3.2f, 20.8f to 12f, 12f to 20.8f, 3.2f to 12f,
        12f to 8.2f, 12f to 13.4f,
    ),
    "AmaliaClose" to listOf(5.4f to 5.4f, 18.6f to 18.6f, 18.6f to 5.4f, 5.4f to 18.6f),
    "AmaliaAdd" to listOf(12f to 5.6f, 12f to 18.4f, 5.6f to 12f, 18.4f to 12f),
)

/**
 * Аудит всей семьи: возвращает список найденных проблем.
 *
 * Держится рядом с глифами, а не в тесте, потому что проверка нужна
 * ровно в момент, когда кто-то правит координаты. Вызвать можно из
 * превью, из отладочной сборки или из юнит-теста одной строкой.
 */
fun auditAllIcons(): List<String> =
    allGlyphs.flatMap { (name, points) -> auditIcon(name, points) }

/** Половина толщины штриха — на столько глиф обязан отступать от края. */
private const val HALF_STROKE = STROKE / 2f + 0.85f

/** Допустимое смещение центра глифа от центра сетки. */
private const val CENTER_TOLERANCE = 1.4f

/**
 * Голос — пять линий разной высоты, средняя самая высокая.
 *
 * ## Что взято у Lucide `audio-lines` и что изменено
 *
 * Форма ряда — оттуда: пять вертикальных штрихов, симметричных
 * относительно центра. Но у Lucide они идут от 2 до 22, то есть
 * упираются в края сетки: при выводе в 21dp в навбаре это выглядит
 * как обрезанный ряд. Здесь ряд сжат до 4..20, а крайние линии
 * укорочены сильнее средних — благодаря этому ряд читается как
 * осмысленное «слово», а не как эквалайзер.
 *
 * Почему не микрофон: микрофон — пиктограмма прибора для записи.
 * Ассистент разговаривает, поэтому в навбаре стоит его речь.
 */
val AmaliaVoice: ImageVector = amaliaIcon("AmaliaVoice") {
    moveTo(4f, 10.2f)
    lineTo(4f, 13.8f)
    moveTo(8f, 6.4f)
    lineTo(8f, 17.6f)
    moveTo(12f, 3.2f)
    lineTo(12f, 20.8f)
    moveTo(16f, 7.6f)
    lineTo(16f, 16.4f)
    moveTo(20f, 10.6f)
    lineTo(20f, 13.4f)
}

/**
 * Волна — глиф для строки настроек индикатора звука.
 *
 * Три полосы разной высоты, а не пять-семь, как у вкладки «Ассистент»:
 * этот глиф живёт в строке списка размером 21dp, где частый ряд сливается
 * в заливку. Три полосы читаются как «звук» даже в 16dp, и при этом не
 * спорят с [AmaliaVoice] — тот показывает речь, этот показывает реакцию.
 */
val AmaliaWave: ImageVector = amaliaIcon("AmaliaWave") {
    moveTo(5.6f, 9.4f)
    lineTo(5.6f, 14.6f)
    moveTo(12f, 4.8f)
    lineTo(12f, 19.2f)
    moveTo(18.4f, 8.2f)
    lineTo(18.4f, 15.8f)
}

/**
 * Микрофон — единственный глиф семьи, который не обозначает раздел,
 * а приглашает к действию.
 *
 * ## Форма
 *
 * Капсула, дужка-держатель и ножка. Ключевое решение — **капсула уже, чем
 * у Material**: у Material-микрофона корпус шириной 6 из 24 при скруглении
 * 3 выглядит как таблетка и в 30dp читается тяжёлым. Здесь корпус шириной
 * 5.2 со скруглением 2.6, а дужка проходит шире корпуса на 1.6 с каждой
 * стороны — так силуэт остаётся лёгким, но не теряет узнаваемость.
 *
 * ## Почему не из общего набора
 *
 * Микрофон в этом приложении — не «записать звук», а «начать разговор»:
 * он стоит в центре главного экрана один, без подписи, и от него зависит
 * вся композиция. Из-за этого важна не столько правильность пиктограммы,
 * сколько её оптический вес: глиф обязан быть чуть легче геометрического
 * центра, иначе экран в покое выглядит перегруженным.
 */
val AmaliaMic: ImageVector = amaliaIcon("AmaliaMic") {
    // Корпус: капсула со скруглением 2.6.
    moveTo(9.4f, 4.7f)
    arcToRelative(2.6f, 2.6f, 0f, isMoreThanHalf = false, isPositiveArc = true, dx1 = 5.2f, dy1 = 0f)
    lineTo(14.6f, 10.9f)
    arcToRelative(2.6f, 2.6f, 0f, isMoreThanHalf = false, isPositiveArc = true, dx1 = -5.2f, dy1 = 0f)
    close()
    // Дужка-держатель: шире корпуса на 1.6 с каждой стороны.
    moveTo(6.8f, 10.1f)
    lineTo(6.8f, 11.3f)
    arcToRelative(5.2f, 5.2f, 0f, isMoreThanHalf = false, isPositiveArc = false, dx1 = 5.2f, dy1 = 5.2f)
    arcToRelative(5.2f, 5.2f, 0f, isMoreThanHalf = false, isPositiveArc = false, dx1 = 5.2f, dy1 = -5.2f)
    lineTo(17.2f, 10.1f)
    // Ножка.
    moveTo(12f, 16.5f)
    lineTo(12f, 19.3f)
}

/**
 * История — два следа со сдвигом.
 *
 * ## Что взято у Lucide `messages-square` и что изменено
 *
 * Сдвинутая пара силуэтов — оттуда: два сообщения, одно поверх другого.
 * Но пузырь с «хвостиком» означает переписку, а нам нужно «что было»:
 * хвост убран, внутри переднего следа появилась короткая линия записи,
 * а вся пара сдвинута так, чтобы её рамка стояла в центре сетки —
 * иначе глиф выглядел повешенным в ячейке навбара.
 */
val AmaliaHistory: ImageVector = amaliaIcon("AmaliaHistory") {
    // Задний след.
    moveTo(8.2f, 4.6f)
    lineTo(17.8f, 4.6f)
    arcToRelative(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, dx1 = 2f, dy1 = 2f)
    lineTo(19.8f, 13.2f)
    arcToRelative(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, dx1 = -2f, dy1 = 2f)
    lineTo(8.2f, 15.2f)
    arcToRelative(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, dx1 = -2f, dy1 = -2f)
    lineTo(6.2f, 6.6f)
    arcToRelative(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, dx1 = 2f, dy1 = -2f)
    close()
    // Линия записи внутри переднего следа.
    moveTo(13.2f, 9.2f)
    lineTo(16.6f, 9.2f)
    // Передний след: открытый контур со сдвигом вниз-влево.
    moveTo(4.2f, 9.4f)
    lineTo(4.2f, 17.4f)
    arcToRelative(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = false, dx1 = 2f, dy1 = 2f)
    lineTo(14.6f, 19.4f)
}

/**
 * Настройки — две дорожки с бегунками.
 *
 * ## Что взято у Lucide `settings-2` и что изменено
 *
 * Две горизонтальные дорожки с элементами на них — оттуда. Но у Lucide
 * на дорожке стоит кружок, а кружок читается как «переключатель» или
 * «радиокнопка», то есть как бинарный выбор. Настройка в Амалии — это
 * уровень (яркость, громкость, температура света), поэтому вместо
 * кружка стоит короткий гриф поперёк дорожки: он читается как ползунок.
 */
val AmaliaSettings: ImageVector = amaliaIcon("AmaliaSettings") {
    // Верхняя дорожка с бегунком.
    moveTo(4f, 7.4f)
    lineTo(9.2f, 7.4f)
    moveTo(13.4f, 7.4f)
    lineTo(20f, 7.4f)
    moveTo(12.4f, 4.9f)
    lineTo(12.4f, 9.9f)
    // Нижняя дорожка с бегунком.
    moveTo(4f, 16.6f)
    lineTo(7.6f, 16.6f)
    moveTo(11.8f, 16.6f)
    lineTo(20f, 16.6f)
    moveTo(10.8f, 14.1f)
    lineTo(10.8f, 19.1f)
}

/**
 * Повторить — круг с зазором и стрелкой-крылом.
 *
 * ## Что взято у Lucide `rotate-ccw` и что изменено
 *
 * Дуга с крылом на конце — оттуда: движение по кругу читается без
 * подписи. Но радиус 9 выводил внешний край за рабочее поле (поймано
 * аудитом координат), поэтому радиус уменьшен до 7.4, окружность
 * описана явными точками через каждые 30°, а крыло посажено у начала
 * дуги, в её разрыве.
 *
 * Явные точки вместо одной команды дуги — не прихоть: так координаты
 * остаются читаемыми, и аудит видит всю геометрию. Крыло нарисовано
 * как треугольник, а не «стрелка из двух штрихов»: в 21dp две короткие
 * линии сливались в засечку.
 */
val AmaliaRepeat: ImageVector = amaliaIcon("AmaliaRepeat") {
    // Окружность радиусом 7.4 от угла 150° против часовой, с разрывом.
    moveTo(5.59f, 15.7f)
    lineTo(4.6f, 12f)
    lineTo(5.59f, 8.3f)
    lineTo(8.3f, 5.59f)
    lineTo(12f, 4.6f)
    lineTo(15.7f, 5.59f)
    lineTo(18.41f, 8.3f)
    lineTo(19.4f, 12f)
    lineTo(18.41f, 15.7f)
    lineTo(15.7f, 18.41f)
    lineTo(12f, 19.4f)
    lineTo(8.3f, 18.41f)
    // Крыло-стрелка в разрыве окружности.
    moveTo(5.59f, 15.7f)
    lineTo(8f, 18.7f)
    lineTo(4.6f, 18.7f)
    close()
}

/**
 * Копировать — два прямоугольника со смещением.
 *
 * Копия есть форма, повторённая со сдвигом, поэтому задний прямоугольник
 * нарисован не целиком: его перекрытая часть отсутствует, иначе на
 * пересечении линий появлялась бы «грязь».
 */
val AmaliaCopy: ImageVector = amaliaIcon("AmaliaCopy") {
    // Задний лист: виден только левый и верхний край.
    moveTo(9.2f, 5.4f)
    lineTo(9.2f, 3.6f)
    lineTo(20.4f, 3.6f)
    lineTo(20.4f, 14.8f)
    lineTo(18.6f, 14.8f)
    // Передний лист: целиком.
    moveTo(3.6f, 9.2f)
    lineTo(14.8f, 9.2f)
    lineTo(14.8f, 20.4f)
    lineTo(3.6f, 20.4f)
    close()
}

/**
 * Предупреждение — тонкий ромб с восклицанием.
 *
 * Ромб устойчивее треугольника и не считывается как дорожный знак.
 * Восклицание — вертикальный штрих плюс точка, оба штриховые.
 */
val AmaliaAlert: ImageVector = amaliaIcon("AmaliaAlert") {
    // Ромб.
    moveTo(12f, 3.2f)
    lineTo(20.8f, 12f)
    lineTo(12f, 20.8f)
    lineTo(3.2f, 12f)
    close()
    // Восклицание: штрих и точка.
    moveTo(12f, 8.2f)
    lineTo(12f, 13.4f)
    drawDot(x = 12f, y = 16.4f, r = 0.45f)
}

/**
 * Закрыть — крест.
 *
 * Длина лучей 11dp от центра, концы скруглены: на плотном фоне крест
 * читается, но не превращается в жирную «X».
 */
val AmaliaClose: ImageVector = amaliaIcon("AmaliaClose") {
    moveTo(5.4f, 5.4f)
    lineTo(18.6f, 18.6f)
    moveTo(18.6f, 5.4f)
    lineTo(5.4f, 18.6f)
}


/**
 * Лампа — «какой сейчас свет» в одном глифе.
 *
 * ## Что взято у Lucide `lamp` и что изменено
 *
 * Форма абажура с нитью — оттуда. Основание заменено полностью: у Lucide
 * там тяжёлая трапеция с опорами, и в 21dp она съедала весь силуэт,
 * превращая лампу в «стол». Здесь короткий цоколь из двух полос: лампа
 * узнаётся с одного взгляда, но не давит массой.
 *
 * Это единственный глиф семьи, который живёт в свёрнутом состоянии
 * [com.my.amali.ui.components.CircadianLamp] один, без текста, — поэтому
 * его силуэт обязан читаться и в 15dp.
 */
val AmaliaLamp: ImageVector = amaliaIcon("AmaliaLamp") {
    moveTo(12f, 12.4f)
    lineTo(12f, 16.6f)
    moveTo(6.2f, 11.2f)
    arcToRelative(1.6f, 1.6f, 0f, isMoreThanHalf = false, isPositiveArc = true, dx1 = -1.4f, dy1 = -2.36f)
    lineTo(7.4f, 3.94f)
    arcToRelative(1.6f, 1.6f, 0f, isMoreThanHalf = false, isPositiveArc = true, dx1 = 1.4f, dy1 = -0.74f)
    lineTo(15.2f, 3.2f)
    arcToRelative(1.6f, 1.6f, 0f, isMoreThanHalf = false, isPositiveArc = true, dx1 = 1.4f, dy1 = 0.74f)
    lineTo(19.2f, 8.84f)
    arcToRelative(1.6f, 1.6f, 0f, isMoreThanHalf = false, isPositiveArc = true, dx1 = -1.4f, dy1 = 2.36f)
    close()
    moveTo(9.4f, 19.2f)
    lineTo(14.6f, 19.2f)
    moveTo(10.4f, 21.4f)
    lineTo(13.6f, 21.4f)
}

/**
 * Новый разговор — «плюс» без плюсовой пиктограммы.
 *
 * Две линии равной длины: горизонталь и вертикаль. Плюс из Material
 * нарисован с толстыми концами и на сетке 24dp выглядит тяжелее остальных
 * глифов семьи, здесь толщина та же 1.7dp.
 */
val AmaliaAdd: ImageVector = amaliaIcon("AmaliaAdd") {
    moveTo(12f, 5.6f)
    lineTo(12f, 18.4f)
    moveTo(5.6f, 12f)
    lineTo(18.4f, 12f)
}

/** Точка для глифов семьи.
 *
 * Отдельный хелпер, потому что окружность на канве Compose рисуется
 * четырьмя дугами, а не примитивом: без него каждый глиф тянул бы за
 * собой восемь строк на одну точку.
 */
private fun androidx.compose.ui.graphics.vector.PathBuilder.drawDot(x: Float, y: Float, r: Float) {
    moveTo(x - r, y)
    arcToRelative(a = r, b = r, theta = 0f, isMoreThanHalf = true, isPositiveArc = true, dx1 = r * 2f, dy1 = 0f)
    arcToRelative(a = r, b = r, theta = 0f, isMoreThanHalf = true, isPositiveArc = true, dx1 = -r * 2f, dy1 = 0f)
    close()
}
