package com.example.data

import android.media.MediaRecorder

enum class VideoResolution(
    val shortLabel: String,
    val displayName: String,
    val longSide: Int,
    val shortSide: Int,
    val recommendedBitrateMbps: Int
) {
    P480("480p", "480p SD", 854, 480, 4),
    P720("720p", "720p HD", 1280, 720, 8),
    P1080("1080p", "1080p Full HD", 1920, 1080, 14),
    P1440("1440p", "1440p 2K QHD", 2560, 1440, 24);

    fun alignedDimensions(isLandscape: Boolean): Pair<Int, Int> {
        val rawW = if (isLandscape) longSide else shortSide
        val rawH = if (isLandscape) shortSide else longSide
        // Macroblock 16px alignment required by hardware H.264/HEVC encoders
        val alignedW = (rawW / 16) * 16
        val alignedH = (rawH / 16) * 16
        return alignedW to alignedH
    }
}

enum class VideoFps(val value: Int, val label: String) {
    FPS_24(24, "24 FPS"),
    FPS_30(30, "30 FPS"),
    FPS_48(48, "48 FPS"),
    FPS_60(60, "60 FPS")
}

enum class VideoCodec(val mimeType: String, val displayName: String, val shortLabel: String) {
    H264("video/avc", "H.264 (AVC)", "H.264"),
    HEVC("video/hevc", "H.265 (HEVC)", "HEVC")
}

enum class QualityPreset(
    val label: String,
    val resolution: VideoResolution,
    val fps: VideoFps,
    val bitrateMbps: Int
) {
    LOW("Low", VideoResolution.P480, VideoFps.FPS_30, 4),
    MEDIUM("Medium", VideoResolution.P720, VideoFps.FPS_30, 8),
    HIGH("High", VideoResolution.P1080, VideoFps.FPS_60, 16),
    ULTRA("Ultra", VideoResolution.P1440, VideoFps.FPS_60, 28),
    CUSTOM("Custom", VideoResolution.P1080, VideoFps.FPS_60, 14)
}

enum class OrientationMode(val label: String) {
    AUTO("Auto"),
    LANDSCAPE("Landscape"),
    PORTRAIT("Portrait")
}

enum class NoiseCancellationLevel(
    val label: String,
    val gateThresholdDb: Float,
    val floorAttenuationDb: Float,
    val useAndroidNs: Boolean
) {
    OFF("Off", -96f, 0f, false),
    LOW("Low", -52f, -12f, false),
    MEDIUM("Medium", -45f, -20f, true),
    HIGH("High", -39f, -28f, true),
    STRONG("Strong", -34f, -36f, true)
}

enum class MicAudioSource(val label: String, val androidSource: Int, val description: String) {
    MIC(
        "Studio Mic",
        MediaRecorder.AudioSource.MIC,
        "Recommended: Clean full-range 48 kHz studio voice without phone-call distortion"
    ),
    CAMCORDER(
        "Camcorder",
        MediaRecorder.AudioSource.CAMCORDER,
        "Wideband microphone tuned for smooth video recording"
    ),
    UNPROCESSED(
        "Raw / Unprocessed",
        MediaRecorder.AudioSource.UNPROCESSED,
        "Minimal OS processing; relies purely on Zero Studio DSP"
    ),
    VOICE_COMMUNICATION(
        "Voice Comm",
        MediaRecorder.AudioSource.VOICE_COMMUNICATION,
        "Telephony echo-canceling mode (may lower voice warmth on some devices)"
    )
}

