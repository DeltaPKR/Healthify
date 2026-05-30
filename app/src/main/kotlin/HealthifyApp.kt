package com.healthify.app

import android.app.Application
import android.content.Context
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
        //
        // Seeding is one-shot per install (gated by KEY_DEFAULTS_SEEDED).
        // Previously seedDefaultReminders() re-added defaults whenever the
        // reminders table was empty, so deleting every reminder caused the
        // full default set to reappear on the next launch — testers
        // reported this as "the app silently re-creates notifications I
        // already deleted". With the flag, an empty reminders table is a
        // deliberate user choice we respect.
        appScope.launch {
            val prefs = getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)
            val alreadySeeded = prefs.getBoolean(KEY_DEFAULTS_SEEDED, false)
            if (!alreadySeeded) {
                repository.seedDefaultReminders()
                prefs.edit().putBoolean(KEY_DEFAULTS_SEEDED, true).apply()
            }
            // Cache the row id of the seeded Daily Check-in reminder so
            // the rest of the app (ReminderReceiver suppression rule,
            // NotificationsScreen edit-lock UI) can identify it by id —
            // not by category and not by display label. This is the
            // single source of truth that decouples the "skip notif if
            // user already checked in" semantic from the broader
            // "wellness" category. Before this fix any wellness-category
            // reminder inherited that suppression by accident; the
            // bedtime "Wind Down" default in particular went silent
            // whenever the user checked in after 18:00 because its
            // wellness tag was being read as "this IS the check-in
            // nudge". Looking up by label here is a one-shot bootstrap
            // step: once the id is cached, the label can be edited,
            // translated, or detached without breaking anything.
            if (prefs.getInt(KEY_CHECKIN_REMINDER_ID, -1) == -1) {
                repository.findReminderByLabel(DAILY_CHECKIN_LABEL)?.let { row ->
                    prefs.edit().putInt(KEY_CHECKIN_REMINDER_ID, row.id).apply()
                }
            }
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

        // ── Shared-preferences keys ──────────────────────────────────────
        // PREFS_FILE is the single backing file every key below lives in.
        // Other modules (NotificationsScreen, ReminderReceiver) read the
        // check-in-reminder id directly via SharedPreferences, so the
        // constants need to be visible outside this class.
        const val PREFS_FILE                = "healthify_prefs"
        const val KEY_CHECKIN_REMINDER_ID   = "checkin_reminder_id"

        // Flag persisted in SharedPreferences so the default-reminders
        // seed runs exactly once per install (see onCreate).
        private const val KEY_DEFAULTS_SEEDED = "default_reminders_seeded"

        // Label the Daily Check-in row is seeded with. Used ONLY at
        // bootstrap (HealthifyApp.onCreate) to locate the row and cache
        // its id; never read by the suppression rule or the UI at
        // run-time, so a later rename/translation does not detach the
        // id from the rules built on top of it.
        private const val DAILY_CHECKIN_LABEL = "Daily Check-in"
    }
}
