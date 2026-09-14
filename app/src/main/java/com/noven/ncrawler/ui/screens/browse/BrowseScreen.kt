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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
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
import androidx.compose.ui.unit.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.noven.ncrawler.data.db.NovelEntity
import com.noven.ncrawler.ui.theme.*
import com.noven.ncrawler.viewmodel.BrowseUiState
import com.noven.ncrawler.viewmodel.BrowseViewModel
import kotlinx.coroutines.delay

// ── Dark glass tokens — used throughout this screen ──────────────────────────
// Matches the "frosted glass over dark imagery" look from the Disney+ snippet.
// True backdrop blur needs API 31+; we achieve the same feel on API 26+ using
// a semi-opaque dark fill + 1-px white hairline + a tinted diffuse shadow.
private val DarkGlassFill    = Color(0x1AFFFFFF)  // 10% white over dark bg
private val DarkGlassBorder  = Color(0x33FFFFFF)  // 20% white hairline
private val DarkGlassFillMd  = Color(0x26FFFFFF)  // 15% white — slightly more opaque pills

@Composable
fun BrowseScreen(
    onNovelClick: (slug: String) -> Unit,
    onContinueReading: (() -> Unit)? = null,
    lastReadNovelName: String? = null,
    vm: BrowseViewModel = viewModel()
) {
    val browseState by vm.browseState.collectAsStateWithLifecycle()
    val searchState by vm.searchState.collectAsStateWithLifecycle()
    val query       by vm.query.collectAsStateWithLifecycle()
    val isSearching  = query.isNotBlank()

    val focusManager = LocalFocusManager.current
    val keyboard     = LocalSoftwareKeyboardController.current

    // Dark background fills the entire screen — hero image bleeds to edges
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {

            // ── Top bar (floats over content, transparent) ─────────────────
            TopNavBar(
                isSearching   = isSearching,
                query         = query,
                onQueryChange = vm::onQueryChange,
                onClearSearch = {
                    vm.clearSearch()
                    focusManager.clearFocus()
                    keyboard?.hide()
                }
            )

            if (isSearching) {
                SearchContent(
                    state        = searchState,
                    query        = query,
                    onNovelClick = onNovelClick
                )
            } else {
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
}

// ── Top Nav Bar ───────────────────────────────────────────────────────────────
// Transparent in browse mode (hero bleeds through), switches to search field.
@Composable
private fun TopNavBar(
    isSearching: Boolean,
    query: String,
    onQueryChange: (String) -> Unit,
    onClearSearch: () -> Unit
) {
    val focusManager = LocalFocusManager.current
    val keyboard     = LocalSoftwareKeyboardController.current

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
    ) {
        AnimatedContent(
            targetState   = isSearching,
            transitionSpec = {
                fadeIn(tween(180)) togetherWith fadeOut(tween(120))
            },
            label = "topBarMode"
        ) { searching ->
            if (searching) {
                // ── Search mode ───────────────────────────────────────────
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .background(MaterialTheme.colorScheme.background)
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onClearSearch) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back",
                            tint = MaterialTheme.colorScheme.onBackground)
                    }
                    OutlinedTextField(
                        value         = query,
                        onValueChange = onQueryChange,
                        modifier      = Modifier.weight(1f),
                        placeholder   = {
                            Text("Search novels…",
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor   = AccentBlue,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                            focusedTextColor     = MaterialTheme.colorScheme.onBackground,
                            unfocusedTextColor   = MaterialTheme.colorScheme.onBackground,
                            cursorColor          = AccentBlue
                        ),
                        shape = RoundedCornerShape(14.dp),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = {
                            focusManager.clearFocus()
                            keyboard?.hide()
                        })
                    )
                    if (query.isNotBlank()) {
                        IconButton(onClick = onClearSearch) {
                            Icon(Icons.Default.Close, "Clear",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            } else {
                // ── Normal mode — transparent bar over hero ───────────────
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .padding(horizontal = 16.dp),
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Avatar
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(DarkGlassFillMd)
                            .border(1.dp, DarkGlassBorder, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "T",
                            color = Color.White,
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.ExtraBold
                            )
                        )
                    }

                    // Logo
                    Text(
                        "nCrawl",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight    = FontWeight.ExtraBold,
                            fontSize      = 22.sp,
                            letterSpacing = (-0.5).sp
                        ),
                        color = Color.White
                    )

                    // Search icon pill
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(DarkGlassFillMd)
                            .border(1.dp, DarkGlassBorder, CircleShape)
                            .clickable { /* search tap handled by text field focus */ },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = "Search",
                            tint     = Color.White,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                }
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

                // ── Latest Updates ────────────────────────────────────────
                item {
                    Spacer(Modifier.height(24.dp))
                    SectionHeader("Latest Updates")
                    Spacer(Modifier.height(12.dp))
                    LazyRow(
                        contentPadding        = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(latestNovels, key = { it.slug }) { novel ->
                            NovelCard(novel = novel, onClick = { onNovelClick(novel.slug) })
                        }
                    }
                }

                // ── Popular ───────────────────────────────────────────────
                item {
                    Spacer(Modifier.height(28.dp))
                    SectionHeader("Popular")
                    Spacer(Modifier.height(12.dp))
                    LazyRow(
                        contentPadding        = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(popularNovels, key = { it.slug + "_p" }) { novel ->
                            NovelCard(novel = novel, onClick = { onNovelClick(novel.slug) })
                        }
                    }
                    Spacer(Modifier.height(20.dp))
                }
            }
        }
    }
}

