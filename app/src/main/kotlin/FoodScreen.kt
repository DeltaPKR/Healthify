package com.healthify.app.ui.food

import android.text.format.DateFormat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.healthify.app.data.db.MealEntryEntity
import com.healthify.app.data.db.UserEntity
import com.healthify.app.data.repository.AppRepository
import com.healthify.app.data.repository.LogRepository
import com.healthify.app.firebase.FirebaseSync
import com.healthify.app.logs.MealQuality
import com.healthify.app.logs.MealType
import com.healthify.app.nutrition.NutritionTargets
import com.healthify.app.nutrition.Portion
import com.healthify.app.nutrition.totals
import com.healthify.app.time.DayClock
import com.healthify.app.ui.logs.MealLogDialog
import com.healthify.app.ui.theme.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/** How many days back the Food tab can page (today + 6 = one week). */
private const val MAX_DAYS_BACK = 6

/**
 * Meals by slot for today or one of the last [MAX_DAYS_BACK] days. The
 * evening check-in's food question pre-fills from these. Calories and
 * macros appear only when the user counts calories.
 */
@Composable
fun FoodScreen(
    logRepo: LogRepository,
    repo: AppRepository,
    onBack: () -> Unit,
    onAddFood: (type: MealType, date: String) -> Unit,
) {
    val today by remember { DayClock.todayIsoFlow() }.collectAsState(DayClock.todayIso())
    var daysBack by rememberSaveable { mutableIntStateOf(0) }
    val date = remember(today, daysBack) { DayClock.iso(LocalDate.parse(today).minusDays(daysBack.toLong())) }
    val meals by remember(date) { logRepo.mealsFlow(date) }.collectAsState(initial = null)
    val user by remember { repo.getUser() }.collectAsState(initial = null)
    val counting = user?.countCalories == true
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf<MealEntryEntity?>(null) }
    var showSettings by remember { mutableStateOf(false) }
    var settingsTurnOn by remember { mutableStateOf(false) }   // opened from "Count calories"

    Scaffold(
        topBar = {
            TabHeader("Food", kicker = "Your meals", onBack = onBack) {
                GlassIconButton(Icons.Rounded.Tune, "Food settings", { settingsTurnOn = false; showSettings = true }, tint = Gold)
            }
        },
        containerColor = Color.Transparent,
        // The floating nav + LocalBottomBarClearance own the bottom inset.
        contentWindowInsets = WindowInsets(0)
    ) { pad ->
        val list = meals ?: return@Scaffold
        Column(
            Modifier
                .fillMaxSize()
                .padding(pad)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            DaySwitcher(
                label   = dayLabel(date, today),
                canBack = daysBack < MAX_DAYS_BACK,
                canNext = daysBack > 0,
                onBack  = { daysBack++ },
                onNext  = { daysBack-- }
            )
            val u = user
            if (counting && u != null) {
                CalorieSummaryCard(list, u, onSetUp = { showSettings = true }, modifier = Modifier.staggeredEnter(0))
            } else {
                DaySummaryCard(list, onCountCalories = { settingsTurnOn = true; showSettings = true }, modifier = Modifier.staggeredEnter(0))
            }
            MealType.entries.forEachIndexed { i, type ->
                MealSlotCard(
                    type     = type,
                    meals    = list.filter { it.mealType == type.key },
                    counting = counting,
                    onAdd    = { onAddFood(type, date) },
                    onEdit   = { editing = it },
                    modifier = Modifier.staggeredEnter(1 + i)
                )
            }
            Spacer(Modifier.height(LocalBottomBarClearance.current + 8.dp))
        }
    }

    editing?.let { meal ->
        MealLogDialog(
            existing    = meal,
            initialType = MealType.of(meal.mealType),
            onSave      = { t, name, quality, grams ->
                val resized = if (grams != null) Portion.rescale(meal, grams) else meal
                scope.launch { logRepo.saveMeal(resized, t, name, quality) }
                editing = null
            },
            onDelete    = {
                scope.launch { logRepo.deleteMeal(meal) }
                editing = null
            },
            onDismiss   = { editing = null }
        )
    }
    if (showSettings) user?.let { u ->
        FoodSettingsDialog(
            user = u,
            turnOn = settingsTurnOn,
            onSave = { updated ->
                showSettings = false
                scope.launch {
                    repo.saveUser(updated)
                    FirebaseSync.syncUser(updated)
                }
            },
            onDismiss = { showSettings = false }
        )
    }
}

