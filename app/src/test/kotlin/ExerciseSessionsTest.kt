package com.healthify.app.health

import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.metadata.Device
import androidx.health.connect.client.records.metadata.Metadata
import com.healthify.app.data.db.WorkoutSessionEntity
import com.healthify.app.logs.ActivityType
import com.healthify.app.logs.LogSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

class ExerciseSessionsTest {

    private val phone = Device(type = Device.TYPE_PHONE, manufacturer = "Google", model = "Pixel")
    private val berlin = ZoneId.of("Europe/Berlin")
    private val start = Instant.parse("2026-07-01T06:30:00Z").toEpochMilli()

    private fun session(source: String, title: String = "", type: ActivityType = ActivityType.WALK, minutes: Int = 30) =
        WorkoutSessionEntity(
            syncId = "abc-123", date = "2026-07-01", activityType = type.key, title = title,
            startedAt = start, endedAt = start + minutes * 60_000L, durationMin = minutes,
            source = source, updatedAt = 1_234L,
        )

    @Test fun quickLogIsAManualEntry() {
        val r = ExerciseSessions.recordFor(session(LogSource.QUICK), phone, berlin)!!
        assertEquals(ExerciseSessionRecord.EXERCISE_TYPE_WALKING, r.exerciseType)
        assertEquals("Walk", r.title)                       // blank title → activity label
        assertEquals(Instant.ofEpochMilli(start), r.startTime)
        assertEquals(30L, java.time.Duration.between(r.startTime, r.endTime).toMinutes())
        assertEquals(ZoneOffset.ofHours(2), r.startZoneOffset)  // CEST in July
        assertEquals(Metadata.RECORDING_METHOD_MANUAL_ENTRY, r.metadata.recordingMethod)
        assertEquals("abc-123", r.metadata.clientRecordId)
        assertEquals(1_234L, r.metadata.clientRecordVersion)
        assertNull(r.notes)
    }

    @Test fun playerWorkoutWasRecordedOnThePhone() {
        val r = ExerciseSessions.recordFor(session(LogSource.WORKOUT, "Gym Upper Body", ActivityType.STRENGTH), phone, berlin)!!
        assertEquals(ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING, r.exerciseType)
        assertEquals("Gym Upper Body", r.title)
        assertEquals(Metadata.RECORDING_METHOD_ACTIVELY_RECORDED, r.metadata.recordingMethod)
        assertEquals(phone, r.metadata.device)
    }

    @Test fun nothingToWriteWithoutALength() {
        assertNull(ExerciseSessions.recordFor(session(LogSource.WORKOUT).copy(endedAt = null), phone, berlin))
        assertNull(ExerciseSessions.recordFor(session(LogSource.QUICK, minutes = 0), phone, berlin))
    }

    @Test fun everyActivityHasAType() {
        val types = ActivityType.entries.associateWith(ExerciseSessions::exerciseType)
        assertEquals(ExerciseSessionRecord.EXERCISE_TYPE_OTHER_WORKOUT, types[ActivityType.OTHER])
        assertEquals(ExerciseSessionRecord.EXERCISE_TYPE_STRETCHING, types[ActivityType.STRETCH])
        // Only OTHER falls back to the generic type.
        assertEquals(1, types.values.count { it == ExerciseSessionRecord.EXERCISE_TYPE_OTHER_WORKOUT })
    }
}
