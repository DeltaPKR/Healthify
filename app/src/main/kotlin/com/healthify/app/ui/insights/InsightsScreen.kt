package com.healthify.app.ui.insights

import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.healthify.app.data.db.CheckInEntity
import com.healthify.app.data.db.UserEntity
import com.healthify.app.data.repository.AppRepository
import com.healthify.app.streak.StreakManager
import com.healthify.app.ui.theme.*
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

private val MOOD_EMOJIS = listOf("😢", "😕", "😐", "🙂", "😄")
private val MOOD_COLORS = listOf(Coral, Amber, Gold, Green, Sky)

@Composable
fun InsightsScreen(repo: AppRepository, onBack: () -> Unit) {
    // Observe all check-ins as a Flow so the screen refreshes the moment a
    // new check-in is saved. The previous implementation used a one-shot
    // LaunchedEffect(Unit) which only runs once per composition — and
    // because Insights lives in a HorizontalPager it stays composed across
    // tab swipes, so a brand-new check-in never appeared until process death.
    // `null` until the first emission, so the empty state never flashes
    // for a user who does have history.
    val allCheckIns by repo.getAllCheckIns().collectAsState(initial = null)
    val user by repo.getUser().collectAsState(initial = null)

    Scaffold(
        topBar = { TabHeader("Insights", kicker = "Your trends", onBack = onBack) },
        containerColor = Color.Transparent,
        // The floating nav + LocalBottomBarClearance own the bottom inset.
        contentWindowInsets = WindowInsets(0)
    ) { pad ->
        val all = allCheckIns ?: return@Scaffold
        if (all.isEmpty()) {
            EmptyInsights(pad)
            return@Scaffold
        }

        // "Past 7 entries" — most recent 7 rows regardless of how spread out.
        val checkIns = remember(all) { all.take(7) }
        val previous = remember(all) { all.drop(7).take(7) }
        val trend    = remember(all) { all.take(14).reversed() }

        // Current Mon..Sun window for the weekly mood strip.
        val weekCheckIns = remember(all) {
            val monday = LocalDate.now().with(DayOfWeek.MONDAY)
            val iso = DateTimeFormatter.ISO_LOCAL_DATE
            val from = monday.format(iso)
            val to   = monday.plusDays(6).format(iso)
            all.filter { it.date in from..to }
        }

        val avgScore = checkIns.map { it.wellnessScore }.average().toFloat()
        val prevAvg  = previous.takeIf { it.isNotEmpty() }?.map { it.wellnessScore }?.average()?.toFloat()
        val avgSteps = checkIns.map { it.steps }.average().let { if (it.isNaN()) 0 else it.toInt() }
        val avgWater = checkIns.map { it.waterGlasses }.average().let { if (it.isNaN()) 0.0 else it }
        val avgSleep = checkIns.map { it.sleepHours.toDouble() }.average().let { if (it.isNaN()) 0.0 else it }
        val bestDay  = checkIns.maxByOrNull { it.wellnessScore }

        Column(
            Modifier
                .fillMaxSize()
                .padding(pad)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            TrendCard(trend, avgScore, prevAvg, Modifier.staggeredEnter(0))

            // Four across normally; 2 × 2 at large font scales.
            val stats = listOf(
                listOf("⭐", "Score", avgScore.roundToInt().toString()) to Green,
                listOf("🚶", "Steps", compact(avgSteps)) to StepsTint,
                listOf("💧", "Water", "%.1f".format(avgWater)) to Sky,
                listOf("🌙", "Sleep", "%.1fh".format(avgSleep)) to Lavender
            )
            val perRow = if (LocalDensity.current.fontScale > 1.3f) 2 else 4
            Column(Modifier.staggeredEnter(1), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                stats.chunked(perRow).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEach { (t, c) -> StatTile(Modifier.weight(1f), t[0], t[1], t[2], c) }
                    }
                }
            }

            user?.let { u -> StreakCard(u, all.size, Modifier.staggeredEnter(2)) }

            user?.let { u -> GoalCard(u, avgSteps, avgWater, avgSleep, Modifier.staggeredEnter(3)) }

            GlassCard(Modifier.fillMaxWidth().staggeredEnter(4)) {
                Column(Modifier.padding(18.dp)) {
                    Text("MOOD THIS WEEK", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                    Spacer(Modifier.height(14.dp))
                    WeekMoodStrip(weekCheckIns)
                }
            }

            bestDay?.let { best -> BestDayCard(best, Modifier.staggeredEnter(5)) }

            Text(
                "RECENT CHECK-INS",
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted,
                modifier = Modifier.padding(start = 4.dp, top = 4.dp)
            )
            checkIns.forEachIndexed { i, ci -> RecentRow(ci, Modifier.staggeredEnter(6 + i)) }

            Spacer(Modifier.height(LocalBottomBarClearance.current + 8.dp))
        }
    }
}

