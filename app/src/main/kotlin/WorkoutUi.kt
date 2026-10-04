package com.healthify.app.ui.workout

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.healthify.app.data.db.WorkoutSetEntity
import com.healthify.app.ui.theme.*
import com.healthify.app.units.UnitSystem
import com.healthify.app.workout.Exercise
import com.healthify.app.workout.MuscleGroup
import com.healthify.app.workout.PlanItem
import com.healthify.app.workout.Tracking
import com.healthify.app.workout.Weight
import com.healthify.app.workout.WorkoutMath
import java.util.Locale

// Small pieces shared by the routine, library, player and summary screens.

fun String.titleCase(): String = split(' ').joinToString(" ") { w ->
    w.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString() }
}

fun equipmentLabel(equipment: String?): String = when (equipment) {
    null, "body only" -> "No equipment"
    "e-z curl bar"    -> "EZ bar"
    "kettlebells"     -> "Kettlebell"
    else              -> equipment.titleCase()
}

/** "Chest · Dumbbell" for list rows. */
fun Exercise.subtitle(): String {
    val muscle = primaryMuscles.firstOrNull()?.let { m ->
        if (custom) MuscleGroup.of(m)?.label ?: m.titleCase() else m.titleCase()
    }
    return listOfNotNull(muscle, equipmentLabel(equipment)).joinToString(" · ")
}

fun trackingLabel(t: Tracking): String = when (t) {
    Tracking.REPS   -> "Reps"
    Tracking.WEIGHT -> "Weight & reps"
    Tracking.TIME   -> "Time"
}

/** "3 × 10" or "2 × 30 s". */
fun PlanItem.targetLabel(): String =
    if (sec != null) "$sets × ${WorkoutMath.shortDuration(sec)}" else "$sets × ${reps ?: 0}"

/** One logged set in a few characters: "60 kg × 5", "12 reps", "45 s". */
fun WorkoutSetEntity.label(unit: UnitSystem, compact: Boolean = false): String = when {
    durationSec != null && reps == null -> WorkoutMath.shortDuration(durationSec)
    weightKg != null && weightKg > 0f   ->
        if (compact) "${Weight.plain(weightKg, unit)}×${reps ?: 0}" else "${Weight.format(weightKg, unit)} × ${reps ?: 0}"
    else                                -> if (compact) "${reps ?: 0}" else "${reps ?: 0} reps"
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(text.uppercase(), style = MaterialTheme.typography.labelSmall, color = TextMuted, modifier = modifier)
}

/**
 * Compact number field for set rows (a full text field is too tall).
 * [placeholder] shows the suggested value in dim text until the user types.
 */
@Composable
fun NumberCell(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    decimal: Boolean = false,
    enabled: Boolean = true,
    accent: Color = Green,
) {
    val shape = RoundedCornerShape(10.dp)
    var focused by remember { mutableStateOf(false) }
    BasicTextField(
        value = value,
        onValueChange = { s -> onValueChange(s.filter { it.isDigit() || (decimal && (it == '.' || it == ',')) }.take(6)) },
        enabled = enabled,
        singleLine = true,
        textStyle = MaterialTheme.typography.titleSmall.copy(color = TextPrimary, textAlign = TextAlign.Center, fontFeatureSettings = TABULAR),
        cursorBrush = SolidColor(accent),
        keyboardOptions = KeyboardOptions(
            keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number,
            imeAction = ImeAction.Done
        ),
        modifier = modifier
            .height(40.dp)
            .background(if (focused) accent.copy(alpha = 0.10f) else GlassFillTop, shape)
            .border(1.dp, if (focused) accent else GlassBorderTop, shape)
            .onFocusChanged { focused = it.isFocused },
        decorationBox = { inner ->
            Box(Modifier.fillMaxSize().padding(horizontal = 6.dp), contentAlignment = Alignment.Center) {
                if (value.isEmpty()) Text(placeholder, style = MaterialTheme.typography.titleSmall, color = TextDim, textAlign = TextAlign.Center)
                inner()
            }
        }
    )
}
