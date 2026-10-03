package com.healthify.app

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.work.WorkManager
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.crashlytics.crashlytics
import com.healthify.app.data.db.AppDatabase
import com.healthify.app.data.db.DbBackup
import com.healthify.app.data.repository.AppRepository
import com.healthify.app.data.repository.LogRepository
import com.healthify.app.firebase.FirebaseSync
import com.healthify.app.health.HealthConnectManager
import com.healthify.app.notifications.NotificationChannels
import com.healthify.app.notifications.NotificationScheduler
import com.healthify.app.score.HealthScore
import com.healthify.app.units.UnitsReview
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class HealthifyApp : Application() {

    /** Process-wide scope for fire-and-forget work (Firestore writes, seeding). */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // ── Room ─────────────────────────────────────────────────────────────────
    // Upgrades must go through AutoMigrations (see AppDatabase). A missing
    // migration now crashes instead of silently wiping history. Downgrades
    // (sideloading an older build) still wipe, which beats a crash loop.
    val database by lazy {
        DbBackup.snapshotOnAppUpdate(this, AppDatabase.NAME)
        Room.databaseBuilder(this, AppDatabase::class.java, AppDatabase.NAME)
            .fallbackToDestructiveMigrationOnDowngrade()
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

    val logRepository by lazy { LogRepository(database, appScope) }

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

        UnitsReview.init(this)

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
            // 1.0.0 scheduled reminders as periodic WorkManager jobs. Reminders
            // moved to AlarmManager long ago, but an install that never got
            // cleaned up can still hold those jobs; drop them once.
            if (!prefs.getBoolean(KEY_LEGACY_WORK_CANCELLED, false)) {
                WorkManager.getInstance(this@HealthifyApp).cancelAllWork()
                prefs.edit().putBoolean(KEY_LEGACY_WORK_CANCELLED, true).apply()
            }
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
            // One-time re-score of stored check-ins when the HealthScore
            // formula changed (v1 used two different formulas for home and
            // history). Local first — it doesn't wait on auth — then the
            // changed rows are pushed once sign-in is up. Firestore is
            // push-only here, so nothing can pull the old scores back.
            val prefs = getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)
            val rescored = if (prefs.getInt(KEY_SCORE_VERSION, 1) < HealthScore.VERSION) {
                repository.recomputeAllScores().also {
                    prefs.edit().putInt(KEY_SCORE_VERSION, HealthScore.VERSION).apply()
                }
            } else emptyList()
            // ≤1.0.16 stored imperial input raw as cm/kg; fix it once.
            val repairedUser = UnitsReview.repairOnce(this@HealthifyApp, repository)

            FirebaseSync.ensureSignedIn()
            // Fire-and-forget: each write's await() only returns once the
            // server acks, so a sequential loop would stall offline.
            rescored.forEach { ci -> launch { FirebaseSync.syncCheckIn(ci) } }
            repairedUser?.let { u -> launch { FirebaseSync.syncUser(u) } }
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

        // HealthScore formula version the stored check-ins were scored with.
        private const val KEY_SCORE_VERSION = "health_score_version"

        // Set once the 1.0.0-era WorkManager jobs have been cancelled.
        private const val KEY_LEGACY_WORK_CANCELLED = "legacy_work_cancelled"

        // Label the Daily Check-in row is seeded with. Used ONLY at
        // bootstrap (HealthifyApp.onCreate) to locate the row and cache
        // its id; never read by the suppression rule or the UI at
        // run-time, so a later rename/translation does not detach the
        // id from the rules built on top of it.
        private const val DAILY_CHECKIN_LABEL = "Daily Check-in"
    }
}
