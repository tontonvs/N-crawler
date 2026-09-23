package com.noven.ncrawler.ui.screens.detail

import android.graphics.drawable.BitmapDrawable
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DownloadForOffline
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarHalf
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.palette.graphics.Palette
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.noven.ncrawler.data.db.DownloadProgress
import com.noven.ncrawler.data.db.DownloadStatus
import com.noven.ncrawler.data.db.NovelEntity
import com.noven.ncrawler.data.local.DominantColorStore
import com.noven.ncrawler.data.scraper.ChapterLink
import com.noven.ncrawler.ui.theme.MontserratFamily
import com.noven.ncrawler.viewmodel.DetailUiState
import com.noven.ncrawler.viewmodel.DetailViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

// CHANGE (this pass):
//  • Every piece of text is Montserrat — the reader's font — including the
//    rating, chips, chapter rows, buttons, error state and the snackbar.
//  • Buttons (back, refresh, play, retry, "see all") use the reader's design:
//    soft filled circles / pills, no border, no glass edge.
//  • "Show More" is gone. The first screen is exactly one viewport tall (cover
//    art, title, meta, rating, play) and NOTHING else shows on it — the summary
//    and chapter list start below the fold and only fade in once you scroll
//    them into view. A faint double-chevron hint drifts downward for 2s at the
//    bottom, then fades away.
//  • Label AND value text is larger throughout (Status/Completed, Genre,
//    Latest, Summary, Chapters …).
// The palette extraction (dominant → background, vibrant → accent) is untouched
// — the reader's cover-colour theming mirrors it.

// ── Colour helpers ────────────────────────────────────────────────────────────

private val FallbackTop    = Color(0xFF050A1A)
private val FallbackAccent = Color(0xFF4FC3F7)

// LazyColumn item positions in CinematicDetail (hero = 0). RevealOnScroll needs
// them to know when ITS item has been scrolled into view.
private const val SUMMARY_INDEX  = 1
private const val CHAPTERS_INDEX = 2

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
                modifier           = Modifier.size(21.dp),
            )
        }
        Spacer(Modifier.width(9.dp))
        Text(
            text       = rawRating.ifBlank { "—" },
            color      = Color.White,
            fontFamily = MontserratFamily,
            fontSize   = 17.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

// ── Reader-style circle button (back / refresh / play) ────────────────────────
// The reader's buttons are soft filled circles: the foreground colour at 13%,
// no border, icon at 90%. Here the circle sits over cover art rather than the
// reader's solid page, so a dark base tint sits under the 13% white to keep it
// visible on bright covers.

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ReaderCircleBtn(
    onClick: () -> Unit,
    // CHANGE (partial downloads): optional — every existing caller (Back,
    // Refresh, Play) passes nothing here and keeps behaving exactly as
    // before. Only the new download button uses it, to open the
    // download-options sheet without disturbing its single-tap action.
    onLongClick: (() -> Unit)? = null,
    size: Dp = 48.dp,
    content: @Composable () -> Unit,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier         = Modifier
            .size(size)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.40f))
            .background(Color.White.copy(alpha = 0.13f))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) { content() }
}

// ── Play button ───────────────────────────────────────────────────────────────

