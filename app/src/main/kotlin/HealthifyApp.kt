package com.healthify.app

import android.app.Application
import androidx.room.Room
import com.healthify.app.data.db.AppDatabase
import com.healthify.app.data.repository.AppRepository
import com.healthify.app.firebase.FirebaseSync
import com.healthify.app.health.HealthConnectManager
import com.healthify.app.notifications.NotificationChannels
import com.healthify.app.notifications.NotificationScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class HealthifyApp : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // ── Room ─────────────────────────────────────────────────────────────────
    val database by lazy {
        Room.databaseBuilder(this, AppDatabase::class.java, "healthify.db")
            .fallbackToDestructiveMigration()
            .build()
    }

    // ── Repository ───────────────────────────────────────────────────────────
    val repository by lazy {
        AppRepository(
            userDao     = database.userDao(),
            checkInDao  = database.checkInDao(),
            reminderDao = database.reminderDao()
        )
    }

    // ── Health Connect ───────────────────────────────────────────────────────
    val healthConnectManager by lazy { HealthConnectManager(this) }

    override fun onCreate() {
        super.onCreate()
        _instance = this

        // Create notification channels
        NotificationChannels.createAll(this)

        // Sign in to Firebase anonymously (offline-safe)
        appScope.launch {
            FirebaseSync.ensureSignedIn()

            // Seed default reminders on first launch
            repository.seedDefaultReminders()

            // Re-arm all enabled reminders. AlarmManager.set... is idempotent
            // per reminder id, so this safely overwrites any prior alarm.
            repository.getEnabledReminders().forEach { reminder ->
                NotificationScheduler.schedule(this@HealthifyApp, reminder)
            }
        }
    }

    companion object {
        private var _instance: HealthifyApp? = null
        val instance get() = _instance!!
    }
}
