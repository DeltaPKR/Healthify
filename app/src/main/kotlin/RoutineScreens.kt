package com.healthify.app.ui.workout

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.healthify.app.Entitlements
import com.healthify.app.Feature
import com.healthify.app.data.db.WorkoutSessionEntity
import com.healthify.app.logs.ActivityType
import com.healthify.app.ui.theme.*
import com.healthify.app.workout.*
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

private val Accent = Green

/**
 * A routine before you start it: what's in it and roughly how long it
 * takes. Built-in routines can be copied ("Make it yours"); custom ones
 * edited or deleted.
 */
@Composable
fun RoutineDetailScreen(
    repo: WorkoutRepository,
    ref: String,
    onBack: () -> Unit,
    onStarted: (Long) -> Unit,
    onEdit: (String) -> Unit,
) {
    val routine by remember(ref) { repo.routinesFlow().map { all -> all.firstOrNull { it.ref == ref } } }
        .collectAsState(initial = null)
    val exercises by produceState<Map<String, Exercise>>(emptyMap()) { value = repo.exerciseMap() }
    val active by remember { repo.activeSessionFlow() }.collectAsState(initial = null)
    val scope = rememberCoroutineScope()
    var askBusy by remember { mutableStateOf<WorkoutSessionEntity?>(null) }
    var askDelete by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TabHeader(routine?.name ?: "Routine", kicker = if (routine?.custom == true) "Your routine" else "Routine", onBack = onBack) {
                if (routine?.custom == true) {
                    GlassIconButton(Icons.Rounded.Delete, "Delete routine", { askDelete = true }, tint = Coral)
                    GlassIconButton(Icons.Rounded.Edit, "Edit routine", { onEdit(ref) }, tint = Accent)
                }
            }
        },
        bottomBar = {
            val r = routine ?: return@Scaffold
            Column(Modifier.navigationBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                GlowButton("Start workout", {
                    val busy = active
                    if (busy != null) askBusy = busy
                    else scope.launch { onStarted(repo.start(r)) }
                }, enabled = r.items.isNotEmpty(), height = 56.dp)
                if (!r.custom && Entitlements.has(Feature.CUSTOM_ROUTINES)) {
                    GhostButton("Make it yours", { onEdit(RoutineEditorViewModel.copyOf(ref)) })
                }
            }
        },
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0)
    ) { pad ->
        val r = routine ?: return@Scaffold
        Column(
            Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            GlassCard(Modifier.fillMaxWidth(), tint = Accent) {
                Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconOrb(Accent, size = 56.dp) { Text(r.emoji, fontSize = 26.sp) }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(routineMeta(r), style = MaterialTheme.typography.labelLarge, color = Accent)
                        if (r.blurb.isNotBlank()) {
                            Spacer(Modifier.height(4.dp))
                            Text(r.blurb, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                        }
                        if (r.equipment.isNotBlank() && r.equipment != "None") {
                            Spacer(Modifier.height(4.dp))
                            Text("Equipment: ${r.equipment}", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                        }
                    }
                }
            }
            GlassCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(vertical = 8.dp)) {
                    if (r.items.isEmpty()) {
                        Text("No exercises yet. Edit the routine to add some.", style = MaterialTheme.typography.bodyMedium,
                            color = TextMuted, modifier = Modifier.padding(18.dp))
                    }
                    r.items.forEachIndexed { i, p ->
                        val e = exercises[p.exerciseId]
                        Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("${i + 1}", style = MaterialTheme.typography.titleSmall, color = TextDim, modifier = Modifier.width(26.dp))
                            Column(Modifier.weight(1f)) {
                                Text(e?.name ?: "Unknown exercise", style = MaterialTheme.typography.bodyLarge, color = TextPrimary)
                                Text(listOfNotNull(e?.subtitle(), p.note.ifBlank { null }).joinToString(" · "),
                                    style = MaterialTheme.typography.bodySmall, color = TextMuted)
                            }
                            Spacer(Modifier.width(10.dp))
                            Text(p.targetLabel(), style = MaterialTheme.typography.titleSmall.copy(fontFeatureSettings = TABULAR), color = Accent)
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }

    askBusy?.let { busy ->
        GlassDialog(onDismiss = { askBusy = null }, accent = Gold) {
            IconOrb(Gold, size = 56.dp) { Text("⏸️", fontSize = 26.sp) }
            Spacer(Modifier.height(16.dp))
            Text("A workout is still open", style = MaterialTheme.typography.headlineSmall, color = TextPrimary)
            Spacer(Modifier.height(8.dp))
            Text("\"${busy.title}\" isn't finished. Pick it up where you left off, or throw it away and start this one.",
                style = MaterialTheme.typography.bodyMedium, color = TextMuted)
            Spacer(Modifier.height(22.dp))
            GlowButton("Resume it", { askBusy = null; onStarted(busy.id) }, accent = Gold, height = 52.dp)
            Spacer(Modifier.height(10.dp))
            GhostButton("Discard it and start this one", {
                askBusy = null
                scope.launch {
                    repo.discard(busy.id)
                    routine?.let { onStarted(repo.start(it)) }
                }
            }, color = Coral)
        }
    }
    if (askDelete) {
        ConfirmDialog(
            emoji = "🗑️", title = "Delete this routine?",
            message = "Workouts you've already done with it stay in your history.",
            confirmLabel = "Delete",
            onConfirm = { askDelete = false; scope.launch { repo.deleteRoutine(ref); onBack() } },
            onDismiss = { askDelete = false }
        )
    }
}

/** "6 exercises · ~20 min · Beginner". */
fun routineMeta(r: Routine): String = listOfNotNull(
    "${r.items.size} exercise${if (r.items.size == 1) "" else "s"}",
    "~${r.estimatedMinutes} min",
    r.level.ifBlank { null }?.titleCase()
).joinToString(" · ")

// ═══════════════════════════════════════════════════════════════════════════
// EDITOR
// ═══════════════════════════════════════════════════════════════════════════

/**
 * Edits a custom routine. [target] is [NEW], a custom routine ref, or
 * [copyOf] a routine: a copy is only created when it is saved.
 */
class RoutineEditorViewModel(private val repo: WorkoutRepository, target: String) : ViewModel() {
    private val copySource = target.removePrefix(COPY).takeIf { target.startsWith(COPY) }
    private val editRef = target.takeIf { it != NEW && copySource == null }
    private var basedOn: String? = null
    val isNew: Boolean get() = editRef == null
    var name by mutableStateOf("")
    var emoji by mutableStateOf(EMOJIS.first())
    var activity by mutableStateOf(ActivityType.STRENGTH)
    val items = mutableStateListOf<PlanItem>()
    var exercises by mutableStateOf<Map<String, Exercise>>(emptyMap())
        private set
    var loaded by mutableStateOf(editRef == null && copySource == null)
        private set
    var dirty by mutableStateOf(false)
        private set

    init {
        viewModelScope.launch {
            exercises = repo.exerciseMap()
            (editRef ?: copySource)?.let { repo.routine(it) }?.let { r ->
                name = if (copySource != null && r.custom) "${r.name} (copy)" else r.name
                emoji = r.emoji; activity = r.activity
                basedOn = r.basedOn ?: copySource?.let(RoutineRef::builtInId)
                items.addAll(r.items.mapIndexed { i, p -> p.copy(key = i) })
                dirty = copySource != null
            }
            loaded = true
        }
    }

    fun touch() { dirty = true }

    fun addExercises(ids: List<String>) = viewModelScope.launch {
        exercises = repo.exerciseMap()
        ids.forEach { id ->
            val tracking = exercises[id]?.tracking ?: Tracking.REPS
            items += PlanItem.defaultFor(WorkoutPlan.nextKey(items), id, tracking)
        }
        dirty = true
    }

    fun update(item: PlanItem) {
        val i = items.indexOfFirst { it.key == item.key }
        if (i >= 0) { items[i] = item; dirty = true }
    }

    fun move(key: Int, by: Int) {
        val i = items.indexOfFirst { it.key == key }
        val j = i + by
        if (i < 0 || j !in items.indices) return
        items.add(j, items.removeAt(i)); dirty = true
    }

    fun remove(key: Int) { items.removeAll { it.key == key }; dirty = true }

    fun save(onSaved: (String) -> Unit) = viewModelScope.launch {
        onSaved(repo.saveRoutine(editRef, name, emoji, activity, items.toList(), basedOn))
    }

    class Factory(private val repo: WorkoutRepository, private val target: String) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = RoutineEditorViewModel(repo, target) as T
    }

    companion object {
        const val NEW = "new"
        private const val COPY = "copy:"
        fun copyOf(ref: String) = COPY + ref

        val EMOJIS = listOf("💪", "🏋️", "🤸", "🔥", "🎯", "🦵", "🏃", "🧘", "🙆", "⚡", "🌅", "🌙")
        val ACTIVITIES = listOf(ActivityType.STRENGTH, ActivityType.HIIT, ActivityType.STRETCH, ActivityType.YOGA, ActivityType.OTHER)
        private val REST_CHOICES = listOf(0, 30, 45, 60, 90, 120, 180)
        fun nextRest(sec: Int, by: Int): Int {
            val i = REST_CHOICES.indexOfFirst { it >= sec }.let { if (it < 0) REST_CHOICES.lastIndex else it }
            return REST_CHOICES[(i + by).coerceIn(0, REST_CHOICES.lastIndex)]
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RoutineEditorScreen(
    vm: RoutineEditorViewModel,
    onBack: () -> Unit,
    onPickExercises: () -> Unit,
    onSaved: (String) -> Unit,
) {
    var askDiscard by remember { mutableStateOf(false) }
    val leave = { if (vm.dirty) askDiscard = true else onBack() }
    BackHandler(enabled = vm.dirty) { askDiscard = true }

    Scaffold(
        topBar = { TabHeader(if (vm.isNew) "New routine" else "Edit routine", kicker = "Your routine", onBack = leave) },
        bottomBar = {
            Box(Modifier.navigationBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp)) {
                GlowButton("Save routine", { vm.save(onSaved) }, enabled = vm.loaded && vm.items.isNotEmpty() && vm.name.isNotBlank(), height = 56.dp)
            }
        },
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0)
    ) { pad ->
        if (!vm.loaded) return@Scaffold
        Column(
            Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            GlassCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp)) {
                    FieldLabel("Name")
                    OutlinedTextField(
                        value = vm.name, onValueChange = { vm.name = it.take(40); vm.touch() }, singleLine = true,
                        placeholder = { Text("e.g. Monday push day", color = TextDim) },
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                        shape = RoundedCornerShape(16.dp), colors = glassFieldColors(Accent), modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(14.dp))
                    FieldLabel("Icon")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        RoutineEditorViewModel.EMOJIS.forEach { e ->
                            GlassChip(e, selected = vm.emoji == e, onClick = { vm.emoji = e; vm.touch() }, color = Accent)
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    FieldLabel("Logged as")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        RoutineEditorViewModel.ACTIVITIES.forEach { a ->
                            GlassChip(a.label, selected = vm.activity == a, onClick = { vm.activity = a; vm.touch() }, color = Accent, leading = a.emoji)
                        }
                    }
                }
            }

            if (vm.items.isEmpty()) {
                Text("Add the exercises this routine works through. You can set sets, reps or time, and rest for each.",
                    style = MaterialTheme.typography.bodyMedium, color = TextMuted)
            }
            vm.items.forEachIndexed { i, item ->
                EditorItemCard(
                    item = item,
                    exercise = vm.exercises[item.exerciseId],
                    first = i == 0,
                    last = i == vm.items.lastIndex,
                    onChange = vm::update,
                    onMove = { by -> vm.move(item.key, by) },
                    onRemove = { vm.remove(item.key) }
                )
            }
            GhostButton("+ Add exercises", onPickExercises, color = Accent)
            Spacer(Modifier.height(8.dp))
        }
    }

    if (askDiscard) {
        ConfirmDialog(
            emoji = "✏️", title = "Discard your changes?", message = "Your edits to this routine haven't been saved.",
            confirmLabel = "Discard", onConfirm = { askDiscard = false; onBack() }, onDismiss = { askDiscard = false }
        )
    }
}

