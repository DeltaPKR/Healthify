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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
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

    init {
        // Re-fetch whenever either the user row (streak, name, goals) or
        // the check_ins table changes. Without this the Profile screen
        // would show stale stats until the app is cold-restarted —
        // testers reported the streak/total counters never moving after
        // a check-in until they killed and reopened the app.
        // combine() fires once on subscribe (giving us the initial load)
        // and again on every downstream emission.
        // `distinctUntilChanged` on the (user, check-ins) pair stops the
        // infinite-refresh loop that occurred when downstream code wrote
        // back to the users table with unchanged values: Room re-emits
        // on every successful UPDATE regardless of whether the row
        // actually changed, so without the de-dup we'd re-enter load()
        // forever (visible to the user as the Profile screen flickering
        // its loading spinner non-stop). Data classes give structural
        // equality for free.
        viewModelScope.launch {
            combine(
                repo.getUser(),
                repo.getAllCheckIns()
            ) { u, cis -> u to cis }
                .distinctUntilChanged()
                .collectLatest { _ -> load() }
        }
    }

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
            TabHeader("Profile", kicker = "You", onBack = onBack) {
                GlassIconButton(Icons.Default.Edit, "Edit profile", { showEdit = true }, tint = Green)
            }
        },
        containerColor = Color.Transparent,
        // The floating nav + LocalBottomBarClearance own the bottom inset.
        contentWindowInsets = WindowInsets(0)
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
            GlassCard(Modifier.fillMaxWidth()) {
                Column(
                    Modifier.fillMaxWidth().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        Modifier
                            .size(92.dp)
                            .radialGlow(Green, alpha = 0.3f, scale = 1.2f)
                            .clip(CircleShape)
                            .background(Brush.linearGradient(listOf(Color(0xFF16263D), Color(0xFF0E1829))))
                            .border(2.5.dp, Brush.sweepGradient(listOf(Green, Sky, Lavender, Green)), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            u.name.firstOrNull()?.uppercase() ?: "👤",
                            fontSize = 36.sp, color = Green
                        )
                    }
                    // maxLines=1 + ellipsis keeps an unusually long display
                    // name (or 200% system font scale) from pushing the
                    // "Edit profile" button off-card.
                    Text(
                        u.name.ifBlank { "Friend" },
                        style = MaterialTheme.typography.headlineLarge.copy(fontSize = 26.sp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    val subtitle = buildList {
                        if (u.age > 0) add("${u.age} yrs")
                        if (u.gender.isNotBlank() && u.gender != "Skip") add(u.gender)
                    }.joinToString(" · ")
                    if (subtitle.isNotBlank()) {
                        Text(
                            subtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
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
            // Four across normally; 2 × 2 at large font scales so the
            // labels don't break mid-word.
            val stats = listOf(
                StatSpec("🔥", u.currentStreak.toString(), "Current\nstreak", Green),
                StatSpec("👑", u.longestStreak.toString(), "Best\nstreak", Gold),
                StatSpec("✓", s.totalCheckIns.toString(), "Total\ncheck-ins", Sky),
                StatSpec("⭐", if (s.avgScore > 0) "%d".format(s.avgScore.toInt()) else "—", "Avg\nscore", Lavender)
            )
            val perRow = if (LocalDensity.current.fontScale > 1.3f) 2 else 4
            stats.chunked(perRow).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { st -> StatTile(icon = st.icon, value = st.value, label = st.label, color = st.color) }
                }
            }

            // ── Body Metrics ─────────────────────────────────────────────
            SectionLabel("Body metrics")
            GlassCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
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
            GlassCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
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
            GlassCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
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
            GlassCard(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                tint = Coral,
                onClick = { showConfirmReset = true }
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
            Spacer(Modifier.height(LocalBottomBarClearance.current))
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
        ConfirmDialog(
            emoji = "🔄",
            title = "Re-do onboarding?",
            message = "You'll go through the welcome flow again. Your check-ins and streak are kept.",
            confirmLabel = "Continue",
            onConfirm = {
                showConfirmReset = false
                viewModel.resetOnboarding()
                onResetOnboarding()
            },
            onDismiss = { showConfirmReset = false }
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// EDIT DIALOG
// ═══════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalLayoutApi::class)
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

    GlassDialog(onDismiss = onDismiss, accent = Green) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("EDIT PROFILE", style = MaterialTheme.typography.labelSmall, color = Green)
                Spacer(Modifier.height(4.dp))
                Text("Your details", style = MaterialTheme.typography.headlineSmall, color = TextPrimary)
            }
            IconOrb(Green, size = 52.dp) {
                Text(
                    name.trim().firstOrNull()?.uppercase() ?: "🙂",
                    style = MaterialTheme.typography.titleLarge,
                    color = Green
                )
            }
        }

        Spacer(Modifier.height(20.dp))
        FieldLabel("Name")
        OutlinedTextField(
            value = name, onValueChange = { name = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true, shape = RoundedCornerShape(16.dp),
            colors = glassFieldColors()
        )

        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            DialogField("Age", age, Modifier.weight(1f), KeyboardType.Number) {
                age = it.filter { c -> c.isDigit() }.take(3)
            }
            DialogField("Height cm", heightCm, Modifier.weight(1f), KeyboardType.Decimal) {
                heightCm = it.filter { c -> c.isDigit() || c == '.' }
            }
            DialogField("Weight kg", weightKg, Modifier.weight(1f), KeyboardType.Decimal) {
                weightKg = it.filter { c -> c.isDigit() || c == '.' }
            }
        }

        Spacer(Modifier.height(16.dp))
        FieldLabel("Gender")
        androidx.compose.foundation.layout.FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("Male", "Female", "Non-binary", "Skip").forEach { g ->
                GlassChip(g, selected = gender == g, onClick = { gender = g })
            }
        }

        Spacer(Modifier.height(20.dp))
        FieldLabel("Daily goals", color = Green)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            DialogField("🚶 Steps", stepGoal, Modifier.weight(1f), KeyboardType.Number) {
                stepGoal = it.filter { c -> c.isDigit() }.take(6)
            }
            DialogField("💧 Water", waterGoal, Modifier.weight(1f), KeyboardType.Number) {
                waterGoal = it.filter { c -> c.isDigit() }.take(2)
            }
            DialogField("🌙 Sleep h", sleepGoal, Modifier.weight(1f), KeyboardType.Decimal) {
                sleepGoal = it.filter { c -> c.isDigit() || c == '.' }
            }
        }

        Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GhostButton("Cancel", onDismiss, Modifier.weight(1f))
            GlowButton(
                text = "Save",
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
                modifier = Modifier.weight(1f),
                height = 52.dp
            )
        }
    }
}

