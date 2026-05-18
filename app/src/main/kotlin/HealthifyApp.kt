package com.healthify.app

import android.app.Application
import androidx.room.Room
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.crashlytics.crashlytics
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

        // Crashlytics — disabled on debug builds so local crashes (which we'd
        // see in logcat anyway) don't pollute the production crash dashboard.
        // Toggling at runtime beats duplicating manifest entries per build type.
        Firebase.crashlytics.setCrashlyticsCollectionEnabled(!BuildConfig.DEBUG)

        // Create notification channels
        NotificationChannels.createAll(this)

        // Seed defaults + re-arm reminders in their own coroutine. The auth
        // launch below can hang or fail (e.g. SHA-1 mismatch on a Play-served
        // install) — if reminders waited on it, no alarms would ever fire on
        // affected devices. AlarmManager.set... is idempotent per reminder
        // id, so re-arming on every launch is safe.
        appScope.launch {
            repository.seedDefaultReminders()
            repository.getEnabledReminders().forEach { reminder ->
                NotificationScheduler.schedule(this@HealthifyApp, reminder)
            }
        }

        // Sign in to Firebase anonymously (offline-safe). Independent of
        // reminder scheduling above so a failure here cannot break alarms.
        appScope.launch {
            FirebaseSync.ensureSignedIn()
            // Tag crash reports with the anonymous Firebase UID so a single
            // user's crashes are de-duplicated server-side, without storing PII.
            Firebase.auth.currentUser?.uid?.let { Firebase.crashlytics.setUserId(it) }
        }
    }

    companion object {
        private var _instance: HealthifyApp? = null
        val instance get() = _instance!!
    }
}
