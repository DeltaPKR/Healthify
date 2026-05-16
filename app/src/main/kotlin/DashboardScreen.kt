package com.healthify.app.ui.dashboard

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.healthify.app.data.db.CheckInEntity
import com.healthify.app.data.db.UserEntity
import com.healthify.app.data.repository.AppRepository
import com.healthify.app.health.HealthConnectManager
import com.healthify.app.streak.StreakManager
import com.healthify.app.ui.theme.*
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

// ═══════════════════════════════════════════════════════════════════════════
// VIEW MODEL
// ═══════════════════════════════════════════════════════════════════════════

data class DashboardUiState(
    val user: UserEntity?   = null,
    val todayCheckIn: CheckInEntity? = null,
    val stepsToday: Int     = 0,
    val sleepHours: Float   = 0f,
    val heartRate: Int      = 0,
    val streak: Int         = 0,
    val longestStreak: Int  = 0,
    val isStreakHealthy: Boolean = false,
    val aiTip: String       = "",
    val healthScore: Int    = 0,
    val isLoading: Boolean  = true,
    val healthConnectAvailable: Boolean = false,
    val canCheckIn: Boolean = true,
    val cooldownMsRemaining: Long = 0L
)

class DashboardViewModel(
    private val repo: AppRepository,
    private val healthConnectManager: HealthConnectManager
) : ViewModel() {

    var uiState by mutableStateOf(DashboardUiState())
        private set

    init {
        // Observe today's check-in as a Flow so the dashboard refreshes
        // immediately after the user submits a check-in (or any DB write).
        viewModelScope.launch {
            repo.getCheckInForTodayFlow().collectLatest { _ -> load() }
        }
    }

    fun load() = viewModelScope.launch {
        uiState = uiState.copy(isLoading = true)
        val user    = repo.getUserOnce()
        val checkIn = repo.getCheckInForToday()

        val healthData = healthConnectManager.readAll()
        // Prefer Health Connect when it actually has data; otherwise use whatever
        // the user entered during check-in. This way a 0-reading from HC doesn't
        // wipe out the user's manually-entered sleep/steps.
        val hcSteps = if (healthData.isAvailable) healthData.stepsToday else 0
        val hcSleep = if (healthData.isAvailable) healthData.sleepLastNightHours else 0f
        val steps   = if (hcSteps > 0) hcSteps else (checkIn?.steps ?: 0)
        val sleep   = if (hcSleep > 0f) hcSleep else (checkIn?.sleepHours ?: 0f)
        val hr      = healthData.heartRateAvg

        val streakResult = StreakManager.evaluate(repo)
        val score = computeScore(checkIn, steps, sleep, user)
        val tip   = generateTip(steps, sleep, checkIn, user)

        val (canCheckIn, msRemaining) = repo.checkInCooldownStatus()

        uiState = DashboardUiState(
            user                   = user,
            todayCheckIn           = checkIn,
            stepsToday             = steps,
            sleepHours             = sleep,
            heartRate              = hr,
            streak                 = streakResult.current,
            longestStreak          = streakResult.longest,
            isStreakHealthy        = streakResult.isHealthyToday,
            aiTip                  = tip,
            healthScore            = score,
            isLoading              = false,
            healthConnectAvailable = healthData.isAvailable,
            canCheckIn             = canCheckIn,
            cooldownMsRemaining    = msRemaining
        )
    }

    private fun computeScore(ci: CheckInEntity?, steps: Int, sleep: Float, user: UserEntity?): Int {
        var s = 50
        ci?.let {
            if (it.moodScore >= 0) s += (it.moodScore + 1) * 4
            s += minOf(20, it.waterGlasses * 2)
            s += minOf(15, (sleep / 8 * 15).toInt())
            s += minOf(15, (steps.toFloat() / (user?.stepGoal ?: 10000) * 15).toInt())
        }
        return minOf(100, maxOf(0, s))
    }

    private fun generateTip(steps: Int, sleep: Float, ci: CheckInEntity?, user: UserEntity?): String {
        val goal = user?.stepGoal ?: 10_000
        val pct  = if (goal > 0) steps.toFloat() / goal else 0f
        return when {
            sleep < 6   -> "🌙 You slept under 6 hours. Even a 20-min nap can help recovery."
            pct < 0.3   -> "🚶 You've only hit ${(pct * 100).toInt()}% of your step goal. A 10-min walk after each meal adds up fast."
            pct > 0.8   -> "🏆 Amazing! You're at ${(pct * 100).toInt()}% of your step goal. Finish strong today!"
            ci?.foodQuality == "skip" -> "🥗 You mentioned skipping a meal. Try a light snack — your body needs fuel."
            ci?.moodScore != null && ci.moodScore <= 1 -> "💙 Mood seems low today. A short walk outside can genuinely lift your spirits."
            else        -> "💡 You're doing well! Consistency is the key to lasting health. Keep it up."
        }
    }

    class Factory(val repo: AppRepository, val healthConnectManager: HealthConnectManager) :
        ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(c: Class<T>) =
            DashboardViewModel(repo, healthConnectManager) as T
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// SCREEN
// ═══════════════════════════════════════════════════════════════════════════

@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    onNavigateCheckIn: () -> Unit,
    onNavigateInsights: () -> Unit,
    onNavigateNotifications: () -> Unit,
    onNavigateProfile: () -> Unit
) {
    val s = viewModel.uiState
    val hour = remember { java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY) }
    val greeting = when { hour < 12 -> "Good morning"; hour < 18 -> "Good afternoon"; else -> "Good evening" }
    val greetEmoji = when { hour < 12 -> "☀️"; hour < 18 -> "🌤️"; else -> "🌙" }

    // Refresh on resume — picks up changes from CheckIn, Profile, Notifications
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.load()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Ticker: while locked, refresh every minute so the countdown text stays
    // accurate and the card auto-unlocks the moment the cooldown clears.
    LaunchedEffect(s.canCheckIn) {
        while (!viewModel.uiState.canCheckIn) {
            kotlinx.coroutines.delay(60_000L)
            viewModel.load()
        }
    }

    Scaffold(
        containerColor = BgDark,
    ) { pad ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(pad)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 16.dp)
        ) {
            // ── Header ─────────────────────────────────────────────────────
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, MMMM d", Locale.US)),
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        "$greeting, $greetEmoji",
                        style = MaterialTheme.typography.bodyMedium, color = TextMuted
                    )
                    Text(
                        s.user?.name ?: "Friend",
                        style = MaterialTheme.typography.headlineMedium, color = Green
                    )
                }
                // Avatar — tapping it opens the Profile screen
                Box(
                    Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Brush.radialGradient(listOf(GreenDim, SkyDim)))
                        .clickable { onNavigateProfile() },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        s.user?.name?.firstOrNull()?.uppercase() ?: "👤",
                        fontSize = 22.sp, color = Green
                    )
                }
            }

            // ── Streak Banner ───────────────────────────────────────────────
            if (s.streak > 0) {
                StreakBanner(streak = s.streak, longest = s.longestStreak, isHealthy = s.isStreakHealthy)
                Spacer(Modifier.height(8.dp))
            }

            // ── Health Score Ring ───────────────────────────────────────────
            Card(
                Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                shape  = RoundedCornerShape(20.dp)
            ) {
                Column(
                    Modifier.padding(24.dp).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    HealthScoreRing(score = s.healthScore)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        when {
                            s.healthScore >= 80 -> "You're crushing it today! 💪"
                            s.healthScore >= 60 -> "Good progress — keep going! 🌱"
                            else                -> "Every step counts. You've got this 💙"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextMuted
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            // ── Metrics Row ─────────────────────────────────────────────────
            Text("Today's Metrics",
                Modifier.padding(horizontal = 24.dp),
                style = MaterialTheme.typography.labelSmall)
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier.padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                val water = s.todayCheckIn?.waterGlasses ?: 0
                val goal  = s.user?.waterGoalGlasses ?: 8
                MetricCard("💧", "Water", "$water/$goal", "glasses", Sky, SkyDim, water.toFloat() / goal)
                MetricCard("🚶", "Steps", "%,d".format(s.stepsToday), "steps", Green, GreenDim,
                    s.stepsToday.toFloat() / (s.user?.stepGoal ?: 10_000))
                MetricCard("🌙", "Sleep", "%.1fh".format(s.sleepHours), "hrs", Lavender, LavenderDim,
                    s.sleepHours / (s.user?.sleepGoalHours ?: 8f))
            }

            Spacer(Modifier.height(20.dp))

            // ── AI Tip ──────────────────────────────────────────────────────
            if (s.aiTip.isNotEmpty()) {
                Card(
                    Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0B2520)),
                    shape  = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, Color(0x301AD9A0))
                ) {
                    Row(Modifier.padding(18.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("💡", fontSize = 22.sp)
                        Column {
                            Text("AI HEALTH TIP", style = MaterialTheme.typography.labelSmall, color = Green)
                            Spacer(Modifier.height(4.dp))
                            Text(s.aiTip, style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xCCEDF5FF))
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            // ── Check-in CTA ────────────────────────────────────────────────
            CheckInCard(
                hasCheckedIn        = s.todayCheckIn != null,
                canCheckIn          = s.canCheckIn,
                msRemaining         = s.cooldownMsRemaining,
                onClick             = onNavigateCheckIn
            )

            Spacer(Modifier.height(16.dp))

            // ── Heart Rate (if available) ───────────────────────────────────
            if (s.heartRate > 0) {
                Card(
                    Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                    shape  = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("❤️‍🔥", fontSize = 26.sp)
                        Column {
                            Text("Heart Rate", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                            Text("${s.heartRate} BPM", style = MaterialTheme.typography.titleLarge, color = Coral)
                        }
                    }
                }
            }
        }
    }
}

