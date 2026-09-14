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

    val state       by vm.state.collectAsStateWithLifecycle()
    val downloading by vm.downloading.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
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
                novel         = s.novel,
                chapters      = s.chapters,
                downloading   = downloading,
                padding       = padding,
                onReadChapter = onReadChapter,
                onDownload    = { num -> vm.downloadChapter(slug, num) {} }
            )
        }
    }
}

@Composable
private fun DetailContent(
    novel: NovelEntity,
    chapters: List<ChapterLink>,
    downloading: Set<Int>,
    padding: PaddingValues,
    onReadChapter: (Int) -> Unit,
    onDownload: (Int) -> Unit
) {
    var synopsisExpanded by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = padding.calculateTopPadding(), bottom = 24.dp
        )
    ) {
        // Hero
        item {
            Box(Modifier.fillMaxWidth().height(320.dp)) {
                AsyncImage(
                    model              = novel.coverUrl,
                    contentDescription = novel.title,
                    contentScale       = ContentScale.Crop,
                    modifier           = Modifier.fillMaxSize()
                )
                Box(
                    Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.6f)
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, MaterialTheme.colorScheme.background)
                            )
                        )
                )
                Column(
                    Modifier.align(Alignment.BottomStart)
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Text(
                        novel.title,
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold, fontSize = 20.sp),
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment     = Alignment.CenterVertically
                    ) {
                        if (novel.status.isNotBlank()) StatusChip(novel.status)
                        if (novel.rating.isNotBlank()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Star, null,
                                    tint     = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(3.dp))
                                Text(novel.rating,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary)
                            }
                        }
                        if (chapters.isNotEmpty()) {
                            Text("${chapters.size} chapters",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }

        // Genres
        if (novel.genres.isNotBlank()) {
            item {
                Row(
                    Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    novel.genres.split(",").take(4).forEach { genre ->
                        if (genre.trim().isNotBlank()) GenreChip(genre.trim())
                    }
                }
            }
        }

        // Synopsis
        if (novel.synopsis.isNotBlank()) {
            item {
                Column(Modifier.padding(horizontal = 16.dp).animateContentSize()) {
                    Text(
                        novel.synopsis,
                        style    = MaterialTheme.typography.bodyMedium,
                        color    = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = if (synopsisExpanded) Int.MAX_VALUE else 4,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (synopsisExpanded) "Show less" else "Read more",
                        style    = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.SemiBold),
                        color    = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable { synopsisExpanded = !synopsisExpanded }
                    )
                }
                Spacer(Modifier.height(16.dp))
            }
        }

        // Chapter header
        item {
            Row(
                Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.AutoMirrored.Filled.MenuBook, null,
                    tint     = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    "Chapters (${chapters.size})",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold)
                )
            }
            HorizontalDivider(
                Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
            )
        }

        // Empty state
        if (chapters.isEmpty()) {
            item {
                Box(Modifier.fillMaxWidth().padding(32.dp), Alignment.Center) {
                    Text("No chapters found — try refreshing",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        // Chapter rows — use items() not itemsIndexed() to avoid type inference issue
        items(
            items = chapters,
            key   = { chapter -> chapter.num }
        ) { chapter ->
            ChapterRow(
                chapter       = chapter,
                isDownloading = chapter.num in downloading,
                onRead        = { onReadChapter(chapter.num) }
            )
            HorizontalDivider(
                Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
            )
        }
    }
}

@Composable
private fun ChapterRow(
    chapter: ChapterLink,
    isDownloading: Boolean,
    onRead: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onRead)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text     = chapter.title.ifBlank { "Chapter ${chapter.num}" },
            style    = MaterialTheme.typography.bodyMedium,
            color    = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (isDownloading) {
            CircularProgressIndicator(
                modifier    = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color       = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun StatusChip(status: String) {
    val isComplete = status.contains("Complete", ignoreCase = true)
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = if (isComplete) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceVariant
    ) {
        Text(
            status,
            style    = MaterialTheme.typography.labelSmall,
            color    = if (isComplete) MaterialTheme.colorScheme.onPrimaryContainer
                       else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
        )
    }
}

@Composable
private fun GenreChip(genre: String) {
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
        Text(
            genre,
            style    = MaterialTheme.typography.labelSmall,
            color    = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}
