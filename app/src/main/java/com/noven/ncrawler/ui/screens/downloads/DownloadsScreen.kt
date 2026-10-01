package com.noven.ncrawler.ui.screens.downloads

import com.noven.ncrawler.ui.components.SolarIcons
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.noven.ncrawler.data.db.DownloadProgress
import com.noven.ncrawler.data.db.DownloadStatus
import com.noven.ncrawler.ui.components.CoverImage
import com.noven.ncrawler.ui.components.errorShake
import com.noven.ncrawler.ui.components.pressable
import com.noven.ncrawler.ui.components.staggerIn
import com.noven.ncrawler.ui.theme.MontserratFamily
import com.noven.ncrawler.viewmodel.DownloadItem
import com.noven.ncrawler.viewmodel.DownloadsViewModel
import com.noven.ncrawler.viewmodel.NetworkWait
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

    // CHANGE (reliability fix, generalised for the network choice): what a
    // QUEUED download is waiting for (Wi-Fi, mobile data, or any connection).
    // WorkManager holds such a download with no error and no feedback, so this
    // makes it visible instead of leaving the user staring at "Queued".
    val networkWait by vm.networkWait.collectAsStateWithLifecycle()

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

    // CHANGE (TXT export, restored): folder picker + export entry point. The
    // first Export tap (no folder saved yet) opens the system folder picker,
    // remembers the choice, then starts the export for the novel that was
    // tapped. The folder icon on each card re-opens the picker any time.
    val context = LocalContext.current
    var pendingExportSlug by remember { mutableStateOf<String?>(null) }
    val folderPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        val slug = pendingExportSlug
        pendingExportSlug = null
        if (uri != null) {
            if (vm.onExportFolderChosen(uri)) {
                if (slug != null) {
                    vm.exportTxt(slug)
                    Toast.makeText(context, "Exporting TXT files — progress is in the notification", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Export folder saved", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(context, "Couldn't use that folder — pick another", Toast.LENGTH_LONG).show()
            }
        }
    }
    val requestExport: (String) -> Unit = { slug ->
        if (vm.hasExportFolder()) {
            vm.exportTxt(slug)
            Toast.makeText(context, "Exporting TXT files — progress is in the notification", Toast.LENGTH_SHORT).show()
        } else {
            pendingExportSlug = slug
            folderPicker.launch(null)
        }
    }
    val requestPickFolder: () -> Unit = {
        pendingExportSlug = null
        folderPicker.launch(null)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Downloads",
                        fontFamily = MontserratFamily,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize   = 24.sp
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
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
                        SolarIcons.Download,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp).staggerIn(0, distance = 10.dp),
                        tint     = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    )
                    Text(
                        "No downloads yet",
                        modifier = Modifier.staggerIn(1, distance = 10.dp, stepMs = 70),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "Download a novel to read offline",
                        modifier = Modifier.staggerIn(2, distance = 10.dp, stepMs = 70),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier       = Modifier.fillMaxSize().padding(padding),
                // 120dp bottom so the last item clears the floating nav (was 16dp).
                contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 120.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (activeDownloads.isNotEmpty()) {
                    item { SectionHeader("Downloading") }
                    // CHANGE (motion): one running index across all three sections, so
                    // the cards stack in top to bottom as a single sequence (once).
                    itemsIndexed(activeDownloads, key = { _, it -> it.novel.slug + "_active" }) { i, entry ->
                        DownloadCard(
                            modifier         = Modifier.staggerIn(i),
                            item             = entry,
                            vm               = vm,
                            networkWait      = networkWait,
                            onClick          = { onNovelClick(entry.novel.slug) },
                            onPrimary        = { vm.pause(entry.novel.slug) },
                            onExport         = { requestExport(entry.novel.slug) },
                            onPickFolder     = requestPickFolder,
                            onDelete         = { pendingDelete = entry }
                        )
                    }
                    item { Spacer(Modifier.height(8.dp)) }
                }

                if (needsAttention.isNotEmpty()) {
                    item { SectionHeader("Needs attention") }
                    itemsIndexed(needsAttention, key = { _, it -> it.novel.slug + "_attention" }) { i, entry ->
                        DownloadCard(
                            modifier         = Modifier.staggerIn(i + activeDownloads.size),
                            item             = entry,
                            vm               = vm,
                            networkWait      = networkWait,
                            onClick          = { onNovelClick(entry.novel.slug) },
                            onPrimary        = { vm.resume(entry.novel.slug) },
                            onExport         = { requestExport(entry.novel.slug) },
                            onPickFolder     = requestPickFolder,
                            onDelete         = { pendingDelete = entry }
                        )
                    }
                    item { Spacer(Modifier.height(8.dp)) }
                }

                if (completedDownloads.isNotEmpty()) {
                    item { SectionHeader("Downloaded") }
                    itemsIndexed(completedDownloads, key = { _, it -> it.novel.slug + "_done" }) { i, entry ->
                        DownloadCard(
                            modifier         = Modifier.staggerIn(i + activeDownloads.size + needsAttention.size),
                            item             = entry,
                            vm               = vm,
                            networkWait      = networkWait,
                            onClick          = { onNovelClick(entry.novel.slug) },
                            onPrimary        = null,
                            onExport         = { requestExport(entry.novel.slug) },
                            onPickFolder     = requestPickFolder,
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
                Text("Removes \"${entry.novel.title}\" chapters from this phone. It stays in your Library.")
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
    modifier: Modifier = Modifier,
    item: DownloadItem,
    vm: DownloadsViewModel,
    networkWait: NetworkWait,
    onClick: () -> Unit,
    onPrimary: (() -> Unit)?,
    onExport: () -> Unit,
    onPickFolder: () -> Unit,
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

    // CHANGE (download fix): SUM(LENGTH(content)) reads every stored chapter
    // body. Re-running it after EVERY chapter (every ~2 s) on a large novel is a
    // lot of disk work for a low-end phone while a download is also writing.
    // Now it refreshes every 25 chapters, and whenever the status changes (so
    // the final number is always exact).
    val sizeBytes by produceState<Long?>(
        initialValue = null,
        key1 = item.novel.slug,
        key2 = progress.downloadedChapters / 25,
        key3 = progress.status
    ) {
        value = vm.sizeBytesFor(item.novel.slug)
    }

    // CHANGE (reliability fix): only relevant for a still-QUEUED item —
    // once it's actually DOWNLOADING the network clearly wasn't the
    // problem.
    val wait = if (progress.status == DownloadStatus.QUEUED) networkWait else NetworkWait.NONE

    // CHANGE (motion): press-in feedback (was a ripple) + a quick shake when this
    // download fails while it's on screen.
    Card(
        modifier  = modifier
            .fillMaxWidth()
            .errorShake(trigger = progress.status == DownloadStatus.ERROR, onEnter = false)
            .pressable(onClick = onClick, pressedScale = 0.98f),
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
                    CoverImage(
                        url                = item.novel.coverUrl,
                        contentDescription = item.novel.title,
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
                        statusLine(progress, sizeBytes, wait),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (progress.status == DownloadStatus.ERROR)
                            MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Status icon
                StatusIcon(progress.status, wait)
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
                // CHANGE (TXT export, restored): only offered when there is
                // something on disk to export, and not while merely queued.
                if (progress.downloadedChapters > 0 && progress.status != DownloadStatus.QUEUED) {
                    TextButton(onClick = onExport) {
                        Icon(
                            SolarIcons.Document,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("Export TXT")
                    }
                    IconButton(onClick = onPickFolder) {
                        Icon(
                            SolarIcons.FolderOpen,
                            contentDescription = "Choose export folder",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        SolarIcons.TrashBin,
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

private fun statusLine(progress: DownloadProgress, sizeBytes: Long?, wait: NetworkWait): String {
    val sizeSuffix = sizeBytes?.takeIf { it > 0 }?.let { " · ${formatBytes(it)}" } ?: ""
    return when (progress.status) {
        DownloadStatus.COMPLETE    -> "${progress.totalChapters} chapters$sizeSuffix"
        DownloadStatus.DOWNLOADING -> "${progress.downloadedChapters} / ${progress.totalChapters}$sizeSuffix"
        // CHANGE (reliability fix): was always "Queued..." even when the
        // real reason it's not moving is the network constraint (Wi-Fi only /
        // mobile data only / no connection) — now names what it's waiting for.
        DownloadStatus.QUEUED      -> if (wait != NetworkWait.NONE) wait.label else "Queued"
        DownloadStatus.PAUSED      -> "Paused · ${progress.downloadedChapters}/${progress.totalChapters}"
        DownloadStatus.ERROR       ->
            "${(progress.totalChapters - progress.downloadedChapters).coerceAtLeast(0)} failed"
    }
}

private fun formatBytes(bytes: Long): String {
    val kb = bytes / 1024.0
    if (kb < 1024) return "${kb.roundToInt()} KB"
    return "%.1f MB".format(kb / 1024.0)
}

// CHANGE (motion): the icon cross-fades (180ms) when the status changes — a
// download finishing or failing used to swap instantly and was easy to miss.
@Composable
private fun StatusIcon(status: DownloadStatus, wait: NetworkWait = NetworkWait.NONE) {
    // A network-blocked QUEUED item gets its own icon instead of the generic
    // clock, so it reads as "waiting on something external", not "about to start".
    Crossfade(
        targetState   = status to (if (status == DownloadStatus.QUEUED) wait else NetworkWait.NONE),
        animationSpec = tween(180)
    ) { (st, w) ->
        if (w != NetworkWait.NONE) {
            Icon(
                SolarIcons.Wifi,
                contentDescription = w.label,
                tint     = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        } else when (st) {
            DownloadStatus.DOWNLOADING -> {
                CircularProgressIndicator(
                    modifier    = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color       = MaterialTheme.colorScheme.primary
                )
            }
            DownloadStatus.COMPLETE -> {
                Icon(
                    SolarIcons.CheckCircleBold,
                    contentDescription = "Complete",
                    tint     = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
            DownloadStatus.QUEUED -> {
                Icon(
                    SolarIcons.ClockCircle,
                    contentDescription = "Queued",
                    tint     = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
            DownloadStatus.PAUSED -> {
                Icon(
                    SolarIcons.PauseCircle,
                    contentDescription = "Paused",
                    tint     = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
            DownloadStatus.ERROR -> {
                Icon(
                    SolarIcons.DangerCircle,
                    contentDescription = "Error",
                    tint     = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
