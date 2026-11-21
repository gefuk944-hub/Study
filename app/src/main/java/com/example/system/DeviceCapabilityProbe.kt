package com.example.system

import android.app.ActivityManager
import android.content.Context
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import android.os.Build
import com.example.data.PerformanceMode
import com.example.data.VideoCodec
import com.example.data.VideoFps
import com.example.data.VideoResolution

data class ResolutionSupport(
    val resolution: VideoResolution,
    val isSupported: Boolean,
    val reason: String? = null
)

data class FpsSupport(
    val fps: VideoFps,
    val isSupported: Boolean,
    val reason: String? = null
)

data class CodecSupport(
    val codec: VideoCodec,
    val isHardwareAccelerated: Boolean,
    val isSupported: Boolean,
    val encoderName: String?,
    val maxWidth: Int,
    val maxHeight: Int,
    val maxBitrateMbps: Int,
    val reason: String? = null
)

data class DeviceCapabilities(
    val codecs: Map<VideoCodec, CodecSupport>,
    val resolutionSupport: Map<VideoResolution, ResolutionSupport>,
    val fpsSupportByResolution: Map<VideoResolution, Map<VideoFps, FpsSupport>>,
    val totalRamGb: Float,
    val isLowRamTier: Boolean,
    val supportsInternalAudioCapture: Boolean,
    val supportsAppWindowCaptureChoice: Boolean,
    val supportsHardwareNoiseSuppressor: Boolean,
    val supportsHardwareEchoCanceler: Boolean,
    val supportsHardwareAgc: Boolean,
    val recommendedResolution: VideoResolution,
    val recommendedFps: VideoFps,
    val recommendedMode: PerformanceMode
) {
    fun isResolutionSupported(res: VideoResolution): Boolean =
        resolutionSupport[res]?.isSupported == true

    fun isFpsSupported(res: VideoResolution, fps: VideoFps): Boolean =
        fpsSupportByResolution[res]?.get(fps)?.isSupported == true

    fun isCodecSupported(codec: VideoCodec): Boolean =
        codecs[codec]?.isSupported == true
}

/**
 * Probes actual Android device hardware encoders via MediaCodecList, RAM via ActivityManager,
 * and platform audio capabilities so Zero Recorder never exposes unsupported resolutions or FPS.
 */
object DeviceCapabilityProbe {

    fun probe(context: Context): DeviceCapabilities {
        val codecList = MediaCodecList(MediaCodecList.REGULAR_CODECS)
        val allCodecs = codecList.codecInfos.filter { it.isEncoder }

        val h264Support = probeCodec(allCodecs, VideoCodec.H264)
        val hevcSupport = probeCodec(allCodecs, VideoCodec.HEVC)

        val codecMap = mapOf(
            VideoCodec.H264 to h264Support,
            VideoCodec.HEVC to hevcSupport
        )

        // Probe primary encoder capabilities for resolution & FPS matrix
        val primaryEncoderCaps = findBestVideoCaps(allCodecs, VideoCodec.H264.mimeType)
            ?: findBestVideoCaps(allCodecs, VideoCodec.HEVC.mimeType)

        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        am.getMemoryInfo(memInfo)
        val totalRamGb = memInfo.totalMem / (1024f * 1024f * 1024f)
        val isLowRamTier = totalRamGb <= 4.6f || am.isLowRamDevice

        val resSupport = mutableMapOf<VideoResolution, ResolutionSupport>()
        val fpsMatrix = mutableMapOf<VideoResolution, Map<VideoFps, FpsSupport>>()

        for (res in VideoResolution.entries) {
            val (w, h) = res.alignedDimensions(isLandscape = true)
            val sizeSupported = if (primaryEncoderCaps != null) {
                primaryEncoderCaps.isSizeSupported(w, h) || primaryEncoderCaps.isSizeSupported(h, w)
            } else {
                res != VideoResolution.P1440
            }

            val reason = when {
                !sizeSupported -> "Device video encoder max size (${h264Support.maxWidth}x${h264Support.maxHeight}) cannot encode ${res.shortLabel}."
                else -> null
            }

            resSupport[res] = ResolutionSupport(res, sizeSupported, reason)

            val fpsMap = mutableMapOf<VideoFps, FpsSupport>()
            for (fps in VideoFps.entries) {
                if (!sizeSupported) {
                    fpsMap[fps] = FpsSupport(fps, false, "Unavailable at ${res.shortLabel}")
                } else {
                    val rateSupported = if (primaryEncoderCaps != null) {
                        primaryEncoderCaps.areSizeAndRateSupported(w, h, fps.value.toDouble()) ||
                            primaryEncoderCaps.areSizeAndRateSupported(h, w, fps.value.toDouble())
                    } else {
                        fps.value <= 30
                    }
                    val fpsReason = if (!rateSupported) {
                        "Hardware encoder does not support ${fps.label} at ${res.shortLabel}"
                    } else null
                    fpsMap[fps] = FpsSupport(fps, rateSupported, fpsReason)
                }
            }
            fpsMatrix[res] = fpsMap
        }

        // Smart safe recommendation: never force 2K/60 on 4GB RAM or low-end encoders
        val recRes = when {
            isLowRamTier && resSupport[VideoResolution.P720]?.isSupported == true -> VideoResolution.P720
            resSupport[VideoResolution.P1080]?.isSupported == true -> VideoResolution.P1080
            resSupport[VideoResolution.P720]?.isSupported == true -> VideoResolution.P720
            else -> VideoResolution.P480
        }
        val recFps = when {
            isLowRamTier -> VideoFps.FPS_30
            fpsMatrix[recRes]?.get(VideoFps.FPS_60)?.isSupported == true -> VideoFps.FPS_60
            else -> VideoFps.FPS_30
        }
        val recMode = if (isLowRamTier) PerformanceMode.LOW_RAM else PerformanceMode.BALANCED

        return DeviceCapabilities(
            codecs = codecMap,
            resolutionSupport = resSupport,
            fpsSupportByResolution = fpsMatrix,
            totalRamGb = totalRamGb,
            isLowRamTier = isLowRamTier,
            supportsInternalAudioCapture = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q,
            supportsAppWindowCaptureChoice = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE,
            supportsHardwareNoiseSuppressor = runCatching { NoiseSuppressor.isAvailable() }.getOrDefault(false),
            supportsHardwareEchoCanceler = runCatching { AcousticEchoCanceler.isAvailable() }.getOrDefault(false),
            supportsHardwareAgc = runCatching { AutomaticGainControl.isAvailable() }.getOrDefault(false),
            recommendedResolution = recRes,
            recommendedFps = recFps,
            recommendedMode = recMode
        )
    }

