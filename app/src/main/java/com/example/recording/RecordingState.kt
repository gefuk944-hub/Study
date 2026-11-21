package com.example.recording

import android.net.Uri
import com.example.audio.AudioSourceRuntimeStatus
import com.example.audio.LiveAudioTelemetry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class RecordingPhase {
    IDLE,
    COUNTDOWN,
    RECORDING,
    PAUSED,
    FINALIZING
}

data class RecordingSessionState(
    val phase: RecordingPhase = RecordingPhase.IDLE,
    val countdownRemaining: Int = 0,
    val elapsedMs: Long = 0L,
    val resolutionLabel: String = "1080p",
    val activeFps: Int = 60,
    val targetFps: Int = 60,
    val activeBitrateMbps: Float = 14f,
    val codecLabel: String = "H.264",
    val estimatedCurrentBytes: Long = 0L,
    val captureModeLabel: String = "Full Display",
    val micMuted: Boolean = false,
    val audioTelemetry: LiveAudioTelemetry = LiveAudioTelemetry(),
    val floatingOverlayActive: Boolean = false,
    val thermalWarningMessage: String? = null,
    val statusBannerMessage: String? = null,
    val lastSavedVideoUri: Uri? = null,
    val lastSavedVideoName: String? = null,
    val lastScreenshotUri: Uri? = null,
    val lastScreenshotName: String? = null,
    val mediaVersionCounter: Int = 0
) {
    val isActiveOrPaused: Boolean
        get() = phase == RecordingPhase.RECORDING || phase == RecordingPhase.PAUSED

    val formattedTimer: String
        get() {
            val totalSec = (elapsedMs / 1000L).coerceAtLeast(0L)
            val hrs = totalSec / 3600L
            val mins = (totalSec % 3600L) / 60L
            val secs = totalSec % 60L
            return if (hrs > 0) "%02d:%02d:%02d".format(hrs, mins, secs)
            else "%02d:%02d".format(mins, secs)
        }
}

/**
 * Process-local reactive bridge shared between ScreenRecordingService, FloatingControlService,
 * and RecorderViewModel so live timers, audio meters, and controls stay synchronized with zero lag.
 */
object RecordingEngineController {
    private val _sessionState = MutableStateFlow(RecordingSessionState())
    val sessionState: StateFlow<RecordingSessionState> = _sessionState.asStateFlow()

    fun update(transform: (RecordingSessionState) -> RecordingSessionState) {
        _sessionState.update(transform)
    }

    fun updateAudioTelemetry(telemetry: LiveAudioTelemetry) {
        _sessionState.update { it.copy(audioTelemetry = telemetry) }
    }

    fun clearBanner() {
        _sessionState.update { it.copy(statusBannerMessage = null) }
    }

    fun clearThermalWarning() {
        _sessionState.update { it.copy(thermalWarningMessage = null) }
    }

    fun notifyMediaChanged(savedVideoUri: Uri? = null, savedVideoName: String? = null, screenshotUri: Uri? = null, screenshotName: String? = null, message: String? = null) {
        _sessionState.update {
            it.copy(
                lastSavedVideoUri = savedVideoUri ?: it.lastSavedVideoUri,
                lastSavedVideoName = savedVideoName ?: it.lastSavedVideoName,
                lastScreenshotUri = screenshotUri ?: it.lastScreenshotUri,
                lastScreenshotName = screenshotName ?: it.lastScreenshotName,
                statusBannerMessage = message ?: it.statusBannerMessage,
                mediaVersionCounter = it.mediaVersionCounter + 1
            )
        }
    }

    fun resetToIdle(message: String? = null) {
        _sessionState.update {
            it.copy(
                phase = RecordingPhase.IDLE,
                countdownRemaining = 0,
                elapsedMs = 0L,
                estimatedCurrentBytes = 0L,
                audioTelemetry = LiveAudioTelemetry(
                    gameStatus = AudioSourceRuntimeStatus.DISABLED,
                    micStatus = AudioSourceRuntimeStatus.DISABLED,
                    teamStatus = AudioSourceRuntimeStatus.DISABLED
                ),
                statusBannerMessage = message ?: it.statusBannerMessage
            )
        }
    }
}
