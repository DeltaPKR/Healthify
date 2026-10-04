package com.healthify.app.ui.workout

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.healthify.app.data.db.SetHistoryRow
import com.healthify.app.ui.theme.*
import com.healthify.app.units.UnitSystem
import com.healthify.app.workout.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val Accent = Green

/**
 * The exercise library: search as you type (it's all on the device), filter
 * by muscle and equipment. In [pickMode] rows are selectable and the bottom
 * button hands the chosen ids back; otherwise a row opens its detail page.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ExerciseLibraryScreen(
    repo: WorkoutRepository,
    pickMode: Boolean,
    onBack: () -> Unit,
    onOpen: (String) -> Unit,
    onPicked: (List<String>) -> Unit,
) {
    val all by remember { repo.exercisesFlow() }.collectAsState(initial = null)
    var query by rememberSaveable { mutableStateOf("") }
    var muscle by rememberSaveable { mutableStateOf<MuscleGroup?>(null) }
    var equipment by rememberSaveable { mutableStateOf<EquipmentGroup?>(null) }
    val picked = remember { mutableStateListOf<String>() }
    var creating by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current

    val results = remember(all, query, muscle, equipment) {
        all?.let { ExerciseCatalog.search(it, query, muscle, equipment) }
    }

    Scaffold(
        topBar = {
            TabHeader(if (pickMode) "Add exercises" else "Exercises", kicker = "Library", onBack = onBack) {
                GlassIconButton(Icons.Rounded.Add, "Create your own exercise", { creating = true }, tint = Accent)
            }
        },
        bottomBar = {
            if (pickMode) {
                Box(Modifier.navigationBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp)) {
                    GlowButton(
                        if (picked.isEmpty()) "Pick exercises" else "Add ${picked.size}",
                        { onPicked(picked.toList()) },
                        enabled = picked.isNotEmpty(),
                        height = 54.dp
                    )
                }
            }
        },
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0)
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it.take(40) },
                placeholder = { Text("Search, e.g. squat", color = TextDim) },
                leadingIcon = { Icon(Icons.Rounded.Search, null, tint = TextMuted) },
                trailingIcon = {
                    if (query.isNotEmpty()) IconButton(onClick = { query = "" }) {
                        Icon(Icons.Rounded.Close, "Clear search", tint = TextMuted)
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
                shape = RoundedCornerShape(18.dp),
                colors = glassFieldColors(Accent),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)
            )
            Spacer(Modifier.height(10.dp))
            LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(MuscleGroup.entries) { g ->
                    GlassChip(g.label, selected = muscle == g, onClick = { muscle = if (muscle == g) null else g }, color = Accent)
                }
            }
            Spacer(Modifier.height(8.dp))
            LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(EquipmentGroup.entries) { g ->
                    GlassChip(g.label, selected = equipment == g, onClick = { equipment = if (equipment == g) null else g }, color = Sky)
                }
            }
            Spacer(Modifier.height(6.dp))

            val list = results
            if (list == null) {
                Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Accent)
                }
                return@Column
            }
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 6.dp, bottom = LocalBottomBarClearance.current + 16.dp)
            ) {
                item {
                    Text(
                        if (list.isEmpty()) "No matches. Not in the library? Add it with +."
                        else "${list.size} exercise${if (list.size == 1) "" else "s"}",
                        style = MaterialTheme.typography.bodySmall, color = TextMuted,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                }
                items(list, key = { it.id }) { e ->
                    val isPicked = e.id in picked
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .animateItemPlacement()
                            .clip16()
                            .clickable(onClickLabel = if (pickMode) "Select" else "Open") {
                                if (pickMode) { if (isPicked) picked.remove(e.id) else picked.add(e.id) } else onOpen(e.id)
                            }
                            .padding(vertical = 10.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(e.name, style = MaterialTheme.typography.bodyLarge, color = if (isPicked) Accent else TextPrimary,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                                if (e.custom) {
                                    Spacer(Modifier.width(8.dp))
                                    Text("YOURS", style = MaterialTheme.typography.labelSmall, color = Gold)
                                }
                            }
                            Text(e.subtitle(), style = MaterialTheme.typography.bodySmall, color = TextMuted, maxLines = 1)
                        }
                        if (pickMode) {
                            Icon(
                                if (isPicked) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                                contentDescription = if (isPicked) "Selected" else "Not selected",
                                tint = if (isPicked) Accent else TextDim
                            )
                        } else {
                            Icon(Icons.Rounded.ChevronRight, null, tint = TextDim, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }
        }
    }

    if (creating) {
        CustomExerciseDialog(
            initialName = query.trim().titleCase(),
            onSave = { name, tracking, group, equip ->
                creating = false
                scope.launch {
                    val e = repo.addCustomExercise(name, tracking, group?.muscles?.first() ?: "", equip?.let(::equipmentKey) ?: "")
                    query = ""
                    if (pickMode) picked.add(e.id) else onOpen(e.id)
                }
            },
            onDismiss = { creating = false }
        )
    }
}

private fun Modifier.clip16() = this.then(Modifier.clip(RoundedCornerShape(14.dp)))

private fun equipmentKey(g: EquipmentGroup): String = when (g) {
    EquipmentGroup.NONE       -> "body only"
    EquipmentGroup.DUMBBELL   -> "dumbbell"
    EquipmentGroup.BARBELL    -> "barbell"
    EquipmentGroup.MACHINE    -> "machine"
    EquipmentGroup.KETTLEBELL -> "kettlebells"
    EquipmentGroup.OTHER      -> "other"
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CustomExerciseDialog(
    initialName: String,
    onSave: (String, Tracking, MuscleGroup?, EquipmentGroup?) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    var tracking by remember { mutableStateOf(Tracking.REPS) }
    var group by remember { mutableStateOf<MuscleGroup?>(null) }
    var equip by remember { mutableStateOf<EquipmentGroup?>(null) }
    GlassDialog(onDismiss = onDismiss, accent = Accent) {
        Text("YOUR EXERCISE", style = MaterialTheme.typography.labelSmall, color = Accent)
        Spacer(Modifier.height(4.dp))
        Text("Add an exercise", style = MaterialTheme.typography.headlineSmall, color = TextPrimary)
        Spacer(Modifier.height(16.dp))
        FieldLabel("Name")
        OutlinedTextField(
            value = name, onValueChange = { name = it.take(60) }, singleLine = true,
            placeholder = { Text("e.g. Sled push", color = TextDim) },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
            shape = RoundedCornerShape(16.dp), colors = glassFieldColors(Accent), modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(14.dp))
        FieldLabel("How do you track it?")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Tracking.entries.forEach { t -> GlassChip(trackingLabel(t), tracking == t, { tracking = t }, color = Accent) }
        }
        Spacer(Modifier.height(14.dp))
        FieldLabel("Muscles (optional)")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            MuscleGroup.entries.forEach { g -> GlassChip(g.label, group == g, { group = if (group == g) null else g }, color = Accent) }
        }
        Spacer(Modifier.height(14.dp))
        FieldLabel("Equipment (optional)")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            EquipmentGroup.entries.forEach { g -> GlassChip(g.label, equip == g, { equip = if (equip == g) null else g }, color = Sky) }
        }
        Spacer(Modifier.height(22.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GhostButton("Cancel", onDismiss, Modifier.weight(1f))
            GlowButton("Add", { onSave(name.trim(), tracking, group, equip) }, Modifier.weight(1f),
                enabled = name.isNotBlank(), height = 52.dp)
        }
    }
}

/** An exercise's muscles, how-to, your records and recent sets. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExerciseDetailScreen(
    repo: WorkoutRepository,
    exerciseId: String,
    unit: UnitSystem,
    onBack: () -> Unit,
) {
    val exercise by produceState<Exercise?>(null, exerciseId) { value = repo.exerciseMap()[exerciseId] }
    val history by remember(exerciseId) { repo.historyFlow(exerciseId) }.collectAsState(initial = emptyList())
    val best by produceState(Best(), exerciseId, history.size) { value = repo.best(exerciseId) }

    Scaffold(
        topBar = { TabHeader(exercise?.name ?: "Exercise", kicker = "Exercise", onBack = onBack) },
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0)
    ) { pad ->
        val e = exercise ?: return@Scaffold
        Column(
            Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                (e.primaryMuscles.map { it.titleCase() to Accent } +
                    e.secondaryMuscles.map { it.titleCase() to TextMuted } +
                    listOf(equipmentLabel(e.equipment) to Sky) +
                    listOfNotNull(e.level.takeIf { it.isNotBlank() }?.titleCase()?.let { it to Lavender })
                ).forEach { (label, color) -> InfoPill(label, color) }
            }

            GlassCard(Modifier.fillMaxWidth(), tint = Gold) {
                Column(Modifier.padding(18.dp)) {
                    SectionLabel("Your records")
                    Spacer(Modifier.height(8.dp))
                    if (best.isFirstTime) {
                        Text("Not done yet. Your first workout with it sets your baseline.",
                            style = MaterialTheme.typography.bodyMedium, color = TextMuted)
                    } else {
                        RecordLines(best, e.tracking, unit)
                    }
                }
            }

            if (e.instructions.isNotEmpty()) {
                GlassCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        SectionLabel("How to do it")
                        e.instructions.forEachIndexed { i, step ->
                            Row {
                                Text("${i + 1}", style = MaterialTheme.typography.titleSmall, color = Accent, modifier = Modifier.width(22.dp))
                                Text(step, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                            }
                        }
                        Text("Exercise data: free-exercise-db (public domain).", style = MaterialTheme.typography.bodySmall, color = TextDim)
                    }
                }
            }

            if (history.isNotEmpty()) {
                GlassCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        SectionLabel("Recent")
                        history.groupBy { it.startedAt }.entries.take(8).forEach { (_, rows) ->
                            HistoryLine(rows, unit)
                        }
                    }
                }
            }
            Spacer(Modifier.height(LocalBottomBarClearance.current + 8.dp))
        }
    }
}

@Composable
private fun RecordLines(best: Best, tracking: Tracking, unit: UnitSystem) {
    val lines = buildList {
        if (tracking == Tracking.WEIGHT) {
            best.maxWeight?.let { add("🏋️" to "Heaviest: ${Weight.format(it, unit)}") }
            best.bestE1rm?.let { add("📈" to "Best estimated 1-rep max: ${Weight.format(it.toFloat(), unit)}") }
        }
        if (tracking != Tracking.TIME) best.maxReps?.let { add("🔁" to "Most reps in a set: $it") }
        best.maxSec?.let { add("⏱️" to "Longest set: ${WorkoutMath.shortDuration(it)}") }
        add("✅" to "${best.sets} set${if (best.sets == 1) "" else "s"} logged")
    }
    lines.forEach { (emoji, text) ->
        Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(emoji, fontSize = 16.sp)
            Spacer(Modifier.width(10.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
        }
    }
}

private val DayFormat = DateTimeFormatter.ofPattern("EEE d MMM", Locale.US)

@Composable
private fun HistoryLine(rows: List<SetHistoryRow>, unit: UnitSystem) {
    val day = runCatching { LocalDate.parse(rows.first().date).format(DayFormat) }.getOrDefault(rows.first().date)
    Column {
        Text(day, style = MaterialTheme.typography.labelLarge, color = TextMuted)
        Text(
            rows.sortedBy { it.set.setIndex }.joinToString("  ·  ") { r ->
                r.set.label(unit, compact = true) + (if (r.set.isWarmup) " (w)" else "") + (if (r.set.prType != null) " 🏆" else "")
            },
            style = MaterialTheme.typography.bodyMedium, color = TextPrimary
        )
    }
}

@Composable
fun InfoPill(text: String, color: Color) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = color,
        modifier = Modifier
            .clip(RoundedCornerShape(100.dp))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 12.dp, vertical = 6.dp)
    )
}

/** The library's how-to for one exercise, opened from the player. */
@Composable
fun InstructionsDialog(exercise: Exercise, onDismiss: () -> Unit) {
    GlassDialog(onDismiss = onDismiss, accent = Accent) {
        Text(exercise.subtitle().uppercase(), style = MaterialTheme.typography.labelSmall, color = Accent)
        Spacer(Modifier.height(4.dp))
        Text(exercise.name, style = MaterialTheme.typography.headlineSmall, color = TextPrimary)
        Spacer(Modifier.height(14.dp))
        if (exercise.instructions.isEmpty()) {
            Text("No instructions for this one.", style = MaterialTheme.typography.bodyMedium, color = TextMuted)
        }
        exercise.instructions.forEachIndexed { i, step ->
            Row(Modifier.padding(bottom = 10.dp)) {
                Text("${i + 1}", style = MaterialTheme.typography.titleSmall, color = Accent, modifier = Modifier.width(22.dp))
                Text(step, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
            }
        }
        Spacer(Modifier.height(10.dp))
        GlowButton("Got it", onDismiss, height = 52.dp)
    }
}
