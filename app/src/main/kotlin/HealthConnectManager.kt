package com.healthify.app.health

import android.content.Context
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

data class HealthData(
    val stepsToday: Int = 0,
    val sleepLastNightHours: Float = 0f,
    val isAvailable: Boolean = false
)

/**
 * One metric per Health Connect read permission the app declares.
 *
 * This is the single source of truth for the permission rationale: the
 * pre-permission screen renders it, and the Play Console Health Apps
 * Declaration copy in `docs/PLAY_CONSOLE.md` is kept in sync with it.
 * Adding an entry here without a screen that renders the value is what
 * gets an app rejected for "Excessive data access for declared feature".
 */
data class HealthPermissionRationale(
    val emoji: String,
    val dataType: String,
    /** Where the value is shown, and what it is used for. */
    val purpose: String
)

class HealthConnectManager(private val context: Context) {

    // Cached once available. Until then every access re-checks, so a user
    // who installs or updates Health Connect mid-session can use it
    // without restarting the app.
    @Volatile private var cachedClient: HealthConnectClient? = null
    private val client: HealthConnectClient?
        get() = cachedClient ?: try {
            if (sdkStatus() == HealthConnectClient.SDK_AVAILABLE)
                HealthConnectClient.getOrCreate(context).also { cachedClient = it }
            else null
        } catch (e: Exception) { null }

    val isAvailable: Boolean get() = client != null

    /** [HealthConnectClient.getSdkStatus], or unavailable if even that fails. */
    fun sdkStatus(): Int = try {
        HealthConnectClient.getSdkStatus(context)
    } catch (e: Exception) { HealthConnectClient.SDK_UNAVAILABLE }

    /**
     * Health Connect is missing or out of date but can be installed from
     * Google Play (Android 13 and lower).
     */
    val canInstall: Boolean
        get() = sdkStatus() == HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED

    /**
     * MINIMUM SCOPE — read-only, exactly the two data types the dashboard
     * renders. Every entry here has a matching read function below, a
     * matching card on the dashboard, and a matching entry in
     * [permissionRationales]. Keep those four in lockstep.
     *
     * The one write permission (exercise, 1.4.1) is not part of this set:
     * it's requested on its own from Move settings ([WorkoutHealthSync]),
     * so the Home connect card still only asks for steps and sleep.
     *
     * Heart rate, distance and active calories were declared in the
     * manifest up to 1.0.14 without ever being read; they were removed in
     * 1.0.15 to satisfy the Health Connect "Minimum Scope" requirement.
     */
    val requiredPermissions = setOf(
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getReadPermission(SleepSessionRecord::class),
    )

    /** User-facing explanation shown before the system permission sheet. */
    val permissionRationales = listOf(
        HealthPermissionRationale(
            emoji    = "👟",
            dataType = "Steps",
            purpose  = "Shows today's step count on your dashboard against your " +
                       "daily step goal, and feeds your daily wellness score."
        ),
        HealthPermissionRationale(
            emoji    = "😴",
            dataType = "Sleep",
            purpose  = "Shows how long you slept last night on your dashboard, " +
                       "and feeds the same daily wellness score."
        ),
    )

    suspend fun hasAllPermissions(): Boolean = grantedPermissions().containsAll(requiredPermissions)

    /** Every Health Connect permission granted to the app; empty if unavailable. */
    suspend fun grantedPermissions(): Set<String> {
        val c = client ?: return emptySet()
        return try {
            c.permissionController.getGrantedPermissions().also { Log.d(TAG, "granted permissions: $it") }
        } catch (e: Exception) {
            Log.e(TAG, "getGrantedPermissions failed", e)
            emptySet()
        }
    }

    /**
     * Writes [records], replacing earlier copies with the same client
     * record id and a lower version. Throws on failure so the caller can
     * retry later.
     */
    suspend fun insert(records: List<Record>) {
        val c = checkNotNull(client) { "Health Connect unavailable" }
        c.insertRecords(records)
    }

    /** Deletes the app's exercise session written with [clientRecordId]. Throws on failure. */
    suspend fun deleteExerciseSession(clientRecordId: String) {
        val c = checkNotNull(client) { "Health Connect unavailable" }
        c.deleteRecords(ExerciseSessionRecord::class, recordIdsList = emptyList(), clientRecordIdsList = listOf(clientRecordId))
    }

    companion object { private const val TAG = "HealthConnect" }

    /** Read today's step count. Falls back to 0 on any error. */
    suspend fun readStepsToday(): Int {
        val c = client ?: run { Log.w(TAG, "HC client null"); return 0 }
        return try {
            val today     = LocalDate.now()
            val startTime = today.atStartOfDay(ZoneId.systemDefault()).toInstant()
            val endTime   = Instant.now()
            val request = ReadRecordsRequest(
                recordType  = StepsRecord::class,
                timeRangeFilter = TimeRangeFilter.between(startTime, endTime)
            )
            val records = c.readRecords(request).records
            val total = records.sumOf { it.count }.toInt()
            Log.d(TAG, "readStepsToday: ${records.size} records, total=$total (start=$startTime end=$endTime)")
            records.forEach { Log.d(TAG, "  record: ${it.count} steps from ${it.startTime} to ${it.endTime} src=${it.metadata.dataOrigin.packageName}") }
            total
        } catch (e: Exception) {
            Log.e(TAG, "readStepsToday failed", e)
            0
        }
    }

    /**
     * Read the total sleep duration the user logged for "last night".
     * Window: yesterday 18:00 (local) → now. This covers standard sleepers,
     * late risers (woke after noon), and night-shift workers whose main
     * session ends in the afternoon. Sessions overlapping the window are
     * summed and clipped to the window edges so we don't double-count.
     */
    suspend fun readSleepLastNight(): Float {
        val c = client ?: return 0f
        return try {
            val zone        = ZoneId.systemDefault()
            val yesterday   = LocalDate.now().minusDays(1)
            val startTime   = yesterday.atTime(18, 0).atZone(zone).toInstant()
            val endTime     = Instant.now()
            val request = ReadRecordsRequest(
                recordType      = SleepSessionRecord::class,
                timeRangeFilter = TimeRangeFilter.between(startTime, endTime)
            )
            val totalMillis = c.readRecords(request).records.sumOf { rec ->
                val s = if (rec.startTime.isBefore(startTime)) startTime else rec.startTime
                val e = if (rec.endTime.isAfter(endTime))   endTime   else rec.endTime
                maxOf(0L, ChronoUnit.MILLIS.between(s, e))
            }
            Log.d(TAG, "readSleepLastNight: ${totalMillis / 3_600_000f}h (window=$startTime → $endTime)")
            totalMillis / 3_600_000f
        } catch (e: Exception) {
            Log.e(TAG, "readSleepLastNight failed", e)
            0f
        }
    }

    /**
     * Fetch all health data in one call. On-demand only — the app never
     * polls Health Connect in the background.
     */
    suspend fun readAll(): HealthData {
        if (!isAvailable) return HealthData(isAvailable = false)
        return try {
            HealthData(
                stepsToday          = readStepsToday(),
                sleepLastNightHours = readSleepLastNight(),
                isAvailable         = true
            )
        } catch (e: Exception) {
            HealthData(isAvailable = true) // available but read failed
        }
    }
}
