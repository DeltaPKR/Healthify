package com.healthify.app.data.repository

import androidx.room.withTransaction
import com.healthify.app.data.db.*
import com.healthify.app.firebase.FirebaseSync
import com.healthify.app.logs.ActivityType
import com.healthify.app.logs.LogSource
import com.healthify.app.logs.MealQuality
import com.healthify.app.logs.MealType
import com.healthify.app.score.HealthScore
import com.healthify.app.time.DayClock
import com.healthify.app.workout.WorkoutMath
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/**
 * All-day logs: water, meals, activities.
 *
 * Water rule: the water log is the source of truth. For any date with a
 * check-in, check_ins.waterGlasses always equals that date's log sum, and
 * the check-in's score is re-computed when it changes. Every write to the
 * log goes through [changeWater] so the two can't drift.
 *
 * Firestore writes are fire-and-forget on [syncScope]: callers include a
 * notification receiver, which must not wait for a network ack.
 */
class LogRepository(
    private val db: AppDatabase,
    private val syncScope: CoroutineScope,
) {
    private val waterDao   = db.waterLogDao()
    private val mealDao    = db.mealDao()
    private val workoutDao = db.workoutDao()
    private val checkInDao = db.checkInDao()
    private val userDao    = db.userDao()

    // ── Water ────────────────────────────────────────────────────────────────
    fun waterTotalFlow(date: String): Flow<Int> = waterDao.totalForDateFlow(date)
    suspend fun waterTotal(date: String = DayClock.todayIso()): Int = waterDao.totalForDate(date)

    /**
     * Adds [delta] glasses (removes when negative, newest logs first, never
     * below zero) and returns the day's new total.
     */
    suspend fun changeWater(delta: Int, source: String, date: String = DayClock.todayIso()): Int {
        if (delta == 0) return waterTotal(date)
        val (total, checkIn) = db.withTransaction {
            if (delta > 0) waterDao.insert(WaterLogEntity(date = date, glasses = delta, source = source))
            else removeNewest(date, -delta)
            val total = waterDao.totalForDate(date)
            total to mirrorIntoCheckIn(date, total)
        }
        syncScope.launch { FirebaseSync.syncWaterDay(date, total) }
        checkIn?.let { syncScope.launch { FirebaseSync.syncCheckIn(it) } }
        return total
    }

    private suspend fun removeNewest(date: String, glasses: Int) {
        var left = glasses
        for (log in waterDao.forDateNewestFirst(date)) {
            if (left <= 0) break
            if (log.glasses <= left) {
                waterDao.delete(log)
                left -= log.glasses
            } else {
                waterDao.update(log.copy(glasses = log.glasses - left))
                left = 0
            }
        }
    }

    /** Returns the re-scored check-in when one exists and changed. */
    private suspend fun mirrorIntoCheckIn(date: String, total: Int): CheckInEntity? {
        val ci = checkInDao.getCheckInForDate(date) ?: return null
        if (ci.waterGlasses == total) return null
        val updated = ci.copy(waterGlasses = total)
        val scored = updated.copy(wellnessScore = HealthScore.of(updated, userDao.getUserOnce()))
        checkInDao.upsert(scored)
        return scored
    }

    // ── Meals ────────────────────────────────────────────────────────────────
    fun mealsFlow(date: String): Flow<List<MealEntryEntity>> = mealDao.forDateFlow(date)
    suspend fun mealsFor(date: String = DayClock.todayIso()): List<MealEntryEntity> = mealDao.forDate(date)

    suspend fun saveMeal(meal: MealEntryEntity): MealEntryEntity {
        val stamped = meal.copy(updatedAt = System.currentTimeMillis())
        val rowId = mealDao.upsert(stamped)
        val saved = if (stamped.id == 0L) stamped.copy(id = rowId) else stamped
        syncScope.launch { FirebaseSync.syncMeal(saved) }
        return saved
    }

    /** Creates a meal for [date] or updates [existing] with the dialog's answers. */
    suspend fun saveMeal(
        existing: MealEntryEntity?,
        type: MealType,
        name: String,
        quality: MealQuality,
        date: String = DayClock.todayIso(),
    ): MealEntryEntity = saveMeal(
        (existing ?: MealEntryEntity(date = date, mealType = type.key, quality = quality.key))
            .copy(mealType = type.key, name = name, quality = quality.key)
    )

    suspend fun deleteMeal(meal: MealEntryEntity) {
        mealDao.delete(meal)
        syncScope.launch { FirebaseSync.deleteMeal(meal.syncId) }
    }

    // ── Activities ───────────────────────────────────────────────────────────
    fun workoutsFlow(date: String): Flow<List<WorkoutSessionEntity>> = workoutDao.forDateFlow(date)
    fun minutesByDayFlow(from: String, to: String): Flow<List<DayMinutes>> =
        workoutDao.minutesByDayFlow(from, to)

    /** Logs a finished activity of [minutes] that ended now. */
    suspend fun logActivity(type: ActivityType, minutes: Int, date: String = DayClock.todayIso()) {
        val end = System.currentTimeMillis()
        saveWorkout(
            WorkoutSessionEntity(
                date         = date,
                activityType = type.key,
                startedAt    = end - minutes * 60_000L,
                endedAt      = end,
                durationMin  = minutes,
                source       = LogSource.QUICK,
                kcalEstimate = kcalFor(type, minutes)
            )
        )
    }

    /** Edits a logged activity's kind and length, keeping its end time. */
    suspend fun updateActivity(session: WorkoutSessionEntity, type: ActivityType, minutes: Int) {
        val end = session.endedAt ?: System.currentTimeMillis()
        saveWorkout(
            session.copy(
                activityType = type.key,
                durationMin  = minutes,
                startedAt    = end - minutes * 60_000L,
                endedAt      = end,
                kcalEstimate = kcalFor(type, minutes)
            )
        )
    }

    private suspend fun kcalFor(type: ActivityType, minutes: Int): Float? =
        WorkoutMath.kcal(type.met, userDao.getUserOnce()?.weightKg ?: 0f, minutes)

    suspend fun saveWorkout(session: WorkoutSessionEntity): WorkoutSessionEntity {
        val stamped = session.copy(updatedAt = System.currentTimeMillis())
        val rowId = workoutDao.upsert(stamped)
        val saved = if (stamped.id == 0L) stamped.copy(id = rowId) else stamped
        syncScope.launch { FirebaseSync.syncWorkout(saved) }
        return saved
    }

    suspend fun deleteWorkout(session: WorkoutSessionEntity) {
        workoutDao.delete(session)
        syncScope.launch { FirebaseSync.deleteWorkout(session.syncId) }
    }
}
