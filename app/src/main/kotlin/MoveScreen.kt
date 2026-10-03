package com.healthify.app.ui.move

import android.text.format.DateFormat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import com.healthify.app.logs.WEEKLY_ACTIVE_MINUTES_TARGET
import com.healthify.app.time.DayClock
import com.healthify.app.ui.logs.ActivityLogDialog
import com.healthify.app.ui.theme.*
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Date
import java.util.Locale

private val StepsTint = Color(0xFF5BF5C4)

/**
 * Steps against the goal, today's logged activities and this week's
 * active minutes. [stepsToday] comes from Home's view model (Health Connect
 * when connected, otherwise the check-in), so both tabs show one number.
 */
@Composable
fun MoveScreen(
    logRepo: LogRepository,
    stepsToday: Int,
    stepGoal: Int,
    healthConnected: Boolean,
    onBack: () -> Unit
) {
    val today by remember { DayClock.todayIsoFlow() }.collectAsState(DayClock.todayIso())
    val monday = remember(today) { LocalDate.parse(today).with(DayOfWeek.MONDAY) }
    val workouts by remember(today) { logRepo.workoutsFlow(today) }.collectAsState(initial = null)
    val week by remember(monday) {
        logRepo.minutesByDayFlow(DayClock.iso(monday), DayClock.iso(monday.plusDays(6)))
    }.collectAsState(initial = emptyList())
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
            StepsCard(stepsToday, stepGoal.coerceAtLeast(1), healthConnected, Modifier.staggeredEnter(0))
            WeekCard(monday, LocalDate.parse(today), minutesByDate, Modifier.staggeredEnter(1))
            TodayCard(
                workouts = list.filter { it.endedAt != null },
                onAdd    = { adding = true },
                onEdit   = { editing = it },
                modifier = Modifier.staggeredEnter(2)
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
                        .clickable(onClickLabel = "Edit activity") { onEdit(w) }
                        .padding(vertical = 6.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconOrb(Green, size = 40.dp) { Text(type.emoji, fontSize = 18.sp) }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(w.title.ifBlank { type.label }, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                        Text(
                            "${w.durationMin} min · ${timeFormat.format(Date(w.startedAt))}",
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
