package com.healthify.app.data.repository

import com.healthify.app.data.db.*
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class AppRepository(
    private val userDao: UserDao,
    private val checkInDao: CheckInDao,
    private val reminderDao: ReminderDao
) {
    val iso: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    companion object {
        /** Daily check-in unlocks at this hour (24-h clock, local time). */
        const val CHECKIN_RESET_HOUR: Int = 18
    }

    // ── User ────────────────────────────────────────────────────────────────
    fun getUser(): Flow<UserEntity?> = userDao.getUser()
    suspend fun getUserOnce(): UserEntity? = userDao.getUserOnce()
    suspend fun saveUser(user: UserEntity) = userDao.upsert(user)
    suspend fun updateStreak(streak: Int, longest: Int, date: String) =
        userDao.updateStreak(streak, longest, date)

    // ── Check-ins ───────────────────────────────────────────────────────────
    fun getAllCheckIns(): Flow<List<CheckInEntity>> = checkInDao.getAllCheckIns()
    suspend fun getCheckInForToday(): CheckInEntity? =
        checkInDao.getCheckInForDate(LocalDate.now().format(iso))
    fun getCheckInForTodayFlow(): Flow<CheckInEntity?> =
        checkInDao.getCheckInForDateFlow(LocalDate.now().format(iso))
    suspend fun getRecentCheckIns(n: Int = 7): List<CheckInEntity> =
        checkInDao.getRecentCheckIns(n)
    suspend fun saveCheckIn(ci: CheckInEntity) = checkInDao.upsert(ci)
    suspend fun getCheckInsInRange(from: LocalDate, to: LocalDate): List<CheckInEntity> =
        checkInDao.getCheckInsInRange(from.format(iso), to.format(iso))
    suspend fun avgScoreSince(days: Int): Float {
        val from = LocalDate.now().minusDays(days.toLong()).format(iso)
        return checkInDao.avgScoreSince(from) ?: 0f
    }
    suspend fun totalCheckIns(): Int = checkInDao.totalCount()

    /** Most recent check-in, regardless of date. */
    suspend fun getMostRecentCheckIn(): CheckInEntity? =
        checkInDao.getRecentCheckIns(1).firstOrNull()

    /** Timestamp of the most recent check-in, or null if none. */
    suspend fun lastCheckInTimestamp(): Long? = getMostRecentCheckIn()?.timestamp

    /**
     * Returns (canCheckIn, msUntilNextAllowed).
     * Check-in unlocks at the next [CHECKIN_RESET_HOUR] (local time) after the
     * last check-in. So checking in at 14:00 unlocks at 18:00 the same day,
     * and checking in at 19:00 unlocks at 18:00 the next day.
     */
    suspend fun checkInCooldownStatus(now: Long = System.currentTimeMillis()): Pair<Boolean, Long> {
        val last = lastCheckInTimestamp() ?: return true to 0L
        val zone = ZoneId.systemDefault()
        val lastZdt = Instant.ofEpochMilli(last).atZone(zone)
        val sameDayReset = lastZdt.toLocalDate().atTime(CHECKIN_RESET_HOUR, 0).atZone(zone)
        val unlockZdt = if (lastZdt.isBefore(sameDayReset)) sameDayReset
                        else sameDayReset.plusDays(1)
        val unlockMs = unlockZdt.toInstant().toEpochMilli()
        return if (now >= unlockMs) true to 0L
        else false to (unlockMs - now)
    }

    // ── Reminders ───────────────────────────────────────────────────────────
    fun getAllReminders(): Flow<List<ReminderEntity>> = reminderDao.getAllReminders()
    suspend fun getEnabledReminders(): List<ReminderEntity> = reminderDao.getEnabledReminders()
    suspend fun saveReminder(reminder: ReminderEntity): Long = reminderDao.upsert(reminder)
    suspend fun toggleReminder(id: Int, enabled: Boolean) = reminderDao.setEnabled(id, enabled)
    suspend fun deleteReminder(reminder: ReminderEntity) = reminderDao.delete(reminder)
    suspend fun getReminderById(id: Int): ReminderEntity? = reminderDao.getById(id)

    // ── Seed default reminders ──────────────────────────────────────────────
    suspend fun seedDefaultReminders() {
        val count = reminderDao.totalCount()
        if (count > 0) return
        val defaults = listOf(
            ReminderEntity(label = "Drink Water",    emoji = "💧", hourOfDay = 8,  minute = 0,  category = "water",    repeatDays = "1,2,3,4,5,6,7"),
            ReminderEntity(label = "Drink Water",    emoji = "💧", hourOfDay = 10, minute = 0,  category = "water",    repeatDays = "1,2,3,4,5,6,7"),
            ReminderEntity(label = "Drink Water",    emoji = "💧", hourOfDay = 12, minute = 0,  category = "water",    repeatDays = "1,2,3,4,5,6,7"),
            ReminderEntity(label = "Drink Water",    emoji = "💧", hourOfDay = 14, minute = 0,  category = "water",    repeatDays = "1,2,3,4,5,6,7"),
            ReminderEntity(label = "Drink Water",    emoji = "💧", hourOfDay = 16, minute = 0,  category = "water",    repeatDays = "1,2,3,4,5,6,7"),
            ReminderEntity(label = "Morning Walk",   emoji = "🏃", hourOfDay = 7,  minute = 30, category = "movement", repeatDays = "1,2,3,4,5"),
            ReminderEntity(label = "Daily Check-in", emoji = "❤️", hourOfDay = 18, minute = 0,  category = "wellness", repeatDays = "1,2,3,4,5,6,7"),
            ReminderEntity(label = "Wind Down",      emoji = "🌙", hourOfDay = 22, minute = 0,  category = "wellness", repeatDays = "1,2,3,4,5,6,7"),
        )
        defaults.forEach { reminderDao.upsert(it) }
    }
}
