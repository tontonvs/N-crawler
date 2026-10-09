package com.noven.ncrawler.ui.screens.detail

import com.noven.ncrawler.ui.components.rememberReducedMotion
import com.noven.ncrawler.ui.components.Motion
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.launch
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import android.provider.Settings
import com.noven.ncrawler.ui.components.SolarIcons
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.CompositionLocalProvider
import dev.chrisbanes.haze.HazeState
import com.noven.ncrawler.ui.theme.GlassMode
import com.noven.ncrawler.ui.components.glassSource
import android.graphics.drawable.BitmapDrawable
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import com.noven.ncrawler.ui.components.FavouritePink
import com.noven.ncrawler.ui.components.BookmarkGold
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.palette.graphics.Palette
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.noven.ncrawler.data.db.NovelEntity
import com.noven.ncrawler.data.local.DominantColorStore
import com.noven.ncrawler.data.scraper.ChapterLink
import com.noven.ncrawler.ui.components.errorShake
import com.noven.ncrawler.ui.theme.GlassSpec
import com.noven.ncrawler.ui.theme.GlassBase
import com.noven.ncrawler.ui.components.glassFill
import com.noven.ncrawler.ui.theme.MontserratFamily
import com.noven.ncrawler.viewmodel.DetailUiState
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.graphics.TransformOrigin
import com.noven.ncrawler.viewmodel.DetailViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first

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

// Chapter list loading. The first batch shows at once; ONE tap on "See more" then
// streams in the rest in growing batches (10, 20, 30, 30 …), each preceded by a
// short skeleton beat, so a 3000-chapter novel never lands in one frame.
private const val CHAPTER_FIRST_BATCH = 10
private const val CHAPTER_BATCH_STEP  = 10
private const val CHAPTER_BATCH_MAX   = 30
private const val CHAPTER_BATCH_GAP_MS = 70L

// Blur layer for the top-row circle buttons (Glass mode). Provided only to the
// top row, which is a sibling of the cover layer — never to its descendants.
internal val LocalDetailHaze = compositionLocalOf<HazeState?> { null }

// ── Chapter panel backgrounds ─────────────────────────────────────────────────
// The chapter list used to be ONE tall item (every row measured at once — the
// freeze). Rows are now real lazy items, so each piece paints its own slice of the
// panel: the header has the rounded top, the rows are flat, same fill throughout.

@Composable
private fun chapterPanel(top: Boolean): Modifier {
    val shape = if (top) RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp) else RectangleShape
    return if (GlassMode.enabled) {
        // Glass mode: nav-strength fill (51%), no border.
        Modifier.clip(shape).background(GlassBase.copy(alpha = GlassSpec.NAV_FILL))
    } else {
        // Classic: black 50% + hairline (top: full outline; rows: the two sides)
        val line = Color.White.copy(alpha = 0.08f)
        Modifier
            .clip(shape)
            .background(Color.Black.copy(alpha = 0.50f))
            .then(
                if (top) Modifier.border(1.dp, line, shape)
                else Modifier.drawBehind {
                    drawLine(line, Offset(0.5f, 0f), Offset(0.5f, size.height), strokeWidth = 1.dp.toPx())
                    drawLine(line, Offset(size.width - 0.5f, 0f), Offset(size.width - 0.5f, size.height), strokeWidth = 1.dp.toPx())
                }
            )
    }
}

// ── Novel image card ──────────────────────────────────────────────────────────
// The Browse hero banner's design (rounded card, cover with a slow drift, dark
// bottom scrim, text over the bottom) with three changes:
//  • much taller — a long card — so the whole first screen's details fit on it;
//  • the drift is gentler (1.04→1.10 zoom, was 1.12→1.20): the point of this
//    card is a CLEAR cover, and heavy zoom is what blurred it;
//  • it carries the palette listener (the colours the screen is themed with).