@Composable
private fun EditorItemCard(
    item: PlanItem,
    exercise: Exercise?,
    first: Boolean,
    last: Boolean,
    onChange: (PlanItem) -> Unit,
    onMove: (Int) -> Unit,
    onRemove: () -> Unit,
) {
    GlassCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp)) {
        Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(exercise?.name ?: "Unknown exercise", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                    exercise?.let { Text(it.subtitle(), style = MaterialTheme.typography.bodySmall, color = TextMuted) }
                }
                IconButton(onClick = { onMove(-1) }, enabled = !first) { Icon(Icons.Rounded.KeyboardArrowUp, "Move up", tint = if (first) TextDim else TextMuted) }
                IconButton(onClick = { onMove(1) }, enabled = !last) { Icon(Icons.Rounded.KeyboardArrowDown, "Move down", tint = if (last) TextDim else TextMuted) }
                IconButton(onClick = onRemove) { Icon(Icons.Rounded.Close, "Remove exercise", tint = Coral) }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GlassChip("Reps", selected = !item.timed, onClick = {
                    if (item.timed) onChange(item.copy(reps = 10, sec = null))
                }, color = Accent)
                GlassChip("Time", selected = item.timed, onClick = {
                    if (!item.timed) onChange(item.copy(reps = null, sec = 30))
                }, color = Accent)
            }
            Spacer(Modifier.height(6.dp))
            Stepper("Sets", "${item.sets}",
                onMinus = { onChange(item.copy(sets = (item.sets - 1).coerceAtLeast(1))) },
                onPlus = { onChange(item.copy(sets = (item.sets + 1).coerceAtMost(PlanItem.MAX_SETS))) })
            if (item.timed) {
                val sec = item.sec ?: 30
                Stepper("Time", WorkoutMath.shortDuration(sec),
                    onMinus = { onChange(item.copy(sec = (sec - if (sec > 60) 15 else 5).coerceAtLeast(5))) },
                    onPlus = { onChange(item.copy(sec = (sec + if (sec >= 60) 15 else 5).coerceAtMost(3600))) })
            } else {
                val reps = item.reps ?: 10
                Stepper("Reps", "$reps",
                    onMinus = { onChange(item.copy(reps = (reps - 1).coerceAtLeast(1))) },
                    onPlus = { onChange(item.copy(reps = (reps + 1).coerceAtMost(100))) })
            }
            Stepper("Rest", if (item.restSec == 0) "None" else WorkoutMath.shortDuration(item.restSec),
                onMinus = { onChange(item.copy(restSec = RoutineEditorViewModel.nextRest(item.restSec, -1))) },
                onPlus = { onChange(item.copy(restSec = RoutineEditorViewModel.nextRest(item.restSec, 1))) })
        }
    }
}

@Composable
fun Stepper(label: String, value: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = TextMuted, modifier = Modifier.weight(1f))
        IconButton(onClick = onMinus) { Icon(Icons.Rounded.Remove, "Less $label", tint = TextPrimary) }
        Text(value, style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = TABULAR), color = Accent,
            textAlign = TextAlign.Center, modifier = Modifier.widthIn(min = 64.dp))
        IconButton(onClick = onPlus) { Icon(Icons.Rounded.Add, "More $label", tint = TextPrimary) }
    }
}
