package com.healthify.app.ui.theme

import android.os.Build
import android.provider.Settings
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.LocalIndication
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random

// ═══════════════════════════════════════════════════════════════════════════
// MOTION PREFERENCES
// ═══════════════════════════════════════════════════════════════════════════

/**
 * True when the user turned animations off (Developer options / Accessibility
 * "Remove animations" both set ANIMATOR_DURATION_SCALE to 0). Ambient motion —
 * aurora drift, pulses, confetti, staggered entrances — is skipped when set.
 */
val LocalReducedMotion = staticCompositionLocalOf { false }

@Composable
fun rememberReducedMotion(): Boolean {
    val ctx = LocalContext.current
    return remember {
        Settings.Global.getFloat(ctx.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}

/**
 * Height the floating bottom nav covers at the bottom of each tab page. Tab
 * content scrolls *under* the glass nav, so every tab ends its scroll content
 * with a spacer this tall to keep the last item reachable.
 */
val LocalBottomBarClearance = compositionLocalOf { 0.dp }

/**
 * Whether the tab hosting this content is the one on screen. The pager
 * composes neighbouring tabs ahead of time; entrances and count-ups wait
 * for this so they play when the user actually arrives. Defaults to true
 * for content outside the pager.
 */
val LocalTabVisible = compositionLocalOf { true }

/** True from the first time the hosting tab is on screen, and stays true. */
@Composable
fun rememberHasBeenVisible(): Boolean {
    val visible = LocalTabVisible.current
    var seen by remember { mutableStateOf(visible) }
    LaunchedEffect(visible) { if (visible) seen = true }
    return seen
}

/**
 * App-wide aurora tint. The root AuroraBackground reads it; a full-screen
 * flow (check-in) sets it while shown so the one shared backdrop shifts
 * colour instead of a second aurora being drawn on top.
 */
object Aurora {
    var tint by mutableStateOf<Color?>(null)
}

// ═══════════════════════════════════════════════════════════════════════════
// AURORA BACKGROUND
// ═══════════════════════════════════════════════════════════════════════════

/**
 * Full-screen backdrop: deep navy plus four large, soft radial "light" blobs
 * drifting on one slow loop. All motion is read in the draw phase, so the
 * loop never recomposes anything.
 *
 * [tint], when set, pulls the two dominant blobs toward that colour (the
 * check-in flow uses it to colour each question).
 */
@Composable
fun AuroraBackground(
    modifier: Modifier = Modifier,
    palette: AuroraPalette = remember { auroraFor(TimeOfDay.now()) },
    tint: Color? = null,
    intensity: Float = 1f
) {
    val reduced = LocalReducedMotion.current
    val phase: State<Float> = if (reduced) {
        remember { mutableFloatStateOf(0.15f) }
    } else {
        rememberInfiniteTransition(label = "aurora").animateFloat(
            initialValue = 0f,
            targetValue  = 1f,
            animationSpec = infiniteRepeatable(tween(28_000, easing = LinearEasing)),
            label = "auroraPhase"
        )
    }
    val a by animateColorAsState(tint?.let { lerp(palette.a, it, 0.75f) } ?: palette.a, tween(900), label = "auroraA")
    val b by animateColorAsState(tint?.let { lerp(palette.b, it, 0.45f) } ?: palette.b, tween(900), label = "auroraB")

    Canvas(modifier.fillMaxSize()) {
        drawRect(BgDark)
        val w = size.width
        val h = size.height
        val r = max(w, h)
        val t = phase.value * 2f * PI.toFloat()
        // Integer frequencies keep the loop seamless at t = 0 / 2π.
        auroraBlob(Offset(w * (0.12f + 0.16f * sin(t)),        h * (0.04f + 0.05f * cos(t))),        r * 0.46f, a, 0.40f * intensity)
        auroraBlob(Offset(w * (0.96f + 0.10f * cos(t)),        h * (0.22f + 0.07f * sin(2 * t))),    r * 0.40f, b, 0.30f * intensity)
        auroraBlob(Offset(w * (0.05f + 0.12f * cos(2 * t)),    h * (0.66f + 0.06f * sin(t))),        r * 0.38f, palette.c, 0.20f * intensity)
        auroraBlob(Offset(w * (0.90f + 0.08f * sin(t + 1.3f)), h * (0.98f + 0.04f * cos(t))),        r * 0.40f, palette.d, 0.16f * intensity)
        // Gentle bottom vignette keeps the nav and lower cards readable.
        drawRect(Brush.verticalGradient(0.55f to Color.Transparent, 1f to BgDark.copy(alpha = 0.55f)))
    }
}

private fun DrawScope.auroraBlob(center: Offset, radius: Float, color: Color, alpha: Float) {
    drawCircle(
        brush = Brush.radialGradient(
            0f    to color.copy(alpha = alpha),
            0.35f to color.copy(alpha = alpha * 0.55f),
            0.7f  to color.copy(alpha = alpha * 0.15f),
            1f    to Color.Transparent,
            center = center,
            radius = radius
        ),
        radius = radius,
        center = center
    )
}

// ═══════════════════════════════════════════════════════════════════════════
// GLASS CARD
// ═══════════════════════════════════════════════════════════════════════════

/**
 * Frosted translucent card: soft white wash, bright top hairline, optional
 * coloured [tint] wash and outer [glow]. When [onClick] is set the card
 * dips slightly while pressed.
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = RoundedCornerShape(24.dp),
    tint: Color? = null,
    glow: Color? = null,
    glowRadius: Dp = 22.dp,
    borderBrush: Brush? = null,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.975f else 1f, spring(stiffness = Spring.StiffnessMedium), label = "glassPress")
    val indication = LocalIndication.current

    Column(
        modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .then(if (glow != null) Modifier.glow(glow, glowRadius, shape) else Modifier)
            .clip(shape)
            .background(Brush.verticalGradient(listOf(GlassFillTop, GlassFillBottom)))
            .then(
                if (tint != null) Modifier.background(
                    Brush.linearGradient(listOf(tint.copy(alpha = 0.16f), tint.copy(alpha = 0.04f)))
                ) else Modifier
            )
            .border(1.dp, borderBrush ?: Brush.verticalGradient(listOf(GlassBorderTop, GlassBorderBottom)), shape)
            .then(
                if (onClick != null) Modifier.clickable(
                    interactionSource = interaction,
                    indication = indication,
                    role = Role.Button,
                    onClick = onClick
                ) else Modifier
            ),
        content = content
    )
}

// ═══════════════════════════════════════════════════════════════════════════
// GLOW
// ═══════════════════════════════════════════════════════════════════════════

/** True where the GPU renderer draws shadow layers on shapes (API 28+). */
val SupportsShadowGlow: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P

/**
 * Soft coloured light around a rounded shape, drawn with a framework shadow
 * layer. On API 26–27 the hardware renderer ignores shadow layers on shapes,
 * so the glow is simply absent there — nothing else changes.
 */
fun Modifier.glow(color: Color, radius: Dp = 22.dp, shape: RoundedCornerShape = RoundedCornerShape(24.dp), alpha: Float = 0.55f): Modifier =
    glow(color, radius, shape) { alpha }

/** [glow] whose strength is read at draw time — animate it without recomposing. */
fun Modifier.glow(color: Color, radius: Dp, shape: RoundedCornerShape, alpha: () -> Float): Modifier =
    if (!SupportsShadowGlow) this else drawBehind {
        val corner = shape.topStart.toPx(size, this)
        drawIntoCanvas { canvas ->
            val paint = Paint()
            paint.asFrameworkPaint().apply {
                isAntiAlias = true
                this.color = android.graphics.Color.TRANSPARENT
                setShadowLayer(radius.toPx(), 0f, 0f, color.copy(alpha = alpha().coerceIn(0f, 1f)).toArgb())
            }
            canvas.drawRoundRect(0f, 0f, size.width, size.height, corner, corner, paint)
        }
    }

/** Circular variant of [glow] for orbs and avatars. */
fun Modifier.circleGlow(color: Color, radius: Dp = 18.dp, alpha: () -> Float = { 0.6f }): Modifier =
    if (!SupportsShadowGlow) this else drawBehind {
        drawIntoCanvas { canvas ->
            val paint = Paint()
            paint.asFrameworkPaint().apply {
                isAntiAlias = true
                this.color = android.graphics.Color.TRANSPARENT
                setShadowLayer(radius.toPx(), 0f, 0f, color.copy(alpha = alpha().coerceIn(0f, 1f)).toArgb())
            }
            canvas.drawCircle(center, size.minDimension / 2f, paint)
        }
    }

/**
 * A soft diagonal highlight that sweeps across the content every
 * [periodMs] — the "glint" on primary call-to-action surfaces.
 */
fun Modifier.shimmer(periodMs: Int = 3200, strength: Float = 0.22f): Modifier = composed {
    if (LocalReducedMotion.current) return@composed this
    val t = rememberInfiniteTransition(label = "shimmer").animateFloat(
        initialValue = -0.6f,
        targetValue  = 2.2f,
        animationSpec = infiniteRepeatable(tween(periodMs, easing = LinearEasing)),
        label = "shimmerX"
    )
    drawWithContent {
        drawContent()
        val x = size.width * t.value
        val band = size.width * 0.35f
        drawRect(
            Brush.linearGradient(
                0f to Color.Transparent,
                0.5f to Color.White.copy(alpha = strength),
                1f to Color.Transparent,
                start = Offset(x - band, 0f),
                end   = Offset(x, size.height)
            )
        )
    }
}

/** Radial light behind content — works on every API (no shadow layer). */
fun Modifier.radialGlow(color: Color, alpha: Float = 0.45f, scale: Float = 1f): Modifier = drawBehind {
    val r = size.minDimension * 0.75f * scale
    drawCircle(
        Brush.radialGradient(listOf(color.copy(alpha = alpha), Color.Transparent), center = center, radius = r),
        radius = r, center = center
    )
}

// ═══════════════════════════════════════════════════════════════════════════
// ENTRANCES, COUNTERS, PULSES
// ═══════════════════════════════════════════════════════════════════════════

/**
 * Fade + rise on first composition, delayed by [index] so a column of cards
 * cascades in. Runs once per composition — the tab pager keeps pages
 * composed, so it plays when the app opens, not on every swipe.
 */
fun Modifier.staggeredEnter(index: Int, rise: Dp = 22.dp): Modifier = composed {
    val reduced = LocalReducedMotion.current
    val progress = remember { Animatable(if (reduced) 1f else 0f) }
    val seen = rememberHasBeenVisible()
    LaunchedEffect(seen) {
        if (!reduced && seen && progress.value < 1f) {
            delay(80L + index * 70L)
            progress.animateTo(1f, tween(560, easing = EaseOutCubic))
        }
    }
    val risePx = with(LocalDensity.current) { rise.toPx() }
    graphicsLayer {
        alpha = progress.value
        translationY = (1f - progress.value) * risePx
        // Per-draw alpha, no offscreen buffer: a buffer is sized to the
        // node, so glows reaching past it (CTA, buttons) would be cut
        // square for the length of the fade.
        compositingStrategy = CompositingStrategy.ModulateAlpha
    }
}

/** Integer that counts up from 0 to [target] the first time, then glides to new targets. */
@Composable
fun rememberCountUp(target: Int, durationMs: Int = 1200, delayMs: Long = 0): State<Int> {
    val reduced = LocalReducedMotion.current
    val anim = remember { Animatable(if (reduced) target.toFloat() else 0f) }
    val seen = rememberHasBeenVisible()
    LaunchedEffect(target, seen) {
        if (reduced) anim.snapTo(target.toFloat())
        else if (seen) {
            if (delayMs > 0 && anim.value == 0f) delay(delayMs)
            anim.animateTo(target.toFloat(), tween(durationMs, easing = EaseOutCubic))
        }
    }
    return remember { derivedStateOf { anim.value.toInt() } }
}

/** Float progress (0..n) that sweeps up from 0 on first show, then follows [target]. */
@Composable
fun rememberSweep(target: Float, durationMs: Int = 1300, delayMs: Long = 0): State<Float> {
    val reduced = LocalReducedMotion.current
    val anim = remember { Animatable(if (reduced) target else 0f) }
    val seen = rememberHasBeenVisible()
    LaunchedEffect(target, seen) {
        if (reduced) anim.snapTo(target)
        else if (seen) {
            if (delayMs > 0 && anim.value == 0f) delay(delayMs)
            anim.animateTo(target, tween(durationMs, easing = EaseOutCubic))
        }
    }
    return anim.asState()
}

/** Endless 0→1→0 breathing value; constant 0.5 under reduced motion. */
@Composable
fun rememberBreath(periodMs: Int = 2400, label: String = "breath"): State<Float> {
    if (LocalReducedMotion.current) return remember { mutableFloatStateOf(0.5f) }
    return rememberInfiniteTransition(label = label).animateFloat(
        initialValue = 0f,
        targetValue  = 1f,
        animationSpec = infiniteRepeatable(tween(periodMs, easing = EaseInOutSine), RepeatMode.Reverse),
        label = label
    )
}

// ═══════════════════════════════════════════════════════════════════════════
// CONFETTI
// ═══════════════════════════════════════════════════════════════════════════

val ConfettiColors = listOf(Green, Sky, Lavender, Gold, Coral, Rose, Teal)

private class Particle(
    var x: Float, var y: Float,
    var vx: Float, var vy: Float,
    var rot: Float, val vRot: Float,
    val w: Float, val h: Float,
    val color: Color,
    val round: Boolean,
    val wobble: Float
)

/**
 * One-shot confetti burst from [origin] (fractions of the box) each time
 * [trigger] changes to a non-zero value. [intensity] scales the particle
 * count; milestones pass ~2 for an extra shower from the top edge.
 * Draws nothing under reduced motion.
 */
@Composable
fun ConfettiBurst(
    trigger: Int,
    modifier: Modifier = Modifier,
    origin: Offset = Offset(0.5f, 0.32f),
    intensity: Float = 1f,
    colors: List<Color> = ConfettiColors
) {
    if (LocalReducedMotion.current) return
    val density = LocalDensity.current.density
    var box by remember { mutableStateOf(IntSize.Zero) }
    val particles = remember { mutableStateListOf<Particle>() }
    var tick by remember { mutableLongStateOf(0L) }

    LaunchedEffect(trigger, box) {
        if (trigger == 0 || box == IntSize.Zero) return@LaunchedEffect
        val rnd = Random(trigger * 7919)
        val ox = box.width * origin.x
        val oy = box.height * origin.y
        val burst = (110 * intensity).toInt()
        particles.clear()
        repeat(burst) {
            val angle = (-PI / 2 + (rnd.nextFloat() - 0.5f) * 2.3f).toFloat()
            val speed = (650f + rnd.nextFloat() * 1150f) * density / 2.6f
            particles += Particle(
                x = ox, y = oy,
                vx = cos(angle) * speed, vy = sin(angle) * speed,
                rot = rnd.nextFloat() * 360f, vRot = (rnd.nextFloat() - 0.5f) * 720f,
                w = (5f + rnd.nextFloat() * 5f) * density, h = (8f + rnd.nextFloat() * 8f) * density,
                color = colors[rnd.nextInt(colors.size)],
                round = rnd.nextFloat() < 0.3f,
                wobble = rnd.nextFloat() * 6f
            )
        }
        if (intensity > 1.5f) {
            repeat((70 * (intensity - 1f)).toInt()) {
                particles += Particle(
                    x = rnd.nextFloat() * box.width, y = -rnd.nextFloat() * box.height * 0.5f,
                    vx = (rnd.nextFloat() - 0.5f) * 120f * density / 2.6f, vy = (120f + rnd.nextFloat() * 200f) * density / 2.6f,
                    rot = rnd.nextFloat() * 360f, vRot = (rnd.nextFloat() - 0.5f) * 540f,
                    w = (5f + rnd.nextFloat() * 5f) * density, h = (8f + rnd.nextFloat() * 8f) * density,
                    color = colors[rnd.nextInt(colors.size)],
                    round = rnd.nextFloat() < 0.3f,
                    wobble = rnd.nextFloat() * 6f
                )
            }
        }
        val gravity = 900f * density / 2.6f
        var last = withFrameNanos { it }
        val start = last
        while (isActive) {
            val now = withFrameNanos { it }
            val dt = ((now - last) / 1_000_000_000f).coerceAtMost(0.05f)
            last = now
            val age = (now - start) / 1_000_000_000f
            particles.forEach { p ->
                p.vy += gravity * dt
                p.vx *= 0.985f
                p.vy *= 0.992f
                p.x += (p.vx + sin(age * 5f + p.wobble) * 40f * density / 2.6f) * dt
                p.y += p.vy * dt
                p.rot += p.vRot * dt
            }
            tick = now
            if (age > 4.5f || particles.all { it.y > box.height + 40f }) break
        }
        particles.clear()
        tick = 0L
    }

    Canvas(modifier.fillMaxSize().onSizeChanged { box = it }) {
        if (tick < 0L) return@Canvas // reading tick re-draws on every confetti frame
        particles.forEach { p ->
            rotate(p.rot, pivot = Offset(p.x, p.y)) {
                if (p.round) drawCircle(p.color, p.w / 2f, Offset(p.x, p.y))
                else drawRect(p.color, Offset(p.x - p.w / 2f, p.y - p.h / 2f), Size(p.w, p.h))
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// HAPTICS
// ═══════════════════════════════════════════════════════════════════════════

/**
 * Thin wrapper over View.performHapticFeedback — honours the user's system
 * touch-feedback setting and needs no VIBRATE permission.
 */
class Haptics(private val view: View) {
    fun tick() { view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK) }
    fun confirm() {
        view.performHapticFeedback(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.CONFIRM
            else HapticFeedbackConstants.VIRTUAL_KEY
        )
    }
    fun heavy() { view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS) }
}

@Composable
fun rememberHaptics(): Haptics {
    val view = LocalView.current
    return remember(view) { Haptics(view) }
}

// ═══════════════════════════════════════════════════════════════════════════
// SMALL PIECES
// ═══════════════════════════════════════════════════════════════════════════

/**
 * Font size that follows the system font scale only up to [maxScale]. For
 * text inside fixed-size chrome (nav slots, ring centres) where growing
 * without bound would collide with neighbours; body text still scales fully.
 */
@Composable
fun TextUnit.capped(maxScale: Float = 1.25f): TextUnit {
    val fs = LocalDensity.current.fontScale
    return if (fs <= maxScale) this else (value * maxScale / fs).sp
}

/** Brand gradient used for hero text and primary orbs. */
val BrandGradient = Brush.linearGradient(listOf(Green, Sky))

/**
 * Primary pill button: accent gradient, dark ink, soft glow. Disabled it
 * drops to plain glass. [loading] swaps the label for a spinner.
 */
@Composable
fun GlowButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accent: Color = Green,
    enabled: Boolean = true,
    loading: Boolean = false
) {
    val shape = RoundedCornerShape(20.dp)
    val ink = Color(0xFF06121C)
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.96f else 1f, spring(dampingRatio = 0.55f, stiffness = 600f), label = "btnPress")
    val on by animateFloatAsState(if (enabled) 1f else 0f, tween(250), label = "btnOn")
    val end = lerp(accent, Color.White, 0.28f)
    Box(
        modifier
            .fillMaxWidth()
            .height(58.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .glow(accent, 22.dp, shape) { 0.45f * on }
            .clip(shape)
            .background(Brush.verticalGradient(listOf(GlassFillTop, GlassFillBottom)))
            .background(Brush.linearGradient(listOf(accent.copy(alpha = on), end.copy(alpha = on))))
            .border(1.dp, Color.White.copy(alpha = 0.12f + 0.1f * on), shape)
            .clickable(
                interactionSource = interaction,
                indication = LocalIndication.current,
                enabled = enabled && !loading,
                role = Role.Button,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        if (loading) {
            CircularProgressIndicator(color = ink, strokeWidth = 2.5.dp, modifier = Modifier.size(24.dp))
        } else {
            Text(
                text,
                style = MaterialTheme.typography.titleMedium,
                color = lerp(TextMuted, ink, on)
            )
        }
    }
}

/** Streak emoji on a soft light that flickers like a flame. */
@Composable
fun FlameBadge(emoji: String, color: Color, lively: Boolean, size: Dp = 54.dp) {
    val breath = rememberBreath(1300, "flame")
    Box(
        Modifier
            .size(size)
            .radialGlow(color, alpha = 0.45f),
        contentAlignment = Alignment.Center
    ) {
        Text(
            emoji,
            fontSize = (size.value * 0.56f).sp,
            modifier = Modifier.graphicsLayer {
                if (lively) {
                    val b = breath.value
                    scaleX = 1f + 0.07f * b
                    scaleY = 1f + 0.10f * b
                    rotationZ = (b - 0.5f) * 6f
                    transformOrigin = TransformOrigin(0.5f, 0.9f)
                }
            }
        )
    }
}

/** 44dp glass circle holding one icon — back / close / header actions. */
@Composable
fun GlassIconButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    tint: Color = TextPrimary
) {
    Box(
        Modifier
            .size(44.dp)
            .clip(androidx.compose.foundation.shape.CircleShape)
            .background(Brush.verticalGradient(listOf(GlassFillTop, GlassFillBottom)))
            .background(if (tint != TextPrimary) tint.copy(alpha = 0.12f) else Color.Transparent)
            .border(
                1.dp,
                Brush.verticalGradient(listOf(if (tint != TextPrimary) tint.copy(alpha = 0.5f) else GlassBorderTop, GlassBorderBottom)),
                androidx.compose.foundation.shape.CircleShape
            )
            .clickable(onClickLabel = description, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = description, tint = tint, modifier = Modifier.size(22.dp))
    }
}

/**
 * Header for the Insights / Reminders / Profile tabs: glass back button,
 * a big title with an optional kicker line, and trailing actions. Consumes
 * the status-bar inset itself (the tab Scaffolds pass WindowInsets(0)).
 */
@Composable
fun TabHeader(
    title: String,
    kicker: String? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    Row(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onBack != null) {
            GlassIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "Back to home", onBack)
            Spacer(Modifier.width(14.dp))
        }
        Column(Modifier.weight(1f)) {
            if (kicker != null) {
                Text(kicker.uppercase(), style = MaterialTheme.typography.labelSmall, color = TextMuted, maxLines = 1)
            }
            Text(title, style = MaterialTheme.typography.headlineMedium, color = TextPrimary, maxLines = 1)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically, content = actions)
    }
}

/** Round icon "orb": tinted glass disc with a soft radial light. */
@Composable
fun IconOrb(
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.34f))
            .background(Brush.linearGradient(listOf(color.copy(alpha = 0.28f), color.copy(alpha = 0.08f))))
            .border(1.dp, Brush.verticalGradient(listOf(color.copy(alpha = 0.45f), color.copy(alpha = 0.05f))), RoundedCornerShape(size * 0.34f)),
        contentAlignment = androidx.compose.ui.Alignment.Center,
        content = content
    )
}
