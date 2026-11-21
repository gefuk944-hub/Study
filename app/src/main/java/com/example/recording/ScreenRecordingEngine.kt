package com.example.recording

import android.content.ContentValues
import android.content.Context
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.projection.MediaProjection
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.util.DisplayMetrics
import android.util.Log
import android.view.Surface
import android.view.WindowManager
import com.example.audio.ThreeSourceAudioMixer
import com.example.data.MediaStoreRepository
import com.example.data.OrientationMode
import com.example.data.RecorderSettings
import com.example.data.VideoCodec
import java.io.File
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.roundToInt

data class RecordingStopSummary(
    val savedUri: Uri?,
    val displayName: String?,
    val durationMs: Long,
    val recoveredFromInterruption: Boolean,
    val message: String
)

/**
 * Native hardware-accelerated screen recording engine combining:
 * - MediaProjection + VirtualDisplay -> Surface -> Hardware H.264/HEVC MediaCodec
 * - ThreeSourceAudioMixer (Game Audio + DSP Mic + Teammate Voice) -> AAC MediaCodec
 * - Synchronized MediaMuxer writing directly into Android Gallery (Movies/ZeroRecorder/)
 * - Seamless Pause/Resume timestamp compensation, live adaptive bitrate, and safe recovery on interruption.
 */
