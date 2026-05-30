package com.healthify.app.streak

import com.healthify.app.data.db.CheckInEntity
import com.healthify.app.data.db.UserEntity
import com.healthify.app.data.repository.AppRepository
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
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

        // Hint list — surfaces what's not optimal today. Not used for the
        // streak count anymore (see below); kept so callers can show
        // motivational hints. Renamed from "missed" to clarify it does
        // NOT break the streak.
        val hints = mutableListOf<String>()
        if (checkIn.waterGlasses < criteria.minWaterGlasses)
            hints += "Drink more water (${checkIn.waterGlasses}/${criteria.minWaterGlasses} glasses)"
        if (checkIn.sleepHours < criteria.minSleepHours)
            hints += "Sleep more (${checkIn.sleepHours}h / goal ${criteria.minSleepHours}h)"
        if (checkIn.steps > 0 && checkIn.steps < criteria.minSteps)
            hints += "More steps (${checkIn.steps}/${criteria.minSteps})"
        if (checkIn.moodScore in 0..4 && checkIn.moodScore < criteria.minMoodScore)
            hints += "Mood is low – we're here for you 💙"
        // "Healthy today" badge stays attached to meeting all criteria.
        val isHealthy = hints.isEmpty()

        // Streak counts consecutive *days the user checked in at all* —
        // not days they hit every wellness target. Testers reported that
        // a single suboptimal day (e.g. 4 glasses of water on a busy
        // travel day) silently reset the streak; users perceived this as
        // a save bug ("my streak vanished"). Consistency of the habit
        // itself is the right primitive to reward; per-day quality is
        // surfaced separately via `isHealthyToday` + `hints`.
        val lastDate = if (user.lastStreakDate.isNotEmpty())
            LocalDate.parse(user.lastStreakDate, iso) else null

        val daysSinceLast = if (lastDate != null)
            ChronoUnit.DAYS.between(lastDate, today) else Long.MAX_VALUE

        val newStreak = when {
            daysSinceLast == 1L -> user.currentStreak + 1   // consecutive day
            daysSinceLast == 0L -> maxOf(user.currentStreak, 1) // same day re-save
            else                -> 1                         // gap → fresh streak of 1
        }

        val newLongest = maxOf(user.longestStreak, newStreak)

        // Only write when something actually changed. Room invalidates
        // every Flow observer on the table on ANY successful UPDATE —
        // even one that sets columns to the same values they already
        // hold. DashboardViewModel observes the user flow and calls
        // evaluate() from inside load(); an unconditional write here
        // therefore feeds a no-op UPDATE back into the flow, which
        // re-triggers load(), which writes again, etc., producing the
        // infinite "Profile screen endlessly refreshing" loop testers
        // reported. Comparing the proposed values against what is
        // already in the row breaks the cycle without giving up the
        // "lastStreakDate anchor" behaviour: the only path that
        // actually mutates the row is one where something genuinely
        // changed (streak count, longest, or anchor date).
        val todayStr = today.format(iso)
        val needsWrite = newStreak != user.currentStreak ||
            newLongest != user.longestStreak ||
            todayStr != user.lastStreakDate
        if (needsWrite) {
            // NonCancellable: this call can be reached from a Flow
            // collector (`DashboardViewModel.init { collectLatest ... }`)
            // which cancels the previous evaluate on every new emission.
            // Without NonCancellable a quick second emission could cancel
            // the write mid-flight and the streak would silently fail to
            // update.
            withContext(NonCancellable) {
                repo.updateStreak(newStreak, newLongest, todayStr)
            }
        }

        return StreakResult(
            current        = newStreak,
            longest        = newLongest,
            isHealthyToday = isHealthy,
            missedCriteria = hints
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
