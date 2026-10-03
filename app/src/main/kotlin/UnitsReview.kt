package com.healthify.app.units

import android.content.Context
import com.healthify.app.HealthifyApp
import com.healthify.app.data.db.UserEntity
import com.healthify.app.data.repository.AppRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * One-time repair of ≤1.0.16 body metrics (see [LegacyUnits]) plus the
 * "please check your height & weight" card it raises. The card stays until
 * the user saves the edit-profile dialog or dismisses it.
 */
object UnitsReview {

    private const val KEY_REPAIR_DONE = "legacy_units_repair_done"
    private const val KEY_PENDING     = "units_review_pending"

    private val _pending = MutableStateFlow(false)
    val pending: StateFlow<Boolean> = _pending.asStateFlow()

    private fun prefs(context: Context) =
        context.getSharedPreferences(HealthifyApp.PREFS_FILE, Context.MODE_PRIVATE)

    fun init(context: Context) {
        _pending.value = prefs(context).getBoolean(KEY_PENDING, false)
    }

    /** Returns the repaired user (for re-sync), or null when nothing changed. */
    suspend fun repairOnce(context: Context, repo: AppRepository): UserEntity? {
        val prefs = prefs(context)
        if (prefs.getBoolean(KEY_REPAIR_DONE, false)) return null
        val user = repo.getUserOnce()
        val fixed = user?.let { LegacyUnits.repair(it.heightCm, it.weightKg) }
        var repaired: UserEntity? = null
        if (user != null && fixed != null) {
            repaired = user.copy(
                heightCm   = Units.validHeightCm(fixed.heightCm),
                weightKg   = Units.validWeightKg(fixed.weightKg),
                unitSystem = fixed.unitSystem.key,
            )
            repo.updateBodyMetrics(repaired.heightCm, repaired.weightKg, repaired.unitSystem)
            prefs.edit().putBoolean(KEY_PENDING, true).apply()
            _pending.value = true
        }
        prefs.edit().putBoolean(KEY_REPAIR_DONE, true).apply()
        return repaired
    }

    fun dismiss(context: Context) {
        prefs(context).edit().putBoolean(KEY_PENDING, false).apply()
        _pending.value = false
    }
}
