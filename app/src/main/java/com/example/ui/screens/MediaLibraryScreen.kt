package com.example.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.data.MediaStoreRepository
import com.example.data.RecordedVideoItem
import com.example.data.ScreenshotItem
import com.example.ui.components.ConfirmDeleteDialog
import com.example.ui.components.GamingCard
import com.example.ui.components.RenameMediaDialog
import com.example.ui.components.StatusPill
import com.example.ui.theme.CarbonBorder
import com.example.ui.theme.CarbonSurface
import com.example.ui.theme.CarbonSurfaceHigh
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.NeonViolet
import com.example.ui.theme.ObsidianVoid
import com.example.ui.theme.RecordCrimson
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MediaLibraryScreen(
    videos: List<RecordedVideoItem>,
    screenshots: List<ScreenshotItem>,
    mediaRepo: MediaStoreRepository,
    initialTab: Int = 0,
    onRefresh: () -> Unit,
    onOpenVideoPlayer: (RecordedVideoItem) -> Unit,
    onTakeScreenshot: () -> Unit,
    onShareVideo: (RecordedVideoItem) -> Unit,
    onRenameVideo: (RecordedVideoItem, String) -> Unit,
    onDeleteVideo: (RecordedVideoItem) -> Unit,
    onShareScreenshot: (ScreenshotItem) -> Unit,
    onRenameScreenshot: (ScreenshotItem, String) -> Unit,
    onDeleteScreenshot: (ScreenshotItem) -> Unit
) {
    var selectedTab by remember(initialTab) { mutableIntStateOf(initialTab) }
    var renameVideoTarget by remember { mutableStateOf<RecordedVideoItem?>(null) }
    var deleteVideoTarget by remember { mutableStateOf<RecordedVideoItem?>(null) }
    var infoVideoTarget by remember { mutableStateOf<RecordedVideoItem?>(null) }

    var previewScreenshot by remember { mutableStateOf<ScreenshotItem?>(null) }
    var renameShotTarget by remember { mutableStateOf<ScreenshotItem?>(null) }
    var deleteShotTarget by remember { mutableStateOf<ScreenshotItem?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianVoid),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 720.dp)
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        if (selectedTab == 0) "Video Library" else "Screenshot Gallery",
                        style = MaterialTheme.typography.headlineMedium,
                        color = TextPrimary
                    )
                    Text(
                        if (selectedTab == 0) "Saved in Movies/ZeroRecorder/" else "Saved in Pictures/ZeroRecorder/",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
                Row {
                    if (selectedTab == 1) {
                        IconButton(
                            onClick = onTakeScreenshot,
                            modifier = Modifier.testTag("gallery_take_screenshot_button")
                        ) {
                            Icon(Icons.Default.CameraAlt, contentDescription = "Take screenshot", tint = CyberCyan)
                        }
                    }
                    IconButton(
                        onClick = onRefresh,
                        modifier = Modifier.testTag("refresh_media_library_button")
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh gallery", tint = ElectricBlue)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                    modifier = Modifier.testTag("library_tab_videos"),
                    colors = SegmentedButtonDefaults.colors(
                        activeContainerColor = ElectricBlue,
                        activeContentColor = Color.White,
                        inactiveContainerColor = CarbonSurface,
                        inactiveContentColor = TextSecondary
                    )
                ) {
                    Text("Videos (${videos.size})")
                }
                SegmentedButton(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                    modifier = Modifier.testTag("library_tab_screenshots"),
                    colors = SegmentedButtonDefaults.colors(
                        activeContainerColor = NeonViolet,
                        activeContentColor = Color.White,
                        inactiveContainerColor = CarbonSurface,
                        inactiveContentColor = TextSecondary
                    )
                ) {
                    Text("Screenshots (${screenshots.size})")
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (selectedTab == 0) {
                if (videos.isEmpty()) {
                    EmptyGalleryPlaceholder(
                        icon = Icons.Default.Movie,
                        title = "No Gameplay Recordings Yet",
                        subtitle = "Recorded clips automatically save to your phone's Gallery under Movies/ZeroRecorder/ with full thumbnails and audio tracks."
                    )
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(bottom = 24.dp)
                    ) {
                        items(videos, key = { it.id }) { item ->
                            VideoLibraryCard(
                                item = item,
                                mediaRepo = mediaRepo,
                                onPlay = { onOpenVideoPlayer(item) },
                                onShare = { onShareVideo(item) },
                                onRename = { renameVideoTarget = item },
                                onInfo = { infoVideoTarget = item },
                                onDelete = { deleteVideoTarget = item }
                            )
                        }
                    }
                }
            } else {
                if (screenshots.isEmpty()) {
                    EmptyGalleryPlaceholder(
                        icon = Icons.Default.PhotoLibrary,
                        title = "No Screenshots Captured Yet",
                        subtitle = "Take high-resolution screenshots during gameplay or directly from the Home Dashboard. They save automatically to Pictures/ZeroRecorder/.",
                        actionLabel = "Capture Screenshot Now",
                        onAction = onTakeScreenshot
                    )
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 156.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(bottom = 24.dp)
                    ) {
                        items(screenshots, key = { it.id }) { shot ->
                            ScreenshotGridCard(
                                item = shot,
                                onClick = { previewScreenshot = shot },
                                onShare = { onShareScreenshot(shot) },
                                onDelete = { deleteShotTarget = shot }
                            )
                        }
                    }
                }
            }
        }
    }

    // Dialogs for Videos
    renameVideoTarget?.let { target ->
        RenameMediaDialog(
            currentName = target.displayName,
            title = "Rename Video",
            onDismiss = { renameVideoTarget = null },
            onConfirm = { newName ->
                onRenameVideo(target, newName)
                renameVideoTarget = null
            }
        )
    }

    deleteVideoTarget?.let { target ->
        ConfirmDeleteDialog(
            itemName = target.displayName,
            onDismiss = { deleteVideoTarget = null },
            onConfirm = {
                onDeleteVideo(target)
                deleteVideoTarget = null
            }
        )
    }

    infoVideoTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { infoVideoTarget = null },
            containerColor = CarbonSurface,
            title = { Text("Recording Details", color = TextPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    InfoLine("File", target.displayName)
                    InfoLine("Folder", target.relativePath)
                    InfoLine("Duration", target.formattedDuration)
                    InfoLine("Resolution", target.resolutionLabel)
                    InfoLine("Frame Rate", if (target.fps > 0) "${target.fps} FPS" else "Variable")
                    InfoLine("File Size", target.formattedSize)
                    InfoLine("Recorded", formatEpochSec(target.dateAddedSec))
                }
            },
            confirmButton = {
                TextButton(onClick = { infoVideoTarget = null }) { Text("Close") }
            }
        )
    }

    // Fullscreen Screenshot Preview Viewer
    previewScreenshot?.let { shot ->
        Dialog(
            onDismissRequest = { previewScreenshot = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = ObsidianVoid
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                shot.displayName,
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                "${shot.resolutionLabel} • ${shot.formattedSize} • ${formatEpochSec(shot.dateAddedSec)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                        IconButton(onClick = { previewScreenshot = null }) {
                            Icon(Icons.Default.Close, contentDescription = "Close preview", tint = TextPrimary)
                        }
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        AsyncImage(
                            model = shot.uri,
                            contentDescription = shot.displayName,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        TextButton(onClick = { onShareScreenshot(shot) }) {
                            Icon(Icons.Default.Share, contentDescription = null, tint = ElectricBlue)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Share")
                        }
                        TextButton(onClick = { renameShotTarget = shot }) {
                            Icon(Icons.Default.Edit, contentDescription = null, tint = CyberCyan)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Rename")
                        }
                        TextButton(onClick = {
                            deleteShotTarget = shot
                            previewScreenshot = null
                        }) {
                            Icon(Icons.Default.Delete, contentDescription = null, tint = RecordCrimson)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Delete", color = RecordCrimson)
                        }
                    }
                }
            }
        }
    }

    renameShotTarget?.let { target ->
        RenameMediaDialog(
            currentName = target.displayName,
            title = "Rename Screenshot",
            onDismiss = { renameShotTarget = null },
            onConfirm = { newName ->
                onRenameScreenshot(target, newName)
                renameShotTarget = null
            }
        )
    }

    deleteShotTarget?.let { target ->
        ConfirmDeleteDialog(
            itemName = target.displayName,
            onDismiss = { deleteShotTarget = null },
            onConfirm = {
                onDeleteScreenshot(target)
                deleteShotTarget = null
            }
        )
    }
}

