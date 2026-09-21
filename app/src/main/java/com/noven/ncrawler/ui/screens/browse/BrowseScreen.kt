package com.noven.ncrawler.ui.screens.browse

import android.provider.Settings
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.noven.ncrawler.data.db.NovelEntity
import androidx.core.graphics.ColorUtils
import com.noven.ncrawler.data.local.DominantColorStore
import com.noven.ncrawler.ui.components.GenreGlassTile
import com.noven.ncrawler.ui.components.NovelGlassCard
import com.noven.ncrawler.ui.components.novelCardWidthFor
import com.noven.ncrawler.ui.theme.*
import com.noven.ncrawler.viewmodel.BrowseUiState
import com.noven.ncrawler.viewmodel.BrowseViewModel
import com.noven.ncrawler.viewmodel.ContinueReadingInfo
import kotlinx.coroutines.delay

// ── Glass tokens — used throughout this screen ────────────────────────────────
// Two families: an on-image variant (white-translucent, for controls sitting
// over the hero cover / card thumbnails) and the light-page variant from
// Theme.kt (GlassSurfaceLight / GlassBorderLight, for chips + panels that sit
// directly on the light #F4F7F9 background — never the old dark-glass look).
private val OnImageGlassFill    = Color(0x1AFFFFFF)  // 10% white over image
private val OnImageGlassBorder  = Color(0x33FFFFFF)  // 20% white hairline
private val OnImageGlassFillMd  = Color(0x26FFFFFF)  // 15% white — slightly more opaque pills

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

    // Light background fills the entire screen
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {

            // ── Top bar — always logo/download, never swaps modes ──────────
            // Cut-out colour = dominant colour of the novel last opened on the
            // Detail screen (saved there); brand blue if there isn't one yet.
            // Re-read whenever Browse is (re)composed, i.e. on every return from Detail.
            val context = LocalContext.current
            val darkTheme = isSystemInDarkTheme()
            val cutoutColor = remember(darkTheme) {
                cutoutColorFor(DominantColorStore(context).getLast(), darkTheme)
            }
            TopNavBar(onDownloadsClick = onDownloadsClick, cutoutColor = cutoutColor)

            // Search lives exclusively in the SearchOverlay (opened from the
            // search FAB in the floating nav) — the homepage itself is browse-only, no inline bar.
            BrowseContent(
                state             = browseState,
                popularState      = popularState,
                onNovelClick      = onNovelClick,
                onRetry           = vm::loadHomepage,
                onGenreClick      = onGenreClick ?: {},
                onDiscoverClick   = onDiscoverClick,
                continueReading   = continueReading,
                onContinueReading = onContinueReading,
                recentlyReading   = recentlyReading
            )
        }
    }
}

// ── Top Nav Bar ───────────────────────────────────────────────────────────────
// CHANGE: rebuilt around a smooth inverted cut-out (from cutout.html): the bar
// is a surface-coloured shape with a rounded "bay" bitten out of its top-right
// corner, filled with a colour, and the downloads button floats in the bay with
// a ring of that colour around it. The bay's outline is exactly the HTML's:
//   • a concave arc concentric with the button (radius = button/2 + gap),
//   • convex fillets where the bay opens onto the bar's top and right edges.
// The bar is taller (56dp → 104dp below the status bar) to make room for it, its
// bottom corners are rounded, and the logo is bigger and nudged right.
// The bay runs up to the top of the screen, behind the status-bar icons. The bay
// colour is clamped to a mid lightness (see cutoutColorFor) so the system's
// dark-in-light / light-in-dark status icons stay readable over it AND over the bar.
private val BAR_CONTENT_HEIGHT = 104.dp     // below the status bar (was 56dp)
private val BTN_SIZE           = 40.dp      // downloads button (unchanged)
private val BTN_MARGIN_END     = 14.dp      // button ↔ screen's right edge
private val BTN_MARGIN_TOP     = 12.dp      // button ↔ bottom of the status bar
private val CUTOUT_GAP         = 10.dp      // ring of bay colour around the button
private val CONVEX_RADIUS      = 16.dp      // fillets where the bay opens onto the edges
private val BAR_BOTTOM_RADIUS  = 24.dp

