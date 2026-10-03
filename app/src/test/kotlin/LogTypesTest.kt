package com.healthify.app.logs

import com.healthify.app.data.db.CheckInEntity
import com.healthify.app.data.db.UserEntity
import com.healthify.app.score.HealthScore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MealQualityTest {

    @Test fun noMeals() {
        assertNull(MealQuality.forDay(emptyList()))
        assertNull(MealQuality.forDay(listOf("", "bogus")))
    }

    @Test fun singleMealKeepsItsTag() {
        MealQuality.entries.forEach { assertEquals(it, MealQuality.forDay(listOf(it.key))) }
    }

    @Test fun averageMapsBack() {
        assertEquals(MealQuality.WELL, MealQuality.forDay(listOf("well", "ok")))          // 8.5
        assertEquals(MealQuality.OK,   MealQuality.forDay(listOf("well", "ok", "ok")))    // 8.0
        assertEquals(MealQuality.OK,   MealQuality.forDay(listOf("well", "skip")))        // 5.0
        assertEquals(MealQuality.POOR, MealQuality.forDay(listOf("ok", "skip", "skip")))  // 2.33
        assertEquals(MealQuality.SKIP, MealQuality.forDay(listOf("poor", "skip", "skip"))) // 1.0
    }

    @Test fun mealSlotByHour() {
        assertEquals(MealType.BREAKFAST, MealType.forHour(7))
        assertEquals(MealType.LUNCH, MealType.forHour(13))
        assertEquals(MealType.SNACK, MealType.forHour(16))
        assertEquals(MealType.DINNER, MealType.forHour(19))
        assertEquals(MealType.SNACK, MealType.forHour(23))
        assertEquals(MealType.SNACK, MealType.forHour(2))
    }

    @Test fun unknownKeysFallBack() {
        assertEquals(MealType.SNACK, MealType.of("brunch"))
        assertEquals(ActivityType.OTHER, ActivityType.of("climbing"))
    }
}

class HealthScoreTest {

    private val user = UserEntity(waterGoalGlasses = 8, stepGoal = 8_000, sleepGoalHours = 8f)
    private val ci = CheckInEntity(
        date = "2026-10-03", moodScore = 3, waterGlasses = 6, foodQuality = "ok",
        sleepHours = 7f, dayRating = 4, steps = 6_000
    )

    @Test fun defaultsMatchTheCheckInsOwnAnswers() {
        assertEquals(
            HealthScore.compute(ci, 6_000, 7f, user),
            HealthScore.compute(ci, 6_000, 7f, user, water = 6, food = "ok")
        )
        assertEquals(HealthScore.of(ci, user), HealthScore.compute(ci, ci.steps, ci.sleepHours, user))
    }

    @Test fun knownTotal() {
        // water 15 + steps 15 + sleep 17.5→18 + mood 16 + food 7 + day 8
        assertEquals(79, HealthScore.of(ci, user))
    }

    @Test fun beforeCheckInUsesLogsOnly() {
        // No check-in: mood and day are 0; water and food come from the logs.
        val score = HealthScore.compute(null, 4_000, 8f, user, water = 8, food = "well")
        assertEquals(20 + 10 + 20 + 0 + 10 + 0, score)
    }
}
