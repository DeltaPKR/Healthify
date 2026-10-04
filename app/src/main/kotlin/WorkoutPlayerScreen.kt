package com.healthify.app.ui.workout

import android.media.AudioManager
import android.media.ToneGenerator
import android.os.SystemClock
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.healthify.app.data.db.WorkoutSessionEntity
import com.healthify.app.data.db.WorkoutSetEntity
import com.healthify.app.ui.theme.*
import com.healthify.app.units.UnitSystem
import com.healthify.app.workout.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private val Accent = Green

/** Sounds and haptics the player asks the screen to play. */
enum class Cue { COUNT, GO, DONE, REST_OVER }

/** What the user has typed into a set row that isn't logged yet. */
data class Draft(val weight: String = "", val reps: String = "", val warmup: Boolean = false)

/** A timed set: a 3-2-1 countdown, then it runs to [targetSec] (or until stopped). */
data class TimedSet(val slot: Int, val setIndex: Int, val startAt: Long, val targetSec: Int)

data class Rest(val endAt: Long, val totalSec: Int)

/**
 * The workout player. The session row exists from the moment the workout
 * starts and every set is saved when ticked, so a killed app loses nothing
 * but a running timer. Timers use elapsedRealtime, so they keep time while
 * the app is in the background; no foreground service is involved.
 */
class WorkoutViewModel(private val repo: WorkoutRepository, val sessionId: Long) : ViewModel() {

    val session: StateFlow<WorkoutSessionEntity?> =
        repo.sessionFlow(sessionId).stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val sets: StateFlow<List<WorkoutSetEntity>> =
        repo.setsFlow(sessionId).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    var items by mutableStateOf<List<PlanItem>>(emptyList())
        private set
    var exercises by mutableStateOf<Map<String, Exercise>>(emptyMap())
        private set
    /** Each exercise's best before this workout. */
    val history = mutableStateMapOf<String, Best>()
    /** The sets from the last time each exercise was done. */
    val lastTime = mutableStateMapOf<String, List<WorkoutSetEntity>>()
    val drafts = mutableStateMapOf<String, Draft>()
    var loaded by mutableStateOf(false)
        private set
    var gone by mutableStateOf(false)
        private set

    var rest by mutableStateOf<Rest?>(null)
        private set
    var timed by mutableStateOf<TimedSet?>(null)
        private set
    var now by mutableLongStateOf(SystemClock.elapsedRealtime())
        private set
    var wallNow by mutableLongStateOf(System.currentTimeMillis())
        private set
    val cues = MutableSharedFlow<Cue>(extraBufferCapacity = 8)

    private var lastCount = -1

    init {
        viewModelScope.launch {
            exercises = repo.exerciseMap()
            val s = session.filterNotNull().first()
            items = WorkoutPlan.fromJson(s.plan)
            loadHistory(items.map { it.exerciseId })
            loaded = true
        }
        viewModelScope.launch {
            // Wait for the first emission, then notice if the row disappears (discarded elsewhere).
            session.filterNotNull().first()
            session.collect { if (it == null) gone = true }
        }
        viewModelScope.launch {
            while (true) {
                now = SystemClock.elapsedRealtime()
                wallNow = System.currentTimeMillis()
                tick()
                delay(if (timed != null || rest != null) 100L else 500L)
            }
        }
    }

    private suspend fun loadHistory(ids: List<String>) {
        ids.distinct().filter { it !in history }.forEach { id ->
            history[id] = repo.best(id, excludeSession = sessionId)
            lastTime[id] = repo.lastTime(id, excludeSession = sessionId)
        }
    }

    private fun tick() {
        timed?.let { t ->
            val untilStart = t.startAt - now
            if (untilStart > 0) {
                val n = ((untilStart + 999) / 1000).toInt()
                if (n != lastCount) { lastCount = n; cues.tryEmit(Cue.COUNT) }
            } else {
                if (lastCount != 0) { lastCount = 0; cues.tryEmit(Cue.GO) }
                if (now - t.startAt >= t.targetSec * 1000L) {
                    cues.tryEmit(Cue.DONE)
                    completeTimed(t, t.targetSec)
                }
            }
        }
        rest?.let { r ->
            if (now >= r.endAt) { rest = null; cues.tryEmit(Cue.REST_OVER) }
        }
    }

