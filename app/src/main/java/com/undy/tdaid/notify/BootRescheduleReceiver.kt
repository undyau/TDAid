package com.undy.tdaid.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.undy.tdaid.data.ServiceLocator

/** AlarmManager alarms are wiped on reboot, so anything already armed needs re-scheduling once
 *  the device comes back up — otherwise a TD who restarts their phone mid-tournament silently
 *  loses all their alerts.
 *
 *  Real tee times only live in [com.undy.tdaid.data.repo.LiveRosterRepository]'s in-memory cache,
 *  which a reboot also wipes — there's nothing real to reschedule from here without a network
 *  fetch, which isn't safe to attempt from a BroadcastReceiver's short execution window. Alarms
 *  get re-armed for real as soon as the TD reopens Field Mode after the reboot (its own
 *  LaunchedEffect re-schedules on every load), so this is a known gap rather than a silent one. */
class BootRescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        ServiceLocator.init(context.applicationContext)
    }
}
