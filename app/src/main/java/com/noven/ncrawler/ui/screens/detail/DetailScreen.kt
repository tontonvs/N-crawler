package com.noven.ncrawler.ui.screens.detail

import com.noven.ncrawler.ui.components.AnimatedDownloadIcon
import com.noven.ncrawler.ui.components.AnimatedRefreshIcon
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Shape
import android.provider.Settings
import com.noven.ncrawler.ui.components.SolarIcons
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.CompositionLocalProvider
import dev.chrisbanes.haze.HazeState
import com.noven.ncrawler.ui.theme.GlassMode
import com.noven.ncrawler.ui.components.glassSource
import com.noven.ncrawler.ui.components.glassBlur
import android.graphics.drawable.BitmapDrawable
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
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
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
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
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
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
import androidx.compose.runtime.key
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
import com.noven.ncrawler.ui.components.Motion
import com.noven.ncrawler.ui.components.errorShake
import com.noven.ncrawler.ui.theme.GlassSurfaceDark
import com.noven.ncrawler.ui.theme.GlassSpec
import com.noven.ncrawler.ui.theme.GlassBase
import com.noven.ncrawler.ui.components.glassFill
import com.noven.ncrawler.ui.components.pressScale
import com.noven.ncrawler.ui.components.pressable
import com.noven.ncrawler.ui.theme.MontserratFamily
import com.noven.ncrawler.ui.theme.StarGold
import com.noven.ncrawler.viewmodel.DetailUiState
import com.noven.ncrawler.viewmodel.DetailViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

// CHANGE (Detail redesign pass):
//  • The first screen is now a long, SHARP cover card (the Browse hero's design)
//    with the details laid over it — genre, title, Status | Genre | Author,
//    rating, play. The blurred full-bleed cover stays behind it.
//  • "Latest" is gone from the meta row (Author took its place) and now sits
//    under the play button: "Latest Ch. N" when unread, "Continue Ch. N" when
//    in progress. Unread play still starts at Chapter 1.
//  • Chapters: sort toggle (newest first by default), "See more" reveals 5 at a
//    time (the old all-at-once list froze the app), real titles where known.
//  • Refresh: animated icon while it runs + a skeleton of this screen's layout;
//    the skeleton also shows when a novel is opened.
// CHANGE (earlier pass):
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

// Chapter list paging: first batch, then this many more per "See more" tap.
private const val CHAPTER_FIRST_BATCH = 10
private const val CHAPTER_PAGE        = 5

// ── Star rating ───────────────────────────────────────────────────────────────

@Composable
private fun StarRating(
    rawRating: String,                     // e.g. "8.7" from NovelEntity.rating
    modifier: Modifier = Modifier,
    starColor: Color = StarGold,   // was a hardcoded near-duplicate of StarGold
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

// Blur layer for the top-row circle buttons (Glass mode). Provided only to the
// top row, which is a sibling of the cover layer — never to its descendants.
private val LocalDetailHaze = compositionLocalOf<HazeState?> { null }

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
    // CHANGE (motion): press-in scale (90ms, no bounce) instead of a ripple —
    // same feel as the floating nav. The scale sits outside the clip so the
    // whole circle shrinks.
    // Glass mode: real 30dp blur over the cover (when a blur layer is provided
    // by the top row) tinted with the 44% glass fill; otherwise just the fill.
    // Classic: the original dark scrim (black 40% + white 13%).
    val source = remember { MutableInteractionSource() }
    val haze   = LocalDetailHaze.current
    val glass  = GlassMode.enabled
    Box(
        contentAlignment = Alignment.Center,
        modifier         = Modifier
            .size(size)
            .pressScale(source, 0.92f)
            .clip(CircleShape)
            .then(
                when {
                    !glass        -> Modifier
                        .background(Color.Black.copy(alpha = 0.40f))
                        .background(Color.White.copy(alpha = 0.13f))
                    haze != null  -> Modifier.glassBlur(haze, CircleShape, GlassSurfaceDark)
                    else          -> Modifier.background(GlassSurfaceDark)
                }
            )
            .combinedClickable(
                interactionSource = source,
                indication        = null,
                onClick           = onClick,
                onLongClick       = onLongClick
            ),
    ) { content() }
}

// ── Play button ───────────────────────────────────────────────────────────────

