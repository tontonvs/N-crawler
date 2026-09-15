package com.noven.ncrawler.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.noven.ncrawler.ui.screens.browse.BrowseScreen
import com.noven.ncrawler.ui.screens.detail.NovelDetailScreen

// ─────────────────────────────────────────────────────────────────────────────
// Route constants — single source of truth, avoids magic strings everywhere
// ─────────────────────────────────────────────────────────────────────────────

object Routes {
    const val BROWSE = "browse"
    const val DETAIL = "detail/{novelId}"

    /** Build a typed detail route from a novel ID. */
    fun detail(novelId: String) = "detail/${novelId.encodeForRoute()}"

    private fun String.encodeForRoute() =
        java.net.URLEncoder.encode(this, "UTF-8")
}

// ─────────────────────────────────────────────────────────────────────────────
// Root nav graph
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun NCrawlerNavGraph() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Routes.BROWSE,
    ) {

        // ── Browse / Search screen (Sprint 1) ────────────────────────────────
        composable(route = Routes.BROWSE) {
            BrowseScreen(
                onNovelClick = { novelId ->
                    navController.navigate(Routes.detail(novelId))
                },
            )
        }

        // ── Novel Detail screen (Sprint 2) ───────────────────────────────────
        // CHANGE: added this composable block — routes to NovelDetailScreen
        // with a URL-encoded novelId argument.
        //
        // Advantage : type-safe String arg avoids cast errors at runtime.
        // Disadvantage : URL-encoding adds ~1 ms overhead — negligible.
        composable(
            route = Routes.DETAIL,
            arguments = listOf(
                navArgument("novelId") {
                    type = NavType.StringType
                    nullable = false
                }
            ),
        ) { backStackEntry ->
            val novelId = backStackEntry.arguments
                ?.getString("novelId")
                ?.let { java.net.URLDecoder.decode(it, "UTF-8") }
                ?: ""

            NovelDetailScreen(
                novelId = novelId,
                onBack = { navController.popBackStack() },
                onChapterClick = { chapterUrl ->
                    // Sprint 3: navigate to reader screen
                    // navController.navigate(Routes.reader(chapterUrl))
                },
            )
        }
    }
}
