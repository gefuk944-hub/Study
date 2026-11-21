package com.example.system

import android.graphics.Color
import android.graphics.Paint
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.view.Surface
import com.example.data.RecorderSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

data class VideoQualityTestResult(
    val passed: Boolean,
    val encoderName: String,
    val resolutionLabel: String,
    val targetFps: Int,
    val achievedFps: Float,
    val avgFrameEncodeMs: Float,
    val summary: String
)

/**
 * Runs a real 1.5-second hardware MediaCodec encoding benchmark using the user's selected
 * resolution, FPS, codec, and bitrate to verify smooth real-time encoding before gaming.
 */
object VideoQualityTester {

    suspend fun runBenchmark(settings: RecorderSettings): VideoQualityTestResult =
        withContext(Dispatchers.Default) {
            val (width, height) = settings.resolution.alignedDimensions(isLandscape = true)
            val targetFps = settings.fps.value
            val bitrateBps = (settings.effectiveVideoBitrateMbps() * 1_000_000f).roundToInt()

            var codec: MediaCodec? = null
            var inputSurface: Surface? = null

            try {
                val format = MediaFormat.createVideoFormat(settings.codec.mimeType, width, height).apply {
                    setInteger(
                        MediaFormat.KEY_COLOR_FORMAT,
                        MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface
                    )
                    setInteger(MediaFormat.KEY_BIT_RATE, bitrateBps)
                    setInteger(MediaFormat.KEY_FRAME_RATE, targetFps)
                    setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
                }

                codec = MediaCodec.createEncoderByType(settings.codec.mimeType)
                val codecName = codec.name
                codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                inputSurface = codec.createInputSurface()
                codec.start()

                val testFrames = (targetFps * 1.2f).roundToInt().coerceIn(24, 72)
                val paint = Paint().apply { isAntiAlias = false }
                val bufferInfo = MediaCodec.BufferInfo()

                val startNs = System.nanoTime()
                var encodedPackets = 0

                for (frameIdx in 0 until testFrames) {
                    val canvas = inputSurface.lockHardwareCanvas()
                    try {
                        canvas.drawColor(Color.rgb(12, 16, 28))
                        paint.color = Color.rgb(41, 121, 255)
                        val x = ((frameIdx * 37) % width).toFloat()
                        canvas.drawRect(x, 0f, (x + 120f).coerceAtMost(width.toFloat()), height.toFloat(), paint)
                    } finally {
                        inputSurface.unlockCanvasAndPost(canvas)
                    }

                    while (true) {
                        val outIdx = codec.dequeueOutputBuffer(bufferInfo, 2_000L)
                        if (outIdx >= 0) {
                            if (bufferInfo.size > 0) encodedPackets++
                            codec.releaseOutputBuffer(outIdx, false)
                        } else {
                            break
                        }
                    }
                }

                codec.signalEndOfInputStream()
                val drainDeadline = System.nanoTime() + 500_000_000L
                while (System.nanoTime() < drainDeadline) {
                    val outIdx = codec.dequeueOutputBuffer(bufferInfo, 10_000L)
                    if (outIdx >= 0) {
                        if (bufferInfo.size > 0) encodedPackets++
                        codec.releaseOutputBuffer(outIdx, false)
                        if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) break
                    } else if (outIdx == MediaCodec.INFO_TRY_AGAIN_LATER) {
                        break
                    }
                }

                val elapsedMs = ((System.nanoTime() - startNs) / 1_000_000f).coerceAtLeast(1f)
                val achievedFps = (testFrames * 1000f) / elapsedMs
                val avgEncodeMs = elapsedMs / testFrames
                val stable = achievedFps >= targetFps * 0.85f && encodedPackets > 0

                val verdict = if (stable) {
                    "Smooth hardware encoding confirmed (${achievedFps.roundToInt()} FPS throughput, ${"%.1f".format(avgEncodeMs)} ms/frame)."
                } else {
                    "Encoder struggled at ${settings.resolution.shortLabel} ${targetFps}fps (${achievedFps.roundToInt()} FPS achieved). Consider lowering resolution or enabling Adaptive FPS."
                }

                VideoQualityTestResult(
                    passed = stable,
                    encoderName = codecName,
                    resolutionLabel = "${width}x${height}",
                    targetFps = targetFps,
                    achievedFps = achievedFps,
                    avgFrameEncodeMs = avgEncodeMs,
                    summary = verdict
                )
            } catch (e: Exception) {
                VideoQualityTestResult(
                    passed = false,
                    encoderName = settings.codec.displayName,
                    resolutionLabel = "${width}x${height}",
                    targetFps = targetFps,
                    achievedFps = 0f,
                    avgFrameEncodeMs = 0f,
                    summary = "Hardware test failed: ${e.localizedMessage ?: "Unsupported encoder configuration."}"
                )
            } finally {
                runCatching { inputSurface?.release() }
                runCatching { codec?.stop() }
                runCatching { codec?.release() }
            }
        }
}
