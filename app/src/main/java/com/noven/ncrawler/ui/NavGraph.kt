package com.noven.ncrawler.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.noven.ncrawler.ui.screens.browse.BrowseScreen
import com.noven.ncrawler.ui.screens.detail.NovelDetailScreen

object Routes {
    const val BROWSE = "browse"
    const val DETAIL = "detail/{novelId}"

    fun detail(novelId: String): String =
        "detail/${java.net.URLEncoder.encode(novelId, "UTF-8")}"
}

@Composable
fun NCrawlerNavGraph() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = Routes.BROWSE) {

        // Browse screen — Sprint 1 signature (no onNovelClick yet)
        composable(route = Routes.BROWSE) {
            BrowseScreen()
        }

        // Detail screen — Sprint 2
        composable(
            route = Routes.DETAIL,
            arguments = listOf(navArgument("novelId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val novelId = backStackEntry.arguments
                ?.getString("novelId")
                ?.let { java.net.URLDecoder.decode(it, "UTF-8") }
                ?: ""

            NovelDetailScreen(
                novelId      = novelId,
                onBack       = { navController.popBackStack() },
                onChapterClick = { /* Sprint 3: navigate to reader */ },
            )
        }
    }
}
