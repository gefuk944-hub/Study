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
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.data.RecorderSettings
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs
import kotlin.math.tanh

enum class AudioSourceRuntimeStatus(val label: String) {
    ACTIVE("Active"),
    COMBINED_WITH_GAME("Combined w/ Game"),
    MUTED("Muted"),
    DISABLED("Off"),
    NO_SIGNAL_OR_BLOCKED("No Signal / Blocked by App"),
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
 * Real-time Three-Source Audio Capture, DSP, Mixing & AAC Encoder Engine.
 *
 * Captures and synchronizes:
 * 1. Game Audio via AudioPlaybackCapture (USAGE_GAME + USAGE_MEDIA + USAGE_UNKNOWN) — zero NC!
 * 2. Player Microphone via AudioRecord + Hardware AudioEffects + AudioDspProcessor
 * 3. Teammate Voice Chat via dedicated AudioPlaybackCapture (USAGE_VOICE_COMMUNICATION)
 *    with automatic fallback to unified game+voice playback capture if the OEM limits
 *    simultaneous AudioPlaybackCapture sessions, and honest detection if a game blocks capture.
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
        private const val TAG = "ThreeSourceAudioMixer"
        const val SAMPLE_RATE = 48000
        const val CHANNEL_COUNT = 2
        const val BITRATE = 192_000
        private const val FRAME_SAMPLES_PER_CHANNEL = 960 // 20ms frame
        private const val STEREO_FRAME_SHORTS = FRAME_SAMPLES_PER_CHANNEL * CHANNEL_COUNT
    }

    @Volatile
    private var settings: RecorderSettings = initialSettings

    @Volatile
    private var isPaused: Boolean = false

    private val isRunning = AtomicBoolean(false)
    private var mixerThread: Thread? = null

    private var gameRecord: AudioRecord? = null
    private var micRecord: AudioRecord? = null
    private var teamRecord: AudioRecord? = null

    private var hardwareNs: NoiseSuppressor? = null
    private var hardwareAec: AcousticEchoCanceler? = null
    private var hardwareAgc: AutomaticGainControl? = null

    private var audioEncoder: MediaCodec? = null

    private var gameStatus = AudioSourceRuntimeStatus.DISABLED
    private var micStatus = AudioSourceRuntimeStatus.DISABLED
    private var teamStatus = AudioSourceRuntimeStatus.DISABLED
    private var teamNote = ""
    private var teamCombinedWithGame = false

    fun updateSettings(newSettings: RecorderSettings) {
        settings = newSettings
        runCatching { hardwareNs?.enabled = newSettings.noiseCancellation.useAndroidNs }
        runCatching { hardwareAec?.enabled = newSettings.echoReduction }
        runCatching { hardwareAgc?.enabled = newSettings.autoGainControl }
    }

    fun setPaused(paused: Boolean) {
        isPaused = paused
    }

    fun hasAnyActiveSource(): Boolean {
        return gameRecord != null || micRecord != null || teamRecord != null
    }

    fun start(recordingStartNanoProvider: () -> Long, pausedDurationUsProvider: () -> Long): Boolean {
        if (isRunning.getAndSet(true)) return true

        val hasMicPerm = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        setupPlaybackCaptureSources(hasMicPerm)
        setupMicrophoneSource(hasMicPerm)

        if (!hasAnyActiveSource()) {
            Log.w(TAG, "No audio sources available to start; continuing video-only")
            emitTelemetry(0f, 0f, 0f)
            isRunning.set(false)
            return false
        }

        if (!setupAacEncoder()) {
            releaseAll()
            isRunning.set(false)
            return false
        }

        emitTelemetry(0f, 0f, 0f)

        mixerThread = Thread({
            runMixingLoop(recordingStartNanoProvider, pausedDurationUsProvider)
        }, "ZeroRec-AudioMixer").apply {
            priority = Thread.MAX_PRIORITY
            start()
        }
        return true
    }

    @SuppressLint("MissingPermission")
    private fun setupPlaybackCaptureSources(hasMicPerm: Boolean) {
        val s = settings
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            if (s.gameAudioEnabled) gameStatus = AudioSourceRuntimeStatus.UNSUPPORTED_ANDROID_VERSION
            if (s.teamVoiceEnabled) {
                teamStatus = AudioSourceRuntimeStatus.UNSUPPORTED_ANDROID_VERSION
                teamNote = "Internal playback & teammate voice capture require Android 10 (API 29) or newer."
            }
            return
        }
        if (mediaProjection == null) {
            gameStatus = AudioSourceRuntimeStatus.DISABLED
            teamStatus = AudioSourceRuntimeStatus.DISABLED
            return
        }
        if (!hasMicPerm) {
            if (s.gameAudioEnabled) gameStatus = AudioSourceRuntimeStatus.PERMISSION_MISSING
            if (s.teamVoiceEnabled) {
                teamStatus = AudioSourceRuntimeStatus.PERMISSION_MISSING
                teamNote = "Android requires Microphone permission even for internal AudioPlaybackCapture."
            }
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

        // 1. Try dedicated Teammate Voice Capture stream (USAGE_VOICE_COMMUNICATION) first if enabled
        if (s.teamVoiceEnabled) {
            val teamConfig = AudioPlaybackCaptureConfiguration.Builder(mediaProjection)
                .addMatchingUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                .addMatchingUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION_SIGNALLING)
                .build()

            val candidateTeam = runCatching {
                AudioRecord.Builder()
                    .setAudioFormat(stereoFormat)
                    .setBufferSizeInBytes(minBuf * 2)
                    .setAudioPlaybackCaptureConfig(teamConfig)
                    .build()
            }.getOrNull()

            if (candidateTeam != null && candidateTeam.state == AudioRecord.STATE_INITIALIZED) {
                teamRecord = candidateTeam
                teamStatus = if (s.teamVoiceMuted) AudioSourceRuntimeStatus.MUTED else AudioSourceRuntimeStatus.ACTIVE
                teamNote = "Dedicated voice-chat capture active (when permitted by game/VoIP app)."
            } else {
                runCatching { candidateTeam?.release() }
                teamCombinedWithGame = true
            }
        }

        // 2. Game Audio Capture (USAGE_GAME + USAGE_MEDIA + USAGE_UNKNOWN)
        // If dedicated team capture was unavailable on this OEM, combine USAGE_VOICE_COMMUNICATION into game stream
        if (s.gameAudioEnabled || (s.teamVoiceEnabled && teamCombinedWithGame)) {
            val gameConfigBuilder = AudioPlaybackCaptureConfiguration.Builder(mediaProjection)
                .addMatchingUsage(AudioAttributes.USAGE_GAME)
                .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)

            if (s.teamVoiceEnabled && teamCombinedWithGame) {
                gameConfigBuilder
                    .addMatchingUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .addMatchingUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION_SIGNALLING)
            }

            val candidateGame = runCatching {
                AudioRecord.Builder()
                    .setAudioFormat(stereoFormat)
                    .setBufferSizeInBytes(minBuf * 2)
                    .setAudioPlaybackCaptureConfig(gameConfigBuilder.build())
                    .build()
            }.getOrNull()

            if (candidateGame != null && candidateGame.state == AudioRecord.STATE_INITIALIZED) {
                gameRecord = candidateGame
                gameStatus = if (s.gameAudioMuted) AudioSourceRuntimeStatus.MUTED else AudioSourceRuntimeStatus.ACTIVE
                if (s.teamVoiceEnabled && teamCombinedWithGame) {
                    teamStatus = AudioSourceRuntimeStatus.COMBINED_WITH_GAME
                    teamNote = "This device allows one playback capture stream; teammate voice is captured together with Game Audio."
                }
            } else {
                runCatching { candidateGame?.release() }
                // Retry once without dedicated team stream in case OEM restricts >1 playback capture
                if (teamRecord != null && s.gameAudioEnabled) {
                    runCatching { teamRecord?.release() }
                    teamRecord = null
                    teamCombinedWithGame = true
                    val fallbackConfig = AudioPlaybackCaptureConfiguration.Builder(mediaProjection)
                        .addMatchingUsage(AudioAttributes.USAGE_GAME)
                        .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                        .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
                        .addMatchingUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                        .build()
                    val combined = runCatching {
                        AudioRecord.Builder()
                            .setAudioFormat(stereoFormat)
                            .setBufferSizeInBytes(minBuf * 2)
                            .setAudioPlaybackCaptureConfig(fallbackConfig)
                            .build()
                    }.getOrNull()
                    if (combined != null && combined.state == AudioRecord.STATE_INITIALIZED) {
                        gameRecord = combined
                        gameStatus = AudioSourceRuntimeStatus.ACTIVE
                        teamStatus = AudioSourceRuntimeStatus.COMBINED_WITH_GAME
                        teamNote = "Device OEM limits to 1 internal stream: capturing Game + Team Voice combined."
                    } else {
                        runCatching { combined?.release() }
                        gameStatus = AudioSourceRuntimeStatus.NO_SIGNAL_OR_BLOCKED
                        teamStatus = AudioSourceRuntimeStatus.NO_SIGNAL_OR_BLOCKED
                        teamNote = "Internal audio capture could not start on this device."
                    }
                } else {
                    gameStatus = AudioSourceRuntimeStatus.NO_SIGNAL_OR_BLOCKED
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun setupMicrophoneSource(hasMicPerm: Boolean) {
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
        ).coerceAtLeast(FRAME_SAMPLES_PER_CHANNEL * 4)

        val candidateMic = runCatching {
            AudioRecord(
                s.micSource.androidSource,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                minBuf * 2
            )
        }.getOrNull()

        if (candidateMic != null && candidateMic.state == AudioRecord.STATE_INITIALIZED) {
            micRecord = candidateMic
            val sessionId = candidateMic.audioSessionId
            if (NoiseSuppressor.isAvailable()) {
                hardwareNs = runCatching { NoiseSuppressor.create(sessionId) }.getOrNull()
                hardwareNs?.enabled = s.noiseCancellation.useAndroidNs
            }
            if (AcousticEchoCanceler.isAvailable()) {
                hardwareAec = runCatching { AcousticEchoCanceler.create(sessionId) }.getOrNull()
                hardwareAec?.enabled = s.echoReduction
            }
            if (AutomaticGainControl.isAvailable()) {
                hardwareAgc = runCatching { AutomaticGainControl.create(sessionId) }.getOrNull()
                hardwareAgc?.enabled = s.autoGainControl
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

    private fun runMixingLoop(
        recordingStartNanoProvider: () -> Long,
        pausedDurationUsProvider: () -> Long
    ) {
        val gameBuf = ShortArray(STEREO_FRAME_SHORTS)
        val teamBuf = ShortArray(STEREO_FRAME_SHORTS)
        val micBuf = ShortArray(FRAME_SAMPLES_PER_CHANNEL)
        val mixedBytes = ByteArray(STEREO_FRAME_SHORTS * 2)

        val dsp = AudioDspProcessor(SAMPLE_RATE)
        val bufferInfo = MediaCodec.BufferInfo()

        runCatching { gameRecord?.startRecording() }
        runCatching { teamRecord?.startRecording() }
        runCatching { micRecord?.startRecording() }

        var totalSamplesEncoded = 0L
        var consecutiveSilentTeamFrames = 0
        var consecutiveSilentGameFrames = 0
        var telemetryTick = 0
        var smoothDuckGain = 1.0f

        try {
            while (isRunning.get()) {
                val s = settings

                // Read available PCM streams (non-blocking for secondary sources so one stalled source never blocks the mix)
                val gameRead = gameRecord?.let { rec ->
                    val mode = if (micRecord == null) AudioRecord.READ_BLOCKING else AudioRecord.READ_NON_BLOCKING
                    rec.read(gameBuf, 0, STEREO_FRAME_SHORTS, mode).coerceAtLeast(0)
                } ?: 0

                val teamRead = teamRecord?.let { rec ->
                    rec.read(teamBuf, 0, STEREO_FRAME_SHORTS, AudioRecord.READ_NON_BLOCKING).coerceAtLeast(0)
                } ?: 0

                val micRead = micRecord?.let { rec ->
                    rec.read(micBuf, 0, FRAME_SAMPLES_PER_CHANNEL, AudioRecord.READ_BLOCKING).coerceAtLeast(0)
                } ?: 0

                if (micRecord == null && gameRecord == null && teamRecord != null) {
                    teamRecord?.read(teamBuf, 0, STEREO_FRAME_SHORTS, AudioRecord.READ_BLOCKING)
                }

                // If paused, drain inputs without encoding into muxer
                if (isPaused) {
                    if (telemetryTick++ % 5 == 0) emitTelemetry(0f, 0f, 0f)
                    continue
                }

                // 1. Process Microphone strictly through Mic DSP chain (never touches game/team audio!)
                val micMeter = if (micRead > 0 && s.micEnabled && !s.micMuted) {
                    dsp.processMicBufferInPlace(
                        samples = micBuf,
                        sampleCount = micRead,
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

                // 2. Measure untouched Game & Team RMS levels
                val gameLevel = if (gameRead > 0 && s.gameAudioEnabled && !s.gameAudioMuted) {
                    AudioDspProcessor.computeBufferRmsNormalized(gameBuf, gameRead)
                } else 0f

                val teamLevel = if (teamRead > 0 && s.teamVoiceEnabled && !s.teamVoiceMuted) {
                    AudioDspProcessor.computeBufferRmsNormalized(teamBuf, teamRead)
                } else 0f

                // Detect if a game blocks internal voice or game capture (after ~4 seconds of silence)
                if (teamRecord != null && s.teamVoiceEnabled && !s.teamVoiceMuted) {
                    if (teamLevel > 0.01f) {
                        consecutiveSilentTeamFrames = 0
                        teamStatus = AudioSourceRuntimeStatus.ACTIVE
                        teamNote = "Teammate voice stream detected and mixing cleanly."
                    } else if (++consecutiveSilentTeamFrames > 250) {
                        teamStatus = AudioSourceRuntimeStatus.NO_SIGNAL_OR_BLOCKED
                        teamNote = "No teammate voice signal detected. If teammates are speaking, this game may block OS voice capture—try speakerphone mode or Discord capture."
                    }
                }
                if (gameRecord != null && s.gameAudioEnabled && !s.gameAudioMuted) {
                    if (gameLevel > 0.01f) {
                        consecutiveSilentGameFrames = 0
                        gameStatus = AudioSourceRuntimeStatus.ACTIVE
                    } else if (++consecutiveSilentGameFrames > 300) {
                        gameStatus = AudioSourceRuntimeStatus.NO_SIGNAL_OR_BLOCKED
                    }
                }
                if (micRecord != null) {
                    micStatus = if (s.micMuted || !s.micEnabled) AudioSourceRuntimeStatus.MUTED else AudioSourceRuntimeStatus.ACTIVE
                }
                if (gameRecord != null && (s.gameAudioMuted || !s.gameAudioEnabled)) {
                    gameStatus = AudioSourceRuntimeStatus.MUTED
                }
                if (teamRecord != null && (s.teamVoiceMuted || !s.teamVoiceEnabled)) {
                    teamStatus = AudioSourceRuntimeStatus.MUTED
                }

                // 3. Smart Voice Balance: gently duck game audio (~-3.5 dB) when player or teammates speak
                val voiceActive = (micMeter.outputLevelNormalized > 0.16f) || (teamLevel > 0.16f)
                val targetDuck = if (s.smartVoiceBalance && voiceActive) 0.68f else 1.0f
                val duckCoeff = if (targetDuck < smoothDuckGain) 0.25f else 0.05f
                smoothDuckGain += duckCoeff * (targetDuck - smoothDuckGain)

                val gameGain = if (s.gameAudioEnabled && !s.gameAudioMuted) s.gameAudioVolume * smoothDuckGain else 0f
                val micGain = if (s.micEnabled && !s.micMuted) s.micVolume else 0f
                val teamGain = if (s.teamVoiceEnabled && !s.teamVoiceMuted) s.teamVoiceVolume else 0f

                // 4. 32-bit float mix with soft-knee clipping protection
                val frames = FRAME_SAMPLES_PER_CHANNEL
                for (f in 0 until frames) {
                    val stereoIdx = f * 2
                    val gL = if (stereoIdx < gameRead) (gameBuf[stereoIdx] / 32768f) * gameGain else 0f
                    val gR = if (stereoIdx + 1 < gameRead) (gameBuf[stereoIdx + 1] / 32768f) * gameGain else 0f

                    val tL = if (stereoIdx < teamRead) (teamBuf[stereoIdx] / 32768f) * teamGain else 0f
                    val tR = if (stereoIdx + 1 < teamRead) (teamBuf[stereoIdx + 1] / 32768f) * teamGain else 0f

                    val m = if (f < micRead) (micBuf[f] / 32768f) * micGain else 0f

                    var mixL = gL + tL + m
                    var mixR = gR + tR + m

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
                    mixedBytes[byteIdx + 1] = ((outL shr 8) and 0xFF).toByte()
                    mixedBytes[byteIdx + 2] = (outR and 0xFF).toByte()
                    mixedBytes[byteIdx + 3] = ((outR shr 8) and 0xFF).toByte()
                }

                // 5. Feed mixed PCM into AAC MediaCodec synchronized with video clock
                val codec = audioEncoder ?: break
                val inputIndex = codec.dequeueInputBuffer(10_000)
                if (inputIndex >= 0) {
                    val inputBuffer = codec.getInputBuffer(inputIndex)
                    inputBuffer?.clear()
                    inputBuffer?.put(mixedBytes)

                    val ptsUs = if (recordingStartNanoProvider() > 0L) {
                        val elapsedUs = (System.nanoTime() - recordingStartNanoProvider()) / 1000L -
                            pausedDurationUsProvider()
                        maxOf(elapsedUs, (totalSamplesEncoded * 1_000_000L) / SAMPLE_RATE)
                    } else {
                        (totalSamplesEncoded * 1_000_000L) / SAMPLE_RATE
                    }
                    totalSamplesEncoded += frames
                    codec.queueInputBuffer(inputIndex, 0, mixedBytes.size, ptsUs.coerceAtLeast(0L), 0)
                }

                drainEncoder(codec, bufferInfo, endOfStream = false)

                if (telemetryTick++ % 3 == 0) {
                    emitTelemetry(
                        gameLevel = gameLevel,
                        micLevel = micMeter.outputLevelNormalized,
                        teamLevel = if (teamCombinedWithGame) gameLevel * 0.5f else teamLevel
                    )
                }
            }

            // Signal End-of-Stream cleanly
            audioEncoder?.let { codec ->
                val inIdx = codec.dequeueInputBuffer(10_000)
                if (inIdx >= 0) {
                    val ptsUs = (totalSamplesEncoded * 1_000_000L) / SAMPLE_RATE
                    codec.queueInputBuffer(inIdx, 0, 0, ptsUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                }
                drainEncoder(codec, bufferInfo, endOfStream = true)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error inside 3-source audio mixer loop", e)
        } finally {
            releaseAll()
            emitTelemetry(0f, 0f, 0f)
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
                        onEncodedAudioReady(outBuf, bufferInfo)
                    }
                    codec.releaseOutputBuffer(outIndex, false)
                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) break
                }
            }
        }
    }

    private fun softLimit(x: Float): Float {
        val a = abs(x)
        if (a <= 0.85f) return x
        val limited = 0.85f + 0.13f * tanh(((a - 0.85f) / 0.13f).toDouble()).toFloat()
        return if (x < 0f) -limited else limited
    }

    private fun emitTelemetry(gameLevel: Float, micLevel: Float, teamLevel: Float) {
        onTelemetryUpdate(
            LiveAudioTelemetry(
                gameLevel = gameLevel,
                micLevel = micLevel,
                teamLevel = teamLevel,
                gameStatus = gameStatus,
                micStatus = micStatus,
                teamStatus = teamStatus,
                teamVoiceNote = teamNote
            )
        )
    }

    fun stop() {
        isRunning.set(false)
        runCatching { mixerThread?.join(1500) }
        mixerThread = null
        releaseAll()
    }

    private fun releaseAll() {
        runCatching { gameRecord?.stop() }; runCatching { gameRecord?.release() }; gameRecord = null
        runCatching { teamRecord?.stop() }; runCatching { teamRecord?.release() }; teamRecord = null
        runCatching { micRecord?.stop() }; runCatching { micRecord?.release() }; micRecord = null
        runCatching { hardwareNs?.release() }; hardwareNs = null
        runCatching { hardwareAec?.release() }; hardwareAec = null
        runCatching { hardwareAgc?.release() }; hardwareAgc = null
        runCatching { audioEncoder?.stop() }; runCatching { audioEncoder?.release() }; audioEncoder = null
    }
}
