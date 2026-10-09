package com.noven.ncrawler.ui.screens.downloads

import com.noven.ncrawler.ui.components.SolarIcons
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.noven.ncrawler.data.db.DownloadStatus
import com.noven.ncrawler.ui.components.staggerIn
import com.noven.ncrawler.ui.theme.MontserratFamily
import com.noven.ncrawler.viewmodel.DownloadItem
import com.noven.ncrawler.viewmodel.DownloadsViewModel

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
