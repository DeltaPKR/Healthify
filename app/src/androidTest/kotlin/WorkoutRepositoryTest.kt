package com.healthify.app.workout

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.healthify.app.data.db.AppDatabase
import com.healthify.app.data.db.UserEntity
import com.healthify.app.data.db.WorkoutSetEntity
import com.healthify.app.logs.ActivityType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Sessions, sets, records and routines against a real (in-memory) Room DB. */
@RunWith(AndroidJUnit4::class)
class WorkoutRepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var repo: WorkoutRepository

    @Before fun setUp() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java).build()
        // A cancelled scope: Firestore writes are launched but never run.
        repo = WorkoutRepository(ctx, db, CoroutineScope(Job().apply { cancel() }))
        runBlocking { db.userDao().upsert(UserEntity(weightKg = 70f)) }
    }

    @After fun tearDown() = db.close()

    private suspend fun squatWorkout(vararg kgReps: Pair<Float, Int>): Long {
        val routine = repo.routine(RoutineRef.builtIn("barbell_5x5"))!!
        Thread.sleep(5)   // distinct start times, as real workouts have
        val id = repo.start(routine)
        kgReps.forEachIndexed { i, (kg, reps) ->
            repo.saveSet(WorkoutSetEntity(sessionId = id, exerciseId = "Barbell_Squat", slot = 0, setIndex = i,
                reps = reps, weightKg = kg, completedAt = System.currentTimeMillis() + i))
        }
        repo.finish(id)
        return id
    }

    @Test fun startResumesTheOpenWorkout() = runBlocking {
        val a = repo.start(null)
        assertEquals(a, repo.start(repo.routine(RoutineRef.builtIn("gym_upper"))))
        assertEquals(a, repo.activeSessionFlow().first()!!.id)
        repo.discard(a)
        assertNull(repo.activeSessionFlow().first())
    }

    @Test fun recordsAcrossWorkouts() = runBlocking {
        val first = squatWorkout(60f to 5, 60f to 5)
        // First time: a baseline, no records.
        assertTrue(db.workoutSetDao().forSession(first).all { it.prType == null })
        val summary = repo.summary(first)!!
        assertEquals(setOf("Barbell_Squat"), summary.firstTimes)

        val second = squatWorkout(62.5f to 5, 65f to 3, 60f to 8)
        val sets = db.workoutSetDao().forSession(second).associateBy { it.setIndex }
        assertEquals(setOf(PrType.WEIGHT), PrType.parse(sets[1]!!.prType))      // 65 kg: heaviest
        assertEquals(setOf(PrType.E1RM), PrType.parse(sets[2]!!.prType))        // 60×8 ≈ 76 kg
        assertNull(sets[0]!!.prType)                                             // beaten later in the workout
        assertTrue(repo.summary(second)!!.firstTimes.isEmpty())

        val best = repo.best("Barbell_Squat")
        assertEquals(65f, best.maxWeight!!, 0.01f)
        assertEquals(60 * (1 + 8 / 30.0), best.bestE1rm!!, 0.01)
        assertEquals(3, repo.lastTime("Barbell_Squat", excludeSession = -1).size)

        val finished = db.workoutDao().byId(second)!!
        assertNotNull(finished.endedAt)
        assertTrue(finished.durationMin >= 1)
        assertEquals(ActivityType.STRENGTH.met * 70f * finished.durationMin / 60f, finished.kcalEstimate!!, 0.01f)
    }

    @Test fun deletingAWorkoutDeletesItsSets() = runBlocking {
        val id = squatWorkout(60f to 5)
        repo.delete(id)
        assertTrue(db.workoutSetDao().forSession(id).isEmpty())
        assertEquals(0, repo.best("Barbell_Squat").sets)
    }

    @Test fun removingAnExerciseDropsItsSets() = runBlocking {
        val routine = repo.routine(RoutineRef.builtIn("barbell_5x5"))!!
        val id = repo.start(routine)
        repo.saveSet(WorkoutSetEntity(sessionId = id, exerciseId = "Barbell_Squat", slot = 0, setIndex = 0, reps = 5, weightKg = 60f))
        repo.saveSet(WorkoutSetEntity(sessionId = id, exerciseId = "Bent_Over_Barbell_Row", slot = 2, setIndex = 0, reps = 5, weightKg = 40f))
        val plan = WorkoutPlan.fromJson(db.workoutDao().byId(id)!!.plan)
        repo.removeItem(id, plan, key = 0)
        assertEquals(listOf(1, 2), WorkoutPlan.fromJson(db.workoutDao().byId(id)!!.plan).map { it.key })
        assertEquals(listOf("Bent_Over_Barbell_Row"), db.workoutSetDao().forSession(id).map { it.exerciseId })
    }

    @Test fun customRoutines() = runBlocking {
        val ref = repo.duplicate(RoutineRef.builtIn("morning_mobility"))!!
        val copy = repo.routine(ref)!!
        assertTrue(copy.custom)
        assertEquals("morning_mobility", copy.basedOn)
        assertEquals(repo.routine(RoutineRef.builtIn("morning_mobility"))!!.items.map { it.exerciseId },
            copy.items.map { it.exerciseId })

        val edited = repo.saveRoutine(ref, "Short mornings", "☀️", ActivityType.STRETCH, copy.items.take(2))
        assertEquals(ref, edited)
        assertEquals(2, repo.routine(ref)!!.items.size)
        assertEquals("Short mornings", repo.routinesFlow().first().first().name)   // custom ones first

        repo.deleteRoutine(ref)
        assertNull(repo.routine(ref))
        assertEquals(0, db.query("SELECT * FROM routine_exercises", null).use { it.count })
    }

    @Test fun customExercises() = runBlocking {
        val e = repo.addCustomExercise("Sled push", Tracking.TIME, "quadriceps", "other")
        assertTrue(e.id.startsWith(ExerciseCatalog.CUSTOM_PREFIX))
        val all = repo.exercisesFlow().first()
        assertEquals(e.id, all.first().id)
        assertTrue(all.size > 800)
    }
}