// ── Check-in CTA card ────────────────────────────────────────────────────────
@Composable
private fun CheckInCard(
    hasCheckedIn: Boolean,
    canCheckIn: Boolean,
    msRemaining: Long,
    onClick: () -> Unit
) {
    val locked = !canCheckIn
    val cardContainer = if (locked) SurfaceCard2 else SurfaceCard
    val borderColor   = if (locked) Color(0x30A78BFA) else Color(0x30FF7B6E)

    val cardModifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 24.dp)

    val content: @Composable () -> Unit = {
        Row(
            Modifier.padding(18.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(46.dp).clip(RoundedCornerShape(15.dp))
                    .background(if (locked) LavenderDim else CoralDim),
                contentAlignment = Alignment.Center
            ) { Text(if (locked) "⏳" else "❤️", fontSize = 22.sp) }

            Column(Modifier.weight(1f)) {
                Text(
                    if (locked) "Already checked in" else "Daily Check-in",
                    style = MaterialTheme.typography.titleMedium, color = TextPrimary
                )
                Text(
                    when {
                        locked         -> "Next check-in in ${formatRemaining(msRemaining)}"
                        hasCheckedIn   -> "Updated today ✓"
                        else           -> "How are you feeling today?"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = when {
                        locked       -> Lavender
                        hasCheckedIn -> Green
                        else         -> TextMuted
                    }
                )
            }
            if (!locked) {
                Icon(Icons.Default.ChevronRight, null, tint = Coral)
            }
        }
    }

    if (locked) {
        // Non-interactive info card: no onClick, no chevron, no ripple.
        Card(
            modifier = cardModifier,
            colors = CardDefaults.cardColors(containerColor = cardContainer),
            shape  = RoundedCornerShape(20.dp),
            border = BorderStroke(1.dp, borderColor)
        ) { content() }
    } else {
        Card(
            onClick = onClick,
            modifier = cardModifier,
            colors = CardDefaults.cardColors(containerColor = cardContainer),
            shape  = RoundedCornerShape(20.dp),
            border = BorderStroke(1.dp, borderColor)
        ) { content() }
    }
}