    // ── Rows ─────────────────────────────────────────────────────────────────
    fun setsFor(slot: Int): List<WorkoutSetEntity> = sets.value.filter { it.slot == slot }

    private fun key(slot: Int, index: Int) = "$slot:$index"
    fun draft(slot: Int, index: Int): Draft = drafts[key(slot, index)] ?: Draft()
    fun editDraft(slot: Int, index: Int, d: Draft) { drafts[key(slot, index)] = d }

    /** Weight to suggest: this workout's previous set, else last time's, in kg. */
    fun suggestedWeight(item: PlanItem, index: Int): Float? {
        val earlier = setsFor(item.key).filter { it.setIndex < index }.maxByOrNull { it.setIndex }?.weightKg
        val last = lastTime[item.exerciseId].orEmpty()
        return earlier ?: last.getOrNull(index)?.weightKg ?: last.lastOrNull()?.weightKg
    }

    fun suggestedReps(item: PlanItem, index: Int): Int =
        item.reps ?: lastTime[item.exerciseId]?.getOrNull(index)?.reps ?: 10

    fun tracking(item: PlanItem): Tracking = exercises[item.exerciseId]?.tracking ?: Tracking.REPS

    /** Logs a counted set from its row (typed values, else the suggestions). */
    fun complete(item: PlanItem, index: Int, unit: UnitSystem): Boolean {
        val d = draft(item.key, index)
        val reps = d.reps.toIntOrNull()?.takeIf { it in 1..999 } ?: if (d.reps.isBlank()) suggestedReps(item, index) else return false
        val weighted = tracking(item) == Tracking.WEIGHT
        val kg = if (!weighted) null
            else if (d.weight.isBlank()) suggestedWeight(item, index) ?: return false
            else Weight.parse(d.weight, unit) ?: return false
        save(item, index, reps = reps, weightKg = kg, durationSec = null, warmup = d.warmup)
        return true
    }

    fun undo(set: WorkoutSetEntity, unit: UnitSystem) = viewModelScope.launch {
        drafts[key(set.slot, set.setIndex)] = Draft(
            weight = set.weightKg?.let { Weight.plain(it, unit) }.orEmpty(),
            reps = set.reps?.toString().orEmpty(),
            warmup = set.isWarmup
        )
        repo.deleteSet(set)
    }

    private fun save(item: PlanItem, index: Int, reps: Int?, weightKg: Float?, durationSec: Int?, warmup: Boolean) {
        viewModelScope.launch {
            repo.saveSet(
                WorkoutSetEntity(
                    sessionId = sessionId, exerciseId = item.exerciseId, slot = item.key, setIndex = index,
                    reps = reps, weightKg = weightKg, durationSec = durationSec, isWarmup = warmup
                )
            )
            drafts.remove(key(item.key, index))
            startRest(item, index)
        }
    }

    // ── Timers ───────────────────────────────────────────────────────────────
    fun startTimed(item: PlanItem, index: Int) {
        rest = null
        lastCount = -1
        timed = TimedSet(item.key, index, SystemClock.elapsedRealtime() + COUNTDOWN_MS, item.sec ?: 30)
    }

    /** Stops a timed set: before it starts, it's cancelled; after, what was done is logged. */
    fun stopTimed() {
        val t = timed ?: return
        val done = ((SystemClock.elapsedRealtime() - t.startAt) / 1000).toInt()
        if (done < 1) timed = null else completeTimed(t, done)
    }

    private fun completeTimed(t: TimedSet, sec: Int) {
        timed = null
        val item = items.firstOrNull { it.key == t.slot } ?: return
        save(item, t.setIndex, reps = null, weightKg = null, durationSec = sec, warmup = draft(t.slot, t.setIndex).warmup)
    }

