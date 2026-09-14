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
import com.noven.ncrawler.ui.theme.NavBlue
import com.noven.ncrawler.ui.theme.NavBgColor
import com.noven.ncrawler.ui.theme.AccentBlue
import com.noven.ncrawler.ui.theme.StarGold
import com.noven.ncrawler.ui.theme.GlassSurfaceLight
import com.noven.ncrawler.ui.theme.GlassBorderLight
import com.noven.ncrawler.viewmodel.BrowseUiState
import com.noven.ncrawler.viewmodel.BrowseViewModel
import kotlinx.coroutines.delay

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
    val isSearching = query.isNotBlank()

    val focusManager = LocalFocusManager.current
    val keyboard     = LocalSoftwareKeyboardController.current

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // ── Top Navigation Bar ────────────────────────────────────────
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

            // ── Content ───────────────────────────────────────────────────
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
            .background(NavBgColor)
            .statusBarsPadding()
    ) {
        if (isSearching) {
            // Search mode — full-width search field
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onClearSearch) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = NavBlue)
                }
                OutlinedTextField(
                    value         = query,
                    onValueChange = onQueryChange,
                    modifier      = Modifier.weight(1f),
                    placeholder   = { Text("Search novels…", color = NavBlue.copy(alpha = 0.5f)) },
                    singleLine    = true,
                    colors        = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor   = NavBlue,
                        unfocusedBorderColor = NavBlue.copy(alpha = 0.3f),
                        focusedTextColor     = NavBlue,
                        unfocusedTextColor   = NavBlue,
                        cursorColor          = NavBlue
                    ),
                    shape = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = {
                        focusManager.clearFocus()
                        keyboard?.hide()
                    })
                )
                if (query.isNotBlank()) {
                    IconButton(onClick = onClearSearch) {
                        Icon(Icons.Default.Close, "Clear", tint = NavBlue)
                    }
                }
            }
        } else {
            // Normal mode
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(horizontal = 16.dp),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Profile avatar — circular
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(NavBlue),
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

                // nCrawl title — centered
                Text(
                    "nCrawl",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight   = FontWeight.ExtraBold,
                        fontSize     = 22.sp,
                        letterSpacing = (-0.5).sp
                    ),
                    color = NavBlue
                )

                // Downloads button — circular
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .border(2.dp, NavBlue, CircleShape)
                        .background(Color.Transparent),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Download,
                        contentDescription = "Downloads",
                        tint     = NavBlue,
                        modifier = Modifier.size(18.dp)
                    )
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
            val sources       = listOf("FreeWebNovel", "NovelFull", "NovelBin", "WuxiaWorld")

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 120.dp) // nav clearance
            ) {
                // Hero banner — inset, rounded, matches the frosted "Start Reading" pill
                if (hero != null) {
                    item {
                        Spacer(Modifier.height(12.dp))
                        HeroBanner(novel = hero, onClick = { onNovelClick(hero.slug) })
                    }
                }

                // Continue reading — only shown once there's something to resume
                if (onContinueReading != null && !lastReadNovelName.isNullOrBlank()) {
                    item {
                        Spacer(Modifier.height(16.dp))
                        ContinueReadingRow(
                            novelName = lastReadNovelName,
                            onClick   = onContinueReading
                        )
                    }
                }

                // Source chips
                item {
                    Spacer(Modifier.height(20.dp))
                    SectionLabel("Sources")
                    Spacer(Modifier.height(8.dp))
                    LazyRow(
                        contentPadding        = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(sources) { source ->
                            SourceChip(
                                name      = source,
                                isActive  = source == "FreeWebNovel"
                            )
                        }
                    }
                }

                // Latest Updates
                item {
                    Spacer(Modifier.height(20.dp))
                    SectionLabel("Latest Updates")
                    Spacer(Modifier.height(10.dp))
                    LazyRow(
                        contentPadding        = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(latestNovels, key = { it.slug }) { novel ->
                            NovelCard(novel = novel, onClick = { onNovelClick(novel.slug) })
                        }
                    }
                }

                // Popular
                item {
                    Spacer(Modifier.height(20.dp))
                    SectionLabel("Popular")
                    Spacer(Modifier.height(10.dp))
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

// ── Continue Reading row — glass card, surfaces the paused novel ──────────────
@Composable
private fun ContinueReadingRow(novelName: String, onClick: () -> Unit) {
    Surface(
        onClick  = onClick,
        shape    = RoundedCornerShape(18.dp),
        color    = GlassSurfaceLight,
        border   = BorderStroke(1.dp, GlassBorderLight),
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .shadow(
                elevation    = 6.dp,
                shape        = RoundedCornerShape(18.dp),
                ambientColor = Color.Black.copy(alpha = 0.08f),
                spotColor    = Color.Black.copy(alpha = 0.12f)
            )
    ) {
        Row(
            modifier          = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(AccentBlue),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.PlayArrow,
                    contentDescription = null,
                    tint     = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Continue Reading",
                    style = MaterialTheme.typography.labelMedium,
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
            Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ── Section Label ─────────────────────────────────────────────────────────────
@Composable
private fun SectionLabel(title: String) {
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
                fontWeight   = FontWeight.ExtraBold,
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

// ── Source Chip ───────────────────────────────────────────────────────────────
@Composable
private fun SourceChip(name: String, isActive: Boolean) {
    Surface(
        shape  = RoundedCornerShape(24.dp),
        color  = if (isActive) AccentBlue else GlassSurfaceLight,
        border = if (isActive) null else BorderStroke(1.dp, GlassBorderLight),
        modifier = Modifier
            .height(30.dp)
            .then(
                if (!isActive) Modifier.shadow(
                    elevation    = 2.dp,
                    shape        = RoundedCornerShape(24.dp),
                    ambientColor = Color.Black.copy(alpha = 0.06f),
                    spotColor    = Color.Black.copy(alpha = 0.10f)
                ) else Modifier
            )
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier         = Modifier.padding(horizontal = 12.dp)
        ) {
            Text(
                name,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = if (isActive) Color.White
                        else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ── Hero Banner ───────────────────────────────────────────────────────────────
@Composable
private fun HeroBanner(novel: NovelEntity, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(220.dp)
            .clip(RoundedCornerShape(28.dp))
            .clickable(onClick = onClick)
    ) {
        // Cover image
        AsyncImage(
            model              = novel.coverUrl,
            contentDescription = novel.title,
            contentScale       = ContentScale.Crop,
            modifier           = Modifier.fillMaxSize()
        )
        // Gradient
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0f    to Color.Transparent,
                            0.45f to Color.Black.copy(alpha = 0.1f),
                            1f    to Color.Black.copy(alpha = 0.88f)
                        )
                    )
                )
        )
        // Info
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp)
        ) {
            if (novel.genres.isNotBlank()) {
                Text(
                    novel.genres.split(",").take(2).joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.7f)
                )
                Spacer(Modifier.height(2.dp))
            }
            Text(
                novel.title,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(8.dp))
            // Frosted-glass "Start Reading" pill
            Surface(
                shape  = RoundedCornerShape(10.dp),
                color  = GlassSurfaceLight,
                border = BorderStroke(1.dp, GlassBorderLight)
            ) {
                Row(
                    modifier          = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.PlayArrow, null,
                        tint     = AccentBlue,
                        modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "Start Reading",
                        style = MaterialTheme.typography.labelLarge,
                        color = AccentBlue
                    )
                }
            }
        }
    }
}

