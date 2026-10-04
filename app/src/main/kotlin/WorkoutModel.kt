package com.healthify.app.workout

import com.healthify.app.data.db.CustomExerciseEntity
import com.healthify.app.data.db.RoutineExerciseEntity
import com.healthify.app.data.db.RoutineWithItems
import com.healthify.app.logs.ActivityType
import org.json.JSONArray
import org.json.JSONObject

// Pure models for the Move pillar: the exercise catalog, routines and a
// workout's plan. No Android dependencies, so all of it is unit-tested.

/** What a set of this exercise records by default. */
enum class Tracking(val key: String) {
    REPS("reps"),        // bodyweight: reps only
    WEIGHT("weight"),    // reps × weight
    TIME("time");        // a timed hold or interval

    companion object {
        fun of(key: String?): Tracking = entries.firstOrNull { it.key == key } ?: REPS
    }
}

data class Exercise(
    val id: String,
    val name: String,
    val level: String,
    val category: String,
    val equipment: String?,
    val force: String?,
    val primaryMuscles: List<String>,
    val secondaryMuscles: List<String>,
    val instructions: List<String>,
    val tracking: Tracking,
    val custom: Boolean = false,
)

/** Muscle filter chips; each groups the library's own muscle names. */
enum class MuscleGroup(val label: String, val muscles: Set<String>) {
    CHEST("Chest", setOf("chest")),
    BACK("Back", setOf("lats", "middle back", "lower back", "traps")),
    SHOULDERS("Shoulders", setOf("shoulders")),
    ARMS("Arms", setOf("biceps", "triceps", "forearms")),
    CORE("Core", setOf("abdominals")),
    LEGS("Legs", setOf("quadriceps", "hamstrings", "glutes", "calves", "adductors", "abductors")),
    NECK("Neck", setOf("neck"));

    companion object {
        fun of(muscle: String): MuscleGroup? = entries.firstOrNull { muscle in it.muscles }
    }
}

/** Equipment filter chips over the library's equipment names. */
enum class EquipmentGroup(val label: String, val equipment: Set<String?>) {
    NONE("No equipment", setOf("body only", null)),
    DUMBBELL("Dumbbells", setOf("dumbbell")),
    BARBELL("Barbell", setOf("barbell", "e-z curl bar")),
    MACHINE("Machines & cables", setOf("machine", "cable")),
    KETTLEBELL("Kettlebell", setOf("kettlebells")),
    OTHER("Other", setOf("bands", "medicine ball", "exercise ball", "foam roll", "other"));

    fun matches(e: Exercise) = e.equipment in equipment
}

object ExerciseCatalog {
    const val CUSTOM_PREFIX = "custom:"

    private val WEIGHTED = setOf("barbell", "dumbbell", "kettlebells", "cable", "machine", "e-z curl bar")

    /**
     * Default tracking from the library's fields: stretches, cardio, foam
     * rolling and static holds are timed; free weights, cables and machines
     * take a weight; everything else counts reps.
     */
    fun trackingFor(category: String, equipment: String?, force: String?): Tracking = when {
        category == "stretching" || category == "cardio" -> Tracking.TIME
        force == "static" || equipment == "foam roll"   -> Tracking.TIME
        equipment in WEIGHTED                            -> Tracking.WEIGHT
        else                                             -> Tracking.REPS
    }

    /** Parses the bundled free-exercise-db list (assets/exercises.json). */
    fun parse(json: String): List<Exercise> {
        val arr = JSONArray(json)
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val id = o.optString("id").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val category = o.optString("category")
            val equipment = o.str("equipment")
            val force = o.str("force")
            Exercise(
                id = id,
                name = o.optString("name").ifBlank { id.replace('_', ' ') },
                level = o.optString("level", "beginner"),
                category = category,
                equipment = equipment,
                force = force,
                primaryMuscles = o.strings("primaryMuscles"),
                secondaryMuscles = o.strings("secondaryMuscles"),
                instructions = o.strings("instructions"),
                tracking = trackingFor(category, equipment, force),
            )
        }
    }

    fun fromCustom(c: CustomExerciseEntity) = Exercise(
        id = CUSTOM_PREFIX + c.syncId,
        name = c.name,
        level = "",
        category = "custom",
        equipment = c.equipment.ifBlank { null },
        force = null,
        primaryMuscles = listOfNotNull(c.muscle.ifBlank { null }),
        secondaryMuscles = emptyList(),
        instructions = emptyList(),
        tracking = Tracking.of(c.tracking),
        custom = true,
    )

    /**
     * Name search plus filters. Every word of [query] must appear in the
     * name; names starting with the query rank first, then shorter names
     * (the plain "Pushups" before its variations).
     */
    fun search(
        all: List<Exercise>,
        query: String,
        muscle: MuscleGroup? = null,
        equipment: EquipmentGroup? = null,
    ): List<Exercise> {
        val words = query.lowercase().split(' ', '-', '_').filter { it.isNotBlank() }
        val q = query.trim().lowercase()
        return all.asSequence()
            .filter { e -> muscle == null || e.primaryMuscles.any { it in muscle.muscles } }
            .filter { e -> equipment == null || equipment.matches(e) }
            .filter { e ->
                val name = e.name.lowercase()
                words.all { it in name }
            }
            .sortedWith(
                compareBy<Exercise>({ !it.custom }, { q.isNotEmpty() && !it.name.lowercase().startsWith(q) }, { it.name.length }, { it.name })
            )
            .toList()
    }

    private fun JSONObject.str(key: String): String? =
        if (isNull(key)) null else optString(key).takeIf { it.isNotBlank() }

    private fun JSONObject.strings(key: String): List<String> {
        val a = optJSONArray(key) ?: return emptyList()
        return (0 until a.length()).mapNotNull { a.optString(it).takeIf { s -> s.isNotBlank() } }
    }
}

