package com.healthify.app.ui.notifications

import android.content.Context
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.healthify.app.HealthifyApp
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

    // The Daily Check-in default reminder is locked in the UI — the user
    // can toggle it on/off but cannot rename, retime, or delete it. This
    // keeps the row id stable so the suppression rule wired up in
    // ReminderReceiver (only the seeded check-in nudge is silenced when
    // the user has already checked in) never detaches from the row it's
    // meant to apply to. -1 sentinel = lookup hasn't run yet (e.g.
    // first-launch race) → no row is treated as protected this session,
    // which is the safe default.
    val checkInReminderId = remember {
        context.getSharedPreferences(HealthifyApp.PREFS_FILE, Context.MODE_PRIVATE)
            .getInt(HealthifyApp.KEY_CHECKIN_REMINDER_ID, -1)
    }

    var showEditor by remember { mutableStateOf(false) }
    var editing    by remember { mutableStateOf<ReminderEntity?>(null) }

    Scaffold(
        topBar = {
            TabHeader("Reminders", kicker = "Stay on track", onBack = onBack) {
                GlassIconButton(Icons.Default.Add, "Add reminder", {
                    editing = null
                    showEditor = true
                }, tint = Green)
            }
        },
        containerColor = Color.Transparent,
        // The floating nav + LocalBottomBarClearance own the bottom inset.
        contentWindowInsets = WindowInsets(0)
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
                    GlassCard(Modifier.weight(1f), shape = RoundedCornerShape(20.dp), tint = color) {
                        Column(
                            Modifier.padding(14.dp).fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(icon, fontSize = 22.sp)
                            Text(count.toString(),
                                style = MaterialTheme.typography.headlineSmall, color = color)
                            Text(cat.replaceFirstChar { it.uppercase() },
                                style = MaterialTheme.typography.bodySmall, color = TextMuted,
                                maxLines = 1, softWrap = false,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        }
                    }
                }
            }

            Spacer(Modifier.height(4.dp))

            // ── Reminders list / empty state ──────────────────────────────
            if (reminders.isEmpty()) {
                GlassCard(Modifier.fillMaxWidth()) {
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
                        GlowButton("+ Add reminder", onClick = {
                            editing = null
                            showEditor = true
                        })
                    }
                }
            } else {
                Text("YOUR REMINDERS",
                    style = MaterialTheme.typography.labelSmall, color = TextMuted,
                    modifier = Modifier.padding(start = 4.dp, top = 4.dp))

                reminders.forEach { reminder ->
                    val isProtected = reminder.id == checkInReminderId
                    ReminderCard(
                        reminder    = reminder,
                        isProtected = isProtected,
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
                            // Locked rows don't open the editor — the
                            // launcher is wired to a no-op above when
                            // isProtected is true, so this lambda is
                            // unreachable for them. Kept here only so
                            // the parameter shape stays uniform.
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
            Spacer(Modifier.height(LocalBottomBarClearance.current))
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
    isProtected: Boolean,
    onToggle: (Boolean) -> Unit,
    onEdit:   () -> Unit,
    onDelete: () -> Unit
) {
    val catColor = categoryColor(reminder.category)

    var showConfirmDelete by remember { mutableStateOf(false) }

    // Protected (seeded Daily Check-in) rows: the card opens nothing on
    // tap and the trash button is replaced by a "DEFAULT" chip. The
    // Switch stays interactive so the user can still mute the nudge
    // without losing the suppression-rule anchor. See HealthifyApp and
    // ReminderReceiver for why the row id has to stay stable.
    val daysLabel = daysSummary(reminder.repeatDays.split(",").mapNotNull { it.trim().toIntOrNull() })

    GlassCard(
        modifier = Modifier.fillMaxWidth().then(
            if (!reminder.enabled) Modifier.alpha(0.55f) else Modifier
        ),
        shape   = RoundedCornerShape(20.dp),
        onClick = if (isProtected) null else onEdit
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            IconOrb(catColor) { Text(reminder.emoji, fontSize = 20.sp) }

            Column(Modifier.weight(1f)) {
                Text(
                    reminder.label,
                    style = MaterialTheme.typography.titleSmall,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        "%02d:%02d".format(reminder.hourOfDay, reminder.minute),
                        style = MaterialTheme.typography.titleLarge.copy(fontFeatureSettings = TABULAR),
                        color = catColor
                    )
                    Text(
                        "  ·  $daysLabel",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        modifier = Modifier.padding(bottom = 3.dp)
                    )
                }
            }

            if (isProtected) {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(100.dp))
                        .background(GreenDim)
                        .border(1.dp, Green.copy(alpha = 0.35f), RoundedCornerShape(100.dp))
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text(
                        "DEFAULT",
                        style = MaterialTheme.typography.labelSmall,
                        color = Green
                    )
                }
            } else {
                Box(
                    Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(Coral.copy(alpha = 0.12f))
                        .border(1.dp, Coral.copy(alpha = 0.3f), CircleShape)
                        .clickable(onClickLabel = "Delete reminder") { showConfirmDelete = true },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Delete, "Delete reminder", tint = Coral,
                        modifier = Modifier.size(18.dp))
                }
            }

            Switch(
                checked = reminder.enabled,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(
                    checkedThumbColor      = Color.White,
                    checkedTrackColor      = Green,
                    checkedBorderColor     = Color.Transparent,
                    uncheckedThumbColor    = TextMuted,
                    uncheckedTrackColor    = Color.White.copy(alpha = 0.06f),
                    uncheckedBorderColor   = Color.White.copy(alpha = 0.18f)
                )
            )
        }
    }

    if (showConfirmDelete) {
        ConfirmDialog(
            emoji = reminder.emoji,
            title = "Delete reminder?",
            message = "\"${reminder.label}\" will be removed and won't notify you anymore.",
            confirmLabel = "Delete",
            onConfirm = {
                showConfirmDelete = false
                onDelete()
            },
            onDismiss = { showConfirmDelete = false }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// EDITOR DIALOG: name, icon picker, time, save/delete
// ─────────────────────────────────────────────────────────────────────────────

private fun categoryColor(id: String): Color = when (id) {
    "water"    -> Sky
    "meds"     -> Coral
    "movement" -> Green
    "wellness" -> Lavender
    else       -> Gold
}

/** "Every day" / "Weekdays" / "Weekends", else the day initials (1 = Mon). */
private fun daysSummary(days: Collection<Int>): String {
    val set = days.filter { it in 1..7 }.toSet()
    return when (set) {
        (1..7).toSet()       -> "Every day"
        setOf(1, 2, 3, 4, 5) -> "Weekdays"
        setOf(6, 7)          -> "Weekends"
        emptySet<Int>()      -> "No days"
        else -> set.sorted().joinToString(" ") { "MTWTFSS"[it - 1].toString() }
    }
}

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
    var confirmDelete by remember { mutableStateOf(false) }
    // The whole sheet takes the category's colour: corner light, field
    // focus, wheel band, day dots and the Save button.
    val accent by animateColorAsState(categoryColor(category), tween(300), label = "catAccent")

    GlassDialog(onDismiss = onDismiss, accent = accent) {
        // Header — a live preview of the reminder being built.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    if (isEdit) "EDIT REMINDER" else "NEW REMINDER",
                    style = MaterialTheme.typography.labelSmall,
                    color = accent
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    label.ifBlank { "Reminder" },
                    style = MaterialTheme.typography.headlineSmall,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "%02d:%02d  ·  %s".format(hour, minute, daysSummary(days)),
                    style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = TABULAR),
                    color = TextMuted
                )
            }
            Spacer(Modifier.width(12.dp))
            Box(Modifier.size(64.dp).radialGlow(accent, alpha = 0.35f, scale = 1.2f), contentAlignment = Alignment.Center) {
                IconOrb(accent, size = 56.dp) {
                    AnimatedContent(
                        targetState = emoji,
                        transitionSpec = {
                            (scaleIn(spring(dampingRatio = 0.45f, stiffness = 420f), initialScale = 0.5f) + fadeIn()) togetherWith
                                (scaleOut(tween(120)) + fadeOut(tween(120)))
                        },
                        label = "reminderEmoji"
                    ) { e -> Text(e, fontSize = 26.sp) }
                }
            }
        }

        Spacer(Modifier.height(22.dp))
        FieldLabel("Name")
        OutlinedTextField(
            value = label,
            onValueChange = { if (it.length <= 40) label = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text("e.g. Take vitamin D") },
            shape = RoundedCornerShape(16.dp),
            colors = glassFieldColors(accent)
        )

        Spacer(Modifier.height(18.dp))
        FieldLabel("Category")
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

        Spacer(Modifier.height(18.dp))
        FieldLabel("Icon")
        IconGrid(category = category, selected = emoji, accent = accent) { choice -> emoji = choice.emoji }

        Spacer(Modifier.height(18.dp))
        FieldLabel("Time")
        TimeStepper(
            hour = hour,
            minute = minute,
            accent = accent,
            onHourChange = { hour = it },
            onMinuteChange = { minute = it }
        )

        Spacer(Modifier.height(18.dp))
        FieldLabel("Repeats")
        DayChips(selected = days, accent = accent) { d -> if (d in days) days.remove(d) else days.add(d) }
        if (days.isEmpty()) {
            Text(
                "Pick at least one day",
                style = MaterialTheme.typography.bodySmall,
                color = Coral,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        Spacer(Modifier.height(24.dp))
        // Delete (only when editing) — full-width, with confirm
        if (isEdit && onDelete != null) {
            GhostButton("Delete reminder", { confirmDelete = true }, color = Coral)
            Spacer(Modifier.height(10.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GhostButton("Cancel", onDismiss, Modifier.weight(1f))
            GlowButton(
                text = if (isEdit) "Save" else "Add",
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
                modifier = Modifier.weight(1f),
                accent = accent,
                enabled = days.isNotEmpty(),
                height = 52.dp
            )
        }
    }

    if (confirmDelete && onDelete != null) {
        ConfirmDialog(
            emoji = "🗑️",
            title = "Delete reminder?",
            message = "\"${label.ifBlank { initial?.label ?: "Reminder" }}\" will be removed and won't notify you anymore.",
            confirmLabel = "Delete",
            onConfirm = {
                confirmDelete = false
                onDelete()
            },
            onDismiss = { confirmDelete = false }
        )
    }
}

// ── Category chips (explicit type picker) ────────────────────────────────────
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategoryChips(selected: String, onSelect: (String) -> Unit) {
    val haptics = rememberHaptics()
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        CATEGORIES.forEach { cat ->
            GlassChip(
                text = cat.label,
                selected = cat.id == selected,
                onClick = { haptics.tick(); onSelect(cat.id) },
                color = categoryColor(cat.id),
                leading = cat.emoji
            )
        }
    }
}

// ── Day-of-week dots (Mon..Sun = 1..7, ISO) ──────────────────────────────────
@Composable
private fun DayChips(selected: List<Int>, accent: Color, onToggle: (Int) -> Unit) {
    val haptics = rememberHaptics()
    val labels = listOf("M", "T", "W", "T", "F", "S", "S")
    val ink = Color(0xFF06121C)
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
        labels.forEachIndexed { idx, lbl ->
            val day = idx + 1
            val sel = day in selected
            val scale by animateFloatAsState(if (sel) 1f else 0.92f, spring(dampingRatio = 0.5f, stiffness = 500f), label = "day$day")
            Box(
                Modifier
                    .weight(1f)
                    .aspectRatio(1f)
                    .graphicsLayer { scaleX = scale; scaleY = scale }
                    .clip(CircleShape)
                    .background(
                        if (sel) Brush.linearGradient(listOf(accent, lerp(accent, Color.White, 0.3f)))
                        else Brush.verticalGradient(listOf(GlassFillTop, GlassFillBottom))
                    )
                    .border(1.dp, if (sel) Color.White.copy(alpha = 0.18f) else GlassBorderTop, CircleShape)
                    .clickable(role = androidx.compose.ui.semantics.Role.Checkbox) { haptics.tick(); onToggle(day) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    lbl,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (sel) ink else TextMuted
                )
            }
        }
    }
}