private fun formatRemaining(ms: Long): String {
    if (ms <= 0) return "now"
    val totalSeconds = (ms / 1000L).toInt()
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return when {
        h > 0 && m > 0 -> "${h}h ${m}m"
        h > 0          -> "${h}h"
        m > 0          -> "${m}m"
        s > 0          -> "${s}s"
        else           -> "now"
    }
}

// ── Streak Banner ─────────────────────────────────────────────────────────────
@Composable
fun StreakBanner(streak: Int, longest: Int, isHealthy: Boolean) {
    val badge  = StreakManager.badge(streak)
    val msg    = StreakManager.message(streak)
    val bgFrom = if (streak >= 7) Color(0xFF2D1A00) else Color(0xFF1A2D00)
    val border = if (streak >= 7) Color(0x40FFD166) else Color(0x401AD9A0)

    Card(
        Modifier.fillMaxWidth().padding(horizontal = 24.dp),
        colors = CardDefaults.cardColors(containerColor = bgFrom),
        shape  = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, border)
    ) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(badge, fontSize = 30.sp)
            Column(Modifier.weight(1f)) {
                Text(msg, style = MaterialTheme.typography.titleMedium,
                    color = if (streak >= 7) Gold else Green)
                Text("Longest: $longest days", style = MaterialTheme.typography.bodySmall)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(streak.toString(),
                    style = MaterialTheme.typography.headlineLarge,
                    color = if (streak >= 7) Gold else Green)
                Text("streak", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

// ── Animated Health Score Ring ────────────────────────────────────────────────
@Composable
fun HealthScoreRing(score: Int) {
    val animatedScore by animateIntAsState(targetValue = score,
        animationSpec = tween(1400, easing = EaseOutCubic), label = "score")
    val sweep = animatedScore / 100f * 360f

    Box(Modifier.size(140.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            drawArc(color = Color(0x10FFFFFF), startAngle = -90f, sweepAngle = 360f,
                useCenter = false, style = androidx.compose.ui.graphics.drawscope.Stroke(
                    width = 22f, cap = StrokeCap.Round))
            drawArc(
                brush = Brush.sweepGradient(listOf(Color(0xFF1AD9A0), Color(0xFF5EC4FF), Color(0xFF1AD9A0))),
                startAngle = -90f, sweepAngle = sweep, useCenter = false,
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 22f, cap = StrokeCap.Round)
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(animatedScore.toString(),
                style = MaterialTheme.typography.headlineLarge.copy(fontSize = 32.sp),
                color = TextPrimary)
            Text("HEALTH\nSCORE", style = MaterialTheme.typography.labelSmall,
                color = TextMuted, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
    }
}

// ── Metric Card ───────────────────────────────────────────────────────────────
@Composable
fun RowScope.MetricCard(
    icon: String, label: String, value: String, unit: String,
    color: Color, colorDim: Color, progress: Float
) {
    Card(
        Modifier.weight(1f),
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        shape  = RoundedCornerShape(18.dp)
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(colorDim),
                contentAlignment = Alignment.Center) {
                Text(icon, fontSize = 18.sp)
            }
            Text(value, style = MaterialTheme.typography.titleLarge.copy(
                fontSize = 17.sp, fontWeight = FontWeight.ExtraBold), color = TextPrimary)
            Text(unit, style = MaterialTheme.typography.bodySmall, color = TextDim)
            LinearProgressIndicator(
                progress = { minOf(1f, maxOf(0f, progress)) },
                modifier = Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)),
                color       = color,
                trackColor  = Divider
            )
            Text("${(minOf(1f, progress) * 100).toInt()}%",
                style = MaterialTheme.typography.labelSmall, color = color)
        }
    }
}

