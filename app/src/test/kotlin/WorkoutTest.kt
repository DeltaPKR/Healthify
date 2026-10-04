package com.healthify.app.workout

import com.healthify.app.data.db.WorkoutSetEntity
import com.healthify.app.logs.ActivityType
import com.healthify.app.units.UnitSystem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

// Unit tests run with the module directory as the working directory.
private val library by lazy { ExerciseCatalog.parse(File("src/main/assets/exercises.json").readText()) }
private val routines by lazy { BuiltInRoutines.parse(File("src/main/assets/routines.json").readText()) }

class ExerciseCatalogTest {

    @Test fun libraryLoads() {
        assertTrue(library.size > 800)
        assertEquals(library.size, library.map { it.id }.toSet().size)
        assertTrue(library.all { it.name.isNotBlank() })
    }

    @Test fun trackingDefaults() {
        val byId = library.associateBy { it.id }
        assertEquals(Tracking.TIME, byId.getValue("Plank").tracking)               // static hold
        assertEquals(Tracking.TIME, byId.getValue("Childs_Pose").tracking)         // stretch
        assertEquals(Tracking.TIME, byId.getValue("Running_Treadmill").tracking)   // cardio
        assertEquals(Tracking.REPS, byId.getValue("Pushups").tracking)
        assertEquals(Tracking.REPS, byId.getValue("Pullups").tracking)
        assertEquals(Tracking.WEIGHT, byId.getValue("Barbell_Squat").tracking)
        assertEquals(Tracking.WEIGHT, byId.getValue("Triceps_Pushdown").tracking)  // cable
    }

    @Test fun search() {
        val hits = ExerciseCatalog.search(library, "push up")
        assertTrue(hits.isNotEmpty())
        assertTrue(hits.all { "push" in it.name.lowercase() && "up" in it.name.lowercase() })
        val chestDumbbell = ExerciseCatalog.search(library, "", MuscleGroup.CHEST, EquipmentGroup.DUMBBELL)
        assertTrue(chestDumbbell.isNotEmpty())
        assertTrue(chestDumbbell.all { "chest" in it.primaryMuscles && it.equipment == "dumbbell" })
        // The plain name ranks before its variations.
        assertEquals("Pushups", ExerciseCatalog.search(library, "pushups").first().id)
    }
}

class RoutinesTest {

    @Test fun builtInsAreComplete() {
        assertEquals(10, routines.size)
        val ids = library.map { it.id }.toSet()
        routines.forEach { r ->
            assertTrue(r.name, r.items.isNotEmpty())
            r.items.forEach { p ->
                assertTrue("${r.name}: unknown exercise ${p.exerciseId}", p.exerciseId in ids)
                assertTrue("${r.name}/${p.exerciseId}: needs reps or seconds", (p.reps == null) != (p.sec == null))
                assertTrue(p.sets in 1..PlanItem.MAX_SETS)
            }
            assertTrue(r.name, r.estimatedMinutes in 3..75)
        }
        assertEquals(ActivityType.STRETCH, routines.first { it.ref == RoutineRef.builtIn("evening_wind_down") }.activity)
    }

    @Test fun planJsonRoundTrip() {
        val items = listOf(
            PlanItem(0, "Barbell_Squat", 5, 5, null, 180, "Start light"),
            PlanItem(3, "Plank", 2, null, 45, 30),
        )
        assertEquals(items, WorkoutPlan.fromJson(WorkoutPlan.toJson(items)))
        assertEquals(emptyList<PlanItem>(), WorkoutPlan.fromJson(null))
        assertEquals(4, WorkoutPlan.nextKey(items))
    }

    @Test fun refs() {
        assertEquals("morning_mobility", RoutineRef.builtInId("builtin:morning_mobility"))
        assertNull(RoutineRef.customSyncId("builtin:morning_mobility"))
        assertEquals("abc", RoutineRef.customSyncId(RoutineRef.custom("abc")))
    }
}

class WorkoutMathTest {

    private var nextId = 1L
    private fun set(reps: Int? = null, kg: Float? = null, sec: Int? = null, warm: Boolean = false) =
        WorkoutSetEntity(id = nextId++, sessionId = 1, exerciseId = "x", slot = 0, setIndex = nextId.toInt(),
            reps = reps, weightKg = kg, durationSec = sec, isWarmup = warm, completedAt = nextId)

