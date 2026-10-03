@file:OptIn(ExperimentalTextApi::class)

package com.healthify.app.ui.dashboard

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
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
import com.healthify.app.firebase.FirebaseSync
import com.healthify.app.health.HealthConnectManager
import com.healthify.app.score.HealthScore
import com.healthify.app.streak.StreakManager
import com.healthify.app.ui.profile.UnitsReviewCard
import com.healthify.app.ui.theme.*
import com.healthify.app.units.UnitsReview
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

// ═══════════════════════════════════════════════════════════════════════════
// VIEW MODEL
// ═══════════════════════════════════════════════════════════════════════════

data class DashboardUiState(
    val user: UserEntity?   = null,
    val todayCheckIn: CheckInEntity? = null,
    val stepsToday: Int     = 0,
    val sleepHours: Float   = 0f,
    val streak: Int         = 0,
    val longestStreak: Int  = 0,
    val isStreakHealthy: Boolean = false,
    val aiTip: String       = "",
    val healthScore: Int    = 0,
    val isLoading: Boolean  = true,
    val healthConnectAvailable: Boolean = false,
    /**
     * Health Connect is installed AND the user has granted the two read
     * permissions. When it is available but not connected the dashboard
     * shows a card offering to connect, so the feature is always reachable
     * even for a user who dismissed the first-launch prompt.
     */
    val healthConnectConnected: Boolean = false,
    val canCheckIn: Boolean = true,
    val cooldownMsRemaining: Long = 0L,
    /** Dates in the current Mon–Sun week that have a saved check-in. */
    val weekCheckInDates: Set<LocalDate> = emptySet(),
    /** False until the first load() lands — the UI waits so defaults never flash. */
    val loaded: Boolean = false
)

