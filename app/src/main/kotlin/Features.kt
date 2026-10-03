package com.healthify.app

/**
 * Features that could become part of a paid tier later. Every call site
 * checks [Entitlements.has] so gating is one change here; until a
 * monetisation decision is made, everything is on and no billing exists.
 */
enum class Feature { BARCODE_SCAN, NUTRITION_TARGETS, CUSTOM_ROUTINES, HC_WORKOUT_EXPORT }

object Entitlements {
    fun has(@Suppress("UNUSED_PARAMETER") feature: Feature): Boolean = true
}
