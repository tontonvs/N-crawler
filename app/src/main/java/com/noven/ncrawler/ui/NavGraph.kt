package com.noven.ncrawler.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.noven.ncrawler.ui.screens.browse.BrowseScreen
import com.noven.ncrawler.ui.screens.browse.SearchOverlay
import com.noven.ncrawler.ui.screens.detail.DetailScreen
import com.noven.ncrawler.ui.screens.downloads.DownloadsScreen
import com.noven.ncrawler.ui.screens.library.LibraryScreen
import com.noven.ncrawler.ui.screens.reader.ReaderScreen

// ─── Route constants ──────────────────────────────────────────────────────────

object Routes {
    const val BROWSE    = "browse"
    const val DETAIL    = "detail/{slug}"
    const val READER    = "reader/{slug}/{chapterNum}"
    const val LIBRARY   = "library"
    const val DOWNLOADS = "downloads"
    const val SEARCH    = "search"

    fun detail(slug: String)                         = "detail/${slug.enc()}"
    fun reader(slug: String, chapterNum: Int)        = "reader/${slug.enc()}/$chapterNum"

    private fun String.enc() = java.net.URLEncoder.encode(this, "UTF-8")
    fun String.dec()         = java.net.URLDecoder.decode(this, "UTF-8")
}

// ─── Root nav graph ───────────────────────────────────────────────────────────

@Composable
fun NCrawlerNavGraph() {
    val nav = rememberNavController()

    NavHost(navController = nav, startDestination = Routes.BROWSE) {

        // ── Browse ────────────────────────────────────────────────────────
        // CHANGE: BrowseScreen now receives all three lambdas it requires
        // Advantage: cards are tappable and navigate correctly
        // Disadvantage: none
        composable(Routes.BROWSE) {
            BrowseScreen(
                onNovelClick      = { slug -> nav.navigate(Routes.detail(slug)) },
                onContinueReading = { slug, chapter ->
                    nav.navigate(Routes.reader(slug, chapter))
                },
                onDownloadsClick  = { nav.navigate(Routes.DOWNLOADS) }
            )
        }

        // ── Search overlay ────────────────────────────────────────────────
        composable(Routes.SEARCH) {
            // SearchOverlay needs a BrowseViewModel — share via the Browse
            // back-stack entry so the same VM instance is reused
            val browseEntry = nav.getBackStackEntry(Routes.BROWSE)
            SearchOverlay(
                // SearchOverlay takes a BrowseViewModel — we get it from
                // the Browse destination so state is shared
                vm          = androidx.lifecycle.viewmodel.compose.viewModel(browseEntry),
                onNovelClick = { slug ->
                    nav.navigate(Routes.detail(slug)) {
                        popUpTo(Routes.SEARCH) { inclusive = true }
                    }
                },
                onClose = { nav.popBackStack() }
            )
        }

        // ── Detail ────────────────────────────────────────────────────────
        composable(
            route     = Routes.DETAIL,
            arguments = listOf(navArgument("slug") { type = NavType.StringType })
        ) { entry ->
            val slug = entry.arguments?.getString("slug")
                ?.let { Routes.run { it.dec() } } ?: ""
            DetailScreen(
                slug          = slug,
                onBack        = { nav.popBackStack() },
                onReadChapter = { chapterNum ->
                    nav.navigate(Routes.reader(slug, chapterNum))
                }
            )
        }

        // ── Reader ────────────────────────────────────────────────────────
        composable(
            route     = Routes.READER,
            arguments = listOf(
                navArgument("slug")       { type = NavType.StringType },
                navArgument("chapterNum") { type = NavType.IntType }
            )
        ) { entry ->
            val slug       = entry.arguments?.getString("slug")
                ?.let { Routes.run { it.dec() } } ?: ""
            val chapterNum = entry.arguments?.getInt("chapterNum") ?: 1
            ReaderScreen(
                slug       = slug,
                chapterNum = chapterNum,
                onBack     = { nav.popBackStack() }
            )
        }

        // ── Library ───────────────────────────────────────────────────────
        composable(Routes.LIBRARY) {
            LibraryScreen(
                onNovelClick      = { slug -> nav.navigate(Routes.detail(slug)) },
                onContinueReading = { slug, chapter ->
                    nav.navigate(Routes.reader(slug, chapter))
                }
            )
        }

        // ── Downloads ─────────────────────────────────────────────────────
        composable(Routes.DOWNLOADS) {
            DownloadsScreen(
                onNovelClick = { slug -> nav.navigate(Routes.detail(slug)) }
            )
        }
    }
}