/** Small labelled numeric field for the three-across rows. */
@Composable
private fun DialogField(
    label: String,
    value: String,
    modifier: Modifier,
    keyboard: KeyboardType,
    onChange: (String) -> Unit
) {
    Column(modifier) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = TextMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(bottom = 6.dp)
        )
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            keyboardOptions = KeyboardOptions(keyboardType = keyboard),
            colors = glassFieldColors()
        )
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
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf(FirebaseSync.currentState(ctx)) }
    var errorReason by remember { mutableStateOf(FirebaseSync.lastAuthErrorReason()) }
    // Poll every 2s — connectivity and auth flip rarely, this is cheap.
    LaunchedEffect(Unit) {
        while (true) {
            state = FirebaseSync.currentState(ctx)
            errorReason = FirebaseSync.lastAuthErrorReason()
            delay(2000)
        }
    }
    val valueColor = when (state) {
        CloudSyncState.ACTIVE     -> Green
        CloudSyncState.CONNECTING -> Gold
        CloudSyncState.OFFLINE    -> Coral
        // ERROR = sign-in attempts exhausted (e.g. SHA-1 mismatch on a
        // Play-served install). Coral signals "user-actionable problem"
        // — Coral is the same hue used for the danger zone elsewhere so
        // it reads as "something is wrong, not just slow".
        CloudSyncState.ERROR      -> Coral
    }
    // Tapping the row in ERROR state retries sign-in. CONNECTING / ACTIVE
    // are passive — no action — and tapping wouldn't help. OFFLINE is also
    // passive because we can't fix the network from here.
    val rowMod = Modifier
        .fillMaxWidth()
        .let { mod ->
            if (state == CloudSyncState.ERROR) mod.clickable {
                scope.launch {
                    // Show CONNECTING during the retry so the user gets feedback.
                    state = CloudSyncState.CONNECTING
                    errorReason = null
                    FirebaseSync.retrySignIn()
                    state = FirebaseSync.currentState(ctx)
                    errorReason = FirebaseSync.lastAuthErrorReason()
                }
            } else mod
        }
    Column(modifier = rowMod) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("☁️ Cloud sync", style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f), color = TextPrimary)
            Text(state.label, style = MaterialTheme.typography.titleMedium, color = valueColor)
        }
        if (state == CloudSyncState.ERROR) {
            Spacer(Modifier.height(4.dp))
            Text(
                "Tap to retry",
                style = MaterialTheme.typography.bodySmall,
                color = Coral
            )
            // 3 lines + ellipsis — gives room for the full Firebase message
            // (e.g. "An internal error has occurred. [INVALID_REFRESH_TOKEN]")
            // without exploding the card on long traces.
            Text(
                errorReason ?: "Unknown error",
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }
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
    // Poll auth-state status too, so we can distinguish "still trying" from
    // "definitively failed" — when failed we show that instead of pretending
    // sign-in is in progress. The auth listener only fires on real state
    // changes (null → uid or uid → null); a sequence of failed sign-in
    // attempts never produces a fire, so a side channel is required.
    var authState by remember { mutableStateOf(FirebaseSync.currentState(ctx)) }
    LaunchedEffect(Unit) {
        while (uid == null) {
            authState = FirebaseSync.currentState(ctx)
            delay(2000)
        }
    }
    val display = when {
        uid != null                           -> "${uid!!.take(8)}…"
        authState == CloudSyncState.ERROR     -> "Sign-in failed"
        authState == CloudSyncState.OFFLINE   -> "Offline"
        else                                  -> "Signing in…"
    }
    val displayColor = when {
        uid != null                           -> Sky
        authState == CloudSyncState.ERROR     -> Coral
        else                                  -> TextMuted
    }
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
        Text(
            display,
            style = MaterialTheme.typography.titleMedium,
            color = displayColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
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

private data class StatSpec(val icon: String, val value: String, val label: String, val color: Color)

@Composable
private fun RowScope.StatTile(icon: String, value: String, label: String, color: Color) {
    GlassCard(Modifier.weight(1f), shape = RoundedCornerShape(18.dp), tint = color) {
        Column(
            Modifier.padding(vertical = 14.dp, horizontal = 8.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(icon, fontSize = 20.sp)
            Text(value,
                style = MaterialTheme.typography.headlineSmall, color = color)
            Text(label, style = MaterialTheme.typography.labelMedium,
                color = TextMuted, textAlign = TextAlign.Center, lineHeight = 14.sp)
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


