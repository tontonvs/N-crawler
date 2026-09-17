package com.noven.ncrawler.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.noven.ncrawler.ui.screens.browse.BrowseScreen
import com.noven.ncrawler.ui.screens.browse.SearchOverlay
import com.noven.ncrawler.ui.screens.detail.DetailScreen
import com.noven.ncrawler.ui.screens.discover.DiscoverScreen
import com.noven.ncrawler.ui.screens.discover.GenreScreen
import com.noven.ncrawler.ui.screens.downloads.DownloadsScreen
import com.noven.ncrawler.ui.screens.library.LibraryScreen
import com.noven.ncrawler.ui.screens.reader.ReaderScreen
import com.noven.ncrawler.ui.screens.settings.SourceSettingsScreen
import com.noven.ncrawler.ui.theme.AccentBlue
import com.noven.ncrawler.ui.theme.NavBlue
import com.noven.ncrawler.ui.theme.GlassSurfaceLight
import com.noven.ncrawler.ui.theme.GlassSurfaceDark
import com.noven.ncrawler.ui.theme.GlassBorderLight
import com.noven.ncrawler.ui.theme.GlassBorderDark
import com.noven.ncrawler.viewmodel.BrowseViewModel

object Routes {
    const val BROWSE    = "browse"
    const val LIBRARY   = "library"
    const val DOWNLOADS = "downloads"
    const val DISCOVER  = "discover"
    const val SETTINGS  = "settings"
    const val GENRE     = "genre/{genreName}"
    const val DETAIL    = "detail/{slug}"
    const val READER    = "reader/{slug}/{chapter}"

    fun detail(slug: String)               = "detail/$slug"
    fun reader(slug: String, chapter: Int) = "reader/$slug/$chapter"
    fun genre(name: String)                = "genre/${java.net.URLEncoder.encode(name, "UTF-8")}"
}

// Routes where bottom nav is hidden (immersive screens)
private val fullScreenRoutes = listOf("detail/", "reader/")