// ── Hero Banner — full-bleed, no rounded corners, deep scrim ──────────────────
// Mirrors the Disney+ Luca hero: cover image fills the card, gradient from
// transparent to near-black at the bottom, title + frosted CTA pill over it.
@Composable
private fun HeroBanner(novel: NovelEntity, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(420.dp)
            .clickable(onClick = onClick)
    ) {
        // Cover image — full bleed
        AsyncImage(
            model              = novel.coverUrl,
            contentDescription = novel.title,
            contentScale       = ContentScale.Crop,
            modifier           = Modifier.fillMaxSize()
        )

        // Deep scrim — transparent top, near-black bottom (like Disney+ snippet)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0f    to Color.Black.copy(alpha = 0.05f),
                            0.35f to Color.Black.copy(alpha = 0.1f),
                            0.65f to Color.Black.copy(alpha = 0.55f),
                            1f    to Color.Black.copy(alpha = 0.93f)
                        )
                    )
                )
        )

        // Content — bottom-aligned
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 16.dp, end = 16.dp, bottom = 20.dp)
        ) {
            // Genre label — small caps style
            if (novel.genres.isNotBlank()) {
                Text(
                    novel.genres.split(",").take(2).joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall.copy(
                        letterSpacing = 1.5.sp
                    ),
                    color = Color.White.copy(alpha = 0.65f)
                )
                Spacer(Modifier.height(4.dp))
            }

            // Title — large, bold, tight
            Text(
                novel.title,
                style = MaterialTheme.typography.displaySmall.copy(
                    fontWeight    = FontWeight.ExtraBold,
                    fontSize      = 30.sp,
                    lineHeight    = 34.sp,
                    letterSpacing = (-0.5).sp
                ),
                color    = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(Modifier.height(14.dp))

            // Row: frosted "Start Reading" pill + rating badge
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Frosted-glass "Start Reading" pill — Disney+ play button feel
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50.dp))
                        .background(DarkGlassFillMd)
                        .border(1.dp, DarkGlassBorder, RoundedCornerShape(50.dp))
                        .padding(horizontal = 16.dp, vertical = 9.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint     = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(5.dp))
                        Text(
                            "Start Reading",
                            style = MaterialTheme.typography.labelLarge,
                            color = Color.White
                        )
                    }
                }

                // Rating badge
                if (novel.rating.isNotBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Star,
                            contentDescription = null,
                            tint     = StarGold,
                            modifier = Modifier.size(13.dp)
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
        color    = DarkGlassFill,
        border   = BorderStroke(1.dp, DarkGlassBorder),
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
                Icons.Default.ChevronRight,
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
// glass for inactive, solid accent for active.
@Composable
private fun GenreChip(name: String, isActive: Boolean) {
    val bg = if (isActive) AccentBlue else DarkGlassFill
    val border = if (isActive) Color.Transparent else DarkGlassBorder
    val textColor = Color.White

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(50.dp))
            .clickable { }
            .padding(horizontal = 14.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            name,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = textColor.copy(alpha = if (isActive) 1f else 0.75f)
        )
    }
}

