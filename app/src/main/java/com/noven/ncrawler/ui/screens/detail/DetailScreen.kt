package com.noven.ncrawler.ui.screens.detail

import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarHalf
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.palette.graphics.Palette
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.noven.ncrawler.data.db.NovelEntity
import com.noven.ncrawler.data.scraper.ChapterLink
import com.noven.ncrawler.viewmodel.DetailUiState
import com.noven.ncrawler.viewmodel.DetailViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// ── Colour helpers ────────────────────────────────────────────────────────────

private val FallbackTop    = Color(0xFF050A1A)
private val FallbackAccent = Color(0xFF4FC3F7)

// ── Star rating ───────────────────────────────────────────────────────────────

@Composable
private fun StarRating(
    rawRating: String,                     // e.g. "8.7" from NovelEntity.rating
    modifier: Modifier = Modifier,
    starColor: Color = Color(0xFFFFB400),
    emptyColor: Color = Color.White.copy(alpha = 0.30f),
) {
    val score = rawRating.toFloatOrNull() ?: 0f
    // NovelArrow ratings are out of 10 — map to 5 stars
    val stars = (score / 2f).coerceIn(0f, 5f)

    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        for (i in 1..5) {
            val icon = when {
                stars >= i          -> Icons.Filled.Star
                stars >= i - 0.5f   -> Icons.Filled.StarHalf
                else                -> Icons.Outlined.StarOutline
            }
            Icon(
                imageVector        = icon,
                contentDescription = null,
                tint               = if (stars >= i - 0.5f) starColor else emptyColor,
                modifier           = Modifier.size(17.dp),
            )
        }
        Spacer(Modifier.width(7.dp))
        Text(
            text       = rawRating.ifBlank { "—" },
            color      = Color.White,
            fontSize   = 13.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

// ── Glass play button ─────────────────────────────────────────────────────────

@Composable
private fun GlassPlayButton(label: String, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier            = Modifier.clickable(onClick = onClick),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier         = Modifier
                .size(68.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.18f))
                .border(1.5.dp, Color.White.copy(alpha = 0.45f), CircleShape),
        ) {
            Icon(
                imageVector        = Icons.Filled.PlayArrow,
                contentDescription = label,
                tint               = Color.White,
                modifier           = Modifier.size(34.dp),
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(label, color = Color.White.copy(alpha = 0.80f), fontSize = 11.sp)
    }
}

// ── Meta chip (Status | Genre | Latest) ──────────────────────────────────────

@Composable
private fun MetaChip(label: String, value: String, accent: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text       = value,
            color      = Color.White,
            fontSize   = 13.sp,
            fontWeight = FontWeight.Bold,
            textAlign  = TextAlign.Center,
            maxLines   = 1,
            overflow   = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(3.dp))
        Text(
            text          = label,
            color         = accent.copy(alpha = 0.75f),
            fontSize      = 9.sp,
            fontWeight    = FontWeight.SemiBold,
            letterSpacing = 1.2.sp,
            textAlign     = TextAlign.Center,
        )
    }
}

// ── Vertical separator ────────────────────────────────────────────────────────

@Composable
private fun MetaSeparator() {
    Box(
        modifier = Modifier
            .height(28.dp)
            .width(1.dp)
            .background(Color.White.copy(alpha = 0.18f)),
    )
}

// ── Chapter row ───────────────────────────────────────────────────────────────

@Composable
private fun ChapterRow(chapter: ChapterLink, accent: Color, onClick: () -> Unit) {
    Row(
        modifier          = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(accent.copy(alpha = 0.75f)),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text     = chapter.title.ifBlank { "Chapter ${chapter.num}" },
            color    = Color.White.copy(alpha = 0.88f),
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            text     = "Ch.${chapter.num}",
            color    = accent.copy(alpha = 0.55f),
            fontSize = 10.sp,
        )
    }
    HorizontalDivider(
        color     = Color.White.copy(alpha = 0.07f),
        thickness = 0.5.dp,
    )
}

// ── Floating glass icon button (back / refresh) ───────────────────────────────

