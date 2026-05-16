package com.healthify.app.health

import android.content.Context
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.HeartRateRecord
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
    val heartRateAvg: Int = 0,
    val isAvailable: Boolean = false
)

class HealthConnectManager(private val context: Context) {

    private val client: HealthConnectClient? by lazy {
        try {
            if (HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE)
                HealthConnectClient.getOrCreate(context)
            else null
        } catch (e: Exception) { null }
    }

    val isAvailable: Boolean get() = client != null

    val requiredPermissions = setOf(
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getReadPermission(SleepSessionRecord::class),
        HealthPermission.getReadPermission(HeartRateRecord::class),
    )

    suspend fun hasAllPermissions(): Boolean {
        val c = client ?: return false
        return try {
            val granted = c.permissionController.getGrantedPermissions()
            Log.d(TAG, "granted permissions: $granted")
            granted.containsAll(requiredPermissions)
        } catch (e: Exception) {
            Log.e(TAG, "hasAllPermissions failed", e)
            false
        }
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

    /** Read average resting heart rate from the last hour. */
    suspend fun readHeartRateAvg(): Int {
        val c = client ?: return 0
        return try {
            val endTime   = Instant.now()
            val startTime = endTime.minusSeconds(3600)
            val request = ReadRecordsRequest(
                recordType      = HeartRateRecord::class,
                timeRangeFilter = TimeRangeFilter.between(startTime, endTime)
            )
            val samples = c.readRecords(request).records.flatMap { it.samples }
            if (samples.isEmpty()) 0
            else samples.map { it.beatsPerMinute }.average().toInt()
        } catch (e: Exception) { 0 }
    }

    /** Fetch all health data in one call. */
    suspend fun readAll(): HealthData {
        if (!isAvailable) return HealthData(isAvailable = false)
        return try {
            HealthData(
                stepsToday        = readStepsToday(),
                sleepLastNightHours = readSleepLastNight(),
                heartRateAvg      = readHeartRateAvg(),
                isAvailable       = true
            )
        } catch (e: Exception) {
            HealthData(isAvailable = true) // available but read failed
        }
    }
}
