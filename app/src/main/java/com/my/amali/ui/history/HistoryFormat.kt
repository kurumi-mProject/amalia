package com.my.amali.ui.history

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.res.stringResource
import com.my.amali.R
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * Форматирование времени для истории.
 *
 * Дата в списке разговоров вторична: пользователь ищет «когда это было»,
 * а не «какое число». Поэтому свежие элементы получают относительное время
 * («5 мин назад»), а старые — метку дня, по которой список группируется.
 */

/** Ключ дня: год*1000 + день года. По нему история режется на секции. */
fun dayKey(timestamp: Long): Int {
    val calendar = Calendar.getInstance().apply { timeInMillis = timestamp }
    return calendar.get(Calendar.YEAR) * 1000 + calendar.get(Calendar.DAY_OF_YEAR)
}

/** Заголовок секции истории: «Сегодня», «Вчера», «На этой неделе» или «Раньше». */
@Composable
@ReadOnlyComposable
fun dayLabel(timestamp: Long): String {
    val now = Calendar.getInstance()
    val then = Calendar.getInstance().apply { timeInMillis = timestamp }
    val days = TimeUnit.MILLISECONDS.toDays(now.timeInMillis - then.timeInMillis)
    val sameDay = now.get(Calendar.YEAR) == then.get(Calendar.YEAR) &&
        now.get(Calendar.DAY_OF_YEAR) == then.get(Calendar.DAY_OF_YEAR)
    val yesterday = days == 1L && isYesterday(now, then)
    return when {
        sameDay -> stringResource(R.string.history_today)
        yesterday -> stringResource(R.string.history_yesterday)
        days <= 6 -> stringResource(R.string.history_this_week)
        else -> stringResource(R.string.history_older)
    }
}

/** Относительное время одной строкой: «только что» → «5 мин» → «3 ч» → «4 дн». */
@Composable
@ReadOnlyComposable
fun relativeTime(timestamp: Long): String {
    val delta = System.currentTimeMillis() - timestamp
    val minutes = TimeUnit.MILLISECONDS.toMinutes(delta)
    val hours = TimeUnit.MILLISECONDS.toHours(delta)
    val days = TimeUnit.MILLISECONDS.toDays(delta)
    return when {
        minutes < 1 -> stringResource(R.string.time_just_now)
        hours < 1 -> stringResource(R.string.time_minutes_ago, minutes.toInt())
        days < 1 -> stringResource(R.string.time_hours_ago, hours.toInt())
        days < 8 -> stringResource(R.string.time_days_ago, days.toInt())
        else -> shortDate(timestamp)
    }
}

/** Короткая дата для старых записей: «12.03» или «12.03.24». */
@Composable
@ReadOnlyComposable
fun shortDate(timestamp: Long): String {
    val now = Calendar.getInstance()
    val then = Calendar.getInstance().apply { timeInMillis = timestamp }
    val pattern = if (now.get(Calendar.YEAR) == then.get(Calendar.YEAR)) {
        "dd.MM"
    } else {
        "dd.MM.yy"
    }
    return java.text.SimpleDateFormat(pattern, java.util.Locale.getDefault())
        .format(java.util.Date(timestamp))
}

/** Время внутри сообщения: часы и минуты. */
@Composable
@ReadOnlyComposable
fun clockTime(timestamp: Long): String =
    java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
        .format(java.util.Date(timestamp))

/** true, если [then] — календарный «вчера» относительно [now]. */
private fun isYesterday(now: Calendar, then: Calendar): Boolean {
    val probe = now.clone() as Calendar
    probe.add(Calendar.DAY_OF_YEAR, -1)
    return probe.get(Calendar.YEAR) == then.get(Calendar.YEAR) &&
        probe.get(Calendar.DAY_OF_YEAR) == then.get(Calendar.DAY_OF_YEAR)
}
