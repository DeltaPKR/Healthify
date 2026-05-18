package com.healthify.app.firebase

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.google.firebase.auth.ktx.auth
import com.google.firebase.crashlytics.ktx.crashlytics
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.healthify.app.data.db.CheckInEntity
import com.healthify.app.data.db.UserEntity
import kotlinx.coroutines.delay
import kotlinx.coroutines.tasks.await

enum class CloudSyncState(val label: String) {
    ACTIVE("Active"),
    CONNECTING("Connecting…"),
    OFFLINE("Offline"),
    ERROR("Sign-in failed"),
}

/**
 * Firebase Firestore sync.
 *
 * Schema:
 *   users/{uid}/profile        → UserEntity fields
 *   users/{uid}/checkins/{date} → CheckInEntity fields
 *
 * Uses anonymous auth so users don't need to sign up.
 * If internet is unavailable Firestore queues writes and syncs later automatically.
 */
object FirebaseSync {

    private const val TAG = "FirebaseSync"

    private val auth by lazy { Firebase.auth }
    private val db   by lazy { Firebase.firestore }

    /**
     * Last sign-in attempt outcome. Surfaced through [currentState] so the
     * UI can distinguish a sign-in failure (e.g. SHA-1 mismatch on a Play-
     * served install where the app-signing-key fingerprint isn't on the
     * Firebase project) from a slow first-launch sign-in still in flight.
     */
    @Volatile
    private var lastAuthError: Exception? = null

    // ── Anonymous sign-in ────────────────────────────────────────────────
    /**
     * Attempts anonymous sign-in with bounded retries + visible error
     * logging. Previously this swallowed every exception silently, which
     * masked SHA-1 mismatch failures during Play app-signing rollout —
     * the UI stayed "Connecting…" forever.
     */
    suspend fun ensureSignedIn() {
        if (auth.currentUser != null) {
            lastAuthError = null
            return
        }
        // Up to 3 attempts with exponential backoff: 0s, 2s, 6s. After
        // the final failure the error is exposed via [currentState] /
        // Crashlytics so the user (and we) know auth is broken instead
        // of pretending the network is just slow.
        var delayMs = 0L
        repeat(3) { attempt ->
            if (delayMs > 0) delay(delayMs)
            try {
                auth.signInAnonymously().await()
                lastAuthError = null
                Log.i(TAG, "Anonymous sign-in succeeded on attempt ${attempt + 1}")
                return
            } catch (e: Exception) {
                lastAuthError = e
                Log.w(TAG, "Anonymous sign-in attempt ${attempt + 1} failed: ${e.message}")
                Firebase.crashlytics.recordException(e)
                delayMs = if (delayMs == 0L) 2_000L else delayMs * 3
            }
        }
        Log.e(TAG, "Anonymous sign-in failed after 3 attempts; sync disabled until next app start")
    }

    /**
     * Force a fresh sign-in attempt. Cheap wrapper for UI retry buttons.
     */
    suspend fun retrySignIn() {
        // Clear the cached error so currentState() reports CONNECTING
        // during the retry, not ERROR.
        lastAuthError = null
        ensureSignedIn()
    }

    private val uid: String?
        get() = auth.currentUser?.uid

    /**
     * Best-effort snapshot of cloud sync health.
     * - OFFLINE: device has no validated internet → writes are queued locally.
     * - CONNECTING: online but anonymous sign-in hasn't completed yet.
     * - ACTIVE: online and signed in (writes flow through to Firestore).
     * - ERROR: a sign-in attempt finished with an exception (e.g. SHA-1
     *          mismatch, API key restriction). Distinguished from CONNECTING
     *          so the UI can offer a retry / contact-support affordance.
     */
    fun currentState(context: Context): CloudSyncState {
        val online = isOnline(context)
        val authed = auth.currentUser != null
        val error  = lastAuthError != null
        return when {
            !online && !authed -> CloudSyncState.OFFLINE
            authed             -> CloudSyncState.ACTIVE
            error              -> CloudSyncState.ERROR
            else               -> CloudSyncState.CONNECTING
        }
    }

    private fun isOnline(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
               caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    // ── User profile ─────────────────────────────────────────────────────
    suspend fun syncUser(user: UserEntity) {
        val uid = uid ?: return
        try {
            val data = mapOf(
                "name"          to user.name,
                "age"           to user.age,
                "gender"        to user.gender,
                "heightCm"      to user.heightCm,
                "weightKg"      to user.weightKg,
                "conditions"    to user.conditions,
                "goals"         to user.goals,
                "stepGoal"      to user.stepGoal,
                "currentStreak" to user.currentStreak,
                "longestStreak" to user.longestStreak,
                "updatedAt"     to System.currentTimeMillis()
            )
            db.collection("users").document(uid)
              .collection("profile").document("main")
              .set(data, SetOptions.merge()).await()
        } catch (e: Exception) { /* queued offline */ }
    }

    // ── Check-in ─────────────────────────────────────────────────────────
    suspend fun syncCheckIn(ci: CheckInEntity) {
        val uid = uid ?: return
        try {
            val data = mapOf(
                "date"          to ci.date,
                "moodScore"     to ci.moodScore,
                "waterGlasses"  to ci.waterGlasses,
                "foodQuality"   to ci.foodQuality,
                "sleepHours"    to ci.sleepHours,
                "dayRating"     to ci.dayRating,
                "steps"         to ci.steps,
                "wellnessScore" to ci.wellnessScore,
                "timestamp"     to ci.timestamp
            )
            db.collection("users").document(uid)
              .collection("checkins").document(ci.date)
              .set(data, SetOptions.merge()).await()
        } catch (e: Exception) { /* queued offline */ }
    }

    // ── Analytics aggregates ─────────────────────────────────────────────
    suspend fun pushStreakUpdate(uid: String? = this.uid, streak: Int, longest: Int) {
        val u = uid ?: return
        try {
            db.collection("users").document(u)
              .collection("profile").document("main")
              .update(mapOf("currentStreak" to streak, "longestStreak" to longest)).await()
        } catch (e: Exception) { }
    }
}
