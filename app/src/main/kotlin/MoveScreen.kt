package com.healthify.app.ui.move

import android.text.format.DateFormat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.healthify.app.data.db.WorkoutSessionEntity
import com.healthify.app.data.repository.LogRepository
import com.healthify.app.logs.ActivityType
import com.healthify.app.logs.LogSource
import com.healthify.app.logs.WEEKLY_ACTIVE_MINUTES_TARGET
import com.healthify.app.time.DayClock
import com.healthify.app.ui.logs.ActivityLogDialog
import com.healthify.app.ui.theme.*
import com.healthify.app.ui.workout.routineMeta
import com.healthify.app.workout.Routine
import com.healthify.app.workout.WorkoutMath
import com.healthify.app.workout.WorkoutRepository
import com.healthify.app.Entitlements
import com.healthify.app.Feature
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Date
import java.util.Locale

private val StepsTint = Color(0xFF5BF5C4)

/**
 * Steps against the goal, workouts (routines, the exercise library and a
 * workout in progress), this week's active minutes and today's
 * activities. [stepsToday] comes from Home's view model (Health Connect
 * when connected, otherwise the check-in), so both tabs show one number.
 */
@Composable
fun MoveScreen(
    logRepo: LogRepository,
    workoutRepo: WorkoutRepository,
    stepsToday: Int,
    stepGoal: Int,
    healthConnected: Boolean,
    showCalories: Boolean,
    onBack: () -> Unit,
    onOpenRoutine: (String) -> Unit,
    onNewRoutine: () -> Unit,
    onOpenLibrary: () -> Unit,
    onOpenWorkout: (Long) -> Unit,
    onOpenSummary: (Long) -> Unit,
) {
    val today by remember { DayClock.todayIsoFlow() }.collectAsState(DayClock.todayIso())
    val monday = remember(today) { LocalDate.parse(today).with(DayOfWeek.MONDAY) }
    val workouts by remember(today) { logRepo.workoutsFlow(today) }.collectAsState(initial = null)
    val week by remember(monday) {
        logRepo.minutesByDayFlow(DayClock.iso(monday), DayClock.iso(monday.plusDays(6)))
    }.collectAsState(initial = emptyList())
    val routines by remember { workoutRepo.routinesFlow() }.collectAsState(initial = emptyList())
    val active by remember { workoutRepo.activeSessionFlow() }.collectAsState(initial = null)
    val scope = rememberCoroutineScope()
    var adding by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<WorkoutSessionEntity?>(null) }

    Scaffold(
        topBar = { TabHeader("Move", kicker = "Today's activity", onBack = onBack) },
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0)
    ) { pad ->
        val list = workouts ?: return@Scaffold
        val minutesByDate = week.associate { it.date to it.minutes }
        Column(
            Modifier
                .fillMaxSize()
                .padding(pad)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            active?.let { ActiveWorkoutCard(it, onResume = { onOpenWorkout(it.id) }) }
            StepsCard(stepsToday, stepGoal.coerceAtLeast(1), healthConnected, Modifier.staggeredEnter(0))
            WorkoutsCard(
                routines      = routines,
                onOpen        = onOpenRoutine,
                onNew         = onNewRoutine,
                onEmpty       = { scope.launch { onOpenWorkout(workoutRepo.start(null)) } },
                onLibrary     = onOpenLibrary,
                modifier      = Modifier.staggeredEnter(1)
            )
            WeekCard(monday, LocalDate.parse(today), minutesByDate, Modifier.staggeredEnter(2))
            TodayCard(
                workouts     = list.filter { it.endedAt != null },
                showCalories = showCalories,
                onAdd        = { adding = true },
                onEdit       = { w -> if (w.source == LogSource.WORKOUT) onOpenSummary(w.id) else editing = w },
                modifier     = Modifier.staggeredEnter(3)
            )
            Spacer(Modifier.height(LocalBottomBarClearance.current + 8.dp))
        }
    }

    if (adding) {
        ActivityLogDialog(
            existing  = null,
            onSave    = { type, minutes ->
                scope.launch { logRepo.logActivity(type, minutes, today) }
                adding = false
            },
            onDelete  = null,
            onDismiss = { adding = false }
        )
    }
    editing?.let { w ->
        ActivityLogDialog(
            existing  = w,
            onSave    = { type, minutes ->
                scope.launch { logRepo.updateActivity(w, type, minutes) }
                editing = null
            },
            onDelete  = {
                scope.launch { logRepo.deleteWorkout(w) }
                editing = null
            },
            onDismiss = { editing = null }
        )
    }
}

