package com.my.amali.ui.assistant

import androidx.annotation.StringRes
import com.my.amali.R
import com.my.amali.ui.theme.CircadianPhase

/**
 * Каталог фраз приветствия главного экрана.
 *
 * ════════════════════════════════════════════════════════════════════════
 *  СТРУКТУРА: ФАЗА × ТОН × НОМЕР
 * ════════════════════════════════════════════════════════════════════════
 *
 * Фраза выбирается по двум независимым осям:
 *
 *  — **Фаза суток** ([GreetingSlot]) — из того же [CircadianPhase], по
 *    которому строится весь свет экрана. Ночью человек должен читать ночное
 *    приветствие, а не «Доброе утро» в три часа: текст обязан совпадать
 *    с освещением, иначе экран спорит сам с собой.
 *  — **Тон** ([GreetingTone]) — голос Амалии. Это не украшение: у одного
 *    и того же продукта тон — параметр, потому что разным людям нужно
 *    разное отношение. Одному достаточно «Добрый вечер»; тому, кто завёл
 *    ассистента как собеседника, — «Вечер для себя».
 *
 * По каждому пересечению — три фразы. Итого 4 × 3 × 3 = **36**.
 *
 * ════════════════════════════════════════════════════════════════════════
 *  ПОЧЕМУ ПЕРЕЧИСЛЕНИЕ АДРЕСОВ, А НЕ СПИСОК СТРОК
 * ════════════════════════════════════════════════════════════════════════
 *
 * Здесь только адреса строк, ни одного текста. Причины две, обе практические:
 *
 *  1. **Перевод.** Текст в коде — это текст, который никто не переведёт.
 *     Ровно на этом приложение уже спотыкалось: подписи ступеней света жили
 *     строкой внутри расчёта цвета, и восемь локалей из девяти видели русское
 *     «Закат» посреди своего интерфейса.
 *  2. **Проверяемость.** Список адресов можно сверить с ресурсами скриптом:
 *     «все 36 ключей есть во всех трёх языках». Список строк так не сверишь —
 *     опечатку в тексте не отличить от замысла.
 *
 * ════════════════════════════════════════════════════════════════════════
 *  ЯЗЫКИ И ЧЕГО ЗДЕСЬ НЕТ
 * ════════════════════════════════════════════════════════════════════════
 *
 * Переведены три языка: русский (`values/` — язык по умолчанию проекта),
 * английский (`values-en/`) и японский (`values-ja/`). Остальные локали
 * приложения (немецкий, испанский, французский, китайский, хинди, арабский)
 * получат **английский** автоматически: Android берёт `values-en/` для любой
 * локали, перевода для которой нет. Условий в коде для этого нет и быть
 * не должно — язык выбирает система, а не программист.
 */
internal object GreetingPhrases {