    private fun startRest(item: PlanItem, index: Int) {
        val isLastSetOfWorkout = item.key == items.lastOrNull()?.key && index >= item.sets - 1
        if (item.restSec > 0 && !isLastSetOfWorkout) {
            rest = Rest(SystemClock.elapsedRealtime() + item.restSec * 1000L, item.restSec)
        }
    }

    fun adjustRest(bySec: Int) {
        val r = rest ?: return
        val end = r.endAt + bySec * 1000L
        rest = if (end <= SystemClock.elapsedRealtime()) null else r.copy(endAt = end, totalSec = (r.totalSec + bySec).coerceAtLeast(1))
    }

    fun skipRest() { rest = null }

    // ── Plan ─────────────────────────────────────────────────────────────────
    private fun updatePlan(newItems: List<PlanItem>) {
        items = newItems
        viewModelScope.launch { repo.savePlan(sessionId, newItems) }
    }

    fun addSet(item: PlanItem) = updatePlan(items.map {
        if (it.key == item.key) it.copy(sets = (it.sets + 1).coerceAtMost(PlanItem.MAX_SETS)) else it
    })

    /** Removes the last set row, if it isn't logged. */
    fun removeSet(item: PlanItem) {
        val logged = setsFor(item.key).size
        if (item.sets <= 1 || item.sets <= logged) return
        updatePlan(items.map { if (it.key == item.key) it.copy(sets = it.sets - 1) else it })
    }

    fun removeExercise(item: PlanItem) = viewModelScope.launch {
        if (timed?.slot == item.key) timed = null
        items = items.filterNot { it.key == item.key }
        repo.removeItem(sessionId, items + item, item.key)
    }

    fun addExercises(ids: List<String>) = viewModelScope.launch {
        exercises = repo.exerciseMap()
        var next = items
        ids.forEach { id ->
            next = next + PlanItem.defaultFor(WorkoutPlan.nextKey(next), id, exercises[id]?.tracking ?: Tracking.REPS)
        }
        updatePlan(next)
        loadHistory(ids)
    }

    /** Sets that beat everything before them (history and earlier sets today). */
    fun liveRecords(all: List<WorkoutSetEntity>): Set<Long> {
        val out = mutableSetOf<Long>()
        all.groupBy { it.exerciseId }.forEach { (exId, list) ->
            var best = history[exId] ?: return@forEach
            val tracking = exercises[exId]?.tracking ?: Tracking.REPS
            list.sortedBy { it.completedAt }.forEach { s ->
                if (WorkoutMath.recordsFor(s, best, tracking).isNotEmpty()) out += s.id
                best = best.with(s)
            }
        }
        return out
    }

    val plannedSets: Int get() = items.sumOf { it.sets }

    suspend fun finish(): Boolean = repo.finish(sessionId) != null
    suspend fun discard() = repo.discard(sessionId)

    class Factory(private val repo: WorkoutRepository, private val sessionId: Long) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = WorkoutViewModel(repo, sessionId) as T
    }

    companion object {
        const val COUNTDOWN_MS = 3_000L
    }
}