@Composable
private fun StepsCard(steps: Int, goal: Int, healthConnected: Boolean, modifier: Modifier = Modifier) {
    val progress by animateFloatAsState((steps.toFloat() / goal).coerceIn(0f, 1f), tween(900), label = "steps")
    GlassCard(modifier.fillMaxWidth(), tint = StepsTint) {
        Column(Modifier.padding(18.dp)) {
            Text("STEPS TODAY", style = MaterialTheme.typography.labelSmall, color = TextMuted)
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text("%,d".format(steps), style = MaterialTheme.typography.displaySmall.copy(fontFeatureSettings = TABULAR),
                    color = TextPrimary)
                Text("  / %,d".format(goal), style = MaterialTheme.typography.titleMedium, color = TextMuted,
                    modifier = Modifier.padding(bottom = 6.dp))
            }
            Spacer(Modifier.height(12.dp))
            Box(
                Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp))
                    .background(Color.White.copy(alpha = 0.10f))
            ) {
                Box(
                    Modifier.fillMaxHeight().fillMaxWidth(progress).clip(RoundedCornerShape(4.dp))
                        .background(Brush.horizontalGradient(listOf(Color(0xFF0DB888), StepsTint)))
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                when {
                    steps >= goal -> "Goal reached — every extra step is a bonus 🏆"
                    steps == 0 && !healthConnected -> "Connect Health Connect on Home to count steps automatically."
                    steps == 0    -> "No steps yet today. A short walk gets things going."
                    else          -> "%,d to go. A 10-minute walk is about 1,000 steps.".format(goal - steps)
                },
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted
            )
        }
    }
}

@Composable
private fun WeekCard(
    monday: LocalDate,
    today: LocalDate,
    minutesByDate: Map<String, Int>,
    modifier: Modifier = Modifier
) {
    val total = minutesByDate.values.sum()
    val days = (0..6).map { monday.plusDays(it.toLong()) }
    // Bars scale to the busiest day, but never below a daily share of the
    // weekly target so one 10-minute walk doesn't fill its bar.
    val scale = maxOf(minutesByDate.values.maxOrNull() ?: 0, WEEKLY_ACTIVE_MINUTES_TARGET / 5)
    GlassCard(modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp)) {
            Text("THIS WEEK", style = MaterialTheme.typography.labelSmall, color = TextMuted)
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text("$total", style = MaterialTheme.typography.headlineLarge.copy(fontFeatureSettings = TABULAR),
                    color = Green)
                Text(" / $WEEKLY_ACTIVE_MINUTES_TARGET active minutes", style = MaterialTheme.typography.bodyMedium,
                    color = TextMuted, modifier = Modifier.padding(bottom = 4.dp))
            }
            Spacer(Modifier.height(14.dp))
            Row(
                Modifier.fillMaxWidth().height(96.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                days.forEach { d ->
                    val minutes = minutesByDate[DayClock.iso(d)] ?: 0
                    val isToday = d == today
                    val label = d.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.US)
                    Column(
                        Modifier.weight(1f).fillMaxHeight()
                            .semantics { contentDescription = "${d.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.US)}: $minutes minutes" },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Bottom
                    ) {
                        val frac = (minutes.toFloat() / scale).coerceIn(0f, 1f)
                        Box(
                            Modifier
                                .weight(1f, fill = false)
                                .fillMaxWidth()
                                .fillMaxHeight(frac.coerceAtLeast(0.04f))
                                .clip(RoundedCornerShape(6.dp))
                                .background(
                                    if (minutes > 0) Brush.verticalGradient(listOf(StepsTint, Green))
                                    else Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.08f), Color.White.copy(alpha = 0.08f)))
                                )
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(label, style = MaterialTheme.typography.labelSmall,
                            color = if (isToday) Green else TextMuted)
                    }
                }
            }
        }
    }
}