@Composable
fun NCrawlerNavGraph() {
    val nav          = rememberNavController()
    val currentEntry by nav.currentBackStackEntryAsState()
    val currentRoute  = currentEntry?.destination?.route ?: ""

    val isFullScreen = fullScreenRoutes.any { prefix ->
        currentRoute.startsWith(prefix)
    }

    // Hoisted here (not inside BrowseScreen) so the Search overlay — opened
    // from the bottom nav, reachable from any tab — shares the same query/
    // results/recent-searches state as the Browse screen itself. Also lets
    // the play FAB resume the same "last read" novel the hero card shows.
    val browseVm: BrowseViewModel = viewModel()
    val continueReading by browseVm.continueReading.collectAsStateWithLifecycle()
    var showSearchOverlay by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        // Main nav host — no bottom padding, nav floats over content
        NavHost(
            navController    = nav,
            startDestination = Routes.BROWSE,
            modifier         = Modifier.fillMaxSize()
        ) {
            composable(Routes.BROWSE) {
                BrowseScreen(
                    onNovelClick      = { slug -> nav.navigate(Routes.detail(slug)) },
                    onDownloadsClick  = { nav.navigate(Routes.DOWNLOADS) },
                    onContinueReading = { slug, chapter -> nav.navigate(Routes.reader(slug, chapter)) },
                    onGenreClick      = { genre -> nav.navigate(Routes.genre(genre)) },
                    // CHANGE: wires the new "See More" genre card + the
                    // "Genre" section's "See all" link to the existing
                    // Discover screen, which already lists every genre.
                    onDiscoverClick   = { nav.navigate(Routes.DISCOVER) },
                    // CHANGE: profile circle now opens the source picker
                    onSettingsClick   = { nav.navigate(Routes.SETTINGS) },
                    vm                = browseVm
                )
            }

            composable(Routes.SETTINGS) {
                SourceSettingsScreen(onBack = { nav.popBackStack() })
            }

            composable(Routes.LIBRARY) {
                LibraryScreen(
                    onNovelClick      = { slug -> nav.navigate(Routes.detail(slug)) },
                    onContinueReading = { slug, chapter ->
                        nav.navigate(Routes.reader(slug, chapter))
                    }
                )
            }

            composable(Routes.DOWNLOADS) {
                DownloadsScreen(
                    onNovelClick = { slug -> nav.navigate(Routes.detail(slug)) }
                )
            }

            composable(Routes.DISCOVER) {
                DiscoverScreen(
                    onGenreClick = { genre -> nav.navigate(Routes.genre(genre)) }
                )
            }

            composable(
                route     = Routes.GENRE,
                arguments = listOf(navArgument("genreName") { type = NavType.StringType })
            ) { back ->
                val encoded = back.arguments?.getString("genreName") ?: return@composable
                val genre   = java.net.URLDecoder.decode(encoded, "UTF-8")
                GenreScreen(
                    genre        = genre,
                    onBack       = { nav.popBackStack() },
                    onNovelClick = { slug -> nav.navigate(Routes.detail(slug)) }
                )
            }

            composable(
                route     = Routes.DETAIL,
                arguments = listOf(navArgument("slug") { type = NavType.StringType })
            ) { back ->
                val slug = back.arguments?.getString("slug") ?: return@composable
                DetailScreen(
                    slug          = slug,
                    onBack        = { nav.popBackStack() },
                    onReadChapter = { chapter -> nav.navigate(Routes.reader(slug, chapter)) }
                )
            }

            composable(
                route     = Routes.READER,
                arguments = listOf(
                    navArgument("slug")    { type = NavType.StringType },
                    navArgument("chapter") { type = NavType.IntType }
                )
            ) { back ->
                val slug    = back.arguments?.getString("slug")  ?: return@composable
                val chapter = back.arguments?.getInt("chapter")  ?: 1
                ReaderScreen(
                    slug       = slug,
                    chapterNum = chapter,
                    onBack     = { nav.popBackStack() }
                )
            }
        }

        // ── Search overlay — reachable from any tab via the bottom nav ──────
        // Rendered BEFORE the floating nav below so the nav paints on top of
        // it (Box z-order = composition order) — otherwise the overlay's
        // opaque background fully covers the nav, making it untappable.
        AnimatedVisibility(
            visible = showSearchOverlay,
            enter   = fadeIn(),
            exit    = fadeOut()
        ) {
            SearchOverlay(
                vm           = browseVm,
                onNovelClick = { slug ->
                    showSearchOverlay = false
                    nav.navigate(Routes.detail(slug))
                },
                onClose      = { showSearchOverlay = false }
            )
        }

        // ── Floating bottom nav ────────────────────────────────────────────
        AnimatedVisibility(
            visible  = !isFullScreen,
            enter    = fadeIn() + slideInVertically(initialOffsetY = { it }),
            exit     = fadeOut() + slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 16.dp)
        ) {
            FloatingNavBar(
                currentRoute        = currentRoute,
                isSearchOverlayOpen = showSearchOverlay,
                onNavigate          = { route ->
                    if (route != currentRoute) {
                        showSearchOverlay = false
                        nav.navigate(route) {
                            popUpTo(Routes.BROWSE) { saveState = true }
                            launchSingleTop = true
                            restoreState    = true
                        }
                    }
                },
                onOpenSearch = { showSearchOverlay = true },
                onFabClick   = {
                    // Resume the same last-read novel the hero card shows,
                    // at its exact chapter — falls back to Library when
                    // nothing has been read yet.
                    val cr = continueReading
                    if (cr != null) {
                        showSearchOverlay = false
                        nav.navigate(Routes.reader(cr.novel.slug, cr.progress.lastChapterNum))
                    } else if (currentRoute != Routes.LIBRARY) {
                        showSearchOverlay = false
                        nav.navigate(Routes.LIBRARY) {
                            popUpTo(Routes.BROWSE) { saveState = true }
                            launchSingleTop = true
                            restoreState    = true
                        }
                    }
                }
            )
        }
    }
}

