package com.healthify.app.ui.food

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.healthify.app.Entitlements
import com.healthify.app.Feature
import com.healthify.app.data.db.UserEntity
import com.healthify.app.nutrition.ActivityLevel
import com.healthify.app.nutrition.CalorieGoal
import com.healthify.app.nutrition.NutritionTargets
import com.healthify.app.ui.logs.ChoiceTile
import com.healthify.app.ui.theme.*
import com.healthify.app.units.BodyMetricsInput
import com.healthify.app.units.UnitSystem
import com.healthify.app.units.Units

/**
 * Calorie counting is opt-in. Turning it on asks only for what the estimate
 * still needs (age, height, weight, activity), then shows the daily target,
 * which the user can replace with their own number.
 */
@Composable
fun FoodSettingsDialog(
    user: UserEntity,
    onSave: (UserEntity) -> Unit,
    onDismiss: () -> Unit,
    /** Start with counting switched on (the user tapped "Count calories"). */
    turnOn: Boolean = false,
) {
    var counting by remember { mutableStateOf(user.countCalories || turnOn) }
    var age by remember { mutableStateOf(user.age.takeIf { it > 0 }?.toString() ?: "") }
    val body = remember { BodyMetricsInput(UnitSystem.of(user.unitSystem), user.heightCm, user.weightKg) }
    var activity by remember { mutableStateOf(ActivityLevel.of(user.activityLevel)) }
    var goal by remember { mutableStateOf(CalorieGoal.of(user.calorieGoal)) }
    var override by remember { mutableStateOf(user.calorieTargetOverride.takeIf { it > 0 }?.toString() ?: "") }
    val needsAge = user.age !in 13..120
    val needsBody = user.heightCm <= 0f || user.weightKg <= 0f

    fun draft(): UserEntity = user.copy(
        countCalories = counting,
        age = age.toIntOrNull()?.coerceIn(0, 120) ?: user.age,
        heightCm = Units.validHeightCm(body.heightCmValue()).takeIf { it > 0f } ?: user.heightCm,
        weightKg = Units.validWeightKg(body.weightKgValue()).takeIf { it > 0f } ?: user.weightKg,
        activityLevel = activity?.key ?: user.activityLevel,
        calorieGoal = goal.key,
        calorieTargetOverride = override.toIntOrNull()?.takeIf { it in 800..6000 } ?: 0
    )

    GlassDialog(onDismiss = onDismiss, accent = Gold) {
        Text("FOOD SETTINGS", style = MaterialTheme.typography.labelSmall, color = Gold)
        Spacer(Modifier.height(4.dp))
        Text("Count calories", style = MaterialTheme.typography.headlineSmall, color = TextPrimary)
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Optional. Off, Healthify only tracks how your meals went — no calories or macros anywhere.",
                style = MaterialTheme.typography.bodySmall, color = TextMuted, modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(12.dp))
            Switch(
                checked = counting, onCheckedChange = { counting = it },
                colors = SwitchDefaults.colors(checkedTrackColor = Gold, checkedThumbColor = BgDark)
            )
        }

        if (counting && Entitlements.has(Feature.NUTRITION_TARGETS)) {
            if (needsAge || needsBody) {
                Spacer(Modifier.height(16.dp))
                FieldLabel("For your estimate")
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (needsAge) SmallField("Age", age, Modifier.weight(0.8f)) { age = it.filter(Char::isDigit).take(3) }
                    if (needsBody) {
                        if (body.unit == UnitSystem.METRIC) {
                            SmallField("cm", body.heightCm, Modifier.weight(1f), body::onHeightCm)
                            SmallField("kg", body.weight, Modifier.weight(1f), body::onWeight)
                        } else {
                            SmallField("ft", body.heightFt, Modifier.weight(0.7f), body::onHeightFt)
                            SmallField("in", body.heightIn, Modifier.weight(0.7f), body::onHeightIn)
                            SmallField("lb", body.weight, Modifier.weight(1f), body::onWeight)
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            FieldLabel("How active are you?")
            ActivityLevel.entries.forEach { a ->
                ChoiceTile(
                    emoji = when (a) {
                        ActivityLevel.SEDENTARY -> "🪑"; ActivityLevel.LIGHT -> "🚶"; ActivityLevel.MODERATE -> "🏃"
                        ActivityLevel.ACTIVE -> "🏋️"; ActivityLevel.VERY_ACTIVE -> "⚡"
                    },
                    label = a.label,
                    subtitle = a.hint,
                    selected = activity == a,
                    color = Gold,
                    onClick = { activity = a },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                )
            }

            Spacer(Modifier.height(8.dp))
            FieldLabel("Goal")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CalorieGoal.entries.forEach { g ->
                    GlassChip(g.label, selected = goal == g, onClick = { goal = g }, color = Gold)
                }
            }

            Spacer(Modifier.height(16.dp))
            val estimate = NutritionTargets.estimate(draft().copy(calorieTargetOverride = 0))
            Text(
                estimate?.let { "Estimated: ≈ %,d kcal a day".format(it) } ?: "Fill in the above for an estimate.",
                style = MaterialTheme.typography.titleMedium, color = Gold
            )
            Spacer(Modifier.height(10.dp))
            SmallField("Or use your own daily target (kcal)", override, Modifier.fillMaxWidth()) {
                override = it.filter(Char::isDigit).take(4)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "An estimate from the Mifflin-St Jeor equation, not medical advice. If you have a health " +
                    "condition or a specific goal, check your target with a professional.",
                style = MaterialTheme.typography.bodySmall, color = TextDim
            )
        }

        Spacer(Modifier.height(22.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GhostButton("Cancel", onDismiss, Modifier.weight(1f))
            GlowButton("Save", { onSave(draft()) }, Modifier.weight(1f), accent = Gold, height = 52.dp)
        }
    }
}

@Composable
private fun SmallField(label: String, value: String, modifier: Modifier, onChange: (String) -> Unit) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = TextMuted, maxLines = 1,
            modifier = Modifier.padding(bottom = 6.dp))
        OutlinedTextField(
            value = value, onValueChange = onChange, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp),
            colors = glassFieldColors(Gold)
        )
    }
}
