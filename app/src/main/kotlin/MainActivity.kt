@file:OptIn(ExperimentalTextApi::class)

package com.healthify.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.health.connect.client.PermissionController
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOutBack
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.ExperimentalTextApi
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
import androidx.navigation.NavType
import androidx.navigation.navArgument
import com.healthify.app.logs.MealType
import com.healthify.app.time.DayClock
import com.healthify.app.ui.food.FoodAddScreen
import com.healthify.app.ui.food.FoodAddViewModel
import com.healthify.app.ui.food.FoodScreen
import com.healthify.app.ui.food.dayLabel
import com.healthify.app.ui.insights.InsightsScreen
import com.healthify.app.ui.move.MoveScreen
import com.healthify.app.ui.workout.ExerciseDetailScreen
import com.healthify.app.ui.workout.ExerciseLibraryScreen
import com.healthify.app.ui.workout.RoutineDetailScreen
import com.healthify.app.ui.workout.RoutineEditorScreen
import com.healthify.app.ui.workout.RoutineEditorViewModel
import com.healthify.app.ui.workout.WorkoutPlayerScreen
import com.healthify.app.ui.workout.WorkoutSummaryScreen
import com.healthify.app.ui.workout.WorkoutViewModel
import com.healthify.app.units.UnitSystem
import androidx.navigation.NavBackStackEntry
import android.net.Uri
import com.healthify.app.ui.notifications.NotificationsScreen
import com.healthify.app.ui.onboarding.OnboardingScreen
import com.healthify.app.ui.onboarding.OnboardingViewModel
import com.healthify.app.ui.profile.ProfileScreen
import com.healthify.app.ui.profile.ProfileViewModel
import com.healthify.app.health.HealthPermissionRationale
import com.healthify.app.ui.theme.Aurora
import com.healthify.app.ui.theme.AuroraBackground
import com.healthify.app.ui.theme.BrandGradient
import com.healthify.app.ui.theme.GhostButton
import com.healthify.app.ui.theme.GlassDialog
import com.healthify.app.ui.theme.GlowButton
import com.healthify.app.ui.theme.IconOrb
import com.healthify.app.ui.theme.Sky
import com.healthify.app.ui.theme.Green
import com.healthify.app.ui.theme.HealthifyTheme
import com.healthify.app.ui.theme.LocalBottomBarClearance
import com.healthify.app.ui.theme.LocalReducedMotion
import com.healthify.app.ui.theme.LocalTabVisible
import com.healthify.app.ui.theme.SurfaceCard
import com.healthify.app.ui.theme.TextMuted
import com.healthify.app.ui.theme.TextPrimary
import com.healthify.app.ui.theme.radialGlow
import com.healthify.app.ui.theme.rememberReducedMotion

// Both perm flags are persisted across launches. We ask exactly once
// per install for each. If the user dismisses or denies the prompt, we
// respect that and never ask again from within the app — they can
// always grant later from system settings or the Health Connect app.
// Re-prompting on every cold launch (the previous behaviour) was
// reported as intrusive by testers, and the standard pattern for
// optional integrations is one ask at first launch.
private const val KEY_NOTIF_PERM_ASKED = "notif_perm_asked"
private const val KEY_HC_PERMS_ASKED   = "hc_perms_asked"

// ── Navigation routes ────────────────────────────────────────────────────────
object Routes {
    const val ONBOARDING = "onboarding"
    const val MAIN       = "main"
    const val CHECK_IN   = "checkin"
    const val PROFILE    = "profile"     // opened from the Home avatar
    const val REMINDERS  = "reminders"   // opened from Profile
    const val FOOD_ADD   = "food_add/{mealType}/{date}"   // search / scan into one meal slot
    const val ROUTINE      = "routine/{ref}"                   // preview + start
    const val ROUTINE_EDIT = "routine_edit/{target}"           // RoutineEditorViewModel.NEW, a ref, or a copy
    const val EXERCISES    = "exercises?pick={pick}"           // library; pick=true returns ids
    const val EXERCISE     = "exercise/{id}"
    const val WORKOUT      = "workout/{sessionId}"             // the player
    const val WORKOUT_DONE = "workout_done/{sessionId}?fresh={fresh}"

