package com.healthify.app.ui.insights

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.healthify.app.data.db.CheckInEntity
import com.healthify.app.data.repository.AppRepository
import com.healthify.app.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InsightsScreen(repo: AppRepository, onBack: () -> Unit) {
    val scope    = rememberCoroutineScope()
    var checkIns by remember { mutableStateOf<List<CheckInEntity>>(emptyList()) }
    var avgScore by remember { mutableStateOf(0f) }

    LaunchedEffect(Unit) {
        scope.launch {
            checkIns = repo.getRecentCheckIns(7)
            avgScore = repo.avgScoreSince(7)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Insights", style = MaterialTheme.typography.headlineMedium) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, null, tint = TextMuted)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BgDark)
            )
        },
        containerColor = BgDark
    ) { pad ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(pad)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Past 7 Days", style = MaterialTheme.typography.labelSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(
                    Triple("🚶", "Avg Steps", "%,d".format(checkIns.map { it.steps }.average().let { if (it.isNaN()) 0 else it.toInt() })),
                    Triple("💧", "Avg Water", "%.1f gl".format(checkIns.map { it.waterGlasses }.average().let { if (it.isNaN()) 0.0 else it })),
                    Triple("🌙", "Avg Sleep", "%.1fh".format(checkIns.map { it.sleepHours.toDouble() }.average().let { if (it.isNaN()) 0.0 else it })),
                    Triple("⭐", "Avg Score", "%.0f".format(avgScore)),
                ).forEach { (icon, lbl, val_) ->
                    Card(Modifier.weight(1f),
                        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                        shape  = RoundedCornerShape(16.dp)) {
                        Column(Modifier.padding(12.dp)) {
                            Text(icon, fontSize = 20.sp)
                            Text(val_, style = MaterialTheme.typography.titleLarge, color = Green)
                            Text(lbl, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

            if (checkIns.size >= 2) {
                Card(colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                    shape = RoundedCornerShape(20.dp)) {
                    Column(Modifier.padding(20.dp)) {
                        Text("WELLNESS SCORE TREND", style = MaterialTheme.typography.labelSmall)
                        Spacer(Modifier.height(16.dp))
                        WellnessTrendChart(checkIns)
                    }
                }
            }

            if (checkIns.any { it.steps > 0 }) {
                Card(colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                    shape = RoundedCornerShape(20.dp)) {
                    Column(Modifier.padding(20.dp)) {
                        Text("DAILY STEPS", style = MaterialTheme.typography.labelSmall)
                        Spacer(Modifier.height(16.dp))
                        StepsBarChart(checkIns)
                    }
                }
            }

            Text("Recent Check-ins", style = MaterialTheme.typography.labelSmall)
            checkIns.take(7).forEach { ci ->
                Card(colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                    shape = RoundedCornerShape(14.dp)) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text(ci.date, style = MaterialTheme.typography.bodySmall, color = TextMuted)
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text("💧 ${ci.waterGlasses}gl", style = MaterialTheme.typography.bodySmall)
                                Text("🌙 %.1fh".format(ci.sleepHours), style = MaterialTheme.typography.bodySmall)
                                if (ci.steps > 0) Text("🚶 %,d".format(ci.steps), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        Text(ci.wellnessScore.toString(),
                            style = MaterialTheme.typography.titleLarge, color = Green)
                    }
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun WellnessTrendChart(data: List<CheckInEntity>) {
    val scores = data.map { it.wellnessScore.toFloat() }
    val maxV   = (scores.maxOrNull() ?: 100f).coerceAtLeast(1f)
    Canvas(Modifier.fillMaxWidth().height(90.dp)) {
        if (scores.size < 2) return@Canvas
        val w = size.width; val h = size.height
        val stepX = w / (scores.size - 1)
        val pts = scores.mapIndexed { i, v -> Pair(i * stepX, h - (v / maxV) * (h - 10f)) }
        val path = Path().apply {
            moveTo(pts[0].first, pts[0].second)
            pts.drop(1).forEach { (x, y) -> lineTo(x, y) }
        }
        val fill = Path().apply {
            moveTo(pts[0].first, h); lineTo(pts[0].first, pts[0].second)
            pts.drop(1).forEach { (x, y) -> lineTo(x, y) }
            lineTo(pts.last().first, h); close()
        }
        drawPath(fill, Brush.verticalGradient(
            listOf(Color(0x301AD9A0), Color.Transparent)))
        drawPath(path, Color(0xFF1AD9A0), style = Stroke(width = 2.5f, cap = StrokeCap.Round))
        pts.forEach { (x, y) ->
            drawCircle(Color(0xFF1AD9A0), radius = 5f, center = androidx.compose.ui.geometry.Offset(x, y))
        }
    }
}

@Composable
private fun StepsBarChart(data: List<CheckInEntity>) {
    val maxSteps = (data.maxOfOrNull { it.steps } ?: 1).coerceAtLeast(1)
    Row(
        Modifier.fillMaxWidth().height(90.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        data.forEach { ci ->
            val frac = ci.steps.toFloat() / maxSteps
            Column(
                Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom
            ) {
                Box(
                    Modifier.fillMaxWidth()
                        .height((frac * 70).dp.coerceAtLeast(4.dp))
                        .clip(RoundedCornerShape(topStart = 5.dp, topEnd = 5.dp))
                        .background(
                            Brush.verticalGradient(
                                listOf(Color(0xFF5EC4FF), Color(0xFF1AD9A0))
                            )
                        )
                )
                Spacer(Modifier.height(4.dp))
                Text(ci.date.takeLast(5), style = MaterialTheme.typography.bodySmall,
                    color = TextDim, fontSize = 8.sp)
            }
        }
    }
}
