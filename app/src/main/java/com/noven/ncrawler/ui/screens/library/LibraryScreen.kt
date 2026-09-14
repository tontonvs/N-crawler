package com.noven.ncrawler.ui.screens.library

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
import com.noven.ncrawler.viewmodel.LibraryItem
import com.noven.ncrawler.viewmodel.LibraryViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    onNovelClick: (slug: String) -> Unit,
    onContinueReading: (slug: String, chapter: Int) -> Unit,
    vm: LibraryViewModel = viewModel()
) {
    val items by vm.libraryItems.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Library",
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
        if (items.isEmpty()) {
            LibraryEmptyState(modifier = Modifier.padding(padding))
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(items, key = { it.novel.slug }) { item ->
                    LibraryCard(
                        item     = item,
                        onClick  = { onNovelClick(item.novel.slug) },
                        onContinue = {
                            val chapter = item.readingProgress?.lastChapterNum ?: 1
                            onContinueReading(item.novel.slug, chapter)
                        },
                        onRemove = { vm.removeFromLibrary(item.novel.slug) }
                    )
                }
            }
        }
    }
}

@Composable
private fun LibraryCard(
    item: LibraryItem,
    onClick: () -> Unit,
    onContinue: () -> Unit,
    onRemove: () -> Unit
) {
    val novel    = item.novel
    val reading  = item.readingProgress
    val download = item.downloadProgress

    val readProgress = if (novel.chapterCount > 0 && reading != null)
        (reading.lastChapterNum.toFloat() / novel.chapterCount).coerceIn(0f, 1f)
    else 0f

    val downloadProgress = if (download != null && download.totalChapters > 0)
        (download.downloadedChapters.toFloat() / download.totalChapters).coerceIn(0f, 1f)
    else 0f

    // Animate progress bar
    val animatedReadProgress by animateFloatAsState(
        targetValue  = readProgress,
        animationSpec = tween(600, easing = EaseOutCubic),
        label        = "readProgress"
    )

    Card(
        modifier  = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape     = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        colors    = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Cover
            Box(
                modifier = Modifier
                    .width(72.dp)
                    .height(100.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                AsyncImage(
                    model              = novel.coverUrl,
                    contentDescription = novel.title,
                    contentScale       = ContentScale.Crop,
                    modifier           = Modifier.fillMaxSize()
                )
            }

            // Info
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    novel.title,
                    style    = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.SemiBold
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                if (novel.genres.isNotBlank()) {
                    Text(
                        novel.genres.split(",").take(2).joinToString(" · "),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(Modifier.height(4.dp))

                // Reading progress
                if (reading != null) {
                    Text(
                        "Chapter ${reading.lastChapterNum}" +
                            if (novel.chapterCount > 0) " / ${novel.chapterCount}" else "",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    LinearProgressIndicator(
                        progress          = { animatedReadProgress },
                        modifier          = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color             = MaterialTheme.colorScheme.primary,
                        trackColor        = MaterialTheme.colorScheme.surfaceVariant
                    )
                }

                // Download status
                if (download != null) {
                    Spacer(Modifier.height(2.dp))
                    when (download.status) {
                        DownloadStatus.DOWNLOADING -> {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier    = Modifier.size(10.dp),
                                    strokeWidth = 1.5.dp,
                                    color       = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    "${download.downloadedChapters}/${download.totalChapters} downloaded",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        DownloadStatus.COMPLETE -> {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    Icons.Default.DownloadDone,
                                    contentDescription = null,
                                    modifier = Modifier.size(12.dp),
                                    tint     = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    "All chapters downloaded",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        DownloadStatus.PAUSED -> {
                            Text(
                                "Download paused",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        else -> {}
                    }
                }

                Spacer(Modifier.height(6.dp))

                // Action buttons
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(
                        onClick   = onContinue,
                        modifier  = Modifier.weight(1f),
                        shape     = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            if (reading != null) Icons.Default.PlayArrow else Icons.Default.MenuBook,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            if (reading != null) "Continue" else "Start",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LibraryEmptyState(modifier: Modifier = Modifier) {
    Box(
        modifier          = modifier.fillMaxSize(),
        contentAlignment  = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                Icons.Default.CollectionsBookmark,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint     = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            )
            Text(
                "Your library is empty",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "Tap the bookmark icon on any novel to save it here",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}
