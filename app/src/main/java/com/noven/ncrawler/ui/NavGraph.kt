package com.noven.ncrawler.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.noven.ncrawler.ui.components.NavIcons
import com.noven.ncrawler.ui.screens.browse.BrowseScreen
import com.noven.ncrawler.ui.screens.browse.SearchOverlay
import com.noven.ncrawler.ui.screens.detail.DetailScreen
import com.noven.ncrawler.ui.screens.discover.DiscoverScreen
import com.noven.ncrawler.ui.screens.discover.GenreScreen
import com.noven.ncrawler.ui.screens.downloads.DownloadsScreen
import com.noven.ncrawler.ui.screens.library.LibraryScreen
import com.noven.ncrawler.ui.screens.reader.ReaderScreen
import com.noven.ncrawler.ui.screens.settings.SourceSettingsScreen
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
    // from the search FAB, reachable from any tab — shares the same query/
    // results/recent-searches state as the Browse screen itself.
    val browseVm: BrowseViewModel = viewModel()
    var showSearchOverlay by remember { mutableStateOf(false) }

    // Back closes the search overlay before it leaves the screen behind it
    BackHandler(enabled = showSearchOverlay) { showSearchOverlay = false }

    // FIX: tapping a pill icon ALWAYS opens that tab's own screen, from wherever
    // you are. Two earlier bugs:
    //  1. the tap was ignored when route == currentRoute, so with the search
    //     overlay open over Home, tapping Home did nothing;
    //  2. popUpTo(saveState = true) + restoreState = true made a tab tap
    //     RESTORE the saved back stack. Tapping Home (the graph's start
    //     destination) after leaving it for Settings restored the Settings
    //     screen you had just popped — so Downloads → Settings → Home
    //     appeared to do nothing — and a tab could likewise revive a stale
    //     nested screen (Discover → Genre → tap Discover).
    // Now nothing is saved or restored on tab taps:
    //  - Home is never popped (it's the start destination), so it just drops
    //    everything above it — and keeps its own scroll position;
    //  - Library / Discover / Settings pop back to Home and open fresh, so the
    //    tab's root screen is always what you get.
    fun openTab(route: String) {
        showSearchOverlay = false
        if (route == currentRoute) return
        if (route == Routes.BROWSE) {
            if (!nav.popBackStack(Routes.BROWSE, inclusive = false)) {
                nav.navigate(Routes.BROWSE) { launchSingleTop = true }
            }
        } else {
            nav.navigate(route) {
                popUpTo(Routes.BROWSE) { saveState = false }
                launchSingleTop = true
                restoreState    = false
            }
        }
    }

    // Which pill tab is highlighted (-1 = none: overlay open, or a screen with
    // no tab of its own such as Downloads)
    val selectedIndex = when {
        showSearchOverlay                                      -> -1
        currentRoute == Routes.BROWSE                          -> 0
        currentRoute == Routes.LIBRARY                         -> 1
        currentRoute == Routes.DISCOVER || currentRoute.startsWith("genre/") -> 2
        currentRoute == Routes.SETTINGS                        -> 3
        else                                                   -> -1
    }

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
                    // CHANGE: no onSettingsClick any more — the profile
                    // avatar is gone from the top bar; Settings is the gear
                    // in the floating nav.
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

        // ── Search overlay — reachable from any tab via the search FAB ──────
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
                selectedIndex = selectedIndex,
                searchOpen    = showSearchOverlay,
                onTab         = ::openTab,
                onSearchClick = { showSearchOverlay = !showSearchOverlay }
            )
        }
    }
}

// ── Floating nav: pill (Home · Library · Discover · Settings) + search FAB ───
// CHANGE: rebuilt after floating_pill_navigation_bar.html — a solid pill with a
// circular selector that slides (with a slight overshoot) behind the active
// icon; the active icon crossfades from outline to solid. The old glass
// capsule, the Search tab inside it and the blue "continue reading" play FAB
// are gone: search is the FAB now, in the reader's circle-button style, and the
// profile avatar became the Settings gear.
@Composable
private fun FloatingNavBar(
    selectedIndex: Int,
    searchOpen: Boolean,
    onTab: (String) -> Unit,
    onSearchClick: () -> Unit
) {
    Row(
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        PillNav(selectedIndex = selectedIndex, onTab = onTab)
        SearchFab(active = searchOpen, onClick = onSearchClick)
    }
}

