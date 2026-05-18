package com.healthify.app.ui.profile

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.healthify.app.data.db.UserEntity
import com.healthify.app.BuildConfig
import com.healthify.app.data.repository.AppRepository
import com.healthify.app.firebase.CloudSyncState
import com.healthify.app.firebase.FirebaseSync
import com.healthify.app.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ═══════════════════════════════════════════════════════════════════════════
// VIEW MODEL
// ═══════════════════════════════════════════════════════════════════════════

data class ProfileUiState(
    val user: UserEntity? = null,
    val totalCheckIns: Int = 0,
    val avgScore: Float = 0f,
    val isLoading: Boolean = true
)

class ProfileViewModel(private val repo: AppRepository) : ViewModel() {

    var uiState by mutableStateOf(ProfileUiState())
        private set

    init { load() }

    fun load() = viewModelScope.launch {
        uiState = uiState.copy(isLoading = true)
        val user = repo.getUserOnce()
        val total = repo.totalCheckIns()
        val avg = if (total > 0) repo.avgScoreSince(30) else 0f
        uiState = ProfileUiState(
            user = user,
            totalCheckIns = total,
            avgScore = avg,
            isLoading = false
        )
    }

    fun saveUser(updated: UserEntity) = viewModelScope.launch {
        repo.saveUser(updated)
        FirebaseSync.syncUser(updated)
        load()
    }

    fun resetOnboarding() = viewModelScope.launch {
        val current = repo.getUserOnce() ?: return@launch
        repo.saveUser(current.copy(onboardingComplete = false))
    }

