package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.recorderDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "zero_recorder_settings"
)

/**
 * Local-only settings repository backed by Jetpack DataStore Preferences.
 * No account or internet connection required; all settings persist across restarts.
 */
class SettingsRepository(private val context: Context) {

    private object Keys {
        val RESOLUTION = stringPreferencesKey("resolution")
        val FPS = intPreferencesKey("fps")
        val BITRATE_MBPS = intPreferencesKey("bitrate_mbps")
        val AUTO_BITRATE = booleanPreferencesKey("auto_bitrate")
        val CODEC = stringPreferencesKey("codec")
        val QUALITY_PRESET = stringPreferencesKey("quality_preset")
        val ORIENTATION = stringPreferencesKey("orientation")
        val ADAPTIVE_FPS = booleanPreferencesKey("adaptive_fps")
        val ADAPTIVE_BITRATE = booleanPreferencesKey("adaptive_bitrate")

        val AUDIO_PRESET = stringPreferencesKey("audio_preset")
        val GAME_ENABLED = booleanPreferencesKey("game_enabled")
        val GAME_VOLUME = floatPreferencesKey("game_volume")
        val GAME_MUTED = booleanPreferencesKey("game_muted")
        val GAME_ORIGINAL_MODE = booleanPreferencesKey("game_original_mode")

        val MIC_ENABLED = booleanPreferencesKey("mic_enabled")
        val MIC_VOLUME = floatPreferencesKey("mic_volume")
        val MIC_GAIN_DB = floatPreferencesKey("mic_gain_db")
        val MIC_MUTED = booleanPreferencesKey("mic_muted")
        val MIC_SOURCE = stringPreferencesKey("mic_source")

        val TEAM_ENABLED = booleanPreferencesKey("team_enabled")
        val TEAM_VOLUME = floatPreferencesKey("team_volume")
        val TEAM_MUTED = booleanPreferencesKey("team_muted")
        val SMART_VOICE_BALANCE = booleanPreferencesKey("smart_voice_balance")
        val CLIPPING_PROTECTION = booleanPreferencesKey("clipping_protection")

        val NOISE_CANCELLATION = stringPreferencesKey("noise_cancellation")
        val WIND_REDUCTION_ENABLED = booleanPreferencesKey("wind_reduction_enabled")
        val WIND_REDUCTION_INTENSITY = floatPreferencesKey("wind_reduction_intensity")
        val VOICE_ENHANCEMENT = booleanPreferencesKey("voice_enhancement")
        val AUTO_GAIN_CONTROL = booleanPreferencesKey("auto_gain_control")
        val ECHO_REDUCTION = booleanPreferencesKey("echo_reduction")

        val CUSTOM_GAME_VOL = floatPreferencesKey("custom_game_vol")
        val CUSTOM_MIC_VOL = floatPreferencesKey("custom_mic_vol")
        val CUSTOM_MIC_GAIN = floatPreferencesKey("custom_mic_gain")
        val CUSTOM_TEAM_VOL = floatPreferencesKey("custom_team_vol")
        val CUSTOM_NC = stringPreferencesKey("custom_nc")
        val CUSTOM_VOICE_ENH = booleanPreferencesKey("custom_voice_enh")

        val FLOATING_ENABLED = booleanPreferencesKey("floating_enabled")
        val FLOATING_COMPACT = booleanPreferencesKey("floating_compact")
        val FLOATING_OPACITY = floatPreferencesKey("floating_opacity")
        val FLOATING_SIZE = stringPreferencesKey("floating_size")
        val HIDE_FLOATING_RECORDING = booleanPreferencesKey("hide_floating_recording")

        val COUNTDOWN_SECONDS = intPreferencesKey("countdown_seconds")
        val SHOW_RECORDING_TIMER = booleanPreferencesKey("show_recording_timer")
        val NOTIFICATION_CONTROLS = booleanPreferencesKey("notification_controls")
        val HAPTIC_FEEDBACK = booleanPreferencesKey("haptic_feedback")
        val PERFORMANCE_MODE = stringPreferencesKey("performance_mode")
        val AUTO_STOP_STORAGE_MB = intPreferencesKey("auto_stop_storage_mb")
        val MAX_DURATION_MIN = intPreferencesKey("max_duration_min")
        val FILE_PREFIX = stringPreferencesKey("file_prefix")
        val AUDIO_V2_MIGRATED = booleanPreferencesKey("audio_v2_migrated")
    }

