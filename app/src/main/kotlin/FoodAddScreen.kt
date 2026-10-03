package com.healthify.app.ui.food

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.healthify.app.Entitlements
import com.healthify.app.Feature
import com.healthify.app.data.db.FoodItemEntity
import com.healthify.app.data.db.MealEntryEntity
import com.healthify.app.data.db.UserEntity
import com.healthify.app.data.repository.AppRepository
import com.healthify.app.data.repository.LogRepository
import com.healthify.app.food.BarcodeScanner
import com.healthify.app.food.FoodRepository
import com.healthify.app.food.OffResult
import com.healthify.app.logs.MealQuality
import com.healthify.app.logs.MealType
import com.healthify.app.nutrition.Portion
import com.healthify.app.ui.logs.MealLogDialog
import com.healthify.app.ui.theme.*
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

// ═══════════════════════════════════════════════════════════════════════════
// VIEW MODEL
// ═══════════════════════════════════════════════════════════════════════════

class FoodAddViewModel(
    private val foodRepo: FoodRepository,
    private val logRepo: LogRepository,
    repo: AppRepository,
    val date: String,
) : ViewModel() {

    val user: StateFlow<UserEntity?> = repo.getUser()
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val recent: StateFlow<List<FoodItemEntity>?> = foodRepo.recentFlow()
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    var query by mutableStateOf("")
    var searching by mutableStateOf(false)
        private set
    /** The query the results below belong to; null until the first search. */
    var searchedFor by mutableStateOf<String?>(null)
        private set
    var local by mutableStateOf<List<FoodItemEntity>>(emptyList())
        private set
    var remote by mutableStateOf<OffResult<List<FoodItemEntity>>?>(null)
        private set
    /** A food picked or scanned: opens the amount sheet. */
    var selected by mutableStateOf<FoodItemEntity?>(null)
    /** A barcode Open Food Facts doesn't know: offer to add it by hand. */
    var unknownBarcode by mutableStateOf<String?>(null)
    var notice by mutableStateOf<String?>(null)
    var lookingUp by mutableStateOf(false)
        private set

    /** Runs on the keyboard's Search action / button only — never per keystroke. */
    fun search() {
        val q = query.trim()
        if (q.length < 2 || searching) return
        searching = true
        viewModelScope.launch {
            local = foodRepo.searchLocal(q)
            remote = foodRepo.searchRemote(q)
            searchedFor = q
            searching = false
        }
    }

    fun clearSearch() {
        query = ""; searchedFor = null; local = emptyList(); remote = null
    }

    fun onBarcode(code: String) {
        lookingUp = true
        viewModelScope.launch {
            when (val r = foodRepo.lookupBarcode(code)) {
                is OffResult.Ok       -> selected = r.value
                OffResult.NotFound    -> unknownBarcode = code
                is OffResult.Busy     -> notice = "Too many lookups in a minute — try again in ${r.retryInSec} s."
                OffResult.Offline     -> notice = "You're offline. Scanned foods you've logged before still work."
                is OffResult.Failed   -> notice = "Couldn't look that barcode up (${r.message})."
            }
            lookingUp = false
        }
    }

    fun createCustom(name: String, kcal100: Float?, barcode: String?) = viewModelScope.launch {
        unknownBarcode = null
        selected = foodRepo.createCustom(name, kcal100, barcode)
    }

    fun logFood(food: FoodItemEntity, grams: Float, type: MealType, quality: MealQuality, onDone: () -> Unit) =
        viewModelScope.launch {
            // Search results aren't stored until picked; refreshes a cached copy too.
            val stored = if (food.id == 0L || food.source == FoodItemEntity.SOURCE_OFF) foodRepo.save(food) else food
            val base = MealEntryEntity(date = date, mealType = type.key, quality = quality.key, name = stored.name)
            logRepo.saveMeal(Portion.mealFrom(stored, grams, base))
            foodRepo.markUsed(stored.id)
            onDone()
        }

    fun quickLog(type: MealType, name: String, quality: MealQuality, onDone: () -> Unit) = viewModelScope.launch {
        logRepo.saveMeal(null, type, name, quality, date)
        onDone()
    }

    class Factory(
        private val foodRepo: FoodRepository,
        private val logRepo: LogRepository,
        private val repo: AppRepository,
        private val date: String,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>) =
            FoodAddViewModel(foodRepo, logRepo, repo, date) as T
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// SCREEN
// ═══════════════════════════════════════════════════════════════════════════

/** Search, scan or quick-log a food into one meal slot of [vm]'s date. */
@Composable
fun FoodAddScreen(vm: FoodAddViewModel, mealType: MealType, dateLabel: String, onDone: () -> Unit) {
    val context = LocalContext.current
    val focus = LocalFocusManager.current
    val user by vm.user.collectAsState()
    val recent by vm.recent.collectAsState()
    val counting = user?.countCalories == true
    var showQuick by remember { mutableStateOf(false) }
    var showCustom by remember { mutableStateOf(false) }
    var showManualBarcode by remember { mutableStateOf(false) }

    fun scan() {
        BarcodeScanner.scan(context) { outcome ->
            when (outcome) {
                is BarcodeScanner.Outcome.Scanned -> vm.onBarcode(outcome.code)
                BarcodeScanner.Outcome.Cancelled  -> Unit
                BarcodeScanner.Outcome.Unavailable -> showManualBarcode = true
            }
        }
    }

    Scaffold(
        topBar = {
            TabHeader("Add to ${mealType.label.lowercase()}", kicker = dateLabel, onBack = onDone) {
                if (Entitlements.has(Feature.BARCODE_SCAN)) {
                    GlassIconButton(Icons.Rounded.QrCodeScanner, "Scan a barcode", { scan() }, tint = Gold)
                }
            }
        },
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0)
    ) { pad ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(pad)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            OutlinedTextField(
                value = vm.query,
                onValueChange = { vm.query = it.take(80) },
                placeholder = { Text("Search foods, e.g. greek yogurt", color = TextDim) },
                leadingIcon = { Icon(Icons.Rounded.Search, null, tint = TextMuted) },
                trailingIcon = {
                    if (vm.query.isNotEmpty()) IconButton(onClick = { vm.clearSearch() }) {
                        Icon(Icons.Rounded.Close, "Clear search", tint = TextMuted)
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focus.clearFocus(); vm.search() }),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = glassFieldColors(Gold)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GlowButton(
                    text = if (vm.searching) "Searching…" else "Search",
                    onClick = { focus.clearFocus(); vm.search() },
                    modifier = Modifier.weight(1f),
                    accent = Gold,
                    enabled = vm.query.trim().length >= 2 && !vm.searching,
                    height = 48.dp
                )
                GhostButton("Quick log", { showQuick = true }, Modifier.weight(1f), height = 48.dp)
            }

            vm.notice?.let { NoticeCard(it) { vm.notice = null } }
            if (vm.lookingUp) LinearProgressIndicator(Modifier.fillMaxWidth(), color = Gold, trackColor = Color.White.copy(alpha = 0.08f))

            if (vm.searchedFor == null) {
                SectionTitle("Recent")
                val list = recent.orEmpty()
                if (list.isEmpty()) {
                    Text(
                        "Search or scan a barcode. Foods you log show up here for next time.",
                        style = MaterialTheme.typography.bodyMedium, color = TextMuted
                    )
                }
                list.forEach { FoodRow(it, counting) { vm.selected = it } }
            } else {
                if (vm.local.isNotEmpty()) {
                    SectionTitle("Your foods")
                    vm.local.forEach { FoodRow(it, counting) { vm.selected = it } }
                }
                SectionTitle("Open Food Facts")
                when (val r = vm.remote) {
                    is OffResult.Ok -> {
                        if (r.value.isEmpty()) Text("No matches. Try fewer words, or add it yourself.",
                            style = MaterialTheme.typography.bodyMedium, color = TextMuted)
                        r.value.forEach { FoodRow(it, counting) { vm.selected = it } }
                    }
                    is OffResult.Busy -> NoticeCard(
                        "Search is paused for ${r.retryInSec} s. The food database is free and shared, " +
                            "so the app keeps its searches gentle."
                    )
                    OffResult.Offline -> NoticeCard("You're offline. Only foods you've saved are searchable right now.")
                    is OffResult.Failed -> NoticeCard("Search didn't work (${r.message}). Try again in a moment.")
                    OffResult.NotFound, null -> Unit
                }
            }

            TextButton(onClick = { showCustom = true }, modifier = Modifier.fillMaxWidth()) {
                Text("Can't find it? Add your own food", color = Gold)
            }
            Text(
                "Food data from Open Food Facts, available under the Open Database License (ODbL).",
                style = MaterialTheme.typography.bodySmall,
                color = TextDim,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(LocalBottomBarClearance.current + 16.dp))
        }
    }

    vm.selected?.let { food ->
        FoodAmountDialog(
            food = food,
            initialType = mealType,
            counting = counting,
            imperial = user?.unitSystem == "imperial",
            onSave = { type, grams, quality -> vm.logFood(food, grams, type, quality) { vm.selected = null; onDone() } },
            onDismiss = { vm.selected = null }
        )
    }
    vm.unknownBarcode?.let { code ->
        CustomFoodDialog(
            title = "Not in the database yet",
            message = "Barcode $code isn't in Open Food Facts. Add it once and it's here next time.",
            askCalories = counting,
            onSave = { name, kcal -> vm.createCustom(name, kcal, code) },
            onDismiss = { vm.unknownBarcode = null }
        )
    }
    if (showCustom) {
        CustomFoodDialog(
            title = "Add your own food",
            message = null,
            askCalories = counting,
            onSave = { name, kcal -> showCustom = false; vm.createCustom(name, kcal, null) },
            onDismiss = { showCustom = false }
        )
    }
    if (showQuick) {
        MealLogDialog(
            existing = null,
            initialType = mealType,
            onSave = { type, name, quality, _ -> showQuick = false; vm.quickLog(type, name, quality, onDone) },
            onDelete = null,
            onDismiss = { showQuick = false }
        )
    }
    if (showManualBarcode) {
        ManualBarcodeDialog(
            onSubmit = { showManualBarcode = false; vm.onBarcode(it) },
            onDismiss = { showManualBarcode = false }
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text.uppercase(), style = MaterialTheme.typography.labelSmall, color = TextMuted,
        modifier = Modifier.padding(start = 4.dp, top = 6.dp))
}

