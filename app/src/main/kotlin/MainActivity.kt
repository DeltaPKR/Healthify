package com.healthify.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.health.connect.client.PermissionController
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseInOutCubic
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.*
import com.healthify.app.ui.checkin.CheckInScreen
import com.healthify.app.ui.dashboard.DashBottomNav
import com.healthify.app.ui.dashboard.DashboardScreen
import com.healthify.app.ui.dashboard.DashboardViewModel
import com.healthify.app.ui.insights.InsightsScreen
import com.healthify.app.ui.notifications.NotificationsScreen
import com.healthify.app.ui.onboarding.OnboardingScreen
import com.healthify.app.ui.onboarding.OnboardingViewModel
import com.healthify.app.ui.profile.ProfileScreen
import com.healthify.app.ui.profile.ProfileViewModel
import com.healthify.app.ui.theme.BgDark
import com.healthify.app.ui.theme.Green
import com.healthify.app.ui.theme.HealthifyTheme
import com.healthify.app.ui.theme.TextPrimary

private const val KEY_HC_PERMS_ASKED   = "hc_perms_asked"
private const val KEY_NOTIF_PERM_ASKED = "notif_perm_asked"

// ── Navigation routes ────────────────────────────────────────────────────────
object Routes {
    const val ONBOARDING    = "onboarding"
    const val DASHBOARD     = "dashboard"
    const val CHECK_IN      = "checkin"
    const val INSIGHTS      = "insights"
    const val NOTIFICATIONS = "notifications"
    const val PROFILE       = "profile"
}

// Routes that show the bottom nav. Onboarding and check-in are flows → hidden there.
private val TAB_ROUTES = setOf(
    Routes.DASHBOARD,
    Routes.INSIGHTS,
    Routes.NOTIFICATIONS,
    Routes.PROFILE
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            HealthifyTheme {
                HealthifyNavGraph()
            }
        }
    }
}

@Composable
fun HealthifyNavGraph() {
    val app  = HealthifyApp.instance
    val repo = app.repository

    // Decide the start destination before drawing anything so already-onboarded
    // users don't briefly see the registration screen.
    var resolvedRoute by remember { mutableStateOf<String?>(null) }
    var splashTimeElapsed by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val user = repo.getUserOnce()
        resolvedRoute = if (user?.onboardingComplete == true) Routes.DASHBOARD else Routes.ONBOARDING
    }
    LaunchedEffect(Unit) {
        delay(SPLASH_MIN_DURATION_MS)
        splashTimeElapsed = true
    }

    val startRoute = resolvedRoute
    if (startRoute == null || !splashTimeElapsed) {
        SplashScreen()
        return
    }

    val navController = rememberNavController()

    // Observe the current destination so we know which tab is active
    // and whether to show the bottom nav at all.
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    val showBottomNav = currentRoute in TAB_ROUTES
    val selectedTab = when (currentRoute) {
        Routes.INSIGHTS      -> "insights"
        Routes.NOTIFICATIONS -> "reminders"
        Routes.PROFILE       -> "profile"
        else                 -> "home"
    }

    Scaffold(
        containerColor = BgDark,
        bottomBar = {
            if (showBottomNav) {
                DashBottomNav(
                    selectedTab     = selectedTab,
                    onHome          = { navController.navigateTab(Routes.DASHBOARD) },
                    onInsights      = { navController.navigateTab(Routes.INSIGHTS) },
                    onCheckIn       = { navController.navigate(Routes.CHECK_IN) },
                    onNotifications = { navController.navigateTab(Routes.NOTIFICATIONS) },
                    onProfile       = { navController.navigateTab(Routes.PROFILE) }
                )
            }
        }
    ) { innerPadding ->
        NavHost(
            navController    = navController,
            startDestination = startRoute,
            modifier         = Modifier.padding(innerPadding)
        ) {
            composable(Routes.ONBOARDING) {
                val vm: OnboardingViewModel = viewModel(
                    factory = OnboardingViewModel.Factory(repo)
                )
                OnboardingScreen(
                    viewModel  = vm,
                    onComplete = {
                        navController.navigate(Routes.DASHBOARD) {
                            popUpTo(Routes.ONBOARDING) { inclusive = true }
                        }
                    }
                )
            }

            composable(Routes.DASHBOARD) {
                val vm: DashboardViewModel = viewModel(
                    factory = DashboardViewModel.Factory(
                        repo                 = repo,
                        healthConnectManager = app.healthConnectManager
                    )
                )

                // ── Health Connect & notification permission flow ─────────
                // Ask at most once per install. Persisting the flags in
                // SharedPreferences means cold launches don't re-prompt.
                val hc = app.healthConnectManager
                val ctx = LocalContext.current
                val permLauncher = rememberLauncherForActivityResult(
                    contract = PermissionController.createRequestPermissionResultContract()
                ) {
                    vm.load()
                }
                val notifLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission()
                ) { /* nothing extra to do; channels are already created */ }
                LaunchedEffect(Unit) {
                    val prefs = ctx.getSharedPreferences("healthify_prefs", Context.MODE_PRIVATE)

                    // Notifications: Android 13+ requires runtime permission.
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        val notifGranted = ContextCompat.checkSelfPermission(
                            ctx, Manifest.permission.POST_NOTIFICATIONS
                        ) == PackageManager.PERMISSION_GRANTED
                        val notifAsked = prefs.getBoolean(KEY_NOTIF_PERM_ASKED, false)
                        if (!notifGranted && !notifAsked) {
                            prefs.edit().putBoolean(KEY_NOTIF_PERM_ASKED, true).apply()
                            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }

                    // Health Connect.
                    val hcAsked = prefs.getBoolean(KEY_HC_PERMS_ASKED, false)
                    if (!hcAsked && hc.isAvailable && !hc.hasAllPermissions()) {
                        prefs.edit().putBoolean(KEY_HC_PERMS_ASKED, true).apply()
                        permLauncher.launch(hc.requiredPermissions)
                    }
                }

                DashboardScreen(
                    viewModel               = vm,
                    onNavigateCheckIn       = { navController.navigate(Routes.CHECK_IN) },
                    onNavigateInsights      = { navController.navigateTab(Routes.INSIGHTS) },
                    onNavigateNotifications = { navController.navigateTab(Routes.NOTIFICATIONS) },
                    onNavigateProfile       = { navController.navigateTab(Routes.PROFILE) }
                )
            }

            composable(Routes.CHECK_IN) {
                CheckInScreen(
                    repo          = repo,
                    healthConnect = app.healthConnectManager,
                    onComplete    = { navController.popBackStack() },
                    onBack        = { navController.popBackStack() }
                )
            }

            composable(Routes.INSIGHTS) {
                InsightsScreen(
                    repo   = repo,
                    onBack = { navController.popBackStack() }
                )
            }

            composable(Routes.NOTIFICATIONS) {
                NotificationsScreen(
                    repo    = repo,
                    context = app,
                    onBack  = { navController.popBackStack() }
                )
            }

            composable(Routes.PROFILE) {
                val vm: ProfileViewModel = viewModel(
                    factory = ProfileViewModel.Factory(repo)
                )
                ProfileScreen(
                    viewModel         = vm,
                    onBack            = { navController.popBackStack() },
                    onResetOnboarding = {
                        navController.navigate(Routes.ONBOARDING) {
                            popUpTo(Routes.DASHBOARD) { inclusive = true }
                        }
                    }
                )
            }
        }
    }
}

