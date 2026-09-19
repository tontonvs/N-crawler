package com.noven.ncrawler.ui.screens.browse

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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
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
    onSettingsClick: (() -> Unit)? = null,
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

            // ── Top bar — always avatar/logo/download, never swaps modes ───
            TopNavBar(onDownloadsClick = onDownloadsClick, onSettingsClick = onSettingsClick)

            // Search lives exclusively in the SearchOverlay (opened from the
            // bottom nav) — the homepage itself is browse-only, no inline bar.
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
// Solid white bar (Material You style) — never transparent, never swaps modes.
// avatar (left) · logo (true center, via Box alignment) · download (right).
@Composable
private fun TopNavBar(onDownloadsClick: (() -> Unit)?, onSettingsClick: (() -> Unit)?) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 16.dp)
        ) {
            // Avatar — left, shifted in a bit further, bigger, a full
            // circle — border/bg both theme colors, so it adapts
            // automatically between light/dark mode. Tap opens Settings —
            // the natural, standard place for it now that NavGraph wires
            // onSettingsClick through.
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 8.dp)
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f), CircleShape)
                    .clickable(enabled = onSettingsClick != null) {
                        onSettingsClick?.invoke()
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "T",
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.ExtraBold,
                        fontSize   = 20.sp
                    )
                )
            }

            // Logo — true center, bigger, full name
            Text(
                "nCrawler",
                modifier = Modifier.align(Alignment.Center),
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight    = FontWeight.ExtraBold,
                    fontSize      = 24.sp,
                    letterSpacing = (-0.5).sp
                ),
                color = MaterialTheme.colorScheme.onSurface
            )

            // Download button — matches the mockup shape (full circle, 2px
            // border, transparent background, hand-drawn "tray" arrow), but
            // the border/icon color is now theme-adaptive (onSurface)
            // instead of hardcoded black — the mockup was light-mode only,
            // and a pure-black circle disappears against a dark top bar.
            val downloadInteraction = remember { MutableInteractionSource() }
            val downloadPressed by downloadInteraction.collectIsPressedAsState()
            val downloadAlpha = if (downloadPressed) 0.7f else 1f
            val downloadTint = MaterialTheme.colorScheme.onSurface
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 8.dp)
                    .size(40.dp)
                    .clip(CircleShape)
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
}

