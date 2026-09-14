package com.noven.ncrawler.ui.screens.detail

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.noven.ncrawler.data.db.DownloadStatus
import com.noven.ncrawler.data.db.NovelEntity
import com.noven.ncrawler.data.scraper.ChapterLink
import com.noven.ncrawler.viewmodel.DetailUiState
import com.noven.ncrawler.viewmodel.DetailViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    slug: String,
    onBack: () -> Unit,
    onReadChapter: (chapterNum: Int) -> Unit,
    vm: DetailViewModel = viewModel()
) {
    LaunchedEffect(slug) { vm.load(slug) }

    val state           by vm.state.collectAsStateWithLifecycle()
    val downloadProgress by vm.downloadProgress.collectAsStateWithLifecycle()
    val updateMessage   by vm.updateMessage.collectAsStateWithLifecycle()

    // Snackbar for update messages
    val snackbarHost = remember { SnackbarHostState() }
    LaunchedEffect(updateMessage) {
        updateMessage?.let {
            snackbarHost.showSnackbar(it)
            vm.clearUpdateMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHost) },
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    // Check for updates
                    IconButton(onClick = vm::checkForUpdates) {
                        Icon(Icons.Default.Refresh, "Check for updates")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { padding ->
        when (val s = state) {
            is DetailUiState.Loading -> Box(
                Modifier.fillMaxSize().padding(padding), Alignment.Center
            ) { CircularProgressIndicator(color = MaterialTheme.colorScheme.primary) }

            is DetailUiState.Error -> Box(
                Modifier.fillMaxSize().padding(padding), Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.WifiOff, null, Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(12.dp))
                    Text(s.message, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = { vm.load(slug) }) { Text("Retry") }
                }
            }

            is DetailUiState.Success -> DetailContent(
                novel            = s.novel,
                chapters         = s.chapters,
                lastReadChapter  = s.lastReadChapter,
                downloadProgress = downloadProgress,
                padding          = padding,
                onReadChapter    = onReadChapter,
                onDownloadAll    = vm::downloadAll,
                onCancelDownload = vm::cancelDownload
            )
        }
    }
}

