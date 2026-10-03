package com.healthify.app.score

import com.healthify.app.data.db.CheckInEntity
import com.healthify.app.data.db.UserEntity
import kotlin.math.roundToInt

/**
 * The single 0–100 wellness score — shown on the home rings, stored on each
 * check-in (Insights, Profile averages) and revealed on the celebration.
 *
 *   Water, steps, sleep  20 each, against the user's own goals (= the rings)
 *   Mood                 20   (Terrible 4 … Amazing 20)
 *   Food                 10   (Ate well 10, Decent 7, Not great 3, Skipped 0)
 *   Day rating           10   (2 per star)
 *
 * Each part is rounded on its own so the breakdown always adds up to the
 * total. A day only reaches 100 with every ring closed and a great check-in.
 */
object HealthScore {

    /**
     * Bump whenever the formula changes. HealthifyApp recomputes every
     * stored check-in once when the persisted version is older.
     */
    const val VERSION = 2

    data class Part(
        val label: String,
        val emoji: String,
        val points: Int,
        val max: Int
    )

    /**
     * [water] and [food] default to the check-in's own answers. Home passes
     * today's water log total and, before the check-in, the food tag worked
     * out from logged meals, so the score shows "today so far".
     */
    fun parts(
        ci: CheckInEntity?,
        steps: Int,
        sleep: Float,
        user: UserEntity?,
        water: Int = ci?.waterGlasses ?: 0,
        food: String? = ci?.foodQuality,
    ): List<Part> {
        val waterGoal = (user?.waterGoalGlasses ?: 8).coerceAtLeast(1)
        val stepGoal  = (user?.stepGoal ?: 10_000).coerceAtLeast(1)
        val sleepGoal = (user?.sleepGoalHours ?: 8f).coerceAtLeast(0.5f)

        fun ofGoal(value: Float, goal: Float, max: Int) =
            ((value / goal).coerceIn(0f, 1f) * max).roundToInt()

        return listOf(
            Part("Water", "💧", ofGoal(water.toFloat(), waterGoal.toFloat(), 20), 20),
            Part("Steps", "🚶", ofGoal(steps.toFloat(), stepGoal.toFloat(), 20), 20),
            Part("Sleep", "🌙", ofGoal(sleep, sleepGoal, 20), 20),
            Part("Mood", "😊", ci?.moodScore?.takeIf { it in 0..4 }?.let { (it + 1) * 4 } ?: 0, 20),
            Part("Food", "🥗", when (food) {
                "well" -> 10
                "ok"   -> 7
                "poor" -> 3
                else   -> 0
            }, 10),
            Part("Day", "⭐", ci?.dayRating?.takeIf { it in 1..5 }?.let { it * 2 } ?: 0, 10)
        )
    }

    fun compute(
        ci: CheckInEntity?,
        steps: Int,
        sleep: Float,
        user: UserEntity?,
        water: Int = ci?.waterGlasses ?: 0,
        food: String? = ci?.foodQuality,
    ): Int = parts(ci, steps, sleep, user, water, food).sumOf { it.points }.coerceIn(0, 100)

    /** Score of a stored check-in from its own recorded values. */
    fun of(ci: CheckInEntity, user: UserEntity?): Int =
        compute(ci, ci.steps, ci.sleepHours, user)
}
