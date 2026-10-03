package com.healthify.app.data.repository

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.healthify.app.data.db.AppDatabase
import com.healthify.app.data.db.CheckInEntity
import com.healthify.app.data.db.UserEntity
import com.healthify.app.logs.LogSource
import com.healthify.app.score.HealthScore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** The water log is the source of truth; check_ins.waterGlasses mirrors its sum. */
@RunWith(AndroidJUnit4::class)
class LogRepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var logs: LogRepository
    private val day = "2026-10-03"
    private val user = UserEntity(waterGoalGlasses = 8, stepGoal = 8_000, sleepGoalHours = 8f)

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext, AppDatabase::class.java
        ).build()
        // A cancelled scope: Firestore writes are launched but never run.
        logs = LogRepository(db, CoroutineScope(Job().apply { cancel() }))
        runBlocking { db.userDao().upsert(user) }
    }

    @After fun tearDown() = db.close()

    @Test fun addAndRemoveWithoutCheckIn() = runBlocking {
        assertEquals(1, logs.changeWater(1, LogSource.HOME, day))
        assertEquals(3, logs.changeWater(2, LogSource.NOTIFICATION, day))
        assertEquals(2, logs.changeWater(-1, LogSource.HOME, day))      // splits the newest row
        assertEquals(0, logs.changeWater(-5, LogSource.HOME, day))      // never below zero
        assertNull(db.checkInDao().getCheckInForDate(day))
    }

    @Test fun checkInMirrorsLogAndIsRescored() = runBlocking {
        logs.changeWater(6, LogSource.CHECKIN, day)
        val ci = CheckInEntity(date = day, moodScore = 3, waterGlasses = 6, foodQuality = "ok",
            sleepHours = 7f, dayRating = 4, steps = 6_000)
        db.checkInDao().upsert(ci.copy(wellnessScore = HealthScore.of(ci, user)))

        logs.changeWater(1, LogSource.HOME, day)
        val after = db.checkInDao().getCheckInForDate(day)!!
        assertEquals(7, after.waterGlasses)
        assertEquals(HealthScore.of(after, user), after.wellnessScore)

        logs.changeWater(-2, LogSource.HOME, day)
        val after2 = db.checkInDao().getCheckInForDate(day)!!
        assertEquals(5, after2.waterGlasses)
        assertEquals(5, logs.waterTotal(day))
        assertEquals(HealthScore.of(after2, user), after2.wellnessScore)
    }

    @Test fun otherDaysUntouched() = runBlocking {
        logs.changeWater(2, LogSource.HOME, "2026-10-02")
        logs.changeWater(1, LogSource.HOME, day)
        assertEquals(2, logs.waterTotal("2026-10-02"))
        assertEquals(1, logs.waterTotal(day))
    }
}
