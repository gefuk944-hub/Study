package com.example.audio

import com.example.data.NoiseCancellationLevel
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tanh

/**
 * Snapshot of live microphone DSP telemetry for UI meters.
 */
data class DspMeterResult(
    val inputLevelNormalized: Float,   // 0f..1f
    val outputLevelNormalized: Float,  // 0f..1f
    val inputDb: Float,                // -96..0 dBFS
    val outputDb: Float,               // -96..0 dBFS
    val noiseFloorDb: Float,           // estimated ambient floor dBFS
    val gainReductionDb: Float,        // active noise/wind attenuation
    val clippingProtected: Boolean
)

/**
 * Pure Kotlin real-time DSP pipeline running strictly on the user's Microphone PCM stream.
 * Never touches Game Audio or Teammate Voice streams.
 *
 * Processing order:
 * 1. DC-offset removal + Dedicated Wind Noise Reduction (2nd-order Butterworth HPF 80-240 Hz + gust damper)
 * 2. Background Noise Cancellation (Adaptive noise-floor tracker + smooth soft-knee gate)
 * 3. On-Device DSP Voice Enhancement (3.2 kHz vocal presence peaking EQ + warm body shelf + RMS auto-leveler)
 * 4. User Mic Gain + Soft-knee Clipping Limiter (tanh saturation above -1.5 dBFS)
 */
