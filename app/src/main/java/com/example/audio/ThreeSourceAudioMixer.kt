package com.example.audio

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import android.media.projection.MediaProjection
import android.os.Build
import android.os.Process
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.data.MicAudioSource
import com.example.data.RecorderSettings
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs
import kotlin.math.tanh

enum class AudioSourceRuntimeStatus(val label: String) {
    ACTIVE("Active"),
    COMBINED_WITH_GAME("Game + Team Active"),
    MUTED("Muted"),
    DISABLED("Off"),
    NO_SIGNAL_OR_BLOCKED("Waiting / App Silent"),
    UNSUPPORTED_ANDROID_VERSION("Requires Android 10+"),
    PERMISSION_MISSING("Permission Required")
}

data class LiveAudioTelemetry(
    val gameLevel: Float = 0f,
    val micLevel: Float = 0f,
    val teamLevel: Float = 0f,
    val gameStatus: AudioSourceRuntimeStatus = AudioSourceRuntimeStatus.DISABLED,
    val micStatus: AudioSourceRuntimeStatus = AudioSourceRuntimeStatus.DISABLED,
    val teamStatus: AudioSourceRuntimeStatus = AudioSourceRuntimeStatus.DISABLED,
    val teamVoiceNote: String = ""
)

/**
 * Thread-safe circular PCM short ring buffer separating blocking AudioRecord producers
 * from the deterministic 1024-sample AAC-LC mixer/encoder clock.
 * Eliminates READ_NON_BLOCKING partial reads, buffer glitches, and static/crackling.
 */
private class PcmRingBuffer(private val capacityShorts: Int) {
    private val ring = ShortArray(capacityShorts)
    private var writePos = 0
    private var readPos = 0
    private var count = 0

    @Synchronized
    fun write(data: ShortArray, length: Int) {
        if (length <= 0) return
        // If producer runs slightly ahead, drop oldest samples cleanly to keep latency low (<60ms)
        if (length >= capacityShorts) {
            val start = length - capacityShorts
            System.arraycopy(data, start, ring, 0, capacityShorts)
            readPos = 0
            writePos = 0
            count = capacityShorts
            return
        }
        val overflow = (count + length) - capacityShorts
        if (overflow > 0) {
            readPos = (readPos + overflow) % capacityShorts
            count -= overflow
        }
        var remaining = length
        var srcIdx = 0
        while (remaining > 0) {
            val chunk = minOf(remaining, capacityShorts - writePos)
            System.arraycopy(data, srcIdx, ring, writePos, chunk)
            writePos = (writePos + chunk) % capacityShorts
            srcIdx += chunk
            remaining -= chunk
        }
        count += length
    }

    @Synchronized
    fun readOrZero(dest: ShortArray, length: Int): Int {
        if (count < length) {
            // Read whatever is available, zero-fill remainder (smoothly avoids stale-buffer static!)
            val avail = count
            var rem = avail
            var dstIdx = 0
            while (rem > 0) {
                val chunk = minOf(rem, capacityShorts - readPos)
                System.arraycopy(ring, readPos, dest, dstIdx, chunk)
                readPos = (readPos + chunk) % capacityShorts
                dstIdx += chunk
                rem -= chunk
            }
            count = 0
            for (i in avail until length) dest[i] = 0
            return avail
        }
        var remaining = length
        var dstIdx = 0
        while (remaining > 0) {
            val chunk = minOf(remaining, capacityShorts - readPos)
            System.arraycopy(ring, readPos, dest, dstIdx, chunk)
            readPos = (readPos + chunk) % capacityShorts
            dstIdx += chunk
            remaining -= chunk
        }
        count -= length
        return length
    }

    @Synchronized
    fun clear() {
        writePos = 0
        readPos = 0
        count = 0
    }
}

/**
 * Studio-grade Dual-Stream Synchronized Audio Engine.
 *
 * Unified architecture engineered for crisp zero-crackle Android gameplay recording:
 * 1. **Game & Teammates Audio**: ONE dedicated blocking AudioPlaybackCapture stream (48 kHz Stereo)
 *    capturing USAGE_GAME + USAGE_MEDIA + USAGE_UNKNOWN + USAGE_VOICE_COMMUNICATION together
 *    (with automatic OEM fallback to GAME+MEDIA+UNKNOWN if VOICE_COMMUNICATION is rejected),
 *    with ZERO noise cancellation so footsteps, gunshots, music, and squad chat sound pristine.
 * 2. **My Microphone (Studio Voice)**: Dedicated blocking AudioRecord thread (48 kHz Mono) using
 *    wideband MIC capture (avoids Android telephony SCO distortion!) processed through our
 *    click-free studio AudioDspProcessor.
 * 3. **Deterministic 1024-Sample AAC-LC Mixer & Encoder**: Pure monotonic sample-clock PTS
 *    (zero wall-clock timestamp jitter -> zero crackling/popping!).
 */