@Composable
private fun PlayButton(label: String, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier            = Modifier
            .clip(RoundedCornerShape(24.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 6.dp),
    ) {
        ReaderCircleBtn(onClick = onClick, size = 76.dp) {
            Icon(
                imageVector        = Icons.Filled.PlayArrow,
                contentDescription = label,
                tint               = Color.White.copy(alpha = 0.9f),
                modifier           = Modifier.size(40.dp),
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text       = label,
            color      = Color.White.copy(alpha = 0.9f),
            fontFamily = MontserratFamily,
            fontSize   = 15.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

// ── Download button (top row) ───────────────────────────────────────────────
// CHANGE (Downloads overhaul, detail-screen wiring): DetailViewModel already
// had downloadAll()/cancelDownload() and a live downloadProgress flow — this
// screen just never rendered them, so tapping nothing here ever actually
// started a download. Reuses the exact status vocabulary DownloadsScreen
// established: tap starts a download when idle, pauses an active one,
// resumes a paused one, retries a failed one, and — once complete — opens
// the Downloads screen, since "tap to manage/delete" is the only useful
// thing left to do with a completed download from here.
@Composable
private fun DownloadCircleBtn(
    progress: DownloadProgress?,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onManage: () -> Unit,
    // CHANGE (partial downloads): long-press opens the download-options
    // sheet (all / last N / by volume / custom range) regardless of the
    // current status — lets you top up an already-partial download too.
    onLongPress: () -> Unit,
) {
    val status = progress?.status
    val action = when (status) {
        null, DownloadStatus.PAUSED, DownloadStatus.ERROR -> onStart
        DownloadStatus.QUEUED, DownloadStatus.DOWNLOADING  -> onPause
        DownloadStatus.COMPLETE                            -> onManage
    }
    ReaderCircleBtn(onClick = action, onLongClick = onLongPress) {
        // CHANGE (motion polish): status changes are occasional, not a
        // high-frequency interaction, so a short cross-fade + scale swap
        // (Jakub Krehel's "animated icon swap" pattern) reads as intentional
        // rather than jarring — an instant icon swap here would otherwise
        // look like a glitch every time a chapter finishes. Exit (120ms) is
        // deliberately quicker/subtler than enter (200ms); no overshoot.
        AnimatedContent(
            targetState   = status,
            transitionSpec = {
                (fadeIn(tween(200)) + scaleIn(initialScale = 0.85f, animationSpec = tween(200)))
                    .togetherWith(fadeOut(tween(120)) + scaleOut(targetScale = 0.85f, animationSpec = tween(120)))
            },
            label = "downloadStatusIcon",
        ) { s ->
            when (s) {
                DownloadStatus.DOWNLOADING -> {
                    CircularProgressIndicator(
                        modifier    = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color       = Color.White.copy(alpha = 0.9f),
                    )
                }
                DownloadStatus.QUEUED -> {
                    Icon(
                        Icons.Filled.Schedule,
                        contentDescription = "Queued to download",
                        tint               = Color.White.copy(alpha = 0.9f),
                        modifier           = Modifier.size(22.dp),
                    )
                }
                DownloadStatus.PAUSED -> {
                    Icon(
                        Icons.Filled.PauseCircle,
                        contentDescription = "Download paused — tap to resume",
                        tint               = Color.White.copy(alpha = 0.9f),
                        modifier           = Modifier.size(24.dp),
                    )
                }
                DownloadStatus.ERROR -> {
                    Icon(
                        Icons.Filled.ErrorOutline,
                        contentDescription = "Download failed — tap to retry",
                        tint               = Color(0xFFFF6B6B),
                        modifier           = Modifier.size(24.dp),
                    )
                }
                DownloadStatus.COMPLETE -> {
                    Icon(
                        Icons.Filled.CheckCircle,
                        contentDescription = "Downloaded — tap to manage",
                        tint               = Color.White.copy(alpha = 0.9f),
                        modifier           = Modifier.size(24.dp),
                    )
                }
                null -> {
                    Icon(
                        Icons.Filled.DownloadForOffline,
                        contentDescription = "Download chapters — hold for options",
                        tint               = Color.White.copy(alpha = 0.9f),
                        modifier           = Modifier.size(24.dp),
                    )
                }
            }
        }
    }
}

// ── Download options sheet (partial downloads) ──────────────────────────────
// CHANGE (partial downloads): opened by long-pressing the download button.
// Offers "All", a few "Last N" presets, synthetic volume chips (see note
// below — the sources here don't publish real volume boundaries, so these
// are fixed 100-chapter blocks, not scraped structure), and a custom range
// via RangeSlider. All three paths funnel into NovelRepository.queueDownloadRange
// under the hood, so they share the same progress bookkeeping and
// concurrency guard as every other download path.
private const val SYNTHETIC_VOLUME_SIZE = 100

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DownloadOptionsSheet(
    chapters: List<ChapterLink>,
    accent: Color,
    onDismiss: () -> Unit,
    onDownloadAll: () -> Unit,
    onDownloadLast: (count: Int) -> Unit,
    onDownloadRange: (start: Int, end: Int) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val minNum = chapters.minOfOrNull { it.num } ?: 1
    val maxNum = chapters.maxOfOrNull { it.num } ?: 1
    val total  = chapters.size

    val volumes = remember(minNum, maxNum) {
        (minNum..maxNum step SYNTHETIC_VOLUME_SIZE).mapIndexed { i, start ->
            val end = (start + SYNTHETIC_VOLUME_SIZE - 1).coerceAtMost(maxNum)
            Triple(start, end, i + 1)
        }
    }

    var rangeSelection by remember(minNum, maxNum) {
        mutableStateOf(minNum.toFloat()..maxNum.toFloat())
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState       = sheetState,
        containerColor   = Color(0xFF10141F),
        contentColor     = Color.White,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
        ) {
            Text(
                "Download chapters",
                color      = Color.White,
                fontFamily = MontserratFamily,
                fontSize   = 18.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                "$total chapters available",
                color      = Color.White.copy(alpha = 0.55f),
                fontFamily = MontserratFamily,
                fontSize   = 13.sp,
            )
            Spacer(Modifier.height(18.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PresetChip("All ($total)", accent) { onDownloadAll(); onDismiss() }
                listOf(50, 100, 200).filter { it < total }.forEach { n ->
                    PresetChip("Last $n", accent) { onDownloadLast(n); onDismiss() }
                }
            }

            if (volumes.size > 1) {
                Spacer(Modifier.height(22.dp))
                Text(
                    "BY VOLUME",
                    color      = Color.White.copy(alpha = 0.5f),
                    fontFamily = MontserratFamily,
                    fontSize   = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(volumes) { (start, end, idx) ->
                        PresetChip("Vol. $idx", accent) {
                            onDownloadRange(start, end); onDismiss()
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
            Text(
                "CUSTOM RANGE",
                color      = Color.White.copy(alpha = 0.5f),
                fontFamily = MontserratFamily,
                fontSize   = 11.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(4.dp))
            val rangeChapterCount = rangeSelection.endInclusive.roundToInt() - rangeSelection.start.roundToInt() + 1
            Text(
                "Ch. ${rangeSelection.start.roundToInt()} – ${rangeSelection.endInclusive.roundToInt()}  ·  $rangeChapterCount chapters",
                color      = Color.White,
                fontFamily = MontserratFamily,
                fontSize   = 14.sp,
                fontWeight = FontWeight.SemiBold,
            )
            RangeSlider(
                value         = rangeSelection,
                onValueChange = { rangeSelection = it },
                valueRange    = minNum.toFloat()..maxNum.toFloat(),
                colors = SliderDefaults.colors(
                    thumbColor         = accent,
                    activeTrackColor   = accent,
                    inactiveTrackColor = Color.White.copy(alpha = 0.15f),
                ),
            )
            Spacer(Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(accent.copy(alpha = 0.9f))
                    .clickable {
                        onDownloadRange(
                            rangeSelection.start.roundToInt(),
                            rangeSelection.endInclusive.roundToInt(),
                        )
                        onDismiss()
                    }
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "Download selected",
                    color      = Color.White,
                    fontFamily = MontserratFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize   = 15.sp,
                )
            }
        }
    }
}

@Composable
private fun PresetChip(label: String, accent: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White.copy(alpha = 0.08f))
            .border(1.dp, accent.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp),
    ) {
        Text(
            label,
            color      = Color.White,
            fontFamily = MontserratFamily,
            fontSize   = 13.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

// ── Meta chip (Status | Genre | Latest) ──────────────────────────────────────
// value = the info ("Completed"), label = its title ("STATUS"). Both are larger
// than before (16sp / 12sp, were 13sp / 9sp).

@Composable
private fun MetaChip(label: String, value: String, accent: Color, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text       = value,
            color      = Color.White,
            fontFamily = MontserratFamily,
            fontSize   = 16.sp,
            fontWeight = FontWeight.Bold,
            textAlign  = TextAlign.Center,
            maxLines   = 2,
            overflow   = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text          = label,
            color         = accent.copy(alpha = 0.85f),
            fontFamily    = MontserratFamily,
            fontSize      = 12.sp,
            fontWeight    = FontWeight.SemiBold,
            letterSpacing = 1.sp,
            textAlign     = TextAlign.Center,
        )
    }
}

// ── Vertical separator ────────────────────────────────────────────────────────

@Composable
private fun MetaSeparator() {
    Box(
        modifier = Modifier
            .height(36.dp)
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
            .padding(vertical = 14.dp, horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(accent.copy(alpha = 0.75f)),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text       = chapter.title.ifBlank { "Chapter ${chapter.num}" },
            color      = Color.White.copy(alpha = 0.90f),
            fontFamily = MontserratFamily,
            fontSize   = 16.sp,
            maxLines   = 1,
            overflow   = TextOverflow.Ellipsis,
            modifier   = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text       = "Ch.${chapter.num}",
            color      = accent.copy(alpha = 0.85f),
            fontFamily = MontserratFamily,
            fontSize   = 13.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
    HorizontalDivider(
        color     = Color.White.copy(alpha = 0.07f),
        thickness = 0.5.dp,
    )
}

// ── Reveal on scroll ──────────────────────────────────────────────────────────
// The item stays invisible until the user has actually scrolled it at least
// 72dp into the viewport — merely being composed (LazyColumn can compose an
// item just outside the viewport) doesn't reveal it. Then it fades + rises
// 24dp. Alpha/translation only, so the item's layout height never changes and
// the scroll position can't jump.

@Composable
private fun RevealOnScroll(
    listState: LazyListState,
    itemIndex: Int,
    content: @Composable () -> Unit,
) {
    var shown by remember { mutableStateOf(false) }
    val thresholdPx = with(LocalDensity.current) { 72.dp.toPx() }

    LaunchedEffect(listState, itemIndex) {
        snapshotFlow {
            val info = listState.layoutInfo
            val item = info.visibleItemsInfo.firstOrNull { it.index == itemIndex }
            item != null && item.offset + thresholdPx < info.viewportEndOffset
        }.first { it }
        shown = true
    }

    val progress by animateFloatAsState(
        targetValue   = if (shown) 1f else 0f,
        animationSpec = tween(550, easing = FastOutSlowInEasing),
        label         = "revealProgress",
    )
    Box(
        modifier = Modifier.graphicsLayer {
            alpha        = progress
            translationY = (1f - progress) * 24.dp.toPx()
        },
    ) { content() }
}

// ── Scroll hint: a chevron inside a chevron (like the road arrows painted on
// walls), 3dp stroke, slightly transparent. The two chevrons light up one after
// the other, top then bottom, so the motion reads as "downwards". Fades in, and
// out again when [visible] goes false; once fully faded it stops drawing.

private fun wave(p: Float): Float {
    val x = ((p % 1f) + 1f) % 1f
    return sin(PI.toFloat() * x)
}

@Composable
private fun ScrollHint(visible: Boolean, modifier: Modifier = Modifier) {
    val fade by animateFloatAsState(
        targetValue   = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = if (visible) 350 else 600),
        label         = "scrollHintFade",
    )
    if (!visible && fade <= 0.01f) return   // fully faded away — nothing left to draw

    val transition = rememberInfiniteTransition(label = "scrollHintWave")
    val phase by transition.animateFloat(
        initialValue  = 0f,
        targetValue   = 1f,
        animationSpec = infiniteRepeatable(tween(1000, easing = LinearEasing)),
        label         = "scrollHintPhase",
    )

    Canvas(
        modifier = modifier
            .size(width = 36.dp, height = 44.dp)
            .graphicsLayer { alpha = fade },
    ) {
        val stroke = 3.dp.toPx()
        val w      = size.width
        val chevH  = 12.dp.toPx()
        val gap    = 11.dp.toPx()
        val drift  = phase * 5.dp.toPx()
        val maxA   = 0.7f                       // "slightly transparent"

        fun chevron(top: Float, alpha: Float) {
            val path = Path().apply {
                moveTo(stroke, top)
                lineTo(w / 2f, top + chevH)
                lineTo(w - stroke, top)
            }
            drawPath(
                path  = path,
                color = Color.White.copy(alpha = maxA * alpha),
                style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }

        chevron(top = 3.dp.toPx() + drift,        alpha = wave(phase))
        chevron(top = 3.dp.toPx() + gap + drift,  alpha = wave(phase - 0.28f))
    }
}

// ── Main screen ───────────────────────────────────────────────────────────────

@Composable
fun DetailScreen(
    slug: String,
    onBack: () -> Unit,
    onReadChapter: (chapterNum: Int) -> Unit,
    // CHANGE (Downloads overhaul): only used once the download is COMPLETE —
    // see DownloadCircleBtn below.
    onDownloadsClick: () -> Unit,
    vm: DetailViewModel = viewModel(),
) {
    LaunchedEffect(slug) { vm.load(slug) }

    val state           by vm.state.collectAsStateWithLifecycle()
    val downloadProgress by vm.downloadProgress.collectAsStateWithLifecycle()
    val updateMessage   by vm.updateMessage.collectAsStateWithLifecycle()

    // CHANGE (partial downloads): only non-null once the novel and its
    // chapter list have actually loaded — the download-options sheet needs
    // the chapter list (for volume/range bounds) so it can only open then.
    val successState = state as? DetailUiState.Success
    var showDownloadSheet by remember { mutableStateOf(false) }

    val context = LocalContext.current
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
                        Text(
                            s.message,
                            color      = Color.White.copy(alpha = 0.7f),
                            fontFamily = MontserratFamily,
                            fontSize   = 16.sp,
                            textAlign  = TextAlign.Center,
                            modifier   = Modifier.padding(horizontal = 32.dp),
                        )
                        Spacer(Modifier.height(18.dp))
                        // Reader-style pill: soft filled, no border
                        Box(
                            modifier         = Modifier
                                .clip(RoundedCornerShape(24.dp))
                                .background(Color.White.copy(alpha = 0.13f))
                                .clickable { vm.load(slug) }
                                .padding(horizontal = 28.dp, vertical = 12.dp),
                        ) {
                            Text(
                                "Retry",
                                color      = Color.White,
                                fontFamily = MontserratFamily,
                                fontSize   = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
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
                        // Extract palette off main thread — guarded: a bad
                        // bitmap here must never be able to blank the cover.
                        try {
                            val palette = Palette.from(bmp).generate()
                            dominantColor = Color(palette.getDominantColor(0xFF050A1A.toInt()))
                            // Remember it: Browse colours the cut-out behind its
                            // downloads button with the last novel's dominant colour.
                            // (Only a real swatch — not the fallback navy.)
                            palette.dominantSwatch?.let {
                                DominantColorStore(context).saveLast(slug, it.rgb)
                            }
                            vibrantColor  = Color(
                                palette.getVibrantColor(
                                    palette.getLightVibrantColor(
                                        palette.getMutedColor(0xFF4FC3F7.toInt())
                                    )
                                )
                            )
                        } catch (e: Exception) {
                            // Falls back to FallbackTop/FallbackAccent — the
                            // cover image itself is unaffected either way.
                        }
                    },
                )
            }
        }

        // ── Floating top row: back + download + refresh — always on top ──
        // Reader-style 48dp circles with 24dp icons (same as the reader header).
        Row(
            modifier              = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically,
        ) {
            ReaderCircleBtn(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint               = Color.White.copy(alpha = 0.9f),
                    modifier           = Modifier.size(24.dp),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DownloadCircleBtn(
                    progress    = downloadProgress,
                    onStart     = vm::downloadAll,
                    onPause     = vm::cancelDownload,
                    onManage    = onDownloadsClick,
                    onLongPress = { if (successState != null) showDownloadSheet = true },
                )
                ReaderCircleBtn(onClick = vm::checkForUpdates) {
                    Icon(
                        Icons.Filled.Refresh,
                        contentDescription = "Check updates",
                        tint               = Color.White.copy(alpha = 0.9f),
                        modifier           = Modifier.size(24.dp),
                    )
                }
            }
        }

        // Snackbar — custom content so its text is Montserrat too
        SnackbarHost(
            hostState = snackbarHost,
            modifier  = Modifier.align(Alignment.BottomCenter).navigationBarsPadding(),
        ) { data ->
            Snackbar(modifier = Modifier.padding(12.dp)) {
                Text(
                    data.visuals.message,
                    fontFamily = MontserratFamily,
                    fontSize   = 14.sp,
                )
            }
        }

        // CHANGE (partial downloads): long-press the download button to get
        // here. Guarded on successState so it can never be requested before
        // the chapter list has actually loaded.
        if (showDownloadSheet && successState != null) {
            DownloadOptionsSheet(
                chapters        = successState.chapters,
                accent          = accent,
                onDismiss       = { showDownloadSheet = false },
                onDownloadAll   = { vm.downloadAll() },
                onDownloadLast  = { count -> vm.downloadLast(count) },
                onDownloadRange = { start, end -> vm.downloadRange(start, end) },
            )
        }
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
    val listState = rememberLazyListState()
    var showAllChapters by remember { mutableStateOf(false) }
    val PREVIEW_COUNT = 10

    // Scroll hint: visible for 2s, then it fades away — or sooner, the moment
    // the user starts scrolling (they've found the content, no hint needed).
    var showHint by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        delay(2000)
        showHint = false
    }
    LaunchedEffect(listState) {
        snapshotFlow {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0
        }.first { it }
        showHint = false
    }

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
                // Palette.from() below needs a software bitmap — Coil defaults
                // to hardware bitmaps on API 26+, which Palette throws on
                // (IllegalArgumentException), silently killing this request.
                // Cards never hit this because they never touch Palette.
                .allowHardware(false)
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
            state          = listState,
            modifier       = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {

            // ── First screen: exactly ONE viewport tall ───────────────────
            // Cover art fills the top (the back/refresh row floats over it); the
            // text block is bottom-anchored above the hint zone. Because this item
            // is a full viewport high, the summary and chapters below it start
            // off-screen — nothing of them shows until the user scrolls.
            // wrapContentHeight(unbounded) so that on a very short screen any
            // overflow goes off the TOP (under the button row), never pushing the
            // play button below the fold.
            item {
                Box(
                    modifier = Modifier
                        .fillParentMaxHeight()
                        .fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .wrapContentHeight(align = Alignment.Bottom, unbounded = true)
                            // 84dp keeps the play button clear of the scroll hint
                            .padding(bottom = 84.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        // Sub-brand label: first genre
                        Text(
                            text          = primaryGenre.uppercase().ifBlank { "NOVEL" },
                            color         = accent,
                            fontFamily    = MontserratFamily,
                            fontSize      = 14.sp,
                            fontWeight    = FontWeight.SemiBold,
                            letterSpacing = 2.sp,
                            textAlign     = TextAlign.Center,
                            modifier      = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 32.dp),
                        )

                        Spacer(Modifier.height(8.dp))

                        // Title
                        Text(
                            text       = novel.title,
                            color      = Color.White,
                            fontFamily = MontserratFamily,
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

                        Spacer(Modifier.height(22.dp))

                        // Meta row: Status | Genre | Latest — equal-weight
                        // columns so the larger text wraps/ellipsizes inside its
                        // own slot instead of pushing the row wider than the screen.
                        Row(
                            modifier          = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            MetaChip(
                                label    = "STATUS",
                                value    = novel.status.ifBlank { "—" },
                                accent   = accent,
                                modifier = Modifier.weight(1f),
                            )
                            MetaSeparator()
                            MetaChip(
                                label    = "GENRE",
                                value    = primaryGenre.ifBlank { "—" },
                                accent   = accent,
                                modifier = Modifier.weight(1f),
                            )
                            MetaSeparator()
                            MetaChip(
                                label    = "LATEST",
                                value    = latestLabel,
                                accent   = accent,
                                modifier = Modifier.weight(1f),
                            )
                        }

                        Spacer(Modifier.height(22.dp))

                        // Star rating
                        StarRating(
                            rawRating = novel.rating,
                            modifier  = Modifier.padding(horizontal = 24.dp),
                        )

                        Spacer(Modifier.height(26.dp))

                        // Play button
                        val readLabel = if (lastReadChapter != null)
                            "Continue Ch.$lastReadChapter" else "Start Reading"
                        val targetChapter = lastReadChapter
                            ?: chapters.lastOrNull()?.num
                            ?: 1

                        PlayButton(
                            label   = readLabel,
                            onClick = { onReadChapter(targetChapter) },
                        )
                    }
                }
            }

            // ── Synopsis (item 1) — no "Show More": it starts below the fold
            // and fades in once you scroll it into view ────────────────────
            item {
                RevealOnScroll(listState, itemIndex = SUMMARY_INDEX) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp),
                    ) {
                        Spacer(Modifier.height(24.dp))
                        Text(
                            text          = "SUMMARY",
                            color         = accent,
                            fontFamily    = MontserratFamily,
                            fontSize      = 14.sp,
                            fontWeight    = FontWeight.SemiBold,
                            letterSpacing = 1.5.sp,
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text       = novel.synopsis.ifBlank { "No summary available." },
                            color      = Color.White.copy(alpha = 0.85f),
                            fontFamily = MontserratFamily,
                            fontSize   = 16.sp,
                            lineHeight = 26.sp,
                        )
                        Spacer(Modifier.height(28.dp))
                    }
                }
            }

            // ── Chapter list (item 2) — same: hidden until scrolled to ────
            item {
                RevealOnScroll(listState, itemIndex = CHAPTERS_INDEX) {
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
                                .padding(horizontal = 16.dp, vertical = 16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment     = Alignment.CenterVertically,
                        ) {
                            Text(
                                text       = "Chapters",
                                color      = Color.White,
                                fontFamily = MontserratFamily,
                                fontSize   = 18.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text       = "${chapters.size} total",
                                color      = accent.copy(alpha = 0.85f),
                                fontFamily = MontserratFamily,
                                fontSize   = 14.sp,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                        HorizontalDivider(
                            color     = Color.White.copy(alpha = 0.10f),
                            thickness = 0.5.dp,
                        )

                        // Preview 10, or the full list once "See All" is tapped.
                        // Plain (non-lazy) rows, same as before — fine up to a
                        // few thousand chapters; if a novel's full list ever
                        // gets sluggish to expand, that's the point to switch
                        // this inner list to its own LazyColumn.
                        val preview = if (showAllChapters) chapters else chapters.take(PREVIEW_COUNT)
                        preview.forEach { chapter ->
                            ChapterRow(
                                chapter     = chapter,
                                accent      = accent,
                                onClick     = { onReadChapter(chapter.num) },
                            )
                        }

                        // "See All" with fade gradient mask if >10 chapters
                        if (!showAllChapters && chapters.size > PREVIEW_COUNT) {
                            Box(
                                modifier         = Modifier
                                    .fillMaxWidth()
                                    .height(90.dp)
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
                                // Reader-style pill
                                Box(
                                    modifier = Modifier
                                        .padding(bottom = 14.dp)
                                        .clip(RoundedCornerShape(24.dp))
                                        .background(Color.White.copy(alpha = 0.13f))
                                        .clickable { showAllChapters = true }
                                        .padding(horizontal = 22.dp, vertical = 10.dp),
                                ) {
                                    Text(
                                        text       = "See All ${chapters.size} Chapters",
                                        color      = Color.White,
                                        fontFamily = MontserratFamily,
                                        fontSize   = 15.sp,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(8.dp))
                    }
                }
            }

            item { Spacer(Modifier.height(48.dp)) }
        }

        // Scroll hint — bottom centre, over the content
        ScrollHint(
            visible  = showHint,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 28.dp),
        )
    }
}
