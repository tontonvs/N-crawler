package com.noven.ncrawler.ui.screens.browse

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.noven.ncrawler.ui.components.AppPullToRefresh
import com.noven.ncrawler.ui.components.Motion
import com.noven.ncrawler.ui.components.ShimmerScope
import com.noven.ncrawler.ui.components.skeleton
import com.noven.ncrawler.ui.components.staggerIn
import com.noven.ncrawler.ui.components.rememberReducedMotion
import com.noven.ncrawler.ui.components.collapseOf
import com.noven.ncrawler.ui.components.MORPH_COLLAPSE_AT
import com.noven.ncrawler.ui.components.MORPH_EXPAND_BELOW
import com.noven.ncrawler.ui.components.BAR_CONTENT_HEIGHT
import com.noven.ncrawler.ui.theme.*
import com.noven.ncrawler.viewmodel.BrowseUiState
import com.noven.ncrawler.viewmodel.BrowseViewModel
import com.noven.ncrawler.viewmodel.ContinueReadingInfo
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.haze

// CHANGE (UI polish): tokens for the collapsing top bar, the recent-read backdrop,
// the curved content panel and the glass search overlay.
private val PanelRadius            = 28.dp   // curve of the content panel's top corners
private const val BACKDROP_DECODE_W = 200    // backdrop cover is decoded this small, then scaled up
private const val BACKDROP_DECODE_H = 300    // = the "very slight blur" (bigger = sharper, smaller = softer)

@Composable
fun BrowseScreen(
    onNovelClick: (slug: String) -> Unit,
    onContinueReading: ((slug: String, chapterNum: Int) -> Unit)? = null,
    onDownloadsClick: (() -> Unit)? = null,
    onGenreClick: ((genre: String) -> Unit)? = null,
    // CHANGE: new optional callback — powers the trailing "See More" genre
    // card and the "See all" header link, both of which now route to the
    // existing DiscoverScreen (which already lists every genre as a card).
    onDiscoverClick: (() -> Unit)? = null,
    vm: BrowseViewModel = viewModel()
) {
    val browseState      by vm.browseState.collectAsStateWithLifecycle()
    val popularState      by vm.popularState.collectAsStateWithLifecycle()
    val continueReading  by vm.continueReading.collectAsStateWithLifecycle()
    val recentlyReading  by vm.recentlyReading.collectAsStateWithLifecycle()
    val isRefreshing     by vm.isRefreshing.collectAsStateWithLifecycle()
    // CHANGE (top bar): true while a download is running → animates the top-bar icons.
    val downloading      by vm.isDownloading.collectAsStateWithLifecycle()
    // CHANGE (progress ring): overall progress of the running downloads, 0..1.
    val downloadFraction by vm.downloadFraction.collectAsStateWithLifecycle()

    // The list's scroll state lives here (not inside BrowseContent) so the top bar
    // can follow the scroll position and collapse with it.
    val listState = rememberLazyListState()

    // CHANGE (top bar): the page content is the blur source for the glass top bar
    // (same Haze setup as the floating nav and the search overlay).
    val topHaze = remember { HazeState() }
    val glass   = rememberBarGlass()

    val density     = LocalDensity.current
    val statusBarDp = with(density) { WindowInsets.statusBars.getTop(density).toDp() }
    val barHeight   = statusBarDp + BAR_CONTENT_HEIGHT
    val barHeightPx = with(density) { barHeight.toPx() }
    val pageBg      = MaterialTheme.colorScheme.background
    val reducedMotion = rememberReducedMotion()

    // CHANGE (morph): ONE glass element is either the full-width bar (0f) or the "Browse"
    // capsule (1f); [morph] is the animated position between the two. The scroll only
    // flips [collapsed] (two thresholds = hysteresis, so a finger hovering on the boundary
    // can't make it flicker); the morph itself is time-based and interruptible — flip it
    // mid-flight and Animatable retargets from where it currently is.
    var collapsed by remember { mutableStateOf(false) }
    LaunchedEffect(listState, barHeightPx) {
        snapshotFlow { collapseOf(listState, barHeightPx) }
            .collect { c -> collapsed = if (collapsed) c > MORPH_EXPAND_BELOW else c >= MORPH_COLLAPSE_AT }
    }
    val morph = remember { Animatable(0f) }
    LaunchedEffect(collapsed, reducedMotion) {
        val target = if (collapsed) 1f else 0f
        if (reducedMotion) {
            morph.snapTo(target)             // system animations off: no travel, just the end state
        } else {
            // forming the capsule = something arriving (340ms); growing back = settling (300ms)
            morph.animateTo(
                target,
                tween(if (collapsed) Motion.ENTER_MS else Motion.SCREEN_MS, easing = Motion.EaseOut)
            )
        }
    }

    // Light background fills the entire screen
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(pageBg)
    ) {
        // Search lives exclusively in the SearchOverlay (opened from the
        // search FAB in the floating nav) — the homepage itself is browse-only, no inline bar.
        // Pull down to reload the feed. The current rows stay on screen while
        // it loads (a silent refresh), unlike Retry / a source switch, which
        // show the skeleton.
        // The list fills the whole screen and scrolls UNDER the top bar (drawn on top,
        // below). The pull indicator's offset is the bar's height, so at rest it looks
        // identical. CHANGE (top bar): .haze(topHaze) makes this the layer the glass
        // bar blurs — the hero's blurred backdrop now continues up behind the bar.
        AppPullToRefresh(
            isRefreshing       = isRefreshing,
            onRefresh          = vm::refresh,
            failures           = vm.refreshFailed,
            indicatorTopOffset = barHeight,
            modifier           = Modifier.fillMaxSize().haze(topHaze)
        ) {
            BrowseContent(
                state             = browseState,
                popularState      = popularState,
                onNovelClick      = onNovelClick,
                onRetry           = vm::loadHomepage,
                onGenreClick      = onGenreClick ?: {},
                onDiscoverClick   = onDiscoverClick,
                continueReading   = continueReading,
                onContinueReading = onContinueReading,
                recentlyReading   = recentlyReading,
                listState         = listState,
                topInset          = barHeight
            )
        }

        // CHANGE: the status-bar scrim (a page-colour gradient behind the clock/battery icons
        // once the bar became the pill) is gone — on scroll, the pill and the download button
        // are the ONLY things drawn up top; content scrolls freely behind the status bar.

        // ── Top chrome: the glass bar (logo + download button) that morphs into the
        // "Browse" capsule as you scroll, and back (see TopChrome).
        TopChrome(
            onDownloadsClick = onDownloadsClick,
            downloading      = downloading,
            collapsed        = collapsed,
            progress         = downloadFraction,
            glass            = glass,
            hazeState        = topHaze,
            morph            = { morph.value },
            modifier         = Modifier.align(Alignment.TopCenter)
        )
    }
}

