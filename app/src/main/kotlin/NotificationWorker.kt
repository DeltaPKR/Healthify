package com.healthify.app.notifications

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import com.healthify.app.HealthifyApp
import com.healthify.app.MainActivity
import com.healthify.app.R
import com.healthify.app.data.db.ReminderEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.Calendar

// ═══════════════════════════════════════════════════════════════════════════
// NOTIFICATION CHANNELS
// ═══════════════════════════════════════════════════════════════════════════

object NotificationChannels {
    const val REMINDERS   = "healthify_reminders"
    const val CHECK_IN    = "healthify_checkin"
    const val STREAK      = "healthify_streak"

    // Bump this any time we need to force every existing install to drop
    // and re-create its NotificationChannels with refreshed settings.
    // NotificationChannel settings (sound, importance, vibration) are
    // immutable once created — only the user can change them through OS
    // settings — so calling createNotificationChannel() with new values
    // on an existing id is a no-op. Bumping this constant flips the
    // SharedPreferences gate below and forces a one-time delete + create.
    private const val CHANNEL_SETTINGS_REVISION = 2
    private const val KEY_CHANNEL_SETTINGS_REV  = "channel_settings_rev"

    fun createAll(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val prefs = context.getSharedPreferences(
            HealthifyApp.PREFS_FILE, Context.MODE_PRIVATE
        )

        // One-time refresh: if the current revision doesn't match the
        // value we last persisted, drop every existing channel so that
        // the createNotificationChannel calls below get fresh settings
        // (including the explicit sound + vibration we set up next).
        // Without this step the original silent-by-accident channel
        // config from older installs would persist forever — testers
        // reported "no sound when notification is sent" for exactly
        // this reason.
        val storedRev = prefs.getInt(KEY_CHANNEL_SETTINGS_REV, 0)
        if (storedRev != CHANNEL_SETTINGS_REVISION) {
            listOf(REMINDERS, CHECK_IN, STREAK).forEach { id ->
                try { nm.deleteNotificationChannel(id) } catch (_: Exception) { /* ignore */ }
            }
            prefs.edit().putInt(KEY_CHANNEL_SETTINGS_REV, CHANNEL_SETTINGS_REVISION).apply()
        }

        // Explicit sound + vibration. Without setSound() the platform
        // sometimes leaves a channel silent depending on OEM defaults
        // (especially on Xiaomi / Vivo / Realme variants); calling it
        // unconditionally with the system default tone guarantees an
        // audible ping.
        val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val audioAttrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        nm.createNotificationChannel(
            NotificationChannel(
                REMINDERS,
                context.getString(R.string.channel_reminders_name),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = context.getString(R.string.channel_reminders_desc)
                setSound(soundUri, audioAttrs)
                enableVibration(true)
                vibrationPattern = longArrayOf(0L, 250L, 200L, 250L)
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(
                CHECK_IN,
                context.getString(R.string.channel_checkin_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.channel_checkin_desc)
                setSound(soundUri, audioAttrs)
                enableVibration(true)
                vibrationPattern = longArrayOf(0L, 300L, 200L, 300L)
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(
                STREAK,
                context.getString(R.string.channel_streak_name),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = context.getString(R.string.channel_streak_desc)
                setSound(soundUri, audioAttrs)
                enableVibration(true)
            }
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// NOTIFICATION SCHEDULER (AlarmManager-based for exact, reliable timing)
// ═══════════════════════════════════════════════════════════════════════════

object NotificationScheduler {

    const val ACTION_FIRE = "com.healthify.app.action.REMINDER_FIRE"
    const val EXTRA_REMINDER_ID = "reminder_id"

    /**
     * Schedule the next single fire for this reminder using AlarmManager.
     * AlarmManager.setExactAndAllowWhileIdle gives exact firing (within Doze
     * limits) and the receiver re-schedules itself for the next occurrence.
     *
     * Calling this for an existing reminder.id overwrites the previously
     * scheduled alarm — it is idempotent and safe to call repeatedly.
     */
    fun schedule(context: Context, reminder: ReminderEntity) {
        if (reminder.id == 0) return
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val now = Calendar.getInstance()
        val target = nextFireTime(reminder, now)
        val pi = buildPendingIntent(context, reminder.id)
            ?: return

        val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
        try {
            if (canExact) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, target.timeInMillis, pi)
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, target.timeInMillis, pi)
            }
        } catch (se: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, target.timeInMillis, pi)
        }
    }

    fun cancel(context: Context, reminder: ReminderEntity) = cancel(context, reminder.id)

    fun cancel(context: Context, reminderId: Int) {
        if (reminderId == 0) return
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = buildPendingIntent(
            context,
            reminderId,
            flags = PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pi != null) {
            am.cancel(pi)
            pi.cancel()
        }
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(reminderId)
    }

    /** Post an immediate streak-milestone notification (no scheduling). */
    fun notifyStreakMilestone(context: Context, streak: Int) {
        val notifId = 10_000 + streak
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pi = PendingIntent.getActivity(
            context, notifId, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification = NotificationCompat.Builder(context, NotificationChannels.STREAK)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("🏆 Healthify")
            .setContentText(context.getString(R.string.notif_body_streak, streak, streakMsg(context, streak)))
            .setAutoCancel(true)
            .setContentIntent(pi)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(notifId, notification)
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    /**
     * Routes a reminder to its notification channel.
     *
     * The CHECK_IN channel uses IMPORTANCE_HIGH so the daily check-in
     * nudge is hard to miss. Every other reminder — including bedtime
     * wellness reminders like the default "Wind Down" — uses the
     * standard REMINDERS channel: high importance at 22:00 is too
     * disruptive, and routing all wellness reminders to CHECK_IN was
     * the same "category = identity" mistake that caused the suppression
     * and notification-text bugs.
     */
    fun channelFor(isCheckInReminder: Boolean): String =
        if (isCheckInReminder) NotificationChannels.CHECK_IN else NotificationChannels.REMINDERS

    /**
     * Returns the body text for a reminder notification.
     *
     * The check-in copy ("How's your day going? …") is reserved for the
     * seeded Daily Check-in reminder identified by [isCheckInReminder]
     * — NOT for every wellness-category reminder. Before this change
     * the default 22:00 "Wind Down" reminder fired with the check-in
     * body text because it shared category="wellness" with the
     * check-in nudge. Wellness (and general) reminders now fall back
     * to the user's own label, which matches the body the user
     * configured in the editor.
     */
    fun getNotifText(context: Context, reminder: ReminderEntity, isCheckInReminder: Boolean): String {
        if (isCheckInReminder) return context.getString(R.string.notif_body_checkin)
        return when (reminder.category) {
            "water"    -> context.getString(R.string.notif_body_water)
            "movement" -> context.getString(R.string.notif_body_movement)
            "meds"     -> context.getString(R.string.notif_body_meds, reminder.label)
            else       -> reminder.label
        }
    }

    private fun buildPendingIntent(
        context: Context,
        reminderId: Int,
        flags: Int = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    ): PendingIntent? {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = ACTION_FIRE
            putExtra(EXTRA_REMINDER_ID, reminderId)
        }
        return PendingIntent.getBroadcast(context, reminderId, intent, flags)
    }

    /**
     * Find the next valid fire time honoring repeatDays (ISO Mon=1..Sun=7).
     * Empty repeatDays => fires every day. Looks ahead up to 7 days.
     */
    private fun nextFireTime(reminder: ReminderEntity, now: Calendar): Calendar {
        val allowed = reminder.repeatDays.split(",")
            .mapNotNull { it.trim().toIntOrNull() }.toSet()
        val cand = Calendar.getInstance().apply {
            timeInMillis = now.timeInMillis
            set(Calendar.HOUR_OF_DAY, reminder.hourOfDay)
            set(Calendar.MINUTE, reminder.minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (!cand.after(now)) cand.add(Calendar.DAY_OF_YEAR, 1)
        if (allowed.isEmpty()) return cand
        repeat(7) {
            val calDow = cand.get(Calendar.DAY_OF_WEEK)
            // Calendar.DAY_OF_WEEK: Sun=1..Sat=7; ISO: Mon=1..Sun=7
            val isoDow = if (calDow == Calendar.SUNDAY) 7 else calDow - 1
            if (isoDow in allowed) return cand
            cand.add(Calendar.DAY_OF_YEAR, 1)
        }
        return cand
    }

    private fun streakMsg(context: Context, streak: Int) = when {
        streak == 3   -> context.getString(R.string.streak_milestone_3)
        streak == 7   -> context.getString(R.string.streak_milestone_7)
        streak == 14  -> context.getString(R.string.streak_milestone_14)
        streak == 30  -> context.getString(R.string.streak_milestone_30)
        else          -> context.getString(R.string.streak_milestone_default)
    }

    private val MILESTONE_STREAKS = setOf(3, 7, 14, 21, 30, 60, 90, 100, 365)
    fun isMilestone(streak: Int) = streak in MILESTONE_STREAKS
}

// ═══════════════════════════════════════════════════════════════════════════
// REMINDER RECEIVER (fires on alarm; posts notification and reschedules)
// ═══════════════════════════════════════════════════════════════════════════

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != NotificationScheduler.ACTION_FIRE) return
        val reminderId = intent.getIntExtra(NotificationScheduler.EXTRA_REMINDER_ID, -1)
        if (reminderId <= 0) return

        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                val app = context.applicationContext as? HealthifyApp ?: return@launch
                val reminder = app.repository.getReminderById(reminderId) ?: return@launch
                if (!reminder.enabled) return@launch

                // Suppress only the seeded Daily Check-in nudge if the
                // user has already checked in for the current window. We
                // identify it by the row id we cached in SharedPreferences
                // at first launch (HealthifyApp.onCreate) — NOT by
                // channel / category. The previous "channel == CHECK_IN"
                // check accidentally muted every wellness-category
                // reminder (e.g. the 22:00 "Wind Down" default) whenever
                // the user checked in after 18:00, because all wellness
                // reminders route to the CHECK_IN channel.
                val prefs = context.getSharedPreferences(
                    HealthifyApp.PREFS_FILE, Context.MODE_PRIVATE
                )
                val checkInReminderId = prefs.getInt(HealthifyApp.KEY_CHECKIN_REMINDER_ID, -1)
                val isCheckInReminder = reminder.id == checkInReminderId
                val shouldFire = if (isCheckInReminder) {
                    val (canCheckIn, _) = app.repository.checkInCooldownStatus()
                    canCheckIn
                } else true

                if (shouldFire) {
                    postReminderNotification(context, reminder, isCheckInReminder)
                }

                // Always queue the next occurrence so the daily cycle keeps running.
                NotificationScheduler.schedule(context, reminder)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun postReminderNotification(
        context: Context,
        reminder: ReminderEntity,
        isCheckInReminder: Boolean
    ) {
        // Channel + body text both pivot on the same id-anchored flag,
        // so a wellness-category reminder (e.g. the 22:00 "Wind Down"
        // default) gets the REMINDERS channel and its own label as
        // body text instead of being forced into the CHECK_IN channel
        // with the "How's your day going?" copy.
        val channel = NotificationScheduler.channelFor(isCheckInReminder)
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pi = PendingIntent.getActivity(
            context, reminder.id, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("${reminder.emoji} Healthify")
            .setContentText(NotificationScheduler.getNotifText(context, reminder, isCheckInReminder))
            .setAutoCancel(true)
            .setContentIntent(pi)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(reminder.id, notification)
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// BOOT RECEIVER (re-schedules all enabled reminders after device restart)
// ═══════════════════════════════════════════════════════════════════════════

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val act = intent.action ?: return
        // Receiver only handles post-unlock broadcasts so the Credential-Encrypted
        // Room database is accessible when we re-arm alarms. LOCKED_BOOT_COMPLETED
        // intentionally omitted — see the matching note in AndroidManifest.xml.
        if (act != Intent.ACTION_BOOT_COMPLETED &&
            act != "android.intent.action.QUICKBOOT_POWERON" &&
            act != Intent.ACTION_MY_PACKAGE_REPLACED &&
            act != Intent.ACTION_TIME_CHANGED &&
            act != Intent.ACTION_TIMEZONE_CHANGED) return

        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                val app = context.applicationContext as? HealthifyApp ?: return@launch
                app.repository.getEnabledReminders().forEach { reminder ->
                    NotificationScheduler.schedule(context, reminder)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
