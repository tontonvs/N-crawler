package com.noven.ncrawler.ui.screens.detail

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.*
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
import androidx.compose.ui.unit.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.noven.ncrawler.data.scraper.ChapterLink
import com.noven.ncrawler.data.db.NovelEntity
import com.noven.ncrawler.ui.theme.AccentBlue
import com.noven.ncrawler.ui.theme.StarGold
import com.noven.ncrawler.viewmodel.DetailUiState
import com.noven.ncrawler.viewmodel.DetailViewModel

// ── Dark glass tokens ─────────────────────────────────────────────────────────
private val DarkGlassFill   = Color(0x1AFFFFFF)  // 10% white
private val DarkGlassBorder = Color(0x33FFFFFF)  // 20% white hairline
private val DarkGlassFillMd = Color(0x26FFFFFF)  // 15% white

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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        when (val s = state) {
            is DetailUiState.Loading -> DetailSkeleton()

            is DetailUiState.Error -> Box(
                Modifier.fillMaxSize(),
                Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.WifiOff,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint     = MaterialTheme.colorScheme.error
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(s.message, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = { vm.load(slug) },
                        shape   = RoundedCornerShape(12.dp),
                        colors  = ButtonDefaults.buttonColors(containerColor = AccentBlue)
                    ) { Text("Retry") }
                }
            }

            is DetailUiState.Success -> DetailContent(
                novel         = s.novel,
                chapters      = s.chapters,
                downloading   = downloading,
                slug          = slug,
                onReadChapter = onReadChapter,
                onDownload    = { num -> vm.downloadChapter(slug, num) {} }
            )
        }

        // ── Floating back button — always on top of hero ──────────────────
        Box(
            modifier = Modifier
                .statusBarsPadding()
                .padding(12.dp)
                .size(38.dp)
                .clip(CircleShape)
                .background(DarkGlassFill)
                .border(1.dp, DarkGlassBorder, CircleShape)
                .clickable(onClick = onBack)
                .align(Alignment.TopStart),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint     = Color.White,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

// ── Detail Content ────────────────────────────────────────────────────────────
@Composable
private fun DetailContent(
    novel: NovelEntity,
    chapters: List<ChapterLink>,
    downloading: Set<Int>,
    slug: String,
    onReadChapter: (Int) -> Unit,
    onDownload: (Int) -> Unit
) {
    var synopsisExpanded by remember { mutableStateOf(false) }

    LazyColumn(
        modifier       = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 120.dp)
    ) {

        // ── Hero — full-bleed cover + deep scrim ──────────────────────────
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(420.dp)
            ) {
                AsyncImage(
                    model              = novel.coverUrl,
                    contentDescription = novel.title,
                    contentScale       = ContentScale.Crop,
                    modifier           = Modifier.fillMaxSize()
                )

                // Scrim: lighter top (back button), heavy bottom (text/CTAs)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colorStops = arrayOf(
                                    0f    to Color.Black.copy(alpha = 0.40f),
                                    0.38f to Color.Black.copy(alpha = 0.05f),
                                    0.60f to Color.Black.copy(alpha = 0.40f),
                                    1f    to Color.Black.copy(alpha = 0.97f)
                                )
                            )
                        )
                )

                // Bottom content
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(horizontal = 16.dp, vertical = 20.dp)
                ) {
                    // Meta row — status pill + rating + chapter count
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment     = Alignment.CenterVertically
                    ) {
                        if (novel.status.isNotBlank()) {
                            GlassPill(novel.status)
                        }
                        if (novel.rating.isNotBlank()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Star,
                                    contentDescription = null,
                                    tint     = StarGold,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(Modifier.width(3.dp))
                                Text(
                                    novel.rating,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = StarGold
                                )
                            }
                        }
                        if (chapters.isNotEmpty()) {
                            Text(
                                "${chapters.size} ch.",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.65f)
                            )
                        }
                    }

                    Spacer(Modifier.height(6.dp))

                    // Title
                    Text(
                        novel.title,
                        style = MaterialTheme.typography.displaySmall.copy(
                            fontWeight    = FontWeight.ExtraBold,
                            fontSize      = 28.sp,
                            lineHeight    = 33.sp,
                            letterSpacing = (-0.5).sp
                        ),
                        color    = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(Modifier.height(14.dp))

                    // Primary CTA pill
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50.dp))
                            .background(AccentBlue)
                            .clickable {
                                val firstChapter = chapters.lastOrNull()?.num ?: 1
                                onReadChapter(firstChapter)
                            }
                            .padding(horizontal = 20.dp, vertical = 10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.AutoMirrored.Filled.MenuBook,
                                contentDescription = null,
                                tint     = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "Start Reading",
                                style = MaterialTheme.typography.labelLarge,
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }

        // ── Genre chips ───────────────────────────────────────────────────
        if (novel.genres.isNotBlank()) {
            item {
                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    novel.genres.split(",").take(5).forEach { genre ->
                        if (genre.trim().isNotBlank()) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50.dp))
                                    .background(DarkGlassFill)
                                    .border(1.dp, DarkGlassBorder, RoundedCornerShape(50.dp))
                                    .padding(horizontal = 12.dp, vertical = 5.dp)
                            ) {
                                Text(
                                    genre.trim(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        // ── Synopsis ──────────────────────────────────────────────────────
        if (novel.synopsis.isNotBlank()) {
            item {
                Spacer(Modifier.height(16.dp))
                Column(
                    Modifier
                        .padding(horizontal = 16.dp)
                        .animateContentSize(animationSpec = tween(220))
                ) {
                    Text(
                        novel.synopsis,
                        style    = MaterialTheme.typography.bodyMedium.copy(lineHeight = 22.sp),
                        color    = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = if (synopsisExpanded) Int.MAX_VALUE else 3,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(5.dp))
                    Text(
                        if (synopsisExpanded) "Show less" else "Show more",
                        style    = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.SemiBold
                        ),
                        color    = AccentBlue,
                        modifier = Modifier.clickable { synopsisExpanded = !synopsisExpanded }
                    )
                }
            }
        }

        // ── Chapter list header ───────────────────────────────────────────
        item {
            Spacer(Modifier.height(24.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Text(
                    "Chapters",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight    = FontWeight.ExtraBold,
                        letterSpacing = (-0.2).sp
                    ),
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    "${chapters.size} total",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(8.dp))
            HorizontalDivider(
                Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
            )
        }

        if (chapters.isEmpty()) {
            item {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    Alignment.Center
                ) {
                    Text(
                        "No chapters found",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        // ── Chapter rows ──────────────────────────────────────────────────
        items(chapters, key = { it.num }) { chapter ->
            val isDownloading = chapter.num in downloading

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onReadChapter(chapter.num) }
                    .padding(horizontal = 16.dp, vertical = 13.dp),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Left: chapter badge + title
                Row(
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier              = Modifier.weight(1f)
                ) {
                    // Chapter number badge
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(DarkGlassFill)
                            .border(1.dp, DarkGlassBorder, RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "${chapter.num}",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize   = 10.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Text(
                        chapter.title.ifBlank { "Chapter ${chapter.num}" },
                        style    = MaterialTheme.typography.bodyMedium,
                        color    = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Right: download icon or spinner
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .clickable(enabled = !isDownloading) { onDownload(chapter.num) },
                    contentAlignment = Alignment.Center
                ) {
                    if (isDownloading) {
                        CircularProgressIndicator(
                            modifier    = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color       = AccentBlue
                        )
                    } else {
                        Icon(
                            Icons.Default.Download,
                            contentDescription = "Download Ch.${chapter.num}",
                            tint     = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            HorizontalDivider(
                Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.07f)
            )
        }
    }
}

// ── Glass Pill ────────────────────────────────────────────────────────────────
@Composable
private fun GlassPill(text: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(DarkGlassFillMd)
            .border(1.dp, DarkGlassBorder, RoundedCornerShape(6.dp))
            .padding(horizontal = 7.dp, vertical = 3.dp)
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.85f)
        )
    }
}

// ── Detail Skeleton ───────────────────────────────────────────────────────────
@Composable
private fun DetailSkeleton() {
    val shimmer = MaterialTheme.colorScheme.surfaceVariant

    Column(modifier = Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(420.dp)
                .background(shimmer)
        )
        Spacer(Modifier.height(16.dp))

        Row(
            Modifier.padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            repeat(3) {
                Box(
                    Modifier
                        .size(64.dp, 26.dp)
                        .clip(RoundedCornerShape(50.dp))
                        .background(shimmer)
                )
            }
        }
        Spacer(Modifier.height(16.dp))

        Column(
            Modifier.padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            repeat(3) {
                Box(
                    Modifier
                        .fillMaxWidth(if (it == 2) 0.6f else 1f)
                        .height(12.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(shimmer)
                )
            }
        }
        Spacer(Modifier.height(24.dp))

        repeat(6) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 13.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Box(
                    Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(shimmer)
                )
                Box(
                    Modifier
                        .weight(1f)
                        .height(13.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(shimmer)
                )
            }
            HorizontalDivider(
                Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.07f)
            )
        }
    }
}