    class Factory(private val repo: AppRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>) =
            ProfileViewModel(repo) as T
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// SCREEN
// ═══════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    viewModel: ProfileViewModel,
    onBack: () -> Unit,
    onResetOnboarding: () -> Unit
) {
    val s = viewModel.uiState
    var showEdit by remember { mutableStateOf(false) }
    var showConfirmReset by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Profile", style = MaterialTheme.typography.headlineMedium) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, null, tint = TextMuted)
                    }
                },
                actions = {
                    IconButton(onClick = { showEdit = true }) {
                        Icon(Icons.Default.Edit, "Edit profile", tint = Green)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BgDark)
            )
        },
        containerColor = BgDark
    ) { pad ->
        if (s.isLoading) {
            Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Green)
            }
            return@Scaffold
        }
        val u = s.user ?: UserEntity()

        Column(
            Modifier
                .fillMaxSize()
                .padding(pad)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── Avatar + Name header ──────────────────────────────────────
            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        Modifier.size(86.dp).clip(CircleShape)
                            .background(Brush.radialGradient(listOf(GreenDim, SkyDim))),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            u.name.firstOrNull()?.uppercase() ?: "👤",
                            fontSize = 36.sp, color = Green
                        )
                    }
                    Text(
                        u.name.ifBlank { "Friend" },
                        style = MaterialTheme.typography.headlineLarge.copy(fontSize = 26.sp)
                    )
                    val subtitle = buildList {
                        if (u.age > 0) add("${u.age} yrs")
                        if (u.gender.isNotBlank() && u.gender != "Skip") add(u.gender)
                    }.joinToString(" · ")
                    if (subtitle.isNotBlank()) {
                        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = TextMuted)
                    }
                    Spacer(Modifier.height(2.dp))
                    OutlinedButton(
                        onClick = { showEdit = true },
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Green),
                        contentPadding = PaddingValues(horizontal = 22.dp, vertical = 8.dp)
                    ) {
                        Icon(Icons.Default.Edit, null, tint = Green,
                            modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Edit profile", color = Green,
                            style = MaterialTheme.typography.titleMedium)
                    }
                }
            }

            // ── Streak Stats ─────────────────────────────────────────────
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile(
                    icon = "🔥",
                    value = u.currentStreak.toString(),
                    label = "Current\nstreak",
                    color = Green
                )
                StatTile(
                    icon = "👑",
                    value = u.longestStreak.toString(),
                    label = "Best\nstreak",
                    color = Gold
                )
                StatTile(
                    icon = "✓",
                    value = s.totalCheckIns.toString(),
                    label = "Total\ncheck-ins",
                    color = Sky
                )
                StatTile(
                    icon = "⭐",
                    value = if (s.avgScore > 0) "%d".format(s.avgScore.toInt()) else "—",
                    label = "Avg\nscore",
                    color = Lavender
                )
            }

            // ── Body Metrics ─────────────────────────────────────────────
            SectionLabel("Body metrics")
            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    InfoRow("📏 Height", if (u.heightCm > 0) "%.0f cm".format(u.heightCm) else "—")
                    HorizontalDivider(color = Divider)
                    InfoRow("⚖️ Weight", if (u.weightKg > 0) "%.1f kg".format(u.weightKg) else "—")
                    if (u.heightCm > 0 && u.weightKg > 0) {
                        HorizontalDivider(color = Divider)
                        val bmi = u.weightKg / ((u.heightCm / 100f) * (u.heightCm / 100f))
                        val bmiLabel = when {
                            bmi < 18.5 -> "Underweight" to Sky
                            bmi < 25   -> "Healthy"     to Green
                            bmi < 30   -> "Overweight"  to Gold
                            else       -> "Obese"       to Coral
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("🩺 BMI",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f), color = TextPrimary)
                            Text("%.1f".format(bmi),
                                style = MaterialTheme.typography.titleMedium, color = Green)
                            Spacer(Modifier.width(8.dp))
                            Box(
                                Modifier.clip(RoundedCornerShape(100.dp))
                                    .background(bmiLabel.second.copy(alpha = 0.18f))
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text(bmiLabel.first,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = bmiLabel.second)
                            }
                        }
                    }
                }
            }

            // ── Goals ────────────────────────────────────────────────────
            SectionLabel("Daily goals")
            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    InfoRow("🚶 Steps", "%,d".format(u.stepGoal))
                    HorizontalDivider(color = Divider)
                    InfoRow("💧 Water", "${u.waterGoalGlasses} glasses")
                    HorizontalDivider(color = Divider)
                    InfoRow("🌙 Sleep", "%.1fh".format(u.sleepGoalHours))
                }
            }

            // ── Conditions ───────────────────────────────────────────────
            val conditions = u.conditions.split(",")
                .map { it.trim() }.filter { it.isNotEmpty() }
            if (conditions.isNotEmpty()) {
                SectionLabel("Health conditions")
                ChipFlow(conditions, color = Coral, dim = CoralDim)
            }

            // ── Wellness Goals ───────────────────────────────────────────
            val goals = u.goals.split(",")
                .map { it.trim() }.filter { it.isNotEmpty() }
            if (goals.isNotEmpty()) {
                SectionLabel("Wellness goals")
                ChipFlow(goals, color = Green, dim = GreenDim)
            }

            // ── About ────────────────────────────────────────────────────
            SectionLabel("About")
            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    InfoRow("🌿 App", "Healthify v${BuildConfig.VERSION_NAME}")
                    HorizontalDivider(color = Divider)
                    CloudSyncRow()
                    HorizontalDivider(color = Divider)
                    AccountIdRow()
                    HorizontalDivider(color = Divider)
                    DeleteDataRow()
                }
            }

            // ── Danger zone ──────────────────────────────────────────────
            Spacer(Modifier.height(8.dp))
            Card(
                Modifier.fillMaxWidth().clickable { showConfirmReset = true },
                colors = CardDefaults.cardColors(containerColor = CoralDim),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x40FF7B6E))
            ) {
                Row(
                    Modifier.padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text("🔄", fontSize = 22.sp)
                    Column(Modifier.weight(1f)) {
                        Text("Re-do onboarding", color = Coral,
                            style = MaterialTheme.typography.titleMedium)
                        Text("Update your name, body metrics, conditions and goals",
                            style = MaterialTheme.typography.bodySmall, color = TextMuted)
                    }
                    Icon(Icons.Default.ChevronRight, null, tint = Coral)
                }
            }
            Spacer(Modifier.height(40.dp))
        }
    }

    // ── Edit dialog ───────────────────────────────────────────────────────
    if (showEdit && s.user != null) {
        EditProfileDialog(
            user = s.user,
            onDismiss = { showEdit = false },
            onSave = { updated ->
                viewModel.saveUser(updated)
                showEdit = false
            }
        )
    }

    // ── Reset confirm ─────────────────────────────────────────────────────
    if (showConfirmReset) {
        AlertDialog(
            onDismissRequest = { showConfirmReset = false },
            title = { Text("Re-do onboarding?", color = TextPrimary) },
            text = {
                Text(
                    "You'll go through the welcome flow again. Your check-ins and streak are kept.",
                    color = TextMuted
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showConfirmReset = false
                    viewModel.resetOnboarding()
                    onResetOnboarding()
                }) { Text("Continue", color = Coral) }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmReset = false }) {
                    Text("Cancel", color = TextMuted)
                }
            },
            containerColor = SurfaceCard,
            shape = RoundedCornerShape(20.dp)
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// EDIT DIALOG
// ═══════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun EditProfileDialog(
    user: UserEntity,
    onDismiss: () -> Unit,
    onSave: (UserEntity) -> Unit
) {
    var name by remember { mutableStateOf(user.name) }
    var age by remember { mutableStateOf(if (user.age > 0) user.age.toString() else "") }
    var gender by remember { mutableStateOf(user.gender) }
    var heightCm by remember { mutableStateOf(if (user.heightCm > 0) "%.0f".format(user.heightCm) else "") }
    var weightKg by remember { mutableStateOf(if (user.weightKg > 0) "%.1f".format(user.weightKg) else "") }
    var stepGoal by remember { mutableStateOf(user.stepGoal.toString()) }
    var waterGoal by remember { mutableStateOf(user.waterGoalGlasses.toString()) }
    var sleepGoal by remember { mutableStateOf("%.1f".format(user.sleepGoalHours)) }

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceCard),
            shape = RoundedCornerShape(22.dp)
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text("Edit profile",
                    style = MaterialTheme.typography.headlineMedium, color = TextPrimary)

                FieldLabel("Name")
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true, shape = RoundedCornerShape(12.dp),
                    colors = dialogTextFieldColors()
                )

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Column(Modifier.weight(1f)) {
                        FieldLabel("Age")
                        OutlinedTextField(
                            value = age, onValueChange = { age = it.filter { c -> c.isDigit() }.take(3) },
                            modifier = Modifier.fillMaxWidth(), singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = dialogTextFieldColors()
                        )
                    }
                    Column(Modifier.weight(2f)) {
                        FieldLabel("Gender")
                        androidx.compose.foundation.layout.FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf("Male", "Female", "Non-binary", "Skip").forEach { g ->
                                val sel = gender == g
                                Box(
                                    Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (sel) GreenDim else SurfaceCard2)
                                        .border(1.dp, if (sel) Green else Divider,
                                            RoundedCornerShape(10.dp))
                                        .clickable { gender = g }
                                        .padding(horizontal = 10.dp, vertical = 8.dp)
                                ) {
                                    Text(g, style = MaterialTheme.typography.bodySmall,
                                        color = if (sel) Green else TextMuted)
                                }
                            }
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Column(Modifier.weight(1f)) {
                        FieldLabel("Height (cm)")
                        OutlinedTextField(
                            value = heightCm,
                            onValueChange = { heightCm = it.filter { c -> c.isDigit() || c == '.' } },
                            modifier = Modifier.fillMaxWidth(), singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            colors = dialogTextFieldColors()
                        )
                    }
                    Column(Modifier.weight(1f)) {
                        FieldLabel("Weight (kg)")
                        OutlinedTextField(
                            value = weightKg,
                            onValueChange = { weightKg = it.filter { c -> c.isDigit() || c == '.' } },
                            modifier = Modifier.fillMaxWidth(), singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            colors = dialogTextFieldColors()
                        )
                    }
                }

                Spacer(Modifier.height(4.dp))
                Text("Daily goals", style = MaterialTheme.typography.labelSmall, color = Green)

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Column(Modifier.weight(1f)) {
                        FieldLabel("Steps")
                        OutlinedTextField(
                            value = stepGoal,
                            onValueChange = { stepGoal = it.filter { c -> c.isDigit() }.take(6) },
                            modifier = Modifier.fillMaxWidth(), singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = dialogTextFieldColors()
                        )
                    }
                    Column(Modifier.weight(1f)) {
                        FieldLabel("Water (gl)")
                        OutlinedTextField(
                            value = waterGoal,
                            onValueChange = { waterGoal = it.filter { c -> c.isDigit() }.take(2) },
                            modifier = Modifier.fillMaxWidth(), singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = dialogTextFieldColors()
                        )
                    }
                    Column(Modifier.weight(1f)) {
                        FieldLabel("Sleep (h)")
                        OutlinedTextField(
                            value = sleepGoal,
                            onValueChange = { sleepGoal = it.filter { c -> c.isDigit() || c == '.' } },
                            modifier = Modifier.fillMaxWidth(), singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            colors = dialogTextFieldColors()
                        )
                    }
                }

                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape = RoundedCornerShape(13.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Divider)
                    ) { Text("Cancel", color = TextMuted) }
                    Button(
                        onClick = {
                            val updated = user.copy(
                                name = name.trim().ifBlank { "Friend" },
                                age = age.toIntOrNull() ?: user.age,
                                gender = gender,
                                heightCm = heightCm.toFloatOrNull() ?: user.heightCm,
                                weightKg = weightKg.toFloatOrNull() ?: user.weightKg,
                                stepGoal = stepGoal.toIntOrNull()?.coerceIn(1000, 100_000)
                                    ?: user.stepGoal,
                                waterGoalGlasses = waterGoal.toIntOrNull()?.coerceIn(1, 30)
                                    ?: user.waterGoalGlasses,
                                sleepGoalHours = sleepGoal.toFloatOrNull()?.coerceIn(4f, 14f)
                                    ?: user.sleepGoalHours
                            )
                            onSave(updated)
                        },
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape = RoundedCornerShape(13.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Green)
                    ) {
                        Text("Save", color = MaterialTheme.colorScheme.onPrimary,
                            style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// SMALL COMPOSABLES
// ═══════════════════════════════════════════════════════════════════════════

@Composable
private fun SectionLabel(text: String) {
    Text(text.uppercase(), style = MaterialTheme.typography.labelSmall,
        color = TextMuted, modifier = Modifier.padding(start = 4.dp, top = 4.dp))
}

@Composable
private fun FieldLabel(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = TextMuted,
        modifier = Modifier.padding(bottom = 4.dp))
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f), color = TextPrimary)
        Text(value, style = MaterialTheme.typography.titleMedium, color = Green)
    }
}