@Composable
private fun TopNavBar(onDownloadsClick: (() -> Unit)?, cutoutColor: Color) {
    val density = LocalDensity.current
    val statusBarDp = with(density) { WindowInsets.statusBars.getTop(density).toDp() }
    val surface = MaterialTheme.colorScheme.surface

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(statusBarDp + BAR_CONTENT_HEIGHT)
    ) {
        // Bar + bay, drawn as one shape
        Canvas(modifier = Modifier.matchParentSize()) {
            val w = size.width
            val h = size.height
            val statusPx  = statusBarDp.toPx()
            val convexR   = CONVEX_RADIUS.toPx()
            val bottomR   = BAR_BOTTOM_RADIUS.toPx()
            val concaveR  = (BTN_SIZE / 2 + CUTOUT_GAP).toPx()
            // Button centre; the concave arc is concentric with it
            val cx = w - (BTN_MARGIN_END + BTN_SIZE / 2).toPx()
            val cy = statusPx + (BTN_MARGIN_TOP + BTN_SIZE / 2).toPx()
            val bayLeft   = cx - concaveR              // left wall of the bay
            val bayBottom = cy + concaveR              // floor of the bay

            // 1. the bay colour, only where the bay is (so no colour can fringe
            //    along the bar's own rounded corners)
            val bayX = bayLeft - convexR - 1f
            drawRect(
                color   = cutoutColor,
                topLeft = Offset(bayX, 0f),
                size    = Size(w - bayX, bayBottom + convexR + 1f)
            )

            // 2. the bar, clockwise from the top-left, leaving the bay open
            val bar = Path().apply {
                moveTo(0f, 0f)
                lineTo(bayLeft - convexR, 0f)
                // convex fillet at the top of the bay's left wall
                arcTo(Rect(Offset(bayLeft - convexR, convexR), convexR), 270f, 90f, false)
                lineTo(bayLeft, cy)
                // concave arc around the button: left → bottom
                arcTo(Rect(Offset(cx, cy), concaveR), 180f, -90f, false)
                lineTo(w - convexR, bayBottom)
                // convex fillet where the bay's floor meets the right edge
                arcTo(Rect(Offset(w - convexR, bayBottom + convexR), convexR), 270f, 90f, false)
                lineTo(w, h - bottomR)
                arcTo(Rect(Offset(w - bottomR, h - bottomR), bottomR), 0f, 90f, false)
                lineTo(bottomR, h)
                arcTo(Rect(Offset(bottomR, h - bottomR), bottomR), 90f, 90f, false)
                close()
            }
            drawPath(bar, color = surface)
        }

        // Logo — larger (24 → 28sp) and nudged right (24dp → 36dp from the edge),
        // vertically centred in the bar body under the status bar
        Box(
            modifier = Modifier
                .matchParentSize()
                .padding(top = statusBarDp)
        ) {
            Text(
                "nCrawler",
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 36.dp),
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight    = FontWeight.ExtraBold,
                    fontSize      = 28.sp,
                    letterSpacing = (-0.5).sp
                ),
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        // Download button — same tray icon and 2dp theme-adaptive ring as before,
        // now on a translucent surface-coloured disc (like the HTML's frosted
        // button) so it reads on any bay colour.
        val downloadInteraction = remember { MutableInteractionSource() }
        val downloadPressed by downloadInteraction.collectIsPressedAsState()
        val downloadAlpha = if (downloadPressed) 0.7f else 1f
        val downloadTint = MaterialTheme.colorScheme.onSurface
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = statusBarDp + BTN_MARGIN_TOP, end = BTN_MARGIN_END)
                .size(BTN_SIZE)
                .clip(CircleShape)
                .background(surface.copy(alpha = 0.88f))
                .border(2.dp, downloadTint.copy(alpha = downloadAlpha), CircleShape)
                .clickable(
                    enabled           = onDownloadsClick != null,
                    interactionSource = downloadInteraction,
                    indication        = null
                ) {
                    onDownloadsClick?.invoke()
                },
            contentAlignment = Alignment.Center
        ) {
            DownloadTrayIcon(
                modifier = Modifier.size(26.dp),
                tint     = downloadTint.copy(alpha = downloadAlpha)
            )
        }
    }
}

// The bay colour. Base = the last Detail novel's dominant colour; if there is none
// (or it is a grey with no hue) improvise from the brand blue. Either way the
// colour is clamped to a mid lightness for the current theme: light mode 0.55–0.72
// (visible against the white bar, and the dark status icons stay readable on it),
// dark mode 0.22–0.38 (visible against the dark bar, light icons stay readable),
// with at least some saturation so it never reads as grey.
private fun cutoutColorFor(argb: Int?, dark: Boolean): Color {
    val hsl = FloatArray(3)
    val usable = argb?.let { ColorUtils.colorToHSL(it, hsl); hsl[1] >= 0.12f } ?: false
    if (!usable) ColorUtils.colorToHSL((if (dark) AccentBlueDark else AccentBlue).toArgb(), hsl)
    val s = hsl[1].coerceIn(0.35f, 0.85f)
    val l = if (dark) hsl[2].coerceIn(0.22f, 0.38f) else hsl[2].coerceIn(0.55f, 0.72f)
    return Color(ColorUtils.HSLToColor(floatArrayOf(hsl[0], s, l)))
}

@Composable
private fun DownloadTrayIcon(modifier: Modifier = Modifier, tint: Color = Color.Black) {
    Canvas(modifier = modifier) {
        val scale = size.width / 24f
        val strokePx = 2.dp.toPx()

        fun pt(x: Float, y: Float) = Offset(x * scale, y * scale)

        drawLine(tint, pt(12f, 4f), pt(12f, 14f), strokeWidth = strokePx, cap = StrokeCap.Round)

        val head = Path().apply {
            moveTo(pt(8f, 10f).x, pt(8f, 10f).y)
            lineTo(pt(12f, 14f).x, pt(12f, 14f).y)
            lineTo(pt(16f, 10f).x, pt(16f, 10f).y)
        }
        drawPath(head, tint, style = Stroke(width = strokePx, cap = StrokeCap.Round, join = StrokeJoin.Round))

        drawLine(tint, pt(7f, 18f), pt(17f, 18f), strokeWidth = strokePx, cap = StrokeCap.Round)
    }
}

