package com.noven.ncrawler.ui.screens.detail

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarHalf
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.palette.graphics.Palette
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.noven.ncrawler.viewmodel.NovelDetailViewModel

// ─── Colour helpers ───────────────────────────────────────────────────────────

/** Convert an ARGB int from Palette into a Compose Color. */
private fun Int.toComposeColor(): Color = Color(this)

// ─── Rating bar ───────────────────────────────────────────────────────────────

@Composable
private fun StarRating(
    rating: Float,
    maxStars: Int = 5,
    starColor: Color = Color(0xFFFFB400),
    emptyColor: Color = Color.White.copy(alpha = 0.35f),
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        for (i in 1..maxStars) {
            val starIcon = when {
                rating >= i -> Icons.Filled.Star
                rating >= i - 0.5f -> Icons.Filled.StarHalf
                else -> Icons.Outlined.StarOutline
            }
            val tint = if (rating >= i - 0.5f) starColor else emptyColor
            Icon(
                imageVector = starIcon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(18.dp),
            )
        }
        Spacer(Modifier.width(6.dp))
        Text(
            text = String.format("%.1f", rating),
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

// ─── Glass play button ────────────────────────────────────────────────────────

@Composable
private fun GlassPlayButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = Color.White,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(68.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.20f))
            .border(1.dp, Color.White.copy(alpha = 0.45f), CircleShape)
            .clickable(onClick = onClick),
    ) {
        // subtle inner glow — 1px inner highlight line
        Box(
            modifier = Modifier
                .size(66.dp)
                .clip(CircleShape)
                .border(
                    width = 1.dp,
                    color = Color.White.copy(alpha = 0.15f),
                    shape = CircleShape,
                ),
        )
        Icon(
            imageVector = Icons.Filled.PlayArrow,
            contentDescription = "Read",
            tint = tint,
            modifier = Modifier.size(32.dp),
        )
    }
}

// ─── Chapter row ─────────────────────────────────────────────────────────────

@Composable
private fun ChapterRow(
    chapterTitle: String,
    accentColor: Color,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(accentColor.copy(alpha = 0.8f)),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = chapterTitle,
            color = Color.White.copy(alpha = 0.90f),
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Icon(
            imageVector = Icons.Filled.KeyboardArrowDown,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.45f),
            modifier = Modifier.size(16.dp),
        )
    }
    HorizontalDivider(color = Color.White.copy(alpha = 0.08f), thickness = 0.5.dp)
}

// ─── Meta chip (status, genre, latest) ───────────────────────────────────────

@Composable
private fun MetaChip(label: String, value: String, accentColor: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = label,
            color = accentColor.copy(alpha = 0.80f),
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 1.sp,
            textAlign = TextAlign.Center,
        )
    }
}

// ─── Main screen ──────────────────────────────────────────────────────────────

