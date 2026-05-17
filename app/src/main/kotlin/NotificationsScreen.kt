package com.healthify.app.ui.notifications

import android.content.Context
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.healthify.app.data.db.ReminderEntity
import com.healthify.app.data.repository.AppRepository
import com.healthify.app.notifications.NotificationScheduler
import com.healthify.app.ui.theme.*
import kotlinx.coroutines.launch
import kotlin.math.abs

// ── Icon catalog -> (emoji, category) ────────────────────────────────────────
private data class IconChoice(val emoji: String, val category: String, val label: String)

private data class Category(val id: String, val emoji: String, val label: String)

private val CATEGORIES = listOf(
    Category("water",    "💧", "Water"),
    Category("meds",     "💊", "Meds"),
    Category("movement", "🏃", "Movement"),
    Category("wellness", "🧘", "Wellness"),
    Category("general",  "✨", "General")
)

private val ICON_CATALOG = listOf(
    IconChoice("💧", "water",    "Water"),
    IconChoice("☕", "water",    "Drink"),
    IconChoice("💊", "meds",     "Meds"),
    IconChoice("🩺", "meds",     "Health"),
    IconChoice("🏃", "movement", "Run"),
    IconChoice("🚶", "movement", "Walk"),
    IconChoice("💪", "movement", "Workout"),
    IconChoice("🧘", "wellness", "Meditate"),
    IconChoice("🌙", "wellness", "Sleep"),
    IconChoice("🛌", "wellness", "Rest"),
    IconChoice("❤️", "wellness", "Check-in"),
    IconChoice("🧠", "wellness", "Mental"),
    IconChoice("🥗", "general",  "Eat"),
    IconChoice("🍎", "general",  "Snack"),
    IconChoice("✨", "general",  "Self-care"),
    IconChoice("⏰", "general",  "Alarm"),
    IconChoice("🌿", "general",  "Habit"),
    IconChoice("🔔", "general",  "Other")
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(repo: AppRepository, context: Context, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val reminders by repo.getAllReminders().collectAsState(emptyList())

    var showEditor by remember { mutableStateOf(false) }
    var editing    by remember { mutableStateOf<ReminderEntity?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Reminders", style = MaterialTheme.typography.headlineMedium) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, null, tint = TextMuted)
                    }
                },
                actions = {
                    IconButton(onClick = {
                        editing = null
                        showEditor = true
                    }) { Icon(Icons.Default.Add, "Add reminder", tint = Green) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BgDark)
            )
        },
        containerColor = BgDark
    ) { pad ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(pad)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ── Category summary ──────────────────────────────────────────
            val cats = reminders.groupBy { it.category }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(
                    "water"    to (Sky      to "💧"),
                    "meds"     to (Coral    to "💊"),
                    "wellness" to (Lavender to "🧘")
                ).forEach { (cat, pair) ->
                    val (color, icon) = pair
                    val count = cats[cat]?.count { it.enabled } ?: 0
                    Card(
                        Modifier.weight(1f),
                        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                        shape  = RoundedCornerShape(14.dp)
                    ) {
                        Column(
                            Modifier.padding(14.dp).fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(icon, fontSize = 22.sp)
                            Text(count.toString(),
                                style = MaterialTheme.typography.headlineSmall, color = color)
                            Text(cat.replaceFirstChar { it.uppercase() },
                                style = MaterialTheme.typography.bodySmall, color = TextMuted)
                        }
                    }
                }
            }

            Spacer(Modifier.height(4.dp))

            // ── Reminders list / empty state ──────────────────────────────
            if (reminders.isEmpty()) {
                Card(
                    Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                    shape  = RoundedCornerShape(18.dp)
                ) {
                    Column(
                        Modifier.padding(28.dp).fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("🔕", fontSize = 38.sp)
                        Text("No reminders yet",
                            style = MaterialTheme.typography.titleLarge, color = TextPrimary)
                        Text(
                            "Tap + above to create your first reminder. We'll nudge you at the time you choose.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextMuted,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(6.dp))
                        Button(
                            onClick = {
                                editing = null
                                showEditor = true
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Green),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text("+ Add reminder",
                                color = MaterialTheme.colorScheme.onPrimary,
                                style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
            } else {
                Text("YOUR REMINDERS",
                    style = MaterialTheme.typography.labelSmall, color = TextMuted,
                    modifier = Modifier.padding(start = 4.dp, top = 4.dp))

                reminders.forEach { reminder ->
                    ReminderCard(
                        reminder = reminder,
                        onToggle = { enabled ->
                            scope.launch {
                                repo.toggleReminder(reminder.id, enabled)
                                if (enabled) {
                                    NotificationScheduler.schedule(context, reminder.copy(enabled = true))
                                } else {
                                    NotificationScheduler.cancel(context, reminder)
                                }
                            }
                        },
                        onEdit = {
                            editing = reminder
                            showEditor = true
                        },
                        onDelete = {
                            scope.launch {
                                NotificationScheduler.cancel(context, reminder)
                                repo.deleteReminder(reminder)
                            }
                        }
                    )
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }

    // ── Add / edit dialog ────────────────────────────────────────────────
    if (showEditor) {
        ReminderEditorDialog(
            initial = editing,
            onDismiss = { showEditor = false },
            onSave = { result ->
                showEditor = false
                scope.launch {
                    // Cancel any prior alarm (matches by reminder id) before
                    // writing the new state, so an in-flight fire can't race
                    // with the upcoming schedule call.
                    if (editing != null) {
                        NotificationScheduler.cancel(context, editing!!)
                    }
                    val newId = repo.saveReminder(result).toInt()
                    val saved = result.copy(id = if (result.id == 0) newId else result.id)
                    if (saved.enabled) {
                        NotificationScheduler.schedule(context, saved)
                    } else {
                        // Saved as disabled — ensure no stale alarm survives.
                        NotificationScheduler.cancel(context, saved)
                    }
                }
            },
            onDelete = if (editing != null) {
                {
                    val target = editing!!
                    showEditor = false
                    scope.launch {
                        NotificationScheduler.cancel(context, target)
                        repo.deleteReminder(target)
                    }
                }
            } else null
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// REMINDER CARD
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ReminderCard(
    reminder: ReminderEntity,
    onToggle: (Boolean) -> Unit,
    onEdit:   () -> Unit,
    onDelete: () -> Unit
) {
    val catColor = when (reminder.category) {
        "water"    -> Sky
        "meds"     -> Coral
        "movement" -> Green
        "wellness" -> Lavender
        else       -> TextMuted
    }
    val catColorDim = when (reminder.category) {
        "water"    -> SkyDim
        "meds"     -> CoralDim
        "movement" -> GreenDim
        "wellness" -> LavenderDim
        else       -> Divider
    }

    var showConfirmDelete by remember { mutableStateOf(false) }

    Card(
        onClick  = onEdit,
        modifier = Modifier.fillMaxWidth().then(
            if (!reminder.enabled) Modifier.alpha(0.55f) else Modifier
        ),
        colors   = CardDefaults.cardColors(containerColor = SurfaceCard),
        shape    = RoundedCornerShape(18.dp)
    ) {
        Row(
            Modifier.padding(14.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(catColorDim),
                contentAlignment = Alignment.Center
            ) { Text(reminder.emoji, fontSize = 20.sp) }

            Column(Modifier.weight(1f)) {
                Text(reminder.label,
                    style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                Text(
                    "%02d:%02d · ${reminder.repeatDays.split(",").size}x/week"
                        .format(reminder.hourOfDay, reminder.minute),
                    style = MaterialTheme.typography.bodySmall,
                    color = catColor
                )
            }

            IconButton(
                onClick = { showConfirmDelete = true },
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(CoralDim)
            ) {
                Icon(Icons.Default.Delete, "Delete reminder", tint = Coral,
                    modifier = Modifier.size(22.dp))
            }

            Switch(
                checked = reminder.enabled,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(
                    checkedThumbColor      = Color.White,
                    checkedTrackColor      = Green,
                    uncheckedThumbColor    = TextDim,
                    uncheckedTrackColor    = SurfaceCard2
                )
            )
        }
    }

    if (showConfirmDelete) {
        AlertDialog(
            onDismissRequest = { showConfirmDelete = false },
            title = { Text("Delete reminder?", color = TextPrimary) },
            text = { Text("\"${reminder.label}\" will be removed and won't notify you anymore.",
                color = TextMuted) },
            confirmButton = {
                TextButton(onClick = {
                    showConfirmDelete = false
                    onDelete()
                }) { Text("Delete", color = Coral) }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDelete = false }) {
                    Text("Cancel", color = TextMuted)
                }
            },
            containerColor = SurfaceCard,
            shape = RoundedCornerShape(20.dp)
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// EDITOR DIALOG: name, icon picker, time, save/delete
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReminderEditorDialog(
    initial: ReminderEntity?,
    onDismiss: () -> Unit,
    onSave: (ReminderEntity) -> Unit,
    onDelete: (() -> Unit)?
) {
    val isEdit = initial != null
    var label  by remember { mutableStateOf(initial?.label ?: "") }
    var emoji  by remember { mutableStateOf(initial?.emoji ?: "💧") }
    var category by remember { mutableStateOf(initial?.category ?: "water") }
    var hour   by remember { mutableStateOf(initial?.hourOfDay ?: 9) }
    var minute by remember { mutableStateOf(initial?.minute ?: 0) }
    val days = remember {
        val parsed = (initial?.repeatDays ?: "1,2,3,4,5,6,7")
            .split(",").mapNotNull { it.trim().toIntOrNull() }.toSet()
        mutableStateListOf<Int>().also { it.addAll(parsed.ifEmpty { setOf(1,2,3,4,5,6,7) }.sorted()) }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(Modifier.padding(horizontal = 18.dp)) {
            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                shape = RoundedCornerShape(22.dp)
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(22.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Header
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (isEdit) "Edit reminder" else "New reminder",
                            style = MaterialTheme.typography.headlineMedium, color = TextPrimary,
                            modifier = Modifier.weight(1f)
                        )
                        Text(emoji, fontSize = 30.sp)
                    }

                    // Name
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("NAME", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                        OutlinedTextField(
                            value = label,
                            onValueChange = { if (it.length <= 40) label = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            placeholder = { Text("e.g. Take vitamin D", color = TextDim) },
                            shape = RoundedCornerShape(12.dp),
                            colors = dialogTextFieldColors()
                        )
                    }

                    // Category chips (explicit type selection)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("CATEGORY",
                            style = MaterialTheme.typography.labelSmall, color = TextMuted)
                        CategoryChips(
                            selected = category,
                            onSelect = { newCat ->
                                if (newCat != category) {
                                    category = newCat
                                    // Reset emoji to first icon of the new category
                                    emoji = ICON_CATALOG.first { it.category == newCat }.emoji
                                }
                            }
                        )
                    }

                    // Icon picker grid (filtered by selected category)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("ICON", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                        IconGrid(
                            category = category,
                            selected = emoji,
                            onSelect = { choice -> emoji = choice.emoji }
                        )
                    }

                    // Time
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("TIME", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                        TimeStepper(
                            hour = hour,
                            minute = minute,
                            onHourChange = { hour = it },
                            onMinuteChange = { minute = it }
                        )
                    }

                    // Days of week
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("REPEATS", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                        DayChips(
                            selected = days,
                            onToggle = { d ->
                                if (d in days) days.remove(d) else days.add(d)
                            }
                        )
                    }

                    Spacer(Modifier.height(2.dp))

                    // Delete (only when editing) — clear, full-width, with confirm
                    if (isEdit && onDelete != null) {
                        var confirmDelete by remember { mutableStateOf(false) }
                        OutlinedButton(
                            onClick = { confirmDelete = true },
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            shape = RoundedCornerShape(13.dp),
                            border = BorderStroke(1.dp, Coral.copy(alpha = 0.6f))
                        ) {
                            Icon(Icons.Default.Delete, null, tint = Coral,
                                modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Delete reminder", color = Coral,
                                style = MaterialTheme.typography.titleMedium)
                        }
                        if (confirmDelete) {
                            AlertDialog(
                                onDismissRequest = { confirmDelete = false },
                                title = { Text("Delete reminder?", color = TextPrimary) },
                                text = { Text(
                                    "\"${label.ifBlank { initial?.label ?: "Reminder" }}\" will be removed and won't notify you anymore.",
                                    color = TextMuted) },
                                confirmButton = {
                                    TextButton(onClick = {
                                        confirmDelete = false
                                        onDelete()
                                    }) { Text("Delete", color = Coral) }
                                },
                                dismissButton = {
                                    TextButton(onClick = { confirmDelete = false }) {
                                        Text("Cancel", color = TextMuted)
                                    }
                                },
                                containerColor = SurfaceCard,
                                shape = RoundedCornerShape(20.dp)
                            )
                        }
                    }

                    // Cancel / Save
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f).height(48.dp),
                            shape = RoundedCornerShape(13.dp),
                            border = BorderStroke(1.dp, Divider)
                        ) { Text("Cancel", color = TextMuted) }
                        Button(
                            onClick = {
                                val result = (initial ?: ReminderEntity()).copy(
                                    label      = label.trim().ifBlank { "Reminder" },
                                    emoji      = emoji,
                                    hourOfDay  = hour,
                                    minute     = minute,
                                    category   = category,
                                    enabled    = initial?.enabled ?: true,
                                    repeatDays = days.sorted().joinToString(",")
                                )
                                onSave(result)
                            },
                            modifier = Modifier.weight(1f).height(48.dp),
                            shape = RoundedCornerShape(13.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Green),
                            enabled = days.isNotEmpty()
                        ) {
                            Text(if (isEdit) "Save" else "Add",
                                color = MaterialTheme.colorScheme.onPrimary,
                                style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
            }
        }
    }
}

// ── Category chips (explicit type picker) ────────────────────────────────────
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategoryChips(selected: String, onSelect: (String) -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        CATEGORIES.forEach { cat ->
            val sel = cat.id == selected
            val (color, dim) = when (cat.id) {
                "water"    -> Sky      to SkyDim
                "meds"     -> Coral    to CoralDim
                "movement" -> Green    to GreenDim
                "wellness" -> Lavender to LavenderDim
                else       -> Gold     to GoldDim
            }
            Box(
                Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (sel) dim else SurfaceCard2)
                    .border(
                        1.5.dp,
                        if (sel) color else Divider,
                        RoundedCornerShape(20.dp)
                    )
                    .clickable { onSelect(cat.id) }
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(cat.emoji, fontSize = 16.sp)
                    Text(
                        cat.label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (sel) color else TextMuted
                    )
                }
            }
        }
    }
}

// ── Day-of-week chips (Mon..Sun = 1..7, ISO) ────────────────────────────────
@Composable
private fun DayChips(selected: List<Int>, onToggle: (Int) -> Unit) {
    val labels = listOf("M", "T", "W", "T", "F", "S", "S")
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
        labels.forEachIndexed { idx, lbl ->
            val day = idx + 1
            val sel = day in selected
            Box(
                Modifier
                    .weight(1f)
                    .height(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (sel) GreenDim else SurfaceCard2)
                    .border(
                        1.5.dp,
                        if (sel) Green else Divider,
                        RoundedCornerShape(12.dp)
                    )
                    .clickable { onToggle(day) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    lbl,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (sel) Green else TextMuted
                )
            }
        }
    }
}

// ── Icon grid (filtered by category) ─────────────────────────────────────────
@Composable
private fun IconGrid(category: String, selected: String, onSelect: (IconChoice) -> Unit) {
    val filtered = ICON_CATALOG.filter { it.category == category }
    val rows = filtered.chunked(6)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { choice ->
                    val sel = choice.emoji == selected
                    Box(
                        Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (sel) GreenDim else SurfaceCard2)
                            .border(
                                1.5.dp,
                                if (sel) Green else Divider,
                                RoundedCornerShape(12.dp)
                            )
                            .clickable { onSelect(choice) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(choice.emoji, fontSize = 22.sp)
                    }
                }
                // pad short rows
                repeat(6 - row.size) {
                    Box(Modifier.weight(1f))
                }
            }
        }
    }
}

