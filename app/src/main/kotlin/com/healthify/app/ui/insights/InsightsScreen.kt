package com.healthify.app.ui.insights

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.healthify.app.data.db.CheckInEntity
import com.healthify.app.data.db.UserEntity
import com.healthify.app.data.repository.AppRepository
import com.healthify.app.ui.theme.*
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InsightsScreen(repo: AppRepository, onBack: () -> Unit) {
    // Observe all check-ins as a Flow so the screen refreshes the moment a
    // new check-in is saved. The previous implementation used a one-shot
    // LaunchedEffect(Unit) which only runs once per composition — and
    // because Insights lives in a HorizontalPager it stays composed across
    // tab swipes, so a brand-new check-in never appeared until process death.
    val allCheckIns by repo.getAllCheckIns()
        .collectAsState(initial = emptyList())
    val user by repo.getUser()
        .collectAsState(initial = null)

    // "Past 7 entries" — most recent 7 rows regardless of how spread out.
    val checkIns = remember(allCheckIns) { allCheckIns.take(7) }

    // Current Mon..Sun window for the weekly mood strip.
    val weekCheckIns = remember(allCheckIns) {
        val today  = LocalDate.now()
        val monday = today.with(DayOfWeek.MONDAY)
        val sunday = monday.plusDays(6)
        val isoFmt = DateTimeFormatter.ISO_LOCAL_DATE
        val mondayStr = monday.format(isoFmt)
        val sundayStr = sunday.format(isoFmt)
        allCheckIns.filter { it.date in mondayStr..sundayStr }
    }

    // Derived counters / averages — recompute when the underlying list changes.
    val totalCheckIns = allCheckIns.size
    val avgScore = remember(checkIns) {
        if (checkIns.isEmpty()) 0f
        else checkIns.map { it.wellnessScore }.average().toFloat()
    }

    val avgSteps = checkIns.map { it.steps }.average().let { if (it.isNaN()) 0 else it.toInt() }
    val avgWater = checkIns.map { it.waterGlasses }.average().let { if (it.isNaN()) 0.0 else it }
    val avgSleep = checkIns.map { it.sleepHours.toDouble() }.average().let { if (it.isNaN()) 0.0 else it }
    val bestDay  = checkIns.maxByOrNull { it.wellnessScore }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Insights", style = MaterialTheme.typography.headlineMedium) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, null, tint = TextMuted)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BgDark)
            )
        },
        containerColor = BgDark
    ) { pad ->
        if (checkIns.isEmpty()) {
            EmptyInsights(pad)
            return@Scaffold
        }

        Column(
            Modifier
                .fillMaxSize()
                .padding(pad)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Past 7 days at-a-glance ────────────────────────────────────
            Text("Past 7 Days", style = MaterialTheme.typography.labelSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatCard(Modifier.weight(1f), "⭐", "Avg Score", "%.0f".format(avgScore))
                StatCard(Modifier.weight(1f), "🚶", "Avg Steps", "%,d".format(avgSteps))
                StatCard(Modifier.weight(1f), "💧", "Avg Water", "%.1f gl".format(avgWater))
                StatCard(Modifier.weight(1f), "🌙", "Avg Sleep", "%.1fh".format(avgSleep))
            }

            // Streak ──────────────────────────────────────────────────────
            user?.let { u ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                    shape  = RoundedCornerShape(20.dp)
                ) {
                    Row(
                        Modifier.padding(20.dp).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("🔥", fontSize = 36.sp)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                "${u.currentStreak} day streak",
                                style = MaterialTheme.typography.titleLarge,
                                color = Green
                            )
                            Text(
                                "Longest: ${u.longestStreak} days  •  $totalCheckIns total check-ins",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }

            // Goal progress ───────────────────────────────────────────────
            user?.let { u ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                    shape  = RoundedCornerShape(20.dp)
                ) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text("GOAL PROGRESS (7-day avg)", style = MaterialTheme.typography.labelSmall)
                        GoalRow(
                            emoji   = "🚶",
                            label   = "Steps",
                            current = avgSteps.toDouble(),
                            goal    = u.stepGoal.toDouble(),
                            display = "%,d / %,d".format(avgSteps, u.stepGoal),
                            color   = Sky
                        )
                        GoalRow(
                            emoji   = "💧",
                            label   = "Water",
                            current = avgWater,
                            goal    = u.waterGoalGlasses.toDouble(),
                            display = "%.1f / %d gl".format(avgWater, u.waterGoalGlasses),
                            color   = Green
                        )
                        GoalRow(
                            emoji   = "🌙",
                            label   = "Sleep",
                            current = avgSleep,
                            goal    = u.sleepGoalHours.toDouble(),
                            display = "%.1f / %.1fh".format(avgSleep, u.sleepGoalHours),
                            color   = Lavender
                        )
                    }
                }
            }

            // Mood mix ────────────────────────────────────────────────────
            Card(
                colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                shape  = RoundedCornerShape(20.dp)
            ) {
                Column(Modifier.padding(20.dp)) {
                    Text("MOOD OVER THE WEEK", style = MaterialTheme.typography.labelSmall)
                    Spacer(Modifier.height(12.dp))
                    WeekMoodStrip(weekCheckIns)
                }
            }

            // Best day highlight ──────────────────────────────────────────
            bestDay?.let { best ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                    shape  = RoundedCornerShape(20.dp)
                ) {
                    Row(
                        Modifier
                            .padding(20.dp)
                            .fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(Brush.linearGradient(listOf(Green, Sky))),
                            contentAlignment = Alignment.Center
                        ) { Text("🏆", fontSize = 28.sp) }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text("BEST DAY THIS WEEK", style = MaterialTheme.typography.labelSmall)
                            Text(best.date, style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Wellness score ${best.wellnessScore} · ${moodEmoji(best.moodScore)}",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }

            // Recent check-ins ────────────────────────────────────────────
            Text("Recent Check-ins", style = MaterialTheme.typography.labelSmall)
            checkIns.take(7).forEach { ci ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                    shape  = RoundedCornerShape(14.dp)
                ) {
                    Row(
                        Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(ci.date, style = MaterialTheme.typography.bodySmall, color = TextMuted)
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text("💧 ${ci.waterGlasses}gl", style = MaterialTheme.typography.bodySmall)
                                Text("🌙 %.1fh".format(ci.sleepHours), style = MaterialTheme.typography.bodySmall)
                                if (ci.steps > 0) Text("🚶 %,d".format(ci.steps), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        Text(
                            ci.wellnessScore.toString(),
                            style = MaterialTheme.typography.titleLarge,
                            color = Green
                        )
                    }
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun StatCard(modifier: Modifier, icon: String, label: String, value: String) {
    Card(
        modifier = modifier,
        colors   = CardDefaults.cardColors(containerColor = SurfaceCard),
        shape    = RoundedCornerShape(16.dp)
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(icon, fontSize = 20.sp)
            Text(value, style = MaterialTheme.typography.titleLarge, color = Green)
            Text(label, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun GoalRow(
    emoji: String,
    label: String,
    current: Double,
    goal: Double,
    display: String,
    color: Color
) {
    val pct = if (goal > 0) (current / goal).coerceIn(0.0, 1.0).toFloat() else 0f
    Column {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(emoji, fontSize = 16.sp)
                Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            }
            Text(display, style = MaterialTheme.typography.bodySmall, color = TextMuted)
        }
        Spacer(Modifier.height(6.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(SurfaceCard2)
        ) {
            if (pct > 0f) {
                Box(
                    Modifier
                        .fillMaxWidth(pct)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(4.dp))
                        .background(color)
                )
            }
        }
    }
}

@Composable
private fun EmptyInsights(pad: PaddingValues) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(pad)
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("📊", fontSize = 56.sp)
        Spacer(Modifier.height(12.dp))
        Text("No check-ins yet", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(6.dp))
        Text(
            "Log a daily check-in to start seeing insights — averages, streak, mood and goal progress will appear here.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextMuted
        )
    }
}

private val MOOD_EMOJIS = listOf("😔", "😟", "😐", "🙂", "😄")
private fun moodEmoji(score: Int): String =
    if (score in MOOD_EMOJIS.indices) MOOD_EMOJIS[score] else "—"

/**
 * Mon..Sun strip. Each cell shows the mood emoji for that day's check-in,
 * or a muted placeholder when no check-in was logged that day. Today is
 * highlighted so the user can orient themselves on the strip.
 */
@Composable
private fun WeekMoodStrip(weekCheckIns: List<CheckInEntity>) {
    val today  = LocalDate.now()
    val monday = today.with(DayOfWeek.MONDAY)
    val isoFmt = DateTimeFormatter.ISO_LOCAL_DATE
    val byDate = weekCheckIns.associateBy { it.date }

    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        for (i in 0..6) {
            val date     = monday.plusDays(i.toLong())
            val ci       = byDate[date.format(isoFmt)]
            val isToday  = date == today
            val isFuture = date.isAfter(today)
            val labelColor = when {
                isToday  -> Green
                isFuture -> TextDim
                else     -> TextMuted
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (ci != null) {
                    Text(moodEmoji(ci.moodScore), fontSize = 22.sp)
                } else {
                    // Blank slot: muted dot keeps visual rhythm without
                    // implying a recorded mood.
                    Text(
                        "·",
                        fontSize = 22.sp,
                        color = TextDim
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    dayLetter(date.dayOfWeek),
                    style = MaterialTheme.typography.bodySmall,
                    color = labelColor,
                    fontSize = 11.sp,
                    fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal
                )
            }
        }
    }
}

private fun dayLetter(d: DayOfWeek): String = when (d) {
    DayOfWeek.MONDAY    -> "M"
    DayOfWeek.TUESDAY   -> "T"
    DayOfWeek.WEDNESDAY -> "W"
    DayOfWeek.THURSDAY  -> "T"
    DayOfWeek.FRIDAY    -> "F"
    DayOfWeek.SATURDAY  -> "S"
    DayOfWeek.SUNDAY    -> "S"
}