@Composable
fun NovelDetailScreen(
    novelId: String,
    onBack: () -> Unit,
    onChapterClick: (String) -> Unit,
    viewModel: NovelDetailViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(novelId) { viewModel.loadDetail(novelId) }

    // Derived palette colours (fallback = deep blue-black)
    val dominantColor by viewModel.dominantColor.collectAsState()
    val vibrantColor by viewModel.vibrantColor.collectAsState()

    val bgTop = dominantColor ?: Color(0xFF050A1A)
    val bgBottom = Color(0xFF000000)
    val accent = vibrantColor ?: Color(0xFF4FC3F7)

    var showMore by remember { mutableStateOf(false) }
    val PREVIEW_CHAPTER_COUNT = 10

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(bgTop, bgBottom),
                    startY = 0f,
                    endY = Float.POSITIVE_INFINITY,
                )
            ),
    ) {
        // ── Full-screen cover art with heavy scrim ────────────────────────────
        if (uiState.coverUrl != null) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(uiState.coverUrl)
                    .crossfade(600)
                    .listener(
                        onSuccess = { _, result ->
                            viewModel.extractPalette(result.drawable)
                        }
                    )
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .drawWithContent {
                        drawContent()
                        // Heavy bottom-to-top scrim so text is always readable
                        drawRect(
                            Brush.verticalGradient(
                                0f to Color.Black.copy(alpha = 0.15f),
                                0.35f to Color.Black.copy(alpha = 0.55f),
                                0.65f to Color.Black.copy(alpha = 0.82f),
                                1f to Color.Black.copy(alpha = 0.97f),
                            )
                        )
                    },
            )
        }

        // ── Scrollable content ────────────────────────────────────────────────
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {

            // Back button
            item {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White,
                    )
                }
            }

            // Cover art spacer — pushes details below the fold on screen
            item { Spacer(Modifier.height(200.dp)) }

            // ── Authors ─────────────────────────────────────────────────────
            item {
                Text(
                    text = uiState.authors.ifBlank { "Unknown Author" }.uppercase(),
                    color = accent,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 2.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                )
            }

            item { Spacer(Modifier.height(8.dp)) }

            // ── Novel title ─────────────────────────────────────────────────
            item {
                Text(
                    text = uiState.title.ifBlank { "Loading…" },
                    color = Color.White,
                    fontSize = 36.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.5.sp,
                    lineHeight = 40.sp,
                    textAlign = TextAlign.Center,
                    style = TextStyle(
                        shadow = Shadow(
                            color = Color.Black.copy(alpha = 0.45f),
                            offset = Offset(0f, 4f),
                            blurRadius = 12f,
                        )
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                )
            }

            item { Spacer(Modifier.height(16.dp)) }

            // ── Meta row: status | genre | latest chapter ────────────────────
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MetaChip(
                        label = "STATUS",
                        value = uiState.status.ifBlank { "—" },
                        accentColor = accent,
                    )

                    Box(
                        modifier = Modifier
                            .height(28.dp)
                            .width(1.dp)
                            .background(Color.White.copy(alpha = 0.2f)),
                    )

                    MetaChip(
                        label = "GENRE",
                        value = uiState.genre.ifBlank { "—" },
                        accentColor = accent,
                    )

                    Box(
                        modifier = Modifier
                            .height(28.dp)
                            .width(1.dp)
                            .background(Color.White.copy(alpha = 0.2f)),
                    )

                    MetaChip(
                        label = "LATEST",
                        value = uiState.latestChapter.ifBlank { "—" },
                        accentColor = accent,
                    )
                }
            }

            item { Spacer(Modifier.height(20.dp)) }

            // ── Star rating ──────────────────────────────────────────────────
            item {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    StarRating(
                        rating = uiState.rating,
                        starColor = Color(0xFFFFB400),
                    )
                }
            }

            item { Spacer(Modifier.height(28.dp)) }

            // ── Glass play/read button ───────────────────────────────────────
            item {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    GlassPlayButton(
                        onClick = {
                            uiState.chapters.firstOrNull()?.let { onChapterClick(it.url) }
                        },
                        tint = Color.White,
                    )
                }
            }

            item { Spacer(Modifier.height(28.dp)) }

            // ── "Show More" toggle — summary + chapter list ──────────────────
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showMore = !showMore }
                        .padding(vertical = 10.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = if (showMore) "Show Less" else "Show More",
                        color = Color.White.copy(alpha = 0.80f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    Spacer(Modifier.width(6.dp))
                    Icon(
                        imageVector = if (showMore) Icons.Filled.KeyboardArrowUp
                        else Icons.Filled.KeyboardArrowDown,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.80f),
                        modifier = Modifier.size(16.dp),
                    )
                }
            }

            // ── Summary ──────────────────────────────────────────────────────
            item {
                AnimatedVisibility(
                    visible = showMore,
                    enter = fadeIn() + expandVertically(
                        animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
                    ),
                    exit = fadeOut() + shrinkVertically(
                        animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
                    ),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp),
                    ) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "Summary",
                            color = accent,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 1.5.sp,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = uiState.summary.ifBlank { "No summary available." },
                            color = Color.White.copy(alpha = 0.82f),
                            fontSize = 13.sp,
                            lineHeight = 20.sp,
                        )
                        Spacer(Modifier.height(20.dp))
                    }
                }
            }

            // ── Chapter list (inside Show More) ──────────────────────────────
            item {
                AnimatedVisibility(
                    visible = showMore,
                    enter = fadeIn() + expandVertically(
                        animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
                    ),
                    exit = fadeOut() + shrinkVertically(
                        animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
                    ),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                            .background(Color.Black.copy(alpha = 0.45f))
                            .border(
                                width = 1.dp,
                                color = Color.White.copy(alpha = 0.08f),
                                shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                            ),
                    ) {
                        // Section header
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "Chapters",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = "${uiState.chapters.size} total",
                                color = accent.copy(alpha = 0.75f),
                                fontSize = 11.sp,
                            )
                        }
                        HorizontalDivider(color = Color.White.copy(alpha = 0.10f), thickness = 0.5.dp)

                        val visibleChapters = uiState.chapters.take(PREVIEW_CHAPTER_COUNT)
                        val hasMore = uiState.chapters.size > PREVIEW_CHAPTER_COUNT

                        visibleChapters.forEachIndexed { index, chapter ->
                            ChapterRow(
                                chapterTitle = chapter.title,
                                accentColor = accent,
                                onClick = { onChapterClick(chapter.url) },
                            )
                        }

                        // "See More" with fade gradient mask
                        if (hasMore) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(72.dp)
                                    .drawWithContent {
                                        drawContent()
                                        drawRect(
                                            Brush.verticalGradient(
                                                0f to Color.Transparent,
                                                1f to Color.Black.copy(alpha = 0.90f),
                                            )
                                        )
                                    },
                                contentAlignment = Alignment.BottomCenter,
                            ) {
                                Text(
                                    text = "See More Chapters",
                                    color = accent,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier
                                        .clickable { /* TODO: navigate to full chapter list */ }
                                        .padding(bottom = 12.dp),
                                )
                            }
                        }
                    }
                }
            }

            // Loading state
            if (uiState.isLoading) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(40.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(color = accent)
                    }
                }
            }

            item { Spacer(Modifier.height(40.dp)) }
        }
    }
}