// ── Floating Nav Bar ──────────────────────────────────────────────────────────
// Frosted glass pill with 3 icons + separate blue FAB (continue reading)
@Composable
private fun FloatingNavBar(
    currentRoute: String,
    isSearchOverlayOpen: Boolean,
    onNavigate: (String) -> Unit,
    onOpenSearch: () -> Unit,
    onFabClick: () -> Unit
) {
    val isDark      = isSystemInDarkTheme()
    val glassFill   = if (isDark) GlassSurfaceDark else GlassSurfaceLight
    val glassBorder = if (isDark) GlassBorderDark else GlassBorderLight

    Row(
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // ── Pill capsule — Home / Search / Library ─────────────────────────
        Surface(
            shape          = RoundedCornerShape(50.dp),
            color          = glassFill,
            border         = BorderStroke(1.dp, glassBorder),
            tonalElevation = 8.dp,
            modifier       = Modifier
                .shadow(
                    elevation    = 16.dp,
                    shape        = RoundedCornerShape(50.dp),
                    ambientColor = Color.Black.copy(alpha = 0.15f),
                    spotColor    = Color.Black.copy(alpha = 0.25f)
                )
        ) {
            Row(
                modifier              = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment     = Alignment.CenterVertically
            ) {
                NavPillItem(
                    icon        = Icons.Outlined.Home,
                    iconActive  = Icons.Rounded.Home,
                    label       = "Home",
                    isSelected  = currentRoute == Routes.BROWSE && !isSearchOverlayOpen,
                    onClick     = { onNavigate(Routes.BROWSE) }
                )
                NavPillItem(
                    icon        = Icons.Outlined.Search,
                    iconActive  = Icons.Rounded.Search,
                    label       = "Search",
                    isSelected  = isSearchOverlayOpen,
                    // Opens the full-screen search overlay (see NavGraph) —
                    // reachable from any tab, not just Browse.
                    onClick     = onOpenSearch
                )
                NavPillItem(
                    icon        = Icons.Outlined.FolderOpen,
                    iconActive  = Icons.Rounded.FolderOpen,
                    label       = "Library",
                    isSelected  = currentRoute == Routes.LIBRARY && !isSearchOverlayOpen,
                    onClick     = { onNavigate(Routes.LIBRARY) }
                )
                NavPillItem(
                    icon        = Icons.Outlined.Explore,
                    iconActive  = Icons.Rounded.Explore,
                    label       = "Discover",
                    isSelected  = (currentRoute == Routes.DISCOVER || currentRoute.startsWith("genre/"))
                        && !isSearchOverlayOpen,
                    onClick     = { onNavigate(Routes.DISCOVER) }
                )
            }
        }

        // ── Blue FAB — Continue Reading ────────────────────────────────────
        // WhatsApp-style: outside the capsule, blue circle, white play icon.
        // Occasional-frequency tap, so a small press-in spring (Jhey-style
        // delighter) is fine — it stays under 150ms and never fires on load.
        val interactionSource = remember { MutableInteractionSource() }
        val isPressed by interactionSource.collectIsPressedAsState()
        val scale by animateFloatAsState(
            targetValue   = if (isPressed) 0.90f else 1f,
            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
            label         = "fabPress"
        )

        Surface(
            shape    = CircleShape,
            color    = AccentBlue,
            modifier = Modifier
                .size(52.dp)
                .graphicsLayer(scaleX = scale, scaleY = scale)
                .shadow(
                    elevation    = 12.dp,
                    shape        = CircleShape,
                    spotColor    = AccentBlue.copy(alpha = 0.4f),
                    ambientColor = AccentBlue.copy(alpha = 0.2f)
                )
                .clickable(
                    interactionSource = interactionSource,
                    indication        = null,
                    onClick            = onFabClick
                )
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Rounded.PlayArrow,
                    contentDescription = "Continue Reading",
                    tint     = Color.White,
                    modifier = Modifier.size(26.dp)
                )
            }
        }
    }
}

// ── Nav pill item ─────────────────────────────────────────────────────────────
@Composable
private fun NavPillItem(
    icon: ImageVector,
    iconActive: ImageVector,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val bgColor by animateColorAsState(
        targetValue   = if (isSelected) AccentBlue.copy(alpha = 0.12f) else Color.Transparent,
        animationSpec = tween(200),
        label         = "navBg"
    )
    val iconColor by animateColorAsState(
        targetValue   = if (isSelected) AccentBlue else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(200),
        label         = "navIcon"
    )

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50.dp))
            .background(bgColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            if (isSelected) iconActive else icon,
            contentDescription = label,
            tint     = iconColor,
            modifier = Modifier.size(22.dp)
        )
    }
}
