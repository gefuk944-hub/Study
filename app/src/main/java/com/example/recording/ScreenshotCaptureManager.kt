package com.example.recording

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.HandlerThread
import android.provider.MediaStore
import android.util.DisplayMetrics
import android.view.WindowManager
import com.example.data.MediaStoreRepository
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.coroutines.resume

data class ScreenshotCaptureResult(
    val success: Boolean,
    val uri: Uri? = null,
    val displayName: String? = null,
    val message: String
)

/**
 * Captures crisp full-resolution screenshots via MediaProjection + ImageReader and saves
 * directly to Pictures/ZeroRecorder/ in Android MediaStore.
 */
object ScreenshotCaptureManager {

    suspend fun captureFromProjection(
        context: Context,
        projection: MediaProjection,
        prefix: String = "ZeroShot"
    ): ScreenshotCaptureResult = suspendCancellableCoroutine { cont ->
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val (width, height, densityDpi) = getDisplayDimensions(context, wm)

        val handlerThread = HandlerThread("ZeroRec-Screenshot").apply { start() }
        val handler = Handler(handlerThread.looper)

        var virtualDisplay: VirtualDisplay? = null
        val imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        var completed = false

        fun finish(result: ScreenshotCaptureResult) {
            if (completed) return
            completed = true
            runCatching { virtualDisplay?.release() }
            runCatching { imageReader.close() }
            runCatching { handlerThread.quitSafely() }
            if (cont.isActive) cont.resume(result)
        }

        // Timeout guard so ImageReader never hangs if display is static
        handler.postDelayed({
            finish(ScreenshotCaptureResult(false, message = "Screenshot timed out waiting for display frame."))
        }, 2500L)

        imageReader.setOnImageAvailableListener({ reader ->
            if (completed) return@setOnImageAvailableListener
            val image = runCatching { reader.acquireLatestImage() }.getOrNull() ?: return@setOnImageAvailableListener
            try {
                val plane = image.planes[0]
                val buffer = plane.buffer
                val pixelStride = plane.pixelStride
                val rowStride = plane.rowStride
                val rowPadding = rowStride - pixelStride * width

                val paddedBitmap = Bitmap.createBitmap(
                    width + rowPadding / pixelStride,
                    height,
                    Bitmap.Config.ARGB_8888
                )
                paddedBitmap.copyPixelsFromBuffer(buffer)

                val cropped = Bitmap.createBitmap(paddedBitmap, 0, 0, width, height)
                if (cropped !== paddedBitmap) paddedBitmap.recycle()

                val saved = saveBitmapToGallery(context, cropped, prefix)
                cropped.recycle()
                finish(saved)
            } catch (e: Exception) {
                finish(ScreenshotCaptureResult(false, message = "Screenshot failed: ${e.localizedMessage}"))
            } finally {
                runCatching { image.close() }
            }
        }, handler)

        try {
            virtualDisplay = projection.createVirtualDisplay(
                "ZeroRecorder-Shot",
                width,
                height,
                densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader.surface,
                null,
                handler
            )
        } catch (e: Exception) {
            finish(ScreenshotCaptureResult(false, message = "Could not capture screen: ${e.localizedMessage}"))
        }

        cont.invokeOnCancellation {
            runCatching { virtualDisplay?.release() }
            runCatching { imageReader.close() }
            runCatching { handlerThread.quitSafely() }
        }
    }

    private fun getDisplayDimensions(context: Context, wm: WindowManager): Triple<Int, Int, Int> {
        val density = context.resources.displayMetrics.densityDpi
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = wm.currentWindowMetrics.bounds
            Triple(bounds.width().coerceAtLeast(360), bounds.height().coerceAtLeast(360), density)
        } else {
            val dm = DisplayMetrics()
            @Suppress("DEPRECATION")
            wm.defaultDisplay.getRealMetrics(dm)
            Triple(dm.widthPixels.coerceAtLeast(360), dm.heightPixels.coerceAtLeast(360), dm.densityDpi)
        }
    }

    private fun saveBitmapToGallery(
        context: Context,
        bitmap: Bitmap,
        prefix: String
    ): ScreenshotCaptureResult {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val fileName = "${prefix}_${stamp}.png"

        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                    put(MediaStore.Images.Media.RELATIVE_PATH, "${MediaStoreRepository.SCREENSHOT_FOLDER}/")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
                val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                    ?: return ScreenshotCaptureResult(false, message = "MediaStore rejected screenshot entry.")

                context.contentResolver.openOutputStream(uri)?.use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
                val done = ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }
                context.contentResolver.update(uri, done, null, null)
                ScreenshotCaptureResult(true, uri, fileName, "Saved screenshot to Pictures/ZeroRecorder/$fileName")
            } else {
                val dir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                    "ZeroRecorder"
                ).apply { mkdirs() }
                val file = File(dir, fileName)
                FileOutputStream(file).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
                val uri = Uri.fromFile(file)
                ScreenshotCaptureResult(true, uri, fileName, "Saved screenshot to Pictures/ZeroRecorder/$fileName")
            }
        } catch (e: Exception) {
            ScreenshotCaptureResult(false, message = "Failed saving screenshot: ${e.localizedMessage}")
        }
    }
}