class DashboardViewModel(
    private val repo: AppRepository,
    private val healthConnectManager: HealthConnectManager
) : ViewModel() {

    var uiState by mutableStateOf(DashboardUiState())
        private set

    init {
        // Observe BOTH the current-window check-in flow AND the user row so
        // the dashboard refreshes immediately after:
        //   • a check-in is saved (check_ins table changes)
        //   • the streak is recomputed inside StreakManager.evaluate
        //     (UPDATE users SET currentStreak=…)
        // Combining the two means a single collector hop triggers `load()`
        // on either upstream change. Without the user-flow leg, the
        // streak/longestStreak counters on the dashboard would stay stale
        // until the user backgrounded and reopened the app — which is the
        // exact bug testers reported ("streak doesn't update after
        // check-in until I restart").
        //
        // `distinctUntilChanged` on the (checkIn, user) pair is the
        // defensive guard against the infinite-loop regression: if any
        // downstream code ever writes to the users table with the same
        // values it already holds, Room will still re-emit on the user
        // flow, but `distinctUntilChanged` swallows the duplicate so we
        // don't re-enter load() and trigger another no-op write. Data
        // classes (`CheckInEntity`, `UserEntity`) give us structural
        // equality for free.
        viewModelScope.launch {
            combine(
                repo.getCheckInForCurrentWindowFlow(),
                repo.getUser()
            ) { ci, u -> ci to u }
                .distinctUntilChanged()
                .collectLatest { _ -> load() }
        }
    }

    fun load() = viewModelScope.launch {
        uiState = uiState.copy(isLoading = true)
        val user    = repo.getUserOnce()
        val checkIn = repo.getCheckInForCurrentWindow()
        val (canCheckIn, msRemaining) = repo.checkInCooldownStatus()
        val monday = LocalDate.now().with(DayOfWeek.MONDAY)
        val weekDates = repo.getCheckInsInRange(monday, monday.plusDays(6))
            .mapNotNull { runCatching { LocalDate.parse(it.date) }.getOrNull() }
            .toSet()

        // First paint comes from the local DB alone. A cold Health Connect
        // binder can take a second or two to answer; the rings then glide
        // from the checked-in values to the live ones instead of the page
        // sitting empty.
        if (!uiState.loaded) {
            val localSteps = checkIn?.steps ?: 0
            val localSleep = checkIn?.sleepHours ?: 0f
            uiState = uiState.copy(
                user             = user,
                todayCheckIn     = checkIn,
                stepsToday       = localSteps,
                sleepHours       = localSleep,
                streak           = user?.currentStreak ?: 0,
                longestStreak    = user?.longestStreak ?: 0,
                healthScore      = HealthScore.compute(checkIn, localSteps, localSleep, user),
                canCheckIn       = canCheckIn,
                cooldownMsRemaining = msRemaining,
                weekCheckInDates = weekDates,
                loaded           = true
            )
        }

        val healthData  = healthConnectManager.readAll()
        // Installed AND granted. Drives the "Connect Health Connect" card —
        // without it a user who dismissed the first-launch prompt would have
        // no way back to the feature short of system settings.
        val hcConnected = healthData.isAvailable && healthConnectManager.hasAllPermissions()
        // Prefer Health Connect when it actually has data; otherwise use whatever
        // the user entered during check-in. This way a 0-reading from HC doesn't
        // wipe out the user's manually-entered sleep/steps.
        val hcSteps = if (healthData.isAvailable) healthData.stepsToday else 0
        val hcSleep = if (healthData.isAvailable) healthData.sleepLastNightHours else 0f
        val steps   = if (hcSteps > 0) hcSteps else (checkIn?.steps ?: 0)
        val sleep   = if (hcSleep > 0f) hcSleep else (checkIn?.sleepHours ?: 0f)

        val streakResult = StreakManager.evaluate(repo)
        val score = HealthScore.compute(checkIn, steps, sleep, user)

        // Keep today's stored score in step with the live one (steps keep
        // climbing after the check-in), so Insights and Profile match home.
        // Only for a check-in dated today: the 6 PM window can still hold
        // yesterday evening's check-in, whose day is already over.
        if (checkIn != null && checkIn.wellnessScore != score &&
            checkIn.date == LocalDate.now().toString()
        ) {
            repo.updateCheckInScore(checkIn.date, score)
            launch { FirebaseSync.syncCheckIn(checkIn.copy(wellnessScore = score)) }
        }
        val tip   = generateTip(steps, sleep, checkIn, user)

        uiState = DashboardUiState(
            user                   = user,
            todayCheckIn           = checkIn,
            stepsToday             = steps,
            sleepHours             = sleep,
            streak                 = streakResult.current,
            longestStreak          = streakResult.longest,
            isStreakHealthy        = streakResult.isHealthyToday,
            aiTip                  = tip,
            healthScore            = score,
            isLoading              = false,
            healthConnectAvailable = healthData.isAvailable,
            healthConnectConnected = hcConnected,
            canCheckIn             = canCheckIn,
            cooldownMsRemaining    = msRemaining,
            weekCheckInDates       = weekDates,
            loaded                 = true
        )
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

// Ring colours (outer → inner). `end` is also the legend accent.
private val WaterStart = Color(0xFF2F8BFF)
private val WaterEnd   = Color(0xFF6FD6FF)
private val StepsStart = Color(0xFF0DB888)
private val StepsEnd   = Color(0xFF5BF5C4)
private val SleepStart = Color(0xFF7C5CF5)
private val SleepEnd   = Color(0xFFC4B5FD)

@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    onNavigateCheckIn: () -> Unit,
    onNavigateInsights: () -> Unit,
    onNavigateNotifications: () -> Unit,
    onNavigateProfile: () -> Unit,
    onConnectHealthConnect: () -> Unit = {}
) {
    val s = viewModel.uiState

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

    Box(Modifier.fillMaxSize()) {
    // Hold the page until real data arrives: the staggered entrance then
    // plays with the user's numbers instead of swapping in over defaults.
    if (s.loaded) Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        DashHeader(
            name     = s.user?.name,
            onAvatar = onNavigateProfile,
            modifier = Modifier.staggeredEnter(0)
        )

        val unitsReviewPending by UnitsReview.pending.collectAsState()
        if (unitsReviewPending) {
            UnitsReviewCard(
                onReview = onNavigateProfile,
                modifier = Modifier.padding(horizontal = 20.dp).staggeredEnter(1)
            )
        }

        RingsCard(s, Modifier.staggeredEnter(1))

        StreakCard(
            streak     = s.streak,
            longest    = s.longestStreak,
            weekDates  = s.weekCheckInDates,
            canCheckIn = s.canCheckIn,
            modifier   = Modifier.staggeredEnter(2)
        )

        CheckInCard(
            canCheckIn   = s.canCheckIn,
            hasCheckedIn = s.todayCheckIn != null,
            msRemaining  = s.cooldownMsRemaining,
            lastCheckInMs = s.todayCheckIn?.timestamp,
            onClick      = onNavigateCheckIn,
            modifier     = Modifier.staggeredEnter(3)
        )

        if (s.aiTip.isNotEmpty()) {
            TipCard(s.aiTip, Modifier.staggeredEnter(4))
        }

        // ── Health Connect connect prompt ───────────────────────────────
        // Shown whenever Health Connect is installed but the two read
        // permissions are not granted — including for a user who
        // dismissed the first-launch rationale. Tapping it re-opens that
        // same rationale, so there is always an in-app route to the
        // feature and the steps/sleep rings are never silently empty.
        if (s.healthConnectAvailable && !s.healthConnectConnected) {
            ConnectHealthConnectCard(onClick = onConnectHealthConnect, modifier = Modifier.staggeredEnter(5))
        }

        Spacer(Modifier.height(LocalBottomBarClearance.current + 8.dp))
    }

    // Status-bar scrim: cards scrolling up fade out under the clock/icons.
    Box(
        Modifier
            .fillMaxWidth()
            .windowInsetsTopHeight(WindowInsets.statusBars)
            .background(Brush.verticalGradient(listOf(BgDark.copy(alpha = 0.85f), BgDark.copy(alpha = 0.4f))))
    )
    }
}

