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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
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
    onContinueReading: (() -> Unit)? = null,
    onDownloadsClick: (() -> Unit)? = null,
    lastReadNovelName: String? = null,
    vm: BrowseViewModel = viewModel()
) {
    val browseState by vm.browseState.collectAsStateWithLifecycle()

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
                onNovelClick      = onNovelClick,
                onRetry           = vm::loadHomepage,
                onContinueReading = onContinueReading,
                lastReadNovelName = lastReadNovelName
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
                OutlinedTextField(
                    value         = query,
                    onValueChange = vm::onQueryChange,
                    modifier      = Modifier
                        .weight(1f)
                        .focusRequester(focusRequester),
                    placeholder   = {
                        Text("Search novels…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    },
                    leadingIcon = {
                        Icon(Icons.Rounded.Search, contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor   = AccentBlue,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                        focusedTextColor     = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor   = MaterialTheme.colorScheme.onSurface,
                        cursorColor          = AccentBlue
                    ),
                    shape = RoundedCornerShape(16.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = {
                        vm.commitSearch(query)
                        focusManager.clearFocus()
                        keyboard?.hide()
                    })
                )
                Spacer(Modifier.width(8.dp))
                IconButton(onClick = {
                    vm.clearSearch()
                    onClose()
                }) {
                    Icon(
                        Icons.Rounded.Close,
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
                                Icon(
                                    Icons.Rounded.History,
                                    contentDescription = null,
                                    tint     = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    term,
                                    style    = MaterialTheme.typography.bodyMedium,
                                    color    = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f)
                                )
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
    onNovelClick: (String) -> Unit,
    onRetry: () -> Unit,
    onContinueReading: (() -> Unit)?,
    lastReadNovelName: String?
) {
    when (state) {
        is BrowseUiState.Loading -> BrowseSkeleton()
        is BrowseUiState.Error   -> BrowseError(state.message, onRetry)
        is BrowseUiState.Empty   -> BrowseError("No novels found", onRetry)
        is BrowseUiState.Success -> {
            val novels        = state.novels
            val hero          = novels.firstOrNull()
            val latestNovels  = novels.drop(1).take(20)
            val popularNovels = novels.take(15)
            val genres        = listOf("Fantasy", "Action", "Romance", "Sci-Fi", "Martial Arts")

            LazyColumn(
                modifier       = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 120.dp)
            ) {

                // ── Hero — full-bleed, no horizontal padding ──────────────
                if (hero != null) {
                    item {
                        HeroBanner(novel = hero, onClick = { onNovelClick(hero.slug) })
                    }
                }

                // ── Continue reading pill ─────────────────────────────────
                if (onContinueReading != null && !lastReadNovelName.isNullOrBlank()) {
                    item {
                        Spacer(Modifier.height(16.dp))
                        ContinueReadingRow(
                            novelName = lastReadNovelName,
                            onClick   = onContinueReading
                        )
                    }
                }

                // ── Genre chips ───────────────────────────────────────────
                item {
                    Spacer(Modifier.height(20.dp))
                    LazyRow(
                        contentPadding        = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        itemsIndexed(genres) { i, genre ->
                            GenreChip(
                                name     = genre,
                                isActive = i == 0
                            )
                        }
                    }
                }

                // ── Latest Updates — landscape cards, 2-col grid ────────────
                item {
                    Spacer(Modifier.height(24.dp))
                    SectionHeader("Latest Updates")
                    Spacer(Modifier.height(12.dp))
                    NovelGrid(novels = latestNovels, onNovelClick = onNovelClick)
                }

                // ── Popular — landscape cards, 2-col grid ───────────────────
                item {
                    Spacer(Modifier.height(28.dp))
                    SectionHeader("Popular")
                    Spacer(Modifier.height(12.dp))
                    NovelGrid(novels = popularNovels, onNovelClick = onNovelClick, keySuffix = "_p")
                    Spacer(Modifier.height(20.dp))
                }
            }
        }
    }
}

// ── Hero Banner — inset card, rounded corners, compact height ─────────────────
// NOT full-bleed: horizontal page padding + ~20dp corner radius, ~160dp tall
// (like the Disney+ "featured banner" card, not the full-screen Luca poster).
@Composable
private fun HeroBanner(novel: NovelEntity, onClick: () -> Unit) {
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
                            "Start Reading",
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

// ── Continue Reading row ──────────────────────────────────────────────────────
@Composable
private fun ContinueReadingRow(novelName: String, onClick: () -> Unit) {
    Surface(
        onClick  = onClick,
        shape    = RoundedCornerShape(18.dp),
        color    = GlassSurfaceLight,
        border   = BorderStroke(1.dp, GlassBorderLight),
        shadowElevation = 2.dp,
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
    ) {
        Row(
            modifier          = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(AccentBlue),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.PlayArrow,
                    contentDescription = null,
                    tint     = Color.White,
                    modifier = Modifier.size(17.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Continue Reading",
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.5.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    novelName,
                    style    = MaterialTheme.typography.titleSmall,
                    color    = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(
                Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
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
// Mirrors the "Channel" chips (Disney / Pixar / Marvel) from the snippet —
// tall (50dp), white frosted-glass, bold dark text. Never dark glass; the
// active chip swaps to solid accent + white text for affordance only.
@Composable
private fun GenreChip(name: String, isActive: Boolean) {
    val bg        = if (isActive) AccentBlue else GlassSurfaceLight
    val border    = if (isActive) Color.Transparent else GlassBorderLight
    val textColor = if (isActive) Color.White else MaterialTheme.colorScheme.onSurface

    Box(
        modifier = Modifier
            .height(50.dp)
            .clip(RoundedCornerShape(25.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(25.dp))
            .clickable { }
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

// ── Novel Grid ────────────────────────────────────────────────────────────────
// 2-column grid of landscape cards (Moana/Encanto style) — NOT a horizontal
// portrait scroll. Built with chunked Rows rather than LazyVerticalGrid since
// it lives inside an outer LazyColumn item (avoids nested-scroll conflicts);
// section sizes here (≤20 items) are small enough that this costs nothing.
@Composable
private fun NovelGrid(
    novels: List<NovelEntity>,
    onNovelClick: (String) -> Unit,
    keySuffix: String = ""
) {
    Column(
        modifier            = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        novels.chunked(2).forEach { pair ->
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                pair.forEach { novel ->
                    key(novel.slug + keySuffix) {
                        NovelCard(
                            novel    = novel,
                            onClick  = { onNovelClick(novel.slug) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                // Odd item out on the last row — keep it half-width, not stretched
                if (pair.size == 1) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
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
                    .height(90.dp)
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
                            Spacer(Modifier.fillMaxWidth().height(90.dp))
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
