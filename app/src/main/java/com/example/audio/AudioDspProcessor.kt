package com.example.audio

import com.example.data.NoiseCancellationLevel
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
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
 * Studio-grade real-time Kotlin DSP vocal chain running strictly on the user's Microphone PCM stream.
 * Never processes Game & Teammates audio.
 *
 * Engineered to eliminate background static/crackling ("kirkarahat") and robotic/weird voice artifacts:
 * 1. DC-offset blocker + 2nd-order Butterworth High-Pass Filter (75 Hz standard / 100-210 Hz Wind mode)
 * 2. Smooth per-sample time-constant Noise Gate (8 ms attack, 150 ms hold, 220 ms release) + dynamic
 *    high-frequency De-Hiss low-pass during silence so mic hiss vanishes without clicks or choppy voice
 * 3. Studio Voice Enhancement: subtle 180 Hz warmth shelf + 3.0 kHz clarity peak + gentle 2.5:1 vocal compressor
 * 4. Transparent soft-knee peak limiter at -1.0 dBFS preventing harsh digital clipping
 */
class AudioDspProcessor(
    private val sampleRate: Int = 48000
) {
    // 1-pole DC blocker state
    private var dcPrevIn = 0f
    private var dcPrevOut = 0f

    // 2nd-order Butterworth HPF (sub-bass rumble & Wind Noise Reduction)
    private var hpfX1 = 0f; private var hpfX2 = 0f
    private var hpfY1 = 0f; private var hpfY2 = 0f
    private var hpfB0 = 1f; private var hpfB1 = 0f; private var hpfB2 = 0f
    private var hpfA1 = 0f; private var hpfA2 = 0f
    private var lastHpfCutoff = -1f

    // Studio Warmth Low-Shelf EQ (~180 Hz, +1.5 dB)
    private var warmX1 = 0f; private var warmX2 = 0f
    private var warmY1 = 0f; private var warmY2 = 0f
    private var warmB0 = 1f; private var warmB1 = 0f; private var warmB2 = 0f
    private var warmA1 = 0f; private var warmA2 = 0f

    // Studio Vocal Presence Peaking EQ (~3.0 kHz, +2.2 dB, Q = 0.9)
    private var eqX1 = 0f; private var eqX2 = 0f
    private var eqY1 = 0f; private var eqY2 = 0f
    private var eqB0 = 1f; private var eqB1 = 0f; private var eqB2 = 0f
    private var eqA1 = 0f; private var eqA2 = 0f

    // Smooth 1-pole dynamic De-Hiss LPF (attenuates mic static/hiss between words)
    private var deHissState = 0f

    // Low-frequency gust detector for Wind mode
    private var lowBandState = 0f
    private var lowBandEnv = 0f

    // Adaptive noise floor & click-free per-sample envelope gate
    private var estimatedNoiseFloorLinear = 0.0025f // ~ -52 dBFS initial
    private var speechEnv = 0f
    private var gateGain = 1.0f
    private var gateHoldSamplesRemaining = 0

    // Musical 2.5:1 vocal compressor envelope
    private var compEnv = 0f
    private var compGain = 1.0f

    // Exact per-sample exponential smoothing coefficients
    private val envAttackCoeff = timeConstantCoeff(4.0)
    private val envReleaseCoeff = timeConstantCoeff(90.0)
    private val gateAttackCoeff = timeConstantCoeff(8.0)     // 8 ms click-free open
    private val gateReleaseCoeff = timeConstantCoeff(220.0)  // 220 ms silky close
    private val gateHoldSamples = (sampleRate * 0.150).toInt() // 150 ms hold between syllables
    private val compAttackCoeff = timeConstantCoeff(12.0)
    private val compReleaseCoeff = timeConstantCoeff(140.0)

    init {
        configureHighPass(cutoffHz = 75f)
        configureWarmthLowShelf(freqHz = 180f, gainDb = 1.5f)
        configurePresenceEq(centerFreqHz = 3000f, q = 0.90f, gainDb = 2.2f)
    }

    private fun timeConstantCoeff(ms: Double): Float {
        val samples = (ms * 0.001 * sampleRate).coerceAtLeast(1.0)
        return (1.0 - exp(-1.0 / samples)).toFloat()
    }

    fun reset() {
        dcPrevIn = 0f; dcPrevOut = 0f
        hpfX1 = 0f; hpfX2 = 0f; hpfY1 = 0f; hpfY2 = 0f
        warmX1 = 0f; warmX2 = 0f; warmY1 = 0f; warmY2 = 0f
        eqX1 = 0f; eqX2 = 0f; eqY1 = 0f; eqY2 = 0f
        deHissState = 0f
        lowBandState = 0f; lowBandEnv = 0f
        estimatedNoiseFloorLinear = 0.0025f
        speechEnv = 0f
        gateGain = 1.0f
        gateHoldSamplesRemaining = 0
        compEnv = 0f
        compGain = 1.0f
    }

    /**
     * Processes a mono PCM16 buffer in-place with zero-click studio DSP and returns live telemetry.
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

        // 1. High-Pass Filter cutoff: 75 Hz clean studio rolloff, or 100-210 Hz when Wind Reduction is ON
        val targetCutoff = if (windReductionEnabled) {
            100f + (windReductionIntensity.coerceIn(0f, 1f) * 110f)
        } else {
            75f
        }
        if (abs(targetCutoff - lastHpfCutoff) > 2f) {
            configureHighPass(targetCutoff)
        }

        // Measure pre-DSP RMS
        var inSumSq = 0.0
        for (i in 0 until sampleCount) {
            val f = samples[i] / 32768f
            inSumSq += f * f
        }
        val inputRms = sqrt(inSumSq / sampleCount).toFloat()
        val inputDb = linearToDb(inputRms)

        // Stage A: DC Blocker + Butterworth HPF + Wind Gust Tracker
        val work = FloatArray(sampleCount)
        val lpAlpha = 0.022f
        var lowEnergySum = 0.0
        var postHpfSumSq = 0.0

        for (i in 0 until sampleCount) {
            val raw = samples[i] / 32768f
            // DC blocker removes sub-5Hz microphone bias pops
            val dcBlocked = raw - dcPrevIn + 0.996f * dcPrevOut
            dcPrevIn = raw
            dcPrevOut = dcBlocked

            // Track low-frequency wind gust energy
            lowBandState += lpAlpha * (dcBlocked - lowBandState)
            lowEnergySum += lowBandState * lowBandState

            // 2nd-order Butterworth HPF
            val y = hpfB0 * dcBlocked + hpfB1 * hpfX1 + hpfB2 * hpfX2 - hpfA1 * hpfY1 - hpfA2 * hpfY2
            hpfX2 = hpfX1
            hpfX1 = dcBlocked
            hpfY2 = hpfY1
            hpfY1 = y
            work[i] = y
            postHpfSumSq += y * y
        }

        // Gentle wind-gust attenuation only when sub-200Hz rumble dominates spectrum
        var windAttenuation = 1.0f
        if (windReductionEnabled) {
            val lowRms = sqrt(lowEnergySum / sampleCount).toFloat()
            lowBandEnv = 0.88f * lowBandEnv + 0.12f * lowRms
            val ratio = if (inputRms > 1e-4f) lowBandEnv / inputRms else 0f
            if (ratio > 0.62f && lowBandEnv > 0.018f) {
                val excess = ((ratio - 0.62f) / 0.38f).coerceIn(0f, 1f)
                windAttenuation = 1f - (excess * 0.45f * windReductionIntensity.coerceIn(0f, 1f))
            }
        }

        val postHpfRms = sqrt(postHpfSumSq / sampleCount).toFloat() * windAttenuation

        // Stage B: Adaptive Noise Floor Estimation (tracks steady fan/AC/mic hiss cleanly)
        if (postHpfRms < estimatedNoiseFloorLinear * 1.45f) {
            estimatedNoiseFloorLinear = 0.95f * estimatedNoiseFloorLinear + 0.05f * postHpfRms
        } else {
            estimatedNoiseFloorLinear = 0.9985f * estimatedNoiseFloorLinear + 0.0015f * postHpfRms
        }
        estimatedNoiseFloorLinear = estimatedNoiseFloorLinear.coerceIn(0.0004f, 0.045f)
        val noiseFloorDb = linearToDb(estimatedNoiseFloorLinear)

        // Precompute Noise Gate thresholds
        val ncActive = noiseCancellation != NoiseCancellationLevel.OFF
        val openThresholdLinear = if (ncActive) {
            val adaptiveDb = max(noiseCancellation.gateThresholdDb, noiseFloorDb + 6.5f)
            dbToLinear(adaptiveDb)
        } else 0f
        val closeThresholdLinear = openThresholdLinear * 0.62f // Hysteresis prevents chattering!
        val floorGain = if (ncActive) dbToLinear(noiseCancellation.floorAttenuationDb) else 1.0f

        val userGainLinear = dbToLinear(micGainDb)
        // Soft vocal compressor parameters (studio smoothness without pumping hiss)
        val useCompressor = voiceEnhancementEnabled || autoGainControl
        val compThreshold = 0.126f // -18 dBFS
        val compRatio = 2.4f
        val compMakeup = if (voiceEnhancementEnabled) 1.22f else 1.08f // subtle +1.7 dB body

        var outSumSq = 0.0
        var clipped = false
        var avgGateGain = 0.0

        // Stage C & D: Per-sample click-free Gate + Dynamic De-Hiss + Studio EQ + Vocal Compressor + Limiter
        for (i in 0 until sampleCount) {
            var s = work[i] * windAttenuation
            val absS = abs(s)

            // 1. Smooth speech envelope follower
            val envCoeff = if (absS > speechEnv) envAttackCoeff else envReleaseCoeff
            speechEnv += envCoeff * (absS - speechEnv)

            // 2. Click-free Noise Gate with Hysteresis & 150ms Hold
            val desiredGate = if (!ncActive) {
                1.0f
            } else {
                when {
                    speechEnv >= openThresholdLinear -> {
                        gateHoldSamplesRemaining = gateHoldSamples
                        1.0f
                    }
                    gateHoldSamplesRemaining > 0 -> {
                        gateHoldSamplesRemaining--
                        1.0f
                    }
                    speechEnv <= closeThresholdLinear -> floorGain
                    else -> {
                        val t = ((speechEnv - closeThresholdLinear) / (openThresholdLinear - closeThresholdLinear).coerceAtLeast(1e-5f))
                            .coerceIn(0f, 1f)
                        val smoothT = t * t * (3f - 2f * t)
                        floorGain + (1.0f - floorGain) * smoothT
                    }
                }
            }
            val gCoeff = if (desiredGate > gateGain) gateAttackCoeff else gateReleaseCoeff
            gateGain += gCoeff * (desiredGate - gateGain)
            avgGateGain += gateGain

            // 3. Dynamic High-Frequency De-Hiss: removes static "kirkarahat" smoothly during quiet gaps
            //    while keeping full 20 kHz studio sparkle when speaking (gateGain ~= 1.0)
            if (ncActive) {
                // Blend cutoff from ~3.8 kHz (quiet) up to fully open (speaking)
                val lpfAlpha = (0.36f + 0.64f * gateGain).coerceIn(0.36f, 1.0f)
                deHissState += lpfAlpha * (s - deHissState)
                s = deHissState * gateGain
            } else {
                deHissState = s
            }

            // 4. Studio Voice Enhancement EQ (Warmth + Crisp Presence)
            if (voiceEnhancementEnabled) {
                val warmOut = warmB0 * s + warmB1 * warmX1 + warmB2 * warmX2 - warmA1 * warmY1 - warmA2 * warmY2
                warmX2 = warmX1; warmX1 = s; warmY2 = warmY1; warmY1 = warmOut

                val eqOut = eqB0 * warmOut + eqB1 * eqX1 + eqB2 * eqX2 - eqA1 * eqY1 - eqA2 * eqY2
                eqX2 = eqX1; eqX1 = warmOut; eqY2 = eqY1; eqY1 = eqOut
                s = eqOut
            }

            // 5. Gentle Broadcast 2.4:1 Vocal Compressor (levels speech smoothly without boosting background hiss)
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
                // Only apply makeup gain smoothly while speech is present so idle noise stays silent
                val dynamicMakeup = 1.0f + (compMakeup - 1.0f) * gateGain
                s *= (compGain * dynamicMakeup)
            }

            // 6. User Mic Gain + Transparent Soft-Knee Peak Limiter (-1.0 dBFS ceiling)
            s *= userGainLinear

            if (clippingProtection) {
                val peak = abs(s)
                if (peak > 0.88f) {
                    clipped = true
                    val limited = 0.88f + 0.10f * tanh(((peak - 0.88f) / 0.10f).toDouble()).toFloat()
                    s = if (s < 0f) -limited else limited
                }
            }

            val clamped = s.coerceIn(-1.0f, 1.0f)
            outSumSq += clamped * clamped
            samples[i] = (clamped * 32767f).toInt().coerceIn(-32768, 32767).toShort()
        }

        val outputRms = sqrt(outSumSq / sampleCount).toFloat()
        val outputDb = linearToDb(outputRms)
        val meanGate = (avgGateGain / sampleCount).toFloat()
        val gainReductionDb = linearToDb(meanGate * windAttenuation).coerceAtMost(0f)

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

    private fun configureHighPass(cutoffHz: Float) {
        lastHpfCutoff = cutoffHz
        val omega = 2.0 * PI * cutoffHz / sampleRate
        val cosW = cos(omega)
        val sinW = sin(omega)
        val alpha = sinW / sqrt(2.0) // Butterworth Q = 0.7071
        val a0 = 1.0 + alpha
        hpfB0 = (((1.0 + cosW) / 2.0) / a0).toFloat()
        hpfB1 = ((-(1.0 + cosW)) / a0).toFloat()
        hpfB2 = (((1.0 + cosW) / 2.0) / a0).toFloat()
        hpfA1 = ((-2.0 * cosW) / a0).toFloat()
        hpfA2 = ((1.0 - alpha) / a0).toFloat()
    }

    private fun configureWarmthLowShelf(freqHz: Float, gainDb: Float) {
        val a = 10.0.pow(gainDb / 40.0)
        val omega = 2.0 * PI * freqHz / sampleRate
        val sinW = sin(omega)
        val cosW = cos(omega)
        val sqrtA = sqrt(a)
        val alpha = sinW / sqrt(2.0)

        val b0 = a * ((a + 1.0) - (a - 1.0) * cosW + 2.0 * sqrtA * alpha)
        val b1 = 2.0 * a * ((a - 1.0) - (a + 1.0) * cosW)
        val b2 = a * ((a + 1.0) - (a - 1.0) * cosW - 2.0 * sqrtA * alpha)
        val a0 = (a + 1.0) + (a - 1.0) * cosW + 2.0 * sqrtA * alpha
        val a1 = -2.0 * ((a - 1.0) + (a + 1.0) * cosW)
        val a2 = (a + 1.0) + (a - 1.0) * cosW - 2.0 * sqrtA * alpha

        warmB0 = (b0 / a0).toFloat()
        warmB1 = (b1 / a0).toFloat()
        warmB2 = (b2 / a0).toFloat()
        warmA1 = (a1 / a0).toFloat()
        warmA2 = (a2 / a0).toFloat()
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
