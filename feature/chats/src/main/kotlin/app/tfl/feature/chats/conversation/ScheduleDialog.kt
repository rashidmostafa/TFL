package app.tfl.feature.chats.conversation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.tfl.core.designsystem.theme.TflTheme
import app.tfl.feature.chats.R
import app.tfl.feature.chats.TimeContext
import app.tfl.feature.chats.upcoming
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** How far ahead a message can be scheduled. */
internal const val MAX_SCHEDULE_DAYS = 365L

/**
 * Picks when a message goes: a date (today to a year ahead), then a time on this phone's clock. It
 * must be in the future.
 *
 * @param initialMillis the time to start from; an hour from now if null.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ScheduleDialog(
    time: TimeContext,
    onPick: (atMillis: Long) -> Unit,
    onDismiss: () -> Unit,
    initialMillis: Long? = null,
) {
    val start = Instant.ofEpochMilli(initialMillis ?: (time.nowMillis + HOUR_MILLIS)).atZone(time.zone)
    val today = time.date(time.nowMillis)
    var choosingTime by rememberSaveable { mutableStateOf(false) }
    val dates = rememberDatePickerState(
        initialSelectedDateMillis = start.toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        selectableDates = remember(today) {
            object : SelectableDates {
                // The picker works in UTC midnights.
                override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                    val day = LocalDate.ofEpochDay(Math.floorDiv(utcTimeMillis, DAY_MILLIS))
                    return !day.isBefore(today) && !day.isAfter(today.plusDays(MAX_SCHEDULE_DAYS))
                }

                override fun isSelectableYear(year: Int): Boolean = year in today.year..today.plusDays(MAX_SCHEDULE_DAYS).year
            }
        },
    )
    val clock = rememberTimePickerState(initialHour = start.hour, initialMinute = start.minute, is24Hour = time.is24Hour)

    if (!choosingTime) {
        DatePickerDialog(
            onDismissRequest = onDismiss,
            confirmButton = {
                TextButton(onClick = { choosingTime = true }, enabled = dates.selectedDateMillis != null) {
                    Text(stringResource(R.string.chat_schedule_next))
                }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.chat_cancel)) } },
        ) {
            DatePicker(state = dates, title = { Text(stringResource(R.string.chat_schedule_pick_day)) })
        }
    } else {
        val day = dates.selectedDateMillis?.let { LocalDate.ofEpochDay(Math.floorDiv(it, DAY_MILLIS)) } ?: today
        val at = day.atTime(clock.hour, clock.minute).atZone(time.zone).toInstant().toEpochMilli()
        val inFuture = at > time.nowMillis
        AlertDialog(
            onDismissRequest = onDismiss,
            confirmButton = {
                TextButton(onClick = { onPick(at) }, enabled = inFuture) { Text(stringResource(R.string.chat_schedule_confirm)) }
            },
            dismissButton = { TextButton(onClick = { choosingTime = false }) { Text(stringResource(R.string.chat_schedule_back)) } },
            title = { Text(stringResource(R.string.chat_schedule_pick_time)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    TimePicker(state = clock)
                    Text(
                        text = if (inFuture) time.upcoming(at) else stringResource(R.string.chat_schedule_past),
                        style = TflTheme.typography.bodyMd,
                        color = if (inFuture) TflTheme.colors.primary else TflTheme.colors.danger,
                    )
                }
            },
        )
    }
}

private const val DAY_MILLIS = 86_400_000L
private const val HOUR_MILLIS = 3_600_000L