/**
 * Tab-style navigation: pops back to Dashboard (the tab root) while saving
 * each tab's state (scroll position, etc.) and restores it on re-entry.
 * Tapping the currently active tab is a no-op.
 */
private const val SPLASH_MIN_DURATION_MS = 1800L

@Composable
private fun SplashScreen() {
    // Intro: fade + scale-up from 0.6 → 1.0
    val scale = remember { Animatable(0.6f) }
    val alpha = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        alpha.animateTo(1f, animationSpec = tween(durationMillis = 600, easing = EaseInOutCubic))
    }
    LaunchedEffect(Unit) {
        scale.animateTo(1f, animationSpec = tween(durationMillis = 700, easing = EaseInOutCubic))
    }

    // Subtle heartbeat pulse on the logo
    val pulse = rememberInfiniteTransition(label = "pulse")
    val pulseScale by pulse.animateFloat(
        initialValue = 1f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDark),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Text(
                text = "❤",
                color = Green,
                fontSize = 72.sp,
                modifier = Modifier
                    .alpha(alpha.value)
                    .scale(scale.value * pulseScale)
            )
            Text(
                text = "Healthify",
                color = TextPrimary,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .alpha(alpha.value)
                    .scale(scale.value)
            )
            Text(
                text = "Your daily wellness companion",
                color = TextPrimary,
                fontSize = 14.sp,
                modifier = Modifier.alpha(alpha.value * 0.7f)
            )
            CircularProgressIndicator(
                color = Green,
                modifier = Modifier
                    .size(36.dp)
                    .alpha(alpha.value),
                strokeWidth = 3.dp
            )
        }
    }
}

private fun NavHostController.navigateTab(route: String) {
    if (currentDestination?.route == route) return
    navigate(route) {
        popUpTo(Routes.DASHBOARD) {
            saveState = true
            inclusive = false
        }
        launchSingleTop = true
        restoreState = true
    }
}