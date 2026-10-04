package com.healthify.app.workout

import com.healthify.app.data.db.ExerciseBestRow
import com.healthify.app.data.db.WorkoutSetEntity
import com.healthify.app.units.UnitSystem
import com.healthify.app.units.Units
import kotlin.math.roundToInt

/** The kinds of personal record a set can set. */
enum class PrType(val key: String, val label: String) {
    WEIGHT("weight", "Heaviest weight"),
    E1RM("e1rm", "Best estimated 1-rep max"),
    REPS("reps", "Most reps"),
    TIME("time", "Longest set");

    companion object {
        fun parse(keys: String?): Set<PrType> =
            keys.orEmpty().split(',').mapNotNull { k -> entries.firstOrNull { it.key == k } }.toSet()

        fun join(types: Set<PrType>): String? =
            types.takeIf { it.isNotEmpty() }?.joinToString(",") { it.key }
    }
}

/** An exercise's best results so far; [sets] 0 means it was never done. */
data class Best(
    val maxWeight: Float? = null,
    val bestE1rm: Double? = null,
    val maxReps: Int? = null,
    val maxSec: Int? = null,
    val sets: Int = 0,
) {
    val isFirstTime: Boolean get() = sets == 0

    /** Folds one more set in (for "best so far this workout"). */
    fun with(set: WorkoutSetEntity): Best {
        if (set.isWarmup) return this
        val w = set.weightKg?.takeIf { it > 0f && (set.reps ?: 0) >= 1 }
        val e = WorkoutMath.epley(set.weightKg, set.reps)
        val r = set.reps?.takeIf { set.weightKg == null || set.weightKg == 0f }
        return Best(
            maxWeight = maxOfNullable(maxWeight, w),
            bestE1rm = maxOfNullable(bestE1rm, e),
            maxReps = maxOfNullable(maxReps, r),
            maxSec = maxOfNullable(maxSec, set.durationSec),
            sets = sets + 1,
        )
    }

    companion object {
        fun of(row: ExerciseBestRow) = Best(row.maxWeight, row.bestE1rm, row.maxReps, row.maxSec, row.sets)
    }
}

private fun <T : Comparable<T>> maxOfNullable(a: T?, b: T?): T? = when {
    a == null -> b
    b == null -> a
    else      -> maxOf(a, b)
}

object WorkoutMath {

    /** Epley one-rep-max estimate, trusted for 1–12 reps only. */
    fun epley(weightKg: Float?, reps: Int?): Double? {
        if (weightKg == null || weightKg <= 0f || reps == null || reps !in 1..12) return null
        return if (reps == 1) weightKg.toDouble() else weightKg * (1 + reps / 30.0)
    }

    /**
     * Records [set] beats compared with [before] (all earlier sets, this
     * workout's included). Which measures count follows the exercise's
     * [tracking]: a timed interval of squats isn't a "longest hold" record.
     * Nothing counts when [before] is empty: the first time is a baseline.
     */
    fun recordsFor(set: WorkoutSetEntity, before: Best, tracking: Tracking): Set<PrType> {
        if (set.isWarmup || before.isFirstTime) return emptySet()
        val out = mutableSetOf<PrType>()
        when (tracking) {
            Tracking.WEIGHT -> {
                val w = set.weightKg
                if (w != null && w > 0f && (set.reps ?: 0) >= 1 && w > (before.maxWeight ?: 0f) + EPS) out += PrType.WEIGHT
                val e = epley(set.weightKg, set.reps)
                if (e != null && e > (before.bestE1rm ?: 0.0) + EPS) out += PrType.E1RM
            }
            Tracking.REPS -> {
                val r = set.reps
                if (r != null && (set.weightKg ?: 0f) == 0f && r > (before.maxReps ?: 0)) out += PrType.REPS
            }
            Tracking.TIME -> {
                val s = set.durationSec
                if (s != null && s > (before.maxSec ?: 0)) out += PrType.TIME
            }
        }
        return out
    }

    /**
     * Marks a finished workout's records: walking its sets in order, a set
     * gets a record type when it beats everything before it, history and
     * earlier sets in this workout alike; afterwards only the last set to
     * claim each type keeps it, so one lift shows one "heaviest".
     */
    fun markRecords(sets: List<WorkoutSetEntity>, history: Best, tracking: Tracking): Map<Long, Set<PrType>> {
        if (history.isFirstTime) return emptyMap()
        var best = history
        val winner = mutableMapOf<PrType, Long>()
        sets.sortedWith(compareBy({ it.completedAt }, { it.setIndex })).forEach { s ->
            recordsFor(s, best, tracking).forEach { winner[it] = s.id }
            best = best.with(s)
        }
        return winner.entries.groupBy({ it.value }, { it.key }).mapValues { it.value.toSet() }
    }

    /** Weight × reps over the non-warm-up sets, in kg. */
    fun volumeKg(sets: List<WorkoutSetEntity>): Float =
        sets.filter { !it.isWarmup }.sumOf { ((it.weightKg ?: 0f) * (it.reps ?: 0)).toDouble() }.toFloat()

    /** MET × body weight × hours. Null when the weight isn't known. */
    fun kcal(met: Float, weightKg: Float, minutes: Int): Float? =
        if (weightKg <= 0f || minutes <= 0) null else met * weightKg * minutes / 60f

    /** "1:05" for a timer, "45 s" / "2 min" for targets. */
    fun clock(totalSec: Int): String {
        val s = totalSec.coerceAtLeast(0)
        return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
    }

    fun shortDuration(sec: Int): String = when {
        sec < 60       -> "$sec s"
        sec % 60 == 0  -> "${sec / 60} min"
        else           -> "${(sec / 60f * 10).roundToInt() / 10f} min"
    }

    private const val EPS = 0.001
}

/** Weights are stored in kg; imperial users see and type pounds. */
object Weight {
    fun unitLabel(unit: UnitSystem) = if (unit == UnitSystem.IMPERIAL) "lb" else "kg"

    /** The number to show or pre-fill, in the user's unit, without trailing zeros. */
    fun plain(kg: Float, unit: UnitSystem): String =
        Units.plain(if (unit == UnitSystem.IMPERIAL) Units.kgToLb(kg) else kg, 1)

    fun format(kg: Float, unit: UnitSystem): String = "${plain(kg, unit)} ${unitLabel(unit)}"

    /** Parses what was typed (in the user's unit) to kg; null when empty or not a sensible weight. */
    fun parse(text: String, unit: UnitSystem): Float? {
        val v = text.replace(',', '.').toFloatOrNull()?.takeIf { it in 0f..2000f } ?: return null
        return if (unit == UnitSystem.IMPERIAL) Units.lbToKg(v) else v
    }
}