class ScreenRecordingEngine(
    private val context: Context,
    private val mediaProjection: MediaProjection,
    private val initialSettings: RecorderSettings,
    private val onStoppedUnexpectedly: (RecordingStopSummary) -> Unit
) {
    companion object {
        private const val TAG = "ScreenRecordingEngine"
    }

    @Volatile
    private var settings: RecorderSettings = initialSettings

    private val isRunning = AtomicBoolean(false)
    private val isPaused = AtomicBoolean(false)

    private var videoEncoder: MediaCodec? = null
    private var inputSurface: Surface? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var audioMixer: ThreeSourceAudioMixer? = null
    private var mediaMuxer: MediaMuxer? = null

    private var outputUri: Uri? = null
    private var outputFileName: String? = null
    private var legacyFile: File? = null
    private var outputPfd: ParcelFileDescriptor? = null

    private val muxerLock = Any()
    private var videoTrackIndex = -1
    private var audioTrackIndex = -1
    private var expectsAudioTrack = false
    private var muxerStarted = false

    private val recordingStartNano = AtomicLong(0L)
    private val pauseStartNano = AtomicLong(0L)
    private val totalPausedUs = AtomicLong(0L)
    private val bytesWritten = AtomicLong(0L)
    private var videoFramesEncoded = 0L
    private var lastVideoPtsUs = 0L
    private var lastAudioPtsUs = 0L

    private var currentBitrateBps = 0
    private var currentWidth = 1280
    private var currentHeight = 720
    private var videoDrainThread: Thread? = null

    private val projectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            Log.w(TAG, "MediaProjection revoked/stopped externally — running safe recovery")
            if (isRunning.get()) {
                val summary = stop(recoveredFromInterruption = true)
                onStoppedUnexpectedly(summary)
            }
        }
    }

    fun start(): Boolean {
        if (isRunning.getAndSet(true)) return true

        val s = settings
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val isLandscape = resolveIsLandscape(wm, s.orientationMode)
        val (w, h) = s.resolution.alignedDimensions(isLandscape)
        currentWidth = w
        currentHeight = h
        currentBitrateBps = (s.effectiveVideoBitrateMbps() * 1_000_000f).roundToInt()

        try {
            mediaProjection.registerCallback(projectionCallback, Handler(Looper.getMainLooper()))

            if (!prepareMediaStoreMuxer(s.fileNamePrefix)) {
                cleanupResources()
                isRunning.set(false)
                return false
            }

            if (!prepareVideoEncoder(w, h, s.fps.value, currentBitrateBps, s.codec)) {
                cleanupResources()
                isRunning.set(false)
                return false
            }

            val densityDpi = context.resources.displayMetrics.densityDpi
            virtualDisplay = mediaProjection.createVirtualDisplay(
                "ZeroRecorder-Display",
                w,
                h,
                densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                inputSurface,
                null,
                null
            )

            recordingStartNano.set(System.nanoTime())

            // Initialize 3-source audio mixer (Game + DSP Mic + Team Voice)
            val wantsAnyAudio = s.gameAudioEnabled || s.micEnabled || s.teamVoiceEnabled
            if (wantsAnyAudio) {
                val mixer = ThreeSourceAudioMixer(
                    context = context,
                    mediaProjection = mediaProjection,
                    initialSettings = s,
                    onEncodedAudioReady = { buf, info -> writeMuxerSample(isVideo = false, buf, info) },
                    onAudioFormatChanged = { format -> onTrackFormatReady(isVideo = false, format) },
                    onTelemetryUpdate = { telemetry ->
                        RecordingEngineController.updateAudioTelemetry(telemetry)
                    }
                )
                val audioStarted = mixer.start(
                    recordingStartNanoProvider = { recordingStartNano.get() },
                    pausedDurationUsProvider = { totalPausedUs.get() }
                )
                if (audioStarted) {
                    audioMixer = mixer
                    expectsAudioTrack = true
                } else {
                    expectsAudioTrack = false
                }
            }

            videoDrainThread = Thread({ drainVideoLoop() }, "ZeroRec-VideoEncoder").apply {
                priority = Thread.MAX_PRIORITY
                start()
            }

            RecordingEngineController.update {
                it.copy(
                    phase = RecordingPhase.RECORDING,
                    elapsedMs = 0L,
                    resolutionLabel = "${s.resolution.shortLabel} (${w}x${h})",
                    activeFps = s.fps.value,
                    targetFps = s.fps.value,
                    activeBitrateMbps = currentBitrateBps / 1_000_000f,
                    codecLabel = s.codec.shortLabel,
                    micMuted = s.micMuted,
                    captureModeLabel = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        "Android Capture Session"
                    } else {
                        "Full Display"
                    }
                )
            }
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start screen recording engine", e)
            cleanupResources()
            isRunning.set(false)
            return false
        }
    }

    fun updateLiveSettings(newSettings: RecorderSettings) {
        settings = newSettings
        audioMixer?.updateSettings(newSettings)
        RecordingEngineController.update { it.copy(micMuted = newSettings.micMuted) }
    }

    fun pause() {
        if (!isRunning.get() || isPaused.getAndSet(true)) return
        pauseStartNano.set(System.nanoTime())
        audioMixer?.setPaused(true)
        RecordingEngineController.update { it.copy(phase = RecordingPhase.PAUSED) }
    }

    fun resume() {
        if (!isRunning.get() || !isPaused.getAndSet(false)) return
        val pauseStart = pauseStartNano.getAndSet(0L)
        if (pauseStart > 0L) {
            val pausedDeltaUs = (System.nanoTime() - pauseStart) / 1000L
            totalPausedUs.addAndGet(pausedDeltaUs.coerceAtLeast(0L))
        }
        audioMixer?.setPaused(false)
        RecordingEngineController.update { it.copy(phase = RecordingPhase.RECORDING) }
    }

    /**
     * Dynamically lowers encoder bitrate on the fly when device heats up or adaptive bitrate triggers.
     */
    fun applyAdaptiveBitrateScale(scale: Float) {
        if (!isRunning.get() || !settings.adaptiveBitrate) return
        val codec = videoEncoder ?: return
        val targetBps = (currentBitrateBps * scale.coerceIn(0.45f, 1.0f))
            .roundToInt()
            .coerceIn(2_000_000, 40_000_000)
        if (targetBps != currentBitrateBps) {
            runCatching {
                val params = Bundle().apply {
                    putInt(MediaCodec.PARAMETER_KEY_VIDEO_BITRATE, targetBps)
                }
                codec.setParameters(params)
                currentBitrateBps = targetBps
                RecordingEngineController.update {
                    it.copy(activeBitrateMbps = targetBps / 1_000_000f)
                }
            }
        }
    }

    fun getCurrentElapsedMs(): Long {
        val start = recordingStartNano.get()
        if (start == 0L) return 0L
        val now = if (isPaused.get() && pauseStartNano.get() > 0L) pauseStartNano.get() else System.nanoTime()
        val elapsedUs = (now - start) / 1000L - totalPausedUs.get()
        return (elapsedUs / 1000L).coerceAtLeast(0L)
    }

    fun getEstimatedBytesWritten(): Long = bytesWritten.get()

    fun getMediaProjection(): MediaProjection = mediaProjection

    fun stop(recoveredFromInterruption: Boolean = false): RecordingStopSummary {
        if (!isRunning.getAndSet(false)) {
            return RecordingStopSummary(outputUri, outputFileName, 0L, recoveredFromInterruption, "Recording already stopped.")
        }

        val finalDurationMs = getCurrentElapsedMs()
        RecordingEngineController.update { it.copy(phase = RecordingPhase.FINALIZING) }

        // Stop audio mixer first
        runCatching { audioMixer?.stop() }
        audioMixer = null

        // Signal video EOS and wait cleanly for video encoder drain
        runCatching { videoEncoder?.signalEndOfInputStream() }
        runCatching { videoDrainThread?.join(2000) }
        videoDrainThread = null

        // Stop muxer safely — recovers partial footage even if interrupted
        var muxerOk = false
        synchronized(muxerLock) {
            if (muxerStarted) {
                muxerOk = runCatching {
                    mediaMuxer?.stop()
                    true
                }.getOrDefault(false)
                muxerStarted = false
            }
        }

        val savedUri = outputUri
        val savedName = outputFileName

        cleanupResources()

        if (muxerOk && savedUri != null) {
            finalizeMediaStoreEntry(savedUri, isSuccess = true)
            val msg = if (recoveredFromInterruption) {
                "Recording interrupted — safely recovered to Movies/ZeroRecorder/$savedName"
            } else {
                "Saved to Gallery: Movies/ZeroRecorder/$savedName"
            }
            return RecordingStopSummary(savedUri, savedName, finalDurationMs, recoveredFromInterruption, msg)
        } else {
            if (savedUri != null) finalizeMediaStoreEntry(savedUri, isSuccess = false)
            legacyFile?.delete()
            return RecordingStopSummary(
                null,
                null,
                finalDurationMs,
                recoveredFromInterruption,
                "Recording stopped before enough frames were captured."
            )
        }
    }

    private fun prepareMediaStoreMuxer(prefix: String): Boolean {
        val cleanPrefix = prefix.replace(Regex("[^a-zA-Z0-9_-]"), "").ifBlank { "ZeroRec" }
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val fileName = "${cleanPrefix}_${stamp}.mp4"
        outputFileName = fileName

        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Video.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                    put(MediaStore.Video.Media.RELATIVE_PATH, "${MediaStoreRepository.VIDEO_FOLDER}/")
                    put(MediaStore.Video.Media.IS_PENDING, 1)
                }
                val uri = context.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
                    ?: return false
                outputUri = uri
                val pfd = context.contentResolver.openFileDescriptor(uri, "rw") ?: return false
                outputPfd = pfd
                mediaMuxer = MediaMuxer(pfd.fileDescriptor, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            } else {
                val dir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
                    "ZeroRecorder"
                ).apply { mkdirs() }
                val file = File(dir, fileName)
                legacyFile = file
                outputUri = Uri.fromFile(file)
                mediaMuxer = MediaMuxer(file.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to prepare MediaStore muxer", e)
            false
        }
    }

    private fun prepareVideoEncoder(
        width: Int,
        height: Int,
        fps: Int,
        bitrateBps: Int,
        codec: VideoCodec
    ): Boolean {
        val mime = codec.mimeType
        return try {
            val format = MediaFormat.createVideoFormat(mime, width, height).apply {
                setInteger(
                    MediaFormat.KEY_COLOR_FORMAT,
                    MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface
                )
                setInteger(MediaFormat.KEY_BIT_RATE, bitrateBps)
                setInteger(MediaFormat.KEY_FRAME_RATE, fps)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    setInteger(MediaFormat.KEY_LATENCY, 1)
                }
            }

            val encoder = MediaCodec.createEncoderByType(mime)
            encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            inputSurface = encoder.createInputSurface()
            encoder.start()
            videoEncoder = encoder
            true
        } catch (e: Exception) {
            Log.e(TAG, "Primary codec $mime failed; attempting H.264 fallback", e)
            if (codec != VideoCodec.H264) {
                prepareVideoEncoder(width, height, fps, bitrateBps, VideoCodec.H264)
            } else {
                false
            }
        }
    }

    private fun drainVideoLoop() {
        val codec = videoEncoder ?: return
        val bufferInfo = MediaCodec.BufferInfo()
        var fpsWindowStartNs = System.nanoTime()
        var fpsWindowFrames = 0

        try {
            while (true) {
                val outIndex = codec.dequeueOutputBuffer(bufferInfo, 10_000L)
                when {
                    outIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                        if (!isRunning.get()) break
                    }
                    outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        onTrackFormatReady(isVideo = true, codec.outputFormat)
                    }
                    outIndex >= 0 -> {
                        val outBuf = codec.getOutputBuffer(outIndex)
                        val isConfig = (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0
                        val isEos = (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0

                        if (outBuf != null && bufferInfo.size > 0 && !isConfig && !isPaused.get()) {
                            // Compensate presentation time for any paused duration so playback is continuous
                            val rawElapsedUs = (System.nanoTime() - recordingStartNano.get()) / 1000L -
                                totalPausedUs.get()
                            val adjustedPtsUs = maxOf(rawElapsedUs, lastVideoPtsUs + 1000L)
                            bufferInfo.presentationTimeUs = adjustedPtsUs.coerceAtLeast(0L)
                            lastVideoPtsUs = bufferInfo.presentationTimeUs

                            outBuf.position(bufferInfo.offset)
                            outBuf.limit(bufferInfo.offset + bufferInfo.size)
                            writeMuxerSample(isVideo = true, outBuf, bufferInfo)

                            videoFramesEncoded++
                            fpsWindowFrames++
                            val nowNs = System.nanoTime()
                            val deltaNs = nowNs - fpsWindowStartNs
                            if (deltaNs >= 1_000_000_000L) {
                                val measuredFps = ((fpsWindowFrames * 1_000_000_000L) / deltaNs).toInt()
                                    .coerceIn(1, settings.fps.value)
                                RecordingEngineController.update {
                                    it.copy(
                                        activeFps = measuredFps,
                                        estimatedCurrentBytes = bytesWritten.get()
                                    )
                                }
                                fpsWindowStartNs = nowNs
                                fpsWindowFrames = 0
                            }
                        }

                        codec.releaseOutputBuffer(outIndex, false)
                        if (isEos) break
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Video encoder drain loop error", e)
        }
    }

    private fun onTrackFormatReady(isVideo: Boolean, format: MediaFormat) {
        synchronized(muxerLock) {
            val muxer = mediaMuxer ?: return
            if (muxerStarted) return
            if (isVideo && videoTrackIndex == -1) {
                videoTrackIndex = muxer.addTrack(format)
            } else if (!isVideo && audioTrackIndex == -1) {
                audioTrackIndex = muxer.addTrack(format)
            }
            val ready = videoTrackIndex >= 0 && (!expectsAudioTrack || audioTrackIndex >= 0)
            if (ready) {
                muxer.start()
                muxerStarted = true
            }
        }
    }

    private fun writeMuxerSample(isVideo: Boolean, buffer: ByteBuffer, info: MediaCodec.BufferInfo) {
        synchronized(muxerLock) {
            if (!muxerStarted) return
            val muxer = mediaMuxer ?: return
            val trackIdx = if (isVideo) videoTrackIndex else audioTrackIndex
            if (trackIdx < 0) return

            if (!isVideo) {
                // Guarantee monotonic audio PTS for MediaMuxer stability
                val safePts = maxOf(info.presentationTimeUs, lastAudioPtsUs + 100L)
                info.presentationTimeUs = safePts
                lastAudioPtsUs = safePts
            }

            runCatching {
                muxer.writeSampleData(trackIdx, buffer, info)
                bytesWritten.addAndGet(info.size.toLong())
            }.onFailure {
                Log.w(TAG, "Skipped muxer frame (${if (isVideo) "video" else "audio"}): ${it.message}")
            }
        }
    }

    private fun finalizeMediaStoreEntry(uri: Uri, isSuccess: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (isSuccess) {
                val values = ContentValues().apply {
                    put(MediaStore.Video.Media.IS_PENDING, 0)
                }
                runCatching { context.contentResolver.update(uri, values, null, null) }
            } else {
                runCatching { context.contentResolver.delete(uri, null, null) }
            }
        }
    }

    private fun resolveIsLandscape(wm: WindowManager, mode: OrientationMode): Boolean {
        return when (mode) {
            OrientationMode.LANDSCAPE -> true
            OrientationMode.PORTRAIT -> false
            OrientationMode.AUTO -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    val b = wm.currentWindowMetrics.bounds
                    b.width() >= b.height()
                } else {
                    val dm = DisplayMetrics()
                    @Suppress("DEPRECATION")
                    wm.defaultDisplay.getRealMetrics(dm)
                    dm.widthPixels >= dm.heightPixels
                }
            }
        }
    }

    private fun cleanupResources() {
        runCatching { mediaProjection.unregisterCallback(projectionCallback) }
        runCatching { virtualDisplay?.release() }; virtualDisplay = null
        runCatching { inputSurface?.release() }; inputSurface = null
        runCatching { videoEncoder?.stop() }
        runCatching { videoEncoder?.release() }; videoEncoder = null
        synchronized(muxerLock) {
            runCatching { mediaMuxer?.release() }
            mediaMuxer = null
            muxerStarted = false
            videoTrackIndex = -1
            audioTrackIndex = -1
        }
        runCatching { outputPfd?.close() }; outputPfd = null
        runCatching { mediaProjection.stop() }
    }
}