@Composable
fun WorkoutPlayerScreen(
    vm: WorkoutViewModel,
    unit: UnitSystem,
    onLeave: () -> Unit,
    onAddExercises: () -> Unit,
    onFinished: (Long) -> Unit,
) {
    val session by vm.session.collectAsState()
    val sets by vm.sets.collectAsState()
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    var finishAsk by remember { mutableStateOf(false) }
    var discardAsk by remember { mutableStateOf(false) }
    var instructionsFor by remember { mutableStateOf<Exercise?>(null) }
    var removeAsk by remember { mutableStateOf<PlanItem?>(null) }

    KeepScreenOn()
    CuePlayer(vm, haptics)
    LaunchedEffect(vm.gone) { if (vm.gone) onLeave() }

    val records = remember(sets, vm.history.toMap()) { vm.liveRecords(sets) }
    val s = session

    Scaffold(
        topBar = {
            PlayerHeader(
                title = s?.title ?: "Workout",
                elapsed = s?.let { WorkoutMath.clock(((vm.wallNow - it.startedAt) / 1000).toInt()) } ?: "",
                onBack = onLeave,
                onFinish = {
                    if (sets.isEmpty() || sets.size < vm.plannedSets) finishAsk = true
                    else scope.launch { if (vm.finish()) onFinished(vm.sessionId) }
                }
            )
        },
        bottomBar = { TimerBar(vm) },
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0)
    ) { pad ->
        if (!vm.loaded) return@Scaffold
        LazyColumn(
            Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (vm.items.isEmpty()) item {
                Text("Add the exercises you're doing as you go. Each set is saved when you tick it.",
                    style = MaterialTheme.typography.bodyMedium, color = TextMuted, modifier = Modifier.padding(4.dp))
            }
            items(vm.items, key = { it.key }) { item ->
                ExerciseBlock(
                    vm = vm,
                    item = item,
                    logged = sets.filter { it.slot == item.key },
                    records = records,
                    unit = unit,
                    onTick = { haptics.confirm() },
                    onInstructions = { vm.exercises[item.exerciseId]?.let { instructionsFor = it } },
                    onRemove = { if (sets.any { it.slot == item.key }) removeAsk = item else vm.removeExercise(item) },
                )
            }
            item { GhostButton("+ Add exercise", onAddExercises, color = Accent) }
            item { GhostButton("Discard workout", { discardAsk = true }, color = Coral) }
        }
    }

    if (finishAsk) {
        val nothing = sets.isEmpty()
        val left = (vm.plannedSets - sets.size).coerceAtLeast(0)
        GlassDialog(onDismiss = { finishAsk = false }, accent = if (nothing) Coral else Accent) {
            IconOrb(if (nothing) Coral else Accent, size = 56.dp) { Text(if (nothing) "🤔" else "🏁", fontSize = 26.sp) }
            Spacer(Modifier.height(16.dp))
            Text(if (nothing) "Nothing logged yet" else "Finish now?", style = MaterialTheme.typography.headlineSmall, color = TextPrimary)
            Spacer(Modifier.height(8.dp))
            Text(
                if (nothing) "Tick sets as you do them. If you're stopping here, discard this workout."
                else "$left set${if (left == 1) "" else "s"} aren't ticked. Only the sets you ticked are saved.",
                style = MaterialTheme.typography.bodyMedium, color = TextMuted
            )
            Spacer(Modifier.height(22.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GhostButton("Keep going", { finishAsk = false }, Modifier.weight(1f))
                if (nothing) GlowButton("Discard", { finishAsk = false; scope.launch { vm.discard(); onLeave() } }, Modifier.weight(1f), accent = Coral, height = 52.dp)
                else GlowButton("Finish", { finishAsk = false; scope.launch { if (vm.finish()) onFinished(vm.sessionId) } }, Modifier.weight(1f), height = 52.dp)
            }
        }
    }
    if (discardAsk) {
        ConfirmDialog(
            emoji = "🗑️", title = "Discard this workout?",
            message = "The sets you've ticked will be deleted. This can't be undone.",
            confirmLabel = "Discard",
            onConfirm = { discardAsk = false; scope.launch { vm.discard(); onLeave() } },
            onDismiss = { discardAsk = false }
        )
    }
    removeAsk?.let { item ->
        ConfirmDialog(
            emoji = "✂️", title = "Remove ${vm.exercises[item.exerciseId]?.name ?: "this exercise"}?",
            message = "Its ticked sets will be removed from this workout.",
            confirmLabel = "Remove",
            onConfirm = { removeAsk = null; vm.removeExercise(item) },
            onDismiss = { removeAsk = null }
        )
    }
    instructionsFor?.let { InstructionsDialog(it) { instructionsFor = null } }
}

