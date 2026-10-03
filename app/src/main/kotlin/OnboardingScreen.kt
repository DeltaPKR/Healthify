@file:OptIn(ExperimentalTextApi::class)

package com.healthify.app.ui.onboarding

import androidx.compose.animation.*
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.healthify.app.data.db.UserEntity
import com.healthify.app.data.repository.AppRepository
import com.healthify.app.firebase.FirebaseSync
import com.healthify.app.ui.theme.*
import com.healthify.app.units.BodyMetricsInput
import com.healthify.app.units.UnitSystem
import com.healthify.app.units.Units
import kotlinx.coroutines.launch

// ═══════════════════════════════════════════════════════════════════════════
// VIEW MODEL
// ═══════════════════════════════════════════════════════════════════════════

class OnboardingViewModel(private val repo: AppRepository) : ViewModel() {

    var step by mutableStateOf(0)
    var name by mutableStateOf("")
    // Age is a free-text field (matches heightCm/weightKg). Parsed back to
    // Int in finish(). Empty string means "user hasn't entered one yet".
    var age  by mutableStateOf("")
    var gender by mutableStateOf("")
    val body = BodyMetricsInput()
    var conditions = mutableStateListOf<String>()
    var goals      = mutableStateListOf<String>()
    var isLoading  by mutableStateOf(false)

    // Snapshot of the row at VM creation; finish() copies onto this so streak
    // counters, goal customizations and lastStreakDate are preserved.
    private var existing: UserEntity? = null

    init {
        viewModelScope.launch {
            val user = repo.getUserOnce()
            existing = user
            if (user != null) {
                if (user.name.isNotEmpty())   name     = user.name
                if (user.age > 0)             age      = user.age.toString()
                if (user.gender.isNotEmpty()) gender   = user.gender
                body.reset(UnitSystem.of(user.unitSystem), user.heightCm, user.weightKg)
                if (user.conditions.isNotEmpty())
                    conditions.addAll(user.conditions.split(",").filter { it.isNotBlank() })
                if (user.goals.isNotEmpty())
                    goals.addAll(user.goals.split(",").filter { it.isNotBlank() })
            }
        }
    }

    fun next() { if (step < 3) step++ }
    fun back() { if (step > 0) step-- }

    fun toggleCondition(c: String) { if (c in conditions) conditions.remove(c) else conditions.add(c) }
    fun toggleGoal(g: String)      { if (g in goals) goals.remove(g) else goals.add(g) }

    fun finish(onComplete: () -> Unit) = viewModelScope.launch {
        isLoading = true
        // Copy onto the existing row when present so we don't reset streaks /
        // goal customizations the user accumulated since first onboarding.
        val base = existing ?: UserEntity()
        val updated = base.copy(
            id                 = 0,
            name               = name.ifBlank { "Friend" },
            age                = age.toIntOrNull()?.coerceIn(0, 120) ?: 0,
            gender             = gender,
            // Always metric in storage; out-of-range input counts as unknown.
            heightCm           = Units.validHeightCm(body.heightCmValue()),
            weightKg           = Units.validWeightKg(body.weightKgValue()),
            unitSystem         = body.unit.key,
            conditions         = conditions.joinToString(","),
            goals              = goals.joinToString(","),
            onboardingComplete = true,
        )
        repo.saveUser(updated)
        isLoading = false
        onComplete()
        FirebaseSync.syncUser(updated)
    }

