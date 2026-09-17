package com.my.amali.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import java.util.Calendar
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * ════════════════════════════════════════════════════════════════════════
 *  CIRCADIAN LIGHT ENGINE
 *  Свет, который не мешает глазу и не ломает сон.
 * ════════════════════════════════════════════════════════════════════════
 *
 * ## Почему это отдельный слой, а не «палитра на 4 времени суток»
 *
 * Прошлая версия адаптации была сломана концептуально, а не косметически:
 * палитра выбиралась грубым `when (hour)` по четырём интервалам и **мгновенно
 * переключалась** на границе часа. Это давало два дефекта:
 *
 *  1. **Скачок в 00:00 / 06:00 / 12:00 / 18:00.** Экран перекрашивался рывком,
 *     а вместе с ним рывком менялись акценты, тени и волна — глаз воспринимает
 *     это как вспышку, а не как смену времени суток.
 *  2. **Отсутствие непрерывности по биологии.** Мелатонин подавляется не
 *     «после 23:00», а пропорционально melanopic-составляющей света, которая
 *     нарастает и спадает плавно вместе с высотой солнца.
 *
 * Поэтому здесь строится **непрерывная модель**: время → один скаляр
 * `solarPhase` → цветовая температура (CCT), melanopic-вклад (mel-DER),
 * циркадный стимул (CS) и целевая яркость экрана. Всё, что видит
 * пользователь (фон, акценты, тени, мотивы), выводится из этих величин,
 * а не из четырёх констант.
 *
 * ## Научная основа (что именно заложено в формулы)
 *
 * — **ipRGC / меланопсин.** Циркадная система управляется
 *   светочувствительными ганглиозными клетками сетчатки с максимумом
 *   чувствительности ≈ 480 нм (синий). Именно они, а не колбочки, определяют
 *   подавление мелатонина.
 *
 * — **mel-DER (melanopic Daylight Efficacy Ratio).** Отношение меланопической
 *   световой эффективности источника к стандартному дневному свету D65.
 *   Ключевой факт (Nature Scientific Reports, s41598-022-21755-7): mel-DER
 *   **монотонно растёт с CCT**. Для чёрного тела при 3500 K mel-DER = 0.62.
 *   Именно поэтому «тёплый вечер» — не эстетика, а физика: 2200–2700 K дают
 *   в разы меньший меланопический вклад, чем 6500 K.
 *
 * — **CS (Circadian Stimulus).** Модель Rea et al., связывающая освещённость
 *   и спектр с фактическим подавлением мелатонина. Обзор метрик
 *   (tecolite.com/circadian-lighting-metrics, 2025) прямо формулирует то, что
 *   определяет архитектуру этого файла: **метрика без времени бесполезна**.
 *   Высокий melanopic lux в 14:00 полезен, в 22:00 — вреден. Величина одна,
 *   знак — разный. Поэтому [circadianStimulus] возвращает «полезность»
 *   со знаком: днём это стимул просыпаться, ночью — давление на сон.
 *
 * — **Экранное ограничение по CCT.** Дисплеи обычно попадают в 2700–4000 K,
 *   а вечерний просмотр уходит ещё ниже (circadianshield.com). DIN SPEC
 *   5031-100 и рекомендации UCL (Ticleanu 2023) дают порог **≤ 2700 K после
 *   заката**, что здесь и является тёплой границей ночи.
 *
 * ## Что это даёт глазу (вторая половина задачи)
 *
 * Помимо циркадной части, движок решает проблему **halation**: светлые буквы
 * на тёмном фоне «расплываются» из-за латерального торможения в сетчатке, и
 * это тем хуже, чем больше перепад яркости на границе. Отсюда:
 *
 *  — фон **никогда не чистый чёрный** (#000): минимальная светлота поднята до
 *    [MIN_BG_LIGHTNESS];
 *  — текст **никогда не чистый белый** (#FFF): максимальная светлота ограничена
 *    [MAX_TEXT_LIGHTNESS];
 *  — целевой контраст основного текста — не 4.5:1 (AA), а [TARGET_BODY_CONTRAST]
 *    ≈ 7:1 (AAA), потому что исследования читаемости дают ~+55% требуемого
 *    контраста в тёмной полярности для компенсации halation;
 *  — вторичный текст держит [MIN_SECONDARY_CONTRAST], иначе подписи «плывут»
 *    даже при формально проходящем контрасте;
 *  — акценты проходят проверку [ensureContrast] — их подтягивают к светлому или
 *    тёмному полюсу до достижения минимума, а не оставляют «на глаз».
 *
 * Модуль не знает ничего о Compose-состоянии: это чистые функции над временем
 * и цветом. Благодаря этому один и тот же расчёт используют и тема, и фон, и
 * превью в настройках — фон и интерфейс физически не могут разъехаться.
 */
object CircadianEngine {

    // ══════════════════════════════════════════════════════════════════
    //  ОСНОВНОЙ РАСЧЁТ
    // ══════════════════════════════════════════════════════════════════

    /**
     * Полный световой профиль для текущего момента.
     *
     * @param hour дробный час суток: 14.5 = 14:30. Дробное, а не целое, —
     *   принципиально: CCT должна ехать плавно с минутой, а не прыгать.
     */
    fun profileAt(hour: Float): LightProfile {
        val phase = solarPhase(hour)
        val elevation = solarElevation(hour)
        val cct = cctFor(elevation, hour)
        val melDer = melanopicDer(cct)
        val cs = circadianStimulus(cct, phase, elevation)

        return LightProfile(
            hour = hour,
            solarPhase = phase,
            solarElevation = elevation,
            cct = cct,
            melanopicDer = melDer,
            circadianStimulus = cs,
            recommendDarkUi = phase >= PHASE_HOMEOSTATIC || phase < PHASE_CONSTANT,
            effectiveDark = effectiveDark(phase, elevation),
            displayLuminance = displayLuminanceFor(phase),
        )
    }

    /**
     * Доля суток, прошедшая от локальной полуночи: 0.0 = 00:00, 0.5 = 12:00.
     * Используется как «фаза солнца» — непрерывный аналог времени суток.
     */
    fun phaseOf(hour: Float): Float = (hour / 24f).let { it - it.toInt().toFloat() }

    /**
     * Высота солнца в условных единицах [-1, 1].
     *
     * Физически это не градусы, а безразмерный аналог: +1 в полдень,
     * 0 на восходе/закате, −1 в полночь. Модель намеренно простая
     * (косинус), потому что приложение не знает ни широты, ни сезона, а
     * выдумывать «точную» астрономию без координат — это ложная точность.
     * Косинус даёт ровно то, что нужно: плавный, симметричный, без изломов
     * переход через зенит.
     */
    fun solarElevation(hour: Float): Float {
        val dayAngle = (hour - 12f) / 12f * PI_F
        return cos(dayAngle).coerceIn(-1f, 1f)
    }

    /**
     * Циклическая фаза суток [0, 1).
     *
     * ВНИМАНИЕ: значение **разрывно** на границе суток (в полночь 0.999 → 0).
     * Поэтому она предназначена только для непрерывных по кругу величин
     * (CCT, цвет, яркость). Для величин, где важна непрерывность на разрыве
     * (например, альфа текстурных слоёв), используйте [solarElevation] —
     * он гладкий всюду и не имеет скачка.
     */
    fun solarPhase(hour: Float): Float {
        val h = ((hour % 24f) + 24f) % 24f
        return h / 24f
    }

    /**
     * Цветовая температура «света» интерфейса в кельвинах.
     *
     * ## Непрерывность — обязательное свойство, а не пожелание
     *
     * Первая версия этой функции имела **разрыв в 500 K на 07:00**: ночная
     * ветка возвращала 3200 K (потолок), а утренняя в той же точке считала
     * 3700 K от высоты солнца. Экран получал скачок цвета ровно в семь утра.
     * Формально «по расписанию», фактически — вспышка, то есть тот же дефект,
     * из-за которого предыдущая реализация и считалась сломанной.
     *
     * Теперь переход между режимами идёт через [smoothstep] по **той же**
     * высоте солнца, что и всё остальное: не «до 07:00 одно, после — другое»,
     * а плавное перетекание с полушириной [EDGE_STEP_HALF]. Разрыв исключён
     * по построению — обе ветки совпадают на границе до последнего кельвина.
     *
     * ## Три режима
     *
     *  — **глубокая ночь** (elevation ≤ 0): движение к [CCT_NIGHT] = 2000 K,
     *    ниже порога 2200 K, рекомендованного для вечерних источников;
     *  — **день** (elevation > 0): рост до [CCT_NOON] = 5600 K. Высокая
     *    дневная CCT полезна: melanopic-вклад днём удерживает циркадную
     *    систему в фазе;
     *  — **золотой час** у горизонта: тёплые [CCT_DUSK] = 2600 K.
     */
    fun cctFor(elevation: Float, hour: Float): Int {
        // Непрерывная интерполяция между ночной и дневной кривой.
        val dayFactor = smoothstep(-EDGE_STEP_HALF, EDGE_STEP_HALF, elevation)

        val nightT = smoothstep(0f, 1f, (-elevation).coerceIn(0f, 1f))
        val nightCct = lerpF(CCT_DUSK.toFloat(), CCT_NIGHT.toFloat(), easeOut(nightT))

        val dayT = smoothstep(0f, 1f, elevation.coerceIn(0f, 1f)).pow(DAY_SPREAD_EXP)
        val dayCct = lerpF(CCT_DUSK.toFloat(), CCT_NOON.toFloat(), dayT)

        val base = lerpF(nightCct, dayCct, dayFactor)

        // Потолок кельвинов в вечерние и ранние часы. Применяется через
        // мягкий минимум, а не жёсткий: иначе в момент включения ограничения
        // (в 20:00) снова появлялся бы скол на графике.
        val eveningWeight = eveningCapWeight(hour)
        val capped = softMin(base, CCT_EVENING_CAP.toFloat(), eveningWeight)

        return capped.roundToInt().coerceIn(CCT_MIN, CCT_MAX)
    }

    /**
     * Насколько сильно сейчас действует вечерний/утренний потолок CCT, 0..1.
     *
     * ## Почему ramps такой длинный
     *
     * Первая версия поднимала ограничение за 2 часа (18:00 → 20:00). Но в это
     * же время CCT после заката **падает** с 5600 K до 2600 K, то есть
     * производная самой кривой достигает ~50 K/мин. Наложение ограничения
     * поверх этого движения добавляло ещё ~120 K/мин и снова давало рывок в
     * районе 07:00, когда ограничение так же быстро отпускало.
     *
     * Правило простое: **скорость снятия ограничения не должна превышать
     * скорость естественного движения света**. Ночное окно — 12 часов, ночью
     * CCT меняется медленно, поэтому ограничение снимается за [CAP_RAMP_HOURS]
     * = 4 часа: этого достаточно, чтобы добавочная скорость была ниже
     * собственной скорости кривой, и график остаётся гладким при любой
     * длине суток.
     */
    private fun eveningCapWeight(hour: Float): Float = when {
        hour >= EVENING_CAP_RAMP_START && hour <= EVENING_CAP_FULL -> {
            (hour - EVENING_CAP_RAMP_START) / CAP_RAMP_HOURS
        }
        hour > EVENING_CAP_FULL || hour < MORNING_CAP_START -> 1f
        hour in MORNING_CAP_START..MORNING_CAP_END -> {
            1f - (hour - MORNING_CAP_START) / CAP_RAMP_HOURS
        }
        else -> 0f
    }

    /**
     * Мягкий минимум: возвращает `min(value, ceiling)`, но сглаженный так,
     * что ограничение нарастает пропорционально [weight] и **не даёт излома**
     * ни при `weight = 0`, ни при `weight = 1`.
     *
     * ## Почему обычный минимум здесь не годится
     *
     * Наивное `value + (ceiling - value) * weight` давало скачок 130 K/мин на
     * 18:00: при `weight = 0` результат равен [value] (2600 K), а уже при
     * `weight = 0.008` он прыгал к потолку 3200 K — то есть ограничение
     * «включалось» рывком, ради устранения которого всё и затевалось.
     *
     * Корректная формула — гармонический переход: добавляется поправка,
     * которая равна нулю при `value ≤ ceiling` (ограничивать нечего) и
     * плавно нарастает по мере превышения. Максимальная производная
     * ограничена сверху, поэтому цвет не может «щелкнуть».
     */
    private fun softMin(value: Float, ceiling: Float, weight: Float): Float {
        val w = weight.coerceIn(0f, 1f)
        if (w <= 0f || value <= ceiling) return value
        val excess = value - ceiling
        return value - excess * w * (1f - w * SOFT_MIN_SHARPNESS)
    }

    /**
     * mel-DER — относительная меланопическая эффективность источника
     * по отношению к D65 (CIE S 026).
     *
     * Аппроксимация: mel-DER монотонно растёт с CCT и проходит через
     * опорные точки, известные из литературы — 3500 K → 0.62 (Nature,
     * s41598-022-21755-7), D65 (6500 K) → 1.00 по определению.
     *
     * Степенная зависимость выбрана потому, что она проходит через обе
     * опорные точки и не даёт нефизичных значений на краях диапазона,
     * куда могут уехать вычисления после ограничений CCT.
     *
     * @return mel-DER, где 1.0 = спектр D65.
     */
    fun melanopicDer(cct: Int): Float {
        val k = cct.coerceIn(CCT_MIN, 8000).toFloat() / MEL_DER_REF_CCT
        return k.pow(MEL_DER_EXPONENT).coerceIn(0.15f, 1.05f)
    }

    /**
     * Циркадный стимул со знаком — насколько свет сейчас «тянет» биологию.
     *
     * Диапазон [-1, 1]:
     *  — **> 0 — полезный дневной стимул**: свет помогает проснуться и
     *    удерживает циркадную систему в фазе. Максимум в середине дня.
     *  — **≈ 0 — нейтрально**: нейтральный вечерний сумрак, ни вредит, ни будит.
     *  — **< 0 — давление на сон**: свет в это время подавляет мелатонин и
     *    сдвигает засыпание. Чем ближе к ночи и чем выше CCT, тем сильнее.
     *
     * Формула сознательно не «лабораторная CS» (модель Rea требует абсолютной
     * освещённости в lux, которой у приложения нет), а её монотонный аналог:
     * произведение melanopic-вклада на временную функцию окна. Это ровно та
     * зависимость, которую подтверждают и обзор метрик, и исследование
     * ночных режимов дисплея: важны **и спектр, и тайминг**, причём
     * произведение, а не сумма.
     */
    fun circadianStimulus(cct: Int, phase: Float, elevation: Float): Float {
        val mel = melanopicDer(cct)
        // Временная функция: пик в середине дня, ноль на границе суток.
        val timeWindow = (elevation + 1f) / 2f           // 0..1
        val daytime = mel * (timeWindow - 0.55f) * 2.2f  // отрицательна вечером
        return daytime.coerceIn(-1f, 1f)
    }

    /**
     * Должна ли тема быть тёмной.
     *
     * Правило: тёмный интерфейс **рекомендован** (и включается автоматически
     * при [UserSettings.darkModePref] = SYSTEM) в двух окнах:
     *  — ночь: phase < 0.26 (примерно до 06:15);
     *  — вечер: phase ≥ 0.78 (примерно после 18:45).
     *
     * Днём тёмный интерфейс держится на [DUSK_DARK_FLOOR], а не выключается
     * мгновенно: иначе на рассвете экран мигал бы белым в глаза.
     */
    fun effectiveDark(phase: Float, elevation: Float): Boolean {
        val night = phase < PHASE_CONSTANT
        val evening = phase >= PHASE_HOMEOSTATIC
        if (night || evening) return true
        // Плавная проверка «предвечерья»: солнце низко, но ещё не село.
        if (phase >= PHASE_HOMEOSTATIC - 0.04f && elevation < 0.25f) return true
        return false
    }

    /**
     * Рекомендованная яркость контента 0..1.
     *
     * Ночью светлой части экрана должно быть меньше не «на глаз», а
     * предсказуемо: пик [DAY_LUMINANCE] в полдень, спад до
     * [NIGHT_LUMINANCE] глубокой ночью. Значение домножается на пользовательскую
     * шкалу в настройках, поэтому здесь оно нормировано.
     */
    fun displayLuminanceFor(phase: Float): Float {
        val h = phase * 24f
        val target = when {
            h >= 7f && h <= 19f -> DAY_LUMINANCE
            h < 5f || h > 22f -> NIGHT_LUMINANCE
            h in 5f..7f -> lerpF(NIGHT_LUMINANCE, DAY_LUMINANCE, (h - 5f) / 2f)
            else -> lerpF(DAY_LUMINANCE, NIGHT_LUMINANCE, (h - 19f) / 3f)
        }
        return target.coerceIn(0.42f, 1f)
    }

    // ══════════════════════════════════════════════════════════════════
    //  ГЕОМЕТРИЯ СУТОК (для проверок в UI и тестов)
    // ══════════════════════════════════════════════════════════════════

    /**
     * Человекочитаемое название фазы суток для подписи в настройках и в шапке.
     *
     * Границы подобраны по высоте солнца, а не по «ровным» часам: закат —
     * это когда садится солнце, а не «когда на часах 18:00». Летом эти
     * события расходятся почти на два часа.
     */
    fun phaseName(phase: Float): CircadianPhase = when (phase) {
        in 0.0f..0.21f -> CircadianPhase.DEEP_NIGHT
        in 0.21f..0.30f -> CircadianPhase.DAWN
        in 0.30f..0.46f -> CircadianPhase.MORNING
        in 0.46f..0.62f -> CircadianPhase.MIDDAY
        in 0.62f..0.75f -> CircadianPhase.AFTERNOON
        in 0.75f..0.83f -> CircadianPhase.DUSK
        in 0.83f..0.94f -> CircadianPhase.EVENING
        else -> CircadianPhase.NIGHT
    }

    /** Текущий дробный час: 14.5 = 14:30. */
    fun nowFractionalHour(): Float {
        val cal = Calendar.getInstance()
        return cal.get(Calendar.HOUR_OF_DAY) + cal.get(Calendar.MINUTE) / 60f
    }

    /** Текущий профиль. */
    fun now(): LightProfile = profileAt(nowFractionalHour())

    // ══════════════════════════════════════════════════════════════════
    //  ГЛАЗ: КОНТРАСТ, HALATION, ТЕНИ
    // ══════════════════════════════════════════════════════════════════

    /**
     * Контраст WCAG между двумя цветами — отношение (L1+0.05)/(L2+0.05).
     *
     * Оставлен именно WCAG-формулой, а не APCA: приложение задаёт целевые
     * *числа* (7:1 для текста) в терминах, которые можно проверить
     * автоматически и которые совпадают с принятой практикой. APCA-оценка
     * учла бы полярность точнее, но у неё нет устоявшихся порогов, а
     * «перекомпенсация» уже заложена явным требованием 7:1 для тела текста.
     */
    fun contrastRatio(a: Color, b: Color): Float {
        val la = a.luminance()
        val lb = b.luminance()
        val hi = maxOf(la, lb)
        val lo = minOf(la, lb)
        return (hi + 0.05f) / (lo + 0.05f)
    }

    /** Контраст ≥ [minRatio]? */
    fun passesContrast(a: Color, b: Color, minRatio: Float): Boolean =
        contrastRatio(a, b) >= minRatio

    /**
     * Подтягивает [color] к [background] до достижения [minRatio].
     *
     * Логика в два прохода, потому что односторонняя коррекция не работает
     * для средних тонов: если цвет чуть светлее фона, его нужно осветлять;
     * если чуть темнее — затемнять; но если фон сам «серый» (средняя яркость),
     * любое направление может не добрать контраст за один проход. Поэтому
     * выбирается полюс, который даёт лучший результат, и уже к нему идёт
     * итеративное приближение с шагом [CONTRAST_STEP].
     *
     * Возвращает цвет, ближайший к исходному, удовлетворяющий требованию.
     * Исходный цвет сохраняется в [Color] без изменений, если он уже прошёл.
     */
    fun ensureContrast(color: Color, background: Color, minRatio: Float): Color {
        if (contrastRatio(color, background) >= minRatio) return color

        val towardsLight = contrastRatio(Color.White, background) >=
            contrastRatio(Color.Black, background)
        val pole = if (towardsLight) Color.White else Color.Black

        var candidate = color
        var step = CONTRAST_STEP
        while (step >= CONTRAST_MIN_STEP) {
            val next = lerp(candidate, pole, step)
            if (contrastRatio(next, background) >= minRatio) {
                candidate = next
                // Не перескакиваем: пробуем вернуться назад меньшим шагом,
                // чтобы не получить «выцветший до белого» акцент.
                step /= 3f
                if (contrastRatio(candidate, background) >= minRatio * 1.02f) continue
                break
            }
            candidate = next
            step /= 2f
        }

        // Последняя ответственность: если итерация не дотянула — доводим
        // до полюса. Это гарантия, что текст останется читаемым, даже если
        // фон окажется экстремально средним по светлоте.
        return if (contrastRatio(candidate, background) >= minRatio) candidate else pole
    }

    /**
     * Ограничение светлоты для «белого» текста.
     *
     * Halation пропорциональна перепаду яркости на границе буквы. Чистый
     * белый #FFFFFF даёт максимальный перепад и максимальное расплывание;
     * опуская светлоту до [MAX_TEXT_LIGHTNESS], мы заметно улучшаем
     * воспринимаемую резкость, почти не теряя контраст (формула WCAG
     * нелинейна, поэтому небольшое снижение L даёт малое падение ratio).
     */
    fun softenText(color: Color, darkUi: Boolean): Color {
        if (!darkUi) return color
        val l = color.luminance()
        if (l <= MAX_TEXT_LIGHTNESS) return color
        val t = ((l - MAX_TEXT_LIGHTNESS) / (1f - MAX_TEXT_LIGHTNESS)).coerceIn(0f, 1f)
        return lerp(color, Color(0xFFE6E4EE), t * SOFTEN_STRENGTH)
    }

    /**
     * Ограничение светлоты для «чёрного» фона.
     *
     * Чистый чёрный #000 в тёмной теме даёт максимальный контраст с текстом
     * и максимальный halation, а на OLED ещё и «дыры» вокруг светлых пятен.
     * Поднятие минимальной светлоты до [MIN_BG_LIGHTNESS] убирает оба эффекта
     * и сохраняет контраст в пределах целевого.
     */
    fun liftBackground(color: Color, darkUi: Boolean): Color {
        if (!darkUi) return color
        val l = color.luminance()
        if (l >= MIN_BG_LIGHTNESS) return color
        return lerp(color, Color(0xFF12131A), BG_LIFT_STRENGTH)
    }

    /**
     * Мягкий вариант «тени» для текущей палитры.
     *
     * Тени — часть системы света, а не декоративный чёрный прямоугольник:
     * тёплый вечер не может отбрасывать ледяную синюю тень, а холодное утро —
     * оранжевую. Поэтому тень строится как затемнённый **цвет фона** со
     * сдвигом в сторону дополнительного тона палитры: физически это похоже
     * на то, как свет отражается от поверхностей вокруг экрана.
     *
     * @param base цвет фона, от которого строим тень.
     * @param warmTone ведущий тёплый тон палитры (для подмешивания).
     */
    fun shadowFor(base: Color, warmTone: Color, darkUi: Boolean): Color {
        val depth = if (darkUi) Color(0xFF000000) else Color(0xFF3A3428)
        val toned = lerp(depth, warmTone, SHADOW_TINT)
        val mixed = lerp(base, toned, SHADOW_DEPTH)
        return mixed.copy(alpha = if (darkUi) SHADOW_ALPHA_DARK else SHADOW_ALPHA_LIGHT)
    }

    // ── внутренние утилиты ────────────────────────────────────────────

    private fun easeOut(t: Float): Float = 1f - (1f - t).pow(2.2f)

    /**
     * Классический smoothstep: 0 на [edge0], 1 на [edge1], с нулевой
     * производной на обоих концах.
     *
     * Именно нулевая производная важна: линейная интерполяция на границе
     * режима даёт излом (резкую смену скорости), который глаз читает как
     * «щелчок» цвета, даже если сами значения почти совпадают.
     */
    private fun smoothstep(edge0: Float, edge1: Float, x: Float): Float {
        if (edge1 <= edge0) return if (x < edge0) 0f else 1f
        val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    private fun lerpF(from: Float, to: Float, t: Float): Float = from + (to - from) * t.coerceIn(0f, 1f)

    // ══════════════════════════════════════════════════════════════════
    //  КОНСТАНТЫ МОДЕЛИ
    // ══════════════════════════════════════════════════════════════════

    /** CCT дневного максимума: свет неба в полдень. */
    const val CCT_NOON = 5600

    /** CCT на границе дня и ночи: тёплый «золотой час». */
    const val CCT_DUSK = 2600

    /** CCT глубокой ночи: ниже порога 2200 K для вечерних источников. */
    const val CCT_NIGHT = 2000

    /** Потолок CCT для вечерних часов, даже если солнце ещё высоко. */
    const val CCT_EVENING_CAP = 3200

    private const val CCT_MIN = 1800
    private const val CCT_MAX = 6800

    /** С 20:00 вечерний потолок CCT действует в полную силу. */
    private const val EVENING_CAP_FULL = 20f

    /** Начало разгона вечернего потолка: 16:00 (см. пояснение у `eveningCapWeight`). */
    private const val EVENING_CAP_RAMP_START = 16f

    /** До 02:00 потолок держится: это середина ночного окна, свет стабилен. */
    private const val MORNING_CAP_START = 2f

    /** К 06:00 ограничение полностью снято — до рассветного разворота CCT. */
    private const val MORNING_CAP_END = 6f

    /** За сколько часов потолок набирает и сбрасывает полную силу. */
    private const val CAP_RAMP_HOURS = 4f

    /**
     * Полуширина перехода между ночной и дневной кривой CCT.
     * Определяет плавность рассветного «разворота»: чем больше значение,
     * тем шире тёплая зона вокруг горизонта.
     */
    private const val EDGE_STEP_HALF = 0.18f

    /** Показатель разгона дневной CCT от горизонта к зениту. */
    private const val DAY_SPREAD_EXP = 0.72f

    /**
     * «Резкость» мягкого минимума в вечернем потолке CCT.
     *
     * При `weight = 1` фактическое ограничение равно `ceiling + excess * k`,
     * где `k = SOFT_MIN_SHARPNESS`. Значение 0.12 означает, что к 20:00 свет
     * приходит не точно к 3200 K, а к 3200 K + 12% от превышения: полный
     * потолок достигался бы только при бесконечном превышении. Это и есть
     * плата за непрерывность — 12% визуально неотличимы, а скачок в 130 K
     * на графике исчезает.
     */
    private const val SOFT_MIN_SHARPNESS = 0.12f

    /** Опорная CCT для аппроксимации mel-DER (D65). */
    private const val MEL_DER_REF_CCT = 6500f

    /**
     * Показатель степени аппроксимации mel-DER.
     *
     * Подобран аналитически, а не «на глаз»: зависимость обязана проходить
     * **одновременно** через две независимые опорные точки —
     *
     *  — чёрное тело 3500 K → mel-DER = 0.62
     *    (Nature Scientific Reports, s41598-022-21755-7);
     *  — стандартный дневной свет D65 (6500 K) → mel-DER = 1.00
     *    (по определению величины в CIE S 026).
     *
     * Из условия `(3500/6500)^p = 0.62` следует `p = ln(0.62)/ln(3500/6500)`
     * ≈ 0.7722. При таком показателе кривая даёт физичные значения по всему
     * диапазону: 2000 K → 0.40, 2700 K → 0.51, 5600 K → 0.89.
     *
     * ВАЖНО: предыдущее значение 2.13 давало на 3500 K всего 0.27 — то есть
     * занижало меланопический вклад почти в 2.3 раза, и вечерняя палитра
     * выглядела «безопасной» там, где на самом деле свет ещё заметно
     * подавлял мелатонин.
     */
    private const val MEL_DER_EXPONENT = 0.7722f

    // ── Фазы суток (доли, не часы: устойчиво к переходу через полночь) ──
    /** С 18:45 включается вечерний режим. */
    const val PHASE_HOMEOSTATIC = 0.78f

    /** До 06:15 удерживается ночной режим. */
    const val PHASE_CONSTANT = 0.26f

    // ── Глаз ──────────────────────────────────────────────────────────
    /** Целевой контраст тела текста в тёмной полярности (AAA, а не AA). */
    const val TARGET_BODY_CONTRAST = 7.0f

    /** Минимальный контраст вторичного текста. */
    const val MIN_SECONDARY_CONTRAST = 4.6f

    /** Минимальный контраст акцента на фоне. */
    const val MIN_ACCENT_CONTRAST = 3.4f

    /** Максимальная светлота «белого» текста в тёмной теме. */
    private const val MAX_TEXT_LIGHTNESS = 0.79f

    /** Минимальная светлота фона в тёмной теме. */
    private const val MIN_BG_LIGHTNESS = 0.0075f

    private const val SOFTEN_STRENGTH = 0.85f
    private const val BG_LIFT_STRENGTH = 0.55f

    private const val SHADOW_TINT = 0.22f
    private const val SHADOW_DEPTH = 0.55f
    private const val SHADOW_ALPHA_DARK = 0.46f
    private const val SHADOW_ALPHA_LIGHT = 0.16f

    private const val CONTRAST_STEP = 0.30f
    private const val CONTRAST_MIN_STEP = 0.02f

    private const val DAY_LUMINANCE = 1.0f
    private const val NIGHT_LUMINANCE = 0.62f

    private const val PI_F = 3.1415927f
}

/**
 * Непрерывный световой профиль момента времени.
 *
 * @property hour дробный час, для которого построен профиль.
 * @property solarPhase циклическая фаза суток [0, 1) — **разрывна** на
 *   границе суток, использовать только для круговых величин.
 * @property solarElevation высота солнца [-1, 1] — **непрерывна** всюду,
 *   использовать для плавных переходов.
 * @property cct цветовая температура света интерфейса, K.
 * @property melanopicDer меланопическая эффективность относительно D65.
 * @property circadianStimulus циркадный стимул со знаком [-1, 1]:
 *   > 0 — полезен, < 0 — давит на сон.
 * @property recommendDarkUi тема должна быть тёмной по биологическим причинам.
 * @property effectiveDark фактическое решение о тёмной теме.
 * @property displayLuminance рекомендованная яркость светлой части контента 0..1.
 */
data class LightProfile(
    val hour: Float,
    val solarPhase: Float,
    val solarElevation: Float,
    val cct: Int,
    val melanopicDer: Float,
    val circadianStimulus: Float,
    val recommendDarkUi: Boolean,
    val effectiveDark: Boolean,
    val displayLuminance: Float,
) {
    /** Тёплый ли сейчас свет: нужно для выбора «соседних» тонов палитры. */
    val isWarm: Boolean get() = cct <= CIRCADIAN_WARM_THRESHOLD

    /**
     * Фаза суток — та же, что показывает чип в шапке главного экрана.
     * Считается из [solarPhase], поэтому подпись и палитра не могут разойтись.
     */
    val phase: CircadianPhase get() = CircadianEngine.phaseName(solarPhase)

    /**
     * Словесная характеристика освещения — показывается пользователю, чтобы
     * адаптация была объяснимой, а не «магической».
     */
    val lightLabel: String
        get() = when {
            cct <= 2100 -> "Свеча"
            cct <= 2500 -> "Тёплая лампа"
            cct <= 3100 -> "Закат"
            cct <= 3900 -> "Вечерний свет"
            cct <= 4600 -> "Нейтральный день"
            else -> "Дневной свет"
        }

    private companion object {
        const val CIRCADIAN_WARM_THRESHOLD = 3400
    }
}

/** Фаза суток для подписей и превью. */
enum class CircadianPhase(val label: String) {
    DEEP_NIGHT("Глубокая ночь"),
    NIGHT("Ночь"),
    DAWN("Рассвет"),
    MORNING("Утро"),
    MIDDAY("Полдень"),
    AFTERNOON("День"),
    DUSK("Закат"),
    EVENING("Вечер");

    /** Должна ли в этой фазе быть тёмная тема. */
    val prefersDark: Boolean
        get() = this == DEEP_NIGHT || this == NIGHT || this == EVENING || this == DUSK
}