private val StepsTint = Color(0xFF5BF5C4)

private fun compact(n: Int): String =
    if (n >= 10_000) "%.1fk".format(n / 1000f) else "%,d".format(n)

private fun scoreColor(score: Int): Color = when {
    score >= 75 -> Green
    score >= 55 -> Sky
    else        -> Lavender
}

private fun friendlyDate(iso: String, pattern: String = "EEE, MMM d"): String {
    val d = runCatching { LocalDate.parse(iso) }.getOrNull() ?: return iso
    val today = LocalDate.now()
    return when (d) {
        today              -> "Today"
        today.minusDays(1) -> "Yesterday"
        else               -> d.format(DateTimeFormatter.ofPattern(pattern, Locale.US))
    }
}

// ── Wellness trend ───────────────────────────────────────────────────────────
@Composable
private fun TrendCard(points: List<CheckInEntity>, avg: Float, prevAvg: Float?, modifier: Modifier = Modifier) {
    val shown by rememberCountUp(avg.roundToInt(), durationMs = 1100, delayMs = 200)
    GlassCard(modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("WELLNESS TREND", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            shown.toString(),
                            style = MaterialTheme.typography.displayLarge.copy(fontSize = 40.sp),
                            color = TextPrimary
                        )
                        Text(
                            " avg · last ${minOf(7, points.size)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextMuted,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }
                }
                if (prevAvg != null) {
                    val d = (avg - prevAvg).roundToInt()
                    val up = d >= 0
                    val c = if (up) Green else Coral
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(100.dp))
                            .background(c.copy(alpha = 0.16f))
                            .border(1.dp, c.copy(alpha = 0.4f), RoundedCornerShape(100.dp))
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            "${if (up) "▲" else "▼"} ${kotlin.math.abs(d)} vs prior",
                            style = MaterialTheme.typography.labelMedium,
                            color = c
                        )
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            TrendChart(points, Modifier.fillMaxWidth().height(150.dp))
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(friendlyDate(points.first().date, "MMM d"), style = MaterialTheme.typography.bodySmall)
                if (points.size > 1) {
                    Text(friendlyDate(points.last().date, "MMM d"), style = MaterialTheme.typography.bodySmall)
                }
            }
            if (points.size < 3) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Check in daily and this line grows into your trend.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted
                )
            }
        }
    }
}

/**
 * Smoothed area chart of wellness scores, oldest → newest. Draws itself in
 * left-to-right the first time the tab is on screen; the newest point is
 * ringed and labelled.
 */
