package com.undy.tdaid.notify

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.undy.tdaid.data.model.TeeGroup
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

private const val TAG = "TeeAlarmScheduler"
const val EXTRA_TIME = "time"
const val EXTRA_NAMES = "names"

/**
 * Schedules real OS alarms — via [AlarmManager], not an in-process timer — so a tee-time alert
 * still fires when the app has been fully closed. Each group gets one alarm at
 * (tee time - interval minutes); [TeeAlertAlarmReceiver] is what actually runs when it goes off.
 */
object TeeAlarmScheduler {

    private val timeFormat = SimpleDateFormat("h:mm a", Locale.US)

    /** Schedules one alarm per group whose (tee time - interval) hasn't passed yet today.
     *  Groups already inside their announce window, or fully in the past, are skipped —
     *  a TD opening Field Mode mid-round shouldn't get a flood of stale alerts. */
    fun scheduleAll(context: Context, groups: List<TeeGroup>, intervalMinutes: Int) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        val now = System.currentTimeMillis()

        groups.forEachIndexed { index, group ->
            val teeMillis = parseTodayMillis(group.time) ?: return@forEachIndexed
            val fireAtMillis = teeMillis - intervalMinutes * 60_000L
            if (fireAtMillis <= now) return@forEachIndexed

            val pendingIntent = pendingIntentFor(context, index, group)
            scheduleExact(alarmManager, fireAtMillis, pendingIntent)
            Log.i(TAG, "Scheduled alert for ${group.time} at ${intervalMinutes}min out")
        }
    }

    fun cancelAll(context: Context, groups: List<TeeGroup>) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        groups.forEachIndexed { index, group ->
            alarmManager.cancel(pendingIntentFor(context, index, group))
        }
    }

    /** Fires one real alarm shortly from now, through the exact same AlarmManager path as
     *  production alerts. Lets a TD confirm sound/vibration work on their phone — and that
     *  alerts survive the app being closed — before a round actually starts. */
    fun scheduleTestAlert(context: Context, delaySeconds: Int = 15) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        val fireAtMillis = System.currentTimeMillis() + delaySeconds * 1000L
        val testGroup = TeeGroup(time = "Test alert", players = emptyList())
        scheduleExact(alarmManager, fireAtMillis, pendingIntentFor(context, requestCode = -1, testGroup))
    }

    private fun scheduleExact(alarmManager: AlarmManager, fireAtMillis: Long, pendingIntent: PendingIntent) {
        val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()
        if (canExact) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, fireAtMillis, pendingIntent)
        } else {
            // No exact-alarm grant: still schedule, just without the tight-timing guarantee.
            alarmManager.set(AlarmManager.RTC_WAKEUP, fireAtMillis, pendingIntent)
        }
    }

    fun canScheduleExact(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return false
        return alarmManager.canScheduleExactAlarms()
    }

    private fun pendingIntentFor(context: Context, requestCode: Int, group: TeeGroup): PendingIntent {
        val intent = Intent(context, TeeAlertAlarmReceiver::class.java).apply {
            putExtra(EXTRA_TIME, group.time)
            putExtra(EXTRA_NAMES, group.players.joinToString(", ") { it.name })
        }
        return PendingIntent.getBroadcast(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /** Parses a display string like "8:30 AM" against today's date. Real tee-time data would
     *  carry a full timestamp; this fake dataset only has a time-of-day string. */
    private fun parseTodayMillis(timeLabel: String): Long? {
        val parsedTimeOfDay = try {
            timeFormat.parse(timeLabel)
        } catch (e: ParseException) {
            null
        } ?: return null

        val timeCal = Calendar.getInstance().apply { time = parsedTimeOfDay }
        val target = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, timeCal.get(Calendar.HOUR_OF_DAY))
            set(Calendar.MINUTE, timeCal.get(Calendar.MINUTE))
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return target.timeInMillis
    }
}