@Composable
private fun GlassCircleBtn(onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier         = Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.35f))
            .border(1.dp, Color.White.copy(alpha = 0.20f), CircleShape)
            .clickable(onClick = onClick),
    ) { content() }
}

// ── Main screen ───────────────────────────────────────────────────────────────

@Composable
fun DetailScreen(
    slug: String,
    onBack: () -> Unit,
    onReadChapter: (chapterNum: Int) -> Unit,
    vm: DetailViewModel = viewModel(),
) {
    LaunchedEffect(slug) { vm.load(slug) }

    val state           by vm.state.collectAsStateWithLifecycle()
    val downloadProgress by vm.downloadProgress.collectAsStateWithLifecycle()
    val updateMessage   by vm.updateMessage.collectAsStateWithLifecycle()

    val snackbarHost = remember { SnackbarHostState() }
    LaunchedEffect(updateMessage) {
        updateMessage?.let { vm.clearUpdateMessage(); snackbarHost.showSnackbar(it) }
    }

    // Palette colours — extracted once the cover bitmap loads
    var dominantColor by remember { mutableStateOf<Color?>(null) }
    var vibrantColor  by remember { mutableStateOf<Color?>(null) }

    val bgTop  = dominantColor ?: FallbackTop
    val accent = vibrantColor  ?: FallbackAccent

    Box(modifier = Modifier.fillMaxSize()) {

        when (val s = state) {

            is DetailUiState.Loading -> {
                // Solid dark bg while loading
                Box(
                    modifier         = Modifier.fillMaxSize().background(FallbackTop),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = FallbackAccent)
                }
            }

            is DetailUiState.Error -> {
                Box(
                    modifier         = Modifier.fillMaxSize().background(FallbackTop),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(s.message, color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp)
                        Spacer(Modifier.height(16.dp))
                        Box(
                            modifier         = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(FallbackAccent.copy(alpha = 0.2f))
                                .border(1.dp, FallbackAccent.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                                .clickable { vm.load(slug) }
                                .padding(horizontal = 24.dp, vertical = 10.dp),
                        ) {
                            Text("Retry", color = Color.White, fontSize = 13.sp)
                        }
                    }
                }
            }

            is DetailUiState.Success -> {
                CinematicDetail(
                    novel          = s.novel,
                    chapters       = s.chapters,
                    lastReadChapter = s.lastReadChapter,
                    bgTop          = bgTop,
                    accent         = accent,
                    onReadChapter  = onReadChapter,
                    onCoverLoaded  = { drawable ->
                        val bmp = (drawable as? BitmapDrawable)?.bitmap ?: return@CinematicDetail
                        // Extract palette off main thread
                        val palette = Palette.from(bmp).generate()
                        dominantColor = Color(palette.getDominantColor(0xFF050A1A.toInt()))
                        vibrantColor  = Color(
                            palette.getVibrantColor(
                                palette.getLightVibrantColor(
                                    palette.getMutedColor(0xFF4FC3F7.toInt())
                                )
                            )
                        )
                    },
                )
            }
        }

        // ── Floating top row: back + refresh — always on top ─────────────
        Row(
            modifier              = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically,
        ) {
            GlassCircleBtn(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint               = Color.White,
                    modifier           = Modifier.size(18.dp),
                )
            }
            GlassCircleBtn(onClick = vm::checkForUpdates) {
                Icon(
                    Icons.Filled.Refresh,
                    contentDescription = "Check updates",
                    tint               = Color.White,
                    modifier           = Modifier.size(18.dp),
                )
            }
        }

        // Snackbar
        SnackbarHost(
            hostState = snackbarHost,
            modifier  = Modifier.align(Alignment.BottomCenter).navigationBarsPadding(),
        )
    }
}

// ── Cinematic detail body ─────────────────────────────────────────────────────

