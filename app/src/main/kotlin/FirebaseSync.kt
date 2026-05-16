package com.healthify.app.firebase

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.healthify.app.data.db.CheckInEntity
import com.healthify.app.data.db.UserEntity
import kotlinx.coroutines.tasks.await

enum class CloudSyncState(val label: String) {
    ACTIVE("Active"),
    CONNECTING("Connecting…"),
    OFFLINE("Offline"),
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

    private val auth by lazy { Firebase.auth }
    private val db   by lazy { Firebase.firestore }

    // ── Anonymous sign-in ────────────────────────────────────────────────
    suspend fun ensureSignedIn() {
        if (auth.currentUser != null) return
        try {
            auth.signInAnonymously().await()
        } catch (e: Exception) {
            // Offline or error – Firestore will buffer writes anyway
        }
    }

    private val uid: String?
        get() = auth.currentUser?.uid

    /**
     * Best-effort snapshot of cloud sync health.
     * - OFFLINE: device has no validated internet → writes are queued locally.
     * - CONNECTING: online but anonymous sign-in hasn't completed yet.
     * - ACTIVE: online and signed in (writes flow through to Firestore).
     */
    fun currentState(context: Context): CloudSyncState {
        val online = isOnline(context)
        val authed = auth.currentUser != null
        return when {
            !online -> CloudSyncState.OFFLINE
            authed  -> CloudSyncState.ACTIVE
            else    -> CloudSyncState.CONNECTING
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