internal const val CARD_HEIGHT_FRACTION = 0.80f
internal val CARD_TOP_PADDING = 72.dp          // clears the floating buttons row

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
    val downloadNotice  by vm.downloadNotice.collectAsStateWithLifecycle()
    val newRange        by vm.newRange.collectAsStateWithLifecycle()
    val downloadedNums  by vm.downloadedNums.collectAsStateWithLifecycle()

    // CHANGE (partial downloads): only non-null once the novel and its
    // chapter list have actually loaded — the download-options sheet needs
    // the chapter list (for volume/range bounds) so it can only open then.
    val successState = state as? DetailUiState.Success
    var showDownloadSheet by remember { mutableStateOf(false) }

    // New chapters (unread range) and how many of them are still missing from the phone.
    val hasDownload = downloadProgress != null
    val missingNew = remember(newRange, successState?.chapters, downloadedNums) {
        val r = newRange
        if (r == null) 0
        else successState?.chapters?.count { it.num in r.from..r.to && it.num !in downloadedNums } ?: 0
    }

    val context = LocalContext.current
    // Each new heart increments this; HeartBurst replays its animation per change.
    var heartBurst by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) { vm.favouriteBurst.collect { heartBurst++ } }

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
                    entered         = !refreshing,
                    chapters        = s.chapters,
                    chaptersLoading = s.chaptersLoading,
                    lastReadChapter = s.lastReadChapter,
                    newFrom         = newRange?.from ?: 0,
                    newTo           = newRange?.to ?: 0,
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
                    updateCount = if (hasDownload) missingNew else 0,
                    onUpdate    = vm::downloadNewChapters,
                )
                // Favourite heart (was a bookmark). Reddish pink when filled; adding one
                // plays the big heart pop (HeartBurst below), then the notice.
                ReaderCircleBtn(onClick = vm::toggleLibrary) {
                    Icon(
                        if (inLibrary) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        contentDescription = if (inLibrary) "Remove from favourites" else "Add to favourites",
                        tint               = if (inLibrary) FavouritePink else Color.White.copy(alpha = 0.9f),
                        modifier           = Modifier.size(24.dp),
                    )
                }
                RefreshButton(refreshing = refreshing, onClick = vm::checkForUpdates)
            }
        }
        }

        // Info bubble under the download button: how many chapters are new. With a download
        // it shows while some new chapters are still missing from the device (the button
        // beside it turns into the update icon); without one it just informs.
        val range = newRange
        val showBubble = range != null && successState != null && !refreshing && !showDownloadSheet &&
            (if (hasDownload) missingNew > 0 else true)
        AnimatedVisibility(
            visible  = showBubble,
            enter    = fadeIn(tween(220)) + scaleIn(initialScale = 0.8f, transformOrigin = TransformOrigin(1f, 0f)),
            exit     = fadeOut(tween(150)),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                // below the 56dp button row; right edge lines up with the download button
                .padding(top = 58.dp, end = 124.dp),
        ) {
            val n = range?.size ?: 0
            NewChaptersBubble(
                label     = "$n new chapter${if (n == 1) "" else "s"}",
                onDismiss = vm::dismissUpdate,
            )
        }

        // Big heart pop in the middle of the screen when a novel is favourited.
        HeartBurst(
            trigger  = heartBurst,
            modifier = Modifier.align(Alignment.Center)
        )

        // "Downloading <novel>" banner — just under the floating buttons row
        DownloadBanner(
            notice    = downloadNotice,
            accent    = accent,
            onView    = onDownloadsClick,
            onDismiss = vm::clearDownloadNotice,
            modifier  = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 84.dp),   // sits above the snackbar's spot
        )

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
    newFrom: Int,
    newTo: Int,
    bgTop: Color,
    accent: Color,
    onReadChapter: (Int) -> Unit,
    onCoverLoaded: (android.graphics.drawable.Drawable) -> Unit,
    entered: Boolean = true,          // false while a refresh skeleton covers the screen
) {
    val listState = rememberLazyListState()

    // Entrance: starts one frame after first composition (so frame 0 is the hidden
    // state), and re-arms whenever `entered` flips back on after a refresh.
    var started by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { started = true }
    val go = started && entered
    val pCard   = staggerProgress(go, 0)
    val pGenre  = staggerProgress(go, 1)
    val pTitle  = staggerProgress(go, 2)
    val pMeta   = staggerProgress(go, 3)
    val pRating = staggerProgress(go, 4)
    val pPlay   = staggerProgress(go, 5)

    // Chapter list state: order (newest first by default) and how many rows are
    // composed. Only `visibleCount` rows ever exist — that's the freeze fix.
    var newestFirst  by remember { mutableStateOf(true) }
    var visibleCount by remember { mutableStateOf(CHAPTER_FIRST_BATCH) }
    var expanded     by remember { mutableStateOf(false) }   // "See more" tapped (once)
    val scope  = rememberCoroutineScope()
    val reducedMotion = rememberReducedMotion()
    val sortedChapters = remember(chapters, newestFirst) {
        if (newestFirst) chapters.sortedByDescending { it.num } else chapters.sortedBy { it.num }
    }
    val shownChapters = remember(sortedChapters, visibleCount) { sortedChapters.take(visibleCount) }

    // After the one "See more" tap, stream in the rest: 10, then 20, then 30 at a
    // time. Each round shows the skeleton rows for a beat (the assumed place of the
    // next batch), then swaps them for the real rows and yields a frame so the list
    // never composes a huge block at once. Restarts if the order or list changes.
    LaunchedEffect(expanded, sortedChapters) {
        if (!expanded) return@LaunchedEffect
        var batch = CHAPTER_BATCH_STEP
        while (visibleCount < sortedChapters.size) {
            if (!reducedMotion) delay(CHAPTER_BATCH_GAP_MS)
            visibleCount = minOf(sortedChapters.size, visibleCount + batch)
            batch = minOf(batch + CHAPTER_BATCH_STEP, CHAPTER_BATCH_MAX)
            withFrameNanos { }
        }
    }

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
                            .fillParentMaxHeight(CARD_HEIGHT_FRACTION)
                            .staggerIn(pCard),
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
                                    .padding(horizontal = 12.dp)
                                    .staggerIn(pGenre),
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
                                    .padding(horizontal = 8.dp)
                                    .staggerIn(pTitle),
                            )

                            Spacer(Modifier.height(20.dp))

                            // Meta row: Status | Genre | Author — equal-weight
                            // columns so long text wraps/ellipsizes inside its
                            // own slot instead of widening the row.
                            Row(
                                modifier          = Modifier.fillMaxWidth().staggerIn(pMeta),
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
                            StarRating(rawRating = novel.rating, modifier = Modifier.staggerIn(pRating))

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

                            Box(Modifier.staggerIn(pPlay)) {
                                PlayButton(
                                    label   = readLabel,
                                    onClick = { onReadChapter(targetChapter) },
                                )
                            }
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

            // ── Chapter list (items 2…) — header, then one LAZY item per row, then
            // the footer. Rows are lazy so only the ones on screen are composed.
            item(key = "chapters-header") {
                RevealOnScroll(listState, itemIndex = CHAPTERS_INDEX) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(chapterPanel(top = true)),
                    ) {
                        // Header: title | count + sort toggle
                        Row(
                            modifier              = Modifier
                                .fillMaxWidth()
                                .padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment     = Alignment.CenterVertically,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text       = "Chapters",
                                    color      = Color.White,
                                    fontFamily = MontserratFamily,
                                    fontSize   = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                                if (newTo > 0) {
                                    Spacer(Modifier.width(8.dp))
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(BookmarkGold)
                                            .padding(horizontal = 7.dp, vertical = 2.dp),
                                    ) {
                                        Text(
                                            text       = "${newTo - newFrom + 1} new",
                                            color      = Color(0xFF1B1405),
                                            fontFamily = MontserratFamily,
                                            fontSize   = 11.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                        )
                                    }
                                }
                            }
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
                                        // if you were deep in the list, land back at the top of it
                                        if (listState.firstVisibleItemIndex > CHAPTERS_INDEX + 1) {
                                            scope.launch { listState.scrollToItem(CHAPTERS_INDEX) }
                                        }
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
                    }
                }
            }

            items(items = shownChapters, key = { "ch-${it.num}" }) { chapter ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(chapterPanel(top = false)),
                ) {
                    Column {
                        ChapterRow(
                            chapter = chapter,
                            accent  = accent,
                            onClick = { onReadChapter(chapter.num) },
                            isNew   = newTo > 0 && chapter.num in newFrom..newTo,
                        )
                    }
                }
            }

            item(key = "chapters-footer") {
                val remaining = sortedChapters.size - visibleCount
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(chapterPanel(top = false)),
                ) {
                    if (remaining > 0) {
                        if (expanded) {
                            // The assumed place of the next batch
                            val a = rememberSkeletonAlpha()
                            repeat(minOf(3, remaining)) { ChapterRowSkeleton(a) }
                        } else {
                            // "See more" with fade gradient — tapped ONCE
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
                                        .clickable { expanded = true }
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
                    }
                    Spacer(Modifier.height(8.dp))
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
