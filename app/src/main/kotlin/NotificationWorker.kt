package com.healthify.app.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.*
import com.healthify.app.MainActivity
import com.healthify.app.data.db.ReminderEntity
import java.time.LocalDate
import java.util.*
import java.util.concurrent.TimeUnit

// ═══════════════════════════════════════════════════════════════════════════
// NOTIFICATION CHANNELS
// ═══════════════════════════════════════════════════════════════════════════

object NotificationChannels {
    const val REMINDERS   = "healthify_reminders"
    const val CHECK_IN    = "healthify_checkin"
    const val STREAK      = "healthify_streak"

    fun createAll(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(REMINDERS, "Health Reminders", NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = "Water, medication, and movement reminders" }
        )
        nm.createNotificationChannel(
            NotificationChannel(CHECK_IN, "Daily Check-in", NotificationManager.IMPORTANCE_HIGH)
                .apply { description = "Your evening wellness check-in prompt" }
        )
        nm.createNotificationChannel(
            NotificationChannel(STREAK, "Streak Updates", NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = "Streak milestone notifications" }
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// NOTIFICATION WORKER  (handles a single reminder at fire time)
// ═══════════════════════════════════════════════════════════════════════════

class NotificationWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val channel  = inputData.getString(KEY_CHANNEL) ?: NotificationChannels.REMINDERS

        // Skip fire if today's day-of-week isn't in the reminder's repeat set.
        // Empty/missing => fire every day (back-compat with old workers).
        val repeatDays = inputData.getString(KEY_REPEAT_DAYS).orEmpty()
        if (repeatDays.isNotBlank()) {
            val allowed = repeatDays.split(",").mapNotNull { it.trim().toIntOrNull() }.toSet()
            val todayIso = LocalDate.now().dayOfWeek.value  // 1=Mon..7=Sun
            if (allowed.isNotEmpty() && todayIso !in allowed) return Result.success()
        }

        // Don't fire check-in reminders if the user already checked in within the last 24h
        if (channel == NotificationChannels.CHECK_IN) {
            val app = applicationContext as? com.healthify.app.HealthifyApp
            if (app != null) {
                val (canCheckIn, _) = app.repository.checkInCooldownStatus()
                if (!canCheckIn) return Result.success()
            }
        }

        val label    = inputData.getString(KEY_LABEL)   ?: "Time to check in!"
        val emoji    = inputData.getString(KEY_EMOJI)   ?: "❤️"
        val notifId  = inputData.getInt(KEY_ID, System.currentTimeMillis().toInt())

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pi = PendingIntent.getActivity(
            context, notifId, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("$emoji Healthify")
            .setContentText(label)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(notifId, notification)
        return Result.success()
    }

    companion object {
        const val KEY_LABEL       = "label"
        const val KEY_EMOJI       = "emoji"
        const val KEY_CHANNEL     = "channel"
        const val KEY_ID          = "notif_id"
        const val KEY_REPEAT_DAYS = "repeat_days"
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// NOTIFICATION SCHEDULER
// ═══════════════════════════════════════════════════════════════════════════

object NotificationScheduler {

    /**
     * Schedule a daily repeating notification for a reminder.
     * Uses a periodic WorkManager task aligned to the reminder's time-of-day.
     */
    fun schedule(context: Context, reminder: ReminderEntity): UUID {
        val workManager = WorkManager.getInstance(context)

        // Cancel any previous work for this reminder
        if (reminder.workerId.isNotEmpty()) {
            workManager.cancelWorkById(UUID.fromString(reminder.workerId))
        }

        // Calculate initial delay to first occurrence
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, reminder.hourOfDay)
            set(Calendar.MINUTE, reminder.minute)
            set(Calendar.SECOND, 0)
            if (before(now)) add(Calendar.DAY_OF_YEAR, 1) // if past today, fire tomorrow
        }
        val initialDelay = target.timeInMillis - now.timeInMillis

        val data = workDataOf(
            NotificationWorker.KEY_LABEL       to getNotifText(reminder.label, reminder.category),
            NotificationWorker.KEY_EMOJI       to reminder.emoji,
            NotificationWorker.KEY_CHANNEL     to channelFor(reminder.category),
            NotificationWorker.KEY_ID          to reminder.id,
            NotificationWorker.KEY_REPEAT_DAYS to reminder.repeatDays
        )

        val request = PeriodicWorkRequestBuilder<NotificationWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
            .setInputData(data)
            .addTag("reminder_${reminder.id}")
            .build()

        workManager.enqueueUniquePeriodicWork(
            "reminder_${reminder.id}",
            ExistingPeriodicWorkPolicy.REPLACE,
            request
        )
        return request.id
    }

    fun cancel(context: Context, reminder: ReminderEntity) {
        WorkManager.getInstance(context).cancelAllWorkByTag("reminder_${reminder.id}")
    }

    /** Schedule a one-time check-in nudge if no check-in has happened by 8 PM */
    fun scheduleCheckInNudge(context: Context) {
        val now    = Calendar.getInstance()
        val target = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 20); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0)
            if (before(now)) add(Calendar.DAY_OF_YEAR, 1)
        }
        val delay = target.timeInMillis - now.timeInMillis

        val data = workDataOf(
            NotificationWorker.KEY_LABEL   to "Don't forget your daily check-in! How are you feeling?",
            NotificationWorker.KEY_EMOJI   to "❤️",
            NotificationWorker.KEY_CHANNEL to NotificationChannels.CHECK_IN,
            NotificationWorker.KEY_ID      to 9999
        )

        val request = PeriodicWorkRequestBuilder<NotificationWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setInputData(data)
            .addTag("check_in_nudge")
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "check_in_nudge",
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    /** Send an immediate streak milestone notification */
    fun notifyStreakMilestone(context: Context, streak: Int) {
        val request = OneTimeWorkRequestBuilder<NotificationWorker>()
            .setInputData(workDataOf(
                NotificationWorker.KEY_LABEL   to "🔥 $streak day streak! ${streakMsg(streak)}",
                NotificationWorker.KEY_EMOJI   to "🏆",
                NotificationWorker.KEY_CHANNEL to NotificationChannels.STREAK,
                NotificationWorker.KEY_ID      to (10000 + streak)
            ))
            .build()
        WorkManager.getInstance(context).enqueue(request)
    }

    private fun channelFor(category: String) = when (category) {
        "wellness" -> NotificationChannels.CHECK_IN
        else       -> NotificationChannels.REMINDERS
    }

    private fun getNotifText(label: String, category: String) = when (category) {
        "water"    -> "Time to hydrate! Have you had water recently? 💧"
        "movement" -> "Get moving! A short walk can boost your mood and energy 🏃"
        "meds"     -> "Medication reminder: time for $label 💊"
        "wellness" -> "How's your day going? Open Healthify for your check-in ❤️"
        else       -> label
    }

    private fun streakMsg(streak: Int) = when {
        streak == 3   -> "3 days in a row. You're building something real!"
        streak == 7   -> "One full week! Keep the fire burning! 🔥"
        streak == 14  -> "Two weeks! You're a habit-building machine! 💪"
        streak == 30  -> "30 DAYS! You're a wellness champion! 👑"
        else          -> "Keep the momentum going!"
    }

    private val MILESTONE_STREAKS = setOf(3, 7, 14, 21, 30, 60, 90, 100, 365)
    fun isMilestone(streak: Int) = streak in MILESTONE_STREAKS
}
