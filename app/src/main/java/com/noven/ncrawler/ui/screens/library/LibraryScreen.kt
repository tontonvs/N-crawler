package com.noven.ncrawler.ui.screens.library

import com.noven.ncrawler.ui.components.SolarIcons
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.noven.ncrawler.data.db.DownloadStatus
import com.noven.ncrawler.ui.components.CoverImage
import com.noven.ncrawler.ui.components.errorShake
import com.noven.ncrawler.ui.components.glassCard
import com.noven.ncrawler.ui.components.pressable
import com.noven.ncrawler.ui.components.staggerIn
import com.noven.ncrawler.ui.theme.MontserratFamily
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

    // FIX: header used the stock Material bar + default font; now matches the
    // Discover/Settings look (Montserrat, background-coloured bar).
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Library",
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
        if (items.isEmpty()) {
            LibraryEmptyState(modifier = Modifier.padding(padding))
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                // 120dp bottom so the last card clears the floating nav (was 16dp).
                contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 120.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // CHANGE (motion): cards stack in (first screenful, once).
                itemsIndexed(items, key = { _, it -> it.novel.slug }) { index, item ->
                    LibraryCard(
                        modifier = Modifier.staggerIn(index),
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
    modifier: Modifier = Modifier,
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

    // CHANGE (motion): press-in feedback (was a plain ripple) + a quick shake if
    // a download on this card fails while you're looking at it.
    Box(
        modifier = modifier
            .fillMaxWidth()
            .errorShake(trigger = download?.status == DownloadStatus.ERROR, onEnter = false)
            .pressable(onClick = onClick, pressedScale = 0.98f)
            .glassCard()
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
                CoverImage(
                    url                = novel.coverUrl,
                    contentDescription = novel.title,
                    modifier           = Modifier.fillMaxSize()
                )
            }

            // Info
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(verticalAlignment = Alignment.Top) {
                    Text(
                        novel.title,
                        modifier   = Modifier.weight(1f).padding(top = 4.dp),
                        fontFamily = MontserratFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize   = 14.sp,
                        maxLines   = 2,
                        overflow   = TextOverflow.Ellipsis
                    )
                    // onRemove used to be wired up but never shown — no way out of the library.
                    IconButton(onClick = onRemove, modifier = Modifier.size(32.dp)) {
                        Icon(
                            SolarIcons.BookmarkBold,
                            contentDescription = "Remove from library",
                            modifier = Modifier.size(20.dp),
                            tint     = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                if (novel.genres.isNotBlank()) {
                    Text(
                        novel.genres.split(",").take(2).map { it.trim() }.joinToString(" · "),
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
                                    "${download.downloadedChapters}/${download.totalChapters}",
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
                                    SolarIcons.CheckCircleBold,
                                    contentDescription = null,
                                    modifier = Modifier.size(12.dp),
                                    tint     = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    "Downloaded",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        DownloadStatus.PAUSED -> {
                            Text(
                                "Paused",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        // These two used to render nothing at all.
                        DownloadStatus.QUEUED -> {
                            Text(
                                "Queued",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        DownloadStatus.ERROR -> {
                            Text(
                                "Failed — retry in Downloads",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error
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
                            if (reading != null) SolarIcons.PlayBold else SolarIcons.Books,
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
                SolarIcons.Library,
                contentDescription = null,
                modifier = Modifier.size(64.dp).staggerIn(0, distance = 10.dp),
                tint     = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            )
            Text(
                "Nothing saved yet",
                modifier = Modifier.staggerIn(1, distance = 10.dp, stepMs = 70),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "Tap the bookmark on a novel to save it",
                modifier = Modifier.staggerIn(2, distance = 10.dp, stepMs = 70),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}
