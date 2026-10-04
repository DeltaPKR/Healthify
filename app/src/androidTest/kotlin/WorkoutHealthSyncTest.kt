package com.healthify.app.health

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.healthify.app.data.db.AppDatabase
import com.healthify.app.data.db.WorkoutSessionEntity
import com.healthify.app.data.repository.LogRepository
import com.healthify.app.logs.ActivityType
import com.healthify.app.logs.LogSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** What's pending for Health Connect, and a real write on a device that has it. */
@RunWith(AndroidJUnit4::class)
class WorkoutHealthSyncTest {

    private val ctx = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var db: AppDatabase
    private lateinit var sync: WorkoutHealthSync
    private lateinit var logs: LogRepository

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java).build()
        // A cancelled scope: background launches (Firestore, requestSync)
        // never run; the tests call syncPending themselves.
        val idle = CoroutineScope(Job().apply { cancel() })
        sync = WorkoutHealthSync(ctx, db, HealthConnectManager(ctx), idle)
        logs = LogRepository(db, idle, sync)
    }

    @After fun tearDown() {
        sync.setEnabled(false)
        db.close()
    }

    private fun walk(minutes: Int = 20) = WorkoutSessionEntity(
        date = "2026-07-01", activityType = ActivityType.WALK.key,
        startedAt = System.currentTimeMillis() - minutes * 60_000L, endedAt = System.currentTimeMillis(),
        durationMin = minutes, source = LogSource.QUICK,
    )

    @Test fun pendingUntilMarkedAndAgainAfterAnEdit() = runBlocking {
        val dao = db.workoutDao()
        val saved = logs.saveWorkout(walk())
        dao.upsert(walk().copy(endedAt = null))                       // unfinished: never pending
        assertEquals(listOf(saved.id), dao.pendingHealthSync(10).map { it.id })

        dao.markHealthSynced(saved.id, version = saved.updatedAt - 1, at = saved.updatedAt + 1)  // stale read
        assertEquals(1, dao.pendingHealthSync(10).size)
        dao.markHealthSynced(saved.id, version = saved.updatedAt, at = saved.updatedAt)
        assertTrue(dao.pendingHealthSync(10).isEmpty())

        Thread.sleep(2)
        // An edit from a copy read before the write keeps hcSyncedAt, and is pending again.
        val edited = logs.saveWorkout(saved.copy(durationMin = 25))
        assertEquals(saved.updatedAt, edited.hcSyncedAt)
        assertEquals(listOf(saved.id), dao.pendingHealthSync(10).map { it.id })
    }

    @Test fun writesAndDeletesInHealthConnect() = runBlocking {
        assumeTrue("Health Connect not available", sync.isAvailable)
        InstrumentationRegistry.getInstrumentation().uiAutomation
            .grantRuntimePermission(ctx.packageName, sync.writePermission)
        assumeTrue("couldn't grant ${sync.writePermission}", sync.hasPermission())

        val saved = logs.saveWorkout(walk())
        sync.syncPending()                                             // off: nothing written
        assertNull(db.workoutDao().byId(saved.id)!!.hcSyncedAt)

        sync.setEnabled(true)
        sync.syncPending()
        val written = db.workoutDao().byId(saved.id)!!
        assertNotNull(written.hcSyncedAt)

        // Rewriting an edit replaces the copy (same client id, higher version).
        Thread.sleep(2)
        logs.saveWorkout(written.copy(durationMin = 30, startedAt = written.endedAt!! - 30 * 60_000L))
        sync.syncPending()
        assertTrue(db.workoutDao().pendingHealthSync(10).isEmpty())

        logs.deleteWorkout(written)
        sync.syncPending()                                             // runs the queued delete
        assertNull(db.workoutDao().byId(saved.id))
    }
}
