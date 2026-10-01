package app.tfl.feature.chats

import android.content.Context
import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import app.tfl.core.crypto.lock.DeviceClock
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import javax.inject.Inject

/** The time, and how this phone shows it. Screens get it in their state, so tests can fix it. */
@Immutable
data class TimeContext(val nowMillis: Long, val zone: ZoneId, val is24Hour: Boolean) {
    fun date(atMillis: Long): LocalDate = Instant.ofEpochMilli(atMillis).atZone(zone).toLocalDate()
}

/** Where chat screens get the time from. */
fun interface ChatClock {
    fun now(): TimeContext
}

class AndroidChatClock @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val clock: DeviceClock,
) : ChatClock {
    override fun now() = TimeContext(clock.currentTimeMillis(), ZoneId.systemDefault(), DateFormat.is24HourFormat(context))
}

/** How often a shown screen reads the clock again: "12m ago", day labels and edit windows move on. */
internal const val TICK_MILLIS = 60_000L

/**
 * The time a screen shows: each of [times] as it comes, then the clock again every minute until the
 * next. Cold, so it only ticks while the screen's state is watched.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal fun ChatClock.ticking(times: Flow<TimeContext>): Flow<TimeContext> = times.flatMapLatest { time ->
    flow {
        emit(time)
        while (true) {
            delay(TICK_MILLIS)
            emit(now())
        }
    }
}

/** "14:02" or "2:02 PM". */
fun TimeContext.clockTime(atMillis: Long): String =
    DateTimeFormatter.ofPattern(if (is24Hour) "HH:mm" else "h:mm a", Locale.getDefault())
        .format(Instant.ofEpochMilli(atMillis).atZone(zone))

/** The app's current locale, read so that a change recomposes. */
@Composable
private fun locale(): Locale = LocalConfiguration.current.locales[0]

/** "Today", "Yesterday", or the date. */
@Composable
fun TimeContext.dayLabel(atMillis: Long): String {
    val today = date(nowMillis)
    val day = date(atMillis)
    return when (day) {
        today -> stringResource(R.string.chats_day_today)
        today.minusDays(1) -> stringResource(R.string.chats_day_yesterday)
        else -> DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale()).format(day)
    }
}

/** "Just now", "12m ago", "3h ago", then "Yesterday" and dates, for the chats list. */
@Composable
fun TimeContext.relative(atMillis: Long): String {
    val minutes = ((nowMillis - atMillis) / 60_000).coerceAtLeast(0)
    return when {
        minutes < 1 -> stringResource(R.string.chats_time_now)
        minutes < 60 -> stringResource(R.string.chats_time_minutes, minutes)
        date(atMillis) == date(nowMillis) -> stringResource(R.string.chats_time_hours, minutes / 60)
        else -> dayLabel(atMillis)
    }
}

/** "Today, 18:00", "Tomorrow, 08:30" or a date and time, for scheduled messages. */
@Composable
fun TimeContext.upcoming(atMillis: Long): String {
    val today = date(nowMillis)
    val day = date(atMillis)
    val time = clockTime(atMillis)
    return when (day) {
        today -> stringResource(R.string.chats_scheduled_today, time)
        today.plusDays(1) -> stringResource(R.string.chats_scheduled_tomorrow, time)
        else -> stringResource(
            R.string.chats_scheduled_date,
            DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale()).format(day),
            time,
        )
    }
}

/** "Off", "5 minutes", "1 hour", "1 day", "1 week". */
@Composable
fun timerLabel(seconds: Int): String = when (seconds) {
    0 -> stringResource(R.string.chats_timer_off)
    300 -> pluralStringResource(R.plurals.chats_timer_minutes, 5, 5)
    3_600 -> pluralStringResource(R.plurals.chats_timer_hours, 1, 1)
    86_400 -> pluralStringResource(R.plurals.chats_timer_days, 1, 1)
    604_800 -> pluralStringResource(R.plurals.chats_timer_weeks, 1, 1)
    else -> pluralStringResource(R.plurals.chats_timer_minutes, seconds / 60, seconds / 60)
}

/** "14:02:07": message info, where the seconds between sent and delivered matter. */
fun TimeContext.clockTimeWithSeconds(atMillis: Long): String =
    DateTimeFormatter.ofPattern(if (is24Hour) "HH:mm:ss" else "h:mm:ss a", Locale.getDefault())
        .format(Instant.ofEpochMilli(atMillis).atZone(zone))
