package com.healthify.app.ui.food

import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.healthify.app.data.db.MealEntryEntity
import com.healthify.app.data.repository.LogRepository
import com.healthify.app.logs.MealQuality
import com.healthify.app.logs.MealType
import com.healthify.app.time.DayClock
import com.healthify.app.ui.logs.MealLogDialog
import com.healthify.app.ui.theme.*
import kotlinx.coroutines.launch
import java.util.Date

/** Today's meals, by slot. The evening check-in's food question pre-fills from these. */
@Composable
fun FoodScreen(logRepo: LogRepository, onBack: () -> Unit) {
    val today by remember { DayClock.todayIsoFlow() }.collectAsState(DayClock.todayIso())
    val meals by remember(today) { logRepo.mealsFlow(today) }.collectAsState(initial = null)
    val scope = rememberCoroutineScope()
    var adding by remember { mutableStateOf<MealType?>(null) }
    var editing by remember { mutableStateOf<MealEntryEntity?>(null) }

    Scaffold(
        topBar = { TabHeader("Food", kicker = "Today's meals", onBack = onBack) },
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
            DaySummaryCard(list, Modifier.staggeredEnter(0))
            MealType.entries.forEachIndexed { i, type ->
                MealSlotCard(
                    type    = type,
                    meals   = list.filter { it.mealType == type.key },
                    onAdd   = { adding = type },
                    onEdit  = { editing = it },
                    modifier = Modifier.staggeredEnter(1 + i)
                )
            }
            Spacer(Modifier.height(LocalBottomBarClearance.current + 8.dp))
        }
    }

    adding?.let { type ->
        MealLogDialog(
            existing    = null,
            initialType = type,
            onSave      = { t, name, quality ->
                scope.launch { logRepo.saveMeal(null, t, name, quality, today) }
                adding = null
            },
            onDelete    = null,
            onDismiss   = { adding = null }
        )
    }
    editing?.let { meal ->
        MealLogDialog(
            existing    = meal,
            initialType = MealType.of(meal.mealType),
            onSave      = { t, name, quality ->
                scope.launch { logRepo.saveMeal(meal, t, name, quality) }
                editing = null
            },
            onDelete    = {
                scope.launch { logRepo.deleteMeal(meal) }
                editing = null
            },
            onDismiss   = { editing = null }
        )
    }
}

private fun qualityColor(q: MealQuality?): Color = when (q) {
    MealQuality.WELL -> Green
    MealQuality.OK   -> Gold
    MealQuality.POOR -> Coral
    MealQuality.SKIP, null -> TextDim
}

@Composable
private fun DaySummaryCard(meals: List<MealEntryEntity>, modifier: Modifier = Modifier) {
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
            }
        }
    }
}

@Composable
private fun MealSlotCard(
    type: MealType,
    meals: List<MealEntryEntity>,
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
                            listOfNotNull(q?.let { "${it.emoji} ${it.label}" }.takeIf { m.name.isNotBlank() },
                                timeFormat.format(Date(m.loggedAt))).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMuted,
                            maxLines = 1
                        )
                    }
                    Icon(Icons.Rounded.ChevronRight, null, tint = TextDim, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}
