package com.example.recording

import android.Manifest
import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.data.RecorderSettings
import com.example.data.SettingsRepository
import com.example.system.SystemTelemetryMonitor
import com.example.system.ThermalHealth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Foreground Service managing active screen recordings, live notification controls,
 * countdowns, thermal/storage auto-stop watchdogs, and screenshot captures.
 */
class ScreenRecordingService : Service() {

    companion object {
        private const val TAG = "ScreenRecordingService"
        private const val CHANNEL_ID = "zero_recorder_channel"
        private const val NOTIFICATION_ID = 7001

        const val ACTION_START_RECORDING = "com.example.action.START_RECORDING"
        const val ACTION_PAUSE_RESUME = "com.example.action.PAUSE_RESUME"
        const val ACTION_STOP_RECORDING = "com.example.action.STOP_RECORDING"
        const val ACTION_TOGGLE_MIC = "com.example.action.TOGGLE_MIC"
        const val ACTION_TAKE_SCREENSHOT = "com.example.action.TAKE_SCREENSHOT"
        const val ACTION_STANDALONE_SCREENSHOT = "com.example.action.STANDALONE_SCREENSHOT"
        const val ACTION_STEP_DOWN_THERMAL = "com.example.action.STEP_DOWN_THERMAL"

        const val EXTRA_RESULT_CODE = "extra_result_code"
        const val EXTRA_RESULT_DATA = "extra_result_data"

        fun startRecordingIntent(context: Context, resultCode: Int, data: Intent): Intent =
            Intent(context, ScreenRecordingService::class.java).apply {
                action = ACTION_START_RECORDING
                putExtra(EXTRA_RESULT_CODE, resultCode)
                putExtra(EXTRA_RESULT_DATA, data)
            }

        fun standaloneScreenshotIntent(context: Context, resultCode: Int, data: Intent): Intent =
            Intent(context, ScreenRecordingService::class.java).apply {
                action = ACTION_STANDALONE_SCREENSHOT
                putExtra(EXTRA_RESULT_CODE, resultCode)
                putExtra(EXTRA_RESULT_DATA, data)
            }

        fun controlIntent(context: Context, action: String): Intent =
            Intent(context, ScreenRecordingService::class.java).apply {
                this.action = action
            }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var settingsRepository: SettingsRepository

    private var engine: ScreenRecordingEngine? = null
    private var currentSettings: RecorderSettings = RecorderSettings()
    private var tickerJob: Job? = null
    private var settingsJob: Job? = null
    private var countdownJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        settingsRepository = SettingsRepository(applicationContext)
        createNotificationChannel()
        settingsJob = serviceScope.launch {
            settingsRepository.settingsFlow.collect { updated ->
                currentSettings = updated
                engine?.updateLiveSettings(updated)
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_RECORDING -> {
                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
                val data = getParcelableIntentExtra(intent, EXTRA_RESULT_DATA)
                if (resultCode == Activity.RESULT_OK && data != null) {
                    beginForegroundAndRecord(resultCode, data)
                } else {
                    RecordingEngineController.resetToIdle("Screen capture permission was not granted.")
                    stopSelf()
                }
            }

            ACTION_STANDALONE_SCREENSHOT -> {
                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
                val data = getParcelableIntentExtra(intent, EXTRA_RESULT_DATA)
                if (resultCode == Activity.RESULT_OK && data != null) {
                    captureStandaloneScreenshot(resultCode, data)
                } else {
                    stopSelf()
                }
            }

            ACTION_PAUSE_RESUME -> {
                val activeEngine = engine
                if (activeEngine != null) {
                    val phase = RecordingEngineController.sessionState.value.phase
                    if (phase == RecordingPhase.PAUSED) {
                        activeEngine.resume()
                    } else if (phase == RecordingPhase.RECORDING) {
                        activeEngine.pause()
                    }
                    refreshNotification()
                }
            }

            ACTION_TOGGLE_MIC -> {
                serviceScope.launch {
                    settingsRepository.updateSettings { it.copy(micMuted = !it.micMuted) }
                    refreshNotification()
                }
            }

            ACTION_TAKE_SCREENSHOT -> {
                captureInRecordingScreenshot()
            }

            ACTION_STEP_DOWN_THERMAL -> {
                engine?.applyAdaptiveBitrateScale(0.65f)
                RecordingEngineController.clearThermalWarning()
                RecordingEngineController.update {
                    it.copy(statusBannerMessage = "Thermal protection applied: video bitrate stepped down.")
                }
            }

            ACTION_STOP_RECORDING -> {
                stopActiveRecording(recoveredFromInterruption = false)
            }
        }
        return START_NOT_STICKY
    }

    private fun beginForegroundAndRecord(resultCode: Int, data: Intent) {
        promoteToForeground(includeMicrophone = hasMicPermission())

        countdownJob?.cancel()
        countdownJob = serviceScope.launch {
            currentSettings = settingsRepository.settingsFlow.first()

            val countdown = currentSettings.countdownSeconds.coerceIn(0, 10)
            if (countdown > 0) {
                for (sec in countdown downTo 1) {
                    RecordingEngineController.update {
                        it.copy(phase = RecordingPhase.COUNTDOWN, countdownRemaining = sec)
                    }
                    delay(1000L)
                }
            }

            val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            val projection = runCatching {
                projectionManager.getMediaProjection(resultCode, data)
            }.getOrNull()

            if (projection == null) {
                RecordingEngineController.resetToIdle("Unable to start MediaProjection session.")
                stopForegroundAndSelf()
                return@launch
            }

            val newEngine = ScreenRecordingEngine(
                context = applicationContext,
                mediaProjection = projection,
                initialSettings = currentSettings,
                onStoppedUnexpectedly = { summary ->
                    serviceScope.launch {
                        tickerJob?.cancel()
                        engine = null
                        RecordingEngineController.resetToIdle(summary.message)
                        if (summary.savedUri != null) {
                            RecordingEngineController.notifyMediaChanged(
                                savedVideoUri = summary.savedUri,
                                savedVideoName = summary.displayName,
                                message = summary.message
                            )
                        }
                        stopForegroundAndSelf()
                    }
                }
            )

            val started = newEngine.start()
            if (!started) {
                RecordingEngineController.resetToIdle("Hardware encoder failed to initialize. Try lowering resolution or FPS.")
                stopForegroundAndSelf()
                return@launch
            }

            engine = newEngine
            startWatchdogAndTicker()
            refreshNotification()
        }
    }

    private fun startWatchdogAndTicker() {
        tickerJob?.cancel()
        tickerJob = serviceScope.launch {
            var watchdogTicks = 0
            while (isActive && engine != null) {
                val activeEngine = engine ?: break
                val elapsedMs = activeEngine.getCurrentElapsedMs()
                val bytes = activeEngine.getEstimatedBytesWritten()

                RecordingEngineController.update {
                    it.copy(elapsedMs = elapsedMs, estimatedCurrentBytes = bytes)
                }

                // Refresh notification every second for live timer
                refreshNotification()

                // Every 5 seconds check storage, max duration limit, and device thermal status
                if (++watchdogTicks % 5 == 0) {
                    val telemetry = SystemTelemetryMonitor.snapshot(
                        applicationContext,
                        currentSettings.autoStopLowStorageMb
                    )

                    if (telemetry.isLowStorage) {
                        stopActiveRecording(
                            recoveredFromInterruption = true,
                            customReason = "Auto-stopped: free storage fell below ${currentSettings.autoStopLowStorageMb} MB."
                        )
                        break
                    }

                    val limitMin = currentSettings.maxDurationMinutes
                    if (limitMin > 0 && elapsedMs >= limitMin * 60_000L) {
                        stopActiveRecording(
                            recoveredFromInterruption = false,
                            customReason = "Reached configured ${limitMin}m duration limit — recording saved."
                        )
                        break
                    }

                    if (telemetry.thermalHealth.isWarning) {
                        if (currentSettings.adaptiveBitrate) {
                            val scale = if (telemetry.thermalHealth == ThermalHealth.CRITICAL) 0.65f else 0.82f
                            activeEngine.applyAdaptiveBitrateScale(scale)
                        }
                        RecordingEngineController.update {
                            it.copy(
                                thermalWarningMessage = "Device heating up (${"%.1f".format(telemetry.batteryTempCelsius)}°C — ${telemetry.thermalStatusLabel}). Tap to reduce recording load."
                            )
                        }
                    } else {
                        RecordingEngineController.clearThermalWarning()
                    }
                }

                delay(1000L)
            }
        }
    }

    private fun captureInRecordingScreenshot() {
        val projection = engine?.getMediaProjection() ?: return
        serviceScope.launch(Dispatchers.IO) {
            val result = ScreenshotCaptureManager.captureFromProjection(
                context = applicationContext,
                projection = projection,
                prefix = "${currentSettings.fileNamePrefix}_Shot"
            )
            RecordingEngineController.notifyMediaChanged(
                screenshotUri = result.uri,
                screenshotName = result.displayName,
                message = result.message
            )
        }
    }

    private fun captureStandaloneScreenshot(resultCode: Int, data: Intent) {
        promoteToForeground(includeMicrophone = false)
        serviceScope.launch(Dispatchers.IO) {
            val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            var projection: MediaProjection? = null
            try {
                // Short delay lets system permission dialog disappear before capturing frame
                delay(320L)
                projection = projectionManager.getMediaProjection(resultCode, data)
                if (projection != null) {
                    val result = ScreenshotCaptureManager.captureFromProjection(
                        context = applicationContext,
                        projection = projection,
                        prefix = "ZeroShot"
                    )
                    RecordingEngineController.notifyMediaChanged(
                        screenshotUri = result.uri,
                        screenshotName = result.displayName,
                        message = result.message
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Standalone screenshot failed", e)
            } finally {
                runCatching { projection?.stop() }
                if (engine == null) {
                    stopForegroundAndSelf()
                }
            }
        }
    }

    private fun stopActiveRecording(
        recoveredFromInterruption: Boolean,
        customReason: String? = null
    ) {
        countdownJob?.cancel()
        tickerJob?.cancel()

        val activeEngine = engine
        engine = null

        if (activeEngine == null) {
            RecordingEngineController.resetToIdle()
            stopForegroundAndSelf()
            return
        }

        serviceScope.launch(Dispatchers.IO) {
            val summary = activeEngine.stop(recoveredFromInterruption)
            val finalMessage = customReason ?: summary.message
            RecordingEngineController.resetToIdle(finalMessage)
            if (summary.savedUri != null) {
                RecordingEngineController.notifyMediaChanged(
                    savedVideoUri = summary.savedUri,
                    savedVideoName = summary.displayName,
                    message = finalMessage
                )
            }
            stopForegroundAndSelf()
        }
    }

    private fun promoteToForeground(includeMicrophone: Boolean) {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val fgsTypes = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && includeMicrophone) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION or
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            } else {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            }
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, fgsTypes)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun refreshNotification() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, buildNotification())
    }

    private fun buildNotification(): Notification {
        val state = RecordingEngineController.sessionState.value
        val openAppIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val isPaused = state.phase == RecordingPhase.PAUSED
        val title = when (state.phase) {
            RecordingPhase.PAUSED -> "${getString(R.string.notification_paused_title)} • ${state.formattedTimer}"
            RecordingPhase.COUNTDOWN -> "Starting in ${state.countdownRemaining}s..."
            else -> "${getString(R.string.notification_recording_title)} • ${state.formattedTimer}"
        }
        val subtitle = "${state.resolutionLabel} • ${state.activeFps} FPS • ${"%.1f".format(state.activeBitrateMbps)} Mbps" +
            if (state.micMuted) " • Mic Muted" else " • Mic Live"

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(subtitle)
            .setOngoing(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(openAppIntent)

        if (currentSettings.notificationControlsEnabled && state.isActiveOrPaused) {
            builder.addAction(
                0,
                if (isPaused) getString(R.string.action_resume) else getString(R.string.action_pause),
                serviceActionPendingIntent(ACTION_PAUSE_RESUME, 1)
            )
            builder.addAction(
                0,
                if (state.micMuted) getString(R.string.action_unmute_mic) else getString(R.string.action_mute_mic),
                serviceActionPendingIntent(ACTION_TOGGLE_MIC, 2)
            )
            builder.addAction(
                0,
                getString(R.string.action_stop),
                serviceActionPendingIntent(ACTION_STOP_RECORDING, 3)
            )
        }

        return builder.build()
    }

    private fun serviceActionPendingIntent(action: String, requestCode: Int): PendingIntent {
        return PendingIntent.getService(
            this,
            requestCode,
            controlIntent(this, action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_recording_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_recording_desc)
                setShowBadge(false)
            }
            nm.createNotificationChannel(channel)
        }
    }

    private fun hasMicPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    private fun stopForegroundAndSelf() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    @Suppress("DEPRECATION")
    private fun getParcelableIntentExtra(intent: Intent, key: String): Intent? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(key, Intent::class.java)
        } else {
            intent.getParcelableExtra(key)
        }
    }

    override fun onDestroy() {
        countdownJob?.cancel()
        tickerJob?.cancel()
        settingsJob?.cancel()
        engine?.stop(recoveredFromInterruption = true)
        engine = null
        serviceScope.cancel()
        super.onDestroy()
    }
}