@Composable
private fun PlayerHeader(title: String, elapsed: String, onBack: () -> Unit, onFinish: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        GlassIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "Leave the workout running", onBack)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text("WORKOUT · $elapsed", style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = TABULAR), color = Accent, maxLines = 1)
            Text(title, style = MaterialTheme.typography.headlineSmall, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(10.dp))
        GlowButton("Finish", onFinish, Modifier.width(96.dp), height = 44.dp)
    }
}

@Composable
private fun ExerciseBlock(
    vm: WorkoutViewModel,
    item: PlanItem,
    logged: List<WorkoutSetEntity>,
    records: Set<Long>,
    unit: UnitSystem,
    onTick: () -> Unit,
    onInstructions: () -> Unit,
    onRemove: () -> Unit,
) {
    val exercise = vm.exercises[item.exerciseId]
    val tracking = vm.tracking(item)
    val weighted = tracking == Tracking.WEIGHT && !item.timed
    val rows = maxOf(item.sets, (logged.maxOfOrNull { it.setIndex } ?: -1) + 1)
    var menu by remember { mutableStateOf(false) }
    val doneAll = logged.size >= item.sets

    GlassCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), tint = if (doneAll) Accent else null) {
        Column(Modifier.padding(start = 14.dp, end = 6.dp, top = 12.dp, bottom = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).clickable(onClickLabel = "How to do it", onClick = onInstructions)) {
                    Text(exercise?.name ?: "Unknown exercise", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                    val sub = listOfNotNull(item.targetLabel() + if (item.restSec > 0) " · rest ${WorkoutMath.shortDuration(item.restSec)}" else "",
                        item.note.ifBlank { null })
                    Text(sub.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = TextMuted)
                }
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, "Exercise options", tint = TextMuted) }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("How to do it") }, onClick = { menu = false; onInstructions() })
                        DropdownMenuItem(text = { Text("Remove exercise", color = Coral) }, onClick = { menu = false; onRemove() })
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth().padding(end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                HeaderCell("SET", Modifier.width(34.dp))
                HeaderCell("LAST", Modifier.weight(1.2f))
                if (weighted) HeaderCell(Weight.unitLabel(unit).uppercase(), Modifier.weight(1f))
                HeaderCell(if (item.timed) "TIME" else "REPS", Modifier.weight(1f))
                Spacer(Modifier.width(52.dp))
            }
            for (i in 0 until rows) {
                val set = logged.firstOrNull { it.setIndex == i }
                SetRow(vm, item, i, set, weighted, set != null && set.id in records, unit, onTick)
            }
            Row {
                TextButton(onClick = { vm.addSet(item) }) { Text("+ Add set", color = Accent) }
                if (item.sets > 1 && item.sets > logged.size) {
                    TextButton(onClick = { vm.removeSet(item) }) { Text("− Remove set", color = TextMuted) }
                }
            }
        }
    }
}

@Composable
private fun HeaderCell(text: String, modifier: Modifier) {
    Text(text, style = MaterialTheme.typography.labelSmall, color = TextDim, textAlign = TextAlign.Center, modifier = modifier)
}

