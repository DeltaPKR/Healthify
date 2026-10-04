package com.healthify.app.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import java.util.UUID

// ═══════════════════════════════════════════════════════════════════════════
// WORKOUTS (DB v5)
// The exercise library and the built-in routines ship as assets and are
// never stored here; these tables hold only what the user creates: their
// routines, their own exercises, and the sets they log in the player.
// Exercise ids are free-exercise-db ids, or "custom:<syncId>" for the
// user's own exercises.
// ═══════════════════════════════════════════════════════════════════════════

/** A routine the user made or copied from a built-in one. */
@Entity(tableName = "routines", indices = [Index(value = ["syncId"], unique = true)])
data class RoutineEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val syncId: String = UUID.randomUUID().toString(),
    val name: String,
    val emoji: String = "💪",
    val activityType: String = "strength",   // ActivityType.key the finished workout is logged as
    val basedOn: String? = null,             // built-in routine id it was copied from
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

/** One exercise in a routine, with its targets. Exactly one of [reps] / [durationSec] is set. */
@Entity(
    tableName = "routine_exercises",
    foreignKeys = [ForeignKey(
        entity = RoutineEntity::class,
        parentColumns = ["id"],
        childColumns = ["routineId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("routineId")]
)
data class RoutineExerciseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val routineId: Long,
    val position: Int,
    val exerciseId: String,
    val sets: Int,
    val reps: Int?,
    val durationSec: Int?,
    val restSec: Int,
    @ColumnInfo(defaultValue = "")
    val note: String = ""
)

data class RoutineWithItems(
    @Embedded val routine: RoutineEntity,
    @Relation(parentColumn = "id", entityColumn = "routineId")
    val items: List<RoutineExerciseEntity>
)

/**
 * One completed set in a player session. [slot] is the stable key of the
 * session plan item it belongs to (so removing an exercise mid-workout
 * doesn't reshuffle sets). [prType] is filled in when the workout is
 * finished: comma-separated PrType keys this set set a record for.
 */
@Entity(
    tableName = "workout_sets",
    foreignKeys = [ForeignKey(
        entity = WorkoutSessionEntity::class,
        parentColumns = ["id"],
        childColumns = ["sessionId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("sessionId"), Index("exerciseId")]
)
data class WorkoutSetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val exerciseId: String,
    val slot: Int,
    val setIndex: Int,
    val reps: Int? = null,
    val weightKg: Float? = null,                // always kg; shown in lb for imperial users
    val durationSec: Int? = null,
    @ColumnInfo(defaultValue = "0")
    val isWarmup: Boolean = false,
    val prType: String? = null,
    val completedAt: Long = System.currentTimeMillis()
)

/** An exercise the user added because the library didn't have it. */
@Entity(tableName = "custom_exercises", indices = [Index(value = ["syncId"], unique = true)])
data class CustomExerciseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val syncId: String = UUID.randomUUID().toString(),
    val name: String,
    val tracking: String,                       // Tracking.key
    @ColumnInfo(defaultValue = "")
    val muscle: String = "",                    // primary muscle, library vocabulary
    @ColumnInfo(defaultValue = "")
    val equipment: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

/** An exercise's best non-warm-up results in finished workouts. */
data class ExerciseBestRow(
    val maxWeight: Float?,      // heaviest weight lifted for at least one rep
    val bestE1rm: Double?,      // best Epley estimate over sets of 1–12 reps
    val maxReps: Int?,          // most reps in an unweighted set
    val maxSec: Int?,           // longest timed set
    val sets: Int               // 0 = never done before
)

/** A logged set with the date of its workout, for an exercise's history. */
data class SetHistoryRow(
    @Embedded val set: WorkoutSetEntity,
    val date: String,
    val startedAt: Long
)

@Dao
interface RoutineDao {
    @Transaction
    @Query("SELECT * FROM routines ORDER BY updatedAt DESC")
    fun allFlow(): Flow<List<RoutineWithItems>>

    @Transaction
    @Query("SELECT * FROM routines WHERE syncId = :syncId")
    suspend fun bySyncId(syncId: String): RoutineWithItems?

    @Upsert
    suspend fun upsert(routine: RoutineEntity): Long

    @Insert
    suspend fun insertItems(items: List<RoutineExerciseEntity>)

    @Query("DELETE FROM routine_exercises WHERE routineId = :routineId")
    suspend fun deleteItems(routineId: Long)

    @Delete
    suspend fun delete(routine: RoutineEntity)
}

@Dao
interface WorkoutSetDao {
    @Query("SELECT * FROM workout_sets WHERE sessionId = :sessionId ORDER BY slot, setIndex")
    fun forSessionFlow(sessionId: Long): Flow<List<WorkoutSetEntity>>

    @Query("SELECT * FROM workout_sets WHERE sessionId = :sessionId ORDER BY slot, setIndex")
    suspend fun forSession(sessionId: Long): List<WorkoutSetEntity>

    @Upsert
    suspend fun upsert(set: WorkoutSetEntity): Long

    @Delete
    suspend fun delete(set: WorkoutSetEntity)

    @Query("DELETE FROM workout_sets WHERE sessionId = :sessionId AND slot = :slot")
    suspend fun deleteSlot(sessionId: Long, slot: Int)

    @Query("UPDATE workout_sets SET prType = :prType WHERE id = :id")
    suspend fun setPrType(id: Long, prType: String?)

    @Query(
        """SELECT MAX(CASE WHEN s.reps >= 1 THEN s.weightKg END) AS maxWeight,
                  MAX(CASE WHEN s.reps = 1 AND s.weightKg > 0 THEN s.weightKg
                           WHEN s.reps BETWEEN 2 AND 12 AND s.weightKg > 0
                           THEN s.weightKg * (1 + s.reps / 30.0) END) AS bestE1rm,
                  MAX(CASE WHEN s.weightKg IS NULL OR s.weightKg = 0 THEN s.reps END) AS maxReps,
                  MAX(s.durationSec) AS maxSec,
                  COUNT(*) AS sets
           FROM workout_sets s JOIN workout_sessions w ON w.id = s.sessionId
           WHERE s.exerciseId = :exerciseId AND s.isWarmup = 0
             AND w.endedAt IS NOT NULL AND w.id != :excludeSession"""
    )
    suspend fun best(exerciseId: String, excludeSession: Long): ExerciseBestRow

    /** Non-warm-up sets of [exerciseId] in finished workouts started before [before]. */
    @Query(
        """SELECT COUNT(*) FROM workout_sets s JOIN workout_sessions w ON w.id = s.sessionId
           WHERE s.exerciseId = :exerciseId AND s.isWarmup = 0
             AND w.endedAt IS NOT NULL AND w.startedAt < :before"""
    )
    suspend fun countBefore(exerciseId: String, before: Long): Int

    /** The sets of the most recent finished workout that included [exerciseId]. */
    @Query(
        """SELECT * FROM workout_sets WHERE exerciseId = :exerciseId AND sessionId = (
               SELECT w.id FROM workout_sessions w JOIN workout_sets s2 ON s2.sessionId = w.id
               WHERE s2.exerciseId = :exerciseId AND w.endedAt IS NOT NULL AND w.id != :excludeSession
               ORDER BY w.startedAt DESC LIMIT 1)
           ORDER BY slot, setIndex"""
    )
    suspend fun lastTime(exerciseId: String, excludeSession: Long): List<WorkoutSetEntity>

    @Query(
        """SELECT s.*, w.date AS date, w.startedAt AS startedAt
           FROM workout_sets s JOIN workout_sessions w ON w.id = s.sessionId
           WHERE s.exerciseId = :exerciseId AND w.endedAt IS NOT NULL
           ORDER BY w.startedAt DESC, s.setIndex LIMIT :limit"""
    )
    fun historyFlow(exerciseId: String, limit: Int = 60): Flow<List<SetHistoryRow>>
}

@Dao
interface CustomExerciseDao {
    @Query("SELECT * FROM custom_exercises ORDER BY name COLLATE NOCASE")
    fun allFlow(): Flow<List<CustomExerciseEntity>>

    @Query("SELECT * FROM custom_exercises ORDER BY name COLLATE NOCASE")
    suspend fun all(): List<CustomExerciseEntity>

    @Insert
    suspend fun insert(exercise: CustomExerciseEntity): Long
}
