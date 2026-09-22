package com.healthify.app.ui.checkin

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.healthify.app.data.db.CheckInEntity
import com.healthify.app.data.db.UserEntity
import com.healthify.app.data.repository.AppRepository
import com.healthify.app.firebase.FirebaseSync
import com.healthify.app.health.HealthConnectManager
import com.healthify.app.notifications.NotificationScheduler
import com.healthify.app.score.HealthScore
import com.healthify.app.streak.StreakManager
import com.healthify.app.ui.dashboard.CountdownRing
import com.healthify.app.ui.theme.*
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

private data class Mood(val emoji: String, val label: String, val color: Color)

private val MOODS = listOf(
    Mood("😢", "Terrible",  Coral),
    Mood("😕", "Not great", Amber),
    Mood("😐", "Okay",      Gold),
    Mood("🙂", "Good",      Green),
    Mood("😄", "Amazing",   Sky)
)

private data class Food(val key: String, val emoji: String, val label: String)

private val FOODS = listOf(
    Food("well", "🥗", "Ate well"),
    Food("ok",   "🍽️", "Decent"),
    Food("poor", "🍕", "Not great"),
    Food("skip", "😕", "Skipped")
)

@Composable
fun CheckInScreen(
    repo: AppRepository,
    healthConnect: HealthConnectManager,
    onComplete: () -> Unit,
    onBack: () -> Unit
) {
    val scope   = rememberCoroutineScope()
    val haptics = rememberHaptics()
    var step    by remember { mutableStateOf(0) }

    // answers
    var mood      by remember { mutableStateOf(-1) }
    var water     by remember { mutableStateOf(4) }
    var food      by remember { mutableStateOf("") }
    var sleepH    by remember { mutableStateOf(7f) }
    var rating    by remember { mutableStateOf(0) }
    var manualSteps by remember { mutableStateOf(0) }
    var isSaving  by remember { mutableStateOf(false) }
    var celebration by remember { mutableStateOf<CelebrationData?>(null) }

    // Probe Health Connect once: if it can't give us steps (unavailable, no
    // permission, or no source writing data), we ask the user manually.
    var hcStepsToday    by remember { mutableStateOf(0) }
    var needsManualSteps by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        val data = healthConnect.readAll()
        hcStepsToday    = data.stepsToday
        needsManualSteps = !data.isAvailable || data.stepsToday == 0
    }

    // The user's own goals: the water glass fills toward what they signed up
    // for, and the steps question quotes their step goal.
    var user by remember { mutableStateOf<UserEntity?>(null) }
    LaunchedEffect(Unit) { user = repo.getUserOnce() }
    val waterGoal = (user?.waterGoalGlasses ?: 8).coerceAtLeast(1)

    val total = if (needsManualSteps) 6 else 5
    val stepsQuestionIndex = 5  // only used when needsManualSteps == true

    // ── Cool-down state ────────────────────────────────────────────────────
    var loadedCooldown by remember { mutableStateOf(false) }
    var canCheckIn     by remember { mutableStateOf(true) }
    var msRemaining    by remember { mutableStateOf(0L) }
    var lastCheckInMs  by remember { mutableStateOf<Long?>(null) }

    LaunchedEffect(Unit) {
        val (can, ms) = repo.checkInCooldownStatus()
        canCheckIn  = can
        msRemaining = ms
        lastCheckInMs = repo.lastCheckInTimestamp()
        loadedCooldown = true
    }
    // Ticker to update remaining time live while locked
    LaunchedEffect(canCheckIn, msRemaining) {
        if (!canCheckIn && msRemaining > 0) {
            while (msRemaining > 0) {
                delay(1000L)
                msRemaining = (msRemaining - 1000L).coerceAtLeast(0L)
                if (msRemaining == 0L) canCheckIn = true
            }
        }
    }

    val context = LocalContext.current

    fun canProceed() = when (step) {
        0 -> mood >= 0
        4 -> rating > 0
        else -> true
    }

    fun save() {
        scope.launch {
            isSaving = true
            val healthData = healthConnect.readAll()
            // Health Connect wins when it has data; otherwise what the user
            // entered — the same preference the dashboard applies, so the
            // score revealed below is exactly the one home shows next.
            val effectiveSteps = if (healthData.isAvailable && healthData.stepsToday > 0)
                healthData.stepsToday else manualSteps
            val effectiveSleep = if (healthData.isAvailable && healthData.sleepLastNightHours > 0f)
                healthData.sleepLastNightHours else sleepH
            val today  = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
            val before = repo.getUserOnce()
            val draft = CheckInEntity(
                date          = today,
                moodScore     = mood,
                waterGlasses  = water,
                foodQuality   = food,
                sleepHours    = sleepH,
                dayRating     = rating,
                steps         = effectiveSteps,
                timestamp     = System.currentTimeMillis()
            )
            val parts = HealthScore.parts(draft, effectiveSteps, effectiveSleep, before)
            val ci = draft.copy(wellnessScore = parts.sumOf { it.points }.coerceIn(0, 100))

            // The persistence work below MUST complete even if the user
            // presses back during the saving spinner (which destroys the
            // composable and cancels `scope`). Without NonCancellable a
            // back-press could interrupt the Room upsert and the check-in
            // is silently lost. The Firebase calls inside also become
            // uncancellable, but they only `await()` queued writes that
            // Firestore will retry anyway, so the extra latency is OK.
            val sr = withContext(NonCancellable) {
                repo.saveCheckIn(ci)
                FirebaseSync.syncCheckIn(ci)
                val result = StreakManager.evaluate(repo)
                if (NotificationScheduler.isMilestone(result.current)) {
                    NotificationScheduler.notifyStreakMilestone(context, result.current)
                }
                FirebaseSync.pushStreakUpdate(streak = result.current, longest = result.longest)
                result
            }
            // Show the reward moment; "Done" (or back) returns to the
            // dashboard, whose ON_RESUME reload picks up the new check-in.
            celebration = CelebrationData(
                score          = ci.wellnessScore,
                parts          = parts,
                streak         = sr.current,
                previousStreak = before?.currentStreak ?: 0,
                isNewBest      = sr.current > 1 && sr.current > (before?.longestStreak ?: 0),
                isHealthyDay   = sr.isHealthyToday,
                isMilestone    = NotificationScheduler.isMilestone(sr.current)
            )
            isSaving = false
        }
    }

    // ── Aurora tint follows the question (and the chosen mood) ─────────────
    val accent = when {
        celebration != null                         -> Green
        loadedCooldown && !canCheckIn               -> Lavender
        step == 0                                   -> MOODS.getOrNull(mood)?.color ?: Green
        step == 1                                   -> Sky
        step == 2                                   -> Gold
        step == 3                                   -> Lavender
        step == 4                                   -> Gold
        step == stepsQuestionIndex && needsManualSteps -> Green
        else                                        -> Teal
    }
    LaunchedEffect(accent) { Aurora.tint = accent }
    DisposableEffect(Unit) { onDispose { Aurora.tint = null } }

    // System back steps to the previous question instead of dropping the
    // whole check-in; on the celebration it simply finishes.
    BackHandler(enabled = celebration == null && step > 0 && !isSaving) { step-- }
    BackHandler(enabled = celebration != null) { onComplete() }

    celebration?.let {
        CelebrationScreen(it, onDone = onComplete)
        return
    }

    // ── Locked state: already checked in within the current window ────────
    if (loadedCooldown && !canCheckIn) {
        val progress = lastCheckInMs?.let {
            val elapsed = (System.currentTimeMillis() - it).coerceAtLeast(0L)
            elapsed.toFloat() / (elapsed + msRemaining).coerceAtLeast(1L)
        } ?: 0f
        CooldownScreen(msRemaining = msRemaining, progress = progress, onBack = onBack)
        return
    }

    Column(
        Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .imePadding()
    ) {
        // ── Top bar ─────────────────────────────────────────────────────
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            GlassIconButton(Icons.Rounded.Close, "Close check-in", onBack)
            SegmentedProgress(current = step, total = total, accent = accent, modifier = Modifier.weight(1f))
            Text(
                if (step < total) "${step + 1}/$total" else "✓",
                style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = TABULAR),
                color = TextMuted
            )
        }

        // ── Question ─────────────────────────────────────────────────────
        AnimatedContent(
            targetState = step,
            transitionSpec = {
                val forward = targetState > initialState
                (fadeIn(tween(300, delayMillis = 60)) +
                    slideInHorizontally(tween(420, easing = EaseOutCubic)) { if (forward) it / 4 else -it / 4 } +
                    scaleIn(tween(420, easing = EaseOutCubic), initialScale = 0.96f)) togetherWith
                    (fadeOut(tween(160)) +
                        slideOutHorizontally(tween(320)) { if (forward) -it / 5 else it / 5 })
            },
            label = "ci_step",
            modifier = Modifier.weight(1f)
        ) { s ->
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 8.dp)
            ) {
                when {
                    s == 0 -> QMood(mood) { mood = it }
                    s == 1 -> QWater(water, waterGoal) { water = it }
                    s == 2 -> QFood(food) { food = it }
                    s == 3 -> QSleep(sleepH, user?.sleepGoalHours ?: 8f) { sleepH = it }
                    s == 4 -> QRating(rating) { rating = it }
                    s == stepsQuestionIndex && needsManualSteps ->
                        QSteps(manualSteps, user?.stepGoal ?: 10_000) { manualSteps = it }
                    else -> QSummary(
                        mood = mood, water = water, waterGoal = waterGoal, food = food,
                        sleep = sleepH, rating = rating,
                        steps = if (needsManualSteps) manualSteps else hcStepsToday,
                        stepsEditable = needsManualSteps,
                        onEdit = { step = it }
                    )
                }
            }
        }

        // ── Bottom button ─────────────────────────────────────────────────
        Box(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp)) {
            if (step < total) {
                GlowButton(
                    text    = if (step == total - 1) "Finish ✨" else "Next",
                    onClick = { if (canProceed()) { haptics.tick(); step++ } },
                    accent  = accent,
                    enabled = canProceed()
                )
            } else {
                GlowButton(
                    text    = "Save check-in",
                    onClick = { haptics.confirm(); save() },
                    accent  = Green,
                    loading = isSaving
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// CHROME
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun GlassIconButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(Brush.verticalGradient(listOf(GlassFillTop, GlassFillBottom)))
            .border(1.dp, Brush.verticalGradient(listOf(GlassBorderTop, GlassBorderBottom)), CircleShape)
            .clickable(onClickLabel = description, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = description, tint = TextPrimary, modifier = Modifier.size(22.dp))
    }
}

/** One segment per question; the current one is part-filled. */
@Composable
private fun SegmentedProgress(current: Int, total: Int, accent: Color, modifier: Modifier = Modifier) {
    val color by animateColorAsState(accent, tween(500), label = "segColor")
    Row(modifier.height(6.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        repeat(total) { i ->
            val target = when {
                i < current  -> 1f
                i == current -> 0.35f
                else         -> 0f
            }
            val fill by animateFloatAsState(target, tween(450, easing = EaseOutCubic), label = "seg$i")
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color.White.copy(alpha = 0.10f))
            ) {
                Box(
                    Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(fill)
                        .clip(RoundedCornerShape(3.dp))
                        .background(color)
                )
            }
        }
    }
}

