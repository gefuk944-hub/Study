package com.example.system

import android.content.ContentValues
import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.example.data.MediaStoreRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer

data class TrimResult(
    val success: Boolean,
    val outputUri: Uri? = null,
    val outputName: String? = null,
    val message: String
)

/**
 * Fast, lossless, non-destructive MP4 video trimmer using MediaExtractor + MediaMuxer.
 * Copies compressed video and audio packets directly into a brand new file in Movies/ZeroRecorder/
 * without modifying or damaging the original gameplay recording.
 */
object VideoTrimmer {

    suspend fun trimVideo(
        context: Context,
        sourceUri: Uri,
        sourceDisplayName: String,
        startMs: Long,
        endMs: Long
    ): TrimResult = withContext(Dispatchers.IO) {
        if (endMs - startMs < 500L) {
            return@withContext TrimResult(false, message = "Trim selection must be at least 0.5 seconds.")
        }

        val baseName = sourceDisplayName.substringBeforeLast(".")
        val outputFileName = "${baseName}_trim_${System.currentTimeMillis() % 10000}.mp4"

        var outputUri: Uri? = null
        var legacyFile: File? = null
        var muxer: MediaMuxer? = null
        val extractor = MediaExtractor()
        var pfd: android.os.ParcelFileDescriptor? = null

        try {
            extractor.setDataSource(context, sourceUri, null)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Video.Media.DISPLAY_NAME, outputFileName)
                    put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                    put(MediaStore.Video.Media.RELATIVE_PATH, "${MediaStoreRepository.VIDEO_FOLDER}/")
                    put(MediaStore.Video.Media.IS_PENDING, 1)
                }
                outputUri = context.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
                    ?: return@withContext TrimResult(false, message = "Failed to allocate MediaStore file for trimmed clip.")
                pfd = context.contentResolver.openFileDescriptor(outputUri, "rw")
                    ?: return@withContext TrimResult(false, message = "Could not open output file descriptor.")
                muxer = MediaMuxer(pfd.fileDescriptor, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            } else {
                val dir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
                    "ZeroRecorder"
                ).apply { mkdirs() }
                legacyFile = File(dir, outputFileName)
                muxer = MediaMuxer(legacyFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            }

            val trackIndexMap = mutableMapOf<Int, Int>()
            var maxBufferSize = 512 * 1024

            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("video/") || mime.startsWith("audio/")) {
                    extractor.selectTrack(i)
                    val dstIndex = muxer.addTrack(format)
                    trackIndexMap[i] = dstIndex
                    if (format.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
                        val size = format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE)
                        if (size > maxBufferSize) maxBufferSize = size
                    }
                }
            }

            if (trackIndexMap.isEmpty()) {
                return@withContext TrimResult(false, message = "No valid audio/video tracks found in clip.")
            }

            muxer.start()

            val startUs = startMs * 1000L
            val endUs = endMs * 1000L
            extractor.seekTo(startUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)

            val buffer = ByteBuffer.allocate(maxBufferSize)
            val bufferInfo = MediaCodec.BufferInfo()
            var basePtsUs = -1L

            while (true) {
                val sampleTrack = extractor.sampleTrackIndex
                if (sampleTrack < 0) break

                val sampleTimeUs = extractor.sampleTime
                if (sampleTimeUs > endUs) break

                val size = extractor.readSampleData(buffer, 0)
                if (size < 0) break

                if (sampleTimeUs >= startUs || basePtsUs >= 0L) {
                    if (basePtsUs < 0L) basePtsUs = sampleTimeUs
                    val dstTrack = trackIndexMap[sampleTrack]
                    if (dstTrack != null) {
                        bufferInfo.offset = 0
                        bufferInfo.size = size
                        bufferInfo.presentationTimeUs = (sampleTimeUs - basePtsUs).coerceAtLeast(0L)
                        bufferInfo.flags = extractor.sampleFlags
                        muxer.writeSampleData(dstTrack, buffer, bufferInfo)
                    }
                }
                extractor.advance()
            }

            muxer.stop()
            muxer.release()
            muxer = null
            pfd?.close()
            pfd = null

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && outputUri != null) {
                val doneValues = ContentValues().apply {
                    put(MediaStore.Video.Media.IS_PENDING, 0)
                }
                context.contentResolver.update(outputUri, doneValues, null, null)
            } else if (legacyFile != null) {
                outputUri = Uri.fromFile(legacyFile)
            }

            TrimResult(
                success = true,
                outputUri = outputUri,
                outputName = outputFileName,
                message = "Saved trimmed clip to Movies/ZeroRecorder/$outputFileName"
            )
        } catch (e: Exception) {
            runCatching { muxer?.release() }
            runCatching { pfd?.close() }
            if (outputUri != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                runCatching { context.contentResolver.delete(outputUri, null, null) }
            }
            legacyFile?.delete()
            TrimResult(
                success = false,
                message = "Trim failed: ${e.localizedMessage ?: "Unsupported MP4 structure"}"
            )
        } finally {
            runCatching { extractor.release() }
        }
    }
}