@Composable
private fun TrendChart(points: List<CheckInEntity>, modifier: Modifier = Modifier) {
    val reveal by rememberSweep(1f, durationMs = 1500, delayMs = 250)
    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelLarge.copy(color = TextPrimary, fontWeight = FontWeight.Bold)
    val scores = points.map { it.wellnessScore }

    Canvas(
        modifier.semantics {
            contentDescription = "Wellness score trend over ${points.size} check-ins, latest ${scores.lastOrNull() ?: 0}"
        }
    ) {
        val w = size.width
        val h = size.height
        val top = 26.dp.toPx()
        val bottom = h - 6.dp.toPx()
        val lo = ((scores.minOrNull() ?: 0) - 15).coerceAtLeast(0).let { it - it % 10 }.toFloat()
        val hi = 100f
        fun y(v: Int) = bottom - ((v - lo) / (hi - lo)).coerceIn(0f, 1f) * (bottom - top)

        val pad = 10.dp.toPx()
        val n = points.size
        val xs = if (n == 1) listOf(w / 2f) else List(n) { i -> pad + i * (w - 2 * pad) / (n - 1) }
        val ys = scores.map { y(it) }

        // Grid
        val dash = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 6.dp.toPx()))
        for (k in 0..3) {
            val gy = top + (bottom - top) * k / 3f
            drawLine(Color.White.copy(alpha = 0.07f), Offset(0f, gy), Offset(w, gy), 1.dp.toPx(), pathEffect = dash)
        }

        val revealX = w * reveal
        if (n >= 2) {
            val line = smoothPath(xs, ys)
            val area = Path().apply {
                addPath(line)
                lineTo(xs.last(), bottom)
                lineTo(xs.first(), bottom)
                close()
            }
            clipRect(right = revealX) {
                drawPath(
                    area,
                    Brush.verticalGradient(listOf(Green.copy(alpha = 0.32f), Green.copy(alpha = 0f)), startY = top, endY = bottom)
                )
                drawPath(
                    line,
                    Brush.horizontalGradient(listOf(Sky, Green)),
                    style = Stroke(3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
            }
        }

        xs.forEachIndexed { i, x ->
            if (x > revealX + 1f) return@forEachIndexed
            val c = Offset(x, ys[i])
            if (i == n - 1) {
                drawCircle(Green.copy(alpha = 0.22f), 12.dp.toPx(), c)
                drawCircle(Green, 6.dp.toPx(), c)
                drawCircle(BgDark, 2.5.dp.toPx(), c)
                val label = measurer.measure(scores[i].toString(), labelStyle)
                val lx = (x - label.size.width / 2f).coerceIn(0f, w - label.size.width)
                drawText(label, topLeft = Offset(lx, (c.y - 14.dp.toPx() - label.size.height).coerceAtLeast(0f)))
            } else {
                drawCircle(Color.White.copy(alpha = 0.55f), 2.5.dp.toPx(), c)
            }
        }
    }
}

/** Catmull-Rom through every point, as cubic Béziers. */
private fun smoothPath(xs: List<Float>, ys: List<Float>): Path = Path().apply {
    moveTo(xs[0], ys[0])
    for (i in 0 until xs.size - 1) {
        val x0 = xs.getOrElse(i - 1) { xs[i] };  val y0 = ys.getOrElse(i - 1) { ys[i] }
        val x1 = xs[i];                          val y1 = ys[i]
        val x2 = xs[i + 1];                      val y2 = ys[i + 1]
        val x3 = xs.getOrElse(i + 2) { x2 };     val y3 = ys.getOrElse(i + 2) { y2 }
        cubicTo(
            x1 + (x2 - x0) / 6f, y1 + (y2 - y0) / 6f,
            x2 - (x3 - x1) / 6f, y2 - (y3 - y1) / 6f,
            x2, y2
        )
    }
}

// ── Tiles & cards ────────────────────────────────────────────────────────────
@Composable
private fun StatTile(modifier: Modifier, icon: String, label: String, value: String, color: Color) {
    GlassCard(modifier, shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 12.dp)) {
            Text(icon, fontSize = 18.sp)
            Spacer(Modifier.height(4.dp))
            Text(
                value,
                style = MaterialTheme.typography.titleLarge.copy(fontFeatureSettings = TABULAR),
                color = color,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )
            Text(label, style = MaterialTheme.typography.labelMedium, color = TextMuted, maxLines = 1)
        }
    }
}