@Composable
private fun QHeader(n: Int, color: Color, title: String, subtitle: String? = null) {
    Column(Modifier.fillMaxWidth()) {
        Text("QUESTION $n", style = MaterialTheme.typography.labelSmall, color = color)
        Spacer(Modifier.height(8.dp))
        Text(title, style = MaterialTheme.typography.headlineLarge)
        if (subtitle != null) {
            Spacer(Modifier.height(6.dp))
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = TextMuted)
        }
    }
}

/** Glass circle button that dips when pressed. */
@Composable
private fun RoundGlassButton(
    icon: ImageVector,
    description: String,
    accent: Color,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.88f else 1f, spring(dampingRatio = 0.45f, stiffness = 700f), label = "rgbPress")
    Box(
        Modifier
            .size(66.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(CircleShape)
            .background(Brush.verticalGradient(listOf(GlassFillTop, GlassFillBottom)))
            .background(accent.copy(alpha = if (enabled) 0.14f else 0.03f))
            .border(1.dp, accent.copy(alpha = if (enabled) 0.45f else 0.12f), CircleShape)
            .clickable(
                interactionSource = interaction,
                indication = LocalIndication.current,
                enabled = enabled,
                role = Role.Button,
                onClickLabel = description,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = description, tint = if (enabled) accent else TextDim, modifier = Modifier.size(30.dp))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// QUESTIONS
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun QMood(selected: Int, onSelect: (Int) -> Unit) {
    val haptics = rememberHaptics()
    val current = MOODS.getOrNull(selected)
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        QHeader(1, current?.color ?: Green, "How are you feeling right now?")
        Spacer(Modifier.height(20.dp))

        // Big preview of the chosen face; pops in on every change. The glow
        // lives on the static box outside AnimatedContent and crossfades its
        // colour: inside, the fade-in renders through an offscreen layer the
        // size of the box, which cut the overhanging glow square mid-pop.
        val glowColor by animateColorAsState(current?.color ?: TextDim, tween(300), label = "moodGlow")
        val glowAlpha by animateFloatAsState(if (current != null) 0.5f else 0.12f, tween(300), label = "moodGlowA")
        Box(
            Modifier.size(160.dp).radialGlow(glowColor, alpha = glowAlpha),
            contentAlignment = Alignment.Center
        ) {
            AnimatedContent(
                targetState = selected,
                transitionSpec = {
                    (scaleIn(spring(dampingRatio = 0.45f, stiffness = 380f), initialScale = 0.4f) + fadeIn(tween(150))) togetherWith
                        (scaleOut(tween(150), targetScale = 0.6f) + fadeOut(tween(150)))
                },
                label = "moodBig"
            ) { sel ->
                val m = MOODS.getOrNull(sel)
                Text(
                    m?.emoji ?: "🙂",
                    fontSize = 88.sp,
                    modifier = Modifier.graphicsLayer { alpha = if (m != null) 1f else 0.3f }
                )
            }
        }
        Text(
            current?.label ?: "Tap the face that fits",
            style = MaterialTheme.typography.titleLarge,
            color = current?.color ?: TextMuted
        )
        Spacer(Modifier.height(28.dp))

        Row(Modifier.fillMaxWidth()) {
            MOODS.forEachIndexed { i, m ->
                val sel = i == selected
                val scale by animateFloatAsState(if (sel) 1.16f else 1f, spring(dampingRatio = 0.4f, stiffness = 500f), label = "mood$i")
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Box(
                        Modifier
                            .size(56.dp)
                            .graphicsLayer { scaleX = scale; scaleY = scale }
                            .then(if (sel) Modifier.circleGlow(m.color, 14.dp) { 0.6f } else Modifier)
                            .clip(CircleShape)
                            .background(
                                if (sel) Brush.linearGradient(listOf(m.color.copy(alpha = 0.5f), m.color.copy(alpha = 0.18f)))
                                else Brush.verticalGradient(listOf(GlassFillTop, GlassFillBottom))
                            )
                            .border(1.5.dp, if (sel) m.color else GlassBorderTop, CircleShape)
                            .clickable(role = Role.RadioButton) { haptics.confirm(); onSelect(i) }
                            .semantics { contentDescription = m.label; this.selected = sel },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(m.emoji, fontSize = 27.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun QWater(value: Int, goal: Int, onValue: (Int) -> Unit) {
    // Allow logging more than the goal — cap at goal+4 (or 12, whichever is larger).
    val maxValue = maxOf(goal + 4, 12)
    val haptics  = rememberHaptics()
    val scope    = rememberCoroutineScope()
    val splash   = remember { Animatable(0f) }
    val hit      = value >= goal

    fun bump() = scope.launch {
        splash.snapTo(1f)
        splash.animateTo(0f, tween(1300, easing = EaseOutCubic))
    }

    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        QHeader(2, Sky, "How much water today?")
        Spacer(Modifier.height(22.dp))
        Box(Modifier.radialGlow(if (hit) Green else Sky, alpha = 0.22f, scale = 1.2f)) {
            WaterGlass(
                fill = value.toFloat() / goal,
                splash = { splash.value },
                modifier = Modifier.size(width = 150.dp, height = 200.dp)
            )
        }
        Spacer(Modifier.height(18.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                value.toString(),
                style = MaterialTheme.typography.displayLarge,
                color = if (hit) Green else Sky
            )
            Text(
                " / $goal",
                style = MaterialTheme.typography.headlineSmall,
                color = TextMuted,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }
        Text("glasses", style = MaterialTheme.typography.bodySmall)
        // Fixed-height slot: the chip appearing must not push the +/- buttons
        // down between two taps.
        val chip by animateFloatAsState(if (hit) 1f else 0f, spring(dampingRatio = 0.4f, stiffness = 400f), label = "goalChip")
        Box(Modifier.height(44.dp), contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .graphicsLayer {
                        alpha = chip.coerceIn(0f, 1f)
                        scaleX = 0.4f + 0.6f * chip
                        scaleY = 0.4f + 0.6f * chip
                    }
                    .clip(RoundedCornerShape(100.dp))
                    .background(GreenDim)
                    .border(1.dp, Green.copy(alpha = 0.4f), RoundedCornerShape(100.dp))
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Text("🎉 Goal hit!", style = MaterialTheme.typography.labelLarge, color = Green)
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(36.dp), verticalAlignment = Alignment.CenterVertically) {
            RoundGlassButton(Icons.Rounded.Remove, "One glass less", TextPrimary, enabled = value > 0) {
                haptics.tick(); onValue(value - 1)
            }
            RoundGlassButton(Icons.Rounded.Add, "One more glass", Sky, enabled = value < maxValue) {
                if (value + 1 == goal) haptics.confirm() else haptics.tick()
                onValue(value + 1)
                bump()
            }
        }
        Spacer(Modifier.height(14.dp))
        Text("Your goal: $goal glasses per day", style = MaterialTheme.typography.bodySmall)
    }
}

/**
 * A glass that fills to [fill] (value / goal) with a two-layer animated
 * wave and rising bubbles. [splash] (0..1, read at draw time) kicks the
 * wave height up briefly after each added glass. Turns green past the goal.
 */
@Composable
private fun WaterGlass(fill: Float, splash: () -> Float, modifier: Modifier = Modifier) {
    val level by animateFloatAsState(fill.coerceIn(0f, 1f), spring(dampingRatio = 0.55f, stiffness = 55f), label = "waterLevel")
    val over = fill >= 1f
    val top by animateColorAsState(if (over) Color(0xFF5BF5C4) else Color(0xFF7FDBFF), tween(600), label = "liqTop")
    val bottom by animateColorAsState(if (over) Color(0xFF0DB888) else Color(0xFF2F7BFF), tween(600), label = "liqBottom")
    val phase = if (LocalReducedMotion.current) remember { mutableFloatStateOf(0.25f) } else
        rememberInfiniteTransition(label = "wave").animateFloat(
            0f, 1f, infiniteRepeatable(tween(2400, easing = LinearEasing)), label = "wavePhase"
        )
    val bubbles = remember {
        val rnd = Random(11)
        List(8) { floatArrayOf(0.12f + rnd.nextFloat() * 0.76f, rnd.nextFloat(), 0.5f + rnd.nextFloat() * 0.8f, 1.5f + rnd.nextFloat() * 2.5f) }
    }

    Canvas(modifier.semantics { contentDescription = "Water glass ${(fill * 100).toInt()} percent full" }) {
        val w = size.width
        val h = size.height
        val glass = Path().apply {
            addRoundRect(
                RoundRect(
                    rect = Rect(0f, 0f, w, h),
                    topLeft = CornerRadius(14.dp.toPx()),
                    topRight = CornerRadius(14.dp.toPx()),
                    bottomRight = CornerRadius(42.dp.toPx()),
                    bottomLeft = CornerRadius(42.dp.toPx())
                )
            )
        }
        drawPath(glass, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.08f), Color.White.copy(alpha = 0.03f))))

        clipPath(glass) {
            if (level > 0.003f) {
                val usable  = h * 0.9f
                val surface = h - usable * level
                val t       = phase.value * 2f * PI.toFloat()
                val amp     = (4.dp.toPx() + 12.dp.toPx() * splash()) * (if (level >= 0.999f) 0.6f else 1f)
                drawPath(wave(w, h, surface - 3.dp.toPx(), amp * 0.8f, t + 2f, w * 0.85f), top.copy(alpha = 0.4f))
                drawPath(
                    wave(w, h, surface, amp, t, w * 1.15f),
                    Brush.verticalGradient(listOf(top, bottom), startY = surface, endY = h)
                )
                // Bubbles drift up through the liquid only.
                val depth = h - surface
                bubbles.forEach { b ->
                    val y = h - ((phase.value * b[2] + b[1]) % 1f) * depth
                    if (y > surface + 6.dp.toPx()) {
                        drawCircle(Color.White.copy(alpha = 0.35f), b[3].dp.toPx(), Offset(w * b[0], y))
                    }
                }
            }
        }
        // Rim, outline and a specular stripe so it reads as glass.
        drawPath(glass, Color.White.copy(alpha = 0.30f), style = Stroke(2.dp.toPx()))
        drawLine(
            Color.White.copy(alpha = 0.22f),
            start = Offset(w * 0.13f, h * 0.10f),
            end = Offset(w * 0.13f, h * 0.62f),
            strokeWidth = 5.dp.toPx(),
            cap = StrokeCap.Round
        )
        // Quarter marks on the right edge.
        for (k in 1..3) {
            val y = h - h * 0.9f * k / 4f
            drawLine(Color.White.copy(alpha = 0.2f), Offset(w - 16.dp.toPx(), y), Offset(w - 6.dp.toPx(), y), 2.dp.toPx(), StrokeCap.Round)
        }
    }
}

private fun wave(w: Float, h: Float, y0: Float, amp: Float, t: Float, wavelength: Float): Path = Path().apply {
    moveTo(0f, h)
    lineTo(0f, y0)
    var x = 0f
    while (x <= w) {
        lineTo(x, y0 + amp * sin(2f * PI.toFloat() * x / wavelength + t))
        x += 6f
    }
    lineTo(w, y0 + amp * sin(2f * PI.toFloat() * w / wavelength + t))
    lineTo(w, h)
    close()
}

@Composable
private fun QFood(selected: String, onSelect: (String) -> Unit) {
    val haptics = rememberHaptics()
    Column(Modifier.fillMaxWidth()) {
        QHeader(3, Gold, "How was your nutrition?")
        Spacer(Modifier.height(24.dp))
        FOODS.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { f ->
                    val sel = selected == f.key
                    val scale by animateFloatAsState(if (sel) 1.03f else 1f, spring(dampingRatio = 0.45f, stiffness = 500f), label = "food${f.key}")
                    val emojiScale by animateFloatAsState(if (sel) 1.2f else 1f, spring(dampingRatio = 0.35f, stiffness = 420f), label = "foodE${f.key}")
                    GlassCard(
                        modifier = Modifier
                            .weight(1f)
                            .height(120.dp)
                            .graphicsLayer { scaleX = scale; scaleY = scale }
                            .semantics { role = Role.RadioButton; this.selected = sel },
                        shape = RoundedCornerShape(22.dp),
                        tint = if (sel) Gold else null,
                        borderBrush = if (sel) SolidColor(Gold) else null,
                        onClick = { haptics.confirm(); onSelect(f.key) }
                    ) {
                        Box(Modifier.fillMaxSize()) {
                            Column(
                                Modifier.align(Alignment.Center),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(f.emoji, fontSize = 36.sp, modifier = Modifier.graphicsLayer { scaleX = emojiScale; scaleY = emojiScale })
                                Spacer(Modifier.height(8.dp))
                                Text(f.label, style = MaterialTheme.typography.titleSmall, color = if (sel) Gold else TextMuted)
                            }
                            if (sel) {
                                Box(
                                    Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(10.dp)
                                        .size(22.dp)
                                        .clip(CircleShape)
                                        .background(Gold),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Rounded.Check, null, tint = Color(0xFF1A1300), modifier = Modifier.size(15.dp))
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
        AnimatedVisibility(visible = selected == "skip", enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
            GlassCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), tint = Coral) {
                Text(
                    "⚠️ Skipping meals can disrupt your energy and mood. Even a small snack helps.",
                    Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Coral
                )
            }
        }
    }
}

@Composable
private fun QSleep(value: Float, goal: Float, onValue: (Float) -> Unit) {
    val haptics = rememberHaptics()
    val quality = when {
        value >= 7f -> "Great 🌟" to Green
        value >= 5f -> "Fair ⚡" to Gold
        else        -> "Poor 😴" to Coral
    }
    Column(Modifier.fillMaxWidth()) {
        QHeader(4, Lavender, "How long did you sleep?")
        Spacer(Modifier.height(22.dp))
        NightSky(hours = value, modifier = Modifier.fillMaxWidth().height(200.dp)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("%.1f".format(value), style = MaterialTheme.typography.displayLarge, color = TextPrimary)
                    Text(" hrs", style = MaterialTheme.typography.headlineSmall, color = TextMuted, modifier = Modifier.padding(bottom = 8.dp))
                }
                Box(
                    Modifier
                        .clip(RoundedCornerShape(100.dp))
                        .background(quality.second.copy(alpha = 0.18f))
                        .padding(horizontal = 14.dp, vertical = 5.dp)
                ) {
                    Text(quality.first, style = MaterialTheme.typography.labelLarge, color = quality.second)
                }
            }
        }
        Spacer(Modifier.height(18.dp))
        Slider(
            value = value,
            onValueChange = { v -> if (v != value) haptics.tick(); onValue(v) },
            valueRange = 0f..12f,
            steps = 23,
            colors = SliderDefaults.colors(
                thumbColor = Lavender,
                activeTrackColor = Lavender,
                inactiveTrackColor = Color.White.copy(alpha = 0.10f),
                activeTickColor = Color.Transparent,
                inactiveTickColor = Color.Transparent
            )
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("0h", style = MaterialTheme.typography.bodySmall)
            Text("✓ ${"%.1f".format(goal).removeSuffix(".0")}h goal", style = MaterialTheme.typography.bodySmall, color = Green)
            Text("12h", style = MaterialTheme.typography.bodySmall)
        }
    }
}

/**
 * Night-sky card: stars appear as the hours go up, twinkling, under a
 * crescent moon that brightens with a fuller night. [content] sits centred.
 */
@Composable
private fun NightSky(hours: Float, modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val shape = RoundedCornerShape(26.dp)
    val shown by animateFloatAsState((hours / 12f).coerceIn(0f, 1f), tween(500), label = "starsShown")
    val twinkle = if (LocalReducedMotion.current) remember { mutableFloatStateOf(0f) } else
        rememberInfiniteTransition(label = "twinkle").animateFloat(
            0f, 1f, infiniteRepeatable(tween(3000, easing = LinearEasing)), label = "tw"
        )
    val stars = remember {
        val rnd = Random(7)
        List(46) { floatArrayOf(rnd.nextFloat(), rnd.nextFloat() * 0.92f, 0.8f + rnd.nextFloat() * 1.6f, rnd.nextFloat() * 6.28f) }
    }
    Box(
        modifier
            .clip(shape)
            .background(Brush.verticalGradient(listOf(Color(0xCC1A1744), Color(0xCC0B1026))))
            .border(1.dp, Brush.verticalGradient(listOf(Lavender.copy(alpha = 0.35f), GlassBorderBottom)), shape),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.matchParentSize()) {
            val w = size.width
            val h = size.height
            val t = twinkle.value * 2f * PI.toFloat()
            stars.forEachIndexed { i, s ->
                val appear = ((shown * stars.size) - i).coerceIn(0f, 1f)
                if (appear > 0f) {
                    val a = appear * (0.55f + 0.45f * sin(t + s[3]))
                    drawCircle(Color.White.copy(alpha = a.coerceIn(0f, 1f)), s[2].dp.toPx(), Offset(w * s[0], h * s[1]))
                }
            }
            // Crescent moon: a disc minus an offset disc.
            val r = 22.dp.toPx()
            val c = Offset(w - 44.dp.toPx(), 42.dp.toPx())
            val moonAlpha = 0.45f + 0.55f * shown
            drawCircle(
                Brush.radialGradient(listOf(Color(0x55FFF1C1), Color.Transparent), center = c, radius = r * 3f),
                radius = r * 3f, center = c, alpha = moonAlpha
            )
            val moon = Path().apply { addOval(Rect(c, r)) }
            val bite = Path().apply { addOval(Rect(Offset(c.x - r * 0.55f, c.y - r * 0.3f), r * 0.95f)) }
            drawPath(Path.combine(PathOperation.Difference, moon, bite), Color(0xFFFFF1C1), alpha = moonAlpha)
        }
        content()
    }
}

@Composable
private fun QRating(selected: Int, onSelect: (Int) -> Unit) {
    val haptics = rememberHaptics()
    val msgs = listOf(
        "", "Rough day. Tomorrow is a fresh start 🌱", "Tough but you showed up 💙",
        "Solid day! Small wins add up 💪", "Really good! Keep shining ✨", "Incredible day! 🌟"
    )
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        QHeader(5, Gold, "Rate your day so far", "Overall, how has today been?")
        Spacer(Modifier.height(44.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            (1..5).forEach { n ->
                val on = selected >= n
                val pop = remember { Animatable(1f) }
                LaunchedEffect(selected) {
                    if (on) {
                        delay((n - 1) * 60L)
                        pop.snapTo(0.55f)
                        pop.animateTo(1f, spring(dampingRatio = 0.35f, stiffness = 480f))
                    }
                }
                Box(
                    Modifier
                        .size(58.dp)
                        .then(if (on) Modifier.radialGlow(Gold, alpha = 0.35f) else Modifier)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            role = Role.RadioButton,
                            onClickLabel = "$n star${if (n > 1) "s" else ""}"
                        ) { haptics.confirm(); onSelect(n) },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (on) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                        contentDescription = "$n star${if (n > 1) "s" else ""}",
                        tint = if (on) Gold else TextDim,
                        modifier = Modifier
                            .size(50.dp)
                            .graphicsLayer { scaleX = pop.value; scaleY = pop.value }
                    )
                }
            }
        }
        Spacer(Modifier.height(32.dp))
        AnimatedContent(
            targetState = selected,
            transitionSpec = { (fadeIn(tween(250)) + slideInVertically { it / 3 }) togetherWith fadeOut(tween(150)) },
            label = "ratingMsg"
        ) { sel ->
            if (sel > 0) {
                GlassCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), tint = Gold) {
                    Text(
                        msgs[sel],
                        Modifier.padding(16.dp).fillMaxWidth(),
                        style = MaterialTheme.typography.bodyLarge,
                        color = Gold,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                Text("Tap a star", style = MaterialTheme.typography.bodyMedium, color = TextMuted)
            }
        }
    }
}

@Composable
private fun QSteps(value: Int, goal: Int, onValue: (Int) -> Unit) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        QHeader(
            6, Green, "How many steps today?",
            "We couldn't read your step count automatically. Enter it manually, or leave it at 0."
        )
        Spacer(Modifier.height(28.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text("%,d".format(value), style = MaterialTheme.typography.displayLarge, color = Green)
            Text(" steps", style = MaterialTheme.typography.headlineSmall, color = TextMuted, modifier = Modifier.padding(bottom = 8.dp))
        }
        Spacer(Modifier.height(14.dp))
        LinearProgressIndicator(
            progress = { (value.toFloat() / goal.coerceAtLeast(1)).coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
            color = Green,
            trackColor = Color.White.copy(alpha = 0.10f)
        )
        Spacer(Modifier.height(20.dp))
        OutlinedTextField(
            value = if (value == 0) "" else value.toString(),
            onValueChange = { txt ->
                val digits = txt.filter { it.isDigit() }.take(6)
                onValue(digits.toIntOrNull() ?: 0)
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            placeholder = { Text("0") },
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Green,
                unfocusedBorderColor = GlassBorderTop,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary,
                focusedContainerColor = GlassFillTop,
                unfocusedContainerColor = GlassFillBottom,
                cursorColor = Green
            )
        )
        Spacer(Modifier.height(10.dp))
        Text("Goal: %,d steps".format(goal), style = MaterialTheme.typography.bodySmall, color = TextDim)
    }
}

/** Review before saving. Tapping a tile jumps back to that question. */
@Composable
private fun QSummary(
    mood: Int, water: Int, waterGoal: Int, food: String, sleep: Float, rating: Int,
    steps: Int, stepsEditable: Boolean,
    onEdit: (Int) -> Unit
) {
    val m = MOODS.getOrNull(mood)
    val f = FOODS.firstOrNull { it.key == food }
    val tiles = listOf(
        SummaryTile(m?.emoji ?: "😊", "Mood", m?.label ?: "—", m?.color ?: Green, 0),
        SummaryTile("💧", "Water", "$water / $waterGoal", Sky, 1),
        SummaryTile(f?.emoji ?: "🍽️", "Food", f?.label ?: "—", Gold, 2),
        SummaryTile("🌙", "Sleep", "%.1fh".format(sleep), Lavender, 3),
        SummaryTile("⭐", "Day", if (rating > 0) "$rating / 5" else "—", Gold, 4),
        SummaryTile("🚶", "Steps", if (steps > 0) "%,d".format(steps) else "—", Green, if (stepsEditable) 5 else null)
    )
    Column(Modifier.fillMaxWidth()) {
        Text("ALMOST DONE", style = MaterialTheme.typography.labelSmall, color = Teal)
        Spacer(Modifier.height(8.dp))
        Text("Here's your day", style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(6.dp))
        Text("Tap anything to change it.", style = MaterialTheme.typography.bodyMedium, color = TextMuted)
        Spacer(Modifier.height(22.dp))
        tiles.chunked(2).forEachIndexed { r, row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEachIndexed { c, t ->
                    GlassCard(
                        modifier = Modifier.weight(1f).staggeredEnter(r * 2 + c),
                        shape = RoundedCornerShape(20.dp),
                        tint = t.color,
                        onClick = t.editStep?.let { s -> { onEdit(s) } }
                    ) {
                        Row(
                            Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(t.emoji, fontSize = 24.sp)
                            Column(Modifier.weight(1f)) {
                                Text(t.label.uppercase(), style = MaterialTheme.typography.labelSmall, color = TextMuted)
                                Text(
                                    t.value,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = t.color,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

private data class SummaryTile(
    val emoji: String,
    val label: String,
    val value: String,
    val color: Color,
    val editStep: Int?
)

// ─────────────────────────────────────────────────────────────────────────────
// COOL-DOWN SCREEN
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun CooldownScreen(msRemaining: Long, progress: Float, onBack: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .systemBarsPadding()
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
            GlassIconButton(Icons.Rounded.Close, "Close", onBack)
        }

        // Centred when it fits, scrollable when it doesn't (large fonts). The
        // scroll column spans the whole viewport so its clip edge can't cut
        // through the ring's glow.
        BoxWithConstraints(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .heightIn(min = maxHeight)
                .padding(horizontal = 28.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(Modifier.size(230.dp).radialGlow(Lavender, alpha = 0.22f), contentAlignment = Alignment.Center) {
                CountdownRing(progress, Lavender, Modifier.size(210.dp), strokeWidth = 10.dp) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("NEXT CHECK-IN", style = MaterialTheme.typography.labelSmall, color = Lavender)
                        Spacer(Modifier.height(4.dp))
                        // Countdown is 8 chars max ("99:59:59") — one line,
                        // capped growth so 200% font scale stays inside the ring.
                        Text(
                            formatCountdown(msRemaining),
                            style = MaterialTheme.typography.displayLarge.copy(fontSize = 36.sp.capped(1.15f)),
                            color = TextPrimary,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
            Spacer(Modifier.height(28.dp))
            Text(
                "You're checked in ✓",
                style = MaterialTheme.typography.headlineMedium,
                color = TextPrimary,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "Daily check-ins reset at 6 PM. We use today's data to update your stats and streak in the meantime.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextMuted,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(28.dp))
            GlowButton("Back to dashboard", onBack, accent = Lavender)
        }
        }
    }
}

private fun formatCountdown(ms: Long): String {
    if (ms <= 0) return "00:00:00"
    val total = ms / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return "%02d:%02d:%02d".format(h, m, s)
}