internal fun dayLabel(date: String, today: String = DayClock.todayIso()): String {
    val d = LocalDate.parse(date)
    val t = LocalDate.parse(today)
    return when (d) {
        t              -> "Today"
        t.minusDays(1) -> "Yesterday"
        else           -> d.format(DateTimeFormatter.ofPattern("EEEE, MMM d", Locale.US))
    }
}

@Composable
private fun DaySwitcher(label: String, canBack: Boolean, canNext: Boolean, onBack: () -> Unit, onNext: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack, enabled = canBack) {
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, "Previous day",
                tint = if (canBack) TextPrimary else TextDim)
        }
        Text(label, style = MaterialTheme.typography.titleMedium, color = TextPrimary,
            textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
        IconButton(onClick = onNext, enabled = canNext) {
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, "Next day",
                tint = if (canNext) TextPrimary else TextDim)
        }
    }
}

/** Calories against the target as a ring, plus macro bars. */
@Composable
private fun CalorieSummaryCard(
    meals: List<MealEntryEntity>,
    user: UserEntity,
    onSetUp: () -> Unit,
    modifier: Modifier = Modifier
) {
    val totals = meals.totals()
    val target = NutritionTargets.dailyTarget(user)
    val progress by animateFloatAsState(
        if (target != null && target > 0) (totals.kcal.toFloat() / target).coerceIn(0f, 1f) else 0f,
        tween(900), label = "kcalRing"
    )
    GlassCard(modifier.fillMaxWidth(), tint = Gold) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(96.dp), contentAlignment = Alignment.Center) {
                    Canvas(Modifier.fillMaxSize()) {
                        val stroke = 10.dp.toPx()
                        val inset = stroke / 2
                        val arc = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke)
                        val tl = androidx.compose.ui.geometry.Offset(inset, inset)
                        drawArc(Color.White.copy(alpha = 0.10f), -90f, 360f, false, tl, arc, style = Stroke(stroke))
                        drawArc(Gold, -90f, 360f * progress, false, tl, arc, style = Stroke(stroke, cap = StrokeCap.Round))
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("%,d".format(totals.kcal), style = MaterialTheme.typography.titleLarge.copy(fontFeatureSettings = TABULAR),
                            color = TextPrimary)
                        Text("kcal", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                    }
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    if (target != null) {
                        val left = target - totals.kcal
                        Text(
                            if (left >= 0) "%,d left".format(left) else "%,d over".format(-left),
                            style = MaterialTheme.typography.titleMedium, color = if (left >= 0) Gold else Coral
                        )
                        Text("of %,d kcal target".format(target), style = MaterialTheme.typography.bodySmall, color = TextMuted)
                    } else {
                        Text("No daily target yet", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                        Text("Set up", style = MaterialTheme.typography.labelLarge, color = Gold,
                            modifier = Modifier.padding(top = 4.dp).clickable(onClick = onSetUp))
                    }
                    if (totals.hasUnknown) {
                        Spacer(Modifier.height(4.dp))
                        Text("Some meals have no calorie info.", style = MaterialTheme.typography.bodySmall, color = TextDim)
                    }
                }
            }
            if (target != null) {
                val m = NutritionTargets.macros(target)
                Spacer(Modifier.height(14.dp))
                MacroBar("Protein", totals.proteinG, m.proteinG, Green)
                MacroBar("Carbs", totals.carbsG, m.carbsG, Sky)
                MacroBar("Fat", totals.fatG, m.fatG, Lavender)
            }
        }
    }
}

@Composable
private fun MacroBar(label: String, grams: Int, target: Int, color: Color) {
    Column(Modifier.padding(top = 6.dp)) {
        Row {
            Text(label, style = MaterialTheme.typography.labelMedium, color = TextMuted, modifier = Modifier.weight(1f))
            Text("$grams / $target g", style = MaterialTheme.typography.labelMedium, color = TextPrimary)
        }
        Spacer(Modifier.height(4.dp))
        Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(Color.White.copy(alpha = 0.08f))) {
            Box(Modifier.fillMaxHeight().fillMaxWidth((grams.toFloat() / target.coerceAtLeast(1)).coerceIn(0f, 1f))
                .clip(RoundedCornerShape(3.dp)).background(color))
        }
    }
}

