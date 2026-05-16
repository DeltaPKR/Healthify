package com.healthify.app.ui.notifications

import android.content.Context
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
                                    val workerId = NotificationScheduler.schedule(context, reminder)
                                    repo.saveReminder(
                                        reminder.copy(enabled = true, workerId = workerId.toString())
                                    )
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
                    // Persist
                    val newId = repo.saveReminder(result).toInt()
                    // Cancel previous if editing, then schedule fresh
                    if (editing != null) {
                        NotificationScheduler.cancel(context, editing!!)
                    }
                    val saved = result.copy(id = if (result.id == 0) newId else result.id)
                    if (saved.enabled) {
                        val workerId = NotificationScheduler.schedule(context, saved)
                        repo.saveReminder(saved.copy(workerId = workerId.toString()))
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

// ── Time stepper (hour + minute with +/- buttons) ────────────────────────────
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
            Modifier.fillMaxWidth().padding(vertical = 14.dp, horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Stepper(
                label = "HOUR",
                value = hour,
                onDec = { onHourChange((hour - 1 + 24) % 24) },
                onInc = { onHourChange((hour + 1) % 24) }
            )
            Text(":", fontSize = 32.sp, color = TextPrimary,
                modifier = Modifier.padding(horizontal = 4.dp))
            Stepper(
                label = "MIN",
                value = minute,
                onDec = { onMinuteChange((minute - 5 + 60) % 60) },
                onInc = { onMinuteChange((minute + 5) % 60) }
            )
        }
    }
    Text(
        "Minute increments of 5",
        style = MaterialTheme.typography.bodySmall,
        color = TextDim,
        modifier = Modifier.padding(start = 6.dp, top = 2.dp)
    )
}

@Composable
private fun Stepper(label: String, value: Int, onDec: () -> Unit, onInc: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier.padding(horizontal = 6.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = Green)
        Row(verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StepBtn(text = "−", onClick = onDec)
            Text(
                "%02d".format(value),
                fontSize = 30.sp,
                color = TextPrimary,
                modifier = Modifier.widthIn(min = 54.dp),
                textAlign = TextAlign.Center
            )
            StepBtn(text = "+", onClick = onInc)
        }
    }
}

@Composable
private fun StepBtn(text: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(38.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(GreenDim)
            .border(1.dp, Green.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(text, fontSize = 22.sp, color = Green)
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