@Composable
private fun TodayCard(
    workouts: List<WorkoutSessionEntity>,
    showCalories: Boolean,
    onAdd: () -> Unit,
    onEdit: (WorkoutSessionEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val timeFormat = remember { DateFormat.getTimeFormat(context) }
    GlassCard(modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp)) {
        Column(Modifier.padding(18.dp)) {
            Text("TODAY", style = MaterialTheme.typography.labelSmall, color = TextMuted)
            if (workouts.isEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text("No activity logged yet. Walks, yoga, the gym — it all counts.",
                    style = MaterialTheme.typography.bodyMedium, color = TextMuted)
            }
            workouts.forEach { w ->
                val type = ActivityType.of(w.activityType)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(onClickLabel = if (w.source == LogSource.WORKOUT) "Open workout" else "Edit activity") { onEdit(w) }
                        .padding(vertical = 6.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconOrb(Green, size = 40.dp) { Text(type.emoji, fontSize = 18.sp) }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(w.title.ifBlank { type.label }, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                        Text(
                            listOfNotNull(
                                "${w.durationMin} min",
                                timeFormat.format(Date(w.startedAt)),
                                w.kcalEstimate?.takeIf { showCalories }?.let { "≈ ${it.toInt()} kcal" }
                            ).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMuted
                        )
                    }
                    Icon(Icons.Rounded.ChevronRight, null, tint = TextDim, modifier = Modifier.size(20.dp))
                }
            }
            Spacer(Modifier.height(16.dp))
            GlowButton("Log activity", onAdd, height = 52.dp)
        }
    }
}

/** A workout started but not finished: resume it from here. */
@Composable
private fun ActiveWorkoutCard(session: WorkoutSessionEntity, onResume: () -> Unit) {
    val minutes = ((System.currentTimeMillis() - session.startedAt) / 60_000).toInt()
    GlassCard(Modifier.fillMaxWidth(), tint = Gold, glow = Gold, onClick = onResume) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            IconOrb(Gold, size = 48.dp) { Text("⏱️", fontSize = 22.sp) }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("WORKOUT IN PROGRESS", style = MaterialTheme.typography.labelSmall, color = Gold)
                Text(session.title, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                Text(
                    if (minutes < 1) "Started just now" else "Started ${WorkoutMath.shortDuration(minutes * 60)} ago",
                    style = MaterialTheme.typography.bodySmall, color = TextMuted
                )
            }
            Text("Resume", style = MaterialTheme.typography.titleSmall, color = Gold)
        }
    }
}

/** Routines to start (yours first), a blank workout, and the exercise library. */
@Composable
private fun WorkoutsCard(
    routines: List<Routine>,
    onOpen: (String) -> Unit,
    onNew: () -> Unit,
    onEmpty: () -> Unit,
    onLibrary: () -> Unit,
    modifier: Modifier = Modifier
) {
    GlassCard(modifier.fillMaxWidth()) {
        Column(Modifier.padding(vertical = 18.dp)) {
            Text("WORKOUTS", style = MaterialTheme.typography.labelSmall, color = TextMuted, modifier = Modifier.padding(horizontal = 18.dp))
            Spacer(Modifier.height(10.dp))
            LazyRow(
                contentPadding = PaddingValues(horizontal = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(routines, key = { it.ref }) { r -> RoutineTile(r) { onOpen(r.ref) } }
                if (Entitlements.has(Feature.CUSTOM_ROUTINES)) item { ActionTile("➕", "New routine", onNew) }
                item { ActionTile("📝", "Empty workout", onEmpty) }
            }
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(onClickLabel = "Open the exercise library", onClick = onLibrary)
                    .padding(horizontal = 18.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("📚", fontSize = 18.sp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Exercise library", style = MaterialTheme.typography.bodyLarge, color = TextPrimary)
                    Text("Over 850 exercises, how-tos and your records", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                }
                Icon(Icons.Rounded.ChevronRight, null, tint = TextDim, modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun RoutineTile(r: Routine, onClick: () -> Unit) {
    GlassCard(Modifier.width(150.dp).height(132.dp), shape = RoundedCornerShape(20.dp), tint = if (r.custom) Gold else Green, onClick = onClick) {
        Column(Modifier.padding(14.dp).fillMaxHeight()) {
            Text(r.emoji, fontSize = 24.sp)
            Spacer(Modifier.height(8.dp))
            Text(r.name, style = MaterialTheme.typography.titleSmall, color = TextPrimary, maxLines = 2,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            Spacer(Modifier.weight(1f))
            Text(if (r.custom) "Yours · ~${r.estimatedMinutes} min" else "~${r.estimatedMinutes} min",
                style = MaterialTheme.typography.labelMedium, color = if (r.custom) Gold else TextMuted, maxLines = 1)
        }
    }
}

@Composable
private fun ActionTile(emoji: String, label: String, onClick: () -> Unit) {
    GlassCard(Modifier.width(120.dp).height(132.dp), shape = RoundedCornerShape(20.dp), onClick = onClick) {
        Column(Modifier.padding(14.dp).fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Text(emoji, fontSize = 24.sp)
            Spacer(Modifier.height(8.dp))
            Text(label, style = MaterialTheme.typography.titleSmall, color = TextMuted,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
    }
}
