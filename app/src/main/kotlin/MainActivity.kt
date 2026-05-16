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
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
    const val ONBOARDING = "onboarding"
    const val MAIN       = "main"
    const val CHECK_IN   = "checkin"
}

// Tab order for the swipeable pager. Index here == HorizontalPager page index.
private const val TAB_HOME      = 0
private const val TAB_INSIGHTS  = 1
private const val TAB_REMINDERS = 2
private const val TAB_PROFILE   = 3
private const val TAB_COUNT     = 4

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
        resolvedRoute = if (user?.onboardingComplete == true) Routes.MAIN else Routes.ONBOARDING
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

    NavHost(
        navController    = navController,
        startDestination = startRoute
    ) {
        composable(Routes.ONBOARDING) {
            val vm: OnboardingViewModel = viewModel(
                factory = OnboardingViewModel.Factory(repo)
            )
            OnboardingScreen(
                viewModel  = vm,
                onComplete = {
                    navController.navigate(Routes.MAIN) {
                        popUpTo(Routes.ONBOARDING) { inclusive = true }
                    }
                }
            )
        }

        composable(Routes.MAIN) {
            MainTabs(navController = navController)
        }

        composable(Routes.CHECK_IN) {
            CheckInScreen(
                repo          = repo,
                healthConnect = app.healthConnectManager,
                onComplete    = { navController.popBackStack() },
                onBack        = { navController.popBackStack() }
            )
        }
    }
}

/**
 * Hosts the four tab screens in a HorizontalPager so the user can swipe
 * left/right to switch tabs (Instagram-style). The bottom nav drives the
 * pager and stays visually in sync with the current page.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MainTabs(navController: NavHostController) {
    val app  = HealthifyApp.instance
    val repo = app.repository

    val pagerState = rememberPagerState(pageCount = { TAB_COUNT })
    val scope = rememberCoroutineScope()

    // Tapping the bottom nav jumps directly to the target page — no slide
    // through the intermediate tabs. (Swipes still animate normally, since
    // they drive the pager state via its own gesture handling.)
    fun goTo(page: Int) {
        scope.launch { pagerState.scrollToPage(page) }
    }

    val selectedTab = when (pagerState.currentPage) {
        TAB_INSIGHTS  -> "insights"
        TAB_REMINDERS -> "reminders"
        TAB_PROFILE   -> "profile"
        else          -> "home"
    }

    // Two-tier touch slop tuned for an Instagram-style direction lock.
    //   • children (LazyColumn etc.) use 1.5× the default slop so vertical
    //     scrolling has a small threshold but engages fairly quickly.
    //   • the pager uses 3.5× the default slop so the drag must be clearly
    //     horizontal before a page change begins.
    // The ~2.3:1 ratio means anything more than ~23° off horizontal resolves
    // as vertical scroll — close to what Instagram's feed/profile uses.
    val defaultViewConfig = LocalViewConfiguration.current
    val childViewConfig = remember(defaultViewConfig) {
        object : ViewConfiguration by defaultViewConfig {
            override val touchSlop: Float = defaultViewConfig.touchSlop * 1.5f
        }
    }
    val pagerViewConfig = remember(defaultViewConfig) {
        object : ViewConfiguration by defaultViewConfig {
            override val touchSlop: Float = defaultViewConfig.touchSlop * 3.5f
        }
    }

    Scaffold(
        containerColor = BgDark,
        bottomBar = {
            DashBottomNav(
                selectedTab     = selectedTab,
                onHome          = { goTo(TAB_HOME) },
                onInsights      = { goTo(TAB_INSIGHTS) },
                onCheckIn       = { navController.navigate(Routes.CHECK_IN) },
                onNotifications = { goTo(TAB_REMINDERS) },
                onProfile       = { goTo(TAB_PROFILE) }
            )
        }
    ) { innerPadding ->
        CompositionLocalProvider(LocalViewConfiguration provides pagerViewConfig) {
            HorizontalPager(
                state    = pagerState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                // Keep the neighbouring page composed so swipes don't briefly
                // flash an empty side while the next screen warms up.
                beyondBoundsPageCount = 1,
                key      = { it }
            ) { page ->
                CompositionLocalProvider(LocalViewConfiguration provides childViewConfig) {
                    when (page) {
                        TAB_HOME      -> DashboardPage(
                            onNavigateCheckIn       = { navController.navigate(Routes.CHECK_IN) },
                            onNavigateInsights      = { goTo(TAB_INSIGHTS) },
                            onNavigateNotifications = { goTo(TAB_REMINDERS) },
                            onNavigateProfile       = { goTo(TAB_PROFILE) }
                        )
                        TAB_INSIGHTS  -> InsightsScreen(
                            repo   = repo,
                            onBack = { goTo(TAB_HOME) }
                        )
                        TAB_REMINDERS -> NotificationsScreen(
                            repo    = repo,
                            context = app,
                            onBack  = { goTo(TAB_HOME) }
                        )
                        TAB_PROFILE   -> ProfilePage(
                            onBack            = { goTo(TAB_HOME) },
                            onResetOnboarding = {
                                navController.navigate(Routes.ONBOARDING) {
                                    popUpTo(Routes.MAIN) { inclusive = true }
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DashboardPage(
    onNavigateCheckIn: () -> Unit,
    onNavigateInsights: () -> Unit,
    onNavigateNotifications: () -> Unit,
    onNavigateProfile: () -> Unit
) {
    val app = HealthifyApp.instance
    val repo = app.repository

    val vm: DashboardViewModel = viewModel(
        factory = DashboardViewModel.Factory(
            repo                 = repo,
            healthConnectManager = app.healthConnectManager
        )
    )

    // ── Health Connect & notification permission flow ─────────────────────
    // Ask at most once per install. Persisting the flags in SharedPreferences
    // means cold launches don't re-prompt.
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
        onNavigateCheckIn       = onNavigateCheckIn,
        onNavigateInsights      = onNavigateInsights,
        onNavigateNotifications = onNavigateNotifications,
        onNavigateProfile       = onNavigateProfile
    )
}

@Composable
private fun ProfilePage(
    onBack: () -> Unit,
    onResetOnboarding: () -> Unit
) {
    val repo = HealthifyApp.instance.repository
    val vm: ProfileViewModel = viewModel(
        factory = ProfileViewModel.Factory(repo)
    )
    ProfileScreen(
        viewModel         = vm,
        onBack            = onBack,
        onResetOnboarding = onResetOnboarding
    )
}

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
            Image(
                painter = painterResource(R.drawable.ic_heart),
                contentDescription = null,
                modifier = Modifier
                    .size(120.dp)
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
