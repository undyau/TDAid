package com.undy.tdaid.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.undy.tdaid.data.ServiceLocator
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/** AlarmManager alarms are wiped on reboot, so anything already armed needs re-scheduling once
 *  the device comes back up — otherwise a TD who restarts their phone mid-tournament silently
 *  loses all their alerts. */
class BootRescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        ServiceLocator.init(context.applicationContext)
        val groups = ServiceLocator.tournamentRepository.teeGroups()
        val intervalMinutes = runBlocking { ServiceLocator.settingsRepository.settings.first().announceIntervalMin }
        TeeAlarmScheduler.scheduleAll(context.applicationContext, groups, intervalMinutes)
    }
}
