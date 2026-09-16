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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
            TopNavBar(onDownloadsClick = onDownloadsClick)

            // Search lives exclusively in the SearchOverlay (opened from the
            // bottom nav) — the homepage itself is browse-only, no inline bar.
            BrowseContent(
                state             = browseState,
                popularState      = popularState,
                onNovelClick      = onNovelClick,
                onRetry           = vm::loadHomepage,
                onGenreClick      = onGenreClick ?: {},
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
private fun TopNavBar(onDownloadsClick: (() -> Unit)?) {
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
            // Avatar — left
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "T",
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.ExtraBold
                    )
                )
            }

            // Logo — true center
            Text(
                "nCrawl",
                modifier = Modifier.align(Alignment.Center),
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight    = FontWeight.ExtraBold,
                    fontSize      = 20.sp,
                    letterSpacing = (-0.5).sp
                ),
                color = MaterialTheme.colorScheme.onSurface
            )

            // Download icon — right
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable(enabled = onDownloadsClick != null) {
                        onDownloadsClick?.invoke()
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Rounded.Download,
                    contentDescription = "Downloads",
                    tint     = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(17.dp)
                )
            }
        }
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
            val latestGenreRows  = remember(novels) { groupByTopGenres(novels.drop(1)) }
            val popularNovels    = (popularState as? BrowseUiState.Success)?.novels ?: emptyList()
            val popularGenreRows = remember(popularNovels) { groupByTopGenres(popularNovels) }

            // First 3 of the curated genre list power the new decorative
            // showcase cards (replaces the old pill-chip filter strip).
            val showcaseGenres = listOf("Fantasy", "Action", "Romance")

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

                // ── Genre showcase — decorative gradient-font cards
                // (replaces the old pill/chip filter strip). Each card
                // opens that genre's full list via Discover's GenreScreen.
                item {
                    Spacer(Modifier.height(24.dp))
                    GenreShowcaseRow(
                        genres    = showcaseGenres,
                        onGenreClick = onGenreClick
                    )
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
// Replaces the old pill/chip filter strip with 3 equal-width glass cards,
// each genre rendered in its own decorative font + gradient, mirroring the
// "Option 2" comparison design (Pacifico / Cinzel / Anton, all OFL-licensed
// and bundled — see ui/theme/Fonts.kt).
@Composable
private fun GenreShowcaseRow(
    genres: List<String>,
    onGenreClick: (String) -> Unit
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
            // Note: no full "all genres" screen exists yet, so this label is
            // intentionally non-interactive for now rather than a dead link.
            Text(
                "See all",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        }
        Spacer(Modifier.height(14.dp))
        Row(
            modifier              = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            genres.take(3).forEachIndexed { i, genre ->
                GenreDecorativeCard(
                    genre    = genre,
                    styleIdx = i % 3,
                    onClick  = { onGenreClick(genre) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun GenreDecorativeCard(
    genre: String,
    styleIdx: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(90.dp)
            .shadow(elevation = 10.dp, shape = RoundedCornerShape(18.dp), clip = false)
            .clip(RoundedCornerShape(18.dp))
            .background(GlassSurfaceLight)
            .clickable(onClick = onClick)
            .padding(8.dp),
        contentAlignment = Alignment.Center
    ) {
        when (styleIdx) {
            // Style 0 — Pacifico cursive, warm pink-orange gradient
            0 -> Text(
                text  = genre,
                style = TextStyle(
                    fontFamily = PacificoFamily,
                    fontSize   = 19.sp,
                    brush      = Brush.linearGradient(
                        colors = listOf(Color(0xFFFF416C), Color(0xFFFF4B2B))
                    )
                ),
                textAlign = TextAlign.Center
            )
            // Style 1 — Cinzel serif bold, epic purple gradient
            1 -> Text(
                text  = genre,
                style = TextStyle(
                    fontFamily    = CinzelFamily,
                    fontWeight    = FontWeight.Bold,
                    fontSize      = 16.sp,
                    letterSpacing = 2.sp,
                    brush         = Brush.linearGradient(
                        colors = listOf(Color(0xFF8E2DE2), Color(0xFF4A00E0))
                    )
                ),
                textAlign = TextAlign.Center
            )
            // Style 2 — Anton (Impact-style) caps, fiery orange-red gradient
            else -> Text(
                text  = genre.uppercase(),
                style = TextStyle(
                    fontFamily    = AntonFamily,
                    fontSize      = 21.sp,
                    letterSpacing = 1.sp,
                    brush         = Brush.linearGradient(
                        colors = listOf(Color(0xFFF97316), Color(0xFFDC2626))
                    )
                ),
                textAlign = TextAlign.Center
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
    val bg        = if (isActive) AccentBlue else GlassSurfaceLight
    val border    = if (isActive) Color.Transparent else GlassBorderLight
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
        Spacer(Modifier.height(20.dp))

        // Chips row — 50dp tall
        Row(
            Modifier.padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            repeat(4) {
                Box(
                    Modifier
                        .size(80.dp, 50.dp)
                        .clip(RoundedCornerShape(25.dp))
                        .background(shimmer)
                )
            }
        }
        Spacer(Modifier.height(24.dp))

        // Section label
        Box(
            Modifier
                .padding(horizontal = 16.dp)
                .size(120.dp, 16.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(shimmer)
        )
        Spacer(Modifier.height(12.dp))

        // Novel cards — 2-col landscape grid
        Column(
            Modifier.padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            repeat(2) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    repeat(2) {
                        Column(
                            Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(16.dp))
                                .background(shimmer)
                        ) {
                            Spacer(Modifier.fillMaxWidth().height(130.dp))
                            Spacer(Modifier.height(30.dp))
                        }
                    }
                }
            }
        }
    }
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
