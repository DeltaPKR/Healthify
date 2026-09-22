@file:OptIn(ExperimentalTextApi::class)

package com.healthify.app.ui.checkin

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.healthify.app.score.HealthScore
import com.healthify.app.streak.StreakManager
import com.healthify.app.ui.dashboard.ActivityRings
import com.healthify.app.ui.dashboard.RingSpec
import com.healthify.app.ui.theme.*
import kotlinx.coroutines.delay

/** Everything the post-check-in reward screen shows. */
data class CelebrationData(
    val score: Int,
    val parts: List<HealthScore.Part>,
    val streak: Int,
    /** Streak before this check-in — the counter flips from here to [streak]. */
    val previousStreak: Int,
    val isNewBest: Boolean,
    val isHealthyDay: Boolean,
    val isMilestone: Boolean
)

private val PartColors = mapOf(
    "Water" to Sky, "Steps" to Green, "Sleep" to Lavender,
    "Mood" to Gold, "Food" to Amber, "Day" to Rose
)

/**
 * The reward moment after saving a check-in. Beats, in order: title,
 * the score ring sweeping up (confetti as it lands), the per-part
 * breakdown, the streak ticking over, then the Done button.
 */
@Composable
fun CelebrationScreen(data: CelebrationData, onDone: () -> Unit) {
    val haptics = rememberHaptics()
    val reduced = LocalReducedMotion.current
    var stage by remember { mutableIntStateOf(if (reduced) 5 else 0) }
    var confetti by remember { mutableIntStateOf(0) }
    // A record run makes every day a "new best", so only milestones get
    // the headline and the heavy confetti; a record shows as a badge.
    val big = data.isMilestone

    LaunchedEffect(Unit) {
        if (reduced) return@LaunchedEffect
        haptics.confirm()
        stage = 1                    // title + ring start
        delay(1450)
        confetti = 1                 // ring lands
        haptics.heavy()
        stage = 2                    // breakdown
        delay(900)
        stage = 3                    // streak card
        delay(700)
        stage = 4                    // streak ticks over
        delay(500)
        stage = 5                    // button
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(28.dp))
            Text(
                when {
                    data.isMilestone -> "${data.streak}-day milestone!"
                    else             -> "Check-in complete!"
                },
                style = MaterialTheme.typography.headlineLarge.copy(brush = BrandGradient),
                textAlign = TextAlign.Center,
                modifier = Modifier.reveal(stage >= 1)
            )
            Spacer(Modifier.height(6.dp))
            Text(
                verdict(data.score),
                style = MaterialTheme.typography.bodyLarge,
                color = TextMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier.reveal(stage >= 1, delayMs = 120)
            )
            Spacer(Modifier.height(22.dp))

            // Score ring: composed at stage 1 so its own sweep + count-up
            // start then. A spacer holds the space before that.
            Box(Modifier.size(224.dp), contentAlignment = Alignment.Center) {
                if (stage >= 1) {
                    val (start, end) = scoreColors(data.score)
                    Box(Modifier.radialGlow(end, alpha = 0.3f, scale = 1.15f)) {
                        ActivityRings(
                            rings = listOf(RingSpec(data.score / 100f, start, end)),
                            score = data.score,
                            description = "Wellness score ${data.score} out of 100",
                            diameter = 210.dp,
                            stroke = 20.dp
                        )
                    }
                }
            }
            Spacer(Modifier.height(20.dp))

            Breakdown(data.parts, shown = stage >= 2)

            Spacer(Modifier.height(16.dp))
            StreakCard(data, shown = stage >= 3, ticked = stage >= 4)

            if (data.isHealthyDay) {
                Spacer(Modifier.height(12.dp))
                Box(
                    Modifier
                        .reveal(stage >= 4, delayMs = 200)
                        .clip(RoundedCornerShape(100.dp))
                        .background(GreenDim)
                        .border(1.dp, Green.copy(alpha = 0.4f), RoundedCornerShape(100.dp))
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text("✨ Healthy day — every daily target met", style = MaterialTheme.typography.labelLarge, color = Green)
                }
            }

            Spacer(Modifier.height(28.dp))
            GlowButton(
                "Done",
                onClick = { haptics.tick(); onDone() },
                accent = Green,
                modifier = Modifier.reveal(stage >= 5)
            )
            Spacer(Modifier.height(24.dp))
        }

        ConfettiBurst(
            trigger = confetti,
            origin = Offset(0.5f, 0.3f),
            intensity = if (big) 2.4f else 1.2f
        )
    }
}

private fun verdict(score: Int): String = when {
    score >= 90 -> "An outstanding day ✨"
    score >= 75 -> "A great day — keep it rolling 💪"
    score >= 55 -> "Solid. Small wins add up 🌱"
    else        -> "Every check-in counts 💙"
}