// ── Search Overlay ────────────────────────────────────────────────────────────
// Opened from the search FAB in the floating nav — full-screen, autofocused
// field, recent searches (max 5, each a rounded rectangle with its own "x") when
// empty, live results once typing, X to close. All text is Montserrat.
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SearchOverlay(
    vm: BrowseViewModel,
    onNovelClick: (String) -> Unit,
    onClose: () -> Unit
) {
    val query          by vm.query.collectAsStateWithLifecycle()
    val searchState    by vm.searchState.collectAsStateWithLifecycle()
    val recentSearches by vm.recentSearches.collectAsStateWithLifecycle()

    val focusManager    = LocalFocusManager.current
    val keyboard        = LocalSoftwareKeyboardController.current
    val focusRequester   = remember { FocusRequester() }

    // The field's thick border + search glyph are black in light mode; black on
    // the dark background would disappear, so dark mode uses the text colour.
    val fieldInk = if (isSystemInDarkTheme()) MaterialTheme.colorScheme.onSurface else Color.Black

    // Autofocus + open the keyboard the moment the overlay appears
    LaunchedEffect(Unit) {
        delay(150)
        focusRequester.requestFocus()
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color    = MaterialTheme.colorScheme.background
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // ── Field + close (X) ───────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Custom-bordered field: Material3's OutlinedTextField has no
                // public "border width" knob, only color — so this uses a
                // filled TextField (indicator hidden) inside a Box with an
                // explicit thick black border for exact control.
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .border(2.5.dp, fieldInk, RoundedCornerShape(16.dp))
                ) {
                    TextField(
                        value         = query,
                        onValueChange = vm::onQueryChange,
                        modifier      = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester),
                        placeholder   = {
                            Text(
                                "Search novels…",
                                fontFamily = MontserratFamily,
                                fontWeight = FontWeight.Bold,
                                color      = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        leadingIcon = {
                            // Filled, solid glyph — TikTok-style, not the
                            // softer Rounded family used elsewhere.
                            Icon(Icons.Filled.Search, contentDescription = null,
                                tint = fieldInk)
                        },
                        textStyle = LocalTextStyle.current.copy(
                            fontFamily = MontserratFamily,
                            fontWeight = FontWeight.Bold,
                            color      = MaterialTheme.colorScheme.onSurface
                        ),
                        singleLine = true,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor   = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor   = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            cursorColor             = AccentBlue
                        ),
                        shape = RoundedCornerShape(16.dp),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = {
                            vm.commitSearch(query)
                            focusManager.clearFocus()
                            keyboard?.hide()
                        })
                    )
                }
                Spacer(Modifier.width(8.dp))
                IconButton(onClick = {
                    vm.clearSearch()
                    onClose()
                }) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = "Close search",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            if (query.isBlank()) {
                // ── Recent searches (max 5) ─────────────────────────────────
                // CHANGE: small rounded rectangles laid out side by side and
                // wrapping onto the next line (FlowRow) — not stacked full-width
                // rows. Same look as the reader's controls: a soft fill of the text
                // colour at 10% (the reader pill's alpha), no border, Montserrat,
                // icon at 90%. Long terms are cut short with an ellipsis (chip is
                // capped at 168dp). Tap = search again, "x" = remove.
                if (recentSearches.isNotEmpty()) {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Text(
                            "Recent Searches",
                            fontFamily = MontserratFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize   = 14.sp,
                            color      = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(10.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement   = Arrangement.spacedBy(8.dp)
                        ) {
                            recentSearches.take(5).forEach { term ->
                                Row(
                                    modifier = Modifier
                                        .widthIn(max = 168.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f))
                                        .clickable { vm.onQueryChange(term) }
                                        .padding(start = 12.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        term,
                                        fontFamily = MontserratFamily,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize   = 14.sp,
                                        color      = MaterialTheme.colorScheme.onSurface,
                                        maxLines   = 1,
                                        overflow   = TextOverflow.Ellipsis,
                                        modifier   = Modifier.weight(1f, fill = false)
                                    )
                                    // Plain box, not IconButton: IconButton's 48dp minimum
                                    // touch size would balloon the small chip.
                                    Box(
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(CircleShape)
                                            .clickable { vm.removeRecentSearch(term) },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            Icons.Filled.Close,
                                            contentDescription = "Remove \"$term\" from recent searches",
                                            tint     = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f),
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            "Search for a novel by title",
                            fontFamily = MontserratFamily,
                            fontSize   = 15.sp,
                            color      = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                // ── Live results ─────────────────────────────────────────────
                SearchContent(
                    state        = searchState,
                    query        = query,
                    onNovelClick = { slug ->
                        vm.commitSearch(query)
                        onNovelClick(slug)
                        onClose()
                    }
                )
            }
        }
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
    recentlyReading: List<ContinueReadingInfo>
) {
    when (state) {
        is BrowseUiState.Loading -> BrowseSkeleton()
        is BrowseUiState.Error   -> BrowseError(state.message, onRetry)
        is BrowseUiState.Empty   -> BrowseError("No novels found", onRetry)
        is BrowseUiState.Success -> {
            val novels = state.novels
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
            val latestList        = novels.drop(1)
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

            LazyColumn(
                modifier       = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 120.dp)
            ) {

                // ── Hero — resumes the last-read novel at its exact chapter
                // when there's reading history; otherwise features the top
                // of the feed and opens its detail page like before.
                if (continueReading != null && onContinueReading != null) {
                    item {
                        HeroBanner(
                            novel      = continueReading.novel,
                            resumeChapter = continueReading.progress.lastChapterNum,
                            onClick    = {
                                onContinueReading(
                                    continueReading.novel.slug,
                                    continueReading.progress.lastChapterNum
                                )
                            }
                        )
                    }
                } else if (hero != null) {
                    item {
                        HeroBanner(novel = hero, resumeChapter = null, onClick = { onNovelClick(hero.slug) })
                    }
                }

                // ── Recently Read — everything read EXCEPT the hero item
                // above. Tapping a card opens the reader at the exact
                // chapter; tapping the small "i" badge opens the detail
                // page instead. No center play button — the whole card
                // (minus the "i") is the tap target.
                if (recentlyReading.isNotEmpty() && onContinueReading != null) {
                    item {
                        Spacer(Modifier.height(24.dp))
                        RecentlyReadRow(
                            items          = recentlyReading,
                            onOpenReader   = onContinueReading,
                            onOpenDetail   = onNovelClick
                        )
                    }
                }

                // ── Genre showcase — decorative gradient-font cards, now a
                // horizontally scrolling row (up to 8 genres, 6 alternating
                // decorative styles) ending in a "See More" card that opens
                // the existing DiscoverScreen (every genre, full list).
                if (showcaseGenres.isNotEmpty()) {
                    item {
                        Spacer(Modifier.height(24.dp))
                        GenreShowcaseRow(
                            genres          = showcaseGenres,
                            onGenreClick    = onGenreClick,
                            onDiscoverClick = onDiscoverClick
                        )
                    }
                }

                // ── Latest Updates — up to 5 genre rows, horizontal samples,
                // "See more" on each opens that genre's full list ─────────────
                if (latestGenreRows.isNotEmpty()) {
                    item {
                        Spacer(Modifier.height(24.dp))
                        SectionHeader("Latest Updates")
                    }
                    items(latestGenreRows, key = { "latest_${it.first}" }) { (genre, rowNovels) ->
                        Spacer(Modifier.height(16.dp))
                        GenreRow(
                            genre        = genre,
                            novels       = rowNovels,
                            onNovelClick = onNovelClick,
                            onSeeMore    = { onGenreClick(genre) }
                        )
                    }
                } else if (showFlatLatest) {
                    // FIX: no genre data to group by (e.g. NovelArrow) —
                    // show everything fetched as one flat row instead of
                    // nothing at all.
                    item {
                        Spacer(Modifier.height(24.dp))
                        SectionHeader("Latest Updates")
                    }
                    item {
                        Spacer(Modifier.height(16.dp))
                        GenreRow(
                            genre        = "Latest",
                            novels       = latestList,
                            onNovelClick = onNovelClick,
                            onSeeMore    = { onDiscoverClick?.invoke() }
                        )
                    }
                }

                // ── Popular — same pattern, sourced from /sort/most-popular ──
                if (popularGenreRows.isNotEmpty()) {
                    item {
                        Spacer(Modifier.height(28.dp))
                        SectionHeader("Popular")
                    }
                    items(popularGenreRows, key = { "popular_${it.first}" }) { (genre, rowNovels) ->
                        Spacer(Modifier.height(16.dp))
                        GenreRow(
                            genre        = genre,
                            novels       = rowNovels,
                            onNovelClick = onNovelClick,
                            onSeeMore    = { onGenreClick(genre) }
                        )
                    }
                    item { Spacer(Modifier.height(20.dp)) }
                } else if (showFlatPopular) {
                    // FIX: same fallback as Latest — flat row when there's
                    // no genre data to group by.
                    item {
                        Spacer(Modifier.height(28.dp))
                        SectionHeader("Popular")
                    }
                    item {
                        Spacer(Modifier.height(16.dp))
                        GenreRow(
                            genre        = "Popular",
                            novels       = popularNovels,
                            onNovelClick = onNovelClick,
                            onSeeMore    = { onDiscoverClick?.invoke() }
                        )
                    }
                    item { Spacer(Modifier.height(20.dp)) }
                }
            }
        }
    }
}

// Groups novels by their (comma-separated) genre tags, keeps the top
// [maxGenres] genres by how many novels carry them, and caps each row's
// sample to [perGenre] so a genre row stays a horizontal scroll, not a wall.
private fun groupByTopGenres(
    novels: List<NovelEntity>,
    maxGenres: Int = 5,
    perGenre: Int = 10
): List<Pair<String, List<NovelEntity>>> {
    val byGenre = linkedMapOf<String, MutableList<NovelEntity>>()
    novels.forEach { novel ->
        novel.genres.split(",")
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .forEach { g -> byGenre.getOrPut(g) { mutableListOf() }.add(novel) }
    }
    return byGenre.entries
        .sortedByDescending { it.value.size }
        .take(maxGenres)
        .map { it.key to it.value.take(perGenre) }
}

// Width of one card in Discover's GenreScreen grid. Homepage rows use the same
// width so a novel card is the same size on both screens. FIX: it used to be
// "(screen − padding) / 2", which in landscape stretched each card to ~half of
// an 800dp+ screen; the shared formula adds columns instead, so a card stays
// about the same size in any orientation.
@Composable
private fun novelCardWidth(): androidx.compose.ui.unit.Dp =
    novelCardWidthFor(LocalConfiguration.current.screenWidthDp)

// ── Genre Row — a labeled horizontal sample with a "See more" that opens
// the full infinite-scroll list for that genre (Discover's GenreScreen).
// CHANGE: cards are now exactly the size of the cards on Discover's genre grid
// (see novelCardWidth — same width, same 6:7 cover), longer than the old
// 130dp squares. They come from the shared NovelGlassCard (ui/components), and
// the row de-dupes by slug — LazyRow keys must be unique, so a source that
// repeats a novel inside one list used to crash with "Key was already used".
@Composable
private fun GenreRow(
    genre: String,
    novels: List<NovelEntity>,
    onNovelClick: (String) -> Unit,
    onSeeMore: () -> Unit
) {
    val uniqueNovels = remember(novels) { novels.distinctBy { it.slug } }
    val cardWidth = novelCardWidth()
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Text(
                genre,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                "See more",
                style    = MaterialTheme.typography.labelMedium,
                color    = AccentBlue,
                modifier = Modifier.clickable(onClick = onSeeMore)
            )
        }
        Spacer(Modifier.height(10.dp))
        // CHANGE: bottom = 8.dp — a LazyRow clips to its bounds, which was cutting
        // off the cards' drop shadow at the bottom edge.
        LazyRow(
            contentPadding        = PaddingValues(start = 16.dp, end = 16.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            items(uniqueNovels, key = { it.slug }) { novel ->
                NovelGlassCard(
                    novel    = novel,
                    onClick  = { onNovelClick(novel.slug) },
                    modifier = Modifier.width(cardWidth)
                )
            }
        }
    }
}

