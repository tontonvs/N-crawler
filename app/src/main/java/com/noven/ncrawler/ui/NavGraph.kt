package com.noven.ncrawler.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.noven.ncrawler.NCrawlerApp
import com.noven.ncrawler.data.local.UpdateCheckStore
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.noven.ncrawler.ui.components.Motion
import com.noven.ncrawler.ui.components.glassSource
import com.noven.ncrawler.ui.theme.GlassMode
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.haze
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

// ── Screen transitions ───────────────────────────────────────────────────────
// Two kinds of move, so you always know where you are:
//  • Going DEEPER (detail / reader / genre / downloads): the new screen slides
//    in from the right over 300ms while the old one drifts back a little and
//    fades — going back plays the same thing in reverse, a touch faster.
//  • Switching TABS (home / library / discover / settings): a short 6% slide
//    toward the tab's side + fade. Tabs are tapped all day, so it stays light.
// Only slide + fade (GPU layers) — nothing re-measures during the move.
private fun String?.isDeeper(): Boolean =
    this != null && (startsWith("detail/") || startsWith("reader/") ||
        startsWith("genre/") || this == Routes.DOWNLOADS)

private fun String?.tabIndex(): Int = when (this) {
    Routes.BROWSE   -> 0
    Routes.LIBRARY  -> 1
    Routes.DISCOVER -> 2
    Routes.SETTINGS -> 3
    else            -> -1
}

// +1 = target tab sits to the right, -1 = left, 0 = no side (fade + tiny rise)
private fun tabDirection(from: String?, to: String?): Int {
    val a = from.tabIndex()
    val b = to.tabIndex()
    return if (a < 0 || b < 0) 0 else (b - a).coerceIn(-1, 1)
}

private fun tabEnter(from: String?, to: String?): EnterTransition {
    val dir = tabDirection(from, to)
    val fade = fadeIn(tween(200, delayMillis = 40, easing = LinearOutSlowInEasing))
    return if (dir == 0) {
        fade + slideInVertically(tween(260, easing = Motion.EaseOut)) { (it * 0.02f).toInt() }
    } else {
        fade + slideInHorizontally(tween(260, easing = Motion.EaseOut)) { (it * 0.06f).toInt() * dir }
    }
}

private fun tabExit(from: String?, to: String?): ExitTransition {
    val dir = tabDirection(from, to)
    val fade = fadeOut(tween(110, easing = LinearEasing))
    return if (dir == 0) fade
    else fade + slideOutHorizontally(tween(200, easing = Motion.EaseOut)) { -(it * 0.04f).toInt() * dir }
}

private fun AnimatedContentTransitionScope<NavBackStackEntry>.screenEnter(): EnterTransition {
    val from = initialState.destination.route
    val to   = targetState.destination.route
    return if (to.isDeeper()) {
        slideInHorizontally(tween(Motion.SCREEN_MS, easing = Motion.EaseOut)) { (it * 0.16f).toInt() } +
            fadeIn(tween(220, easing = LinearOutSlowInEasing))
    } else tabEnter(from, to)
}

private fun AnimatedContentTransitionScope<NavBackStackEntry>.screenExit(): ExitTransition {
    val from = initialState.destination.route
    val to   = targetState.destination.route
    return if (to.isDeeper()) {
        slideOutHorizontally(tween(Motion.SCREEN_MS, easing = Motion.EaseOut)) { -(it * 0.06f).toInt() } +
            fadeOut(tween(200, easing = LinearEasing))
    } else tabExit(from, to)
}

// Back: `initialState` is the screen leaving, `targetState` the one revealed.
private fun AnimatedContentTransitionScope<NavBackStackEntry>.screenPopEnter(): EnterTransition {
    val from = initialState.destination.route
    val to   = targetState.destination.route
    return if (from.isDeeper()) {
        slideInHorizontally(tween(Motion.SCREEN_MS, easing = Motion.EaseOut)) { -(it * 0.06f).toInt() } +
            fadeIn(tween(240, easing = LinearOutSlowInEasing))
    } else tabEnter(from, to)
}