// ── Header ───────────────────────────────────────────────────────────────────
@Composable
private fun DashHeader(name: String?, onAvatar: () -> Unit, modifier: Modifier = Modifier) {
    val tod = remember { TimeOfDay.now() }
    val greeting = when (tod) {
        TimeOfDay.MORNING -> "Good morning"
        TimeOfDay.DAY     -> "Good afternoon"
        TimeOfDay.EVENING -> "Good evening"
        TimeOfDay.NIGHT   -> "Good night"
    }
    Row(
        modifier.fillMaxWidth().padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // weight(1f) caps the column at "row width minus avatar" so
        // a long display name doesn't push the avatar off-screen on
        // 320dp devices or at 200% font scale.
        Column(Modifier.weight(1f)) {
            Text(
                LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, MMMM d", Locale.US)).uppercase(Locale.US),
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    greeting,
                    style = MaterialTheme.typography.titleMedium,
                    color = TextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.width(6.dp))
                GreetingGlyph(tod)
            }
            Text(
                name?.ifBlank { null } ?: "Friend",
                style = MaterialTheme.typography.headlineLarge.copy(brush = BrandGradient, fontSize = 32.sp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Avatar(name, onAvatar)
    }
}

/** Sun turns slowly by day; the moon drifts gently at night. */
@Composable
private fun GreetingGlyph(tod: TimeOfDay) {
    val reduced = LocalReducedMotion.current
    val glyph = when (tod) {
        TimeOfDay.MORNING -> "☀️"
        TimeOfDay.DAY     -> "🌤️"
        TimeOfDay.EVENING -> "🌙"
        TimeOfDay.NIGHT   -> "✨"
    }
    val t = if (reduced) remember { mutableFloatStateOf(0f) } else
        rememberInfiniteTransition(label = "glyph").animateFloat(
            0f, 1f, infiniteRepeatable(tween(4000, easing = EaseInOutSine), RepeatMode.Reverse), label = "glyphT"
        )
    Text(
        glyph,
        fontSize = 18.sp,
        modifier = Modifier.graphicsLayer {
            rotationZ    = (t.value - 0.5f) * 16f
            translationY = (t.value - 0.5f) * 4f * density
        }
    )
}

@Composable
private fun Avatar(name: String?, onClick: () -> Unit) {
    val spin = rememberInfiniteTransition(label = "avatarRing").let {
        if (LocalReducedMotion.current) remember { mutableFloatStateOf(0f) }
        else it.animateFloat(0f, 360f, infiniteRepeatable(tween(9000, easing = LinearEasing)), label = "avatarSpin")
    }
    Box(
        Modifier
            .size(54.dp)
            .clip(CircleShape)
            .clickable(onClickLabel = "Open profile", onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.fillMaxSize()) {
            rotate(spin.value) {
                drawCircle(
                    Brush.sweepGradient(listOf(Green, Sky, Lavender, Green)),
                    radius = size.minDimension / 2f - 1.5.dp.toPx(),
                    style = Stroke(2.dp.toPx())
                )
            }
        }
        Box(
            Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(Color(0xFF16263D), Color(0xFF0E1829)))),
            contentAlignment = Alignment.Center
        ) {
            Text(
                name?.firstOrNull()?.uppercase() ?: "🙂",
                style = MaterialTheme.typography.titleLarge.copy(brush = BrandGradient, fontSize = 20.sp)
            )
        }
    }
}