// ── Time picker (Android-alarm-style scroll wheels) ──────────────────────────
@Composable
private fun TimeStepper(
    hour: Int,
    minute: Int,
    onHourChange: (Int) -> Unit,
    onMinuteChange: (Int) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = SurfaceCard2),
        shape  = RoundedCornerShape(14.dp)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            WheelPicker(
                label = "HOUR",
                range = 0..23,
                value = hour,
                onValueChange = onHourChange,
                modifier = Modifier.weight(1f)
            )
            Text(
                ":",
                fontSize = 32.sp,
                color = TextPrimary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
            WheelPicker(
                label = "MIN",
                range = 0..59,
                value = minute,
                onValueChange = onMinuteChange,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/**
 * Vertical scrolling number wheel — looks/feels like Android's alarm-clock
 * time picker. Wraps infinitely so swiping past 23 lands on 0 (hours) or 59
 * lands on 0 (minutes). Snaps to whole rows. The currently centered row is
 * highlighted between two thin dividers and rendered at full opacity.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WheelPicker(
    label: String,
    range: IntRange,
    value: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val count            = range.last - range.first + 1
    val visibleCount     = 5
    val visibleHalfCount = visibleCount / 2          // 2 rows above/below center
    val itemHeight       = 40.dp

    // Repeat the value list so the wheel scrolls effectively forever in both
    // directions. baseRepeat * count items; start near the middle so the user
    // can scroll either way without hitting an edge.
    val baseRepeat = 2000
    val totalCount = baseRepeat * count

    val initialFirstVisible = remember(value, range) {
        // Place the requested `value` at the centered row when the picker
        // first lays out. firstVisibleItemIndex points to the row at the TOP
        // of the viewport, so subtract visibleHalfCount.
        val centeredIdx = (baseRepeat / 2) * count + (value - range.first)
        centeredIdx - visibleHalfCount
    }

    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialFirstVisible)
    val flingBehavior = rememberSnapFlingBehavior(lazyListState = listState)

    // Centered row's absolute index in the LazyColumn.
    val centeredAbsIndex by remember {
        derivedStateOf { listState.firstVisibleItemIndex + visibleHalfCount }
    }

    // Whenever the scroll comes to rest on a new row, propagate it.
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }.collect { scrolling ->
            if (!scrolling) {
                val itemValue = (centeredAbsIndex % count) + range.first
                if (itemValue != value) onValueChange(itemValue)
            }
        }
    }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = Green)
        Spacer(Modifier.height(4.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(itemHeight * visibleCount),
            contentAlignment = Alignment.Center
        ) {
            LazyColumn(
                state = listState,
                flingBehavior = flingBehavior,
                modifier = Modifier.fillMaxSize()
            ) {
                items(totalCount, key = { it }) { i ->
                    val labelValue = (i % count) + range.first
                    val distance   = abs(i - centeredAbsIndex)
                    val alpha = when (distance) {
                        0    -> 1f
                        1    -> 0.55f
                        else -> 0.25f
                    }
                    val isCenter = distance == 0
                    Box(
                        Modifier
                            .height(itemHeight)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "%02d".format(labelValue),
                            fontSize = if (isCenter) 26.sp else 22.sp,
                            fontWeight = if (isCenter) FontWeight.Bold else FontWeight.Normal,
                            color = if (isCenter) TextPrimary else TextPrimary.copy(alpha = alpha),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
            // Highlighted selection window around the centered row.
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(itemHeight)
                    .align(Alignment.Center)
                    .border(
                        width = 1.dp,
                        color = Green.copy(alpha = 0.45f),
                        shape = RoundedCornerShape(8.dp)
                    )
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun dialogTextFieldColors() = TextFieldDefaults.colors(
    focusedContainerColor = SurfaceCard2,
    unfocusedContainerColor = SurfaceCard2,
    focusedIndicatorColor = Green,
    unfocusedIndicatorColor = Divider,
    cursorColor = Green,
    focusedTextColor = TextPrimary,
    unfocusedTextColor = TextPrimary,
    focusedPlaceholderColor = TextDim,
    unfocusedPlaceholderColor = TextDim
)