private data class NavTab(
    val route: String,
    val label: String,
    val outline: ImageVector,
    val filled: ImageVector
)

private val navTabs = listOf(
    NavTab(Routes.BROWSE,   "Home",     NavIcons.HomeOutline,     NavIcons.HomeFilled),
    NavTab(Routes.LIBRARY,  "Library",  NavIcons.FolderOutline,   NavIcons.FolderFilled),
    NavTab(Routes.DISCOVER, "Discover", NavIcons.DiscoverOutline, NavIcons.DiscoverFilled),
    NavTab(Routes.SETTINGS, "Settings", NavIcons.SettingsOutline, NavIcons.SettingsFilled)
)

// CHANGE: a bit smaller than the HTML's geometry (48dp buttons, 8dp apart,
// 12/10dp pill padding) — 44dp buttons, 4dp apart, and only 6/5dp between the
// pill's edge and the buttons inside it. Everything (selector slide distance,
// FAB size) derives from these, so resizing again is a few numbers.
private val NavItemSize = 44.dp
private val NavItemGap  = 4.dp
private val NavIconSize = 22.dp     // a tiny bit bigger (was 20dp)
// 13% transparent: the pill and the search FAB are 87% opaque
private const val NavOpacity = 0.87f
private val NavPadH     = 6.dp
private val NavPadV     = 5.dp

// Light vs dark. The HTML pill is dark ink on a light page. On a dark app
// background a dark pill would nearly vanish, so dark mode inverts it: light
// pill, dark selector, dark outline icons, white solid icon.
private class NavPalette(
    val pill: Color,
    val selector: Color,
    val inactiveIcon: Color,
    val activeIcon: Color,
    val edge: Color
)

private val NavInk = Color(0xFF1E232D)

@Composable
private fun navPalette(): NavPalette =
    if (isSystemInDarkTheme()) NavPalette(
        pill         = Color(0xFFE6EAF2),
        selector     = NavInk,
        inactiveIcon = NavInk,
        activeIcon   = Color.White,
        edge         = Color.Black.copy(alpha = 0.08f)
    ) else NavPalette(
        pill         = NavInk,
        selector     = Color.White,
        inactiveIcon = Color.White,
        activeIcon   = NavInk,
        edge         = Color.White.copy(alpha = 0.12f)   // the HTML's inset highlight
    )

@Composable
private fun PillNav(selectedIndex: Int, onTab: (String) -> Unit) {
    val palette = navPalette()
    val shape   = RoundedCornerShape(50)

    // The HTML's cubic-bezier(0.34, 1.56, 0.64, 1), 0.4s — overshoots slightly
    // and settles. Clamped to 0 while nothing is selected so it doesn't slide
    // off to the left; it just fades out instead.
    val selectorX by animateDpAsState(
        targetValue   = (NavItemSize + NavItemGap) * selectedIndex.coerceAtLeast(0),
        animationSpec = tween(400, easing = CubicBezierEasing(0.34f, 1.56f, 0.64f, 1f)),
        label         = "navSelectorX"
    )
    val selectorAlpha by animateFloatAsState(
        targetValue   = if (selectedIndex >= 0) 1f else 0f,
        animationSpec = tween(200),
        label         = "navSelectorAlpha"
    )

    Box(
        modifier = Modifier
            .shadow(
                elevation    = 14.dp,
                shape        = shape,
                ambientColor = Color(0x330F172A),
                spotColor    = Color(0x660F172A)
            )
            .clip(shape)
            .background(palette.pill.copy(alpha = NavOpacity))
            .border(1.dp, palette.edge, shape)
            .padding(horizontal = NavPadH, vertical = NavPadV)
    ) {
        // Sliding selector circle
        Box(
            modifier = Modifier
                .offset { IntOffset(selectorX.roundToPx(), 0) }
                .size(NavItemSize)
                .graphicsLayer { alpha = selectorAlpha }
                .shadow(6.dp, CircleShape)
                .background(palette.selector, CircleShape)
        )

        Row(horizontalArrangement = Arrangement.spacedBy(NavItemGap)) {
            navTabs.forEachIndexed { index, tab ->
                PillNavItem(
                    tab      = tab,
                    selected = index == selectedIndex,
                    palette  = palette,
                    onClick  = { onTab(tab.route) }
                )
            }
        }
    }
}