// ── Hero: activity rings ─────────────────────────────────────────────────────
@Composable
private fun RingsCard(s: DashboardUiState, modifier: Modifier = Modifier) {
    val waterGoal = (s.user?.waterGoalGlasses ?: 8).coerceAtLeast(1)
    val stepGoal  = (s.user?.stepGoal ?: 10_000).coerceAtLeast(1)
    val sleepGoal = (s.user?.sleepGoalHours ?: 8f).coerceAtLeast(0.5f)
    val water     = s.todayCheckIn?.waterGlasses ?: 0

    val waterP = water.toFloat() / waterGoal
    val stepsP = s.stepsToday.toFloat() / stepGoal
    val sleepP = s.sleepHours / sleepGoal

    GlassCard(modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        Column(
            Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 18.dp, start = 12.dp, end = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(Modifier.radialGlow(scoreColor(s.healthScore), alpha = if (s.todayCheckIn != null) 0.14f else 0.06f, scale = 1.05f)) {
                ActivityRings(
                    rings = listOf(
                        RingSpec(waterP, WaterStart, WaterEnd),
                        RingSpec(stepsP, StepsStart, StepsEnd),
                        RingSpec(sleepP, SleepStart, SleepEnd)
                    ),
                    score = s.healthScore,
                    description = "Health score ${s.healthScore}. " +
                        "Water $water of $waterGoal glasses. " +
                        "Steps ${s.stepsToday} of $stepGoal. " +
                        "Sleep ${"%.1f".format(s.sleepHours)} of ${"%.1f".format(sleepGoal)} hours."
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                when {
                    s.todayCheckIn == null -> "Check in to fill your rings ✨"
                    s.healthScore >= 80    -> "You're crushing it today! 💪"
                    s.healthScore >= 60    -> "Good progress — keep going! 🌱"
                    else                   -> "Every step counts. You've got this 💙"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = TextMuted,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth()) {
                RingLegend("Water", "$water/$waterGoal", waterP, WaterEnd)
                RingLegend("Steps", compactSteps(s.stepsToday), stepsP, StepsEnd)
                RingLegend("Sleep", "%.1fh".format(s.sleepHours), sleepP, SleepEnd)
            }
        }
    }
}

private fun scoreColor(score: Int): Color = when {
    score >= 80 -> Green
    score >= 60 -> Sky
    else        -> Lavender
}

private fun compactSteps(steps: Int): String =
    if (steps >= 10_000) "%.1fk".format(steps / 1000f) else "%,d".format(steps)

@Composable
private fun RowScope.RingLegend(label: String, value: String, progress: Float, color: Color) {
    val done = progress >= 1f
    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(color))
            Spacer(Modifier.width(6.dp))
            Text(label.uppercase(Locale.US), style = MaterialTheme.typography.labelSmall, color = TextMuted, maxLines = 1)
        }
        Spacer(Modifier.height(4.dp))
        Text(
            value,
            style = MaterialTheme.typography.titleLarge.copy(fontFeatureSettings = TABULAR),
            color = TextPrimary,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            if (done) "✓ Goal" else "${(progress.coerceIn(0f, 1f) * 100).roundToInt()}%",
            style = MaterialTheme.typography.labelMedium,
            color = color,
            maxLines = 1
        )
    }
}

