package com.undy.tdaid.notify

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.undy.tdaid.data.model.TeeGroup
import java.util.Calendar

private const val TAG = "TeeAlarmScheduler"
const val EXTRA_TIME = "time"
const val EXTRA_NAMES = "names"

/**
 * Schedules real OS alarms — via [AlarmManager], not an in-process timer — so a tee-time alert
 * still fires when the app has been fully closed. Each group gets one alarm at
 * (tee time - interval minutes); [TeeAlertAlarmReceiver] is what actually runs when it goes off.
 */
object TeeAlarmScheduler {

    // Demo data uses a 12-hour "8:30 AM" style label; real PDGA Live tee times come back as
    // 24-hour "HH:mm:ss". Matched as plain regex rather than SimpleDateFormat, since a lenient
    // SimpleDateFormat parse of one pattern against the other's input risks silently succeeding
    // with a wrong result rather than throwing.
    private val twelveHourPattern = Regex("""^(\d{1,2}):(\d{2})\s*([AaPp][Mm])$""")
    private val twentyFourHourPattern = Regex("""^(\d{1,2}):(\d{2})(?::\d{2})?$""")

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

    /** Parses a display string like "8:30 AM" (demo) or "14:30:00" (real PDGA Live) against
     *  today's date. Internal (rather than private) so it can be unit tested directly. */
    internal fun parseTodayMillis(timeLabel: String): Long? {
        val (hour, minute) = parseHourMinute(timeLabel.trim()) ?: return null
        val target = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return target.timeInMillis
    }

    private fun parseHourMinute(timeLabel: String): Pair<Int, Int>? {
        twelveHourPattern.find(timeLabel)?.let { match ->
            var hour = match.groupValues[1].toIntOrNull() ?: return null
            val minute = match.groupValues[2].toIntOrNull() ?: return null
            if (hour !in 1..12 || minute !in 0..59) return null
            hour %= 12
            if (match.groupValues[3].equals("PM", ignoreCase = true)) hour += 12
            return hour to minute
        }
        twentyFourHourPattern.find(timeLabel)?.let { match ->
            val hour = match.groupValues[1].toIntOrNull() ?: return null
            val minute = match.groupValues[2].toIntOrNull() ?: return null
            if (hour in 0..23 && minute in 0..59) return hour to minute
        }
        return null
    }
}