@Composable
private fun NoticeCard(text: String, onDismiss: (() -> Unit)? = null) {
    GlassCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), tint = Amber) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(text, style = MaterialTheme.typography.bodySmall, color = TextPrimary, modifier = Modifier.weight(1f))
            if (onDismiss != null) IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, "Dismiss", tint = TextMuted) }
        }
    }
}

@Composable
private fun FoodRow(food: FoodItemEntity, counting: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(GlassFillBottom)
            .clickable(onClickLabel = "Log ${food.name}", onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(food.name, style = MaterialTheme.typography.bodyMedium, color = TextPrimary,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            val sub = listOfNotNull(food.brand, "Your food".takeIf { food.source == FoodItemEntity.SOURCE_CUSTOM })
            if (sub.isNotEmpty()) Text(sub.joinToString(" · "), style = MaterialTheme.typography.bodySmall,
                color = TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (counting && food.kcal100 != null) {
            Text("${food.kcal100.roundToInt()} kcal/100g", style = MaterialTheme.typography.labelMedium, color = TextMuted)
            Spacer(Modifier.width(10.dp))
        }
        NutriScoreBadge(food.nutriScore)
    }
}

/** Official Nutri-Score colours; nothing drawn when the grade is unknown. */
@Composable
fun NutriScoreBadge(grade: String?, modifier: Modifier = Modifier) {
    val color = when (grade) {
        "a" -> Color(0xFF038141); "b" -> Color(0xFF85BB2F); "c" -> Color(0xFFFECB02)
        "d" -> Color(0xFFEE8100); "e" -> Color(0xFFE63E11); else -> return
    }
    Box(
        modifier.size(26.dp).clip(RoundedCornerShape(7.dp)).background(color),
        contentAlignment = Alignment.Center
    ) {
        Text(grade.uppercase(), color = if (grade == "c") Color(0xFF1A1A1A) else Color.White,
            fontSize = 14.sp, style = MaterialTheme.typography.titleSmall)
    }
}

@Composable
private fun ManualBarcodeDialog(onSubmit: (String) -> Unit, onDismiss: () -> Unit) {
    var code by remember { mutableStateOf("") }
    GlassDialog(onDismiss = onDismiss, accent = Gold) {
        Text("TYPE THE BARCODE", style = MaterialTheme.typography.labelSmall, color = Gold)
        Spacer(Modifier.height(4.dp))
        Text("Scanner isn't available", style = MaterialTheme.typography.headlineSmall, color = TextPrimary)
        Spacer(Modifier.height(8.dp))
        Text("It needs Google Play services and a camera. You can type the numbers under the bars instead.",
            style = MaterialTheme.typography.bodyMedium, color = TextMuted)
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = code,
            onValueChange = { code = it.filter(Char::isDigit).take(14) },
            placeholder = { Text("e.g. 3017620422003", color = TextDim) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = glassFieldColors(Gold)
        )
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GhostButton("Cancel", onDismiss, Modifier.weight(1f))
            GlowButton("Look up", { onSubmit(code) }, Modifier.weight(1f), accent = Gold,
                enabled = code.length >= 8, height = 52.dp)
        }
    }
}

@Composable
private fun CustomFoodDialog(
    title: String,
    message: String?,
    askCalories: Boolean,
    onSave: (name: String, kcal100: Float?) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var kcal by remember { mutableStateOf("") }
    GlassDialog(onDismiss = onDismiss, accent = Gold) {
        Text("YOUR FOOD", style = MaterialTheme.typography.labelSmall, color = Gold)
        Spacer(Modifier.height(4.dp))
        Text(title, style = MaterialTheme.typography.headlineSmall, color = TextPrimary)
        if (message != null) {
            Spacer(Modifier.height(8.dp))
            Text(message, style = MaterialTheme.typography.bodyMedium, color = TextMuted)
        }
        Spacer(Modifier.height(16.dp))
        FieldLabel("Name")
        OutlinedTextField(
            value = name, onValueChange = { name = it.take(60) },
            placeholder = { Text("e.g. Grandma's lentil soup", color = TextDim) },
            singleLine = true, modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp), colors = glassFieldColors(Gold)
        )
        if (askCalories) {
            Spacer(Modifier.height(12.dp))
            FieldLabel("Calories per 100 g (optional)")
            OutlinedTextField(
                value = kcal, onValueChange = { kcal = it.filter { c -> c.isDigit() || c == '.' }.take(5) },
                singleLine = true, suffix = { Text("kcal", color = TextMuted) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
                colors = glassFieldColors(Gold)
            )
        }
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GhostButton("Cancel", onDismiss, Modifier.weight(1f))
            GlowButton("Next", { onSave(name.trim(), kcal.toFloatOrNull()?.takeIf { it in 0f..900f }) },
                Modifier.weight(1f), accent = Gold, enabled = name.isNotBlank(), height = 52.dp)
        }
    }
}
