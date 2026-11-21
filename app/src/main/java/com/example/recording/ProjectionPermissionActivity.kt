package com.example.recording

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionConfig
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat

/**
 * Lightweight translucent Activity that requests official Android MediaProjection consent
 * when the user taps Record or Screenshot from the Floating Game Control Panel over a game.
 */
class ProjectionPermissionActivity : ComponentActivity() {

    companion object {
        const val EXTRA_MODE = "extra_mode"
        const val MODE_RECORD = "record"
        const val MODE_SCREENSHOT = "screenshot"

        fun launch(context: Context, mode: String) {
            val intent = Intent(context, ProjectionPermissionActivity::class.java).apply {
                putExtra(EXTRA_MODE, mode)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }

    private var requestedMode: String = MODE_RECORD

    private val projectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val data = result.data
        if (result.resultCode == Activity.RESULT_OK && data != null) {
            val serviceIntent = if (requestedMode == MODE_SCREENSHOT) {
                ScreenRecordingService.standaloneScreenshotIntent(this, result.resultCode, data)
            } else {
                ScreenRecordingService.startRecordingIntent(this, result.resultCode, data)
            }
            ContextCompat.startForegroundService(this, serviceIntent)
        } else {
            RecordingEngineController.update {
                it.copy(statusBannerMessage = "Screen capture permission cancelled.")
            }
        }
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedMode = intent?.getStringExtra(EXTRA_MODE) ?: MODE_RECORD

        val mgr = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val captureIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            mgr.createScreenCaptureIntent(MediaProjectionConfig.createConfigForUserChoice())
        } else {
            mgr.createScreenCaptureIntent()
        }
        projectionLauncher.launch(captureIntent)
    }
}