@Composable
private fun DetailContent(
    novel: NovelEntity,
    chapters: List<ChapterLink>,
    lastReadChapter: Int?,
    downloadProgress: com.noven.ncrawler.data.db.DownloadProgress?,
    padding: PaddingValues,
    onReadChapter: (Int) -> Unit,
    onDownloadAll: () -> Unit,
    onCancelDownload: () -> Unit
) {
    var synopsisExpanded by remember { mutableStateOf(false) }
    val isDownloading = downloadProgress?.status == DownloadStatus.DOWNLOADING ||
                        downloadProgress?.status == DownloadStatus.QUEUED
    val isComplete    = downloadProgress?.status == DownloadStatus.COMPLETE

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = padding.calculateTopPadding(), bottom = 100.dp
        )
    ) {
        // ── Hero ──────────────────────────────────────────────────────────
        item {
            Box(Modifier.fillMaxWidth().height(340.dp)) {
                AsyncImage(
                    model = novel.coverUrl, contentDescription = novel.title,
                    contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()
                )
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.verticalGradient(
                            colorStops = arrayOf(
                                0f   to Color.Transparent,
                                0.45f to Color.Black.copy(alpha = 0.1f),
                                1f   to Color.Black.copy(alpha = 0.9f)
                            )
                        )
                    )
                )
                Column(
                    Modifier.align(Alignment.BottomStart)
                        .padding(horizontal = 16.dp, vertical = 16.dp)
                ) {
                    Text(
                        novel.title,
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold, fontSize = 22.sp),
                        color = Color.White
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (novel.status.isNotBlank()) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = Color.White.copy(alpha = 0.2f)
                            ) {
                                Text(novel.status,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                            }
                        }
                        if (novel.rating.isNotBlank()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Star, null,
                                    tint = Color(0xFFFFCA28), modifier = Modifier.size(12.dp))
                                Spacer(Modifier.width(2.dp))
                                Text(novel.rating, style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFFFFCA28))
                            }
                        }
                        if (chapters.isNotEmpty()) {
                            Text("${chapters.size} chapters",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.8f))
                        }
                    }
                }
            }
        }

        // ── Action buttons ────────────────────────────────────────────────
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Read / Continue button
                Button(
                    onClick = {
                        val chapter = lastReadChapter ?: chapters.lastOrNull()?.num ?: 1
                        onReadChapter(chapter)
                    },
                    modifier = Modifier.weight(1f),
                    shape    = RoundedCornerShape(10.dp)
                ) {
                    Icon(
                        if (lastReadChapter != null) Icons.Default.PlayArrow
                        else Icons.Default.MenuBook,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        if (lastReadChapter != null) "Continue Ch.$lastReadChapter"
                        else "Start Reading"
                    )
                }

                // Download button
                FilledTonalIconButton(
                    onClick = if (isDownloading) onCancelDownload else onDownloadAll,
                    modifier = Modifier.size(48.dp)
                ) {
                    if (isDownloading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        Icon(
                            if (isComplete) Icons.Default.DownloadDone
                            else Icons.Default.Download,
                            contentDescription = if (isComplete) "Downloaded" else "Download all",
                            tint = if (isComplete) MaterialTheme.colorScheme.primary
                                   else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Download progress bar
            if (isDownloading && downloadProgress != null) {
                val p = if (downloadProgress.totalChapters > 0)
                    downloadProgress.downloadedChapters.toFloat() / downloadProgress.totalChapters
                else 0f
                Column(Modifier.padding(horizontal = 16.dp)) {
                    LinearProgressIndicator(
                        progress = { p },
                        modifier = Modifier.fillMaxWidth().height(3.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color    = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "${downloadProgress.downloadedChapters}/${downloadProgress.totalChapters} chapters downloaded",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(8.dp))
            }
        }

        // ── Genres ────────────────────────────────────────────────────────
        if (novel.genres.isNotBlank()) {
            item {
                Row(
                    Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    novel.genres.split(",").take(4).forEach { genre ->
                        if (genre.trim().isNotBlank()) {
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer
                            ) {
                                Text(genre.trim(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
                            }
                        }
                    }
                }
            }
        }

        // ── Synopsis ──────────────────────────────────────────────────────
        if (novel.synopsis.isNotBlank()) {
            item {
                Spacer(Modifier.height(12.dp))
                Column(
                    Modifier.padding(horizontal = 16.dp).animateContentSize()
                ) {
                    Text(novel.synopsis,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = if (synopsisExpanded) Int.MAX_VALUE else 3,
                        overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (synopsisExpanded) "Show less" else "More",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable { synopsisExpanded = !synopsisExpanded }
                    )
                }
                Spacer(Modifier.height(16.dp))
            }
        }

        // ── Chapter header ────────────────────────────────────────────────
        item {
            Row(
                Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.AutoMirrored.Filled.MenuBook, null,
                    tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Chapters (${chapters.size})",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold))
            }
            HorizontalDivider(
                Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
            )
        }

        if (chapters.isEmpty()) {
            item {
                Box(Modifier.fillMaxWidth().padding(32.dp), Alignment.Center) {
                    Text("No chapters found",
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        // ── Chapter rows ──────────────────────────────────────────────────
        items(chapters, key = { it.num }) { chapter ->
            val isLastRead = chapter.num == lastReadChapter
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onReadChapter(chapter.num) }
                    .background(
                        if (isLastRead) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                        else Color.Transparent
                    )
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        chapter.title.ifBlank { "Chapter ${chapter.num}" },
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = if (isLastRead) FontWeight.SemiBold else FontWeight.Normal
                        ),
                        color = if (isLastRead) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (isLastRead) {
                        Text("Last read",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary)
                    }
                }
                Icon(Icons.Default.ChevronRight, null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp))
            }
            HorizontalDivider(
                Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)
            )
        }
    }
}