@Composable
private fun StreakCard(u: UserEntity, totalCheckIns: Int, modifier: Modifier = Modifier) {
    val accent = when {
        u.currentStreak == 0 -> Green
        u.currentStreak < 7  -> Amber
        else                 -> Gold
    }
    GlassCard(modifier.fillMaxWidth(), tint = accent) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            FlameBadge(StreakManager.badge(u.currentStreak), accent, lively = u.currentStreak > 0)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        u.currentStreak.toString(),
                        style = MaterialTheme.typography.headlineLarge.copy(fontFeatureSettings = TABULAR),
                        color = accent
                    )
                    Text(
                        " day streak",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
                Text(
                    "Longest ${u.longestStreak} · $totalCheckIns check-ins in total",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted
                )
            }
        }
    }
}

@Composable
private fun GoalCard(u: UserEntity, avgSteps: Int, avgWater: Double, avgSleep: Double, modifier: Modifier = Modifier) {
    GlassCard(modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("GOAL PROGRESS · 7-DAY AVG", style = MaterialTheme.typography.labelSmall, color = TextMuted)
            GoalRow(0, "💧", "Water", avgWater, u.waterGoalGlasses.toDouble(),
                "%.1f / %d gl".format(avgWater, u.waterGoalGlasses), Sky)
            GoalRow(1, "🚶", "Steps", avgSteps.toDouble(), u.stepGoal.toDouble(),
                "%,d / %,d".format(avgSteps, u.stepGoal), StepsTint)
            GoalRow(2, "🌙", "Sleep", avgSleep, u.sleepGoalHours.toDouble(),
                "%.1f / %.1fh".format(avgSleep, u.sleepGoalHours), Lavender)
        }
    }
}

@Composable
private fun GoalRow(
    index: Int,
    emoji: String,
    label: String,
    current: Double,
    goal: Double,
    display: String,
    color: Color
) {
    val pct = if (goal > 0) (current / goal).coerceIn(0.0, 1.0).toFloat() else 0f
    val fill by rememberSweep(pct, durationMs = 1000, delayMs = 300L + index * 120L)
    Column {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(emoji, fontSize = 16.sp)
                Text(label, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                if (pct >= 1f) Text("✓", style = MaterialTheme.typography.titleSmall, color = color)
            }
            Text(display, style = MaterialTheme.typography.bodySmall, color = TextMuted)
        }
        Spacer(Modifier.height(8.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(Color.White.copy(alpha = 0.07f))
        ) {
            if (fill > 0.001f) {
                Box(
                    Modifier
                        .fillMaxWidth(fill)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(5.dp))
                        .background(Brush.horizontalGradient(listOf(color.copy(alpha = 0.55f), color)))
                )
            }
        }
    }
}

@Composable
private fun BestDayCard(best: CheckInEntity, modifier: Modifier = Modifier) {
    GlassCard(modifier.fillMaxWidth(), tint = Gold) {
        Row(Modifier.padding(18.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(56.dp)
                    .radialGlow(Gold, alpha = 0.35f)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Brush.linearGradient(listOf(Gold, Amber))),
                contentAlignment = Alignment.Center
            ) { Text("🏆", fontSize = 28.sp) }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("BEST RECENT DAY", style = MaterialTheme.typography.labelSmall, color = Gold)
                Text(friendlyDate(best.date, "EEEE, MMM d"), style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                Text(
                    "Wellness score ${best.wellnessScore} · ${moodEmoji(best.moodScore)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted
                )
            }
        }
    }
}

