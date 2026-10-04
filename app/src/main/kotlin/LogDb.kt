package com.healthify.app.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import java.util.UUID

// ═══════════════════════════════════════════════════════════════════════════
// ALL-DAY LOGS (DB v3)
// Water, meals and activities logged any time of day. The evening check-in
// pre-fills from these; the water log is the source of truth for a day's
// glasses (check_ins.waterGlasses mirrors its sum — see LogRepository).
// ═══════════════════════════════════════════════════════════════════════════

@Entity(tableName = "water_logs", indices = [Index("date")])
data class WaterLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,                     // ISO date (DayClock)
    val glasses: Int,                     // ≥ 1
    val loggedAt: Long = System.currentTimeMillis(),
    val source: String                    // LogSource
)

@Entity(
    tableName = "meal_entries",
    indices = [Index("date"), Index(value = ["syncId"], unique = true)]
)
data class MealEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val syncId: String = UUID.randomUUID().toString(),   // Firestore doc id
    val date: String,
    val mealType: String,                 // MealType.key
    val name: String = "",
    val quality: String,                  // MealQuality.key
    val loggedAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    // ── v4: food-based entries. A snapshot taken when logged, so history
    // doesn't move when Open Food Facts data does. Null for quick entries
    // (and for any nutrient the source didn't have).
    val foodItemId: Long? = null,
    val barcode: String? = null,
    val grams: Float? = null,
    val kcal: Float? = null,
    val proteinG: Float? = null,
    val carbsG: Float? = null,
    val fatG: Float? = null,
    val fiberG: Float? = null,
    val sugarG: Float? = null,
    val nutriScore: String? = null        // "a".."e"
)

/**
 * One activity / workout: either quick-logged (type + minutes) or done in
 * the workout player (source [LogSource.WORKOUT]), whose sets live in
 * workout_sets. [endedAt] null = a player session still in progress.
 */
@Entity(
    tableName = "workout_sessions",
    indices = [Index("date"), Index(value = ["syncId"], unique = true)]
)
data class WorkoutSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val syncId: String = UUID.randomUUID().toString(),
    val date: String,
    val activityType: String,             // ActivityType.key
    val title: String = "",
    val startedAt: Long,
    val endedAt: Long?,                   // null = still in progress
    val durationMin: Int,
    val source: String,                   // LogSource
    val updatedAt: Long = System.currentTimeMillis(),
    // ── v5: guided workouts.
    val routineRef: String? = null,       // RoutineRef ("builtin:…" / "custom:…"); null = quick log or empty workout
    val plan: String? = null,             // WorkoutPlan JSON: the exercises this session works through
    @ColumnInfo(defaultValue = "")
    val notes: String = "",
    val kcalEstimate: Float? = null,      // MET × body weight × hours; shown only with calorie counting on
    val hcSyncedAt: Long? = null          // when written to Health Connect (1.4.1)
)

/** Per-day minutes, for the Move tab's week bars. */
data class DayMinutes(val date: String, val minutes: Int)

@Dao
interface WaterLogDao {
    @Query("SELECT COALESCE(SUM(glasses), 0) FROM water_logs WHERE date = :date")
    fun totalForDateFlow(date: String): Flow<Int>

    @Query("SELECT COALESCE(SUM(glasses), 0) FROM water_logs WHERE date = :date")
    suspend fun totalForDate(date: String): Int

    @Query("SELECT * FROM water_logs WHERE date = :date ORDER BY loggedAt DESC, id DESC")
    suspend fun forDateNewestFirst(date: String): List<WaterLogEntity>

    @Insert
    suspend fun insert(log: WaterLogEntity): Long

    @Update
    suspend fun update(log: WaterLogEntity)

    @Delete
    suspend fun delete(log: WaterLogEntity)
}

@Dao
interface MealDao {
    @Query("SELECT * FROM meal_entries WHERE date = :date ORDER BY loggedAt, id")
    fun forDateFlow(date: String): Flow<List<MealEntryEntity>>

    @Query("SELECT * FROM meal_entries WHERE date = :date ORDER BY loggedAt, id")
    suspend fun forDate(date: String): List<MealEntryEntity>

    @Upsert
    suspend fun upsert(meal: MealEntryEntity): Long

    @Delete
    suspend fun delete(meal: MealEntryEntity)
}

@Dao
interface WorkoutDao {
    @Query("SELECT * FROM workout_sessions WHERE date = :date ORDER BY startedAt, id")
    fun forDateFlow(date: String): Flow<List<WorkoutSessionEntity>>

    @Query(
        """SELECT date, SUM(durationMin) AS minutes FROM workout_sessions
           WHERE date >= :from AND date <= :to AND endedAt IS NOT NULL
           GROUP BY date"""
    )
    fun minutesByDayFlow(from: String, to: String): Flow<List<DayMinutes>>

    @Query("SELECT * FROM workout_sessions WHERE id = :id")
    suspend fun byId(id: Long): WorkoutSessionEntity?

    @Query("SELECT * FROM workout_sessions WHERE id = :id")
    fun byIdFlow(id: Long): Flow<WorkoutSessionEntity?>

    /** The workout-player session not yet finished, if any (there is at most one). */
    @Query("SELECT * FROM workout_sessions WHERE endedAt IS NULL AND source = 'workout' ORDER BY startedAt DESC LIMIT 1")
    fun activeFlow(): Flow<WorkoutSessionEntity?>

    @Query("SELECT * FROM workout_sessions WHERE endedAt IS NULL AND source = 'workout' ORDER BY startedAt DESC LIMIT 1")
    suspend fun active(): WorkoutSessionEntity?

    /** Finished sessions not yet written to Health Connect, or edited since. */
    @Query(
        """SELECT * FROM workout_sessions
           WHERE endedAt IS NOT NULL AND (hcSyncedAt IS NULL OR hcSyncedAt < updatedAt)
           ORDER BY startedAt, id LIMIT :limit"""
    )
    suspend fun pendingHealthSync(limit: Int): List<WorkoutSessionEntity>

    /** Marks [id] written, unless it was edited after [version] was read. */
    @Query("UPDATE workout_sessions SET hcSyncedAt = :at WHERE id = :id AND updatedAt = :version")
    suspend fun markHealthSynced(id: Long, version: Long, at: Long)

    @Upsert
    suspend fun upsert(session: WorkoutSessionEntity): Long

    @Delete
    suspend fun delete(session: WorkoutSessionEntity)
}