// ── Icon grid (filtered by category) ─────────────────────────────────────────
@Composable
private fun IconGrid(category: String, selected: String, accent: Color, onSelect: (IconChoice) -> Unit) {
    val haptics = rememberHaptics()
    val filtered = ICON_CATALOG.filter { it.category == category }
    val rows = filtered.chunked(6)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { choice ->
                    val sel = choice.emoji == selected
                    val scale by animateFloatAsState(if (sel) 1.15f else 1f, spring(dampingRatio = 0.4f, stiffness = 500f), label = "icon${choice.emoji}")
                    Box(
                        Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                if (sel) Brush.linearGradient(listOf(accent.copy(alpha = 0.30f), accent.copy(alpha = 0.10f)))
                                else Brush.verticalGradient(listOf(GlassFillTop, GlassFillBottom))
                            )
                            .border(1.dp, if (sel) accent else GlassBorderTop, RoundedCornerShape(16.dp))
                            .clickable(onClickLabel = choice.label) { haptics.tick(); onSelect(choice) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(choice.emoji, fontSize = 22.sp, modifier = Modifier.graphicsLayer { scaleX = scale; scaleY = scale })
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
    accent: Color,
    onHourChange: (Int) -> Unit,
    onMinuteChange: (Int) -> Unit
) {
    val shape = RoundedCornerShape(20.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Brush.verticalGradient(listOf(GlassFillTop, GlassFillBottom)))
            .border(1.dp, GlassBorderTop, shape)
            .padding(vertical = 12.dp, horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        WheelPicker(
            label = "HOUR",
            range = 0..23,
            value = hour,
            accent = accent,
            onValueChange = onHourChange,
            modifier = Modifier.weight(1f)
        )
        Text(
            ":",
            fontSize = 32.sp,
            color = accent,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 0.dp).padding(top = 18.dp)
        )
        WheelPicker(
            label = "MIN",
            range = 0..59,
            value = minute,
            accent = accent,
            onValueChange = onMinuteChange,
            modifier = Modifier.weight(1f)
        )
    }
}

/**
 * Vertical scrolling number wheel — looks/feels like Android's alarm-clock
 * time picker. Wraps infinitely so swiping past 23 lands on 0 (hours) or 59
 * lands on 0 (minutes). Snaps to whole rows, ticks as each row passes the
 * centre, and the centred row sits on a tinted band.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WheelPicker(
    label: String,
    range: IntRange,
    value: Int,
    accent: Color,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics          = rememberHaptics()
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
    // A detent tick each time a new row crosses the centre while scrolling.
    LaunchedEffect(listState) {
        var last = centeredAbsIndex
        snapshotFlow { centeredAbsIndex }.collect { idx ->
            if (idx != last && listState.isScrollInProgress) haptics.tick()
            last = idx
        }
    }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = accent)
        Spacer(Modifier.height(4.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(itemHeight * visibleCount),
            contentAlignment = Alignment.Center
        ) {
            // Selection band behind the centred row.
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(itemHeight + 4.dp)
                    .align(Alignment.Center)
                    .clip(RoundedCornerShape(12.dp))
                    .background(accent.copy(alpha = 0.14f))
                    .border(1.dp, accent.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
            )
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
                        1    -> 0.5f
                        else -> 0.2f
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
                            style = MaterialTheme.typography.headlineSmall.copy(fontFeatureSettings = TABULAR),
                            fontSize = if (isCenter) 28.sp else 22.sp,
                            fontWeight = if (isCenter) FontWeight.ExtraBold else FontWeight.Medium,
                            color = TextPrimary.copy(alpha = alpha),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}