@Composable
private fun PlayButton(label: String, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier            = Modifier
            .pressable(onClick = onClick, pressedScale = 0.95f)   // CHANGE (motion)
            .padding(horizontal = 20.dp, vertical = 6.dp),
    ) {
        ReaderCircleBtn(onClick = onClick, size = 76.dp) {
            Icon(
                imageVector        = SolarIcons.PlayBold,
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
                    // CHANGE (animated download icon): the plain spinner is replaced by the
                    // animated download icon (box draws, wave fills, checkmark, arrow
                    // returns — a 4s loop) for as long as THIS novel is downloading. When
                    // the status changes, the cross-fade above fades it out (120ms) while
                    // it keeps looping, then swaps in the next icon. 32dp canvas = the
                    // same ~24dp of artwork as the other icons in this button.
                    // `paper` = the dark disc colour the white checkmark knocks out to.
                    AnimatedDownloadIcon(
                        animating = true,
                        ink       = Color.White.copy(alpha = 0.9f),
                        paper     = Color(0xFF2B2E33),
                        size      = 32.dp,
                    )
                }
                DownloadStatus.QUEUED -> {
                    Icon(
                        SolarIcons.ClockCircle,
                        contentDescription = "Queued to download",
                        tint               = Color.White.copy(alpha = 0.9f),
                        modifier           = Modifier.size(22.dp),
                    )
                }
                DownloadStatus.PAUSED -> {
                    Icon(
                        SolarIcons.PauseCircle,
                        contentDescription = "Download paused — tap to resume",
                        tint               = Color.White.copy(alpha = 0.9f),
                        modifier           = Modifier.size(24.dp),
                    )
                }
                DownloadStatus.ERROR -> {
                    Icon(
                        SolarIcons.DangerCircle,
                        contentDescription = "Download failed — tap to retry",
                        tint               = Color(0xFFFF6B6B),
                        modifier           = Modifier.size(24.dp),
                    )
                }
                DownloadStatus.COMPLETE -> {
                    Icon(
                        SolarIcons.CheckCircleBold,
                        contentDescription = "Downloaded — tap to manage",
                        tint               = Color.White.copy(alpha = 0.9f),
                        modifier           = Modifier.size(24.dp),
                    )
                }
                null -> {
                    Icon(
                        SolarIcons.Download,
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
                "$total chapters",
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
                "Ch. ${rangeSelection.start.roundToInt()}–${rangeSelection.endInclusive.roundToInt()} · $rangeChapterCount",
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
            .then(
                if (GlassMode.enabled) Modifier.glassFill(RoundedCornerShape(20.dp), dark = true)
                else Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.White.copy(alpha = 0.08f))
                    .border(1.dp, accent.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
            )
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

// ── Skeleton ──────────────────────────────────────────────────────────────────
// One shared pulse for every block (a single transition, read in the DRAW phase,
// so the skeleton never recomposes per frame).

@Composable
private fun rememberSkeletonAlpha(): State<Float> {
    val t = rememberInfiniteTransition(label = "skeleton")
    return t.animateFloat(
        initialValue  = 0.07f,
        targetValue   = 0.17f,
        animationSpec = infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label         = "skeletonAlpha",
    )
}

private fun Modifier.skelBlock(alpha: State<Float>, shape: Shape = RoundedCornerShape(8.dp)): Modifier =
    this.clip(shape).drawBehind { drawRect(Color.White.copy(alpha = alpha.value)) }

// Mirrors the real layout: top buttons row, the long image card with its details
// (genre, title, 3 meta columns, rating, play + label), and the chapter header.
@Composable
private fun DetailSkeleton(bgTop: Color, modifier: Modifier = Modifier) {
    val a = rememberSkeletonAlpha()
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(bgTop, Color(0xFF000000)))),
    ) {
        // top row: back | download, bookmark, refresh
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Box(Modifier.size(48.dp).skelBlock(a, CircleShape))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(3) { Box(Modifier.size(48.dp).skelBlock(a, CircleShape)) }
            }
        }

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            val cardHeight = maxHeight * CARD_HEIGHT_FRACTION
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = CARD_TOP_PADDING, start = 16.dp, end = 16.dp)
                    .fillMaxWidth()
                    .height(cardHeight)
                    .skelBlock(a, RoundedCornerShape(24.dp)),
            ) {
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(Modifier.width(90.dp).height(14.dp).skelBlock(a))
                    Spacer(Modifier.height(12.dp))
                    Box(Modifier.fillMaxWidth(0.85f).height(30.dp).skelBlock(a))
                    Spacer(Modifier.height(8.dp))
                    Box(Modifier.fillMaxWidth(0.55f).height(30.dp).skelBlock(a))
                    Spacer(Modifier.height(24.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        repeat(3) {
                            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(Modifier.fillMaxWidth(0.8f).height(16.dp).skelBlock(a))
                                Spacer(Modifier.height(8.dp))
                                Box(Modifier.fillMaxWidth(0.5f).height(11.dp).skelBlock(a))
                            }
                        }
                    }
                    Spacer(Modifier.height(22.dp))
                    Box(Modifier.width(170.dp).height(20.dp).skelBlock(a))
                    Spacer(Modifier.height(26.dp))
                    Box(Modifier.size(76.dp).skelBlock(a, CircleShape))
                    Spacer(Modifier.height(10.dp))
                    Box(Modifier.width(110.dp).height(15.dp).skelBlock(a))
                }
            }
        }
    }
}