enum class AudioPreset(
    val label: String,
    val gameVolume: Float,
    val micVolume: Float,
    val micGainDb: Float,
    val teamVolume: Float,
    val noiseCancellation: NoiseCancellationLevel,
    val voiceEnhancement: Boolean,
    val smartVoiceBalance: Boolean
) {
    GAMING(
        label = "Gaming",
        gameVolume = 0.90f,
        micVolume = 1.0f,
        micGainDb = 0f,
        teamVolume = 0.90f,
        noiseCancellation = NoiseCancellationLevel.MEDIUM,
        voiceEnhancement = true,
        smartVoiceBalance = true
    ),
    VOICE_FOCUS(
        label = "Studio Voice",
        gameVolume = 0.65f,
        micVolume = 1.15f,
        micGainDb = 2f,
        teamVolume = 0.65f,
        noiseCancellation = NoiseCancellationLevel.HIGH,
        voiceEnhancement = true,
        smartVoiceBalance = true
    ),
    BALANCED(
        label = "Balanced",
        gameVolume = 0.95f,
        micVolume = 1.0f,
        micGainDb = 0f,
        teamVolume = 0.95f,
        noiseCancellation = NoiseCancellationLevel.LOW,
        voiceEnhancement = true,
        smartVoiceBalance = false
    ),
    ORIGINAL_AUDIO(
        label = "Original Audio",
        gameVolume = 1.0f,
        micVolume = 1.0f,
        micGainDb = 0f,
        teamVolume = 1.0f,
        noiseCancellation = NoiseCancellationLevel.OFF,
        voiceEnhancement = false,
        smartVoiceBalance = false
    ),
    CUSTOM(
        label = "Custom",
        gameVolume = 0.90f,
        micVolume = 1.0f,
        micGainDb = 0f,
        teamVolume = 0.90f,
        noiseCancellation = NoiseCancellationLevel.MEDIUM,
        voiceEnhancement = true,
        smartVoiceBalance = true
    )
}

enum class FloatingPanelSize(val label: String, val dpSize: Int) {
    SMALL("Small", 46),
    MEDIUM("Medium", 54),
    LARGE("Large", 62)
}

enum class PerformanceMode(
    val label: String,
    val subtitle: String,
    val telemetryIntervalMs: Long,
    val bitrateScale: Float
) {
    PERFORMANCE(
        "Performance",
        "Max encoder throughput & crisp 60 FPS priority",
        1000L,
        1.0f
    ),
    BALANCED(
        "Balanced",
        "Optimal clarity with controlled heat & battery use",
        1500L,
        0.92f
    ),
    LOW_RAM(
        "Low-RAM (4GB)",
        "Reduced buffers & lightweight HUD for 4GB RAM devices",
        2500L,
        0.80f
    ),
    BATTERY_SAVER(
        "Battery Saver",
        "Lower bitrate ceiling & thermal headroom for long sessions",
        3000L,
        0.65f
    )
}

