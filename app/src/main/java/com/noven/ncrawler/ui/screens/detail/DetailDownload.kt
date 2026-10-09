package com.noven.ncrawler.ui.screens.detail

import com.noven.ncrawler.ui.components.AnimatedDownloadIcon
import com.noven.ncrawler.ui.components.rememberReducedMotion
import com.noven.ncrawler.ui.components.Motion
import com.noven.ncrawler.viewmodel.DownloadNotice
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.ui.draw.shadow
import com.noven.ncrawler.ui.components.SolarIcons
import com.noven.ncrawler.ui.theme.GlassMode
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.PauseCircle
import com.noven.ncrawler.ui.components.BookmarkGold
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noven.ncrawler.data.db.DownloadProgress
import com.noven.ncrawler.data.db.DownloadStatus
import com.noven.ncrawler.data.scraper.ChapterLink
import com.noven.ncrawler.ui.components.glassFill
import com.noven.ncrawler.ui.theme.MontserratFamily
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.defaultMinSize
import com.noven.ncrawler.viewmodel.DetailViewModel
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

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
internal fun DownloadCircleBtn(
    progress: DownloadProgress?,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onManage: () -> Unit,
    // CHANGE (partial downloads): long-press opens the download-options
    // sheet (all / last N / by volume / custom range) regardless of the
    // current status — lets you top up an already-partial download too.
    onLongPress: () -> Unit,
    // CHANGE (updates): > 0 while a COMPLETE download has new chapters not on the phone yet.
    // The button then shows the "update" icon, and tapping it downloads just those chapters.
    updateCount: Int = 0,
    onUpdate: () -> Unit = {},
) {
    val status = progress?.status
    val showUpdate = status == DownloadStatus.COMPLETE && updateCount > 0
    val action = if (showUpdate) onUpdate else when (status) {
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
            targetState   = status to showUpdate,
            transitionSpec = {
                (fadeIn(tween(200)) + scaleIn(initialScale = 0.85f, animationSpec = tween(200)))
                    .togetherWith(fadeOut(tween(120)) + scaleOut(targetScale = 0.85f, animationSpec = tween(120)))
            },
            label = "downloadStatusIcon",
        ) { (s, upd) ->
            if (upd) {
                UpdateDownloadIcon(count = updateCount)
            } else when (s) {
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

// The "update novel" icon: the download arrow in gold with a small count badge.
@Composable
private fun UpdateDownloadIcon(count: Int) {
    Box(modifier = Modifier.size(28.dp), contentAlignment = Alignment.Center) {
        Icon(
            SolarIcons.Download,
            contentDescription = "Download $count new chapter${if (count == 1) "" else "s"}",
            tint               = BookmarkGold,
            modifier           = Modifier.size(24.dp),
        )
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 5.dp, y = (-5).dp)
                .defaultMinSize(minWidth = 15.dp, minHeight = 15.dp)
                .clip(CircleShape)
                .background(BookmarkGold)
                .padding(horizontal = 3.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text       = if (count > 9) "9+" else count.toString(),
                color      = Color(0xFF1B1405),
                fontFamily = MontserratFamily,
                fontSize   = 9.sp,
                fontWeight = FontWeight.ExtraBold,
            )
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
internal fun DownloadOptionsSheet(
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

// ── "Downloading <novel>" banner ──────────────────────────────────────────────
// Shown when a download is started, at the bottom above the snackbar spot. Solid
// dark card, Solar icons, Montserrat. Motion: enter = fade + slide up from below
// (critically damped spring, no bounce, ~400ms); exit = fade + a 12dp drop, 200ms
// — subtler than the enter. Auto-dismisses after 4.5s; "View" opens Downloads.
// Reduced motion: fade only.

private const val BANNER_VISIBLE_MS = 4500L

@Composable
internal fun DownloadBanner(
    notice: DownloadNotice?,
    accent: Color,
    onView: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val reduced = rememberReducedMotion()
    val density = LocalDensity.current
    // keep the last title on screen while the exit animation plays
    var shown by remember { mutableStateOf<DownloadNotice?>(null) }
    LaunchedEffect(notice) {
        if (notice != null) {
            shown = notice
            delay(BANNER_VISIBLE_MS)
            onDismiss()
        }
    }

    AnimatedVisibility(
        visible  = notice != null,
        modifier = modifier,
        enter = if (reduced) fadeIn(tween(120)) else
            fadeIn(tween(300, easing = Motion.EaseOut)) +
                slideInVertically(spring(dampingRatio = 1f, stiffness = Spring.StiffnessMediumLow)) { it },
        exit = if (reduced) fadeOut(tween(120)) else
            fadeOut(tween(200, easing = Motion.EaseOut)) +
                slideOutVertically(tween(200, easing = Motion.EaseOut)) { with(density) { 12.dp.roundToPx() } },
    ) {
        val shape = RoundedCornerShape(20.dp)
        Row(
            modifier = Modifier
                .padding(horizontal = 12.dp)
                .fillMaxWidth()
                // Near-opaque dark surface in BOTH modes: a transparent glass pill
                // disappeared over the cover. Shadow + hairline lift it off the page.
                .shadow(16.dp, shape)
                .clip(shape)
                .background(Color(0xFF12151C).copy(alpha = 0.97f))
                .border(1.dp, Color.White.copy(alpha = 0.14f), shape)
                .padding(start = 12.dp, end = 6.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.22f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    SolarIcons.DownloadBold,
                    contentDescription = null,
                    tint               = accent,
                    modifier           = Modifier.size(22.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text          = "DOWNLOADING",
                    color         = accent,
                    fontFamily    = MontserratFamily,
                    fontSize      = 11.sp,
                    fontWeight    = FontWeight.SemiBold,
                    letterSpacing = 1.5.sp,
                )
                Text(
                    text       = shown?.novelTitle.orEmpty(),
                    color      = Color.White,
                    fontFamily = MontserratFamily,
                    fontSize   = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines   = 1,
                    overflow   = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .clickable {
                        onDismiss()
                        onView()
                    }
                    .padding(start = 12.dp, end = 8.dp, top = 9.dp, bottom = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text       = "View",
                    color      = Color.White,
                    fontFamily = MontserratFamily,
                    fontSize   = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.width(2.dp))
                Icon(
                    SolarIcons.ArrowRight,
                    contentDescription = "View download",
                    tint               = Color.White.copy(alpha = 0.9f),
                    modifier           = Modifier.size(18.dp),
                )
            }
        }
    }
}
