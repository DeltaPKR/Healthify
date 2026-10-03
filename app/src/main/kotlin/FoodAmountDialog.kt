package com.healthify.app.ui.food

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.healthify.app.data.db.FoodItemEntity
import com.healthify.app.data.db.MealEntryEntity
import com.healthify.app.logs.MealQuality
import com.healthify.app.logs.MealType
import com.healthify.app.nutrition.Portion
import com.healthify.app.ui.logs.ChoiceTile
import com.healthify.app.ui.theme.*
import com.healthify.app.units.Units
import kotlin.math.roundToInt

private enum class AmountUnit(val label: String) { SERVING("serving"), GRAMS("g"), OUNCES("oz") }

/**
 * How much of [food], into which meal, and how it went. The quality starts
 * from the product's Nutri-Score; calories and macros show only when the
 * user counts calories ([counting]).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FoodAmountDialog(
    food: FoodItemEntity,
    initialType: MealType,
    counting: Boolean,
    imperial: Boolean,
    onSave: (type: MealType, grams: Float, quality: MealQuality) -> Unit,
    onDismiss: () -> Unit,
) {
    val serving = food.servingSizeG
    val units = buildList {
        if (serving != null) add(AmountUnit.SERVING)
        add(AmountUnit.GRAMS); add(AmountUnit.OUNCES)
    }
    var unit by remember { mutableStateOf(if (serving != null) AmountUnit.SERVING else if (imperial) AmountUnit.OUNCES else AmountUnit.GRAMS) }
    var amount by remember {
        mutableStateOf(when (unit) { AmountUnit.SERVING -> "1"; AmountUnit.GRAMS -> "100"; AmountUnit.OUNCES -> "3.5" })
    }
    var type by remember { mutableStateOf(initialType) }
    var quality by remember { mutableStateOf(Portion.suggestedQuality(food.nutriScore)) }
    val haptics = rememberHaptics()

    val value = amount.toFloatOrNull() ?: 0f
    val grams = when (unit) {
        AmountUnit.SERVING -> value * (serving ?: 0f)
        AmountUnit.GRAMS   -> value
        AmountUnit.OUNCES  -> Units.ozToG(value)
    }

    GlassDialog(onDismiss = onDismiss, accent = Gold) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("LOG FOOD", style = MaterialTheme.typography.labelSmall, color = Gold)
                Spacer(Modifier.height(4.dp))
                Text(food.name, style = MaterialTheme.typography.headlineSmall, color = TextPrimary,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                food.brand?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = TextMuted) }
            }
            Spacer(Modifier.width(10.dp))
            NutriScoreBadge(food.nutriScore)
        }

        Spacer(Modifier.height(16.dp))
        FieldLabel("Amount")
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
                value = amount,
                onValueChange = { amount = it.filter { c -> c.isDigit() || c == '.' }.take(6) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                modifier = Modifier.width(110.dp),
                shape = RoundedCornerShape(16.dp),
                colors = glassFieldColors(Gold)
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                units.forEach { u ->
                    GlassChip(u.label, selected = unit == u, color = Gold, onClick = {
                        // Keep the same quantity when switching units.
                        if (u != unit && grams > 0f) amount = when (u) {
                            AmountUnit.SERVING -> Units.plain(grams / (serving ?: grams), 2)
                            AmountUnit.GRAMS   -> grams.roundToInt().toString()
                            AmountUnit.OUNCES  -> Units.plain(Units.gToOz(grams), 1)
                        }
                        unit = u
                    })
                }
            }
        }
        food.servingLabel?.takeIf { unit == AmountUnit.SERVING }?.let {
            Text("1 serving = $it", style = MaterialTheme.typography.bodySmall, color = TextMuted,
                modifier = Modifier.padding(top = 6.dp))
        }
        if (counting) {
            Spacer(Modifier.height(10.dp))
            val n = Portion.mealFrom(food, grams, MealEntryEntity(date = "", mealType = "", quality = ""))
            Text(
                if (n.kcal == null) "No calorie information for this food."
                else buildString {
                    append("≈ ${n.kcal.roundToInt()} kcal")
                    n.proteinG?.let { append(" · P ${it.roundToInt()} g") }
                    n.carbsG?.let { append(" · C ${it.roundToInt()} g") }
                    n.fatG?.let { append(" · F ${it.roundToInt()} g") }
                },
                style = MaterialTheme.typography.titleSmall,
                color = if (n.kcal == null) TextMuted else Gold
            )
        }

        Spacer(Modifier.height(16.dp))
        FieldLabel("Meal")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            MealType.entries.forEach { t ->
                GlassChip(t.label, selected = type == t, onClick = { type = t }, color = Gold, leading = t.emoji)
            }
        }

        Spacer(Modifier.height(16.dp))
        FieldLabel(if (food.nutriScore != null) "How was it? (suggested from Nutri-Score)" else "How was it?")
        MealQuality.entries.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { q ->
                    ChoiceTile(q.emoji, q.label, quality == q, Gold, { haptics.tick(); quality = q }, Modifier.weight(1f))
                }
            }
            Spacer(Modifier.height(10.dp))
        }

        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GhostButton("Cancel", onDismiss, Modifier.weight(1f))
            GlowButton(
                text = "Log it",
                onClick = { quality?.let { haptics.confirm(); onSave(type, grams, it) } },
                modifier = Modifier.weight(1f),
                accent = Gold,
                enabled = quality != null && grams in 1f..5000f,
                height = 52.dp
            )
        }
    }
}