@Composable
private fun CinematicDetail(
    novel: NovelEntity,
    chapters: List<ChapterLink>,
    lastReadChapter: Int?,
    bgTop: Color,
    accent: Color,
    onReadChapter: (Int) -> Unit,
    onCoverLoaded: (android.graphics.drawable.Drawable) -> Unit,
) {
    var showMore by remember { mutableStateOf(false) }
    val PREVIEW_COUNT = 10

    // Parse first genre only for the chip slot
    val primaryGenre = novel.genres.split(",").firstOrNull()?.trim().orEmpty()

    // Format latest chapter label
    val latestLabel = novel.latestChapter.let {
        if (it.isNotBlank()) "Ch. $it" else "—"
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(bgTop, Color(0xFF000000)))),
    ) {
        // Full-bleed cover art
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(novel.coverUrl)
                .crossfade(500)
                .listener(onSuccess = { _, result -> onCoverLoaded(result.drawable) })
                .build(),
            contentDescription = novel.title,
            contentScale       = ContentScale.Crop,
            modifier           = Modifier
                .fillMaxSize()
                .drawWithContent {
                    drawContent()
                    // Heavy bottom scrim so all text is readable over any cover
                    drawRect(
                        Brush.verticalGradient(
                            0f    to Color.Black.copy(alpha = 0.10f),
                            0.25f to Color.Black.copy(alpha = 0.30f),
                            0.55f to Color.Black.copy(alpha = 0.72f),
                            0.75f to Color.Black.copy(alpha = 0.90f),
                            1f    to Color.Black.copy(alpha = 0.98f),
                        )
                    )
                },
        )

        // Scrollable content
        LazyColumn(
            modifier       = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {

            // Space for back button row (38dp button + 8dp top + 8dp bottom padding)
            item { Spacer(Modifier.height(54.dp)) }

            // Cover art breathing room — pushes text below the fold
            item { Spacer(Modifier.height(220.dp)) }

            // ── Author(s) ─────────────────────────────────────────────────
            item {
                val authors = novel.genres   // field reuse note: genres field holds genres,
                // author isn't a separate field in NovelEntity.
                // We'll show genres as the sub-brand label (like "Disney · Pixar")
                // and a formatted genre pill instead.
                Text(
                    text          = primaryGenre.uppercase().ifBlank { "NOVEL" },
                    color         = accent,
                    fontSize      = 11.sp,
                    fontWeight    = FontWeight.SemiBold,
                    letterSpacing = 2.sp,
                    textAlign     = TextAlign.Center,
                    modifier      = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 32.dp),
                )
            }

            item { Spacer(Modifier.height(8.dp)) }

            // ── Title ─────────────────────────────────────────────────────
            item {
                Text(
                    text       = novel.title,
                    color      = Color.White,
                    fontSize   = 32.sp,
                    fontWeight = FontWeight.ExtraBold,
                    lineHeight = 36.sp,
                    textAlign  = TextAlign.Center,
                    style      = TextStyle(
                        shadow = Shadow(
                            color      = Color.Black.copy(alpha = 0.6f),
                            offset     = Offset(0f, 4f),
                            blurRadius = 14f,
                        )
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                )
            }

            item { Spacer(Modifier.height(20.dp)) }

            // ── Meta row: Status | Genre | Latest ─────────────────────────
            item {
                Row(
                    modifier              = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 32.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment     = Alignment.CenterVertically,
                ) {
                    MetaChip(
                        label  = "STATUS",
                        value  = novel.status.ifBlank { "—" },
                        accent = accent,
                    )
                    MetaSeparator()
                    MetaChip(
                        label  = "GENRE",
                        value  = primaryGenre.ifBlank { "—" },
                        accent = accent,
                    )
                    MetaSeparator()
                    MetaChip(
                        label  = "LATEST",
                        value  = latestLabel,
                        accent = accent,
                    )
                }
            }

            item { Spacer(Modifier.height(20.dp)) }

            // ── Star rating ───────────────────────────────────────────────
            item {
                StarRating(
                    rawRating = novel.rating,
                    modifier  = Modifier.padding(horizontal = 24.dp),
                )
            }

            item { Spacer(Modifier.height(28.dp)) }

            // ── Glass play button ─────────────────────────────────────────
            item {
                val readLabel = if (lastReadChapter != null)
                    "Continue Ch.$lastReadChapter" else "Start Reading"
                val targetChapter = lastReadChapter
                    ?: chapters.lastOrNull()?.num
                    ?: 1

                GlassPlayButton(
                    label   = readLabel,
                    onClick = { onReadChapter(targetChapter) },
                )
            }

            item { Spacer(Modifier.height(32.dp)) }

            // ── Show More toggle ──────────────────────────────────────────
            item {
                Row(
                    modifier              = Modifier
                        .fillMaxWidth()
                        .clickable { showMore = !showMore }
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment     = Alignment.CenterVertically,
                ) {
                    Text(
                        text       = if (showMore) "Show Less" else "Show More",
                        color      = Color.White.copy(alpha = 0.75f),
                        fontSize   = 12.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        imageVector        = if (showMore)
                            Icons.Filled.KeyboardArrowUp
                        else
                            Icons.Filled.KeyboardArrowDown,
                        contentDescription = null,
                        tint               = Color.White.copy(alpha = 0.75f),
                        modifier           = Modifier.size(16.dp),
                    )
                }
            }

            // ── Synopsis ──────────────────────────────────────────────────
            item {
                AnimatedVisibility(
                    visible = showMore,
                    enter   = fadeIn() + expandVertically(spring(stiffness = Spring.StiffnessMediumLow)),
                    exit    = fadeOut() + shrinkVertically(spring(stiffness = Spring.StiffnessMediumLow)),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp),
                    ) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text          = "SUMMARY",
                            color         = accent,
                            fontSize      = 10.sp,
                            fontWeight    = FontWeight.SemiBold,
                            letterSpacing = 1.5.sp,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text       = novel.synopsis.ifBlank { "No summary available." },
                            color      = Color.White.copy(alpha = 0.80f),
                            fontSize   = 13.sp,
                            lineHeight = 21.sp,
                        )
                        Spacer(Modifier.height(24.dp))
                    }
                }
            }

            // ── Chapter list ──────────────────────────────────────────────
            item {
                AnimatedVisibility(
                    visible = showMore,
                    enter   = fadeIn() + expandVertically(spring(stiffness = Spring.StiffnessMediumLow)),
                    exit    = fadeOut() + shrinkVertically(spring(stiffness = Spring.StiffnessMediumLow)),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                            .background(Color.Black.copy(alpha = 0.50f))
                            .border(
                                width  = 1.dp,
                                color  = Color.White.copy(alpha = 0.08f),
                                shape  = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                            ),
                    ) {
                        // Header
                        Row(
                            modifier              = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment     = Alignment.CenterVertically,
                        ) {
                            Text(
                                text       = "Chapters",
                                color      = Color.White,
                                fontSize   = 14.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text     = "${chapters.size} total",
                                color    = accent.copy(alpha = 0.70f),
                                fontSize = 11.sp,
                            )
                        }
                        HorizontalDivider(
                            color     = Color.White.copy(alpha = 0.10f),
                            thickness = 0.5.dp,
                        )

                        // First 10 chapters
                        val preview = chapters.take(PREVIEW_COUNT)
                        preview.forEach { chapter ->
                            ChapterRow(
                                chapter     = chapter,
                                accent      = accent,
                                onClick     = { onReadChapter(chapter.num) },
                            )
                        }

                        // "See More" with fade gradient mask if >10 chapters
                        if (chapters.size > PREVIEW_COUNT) {
                            Box(
                                modifier         = Modifier
                                    .fillMaxWidth()
                                    .height(80.dp)
                                    .drawWithContent {
                                        drawContent()
                                        drawRect(
                                            Brush.verticalGradient(
                                                0f to Color.Transparent,
                                                1f to Color.Black.copy(alpha = 0.95f),
                                            )
                                        )
                                    },
                                contentAlignment = Alignment.BottomCenter,
                            ) {
                                Text(
                                    text       = "See All ${chapters.size} Chapters",
                                    color      = accent,
                                    fontSize   = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier   = Modifier
                                        .padding(bottom = 14.dp)
                                        .clickable { /* TODO: full chapter list screen */ },
                                )
                            }
                        }

                        Spacer(Modifier.height(8.dp))
                    }
                }
            }

            item { Spacer(Modifier.height(48.dp)) }
        }
    }
}
