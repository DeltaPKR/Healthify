package com.healthify.app.units

import java.util.Locale
import kotlin.math.roundToInt

/**
 * Display preference. Storage is always metric (UserEntity.heightCm /
 * weightKg); imperial is converted at the UI edge only.
 */
enum class UnitSystem(val key: String) {
    METRIC("metric"),
    IMPERIAL("imperial");

    companion object {
        fun of(key: String?): UnitSystem = if (key == IMPERIAL.key) IMPERIAL else METRIC
    }
}

object Units {
    private const val CM_PER_INCH = 2.54f
    private const val LB_PER_KG   = 2.2046226f
    private const val G_PER_OZ    = 28.349523f

    // Plausible adult ranges. Anything outside is stored as 0 ("unknown")
    // rather than poisoning BMI and, later, calorie targets.
    val HEIGHT_CM_RANGE = 50f..272f
    val WEIGHT_KG_RANGE = 20f..400f

    /** Total inches rounded first, so 179.9 cm is 5′11″, never 5′12″. */
    fun cmToFtIn(cm: Float): Pair<Int, Int> {
        val totalIn = (cm / CM_PER_INCH).roundToInt()
        return totalIn / 12 to totalIn % 12
    }

    fun ftInToCm(feet: Int, inches: Int): Float = (feet * 12 + inches) * CM_PER_INCH
    fun kgToLb(kg: Float): Float = kg * LB_PER_KG
    fun lbToKg(lb: Float): Float = lb / LB_PER_KG
    fun gToOz(g: Float): Float = g / G_PER_OZ
    fun ozToG(oz: Float): Float = oz * G_PER_OZ

    fun validHeightCm(cm: Float): Float = if (cm in HEIGHT_CM_RANGE) cm else 0f
    fun validWeightKg(kg: Float): Float = if (kg in WEIGHT_KG_RANGE) kg else 0f

    fun formatHeight(cm: Float, unit: UnitSystem): String = when {
        cm <= 0f                  -> "—"
        unit == UnitSystem.METRIC -> "${cm.roundToInt()} cm"
        else -> cmToFtIn(cm).let { (ft, inch) -> "$ft′ $inch″" }
    }

    fun formatWeight(kg: Float, unit: UnitSystem): String = when {
        kg <= 0f                  -> "—"
        unit == UnitSystem.METRIC -> "${plain(kg, 1)} kg"
        else                      -> "${kgToLb(kg).roundToInt()} lb"
    }

    /**
     * Number for an editable field: Locale.US so it parses back with
     * toFloatOrNull() (a "70,5" from a comma locale would not), trailing
     * zeros dropped ("70.0" → "70").
     */
    fun plain(value: Float, decimals: Int): String =
        String.format(Locale.US, "%.${decimals}f", value)
            .let { if ('.' in it) it.trimEnd('0').trimEnd('.') else it }
}

/**
 * Repairs body metrics saved by ≤1.0.16, whose onboarding unit toggle only
 * changed the placeholder: imperial input was stored raw in heightCm /
 * weightKg. Pure so it can be unit-tested; applied once at startup.
 */
object LegacyUnits {

    data class Repaired(val heightCm: Float, val weightKg: Float, val unitSystem: UnitSystem)

    /** Returns null when the stored values already look metric. */
    fun repair(height: Float, weight: Float): Repaired? = when {
        // Metres typed into the cm field (e.g. 1.75).
        height >= 0.5f && height < 2.6f ->
            Repaired(height * 100f, weight, UnitSystem.METRIC)

        // Imperial selected: "5.10" style feet.inches, and weight in lb.
        height >= 3f && height < 9f ->
            Repaired(
                heightCm   = legacyFeetToCm(height),
                weightKg   = if (weight > 0f) Units.lbToKg(weight) else 0f,
                unitSystem = UnitSystem.IMPERIAL,
            )

        else -> null
    }

    /**
     * The field held text like "5.10" but was stored as a Float, so 5.10 and
     * 5.1 are the same value. Read the fraction as two digits:
     * - ≤ 11            → inches          (5.10 → 5′10″, 5.11 → 5′11″, 5.05 → 5′5″)
     * - multiple of 10  → one-digit inches (5.6 → 5′6″)
     * - otherwise       → decimal feet     (5.75 → 5′9″)
     * 5.1 therefore reads as 5′10″, matching the old "e.g. 5.10" hint; the
     * one-time review card lets anyone it misreads correct it.
     */
    internal fun legacyFeetToCm(value: Float): Float {
        val feet = value.toInt()
        val frac = ((value - feet) * 100f).roundToInt()
        return when {
            frac <= 11      -> Units.ftInToCm(feet, frac)
            frac % 10 == 0  -> Units.ftInToCm(feet, frac / 10)
            else            -> value * 12f * 2.54f
        }
    }
}
