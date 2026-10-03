package com.healthify.app.notifications

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.healthify.app.HealthifyApp
import com.healthify.app.MainActivity
import com.healthify.app.R
import com.healthify.app.logs.LogSource
import kotlinx.coroutines.launch

/**
 * Handles the "+1 glass" action on water reminders: logs the glass without
 * opening the app, then swaps the reminder for a short confirmation. Starts
 * no activity, so Android 12's notification-trampoline limit doesn't apply.
 */
class QuickLogReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_LOG_WATER) return
        val notifId = intent.getIntExtra(EXTRA_NOTIF_ID, -1)
        val app = context.applicationContext as? HealthifyApp ?: return

        val pendingResult = goAsync()
        app.appScope.launch {
            try {
                val total = app.logRepository.changeWater(1, LogSource.NOTIFICATION)
                val goal = (app.repository.getUserOnce()?.waterGoalGlasses ?: 8).coerceAtLeast(1)
                if (notifId > 0) confirm(context, notifId, total, goal)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun confirm(context: Context, notifId: Int, total: Int, goal: Int) {
        val open = PendingIntent.getActivity(
            context, notifId,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val text = if (total >= goal) "Logged 💧 $total of $goal — goal reached! 🎉"
                   else "Logged 💧 $total of $goal glasses today"
        val notification = NotificationCompat.Builder(context, NotificationChannels.REMINDERS)
            .setSmallIcon(R.drawable.ic_stat_water)
            .setContentTitle("💧 Healthify")
            .setContentText(text)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setAutoCancel(true)
            .setContentIntent(open)
            .setTimeoutAfter(CONFIRMATION_TIMEOUT_MS)
            .build()
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(notifId, notification)
    }

    companion object {
        const val ACTION_LOG_WATER = "com.healthify.app.action.LOG_WATER"
        const val EXTRA_NOTIF_ID   = "notif_id"

        // Request codes for the action's PendingIntent, kept clear of the
        // reminder ids that ReminderReceiver's own PendingIntents use.
        private const val REQ_LOG_WATER_BASE = 50_000
        private const val CONFIRMATION_TIMEOUT_MS = 5_000L

        fun pendingIntent(context: Context, reminderId: Int): PendingIntent =
            PendingIntent.getBroadcast(
                context,
                REQ_LOG_WATER_BASE + reminderId,
                Intent(context, QuickLogReceiver::class.java).apply {
                    action = ACTION_LOG_WATER
                    putExtra(EXTRA_NOTIF_ID, reminderId)
                },
                // IMMUTABLE is required for PendingIntents on API 31+.
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
    }
}