// ── Bottom Nav ────────────────────────────────────────────────────────────────
@Composable
fun DashBottomNav(
    selectedTab: String = "home",
    onHome: () -> Unit,
    onCheckIn: () -> Unit,
    onInsights: () -> Unit,
    onNotifications: () -> Unit,
    onProfile: () -> Unit
) {
    NavigationBar(containerColor = Color(0xFF05090F), tonalElevation = 0.dp) {
        NavigationBarItem(
            selected = selectedTab == "home",
            onClick  = onHome,
            icon     = { Text("🏠", fontSize = 20.sp) },
            label    = { Text("Home") },
            colors   = navColors()
        )
        NavigationBarItem(
            selected = selectedTab == "insights",
            onClick  = onInsights,
            icon     = { Text("📊", fontSize = 20.sp) },
            label    = { Text("Insights") },
            colors   = navColors()
        )
        NavigationBarItem(
            selected = false,
            onClick  = onCheckIn,
            icon = {
                Box(
                    Modifier.size(50.dp).clip(RoundedCornerShape(16.dp))
                        .background(Brush.linearGradient(listOf(Green, Color(0xFF0DB888)))),
                    contentAlignment = Alignment.Center
                ) { Text("❤️", fontSize = 22.sp) }
            },
            label  = { Text("Check-in", color = Green) },
            colors = navColors()
        )
        NavigationBarItem(
            selected = selectedTab == "reminders",
            onClick  = onNotifications,
            icon     = { Text("🔔", fontSize = 20.sp) },
            label    = { Text("Reminders") },
            colors   = navColors()
        )
        NavigationBarItem(
            selected = selectedTab == "profile",
            onClick  = onProfile,
            icon     = { Text("👤", fontSize = 20.sp) },
            label    = { Text("Profile") },
            colors   = navColors()
        )
    }
}

@Composable
private fun navColors() = NavigationBarItemDefaults.colors(
    selectedIconColor   = Green,
    selectedTextColor   = Green,
    unselectedIconColor = TextDim,
    unselectedTextColor = TextDim,
    indicatorColor      = GreenDim
)
