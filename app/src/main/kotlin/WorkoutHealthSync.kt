package com.healthify.app.health

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.metadata.Device
import androidx.health.connect.client.records.metadata.Metadata
import com.healthify.app.HealthifyApp
import com.healthify.app.data.db.AppDatabase
import com.healthify.app.data.db.WorkoutSessionEntity
import com.healthify.app.logs.ActivityType
import com.healthify.app.logs.LogSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.time.ZoneId

/**
 * Saves finished workouts and logged activities to Health Connect as
 * exercise sessions, once the user turns it on in Move settings (1.4.1).
 *
 * WRITE_EXERCISE is the app's only Health Connect write permission. It is
 * requested on its own, from that setting or the offer after a workout,
 * never together with the steps and sleep reads.
 *
 * Each session is written with clientRecordId = syncId and
 * clientRecordVersion = updatedAt, so writing an edited session replaces
 * its earlier copy. `workout_sessions.hcSyncedAt` marks what's written;
 * anything finished and not yet written, or edited since, is pending and
 * goes out on the next [syncPending]: at launch, after every save, and
 * when the setting is turned on (which also writes earlier workouts).
 * Deleting a written session deletes its copy; a delete that can't run
 * yet (Health Connect unavailable or access removed) waits in prefs.
 */
class WorkoutHealthSync(
    context: Context,
    private val db: AppDatabase,
    private val hc: HealthConnectManager,
    private val scope: CoroutineScope,
) {
    private val prefs = context.getSharedPreferences(HealthifyApp.PREFS_FILE, Context.MODE_PRIVATE)
    private val lock = Mutex()

    private val _enabled = MutableStateFlow(prefs.getBoolean(KEY_ENABLED, false))
    /** The user's choice. Writing also needs [writePermission], which can be revoked outside the app. */
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    val writePermission = HealthPermission.getWritePermission(ExerciseSessionRecord::class)
    val permissions = setOf(writePermission)

    val isAvailable: Boolean get() = hc.isAvailable
    val canInstall: Boolean get() = hc.canInstall

    suspend fun hasPermission(): Boolean = writePermission in hc.grantedPermissions()

    /** On and allowed: what the setting's switch shows. */
    suspend fun isOn(): Boolean = _enabled.value && hasPermission()

    fun setEnabled(on: Boolean) {
        prefs.edit().putBoolean(KEY_ENABLED, on).apply()
        _enabled.value = on
        if (on) requestSync()
    }

    /** The offer on the workout summary, until the user answers it. */
    val offerDismissed: Boolean get() = prefs.getBoolean(KEY_OFFER_DISMISSED, false)
    fun dismissOffer() = prefs.edit().putBoolean(KEY_OFFER_DISMISSED, true).apply()

    /** Writes whatever is pending, in the background. Call after saving a finished session. */
    fun requestSync() {
        scope.launch { syncPending() }
    }

    /** Call after deleting [session] (as read from the database) from Room. */
    fun onDeleted(session: WorkoutSessionEntity) {
        if (session.hcSyncedAt == null) return
        synchronized(prefs) {
            prefs.edit().putStringSet(KEY_PENDING_DELETES, pendingDeletes() + session.syncId).apply()
        }
        requestSync()
    }

    private fun pendingDeletes(): Set<String> =
        prefs.getStringSet(KEY_PENDING_DELETES, null)?.toSet().orEmpty()

    private fun clearPendingDelete(syncId: String) = synchronized(prefs) {
        prefs.edit().putStringSet(KEY_PENDING_DELETES, pendingDeletes() - syncId).apply()
    }

    /**
     * Runs pending deletes, then (when on) writes every pending session.
     * Anything that fails stays pending for the next run.
     */
    suspend fun syncPending() = lock.withLock {
        val deletes = pendingDeletes()
        val on = _enabled.value
        if (!on && deletes.isEmpty()) return@withLock
        if (!hasPermission()) return@withLock

        deletes.forEach { syncId ->
            // Health Connect throws for an id it doesn't have (already
            // deleted there by the user), so a failure isn't retried.
            runCatching { hc.deleteExerciseSession(syncId) }
                .onFailure { Log.w(TAG, "delete $syncId failed", it) }
            clearPendingDelete(syncId)
        }
        if (!on) return@withLock

        val dao = db.workoutDao()
        val device = Device(type = Device.TYPE_PHONE, manufacturer = Build.MANUFACTURER, model = Build.MODEL)
        dao.pendingHealthSync(MAX_PER_RUN).chunked(BATCH).forEach { chunk ->
            val records = chunk.associateWith { ExerciseSessions.recordFor(it, device) }
            val written = try {
                records.values.filterNotNull().takeIf { it.isNotEmpty() }?.let { hc.insert(it) }
                chunk
            } catch (e: Exception) {
                Log.w(TAG, "batch write failed, retrying one by one", e)
                // One bad record fails the whole batch; keep the good ones.
                chunk.filter { s ->
                    val r = records[s] ?: return@filter true
                    runCatching { hc.insert(listOf(r)) }.onFailure { Log.w(TAG, "write ${s.syncId} failed", it) }.isSuccess
                }
            }
            val now = System.currentTimeMillis()
            // Zero-length sessions have no record and count as written.
            written.forEach { dao.markHealthSynced(it.id, it.updatedAt, maxOf(now, it.updatedAt)) }
        }
    }

    companion object {
        private const val TAG = "WorkoutHealthSync"
        private const val KEY_ENABLED = "hc_write_workouts"
        private const val KEY_OFFER_DISMISSED = "hc_write_offer_dismissed"
        private const val KEY_PENDING_DELETES = "hc_pending_deletes"
        private const val BATCH = 100
        private const val MAX_PER_RUN = 2_000
    }
}