@Composable
private fun ChapterRowSkeleton(a: State<Float>) {
    Row(
        modifier          = Modifier
            .fillMaxWidth()
            .padding(vertical = 17.dp, horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(7.dp).skelBlock(a, CircleShape))
        Spacer(Modifier.width(12.dp))
        Box(Modifier.weight(1f).height(14.dp).skelBlock(a))
        Spacer(Modifier.width(24.dp))
        Box(Modifier.width(38.dp).height(12.dp).skelBlock(a))
    }
}

// ── Sort toggle ───────────────────────────────────────────────────────────────
// An arrow + three bars of falling length. The whole icon turns 180° when the
// order flips (arrow down + long bar on top = newest first).

@Composable
private fun SortToggle(newestFirst: Boolean, onClick: () -> Unit) {
    val turn by animateFloatAsState(
        targetValue   = if (newestFirst) 0f else 180f,
        animationSpec = tween(260, easing = FastOutSlowInEasing),
        label         = "sortTurn",
    )
    ReaderCircleBtn(onClick = onClick, size = 40.dp) {
        Canvas(
            Modifier
                .size(22.dp)
                .graphicsLayer { rotationZ = turn },
        ) {
            val u = size.width / 24f
            val sw = 2f * u
            val c  = Color.White.copy(alpha = 0.9f)
            fun line(x1: Float, y1: Float, x2: Float, y2: Float) =
                drawLine(c, Offset(x1 * u, y1 * u), Offset(x2 * u, y2 * u), strokeWidth = sw, cap = StrokeCap.Round)
            // arrow (pointing down)
            line(7f, 4f, 7f, 20f)
            line(3.5f, 16.5f, 7f, 20f)
            line(10.5f, 16.5f, 7f, 20f)
            // bars
            line(13f, 6f, 21f, 6f)
            line(13f, 12f, 18f, 12f)
            line(13f, 18f, 15.5f, 18f)
        }
    }
}

// ── Novel image card ──────────────────────────────────────────────────────────
// The Browse hero banner's design (rounded card, cover with a slow drift, dark
// bottom scrim, text over the bottom) with three changes:
//  • much taller — a long card — so the whole first screen's details fit on it;
//  • the drift is gentler (1.04→1.10 zoom, was 1.12→1.20): the point of this
//    card is a CLEAR cover, and heavy zoom is what blurred it;
//  • it carries the palette listener (the colours the screen is themed with).

private const val CARD_HEIGHT_FRACTION = 0.80f
private val CARD_TOP_PADDING = 72.dp          // clears the floating buttons row