@Composable
private fun RecentRow(ci: CheckInEntity, modifier: Modifier = Modifier) {
    val c = scoreColor(ci.wellnessScore)
    GlassCard(modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(moodEmoji(ci.moodScore), fontSize = 22.sp)
            Column(Modifier.weight(1f)) {
                Text(friendlyDate(ci.date), style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("💧 ${ci.waterGlasses}", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                    Text("🌙 %.1fh".format(ci.sleepHours), style = MaterialTheme.typography.bodySmall, color = TextMuted)
                    if (ci.steps > 0) Text("🚶 ${compact(ci.steps)}", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                }
            }
            Box(
                Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(c.copy(alpha = 0.16f))
                    .border(1.dp, c.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    ci.wellnessScore.toString(),
                    style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = TABULAR),
                    color = c
                )
            }
        }
    }
}

@Composable
private fun EmptyInsights(pad: PaddingValues) {
    val breath = rememberBreath(2600, "emptyFloat")
    Box(
        Modifier
            .fillMaxSize()
            .padding(pad)
            .padding(horizontal = 24.dp)
            .padding(bottom = LocalBottomBarClearance.current),
        contentAlignment = Alignment.Center
    ) {
        GlassCard(Modifier.fillMaxWidth()) {
            Column(
                Modifier.padding(28.dp).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    Modifier
                        .size(96.dp)
                        .radialGlow(Green, alpha = 0.3f)
                        .graphicsLayerTranslate { (breath.value - 0.5f) * 8f },
                    contentAlignment = Alignment.Center
                ) { Text("📈", fontSize = 52.sp) }
                Spacer(Modifier.height(12.dp))
                Text("Your trends start here", style = MaterialTheme.typography.titleLarge, color = TextPrimary)
                Spacer(Modifier.height(6.dp))
                Text(
                    "Log a daily check-in and your wellness trend, averages, streak, mood and goal progress fill in here.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextMuted,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

/** Vertical float driven at draw time (dp). */
private fun Modifier.graphicsLayerTranslate(dy: () -> Float): Modifier =
    this.then(Modifier.graphicsLayer { translationY = dy() * density })

private fun moodEmoji(score: Int): String =
    if (score in MOOD_EMOJIS.indices) MOOD_EMOJIS[score] else "—"

/**
 * Mon..Sun strip. Each logged day is a bubble tinted with that mood's
 * colour; empty days are quiet outlines. Today is highlighted so the user
 * can orient themselves on the strip.
 */
@Composable
private fun WeekMoodStrip(weekCheckIns: List<CheckInEntity>) {
    val today  = LocalDate.now()
    val monday = today.with(DayOfWeek.MONDAY)
    val isoFmt = DateTimeFormatter.ISO_LOCAL_DATE
    val byDate = weekCheckIns.associateBy { it.date }

    Row(Modifier.fillMaxWidth()) {
        for (i in 0..6) {
            val date     = monday.plusDays(i.toLong())
            val ci       = byDate[date.format(isoFmt)]
            val isToday  = date == today
            val isFuture = date.isAfter(today)
            val mood     = ci?.moodScore?.takeIf { it in MOOD_COLORS.indices }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(
                            if (mood != null) MOOD_COLORS[mood].copy(alpha = 0.2f)
                            else Color.White.copy(alpha = if (isFuture) 0.02f else 0.04f)
                        )
                        .border(
                            1.dp,
                            when {
                                mood != null -> MOOD_COLORS[mood].copy(alpha = 0.55f)
                                isToday      -> Green.copy(alpha = 0.6f)
                                else         -> Color.White.copy(alpha = if (isFuture) 0.05f else 0.1f)
                            },
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (mood != null) Text(MOOD_EMOJIS[mood], fontSize = 19.sp)
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    date.dayOfWeek.name.take(1),
                    style = MaterialTheme.typography.labelMedium,
                    color = when {
                        isToday  -> Green
                        isFuture -> TextDim
                        else     -> TextMuted
                    },
                    fontWeight = if (isToday) FontWeight.ExtraBold else FontWeight.SemiBold
                )
            }
        }
    }
}
