package com.example.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionConfig
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.recording.RecordingPhase
import com.example.ui.screens.AdvancedSettingsScreen
import com.example.ui.screens.AudioStudioScreen
import com.example.ui.screens.HomeDashboardScreen
import com.example.ui.screens.MediaLibraryScreen
import com.example.ui.screens.PerformanceMonitorScreen
import com.example.ui.screens.PermissionsPrivacyScreen
import com.example.ui.screens.RecordingSettingsScreen
import com.example.ui.screens.VideoPlayerScreen
import com.example.ui.theme.CarbonSurface
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.GraphiteBase
import com.example.ui.theme.ObsidianVoid
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.viewmodel.RecorderViewModel

object Routes {
    const val HOME = "home_dashboard"
    const val AUDIO_STUDIO = "audio_studio"
    const val LIBRARY = "media_library"
    const val SETTINGS = "recording_settings"
    const val PERFORMANCE = "performance_monitor"
    const val ADVANCED = "advanced_settings"
    const val PERMISSIONS = "permissions_privacy"
    const val PLAYER = "video_player"
}

private data class TopDestination(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

private val PrimaryDestinations = listOf(
    TopDestination(Routes.HOME, "Home", Icons.Filled.Home, Icons.Outlined.Home, "nav_home"),
    TopDestination(Routes.AUDIO_STUDIO, "Audio", Icons.Filled.GraphicEq, Icons.Outlined.GraphicEq, "nav_audio"),
    TopDestination(Routes.LIBRARY, "Library", Icons.Filled.VideoLibrary, Icons.Outlined.VideoLibrary, "nav_library"),
    TopDestination(Routes.SETTINGS, "Settings", Icons.Filled.Settings, Icons.Outlined.Settings, "nav_settings")
)

@Composable
fun ZeroRecorderApp(
    viewModel: RecorderViewModel = viewModel()
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }

    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val recordingState by viewModel.recordingState.collectAsStateWithLifecycle()
    val telemetry by viewModel.telemetry.collectAsStateWithLifecycle()
    val capabilities by viewModel.capabilities.collectAsStateWithLifecycle()
    val permissions by viewModel.permissions.collectAsStateWithLifecycle()
    val micTestState by viewModel.micTestState.collectAsStateWithLifecycle()
    val videos by viewModel.videos.collectAsStateWithLifecycle()
    val screenshots by viewModel.screenshots.collectAsStateWithLifecycle()
    val qualityTestRunning by viewModel.qualityTestRunning.collectAsStateWithLifecycle()
    val qualityTestResult by viewModel.qualityTestResult.collectAsStateWithLifecycle()
    val trimInProgress by viewModel.trimInProgress.collectAsStateWithLifecycle()
    val lastTrimResult by viewModel.lastTrimResult.collectAsStateWithLifecycle()

    var selectedVideoId by remember { mutableLongStateOf(-1L) }
    var libraryInitialTab by remember { mutableIntStateOf(0) }
    var pendingProjectionAction by remember { mutableStateOf<String?>(null) }

    // Refresh permissions & gallery whenever user returns from system settings or another app
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshPermissions()
                viewModel.refreshMediaLibrary()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Show status banners cleanly as Snackbars
    LaunchedEffect(recordingState.statusBannerMessage) {
        val msg = recordingState.statusBannerMessage
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.dismissBanner()
        }
    }

    // Official Android MediaProjection consent launcher
    val projectionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val data = result.data
        val action = pendingProjectionAction
        pendingProjectionAction = null
        if (result.resultCode == Activity.RESULT_OK && data != null) {
            if (action == "screenshot") {
                viewModel.takeStandaloneScreenshotWithProjection(result.resultCode, data)
            } else {
                viewModel.startRecordingWithProjection(result.resultCode, data)
            }
        }
    }

    fun launchMediaProjection(action: String) {
        pendingProjectionAction = action
        val mgr = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            mgr.createScreenCaptureIntent(MediaProjectionConfig.createConfigForUserChoice())
        } else {
            mgr.createScreenCaptureIntent()
        }
        projectionLauncher.launch(intent)
    }

    // Runtime permission launchers (requested only when needed)
    var launchRecordAfterPerms by remember { mutableStateOf(false) }

    val multiPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        viewModel.refreshPermissions()
        if (launchRecordAfterPerms) {
            launchRecordAfterPerms = false
            launchMediaProjection("record")
        }
    }

    val micPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {
        viewModel.refreshPermissions()
    }

    val notifPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {
        viewModel.refreshPermissions()
    }

    fun openOverlaySettings() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}")
        )
        runCatching { context.startActivity(intent) }
    }

    fun openAppSettings() {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:${context.packageName}")
        )
        runCatching { context.startActivity(intent) }
    }

    fun openDeveloperOptions() {
        val intent = Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
        runCatching { context.startActivity(intent) }
            .onFailure { openAppSettings() }
    }

    fun triggerHaptic() {
        if (settings.hapticFeedbackEnabled) {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }

    fun requestStartRecordingFlow() {
        triggerHaptic()
        val missingPerms = mutableListOf<String>()
        val wantsAudio = settings.gameAudioEnabled || settings.micEnabled || settings.teamVoiceEnabled
        if (wantsAudio && !permissions.hasMicrophone) {
            missingPerms.add(Manifest.permission.RECORD_AUDIO)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !permissions.hasNotifications) {
            missingPerms.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (missingPerms.isNotEmpty()) {
            launchRecordAfterPerms = true
            multiPermLauncher.launch(missingPerms.toTypedArray())
        } else {
            launchMediaProjection("record")
        }
    }

    fun requestScreenshotFlow() {
        triggerHaptic()
        if (recordingState.isActiveOrPaused) {
            viewModel.takeScreenshotDuringRecording()
        } else {
            launchMediaProjection("screenshot")
        }
    }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: Routes.HOME
    val showPrimaryNav = currentRoute in PrimaryDestinations.map { it.route }

    fun navigateTop(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianVoid)
    ) {
        val useNavRail = maxWidth >= 600.dp

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = ObsidianVoid,
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            bottomBar = {
                if (showPrimaryNav && !useNavRail) {
                    NavigationBar(
                        containerColor = GraphiteBase,
                        tonalElevation = 4.dp
                    ) {
                        PrimaryDestinations.forEach { dest ->
                            val selected = currentRoute == dest.route
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navigateTop(dest.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                        contentDescription = dest.label
                                    )
                                },
                                label = { Text(dest.label) },
                                modifier = Modifier.testTag(dest.testTag),
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = CyberCyan,
                                    selectedTextColor = TextPrimary,
                                    indicatorColor = ElectricBlue.copy(alpha = 0.22f),
                                    unselectedIconColor = TextMuted,
                                    unselectedTextColor = TextMuted
                                )
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (showPrimaryNav && useNavRail) {
                    NavigationRail(
                        containerColor = GraphiteBase
                    ) {
                        PrimaryDestinations.forEach { dest ->
                            val selected = currentRoute == dest.route
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateTop(dest.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                        contentDescription = dest.label
                                    )
                                },
                                label = { Text(dest.label) },
                                modifier = Modifier.testTag(dest.testTag),
                                colors = NavigationRailItemDefaults.colors(
                                    selectedIconColor = CyberCyan,
                                    selectedTextColor = TextPrimary,
                                    indicatorColor = ElectricBlue.copy(alpha = 0.22f),
                                    unselectedIconColor = TextMuted,
                                    unselectedTextColor = TextMuted
                                )
                            )
                        }
                    }
                }

                Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                    NavHost(
                        navController = navController,
                        startDestination = Routes.HOME
                    ) {
                        composable(Routes.HOME) {
                            HomeDashboardScreen(
                                settings = settings,
                                recordingState = recordingState,
                                telemetry = telemetry,
                                capabilities = capabilities,
                                permissions = permissions,
                                videoCount = videos.size,
                                screenshotCount = screenshots.size,
                                onRequestStartRecording = { requestStartRecordingFlow() },
                                onTogglePauseResume = {
                                    triggerHaptic()
                                    viewModel.togglePauseResume()
                                },
                                onStopRecording = {
                                    triggerHaptic()
                                    viewModel.stopRecording()
                                },
                                onRequestScreenshot = { requestScreenshotFlow() },
                                onToggleMicMute = {
                                    triggerHaptic()
                                    viewModel.toggleMicMuteLive()
                                },
                                onToggleFloatingOverlay = { enable ->
                                    if (enable && !permissions.hasOverlay) {
                                        openOverlaySettings()
                                    } else {
                                        viewModel.setFloatingOverlayEnabled(enable)
                                    }
                                },
                                onStepDownThermal = { viewModel.stepDownQualityForThermal() },
                                onOpenAudioStudio = { navigateTop(Routes.AUDIO_STUDIO) },
                                onOpenRecordingSettings = { navigateTop(Routes.SETTINGS) },
                                onOpenLibrary = {
                                    libraryInitialTab = 0
                                    navigateTop(Routes.LIBRARY)
                                },
                                onOpenPerformanceMonitor = { navController.navigate(Routes.PERFORMANCE) },
                                onOpenAdvancedSettings = { navController.navigate(Routes.ADVANCED) },
                                onOpenPermissions = { navController.navigate(Routes.PERMISSIONS) }
                            )
                        }

                        composable(Routes.AUDIO_STUDIO) {
                            AudioStudioScreen(
                                settings = settings,
                                recordingState = recordingState,
                                micTestState = micTestState,
                                capabilities = capabilities,
                                permissions = permissions,
                                onApplyPreset = { viewModel.applyAudioPreset(it) },
                                onSaveCustomPreset = { viewModel.saveCurrentAsCustomAudioPreset() },
                                onRestoreMixerDefaults = { viewModel.restoreAudioMixerDefaults() },
                                onUpdateSettings = { viewModel.updateSettings(it) },
                                onRequestMicPermission = { micPermLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                                onToggleLiveMicMonitor = { viewModel.toggleLiveMicMonitor() },
                                onStartVoiceTest = { viewModel.startFiveSecondVoiceTest() },
                                onReplayVoiceTest = { viewModel.replayVoiceTestSample() },
                                onStopMicTest = { viewModel.stopMicTest() }
                            )
                        }

                        composable(Routes.LIBRARY) {
                            MediaLibraryScreen(
                                videos = videos,
                                screenshots = screenshots,
                                mediaRepo = viewModel.mediaRepo,
                                initialTab = libraryInitialTab,
                                onRefresh = { viewModel.refreshMediaLibrary() },
                                onOpenVideoPlayer = { item ->
                                    selectedVideoId = item.id
                                    navController.navigate(Routes.PLAYER)
                                },
                                onTakeScreenshot = { requestScreenshotFlow() },
                                onShareVideo = { item ->
                                    runCatching {
                                        context.startActivity(
                                            viewModel.buildShareIntent(item.uri, "video/mp4", "Share Gameplay Video")
                                        )
                                    }
                                },
                                onRenameVideo = { item, newName -> viewModel.renameVideo(item, newName) },
                                onDeleteVideo = { item -> viewModel.deleteVideo(item) },
                                onShareScreenshot = { shot ->
                                    runCatching {
                                        context.startActivity(
                                            viewModel.buildShareIntent(shot.uri, "image/png", "Share Screenshot")
                                        )
                                    }
                                },
                                onRenameScreenshot = { shot, newName -> viewModel.renameScreenshot(shot, newName) },
                                onDeleteScreenshot = { shot -> viewModel.deleteScreenshot(shot) }
                            )
                        }

                        composable(Routes.SETTINGS) {
                            RecordingSettingsScreen(
                                settings = settings,
                                capabilities = capabilities,
                                telemetry = telemetry,
                                qualityTestRunning = qualityTestRunning,
                                qualityTestResult = qualityTestResult,
                                onSelectResolution = { viewModel.selectResolution(it) },
                                onSelectFps = { viewModel.selectFps(it) },
                                onSelectCodec = { viewModel.selectCodec(it) },
                                onApplyPreset = { viewModel.applyQualityPreset(it) },
                                onApplyRecommended = { viewModel.applyRecommendedQuality() },
                                onSetManualBitrate = { viewModel.setManualBitrate(it) },
                                onUpdateSettings = { viewModel.updateSettings(it) },
                                onRunQualityTest = { viewModel.runVideoQualityBenchmark() },
                                onOpenPerformanceMonitor = { navController.navigate(Routes.PERFORMANCE) },
                                onOpenAdvancedSettings = { navController.navigate(Routes.ADVANCED) },
                                onOpenPermissions = { navController.navigate(Routes.PERMISSIONS) }
                            )
                        }

                        composable(Routes.PERFORMANCE) {
                            PerformanceMonitorScreen(
                                settings = settings,
                                telemetry = telemetry,
                                capabilities = capabilities,
                                onBack = { navController.popBackStack() },
                                onSelectPerformanceMode = { mode ->
                                    viewModel.updateSettings { it.copy(performanceMode = mode) }
                                },
                                onStepDownThermal = { viewModel.stepDownQualityForThermal() },
                                onApplyRecommended = { viewModel.applyRecommendedQuality() }
                            )
                        }

                        composable(Routes.ADVANCED) {
                            AdvancedSettingsScreen(
                                settings = settings,
                                hasOverlayPermission = permissions.hasOverlay,
                                onBack = { navController.popBackStack() },
                                onUpdateSettings = { viewModel.updateSettings(it) },
                                onToggleFloatingOverlay = { enable ->
                                    if (enable && !permissions.hasOverlay) {
                                        openOverlaySettings()
                                    } else {
                                        viewModel.setFloatingOverlayEnabled(enable)
                                    }
                                },
                                onOpenDeveloperOptions = { openDeveloperOptions() },
                                onRestoreAllDefaults = { viewModel.restoreAllDefaults() }
                            )
                        }

                        composable(Routes.PERMISSIONS) {
                            PermissionsPrivacyScreen(
                                permissions = permissions,
                                capabilities = capabilities,
                                onBack = { navController.popBackStack() },
                                onRequestMicrophone = { micPermLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                                onRequestNotifications = {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                        notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    }
                                },
                                onOpenOverlaySettings = { openOverlaySettings() },
                                onOpenAppSettings = { openAppSettings() }
                            )
                        }

                        composable(Routes.PLAYER) {
                            val activeVideo = videos.firstOrNull { it.id == selectedVideoId }
                            if (activeVideo != null) {
                                VideoPlayerScreen(
                                    video = activeVideo,
                                    trimInProgress = trimInProgress,
                                    lastTrimResult = lastTrimResult,
                                    onBack = { navController.popBackStack() },
                                    onShare = { item ->
                                        runCatching {
                                            context.startActivity(
                                                viewModel.buildShareIntent(item.uri, "video/mp4", "Share Gameplay Video")
                                            )
                                        }
                                    },
                                    onRename = { item, newName -> viewModel.renameVideo(item, newName) },
                                    onDelete = { item -> viewModel.deleteVideo(item) },
                                    onTrim = { item, startMs, endMs -> viewModel.trimVideo(item, startMs, endMs) },
                                    onClearTrimResult = { viewModel.clearTrimResult() }
                                )
                            } else {
                                LaunchedEffect(Unit) { navController.popBackStack() }
                            }
                        }
                    }

                    // Fullscreen Countdown Overlay
                    androidx.compose.animation.AnimatedVisibility(
                        visible = recordingState.phase == RecordingPhase.COUNTDOWN && recordingState.countdownRemaining > 0,
                        enter = fadeIn(),
                        exit = fadeOut(),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(ObsidianVoid.copy(alpha = 0.84f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = CarbonSurface,
                                modifier = Modifier.size(144.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "${recordingState.countdownRemaining}",
                                        style = MaterialTheme.typography.displaySmall,
                                        color = CyberCyan
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