@Composable
private fun NovelImageCard(
    novel: NovelEntity,
    onCoverLoaded: (android.graphics.drawable.Drawable) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val context = LocalContext.current
    val reducedMotion = remember(context) {
        Settings.Global.getFloat(
            context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f
        ) == 0f
    }
    val drift = rememberInfiniteTransition(label = "cardDrift")
    val zoom by drift.animateFloat(
        initialValue  = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(22000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label         = "cardZoom",
    )
    val panX by drift.animateFloat(
        initialValue  = -1f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(17000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label         = "cardPanX",
    )
    val panY by drift.animateFloat(
        initialValue  = -1f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(23000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label         = "cardPanY",
    )

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(Color(0xFF10141F)),
    ) {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(novel.coverUrl)
                .crossfade(400)
                // Palette.from() needs a software bitmap (hardware bitmaps throw).
                .allowHardware(false)
                .listener(onSuccess = { _, result -> onCoverLoaded(result.drawable) })
                .build(),
            contentDescription = novel.title,
            contentScale       = ContentScale.Crop,
            modifier           = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    if (!reducedMotion) {
                        val scale = 1.04f + 0.06f * zoom
                        scaleX = scale
                        scaleY = scale
                        translationX = panX * (scale - 1f) * size.width  * 0.4f
                        translationY = panY * (scale - 1f) * size.height * 0.4f
                    }
                },
        )

        // Scrim: clear on top (the cover stays sharp), dark at the bottom where the text sits
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0f    to Color.Black.copy(alpha = 0.04f),
                            0.40f to Color.Black.copy(alpha = 0.10f),
                            0.68f to Color.Black.copy(alpha = 0.62f),
                            1f    to Color.Black.copy(alpha = 0.90f),
                        )
                    )
                )
        )

        content()
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
    val inLibrary       by vm.inLibrary.collectAsStateWithLifecycle()
    val updateMessage   by vm.updateMessage.collectAsStateWithLifecycle()
    val refreshing      by vm.refreshing.collectAsStateWithLifecycle()

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

    val detailHaze = remember { HazeState() }

    Box(modifier = Modifier.fillMaxSize()) {

        // CHANGE (motion): Loading -> Error / Content cross-fades (240ms) instead of
        // a hard cut; the error block shakes once. The phase animates; the content
        // reads the latest state.
        val phase = when (state) {
            is DetailUiState.Loading -> 0
            is DetailUiState.Error   -> 1
            is DetailUiState.Success -> 2
        }
        Crossfade(
            targetState   = phase,
            modifier      = Modifier.fillMaxSize().glassSource(detailHaze),   // Glass mode: the layer the top buttons blur
            animationSpec = tween(Motion.BASE_MS)
        ) { p ->
        when (p) {

            0 -> {
                // Skeleton of the real layout while the novel loads
                DetailSkeleton(bgTop = FallbackTop)
            }

            1 -> {
                Box(
                    modifier         = Modifier.fillMaxSize().background(FallbackTop),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        modifier            = Modifier.errorShake(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            (state as? DetailUiState.Error)?.message ?: "Something went wrong",
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
                                .then(
                                    if (GlassMode.enabled) Modifier.glassFill(RoundedCornerShape(24.dp), dark = true)
                                    else Modifier.clip(RoundedCornerShape(24.dp)).background(Color.White.copy(alpha = 0.13f))
                                )
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

            else -> (state as? DetailUiState.Success)?.let { s ->
                CinematicDetail(
                    novel           = s.novel,
                    chapters        = s.chapters,
                    chaptersLoading = s.chaptersLoading,
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
        }

        // Refresh: the skeleton fades over the (already loaded) content for the
        // length of the update check; the content underneath keeps its state.
        AnimatedVisibility(
            visible  = refreshing,
            enter    = fadeIn(tween(180)),
            exit     = fadeOut(tween(300)),
            modifier = Modifier.fillMaxSize(),
        ) {
            DetailSkeleton(bgTop = bgTop)
        }

        // ── Floating top row: back + download + refresh — always on top ──
        // Reader-style 48dp circles with 24dp icons (same as the reader header).
        CompositionLocalProvider(LocalDetailHaze provides detailHaze) {
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
                    SolarIcons.ArrowLeft,
                    contentDescription = "Back",
                    tint               = Color.White.copy(alpha = 0.9f),
                    modifier           = Modifier.size(24.dp),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DownloadCircleBtn(
                    progress    = downloadProgress,
                    onStart     = vm::startOrResumeDownload,   // resume the original range, not the whole novel
                    onPause     = vm::cancelDownload,
                    onManage    = onDownloadsClick,
                    // CHANGE (perf fix): successState now exists as soon as
                    // metadata loads, before chapters have — also require
                    // !chaptersLoading so this can't open the range-slider
                    // sheet against an empty list during that window.
                    onLongPress = {
                        if (successState != null && !successState.chaptersLoading) showDownloadSheet = true
                    },
                )
                // Library membership — there was no way to add/remove a novel from here.
                ReaderCircleBtn(onClick = vm::toggleLibrary) {
                    Icon(
                        if (inLibrary) SolarIcons.BookmarkBold else SolarIcons.Bookmark,
                        contentDescription = if (inLibrary) "Remove from library" else "Add to library",
                        tint               = Color.White.copy(alpha = 0.9f),
                        modifier           = Modifier.size(24.dp),
                    )
                }
                ReaderCircleBtn(onClick = vm::checkForUpdates) {
                    // Animated while the update check runs, the static icon otherwise
                    AnimatedContent(
                        targetState    = refreshing,
                        transitionSpec = {
                            (fadeIn(tween(200)) + scaleIn(initialScale = 0.85f, animationSpec = tween(200)))
                                .togetherWith(fadeOut(tween(120)) + scaleOut(targetScale = 0.85f, animationSpec = tween(120)))
                        },
                        label = "refreshIconSwap",
                    ) { busy ->
                        if (busy) {
                            AnimatedRefreshIcon(
                                ink  = Color.White.copy(alpha = 0.9f),
                                size = 32.dp,
                            )
                        } else {
                            Icon(
                                SolarIcons.Refresh,
                                contentDescription = "Check updates",
                                tint               = Color.White.copy(alpha = 0.9f),
                                modifier           = Modifier.size(24.dp),
                            )
                        }
                    }
                }
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
        // here. Guarded on successState + !chaptersLoading so it can never
        // be requested before the chapter list has actually loaded.
        if (showDownloadSheet && successState != null && !successState.chaptersLoading) {
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
    chaptersLoading: Boolean,
    lastReadChapter: Int?,
    bgTop: Color,
    accent: Color,
    onReadChapter: (Int) -> Unit,
    onCoverLoaded: (android.graphics.drawable.Drawable) -> Unit,
) {
    val listState = rememberLazyListState()

    // Chapter list state: order (newest first by default) and how many rows are
    // composed. Only `visibleCount` rows ever exist — that's the freeze fix.
    var newestFirst  by remember { mutableStateOf(true) }
    var visibleCount by remember { mutableStateOf(CHAPTER_FIRST_BATCH) }
    val sortedChapters = remember(chapters, newestFirst) {
        if (newestFirst) chapters.sortedByDescending { it.num } else chapters.sortedBy { it.num }
    }
    val shownChapters = remember(sortedChapters, visibleCount) { sortedChapters.take(visibleCount) }

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

    // Latest chapter number: from the loaded list when we have it (the stored
    // "latest" text is sometimes a title, not a number), else digits from the text.
    val latestNum: String? = chapters.maxOfOrNull { it.num }?.toString()
        ?: Regex("(\\d+)").find(novel.latestChapter)?.groupValues?.get(1)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(bgTop, Color(0xFF000000)))),
    ) {
        // Blurred-look full-bleed backdrop (same cover, scaled up) behind the card
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(novel.coverUrl)
                .crossfade(500)
                .build(),
            contentDescription = null,
            contentScale       = ContentScale.Crop,
            modifier           = Modifier
                .fillMaxSize()
                .drawWithContent {
                    drawContent()
                    // Heavy scrim so the sharp card stands out and all text reads
                    drawRect(
                        Brush.verticalGradient(
                            0f    to Color.Black.copy(alpha = 0.25f),
                            0.25f to Color.Black.copy(alpha = 0.45f),
                            0.55f to Color.Black.copy(alpha = 0.78f),
                            0.75f to Color.Black.copy(alpha = 0.92f),
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

            // ── First screen: ONE viewport tall ───────────────────────────
            // The long image card sits under the floating buttons row, its
            // details bottom-anchored on it. The rest of the viewport is the
            // scroll-hint zone. Summary and chapters start below the fold.
            // wrapContentHeight(unbounded) so on a very short screen overflow
            // goes off the TOP of the card, never pushing play below it.
            item {
                Box(
                    modifier = Modifier
                        .fillParentMaxHeight()
                        .fillMaxWidth(),
                ) {
                    NovelImageCard(
                        novel         = novel,
                        onCoverLoaded = onCoverLoaded,
                        modifier      = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = CARD_TOP_PADDING, start = 16.dp, end = 16.dp)
                            .fillMaxWidth()
                            .fillParentMaxHeight(CARD_HEIGHT_FRACTION),
                    ) {
                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .wrapContentHeight(align = Alignment.Bottom, unbounded = true)
                                .padding(start = 14.dp, end = 14.dp, bottom = 18.dp),
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
                                    .padding(horizontal = 12.dp),
                            )

                            Spacer(Modifier.height(8.dp))

                            // Title
                            Text(
                                text       = novel.title,
                                color      = Color.White,
                                fontFamily = MontserratFamily,
                                fontSize   = 30.sp,
                                fontWeight = FontWeight.ExtraBold,
                                lineHeight = 34.sp,
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
                                    .padding(horizontal = 8.dp),
                            )

                            Spacer(Modifier.height(20.dp))

                            // Meta row: Status | Genre | Author — equal-weight
                            // columns so long text wraps/ellipsizes inside its
                            // own slot instead of widening the row.
                            Row(
                                modifier          = Modifier.fillMaxWidth(),
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
                                    label    = "AUTHOR",
                                    value    = novel.author.ifBlank { "—" },
                                    accent   = accent,
                                    modifier = Modifier.weight(1f),
                                )
                            }

                            Spacer(Modifier.height(20.dp))

                            // Star rating
                            StarRating(rawRating = novel.rating)

                            Spacer(Modifier.height(22.dp))

                            // Play button. Label under the icon:
                            //   in progress → "Continue Ch. N" (opens that chapter)
                            //   unread      → "Latest Ch. N"   (still opens Chapter 1)
                            val readLabel = when {
                                lastReadChapter != null -> "Continue Ch. $lastReadChapter"
                                latestNum != null       -> "Latest Ch. $latestNum"
                                else                    -> "Latest Ch."
                            }
                            val targetChapter = lastReadChapter
                                ?: chapters.minOfOrNull { it.num }
                                ?: 1

                            PlayButton(
                                label   = readLabel,
                                onClick = { onReadChapter(targetChapter) },
                            )
                        }
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
                            text       = novel.synopsis.ifBlank { "No summary." },
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
                            // Glass mode: nav-strength fill (51%), no border.
                            // Classic: black 50% + hairline.
                            .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                            .then(
                                if (GlassMode.enabled) Modifier.background(GlassBase.copy(alpha = GlassSpec.NAV_FILL))
                                else Modifier
                                    .background(Color.Black.copy(alpha = 0.50f))
                                    .border(
                                        width = 1.dp,
                                        color = Color.White.copy(alpha = 0.08f),
                                        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                                    )
                            ),
                    ) {
                        // Header: title | count + sort toggle
                        Row(
                            modifier              = Modifier
                                .fillMaxWidth()
                                .padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
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
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text       = if (chaptersLoading && chapters.isEmpty())
                                        "Loading…" else "${chapters.size} total",
                                    color      = accent.copy(alpha = 0.85f),
                                    fontFamily = MontserratFamily,
                                    fontSize   = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                )
                                Spacer(Modifier.width(10.dp))
                                SortToggle(
                                    newestFirst = newestFirst,
                                    onClick     = {
                                        newestFirst  = !newestFirst
                                        visibleCount = CHAPTER_FIRST_BATCH   // new order → back to the first batch
                                    },
                                )
                            }
                        }
                        HorizontalDivider(
                            color     = Color.White.copy(alpha = 0.10f),
                            thickness = 0.5.dp,
                        )

                        if (chaptersLoading && chapters.isEmpty()) {
                            val a = rememberSkeletonAlpha()
                            repeat(4) { ChapterRowSkeleton(a) }
                        }

                        // Only the rows revealed so far are composed — see-more adds 5.
                        shownChapters.forEach { chapter ->
                            key(chapter.num) {
                                ChapterRow(
                                    chapter = chapter,
                                    accent  = accent,
                                    onClick = { onReadChapter(chapter.num) },
                                )
                            }
                        }

                        // "See more" with fade gradient while more remain
                        if (visibleCount < sortedChapters.size) {
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
                                        .then(
                                            if (GlassMode.enabled) Modifier.glassFill(RoundedCornerShape(24.dp), dark = true)
                                            else Modifier.clip(RoundedCornerShape(24.dp)).background(Color.White.copy(alpha = 0.13f))
                                        )
                                        .clickable { visibleCount += CHAPTER_PAGE }
                                        .padding(horizontal = 22.dp, vertical = 10.dp),
                                ) {
                                    Text(
                                        text       = "See more",
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
