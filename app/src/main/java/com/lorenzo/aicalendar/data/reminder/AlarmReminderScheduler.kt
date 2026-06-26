package com.lorenzo.aicalendar.data.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import com.lorenzo.aicalendar.MainActivity
import com.lorenzo.aicalendar.domain.model.CalendarEvent
import com.lorenzo.aicalendar.domain.reminder.ReminderScheduler
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

/**
 * AlarmManager-backed reminders. Uses [AlarmManager.setAlarmClock] (user-visible, Doze-exempt).
 * On Android 16 this requires the exact-alarm permission, satisfied by USE_EXACT_ALARM
 * (auto-granted for calendar apps). Scheduling is wrapped defensively so a missing permission
 * degrades to "no reminder" instead of crashing.
 */
class AlarmReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) : ReminderScheduler {

    private val alarmManager = context.getSystemService(AlarmManager::class.java)
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.ITALIAN)

    override fun schedule(event: CalendarEvent) {
        val triggerAt = reminderTriggerMillis(event) ?: return
        val requestCode = event.id.hashCode()

        val time = event.start.atZone(event.zone).format(timeFormatter)
        val text = buildString {
            append("Alle ").append(time)
            event.location?.takeIf { it.isNotBlank() }?.let { append(" · ").append(it) }
        }

        val operation = PendingIntent.getBroadcast(
            context,
            requestCode,
            Intent(context, ReminderReceiver::class.java).apply {
                putExtra(ReminderReceiver.EXTRA_EVENT_ID, event.id)
                putExtra(ReminderReceiver.EXTRA_TITLE, event.title)
                putExtra(ReminderReceiver.EXTRA_TEXT, text)
                putExtra(ReminderReceiver.EXTRA_NOTIFICATION_ID, requestCode)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val showIntent = PendingIntent.getActivity(
            context,
            requestCode,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        try {
            alarmManager.setAlarmClock(AlarmManager.AlarmClockInfo(triggerAt, showIntent), operation)
        } catch (e: SecurityException) {
            // Exact-alarm permission unavailable (e.g. revoked) → skip rather than crash.
            Log.w(TAG, "Cannot schedule exact reminder for event ${event.id}", e)
        }
    }

    private companion object {
        const val TAG = "AlarmReminderScheduler"
    }

    override fun cancel(eventId: String) {
        val operation = PendingIntent.getBroadcast(
            context,
            eventId.hashCode(),
            Intent(context, ReminderReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        alarmManager.cancel(operation)
    }
}