    class Factory(private val repo: AppRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>) =
            OnboardingViewModel(repo) as T
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// SCREEN
// ═══════════════════════════════════════════════════════════════════════════

@Composable
fun OnboardingScreen(viewModel: OnboardingViewModel, onComplete: () -> Unit) {
    LaunchedEffect(viewModel.step) { if (viewModel.step == 99) onComplete() }

    // Transparent: the app-wide aurora shows through.
    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .imePadding()
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            Spacer(Modifier.height(48.dp))
            // Progress dots
            Row(
                Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(4) { i ->
                    val active = i == viewModel.step
                    val done   = i < viewModel.step
                    val width by animateDpAsState(
                        if (active) 28.dp else 8.dp,
                        spring(dampingRatio = 0.6f, stiffness = 400f),
                        label = "dot$i"
                    )
                    val color by animateColorAsState(
                        when {
                            active -> Green
                            done   -> Green.copy(alpha = 0.45f)
                            else   -> TextDim
                        },
                        label = "dotColor$i"
                    )
                    Box(
                        Modifier
                            .padding(horizontal = 4.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(color)
                            .width(width)
                            .height(8.dp)
                    )
                }
            }
            Spacer(Modifier.height(32.dp))

            AnimatedContent(
                targetState = viewModel.step,
                transitionSpec = {
                    fadeIn() + slideInHorizontally { it / 4 } togetherWith
                            fadeOut() + slideOutHorizontally { -it / 4 }
                },
                label = "onboard_step"
            ) { s ->
                when (s) {
                    0 -> StepName(viewModel)
                    1 -> StepDemographics(viewModel)
                    2 -> StepBody(viewModel)
                    3 -> StepProfile(viewModel)
                }
            }

            Spacer(Modifier.height(32.dp))

            // CTA button
            GlowButton(
                text    = if (viewModel.step == 3) "Let's Go 🚀" else "Continue →",
                onClick = { if (viewModel.step == 3) viewModel.finish(onComplete) else viewModel.next() },
                loading = viewModel.isLoading
            )

            if (viewModel.step > 0) {
                TextButton(onClick = { viewModel.back() }, modifier = Modifier.fillMaxWidth()) {
                    Text("← Back", color = TextMuted)
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun StepName(vm: OnboardingViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
        val bob = rememberBreath(2800, "leaf")
        Box(
            Modifier.size(88.dp).radialGlow(Green, alpha = 0.35f, scale = 1.25f),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "🌿",
                fontSize = 54.sp,
                modifier = Modifier.graphicsLayer {
                    translationY = (bob.value - 0.5f) * 8.dp.toPx()
                    rotationZ = (bob.value - 0.5f) * 10f
                }
            )
        }
        Text(
            buildAnnotatedString {
                append("Welcome to\n")
                withStyle(SpanStyle(brush = BrandGradient)) { append("Healthify") }
                append("!")
            },
            style = MaterialTheme.typography.headlineLarge.copy(fontSize = 38.sp, lineHeight = 44.sp)
        )
        Text("Your personal companion for daily health, habits, and wellness.", color = TextMuted)
        OBLabel("What should we call you?")
        OutlinedTextField(
            value = vm.name, onValueChange = { vm.name = it },
            placeholder = { Text("Your name", color = TextDim) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = textFieldColors()
        )
    }
}

@Composable
private fun StepDemographics(vm: OnboardingViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Text("About you 👋", style = MaterialTheme.typography.headlineLarge)
        Text("This helps us personalise your health insights.", color = TextMuted)
        OBLabel("Age")
        // Replaced the previous Slider with a numeric text field. The
        // slider was lossy (drag granularity ≈ 1yr but felt unprecise) and
        // forced a min of 13 — testers asked for direct entry. Digits-only
        // filter + max-3-chars cap prevents the keyboard from accepting
        // unrelated input.
        OutlinedTextField(
            value = vm.age,
            onValueChange = { input ->
                vm.age = input.filter { it.isDigit() }.take(3)
            },
            placeholder = { Text("e.g. 28", color = TextDim) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = textFieldColors()
        )
        OBLabel("Gender")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Male", "Female", "Non-binary", "Skip").forEach { g ->
                val sel = vm.gender == g
                Box(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (sel) GreenDim else GlassFillTop)
                        .border(1.dp, if (sel) Green else GlassBorderTop, RoundedCornerShape(12.dp))
                        .clickable { vm.gender = g }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) { Text(g, style = MaterialTheme.typography.bodySmall, color = if (sel) Green else TextMuted) }
            }
        }
    }
}

@Composable
private fun StepBody(vm: OnboardingViewModel) {
    val body = vm.body
    val isMetric = body.unit == UnitSystem.METRIC
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Text("Body metrics 📏", style = MaterialTheme.typography.headlineLarge)
        Text("Used to calculate your personalised health goals.", color = TextMuted)
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            UnitSystem.entries.forEach { u ->
                val sel = body.unit == u
                Box(
                    Modifier
                        .weight(1f).clip(RoundedCornerShape(10.dp))
                        .background(if (sel) GreenDim else GlassFillTop)
                        .border(1.dp, if (sel) Green else GlassBorderTop, RoundedCornerShape(10.dp))
                        .clickable { body.switchUnit(u) }
                        .padding(vertical = 11.dp),
                    contentAlignment = Alignment.Center
                ) { Text(u.key.replaceFirstChar { it.uppercase() }, color = if (sel) Green else TextMuted) }
            }
        }
        OBLabel("Height")
        if (isMetric) {
            BodyField(body.heightCm, body::onHeightCm, "e.g. 175", "cm", KeyboardType.Decimal, Modifier.fillMaxWidth())
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                BodyField(body.heightFt, body::onHeightFt, "5", "ft", KeyboardType.Number, Modifier.weight(1f))
                BodyField(body.heightIn, body::onHeightIn, "10", "in", KeyboardType.Number, Modifier.weight(1f))
            }
        }
        OBLabel("Weight")
        BodyField(
            body.weight, body::onWeight,
            if (isMetric) "e.g. 70" else "e.g. 154",
            if (isMetric) "kg" else "lb",
            KeyboardType.Decimal, Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun BodyField(
    value: String,
    onChange: (String) -> Unit,
    hint: String,
    unit: String,
    keyboard: KeyboardType,
    modifier: Modifier
) {
    OutlinedTextField(
        value = value, onValueChange = onChange,
        placeholder = { Text(hint, color = TextDim) },
        suffix = { Text(unit, color = TextMuted) },
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        singleLine = true,
        modifier = modifier, shape = RoundedCornerShape(14.dp),
        colors = textFieldColors()
    )
}

@Composable
private fun StepProfile(vm: OnboardingViewModel) {
    val conds = listOf("✅ None","🩸 Diabetes","❤️ Hypertension","🧠 Anxiety",
        "💙 Depression","💔 Heart","🌬 Asthma","🦴 Arthritis")
    val goalList = listOf("💧 Drink more water","😴 Better sleep","🏃 More movement",
        "🧘 Manage stress","💊 Medication reminders","🥗 Eat healthier",
        "❤️ Heart health","🧠 Mental wellness")
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Text("Health profile 🏥", style = MaterialTheme.typography.headlineLarge)
        Text("We'll tailor reminders and advice just for you.", color = TextMuted)
        OBLabel("Any health conditions?")
        FlowRow(conds, vm.conditions) { vm.toggleCondition(it) }
        OBLabel("Your wellness goals")
        FlowRow(goalList, vm.goals) { vm.toggleGoal(it) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FlowRow(items: List<String>, selected: List<String>, onToggle: (String) -> Unit) {
    androidx.compose.foundation.layout.FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items.forEach { item ->
            val sel = item in selected
            Box(
                Modifier
                    .clip(RoundedCornerShape(100.dp))
                    .background(if (sel) GreenDim else GlassFillTop)
                    .border(1.dp, if (sel) Green else GlassBorderTop, RoundedCornerShape(100.dp))
                    .clickable { onToggle(item) }
                    .padding(horizontal = 14.dp, vertical = 9.dp)
            ) { Text(item, style = MaterialTheme.typography.bodySmall, color = if (sel) Green else TextMuted) }
        }
    }
}

@Composable
private fun OBLabel(text: String, trailingColor: androidx.compose.ui.graphics.Color? = null) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(text.uppercase(), style = MaterialTheme.typography.labelSmall, color = TextMuted)
        if (trailingColor != null) {
            val parts = text.split(":")
            if (parts.size > 1) Text(parts[1].trim(), color = trailingColor,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold))
        }
    }
}

@Composable
private fun textFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor   = Green,
    unfocusedBorderColor = GlassBorderTop,
    focusedTextColor     = TextPrimary,
    unfocusedTextColor   = TextPrimary,
    cursorColor          = Green,
    focusedContainerColor   = GreenDim,
    unfocusedContainerColor = GlassFillBottom
)
