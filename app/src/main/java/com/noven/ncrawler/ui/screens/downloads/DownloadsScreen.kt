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
import com.noven.ncrawler.data.db.DownloadStatus
import com.noven.ncrawler.viewmodel.LibraryItem
import com.noven.ncrawler.viewmodel.LibraryViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(
    onNovelClick: (slug: String) -> Unit,
    vm: LibraryViewModel = viewModel()
) {
    val allItems by vm.libraryItems.collectAsStateWithLifecycle()

    // Split into active downloads and completed
    val activeDownloads = allItems.filter {
        it.downloadProgress?.status == DownloadStatus.DOWNLOADING ||
        it.downloadProgress?.status == DownloadStatus.QUEUED
    }
    val completedDownloads = allItems.filter {
        it.downloadProgress?.status == DownloadStatus.COMPLETE
    }

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
        if (activeDownloads.isEmpty() && completedDownloads.isEmpty()) {
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
                // Active downloads section
                if (activeDownloads.isNotEmpty()) {
                    item {
                        Text(
                            "Downloading",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.SemiBold
                            ),
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                    }
                    items(activeDownloads, key = { it.novel.slug + "_active" }) { item ->
                        DownloadCard(item = item, onClick = { onNovelClick(item.novel.slug) })
                    }
                    item { Spacer(Modifier.height(8.dp)) }
                }

                // Completed downloads section
                if (completedDownloads.isNotEmpty()) {
                    item {
                        Text(
                            "Downloaded",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.SemiBold
                            ),
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                    }
                    items(completedDownloads, key = { it.novel.slug + "_done" }) { item ->
                        DownloadCard(item = item, onClick = { onNovelClick(item.novel.slug) })
                    }
                }
            }
        }
    }
}

@Composable
private fun DownloadCard(item: LibraryItem, onClick: () -> Unit) {
    val download = item.downloadProgress ?: return
    val progress = if (download.totalChapters > 0)
        download.downloadedChapters.toFloat() / download.totalChapters else 0f

    val animatedProgress by animateFloatAsState(
        targetValue   = progress,
        animationSpec = tween(400),
        label         = "dlProgress"
    )

    Card(
        modifier  = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape     = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors    = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier              = Modifier.padding(12.dp),
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
                    color             = when (download.status) {
                        DownloadStatus.COMPLETE    -> MaterialTheme.colorScheme.primary
                        DownloadStatus.DOWNLOADING -> MaterialTheme.colorScheme.primary
                        else                       -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
                Spacer(Modifier.height(4.dp))

                Text(
                    when (download.status) {
                        DownloadStatus.COMPLETE    -> "Complete — ${download.totalChapters} chapters"
                        DownloadStatus.DOWNLOADING -> "${download.downloadedChapters} / ${download.totalChapters} chapters"
                        DownloadStatus.QUEUED      -> "Queued..."
                        DownloadStatus.PAUSED      -> "Paused — ${download.downloadedChapters}/${download.totalChapters}"
                        DownloadStatus.ERROR       -> "Error — tap to retry"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Status icon
            when (download.status) {
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
                else -> {}
            }
        }
    }
}
