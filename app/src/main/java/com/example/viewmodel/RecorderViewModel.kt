package com.example.viewmodel

import android.Manifest
import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.audio.MicTestEngine
import com.example.audio.MicTestUiState
import com.example.data.AudioPreset
import com.example.data.FloatingPanelSize
import com.example.data.MediaStoreRepository
import com.example.data.MicAudioSource
import com.example.data.NoiseCancellationLevel
import com.example.data.OrientationMode
import com.example.data.PerformanceMode
import com.example.data.QualityPreset
import com.example.data.RecordedVideoItem
import com.example.data.RecorderSettings
import com.example.data.ScreenshotItem
import com.example.data.SettingsRepository
import com.example.data.VideoCodec
import com.example.data.VideoFps
import com.example.data.VideoResolution
import com.example.overlay.FloatingControlService
import com.example.recording.RecordingEngineController
import com.example.recording.RecordingSessionState
import com.example.recording.ScreenRecordingService
import com.example.system.DeviceCapabilities
import com.example.system.DeviceCapabilityProbe
import com.example.system.SystemTelemetry
import com.example.system.SystemTelemetryMonitor
import com.example.system.TrimResult
import com.example.system.VideoQualityTestResult
import com.example.system.VideoQualityTester
import com.example.system.VideoTrimmer
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class PermissionSnapshot(
    val hasMicrophone: Boolean = false,
    val hasNotifications: Boolean = true,
    val hasOverlay: Boolean = false
)

class RecorderViewModel(application: Application) : AndroidViewModel(application) {

    private val appContext = application.applicationContext
    private val settingsRepo = SettingsRepository(appContext)
    val mediaRepo = MediaStoreRepository(appContext)
    private val micTestEngine = MicTestEngine(appContext)