/**
 * One exercise in a routine or a workout, with its targets. Exactly one
 * of [reps] / [sec] is set: reps for a counted set (with a weight when the
 * exercise takes one), seconds for a timed set. [key] is stable within a
 * workout's plan, so logged sets stay attached when items move.
 */
data class PlanItem(
    val key: Int,
    val exerciseId: String,
    val sets: Int,
    val reps: Int?,
    val sec: Int?,
    val restSec: Int,
    val note: String = "",
) {
    val timed: Boolean get() = sec != null

    companion object {
        const val MAX_SETS = 20

        /** A new item with sensible targets for how [tracking] is recorded. */
        fun defaultFor(key: Int, exerciseId: String, tracking: Tracking) = when (tracking) {
            Tracking.TIME   -> PlanItem(key, exerciseId, sets = 2, reps = null, sec = 30, restSec = 30)
            Tracking.REPS   -> PlanItem(key, exerciseId, sets = 3, reps = 10, sec = null, restSec = 60)
            Tracking.WEIGHT -> PlanItem(key, exerciseId, sets = 3, reps = 10, sec = null, restSec = 90)
        }
    }
}

object RoutineRef {
    private const val BUILT_IN = "builtin:"
    private const val CUSTOM = "custom:"
    fun builtIn(id: String) = BUILT_IN + id
    fun custom(syncId: String) = CUSTOM + syncId
    fun builtInId(ref: String): String? = ref.takeIf { it.startsWith(BUILT_IN) }?.removePrefix(BUILT_IN)
    fun customSyncId(ref: String): String? = ref.takeIf { it.startsWith(CUSTOM) }?.removePrefix(CUSTOM)
}

data class Routine(
    val ref: String,
    val name: String,
    val emoji: String,
    val blurb: String,
    val level: String,
    val activity: ActivityType,
    val equipment: String,
    val items: List<PlanItem>,
    val basedOn: String? = null,
) {
    val custom: Boolean get() = RoutineRef.customSyncId(ref) != null
    val estimatedMinutes: Int get() = WorkoutPlan.estimatedMinutes(items)

    companion object {
        fun fromEntity(r: RoutineWithItems) = Routine(
            ref = RoutineRef.custom(r.routine.syncId),
            name = r.routine.name,
            emoji = r.routine.emoji,
            blurb = "",
            level = "",
            activity = ActivityType.of(r.routine.activityType),
            equipment = "",
            items = r.items.sortedBy { it.position }.mapIndexed { i, it ->
                PlanItem(i, it.exerciseId, it.sets, it.reps, it.durationSec, it.restSec, it.note)
            },
            basedOn = r.routine.basedOn,
        )

        fun itemsToEntities(routineId: Long, items: List<PlanItem>) = items.mapIndexed { i, p ->
            RoutineExerciseEntity(
                routineId = routineId, position = i, exerciseId = p.exerciseId,
                sets = p.sets, reps = p.reps, durationSec = p.sec, restSec = p.restSec, note = p.note
            )
        }
    }
}

object BuiltInRoutines {
    /** Parses assets/routines.json. */
    fun parse(json: String): List<Routine> {
        val arr = JSONArray(json)
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            Routine(
                ref = RoutineRef.builtIn(o.getString("id")),
                name = o.getString("name"),
                emoji = o.optString("emoji", "💪"),
                blurb = o.optString("blurb"),
                level = o.optString("level", "beginner"),
                activity = ActivityType.of(o.optString("activity", "strength")),
                equipment = o.optString("equipment"),
                items = WorkoutPlan.parseItems(o.getJSONArray("items")),
            )
        }
    }
}

/** A workout's plan, stored as JSON on its session row. */
object WorkoutPlan {
    fun toJson(items: List<PlanItem>): String = JSONArray().apply {
        items.forEach { p ->
            put(JSONObject().apply {
                put("key", p.key)
                put("ex", p.exerciseId)
                put("sets", p.sets)
                p.reps?.let { put("reps", it) }
                p.sec?.let { put("sec", it) }
                put("rest", p.restSec)
                if (p.note.isNotEmpty()) put("note", p.note)
            })
        }
    }.toString()

    fun fromJson(json: String?): List<PlanItem> =
        if (json.isNullOrBlank()) emptyList() else parseItems(JSONArray(json))

    internal fun parseItems(arr: JSONArray): List<PlanItem> = (0 until arr.length()).map { i ->
        val o = arr.getJSONObject(i)
        val sec = if (o.has("sec")) o.getInt("sec") else null
        PlanItem(
            key = o.optInt("key", i),
            exerciseId = o.getString("ex"),
            sets = o.optInt("sets", 1).coerceIn(1, PlanItem.MAX_SETS),
            reps = if (sec == null) o.optInt("reps", 10) else null,
            sec = sec,
            restSec = o.optInt("rest", 60).coerceAtLeast(0),
            note = o.optString("note"),
        )
    }

    /** Roughly 3 s a rep, the timed length, the rest after each set and 20 s to move between exercises. */
    fun estimatedMinutes(items: List<PlanItem>): Int {
        val sec = items.sumOf { p -> p.sets * ((p.sec ?: ((p.reps ?: 10) * 3)) + p.restSec) + 20 }
        return ((sec + 59) / 60).coerceAtLeast(1)
    }

    fun nextKey(items: List<PlanItem>): Int = (items.maxOfOrNull { it.key } ?: -1) + 1
}
