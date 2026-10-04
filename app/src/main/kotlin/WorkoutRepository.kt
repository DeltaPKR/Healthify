package com.healthify.app.workout

import android.content.Context
import androidx.room.withTransaction
import com.healthify.app.data.db.*
import com.healthify.app.firebase.FirebaseSync
import com.healthify.app.logs.ActivityType
import com.healthify.app.logs.LogSource
import com.healthify.app.time.DayClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/** What the summary screen shows for a finished workout. */
data class WorkoutSummary(
    val session: WorkoutSessionEntity,
    val sets: List<WorkoutSetEntity>,
    val exercises: Map<String, Exercise>,
    /** Exercises done for the first time in this workout. */
    val firstTimes: Set<String>,
)

/**
 * The Move pillar's data: the bundled exercise library and built-in
 * routines (assets, parsed once), the user's routines and exercises, and
 * workout-player sessions with their sets. Only one player session is open
 * at a time; starting another resumes it.
 */
class WorkoutRepository(
    private val context: Context,
    private val db: AppDatabase,
    private val syncScope: CoroutineScope,
) {
    private val sessions = db.workoutDao()
    private val setDao = db.workoutSetDao()
    private val routineDao = db.routineDao()
    private val customDao = db.customExerciseDao()

    // ── Library ──────────────────────────────────────────────────────────────
    private val loadLock = Mutex()
    @Volatile private var library: List<Exercise>? = null
    @Volatile private var builtIns: List<Routine>? = null

    private suspend fun asset(name: String): String = withContext(Dispatchers.IO) {
        context.assets.open(name).bufferedReader().use { it.readText() }
    }

    suspend fun library(): List<Exercise> = library ?: loadLock.withLock {
        library ?: withContext(Dispatchers.Default) { ExerciseCatalog.parse(asset("exercises.json")) }
            .also { library = it }
    }

    suspend fun builtInRoutines(): List<Routine> = builtIns ?: loadLock.withLock {
        builtIns ?: withContext(Dispatchers.Default) { BuiltInRoutines.parse(asset("routines.json")) }
            .also { builtIns = it }
    }

    /** The user's exercises first, then the library. */
    fun exercisesFlow(): Flow<List<Exercise>> =
        customDao.allFlow().map { custom -> custom.map(ExerciseCatalog::fromCustom) + library() }

    suspend fun exerciseMap(): Map<String, Exercise> =
        (customDao.all().map(ExerciseCatalog::fromCustom) + library()).associateBy { it.id }

    suspend fun addCustomExercise(name: String, tracking: Tracking, muscle: String, equipment: String): Exercise {
        val row = CustomExerciseEntity(name = name.trim(), tracking = tracking.key, muscle = muscle, equipment = equipment)
        val saved = row.copy(id = customDao.insert(row))
        syncScope.launch { FirebaseSync.syncCustomExercise(saved) }
        return ExerciseCatalog.fromCustom(saved)
    }

    fun historyFlow(exerciseId: String): Flow<List<SetHistoryRow>> = setDao.historyFlow(exerciseId)

    suspend fun best(exerciseId: String, excludeSession: Long = -1): Best =
        Best.of(setDao.best(exerciseId, excludeSession))

    suspend fun lastTime(exerciseId: String, excludeSession: Long): List<WorkoutSetEntity> =
        setDao.lastTime(exerciseId, excludeSession)

    // ── Routines ─────────────────────────────────────────────────────────────
    /** The user's routines (most recently edited first), then the built-in ones. */
    fun routinesFlow(): Flow<List<Routine>> =
        routineDao.allFlow().map { mine -> mine.map(Routine::fromEntity) + builtInRoutines() }

    suspend fun routine(ref: String): Routine? {
        RoutineRef.builtInId(ref)?.let { id -> return builtInRoutines().firstOrNull { it.ref == ref } }
        val syncId = RoutineRef.customSyncId(ref) ?: return null
        return routineDao.bySyncId(syncId)?.let(Routine::fromEntity)
    }

    /** Creates ([ref] null) or replaces a custom routine; returns its ref. */
    suspend fun saveRoutine(
        ref: String?,
        name: String,
        emoji: String,
        activity: ActivityType,
        items: List<PlanItem>,
        basedOn: String? = null,
    ): String {
        val saved = db.withTransaction {
            val existing = ref?.let(RoutineRef::customSyncId)?.let { routineDao.bySyncId(it) }?.routine
            val now = System.currentTimeMillis()
            val row = (existing ?: RoutineEntity(name = name, basedOn = basedOn)).copy(
                name = name.trim().ifBlank { "My routine" },
                emoji = emoji,
                activityType = activity.key,
                updatedAt = now
            )
            val id = routineDao.upsert(row).let { if (row.id == 0L) it else row.id }
            routineDao.deleteItems(id)
            routineDao.insertItems(Routine.itemsToEntities(id, items))
            routineDao.bySyncId(row.syncId)!!
        }
        syncScope.launch { FirebaseSync.syncRoutine(saved) }
        return RoutineRef.custom(saved.routine.syncId)
    }

    /** Copies any routine into a new custom one the user can edit; returns its ref. */
    suspend fun duplicate(ref: String): String? {
        val r = routine(ref) ?: return null
        return saveRoutine(
            ref = null,
            name = if (r.custom) "${r.name} (copy)" else r.name,
            emoji = r.emoji,
            activity = r.activity,
            items = r.items,
            basedOn = RoutineRef.builtInId(ref) ?: r.basedOn,
        )
    }

    suspend fun deleteRoutine(ref: String) {
        val syncId = RoutineRef.customSyncId(ref) ?: return
        val r = routineDao.bySyncId(syncId) ?: return
        routineDao.delete(r.routine)
        syncScope.launch { FirebaseSync.deleteRoutine(syncId) }
    }

    // ── Player sessions ──────────────────────────────────────────────────────
    fun activeSessionFlow(): Flow<WorkoutSessionEntity?> = sessions.activeFlow()
    fun sessionFlow(id: Long): Flow<WorkoutSessionEntity?> = sessions.byIdFlow(id)
    fun setsFlow(sessionId: Long): Flow<List<WorkoutSetEntity>> = setDao.forSessionFlow(sessionId)

    /**
     * Starts a workout from [routine] (null = an empty one to fill as you
     * go) and returns its session id. An unfinished workout is returned
     * instead, so two can never run at once.
     */
    suspend fun start(routine: Routine?): Long {
        sessions.active()?.let { return it.id }
        val items = routine?.items.orEmpty().mapIndexed { i, p -> p.copy(key = i) }
        val now = System.currentTimeMillis()
        return sessions.upsert(
            WorkoutSessionEntity(
                date         = DayClock.todayIso(),
                activityType = (routine?.activity ?: ActivityType.STRENGTH).key,
                title        = routine?.name ?: "Workout",
                startedAt    = now,
                endedAt      = null,
                durationMin  = 0,
                source       = LogSource.WORKOUT,
                routineRef   = routine?.ref,
                plan         = WorkoutPlan.toJson(items),
            )
        )
    }

    suspend fun savePlan(sessionId: Long, items: List<PlanItem>) {
        val s = sessions.byId(sessionId) ?: return
        sessions.upsert(s.copy(plan = WorkoutPlan.toJson(items), updatedAt = System.currentTimeMillis()))
    }

    /** Removes one exercise and the sets logged for it. */
    suspend fun removeItem(sessionId: Long, items: List<PlanItem>, key: Int) = db.withTransaction {
        setDao.deleteSlot(sessionId, key)
        savePlan(sessionId, items.filterNot { it.key == key })
    }

    suspend fun saveSet(set: WorkoutSetEntity): WorkoutSetEntity {
        val id = setDao.upsert(set)
        return if (set.id == 0L) set.copy(id = id) else set
    }

    suspend fun deleteSet(set: WorkoutSetEntity) = setDao.delete(set)

    /**
     * Ends the workout: stamps the end time and length, estimates calories
     * from the user's weight, and marks this workout's personal records
     * against every earlier finished workout.
     */
    suspend fun finish(sessionId: Long): WorkoutSessionEntity? {
        val exercises = exerciseMap()
        val weightKg = db.userDao().getUserOnce()?.weightKg ?: 0f
        val (finished, sets) = db.withTransaction {
            val s = sessions.byId(sessionId) ?: return@withTransaction null
            val sets = setDao.forSession(sessionId)
            sets.groupBy { it.exerciseId }.forEach { (exerciseId, list) ->
                val tracking = exercises[exerciseId]?.tracking ?: Tracking.REPS
                val marks = WorkoutMath.markRecords(list, best(exerciseId, sessionId), tracking)
                list.forEach { set -> setDao.setPrType(set.id, PrType.join(marks[set.id].orEmpty())) }
            }
            val end = System.currentTimeMillis()
            val minutes = ((end - s.startedAt) / 60_000.0).roundToInt().coerceAtLeast(1)
            val done = s.copy(
                endedAt      = end,
                durationMin  = minutes,
                kcalEstimate = WorkoutMath.kcal(ActivityType.of(s.activityType).met, weightKg, minutes),
                updatedAt    = end,
            )
            sessions.upsert(done)
            done to setDao.forSession(sessionId)
        } ?: return null
        syncScope.launch { FirebaseSync.syncWorkout(finished, sets) }
        return finished
    }

    /** Throws away an unfinished workout and its sets (never synced). */
    suspend fun discard(sessionId: Long) {
        sessions.byId(sessionId)?.takeIf { it.endedAt == null }?.let { sessions.delete(it) }
    }

    /** Deletes a finished workout, its sets and its Firestore copy. */
    suspend fun delete(sessionId: Long) {
        val s = sessions.byId(sessionId) ?: return
        sessions.delete(s)
        syncScope.launch { FirebaseSync.deleteWorkout(s.syncId) }
    }

    suspend fun summary(sessionId: Long): WorkoutSummary? {
        val s = sessions.byId(sessionId) ?: return null
        val sets = setDao.forSession(sessionId)
        val exercises = exerciseMap()
        val firstTimes = sets.filter { !it.isWarmup }.map { it.exerciseId }.distinct()
            .filter { setDao.countBefore(it, s.startedAt) == 0 }
            .toSet()
        return WorkoutSummary(s, sets, exercises, firstTimes)
    }
}
