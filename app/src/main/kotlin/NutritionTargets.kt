package com.healthify.app.nutrition

import com.healthify.app.data.db.FoodItemEntity
import com.healthify.app.data.db.MealEntryEntity
import com.healthify.app.data.db.UserEntity
import com.healthify.app.logs.MealQuality
import kotlin.math.max
import kotlin.math.roundToInt

enum class ActivityLevel(val key: String, val label: String, val hint: String, val factor: Double) {
    SEDENTARY("sedentary", "Mostly sitting", "Desk job, little exercise", 1.2),
    LIGHT("light", "Lightly active", "Walks, exercise 1–3 days a week", 1.375),
    MODERATE("moderate", "Moderately active", "Exercise 3–5 days a week", 1.55),
    ACTIVE("active", "Very active", "Hard exercise 6–7 days a week", 1.725),
    VERY_ACTIVE("very_active", "Athlete", "Physical job or twice-a-day training", 1.9);

    companion object {
        fun of(key: String?): ActivityLevel? = entries.firstOrNull { it.key == key }
    }
}

enum class CalorieGoal(val key: String, val label: String, val factor: Double) {
    LOSE("lose", "Lose a little", 0.85),
    MAINTAIN("maintain", "Maintain", 1.0),
    GAIN("gain", "Gain a little", 1.10);

    companion object {
        fun of(key: String?): CalorieGoal = entries.firstOrNull { it.key == key } ?: MAINTAIN
    }
}

/** Grams per day for the 20 / 50 / 30 % protein / carbs / fat split. */
data class Macros(val proteinG: Int, val carbsG: Int, val fatG: Int)

object NutritionTargets {

    private const val MIN_KCAL = 1_200

    /**
     * Mifflin-St Jeor resting energy. Sex constant +5 (male) / −161
     * (female); anyone else gets the midpoint −78 rather than a guess.
     */
    fun bmr(weightKg: Float, heightCm: Float, age: Int, gender: String): Double {
        val s = when (gender) {
            "Male"   -> 5.0
            "Female" -> -161.0
            else     -> -78.0
        }
        return 10.0 * weightKg + 6.25 * heightCm - 5.0 * age + s
    }

    /** What's still needed before an estimate is possible. */
    fun missing(user: UserEntity): List<String> = buildList {
        if (user.age !in 13..120) add("age")
        if (user.heightCm <= 0f) add("height")
        if (user.weightKg <= 0f) add("weight")
        if (ActivityLevel.of(user.activityLevel) == null) add("activity")
    }

    /** Estimated daily kcal, or null while [missing] isn't empty. Rounded to 10. */
    fun estimate(user: UserEntity): Int? {
        if (missing(user).isNotEmpty()) return null
        val activity = ActivityLevel.of(user.activityLevel) ?: return null
        val bmr = bmr(user.weightKg, user.heightCm, user.age, user.gender)
        val target = bmr * activity.factor * CalorieGoal.of(user.calorieGoal).factor
        // Never suggest eating below resting needs (or a 1,200 kcal floor).
        return (max(target, max(bmr, MIN_KCAL.toDouble())) / 10.0).roundToInt() * 10
    }

    /** The user's own number when set, otherwise the estimate. */
    fun dailyTarget(user: UserEntity): Int? =
        user.calorieTargetOverride.takeIf { it > 0 } ?: estimate(user)

    fun macros(kcal: Int): Macros = Macros(
        proteinG = (kcal * 0.20 / 4).roundToInt(),
        carbsG   = (kcal * 0.50 / 4).roundToInt(),
        fatG     = (kcal * 0.30 / 9).roundToInt()
    )
}

/** Portion maths on a food's per-100 g values. */
object Portion {

    /** A meal entry for [grams] of [food], nutrients snapshotted now. */
    fun mealFrom(
        food: FoodItemEntity,
        grams: Float,
        base: MealEntryEntity,
    ): MealEntryEntity {
        fun per(v: Float?) = v?.let { it * grams / 100f }
        return base.copy(
            name       = base.name.ifBlank { food.name },
            foodItemId = food.id.takeIf { it > 0 },
            barcode    = food.barcode,
            grams      = grams,
            kcal       = per(food.kcal100),
            proteinG   = per(food.protein100),
            carbsG     = per(food.carbs100),
            fatG       = per(food.fat100),
            fiberG     = per(food.fiber100),
            sugarG     = per(food.sugar100),
            nutriScore = food.nutriScore
        )
    }

    /** Same food, new amount: every nutrient scales with the grams. */
    fun rescale(meal: MealEntryEntity, newGrams: Float): MealEntryEntity {
        val old = meal.grams ?: return meal
        if (old <= 0f || newGrams <= 0f) return meal
        val r = newGrams / old
        fun s(v: Float?) = v?.let { it * r }
        return meal.copy(
            grams = newGrams, kcal = s(meal.kcal), proteinG = s(meal.proteinG),
            carbsG = s(meal.carbsG), fatG = s(meal.fatG),
            fiberG = s(meal.fiberG), sugarG = s(meal.sugarG)
        )
    }

    /** Starting suggestion for "how was it?" from the product's Nutri-Score. */
    fun suggestedQuality(nutriScore: String?): MealQuality? = when (nutriScore?.lowercase()) {
        "a", "b" -> MealQuality.WELL
        "c"      -> MealQuality.OK
        "d", "e" -> MealQuality.POOR
        else     -> null
    }
}

data class DayTotals(val kcal: Int, val proteinG: Int, val carbsG: Int, val fatG: Int, val hasUnknown: Boolean)

/** Sums a day's meals; [DayTotals.hasUnknown] flags entries logged without calories. */
fun List<MealEntryEntity>.totals(): DayTotals = DayTotals(
    kcal       = sumOf { (it.kcal ?: 0f).toDouble() }.roundToInt(),
    proteinG   = sumOf { (it.proteinG ?: 0f).toDouble() }.roundToInt(),
    carbsG     = sumOf { (it.carbsG ?: 0f).toDouble() }.roundToInt(),
    fatG       = sumOf { (it.fatG ?: 0f).toDouble() }.roundToInt(),
    hasUnknown = any { it.kcal == null && it.quality != MealQuality.SKIP.key }
)
