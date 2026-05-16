package com.healthify.app.streak

import com.healthify.app.data.db.CheckInEntity
import com.healthify.app.data.db.UserEntity
import com.healthify.app.data.repository.AppRepository
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/** Criteria for a "healthy day" that counts toward the streak */
data class StreakCriteria(
    val minWaterGlasses: Int  = 6,
    val minSleepHours: Float  = 6f,
    val minSteps: Int         = 5_000,
    val minMoodScore: Int     = 2,     // 0-4 scale; 2 = "Okay"
    val minDayRating: Int     = 3      // 1-5 scale
)

data class StreakResult(
    val current: Int,
    val longest: Int,
    val isHealthyToday: Boolean,
    val missedCriteria: List<String>
)

object StreakManager {

    private val iso = DateTimeFormatter.ISO_LOCAL_DATE
    private val criteria = StreakCriteria()

    /** Evaluate today's check-in and update the streak in the DB. */
    suspend fun evaluate(repo: AppRepository): StreakResult {
        val today     = LocalDate.now()
        val user      = repo.getUserOnce() ?: UserEntity()
        val checkIn   = repo.getCheckInForToday()

        if (checkIn == null) {
            // No check-in yet today – don't break streak, just return current
            return StreakResult(
                current        = user.currentStreak,
                longest        = user.longestStreak,
                isHealthyToday = false,
                missedCriteria = listOf("Complete today's check-in")
            )
        }

        val missed = mutableListOf<String>()
        if (checkIn.waterGlasses < criteria.minWaterGlasses)
            missed += "Drink more water (${checkIn.waterGlasses}/${criteria.minWaterGlasses} glasses)"
        if (checkIn.sleepHours < criteria.minSleepHours)
            missed += "Sleep more (${checkIn.sleepHours}h / goal ${criteria.minSleepHours}h)"
        if (checkIn.steps > 0 && checkIn.steps < criteria.minSteps)
            missed += "More steps (${checkIn.steps}/${criteria.minSteps})"
        if (checkIn.moodScore in 0..4 && checkIn.moodScore < criteria.minMoodScore)
            missed += "Mood is low – we're here for you 💙"
        val isHealthy = missed.isEmpty()

        // Determine new streak
        val lastDate = if (user.lastStreakDate.isNotEmpty())
            LocalDate.parse(user.lastStreakDate, iso) else null

        val daysSinceLast = if (lastDate != null)
            ChronoUnit.DAYS.between(lastDate, today) else Long.MAX_VALUE

        val newStreak = when {
            isHealthy && daysSinceLast == 1L -> user.currentStreak + 1  // consecutive day
            isHealthy && daysSinceLast == 0L -> user.currentStreak      // same day update
            isHealthy                        -> 1                        // streak reset / new start
            else                             -> 0                        // failed today
        }

        val newLongest = maxOf(user.longestStreak, newStreak)

        if (newStreak != user.currentStreak || newLongest != user.longestStreak) {
            repo.updateStreak(newStreak, newLongest, today.format(iso))
        }

        return StreakResult(
            current        = newStreak,
            longest        = newLongest,
            isHealthyToday = isHealthy,
            missedCriteria = missed
        )
    }

    /** Return a motivational message based on streak length. */
    fun message(streak: Int): String = when {
        streak == 0  -> "Start your streak today! 🌱"
        streak == 1  -> "Day 1 – great start! 🌿"
        streak < 3   -> "Building momentum! Keep it up 💪"
        streak < 7   -> "$streak days strong – you're on fire! 🔥"
        streak < 14  -> "One week streak! You're unstoppable 🌟"
        streak < 30  -> "$streak days – incredible consistency 🏆"
        else         -> "$streak days – you're a wellness champion 👑"
    }

    /** Emoji for streak milestone badges */
    fun badge(streak: Int): String = when {
        streak == 0  -> "🌱"
        streak < 3   -> "🌿"
        streak < 7   -> "🔥"
        streak < 14  -> "⚡"
        streak < 30  -> "🌟"
        else         -> "👑"
    }
}