private fun scoreColors(score: Int): Pair<Color, Color> = when {
    score >= 75 -> Color(0xFF0DB888) to Color(0xFF5BF5C4)
    score >= 55 -> Color(0xFF2F8BFF) to Color(0xFF6FD6FF)
    else        -> Color(0xFF7C5CF5) to Color(0xFFC4B5FD)
}

/** Fade + rise once [shown] turns true. */
@Composable
private fun Modifier.reveal(shown: Boolean, delayMs: Int = 0): Modifier {
    val p by animateFloatAsState(
        if (shown) 1f else 0f,
        tween(520, delayMillis = if (shown) delayMs else 0, easing = EaseOutCubic),
        label = "reveal"
    )
    val rise = with(LocalDensity.current) { 22.dp.toPx() }
    return graphicsLayer {
        alpha = p
        translationY = (1f - p) * rise
        // No offscreen buffer, so the Done button's glow isn't cut square
        // while it fades in.
        compositingStrategy = CompositingStrategy.ModulateAlpha
    }
}

/** Six chips — what each part of the day earned — plus the biggest gap. */
@Composable
private fun Breakdown(parts: List<HealthScore.Part>, shown: Boolean) {
    Column(Modifier.fillMaxWidth()) {
        parts.chunked(3).forEachIndexed { r, row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEachIndexed { c, p ->
                    val color = PartColors[p.label] ?: Green
                    val fill by animateFloatAsState(
                        if (shown) p.points.toFloat() / p.max else 0f,
                        tween(700, delayMillis = 200 + (r * 3 + c) * 90, easing = EaseOutCubic),
                        label = "part${p.label}"
                    )
                    GlassCard(
                        Modifier
                            .weight(1f)
                            .reveal(shown, delayMs = (r * 3 + c) * 90),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(p.emoji, fontSize = 14.sp)
                                Spacer(Modifier.width(5.dp))
                                Text(p.label, style = MaterialTheme.typography.labelMedium, color = TextMuted, maxLines = 1)
                            }
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text(
                                    "+${p.points}",
                                    style = MaterialTheme.typography.titleLarge.copy(fontFeatureSettings = TABULAR),
                                    color = color
                                )
                                Text(
                                    "/${p.max}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = TextDim,
                                    modifier = Modifier.padding(start = 2.dp, bottom = 3.dp)
                                )
                            }
                            Spacer(Modifier.height(6.dp))
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .height(4.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(Color.White.copy(alpha = 0.08f))
                            ) {
                                Box(
                                    Modifier
                                        .fillMaxHeight()
                                        .fillMaxWidth(fill)
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(color)
                                )
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
        }
        // Where tomorrow's easiest points are.
        parts.maxByOrNull { it.max - it.points }?.takeIf { it.max - it.points >= 4 }?.let { gap ->
            Text(
                "💡 Most room to grow: ${gap.label.lowercase()} — up to +${gap.max - gap.points} more",
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .reveal(shown, delayMs = 650)
            )
        }
    }
}

@Composable
private fun StreakCard(data: CelebrationData, shown: Boolean, ticked: Boolean) {
    val accent = if (data.streak >= 7) Gold else Amber
    // Flip only on a genuine +1; a reset or same-day re-save just shows the value.
    val flips = data.streak == data.previousStreak + 1
    val showing = if (flips && !ticked) data.previousStreak else data.streak
    val haptics = rememberHaptics()
    LaunchedEffect(ticked) { if (ticked && flips) haptics.confirm() }

    GlassCard(
        Modifier
            .fillMaxWidth()
            .reveal(shown),
        tint = accent
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            FlameBadge(StreakManager.badge(data.streak), accent, lively = true)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    AnimatedContent(
                        targetState = showing,
                        transitionSpec = {
                            (slideInVertically(spring(dampingRatio = 0.5f, stiffness = 380f)) { it } + fadeIn()) togetherWith
                                (slideOutVertically(tween(220)) { -it } + fadeOut(tween(220)))
                        },
                        label = "streakFlip"
                    ) { n ->
                        Text(
                            n.toString(),
                            style = MaterialTheme.typography.headlineLarge.copy(fontFeatureSettings = TABULAR),
                            color = accent
                        )
                    }
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "day streak",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
                Text(StreakManager.message(data.streak), style = MaterialTheme.typography.bodySmall, color = TextMuted)
            }
            if (data.isNewBest) {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(100.dp))
                        .background(Gold.copy(alpha = 0.18f))
                        .border(1.dp, Gold.copy(alpha = 0.5f), RoundedCornerShape(100.dp))
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text("BEST", style = MaterialTheme.typography.labelSmall, color = Gold)
                }
            }
        }
    }
}