data class RecorderSettings(
    // Video Engine
    val resolution: VideoResolution = VideoResolution.P1080,
    val fps: VideoFps = VideoFps.FPS_60,
    val bitrateMbps: Int = 14,
    val autoBitrateOptimization: Boolean = true,
    val codec: VideoCodec = VideoCodec.H264,
    val qualityPreset: QualityPreset = QualityPreset.HIGH,
    val orientationMode: OrientationMode = OrientationMode.AUTO,
    val adaptiveFps: Boolean = true,
    val adaptiveBitrate: Boolean = true,

    // Unified Audio Mixer (1. Game & Teammates Audio + 2. My Microphone)
    val audioPreset: AudioPreset = AudioPreset.GAMING,
    val gameAudioEnabled: Boolean = true,
    val gameAudioVolume: Float = 0.90f, // 0.0..1.5
    val gameAudioMuted: Boolean = false,
    val gameOriginalAudioMode: Boolean = true, // Bypass all NC on game & teammates sound

    val micEnabled: Boolean = true,
    val micVolume: Float = 1.0f, // 0.0..1.5
    val micGainDb: Float = 0.0f, // -6..+12 dB (0 dB clean studio default)
    val micMuted: Boolean = false,
    val micSource: MicAudioSource = MicAudioSource.MIC,

    // Kept synced with unified Game & Teammates Audio for compatibility
    val teamVoiceEnabled: Boolean = true,
    val teamVoiceVolume: Float = 0.90f,
    val teamVoiceMuted: Boolean = false,
    val smartVoiceBalance: Boolean = true, // Smoothly duck game slightly when you speak
    val clippingProtection: Boolean = true,

    // Noise Cancellation & Wind & Studio Voice Enhancement (Mic-only)
    val noiseCancellation: NoiseCancellationLevel = NoiseCancellationLevel.MEDIUM,
    val windReductionEnabled: Boolean = false,
    val windReductionIntensity: Float = 0.50f, // 0.0..1.0
    val voiceEnhancementEnabled: Boolean = true,
    val autoGainControl: Boolean = false, // Disabled by default to avoid telephony hiss pumping
    val echoReduction: Boolean = false,   // Only needed in Voice Comm mode

    // Custom saved audio preset values
    val customGameVolume: Float = 0.90f,
    val customMicVolume: Float = 1.0f,
    val customMicGainDb: Float = 0.0f,
    val customTeamVolume: Float = 0.90f,
    val customNoiseCancellation: NoiseCancellationLevel = NoiseCancellationLevel.MEDIUM,
    val customVoiceEnhancement: Boolean = true,

    // Floating Overlay Control
    val floatingControlEnabled: Boolean = false,
    val floatingCompactMode: Boolean = true,
    val floatingOpacity: Float = 0.88f, // 0.30..1.0
    val floatingSize: FloatingPanelSize = FloatingPanelSize.SMALL,
    val hideFloatingWhileRecording: Boolean = false,

    // Recording & Advanced Controls
    val countdownSeconds: Int = 3, // 0, 3, 5, 10
    val showRecordingTimer: Boolean = true,
    val notificationControlsEnabled: Boolean = true,
    val hapticFeedbackEnabled: Boolean = true,
    val performanceMode: PerformanceMode = PerformanceMode.BALANCED,
    val autoStopLowStorageMb: Int = 1024, // 512, 1024, 2048
    val maxDurationMinutes: Int = 0, // 0 = unlimited, 15, 30, 60, 120
    val fileNamePrefix: String = "ZeroRec"
) {
    /**
     * Calculates effective video bitrate in bps considering auto optimization,
     * FPS scaling, HEVC efficiency, and PerformanceMode.
     */
    fun effectiveVideoBitrateMbps(): Float {
        val base = if (autoBitrateOptimization) {
            val fpsFactor = when (fps) {
                VideoFps.FPS_24 -> 0.72f
                VideoFps.FPS_30 -> 0.85f
                VideoFps.FPS_48 -> 1.05f
                VideoFps.FPS_60 -> 1.25f
            }
            val codecFactor = if (codec == VideoCodec.HEVC) 0.78f else 1.0f
            (resolution.recommendedBitrateMbps * fpsFactor * codecFactor)
        } else {
            bitrateMbps.toFloat()
        }
        return (base * performanceMode.bitrateScale).coerceIn(2f, 40f)
    }

    /**
     * Estimated recording size in MB per minute (video + 192kbps AAC audio).
     */
    fun estimatedMbPerMinute(): Float {
        val videoMbps = effectiveVideoBitrateMbps()
        val audioMbps = 0.192f
        return ((videoMbps + audioMbps) * 60f) / 8f
    }

    /**
     * Estimated max recording duration in minutes given available free bytes.
     */
    fun estimatedMaxMinutes(availableBytes: Long): Int {
        val reserveBytes = autoStopLowStorageMb.toLong() * 1024L * 1024L
        val usableMb = ((availableBytes - reserveBytes).coerceAtLeast(0L)) / (1024f * 1024f)
        val mbPerMin = estimatedMbPerMinute().coerceAtLeast(1f)
        return (usableMb / mbPerMin).toInt()
    }
}