// ── Download tray icon ─────────────────────────────────────────────────────────
// Hand-drawn to match the mockup's SVG exactly (viewBox 0 0 24 24): a shaft
// from (12,4) to (12,14), a chevron arrowhead (8,10)-(12,14)-(16,10), and a
// base line (7,18)-(17,18) — 2px round-capped strokes, no fill.
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
// Opened from the bottom nav's Search icon — full-screen, autofocused field,
// recent searches (max 5) when empty, live results once typing, X to close.
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
                        .border(2.5.dp, Color.Black, RoundedCornerShape(16.dp))
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
                                tint = Color.Black)
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
                if (recentSearches.isNotEmpty()) {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Text(
                            "Recent Searches",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                        recentSearches.take(5).forEach { term ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { vm.onQueryChange(term) }
                                    .padding(vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Filled clock icon — same solid-glyph language
                                // as the search icon above.
                                Icon(
                                    Icons.Filled.History,
                                    contentDescription = null,
                                    tint     = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    term,
                                    // Deliberately NOT Montserrat — a second,
                                    // distinct font from the bold search-bar
                                    // text, and bigger than the old bodyMedium.
                                    fontFamily = FontFamily.Default,
                                    fontSize   = 17.sp,
                                    color      = MaterialTheme.colorScheme.onSurface,
                                    modifier   = Modifier.weight(1f)
                                )
                                IconButton(
                                    onClick  = { vm.removeRecentSearch(term) },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        Icons.Filled.Close,
                                        contentDescription = "Remove \"$term\" from recent searches",
                                        tint     = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                } else {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            "Search for a novel by title",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
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

// ── Genre Row — a labeled horizontal sample with a "See more" that opens
// the full infinite-scroll list for that genre (Discover's GenreScreen).
// Cards are a fixed, narrower width here (130dp) than the old 2-col grid —
// narrower cards read the (portrait) covers better at this card height.
@Composable
private fun GenreRow(
    genre: String,
    novels: List<NovelEntity>,
    onNovelClick: (String) -> Unit,
    onSeeMore: () -> Unit
) {
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
        LazyRow(
            contentPadding        = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            items(novels, key = { it.slug }) { novel ->
                NovelCard(
                    novel    = novel,
                    onClick  = { onNovelClick(novel.slug) },
                    modifier = Modifier.width(130.dp)
                )
            }
        }
    }
}

// ── Hero Banner — inset card, rounded corners, compact height ─────────────────
// NOT full-bleed: horizontal page padding + ~20dp corner radius, ~160dp tall
// (like the Disney+ "featured banner" card, not the full-screen Luca poster).
// resumeChapter != null → this IS the continue-reading novel: CTA becomes
// "Resume Chapter N" and taps the given onClick (which jumps straight to
// that chapter), instead of "Start Reading" opening the detail page.
@Composable
private fun HeroBanner(novel: NovelEntity, resumeChapter: Int?, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(160.dp)
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
    ) {
        // Cover image
        AsyncImage(
            model              = novel.coverUrl,
            contentDescription = novel.title,
            contentScale       = ContentScale.Crop,
            modifier           = Modifier.fillMaxSize()
        )

        // Scrim — transparent top, dark bottom, tuned for the shorter card
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0f    to Color.Black.copy(alpha = 0.05f),
                            0.45f to Color.Black.copy(alpha = 0.15f),
                            1f    to Color.Black.copy(alpha = 0.80f)
                        )
                    )
                )
        )

        // Content — bottom-aligned, compact
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 14.dp, end = 14.dp, bottom = 12.dp)
        ) {
            // Title — one line, tight
            Text(
                novel.title,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight    = FontWeight.ExtraBold,
                    fontSize      = 19.sp,
                    letterSpacing = (-0.3).sp
                ),
                color    = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(Modifier.height(8.dp))

            // Row: frosted "Start Reading" pill + rating badge
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Frosted-glass pill — on-image, so white-translucent still reads
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50.dp))
                        .background(OnImageGlassFillMd)
                        .border(1.dp, OnImageGlassBorder, RoundedCornerShape(50.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Rounded.PlayArrow,
                            contentDescription = null,
                            tint     = Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            if (resumeChapter != null) "Resume Ch. $resumeChapter" else "Start Reading",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White
                        )
                    }
                }

                // Rating badge
                if (novel.rating.isNotBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Rounded.Star,
                            contentDescription = null,
                            tint     = StarGold,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(Modifier.width(3.dp))
                        Text(
                            novel.rating,
                            style = MaterialTheme.typography.labelMedium.copy(
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

@Composable
private fun RecentCard(
    info: ContinueReadingInfo,
    onOpenReader: () -> Unit,
    onOpenDetail: () -> Unit
) {
    val novel = info.novel
    val progress = (info.progress.lastChapterNum.toFloat() / novel.chapterCount.coerceAtLeast(1))
        .coerceIn(0f, 1f)

    Box(
        modifier = Modifier
            .width(140.dp)
            .height(210.dp)
            .clip(RoundedCornerShape(16.dp))
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
                style    = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = FontWeight.ExtraBold,
                    fontSize   = 13.sp
                ),
                color    = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Start
            )

            // Footer — info badge + chapter label, then progress bar
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.35f))
                            .border(1.dp, Color.White.copy(alpha = 0.35f), CircleShape)
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
                        "Ch.${info.progress.lastChapterNum}/${novel.chapterCount}",
                        color    = Color.White.copy(alpha = 0.85f),
                        fontSize = 11.sp,
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
// GenreDecorativeCard below. Ends in a "See More" card that opens the
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
            contentPadding        = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            itemsIndexed(genres, key = { _, g -> g }) { i, genre ->
                GenreDecorativeCard(
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

// CHANGE: height reduced 90dp → 72dp (per feedback), font sizes trimmed
// slightly to match, and doubled from 3 to 6 decorative style variants —
// reuses the same 3 bundled fonts (Pacifico / Cinzel / Anton) with fresh
// gradient pairs so 8 genres in a row don't read as visibly repetitive.
@Composable
private fun GenreDecorativeCard(
    genre: String,
    styleIdx: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(72.dp)
            .shadow(elevation = 8.dp, shape = RoundedCornerShape(16.dp), clip = false)
            .clip(RoundedCornerShape(16.dp))
            .background(glassSurface())
            .clickable(onClick = onClick)
            .padding(6.dp),
        contentAlignment = Alignment.Center
    ) {
        when (styleIdx) {
            // Style 0 — Pacifico cursive, warm pink-orange gradient
            0 -> Text(
                text  = genre,
                style = TextStyle(
                    fontFamily = PacificoFamily,
                    fontSize   = 16.sp,
                    brush      = Brush.linearGradient(
                        colors = listOf(Color(0xFFFF416C), Color(0xFFFF4B2B))
                    )
                ),
                textAlign = TextAlign.Center,
                maxLines  = 2,
                overflow  = TextOverflow.Ellipsis
            )
            // Style 1 — Cinzel serif bold, epic purple gradient
            1 -> Text(
                text  = genre,
                style = TextStyle(
                    fontFamily    = CinzelFamily,
                    fontWeight    = FontWeight.Bold,
                    fontSize      = 13.sp,
                    letterSpacing = 1.5.sp,
                    brush         = Brush.linearGradient(
                        colors = listOf(Color(0xFF8E2DE2), Color(0xFF4A00E0))
                    )
                ),
                textAlign = TextAlign.Center,
                maxLines  = 2,
                overflow  = TextOverflow.Ellipsis
            )
            // Style 2 — Anton (Impact-style) caps, fiery orange-red gradient
            2 -> Text(
                text  = genre.uppercase(),
                style = TextStyle(
                    fontFamily    = AntonFamily,
                    fontSize      = 16.sp,
                    letterSpacing = 0.5.sp,
                    brush         = Brush.linearGradient(
                        colors = listOf(Color(0xFFF97316), Color(0xFFDC2626))
                    )
                ),
                textAlign = TextAlign.Center,
                maxLines  = 2,
                overflow  = TextOverflow.Ellipsis
            )
            // Style 3 — Pacifico cursive, cool teal-blue gradient
            3 -> Text(
                text  = genre,
                style = TextStyle(
                    fontFamily = PacificoFamily,
                    fontSize   = 16.sp,
                    brush      = Brush.linearGradient(
                        colors = listOf(Color(0xFF06B6D4), Color(0xFF3B82F6))
                    )
                ),
                textAlign = TextAlign.Center,
                maxLines  = 2,
                overflow  = TextOverflow.Ellipsis
            )
            // Style 4 — Cinzel serif bold, gold-amber gradient
            4 -> Text(
                text  = genre,
                style = TextStyle(
                    fontFamily    = CinzelFamily,
                    fontWeight    = FontWeight.Bold,
                    fontSize      = 13.sp,
                    letterSpacing = 1.5.sp,
                    brush         = Brush.linearGradient(
                        colors = listOf(Color(0xFFF59E0B), Color(0xFFD97706))
                    )
                ),
                textAlign = TextAlign.Center,
                maxLines  = 2,
                overflow  = TextOverflow.Ellipsis
            )
            // Style 5 — Anton caps, green-emerald gradient
            else -> Text(
                text  = genre.uppercase(),
                style = TextStyle(
                    fontFamily    = AntonFamily,
                    fontSize      = 16.sp,
                    letterSpacing = 0.5.sp,
                    brush         = Brush.linearGradient(
                        colors = listOf(Color(0xFF10B981), Color(0xFF059669))
                    )
                ),
                textAlign = TextAlign.Center,
                maxLines  = 2,
                overflow  = TextOverflow.Ellipsis
            )
        }
    }
}

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
// Landscape card — mirrors the Moana/Encanto cards in the snippet exactly:
// white rounded card, image fills the top, title below, then
// `duration · play button · rating` row.
@Composable
private fun NovelCard(novel: NovelEntity, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue   = if (isPressed) 0.96f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessHigh),
        label         = "cardPress"
    )

    Surface(
        modifier = modifier
            .graphicsLayer(scaleX = scale, scaleY = scale)
            .clickable(
                interactionSource = interactionSource,
                indication        = null,
                onClick           = onClick
            ),
        shape           = RoundedCornerShape(16.dp),
        color           = MaterialTheme.colorScheme.surface,
        shadowElevation = 3.dp
    ) {
        Column {
            // Cover image — landscape, fills card width, top of card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                AsyncImage(
                    model              = novel.coverUrl,
                    contentDescription = novel.title,
                    contentScale       = ContentScale.Crop,
                    modifier           = Modifier.fillMaxSize()
                )
            }

            Column(modifier = Modifier.padding(10.dp)) {
                // Title
                Text(
                    novel.title,
                    style    = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize   = 12.sp
                    ),
                    color    = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(Modifier.height(6.dp))

                // duration · play button · rating
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text  = if (novel.latestChapter.isNotBlank())
                            novel.latestChapter.take(7) else "Ch.—",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )

                    // Small solid play button
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(AccentBlue),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Rounded.PlayArrow,
                            contentDescription = "Read",
                            tint     = Color.White,
                            modifier = Modifier.size(12.dp)
                        )
                    }

                    if (novel.rating.isNotBlank()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Rounded.Star,
                                contentDescription = null,
                                tint     = StarGold,
                                modifier = Modifier.size(10.dp)
                            )
                            Spacer(Modifier.width(2.dp))
                            Text(
                                novel.rating,
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        Spacer(Modifier.width(1.dp))
                    }
                }
            }
        }
    }
}

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
            Text(state.message, color = MaterialTheme.colorScheme.error)
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
                style    = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color    = MaterialTheme.colorScheme.onBackground
            )
            if (novel.genres.isNotBlank()) {
                Spacer(Modifier.height(3.dp))
                Text(
                    novel.genres.split(",").take(2).joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
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
        // Hero skeleton — inset, rounded, ~160dp
        Box(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .height(160.dp)
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
        // + a labeled row + a horizontal scroll of 130dp-wide novel cards,
        // matching GenreRow exactly
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
                    Column(
                        Modifier
                            .width(130.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(shimmer)
                    ) {
                        Spacer(Modifier.fillMaxWidth().height(130.dp))
                        Spacer(Modifier.height(40.dp))
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
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium
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