@Composable
private fun CloudSyncRow() {
    val ctx = LocalContext.current
    var state by remember { mutableStateOf(FirebaseSync.currentState(ctx)) }
    // Poll every 2s — connectivity and auth flip rarely, this is cheap.
    LaunchedEffect(Unit) {
        while (true) {
            state = FirebaseSync.currentState(ctx)
            delay(2000)
        }
    }
    val valueColor = when (state) {
        CloudSyncState.ACTIVE     -> Green
        CloudSyncState.CONNECTING -> Gold
        CloudSyncState.OFFLINE    -> Coral
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("☁️ Cloud sync", style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f), color = TextPrimary)
        Text(state.label, style = MaterialTheme.typography.titleMedium, color = valueColor)
    }
}

/**
 * Shows the anonymous Firebase UID assigned to this install. The privacy
 * policy and the data-deletion landing page tell users to find their UID
 * here so they can include it when emailing a deletion request; surfacing
 * it explicitly makes that promise actually fulfillable.
 *
 * Tap-to-copy: the full UID is placed on the clipboard. Re-renders if
 * Firebase auth signs in after first composition (anonymous sign-in is
 * async at app start) via a manifest auth-state listener.
 */
@Composable
private fun AccountIdRow() {
    val ctx = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val uid by produceState(initialValue = Firebase.auth.currentUser?.uid) {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            value = auth.currentUser?.uid
        }
        Firebase.auth.addAuthStateListener(listener)
        awaitDispose { Firebase.auth.removeAuthStateListener(listener) }
    }
    val display = uid?.let { "${it.take(8)}…" } ?: "Signing in…"
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .let { mod ->
                if (uid != null) mod.clickable {
                    clipboard.setText(AnnotatedString(uid!!))
                    Toast.makeText(ctx, "Account ID copied", Toast.LENGTH_SHORT).show()
                } else mod
            },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("🪪 Account ID", style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f), color = TextPrimary)
        Text(display, style = MaterialTheme.typography.titleMedium, color = Sky)
        if (uid != null) {
            Spacer(Modifier.width(8.dp))
            Icon(
                imageVector = Icons.Default.ContentCopy,
                contentDescription = "Copy Account ID",
                tint = TextMuted,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

/**
 * Opens an email composer pre-addressed to the support inbox with the
 * user's Firebase UID and install info already filled in, matching the
 * deletion instructions on https://deltapkr.github.io/Healthify/delete-data/.
 * If no email client is installed the click is a no-op and a Toast tells
 * the user to use the URL instead.
 */
@Composable
private fun DeleteDataRow() {
    val ctx = LocalContext.current
    val uid by produceState(initialValue = Firebase.auth.currentUser?.uid) {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            value = auth.currentUser?.uid
        }
        Firebase.auth.addAuthStateListener(listener)
        awaitDispose { Firebase.auth.removeAuthStateListener(listener) }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                val body = buildString {
                    append("Please delete my Healthify cloud data.\n\n")
                    append("Anonymous Firebase UID: ")
                    append(uid ?: "(not signed in yet — please attach a screenshot of the Profile screen)")
                    append("\n\nApprox. install date: ")
                }
                val mailto = Uri.parse(
                    "mailto:deltapkr.developer@gmail.com" +
                    "?subject=" + Uri.encode("Healthify data deletion request") +
                    "&body=" + Uri.encode(body)
                )
                val intent = Intent(Intent.ACTION_SENDTO, mailto)
                try {
                    ctx.startActivity(intent)
                } catch (_: android.content.ActivityNotFoundException) {
                    Toast.makeText(
                        ctx,
                        "No email app found — visit deltapkr.github.io/Healthify/delete-data/",
                        Toast.LENGTH_LONG
                    ).show()
                }
            },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("🗑️ Request data deletion", style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f), color = TextPrimary)
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = TextMuted
        )
    }
}

