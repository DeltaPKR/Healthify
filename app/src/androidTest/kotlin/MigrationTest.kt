package com.healthify.app.data.db

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Every schema step must have a case here. Rows are inserted with raw SQL
 * against the *old* schema (as a real 1.0.16 install would hold them), then
 * migrated and read back.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    private val testDb = "migration-test.db"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
    )

    @Test
    fun migrate1To2() {
        helper.createDatabase(testDb, 1).use { seedV1(it) }

        val db = helper.runMigrationsAndValidate(testDb, 2, true)

        db.query("SELECT heightCm, weightKg, unitSystem, currentStreak FROM users WHERE id = 0").use {
            it.moveToFirst()
            assertEquals(5.1f, it.getFloat(0), 0.001f)       // repaired later, at app start
            assertEquals(154f, it.getFloat(1), 0.001f)
            assertEquals("metric", it.getString(2))
            assertEquals(4, it.getInt(3))
        }
        db.query("SELECT COUNT(*) FROM check_ins").use {
            it.moveToFirst(); assertEquals(3, it.getInt(0))
        }
        db.query("SELECT * FROM check_ins WHERE date = '2026-10-01'").use {
            it.moveToFirst()
            assertEquals(-1, it.getColumnIndex("heartRateAvg"))
            assertEquals(-1, it.getColumnIndex("aiInsight"))
            assertEquals(6, it.getInt(it.getColumnIndexOrThrow("waterGlasses")))
            assertEquals(7.5f, it.getFloat(it.getColumnIndexOrThrow("sleepHours")), 0.001f)
            assertEquals(71, it.getInt(it.getColumnIndexOrThrow("wellnessScore")))
        }
        // Reminder ids must survive: KEY_CHECKIN_REMINDER_ID caches one.
        db.query("SELECT id, label FROM reminders ORDER BY id").use {
            assertEquals(9, it.count)
            it.moveToPosition(6)
            assertEquals(7, it.getInt(0))
            assertEquals("Daily Check-in", it.getString(1))
        }
        db.close()
    }

    @Test
    fun migrate2To3() {
        helper.createDatabase(testDb, 2).use { db ->
            db.execSQL(
                """INSERT INTO check_ins (date, moodScore, waterGlasses, foodQuality, sleepHours,
                   dayRating, steps, wellnessScore, timestamp) VALUES
                   ('2026-09-30', 3, 8, 'well', 8.0, 4, 9100, 84, 1759236000000),
                   ('2026-10-01', 3, 0, 'well', 7.5, 4, 6400, 60, 1759322000000)"""
            )
        }

        val db = helper.runMigrationsAndValidate(testDb, 3, true)

        // One backfill row per check-in with water; none for 0 glasses.
        db.query("SELECT date, glasses, loggedAt, source FROM water_logs ORDER BY date").use {
            assertEquals(1, it.count)
            it.moveToFirst()
            assertEquals("2026-09-30", it.getString(0))
            assertEquals(8, it.getInt(1))
            assertEquals(1759236000000L, it.getLong(2))
            assertEquals("backfill", it.getString(3))
        }
        db.query("SELECT COUNT(*) FROM meal_entries").use { it.moveToFirst(); assertEquals(0, it.getInt(0)) }
        db.query("SELECT COUNT(*) FROM workout_sessions").use { it.moveToFirst(); assertEquals(0, it.getInt(0)) }
        db.close()
    }

    @Test
    fun migrate3To4() {
        helper.createDatabase(testDb, 3).use { db ->
            db.execSQL(
                """INSERT INTO users (id, name, age, gender, heightCm, weightKg, conditions, goals,
                   stepGoal, waterGoalGlasses, sleepGoalHours, onboardingComplete, currentStreak,
                   longestStreak, lastStreakDate, unitSystem)
                   VALUES (0, 'Sam', 34, 'Male', 177.8, 69.85, '', '', 8000, 8, 8, 1, 2, 5, '2026-10-03', 'metric')"""
            )
            db.execSQL(
                """INSERT INTO meal_entries (syncId, date, mealType, name, quality, loggedAt, updatedAt)
                   VALUES ('s1', '2026-10-03', 'lunch', 'Salad bowl', 'well', 1, 1)"""
            )
        }

        val db = helper.runMigrationsAndValidate(testDb, 4, true)

        db.query("SELECT countCalories, activityLevel, calorieGoal, calorieTargetOverride, name FROM users").use {
            it.moveToFirst()
            assertEquals(0, it.getInt(0))
            assertEquals("", it.getString(1))
            assertEquals("maintain", it.getString(2))
            assertEquals(0, it.getInt(3))
            assertEquals("Sam", it.getString(4))
        }
        db.query("SELECT name, quality, kcal, grams, foodItemId FROM meal_entries").use {
            it.moveToFirst()
            assertEquals("Salad bowl", it.getString(0))
            assertEquals("well", it.getString(1))
            assertTrue(it.isNull(2)); assertTrue(it.isNull(3)); assertTrue(it.isNull(4))
        }
        db.query("SELECT COUNT(*) FROM food_items").use { it.moveToFirst(); assertEquals(0, it.getInt(0)) }
        db.close()
    }

    /** Opens a v1 file through the real builder (no destructive fallback) and DAOs. */
    @Test
    fun migrateAllThroughRoom() {
        helper.createDatabase(testDb, 1).use { seedV1(it) }

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val room = Room.databaseBuilder(context, AppDatabase::class.java, testDb)
            .fallbackToDestructiveMigrationOnDowngrade()
            .build()
        try {
            runBlocking {
                val user = room.userDao().getUserOnce()
                assertNotNull(user)
                assertEquals("Sam", user!!.name)
                assertEquals("metric", user.unitSystem)
                assertEquals(3, room.checkInDao().totalCount())
                assertEquals(71, room.checkInDao().getCheckInForDate("2026-10-01")!!.wellnessScore)
                val reminders = room.reminderDao().getAllReminders().first()
                assertEquals(9, reminders.size)
                assertFalse(reminders.first { it.label == "Custom stretch" }.enabled)
                // v3: each day's water log total equals its check-in's glasses.
                room.checkInDao().getAllOnce().forEach { ci ->
                    assertEquals(ci.waterGlasses, room.waterLogDao().totalForDate(ci.date))
                }
            }
        } finally {
            room.close()
        }
    }

    private fun seedV1(db: SupportSQLiteDatabase) {
        // 1.0.16 saved imperial input raw: 5.10 ft → 5.1, 154 lb → 154.
        db.execSQL(
            """INSERT INTO users (id, name, age, gender, heightCm, weightKg, conditions, goals,
               stepGoal, waterGoalGlasses, sleepGoalHours, onboardingComplete,
               currentStreak, longestStreak, lastStreakDate)
               VALUES (0, 'Sam', 34, 'Non-binary', 5.1, 154, 'Asthma', 'Drink more water,Sleep better',
               8000, 8, 7.5, 1, 4, 9, '2026-10-01')"""
        )
        val checkIns = listOf(
            "('2026-09-29', 2, 5, 'ok',   6.0, 3, 4200, 72, 58, '', 1759150000000)",
            "('2026-09-30', 3, 8, 'well', 8.0, 4, 9100,  0, 84, '', 1759236000000)",
            "('2026-10-01', 3, 6, 'well', 7.5, 4, 6400,  0, 71, '', 1759322000000)",
        )
        checkIns.forEach {
            db.execSQL(
                """INSERT INTO check_ins (date, moodScore, waterGlasses, foodQuality, sleepHours,
                   dayRating, steps, heartRateAvg, wellnessScore, aiInsight, timestamp) VALUES $it"""
            )
        }
        val reminders = listOf(
            "'Drink Water', '💧', 8, 0, '1,2,3,4,5,6,7', 1, 'water'",
            "'Drink Water', '💧', 10, 0, '1,2,3,4,5,6,7', 1, 'water'",
            "'Drink Water', '💧', 12, 0, '1,2,3,4,5,6,7', 1, 'water'",
            "'Drink Water', '💧', 14, 0, '1,2,3,4,5,6,7', 1, 'water'",
            "'Drink Water', '💧', 16, 0, '1,2,3,4,5,6,7', 1, 'water'",
            "'Morning Walk', '🏃', 7, 30, '1,2,3,4,5', 1, 'movement'",
            "'Daily Check-in', '❤️', 18, 0, '1,2,3,4,5,6,7', 1, 'wellness'",
            "'Wind Down', '🌙', 22, 0, '1,2,3,4,5,6,7', 1, 'wellness'",
            "'Custom stretch', '🧘', 15, 15, '6,7', 0, 'movement'",
        )
        reminders.forEach {
            db.execSQL(
                """INSERT INTO reminders (label, emoji, hourOfDay, minute, repeatDays, enabled,
                   category, workerId) VALUES ($it, '')"""
            )
        }
    }
}
