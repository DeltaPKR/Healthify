package com.healthify.app.ui.logs

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.healthify.app.data.db.MealEntryEntity
import com.healthify.app.data.db.WorkoutSessionEntity
import com.healthify.app.logs.ActivityType
import com.healthify.app.logs.MealQuality
import com.healthify.app.logs.MealType
import com.healthify.app.ui.theme.*

// Dialogs shared by Home's quick-log row, the Food tab and the Move tab.

private const val MAX_MEAL_NAME = 60

/**
 * Log or edit one meal: slot, optional name, and how it went. [existing]
 * null means a new entry. [onDelete] is shown only when editing.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MealLogDialog(
    existing: MealEntryEntity?,
    initialType: MealType,
    onSave: (type: MealType, name: String, quality: MealQuality) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    var type by remember { mutableStateOf(existing?.let { MealType.of(it.mealType) } ?: initialType) }
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var quality by remember { mutableStateOf(MealQuality.of(existing?.quality)) }
    val haptics = rememberHaptics()

    GlassDialog(onDismiss = onDismiss, accent = Gold) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(if (existing == null) "LOG A MEAL" else "EDIT MEAL",
                    style = MaterialTheme.typography.labelSmall, color = Gold)
                Spacer(Modifier.height(4.dp))
                Text("What did you have?", style = MaterialTheme.typography.headlineSmall, color = TextPrimary)
            }
            IconOrb(Gold, size = 52.dp) { Text(type.emoji, fontSize = 24.sp) }
        }

        Spacer(Modifier.height(18.dp))
        FieldLabel("Meal")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            MealType.entries.forEach { t ->
                GlassChip(t.label, selected = type == t, onClick = { type = t }, color = Gold, leading = t.emoji)
            }
        }

        Spacer(Modifier.height(16.dp))
        FieldLabel("Name (optional)")
        OutlinedTextField(
            value = name,
            onValueChange = { name = it.take(MAX_MEAL_NAME) },
            placeholder = { Text("e.g. Oatmeal with berries", color = TextDim) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = glassFieldColors(Gold)
        )

        Spacer(Modifier.height(16.dp))
        FieldLabel("How was it?")
        MealQuality.entries.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { q ->
                    ChoiceTile(
                        emoji = q.emoji,
                        label = q.label,
                        selected = quality == q,
                        color = Gold,
                        onClick = { haptics.tick(); quality = q },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
        }

        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (onDelete != null) GhostButton("Delete", onDelete, Modifier.weight(1f), color = Coral)
            else GhostButton("Cancel", onDismiss, Modifier.weight(1f))
            GlowButton(
                text = "Save",
                onClick = { quality?.let { haptics.confirm(); onSave(type, name.trim(), it) } },
                modifier = Modifier.weight(1f),
                accent = Gold,
                enabled = quality != null,
                height = 52.dp
            )
        }
    }
}

private val MINUTE_PRESETS = listOf(10, 20, 30, 45, 60)

/**
 * Log or edit a finished activity: kind + minutes. [existing] null means a
 * new entry; [onDelete] is shown only when editing.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ActivityLogDialog(
    existing: WorkoutSessionEntity?,
    onSave: (type: ActivityType, minutes: Int) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    var type by remember { mutableStateOf(existing?.let { ActivityType.of(it.activityType) } ?: ActivityType.WALK) }
    var minutes by remember { mutableIntStateOf(existing?.durationMin ?: 30) }
    val haptics = rememberHaptics()

    GlassDialog(onDismiss = onDismiss, accent = Green) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(if (existing == null) "LOG ACTIVITY" else "EDIT ACTIVITY",
                    style = MaterialTheme.typography.labelSmall, color = Green)
                Spacer(Modifier.height(4.dp))
                Text("How did you move?", style = MaterialTheme.typography.headlineSmall, color = TextPrimary)
            }
            IconOrb(Green, size = 52.dp) { Text(type.emoji, fontSize = 24.sp) }
        }

        Spacer(Modifier.height(18.dp))
        FieldLabel("Activity")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ActivityType.entries.forEach { t ->
                GlassChip(t.label, selected = type == t, onClick = { type = t }, leading = t.emoji)
            }
        }

        Spacer(Modifier.height(18.dp))
        FieldLabel("Minutes")
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { haptics.tick(); minutes = (minutes - 5).coerceAtLeast(5) }) {
                Icon(Icons.Rounded.Remove, "Five minutes less", tint = TextPrimary)
            }
            Text(
                "$minutes min",
                style = MaterialTheme.typography.headlineMedium.copy(fontFeatureSettings = TABULAR),
                color = Green,
                modifier = Modifier.weight(1f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            IconButton(onClick = { haptics.tick(); minutes = (minutes + 5).coerceAtMost(600) }) {
                Icon(Icons.Rounded.Add, "Five minutes more", tint = TextPrimary)
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            MINUTE_PRESETS.forEach { m ->
                GlassChip("$m", selected = minutes == m, onClick = { minutes = m }, modifier = Modifier.weight(1f))
            }
        }

        Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (onDelete != null) GhostButton("Delete", onDelete, Modifier.weight(1f), color = Coral)
            else GhostButton("Cancel", onDismiss, Modifier.weight(1f))
            GlowButton(
                text = "Save",
                onClick = { haptics.confirm(); onSave(type, minutes) },
                modifier = Modifier.weight(1f),
                height = 52.dp
            )
        }
    }
}

/** Selectable glass tile with an emoji and a label. */
@Composable
fun ChoiceTile(
    emoji: String,
    label: String,
    selected: Boolean,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    GlassCard(
        modifier,
        shape = RoundedCornerShape(18.dp),
        tint = if (selected) color else null,
        borderBrush = if (selected) androidx.compose.ui.graphics.SolidColor(color) else null,
        onClick = onClick
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(emoji, fontSize = 22.sp)
            Text(
                label,
                style = MaterialTheme.typography.titleSmall,
                color = if (selected) color else TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