// ── Novel Card ────────────────────────────────────────────────────────────────
// Width: 2.5cm ≈ 94dp
// Image: 24px border radius, 2.2cm×2.2cm ≈ 83dp×83dp
// Title: scrolling marquee (7 chars then scroll)
// Chapter number + play button center + rating right
@Composable
private fun NovelCard(novel: NovelEntity, onClick: () -> Unit) {
    val cardWidth = 94.dp
    val imgSize   = 83.dp

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue   = if (isPressed) 0.94f else 1f,
        animationSpec = tween(120),
        label         = "cardPress"
    )

    Column(
        modifier = Modifier
            .width(cardWidth)
            .graphicsLayer(scaleX = scale, scaleY = scale)
            .clickable(
                interactionSource = interactionSource,
                indication        = null,
                onClick            = onClick
            ),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Cover image box
        Box(
            modifier = Modifier
                .size(imgSize)
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            AsyncImage(
                model              = novel.coverUrl,
                contentDescription = novel.title,
                contentScale       = ContentScale.Crop,
                modifier           = Modifier.fillMaxSize()
            )
            // Play button overlay — centered, frosted glass
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .align(Alignment.Center)
                    .clip(CircleShape)
                    .background(AccentBlue.copy(alpha = 0.85f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.PlayArrow,
                    contentDescription = "Read",
                    tint     = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        Spacer(Modifier.height(5.dp))

        // Scrolling title — shows 7 chars then marquee
        ScrollingTitle(
            text     = novel.title,
            modifier = Modifier.width(cardWidth)
        )

        Spacer(Modifier.height(2.dp))

        // Chapter + rating row
        Row(
            modifier              = Modifier.width(cardWidth),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text  = if (novel.latestChapter.isNotBlank())
                    novel.latestChapter.take(6) else "Ch.--",
                style = MaterialTheme.typography.labelSmall,
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
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

// ── Scrolling Title marquee ───────────────────────────────────────────────────
@Composable
private fun ScrollingTitle(text: String, modifier: Modifier = Modifier) {
    val shouldScroll = text.length > 7
    val scrollState  = rememberScrollState()

    LaunchedEffect(text) {
        if (shouldScroll) {
            delay(1000)
            while (true) {
                scrollState.animateScrollTo(
                    scrollState.maxValue,
                    animationSpec = tween(3000, easing = LinearEasing)
                )
                delay(800)
                scrollState.animateScrollTo(0, animationSpec = tween(500))
                delay(1200)
            }
        }
    }

    Row(
        modifier = modifier
            .horizontalScroll(scrollState, enabled = false)
    ) {
        Text(
            text     = text,
            style    = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
            color    = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            softWrap = false
        )
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
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(state.novels, key = { it.slug }) { novel ->
                SearchRow(novel = novel, onClick = { onNovelClick(novel.slug) })
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
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment     = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(52.dp, 72.dp)
                .clip(RoundedCornerShape(12.dp))
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
                style    = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (novel.genres.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    novel.genres.split(",").take(2).joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Icon(Icons.Default.ChevronRight, null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ── Skeleton states ───────────────────────────────────────────────────────────
@Composable
private fun BrowseSkeleton() {
    Column(modifier = Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxWidth().height(220.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant))
        Spacer(Modifier.height(16.dp))
        Row(Modifier.padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            repeat(3) {
                Box(Modifier.size(80.dp, 30.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant))
            }
        }
        Spacer(Modifier.height(20.dp))
        Box(Modifier.padding(horizontal = 16.dp).size(120.dp, 20.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant))
        Spacer(Modifier.height(10.dp))
        Row(Modifier.padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            repeat(4) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(83.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant))
                    Spacer(Modifier.height(5.dp))
                    Box(Modifier.size(70.dp, 10.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant))
                }
            }
        }
    }
}

@Composable private fun SearchSkeleton() {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        repeat(6) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(52.dp, 72.dp).clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(Modifier.size(140.dp, 14.dp).clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant))
                    Box(Modifier.size(90.dp, 10.dp).clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant))
                }
            }
        }
    }
}

@Composable private fun SearchEmpty(query: String) {
    Box(Modifier.fillMaxSize(), Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.SearchOff, null, Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
            Spacer(Modifier.height(12.dp))
            Text("No results for \"$query\"",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable private fun BrowseError(message: String, onRetry: () -> Unit) {
    Box(Modifier.fillMaxSize(), Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.WifiOff, null, Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f))
            Spacer(Modifier.height(12.dp))
            Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(16.dp))
            Button(onClick = onRetry,
                shape  = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AccentBlue)
            ) { Text("Retry") }
        }
    }
}