// ── Streak + week chain ──────────────────────────────────────────────────────
@Composable
private fun StreakCard(
    streak: Int,
    longest: Int,
    weekDates: Set<LocalDate>,
    canCheckIn: Boolean,
    modifier: Modifier = Modifier
) {
    val accent = when {
        streak == 0 -> Green
        streak < 7  -> Amber
        else        -> Gold
    }
    val shown by rememberCountUp(streak, durationMs = 900, delayMs = 300)

    GlassCard(modifier.fillMaxWidth().padding(horizontal = 20.dp), tint = accent) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                FlameBadge(StreakManager.badge(streak), accent, lively = streak > 0)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            shown.toString(),
                            style = MaterialTheme.typography.headlineLarge.copy(fontFeatureSettings = TABULAR),
                            color = accent,
                            maxLines = 1,
                            softWrap = false
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "day streak",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary,
                            modifier = Modifier.padding(bottom = 4.dp),
                            maxLines = 1
                        )
                    }
                    Text(
                        StreakManager.message(streak),
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("BEST", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                    Text(
                        longest.toString(),
                        style = MaterialTheme.typography.titleLarge.copy(fontFeatureSettings = TABULAR),
                        color = TextPrimary
                    )
                }
            }
            Spacer(Modifier.height(18.dp))
            WeekChain(weekDates, accent, pulseToday = canCheckIn)
        }
    }
}

/**
 * Mon–Sun "don't break the chain" row. Checked-in days are filled and glow,
 * consecutive ones are joined by a bar, and today pulses while a check-in is
 * still available.
 */
