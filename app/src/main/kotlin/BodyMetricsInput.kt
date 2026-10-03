package com.healthify.app.units

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.math.roundToInt

/**
 * Height/weight text fields in the user's unit system, backed by metric
 * values. Shared by onboarding and the edit-profile dialog.
 *
 * Imperial height is two whole-number fields (ft, in): a single "5.10"
 * field can't tell 5′1″ from 5′10″. A field the user hasn't typed in
 * reports its original metric value exactly, so flipping units back and
 * forth (or saving untouched) never drifts through rounding.
 */
class BodyMetricsInput(unit: UnitSystem = UnitSystem.METRIC, heightCm: Float = 0f, weightKg: Float = 0f) {

    var unit by mutableStateOf(unit)
        private set
    var heightCm by mutableStateOf("")
        private set
    var heightFt by mutableStateOf("")
        private set
    var heightIn by mutableStateOf("")
        private set
    /** In kg or lb, per [unit]. */
    var weight by mutableStateOf("")
        private set

    private var baseCm = heightCm
    private var baseKg = weightKg
    private var heightEdited = false
    private var weightEdited = false

    init { showBase() }

    fun reset(unit: UnitSystem, heightCm: Float, weightKg: Float) {
        this.unit = unit
        baseCm = heightCm
        baseKg = weightKg
        showBase()
    }

    fun onHeightCm(text: String) { heightCm = text.filter { it.isDigit() || it == '.' }.take(5); heightEdited = true }
    fun onHeightFt(text: String) { heightFt = text.filter { it.isDigit() }.take(1); heightEdited = true }
    fun onHeightIn(text: String) { heightIn = text.filter { it.isDigit() }.take(2); heightEdited = true }
    fun onWeight(text: String)   { weight   = text.filter { it.isDigit() || it == '.' }.take(5); weightEdited = true }

    /** Converts whatever is entered into the other unit system. */
    fun switchUnit(to: UnitSystem) {
        if (to == unit) return
        baseCm = heightCmValue()
        baseKg = weightKgValue()
        unit = to
        showBase()
    }

    /** Entered height in cm; 0 when blank. Not range-checked. */
    fun heightCmValue(): Float = when {
        !heightEdited              -> baseCm
        unit == UnitSystem.METRIC  -> heightCm.toFloatOrNull() ?: 0f
        heightFt.isEmpty() && heightIn.isEmpty() -> 0f
        else -> Units.ftInToCm(heightFt.toIntOrNull() ?: 0, heightIn.toIntOrNull() ?: 0)
    }

    /** Entered weight in kg; 0 when blank. Not range-checked. */
    fun weightKgValue(): Float {
        if (!weightEdited) return baseKg
        val v = weight.toFloatOrNull() ?: return 0f
        return if (unit == UnitSystem.IMPERIAL) Units.lbToKg(v) else v
    }

    private fun showBase() {
        heightEdited = false
        weightEdited = false
        if (baseCm > 0f) {
            val (ft, inch) = Units.cmToFtIn(baseCm)
            heightCm = baseCm.roundToInt().toString()
            heightFt = ft.toString()
            heightIn = inch.toString()
        } else {
            heightCm = ""; heightFt = ""; heightIn = ""
        }
        weight = when {
            baseKg <= 0f                -> ""
            unit == UnitSystem.IMPERIAL -> Units.plain(Units.kgToLb(baseKg), 1)
            else                        -> Units.plain(baseKg, 1)
        }
    }
}
