package com.healthify.app.ui.dashboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.healthify.app.ui.theme.*
import kotlin.math.cos
import kotlin.math.sin

/** One ring: [progress] is value / goal (may exceed 1). */
data class RingSpec(
    val progress: Float,
    val start: Color,
    val end: Color
)

/**
 * Concentric "close your rings" gauge. Outermost ring first in [rings].
 * Each ring sweeps in from empty on first show (staggered), carries a bright
 * leading tip, and glows once its goal is met. [score] counts up in the
 * middle. The whole gauge is a single TalkBack node described by
 * [description].
 */
@Composable
fun ActivityRings(
    rings: List<RingSpec>,
    score: Int,
    description: String,
    modifier: Modifier = Modifier,
    diameter: Dp = 216.dp,
    stroke: Dp = 17.dp,
    gap: Dp = 5.dp
) {
    val sweeps = rings.mapIndexed { i, r ->
        rememberSweep(r.progress.coerceIn(0f, 1f), durationMs = 1400, delayMs = 250L + i * 140L)
    }
    val shown by rememberCountUp(score, durationMs = 1500, delayMs = 250L)
    val breath by rememberBreath(2600, "ringGlow")

    Box(
        modifier
            .size(diameter)
            .semantics(mergeDescendants = true) { contentDescription = description },
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val strokePx = stroke.toPx()
            val gapPx = gap.toPx()
            rings.forEachIndexed { i, ring ->
                val radius = size.minDimension / 2f - strokePx / 2f - i * (strokePx + gapPx)
                val frac = sweeps[i].value
                val complete = ring.progress >= 1f && frac >= 0.999f
                drawRing(ring, radius, strokePx, frac, complete, breath)
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                shown.toString(),
                style = MaterialTheme.typography.displayLarge.copy(fontSize = 40.sp.capped(1.15f), lineHeight = 42.sp.capped(1.15f)),
                color = TextPrimary,
                maxLines = 1,
                softWrap = false
            )
            Text(
                "SCORE",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp.capped(1.15f)),
                color = TextMuted,
                textAlign = TextAlign.Center
            )
        }
    }
}

private fun DrawScope.drawRing(
    ring: RingSpec,
    radius: Float,
    strokePx: Float,
    frac: Float,
    complete: Boolean,
    breath: Float
) {
    val c = center
    val topLeft = Offset(c.x - radius, c.y - radius)
    val arcSize = Size(radius * 2, radius * 2)

    // Track
    drawCircle(ring.start.copy(alpha = 0.13f), radius, c, style = Stroke(strokePx))

    if (frac <= 0.002f) return
    val sweep = 360f * frac

    // Glow pass (API 28+): shadow layer under the arc, stronger once closed.
    if (SupportsShadowGlow) {
        val glowAlpha = if (complete) 0.55f + 0.3f * breath else 0.35f
        drawIntoCanvas { canvas ->
            val p = Paint().apply {
                style = PaintingStyle.Stroke
                strokeWidth = strokePx
                strokeCap = StrokeCap.Round
            }
            p.asFrameworkPaint().apply {
                isAntiAlias = true
                color = android.graphics.Color.TRANSPARENT
                setShadowLayer(strokePx * (if (complete) 1.1f else 0.7f), 0f, 0f, ring.end.copy(alpha = glowAlpha).toArgb())
            }
            canvas.drawArc(topLeft.x, topLeft.y, topLeft.x + arcSize.width, topLeft.y + arcSize.height, -90f, sweep, false, p)
        }
    }

    // Progress arc — sweep gradient rotated so 0° sits at 12 o'clock. The
    // tail of the gradient fades back to `start` so the round cap at the
    // arc's origin (just "behind" 12 o'clock) keeps the start colour.
    rotate(-90f, c) {
        val stops = if (frac < 1f) arrayOf(0f to ring.start, frac to ring.end, 1f to ring.start)
                    else arrayOf(0f to ring.start, 1f to ring.end)
        drawArc(
            brush = Brush.sweepGradient(*stops, center = c),
            startAngle = 0f,
            sweepAngle = sweep,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(strokePx, cap = StrokeCap.Round)
        )
    }

    // Leading tip: small bright bead where the arc currently ends.
    val tipAngle = Math.toRadians((sweep - 90f).toDouble())
    val tip = Offset(c.x + radius * cos(tipAngle).toFloat(), c.y + radius * sin(tipAngle).toFloat())
    drawCircle(ring.end, strokePx / 2f, tip)
    drawCircle(Color.White.copy(alpha = 0.85f), strokePx * 0.16f, tip)
}
