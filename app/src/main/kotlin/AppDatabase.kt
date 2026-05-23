package com.healthify.app.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

// ═══════════════════════════════════════════════════════════════════════════
// ENTITIES
// ═══════════════════════════════════════════════════════════════════════════

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey val id: Int = 0,         // singleton row
    val name: String = "",
    val age: Int = 0,
    val gender: String = "",
    val heightCm: Float = 0f,
    val weightKg: Float = 0f,
    val conditions: String = "",          // JSON-serialised list
    val goals: String = "",               // JSON-serialised list
    val stepGoal: Int = 10_000,
    val waterGoalGlasses: Int = 8,
    val sleepGoalHours: Float = 8f,
    val onboardingComplete: Boolean = false,
    val currentStreak: Int = 0,
    val longestStreak: Int = 0,
    val lastStreakDate: String = ""        // ISO date "2024-06-01"
)

@Entity(tableName = "check_ins")
data class CheckInEntity(
    @PrimaryKey val date: String = "",    // ISO date, one per day
    val moodScore: Int = -1,              // 0–4
    val waterGlasses: Int = 0,
    val foodQuality: String = "",         // "well","ok","poor","skip"
    val sleepHours: Float = 0f,
    val dayRating: Int = 0,               // 1–5
    val steps: Int = 0,                   // synced from Health Connect
    val heartRateAvg: Int = 0,
    val wellnessScore: Int = 0,           // computed 0–100
    val aiInsight: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "reminders")
data class ReminderEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val label: String = "",
    val emoji: String = "",
    val hourOfDay: Int = 8,
    val minute: Int = 0,
    val repeatDays: String = "1,2,3,4,5,6,7",  // comma-sep day-of-week (1=Mon)
    val enabled: Boolean = true,
    val category: String = "general",     // "water","meds","movement","wellness"
    val workerId: String = ""             // UUID of the WorkManager request
)

// ═══════════════════════════════════════════════════════════════════════════
// DAOs
// ═══════════════════════════════════════════════════════════════════════════

@Dao
interface UserDao {
    @Query("SELECT * FROM users WHERE id = 0")
    fun getUser(): Flow<UserEntity?>

    @Query("SELECT * FROM users WHERE id = 0")
    suspend fun getUserOnce(): UserEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(user: UserEntity)

    @Query("UPDATE users SET currentStreak=:streak, longestStreak=:longest, lastStreakDate=:date WHERE id=0")
    suspend fun updateStreak(streak: Int, longest: Int, date: String)
}

@Dao
interface CheckInDao {
    @Query("SELECT * FROM check_ins ORDER BY date DESC")
    fun getAllCheckIns(): Flow<List<CheckInEntity>>

    @Query("SELECT * FROM check_ins ORDER BY date DESC LIMIT :n")
    suspend fun getRecentCheckIns(n: Int): List<CheckInEntity>

    @Query("SELECT * FROM check_ins WHERE date = :date")
    suspend fun getCheckInForDate(date: String): CheckInEntity?

    @Query("SELECT * FROM check_ins WHERE date = :date")
    fun getCheckInForDateFlow(date: String): Flow<CheckInEntity?>

    @Query("SELECT * FROM check_ins WHERE date >= :from AND date <= :to ORDER BY date ASC")
    suspend fun getCheckInsInRange(from: String, to: String): List<CheckInEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(checkIn: CheckInEntity)

    @Query("SELECT AVG(wellnessScore) FROM check_ins WHERE date >= :from")
    suspend fun avgScoreSince(from: String): Float?

    @Query("SELECT COUNT(*) FROM check_ins")
    suspend fun totalCount(): Int
}

@Dao
interface ReminderDao {
    @Query("SELECT * FROM reminders ORDER BY hourOfDay, minute")
    fun getAllReminders(): Flow<List<ReminderEntity>>

    @Query("SELECT * FROM reminders WHERE enabled = 1")
    suspend fun getEnabledReminders(): List<ReminderEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(reminder: ReminderEntity): Long

    @Query("UPDATE reminders SET enabled=:enabled WHERE id=:id")
    suspend fun setEnabled(id: Int, enabled: Boolean)

    @Delete
    suspend fun delete(reminder: ReminderEntity)

    @Query("SELECT * FROM reminders WHERE id=:id")
    suspend fun getById(id: Int): ReminderEntity?

    @Query("SELECT * FROM reminders WHERE label = :label LIMIT 1")
    suspend fun findByLabel(label: String): ReminderEntity?

    @Query("SELECT COUNT(*) FROM reminders")
    suspend fun totalCount(): Int
}

// ═══════════════════════════════════════════════════════════════════════════
// DATABASE
// ═══════════════════════════════════════════════════════════════════════════

@Database(
    entities = [UserEntity::class, CheckInEntity::class, ReminderEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun checkInDao(): CheckInDao
    abstract fun reminderDao(): ReminderDao
}

// class Converters {
//     // Room type converters – currently unused (we store lists as JSON strings)
//     // kept for future complex types
// }
