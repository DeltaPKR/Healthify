package com.healthify.app.logs

/** Meal slots, in the order the Food tab lists them. */
enum class MealType(val key: String, val label: String, val emoji: String) {
    BREAKFAST("breakfast", "Breakfast", "🌅"),
    LUNCH("lunch", "Lunch", "🥪"),
    DINNER("dinner", "Dinner", "🍲"),
    SNACK("snack", "Snacks", "🍎");

    companion object {
        fun of(key: String): MealType = entries.firstOrNull { it.key == key } ?: SNACK

        /** Best guess for a meal logged at [hour] (0–23). */
        fun forHour(hour: Int): MealType = when (hour) {
            in 5..10  -> BREAKFAST
            in 11..15 -> LUNCH
            in 17..21 -> DINNER
            else      -> SNACK
        }
    }
}

/**
 * How a meal went. Same keys and points as the check-in's food question,
 * so meals logged through the day can pre-fill it.
 */
enum class MealQuality(val key: String, val label: String, val emoji: String, val points: Int) {
    WELL("well", "Healthy", "🥗", 10),
    OK("ok", "Decent", "🍽️", 7),
    POOR("poor", "Not great", "🍕", 3),
    SKIP("skip", "Skipped", "😕", 0);

    companion object {
        fun of(key: String?): MealQuality? = entries.firstOrNull { it.key == key }

        /**
         * The day's food answer from its logged meals: the average of their
         * points mapped back to a tag (≥ 8.5 well, ≥ 5 ok, ≥ 1.5 poor, else
         * skip). Null when nothing usable was logged.
         */
        fun forDay(mealQualities: List<String>): MealQuality? {
            val points = mealQualities.mapNotNull { of(it)?.points }
            if (points.isEmpty()) return null
            val avg = points.average()
            return when {
                avg >= 8.5 -> WELL
                avg >= 5.0 -> OK
                avg >= 1.5 -> POOR
                else       -> SKIP
            }
        }
    }
}

/** Quick-logged activity kinds (Move tab and Home quick-log). */
enum class ActivityType(val key: String, val label: String, val emoji: String) {
    WALK("walk", "Walk", "🚶"),
    RUN("run", "Run", "🏃"),
    CYCLE("cycle", "Cycle", "🚴"),
    YOGA("yoga", "Yoga", "🧘"),
    STRENGTH("strength", "Strength", "🏋️"),
    SWIM("swim", "Swim", "🏊"),
    HIIT("hiit", "HIIT", "⚡"),
    OTHER("other", "Other", "✨");

    companion object {
        fun of(key: String): ActivityType = entries.firstOrNull { it.key == key } ?: OTHER
    }
}

/** Where a water log or workout came from (stored for support/debugging). */
object LogSource {
    const val HOME         = "home"
    const val NOTIFICATION = "notification"
    const val CHECKIN      = "checkin"
    const val BACKFILL     = "backfill"   // water from pre-1.2.0 check-ins
    const val QUICK        = "quick"      // workout logged as type + minutes
}

/** Weekly activity target shown on the Move tab (WHO adult guideline). */
const val WEEKLY_ACTIVE_MINUTES_TARGET = 150