    val settingsFlow: Flow<RecorderSettings> = context.recorderDataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(androidx.datastore.preferences.core.emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { prefs -> readFromPrefs(prefs) }

    suspend fun updateSettings(transform: (RecorderSettings) -> RecorderSettings) {
        context.recorderDataStore.edit { prefs ->
            val current = readFromPrefs(prefs)
            val updated = transform(current)
            writeToPrefs(prefs, updated)
        }
    }

    suspend fun restoreAllDefaults() {
        context.recorderDataStore.edit { prefs ->
            prefs.clear()
            prefs[Keys.AUDIO_V2_MIGRATED] = true
        }
    }

    suspend fun restoreAudioMixerDefaults() {
        updateSettings { current ->
            val preset = AudioPreset.GAMING
            current.copy(
                audioPreset = preset,
                gameAudioEnabled = true,
                gameAudioVolume = preset.gameVolume,
                gameAudioMuted = false,
                gameOriginalAudioMode = true,
                micEnabled = true,
                micVolume = preset.micVolume,
                micGainDb = preset.micGainDb,
                micMuted = false,
                micSource = MicAudioSource.MIC,
                teamVoiceEnabled = true,
                teamVoiceVolume = preset.gameVolume,
                teamVoiceMuted = false,
                smartVoiceBalance = preset.smartVoiceBalance,
                clippingProtection = true,
                noiseCancellation = preset.noiseCancellation,
                windReductionEnabled = false,
                windReductionIntensity = 0.50f,
                voiceEnhancementEnabled = preset.voiceEnhancement,
                autoGainControl = false,
                echoReduction = false
            )
        }
    }

    private fun readFromPrefs(prefs: Preferences): RecorderSettings {
        val defaults = RecorderSettings()
        val migratedV2 = prefs[Keys.AUDIO_V2_MIGRATED] ?: false

        // One-time transparent upgrade for users updating from v1.0 so old VOICE_COMMUNICATION /
        // stacked AGC settings never carry over crackling or distorted voice!
        val effectiveMicSource = if (migratedV2) {
            enumOrDefault(prefs[Keys.MIC_SOURCE], defaults.micSource)
        } else {
            MicAudioSource.MIC
        }
        val effectiveMicGain = if (migratedV2) {
            (prefs[Keys.MIC_GAIN_DB] ?: defaults.micGainDb).coerceIn(-6f, 12f)
        } else {
            defaults.micGainDb
        }
        val effectiveAgc = if (migratedV2) (prefs[Keys.AUTO_GAIN_CONTROL] ?: defaults.autoGainControl) else false
        val effectiveEcho = if (migratedV2) (prefs[Keys.ECHO_REDUCTION] ?: defaults.echoReduction) else false

        return RecorderSettings(
            resolution = enumOrDefault(prefs[Keys.RESOLUTION], defaults.resolution),
            fps = VideoFps.entries.firstOrNull { it.value == prefs[Keys.FPS] } ?: defaults.fps,
            bitrateMbps = (prefs[Keys.BITRATE_MBPS] ?: defaults.bitrateMbps).coerceIn(2, 40),
            autoBitrateOptimization = prefs[Keys.AUTO_BITRATE] ?: defaults.autoBitrateOptimization,
            codec = enumOrDefault(prefs[Keys.CODEC], defaults.codec),
            qualityPreset = enumOrDefault(prefs[Keys.QUALITY_PRESET], defaults.qualityPreset),
            orientationMode = enumOrDefault(prefs[Keys.ORIENTATION], defaults.orientationMode),
            adaptiveFps = prefs[Keys.ADAPTIVE_FPS] ?: defaults.adaptiveFps,
            adaptiveBitrate = prefs[Keys.ADAPTIVE_BITRATE] ?: defaults.adaptiveBitrate,
            audioPreset = enumOrDefault(prefs[Keys.AUDIO_PRESET], defaults.audioPreset),
            gameAudioEnabled = prefs[Keys.GAME_ENABLED] ?: defaults.gameAudioEnabled,
            gameAudioVolume = (prefs[Keys.GAME_VOLUME] ?: defaults.gameAudioVolume).coerceIn(0f, 1.5f),
            gameAudioMuted = prefs[Keys.GAME_MUTED] ?: defaults.gameAudioMuted,
            gameOriginalAudioMode = prefs[Keys.GAME_ORIGINAL_MODE] ?: defaults.gameOriginalAudioMode,
            micEnabled = prefs[Keys.MIC_ENABLED] ?: defaults.micEnabled,
            micVolume = (prefs[Keys.MIC_VOLUME] ?: defaults.micVolume).coerceIn(0f, 1.5f),
            micGainDb = effectiveMicGain,
            micMuted = prefs[Keys.MIC_MUTED] ?: defaults.micMuted,
            micSource = effectiveMicSource,
            teamVoiceEnabled = prefs[Keys.GAME_ENABLED] ?: defaults.gameAudioEnabled,
            teamVoiceVolume = (prefs[Keys.GAME_VOLUME] ?: defaults.gameAudioVolume).coerceIn(0f, 1.5f),
            teamVoiceMuted = prefs[Keys.GAME_MUTED] ?: defaults.gameAudioMuted,
            smartVoiceBalance = prefs[Keys.SMART_VOICE_BALANCE] ?: defaults.smartVoiceBalance,
            clippingProtection = prefs[Keys.CLIPPING_PROTECTION] ?: defaults.clippingProtection,
            noiseCancellation = enumOrDefault(prefs[Keys.NOISE_CANCELLATION], defaults.noiseCancellation),
            windReductionEnabled = prefs[Keys.WIND_REDUCTION_ENABLED] ?: defaults.windReductionEnabled,
            windReductionIntensity = (prefs[Keys.WIND_REDUCTION_INTENSITY] ?: defaults.windReductionIntensity).coerceIn(0f, 1f),
            voiceEnhancementEnabled = prefs[Keys.VOICE_ENHANCEMENT] ?: defaults.voiceEnhancementEnabled,
            autoGainControl = effectiveAgc,
            echoReduction = effectiveEcho,
            customGameVolume = prefs[Keys.CUSTOM_GAME_VOL] ?: defaults.customGameVolume,
            customMicVolume = prefs[Keys.CUSTOM_MIC_VOL] ?: defaults.customMicVolume,
            customMicGainDb = prefs[Keys.CUSTOM_MIC_GAIN] ?: defaults.customMicGainDb,
            customTeamVolume = prefs[Keys.CUSTOM_GAME_VOL] ?: defaults.customGameVolume,
            customNoiseCancellation = enumOrDefault(prefs[Keys.CUSTOM_NC], defaults.customNoiseCancellation),
            customVoiceEnhancement = prefs[Keys.CUSTOM_VOICE_ENH] ?: defaults.customVoiceEnhancement,
            floatingControlEnabled = prefs[Keys.FLOATING_ENABLED] ?: defaults.floatingControlEnabled,
            floatingCompactMode = prefs[Keys.FLOATING_COMPACT] ?: defaults.floatingCompactMode,
            floatingOpacity = (prefs[Keys.FLOATING_OPACITY] ?: defaults.floatingOpacity).coerceIn(0.3f, 1f),
            floatingSize = enumOrDefault(prefs[Keys.FLOATING_SIZE], defaults.floatingSize),
            hideFloatingWhileRecording = prefs[Keys.HIDE_FLOATING_RECORDING] ?: defaults.hideFloatingWhileRecording,
            countdownSeconds = prefs[Keys.COUNTDOWN_SECONDS] ?: defaults.countdownSeconds,
            showRecordingTimer = prefs[Keys.SHOW_RECORDING_TIMER] ?: defaults.showRecordingTimer,
            notificationControlsEnabled = prefs[Keys.NOTIFICATION_CONTROLS] ?: defaults.notificationControlsEnabled,
            hapticFeedbackEnabled = prefs[Keys.HAPTIC_FEEDBACK] ?: defaults.hapticFeedbackEnabled,
            performanceMode = enumOrDefault(prefs[Keys.PERFORMANCE_MODE], defaults.performanceMode),
            autoStopLowStorageMb = prefs[Keys.AUTO_STOP_STORAGE_MB] ?: defaults.autoStopLowStorageMb,
            maxDurationMinutes = prefs[Keys.MAX_DURATION_MIN] ?: defaults.maxDurationMinutes,
            fileNamePrefix = prefs[Keys.FILE_PREFIX]?.takeIf { it.isNotBlank() } ?: defaults.fileNamePrefix
        )
    }

    private fun writeToPrefs(
        prefs: androidx.datastore.preferences.core.MutablePreferences,
        s: RecorderSettings
    ) {
        prefs[Keys.RESOLUTION] = s.resolution.name
        prefs[Keys.FPS] = s.fps.value
        prefs[Keys.BITRATE_MBPS] = s.bitrateMbps
        prefs[Keys.AUTO_BITRATE] = s.autoBitrateOptimization
        prefs[Keys.CODEC] = s.codec.name
        prefs[Keys.QUALITY_PRESET] = s.qualityPreset.name
        prefs[Keys.ORIENTATION] = s.orientationMode.name
        prefs[Keys.ADAPTIVE_FPS] = s.adaptiveFps
        prefs[Keys.ADAPTIVE_BITRATE] = s.adaptiveBitrate

        prefs[Keys.AUDIO_PRESET] = s.audioPreset.name
        prefs[Keys.GAME_ENABLED] = s.gameAudioEnabled
        prefs[Keys.GAME_VOLUME] = s.gameAudioVolume
        prefs[Keys.GAME_MUTED] = s.gameAudioMuted
        prefs[Keys.GAME_ORIGINAL_MODE] = s.gameOriginalAudioMode

        prefs[Keys.MIC_ENABLED] = s.micEnabled
        prefs[Keys.MIC_VOLUME] = s.micVolume
        prefs[Keys.MIC_GAIN_DB] = s.micGainDb
        prefs[Keys.MIC_MUTED] = s.micMuted
        prefs[Keys.MIC_SOURCE] = s.micSource.name

        prefs[Keys.TEAM_ENABLED] = s.teamVoiceEnabled
        prefs[Keys.TEAM_VOLUME] = s.teamVoiceVolume
        prefs[Keys.TEAM_MUTED] = s.teamVoiceMuted
        prefs[Keys.SMART_VOICE_BALANCE] = s.smartVoiceBalance
        prefs[Keys.CLIPPING_PROTECTION] = s.clippingProtection

        prefs[Keys.NOISE_CANCELLATION] = s.noiseCancellation.name
        prefs[Keys.WIND_REDUCTION_ENABLED] = s.windReductionEnabled
        prefs[Keys.WIND_REDUCTION_INTENSITY] = s.windReductionIntensity
        prefs[Keys.VOICE_ENHANCEMENT] = s.voiceEnhancementEnabled
        prefs[Keys.AUTO_GAIN_CONTROL] = s.autoGainControl
        prefs[Keys.ECHO_REDUCTION] = s.echoReduction

        prefs[Keys.CUSTOM_GAME_VOL] = s.customGameVolume
        prefs[Keys.CUSTOM_MIC_VOL] = s.customMicVolume
        prefs[Keys.CUSTOM_MIC_GAIN] = s.customMicGainDb
        prefs[Keys.CUSTOM_TEAM_VOL] = s.customTeamVolume
        prefs[Keys.CUSTOM_NC] = s.customNoiseCancellation.name
        prefs[Keys.CUSTOM_VOICE_ENH] = s.customVoiceEnhancement

        prefs[Keys.FLOATING_ENABLED] = s.floatingControlEnabled
        prefs[Keys.FLOATING_COMPACT] = s.floatingCompactMode
        prefs[Keys.FLOATING_OPACITY] = s.floatingOpacity
        prefs[Keys.FLOATING_SIZE] = s.floatingSize.name
        prefs[Keys.HIDE_FLOATING_RECORDING] = s.hideFloatingWhileRecording

        prefs[Keys.COUNTDOWN_SECONDS] = s.countdownSeconds
        prefs[Keys.SHOW_RECORDING_TIMER] = s.showRecordingTimer
        prefs[Keys.NOTIFICATION_CONTROLS] = s.notificationControlsEnabled
        prefs[Keys.HAPTIC_FEEDBACK] = s.hapticFeedbackEnabled
        prefs[Keys.PERFORMANCE_MODE] = s.performanceMode.name
        prefs[Keys.AUTO_STOP_STORAGE_MB] = s.autoStopLowStorageMb
        prefs[Keys.MAX_DURATION_MIN] = s.maxDurationMinutes
        prefs[Keys.FILE_PREFIX] = s.fileNamePrefix
        prefs[Keys.AUDIO_V2_MIGRATED] = true
    }

    private inline fun <reified T : Enum<T>> enumOrDefault(raw: String?, default: T): T {
        if (raw == null) return default
        return enumValues<T>().firstOrNull { it.name == raw } ?: default
    }
}
