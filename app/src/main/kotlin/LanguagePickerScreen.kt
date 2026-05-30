package com.healthify.app.ui.language

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.healthify.app.LocaleManager
import com.healthify.app.R
import com.healthify.app.ui.theme.*

// ═══════════════════════════════════════════════════════════════════════════
// SCREEN
// Shown once on first launch so the user can choose their preferred language
// before onboarding. Tapping a card calls LocaleManager.setLanguage(), which
// updates the mutableStateOf in LocaleManager → MainActivity's
// CompositionLocalProvider re-resolves the localized Context → every
// stringResource() in this screen (title, subtitle, button) immediately
// switches language with no Activity restart.
// ═══════════════════════════════════════════════════════════════════════════

@Composable
fun LanguagePickerScreen(onComplete: () -> Unit) {
    val ctx = LocalContext.current

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.radialGradient(listOf(SurfaceCard2, BgDark)))
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Spacer(Modifier.height(64.dp))

            Text("🌐", fontSize = 56.sp)
            Spacer(Modifier.height(20.dp))

            Text(
                stringResource(R.string.lang_picker_title),
                style     = MaterialTheme.typography.headlineLarge,
                color     = TextPrimary,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.lang_picker_desc),
                color     = TextMuted,
                textAlign = TextAlign.Center,
                style     = MaterialTheme.typography.bodyMedium
            )

            Spacer(Modifier.height(40.dp))

            LocaleManager.SUPPORTED.forEach { lang ->
                LanguageCard(
                    flag     = LocaleManager.FLAG[lang]        ?: "",
                    name     = LocaleManager.NATIVE_NAME[lang] ?: lang,
                    selected = LocaleManager.currentLanguage == lang,
                    onClick  = { LocaleManager.setLanguage(ctx, lang) }
                )
                Spacer(Modifier.height(12.dp))
            }

            Spacer(Modifier.height(32.dp))

            Button(
                onClick  = onComplete,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Green),
                shape  = RoundedCornerShape(16.dp)
            ) {
                Text(
                    stringResource(R.string.lang_picker_btn),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }

            Spacer(Modifier.height(48.dp))
        }
    }
}

@Composable
private fun LanguageCard(
    flag: String,
    name: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) GreenDim else SurfaceCard)
            .border(1.5.dp, if (selected) Green else Divider, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(flag, fontSize = 28.sp)
        Text(
            name,
            style    = MaterialTheme.typography.titleLarge,
            color    = if (selected) Green else TextPrimary,
            modifier = Modifier.weight(1f)
        )
        if (selected) {
            Text("✓", color = Green, fontSize = 20.sp)
        }
    }
}