// ── Hero Banner — inset card, rounded corners ─────────────────────────────────
// NOT full-bleed: horizontal page padding + ~20dp corner radius.
// CHANGE: 2× its old height (160 → 320dp — it already spans the page width, so
// height is the only dimension that can double) and no glass left on it: no
// rim border, and the frosted "Start Reading / Resume" pill is now plain
// icon + text with no rectangle behind it.
// CHANGE: the cover slowly drifts (a Ken Burns pan + zoom) instead of sitting
// still — see the heroDrift block below.
// resumeChapter != null → this IS the continue-reading novel: CTA becomes
// "Resume Chapter N" and taps the given onClick (which jumps straight to
// that chapter), instead of "Start Reading" opening the detail page.
@Composable
private fun HeroBanner(novel: NovelEntity, resumeChapter: Int?, onClick: () -> Unit) {
    // ── Slow cover drift ────────────────────────────────────────────────────
    // Three unhurried, back-and-forth loops with different lengths (zoom 22s,
    // pan-x 17s, pan-y 23s), so the motion never visibly repeats in lockstep.
    // The image is always zoomed at least 12% and only pans inside the slack
    // that zoom creates (80% of it), so no edge is ever exposed. Values are
    // read inside graphicsLayer {} — the draw phase — so this never triggers
    // recomposition. Honours the system "Remove animations" setting.
    val context = LocalContext.current
    val reducedMotion = remember(context) {
        Settings.Global.getFloat(
            context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f
        ) == 0f
    }
    val drift = rememberInfiniteTransition(label = "heroDrift")
    val zoom by drift.animateFloat(
        initialValue  = 0f,
        targetValue   = 1f,
        animationSpec = infiniteRepeatable(tween(22000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label         = "heroZoom"
    )
    val panX by drift.animateFloat(
        initialValue  = -1f,
        targetValue   = 1f,
        animationSpec = infiniteRepeatable(tween(17000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label         = "heroPanX"
    )
    val panY by drift.animateFloat(
        initialValue  = -1f,
        targetValue   = 1f,
        animationSpec = infiniteRepeatable(tween(23000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label         = "heroPanY"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(320.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
    ) {
        // Cover image — drifting (the card's clip() trims the overscan)
        AsyncImage(
            model              = novel.coverUrl,
            contentDescription = novel.title,
            contentScale       = ContentScale.Crop,
            modifier           = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    if (!reducedMotion) {
                        val scale = 1.12f + 0.08f * zoom          // 1.12 → 1.20
                        scaleX = scale
                        scaleY = scale
                        translationX = panX * (scale - 1f) * size.width  * 0.4f
                        translationY = panY * (scale - 1f) * size.height * 0.4f
                    }
                }
        )

        // Scrim — transparent top, dark bottom, so the text reads over any cover
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0f    to Color.Black.copy(alpha = 0.05f),
                            0.45f to Color.Black.copy(alpha = 0.15f),
                            1f    to Color.Black.copy(alpha = 0.82f)
                        )
                    )
                )
        )

        // Content — bottom-aligned
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 18.dp, end = 18.dp, bottom = 18.dp)
        ) {
            // Title — up to two lines now that the card is taller
            Text(
                novel.title,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight    = FontWeight.ExtraBold,
                    fontSize      = 28.sp,
                    lineHeight    = 34.sp,
                    letterSpacing = (-0.3).sp
                ),
                color    = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(Modifier.height(12.dp))

            // Row: play + label (plain — no pill), rating
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Rounded.PlayArrow,
                        contentDescription = null,
                        tint     = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        if (resumeChapter != null) "Resume Ch. $resumeChapter" else "Start Reading",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize   = 15.sp
                        ),
                        color = Color.White
                    )
                }

                // Rating badge
                if (novel.rating.isNotBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Rounded.Star,
                            contentDescription = null,
                            tint     = StarGold,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            novel.rating,
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}

