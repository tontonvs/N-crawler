package com.noven.ncrawler.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.noven.ncrawler.ui.screens.browse.BrowseScreen
import com.noven.ncrawler.ui.screens.detail.DetailScreen
import com.noven.ncrawler.ui.screens.reader.ReaderScreen

object Routes {
    const val BROWSE  = "browse"
    const val DETAIL  = "detail/{slug}"
    const val READER  = "reader/{slug}/{chapter}"
    const val LIBRARY = "library"

    fun detail(slug: String)               = "detail/$slug"
    fun reader(slug: String, chapter: Int) = "reader/$slug/$chapter"
}

@Composable
fun NavGraph() {
    val nav = rememberNavController()

    NavHost(navController = nav, startDestination = Routes.BROWSE) {

        // ── Browse + Search ───────────────────────────────────────────────
        composable(Routes.BROWSE) {
            BrowseScreen(
                onNovelClick = { slug -> nav.navigate(Routes.detail(slug)) }
            )
        }

        // ── Novel Detail ──────────────────────────────────────────────────
        composable(
            route     = Routes.DETAIL,
            arguments = listOf(navArgument("slug") { type = NavType.StringType })
        ) { backStack ->
            val slug = backStack.arguments?.getString("slug") ?: return@composable
            DetailScreen(
                slug          = slug,
                onBack        = { nav.popBackStack() },
                onReadChapter = { chapter ->
                    nav.navigate(Routes.reader(slug, chapter))
                }
            )
        }

        // ── Reader ────────────────────────────────────────────────────────
        composable(
            route     = Routes.READER,
            arguments = listOf(
                navArgument("slug")    { type = NavType.StringType },
                navArgument("chapter") { type = NavType.IntType }
            )
        ) { backStack ->
            val slug    = backStack.arguments?.getString("slug")    ?: return@composable
            val chapter = backStack.arguments?.getInt("chapter")    ?: 1
            ReaderScreen(
                slug       = slug,
                chapterNum = chapter,
                onBack     = { nav.popBackStack() }
            )
        }

        // ── Library (Sprint 3) ────────────────────────────────────────────
        composable(Routes.LIBRARY) { /* coming Sprint 3 */ }
    }
}
