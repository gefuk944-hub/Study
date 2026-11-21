package com.example.ui.screens

import android.media.MediaPlayer
import android.widget.VideoView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.data.RecordedVideoItem
import com.example.system.TrimResult
import com.example.ui.components.ConfirmDeleteDialog
import com.example.ui.components.GamingCard
import com.example.ui.components.RenameMediaDialog
import com.example.ui.components.StatusPill
import com.example.ui.theme.CarbonBorder
import com.example.ui.theme.CarbonSurfaceHigh
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.NeonViolet
import com.example.ui.theme.ObsidianVoid
import com.example.ui.theme.RecordCrimson
import com.example.ui.theme.SignalGreen
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlinx.coroutines.delay

@Composable
fun VideoPlayerScreen(
    video: RecordedVideoItem,
    trimInProgress: Boolean,
    lastTrimResult: TrimResult?,
    onBack: () -> Unit,
    onShare: (RecordedVideoItem) -> Unit,
    onRename: (RecordedVideoItem, String) -> Unit,
    onDelete: (RecordedVideoItem) -> Unit,
    onTrim: (RecordedVideoItem, Long, Long) -> Unit,
    onClearTrimResult: () -> Unit
) {
    var videoViewRef by remember { mutableStateOf<VideoView?>(null) }
    var mediaPlayerRef by remember { mutableStateOf<MediaPlayer?>(null) }

    var isPlaying by remember { mutableStateOf(true) }
    var currentPosMs by remember { mutableIntStateOf(0) }
    var totalDurationMs by remember(video.id) { mutableIntStateOf(video.durationMs.toInt().coerceAtLeast(1000)) }
    var volume by remember { mutableFloatStateOf(1f) }
    var isMuted by remember { mutableStateOf(false) }
    var isFullscreen by remember { mutableStateOf(false) }
    var showTrimmer by remember { mutableStateOf(false) }
    var trimRange by remember(video.id) { mutableStateOf(0f..1f) }

    var showRenameDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    // Poll playback position smoothly
    LaunchedEffect(isPlaying, videoViewRef) {
        while (true) {
            val vv = videoViewRef
            if (vv != null && vv.isPlaying) {
                currentPosMs = vv.currentPosition
                if (vv.duration > 0) totalDurationMs = vv.duration
            }
            delay(200L)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            runCatching { videoViewRef?.stopPlayback() }
            onClearTrimResult()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianVoid)
    ) {
        // Top Header Bar (hidden in fullscreen)
        if (!isFullscreen) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.testTag("player_back_button")
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        video.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        "${video.resolutionLabel} • ${video.formattedSize}",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
                IconButton(onClick = { showRenameDialog = true }) {
                    Icon(Icons.Default.Edit, contentDescription = "Rename", tint = CyberCyan)
                }
                IconButton(onClick = { onShare(video) }) {
                    Icon(Icons.Default.Share, contentDescription = "Share", tint = ElectricBlue)
                }
                IconButton(onClick = { showDeleteDialog = true }) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = RecordCrimson)
                }
            }
        }

        // Video Surface
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (isFullscreen) Modifier.weight(1f)
                    else Modifier.aspectRatio(16f / 9f)
                )
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            AndroidView(
                factory = { ctx ->
                    VideoView(ctx).apply {
                        setVideoURI(video.uri)
                        setOnPreparedListener { mp ->
                            mediaPlayerRef = mp
                            totalDurationMs = mp.duration.coerceAtLeast(1000)
                            val effVol = if (isMuted) 0f else volume
                            mp.setVolume(effVol, effVol)
                            start()
                            isPlaying = true
                        }
                        setOnCompletionListener {
                            isPlaying = false
                        }
                        videoViewRef = this
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            IconButton(
                onClick = { isFullscreen = !isFullscreen },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
                    .background(ObsidianVoid.copy(alpha = 0.65f), RoundedCornerShape(10.dp))
                    .testTag("player_fullscreen_toggle")
            ) {
                Icon(
                    if (isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                    contentDescription = if (isFullscreen) "Exit fullscreen" else "Fullscreen",
                    tint = Color.White
                )
            }
        }

        // Transport Controls + Seek + Volume + Lossless Trimmer
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (!isFullscreen) Modifier.weight(1f).verticalScroll(rememberScrollState()) else Modifier)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Seek Bar & Play/Pause
            GamingCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            val vv = videoViewRef ?: return@IconButton
                            if (vv.isPlaying) {
                                vv.pause()
                                isPlaying = false
                            } else {
                                vv.start()
                                isPlaying = true
                            }
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("player_play_pause_button")
                    ) {
                        Icon(
                            if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            tint = ElectricBlue,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    Text(
                        formatMs(currentPosMs.toLong()),
                        style = MaterialTheme.typography.labelMedium,
                        color = CyberCyan
                    )

                    Slider(
                        value = currentPosMs.toFloat().coerceIn(0f, totalDurationMs.toFloat()),
                        onValueChange = { pos ->
                            currentPosMs = pos.toInt()
                            videoViewRef?.seekTo(currentPosMs)
                        },
                        valueRange = 0f..totalDurationMs.toFloat(),
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp)
                            .testTag("player_seek_slider"),
                        colors = SliderDefaults.colors(thumbColor = ElectricBlue, activeTrackColor = ElectricBlue)
                    )

                    Text(
                        formatMs(totalDurationMs.toLong()),
                        style = MaterialTheme.typography.labelMedium,
                        color = TextSecondary
                    )
                }

                // Volume Control
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            isMuted = !isMuted
                            val eff = if (isMuted) 0f else volume
                            runCatching { mediaPlayerRef?.setVolume(eff, eff) }
                        }
                    ) {
                        Icon(
                            if (isMuted || volume <= 0.01f) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                            contentDescription = "Toggle volume mute",
                            tint = if (isMuted) RecordCrimson else TextSecondary
                        )
                    }
                    Slider(
                        value = if (isMuted) 0f else volume,
                        onValueChange = { v ->
                            volume = v
                            isMuted = v <= 0.01f
                            runCatching { mediaPlayerRef?.setVolume(v, v) }
                        },
                        valueRange = 0f..1f,
                        modifier = Modifier.weight(1f),
                        colors = SliderDefaults.colors(thumbColor = CyberCyan, activeTrackColor = CyberCyan)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { showTrimmer = !showTrimmer },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (showTrimmer) NeonViolet else CarbonSurfaceHigh
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        modifier = Modifier.testTag("player_toggle_trimmer_button")
                    ) {
                        Icon(Icons.Default.ContentCut, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Trim")
                    }
                }
            }

            // Lossless Non-Destructive Trimmer Card
            if (showTrimmer && !isFullscreen) {
                val startMs = (trimRange.start * totalDurationMs).toLong()
                val endMs = (trimRange.endInclusive * totalDurationMs).toLong()
                GamingCard(accentColor = NeonViolet) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Fast Lossless Trimmer", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                        StatusPill(text = "NON-DESTRUCTIVE", color = SignalGreen, leadingDot = false)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Exports a new trimmed clip directly to Movies/ZeroRecorder/ without modifying the original recording.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Start: ${formatMs(startMs)}", style = MaterialTheme.typography.labelMedium, color = CyberCyan)
                        Text("Clip: ${formatMs((endMs - startMs).coerceAtLeast(0L))}", style = MaterialTheme.typography.labelMedium, color = NeonViolet)
                        Text("End: ${formatMs(endMs)}", style = MaterialTheme.typography.labelMedium, color = CyberCyan)
                    }
                    RangeSlider(
                        value = trimRange,
                        onValueChange = { range ->
                            trimRange = range
                            videoViewRef?.seekTo((range.start * totalDurationMs).toInt())
                        },
                        valueRange = 0f..1f,
                        colors = SliderDefaults.colors(thumbColor = NeonViolet, activeTrackColor = NeonViolet),
                        modifier = Modifier.testTag("player_trim_range_slider")
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = { onTrim(video, startMs, endMs) },
                        enabled = !trimInProgress && (endMs - startMs) >= 500L,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("export_trimmed_video_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = NeonViolet)
                    ) {
                        if (trimInProgress) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Exporting Trimmed Clip...")
                        } else {
                            Icon(Icons.Default.ContentCut, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Export Trimmed Copy")
                        }
                    }

                    lastTrimResult?.let { result ->
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = (if (result.success) SignalGreen else RecordCrimson).copy(alpha = 0.14f),
                            border = BorderStroke(1.dp, if (result.success) SignalGreen else RecordCrimson)
                        ) {
                            Text(
                                result.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = TextPrimary,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    if (showRenameDialog) {
        RenameMediaDialog(
            currentName = video.displayName,
            title = "Rename Recording",
            onDismiss = { showRenameDialog = false },
            onConfirm = { newName ->
                onRename(video, newName)
                showRenameDialog = false
            }
        )
    }

    if (showDeleteDialog) {
        ConfirmDeleteDialog(
            itemName = video.displayName,
            onDismiss = { showDeleteDialog = false },
            onConfirm = {
                onDelete(video)
                showDeleteDialog = false
                onBack()
            }
        )
    }
}

private fun formatMs(ms: Long): String {
    val totalSec = (ms / 1000L).coerceAtLeast(0L)
    val mins = totalSec / 60L
    val secs = totalSec % 60L
    return "%02d:%02d".format(mins, secs)
}