// ── Recently Read row ─────────────────────────────────────────────────────────
// Mirrors the "Waiting to watch" carousel design: portrait media cards over
// the cover art, title top, footer with an info badge + progress bar. Two
// deliberate departures from the reference:
//  - No center play button — the whole card (minus the "i" badge) IS the
//    play target, so a redundant button would just clutter the cover art.
//  - The "i" badge opens the detail page; everywhere else on the card opens
//    the reader at the exact last-read chapter. Nested clickables handle
//    this correctly — the inner "i" click consumes the tap before it can
//    bubble to the card's own onClick.
@Composable
private fun RecentlyReadRow(
    items: List<ContinueReadingInfo>,
    onOpenReader: (slug: String, chapterNum: Int) -> Unit,
    onOpenDetail: (String) -> Unit
) {
    Column {
        Text(
            "Recently Read",
            modifier = Modifier.padding(horizontal = 16.dp),
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight    = FontWeight.ExtraBold,
                letterSpacing = (-0.2).sp
            ),
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(14.dp))
        LazyRow(
            contentPadding        = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            items(items, key = { it.novel.slug }) { info ->
                RecentCard(
                    info         = info,
                    onOpenReader = { onOpenReader(info.novel.slug, info.progress.lastChapterNum) },
                    onOpenDetail = { onOpenDetail(info.novel.slug) }
                )
            }
        }
    }
}