/** How a workout session looks in Health Connect. */
object ExerciseSessions {

    fun exerciseType(type: ActivityType): Int = when (type) {
        ActivityType.WALK     -> ExerciseSessionRecord.EXERCISE_TYPE_WALKING
        ActivityType.RUN      -> ExerciseSessionRecord.EXERCISE_TYPE_RUNNING
        ActivityType.CYCLE    -> ExerciseSessionRecord.EXERCISE_TYPE_BIKING
        ActivityType.YOGA     -> ExerciseSessionRecord.EXERCISE_TYPE_YOGA
        ActivityType.STRETCH  -> ExerciseSessionRecord.EXERCISE_TYPE_STRETCHING
        ActivityType.STRENGTH -> ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING
        // Health Connect has no plain "swimming"; a pool is the common case.
        ActivityType.SWIM     -> ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_POOL
        ActivityType.HIIT     -> ExerciseSessionRecord.EXERCISE_TYPE_HIGH_INTENSITY_INTERVAL_TRAINING
        ActivityType.OTHER    -> ExerciseSessionRecord.EXERCISE_TYPE_OTHER_WORKOUT
    }

    /**
     * The Health Connect copy of [s]: type, title, start and end, and
     * nothing else (no sets, weights, notes or calorie estimate). Null
     * while unfinished or if it has no length. Player workouts were timed
     * live on this phone; everything else was typed in.
     */
    fun recordFor(s: WorkoutSessionEntity, device: Device, zone: ZoneId = ZoneId.systemDefault()): ExerciseSessionRecord? {
        val end = s.endedAt ?: return null
        if (end <= s.startedAt) return null
        val start = Instant.ofEpochMilli(s.startedAt)
        val stop = Instant.ofEpochMilli(end)
        val type = ActivityType.of(s.activityType)
        val metadata =
            if (s.source == LogSource.WORKOUT) Metadata.activelyRecorded(device, s.syncId, s.updatedAt)
            else Metadata.manualEntry(s.syncId, s.updatedAt)
        return ExerciseSessionRecord(
            startTime       = start,
            startZoneOffset = zone.rules.getOffset(start),
            endTime         = stop,
            endZoneOffset   = zone.rules.getOffset(stop),
            metadata        = metadata,
            exerciseType    = exerciseType(type),
            title           = s.title.ifBlank { type.label },
        )
    }
}