    private fun probeCodec(allEncoders: List<MediaCodecInfo>, codec: VideoCodec): CodecSupport {
        val matching = allEncoders.filter { info ->
            info.supportedTypes.any { it.equals(codec.mimeType, ignoreCase = true) }
        }
        if (matching.isEmpty()) {
            return CodecSupport(
                codec = codec,
                isHardwareAccelerated = false,
                isSupported = false,
                encoderName = null,
                maxWidth = 0,
                maxHeight = 0,
                maxBitrateMbps = 0,
                reason = "${codec.displayName} encoder is not available on this device."
            )
        }

        val hwEncoder = matching.firstOrNull { isHardwareCodec(it) }
        val chosen = hwEncoder ?: matching.first()
        val caps = runCatching {
            chosen.getCapabilitiesForType(codec.mimeType).videoCapabilities
        }.getOrNull()

        val maxW = caps?.supportedWidths?.upper ?: 1920
        val maxH = caps?.supportedHeights?.upper ?: 1080
        val maxBitrateMbps = ((caps?.bitrateRange?.upper ?: 20_000_000) / 1_000_000).coerceAtLeast(4)

        return CodecSupport(
            codec = codec,
            isHardwareAccelerated = hwEncoder != null,
            isSupported = true,
            encoderName = chosen.name,
            maxWidth = maxW,
            maxHeight = maxH,
            maxBitrateMbps = maxBitrateMbps,
            reason = if (hwEncoder == null) "Software encoder only (higher CPU usage)" else null
        )
    }

    private fun findBestVideoCaps(
        allEncoders: List<MediaCodecInfo>,
        mimeType: String
    ): MediaCodecInfo.VideoCapabilities? {
        val matching = allEncoders.filter { info ->
            info.supportedTypes.any { it.equals(mimeType, ignoreCase = true) }
        }
        val preferred = matching.firstOrNull { isHardwareCodec(it) } ?: matching.firstOrNull()
        return preferred?.let {
            runCatching { it.getCapabilitiesForType(mimeType).videoCapabilities }.getOrNull()
        }
    }

    private fun isHardwareCodec(info: MediaCodecInfo): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            info.isHardwareAccelerated
        } else {
            val lower = info.name.lowercase()
            !lower.startsWith("omx.google.") && !lower.startsWith("c2.android.")
        }
    }
}
