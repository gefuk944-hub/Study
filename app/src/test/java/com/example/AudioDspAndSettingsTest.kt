package com.example

import com.example.audio.AudioDspProcessor
import com.example.data.NoiseCancellationLevel
import com.example.data.PerformanceMode
import com.example.data.RecorderSettings
import com.example.data.VideoCodec
import com.example.data.VideoFps
import com.example.data.VideoResolution
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
            windReductionEnabled = true,
            windReductionIntensity = 1.0f,
            voiceEnhancementEnabled = false,
            autoGainControl = false,
            clippingProtection = true
        )
        assertTrue(
            "50 Hz wind rumble should be heavily attenuated by 240 Hz HPF",
            result.outputDb < result.inputDb - 10f
        )
    }

    @Test
    fun noiseCancellationAttenuatesQuietConstantBackgroundNoise() {
        val dsp = AudioDspProcessor(sampleRate = 48000)
        // Low-level steady room hum (~ -45 dBFS)
        val noiseBuf = ShortArray(960) { i ->
            (sin(2.0 * PI * 300.0 * i / 48000.0) * 180.0).toInt().toShort()
        }
        var lastResult = dsp.processMicBufferInPlace(
            samples = noiseBuf.copyOf(),
            sampleCount = noiseBuf.size,
            micGainDb = 0f,
            noiseCancellation = NoiseCancellationLevel.STRONG,
            windReductionEnabled = false,
            windReductionIntensity = 0f,
            voiceEnhancementEnabled = false,
            autoGainControl = false,
            clippingProtection = true
        )
        repeat(6) {
            lastResult = dsp.processMicBufferInPlace(
                samples = noiseBuf.copyOf(),
                sampleCount = noiseBuf.size,
                micGainDb = 0f,
                noiseCancellation = NoiseCancellationLevel.STRONG,
                windReductionEnabled = false,
                windReductionIntensity = 0f,
                voiceEnhancementEnabled = false,
                autoGainControl = false,
                clippingProtection = true
            )
        }
        assertTrue(
            "Strong noise cancellation should attenuate quiet steady background noise",
            lastResult.outputDb < lastResult.inputDb - 10f
        )
    }

    @Test
    fun clippingProtectionPreventsDigitalOverload() {
        val dsp = AudioDspProcessor(sampleRate = 48000)
        val loudVoice = ShortArray(960) { i ->
            (sin(2.0 * PI * 1000.0 * i / 48000.0) * 28000.0).toInt().toShort()
        }
        val result = dsp.processMicBufferInPlace(
            samples = loudVoice,
            sampleCount = loudVoice.size,
            micGainDb = 12f,
            noiseCancellation = NoiseCancellationLevel.OFF,
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
        // 16.192 Mbps * 60 / 8 ~= 121.44 MB/min
        assertEquals(121.44f, s.estimatedMbPerMinute(), 0.5f)
        // With 3 GB free and 1 GB reserve -> 2048 MB usable -> ~16 minutes
        val mins = s.estimatedMaxMinutes(3L * 1024L * 1024L * 1024L)
        assertEquals(16, mins)
    }
}