@Composable
private fun SetRow(
    vm: WorkoutViewModel,
    item: PlanItem,
    index: Int,
    set: WorkoutSetEntity?,
    weighted: Boolean,
    record: Boolean,
    unit: UnitSystem,
    onTick: () -> Unit,
) {
    val draft = vm.draft(item.key, index)
    val warm = set?.isWarmup ?: draft.warmup
    val last = vm.lastTime[item.exerciseId]?.getOrNull(index)
    val running = vm.timed?.let { it.slot == item.key && it.setIndex == index } == true
    val done = set != null
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp, horizontal = 0.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (done) Accent.copy(alpha = 0.10f) else Color.Transparent)
            .padding(end = 8.dp, top = 3.dp, bottom = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Tap the number to mark a warm-up set (not counted for records).
        Box(
            Modifier.width(34.dp).height(40.dp)
                .clickable(enabled = !done, onClickLabel = if (warm) "Mark as a working set" else "Mark as a warm-up set") {
                    vm.editDraft(item.key, index, draft.copy(warmup = !draft.warmup))
                },
            contentAlignment = Alignment.Center
        ) {
            Text(if (warm) "W" else "${index + 1}", style = MaterialTheme.typography.titleSmall, color = if (warm) Gold else TextMuted)
        }
        Text(last?.label(unit, compact = true) ?: "—", style = MaterialTheme.typography.bodySmall, color = TextDim,
            textAlign = TextAlign.Center, maxLines = 1, modifier = Modifier.weight(1.2f))
        if (weighted) {
            if (done) DoneCell(set!!.weightKg?.let { Weight.plain(it, unit) } ?: "—", Modifier.weight(1f))
            else NumberCell(
                draft.weight, { vm.editDraft(item.key, index, draft.copy(weight = it)) },
                Modifier.weight(1f).padding(horizontal = 3.dp),
                placeholder = vm.suggestedWeight(item, index)?.let { Weight.plain(it, unit) } ?: "",
                decimal = true
            )
        }
        if (item.timed) {
            val label = set?.durationSec?.let { WorkoutMath.shortDuration(it) } ?: WorkoutMath.shortDuration(item.sec ?: 30)
            DoneCell(label, Modifier.weight(1f), dim = !done)
        } else if (done) {
            DoneCell(set!!.reps?.toString() ?: "—", Modifier.weight(1f))
        } else {
            NumberCell(
                draft.reps, { vm.editDraft(item.key, index, draft.copy(reps = it)) },
                Modifier.weight(1f).padding(horizontal = 3.dp),
                placeholder = "${vm.suggestedReps(item, index)}"
            )
        }
        Spacer(Modifier.width(8.dp))
        when {
            done -> TickButton(done = true, record = record, label = "Undo set ${index + 1}") { vm.undo(set!!, unit) }
            item.timed -> PlayButton(running, "Start set ${index + 1}") { if (running) vm.stopTimed() else vm.startTimed(item, index) }
            else -> TickButton(done = false, record = false, label = "Done: set ${index + 1}") {
                if (vm.complete(item, index, unit)) onTick()
            }
        }
    }
}

@Composable
private fun DoneCell(text: String, modifier: Modifier, dim: Boolean = false) {
    Text(text, style = MaterialTheme.typography.titleSmall.copy(fontFeatureSettings = TABULAR),
        color = if (dim) TextMuted else TextPrimary, textAlign = TextAlign.Center, modifier = modifier)
}

@Composable
private fun TickButton(done: Boolean, record: Boolean, label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(if (done) Brush.linearGradient(listOf(Accent, Sky)) else Brush.linearGradient(listOf(GlassFillTop, GlassFillBottom)))
            .border(1.dp, if (done) Color.Transparent else GlassBorderTop, CircleShape)
            .clickable(onClickLabel = label, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (record) Text("🏆", fontSize = 18.sp)
        else Icon(Icons.Rounded.Check, contentDescription = label, tint = if (done) Color(0xFF06121C) else TextMuted)
    }
}

@Composable
private fun PlayButton(running: Boolean, label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(Accent.copy(alpha = if (running) 0.35f else 0.16f))
            .border(1.dp, Accent.copy(alpha = 0.6f), CircleShape)
            .clickable(onClickLabel = if (running) "Stop" else label, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (running) Box(Modifier.size(14.dp).background(Accent, RoundedCornerShape(3.dp)))
        else Icon(Icons.Rounded.PlayArrow, contentDescription = label, tint = Accent)
    }
}

/** The rest countdown or the running timed set, docked at the bottom. */
@Composable
private fun TimerBar(vm: WorkoutViewModel) {
    val rest = vm.rest
    val timed = vm.timed
    AnimatedVisibility(
        visible = rest != null || timed != null,
        enter = slideInVertically { it } + fadeIn(),
        exit = slideOutVertically { it } + fadeOut()
    ) {
        Box(Modifier.navigationBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp)) {
            GlassCard(Modifier.fillMaxWidth(), tint = if (timed != null) Gold else Sky, shape = RoundedCornerShape(24.dp)) {
                if (timed != null) TimedPanel(vm, timed) else if (rest != null) RestPanel(vm, rest)
            }
        }
    }
}

