package com.healthify.app.ui.move

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.healthify.app.health.WorkoutHealthSync
import com.healthify.app.ui.theme.*
import kotlinx.coroutines.launch

/**
 * The "save workouts to Health Connect" switch: its state, and turning it
 * on, which asks for the exercise write permission on its own. Re-read on
 * resume, since access can be removed in Health Connect at any time.
 */
@Stable
class HealthWriteSwitch internal constructor(val sync: WorkoutHealthSync) {
    /** Turned on and allowed. */
    var on by mutableStateOf(false); internal set
    var loaded by mutableStateOf(false); internal set
    /** The last request came back without the permission. */
    var denied by mutableStateOf(false); internal set
    internal var request: () -> Unit = {}

    fun turnOn() = request()
    fun turnOff() {
        sync.setEnabled(false)
        on = false
    }
}

@Composable
fun rememberHealthWriteSwitch(sync: WorkoutHealthSync, onTurnedOn: () -> Unit = {}): HealthWriteSwitch {
    val scope = rememberCoroutineScope()
    val state = remember(sync) { HealthWriteSwitch(sync) }
    val granted = { ok: Boolean ->
        if (ok) { sync.setEnabled(true); sync.dismissOffer(); onTurnedOn() }
        state.on = ok
        state.denied = !ok
    }
    val launcher = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) {
        granted(sync.writePermission in it)
    }
    state.request = {
        scope.launch {
            if (sync.hasPermission()) granted(true) else launcher.launch(sync.permissions)
        }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        scope.launch {
            state.on = sync.isOn()
            state.loaded = true
        }
    }
    return state
}

/** Move settings: whether workouts are saved to Health Connect. */
@Composable
fun MoveSettingsDialog(sync: WorkoutHealthSync, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val hc = rememberHealthWriteSwitch(sync)
    val available = sync.isAvailable

    GlassDialog(onDismiss = onDismiss, accent = Sky) {
        Text("MOVE SETTINGS", style = MaterialTheme.typography.labelSmall, color = Sky)
        Spacer(Modifier.height(4.dp))
        Text("Save to Health Connect", style = MaterialTheme.typography.headlineSmall, color = TextPrimary)
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Adds your workouts and logged activities to Health Connect, so your other health " +
                    "and fitness apps can see them too.",
                style = MaterialTheme.typography.bodySmall, color = TextMuted, modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(12.dp))
            Switch(
                checked = hc.on,
                enabled = available && hc.loaded,
                onCheckedChange = { if (it) hc.turnOn() else hc.turnOff() },
                colors = SwitchDefaults.colors(checkedTrackColor = Sky, checkedThumbColor = BgDark)
            )
        }

        if (!available) {
            Spacer(Modifier.height(14.dp))
            Text(
                if (sync.canInstall) "Health Connect isn't installed on this phone, or needs an update."
                else "Health Connect isn't available on this phone.",
                style = MaterialTheme.typography.bodySmall, color = Gold
            )
            if (sync.canInstall) {
                Spacer(Modifier.height(10.dp))
                GhostButton("Get Health Connect", { openHealthConnectInPlay(context) }, Modifier.fillMaxWidth())
            }
        } else {
            Spacer(Modifier.height(16.dp))
            FieldLabel("What's saved")
            InfoLine("🏋️", "Each workout's type, name, start and end time")
            InfoLine("🔒", "Not your sets, weights or calorie estimates")
            InfoLine("✏️", "Edit or delete a workout here and its copy follows")
            if (hc.on) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "Workouts you logged before turning this on are saved too. Turning it off stops " +
                        "saving new ones; what's already there stays until you delete it in Health Connect.",
                    style = MaterialTheme.typography.bodySmall, color = TextDim
                )
            }
            if (hc.denied) {
                Spacer(Modifier.height(10.dp))
                Text(
                    "Fernday wasn't allowed to add exercise. You can allow it under Fernday in " +
                        "Health Connect's app permissions.",
                    style = MaterialTheme.typography.bodySmall, color = Coral
                )
            }
        }

        Spacer(Modifier.height(22.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (available) GhostButton("Open Health Connect", { openHealthConnect(context) }, Modifier.weight(1.3f))
            GlowButton("Done", onDismiss, Modifier.weight(1f), accent = Sky, height = 52.dp)
        }
    }
}

/**
 * Asked once, in context, on the summary of a just-finished workout:
 * save it (and earlier ones) to Health Connect? Answering either way
 * hides it for good; the setting stays in Move settings.
 */
@Composable
fun HealthConnectOffer(sync: WorkoutHealthSync, modifier: Modifier = Modifier) {
    var hidden by remember { mutableStateOf(sync.offerDismissed || !sync.isAvailable) }
    var justTurnedOn by remember { mutableStateOf(false) }
    val hc = rememberHealthWriteSwitch(sync, onTurnedOn = { justTurnedOn = true })
    if (hidden || !hc.loaded || (hc.on && !justTurnedOn)) return

    GlassCard(modifier.fillMaxWidth(), tint = Sky) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconOrb(Sky, size = 44.dp) { Text(if (hc.on) "✅" else "🔗", fontSize = 20.sp) }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        if (hc.on) "Saving to Health Connect" else "Add workouts to Health Connect?",
                        style = MaterialTheme.typography.titleMedium, color = TextPrimary
                    )
                    Text(
                        when {
                            hc.on -> "This workout and the ones before it. Change it any time in Move settings."
                            hc.denied -> "Not allowed. You can turn it on later in Move settings."
                            else -> "Your other health and fitness apps can then see this workout and earlier ones: " +
                                "type, name and time only."
                        },
                        style = MaterialTheme.typography.bodySmall, color = TextMuted
                    )
                }
            }
            if (!hc.on) {
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    GhostButton("Not now", { sync.dismissOffer(); hidden = true }, Modifier.weight(1f), height = 46.dp)
                    if (!hc.denied) GlowButton("Turn on", hc::turnOn, Modifier.weight(1f), accent = Sky, height = 46.dp)
                }
            }
        }
    }
}

@Composable
private fun InfoLine(emoji: String, text: String) {
    Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(emoji, fontSize = 16.sp)
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
    }
}

private fun openHealthConnect(context: Context) {
    try {
        context.startActivity(Intent(HealthConnectClient.ACTION_HEALTH_CONNECT_SETTINGS))
    } catch (e: ActivityNotFoundException) { /* nothing to open */ }
}

private const val HC_PACKAGE = "com.google.android.apps.healthdata"

private fun openHealthConnectInPlay(context: Context) {
    val market = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$HC_PACKAGE&url=healthconnect%3A%2F%2Fonboarding"))
        .setPackage("com.android.vending")
    try {
        context.startActivity(market)
    } catch (e: ActivityNotFoundException) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$HC_PACKAGE")))
        } catch (e: ActivityNotFoundException) { /* no store or browser */ }
    }
}
