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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.layout.offset
import com.noven.ncrawler.ui.components.SolarArrows
import com.noven.ncrawler.ui.components.glassCard
import com.noven.ncrawler.viewmodel.SourceFolder
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
    // CHANGE (source folders): finished downloads now live in one folder per source.
    val folders by vm.sourceFolders.collectAsStateWithLifecycle()

    // Which source folder is open (null = the main list). Saveable so rotation keeps it.
    var openSourceId by rememberSaveable { mutableStateOf<String?>(null) }
    val openFolder = folders.firstOrNull { it.sourceId == openSourceId }
    // The last novel in an open folder was deleted -> the folder is gone; go back.
    // Only once data has loaded: the flows start empty, and a restored (rotated)
    // screen must not mistake "not loaded yet" for "folder deleted".
    LaunchedEffect(openFolder == null, openSourceId, allItems.isEmpty()) {
        if (openSourceId != null && openFolder == null && allItems.isNotEmpty()) openSourceId = null
    }
    BackHandler(enabled = openFolder != null) { openSourceId = null }

    // "See all" arrows keep the top of the screen neat when many are active.
    var showAllActive    by rememberSaveable { mutableStateOf(false) }
    var showAllAttention by rememberSaveable { mutableStateOf(false) }

    var pendingDelete by remember { mutableStateOf<DownloadItem?>(null) }

    // CHANGE (EPUB export): folder picker + export entry point. The
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
                    vm.exportEpub(slug)
                    Toast.makeText(context, "Building EPUB — progress is in the notification", Toast.LENGTH_SHORT).show()
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
            vm.exportEpub(slug)
            Toast.makeText(context, "Building EPUB — progress is in the notification", Toast.LENGTH_SHORT).show()
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
                        openFolder?.sourceName ?: "Downloads",
                        fontFamily = MontserratFamily,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize   = 24.sp
                    )
                },
                navigationIcon = {
                    if (openFolder != null) {
                        IconButton(onClick = { openSourceId = null }) {
                            Icon(SolarIcons.ArrowLeft, contentDescription = "Back to Downloads")
                        }
                    }
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
                if (openFolder != null) {
                    // ── Inside one source folder: every novel from that source ──
                    itemsIndexed(openFolder.items, key = { _, it -> it.novel.slug + "_folder" }) { i, entry ->
                        DownloadCard(
                            modifier     = Modifier.staggerIn(i),
                            item         = entry,
                            vm           = vm,
                            networkWait  = networkWait,
                            showSource   = false,   // the title bar already says which source
                            onClick      = { onNovelClick(entry.novel.slug) },
                            onPrimary    = null,
                            onExport     = { requestExport(entry.novel.slug) },
                            onPickFolder = requestPickFolder,
                            onDelete     = { pendingDelete = entry }
                        )
                    }
                } else {
                    if (activeDownloads.isNotEmpty()) {
                        item { SectionHeader("Downloading") }
                        // CHANGE (motion): one running index across the sections, so the
                        // cards stack in top to bottom as a single sequence (once).
                        val shown = if (showAllActive) activeDownloads else activeDownloads.take(PREVIEW_COUNT)
                        itemsIndexed(shown, key = { _, it -> it.novel.slug + "_active" }) { i, entry ->
                            DownloadCard(
                                modifier     = Modifier.staggerIn(i),
                                item         = entry,
                                vm           = vm,
                                networkWait  = networkWait,
                                onClick      = { onNovelClick(entry.novel.slug) },
                                onPrimary    = { vm.pause(entry.novel.slug) },
                                onExport     = { requestExport(entry.novel.slug) },
                                onPickFolder = requestPickFolder,
                                onDelete     = { pendingDelete = entry }
                            )
                        }
                        if (activeDownloads.size > PREVIEW_COUNT) {
                            item(key = "active_more") {
                                SeeAllRow(expanded = showAllActive, total = activeDownloads.size) {
                                    showAllActive = !showAllActive
                                }
                            }
                        }
                        item { Spacer(Modifier.height(8.dp)) }
                    }

                    if (needsAttention.isNotEmpty()) {
                        item { SectionHeader("Needs attention") }
                        val shown = if (showAllAttention) needsAttention else needsAttention.take(PREVIEW_COUNT)
                        itemsIndexed(shown, key = { _, it -> it.novel.slug + "_attention" }) { i, entry ->
                            DownloadCard(
                                modifier     = Modifier.staggerIn(i + activeDownloads.size),
                                item         = entry,
                                vm           = vm,
                                networkWait  = networkWait,
                                onClick      = { onNovelClick(entry.novel.slug) },
                                onPrimary    = { vm.resume(entry.novel.slug) },
                                onExport     = { requestExport(entry.novel.slug) },
                                onPickFolder = requestPickFolder,
                                onDelete     = { pendingDelete = entry }
                            )
                        }
                        if (needsAttention.size > PREVIEW_COUNT) {
                            item(key = "attention_more") {
                                SeeAllRow(expanded = showAllAttention, total = needsAttention.size) {
                                    showAllAttention = !showAllAttention
                                }
                            }
                        }
                        item { Spacer(Modifier.height(8.dp)) }
                    }

                    if (folders.isNotEmpty()) {
                        item { SectionHeader("Your novels") }
                        itemsIndexed(folders, key = { _, it -> "folder_" + it.sourceId }) { i, folder ->
                            SourceFolderCard(
                                modifier = Modifier.staggerIn(i + activeDownloads.size + needsAttention.size),
                                folder   = folder,
                                onClick  = { openSourceId = folder.sourceId }
                            )
                        }
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

// Small gold pill ("+3 new", "2 updated") — same gold as the reader bookmark.
@Composable
private fun GoldTag(text: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(com.noven.ncrawler.ui.components.BookmarkGold)
            .padding(horizontal = 6.dp, vertical = 1.dp)
    ) {
        Text(
            text,
            color      = Color(0xFF1B1405),
            fontFamily = MontserratFamily,
            fontWeight = FontWeight.ExtraBold,
            fontSize   = 10.sp
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

// How many cards the "Downloading" / "Needs attention" sections show before the
// "See all" arrow — keeps the top of the screen neat.
private const val PREVIEW_COUNT = 3

// Small translucent pill naming the source ("NovelFull"). Glass-style: a faint tint
// of the primary colour, no border.
@Composable
private fun SourceChip(name: String, modifier: Modifier = Modifier) {
    Text(
        name,
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f))
            .padding(horizontal = 8.dp, vertical = 2.dp),
        fontFamily = MontserratFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize   = 10.sp,
        color      = MaterialTheme.colorScheme.primary,
        maxLines   = 1,
        overflow   = TextOverflow.Ellipsis
    )
}

// "See all 7 >" / "Show less" row under a capped section.
@Composable
private fun SeeAllRow(expanded: Boolean, total: Int, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .pressable(onClick = onToggle, pressedScale = 0.98f)
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment     = Alignment.CenterVertically
    ) {
        Text(
            if (expanded) "Show less" else "See all $total",
            fontFamily = MontserratFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize   = 13.sp,
            color      = MaterialTheme.colorScheme.primary
        )
        Icon(
            SolarArrows.ChevronRight,
            contentDescription = null,
            tint     = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .size(16.dp)
                .graphicsLayer { rotationZ = if (expanded) -90f else 90f }
        )
    }
}

// Rectangle folder for one source: a back panel with a tab, up to 3 covers (the
// latest) fanned and peeking out above a frosted front panel, plus a blank
// "there is more" sliver when the source holds more than 3 novels.
@Composable
private fun SourceFolderCard(
    modifier: Modifier = Modifier,
    folder: SourceFolder,
    onClick: () -> Unit
) {
    val preview  = folder.items.take(3)
    val hasMore  = folder.items.size > 3
    val primary  = MaterialTheme.colorScheme.primary
    val coverW   = 60.dp
    val coverH   = 84.dp
    // (x offset, tilt) per slot; the blank sliver is the last slot.
    val slots    = listOf(18.dp to -7f, 70.dp to 1f, 122.dp to 7f, 172.dp to 12f)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(150.dp)
            .pressable(onClick = onClick, pressedScale = 0.98f)
    ) {
        // Folder tab + back panel
        Box(
            modifier = Modifier
                .padding(start = 8.dp)
                .width(96.dp)
                .height(30.dp)
                .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                .background(primary.copy(alpha = 0.26f))
        )
        Box(
            modifier = Modifier
                .padding(top = 22.dp)
                .fillMaxSize()
                .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 16.dp))
                .background(primary.copy(alpha = 0.20f))
        )

        // Gold dot on the folder tab when something inside has new chapters
        if (folder.updatedCount > 0) {
            Box(
                modifier = Modifier
                    .padding(start = 8.dp + 96.dp - 22.dp, top = 10.dp)
                    .size(10.dp)
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .background(com.noven.ncrawler.ui.components.BookmarkGold)
            )
        }

        // Peeking covers, tilted around their bottom edge so they fan out
        preview.forEachIndexed { i, entry ->
            val (x, tilt) = slots[i]
            Box(
                modifier = Modifier
                    .offset(x = x, y = 22.dp)
                    .size(coverW, coverH)
                    .graphicsLayer { rotationZ = tilt; transformOrigin = TransformOrigin(0.5f, 1f) }
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                CoverImage(
                    url                = entry.novel.coverUrl,
                    contentDescription = entry.novel.title,
                    modifier           = Modifier.fillMaxSize()
                )
            }
        }
        if (hasMore) {
            val (x, tilt) = slots[3]
            Box(
                modifier = Modifier
                    .offset(x = x, y = 22.dp)
                    .size(coverW, coverH)
                    .graphicsLayer { rotationZ = tilt; transformOrigin = TransformOrigin(0.5f, 1f) }
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f))
            )
        }

        // Frosted front panel with the source name
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(74.dp)
                .glassCard(RoundedCornerShape(16.dp))
                .padding(horizontal = 16.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    folder.sourceName,
                    fontFamily = MontserratFamily,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize   = 16.sp,
                    maxLines   = 1,
                    overflow   = TextOverflow.Ellipsis
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    Text(
                        "${folder.items.size} novel${if (folder.items.size == 1) "" else "s"}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (folder.updatedCount > 0) GoldTag("${folder.updatedCount} updated")
                }
            }
            Icon(
                SolarArrows.ChevronRight,
                contentDescription = "Open ${folder.sourceName}",
                tint     = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun DownloadCard(
    modifier: Modifier = Modifier,
    item: DownloadItem,
    vm: DownloadsViewModel,
    networkWait: NetworkWait,
    showSource: Boolean = true,
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
                    // CHANGE (source folders): which site this is downloading from,
                    // plus a gold tag when the novel has unread new chapters.
                    if (showSource || item.newCount > 0) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment     = Alignment.CenterVertically
                        ) {
                            if (showSource) SourceChip(item.sourceName)
                            if (item.newCount > 0) GoldTag("+${item.newCount} new")
                        }
                        Spacer(Modifier.height(4.dp))
                    }
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
                // CHANGE (EPUB export): only offered when there is
                // something on disk to export, and not while merely queued.
                if (progress.downloadedChapters > 0 && progress.status != DownloadStatus.QUEUED) {
                    TextButton(onClick = onExport) {
                        Icon(
                            SolarIcons.Document,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("Export EPUB")
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
