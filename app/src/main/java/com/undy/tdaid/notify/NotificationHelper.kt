package com.undy.tdaid.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.undy.tdaid.MainActivity
import com.undy.tdaid.R
import com.undy.tdaid.data.model.TeeGroup

private const val CHANNEL_ID = "tee_time_alerts"

/** Posts the real system notification behind the Tee-Time Alert screen's countdown, so a TD
 *  still gets sound/vibration and a tray notification even with the app backgrounded. */
object NotificationHelper {

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Tee-time alerts",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Alerts a configurable interval before each group's tee time"
            enableVibration(true)
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun hasPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    }

    fun notifyAnnounceNow(context: Context, group: TeeGroup) {
        if (!hasPermission(context)) return

        val openIntent = android.content.Intent(context, MainActivity::class.java).apply {
            flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val names = group.players.joinToString(", ") { it.name }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Announce now · ${group.time}")
            .setContentText(names)
            .setStyle(NotificationCompat.BigTextStyle().bigText(names))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        NotificationManagerCompat.from(context).notify(group.time.hashCode(), notification)
    }
}
