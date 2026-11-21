package com.example.audio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import androidx.core.content.ContextCompat
import com.example.data.RecorderSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream

enum class MicTestPhase {
    IDLE,
    LIVE_MONITORING,
    RECORDING_SAMPLE,
    PLAYING_SAMPLE
}

data class MicTestUiState(
    val phase: MicTestPhase = MicTestPhase.IDLE,
    val inputLevel: Float = 0f,
    val outputLevel: Float = 0f,
    val outputDb: Float = -96f,
    val noiseFloorDb: Float = -96f,
    val gainReductionDb: Float = 0f,
    val clippingProtected: Boolean = false,
    val sampleProgress: Float = 0f,
    val hasRecordedSample: Boolean = false,
    val hardwareNsAttached: Boolean = false,
    val hardwareAecAttached: Boolean = false,
    val hardwareAgcAttached: Boolean = false,
    val errorMessage: String? = null
)

/**
 * Real pre-recording microphone monitor and 5-second processed voice test engine.
 * Runs the exact same AudioDspProcessor and Android hardware AudioEffects used during recording.
 */
class MicTestEngine(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow(MicTestUiState())
    val state: StateFlow<MicTestUiState> = _state.asStateFlow()

    @Volatile
    private var latestSettings: RecorderSettings = RecorderSettings()

    @Volatile
    private var captureSampleRequested = false

    private var monitorJob: Job? = null
    private var playbackJob: Job? = null
    private var recordedSamplePcm: ShortArray? = null

    fun updateSettings(settings: RecorderSettings) {
        latestSettings = settings
    }

    fun startMonitoring(recordFiveSecondSample: Boolean = false) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            _state.value = _state.value.copy(
                phase = MicTestPhase.IDLE,
                errorMessage = "Microphone permission is required for live monitoring and voice test."
            )
            return
        }

        stopAll()
        captureSampleRequested = recordFiveSecondSample

        monitorJob = scope.launch {
            val sampleRate = 48000
            val minBufBytes = AudioRecord.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(3840)

            var audioRecord: AudioRecord? = null
            var ns: NoiseSuppressor? = null
            var aec: AcousticEchoCanceler? = null
            var agc: AutomaticGainControl? = null

            try {
                audioRecord = AudioRecord(
                    latestSettings.micSource.androidSource,
                    sampleRate,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    minBufBytes * 2
                )
                if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
                    _state.value = _state.value.copy(
                        phase = MicTestPhase.IDLE,
                        errorMessage = "Could not initialize microphone hardware with selected audio source."
                    )
                    return@launch
                }

                val sessionId = audioRecord.audioSessionId
                if (NoiseSuppressor.isAvailable()) {
                    ns = runCatching { NoiseSuppressor.create(sessionId) }.getOrNull()
                    ns?.enabled = latestSettings.noiseCancellation.useAndroidNs
                }
                if (AcousticEchoCanceler.isAvailable()) {
                    aec = runCatching { AcousticEchoCanceler.create(sessionId) }.getOrNull()
                    aec?.enabled = latestSettings.echoReduction
                }
                if (AutomaticGainControl.isAvailable()) {
                    agc = runCatching { AutomaticGainControl.create(sessionId) }.getOrNull()
                    agc?.enabled = latestSettings.autoGainControl
                }

                audioRecord.startRecording()

                val dsp = AudioDspProcessor(sampleRate)
                val frameSamples = 960 // 20ms @ 48kHz
                val buffer = ShortArray(frameSamples)
                val targetSampleCount = sampleRate * 5 // 5 seconds
                val capturedShorts = if (captureSampleRequested) ShortArray(targetSampleCount) else null
                var capturedCursor = 0

                _state.value = _state.value.copy(
                    phase = if (captureSampleRequested) MicTestPhase.RECORDING_SAMPLE else MicTestPhase.LIVE_MONITORING,
                    sampleProgress = 0f,
                    hardwareNsAttached = ns != null,
                    hardwareAecAttached = aec != null,
                    hardwareAgcAttached = agc != null,
                    errorMessage = null
                )

                var lastNsFlag = latestSettings.noiseCancellation.useAndroidNs
                var lastAecFlag = latestSettings.echoReduction
                var lastAgcFlag = latestSettings.autoGainControl

                while (isActive) {
                    val read = audioRecord.read(buffer, 0, frameSamples)
                    if (read <= 0) continue

                    val s = latestSettings
                    if (s.noiseCancellation.useAndroidNs != lastNsFlag) {
                        lastNsFlag = s.noiseCancellation.useAndroidNs
                        runCatching { ns?.enabled = lastNsFlag }
                    }
                    if (s.echoReduction != lastAecFlag) {
                        lastAecFlag = s.echoReduction
                        runCatching { aec?.enabled = lastAecFlag }
                    }
                    if (s.autoGainControl != lastAgcFlag) {
                        lastAgcFlag = s.autoGainControl
                        runCatching { agc?.enabled = lastAgcFlag }
                    }

                    val meter = dsp.processMicBufferInPlace(
                        samples = buffer,
                        sampleCount = read,
                        micGainDb = s.micGainDb,
                        noiseCancellation = s.noiseCancellation,
                        windReductionEnabled = s.windReductionEnabled,
                        windReductionIntensity = s.windReductionIntensity,
                        voiceEnhancementEnabled = s.voiceEnhancementEnabled,
                        autoGainControl = s.autoGainControl,
                        clippingProtection = s.clippingProtection
                    )

                    // Apply user mic volume slider + mute for test preview accuracy
                    val volumeFactor = if (s.micMuted || !s.micEnabled) 0f else s.micVolume
                    if (volumeFactor != 1.0f) {
                        for (i in 0 until read) {
                            buffer[i] = (buffer[i] * volumeFactor).toInt().coerceIn(-32768, 32767).toShort()
                        }
                    }

                    if (capturedShorts != null) {
                        val copyLen = minOf(read, targetSampleCount - capturedCursor)
                        if (copyLen > 0) {
                            System.arraycopy(buffer, 0, capturedShorts, capturedCursor, copyLen)
                            capturedCursor += copyLen
                        }
                        val progress = capturedCursor.toFloat() / targetSampleCount.toFloat()
                        _state.value = _state.value.copy(
                            inputLevel = meter.inputLevelNormalized,
                            outputLevel = if (volumeFactor == 0f) 0f else meter.outputLevelNormalized,
                            outputDb = if (volumeFactor == 0f) -96f else meter.outputDb,
                            noiseFloorDb = meter.noiseFloorDb,
                            gainReductionDb = meter.gainReductionDb,
                            clippingProtected = meter.clippingProtected,
                            sampleProgress = progress
                        )
                        if (capturedCursor >= targetSampleCount) {
                            recordedSamplePcm = capturedShorts
                            break
                        }
                    } else {
                        _state.value = _state.value.copy(
                            inputLevel = meter.inputLevelNormalized,
                            outputLevel = if (volumeFactor == 0f) 0f else meter.outputLevelNormalized,
                            outputDb = if (volumeFactor == 0f) -96f else meter.outputDb,
                            noiseFloorDb = meter.noiseFloorDb,
                            gainReductionDb = meter.gainReductionDb,
                            clippingProtected = meter.clippingProtected
                        )
                    }
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    phase = MicTestPhase.IDLE,
                    errorMessage = e.localizedMessage ?: "Microphone test failed."
                )
            } finally {
                runCatching { audioRecord?.stop() }
                runCatching { audioRecord?.release() }
                runCatching { ns?.release() }
                runCatching { aec?.release() }
                runCatching { agc?.release() }
            }

            if (captureSampleRequested && recordedSamplePcm != null) {
                _state.value = _state.value.copy(
                    hasRecordedSample = true,
                    sampleProgress = 1f
                )
                playRecordedSampleInternal()
            } else if (_state.value.phase != MicTestPhase.PLAYING_SAMPLE) {
                _state.value = _state.value.copy(
                    phase = MicTestPhase.IDLE,
                    inputLevel = 0f,
                    outputLevel = 0f
                )
            }
        }
    }

    fun playRecordedSample() {
        val sample = recordedSamplePcm ?: return
        stopAll()
        playbackJob = scope.launch {
            playRecordedSampleInternal(sample)
        }
    }

    private fun playRecordedSampleInternal(overrideSample: ShortArray? = null) {
        val pcm = overrideSample ?: recordedSamplePcm ?: return
        val sampleRate = 48000
        val minBuf = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(3840)

        var track: AudioTrack? = null
        try {
            track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(minBuf * 2)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            track.play()
            _state.value = _state.value.copy(
                phase = MicTestPhase.PLAYING_SAMPLE,
                sampleProgress = 0f,
                hasRecordedSample = true
            )

            val chunkSize = 960
            var offset = 0
            while (offset < pcm.size && !Thread.currentThread().isInterrupted) {
                val len = minOf(chunkSize, pcm.size - offset)
                val written = track.write(pcm, offset, len)
                if (written <= 0) break
                val level = AudioDspProcessor.computeBufferRmsNormalized(
                    pcm.copyOfRange(offset, offset + len),
                    len
                )
                offset += written
                _state.value = _state.value.copy(
                    outputLevel = level,
                    sampleProgress = offset.toFloat() / pcm.size.toFloat()
                )
            }
        } catch (e: Exception) {
            _state.value = _state.value.copy(errorMessage = e.localizedMessage)
        } finally {
            runCatching { track?.stop() }
            runCatching { track?.release() }
            _state.value = _state.value.copy(
                phase = MicTestPhase.IDLE,
                inputLevel = 0f,
                outputLevel = 0f,
                sampleProgress = 0f
            )
        }
    }

    fun stopAll() {
        monitorJob?.cancel()
        monitorJob = null
        playbackJob?.cancel()
        playbackJob = null
        _state.value = _state.value.copy(
            phase = MicTestPhase.IDLE,
            inputLevel = 0f,
            outputLevel = 0f,
            sampleProgress = 0f
        )
    }
}
