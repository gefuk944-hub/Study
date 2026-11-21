package com.example.audio

import com.example.data.NoiseCancellationLevel
import com.example.data.VoicePickupRange
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.log10
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
 * Studio-grade real-time Kotlin DSP vocal chain running strictly on the user's Microphone PCM stream.
 * Never processes Game & Teammates audio.
 *
 * Processing pipeline:
 * 1. DC Blocker + 2nd-order Butterworth High-Pass Filter (110 Hz crisp anti-boom / 130-230 Hz Wind mode)
 * 2. Real 512-point Overlap-Add FFT Spectral Voice Isolator + Proximity Speaking-Range Gate
 *    (strips fan/room/outside background noise even while speaking, and silences everything outside pickup range)
 * 3. Broadcast Studio Voice Clarity EQ:
 *    - Anti-Mud / Anti-Heavy dip (-2.2 dB @ 260 Hz) so voice NEVER sounds "bhari bhari" or boxy
 *    - Natural Vocal Presence (+2.0 dB @ 3.5 kHz) for crisp, clear, studio-mic articulation
 * 4. Gentle 2.2:1 Broadcast Vocal Leveler + transparent -1.0 dBFS Soft-Knee Peak Limiter
 */
class AudioDspProcessor(
    private val sampleRate: Int = 48000
) {
    private val spectralIsolator = SpectralVoiceIsolator(sampleRate)

    // 1-pole DC blocker
    private var dcPrevIn = 0f
    private var dcPrevOut = 0f

    // 2nd-order Butterworth HPF (sub-bass rumble, chest boom & Wind Noise Reduction)
    private var hpfX1 = 0f; private var hpfX2 = 0f
    private var hpfY1 = 0f; private var hpfY2 = 0f
    private var hpfB0 = 1f; private var hpfB1 = 0f; private var hpfB2 = 0f
    private var hpfA1 = 0f; private var hpfA2 = 0f
    private var lastHpfCutoff = -1f

    // Anti-Mud / Anti-Heavy Peaking Cut (~260 Hz, -2.2 dB, Q = 1.0) — removes "bhari bhari" boominess!
    private var mudX1 = 0f; private var mudX2 = 0f
    private var mudY1 = 0f; private var mudY2 = 0f
    private var mudB0 = 1f; private var mudB1 = 0f; private var mudB2 = 0f
    private var mudA1 = 0f; private var mudA2 = 0f

    // Crisp Vocal Presence Peaking EQ (~3.5 kHz, +2.0 dB, Q = 0.85)
    private var eqX1 = 0f; private var eqX2 = 0f
    private var eqY1 = 0f; private var eqY2 = 0f
    private var eqB0 = 1f; private var eqB1 = 0f; private var eqB2 = 0f
    private var eqA1 = 0f; private var eqA2 = 0f

    // Wind gust transient detector
    private var lowBandState = 0f
    private var lowBandEnv = 0f

    // Gentle 2.2:1 studio vocal compressor
    private var compEnv = 0f
    private var compGain = 1.0f
    private val compAttackCoeff = timeConstantCoeff(12.0)
    private val compReleaseCoeff = timeConstantCoeff(150.0)

    init {
        configureHighPass(cutoffHz = 110f)
        configureMudCutEq(centerFreqHz = 260f, q = 1.0f, gainDb = -2.2f)
        configurePresenceEq(centerFreqHz = 3500f, q = 0.85f, gainDb = 2.0f)
    }

    private fun timeConstantCoeff(ms: Double): Float {
        val samples = (ms * 0.001 * sampleRate).coerceAtLeast(1.0)
        return (1.0 - exp(-1.0 / samples)).toFloat()
    }

    fun reset() {
        spectralIsolator.reset()
        dcPrevIn = 0f; dcPrevOut = 0f
        hpfX1 = 0f; hpfX2 = 0f; hpfY1 = 0f; hpfY2 = 0f
        mudX1 = 0f; mudX2 = 0f; mudY1 = 0f; mudY2 = 0f
        eqX1 = 0f; eqX2 = 0f; eqY1 = 0f; eqY2 = 0f
        lowBandState = 0f; lowBandEnv = 0f
        compEnv = 0f; compGain = 1.0f
    }

    fun processMicBufferInPlace(
        samples: ShortArray,
        sampleCount: Int,
        micGainDb: Float,
        noiseCancellation: NoiseCancellationLevel,
        windReductionEnabled: Boolean,
        windReductionIntensity: Float,
        voiceEnhancementEnabled: Boolean,
        autoGainControl: Boolean,
        clippingProtection: Boolean,
        pickupRange: VoicePickupRange = VoicePickupRange.CLOSE
    ): DspMeterResult {
        if (sampleCount <= 0) {
            return DspMeterResult(0f, 0f, -96f, -96f, -96f, 0f, false)
        }

        // 1. Clean 110 Hz anti-boom HPF (removes heavy chest rumble); sweeps 130-230 Hz for Wind mode
        val targetCutoff = if (windReductionEnabled) {
            130f + (windReductionIntensity.coerceIn(0f, 1f) * 100f)
        } else {
            110f
        }
        if (abs(targetCutoff - lastHpfCutoff) > 2f) {
            configureHighPass(targetCutoff)
        }

        var inSumSq = 0.0
        val work = FloatArray(sampleCount)
        val lpAlpha = 0.020f
        var lowEnergySum = 0.0

        // Stage A: DC Blocker + Butterworth High-Pass Filter
        for (i in 0 until sampleCount) {
            val raw = samples[i] / 32768f
            inSumSq += raw * raw

            val dcBlocked = raw - dcPrevIn + 0.996f * dcPrevOut
            dcPrevIn = raw
            dcPrevOut = dcBlocked

            lowBandState += lpAlpha * (dcBlocked - lowBandState)
            lowEnergySum += lowBandState * lowBandState

            val y = hpfB0 * dcBlocked + hpfB1 * hpfX1 + hpfB2 * hpfX2 - hpfA1 * hpfY1 - hpfA2 * hpfY2
            hpfX2 = hpfX1; hpfX1 = dcBlocked; hpfY2 = hpfY1; hpfY1 = y
            work[i] = y
        }

        val inputRms = sqrt(inSumSq / sampleCount).toFloat()
        val inputDb = linearToDb(inputRms)

        // Optional Wind Gust Damper
        if (windReductionEnabled) {
            val lowRms = sqrt(lowEnergySum / sampleCount).toFloat()
            lowBandEnv = 0.88f * lowBandEnv + 0.12f * lowRms
            val ratio = if (inputRms > 1e-4f) lowBandEnv / inputRms else 0f
            if (ratio > 0.62f && lowBandEnv > 0.018f) {
                val excess = ((ratio - 0.62f) / 0.38f).coerceIn(0f, 1f)
                val windAtten = 1f - (excess * 0.45f * windReductionIntensity.coerceIn(0f, 1f))
                for (i in 0 until sampleCount) work[i] *= windAtten
            }
        }

        // Stage B: Real 512-point FFT Spectral Denoiser + Proximity Voice Pickup Range Gate
        spectralIsolator.processBlockInPlace(
            samples = work,
            count = sampleCount,
            ncLevel = noiseCancellation,
            pickupRange = pickupRange
        )

        // Stage C: Studio Voice Clarity EQ (Anti-Heavy 260 Hz Cut + 3.5 kHz Clarity) + Gentle Leveler + Limiter
        val userGainLinear = dbToLinear(micGainDb)
        val useCompressor = voiceEnhancementEnabled || autoGainControl
        val compThreshold = 0.14f // ~ -17 dBFS
        val compRatio = 2.2f
        val compMakeup = if (voiceEnhancementEnabled) 1.15f else 1.0f

        var outSumSq = 0.0
        var clipped = false

        for (i in 0 until sampleCount) {
            var s = work[i]

            if (voiceEnhancementEnabled) {
                // Remove heavy/boomy 260 Hz proximity muddiness
                val unmud = mudB0 * s + mudB1 * mudX1 + mudB2 * mudX2 - mudA1 * mudY1 - mudA2 * mudY2
                mudX2 = mudX1; mudX1 = s; mudY2 = mudY1; mudY1 = unmud

                // Add crisp natural studio presence at 3.5 kHz
                val crisp = eqB0 * unmud + eqB1 * eqX1 + eqB2 * eqX2 - eqA1 * eqY1 - eqA2 * eqY2
                eqX2 = eqX1; eqX1 = unmud; eqY2 = eqY1; eqY1 = crisp
                s = crisp
            }

            if (useCompressor) {
                val cAbs = abs(s)
                val cCoeff = if (cAbs > compEnv) compAttackCoeff else compReleaseCoeff
                compEnv += cCoeff * (cAbs - compEnv)

                val targetComp = if (compEnv > compThreshold) {
                    val over = compEnv / compThreshold
                    (over.toDouble().pow((1.0 / compRatio) - 1.0)).toFloat()
                } else {
                    1.0f
                }
                compGain += 0.015f * (targetComp - compGain)
                s *= (compGain * compMakeup)
            }

            s *= userGainLinear

            if (clippingProtection) {
                val peak = abs(s)
                if (peak > 0.89f) {
                    clipped = true
                    val limited = 0.89f + 0.09f * tanh(((peak - 0.89f) / 0.09f).toDouble()).toFloat()
                    s = if (s < 0f) -limited else limited
                }
            }

            val clamped = s.coerceIn(-1.0f, 1.0f)
            outSumSq += clamped * clamped
            samples[i] = (clamped * 32767f).toInt().coerceIn(-32768, 32767).toShort()
        }

        val outputRms = sqrt(outSumSq / sampleCount).toFloat()
        val outputDb = linearToDb(outputRms)

        return DspMeterResult(
            inputLevelNormalized = dbToMeterNormalized(inputDb),
            outputLevelNormalized = dbToMeterNormalized(outputDb),
            inputDb = inputDb,
            outputDb = outputDb,
            noiseFloorDb = spectralIsolator.getEstimatedNoiseFloorDb(),
            gainReductionDb = spectralIsolator.getActiveReductionDb(),
            clippingProtected = clipped
        )
    }

    private fun configureHighPass(cutoffHz: Float) {
        lastHpfCutoff = cutoffHz
        val omega = 2.0 * PI * cutoffHz / sampleRate
        val cosW = cos(omega)
        val sinW = sin(omega)
        val alpha = sinW / sqrt(2.0)
        val a0 = 1.0 + alpha
        hpfB0 = (((1.0 + cosW) / 2.0) / a0).toFloat()
        hpfB1 = ((-(1.0 + cosW)) / a0).toFloat()
        hpfB2 = (((1.0 + cosW) / 2.0) / a0).toFloat()
        hpfA1 = ((-2.0 * cosW) / a0).toFloat()
        hpfA2 = ((1.0 - alpha) / a0).toFloat()
    }

    private fun configureMudCutEq(centerFreqHz: Float, q: Float, gainDb: Float) {
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

        mudB0 = (b0 / a0).toFloat()
        mudB1 = (b1 / a0).toFloat()
        mudB2 = (b2 / a0).toFloat()
        mudA1 = (a1 / a0).toFloat()
        mudA2 = (a2 / a0).toFloat()
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
            return ((db + 60f) / 58f).coerceIn(0f, 1f)
        }
    }
}
