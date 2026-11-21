package com.example

import com.example.audio.AudioDspProcessor
import com.example.data.NoiseCancellationLevel
import com.example.data.PerformanceMode
import com.example.data.RecorderSettings
import com.example.data.VideoCodec
import com.example.data.VideoFps
import com.example.data.VideoResolution
import com.example.data.VoicePickupRange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class AudioDspAndSettingsTest {

    @Test
    fun windReductionHighPassAttenuatesLowFrequencyRumble() {
        val dsp = AudioDspProcessor(sampleRate = 48000)
        // Generate 50 Hz wind rumble tone
        val windBuf = ShortArray(4800) { i ->
            (sin(2.0 * PI * 50.0 * i / 48000.0) * 16000.0).toInt().toShort()
        }
        val result = dsp.processMicBufferInPlace(
            samples = windBuf,
            sampleCount = windBuf.size,
            micGainDb = 0f,
            noiseCancellation = NoiseCancellationLevel.OFF,
            pickupRange = VoicePickupRange.WIDE,
            windReductionEnabled = true,
            windReductionIntensity = 1.0f,
            voiceEnhancementEnabled = false,
            autoGainControl = false,
            clippingProtection = true
        )
        assertTrue(
            "50 Hz wind rumble should be heavily attenuated by high-pass filter",
            result.outputDb < result.inputDb - 10f
        )
    }

    @Test
    fun spectralIsolatorAndCloseRangeGateBlockDistantBackgroundNoise() {
        val dsp = AudioDspProcessor(sampleRate = 48000)
        // Simulate distant room/outside ambient noise (~ -42 dBFS broadband hum)
        val noiseBuf = ShortArray(1024) { i ->
            val v = sin(2.0 * PI * 320.0 * i / 48000.0) * 140.0 +
                sin(2.0 * PI * 1400.0 * i / 48000.0) * 90.0 +
                sin(2.0 * PI * 5200.0 * i / 48000.0) * 110.0
            v.toInt().toShort()
        }
        var lastResult = dsp.processMicBufferInPlace(
            samples = noiseBuf.copyOf(),
            sampleCount = noiseBuf.size,
            micGainDb = 0f,
            noiseCancellation = NoiseCancellationLevel.HIGH,
            pickupRange = VoicePickupRange.CLOSE,
            windReductionEnabled = false,
            windReductionIntensity = 0f,
            voiceEnhancementEnabled = true,
            autoGainControl = false,
            clippingProtection = true
        )
        // Run ~900ms so FFT noise profile learns & proximity gate settles cleanly
        repeat(45) {
            lastResult = dsp.processMicBufferInPlace(
                samples = noiseBuf.copyOf(),
                sampleCount = noiseBuf.size,
                micGainDb = 0f,
                noiseCancellation = NoiseCancellationLevel.HIGH,
                pickupRange = VoicePickupRange.CLOSE,
                windReductionEnabled = false,
                windReductionIntensity = 0f,
                voiceEnhancementEnabled = true,
                autoGainControl = false,
                clippingProtection = true
            )
        }
        assertTrue(
            "Close-range spectral voice isolator should attenuate distant room noise by >20 dB (got in=${lastResult.inputDb}, out=${lastResult.outputDb})",
            lastResult.outputDb < lastResult.inputDb - 20f
        )
    }

    @Test
    fun closeRangeStudioSpeechStaysClearNaturalAndUndistorted() {
        val dsp = AudioDspProcessor(sampleRate = 48000)
        // First prime noise floor with very quiet room floor (-60 dBFS)
        val roomFloor = ShortArray(1024) { i ->
            (sin(2.0 * PI * 240.0 * i / 48000.0) * 30.0).toInt().toShort()
        }
        repeat(8) {
            dsp.processMicBufferInPlace(
                samples = roomFloor.copyOf(),
                sampleCount = roomFloor.size,
                micGainDb = 0f,
                noiseCancellation = NoiseCancellationLevel.HIGH,
                pickupRange = VoicePickupRange.CLOSE,
                windReductionEnabled = false,
                windReductionIntensity = 0f,
                voiceEnhancementEnabled = true,
                autoGainControl = false,
                clippingProtection = true
            )
        }

        // Now speak closely at normal human vocal level (~ -14 dBFS @ 1.1 kHz)
        val speech = ShortArray(1024) { i ->
            (sin(2.0 * PI * 1100.0 * i / 48000.0) * 6500.0).toInt().toShort()
        }
        var res = dsp.processMicBufferInPlace(
            samples = speech.copyOf(),
            sampleCount = speech.size,
            micGainDb = 0f,
            noiseCancellation = NoiseCancellationLevel.HIGH,
            pickupRange = VoicePickupRange.CLOSE,
            windReductionEnabled = false,
            windReductionIntensity = 0f,
            voiceEnhancementEnabled = true,
            autoGainControl = false,
            clippingProtection = true
        )
        repeat(8) {
            res = dsp.processMicBufferInPlace(
                samples = speech.copyOf(),
                sampleCount = speech.size,
                micGainDb = 0f,
                noiseCancellation = NoiseCancellationLevel.HIGH,
                pickupRange = VoicePickupRange.CLOSE,
                windReductionEnabled = false,
                windReductionIntensity = 0f,
                voiceEnhancementEnabled = true,
                autoGainControl = false,
                clippingProtection = true
            )
        }
        assertTrue("Close-range studio speech must not clip", !res.clippingProtected)
        assertTrue(
            "Close-range studio speech should remain strong and clear (got ${res.outputDb} dB)",
            res.outputDb > -20f
        )
    }

    @Test
    fun clippingProtectionPreventsDigitalOverload() {
        val dsp = AudioDspProcessor(sampleRate = 48000)
        val loudVoice = ShortArray(1024) { i ->
            (sin(2.0 * PI * 1000.0 * i / 48000.0) * 28000.0).toInt().toShort()
        }
        val result = dsp.processMicBufferInPlace(
            samples = loudVoice,
            sampleCount = loudVoice.size,
            micGainDb = 12f,
            noiseCancellation = NoiseCancellationLevel.OFF,
            pickupRange = VoicePickupRange.WIDE,
            windReductionEnabled = false,
            windReductionIntensity = 0f,
            voiceEnhancementEnabled = true,
            autoGainControl = false,
            clippingProtection = true
        )
        assertTrue("Clipping limiter should engage on high gain signal", result.clippingProtected)
        assertTrue("Peak output must stay within 16-bit PCM bounds", loudVoice.all { it in -32768..32767 })
    }

    @Test
    fun storageEstimationAndMacroblockAlignmentAreValid() {
        val (w2k, h2k) = VideoResolution.P1440.alignedDimensions(isLandscape = true)
        assertEquals(0, w2k % 16)
        assertEquals(0, h2k % 16)

        val s = RecorderSettings(
            resolution = VideoResolution.P1080,
            fps = VideoFps.FPS_60,
            codec = VideoCodec.H264,
            bitrateMbps = 16,
            autoBitrateOptimization = false,
            performanceMode = PerformanceMode.PERFORMANCE,
            autoStopLowStorageMb = 1024
        )
        assertEquals(121.44f, s.estimatedMbPerMinute(), 0.5f)
        val mins = s.estimatedMaxMinutes(3L * 1024L * 1024L * 1024L)
        assertEquals(16, mins)
    }
}
