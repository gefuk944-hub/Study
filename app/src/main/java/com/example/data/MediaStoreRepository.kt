package com.example.data

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Size
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class RecordedVideoItem(
    val id: Long,
    val uri: Uri,
    val displayName: String,
    val durationMs: Long,
    val width: Int,
    val height: Int,
    val fps: Int,
    val sizeBytes: Long,
    val dateAddedSec: Long,
    val relativePath: String
) {
    val resolutionLabel: String
        get() = if (width > 0 && height > 0) "${width}x${height}" else "HD"

    val formattedSize: String
        get() {
            val mb = sizeBytes / (1024f * 1024f)
            return if (mb >= 1024f) "%.2f GB".format(mb / 1024f) else "%.1f MB".format(mb)
        }

    val formattedDuration: String
        get() {
            val totalSec = (durationMs / 1000L).coerceAtLeast(0L)
            val hrs = totalSec / 3600L
            val mins = (totalSec % 3600L) / 60L
            val secs = totalSec % 60L
            return if (hrs > 0) "%d:%02d:%02d".format(hrs, mins, secs) else "%02d:%02d".format(mins, secs)
        }
}

data class ScreenshotItem(
    val id: Long,
    val uri: Uri,
    val displayName: String,
    val width: Int,
    val height: Int,
    val sizeBytes: Long,
    val dateAddedSec: Long
) {
    val resolutionLabel: String
        get() = if (width > 0 && height > 0) "${width}x${height}" else "PNG"

    val formattedSize: String
        get() {
            val kb = sizeBytes / 1024f
            return if (kb >= 1024f) "%.1f MB".format(kb / 1024f) else "%.0f KB".format(kb)
        }
}

/**
 * Zero-permission MediaStore repository managing Zero Recorder videos (Movies/ZeroRecorder/)
 * and screenshots (Pictures/ZeroRecorder/) directly in the Android system Gallery.
 */
class MediaStoreRepository(private val context: Context) {

    companion object {
        const val VIDEO_FOLDER = "Movies/ZeroRecorder"
        const val SCREENSHOT_FOLDER = "Pictures/ZeroRecorder"
    }

    suspend fun loadVideos(): List<RecordedVideoItem> = withContext(Dispatchers.IO) {
        val items = mutableListOf<RecordedVideoItem>()
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        }