class AudioDspProcessor(
    private val sampleRate: Int = 48000
) {
    // 2nd-order Biquad state for Wind High-Pass Filter
    private var windX1 = 0f
    private var windX2 = 0f
    private var windY1 = 0f
    private var windY2 = 0f
    private var windB0 = 1f
    private var windB1 = 0f
    private var windB2 = 0f
    private var windA1 = 0f
    private var windA2 = 0f
    private var lastWindCutoff = -1f

    // 2nd-order Biquad state for Vocal Presence Peaking EQ (~3.2 kHz, +3.2 dB)
    private var eqX1 = 0f
    private var eqX2 = 0f
    private var eqY1 = 0f
    private var eqY2 = 0f
    private var eqB0 = 1f
    private var eqB1 = 0f
    private var eqB2 = 0f
    private var eqA1 = 0f
    private var eqA2 = 0f

    // Low-frequency rumble envelope tracker for wind gusts
    private var lowBandState = 0f
    private var lowBandEnv = 0f

    // Adaptive noise floor & smooth gate envelope
    private var estimatedNoiseFloorLinear = 0.004f // ~ -48 dBFS initial
    private var gateGain = 1.0f
    private var agcGain = 1.0f

    init {
        configurePresenceEq(centerFreqHz = 3200f, q = 1.15f, gainDb = 3.2f)
        configureWindHighPass(cutoffHz = 90f)
    }

    fun reset() {
        windX1 = 0f; windX2 = 0f; windY1 = 0f; windY2 = 0f
        eqX1 = 0f; eqX2 = 0f; eqY1 = 0f; eqY2 = 0f
        lowBandState = 0f; lowBandEnv = 0f
        estimatedNoiseFloorLinear = 0.004f
        gateGain = 1.0f
        agcGain = 1.0f
    }

    /**
     * Processes a mono PCM16 buffer in-place and returns live metering metrics.
     */
    fun processMicBufferInPlace(
        samples: ShortArray,
        sampleCount: Int,
        micGainDb: Float,
        noiseCancellation: NoiseCancellationLevel,
        windReductionEnabled: Boolean,
        windReductionIntensity: Float,
        voiceEnhancementEnabled: Boolean,
        autoGainControl: Boolean,
        clippingProtection: Boolean
    ): DspMeterResult {
        if (sampleCount <= 0) {
            return DspMeterResult(0f, 0f, -96f, -96f, -96f, 0f, false)
        }

        // 1. Update Wind HPF coefficients only when intensity changes
        val targetCutoff = if (windReductionEnabled) {
            85f + (windReductionIntensity.coerceIn(0f, 1f) * 155f) // 85 Hz .. 240 Hz
        } else {
            55f // gentle subsonic DC/handling rumble guard
        }
        if (abs(targetCutoff - lastWindCutoff) > 2f) {
            configureWindHighPass(targetCutoff)
        }

        // Measure pre-DSP input RMS
        var inSumSq = 0.0
        for (i in 0 until sampleCount) {
            val f = samples[i] / 32768f
            inSumSq += f * f
        }
        val inputRms = sqrt(inSumSq / sampleCount).toFloat()
        val inputDb = linearToDb(inputRms)

        // Stage A: High-pass Wind Filter + Wind Gust Detector
        val work = FloatArray(sampleCount)
        val lpAlpha = 0.025f // ~190 Hz low-pass follower for wind thump detection
        var lowEnergySum = 0.0
        for (i in 0 until sampleCount) {
            val raw = samples[i] / 32768f
            // Track low-frequency gust energy
            lowBandState += lpAlpha * (raw - lowBandState)
            lowEnergySum += lowBandState * lowBandState

            // 2nd-order Butterworth HPF
            val y = windB0 * raw + windB1 * windX1 + windB2 * windX2 - windA1 * windY1 - windA2 * windY2
            windX2 = windX1
            windX1 = raw
            windY2 = windY1
            windY1 = y
            work[i] = y
        }

        // Wind gust transient attenuation if strong sub-200Hz pressure bursts are detected
        var windAttenuation = 1.0f
        if (windReductionEnabled) {
            val lowRms = sqrt(lowEnergySum / sampleCount).toFloat()
            lowBandEnv = 0.85f * lowBandEnv + 0.15f * lowRms
            val ratio = if (inputRms > 1e-4f) lowBandEnv / inputRms else 0f
            if (ratio > 0.58f && lowBandEnv > 0.015f) {
                val excess = ((ratio - 0.58f) / 0.42f).coerceIn(0f, 1f)
                windAttenuation = 1f - (excess * 0.55f * windReductionIntensity.coerceIn(0f, 1f))
            }
        }

        // Stage B: Background Noise Cancellation (Adaptive Noise Floor + Smooth Soft-Knee Gate)
        var filteredSumSq = 0.0
        for (i in 0 until sampleCount) {
            val s = work[i] * windAttenuation
            work[i] = s
            filteredSumSq += s * s
        }
        val postWindRms = sqrt(filteredSumSq / sampleCount).toFloat()

        // Adaptive noise floor slowly tracks quiet segments without rising on speech bursts
        if (postWindRms < estimatedNoiseFloorLinear * 1.45f) {
            estimatedNoiseFloorLinear = 0.94f * estimatedNoiseFloorLinear + 0.06f * postWindRms
        } else {
            // Very slow upward drift so steady fan/AC startup is learned over ~3 seconds
            estimatedNoiseFloorLinear = 0.997f * estimatedNoiseFloorLinear + 0.003f * postWindRms
        }
        estimatedNoiseFloorLinear = estimatedNoiseFloorLinear.coerceIn(0.0005f, 0.08f)
        val noiseFloorDb = linearToDb(estimatedNoiseFloorLinear)

        val targetGateGain = if (noiseCancellation == NoiseCancellationLevel.OFF) {
            1.0f
        } else {
            val postWindDb = linearToDb(postWindRms)
            // Dynamic threshold tracks slightly above ambient noise floor, bounded by preset threshold
            val adaptiveThresholdDb = max(noiseCancellation.gateThresholdDb, noiseFloorDb + 5.5f)
            val kneeDb = 9.0f
            val floorLinear = dbToLinear(noiseCancellation.floorAttenuationDb)

            when {
                postWindDb >= adaptiveThresholdDb -> 1.0f
                postWindDb <= adaptiveThresholdDb - kneeDb -> floorLinear
                else -> {
                    val t = (postWindDb - (adaptiveThresholdDb - kneeDb)) / kneeDb
                    val smoothT = t * t * (3f - 2f * t) // smoothstep curve avoids robotic gating
                    floorLinear + (1.0f - floorLinear) * smoothT
                }
            }
        }

        // Fast attack (preserve consonant onset), gentle release (natural vocal tail)
        val attackCoeff = 0.42f
        val releaseCoeff = 0.12f
        val userGainLinear = dbToLinear(micGainDb)

        // Stage C: RMS Auto-Leveler (AGC) when voice enhancement or AGC is active
        if (autoGainControl || voiceEnhancementEnabled) {
            val targetSpeechRms = 0.16f // ~ -16 dBFS clear gaming voice target
            if (targetGateGain > 0.55f && postWindRms > 0.012f) {
                val desiredAgc = (targetSpeechRms / postWindRms).coerceIn(0.65f, 2.4f)
                val coeff = if (desiredAgc < agcGain) 0.18f else 0.04f
                agcGain += coeff * (desiredAgc - agcGain)
            } else {
                // Relax back toward unity during silence so background noise is not boosted
                agcGain += 0.03f * (1.0f - agcGain)
            }
        } else {
            agcGain = 1.0f
        }

        // Stage D: Apply Gate + Presence EQ + Gain + Soft-Knee Clipping Limiter
        var outSumSq = 0.0
        var clipped = false
        for (i in 0 until sampleCount) {
            val coeff = if (targetGateGain > gateGain) attackCoeff else releaseCoeff
            gateGain += coeff * (targetGateGain - gateGain)

            var sample = work[i] * gateGain

            if (voiceEnhancementEnabled) {
                // 3.2 kHz vocal presence peaking biquad EQ
                val eqOut = eqB0 * sample + eqB1 * eqX1 + eqB2 * eqX2 - eqA1 * eqY1 - eqA2 * eqY2
                eqX2 = eqX1
                eqX1 = sample
                eqY2 = eqY1
                eqY1 = eqOut
                sample = eqOut
            }

            sample *= (userGainLinear * agcGain)

            if (clippingProtection) {
                val absS = abs(sample)
                if (absS > 0.84f) {
                    clipped = true
                    // Smooth tanh soft saturation ceiling at ~ -0.3 dBFS
                    sample = (0.84f + 0.14f * tanh(((absS - 0.84f) / 0.14f).toDouble()).toFloat()) *
                        if (sample < 0f) -1f else 1f
                }
            }

            val clamped = sample.coerceIn(-1.0f, 1.0f)
            outSumSq += clamped * clamped
            samples[i] = (clamped * 32767f).toInt().coerceIn(-32768, 32767).toShort()
        }

        val outputRms = sqrt(outSumSq / sampleCount).toFloat()
        val outputDb = linearToDb(outputRms)
        val gainReductionDb = linearToDb(gateGain * windAttenuation).coerceAtMost(0f)

        return DspMeterResult(
            inputLevelNormalized = dbToMeterNormalized(inputDb),
            outputLevelNormalized = dbToMeterNormalized(outputDb),
            inputDb = inputDb,
            outputDb = outputDb,
            noiseFloorDb = noiseFloorDb,
            gainReductionDb = gainReductionDb,
            clippingProtected = clipped
        )
    }

    private fun configureWindHighPass(cutoffHz: Float) {
        lastWindCutoff = cutoffHz
        val omega = 2.0 * PI * cutoffHz / sampleRate
        val cosW = cos(omega)
        val sinW = sin(omega)
        val alpha = sinW / sqrt(2.0) // Butterworth Q = 0.7071
        val a0 = 1.0 + alpha
        windB0 = (((1.0 + cosW) / 2.0) / a0).toFloat()
        windB1 = ((-(1.0 + cosW)) / a0).toFloat()
        windB2 = (((1.0 + cosW) / 2.0) / a0).toFloat()
        windA1 = ((-2.0 * cosW) / a0).toFloat()
        windA2 = ((1.0 - alpha) / a0).toFloat()
    }

    private fun configurePresenceEq(centerFreqHz: Float, q: Float, gainDb: Float) {
        val a = 10.0.pow(gainDb / 40.0)
        val omega = 2.0 * PI * centerFreqHz / sampleRate
        val sinW = sin(omega)
        val cosW = cos(omega)
        val alpha = sinW / (2.0 * q)

        val b0 = 1.0 + alpha * a
        val b1 = -2.0 * cosW
        val b2 = 1.0 - alpha * a
        val a0 = 1.0 + alpha / a
        val a1 = -2.0 * cosW
        val a2 = 1.0 - alpha / a

        eqB0 = (b0 / a0).toFloat()
        eqB1 = (b1 / a0).toFloat()
        eqB2 = (b2 / a0).toFloat()
        eqA1 = (a1 / a0).toFloat()
        eqA2 = (a2 / a0).toFloat()
    }

    companion object {
        fun computeBufferRmsNormalized(buffer: ShortArray, count: Int): Float {
            if (count <= 0) return 0f
            var sumSq = 0.0
            for (i in 0 until count) {
                val f = buffer[i] / 32768f
                sumSq += f * f
            }
            val rms = sqrt(sumSq / count).toFloat()
            return dbToMeterNormalized(linearToDb(rms))
        }

        fun linearToDb(linear: Float): Float {
            if (linear <= 1e-5f) return -96f
            return (20f * log10(linear)).coerceIn(-96f, 0f)
        }

        fun dbToLinear(db: Float): Float = 10f.pow(db / 20f)

        fun dbToMeterNormalized(db: Float): Float {
            // Map -60 dBFS .. -2 dBFS to 0..1 for responsive gaming VU meter
            return ((db + 60f) / 58f).coerceIn(0f, 1f)
        }
    }
}