// ── Browse Content ────────────────────────────────────────────────────────────
@Composable
private fun BrowseContent(
    state: BrowseUiState,
    popularState: BrowseUiState,
    onNovelClick: (String) -> Unit,
    onRetry: () -> Unit,
    onGenreClick: (String) -> Unit,
    onDiscoverClick: (() -> Unit)?,
    continueReading: ContinueReadingInfo?,
    onContinueReading: ((slug: String, chapterNum: Int) -> Unit)?,
    recentlyReading: List<ContinueReadingInfo>,
    // CHANGE (UI polish #1): hoisted so the top bar can follow the scroll; topInset = the
    // top bar's height — the list scrolls under the bar, so its content starts below it.
    listState: LazyListState,
    topInset: Dp
) {
    // CHANGE (motion): skeleton -> content is a short cross-fade (not a hard cut),
    // and inside the content the top blocks stack in one after another (once).
    // Errors get a quick shake. The phase is what animates; the content itself
    // reads the latest state.
    val phase = when (state) {
        is BrowseUiState.Loading -> 0
        is BrowseUiState.Error, is BrowseUiState.Empty -> 1
        is BrowseUiState.Success -> 2
    }
    // A reload that goes through the skeleton (source switch / Retry) must not come back
    // scrolled to the old position now that the list state lives outside this screen.
    LaunchedEffect(phase) { if (phase != 2) listState.scrollToItem(0) }
    Crossfade(
        targetState   = phase,
        modifier      = Modifier.fillMaxSize(),
        animationSpec = tween(Motion.QUICK_MS + 40)
    ) { p ->
    when (p) {
        0 -> ShimmerScope { BrowseSkeleton(topInset) }
        1 -> BrowseError((state as? BrowseUiState.Error)?.message ?: "No novels found", onRetry, topInset)
        else -> (state as? BrowseUiState.Success)?.let { success ->
            val novels = success.novels
            val hero   = novels.firstOrNull()

            // Group into up to 5 genre rows each, samples of ~10 per genre —
            // "Latest" from novels.drop(1), "Popular" from the separate
            // /sort/most-popular fetch (a genuinely different source, not a
            // slice of the same list).
            // FIX: some sources (NovelArrow's homepage/listing scrape) can't
            // supply per-card genre tags — those cards come back with
            // genres = "" and groupByTopGenres() drops them entirely, which
            // silently renders NOTHING even though novels/popularNovels are
            // non-empty. Each section below now falls back to one flat,
            // ungrouped row instead of disappearing whenever grouping
            // yields zero rows.
            // FIX: drop(1) ran unconditionally, but the hero above only takes the
            // first novel when there is no Continue Reading card — so with one
            // showing, the newest release was silently missing from Latest.
            val latestList        = if (continueReading != null && onContinueReading != null) novels else novels.drop(1)
            val latestGenreRows   = remember(novels) { groupByTopGenres(latestList) }
            val showFlatLatest    = latestGenreRows.isEmpty() && latestList.isNotEmpty()
            val popularNovels     = (popularState as? BrowseUiState.Success)?.novels ?: emptyList()
            val popularGenreRows  = remember(popularNovels) { groupByTopGenres(popularNovels) }
            val showFlatPopular   = popularGenreRows.isEmpty() && popularNovels.isNotEmpty()

            // CHANGE: showcase genres now come from the real data (top 8 by
            // how many novels carry them) instead of a hardcoded 3-item list.
            // perGenre=1 because the showcase only needs genre NAMES, not
            // the novel samples — cheap to compute independently of the
            // other two groupings above.
            val showcaseGenres = remember(novels) {
                groupByTopGenres(novels, maxGenres = 8, perGenre = 1).map { it.first }
            }

            // Order in the entrance stack. Only the first screenful animates; rows
            // further down just appear as you scroll to them (no lag while flicking).
            val hasRecent   = recentlyReading.isNotEmpty() && onContinueReading != null
            val genreOrder  = if (hasRecent) 2 else 1
            val latestOrder = genreOrder + (if (showcaseGenres.isNotEmpty()) 1 else 0)

            // CHANGE (UI polish #2/#4): the novel whose cover is blurred behind the hero +
            // Recently Read area = the most recently read one (the hero IS that novel when
            // there is reading history). With no history it falls back to the featured
            // novel in the hero, so the area never looks different from one launch to the next.
            val resumeActive = continueReading != null && onContinueReading != null
            val topNovel     = if (resumeActive) continueReading?.novel else hero
            val panelColor   = MaterialTheme.colorScheme.background

            LazyColumn(
                state          = listState,
                modifier       = Modifier.fillMaxSize(),
                // CHANGE (top bar): no top padding any more — the bar's height is added INSIDE
                // the first item (see the spacer below), so the hero's blurred backdrop starts
                // at the very top of the screen and shows through the glass bar.
                contentPadding = PaddingValues(bottom = 120.dp)
            ) {
                // Everything from the genre showcase down sits on ONE panel that has the
                // page colour and a curved top. The first panel item gets the curve and
                // is pulled up by the curve's radius so the backdrop shows through its
                // corners; every item after it just continues the same colour.
                // On the first item the panel (layout/clip/background) wraps the entrance
                // stagger, not the other way round: a fading layer would clip the curved
                // corners that overhang the item, so only the CONTENT fades and rises.
                var panelOpen = false
                fun panel(): Modifier {
                    val m = if (panelOpen) Modifier.background(panelColor)
                            else Modifier.sheetTop(PanelRadius, panelColor)
                    panelOpen = true
                    return m
                }

                // ── Hero + Recently Read, on the blurred backdrop. The hero resumes the
                // last-read novel at its exact chapter when there's reading history;
                // otherwise it features the top of the feed and opens its detail page.
                // Recently Read = everything read EXCEPT the hero item. Tapping a card
                // opens the reader at the exact chapter; the small "i" badge opens the
                // detail page instead. No center play button — the whole card (minus
                // the "i") is the tap target.
                if (topNovel == null) {
                    item(key = "sec_top_inset") { Spacer(Modifier.height(topInset)) }
                }
                if (topNovel != null) {
                    item(key = "sec_top") {
                        Box(Modifier.fillMaxWidth()) {
                            BlurredBackdrop(url = topNovel.coverUrl, modifier = Modifier.matchParentSize())
                            Column {
                                Spacer(Modifier.height(topInset + 12.dp))
                                Box(Modifier.staggerIn(0, maxAnimated = 5)) {
                                    if (resumeActive && continueReading != null && onContinueReading != null) {
                                        HeroBanner(
                                            novel         = continueReading.novel,
                                            resumeChapter = continueReading.progress.lastChapterNum,
                                            onClick       = {
                                                onContinueReading(
                                                    continueReading.novel.slug,
                                                    continueReading.progress.lastChapterNum
                                                )
                                            }
                                        )
                                    } else {
                                        HeroBanner(
                                            novel         = topNovel,
                                            resumeChapter = null,
                                            onClick       = { onNovelClick(topNovel.slug) }
                                        )
                                    }
                                }
                                if (hasRecent && onContinueReading != null) {
                                    Column(Modifier.staggerIn(1, maxAnimated = 5)) {
                                        Spacer(Modifier.height(24.dp))
                                        RecentlyReadRow(
                                            items          = recentlyReading,
                                            onOpenReader   = onContinueReading,
                                            onOpenDetail   = onNovelClick
                                        )
                                    }
                                }
                                // visible gap above the panel + the part the panel overlaps
                                Spacer(Modifier.height(PanelRadius + 20.dp))
                            }
                        }
                    }
                }

                // ── Genre showcase — decorative gradient-font cards, now a
                // horizontally scrolling row (up to 8 genres, 6 alternating
                // decorative styles) ending in a "See More" card that opens
                // the existing DiscoverScreen (every genre, full list).
                if (showcaseGenres.isNotEmpty()) {
                    val panelMod = panel()
                    item(key = "sec_genre") {
                        Column(panelMod.staggerIn(genreOrder, distance = 8.dp, maxAnimated = 5)) {
                            Spacer(Modifier.height(24.dp))
                            GenreShowcaseRow(
                                genres          = showcaseGenres,
                                onGenreClick    = onGenreClick,
                                onDiscoverClick = onDiscoverClick
                            )
                        }
                    }
                }

                // ── Latest Updates — up to 5 genre rows, horizontal samples,
                // a chevron on each opens that genre's full list ───────────────
                if (latestGenreRows.isNotEmpty()) {
                    val panelMod = panel()
                    item(key = "sec_latest_header") {
                        Column(panelMod.staggerIn(latestOrder, distance = 8.dp, maxAnimated = 5)) {
                            Spacer(Modifier.height(24.dp))
                            SectionHeader("Latest Updates")
                        }
                    }
                    itemsIndexed(latestGenreRows, key = { _, it -> "latest_${it.first}" }) { i, (genre, rowNovels) ->
                        Column(Modifier.staggerIn(latestOrder + 1 + i, maxAnimated = 5).background(panelColor)) {
                            Spacer(Modifier.height(16.dp))
                            GenreRow(
                                genre        = genre,
                                novels       = rowNovels,
                                onNovelClick = onNovelClick,
                                onSeeMore    = { onGenreClick(genre) }
                            )
                        }
                    }
                } else if (showFlatLatest) {
                    // FIX: no genre data to group by (e.g. NovelArrow) —
                    // show everything fetched as one flat row instead of
                    // nothing at all.
                    val panelMod = panel()
                    item(key = "sec_latest_flat_header") {
                        Column(panelMod) {
                            Spacer(Modifier.height(24.dp))
                            SectionHeader("Latest Updates")
                        }
                    }
                    item(key = "sec_latest_flat_row") {
                        Column(Modifier.background(panelColor)) {
                            Spacer(Modifier.height(16.dp))
                            GenreRow(
                                genre        = "Latest",
                                novels       = latestList,
                                onNovelClick = onNovelClick,
                                onSeeMore    = null
                            )
                        }
                    }
                }

                // ── Popular — same pattern, sourced from /sort/most-popular ──
                if (popularGenreRows.isNotEmpty()) {
                    val panelMod = panel()
                    item(key = "sec_popular_header") {
                        Column(panelMod) {
                            Spacer(Modifier.height(28.dp))
                            SectionHeader("Popular")
                        }
                    }
                    items(popularGenreRows, key = { "popular_${it.first}" }) { (genre, rowNovels) ->
                        Column(Modifier.background(panelColor)) {
                            Spacer(Modifier.height(16.dp))
                            GenreRow(
                                genre        = genre,
                                novels       = rowNovels,
                                onNovelClick = onNovelClick,
                                onSeeMore    = { onGenreClick(genre) }
                            )
                        }
                    }
                    item(key = "sec_popular_end") { Spacer(Modifier.fillMaxWidth().height(20.dp).background(panelColor)) }
                } else if (showFlatPopular) {
                    // FIX: same fallback as Latest — flat row when there's
                    // no genre data to group by.
                    val panelMod = panel()
                    item(key = "sec_popular_flat_header") {
                        Column(panelMod) {
                            Spacer(Modifier.height(28.dp))
                            SectionHeader("Popular")
                        }
                    }
                    item(key = "sec_popular_flat_row") {
                        Column(Modifier.background(panelColor)) {
                            Spacer(Modifier.height(16.dp))
                            GenreRow(
                                genre        = "Popular",
                                novels       = popularNovels,
                                onNovelClick = onNovelClick,
                                onSeeMore    = null
                            )
                        }
                    }
                    item(key = "sec_popular_flat_end") { Spacer(Modifier.fillMaxWidth().height(20.dp).background(panelColor)) }
                }
            }
        }
    }
    }
}