private fun qualityColor(q: MealQuality?): Color = when (q) {
    MealQuality.WELL -> Green
    MealQuality.OK   -> Gold
    MealQuality.POOR -> Coral
    MealQuality.SKIP, null -> TextDim
}

@Composable
private fun DaySummaryCard(meals: List<MealEntryEntity>, onCountCalories: () -> Unit, modifier: Modifier = Modifier) {
    val day = MealQuality.forDay(meals.map { it.quality })
    val (title, body) = when (day) {
        null             -> "Nothing logged yet" to
            "Log meals as you go. Your evening check-in fills itself in from them."
        MealQuality.WELL -> "Eating well today 🥗" to "Nice — keep the colourful plates coming."
        MealQuality.OK   -> "A decent day so far" to "One more veggie-forward meal would tip it to great."
        MealQuality.POOR -> "Room to eat better" to "No guilt — a lighter, home-made next meal helps."
        MealQuality.SKIP -> "Lots of skipped meals" to "Your body runs better on regular fuel. A small snack counts."
    }
    GlassCard(modifier.fillMaxWidth(), tint = qualityColor(day).takeIf { day != null }) {
        Row(
            Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            IconOrb(qualityColor(day), size = 52.dp) { Text(day?.emoji ?: "🍽️", fontSize = 24.sp) }
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                Text(body, style = MaterialTheme.typography.bodySmall, color = TextMuted)
                if (meals.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        meals.forEach { m ->
                            Box(Modifier.size(10.dp).clip(CircleShape).background(qualityColor(MealQuality.of(m.quality))))
                        }
                    }
                }
                Text(
                    "Want numbers? Count calories",
                    style = MaterialTheme.typography.labelMedium,
                    color = Gold,
                    modifier = Modifier
                        .padding(top = 10.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClickLabel = "Open food settings", onClick = onCountCalories)
                        .padding(vertical = 2.dp)
                )
            }
        }
    }
}

@Composable
private fun MealSlotCard(
    type: MealType,
    meals: List<MealEntryEntity>,
    counting: Boolean,
    onAdd: () -> Unit,
    onEdit: (MealEntryEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val timeFormat = remember { DateFormat.getTimeFormat(context) }
    GlassCard(modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp)) {
        Column(Modifier.padding(horizontal = 18.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(type.emoji, fontSize = 22.sp)
                Spacer(Modifier.width(10.dp))
                Text(type.label, style = MaterialTheme.typography.titleMedium, color = TextPrimary,
                    modifier = Modifier.weight(1f))
                Row(
                    Modifier
                        .clip(RoundedCornerShape(100.dp))
                        .clickable(onClickLabel = "Add ${type.label.lowercase()}", onClick = onAdd)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.Add, null, tint = Gold, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Add", style = MaterialTheme.typography.labelLarge, color = Gold)
                }
            }
            if (meals.isEmpty()) {
                Text("Nothing yet", style = MaterialTheme.typography.bodySmall, color = TextDim,
                    modifier = Modifier.padding(start = 32.dp, top = 2.dp))
            }
            meals.forEach { m ->
                val q = MealQuality.of(m.quality)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(onClickLabel = "Edit meal") { onEdit(m) }
                        .padding(vertical = 6.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(qualityColor(q)))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            m.name.ifBlank { q?.label ?: "Meal" },
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            listOfNotNull(
                                q?.let { "${it.emoji} ${it.label}" }.takeIf { m.name.isNotBlank() },
                                m.grams?.let { "${it.roundToInt()} g" },
                                timeFormat.format(Date(m.loggedAt))
                            ).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMuted,
                            maxLines = 1
                        )
                    }
                    if (counting && m.kcal != null) {
                        Text("${m.kcal.roundToInt()} kcal", style = MaterialTheme.typography.labelLarge, color = Gold)
                        Spacer(Modifier.width(4.dp))
                    }
                    NutriScoreBadge(m.nutriScore, Modifier.padding(end = 4.dp))
                    Icon(Icons.Rounded.ChevronRight, null, tint = TextDim, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}