    @Test fun epley() {
        assertEquals(100.0, WorkoutMath.epley(100f, 1)!!, 0.001)
        assertEquals(116.667, WorkoutMath.epley(100f, 5)!!, 0.001)
        assertNull(WorkoutMath.epley(100f, 15))          // too many reps to trust
        assertNull(WorkoutMath.epley(null, 5))
    }

    @Test fun firstTimeIsABaseline() {
        assertEquals(emptySet<PrType>(), WorkoutMath.recordsFor(set(reps = 5, kg = 60f), Best(), Tracking.WEIGHT))
        assertEquals(emptyMap<Long, Set<PrType>>(), WorkoutMath.markRecords(listOf(set(reps = 5, kg = 60f)), Best(), Tracking.WEIGHT))
    }

    @Test fun weightRecords() {
        val before = Best(maxWeight = 60f, bestE1rm = 70.0, sets = 6)
        // Heavier, and a better 1RM estimate.
        assertEquals(setOf(PrType.WEIGHT, PrType.E1RM), WorkoutMath.recordsFor(set(reps = 5, kg = 62.5f), before, Tracking.WEIGHT))
        // Same weight, more reps: only the estimate improves.
        assertEquals(setOf(PrType.E1RM), WorkoutMath.recordsFor(set(reps = 8, kg = 60f), before, Tracking.WEIGHT))
        assertEquals(emptySet<PrType>(), WorkoutMath.recordsFor(set(reps = 8, kg = 62.5f, warm = true), before, Tracking.WEIGHT))
    }

    @Test fun oneWinnerPerRecordInAWorkout() {
        val before = Best(maxWeight = 60f, bestE1rm = 70.0, sets = 3)
        val a = set(reps = 5, kg = 62.5f)
        val b = set(reps = 5, kg = 65f)
        val c = set(reps = 3, kg = 62.5f)
        val marks = WorkoutMath.markRecords(listOf(a, b, c), before, Tracking.WEIGHT)
        assertEquals(mapOf(b.id to setOf(PrType.WEIGHT, PrType.E1RM)), marks)
    }

    @Test fun repsAndTimeRecords() {
        assertEquals(setOf(PrType.REPS), WorkoutMath.recordsFor(set(reps = 21), Best(maxReps = 20, sets = 4), Tracking.REPS))
        assertEquals(setOf(PrType.TIME), WorkoutMath.recordsFor(set(sec = 61), Best(maxSec = 60, sets = 2), Tracking.TIME))
        // A timed set of a reps exercise (an interval) is not a record.
        assertEquals(emptySet<PrType>(), WorkoutMath.recordsFor(set(sec = 90), Best(maxReps = 20, sets = 4), Tracking.REPS))
    }

    @Test fun bestFoldsSets() {
        val b = Best().with(set(reps = 5, kg = 80f)).with(set(reps = 12)).with(set(sec = 40)).with(set(reps = 1, kg = 200f, warm = true))
        assertEquals(80f, b.maxWeight!!, 0.001f)
        assertEquals(12, b.maxReps)
        assertEquals(40, b.maxSec)
        assertEquals(3, b.sets)
    }

    @Test fun volumeAndCalories() {
        assertEquals(500f, WorkoutMath.volumeKg(listOf(set(reps = 5, kg = 60f), set(reps = 5, kg = 40f), set(reps = 5, kg = 100f, warm = true))), 0.01f)
        assertEquals(350f, WorkoutMath.kcal(5f, 70f, 60)!!, 0.01f)
        assertNull(WorkoutMath.kcal(5f, 0f, 60))
    }

    @Test fun formatting() {
        assertEquals("1:05", WorkoutMath.clock(65))
        assertEquals("1:00:00", WorkoutMath.clock(3600))
        assertEquals("45 s", WorkoutMath.shortDuration(45))
        assertEquals("2 min", WorkoutMath.shortDuration(120))
        assertEquals(PrType.join(setOf(PrType.WEIGHT)), "weight")
        assertEquals(setOf(PrType.WEIGHT, PrType.E1RM), PrType.parse("weight,e1rm"))
    }
}

class WeightUnitsTest {
    @Test fun poundsInKilogramsStored() {
        val kg = Weight.parse("135", UnitSystem.IMPERIAL)!!
        assertEquals(61.235f, kg, 0.01f)
        assertEquals("135", Weight.plain(kg, UnitSystem.IMPERIAL))
        assertEquals(62.5f, Weight.parse("62,5", UnitSystem.METRIC)!!, 0.001f)
        assertEquals("62.5 kg", Weight.format(62.5f, UnitSystem.METRIC))
        assertNull(Weight.parse("abc", UnitSystem.METRIC))
    }
}