    val settings: StateFlow<RecorderSettings> = settingsRepo.settingsFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, RecorderSettings())

    val recordingState: StateFlow<RecordingSessionState> = RecordingEngineController.sessionState

    val micTestState: StateFlow<MicTestUiState> = micTestEngine.state

    private val _capabilities = MutableStateFlow(DeviceCapabilityProbe.probe(appContext))
    val capabilities: StateFlow<DeviceCapabilities> = _capabilities.asStateFlow()

    private val _telemetry = MutableStateFlow(SystemTelemetryMonitor.snapshot(appContext))
    val telemetry: StateFlow<SystemTelemetry> = _telemetry.asStateFlow()

    private val _permissions = MutableStateFlow(readPermissionSnapshot())
    val permissions: StateFlow<PermissionSnapshot> = _permissions.asStateFlow()

    private val _videos = MutableStateFlow<List<RecordedVideoItem>>(emptyList())
    val videos: StateFlow<List<RecordedVideoItem>> = _videos.asStateFlow()

    private val _screenshots = MutableStateFlow<List<ScreenshotItem>>(emptyList())
    val screenshots: StateFlow<List<ScreenshotItem>> = _screenshots.asStateFlow()

    private val _isRefreshingMedia = MutableStateFlow(false)
    val isRefreshingMedia: StateFlow<Boolean> = _isRefreshingMedia.asStateFlow()

    private val _qualityTestRunning = MutableStateFlow(false)
    val qualityTestRunning: StateFlow<Boolean> = _qualityTestRunning.asStateFlow()

    private val _qualityTestResult = MutableStateFlow<VideoQualityTestResult?>(null)
    val qualityTestResult: StateFlow<VideoQualityTestResult?> = _qualityTestResult.asStateFlow()

    private val _trimInProgress = MutableStateFlow(false)
    val trimInProgress: StateFlow<Boolean> = _trimInProgress.asStateFlow()

    private val _lastTrimResult = MutableStateFlow<TrimResult?>(null)
    val lastTrimResult: StateFlow<TrimResult?> = _lastTrimResult.asStateFlow()

    init {
        enforceDeviceSafeDefaults()
        observeSettingsForMicTest()
        startTelemetryPolling()
        refreshMediaLibrary()
        observeMediaChanges()
    }

    private fun enforceDeviceSafeDefaults() {
        viewModelScope.launch {
            val caps = _capabilities.value
            val current = settings.value
            val safeRes = if (caps.isResolutionSupported(current.resolution)) current.resolution else caps.recommendedResolution
            val safeFps = if (caps.isFpsSupported(safeRes, current.fps)) current.fps else caps.recommendedFps
            val safeCodec = if (caps.isCodecSupported(current.codec)) current.codec else VideoCodec.H264

            if (safeRes != current.resolution || safeFps != current.fps || safeCodec != current.codec) {
                settingsRepo.updateSettings {
                    it.copy(resolution = safeRes, fps = safeFps, codec = safeCodec)
                }
            }
        }
    }

    private fun observeSettingsForMicTest() {
        viewModelScope.launch {
            settings.collect { latest ->
                micTestEngine.updateSettings(latest)
            }
        }
    }

    private fun observeMediaChanges() {
        viewModelScope.launch {
            recordingState
                .map { it.mediaVersionCounter }
                .distinctUntilChanged()
                .collect {
                    refreshMediaLibrary()
                }
        }
    }

    private fun startTelemetryPolling() {
        viewModelScope.launch {
            while (isActive) {
                val s = settings.value
                _telemetry.value = SystemTelemetryMonitor.snapshot(appContext, s.autoStopLowStorageMb)
                val pollMs = if (recordingState.value.isActiveOrPaused) {
                    (s.performanceMode.telemetryIntervalMs * 2L).coerceAtLeast(3000L)
                } else {
                    s.performanceMode.telemetryIntervalMs
                }
                delay(pollMs)
            }
        }
    }

    fun refreshPermissions() {
        _permissions.value = readPermissionSnapshot()
    }

    private fun readPermissionSnapshot(): PermissionSnapshot {
        val mic = ContextCompat.checkSelfPermission(appContext, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        val notif = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        } else true
        val overlay = Settings.canDrawOverlays(appContext)
        return PermissionSnapshot(mic, notif, overlay)
    }

    fun refreshMediaLibrary() {
        viewModelScope.launch {
            _isRefreshingMedia.value = true
            _videos.value = mediaRepo.loadVideos()
            _screenshots.value = mediaRepo.loadScreenshots()
            _isRefreshingMedia.value = false
        }
    }

    // --- Recording & Screenshot Service Commands ---

    fun startRecordingWithProjection(resultCode: Int, data: Intent) {
        micTestEngine.stopAll()
        val intent = ScreenRecordingService.startRecordingIntent(appContext, resultCode, data)
        ContextCompat.startForegroundService(appContext, intent)
    }

    fun takeStandaloneScreenshotWithProjection(resultCode: Int, data: Intent) {
        val intent = ScreenRecordingService.standaloneScreenshotIntent(appContext, resultCode, data)
        ContextCompat.startForegroundService(appContext, intent)
    }

    fun takeScreenshotDuringRecording() {
        appContext.startService(
            ScreenRecordingService.controlIntent(appContext, ScreenRecordingService.ACTION_TAKE_SCREENSHOT)
        )
    }

    fun togglePauseResume() {
        appContext.startService(
            ScreenRecordingService.controlIntent(appContext, ScreenRecordingService.ACTION_PAUSE_RESUME)
        )
    }

    fun stopRecording() {
        appContext.startService(
            ScreenRecordingService.controlIntent(appContext, ScreenRecordingService.ACTION_STOP_RECORDING)
        )
    }

    fun toggleMicMuteLive() {
        if (recordingState.value.isActiveOrPaused) {
            appContext.startService(
                ScreenRecordingService.controlIntent(appContext, ScreenRecordingService.ACTION_TOGGLE_MIC)
            )
        } else {
            updateSettings { it.copy(micMuted = !it.micMuted) }
        }
    }

    fun stepDownQualityForThermal() {
        if (recordingState.value.isActiveOrPaused) {
            appContext.startService(
                ScreenRecordingService.controlIntent(appContext, ScreenRecordingService.ACTION_STEP_DOWN_THERMAL)
            )
        }
        val caps = _capabilities.value
        updateSettings { s ->
            val lowerRes = when (s.resolution) {
                VideoResolution.P1440 -> VideoResolution.P1080
                VideoResolution.P1080 -> VideoResolution.P720
                else -> s.resolution
            }
            val safeRes = if (caps.isResolutionSupported(lowerRes)) lowerRes else caps.recommendedResolution
            s.copy(
                resolution = safeRes,
                fps = VideoFps.FPS_30,
                bitrateMbps = s.bitrateMbps.coerceAtMost(8),
                performanceMode = PerformanceMode.BATTERY_SAVER
            )
        }
        RecordingEngineController.clearThermalWarning()
    }

    fun dismissBanner() = RecordingEngineController.clearBanner()

    // --- Floating Control Panel ---

    fun setFloatingOverlayEnabled(enabled: Boolean) {
        if (enabled && !Settings.canDrawOverlays(appContext)) {
            RecordingEngineController.update {
                it.copy(statusBannerMessage = "Grant 'Display over other apps' permission first to show the floating panel.")
            }
            return
        }
        updateSettings { it.copy(floatingControlEnabled = enabled) }
        if (enabled) {
            FloatingControlService.show(appContext)
        } else {
            FloatingControlService.hide(appContext)
        }
    }

    // --- Video Settings Actions ---

    fun selectResolution(res: VideoResolution) {
        val caps = _capabilities.value
        if (!caps.isResolutionSupported(res)) return
        updateSettings { current ->
            val safeFps = if (caps.isFpsSupported(res, current.fps)) current.fps else VideoFps.FPS_30
            current.copy(
                resolution = res,
                fps = safeFps,
                qualityPreset = QualityPreset.CUSTOM
            )
        }
    }

    fun selectFps(fps: VideoFps) {
        val caps = _capabilities.value
        val res = settings.value.resolution
        if (!caps.isFpsSupported(res, fps)) return
        updateSettings { it.copy(fps = fps, qualityPreset = QualityPreset.CUSTOM) }
    }

    fun selectCodec(codec: VideoCodec) {
        if (!_capabilities.value.isCodecSupported(codec)) return
        updateSettings { it.copy(codec = codec) }
    }

    fun applyQualityPreset(preset: QualityPreset) {
        val caps = _capabilities.value
        val res = if (caps.isResolutionSupported(preset.resolution)) preset.resolution else caps.recommendedResolution
        val fps = if (caps.isFpsSupported(res, preset.fps)) preset.fps else caps.recommendedFps
        updateSettings {
            it.copy(
                qualityPreset = preset,
                resolution = res,
                fps = fps,
                bitrateMbps = preset.bitrateMbps
            )
        }
    }

    fun applyRecommendedQuality() {
        val caps = _capabilities.value
        updateSettings {
            it.copy(
                resolution = caps.recommendedResolution,
                fps = caps.recommendedFps,
                performanceMode = caps.recommendedMode,
                autoBitrateOptimization = true,
                adaptiveFps = true,
                adaptiveBitrate = true
            )
        }
        RecordingEngineController.update {
            it.copy(statusBannerMessage = "Applied optimal settings for ${"%.1f".format(caps.totalRamGb)} GB RAM device.")
        }
    }

    fun setManualBitrate(mbps: Int) {
        updateSettings {
            it.copy(
                bitrateMbps = mbps.coerceIn(2, 40),
                autoBitrateOptimization = false,
                qualityPreset = QualityPreset.CUSTOM
            )
        }
    }

    fun runVideoQualityBenchmark() {
        if (_qualityTestRunning.value) return
        viewModelScope.launch {
            _qualityTestRunning.value = true
            _qualityTestResult.value = VideoQualityTester.runBenchmark(settings.value)
            _qualityTestRunning.value = false
        }
    }

    // --- Audio Studio & Mixer Actions ---

    fun applyAudioPreset(preset: AudioPreset) {
        updateSettings { current ->
            if (preset == AudioPreset.CUSTOM) {
                current.copy(
                    audioPreset = AudioPreset.CUSTOM,
                    gameAudioVolume = current.customGameVolume,
                    micVolume = current.customMicVolume,
                    micGainDb = current.customMicGainDb,
                    teamVoiceVolume = current.customTeamVolume,
                    noiseCancellation = current.customNoiseCancellation,
                    voiceEnhancementEnabled = current.customVoiceEnhancement
                )
            } else {
                current.copy(
                    audioPreset = preset,
                    gameAudioVolume = preset.gameVolume,
                    micVolume = preset.micVolume,
                    micGainDb = preset.micGainDb,
                    teamVoiceVolume = preset.teamVolume,
                    noiseCancellation = preset.noiseCancellation,
                    voiceEnhancementEnabled = preset.voiceEnhancement,
                    smartVoiceBalance = preset.smartVoiceBalance
                )
            }
        }
    }

    fun saveCurrentAsCustomAudioPreset() {
        updateSettings { current ->
            current.copy(
                audioPreset = AudioPreset.CUSTOM,
                customGameVolume = current.gameAudioVolume,
                customMicVolume = current.micVolume,
                customMicGainDb = current.micGainDb,
                customTeamVolume = current.teamVoiceVolume,
                customNoiseCancellation = current.noiseCancellation,
                customVoiceEnhancement = current.voiceEnhancementEnabled
            )
        }
        RecordingEngineController.update {
            it.copy(statusBannerMessage = "Custom audio mixer preset saved locally.")
        }
    }

    fun restoreAudioMixerDefaults() {
        viewModelScope.launch {
            settingsRepo.restoreAudioMixerDefaults()
            RecordingEngineController.update {
                it.copy(statusBannerMessage = "Audio Studio restored to Gaming defaults.")
            }
        }
    }

    fun toggleLiveMicMonitor() {
        if (micTestState.value.phase == com.example.audio.MicTestPhase.LIVE_MONITORING) {
            micTestEngine.stopAll()
        } else {
            micTestEngine.startMonitoring(recordFiveSecondSample = false)
        }
    }

    fun startFiveSecondVoiceTest() {
        micTestEngine.startMonitoring(recordFiveSecondSample = true)
    }

    fun replayVoiceTestSample() {
        micTestEngine.playRecordedSample()
    }

    fun stopMicTest() {
        micTestEngine.stopAll()
    }

    // --- Media Library & Video Trimming ---

    fun renameVideo(item: RecordedVideoItem, newName: String) {
        viewModelScope.launch {
            val ok = mediaRepo.renameMedia(item.uri, newName, ".mp4")
            if (ok) {
                refreshMediaLibrary()
                RecordingEngineController.update { it.copy(statusBannerMessage = "Video renamed.") }
            }
        }
    }

    fun deleteVideo(item: RecordedVideoItem) {
        viewModelScope.launch {
            val ok = mediaRepo.deleteMedia(item.uri)
            if (ok) {
                refreshMediaLibrary()
                RecordingEngineController.update { it.copy(statusBannerMessage = "Deleted ${item.displayName}") }
            }
        }
    }

    fun renameScreenshot(item: ScreenshotItem, newName: String) {
        viewModelScope.launch {
            val ok = mediaRepo.renameMedia(item.uri, newName, ".png")
            if (ok) refreshMediaLibrary()
        }
    }

    fun deleteScreenshot(item: ScreenshotItem) {
        viewModelScope.launch {
            val ok = mediaRepo.deleteMedia(item.uri)
            if (ok) {
                refreshMediaLibrary()
                RecordingEngineController.update { it.copy(statusBannerMessage = "Deleted screenshot.") }
            }
        }
    }

    fun trimVideo(item: RecordedVideoItem, startMs: Long, endMs: Long) {
        if (_trimInProgress.value) return
        viewModelScope.launch {
            _trimInProgress.value = true
            val result = VideoTrimmer.trimVideo(appContext, item.uri, item.displayName, startMs, endMs)
            _lastTrimResult.value = result
            _trimInProgress.value = false
            if (result.success) {
                refreshMediaLibrary()
            }
        }
    }

    fun clearTrimResult() {
        _lastTrimResult.value = null
    }

    // --- Generic Settings Helpers ---

    fun updateSettings(transform: (RecorderSettings) -> RecorderSettings) {
        viewModelScope.launch {
            settingsRepo.updateSettings(transform)
        }
    }

    fun restoreAllDefaults() {
        viewModelScope.launch {
            settingsRepo.restoreAllDefaults()
            FloatingControlService.hide(appContext)
            enforceDeviceSafeDefaults()
            RecordingEngineController.update {
                it.copy(statusBannerMessage = "All Zero Recorder settings restored to defaults.")
            }
        }
    }

    fun buildShareIntent(uri: Uri, mimeType: String, title: String): Intent {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(send, title)
    }

    override fun onCleared() {
        micTestEngine.stopAll()
        super.onCleared()
    }
}