@Composable
private fun WeekChain(dates: Set<LocalDate>, accent: Color, pulseToday: Boolean) {
    val today  = LocalDate.now()
    val monday = today.with(DayOfWeek.MONDAY)
    val days   = (0..6).map { monday.plusDays(it.toLong()) }
    val done   = days.map { it in dates }
    val pulse  = rememberBreath(1500, "todayPulse")
    val dot    = 30.dp

    Box(Modifier.fillMaxWidth()) {
        Canvas(Modifier.matchParentSize()) {
            val cell = size.width / 7f
            val cy   = dot.toPx() / 2f
            val r    = dot.toPx() / 2f
            for (i in 0 until 6) {
                if (done[i] && done[i + 1]) {
                    drawLine(
                        brush = Brush.horizontalGradient(listOf(accent.copy(alpha = 0.8f), accent.copy(alpha = 0.8f))),
                        start = Offset(cell * (i + 0.5f) + r - 2f, cy),
                        end   = Offset(cell * (i + 1.5f) - r + 2f, cy),
                        strokeWidth = 4.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }
            }
        }
        Row(Modifier.fillMaxWidth()) {
            days.forEachIndexed { i, d ->
                val isToday  = d == today
                val isFuture = d.isAfter(today)
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(dot), contentAlignment = Alignment.Center) {
                        when {
                            done[i] -> Box(
                                Modifier
                                    .fillMaxSize()
                                    .circleGlow(accent, 10.dp) { 0.55f }
                                    .clip(CircleShape)
                                    .background(Brush.linearGradient(listOf(accent, lerp(accent, Color.White, 0.35f)))),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Rounded.Check, null, tint = Color(0xFF0A1220), modifier = Modifier.size(17.dp))
                            }
                            isToday -> Canvas(Modifier.fillMaxSize()) {
                                val a = if (pulseToday) 0.35f + 0.65f * pulse.value else 0.5f
                                drawCircle(accent.copy(alpha = 0.10f * a), size.minDimension / 2f)
                                drawCircle(accent.copy(alpha = a), size.minDimension / 2f - 1.dp.toPx(), style = Stroke(2.dp.toPx()))
                                drawCircle(accent.copy(alpha = a), 3.dp.toPx())
                            }
                            else -> Box(
                                Modifier
                                    .fillMaxSize()
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = if (isFuture) 0.03f else 0.05f))
                                    .border(1.dp, Color.White.copy(alpha = if (isFuture) 0.06f else 0.12f), CircleShape)
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        d.dayOfWeek.name.take(1),
                        style = MaterialTheme.typography.labelMedium,
                        color = when {
                            isToday  -> accent
                            isFuture -> TextDim
                            else     -> TextMuted
                        },
                        fontWeight = if (isToday) FontWeight.ExtraBold else FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

// ── Check-in CTA ─────────────────────────────────────────────────────────────
@Composable
private fun CheckInCard(
    canCheckIn: Boolean,
    hasCheckedIn: Boolean,
    msRemaining: Long,
    lastCheckInMs: Long?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (!canCheckIn) {
        // Non-interactive info card: no onClick, no chevron, no ripple.
        val progress = lastCheckInMs?.let {
            val elapsed = (System.currentTimeMillis() - it).coerceAtLeast(0L)
            elapsed.toFloat() / (elapsed + msRemaining).coerceAtLeast(1L)
        } ?: 0f
        GlassCard(modifier.fillMaxWidth().padding(horizontal = 20.dp), tint = Lavender) {
            Row(
                Modifier.padding(18.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CountdownRing(progress, Lavender, Modifier.size(48.dp)) {
                    Icon(Icons.Rounded.Check, null, tint = Lavender, modifier = Modifier.size(22.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text("Checked in for today", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                    Text(
                        "Next check-in in ${formatRemaining(msRemaining)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Lavender
                    )
                }
            }
        }
        return
    }

    val shape = RoundedCornerShape(24.dp)
    val breath = rememberBreath(2800, "cta")
    val heart  = rememberBreath(700, "ctaHeart")
    val ink    = Color(0xFF04140E)
    Box(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .graphicsLayer {
                val s = 1f + 0.012f * breath.value
                scaleX = s; scaleY = s
            }
            .glow(Green, 26.dp, shape) { 0.28f + 0.3f * breath.value }
            .clip(shape)
            .background(Brush.linearGradient(listOf(Green, Teal, Sky)))
            .shimmer()
            .clickable(onClickLabel = "Start daily check-in", onClick = onClick)
    ) {
        Row(
            Modifier.padding(horizontal = 18.dp, vertical = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(48.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.25f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Rounded.Favorite, null, tint = ink,
                    modifier = Modifier.size(24.dp).graphicsLayer {
                        val s = 1f + 0.12f * heart.value * heart.value
                        scaleX = s; scaleY = s
                    }
                )
            }
            Column(Modifier.weight(1f)) {
                Text("Daily check-in", style = MaterialTheme.typography.titleLarge, color = ink)
                Text(
                    if (hasCheckedIn) "Update today's check-in" else "How are you feeling today? · 1 min",
                    style = MaterialTheme.typography.bodySmall,
                    color = ink.copy(alpha = 0.75f)
                )
            }
            Box(
                Modifier.size(38.dp).clip(CircleShape).background(ink.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, tint = ink, modifier = Modifier.size(20.dp))
            }
        }
    }
}

/** Thin circular progress with content in the middle. */
@Composable
fun CountdownRing(
    progress: Float,
    color: Color,
    modifier: Modifier = Modifier,
    strokeWidth: androidx.compose.ui.unit.Dp = 3.dp,
    content: @Composable BoxScope.() -> Unit = {}
) {
    val p by rememberSweep(progress.coerceIn(0f, 1f), durationMs = 1100, delayMs = 300)
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val sw = strokeWidth.toPx()
            val r = size.minDimension / 2f - sw / 2f
            drawCircle(color.copy(alpha = 0.16f), r, style = Stroke(sw))
            drawArc(
                color, -90f, 360f * p, false,
                topLeft = Offset(center.x - r, center.y - r),
                size = androidx.compose.ui.geometry.Size(r * 2, r * 2),
                style = Stroke(sw, cap = StrokeCap.Round)
            )
        }
        content()
    }
}

fun formatRemaining(ms: Long): String {
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

// ── Daily tip ────────────────────────────────────────────────────────────────
@Composable
private fun TipCard(tip: String, modifier: Modifier = Modifier) {
    // Tips lead with their own emoji ("🌙 You slept…"); lift it into the orb.
    val parts = tip.split(" ", limit = 2)
    val (icon, body) = if (parts.size == 2 && parts[0].length <= 4) parts[0] to parts[1] else "💡" to tip
    GlassCard(modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        Row(Modifier.padding(18.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            IconOrb(Gold) { Text(icon, fontSize = 20.sp) }
            Column(Modifier.weight(1f)) {
                Text("DAILY TIP", style = MaterialTheme.typography.labelSmall, color = Gold)
                Spacer(Modifier.height(4.dp))
                Text(body, style = MaterialTheme.typography.bodyMedium, color = Color(0xD9EDF5FF))
            }
        }
    }
}

// ── Health Connect connect card ──────────────────────────────────────────────
@Composable
private fun ConnectHealthConnectCard(onClick: () -> Unit, modifier: Modifier = Modifier) {
    GlassCard(modifier.fillMaxWidth().padding(horizontal = 20.dp), tint = Sky, onClick = onClick) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            IconOrb(Sky) { Icon(Icons.Rounded.Link, null, tint = Sky) }
            Column(Modifier.weight(1f)) {
                Text(
                    "Fill in steps and sleep automatically",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary
                )
                Text(
                    "Connect Health Connect and your rings pick up " +
                    "today's steps and last night's sleep on their own.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted
                )
            }
            Icon(Icons.Rounded.ChevronRight, null, tint = Sky)
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// BOTTOM NAV — floating glass pill with a raised check-in orb
// ═══════════════════════════════════════════════════════════════════════════

private val PillHeight   = 68.dp
private val OrbSize      = 60.dp
private val OrbOverhang  = 20.dp

/**
 * [selectedPage] is the settled tab (0 Home, 1 Insights, 2 Reminders,
 * 3 Profile). [pagePosition] is the live pager position including swipe
 * offset; the selection pill follows it during a swipe. It is read in the
 * layout phase, so swiping never recomposes the nav.
 */
@Composable
fun DashBottomNav(
    selectedPage: Int,
    pagePosition: () -> Float,
    canCheckIn: Boolean,
    ready: Boolean,
    onHome: () -> Unit,
    onCheckIn: () -> Unit,
    onInsights: () -> Unit,
    onNotifications: () -> Unit,
    onProfile: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = rememberHaptics()
    val shape = RoundedCornerShape(28.dp)
    Box(
        modifier
            .fillMaxWidth()
            // Content scrolls under the nav; fade it out so card edges don't
            // peek through the gap around the floating pill.
            .background(Brush.verticalGradient(0f to Color.Transparent, 0.45f to BgDark.copy(alpha = 0.75f), 1f to BgDark.copy(alpha = 0.95f)))
            .navigationBarsPadding()
            .padding(start = 14.dp, end = 14.dp, bottom = 10.dp)
            .height(PillHeight + OrbOverhang)
    ) {
        BoxWithConstraints(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(PillHeight)
                .glow(Color.Black, 18.dp, shape, alpha = 0.7f)
                .clip(shape)
                .background(Brush.verticalGradient(listOf(GlassChromeTop, GlassChromeBottom)))
                .border(1.dp, Brush.verticalGradient(listOf(GlassBorderTop, GlassBorderBottom)), shape)
        ) {
            val slot = maxWidth / 5
            val indicatorW = 56.dp
            val indicatorH = 34.dp
            // Indicator behind the icon, gliding with the pager. Pages 0,1
            // map to slots 0,1 and pages 2,3 to slots 3,4 (slot 2 is the orb).
            Box(
                Modifier
                    .offset {
                        val p = pagePosition()
                        val slotPos = if (p <= 1f) p else 1f + (p - 1f).coerceAtMost(1f) * 2f + (p - 2f).coerceAtLeast(0f)
                        IntOffset(
                            ((slot * slotPos) + (slot - indicatorW) / 2).roundToPx(),
                            9.dp.roundToPx()
                        )
                    }
                    .size(indicatorW, indicatorH)
                    .clip(RoundedCornerShape(17.dp))
                    .background(Brush.linearGradient(listOf(Green.copy(alpha = 0.26f), Sky.copy(alpha = 0.14f))))
            )
            Row(Modifier.fillMaxSize()) {
                NavItem(Icons.Rounded.Home, "Home", selectedPage == 0) { haptics.tick(); onHome() }
                NavItem(Icons.Rounded.Insights, "Insights", selectedPage == 1) { haptics.tick(); onInsights() }
                Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.BottomCenter) {
                    if (ready) Text(
                        if (canCheckIn) "Check-in" else "Done",
                        color = if (canCheckIn) Green else Lavender,
                        fontSize = 10.5.sp.capped(),
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Visible,
                        modifier = Modifier.padding(bottom = 10.dp)
                    )
                }
                NavItem(Icons.Rounded.Notifications, "Reminders", selectedPage == 2) { haptics.tick(); onNotifications() }
                NavItem(Icons.Rounded.Person, "Profile", selectedPage == 3) { haptics.tick(); onProfile() }
            }
        }
        val orbAlpha by animateFloatAsState(if (ready) 1f else 0f, tween(400), label = "orbIn")
        CheckInOrb(
            canCheckIn = canCheckIn,
            onClick    = { haptics.confirm(); onCheckIn() },
            modifier   = Modifier
                .align(Alignment.TopCenter)
                .graphicsLayer {
                    alpha = orbAlpha
                    val s = 0.6f + 0.4f * orbAlpha
                    scaleX = s; scaleY = s
                    compositingStrategy = CompositingStrategy.ModulateAlpha // keep the glow round while fading
                }
        )
    }
}

@Composable
private fun RowScope.NavItem(icon: ImageVector, label: String, selected: Boolean, onClick: () -> Unit) {
    val tint by animateColorAsState(if (selected) Green else TextMuted, tween(250), label = "navTint")
    val lift by animateFloatAsState(if (selected) 1.12f else 1f, spring(dampingRatio = 0.5f, stiffness = 500f), label = "navLift")
    Column(
        Modifier
            .weight(1f)
            .fillMaxHeight()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .semantics { role = Role.Tab; this.selected = selected },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            icon, contentDescription = null, tint = tint,
            modifier = Modifier.size(24.dp).graphicsLayer { scaleX = lift; scaleY = lift }
        )
        Spacer(Modifier.height(4.dp))
        // Single line, and capped growth: at 200% font scale "Reminders"
        // would otherwise spill into the neighbouring slot.
        Text(
            label,
            color = tint,
            fontSize = 10.5.sp.capped(),
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Visible
        )
    }
}

@Composable
private fun CheckInOrb(canCheckIn: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val breath = rememberBreath(2200, "orb")
    val heart  = rememberBreath(650, "orbHeart")
    val ink    = Color(0xFF04140E)
    Box(
        modifier
            .size(OrbSize)
            .then(
                if (canCheckIn) Modifier.circleGlow(Green, 18.dp) { 0.35f + 0.4f * breath.value }
                else Modifier.circleGlow(Color.Black, 12.dp) { 0.6f }
            )
            .clip(CircleShape)
            .background(
                if (canCheckIn) Brush.linearGradient(listOf(Green, Teal, Sky))
                else Brush.linearGradient(listOf(Color(0xFF1C2744), Color(0xFF121B30)))
            )
            .border(
                if (canCheckIn) 0.dp else 1.5.dp,
                if (canCheckIn) Color.Transparent else Lavender.copy(alpha = 0.5f),
                CircleShape
            )
            .clickable(onClickLabel = "Daily check-in", onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (canCheckIn) {
            Icon(
                Icons.Rounded.Favorite, contentDescription = "Daily check-in", tint = ink,
                modifier = Modifier.size(28.dp).graphicsLayer {
                    val s = 1f + 0.14f * heart.value * heart.value
                    scaleX = s; scaleY = s
                }
            )
        } else {
            Icon(Icons.Rounded.Check, contentDescription = "Checked in", tint = Lavender, modifier = Modifier.size(28.dp))
        }
    }
}