// ── Curved content panel ──────────────────────────────────────────────────────
// CHANGE (UI polish #4): the panel behind the genre showcase / Latest Updates / Popular.
// Its top corners are rounded (an "n"-shaped arch). It is pulled UP over the item above
// by [radius]: the item reports a height that many dp shorter and draws that many dp
// higher, so the blurred backdrop is what shows in the two cut-off corners and the
// panel reads as a sheet sliding over the Recently Read area. Only the top [radius] dp
// of the item is drawn outside its measured bounds, and that strip is plain spacing —
// nothing tappable lives there.
private fun Modifier.sheetTop(radius: Dp, color: Color): Modifier =
    this
        .layout { measurable, constraints ->
            val r = radius.roundToPx()
            val placeable = measurable.measure(constraints)
            layout(placeable.width, (placeable.height - r).coerceAtLeast(0)) {
                placeable.place(0, -r)
            }
        }
        .clip(RoundedCornerShape(topStart = radius, topEnd = radius))
        .background(color)

// ── Blurred backdrop for the hero + Recently Read area ───────────────────────
// CHANGE (UI polish #2): the most recently read novel's cover, softly blurred, behind
// the hero banner and the Recently Read cards. The blur comes from decoding the cover
// SMALL (BACKDROP_DECODE_W × BACKDROP_DECODE_H) and letting it scale up smoothly — not
// from a RenderEffect — so it costs nothing per frame while scrolling and looks the
// same on every Android version (RenderEffect blur only exists on 12+). A wash of the
// page colour sits on top (55% light / 65% dark) so the "Recently Read" title and the
// hero's edges stay readable in both themes. A new novel cross-fades in (500ms).
@Composable
private fun BlurredBackdrop(url: String?, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val dark    = isSystemInDarkTheme()
    val reduced = rememberReducedMotion()
    val wash    = MaterialTheme.colorScheme.background.copy(alpha = if (dark) 0.65f else 0.55f)

    Box(modifier.clipToBounds()) {
        Crossfade(
            targetState   = url,
            modifier      = Modifier.fillMaxSize(),
            animationSpec = if (reduced) snap() else tween(Motion.ENTER_MS + 160, easing = Motion.EaseOut)
        ) { u ->
            if (!u.isNullOrBlank()) {
                val request = remember(u) {
                    ImageRequest.Builder(context)
                        .data(u)
                        .size(BACKDROP_DECODE_W, BACKDROP_DECODE_H)
                        .crossfade(false)
                        .build()
                }
                AsyncImage(
                    model              = request,
                    contentDescription = null,
                    contentScale       = ContentScale.Crop,
                    modifier           = Modifier.fillMaxSize()
                )
            }
        }
        Box(Modifier.matchParentSize().background(wash))
    }
}