private fun AnimatedContentTransitionScope<NavBackStackEntry>.screenPopExit(): ExitTransition {
    val from = initialState.destination.route
    val to   = targetState.destination.route
    return if (from.isDeeper()) {
        // Leaving is quicker and quieter than arriving.
        slideOutHorizontally(tween(240, easing = FastOutLinearInEasing)) { (it * 0.16f).toInt() } +
            fadeOut(tween(200, easing = LinearEasing))
    } else tabExit(from, to)
}

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

    // FIX: only the overlay's X cleared the query; back, tab taps and result taps
    // left the old search showing when you reopened it. Every close path now
    // goes through here.
    fun closeSearch() {
        showSearchOverlay = false
        browseVm.clearSearch()
    }

    // Back closes the search overlay before it leaves the screen behind it
    BackHandler(enabled = showSearchOverlay) { closeSearch() }

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
        if (showSearchOverlay) closeSearch()
        // Read the route NOW, from the controller. The `currentRoute` captured when
        // this function was created can be one recomposition old — and Home is the
        // only tab whose route is the one the app starts on, so a stale value made
        // `route == currentRoute` swallow the Home tap (every other tab still worked).
        val liveRoute = nav.currentBackStackEntry?.destination?.route
        if (route == liveRoute) return
        // Home goes through the same navigate + popUpTo path as the other tabs
        // (which work) instead of popBackStack(), whose "nothing to pop" / "route
        // not found" cases silently did nothing. popUpTo(BROWSE) drops everything
        // above Home; launchSingleTop reuses the Home entry that is left on top.
        nav.navigate(route) {
            popUpTo(Routes.BROWSE) { inclusive = false; saveState = false }
            launchSingleTop = true
            restoreState    = false
        }
    }

    // CHANGE (updates): gold badge on the Library tab = novels with new chapters found since
    // you last opened Library. Opening the tab marks them seen (the "New chapters" strip in
    // Library itself keeps listing them until read or dismissed).
    val appContext = LocalContext.current.applicationContext
    val updateNovels by remember { (appContext as NCrawlerApp).repository.updatesFlow() }
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val seenStore = remember { UpdateCheckStore(appContext) }
    var librarySeenAt by remember { mutableStateOf(seenStore.librarySeenAt()) }
    LaunchedEffect(currentRoute, updateNovels) {
        if (currentRoute == Routes.LIBRARY) {
            val now = System.currentTimeMillis()
            seenStore.markLibrarySeen(now)
            librarySeenAt = now
        }
    }
    val libraryBadge = updateNovels.count { it.updateFoundAt > librarySeenAt }

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

    // Glass mode: the nav host is the layer the floating nav blurs. The nav
    // itself is a SIBLING drawn above it (Haze can't blur its own parent).
    val hazeState = remember { HazeState() }

    // CHANGE (UI polish #5): the Search overlay is frosted glass — it blurs the screen
    // underneath with the same Haze setup as the nav. glassSource() only registers the
    // nav host as a blur source in Glass mode, so in Classic mode the host is registered
    // here, and only while the overlay is open. The 220ms tail keeps the source alive
    // through the overlay's 140ms fade-out; without it the blur would vanish the moment
    // the overlay starts closing and the screen behind would "pop" sharp for a beat.
    var searchHazeTail by remember { mutableStateOf(false) }
    LaunchedEffect(showSearchOverlay) {
        if (showSearchOverlay) {
            searchHazeTail = true
        } else {
            delay(220)
            searchHazeTail = false
        }
    }
    val searchNeedsHaze = !GlassMode.enabled && (showSearchOverlay || searchHazeTail)

    Box(modifier = Modifier.fillMaxSize()) {
        // Main nav host — no bottom padding, nav floats over content
        NavHost(
            navController    = nav,
            startDestination = Routes.BROWSE,
            modifier         = Modifier
                .fillMaxSize()
                .glassSource(hazeState)
                .then(if (searchNeedsHaze) Modifier.haze(hazeState) else Modifier),
            enterTransition    = { screenEnter() },
            exitTransition     = { screenExit() },
            popEnterTransition = { screenPopEnter() },
            popExitTransition  = { screenPopExit() }
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
                SourceSettingsScreen()
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
                    slug             = slug,
                    onBack           = { nav.popBackStack() },
                    onReadChapter    = { chapter -> nav.navigate(Routes.reader(slug, chapter)) },
                    // CHANGE (Downloads overhaul): the new download button on
                    // Detail opens the Downloads screen once a novel finishes
                    // downloading, so it stays useful after that first tap.
                    onDownloadsClick = { nav.navigate(Routes.DOWNLOADS) }
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
            // Rises 4% while fading in; closes faster than it opens.
            enter   = fadeIn(tween(200, easing = LinearOutSlowInEasing)) +
                      slideInVertically(tween(280, easing = Motion.EaseOut)) { (it * 0.04f).toInt() },
            exit    = fadeOut(tween(140, easing = LinearEasing))
        ) {
            SearchOverlay(
                vm           = browseVm,
                onNovelClick = { slug ->
                    closeSearch()
                    nav.navigate(Routes.detail(slug))
                },
                onClose      = { closeSearch() },
                hazeState    = hazeState
            )
        }

        // ── Floating bottom nav ────────────────────────────────────────────
        AnimatedVisibility(
            visible  = !isFullScreen,
            enter    = fadeIn(tween(220, easing = LinearOutSlowInEasing)) +
                       slideInVertically(tween(300, easing = Motion.EaseOut)) { it },
            exit     = fadeOut(tween(140, easing = LinearEasing)) +
                       slideOutVertically(tween(200, easing = FastOutLinearInEasing)) { it },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 16.dp)
        ) {
            FloatingNavBar(
                hazeState     = hazeState,
                selectedIndex = selectedIndex,
                searchOpen    = showSearchOverlay,
                badges        = mapOf(Routes.LIBRARY to libraryBadge),
                onTab         = ::openTab,
                onSearchClick = {
                    if (showSearchOverlay) {
                        closeSearch()
                    } else {
                        showSearchOverlay = true
                    }
                }
            )
        }
    }
}
