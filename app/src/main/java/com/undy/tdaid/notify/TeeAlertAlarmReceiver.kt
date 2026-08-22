package com.undy.tdaid.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Runs when one of [TeeAlarmScheduler]'s alarms goes off — including with the app process
 *  not running at all, which is the whole point of using AlarmManager over an in-app timer. */
class TeeAlertAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val time = intent.getStringExtra(EXTRA_TIME) ?: return
        val names = intent.getStringExtra(EXTRA_NAMES) ?: ""
        NotificationHelper.notifyAnnounceNow(context, time, names)
    }
}
