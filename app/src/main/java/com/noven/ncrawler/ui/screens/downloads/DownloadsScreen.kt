package com.noven.ncrawler.ui.screens.downloads

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.noven.ncrawler.data.db.DownloadProgress
import com.noven.ncrawler.data.db.DownloadStatus
import com.noven.ncrawler.viewmodel.DownloadItem
import com.noven.ncrawler.viewmodel.DownloadsViewModel
import kotlin.math.roundToInt

// CHANGE (Downloads overhaul): now on its own DownloadsViewModel instead of
// the shared LibraryViewModel — see DownloadsViewModel.kt for why.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(
    onNovelClick: (slug: String) -> Unit,
    vm: DownloadsViewModel = viewModel()
) {
    val allItems by vm.downloadItems.collectAsStateWithLifecycle()

    // CHANGE (reliability fix): true whenever download-only-on-Wi-Fi is on
    // and the device currently isn't on an unmetered network. Any QUEUED
    // item in that state is not actually progressing — WorkManager is just
    // holding it until the network constraint is met, with no error and no
    // feedback otherwise. This makes that visible instead of leaving the
    // user staring at "Queued..." indefinitely.
    val blockedByNetwork by vm.downloadsBlockedByNetwork.collectAsStateWithLifecycle()

    // CHANGE: three sections instead of two. ERROR/PAUSED items need a user
    // action to continue, so they're grouped apart from a healthy
    // in-progress download instead of being invisible among "Downloading".
    val activeDownloads = allItems.filter {
        it.progress.status == DownloadStatus.DOWNLOADING ||
        it.progress.status == DownloadStatus.QUEUED
    }
    val needsAttention = allItems.filter {
        it.progress.status == DownloadStatus.ERROR ||
        it.progress.status == DownloadStatus.PAUSED
    }
    val completedDownloads = allItems.filter {
        it.progress.status == DownloadStatus.COMPLETE
    }

    var pendingDelete by remember { mutableStateOf<DownloadItem?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Downloads",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold
                        )
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        if (allItems.isEmpty()) {
            Box(
                modifier         = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        Icons.Default.DownloadForOffline,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint     = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    )
                    Text(
                        "No downloads yet",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "Tap 'Download All' on any novel to read offline",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier       = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (activeDownloads.isNotEmpty()) {
                    item { SectionHeader("Downloading") }
                    items(activeDownloads, key = { it.novel.slug + "_active" }) { entry ->
                        DownloadCard(
                            item             = entry,
                            vm               = vm,
                            blockedByNetwork = blockedByNetwork,
                            onClick          = { onNovelClick(entry.novel.slug) },
                            onPrimary        = { vm.pause(entry.novel.slug) },
                            onDelete         = { pendingDelete = entry }
                        )
                    }
                    item { Spacer(Modifier.height(8.dp)) }
                }

                if (needsAttention.isNotEmpty()) {
                    item { SectionHeader("Needs attention") }
                    items(needsAttention, key = { it.novel.slug + "_attention" }) { entry ->
                        DownloadCard(
                            item             = entry,
                            vm               = vm,
                            blockedByNetwork = blockedByNetwork,
                            onClick          = { onNovelClick(entry.novel.slug) },
                            onPrimary        = { vm.resume(entry.novel.slug) },
                            onDelete         = { pendingDelete = entry }
                        )
                    }
                    item { Spacer(Modifier.height(8.dp)) }
                }

                if (completedDownloads.isNotEmpty()) {
                    item { SectionHeader("Downloaded") }
                    items(completedDownloads, key = { it.novel.slug + "_done" }) { entry ->
                        DownloadCard(
                            item             = entry,
                            vm               = vm,
                            blockedByNetwork = blockedByNetwork,
                            onClick          = { onNovelClick(entry.novel.slug) },
                            onPrimary        = null,
                            onDelete         = { pendingDelete = entry }
                        )
                    }
                }
            }
        }
    }

    // CHANGE: confirm before freeing a novel's downloaded chapters — makes
    // explicit that Library membership survives the delete.
    pendingDelete?.let { entry ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title            = { Text("Delete download?") },
            text = {
                Text(
                    "This removes the downloaded chapters for \"${entry.novel.title}\" to free up space. " +
                    "It stays in your Library and you can download it again anytime."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.delete(entry.novel.slug)
                    pendingDelete = null
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
        modifier = Modifier.padding(bottom = 4.dp)
    )
}

@Composable
private fun DownloadCard(
    item: DownloadItem,
    vm: DownloadsViewModel,
    blockedByNetwork: Boolean,
    onClick: () -> Unit,
    onPrimary: (() -> Unit)?,
    onDelete: () -> Unit
) {
    val progress = item.progress
    val downloadFraction = if (progress.totalChapters > 0)
        progress.downloadedChapters.toFloat() / progress.totalChapters else 0f

    val animatedProgress by animateFloatAsState(
        targetValue   = downloadFraction,
        animationSpec = tween(400),
        label         = "dlProgress"
    )

    // CHANGE: size recomputed only when the slug changes or a new chapter
    // actually lands (downloadedChapters ticks up) — a single indexed SUM
    // query, cheap, but no reason to repeat it on every recomposition on a
    // low-end device.
    val sizeBytes by produceState<Long?>(
        initialValue = null,
        key1 = item.novel.slug,
        key2 = progress.downloadedChapters
    ) {
        value = vm.sizeBytesFor(item.novel.slug)
    }

    // CHANGE (reliability fix): only relevant for a still-QUEUED item —
    // once it's actually DOWNLOADING the network clearly wasn't the
    // problem.
    val waitingForWifi = blockedByNetwork && progress.status == DownloadStatus.QUEUED

    Card(
        modifier  = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape     = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors    = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment     = Alignment.CenterVertically
            ) {
                // Cover
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    AsyncImage(
                        model              = item.novel.coverUrl,
                        contentDescription = item.novel.title,
                        contentScale       = ContentScale.Crop,
                        modifier           = Modifier.fillMaxSize()
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        item.novel.title,
                        style    = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.SemiBold
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(6.dp))

                    LinearProgressIndicator(
                        progress          = { animatedProgress },
                        modifier          = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color             = when (progress.status) {
                            DownloadStatus.ERROR -> MaterialTheme.colorScheme.error
                            else                 -> MaterialTheme.colorScheme.primary
                        },
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                    Spacer(Modifier.height(4.dp))

                    Text(
                        statusLine(progress, sizeBytes, waitingForWifi),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (progress.status == DownloadStatus.ERROR)
                            MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Status icon
                StatusIcon(progress.status, waitingForWifi)
            }

            // CHANGE (Downloads overhaul): explicit per-item actions —
            // previously the whole card just navigated to the novel with no
            // way to manage the download itself.
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (onPrimary != null) {
                    TextButton(onClick = onPrimary) {
                        Text(primaryLabel(progress.status))
                    }
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.DeleteOutline,
                        contentDescription = "Delete download",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

private fun primaryLabel(status: DownloadStatus): String = when (status) {
    DownloadStatus.DOWNLOADING -> "Pause"
    DownloadStatus.QUEUED      -> "Cancel"
    DownloadStatus.PAUSED      -> "Resume"
    DownloadStatus.ERROR       -> "Retry"
    DownloadStatus.COMPLETE    -> ""
}

private fun statusLine(progress: DownloadProgress, sizeBytes: Long?, waitingForWifi: Boolean): String {
    val sizeSuffix = sizeBytes?.takeIf { it > 0 }?.let { " · ${formatBytes(it)}" } ?: ""
    return when (progress.status) {
        DownloadStatus.COMPLETE    -> "Complete — ${progress.totalChapters} chapters$sizeSuffix"
        DownloadStatus.DOWNLOADING -> "${progress.downloadedChapters} / ${progress.totalChapters} chapters$sizeSuffix"
        // CHANGE (reliability fix): was always "Queued..." even when the
        // real reason it's not moving is the wifi-only constraint with no
        // Wi-Fi currently available.
        DownloadStatus.QUEUED      -> if (waitingForWifi) "Waiting for Wi-Fi…" else "Queued..."
        DownloadStatus.PAUSED      -> "Paused — ${progress.downloadedChapters}/${progress.totalChapters}$sizeSuffix"
        DownloadStatus.ERROR       ->
            "${(progress.totalChapters - progress.downloadedChapters).coerceAtLeast(0)} chapter(s) failed — tap Retry"
    }
}

private fun formatBytes(bytes: Long): String {
    val kb = bytes / 1024.0
    if (kb < 1024) return "${kb.roundToInt()} KB"
    return "%.1f MB".format(kb / 1024.0)
}

@Composable
private fun StatusIcon(status: DownloadStatus, waitingForWifi: Boolean = false) {
    // CHANGE (reliability fix): a Wi-Fi-blocked QUEUED item gets its own
    // icon instead of the generic clock, so it reads as "waiting on
    // something external" rather than "about to start any second".
    if (status == DownloadStatus.QUEUED && waitingForWifi) {
        Icon(
            Icons.Default.WifiOff,
            contentDescription = "Waiting for Wi-Fi",
            tint     = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
        return
    }
    when (status) {
        DownloadStatus.DOWNLOADING -> {
            CircularProgressIndicator(
                modifier    = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color       = MaterialTheme.colorScheme.primary
            )
        }
        DownloadStatus.COMPLETE -> {
            Icon(
                Icons.Default.CheckCircle,
                contentDescription = "Complete",
                tint     = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }
        DownloadStatus.QUEUED -> {
            Icon(
                Icons.Default.Schedule,
                contentDescription = "Queued",
                tint     = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
        DownloadStatus.PAUSED -> {
            Icon(
                Icons.Default.PauseCircle,
                contentDescription = "Paused",
                tint     = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
        DownloadStatus.ERROR -> {
            Icon(
                Icons.Default.ErrorOutline,
                contentDescription = "Error",
                tint     = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