// Recently Read card: 140×210dp portrait. CHANGE (kept): the frosted rectangle
// behind the info badge / progress bar is gone — badge, chapter label and
// progress sit straight on the cover's dark scrim. Title uses the reader's
// font (Montserrat).
@Composable
private fun RecentCard(
    info: ContinueReadingInfo,
    onOpenReader: () -> Unit,
    onOpenDetail: () -> Unit
) {
    val novel = info.novel
    val progress = (info.progress.lastChapterNum.toFloat() / novel.chapterCount.coerceAtLeast(1))
        .coerceIn(0f, 1f)
    // CHANGE: a novel with an unknown chapter count (0) used to read "Ch.5/0".
    val chapterText = if (novel.chapterCount > 0)
        "Ch.${info.progress.lastChapterNum}/${novel.chapterCount}"
    else
        "Ch.${info.progress.lastChapterNum}"

    Box(
        modifier = Modifier
            .width(140.dp)
            .height(210.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onOpenReader)
    ) {
        AsyncImage(
            model              = novel.coverUrl,
            contentDescription = novel.title,
            contentScale       = ContentScale.Crop,
            modifier           = Modifier.fillMaxSize()
        )

        // Scrim — light top, dark bottom, so the title reads over any cover
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0f    to Color.Black.copy(alpha = 0.10f),
                            0.5f  to Color.Black.copy(alpha = 0.20f),
                            1f    to Color.Black.copy(alpha = 0.88f)
                        )
                    )
                )
        )

        Column(
            modifier            = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Title — top
            Text(
                novel.title,
                fontFamily = MontserratFamily,
                fontWeight = FontWeight.ExtraBold,
                fontSize   = 13.sp,
                lineHeight = 17.sp,
                color      = Color.White,
                maxLines   = 2,
                overflow   = TextOverflow.Ellipsis,
                textAlign  = TextAlign.Start
            )

            // Footer — info badge + chapter label, then progress bar
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.55f))
                            // Nested clickable — consumes the tap here so the
                            // outer card's onOpenReader never fires for this spot.
                            .clickable(onClick = onOpenDetail),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "i",
                            color      = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontStyle  = androidx.compose.ui.text.font.FontStyle.Italic,
                            fontSize   = 13.sp
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        chapterText,
                        color      = Color.White.copy(alpha = 0.85f),
                        fontSize   = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Spacer(Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color.White.copy(alpha = 0.28f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progress)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(2.dp))
                            .background(Color.White)
                    )
                }
            }
        }
    }
}

