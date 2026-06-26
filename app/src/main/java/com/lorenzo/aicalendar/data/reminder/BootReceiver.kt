package com.lorenzo.aicalendar.data.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.lorenzo.aicalendar.domain.repository.EventRepository
import com.lorenzo.aicalendar.domain.reminder.ReminderScheduler
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Instant
import javax.inject.Inject

/**
 * Alarms don't survive a reboot, so re-arm every upcoming event's reminder on BOOT_COMPLETED.
 */
@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject lateinit var repository: EventRepository
    @Inject lateinit var scheduler: ReminderScheduler
    @Inject lateinit var clock: Clock

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                repository.getUpcomingEvents(Instant.now(clock), clock.zone).forEach(scheduler::schedule)
            } finally {
                pending.finish()
            }
        }
    }
}
