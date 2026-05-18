package com.healthify.app.ui.checkin

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.healthify.app.data.db.CheckInEntity
import com.healthify.app.data.repository.AppRepository
import com.healthify.app.firebase.FirebaseSync
import com.healthify.app.health.HealthConnectManager
import com.healthify.app.notifications.NotificationScheduler
import com.healthify.app.streak.StreakManager
import com.healthify.app.ui.theme.*
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun CheckInScreen(
    repo: AppRepository,
    healthConnect: HealthConnectManager,
    onComplete: () -> Unit,
    onBack: () -> Unit
) {
    val scope   = rememberCoroutineScope()
    var step    by remember { mutableStateOf(0) }

    // answers
    var mood      by remember { mutableStateOf(-1) }
    var water     by remember { mutableStateOf(4) }
    var food      by remember { mutableStateOf("") }
    var sleepH    by remember { mutableStateOf(7f) }
    var rating    by remember { mutableStateOf(0) }
    var manualSteps by remember { mutableStateOf(0) }
    var isSaving  by remember { mutableStateOf(false) }
    var streakResult by remember { mutableStateOf<com.healthify.app.streak.StreakResult?>(null) }

    // Probe Health Connect once: if it can't give us steps (unavailable, no
    // permission, or no source writing data), we ask the user manually.
    var hcStepsToday    by remember { mutableStateOf(0) }
    var needsManualSteps by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        val data = healthConnect.readAll()
        hcStepsToday    = data.stepsToday
        needsManualSteps = !data.isAvailable || data.stepsToday == 0
    }

    // Pull the user's personal water goal so the progress bar in QWater
    // tracks what they signed up for, not a hard-coded "12".
    var waterGoal by remember { mutableStateOf(8) }
    LaunchedEffect(Unit) {
        waterGoal = repo.getUserOnce()?.waterGoalGlasses?.coerceAtLeast(1) ?: 8
    }

    val total = if (needsManualSteps) 6 else 5
    val stepsQuestionIndex = 5  // only used when needsManualSteps == true

    // ── 24-hour cool-down state ────────────────────────────────────────────
    var loadedCooldown by remember { mutableStateOf(false) }
    var canCheckIn     by remember { mutableStateOf(true) }
    var msRemaining    by remember { mutableStateOf(0L) }

    LaunchedEffect(Unit) {
        val (can, ms) = repo.checkInCooldownStatus()
        canCheckIn  = can
        msRemaining = ms
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
            // Use HC steps when available; otherwise the user's manual entry.
            val effectiveSteps = if (healthData.isAvailable && healthData.stepsToday > 0)
                healthData.stepsToday else manualSteps
            val today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
            val score = minOf(100,
                (if (mood >= 0) (mood + 1) * 4 else 0) +
                        minOf(20, water * 2) +
                        minOf(15, (sleepH / 8 * 15).toInt()) +
                        minOf(15, (effectiveSteps.toFloat() / 10_000 * 15).toInt()) +
                        rating * 4
            )
            val ci = CheckInEntity(
                date          = today,
                moodScore     = mood,
                waterGlasses  = water,
                foodQuality   = food,
                sleepHours    = sleepH,
                dayRating     = rating,
                steps         = effectiveSteps,
                heartRateAvg  = healthData.heartRateAvg,
                wellnessScore = score,
                timestamp     = System.currentTimeMillis()
            )

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
            streakResult = sr
            isSaving = false
            // Jump straight back to the dashboard. ON_RESUME on the dashboard
            // re-runs viewModel.load(), so the home page picks up the new
            // check-in (score, water, sleep, streak) immediately.
            onComplete()
        }
    }

    // ── Locked state: already checked in within the last 24h ───────────────
    if (loadedCooldown && !canCheckIn) {
        CooldownScreen(msRemaining = msRemaining, onBack = onBack)
        return
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Brush.radialGradient(listOf(SurfaceCard2, BgDark)))
    ) {
        // ── Top bar ─────────────────────────────────────────────────────
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.Close, null, tint = TextMuted)
            }
            LinearProgressIndicator(
                progress = { if (step > 0) step.toFloat() / total else 0f },
                modifier = Modifier.weight(1f).height(5.dp).clip(RoundedCornerShape(3.dp)),
                color       = Green,
                trackColor  = Divider
            )
            Text("$step/$total", style = MaterialTheme.typography.bodySmall)
        }

        // ── Question ─────────────────────────────────────────────────────
        AnimatedContent(
            targetState = step,
            transitionSpec = {
                fadeIn() + slideInHorizontally { it / 3 } togetherWith fadeOut() + slideOutHorizontally { -it / 3 }
            },
            label = "ci_step",
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())
        ) { s ->
            Box(Modifier.padding(horizontal = 24.dp, vertical = 8.dp).fillMaxSize()) {
                when {
                    s == 0 -> QMood(mood)    { mood = it }
                    s == 1 -> QWater(water, waterGoal) { water = it }
                    s == 2 -> QFood(food)    { food = it }
                    s == 3 -> QSleep(sleepH) { sleepH = it }
                    s == 4 -> QRating(rating){ rating = it }
                    s == stepsQuestionIndex && needsManualSteps ->
                        QSteps(manualSteps) { manualSteps = it }
                    else -> QSummary(
                        mood = mood, water = water, food = food,
                        sleep = sleepH, rating = rating,
                        steps = if (needsManualSteps) manualSteps else hcStepsToday,
                        streakResult = streakResult
                    )
                }
            }
        }

        // ── Bottom button ─────────────────────────────────────────────────
        Box(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 24.dp)) {
            if (step < total) {
                Button(
                    onClick = { if (canProceed()) step++ },
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    enabled = canProceed(),
                    colors = ButtonDefaults.buttonColors(containerColor = Green,
                        disabledContainerColor = Green.copy(alpha = 0.3f)),
                    shape  = RoundedCornerShape(16.dp)
                ) { Text(if (step == total - 1) "Finish ✨" else "Next →",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimary) }
            } else {
                if (isSaving) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                        CircularProgressIndicator(color = Green)
                    }
                } else {
                    Button(
                        onClick = { save() },
                        modifier = Modifier.fillMaxWidth().height(54.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Green),
                        shape  = RoundedCornerShape(16.dp)
                    ) { Text("Save check-in 🌟",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimary) }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 24-HOUR COOL-DOWN SCREEN
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun CooldownScreen(msRemaining: Long, onBack: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .background(Brush.radialGradient(listOf(SurfaceCard2, BgDark)))
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.Close, null, tint = TextMuted)
            }
        }

        Column(
            Modifier.fillMaxSize().padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("⏳", fontSize = 64.sp)
            Spacer(Modifier.height(18.dp))
            Text(
                "Already checked in",
                style = MaterialTheme.typography.headlineLarge,
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
            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = LavenderDim),
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, Color(0x40A78BFA))
            ) {
                Column(
                    Modifier.padding(20.dp).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("NEXT CHECK-IN",
                        style = MaterialTheme.typography.labelSmall, color = Lavender)
                    Spacer(Modifier.height(6.dp))
                    // Countdown is 8 chars max ("99:59:59") — clamp to one
                    // line and disable soft-wrap so a 200% font scale shows
                    // it on a single line (ellipsised if it truly can't fit)
                    // rather than wrapping into two cramped lines.
                    Text(
                        formatCountdown(msRemaining),
                        style = MaterialTheme.typography.displayLarge.copy(fontSize = 44.sp),
                        color = Lavender,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(Modifier.height(28.dp))
            Button(
                onClick = onBack,
                modifier = Modifier.fillMaxWidth().height(54.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Green),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text("Back to dashboard",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimary)
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

// ── Question composables ──────────────────────────────────────────────────────

@Composable
private fun CILabel(n: Int, color: Color) {
    Text("QUESTION $n", style = MaterialTheme.typography.labelSmall, color = color)
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun QMood(selected: Int, onSelect: (Int) -> Unit) {
    val moods = listOf("😢" to "Terrible", "😕" to "Not great",
        "😐" to "Okay", "🙂" to "Good", "😄" to "Amazing")
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        CILabel(1, Green)
        Text("How are you feeling\nright now?", style = MaterialTheme.typography.headlineLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            moods.forEachIndexed { i, (em, lbl) ->
                val sel = selected == i
                Card(
                    onClick = { onSelect(i) },
                    modifier = Modifier.weight(1f),
                    colors   = CardDefaults.cardColors(containerColor = if (sel) GreenDim else SurfaceCard),
                    shape    = RoundedCornerShape(14.dp),
                    border   = BorderStroke(1.5.dp, if (sel) Green else Divider)
                ) {
                    Column(
                        Modifier.padding(vertical = 14.dp, horizontal = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(em, fontSize = if (sel) 28.sp else 22.sp)
                        Text(lbl, style = MaterialTheme.typography.bodySmall,
                            color = if (sel) Green else TextDim, textAlign = TextAlign.Center,
                            maxLines = 1)
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
    Column(verticalArrangement = Arrangement.spacedBy(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        CILabel(2, Sky)
        Text("How much water today?", style = MaterialTheme.typography.headlineLarge)
        Box(
            Modifier.size(128.dp).clip(RoundedCornerShape(64.dp))
                .border(3.dp, Sky, RoundedCornerShape(64.dp))
                .background(SkyDim),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("💧", fontSize = 28.sp)
                Text("$value/$goal", style = MaterialTheme.typography.headlineLarge, color = Sky)
                Text("glasses", style = MaterialTheme.typography.bodySmall)
            }
        }
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(onClick = { if (value > 0) onValue(value - 1) },
                modifier = Modifier.size(52.dp), shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, Divider), contentPadding = PaddingValues(0.dp)
            ) { Text("−", fontSize = 22.sp, color = TextPrimary) }
            LinearProgressIndicator(
                progress = { (value.toFloat() / goal).coerceIn(0f, 1f) },
                modifier = Modifier.weight(1f).height(5.dp).clip(RoundedCornerShape(3.dp)),
                color = Sky, trackColor = Divider
            )
            OutlinedButton(onClick = { if (value < maxValue) onValue(value + 1) },
                modifier = Modifier.size(52.dp), shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, Divider), contentPadding = PaddingValues(0.dp)
            ) { Text("+", fontSize = 22.sp, color = TextPrimary) }
        }
        Text("Your goal: $goal glasses per day", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun QFood(selected: String, onSelect: (String) -> Unit) {
    val opts = listOf("well" to ("🥗" to "Ate well"), "ok" to ("🍽️" to "Decent"),
        "poor" to ("🍕" to "Not great"), "skip" to ("😕" to "Skipped"))
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        CILabel(3, Gold)
        Text("How was your nutrition?", style = MaterialTheme.typography.headlineLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            opts.forEach { (v, p) ->
                val (em, lbl) = p; val sel = selected == v
                Card(
                    onClick  = { onSelect(v) },
                    modifier = Modifier.weight(1f),
                    colors   = CardDefaults.cardColors(containerColor = if (sel) GoldDim else SurfaceCard),
                    shape    = RoundedCornerShape(14.dp),
                    border   = BorderStroke(1.5.dp, if (sel) Gold else Divider)
                ) {
                    Column(
                        Modifier.padding(vertical = 14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(em, fontSize = 24.sp)
                        Text(lbl, style = MaterialTheme.typography.bodySmall,
                            color = if (sel) Gold else TextDim, textAlign = TextAlign.Center)
                    }
                }
            }
        }
        if (selected == "skip") {
            Card(colors = CardDefaults.cardColors(containerColor = CoralDim),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, Color(0x30FF7B6E))) {
                Text("⚠️ Skipping meals can disrupt your energy and mood. Even a small snack helps.",
                    Modifier.padding(14.dp), style = MaterialTheme.typography.bodyMedium, color = Coral)
            }
        }
    }
}

@Composable
private fun QSleep(value: Float, onValue: (Float) -> Unit) {
    val quality = when { value >= 7f -> "Great 🌟" to Green; value >= 5f -> "Fair ⚡" to Gold; else -> "Poor 😴" to Coral }
    Column(verticalArrangement = Arrangement.spacedBy(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        CILabel(4, Lavender)
        Text("How long did you sleep?", style = MaterialTheme.typography.headlineLarge)
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("%.1f".format(value), style = MaterialTheme.typography.displayLarge, color = Lavender)
            Text("hrs", style = MaterialTheme.typography.headlineSmall, color = TextMuted)
        }
        Box(Modifier.clip(RoundedCornerShape(100.dp)).background(
            if (value >= 7f) GreenDim else if (value >= 5f) GoldDim else CoralDim
        ).padding(horizontal = 14.dp, vertical = 5.dp)) {
            Text(quality.first, style = MaterialTheme.typography.bodySmall, color = quality.second)
        }
        Slider(value = value, onValueChange = onValue, valueRange = 0f..12f, steps = 23,
            colors = SliderDefaults.colors(thumbColor = Lavender, activeTrackColor = Lavender))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("0h", style = MaterialTheme.typography.bodySmall)
            Text("✓ 8h ideal", style = MaterialTheme.typography.bodySmall, color = Green)
            Text("12h", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun QRating(selected: Int, onSelect: (Int) -> Unit) {
    val msgs = listOf("","Rough day. Tomorrow is a fresh start 🌱","Tough but you showed up 💙",
        "Solid day! Small wins add up 💪","Really good! Keep shining ✨","Incredible day! 🌟")
    Column(verticalArrangement = Arrangement.spacedBy(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        CILabel(5, Gold)
        Text("Rate your day so far", style = MaterialTheme.typography.headlineLarge)
        Text("Overall, how has today been?", style = MaterialTheme.typography.bodyMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            (1..5).forEach { n ->
                val sel = selected >= n
                Text("⭐", fontSize = if (sel) 38.sp else 28.sp,
                    modifier = Modifier.clickable { onSelect(n) })
            }
        }
        if (selected > 0) {
            Card(colors = CardDefaults.cardColors(containerColor = GoldDim),
                shape = RoundedCornerShape(14.dp)) {
                Text(msgs[selected], Modifier.padding(14.dp).fillMaxWidth(),
                    style = MaterialTheme.typography.bodyMedium, color = Gold,
                    textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
private fun QSteps(value: Int, onValue: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        CILabel(6, Green)
        Text("How many steps today?", style = MaterialTheme.typography.headlineLarge)
        Text(
            "We couldn't read your step count automatically. Enter it manually, or leave it at 0.",
            style = MaterialTheme.typography.bodyMedium, color = TextMuted, textAlign = TextAlign.Center
        )
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("%,d".format(value), style = MaterialTheme.typography.displayLarge, color = Green)
            Text("steps", style = MaterialTheme.typography.headlineSmall, color = TextMuted)
        }
        OutlinedTextField(
            value = if (value == 0) "" else value.toString(),
            onValueChange = { txt ->
                val digits = txt.filter { it.isDigit() }.take(6)
                onValue(digits.toIntOrNull() ?: 0)
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            placeholder = { Text("0") },
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Green,
                unfocusedBorderColor = Divider,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary,
                cursorColor = Green
            )
        )
        Text("Goal: 10,000 steps", style = MaterialTheme.typography.bodySmall, color = TextDim)
    }
}

@Composable
private fun QSummary(
    mood: Int, water: Int, food: String, sleep: Float, rating: Int,
    steps: Int,
    streakResult: com.healthify.app.streak.StreakResult?
) {
    val score = minOf(100, (if (mood >= 0) (mood + 1) * 4 else 0) +
            minOf(20, water * 2) + minOf(15, (sleep / 8 * 15).toInt()) +
            minOf(15, (steps.toFloat() / 10_000 * 15).toInt()) + rating * 4)

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(if (score >= 70) "🌟" else if (score >= 50) "💪" else "🌱",
            fontSize = 52.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        Text("Check-in complete!", style = MaterialTheme.typography.headlineLarge,
            modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)

        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF0B2520)),
            shape = RoundedCornerShape(18.dp),
            border = BorderStroke(1.dp, Color(0x301AD9A0))) {
            Column(Modifier.padding(20.dp).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally) {
                Text(score.toString(),
                    style = MaterialTheme.typography.displayLarge, color = Green)
                Text("WELLNESS SCORE", style = MaterialTheme.typography.labelSmall, color = TextMuted)
            }
        }

        streakResult?.let { sr ->
            if (sr.current > 0) {
                Card(colors = CardDefaults.cardColors(
                    containerColor = if (sr.current >= 7) Color(0xFF2D1A00) else Color(0xFF1A2D00)),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, if (sr.current >= 7) Color(0x40FFD166) else Color(0x401AD9A0))
                ) {
                    Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Text(StreakManager.badge(sr.current), fontSize = 28.sp)
                        Column(Modifier.weight(1f)) {
                            Text(StreakManager.message(sr.current),
                                color = if (sr.current >= 7) Gold else Green,
                                style = MaterialTheme.typography.titleMedium)
                            Text("Longest: ${sr.longest} days",
                                style = MaterialTheme.typography.bodySmall)
                        }
                        Text("${sr.current}🔥",
                            style = MaterialTheme.typography.headlineMedium,
                            color = if (sr.current >= 7) Gold else Green)
                    }
                }
            }
        }

        listOf(
            Triple("😊", "Mood", if (mood >= 0) listOf("Terrible","Not great","Okay","Good","Amazing")[mood] else "—"),
            Triple("💧", "Hydration", "$water glasses"),
            Triple("🌙", "Sleep", "%.1fh".format(sleep)),
            Triple("🚶", "Steps", if (steps > 0) "%,d".format(steps) else "—"),
            Triple("⭐", "Day rating", if (rating > 0) "$rating/5" else "—"),
        ).forEach { (icon, label, value) ->
            Card(colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                shape = RoundedCornerShape(13.dp)) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(icon, fontSize = 18.sp)
                    Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Text(value, style = MaterialTheme.typography.titleMedium, color = Green)
                }
            }
        }
    }
}