// ── Genre Showcase — decorative, colour-gradient typography per card ─────────
// Horizontally scrolling row (was a fixed 3-card row) so any number of real
// genres fit, each in one of 6 alternating decorative styles (was 3) — see
// GenreGlassTile (ui/components). Ends in a "See More" card that opens the
// existing DiscoverScreen, which already lists every genre.
@Composable
private fun GenreShowcaseRow(
    genres: List<String>,
    onGenreClick: (String) -> Unit,
    onDiscoverClick: (() -> Unit)?
) {
    Column {
        Row(
            modifier              = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Text(
                "Genre",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight    = FontWeight.ExtraBold,
                    letterSpacing = (-0.2).sp
                ),
                color = MaterialTheme.colorScheme.onBackground
            )
            // CHANGE: now wired to Discover (was a dead label — no screen
            // existed for it before; DiscoverScreen already did).
            Text(
                "See all",
                style    = MaterialTheme.typography.labelMedium,
                color    = if (onDiscoverClick != null) AccentBlue
                           else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = if (onDiscoverClick != null)
                    Modifier.clickable(onClick = onDiscoverClick) else Modifier
            )
        }
        Spacer(Modifier.height(14.dp))
        // CHANGE: fixed 3-card Row → LazyRow so the showcase can hold up to
        // 8 real genres plus a trailing "See More" card without squeezing.
        LazyRow(
            contentPadding        = PaddingValues(start = 16.dp, end = 16.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            itemsIndexed(genres, key = { _, g -> g }) { i, genre ->
                GenreGlassTile(
                    genre    = genre,
                    styleIdx = i % 6,
                    onClick  = { onGenreClick(genre) },
                    modifier = Modifier.width(104.dp)
                )
            }
            if (onDiscoverClick != null) {
                item(key = "genre_see_more") {
                    SeeMoreGenreCard(
                        onClick  = onDiscoverClick,
                        modifier = Modifier.width(104.dp)
                    )
                }
            }
        }
    }
}

// CHANGE: GenreDecorativeCard moved to ui/components/GlassCards.kt as
// GenreGlassTile so Discover's genre grid can use the exact same tile (with
// dark-mode-safe gradients). Its 72dp height / 6-style history lives there.

// New — trailing card at the end of the genre showcase row. Distinct from
// the decorative cards on purpose (outlined accent style, not white glass)
// so it reads as an action, not another genre.
@Composable
private fun SeeMoreGenreCard(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .height(72.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(AccentBlue.copy(alpha = 0.10f))
            .border(1.5.dp, AccentBlue.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(6.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.AutoMirrored.Rounded.ArrowForward,
                contentDescription = "See more genres",
                tint     = AccentBlue,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "See More",
                color      = AccentBlue,
                fontSize   = 10.sp,
                fontWeight = FontWeight.Bold,
                textAlign  = TextAlign.Center,
                lineHeight = 12.sp
            )
        }
    }
}

// ── Section Header ────────────────────────────────────────────────────────────
@Composable
private fun SectionHeader(title: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight    = FontWeight.ExtraBold,
                letterSpacing = (-0.2).sp
            ),
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            "See all",
            style = MaterialTheme.typography.labelMedium,
            color = AccentBlue
        )
    }
}

// ── Genre Chip ────────────────────────────────────────────────────────────────
// Kept for any external reference (e.g. a future filter strip inside
// Discover's GenreScreen) — no longer used on the homepage; the homepage now
// uses GenreShowcaseRow above instead.
@Composable
private fun GenreChip(name: String, isActive: Boolean, onClick: () -> Unit) {
    val bg        = if (isActive) AccentBlue else glassSurface()
    val border    = if (isActive) Color.Transparent else glassBorder()
    val textColor = if (isActive) Color.White else MaterialTheme.colorScheme.onSurface

    Box(
        modifier = Modifier
            .height(50.dp)
            .clip(RoundedCornerShape(25.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(25.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            name,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            color = textColor
        )
    }
}

// ── Novel Card ────────────────────────────────────────────────────────────────
// CHANGE: moved to ui/components/GlassCards.kt as NovelGlassCard — GenreScreen
// carried a hand-copied duplicate that had already drifted from this one.

// ── Search content ────────────────────────────────────────────────────────────
@Composable
private fun SearchContent(
    state: BrowseUiState,
    query: String,
    onNovelClick: (String) -> Unit
) {
    when (state) {
        is BrowseUiState.Loading -> SearchSkeleton()
        is BrowseUiState.Empty   -> SearchEmpty(query)
        is BrowseUiState.Error   -> Box(Modifier.fillMaxSize(), Alignment.Center) {
            Text(
                state.message,
                fontFamily = MontserratFamily,
                color      = MaterialTheme.colorScheme.error
            )
        }
        is BrowseUiState.Success -> LazyColumn(
            contentPadding      = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            items(state.novels, key = { it.slug }) { novel ->
                SearchRow(novel = novel, onClick = { onNovelClick(novel.slug) })
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.08f)
                )
            }
        }
    }
}