@Composable
private fun VideoLibraryCard(
    item: RecordedVideoItem,
    mediaRepo: MediaStoreRepository,
    onPlay: () -> Unit,
    onShare: () -> Unit,
    onRename: () -> Unit,
    onInfo: () -> Unit,
    onDelete: () -> Unit
) {
    var thumbnail by remember(item.uri) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(item.uri) {
        thumbnail = mediaRepo.loadVideoThumbnail(item.uri)
    }

    GamingCard(
        modifier = Modifier
            .clickable(onClick = onPlay)
            .testTag("video_item_${item.id}")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(126.dp)
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(CarbonSurfaceHigh),
                contentAlignment = Alignment.Center
            ) {
                if (thumbnail != null) {
                    Image(
                        bitmap = thumbnail!!.asImageBitmap(),
                        contentDescription = item.displayName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        Icons.Default.Videocam,
                        contentDescription = null,
                        tint = TextMuted,
                        modifier = Modifier.size(28.dp)
                    )
                }
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(ObsidianVoid.copy(alpha = 0.72f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = "Play video",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(4.dp),
                    shape = RoundedCornerShape(6.dp),
                    color = ObsidianVoid.copy(alpha = 0.85f)
                ) {
                    Text(
                        item.formattedDuration,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    StatusPill(text = item.resolutionLabel, color = ElectricBlue, leadingDot = false)
                    if (item.fps > 0) {
                        StatusPill(text = "${item.fps} FPS", color = CyberCyan, leadingDot = false)
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${item.formattedSize} • ${formatEpochSec(item.dateAddedSec)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            IconButton(onClick = onInfo, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Default.Info, contentDescription = "Video details", tint = TextSecondary, modifier = Modifier.size(18.dp))
            }
            IconButton(onClick = onRename, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Default.Edit, contentDescription = "Rename video", tint = CyberCyan, modifier = Modifier.size(18.dp))
            }
            IconButton(onClick = onShare, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Default.Share, contentDescription = "Share video", tint = ElectricBlue, modifier = Modifier.size(18.dp))
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Default.Delete, contentDescription = "Delete video", tint = RecordCrimson, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun ScreenshotGridCard(
    item: ScreenshotItem,
    onClick: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("screenshot_item_${item.id}"),
        shape = RoundedCornerShape(16.dp),
        color = CarbonSurface,
        border = BorderStroke(1.dp, CarbonBorder)
    ) {
        Column {
            AsyncImage(
                model = item.uri,
                contentDescription = item.displayName,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 10f)
                    .background(CarbonSurfaceHigh)
            )
            Column(modifier = Modifier.padding(10.dp)) {
                Text(
                    item.displayName,
                    style = MaterialTheme.typography.labelLarge,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        item.formattedSize,
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                    Row {
                        IconButton(onClick = onShare, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Share, contentDescription = "Share screenshot", tint = ElectricBlue, modifier = Modifier.size(16.dp))
                        }
                        IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete screenshot", tint = RecordCrimson, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyGalleryPlaceholder(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Surface(
                shape = CircleShape,
                color = CarbonSurfaceHigh,
                modifier = Modifier.size(72.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = ElectricBlue, modifier = Modifier.size(34.dp))
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
            Text(title, style = MaterialTheme.typography.titleLarge, color = TextPrimary)
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )
            if (actionLabel != null && onAction != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = onAction,
                    colors = ButtonDefaults.buttonColors(containerColor = NeonViolet)
                ) {
                    Text(actionLabel)
                }
            }
        }
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = TextSecondary)
        Text(value, style = MaterialTheme.typography.bodySmall, color = TextPrimary)
    }
}

private fun formatEpochSec(epochSec: Long): String {
    if (epochSec <= 0L) return ""
    return SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()).format(Date(epochSec * 1000L))
}