@Composable
private fun RowScope.StatTile(icon: String, value: String, label: String, color: Color) {
    Card(
        Modifier.weight(1f),
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            Modifier.padding(vertical = 14.dp, horizontal = 8.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(icon, fontSize = 20.sp)
            Text(value,
                style = MaterialTheme.typography.headlineSmall, color = color)
            Text(label, style = MaterialTheme.typography.labelSmall,
                color = TextMuted, textAlign = TextAlign.Center, lineHeight = 12.sp)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChipFlow(items: List<String>, color: Color, dim: Color) {
    androidx.compose.foundation.layout.FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items.forEach { item ->
            Box(
                Modifier
                    .clip(RoundedCornerShape(100.dp))
                    .background(dim)
                    .border(1.dp, color.copy(alpha = 0.35f), RoundedCornerShape(100.dp))
                    .padding(horizontal = 14.dp, vertical = 9.dp)
            ) {
                Text(item, style = MaterialTheme.typography.bodySmall, color = color)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun dialogTextFieldColors() = TextFieldDefaults.colors(
    focusedContainerColor = SurfaceCard2,
    unfocusedContainerColor = SurfaceCard2,
    focusedIndicatorColor = Green,
    unfocusedIndicatorColor = Divider,
    cursorColor = Green,
    focusedTextColor = TextPrimary,
    unfocusedTextColor = TextPrimary
)