@Composable
private fun RestPanel(vm: WorkoutViewModel, rest: Rest) {
    val left = ((rest.endAt - vm.now + 999) / 1000).toInt().coerceAtLeast(0)
    Column(Modifier.padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("REST", style = MaterialTheme.typography.labelSmall, color = Sky)
                Text(WorkoutMath.clock(left), style = MaterialTheme.typography.headlineMedium.copy(fontFeatureSettings = TABULAR), color = TextPrimary)
            }
            TextButton(onClick = { vm.adjustRest(-15) }) { Text("−15", color = TextMuted) }
            TextButton(onClick = { vm.adjustRest(15) }) { Text("+15", color = TextMuted) }
            TextButton(onClick = vm::skipRest) { Text("Skip", color = Sky) }
        }
        Spacer(Modifier.height(8.dp))
        ProgressLine((left.toFloat() / rest.totalSec).coerceIn(0f, 1f), Sky)
    }
}

@Composable
private fun TimedPanel(vm: WorkoutViewModel, t: TimedSet) {
    val name = vm.items.firstOrNull { it.key == t.slot }?.let { vm.exercises[it.exerciseId]?.name }.orEmpty()
    val untilStart = t.startAt - vm.now
    val elapsed = ((vm.now - t.startAt) / 1000).toInt().coerceAtLeast(0)
    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(if (untilStart > 0) "GET READY" else name.uppercase(), style = MaterialTheme.typography.labelSmall, color = Gold, maxLines = 1)
            Text(
                if (untilStart > 0) "${(untilStart + 999) / 1000}"
                else "${WorkoutMath.clock(elapsed)} / ${WorkoutMath.clock(t.targetSec)}",
                style = MaterialTheme.typography.headlineMedium.copy(fontFeatureSettings = TABULAR), color = TextPrimary
            )
            if (untilStart <= 0) {
                Spacer(Modifier.height(8.dp))
                ProgressLine((elapsed.toFloat() / t.targetSec).coerceIn(0f, 1f), Gold)
            }
        }
        Spacer(Modifier.width(12.dp))
        GlowButton(if (untilStart > 0) "Cancel" else "Stop", vm::stopTimed, Modifier.width(100.dp), accent = Gold, height = 48.dp)
    }
}

@Composable
private fun ProgressLine(fraction: Float, color: Color) {
    Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(Color.White.copy(alpha = 0.1f))) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(fraction).clip(RoundedCornerShape(3.dp)).background(color))
    }
}

@Composable
private fun KeepScreenOn() {
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
}

/** Beeps on the notification stream (silent when the phone is) plus haptics. */
@Composable
private fun CuePlayer(vm: WorkoutViewModel, haptics: Haptics) {
    val tone = remember { runCatching { ToneGenerator(AudioManager.STREAM_NOTIFICATION, 70) }.getOrNull() }
    DisposableEffect(tone) { onDispose { tone?.release() } }
    LaunchedEffect(vm) {
        vm.cues.collect { cue ->
            runCatching {
                when (cue) {
                    Cue.COUNT     -> { tone?.startTone(ToneGenerator.TONE_PROP_BEEP, 120); haptics.tick() }
                    Cue.GO        -> { tone?.startTone(ToneGenerator.TONE_PROP_BEEP2, 250); haptics.confirm() }
                    Cue.DONE      -> { tone?.startTone(ToneGenerator.TONE_PROP_ACK, 400); haptics.heavy() }
                    Cue.REST_OVER -> { tone?.startTone(ToneGenerator.TONE_PROP_BEEP2, 400); haptics.heavy() }
                }
            }
        }
    }
}
