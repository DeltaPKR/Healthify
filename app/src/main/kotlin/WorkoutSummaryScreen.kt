package com.healthify.app.ui.workout

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.healthify.app.logs.ActivityType
import com.healthify.app.ui.theme.*
import com.healthify.app.units.UnitSystem
import com.healthify.app.workout.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val Accent = Green

/**
 * A finished workout: length, sets, volume, records and first-time
 * baselines, then every set. Opened right after finishing ([fresh], with
 * confetti when there's a record) or later from the Move tab.
 */
@Composable
fun WorkoutSummaryScreen(
    repo: WorkoutRepository,
    sessionId: Long,
    fresh: Boolean,
    unit: UnitSystem,
    showCalories: Boolean,
    onDone: () -> Unit,
) {
    val summary by produceState<WorkoutSummary?>(null, sessionId) { value = repo.summary(sessionId) }
    val scope = rememberCoroutineScope()
    var askDelete by remember { mutableStateOf(false) }
    var confetti by remember { mutableIntStateOf(0) }

    val s = summary
    val records = s?.sets.orEmpty().filter { it.prType != null }
    val recordCount = records.sumOf { PrType.parse(it.prType).size }
    LaunchedEffect(s) {
        if (s != null && fresh && (records.isNotEmpty() || s.sets.isNotEmpty())) { delay(250); confetti = 1 }
    }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                TabHeader(if (fresh) "Workout done" else (s?.session?.title ?: "Workout"), kicker = s?.let { dateLabel(it.session.date) }, onBack = onDone) {
                    if (s != null && !fresh) GlassIconButton(Icons.Rounded.Delete, "Delete workout", { askDelete = true }, tint = Coral)
                }
            },
            bottomBar = {
                if (fresh) Box(Modifier.navigationBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp)) {
                    GlowButton("Done", onDone, height = 56.dp)
                }
            },
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets(0)
        ) { pad ->
            val sum = s ?: return@Scaffold
            val session = sum.session
            val working = sum.sets.filter { !it.isWarmup }
            val volume = WorkoutMath.volumeKg(sum.sets)
            Column(
                Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                GlassCard(Modifier.fillMaxWidth(), tint = Accent) {
                    Column(Modifier.padding(18.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconOrb(Accent, size = 52.dp) { Text(ActivityType.of(session.activityType).emoji, fontSize = 24.sp) }
                            Spacer(Modifier.width(14.dp))
                            Column {
                                Text(session.title, style = MaterialTheme.typography.titleLarge, color = TextPrimary)
                                Text(
                                    if (recordCount > 0) "$recordCount new record${if (recordCount == 1) "" else "s"} 🏆"
                                    else "Nice work. Every session counts.",
                                    style = MaterialTheme.typography.bodyMedium, color = TextMuted
                                )
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                        Row(Modifier.fillMaxWidth()) {
                            Stat("${session.durationMin} min", "Time", Modifier.weight(1f))
                            Stat("${working.size}", if (working.size == 1) "Set" else "Sets", Modifier.weight(1f))
                            if (volume > 0f) Stat(Weight.format(volume, unit), "Volume", Modifier.weight(1.3f))
                            if (showCalories) session.kcalEstimate?.let { Stat("≈ ${it.toInt()}", "kcal (estimate)", Modifier.weight(1.2f)) }
                        }
                    }
                }

                if (records.isNotEmpty() || sum.firstTimes.isNotEmpty()) {
                    GlassCard(Modifier.fillMaxWidth(), tint = Gold) {
                        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            SectionLabel(if (records.isNotEmpty()) "Records" else "First times")
                            records.forEach { set ->
                                val name = sum.exercises[set.exerciseId]?.name ?: "Exercise"
                                PrType.parse(set.prType).forEach { type ->
                                    RecordLine("🏆", name, "${type.label}: ${recordValue(type, set, unit)}")
                                }
                            }
                            sum.firstTimes.forEach { id ->
                                val best = sum.sets.filter { it.exerciseId == id && !it.isWarmup }
                                    .maxWithOrNull(compareBy({ it.weightKg ?: 0f }, { it.reps ?: 0 }, { it.durationSec ?: 0 }))
                                RecordLine("📍", sum.exercises[id]?.name ?: "Exercise",
                                    "First time: your baseline" + (best?.let { " is ${it.label(unit)}" } ?: ""))
                            }
                        }
                    }
                }

                val bySlot = sum.sets.groupBy { it.slot }
                GlassCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        SectionLabel("Sets")
                        if (bySlot.isEmpty()) Text("No sets logged.", style = MaterialTheme.typography.bodyMedium, color = TextMuted)
                        bySlot.values.forEach { list ->
                            val name = sum.exercises[list.first().exerciseId]?.name ?: "Exercise"
                            Column {
                                Text(name, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                                Text(
                                    list.sortedBy { it.setIndex }.joinToString("  ·  ") {
                                        it.label(unit, compact = true) + (if (it.isWarmup) " (w)" else "") + (if (it.prType != null) " 🏆" else "")
                                    },
                                    style = MaterialTheme.typography.bodyMedium, color = TextMuted
                                )
                            }
                        }
                    }
                }
                if (showCalories && session.kcalEstimate != null) {
                    Text("Calories are a rough estimate from the activity type, your weight and the time.",
                        style = MaterialTheme.typography.bodySmall, color = TextDim)
                }
                Spacer(Modifier.height(LocalBottomBarClearance.current + 8.dp))
            }
        }
        ConfettiBurst(confetti, Modifier.fillMaxSize(), origin = Offset(0.5f, 0.25f), intensity = if (records.isNotEmpty()) 1.8f else 1f)
    }

    if (askDelete) {
        ConfirmDialog(
            emoji = "🗑️", title = "Delete this workout?",
            message = "Its sets and any records it set are removed. This can't be undone.",
            confirmLabel = "Delete",
            onConfirm = { askDelete = false; scope.launch { repo.delete(sessionId); onDone() } },
            onDismiss = { askDelete = false }
        )
    }
}

private fun recordValue(type: PrType, set: com.healthify.app.data.db.WorkoutSetEntity, unit: UnitSystem): String = when (type) {
    PrType.WEIGHT -> "${Weight.format(set.weightKg ?: 0f, unit)} × ${set.reps ?: 0}"
    PrType.E1RM   -> WorkoutMath.epley(set.weightKg, set.reps)?.let { "≈ ${Weight.format(it.toFloat(), unit)}" } ?: "—"
    PrType.REPS   -> "${set.reps ?: 0} reps"
    PrType.TIME   -> WorkoutMath.shortDuration(set.durationSec ?: 0)
}

@Composable
private fun Stat(value: String, label: String, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleLarge.copy(fontFeatureSettings = TABULAR), color = TextPrimary,
            textAlign = TextAlign.Center, maxLines = 1)
        Text(label, style = MaterialTheme.typography.labelSmall, color = TextMuted, textAlign = TextAlign.Center)
    }
}

@Composable
private fun RecordLine(emoji: String, title: String, detail: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(emoji, fontSize = 18.sp)
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = TextMuted)
        }
    }
}

private val DateFormat = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.US)

private fun dateLabel(iso: String): String =
    runCatching { LocalDate.parse(iso).format(DateFormat) }.getOrDefault(iso)