    /**
     * Все фразы фазы для выбранного тона.
     *
     * Возвращается список, а не одна строка: показ — дело вызывающей стороны,
     * и она вправе перемешать список. Если бы перемешивание жило здесь, оно
     * перезапускалось бы при каждом обращении, и «случайный порядок»
     * превратился бы в случайную фразу на каждый кадр.
     */
    @StringRes
    fun forSlot(slot: GreetingSlot, tone: GreetingTone): List<Int> = when (slot) {
        GreetingSlot.NIGHT -> when (tone) {
            GreetingTone.RESTRAINED -> listOf(
                R.string.greeting_night_plain_1,
                R.string.greeting_night_plain_2,
                R.string.greeting_night_plain_3,
            )
            GreetingTone.WARM -> listOf(
                R.string.greeting_night_warm_1,
                R.string.greeting_night_warm_2,
                R.string.greeting_night_warm_3,
            )
            GreetingTone.SPIRITED -> listOf(
                R.string.greeting_night_bold_1,
                R.string.greeting_night_bold_2,
                R.string.greeting_night_bold_3,
            )
        }
        GreetingSlot.MORNING -> when (tone) {
            GreetingTone.RESTRAINED -> listOf(
                R.string.greeting_morning_plain_1,
                R.string.greeting_morning_plain_2,
                R.string.greeting_morning_plain_3,
            )
            GreetingTone.WARM -> listOf(
                R.string.greeting_morning_warm_1,
                R.string.greeting_morning_warm_2,
                R.string.greeting_morning_warm_3,
            )
            GreetingTone.SPIRITED -> listOf(
                R.string.greeting_morning_bold_1,
                R.string.greeting_morning_bold_2,
                R.string.greeting_morning_bold_3,
            )
        }
        GreetingSlot.DAY -> when (tone) {
            GreetingTone.RESTRAINED -> listOf(
                R.string.greeting_day_plain_1,
                R.string.greeting_day_plain_2,
                R.string.greeting_day_plain_3,
            )
            GreetingTone.WARM -> listOf(
                R.string.greeting_day_warm_1,
                R.string.greeting_day_warm_2,
                R.string.greeting_day_warm_3,
            )
            GreetingTone.SPIRITED -> listOf(
                R.string.greeting_day_bold_1,
                R.string.greeting_day_bold_2,
                R.string.greeting_day_bold_3,
            )
        }
        GreetingSlot.EVENING -> when (tone) {
            GreetingTone.RESTRAINED -> listOf(
                R.string.greeting_evening_plain_1,
                R.string.greeting_evening_plain_2,
                R.string.greeting_evening_plain_3,
            )
            GreetingTone.WARM -> listOf(
                R.string.greeting_evening_warm_1,
                R.string.greeting_evening_warm_2,
                R.string.greeting_evening_warm_3,
            )
            GreetingTone.SPIRITED -> listOf(
                R.string.greeting_evening_bold_1,
                R.string.greeting_evening_bold_2,
                R.string.greeting_evening_bold_3,
            )
        }
    }

    /** Все адреса сразу — для скриптовой сверки с ресурсами и для тестов. */
    val allKeys: List<Int>
        get() = GreetingSlot.entries.flatMap { slot ->
            GreetingTone.entries.flatMap { tone -> forSlot(slot, tone) }
        }
}

/**
 * Фаза суток в терминах приветствия — четыре ступени.
 *
 * Восемь [CircadianPhase] схлопываются в четыре: рассвет и утро для человека
 * одно и то же событие, как и закат с вечером. Дробить до восьми — значит
 * писать восемь наборов текста там, где разница не читается.
 */
internal enum class GreetingSlot {
    NIGHT,
    MORNING,
    DAY,
    EVENING;

    companion object {
        /**
         * Какая ступень приветствия соответствует фазе света.
         *
         * Связь односторонняя: фаза знает про себя, ступень не знает про
         * фазу. Так у приветствия нет доступа к расчёту цвета — а значит,
         * и соблазна принять решение «покрасивее» на основе температуры.
         */
        fun of(phase: CircadianPhase): GreetingSlot = when (phase) {
            CircadianPhase.DEEP_NIGHT, CircadianPhase.NIGHT -> NIGHT
            CircadianPhase.DAWN, CircadianPhase.MORNING -> MORNING
            CircadianPhase.MIDDAY, CircadianPhase.AFTERNOON -> DAY
            CircadianPhase.DUSK, CircadianPhase.EVENING -> EVENING
        }
    }
}

/**
 * Голос Амалии — как она обращается к человеку.
 *
 * Три ступени, и это не «уровни вежливости», а три разных персонажа:
 *
 *  — [RESTRAINED] — приветствие как оно есть. «Добрый вечер», «Утро»,
 *    «Конец дня». Ничего о себе, ничего о собеседнике: ассистент уважает
 *    дистанцию.
 *  — [WARM] — приветствие как обращение к знакомому. «Я рядом», «Как
 *    спалось?», «Отдохнём?». Появляется забота, но без фамильярности.
 *  — [SPIRITED] — приветствие с характером. «Ночь — моё время», «День
 *    короткий. Погнали», «Всё, день сдан». Живо, чуть дерзко, с собственной
 *    позицией — это то, за что голосовых ассистентов и заводят.
 */
internal enum class GreetingTone {
    RESTRAINED,
    WARM,
    SPIRITED;

    companion object {
        /**
         * Тон, который используется прямо сейчас.
         *
         * Все три набора лежат в коде одновременно и переведены на все три
         * языка. Смена тона — правка одной строки здесь, а не переписывание
         * каталога: именно ради этого три варианта и были составлены втроём,
         * а не выбран один.
         */
        val DEFAULT = SPIRITED
    }
}