// ── Novel Card ────────────────────────────────────────────────────────────────
// Portrait thumbnail card — mirrors the Moana/Encanto cards in the snippet:
// rounded image, title below, time + rating below that.
// Width ≈ 110dp, image 2:3 ratio (110×155dp).
@Composable
private fun NovelCard(novel: NovelEntity, onClick: () -> Unit) {
    val cardWidth = 110.dp
    val imgHeight = 155.dp

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue   = if (isPressed) 0.95f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessHigh),
        label         = "cardPress"
    )

    Column(
        modifier = Modifier
            .width(cardWidth)
            .graphicsLayer(scaleX = scale, scaleY = scale)
            .clickable(
                interactionSource = interactionSource,
                indication        = null,
                onClick           = onClick
            ),
        horizontalAlignment = Alignment.Start
    ) {
        // Cover image with frosted play icon (top-right corner)
        Box(
            modifier = Modifier
                .width(cardWidth)
                .height(imgHeight)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            AsyncImage(
                model              = novel.coverUrl,
                contentDescription = novel.title,
                contentScale       = ContentScale.Crop,
                modifier           = Modifier.fillMaxSize()
            )
            // Subtle bottom gradient for readability
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colorStops = arrayOf(
                                0.6f to Color.Transparent,
                                1f   to Color.Black.copy(alpha = 0.45f)
                            )
                        )
                    )
            )
            // Play button — frosted, bottom-right
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(7.dp)
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(DarkGlassFillMd)
                    .border(1.dp, DarkGlassBorder, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.PlayArrow,
                    contentDescription = "Read",
                    tint     = Color.White,
                    modifier = Modifier.size(14.dp)
                )
            }
        }

        Spacer(Modifier.height(6.dp))

        // Title
        Text(
            novel.title,
            style    = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.SemiBold,
                fontSize   = 11.sp
            ),
            color    = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(cardWidth)
        )

        Spacer(Modifier.height(3.dp))

        // Chapter + rating row
        Row(
            modifier              = Modifier.width(cardWidth),
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
            if (novel.rating.isNotBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Star,
                        contentDescription = null,
                        tint     = StarGold,
                        modifier = Modifier.size(9.dp)
                    )
                    Spacer(Modifier.width(2.dp))
                    Text(
                        novel.rating,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
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
            Icons.Default.ChevronRight,
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
        // Hero skeleton
        Box(
            Modifier
                .fillMaxWidth()
                .height(420.dp)
                .background(shimmer)
        )
        Spacer(Modifier.height(16.dp))

        // Chips row
        Row(
            Modifier.padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            repeat(4) {
                Box(
                    Modifier
                        .size(72.dp, 30.dp)
                        .clip(RoundedCornerShape(50.dp))
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

        // Novel cards row
        Row(
            Modifier.padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            repeat(4) {
                Column(horizontalAlignment = Alignment.Start) {
                    Box(
                        Modifier
                            .size(110.dp, 155.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(shimmer)
                    )
                    Spacer(Modifier.height(6.dp))
                    Box(
                        Modifier
                            .size(80.dp, 10.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(shimmer)
                    )
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
                Icons.Default.SearchOff,
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
                Icons.Default.WifiOff,
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
