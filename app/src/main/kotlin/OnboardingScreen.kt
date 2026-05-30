package com.healthify.app.ui.onboarding

import androidx.compose.animation.*
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.healthify.app.R
import com.healthify.app.data.db.UserEntity
import com.healthify.app.data.repository.AppRepository
import com.healthify.app.ui.theme.*
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
    var heightCm by mutableStateOf("")
    var weightKg by mutableStateOf("")
    var unit by mutableStateOf("metric")
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
                if (user.heightCm > 0f)       heightCm = user.heightCm.toInt().toString()
                if (user.weightKg > 0f)       weightKg = "%.1f".format(user.weightKg)
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
            heightCm           = heightCm.toFloatOrNull() ?: 0f,
            weightKg           = weightKg.toFloatOrNull() ?: 0f,
            conditions         = conditions.joinToString(","),
            goals              = goals.joinToString(","),
            onboardingComplete = true,
        )
        repo.saveUser(updated)
        isLoading = false
        onComplete()
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

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.radialGradient(listOf(SurfaceCard2, BgDark)))
    ) {
        Column(
            Modifier
                .fillMaxSize()
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
                    Box(
                        Modifier
                            .padding(horizontal = 4.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (active) Green else TextDim)
                            .width(if (active) 22.dp else 7.dp)
                            .height(7.dp)
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
            Button(
                onClick = { if (viewModel.step == 3) viewModel.finish(onComplete) else viewModel.next() },
                modifier = Modifier.fillMaxWidth().height(54.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Green),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(
                    if (viewModel.step == 3) stringResource(R.string.btn_lets_go)
                    else stringResource(R.string.btn_continue_nav),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }

            if (viewModel.step > 0) {
                TextButton(onClick = { viewModel.back() }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.btn_back_nav), color = TextMuted)
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun StepName(vm: OnboardingViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Text("🌿", style = MaterialTheme.typography.displayLarge)
        Text(stringResource(R.string.onboard_welcome), style = MaterialTheme.typography.headlineLarge)
        Text(stringResource(R.string.onboard_welcome_desc), color = TextMuted)
        OBLabel(stringResource(R.string.onboard_name_label))
        OutlinedTextField(
            value = vm.name, onValueChange = { vm.name = it },
            placeholder = { Text(stringResource(R.string.onboard_name_placeholder), color = TextDim) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = textFieldColors()
        )
    }
}

@Composable
private fun StepDemographics(vm: OnboardingViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Text(stringResource(R.string.onboard_about), style = MaterialTheme.typography.headlineLarge)
        Text(stringResource(R.string.onboard_about_desc), color = TextMuted)
        OBLabel(stringResource(R.string.label_age))
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
            placeholder = { Text(stringResource(R.string.placeholder_age), color = TextDim) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = textFieldColors()
        )
        OBLabel(stringResource(R.string.label_gender))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // Gender values kept as English — stored as DB keys.
            listOf("Male", "Female", "Non-binary", "Skip").forEach { g ->
                val sel = vm.gender == g
                Box(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (sel) GreenDim else SurfaceCard)
                        .border(1.dp, if (sel) Green else Divider, RoundedCornerShape(12.dp))
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
    val isMetric = vm.unit == "metric"
    val metricLabel   = stringResource(R.string.unit_metric_display)
    val imperialLabel = stringResource(R.string.unit_imperial_display)
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Text(stringResource(R.string.onboard_body), style = MaterialTheme.typography.headlineLarge)
        Text(stringResource(R.string.onboard_body_desc), color = TextMuted)
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf("metric", "imperial").forEach { u ->
                val sel = vm.unit == u
                val label = if (u == "metric") metricLabel else imperialLabel
                Box(
                    Modifier
                        .weight(1f).clip(RoundedCornerShape(10.dp))
                        .background(if (sel) GreenDim else SurfaceCard)
                        .border(1.dp, if (sel) Green else Divider, RoundedCornerShape(10.dp))
                        .clickable { vm.unit = u }
                        .padding(vertical = 11.dp),
                    contentAlignment = Alignment.Center
                ) { Text(label, color = if (sel) Green else TextMuted) }
            }
        }
        OBLabel(if (isMetric) stringResource(R.string.label_height_cm) else stringResource(R.string.label_height_ft))
        OutlinedTextField(
            value = vm.heightCm, onValueChange = { vm.heightCm = it },
            placeholder = {
                Text(
                    if (isMetric) stringResource(R.string.placeholder_height_cm)
                    else stringResource(R.string.placeholder_height_ft),
                    color = TextDim
                )
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp),
            colors = textFieldColors()
        )
        OBLabel(if (isMetric) stringResource(R.string.label_weight_kg) else stringResource(R.string.label_weight_lbs))
        OutlinedTextField(
            value = vm.weightKg, onValueChange = { vm.weightKg = it },
            placeholder = {
                Text(
                    if (isMetric) stringResource(R.string.placeholder_weight_kg)
                    else stringResource(R.string.placeholder_weight_lbs),
                    color = TextDim
                )
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp),
            colors = textFieldColors()
        )
    }
}

@Composable
private fun StepProfile(vm: OnboardingViewModel) {
    // conds / goalList kept hardcoded — values stored as DB keys; must not change.
    val conds = listOf("✅ None","🩸 Diabetes","❤️ Hypertension","🧠 Anxiety",
        "💙 Depression","💔 Heart","🌬 Asthma","🦴 Arthritis")
    val goalList = listOf("💧 Drink more water","😴 Better sleep","🏃 More movement",
        "🧘 Manage stress","💊 Medication reminders","🥗 Eat healthier",
        "❤️ Heart health","🧠 Mental wellness")
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Text(stringResource(R.string.onboard_health), style = MaterialTheme.typography.headlineLarge)
        Text(stringResource(R.string.onboard_health_desc), color = TextMuted)
        OBLabel(stringResource(R.string.label_conditions))
        FlowRow(conds, vm.conditions) { vm.toggleCondition(it) }
        OBLabel(stringResource(R.string.label_goals))
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
                    .background(if (sel) GreenDim else SurfaceCard)
                    .border(1.dp, if (sel) Green else Divider, RoundedCornerShape(100.dp))
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
    unfocusedBorderColor = Divider,
    focusedTextColor     = TextPrimary,
    unfocusedTextColor   = TextPrimary,
    cursorColor          = Green,
    focusedContainerColor   = GreenDim,
    unfocusedContainerColor = SurfaceCard
)