class ThreeSourceAudioMixer(
    private val context: Context,
    private val mediaProjection: MediaProjection?,
    private val initialSettings: RecorderSettings,
    private val onEncodedAudioReady: (ByteBuffer, MediaCodec.BufferInfo) -> Unit,
    private val onAudioFormatChanged: (MediaFormat) -> Unit,
    private val onTelemetryUpdate: (LiveAudioTelemetry) -> Unit
) {
    companion object {
        private const val TAG = "ZeroStudioAudioMixer"
        const val SAMPLE_RATE = 48000
        const val CHANNEL_COUNT = 2
        const val BITRATE = 192_000
        // Exact native AAC-LC frame size = 1024 samples/channel (21.333 ms @ 48 kHz)
        private const val AAC_FRAME_SAMPLES = 1024
        private const val STEREO_FRAME_SHORTS = AAC_FRAME_SAMPLES * CHANNEL_COUNT
    }

    @Volatile
    private var settings: RecorderSettings = initialSettings

    @Volatile
    private var isPaused: Boolean = false

    private val isRunning = AtomicBoolean(false)

    private var gameCaptureThread: Thread? = null
    private var micCaptureThread: Thread? = null
    private var mixerThread: Thread? = null

    private var gameRecord: AudioRecord? = null
    private var micRecord: AudioRecord? = null

    private var hardwareNs: NoiseSuppressor? = null
    private var hardwareAec: AcousticEchoCanceler? = null
    private var hardwareAgc: AutomaticGainControl? = null

    private var audioEncoder: MediaCodec? = null

    // ~170ms ring buffers decouple hardware capture jitter from AAC encoding
    private val gameRing = PcmRingBuffer(STEREO_FRAME_SHORTS * 8)
    private val micRing = PcmRingBuffer(AAC_FRAME_SAMPLES * 8)

    @Volatile private var gameStatus = AudioSourceRuntimeStatus.DISABLED
    @Volatile private var micStatus = AudioSourceRuntimeStatus.DISABLED
    @Volatile private var teamVoiceNote = ""

    fun updateSettings(newSettings: RecorderSettings) {
        settings = newSettings
        val useHwNs = newSettings.noiseCancellation.useAndroidNs
        runCatching { hardwareNs?.enabled = useHwNs }
        runCatching { hardwareAec?.enabled = newSettings.echoReduction && newSettings.micSource == MicAudioSource.VOICE_COMMUNICATION }
        runCatching { hardwareAgc?.enabled = newSettings.autoGainControl && newSettings.micSource == MicAudioSource.VOICE_COMMUNICATION }
    }

    fun setPaused(paused: Boolean) {
        isPaused = paused
        if (paused) {
            gameRing.clear()
            micRing.clear()
        }
    }

    fun hasAnyActiveSource(): Boolean = gameRecord != null || micRecord != null

    fun start(recordingStartNanoProvider: () -> Long, pausedDurationUsProvider: () -> Long): Boolean {
        if (isRunning.getAndSet(true)) return true

        val hasMicPerm = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        setupUnifiedGameAndTeamCapture(hasMicPerm)
        setupStudioMicrophone(hasMicPerm)

        if (!hasAnyActiveSource()) {
            Log.w(TAG, "No audio sources available; continuing video-only")
            emitTelemetry(0f, 0f)
            isRunning.set(false)
            return false
        }

        if (!setupAacEncoder()) {
            releaseAll()
            isRunning.set(false)
            return false
        }

        emitTelemetry(0f, 0f)

        // Start dedicated blocking producer threads
        gameRecord?.let { rec ->
            gameCaptureThread = Thread({
                Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
                val buf = ShortArray(STEREO_FRAME_SHORTS)
                runCatching { rec.startRecording() }
                while (isRunning.get()) {
                    val read = rec.read(buf, 0, buf.size, AudioRecord.READ_BLOCKING)
                    if (read > 0 && !isPaused) {
                        gameRing.write(buf, read)
                    } else if (read < 0) {
                        break
                    }
                }
            }, "ZeroRec-GameAudioIn").apply { start() }
        }

        micRecord?.let { rec ->
            micCaptureThread = Thread({
                Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
                val buf = ShortArray(AAC_FRAME_SAMPLES)
                runCatching { rec.startRecording() }
                while (isRunning.get()) {
                    val read = rec.read(buf, 0, buf.size, AudioRecord.READ_BLOCKING)
                    if (read > 0 && !isPaused) {
                        micRing.write(buf, read)
                    } else if (read < 0) {
                        break
                    }
                }
            }, "ZeroRec-MicAudioIn").apply { start() }
        }

        mixerThread = Thread({
            Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
            runStudioMixingLoop()
        }, "ZeroRec-AudioMixer").apply { start() }

        return true
    }

    /**
     * Unified single AudioPlaybackCapture stream for BOTH Game Audio & Teammate Voice Chat.
     * Avoids dual-session OEM conflicts and guarantees reliable internal game audio capture.
     */
    @SuppressLint("MissingPermission")
    private fun setupUnifiedGameAndTeamCapture(hasMicPerm: Boolean) {
        val s = settings
        if (!s.gameAudioEnabled) {
            gameStatus = AudioSourceRuntimeStatus.DISABLED
            teamVoiceNote = "Game & Teammates audio is turned off."
            return
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            gameStatus = AudioSourceRuntimeStatus.UNSUPPORTED_ANDROID_VERSION
            teamVoiceNote = "Internal Game & Teammate audio requires Android 10 (API 29) or newer."
            return
        }
        if (mediaProjection == null) {
            gameStatus = AudioSourceRuntimeStatus.DISABLED
            return
        }
        if (!hasMicPerm) {
            gameStatus = AudioSourceRuntimeStatus.PERMISSION_MISSING
            teamVoiceNote = "Android requires Microphone permission to enable internal Game Audio capture."
            return
        }

        val minBuf = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_STEREO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(STEREO_FRAME_SHORTS * 4)

        val stereoFormat = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(SAMPLE_RATE)
            .setChannelMask(AudioFormat.CHANNEL_IN_STEREO)
            .build()

        // Attempt 1: Full unified Game + Media + Teammate Voice Chat capture
        val unifiedConfig = AudioPlaybackCaptureConfiguration.Builder(mediaProjection)
            .addMatchingUsage(AudioAttributes.USAGE_GAME)
            .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
            .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
            .addMatchingUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
            .addMatchingUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION_SIGNALLING)
            .build()

        var record = tryBuildPlaybackRecord(stereoFormat, minBuf * 3, unifiedConfig)
        if (record != null) {
            gameRecord = record
            gameStatus = if (s.gameAudioMuted) AudioSourceRuntimeStatus.MUTED else AudioSourceRuntimeStatus.COMBINED_WITH_GAME
            teamVoiceNote = "Unified internal capture active: Game sounds + Teammate voice chat recording together."
            return
        }

        // Attempt 2: OEM fallback (Game + Media + Unknown) if OEM rejects VOICE_COMMUNICATION usage
        Log.w(TAG, "Unified playback capture with VOICE_COMMUNICATION failed; retrying Game+Media")
        val gameOnlyConfig = AudioPlaybackCaptureConfiguration.Builder(mediaProjection)
            .addMatchingUsage(AudioAttributes.USAGE_GAME)
            .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
            .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
            .build()

        record = tryBuildPlaybackRecord(stereoFormat, minBuf * 3, gameOnlyConfig)
        if (record != null) {
            gameRecord = record
            gameStatus = if (s.gameAudioMuted) AudioSourceRuntimeStatus.MUTED else AudioSourceRuntimeStatus.ACTIVE
            teamVoiceNote = "Internal Game Audio active (this device restricts OS voice-call stream capture)."
        } else {
            gameStatus = AudioSourceRuntimeStatus.NO_SIGNAL_OR_BLOCKED
            teamVoiceNote = "Could not initialize internal audio capture on this device."
        }
    }

    @SuppressLint("MissingPermission")
    private fun tryBuildPlaybackRecord(
        format: AudioFormat,
        bufferSizeBytes: Int,
        config: AudioPlaybackCaptureConfiguration
    ): AudioRecord? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        val rec = runCatching {
            AudioRecord.Builder()
                .setAudioFormat(format)
                .setBufferSizeInBytes(bufferSizeBytes)
                .setAudioPlaybackCaptureConfig(config)
                .build()
        }.getOrNull()
        return if (rec != null && rec.state == AudioRecord.STATE_INITIALIZED) {
            rec
        } else {
            runCatching { rec?.release() }
            null
        }
    }

    @SuppressLint("MissingPermission")
    private fun setupStudioMicrophone(hasMicPerm: Boolean) {
        val s = settings
        if (!s.micEnabled) {
            micStatus = AudioSourceRuntimeStatus.DISABLED
            return
        }
        if (!hasMicPerm) {
            micStatus = AudioSourceRuntimeStatus.PERMISSION_MISSING
            return
        }

        val minBuf = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(AAC_FRAME_SAMPLES * 4)

        val candidateMic = runCatching {
            AudioRecord(
                s.micSource.androidSource,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                minBuf * 3
            )
        }.getOrNull()

        if (candidateMic != null && candidateMic.state == AudioRecord.STATE_INITIALIZED) {
            micRecord = candidateMic
            val sessionId = candidateMic.audioSessionId
            // Only attach hardware NS cleanly (never stack hardware telephony AGC on wideband MIC!)
            if (NoiseSuppressor.isAvailable()) {
                hardwareNs = runCatching { NoiseSuppressor.create(sessionId) }.getOrNull()
                hardwareNs?.enabled = s.noiseCancellation.useAndroidNs
            }
            if (s.micSource == MicAudioSource.VOICE_COMMUNICATION) {
                if (AcousticEchoCanceler.isAvailable()) {
                    hardwareAec = runCatching { AcousticEchoCanceler.create(sessionId) }.getOrNull()
                    hardwareAec?.enabled = s.echoReduction
                }
                if (AutomaticGainControl.isAvailable()) {
                    hardwareAgc = runCatching { AutomaticGainControl.create(sessionId) }.getOrNull()
                    hardwareAgc?.enabled = s.autoGainControl
                }
            }
            micStatus = if (s.micMuted) AudioSourceRuntimeStatus.MUTED else AudioSourceRuntimeStatus.ACTIVE
        } else {
            runCatching { candidateMic?.release() }
            micStatus = AudioSourceRuntimeStatus.NO_SIGNAL_OR_BLOCKED
        }
    }

    private fun setupAacEncoder(): Boolean {
        return try {
            val format = MediaFormat.createAudioFormat(
                MediaFormat.MIMETYPE_AUDIO_AAC,
                SAMPLE_RATE,
                CHANNEL_COUNT
            ).apply {
                setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                setInteger(MediaFormat.KEY_BIT_RATE, BITRATE)
                setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, STEREO_FRAME_SHORTS * 4)
            }
            val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            codec.start()
            audioEncoder = codec
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize AAC encoder", e)
            false
        }
    }

    private fun runStudioMixingLoop() {
        val gameBuf = ShortArray(STEREO_FRAME_SHORTS)
        val micBuf = ShortArray(AAC_FRAME_SAMPLES)
        val mixedBytes = ByteArray(STEREO_FRAME_SHORTS * 2) // 4096 bytes = 1024 stereo PCM16 frames

        val dsp = AudioDspProcessor(SAMPLE_RATE)
        val bufferInfo = MediaCodec.BufferInfo()

        // Deterministic 21.333 ms frame clock (1024 samples @ 48 kHz)
        val frameDurationNs = (AAC_FRAME_SAMPLES * 1_000_000_000L) / SAMPLE_RATE
        var nextFrameDeadlineNs = System.nanoTime() + frameDurationNs
        var totalSamplesEncoded = 0L
        var telemetryTick = 0
        var smoothDuckGain = 1.0f

        try {
            while (isRunning.get()) {
                // Sleep accurately until next 21.333 ms AAC frame boundary
                val nowNs = System.nanoTime()
                val sleepNs = nextFrameDeadlineNs - nowNs
                if (sleepNs > 1_000_000L) {
                    val ms = sleepNs / 1_000_000L
                    val nsRem = (sleepNs % 1_000_000L).toInt()
                    runCatching { Thread.sleep(ms, nsRem) }
                }
                nextFrameDeadlineNs += frameDurationNs
                // Resync deadline if paused or delayed >100ms
                if (System.nanoTime() - nextFrameDeadlineNs > 100_000_000L) {
                    nextFrameDeadlineNs = System.nanoTime() + frameDurationNs
                }

                if (isPaused) {
                    if (telemetryTick++ % 5 == 0) emitTelemetry(0f, 0f)
                    continue
                }

                val s = settings
                val gameRead = if (gameRecord != null) gameRing.readOrZero(gameBuf, STEREO_FRAME_SHORTS) else 0
                val micRead = if (micRecord != null) micRing.readOrZero(micBuf, AAC_FRAME_SAMPLES) else 0

                // 1. Process Microphone strictly through Studio DSP (never touches game/team audio!)
                val micActive = micRead > 0 && s.micEnabled && !s.micMuted
                val micMeter = if (micActive) {
                    dsp.processMicBufferInPlace(
                        samples = micBuf,
                        sampleCount = AAC_FRAME_SAMPLES,
                        micGainDb = s.micGainDb,
                        noiseCancellation = s.noiseCancellation,
                        windReductionEnabled = s.windReductionEnabled,
                        windReductionIntensity = s.windReductionIntensity,
                        voiceEnhancementEnabled = s.voiceEnhancementEnabled,
                        autoGainControl = s.autoGainControl,
                        clippingProtection = s.clippingProtection
                    )
                } else {
                    DspMeterResult(0f, 0f, -96f, -96f, -96f, 0f, false)
                }

                // 2. Measure untouched Game & Teammates Stereo RMS
                val gameActive = gameRead > 0 && s.gameAudioEnabled && !s.gameAudioMuted
                val gameLevel = if (gameActive) {
                    AudioDspProcessor.computeBufferRmsNormalized(gameBuf, STEREO_FRAME_SHORTS)
                } else 0f

                // Update live channel statuses cleanly
                if (micRecord != null) {
                    micStatus = if (s.micMuted || !s.micEnabled) AudioSourceRuntimeStatus.MUTED else AudioSourceRuntimeStatus.ACTIVE
                }
                if (gameRecord != null) {
                    gameStatus = when {
                        s.gameAudioMuted || !s.gameAudioEnabled -> AudioSourceRuntimeStatus.MUTED
                        else -> AudioSourceRuntimeStatus.COMBINED_WITH_GAME
                    }
                }

                // 3. Smart Voice Balance: smoothly lower game sound slightly (~-3 dB) while user speaks
                val userSpeaking = micMeter.outputLevelNormalized > 0.18f
                val targetDuck = if (s.smartVoiceBalance && userSpeaking) 0.72f else 1.0f
                val duckCoeff = if (targetDuck < smoothDuckGain) 0.20f else 0.04f
                smoothDuckGain += duckCoeff * (targetDuck - smoothDuckGain)

                val gameGain = if (gameActive) s.gameAudioVolume * smoothDuckGain else 0f
                val micGain = if (micActive) s.micVolume else 0f

                // 4. Clean 32-bit float stereo mix + transparent soft-knee master limiter
                for (f in 0 until AAC_FRAME_SAMPLES) {
                    val stereoIdx = f * 2
                    val gL = (gameBuf[stereoIdx] / 32768f) * gameGain
                    val gR = (gameBuf[stereoIdx + 1] / 32768f) * gameGain
                    val m = (micBuf[f] / 32768f) * micGain

                    var mixL = gL + m
                    var mixR = gR + m

                    if (s.clippingProtection) {
                        mixL = softLimit(mixL)
                        mixR = softLimit(mixR)
                    } else {
                        mixL = mixL.coerceIn(-1f, 1f)
                        mixR = mixR.coerceIn(-1f, 1f)
                    }

                    val outL = (mixL * 32767f).toInt().coerceIn(-32768, 32767)
                    val outR = (mixR * 32767f).toInt().coerceIn(-32768, 32767)

                    val byteIdx = stereoIdx * 2
                    mixedBytes[byteIdx] = (outL and 0xFF).toByte()
                    mixedBytes[byteIdx + 1] = ((outL ushr 8) and 0xFF).toByte()
                    mixedBytes[byteIdx + 2] = (outR and 0xFF).toByte()
                    mixedBytes[byteIdx + 3] = ((outR ushr 8) and 0xFF).toByte()
                }

                // 5. Feed exact 1024-sample frame into AAC MediaCodec with jitter-free sample-clock PTS!
                val codec = audioEncoder ?: break
                val inputIndex = codec.dequeueInputBuffer(15_000L)
                if (inputIndex >= 0) {
                    val inputBuffer = codec.getInputBuffer(inputIndex)
                    inputBuffer?.clear()
                    inputBuffer?.put(mixedBytes)

                    val ptsUs = (totalSamplesEncoded * 1_000_000L) / SAMPLE_RATE
                    totalSamplesEncoded += AAC_FRAME_SAMPLES
                    codec.queueInputBuffer(inputIndex, 0, mixedBytes.size, ptsUs, 0)
                }

                drainEncoder(codec, bufferInfo, endOfStream = false)

                if (telemetryTick++ % 3 == 0) {
                    emitTelemetry(gameLevel = gameLevel, micLevel = micMeter.outputLevelNormalized)
                }
            }

            // Signal End-of-Stream cleanly
            audioEncoder?.let { codec ->
                val inIdx = codec.dequeueInputBuffer(15_000L)
                if (inIdx >= 0) {
                    val ptsUs = (totalSamplesEncoded * 1_000_000L) / SAMPLE_RATE
                    codec.queueInputBuffer(inIdx, 0, 0, ptsUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                }
                drainEncoder(codec, bufferInfo, endOfStream = true)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in studio audio mixer loop", e)
        } finally {
            releaseAll()
            emitTelemetry(0f, 0f)
        }
    }

    private fun drainEncoder(codec: MediaCodec, bufferInfo: MediaCodec.BufferInfo, endOfStream: Boolean) {
        val timeoutUs = if (endOfStream) 15_000L else 0L
        while (true) {
            val outIndex = codec.dequeueOutputBuffer(bufferInfo, timeoutUs)
            when {
                outIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                    if (!endOfStream) break
                }
                outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    onAudioFormatChanged(codec.outputFormat)
                }
                outIndex >= 0 -> {
                    val outBuf = codec.getOutputBuffer(outIndex)
                    if (outBuf != null && bufferInfo.size > 0 &&
                        (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0
                    ) {
                        outBuf.position(bufferInfo.offset)
                        outBuf.limit(bufferInfo.offset + bufferInfo.size)
                        // Pass a fresh BufferInfo copy for thread-safe muxing
                        val copyInfo = MediaCodec.BufferInfo().apply {
                            set(bufferInfo.offset, bufferInfo.size, bufferInfo.presentationTimeUs, bufferInfo.flags)
                        }
                        onEncodedAudioReady(outBuf, copyInfo)
                    }
                    codec.releaseOutputBuffer(outIndex, false)
                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) break
                }
            }
        }
    }

    private fun softLimit(x: Float): Float {
        val a = abs(x)
        if (a <= 0.88f) return x
        val limited = 0.88f + 0.10f * tanh(((a - 0.88f) / 0.10f).toDouble()).toFloat()
        return if (x < 0f) -limited else limited
    }

    private fun emitTelemetry(gameLevel: Float, micLevel: Float) {
        onTelemetryUpdate(
            LiveAudioTelemetry(
                gameLevel = gameLevel,
                micLevel = micLevel,
                teamLevel = gameLevel,
                gameStatus = gameStatus,
                micStatus = micStatus,
                teamStatus = gameStatus,
                teamVoiceNote = teamVoiceNote
            )
        )
    }

    fun stop() {
        isRunning.set(false)
        runCatching { gameRecord?.stop() }
        runCatching { micRecord?.stop() }
        runCatching { gameCaptureThread?.join(800) }
        runCatching { micCaptureThread?.join(800) }
        runCatching { mixerThread?.join(1500) }
        gameCaptureThread = null
        micCaptureThread = null
        mixerThread = null
        releaseAll()
    }

    private fun releaseAll() {
        runCatching { gameRecord?.release() }; gameRecord = null
        runCatching { micRecord?.release() }; micRecord = null
        runCatching { hardwareNs?.release() }; hardwareNs = null
        runCatching { hardwareAec?.release() }; hardwareAec = null
        runCatching { hardwareAgc?.release() }; hardwareAgc = null
        runCatching { audioEncoder?.stop() }; runCatching { audioEncoder?.release() }; audioEncoder = null
        gameRing.clear()
        micRing.clear()
    }
}