    /** savedStateHandle key the library (pick mode) returns its selection under. */
    const val PICKED = "picked_exercises"

    fun foodAdd(type: MealType, date: String) = "food_add/${type.key}/$date"
    fun routine(ref: String) = "routine/${Uri.encode(ref)}"
    fun routineEdit(target: String) = "routine_edit/${Uri.encode(target)}"
    fun exercises(pick: Boolean) = "exercises?pick=$pick"
    fun exercise(id: String) = "exercise/${Uri.encode(id)}"
    fun workout(sessionId: Long) = "workout/$sessionId"
    fun workoutDone(sessionId: Long, fresh: Boolean) = "workout_done/$sessionId?fresh=$fresh"
}

// Tab order for the swipeable pager. Index here == HorizontalPager page index.
private const val TAB_HOME     = 0
private const val TAB_FOOD     = 1
private const val TAB_MOVE     = 2
private const val TAB_INSIGHTS = 3
private const val TAB_COUNT    = 4

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // installSplashScreen() must run before super.onCreate() so the
        // framework keeps the splash drawn until our first Compose frame.
        installSplashScreen()
        // Edge-to-edge on every API level (Android 15+ forces it for
        // targetSdk 35+ anyway): the aurora backdrop runs behind both system
        // bars. Each screen consumes the insets it needs exactly once —
        // tab pages via statusBarsPadding()/TopAppBar, the bottom nav via
        // navigationBarsPadding(), full-screen flows via systemBarsPadding().
        enableEdgeToEdge(
            statusBarStyle     = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        super.onCreate(savedInstanceState)
        setContent {
            HealthifyTheme {
                CompositionLocalProvider(LocalReducedMotion provides rememberReducedMotion()) {
                    Box(Modifier.fillMaxSize()) {
                        AuroraBackground(tint = Aurora.tint)
                        HealthifyNavGraph()
                    }
                }
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
    // Unit system and calorie setting for the pushed workout screens.
    val user by repo.getUser().collectAsState(initial = null)

    NavHost(
        navController    = navController,
        startDestination = startRoute,
        enterTransition  = { fadeIn(tween(320)) },
        exitTransition   = { fadeOut(tween(220)) }
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

        // Check-in rises from the bottom like a sheet and sinks back on close.
        composable(
            Routes.CHECK_IN,
            enterTransition  = { slideInVertically(tween(420, easing = EaseOutCubic)) { it / 5 } + fadeIn(tween(320)) },
            popExitTransition = { slideOutVertically(tween(300)) { it / 5 } + fadeOut(tween(260)) }
        ) {
            CheckInScreen(
                repo          = repo,
                logRepo       = app.logRepository,
                healthConnect = app.healthConnectManager,
                onComplete    = { navController.popBackStack() },
                onBack        = { navController.popBackStack() }
            )
        }

        composable(Routes.PROFILE) {
            PushedPage {
                ProfilePage(
                    onBack            = { navController.popBackStack() },
                    onOpenReminders   = { navController.navigate(Routes.REMINDERS) },
                    onResetOnboarding = {
                        navController.navigate(Routes.ONBOARDING) {
                            popUpTo(Routes.MAIN) { inclusive = true }
                        }
                    }
                )
            }
        }

        composable(
            Routes.FOOD_ADD,
            arguments = listOf(
                navArgument("mealType") { type = NavType.StringType },
                navArgument("date") { type = NavType.StringType }
            )
        ) { entry ->
            val type = MealType.of(entry.arguments?.getString("mealType").orEmpty())
            val date = entry.arguments?.getString("date") ?: DayClock.todayIso()
            val vm: FoodAddViewModel = viewModel(
                factory = FoodAddViewModel.Factory(app.foodRepository, app.logRepository, repo, date)
            )
            PushedPage {
                FoodAddScreen(vm, type, dayLabel(date), onDone = { navController.popBackStack() })
            }
        }

        // ── Move: routines, library, player ────────────────────────────────
        val workouts = app.workoutRepository
        composable(Routes.ROUTINE, arguments = listOf(navArgument("ref") { type = NavType.StringType })) { entry ->
            val ref = entry.arguments?.getString("ref").orEmpty()
            PushedPage {
                RoutineDetailScreen(
                    repo      = workouts,
                    ref       = ref,
                    onBack    = { navController.popBackStack() },
                    onStarted = { id -> navController.navigate(Routes.workout(id)) },
                    onEdit    = { target -> navController.navigate(Routes.routineEdit(target)) }
                )
            }
        }

        composable(Routes.ROUTINE_EDIT, arguments = listOf(navArgument("target") { type = NavType.StringType })) { entry ->
            val target = entry.arguments?.getString("target") ?: RoutineEditorViewModel.NEW
            val vm: RoutineEditorViewModel = viewModel(factory = RoutineEditorViewModel.Factory(workouts, target))
            OnPickedExercises(entry) { vm.addExercises(it) }
            PushedPage {
                RoutineEditorScreen(
                    vm              = vm,
                    onBack          = { navController.popBackStack() },
                    onPickExercises = { navController.navigate(Routes.exercises(pick = true)) },
                    onSaved         = { ref -> navController.navigate(Routes.routine(ref)) { popUpTo(Routes.MAIN) } }
                )
            }
        }

        composable(
            Routes.EXERCISES,
            arguments = listOf(navArgument("pick") { type = NavType.BoolType; defaultValue = false })
        ) { entry ->
            PushedPage {
                ExerciseLibraryScreen(
                    repo     = workouts,
                    pickMode = entry.arguments?.getBoolean("pick") == true,
                    onBack   = { navController.popBackStack() },
                    onOpen   = { id -> navController.navigate(Routes.exercise(id)) },
                    onPicked = { ids ->
                        navController.previousBackStackEntry?.savedStateHandle?.set(Routes.PICKED, ArrayList(ids))
                        navController.popBackStack()
                    }
                )
            }
        }

        composable(Routes.EXERCISE, arguments = listOf(navArgument("id") { type = NavType.StringType })) { entry ->
            PushedPage {
                ExerciseDetailScreen(
                    repo       = workouts,
                    exerciseId = entry.arguments?.getString("id").orEmpty(),
                    unit       = UnitSystem.of(user?.unitSystem),
                    onBack     = { navController.popBackStack() }
                )
            }
        }

        composable(Routes.WORKOUT, arguments = listOf(navArgument("sessionId") { type = NavType.LongType })) { entry ->
            val id = entry.arguments?.getLong("sessionId") ?: 0L
            val vm: WorkoutViewModel = viewModel(factory = WorkoutViewModel.Factory(workouts, id))
            OnPickedExercises(entry) { vm.addExercises(it) }
            PushedPage {
                WorkoutPlayerScreen(
                    vm             = vm,
                    unit           = UnitSystem.of(user?.unitSystem),
                    // Discarding both leaves and removes the session, which the
                    // player also reacts to: pop only while it's still on top.
                    onLeave        = { if (navController.currentBackStackEntry?.id == entry.id) navController.popBackStack() },
                    onAddExercises = { navController.navigate(Routes.exercises(pick = true)) },
                    onFinished     = { done ->
                        navController.navigate(Routes.workoutDone(done, fresh = true)) { popUpTo(Routes.MAIN) }
                    }
                )
            }
        }

        composable(
            Routes.WORKOUT_DONE,
            arguments = listOf(
                navArgument("sessionId") { type = NavType.LongType },
                navArgument("fresh") { type = NavType.BoolType; defaultValue = false }
            )
        ) { entry ->
            PushedPage {
                WorkoutSummaryScreen(
                    repo         = workouts,
                    healthSync   = app.workoutHealthSync,
                    sessionId    = entry.arguments?.getLong("sessionId") ?: 0L,
                    fresh        = entry.arguments?.getBoolean("fresh") == true,
                    unit         = UnitSystem.of(user?.unitSystem),
                    showCalories = user?.countCalories == true,
                    onDone       = { navController.popBackStack() }
                )
            }
        }

        composable(Routes.REMINDERS) {
            PushedPage {
                NotificationsScreen(
                    repo    = repo,
                    context = app,
                    onBack  = { navController.popBackStack() }
                )
            }
        }
    }
}

/** Hands exercise ids picked in the library (pick mode) to this destination once. */
@Composable
private fun OnPickedExercises(entry: NavBackStackEntry, onPicked: (List<String>) -> Unit) {
    val picked by entry.savedStateHandle.getStateFlow<ArrayList<String>?>(Routes.PICKED, null).collectAsState()
    LaunchedEffect(picked) {
        val ids = picked ?: return@LaunchedEffect
        entry.savedStateHandle[Routes.PICKED] = null
        if (ids.isNotEmpty()) onPicked(ids)
    }
}

/**
 * Screens pushed over the tabs have no floating nav, so the bottom
 * clearance their content reserves is just the system navigation bar.
 */
@Composable
private fun PushedPage(content: @Composable () -> Unit) {
    val navBar = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    CompositionLocalProvider(LocalBottomBarClearance provides navBar, content = content)
}

/**
 * Hosts the four tab screens in a HorizontalPager so the user can swipe
 * left/right to switch tabs (Instagram-style). The floating bottom nav
 * drives the pager and its selection pill follows the swipe.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MainTabs(navController: NavHostController) {
    val app  = HealthifyApp.instance
    val repo = app.repository

    // Same owner + factory as DashboardPage, so this is the same instance —
    // the nav's check-in orb reads cooldown state from it.
    val dashVm: DashboardViewModel = viewModel(
        factory = DashboardViewModel.Factory(
            repo                 = repo,
            logRepo              = app.logRepository,
            healthConnectManager = app.healthConnectManager
        )
    )

    val pagerState = rememberPagerState(pageCount = { TAB_COUNT })
    val scope = rememberCoroutineScope()

    // Tapping the bottom nav jumps directly to the target page — no slide
    // through the intermediate tabs. (Swipes still animate normally, since
    // they drive the pager state via its own gesture handling.)
    fun goTo(page: Int) {
        scope.launch { pagerState.scrollToPage(page) }
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

    // Tab content scrolls under the floating nav; pages end with a spacer
    // of this height (LocalBottomBarClearance) so nothing is stuck behind it.
    val density = LocalDensity.current
    var navHeightPx by remember { mutableIntStateOf(0) }
    val clearance = with(density) { navHeightPx.toDp() }

    Box(Modifier.fillMaxSize()) {
        CompositionLocalProvider(LocalViewConfiguration provides pagerViewConfig) {
            HorizontalPager(
                state    = pagerState,
                modifier = Modifier.fillMaxSize(),
                // Keep the neighbouring page composed so swipes don't briefly
                // flash an empty side while the next screen warms up.
                beyondBoundsPageCount = 1,
                key      = { it }
            ) { page ->
                CompositionLocalProvider(
                    LocalViewConfiguration provides childViewConfig,
                    LocalBottomBarClearance provides clearance,
                    LocalTabVisible provides (pagerState.currentPage == page)
                ) {
                    when (page) {
                        TAB_HOME     -> DashboardPage(
                            vm                = dashVm,
                            onNavigateCheckIn = { navController.navigate(Routes.CHECK_IN) },
                            onNavigateProfile = { navController.navigate(Routes.PROFILE) }
                        )
                        TAB_FOOD     -> FoodScreen(
                            logRepo   = app.logRepository,
                            repo      = repo,
                            onBack    = { goTo(TAB_HOME) },
                            onAddFood = { type, date -> navController.navigate(Routes.foodAdd(type, date)) }
                        )
                        TAB_MOVE     -> MoveScreen(
                            logRepo         = app.logRepository,
                            workoutRepo     = app.workoutRepository,
                            healthSync      = app.workoutHealthSync,
                            stepsToday      = dashVm.uiState.stepsToday,
                            stepGoal        = dashVm.uiState.user?.stepGoal ?: 10_000,
                            healthConnected = dashVm.uiState.healthConnectConnected,
                            showCalories    = dashVm.uiState.user?.countCalories == true,
                            onBack          = { goTo(TAB_HOME) },
                            onOpenRoutine   = { ref -> navController.navigate(Routes.routine(ref)) },
                            onNewRoutine    = { navController.navigate(Routes.routineEdit(RoutineEditorViewModel.NEW)) },
                            onOpenLibrary   = { navController.navigate(Routes.exercises(pick = false)) },
                            onOpenWorkout   = { id -> navController.navigate(Routes.workout(id)) },
                            onOpenSummary   = { id -> navController.navigate(Routes.workoutDone(id, fresh = false)) }
                        )
                        TAB_INSIGHTS -> InsightsScreen(
                            repo   = repo,
                            onBack = { goTo(TAB_HOME) }
                        )
                    }
                }
            }
        }

        DashBottomNav(
            selectedPage    = pagerState.currentPage,
            pagePosition    = { pagerState.currentPage + pagerState.currentPageOffsetFraction },
            canCheckIn      = dashVm.uiState.canCheckIn,
            ready           = dashVm.uiState.loaded,
            onHome          = { goTo(TAB_HOME) },
            onFood          = { goTo(TAB_FOOD) },
            onCheckIn       = { navController.navigate(Routes.CHECK_IN) },
            onMove          = { goTo(TAB_MOVE) },
            onInsights      = { goTo(TAB_INSIGHTS) },
            modifier        = Modifier
                .align(Alignment.BottomCenter)
                .onSizeChanged { navHeightPx = it.height }
        )
    }
}

@Composable
private fun DashboardPage(
    vm: DashboardViewModel,
    onNavigateCheckIn: () -> Unit,
    onNavigateProfile: () -> Unit
) {
    val app = HealthifyApp.instance

    // ── Health Connect & notification permission flow ─────────────────────
    // Both permissions are requested exactly once per install, on the
    // first launch where they are missing. SharedPreferences flags
    // (KEY_NOTIF_PERM_ASKED, KEY_HC_PERMS_ASKED) persist the "we have
    // asked" state across cold restarts so a user who dismissed either
    // prompt is not re-prompted on every launch. Once the perms ARE
    // granted, the launcher's gate (`hasAllPermissions()` / runtime
    // perm check) short-circuits and nothing fires regardless.
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

    // Health Connect policy requires the user to understand WHY each data
    // type is read before the system permission sheet appears. We show our
    // own rationale first; the sheet is only launched if they tap Continue.
    var showHcRationale by remember { mutableStateOf(false) }

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

        // Health Connect: ask once per install. The flag is flipped to
        // true BEFORE the rationale is shown so that a failure mid-flight
        // (e.g. the HC module changing state) can't loop into re-asking on
        // every subsequent recomposition or cold start.
        val hcAsked = prefs.getBoolean(KEY_HC_PERMS_ASKED, false)
        if (!hcAsked && hc.isAvailable && !hc.hasAllPermissions()) {
            prefs.edit().putBoolean(KEY_HC_PERMS_ASKED, true).apply()
            showHcRationale = true
        }
    }

    if (showHcRationale) {
        HealthConnectRationaleDialog(
            rationales = hc.permissionRationales,
            onDismiss  = { showHcRationale = false },
            onContinue = {
                showHcRationale = false
                permLauncher.launch(hc.requiredPermissions)
            }
        )
    }

    DashboardScreen(
        viewModel               = vm,
        onNavigateCheckIn       = onNavigateCheckIn,
        onNavigateProfile       = onNavigateProfile,
        // Re-opens the same rationale the first-launch flow shows, so a user
        // who tapped "Not now" (or never saw it) can still connect later.
        onConnectHealthConnect  = { showHcRationale = true }
    )
}

/**
 * Pre-permission rationale for Health Connect.
 *
 * Shown before the system permission sheet so the user (and a Play
 * reviewer) can see exactly which data types the app reads and what each
 * one is used for. The list is [HealthConnectManager.permissionRationales],
 * which is also the source the manifest declaration and the Play Console
 * Health Apps Declaration are kept in sync with — so this dialog can never
 * silently drift from the permissions actually requested.
 */
@Composable
private fun HealthConnectRationaleDialog(
    rationales: List<HealthPermissionRationale>,
    onDismiss: () -> Unit,
    onContinue: () -> Unit
) {
    GlassDialog(onDismiss = onDismiss, accent = Sky) {
        IconOrb(Sky, size = 56.dp) { Text("🔗", fontSize = 26.sp) }
        Spacer(Modifier.height(16.dp))
        Text(
            "Connect your health data",
            style = MaterialTheme.typography.headlineSmall,
            color = TextPrimary
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Healthify reads two things from Health Connect, so your " +
            "dashboard and wellness score reflect what you actually did " +
            "today instead of what you had to type in:",
            style = MaterialTheme.typography.bodyMedium,
            color = TextMuted
        )
        Spacer(Modifier.height(16.dp))
        rationales.forEach { r ->
            Row(
                Modifier.fillMaxWidth().padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconOrb(Sky) { Text(r.emoji, fontSize = 20.sp) }
                Column(Modifier.weight(1f)) {
                    Text(
                        r.dataType,
                        style = MaterialTheme.typography.titleSmall,
                        color = TextPrimary
                    )
                    Text(
                        r.purpose,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted
                    )
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "Read-only. Your readings stay on this device; only the daily " +
            "totals you check in with are synced. You grant each type " +
            "separately on the next screen, and you can revoke them any " +
            "time in Health Connect. Saving your workouts to Health " +
            "Connect is a separate choice, in Move settings.",
            style = MaterialTheme.typography.bodySmall,
            color = TextMuted
        )
        Spacer(Modifier.height(22.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GhostButton("Not now", onDismiss, Modifier.weight(1f))
            GlowButton("Continue", onContinue, Modifier.weight(1f), height = 52.dp)
        }
    }
}

@Composable
private fun ProfilePage(
    onBack: () -> Unit,
    onOpenReminders: () -> Unit,
    onResetOnboarding: () -> Unit
) {
    val repo = HealthifyApp.instance.repository
    val vm: ProfileViewModel = viewModel(
        factory = ProfileViewModel.Factory(repo)
    )
    ProfileScreen(
        viewModel         = vm,
        onBack            = onBack,
        onOpenReminders   = onOpenReminders,
        onResetOnboarding = onResetOnboarding
    )
}

// Long enough for the logo to land, short enough not to tax a daily open.
private const val SPLASH_MIN_DURATION_MS = 700L

@Composable
private fun SplashScreen() {
    val reduced = LocalReducedMotion.current
    val intro = remember { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(Unit) {
        intro.animateTo(1f, animationSpec = tween(durationMillis = 520, easing = EaseOutBack))
    }
    // Two heartbeat ripples expanding out of the logo, half a cycle apart.
    val ripple = rememberInfiniteTransition(label = "splashRipple").animateFloat(
        initialValue = 0f,
        targetValue  = 1f,
        animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing)),
        label = "ripple"
    )

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(Modifier.size(170.dp), contentAlignment = Alignment.Center) {
                if (!reduced) {
                    Canvas(Modifier.fillMaxSize()) {
                        for (k in 0..1) {
                            val p = (ripple.value + k * 0.5f) % 1f
                            drawCircle(
                                Green.copy(alpha = (1f - p) * 0.4f * intro.value),
                                radius = size.minDimension / 2f * (0.42f + 0.58f * p),
                                style = Stroke(2.dp.toPx())
                            )
                        }
                    }
                }
                Image(
                    painter = painterResource(R.drawable.ic_heart),
                    contentDescription = null,
                    modifier = Modifier
                        .size(92.dp)
                        .radialGlow(Green, alpha = 0.35f, scale = 1.3f)
                        .graphicsLayer {
                            val s = 0.6f + 0.4f * intro.value
                            scaleX = s; scaleY = s
                            alpha = intro.value.coerceIn(0f, 1f)
                        }
                )
            }
            Text(
                text = "Healthify",
                style = MaterialTheme.typography.headlineLarge.copy(brush = BrandGradient, fontSize = 36.sp),
                modifier = Modifier.graphicsLayer {
                    alpha = intro.value.coerceIn(0f, 1f)
                    translationY = (1f - intro.value) * 12.dp.toPx()
                }
            )
            Text(
                text = "Your daily wellness companion",
                style = MaterialTheme.typography.bodyMedium,
                color = TextMuted,
                modifier = Modifier.graphicsLayer { alpha = intro.value.coerceIn(0f, 1f) * 0.9f }
            )
        }
    }
}