// One button: outline icon (inactive colour) and solid icon (active colour)
// stacked, crossfading over 250ms; a small press-in scale like the HTML's
// :active state.
@Composable
private fun PillNavItem(
    tab: NavTab,
    selected: Boolean,
    palette: NavPalette,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue   = if (isPressed) 0.95f else 1f,
        animationSpec = tween(120),
        label         = "navItemPress"
    )
    val fill by animateFloatAsState(
        targetValue   = if (selected) 1f else 0f,
        animationSpec = tween(250),
        label         = "navItemFill"
    )

    Box(
        modifier = Modifier
            .size(NavItemSize)
            .clip(CircleShape)
            .semantics { contentDescription = tab.label }
            .clickable(
                interactionSource = interactionSource,
                indication        = null,
                role              = Role.Tab,
                onClick           = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector        = tab.outline,
            contentDescription = null,
            tint               = palette.inactiveIcon,
            modifier           = Modifier
                .size(NavIconSize)
                .graphicsLayer { alpha = 1f - fill; scaleX = pressScale; scaleY = pressScale }
        )
        Icon(
            imageVector        = tab.filled,
            contentDescription = null,
            tint               = palette.activeIcon,
            modifier           = Modifier
                .size(NavIconSize)
                .graphicsLayer { alpha = fill; scaleX = pressScale; scaleY = pressScale }
        )
    }
}

// ── Search FAB ───────────────────────────────────────────────────────────────
// Copies the reader's circle buttons (back / prev / next): circle (NavItemSize
// now, so it matches the pill's buttons; the reader's are 48dp) filled
// with the foreground colour at 13%, icon in the foreground colour. `fg` here
// is onSurface — ink in light mode, near-white in dark mode — and the 13% is
// composited onto the surface colour so the circle is solid (the reader's
// buttons sit on a solid page; this one floats over scrolling covers).
// While the search overlay is open the FAB inverts (solid fg, surface-coloured
// icon) to show it's active — the same inversion as the pill's selector.
@Composable
private fun SearchFab(active: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val fg     = scheme.onSurface
    val idleBg = fg.copy(alpha = 0.13f).compositeOver(scheme.surface)

    val bg by animateColorAsState(
        targetValue   = if (active) fg else idleBg,
        animationSpec = tween(200),
        label         = "searchFabBg"
    )
    val iconTint by animateColorAsState(
        targetValue   = if (active) scheme.surface else fg,
        animationSpec = tween(200),
        label         = "searchFabIcon"
    )

    // Occasional-frequency tap, so a small press-in spring is fine — it stays
    // under 150ms and never fires on load.
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue   = if (isPressed) 0.90f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label         = "searchFabPress"
    )

    Box(
        modifier = Modifier
            .size(NavItemSize)
            .graphicsLayer(scaleX = scale, scaleY = scale)
            .shadow(
                elevation    = 10.dp,
                shape        = CircleShape,
                ambientColor = Color(0x260F172A),
                spotColor    = Color(0x480F172A)
            )
            .clip(CircleShape)
            .background(bg.copy(alpha = NavOpacity))
            .clickable(
                interactionSource = interactionSource,
                indication        = null,
                role              = Role.Button,
                onClickLabel      = "Search",
                onClick           = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector        = NavIcons.Search,
            contentDescription = "Search",
            tint               = iconTint,
            modifier           = Modifier.size(NavIconSize)
        )
    }
}