@Composable
private fun SearchRow(novel: NovelEntity, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment     = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp, 66.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            AsyncImage(
                model              = novel.coverUrl,
                contentDescription = novel.title,
                contentScale       = ContentScale.Crop,
                modifier           = Modifier.fillMaxSize()
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                novel.title,
                fontFamily = MontserratFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize   = 15.sp,
                lineHeight = 20.sp,
                maxLines   = 2,
                overflow   = TextOverflow.Ellipsis,
                color      = MaterialTheme.colorScheme.onBackground
            )
            if (novel.genres.isNotBlank()) {
                Spacer(Modifier.height(3.dp))
                Text(
                    novel.genres.split(",").take(2).joinToString(" · "),
                    fontFamily = MontserratFamily,
                    fontSize   = 12.sp,
                    color      = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Icon(
            Icons.Rounded.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp)
        )
    }
}

// ── Skeleton states ───────────────────────────────────────────────────────────
@Composable
private fun BrowseSkeleton() {
    val shimmer = MaterialTheme.colorScheme.surfaceVariant

    Column(modifier = Modifier.fillMaxSize()) {
        // Hero skeleton — inset, rounded, 320dp (matches HeroBanner)
        Box(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .height(320.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(shimmer)
        )
        Spacer(Modifier.height(24.dp))

        // Recently Read skeleton — label + row of 140x210dp portrait cards
        SkeletonLabel(shimmer, width = 130.dp)
        Spacer(Modifier.height(14.dp))
        Row(
            Modifier.padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            repeat(2) {
                Box(
                    Modifier
                        .width(140.dp)
                        .height(210.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(shimmer)
                )
            }
        }
        Spacer(Modifier.height(24.dp))

        // Genre showcase skeleton — "Genre"/"See all" header + row of
        // 104x72dp cards, matching GenreShowcaseRow exactly
        SkeletonLabel(shimmer, width = 90.dp)
        Spacer(Modifier.height(14.dp))
        Row(
            Modifier.padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            repeat(4) {
                Box(
                    Modifier
                        .width(104.dp)
                        .height(72.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(shimmer)
                )
            }
        }
        Spacer(Modifier.height(24.dp))

        // Two genre-row sections (Latest Updates / Popular) — section label
        // + a labeled row + a horizontal scroll of novel cards, matching
        // GenreRow exactly (same width, 6:7 cover, 48dp title block)
        val skeletonCardWidth = novelCardWidth()
        repeat(2) {
            SkeletonLabel(shimmer, width = 140.dp, height = 16.dp)
            Spacer(Modifier.height(16.dp))
            SkeletonLabel(shimmer, width = 100.dp)
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier.padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                repeat(3) {
                    // Rounded cover + a title bar underneath (no card behind the
                    // title any more, matching NovelGlassCard)
                    Column(Modifier.width(skeletonCardWidth)) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(skeletonCardWidth * (7f / 6f))
                                .clip(RoundedCornerShape(16.dp))
                                .background(shimmer)
                        )
                        Spacer(Modifier.height(12.dp))
                        Box(
                            Modifier
                                .fillMaxWidth(0.8f)
                                .height(12.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(shimmer)
                        )
                        Spacer(Modifier.height(20.dp))
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

// Small shimmer bar standing in for a text label — used throughout the
// skeleton above instead of repeating the same Box(...).background(shimmer)
// four times with slightly different sizes.
@Composable
private fun SkeletonLabel(shimmer: Color, width: androidx.compose.ui.unit.Dp, height: androidx.compose.ui.unit.Dp = 20.dp) {
    Box(
        Modifier
            .padding(horizontal = 16.dp)
            .size(width, height)
            .clip(RoundedCornerShape(4.dp))
            .background(shimmer)
    )
}

@Composable
private fun SearchSkeleton() {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        repeat(6) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Box(
                    Modifier
                        .size(48.dp, 66.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                )
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(
                        Modifier
                            .size(140.dp, 13.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    )
                    Box(
                        Modifier
                            .size(90.dp, 10.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchEmpty(query: String) {
    Box(Modifier.fillMaxSize(), Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Rounded.SearchOff,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint     = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "No results for \"$query\"",
                fontFamily = MontserratFamily,
                fontSize   = 15.sp,
                color      = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun BrowseError(message: String, onRetry: () -> Unit) {
    Box(Modifier.fillMaxSize(), Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Rounded.WifiOff,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint     = MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
            )
            Spacer(Modifier.height(12.dp))
            Text(
                message,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = onRetry,
                shape   = RoundedCornerShape(12.dp),
                colors  = ButtonDefaults.buttonColors(containerColor = AccentBlue)
            ) {
                Text("Retry")
            }
        }
    }
}