        val projection = buildList {
            add(MediaStore.Video.Media._ID)
            add(MediaStore.Video.Media.DISPLAY_NAME)
            add(MediaStore.Video.Media.DURATION)
            add(MediaStore.Video.Media.WIDTH)
            add(MediaStore.Video.Media.HEIGHT)
            add(MediaStore.Video.Media.SIZE)
            add(MediaStore.Video.Media.DATE_ADDED)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                add(MediaStore.Video.Media.RELATIVE_PATH)
                add(MediaStore.Video.Media.IS_PENDING)
            }
        }.toTypedArray()

        val selection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            "${MediaStore.Video.Media.RELATIVE_PATH} LIKE ? AND ${MediaStore.Video.Media.IS_PENDING} = 0"
        } else {
            "${MediaStore.Video.Media.DATA} LIKE ?"
        }
        val selectionArgs = arrayOf("%ZeroRecorder%")
        val sortOrder = "${MediaStore.Video.Media.DATE_ADDED} DESC"

        runCatching {
            context.contentResolver.query(collection, projection, selection, selectionArgs, sortOrder)
                ?.use { cursor ->
                    val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                    val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                    val durCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
                    val wCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.WIDTH)
                    val hCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.HEIGHT)
                    val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
                    val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
                    val pathCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        cursor.getColumnIndex(MediaStore.Video.Media.RELATIVE_PATH)
                    } else -1

                    while (cursor.moveToNext()) {
                        val id = cursor.getLong(idCol)
                        val uri = ContentUris.withAppendedId(collection, id)
                        val size = cursor.getLong(sizeCol)
                        if (size <= 0L) continue

                        var w = cursor.getInt(wCol)
                        var h = cursor.getInt(hCol)
                        var dur = cursor.getLong(durCol)
                        var fps = 0

                        // Extract actual FPS (and fallback resolution/duration if MediaStore hasn't indexed yet)
                        runCatching {
                            val mmr = MediaMetadataRetriever()
                            try {
                                mmr.setDataSource(context, uri)
                                if (w <= 0) {
                                    w = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
                                }
                                if (h <= 0) {
                                    h = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
                                }
                                if (dur <= 0L) {
                                    dur = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                                }
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                    val captureRate = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)
                                        ?.toFloatOrNull()
                                    if (captureRate != null && captureRate > 1f) {
                                        fps = captureRate.toInt()
                                    }
                                }
                                if (fps == 0 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && dur > 500L) {
                                    val frameCount = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_FRAME_COUNT)
                                        ?.toLongOrNull()
                                    if (frameCount != null && frameCount > 0) {
                                        fps = ((frameCount * 1000L) / dur).toInt().coerceIn(1, 120)
                                    }
                                }
                            } finally {
                                mmr.release()
                            }
                        }

                        items.add(
                            RecordedVideoItem(
                                id = id,
                                uri = uri,
                                displayName = cursor.getString(nameCol) ?: "Recording_$id.mp4",
                                durationMs = dur,
                                width = w,
                                height = h,
                                fps = fps,
                                sizeBytes = size,
                                dateAddedSec = cursor.getLong(dateCol),
                                relativePath = if (pathCol >= 0) cursor.getString(pathCol) ?: VIDEO_FOLDER else VIDEO_FOLDER
                            )
                        )
                    }
                }
        }
        items
    }

    suspend fun loadScreenshots(): List<ScreenshotItem> = withContext(Dispatchers.IO) {
        val items = mutableListOf<ScreenshotItem>()
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }

        val projection = buildList {
            add(MediaStore.Images.Media._ID)
            add(MediaStore.Images.Media.DISPLAY_NAME)
            add(MediaStore.Images.Media.WIDTH)
            add(MediaStore.Images.Media.HEIGHT)
            add(MediaStore.Images.Media.SIZE)
            add(MediaStore.Images.Media.DATE_ADDED)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                add(MediaStore.Images.Media.IS_PENDING)
            }
        }.toTypedArray()

        val selection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            "${MediaStore.Images.Media.RELATIVE_PATH} LIKE ? AND ${MediaStore.Images.Media.IS_PENDING} = 0"
        } else {
            "${MediaStore.Images.Media.DATA} LIKE ?"
        }
        val selectionArgs = arrayOf("%ZeroRecorder%")
        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

        runCatching {
            context.contentResolver.query(collection, projection, selection, selectionArgs, sortOrder)
                ?.use { cursor ->
                    val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                    val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                    val wCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.WIDTH)
                    val hCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.HEIGHT)
                    val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
                    val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)

                    while (cursor.moveToNext()) {
                        val id = cursor.getLong(idCol)
                        val size = cursor.getLong(sizeCol)
                        if (size <= 0L) continue
                        items.add(
                            ScreenshotItem(
                                id = id,
                                uri = ContentUris.withAppendedId(collection, id),
                                displayName = cursor.getString(nameCol) ?: "Screenshot_$id.png",
                                width = cursor.getInt(wCol),
                                height = cursor.getInt(hCol),
                                sizeBytes = size,
                                dateAddedSec = cursor.getLong(dateCol)
                            )
                        )
                    }
                }
        }
        items
    }

    suspend fun loadVideoThumbnail(uri: Uri): Bitmap? = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val nativeThumb = runCatching {
                context.contentResolver.loadThumbnail(uri, Size(480, 270), null)
            }.getOrNull()
            if (nativeThumb != null) return@withContext nativeThumb
        }
        runCatching {
            val mmr = MediaMetadataRetriever()
            try {
                mmr.setDataSource(context, uri)
                mmr.getFrameAtTime(500_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    ?: mmr.frameAtTime
            } finally {
                mmr.release()
            }
        }.getOrNull()
    }

    suspend fun renameMedia(uri: Uri, newBaseName: String, extension: String): Boolean =
        withContext(Dispatchers.IO) {
            val clean = newBaseName.trim().replace(Regex("[^a-zA-Z0-9._\\- ]"), "_")
            if (clean.isBlank()) return@withContext false
            val finalName = if (clean.endsWith(extension, ignoreCase = true)) clean else "$clean$extension"
            runCatching {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, finalName)
                }
                context.contentResolver.update(uri, values, null, null) > 0
            }.getOrDefault(false)
        }

    suspend fun deleteMedia(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.delete(uri, null, null) > 0
        }.getOrDefault(false)
    }
}
