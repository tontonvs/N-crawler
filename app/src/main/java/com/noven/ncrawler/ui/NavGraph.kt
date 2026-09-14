package com.noven.ncrawler.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.noven.ncrawler.ui.screens.browse.BrowseScreen
import com.noven.ncrawler.ui.screens.detail.DetailScreen
import com.noven.ncrawler.ui.screens.downloads.DownloadsScreen
import com.noven.ncrawler.ui.screens.library.LibraryScreen
import com.noven.ncrawler.ui.screens.reader.ReaderScreen
import com.noven.ncrawler.ui.theme.AccentBlue
import com.noven.ncrawler.ui.theme.NavBlue

object Routes {
    const val BROWSE    = "browse"
    const val LIBRARY   = "library"
    const val DOWNLOADS = "downloads"
    const val DETAIL    = "detail/{slug}"
    const val READER    = "reader/{slug}/{chapter}"

    fun detail(slug: String)               = "detail/$slug"
    fun reader(slug: String, chapter: Int) = "reader/$slug/$chapter"
}

// Routes where bottom nav is hidden (immersive screens)
private val fullScreenRoutes = listOf("detail/", "reader/")

@Composable
fun NavGraph() {
    val nav          = rememberNavController()
    val currentEntry by nav.currentBackStackEntryAsState()
    val currentRoute  = currentEntry?.destination?.route ?: ""

    val isFullScreen = fullScreenRoutes.any { prefix ->
        currentRoute.startsWith(prefix)
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
                    onNovelClick = { slug -> nav.navigate(Routes.detail(slug)) }
                )
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
                currentRoute = currentRoute,
                onNavigate   = { route ->
                    if (route != currentRoute) {
                        nav.navigate(route) {
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
    onNavigate: (String) -> Unit
) {
    Row(
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // ── Pill capsule — Home / Search / Library ─────────────────────────
        Surface(
            shape         = RoundedCornerShape(50.dp),
            color         = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
            tonalElevation = 8.dp,
            modifier      = Modifier
                .shadow(
                    elevation        = 16.dp,
                    shape            = RoundedCornerShape(50.dp),
                    ambientColor     = Color.Black.copy(alpha = 0.15f),
                    spotColor        = Color.Black.copy(alpha = 0.25f)
                )
        ) {
            Row(
                modifier              = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment     = Alignment.CenterVertically
            ) {
                NavPillItem(
                    icon        = Icons.Outlined.Home,
                    iconActive  = Icons.Filled.Home,
                    label       = "Home",
                    isSelected  = currentRoute == Routes.BROWSE,
                    onClick     = { onNavigate(Routes.BROWSE) }
                )
                NavPillItem(
                    icon        = Icons.Outlined.Search,
                    iconActive  = Icons.Filled.Search,
                    label       = "Search",
                    isSelected  = false,
                    // Search activates in BrowseScreen itself — navigate to browse
                    // Search doesn't crash — it navigates to Browse where the bar is
                    onClick     = { onNavigate(Routes.BROWSE) }
                )
                NavPillItem(
                    icon        = Icons.Outlined.CollectionsBookmark,
                    iconActive  = Icons.Filled.CollectionsBookmark,
                    label       = "Library",
                    isSelected  = currentRoute == Routes.LIBRARY,
                    onClick     = { onNavigate(Routes.LIBRARY) }
                )
            }
        }

        // ── Blue FAB — Continue Reading ────────────────────────────────────
        // WhatsApp-style: outside the capsule, blue circle, white play icon
        Surface(
            shape  = CircleShape,
            color  = AccentBlue,
            modifier = Modifier
                .size(52.dp)
                .shadow(
                    elevation    = 12.dp,
                    shape        = CircleShape,
                    spotColor    = AccentBlue.copy(alpha = 0.4f),
                    ambientColor = AccentBlue.copy(alpha = 0.2f)
                )
                .clickable { onNavigate(Routes.LIBRARY) }
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Filled.PlayArrow,
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
