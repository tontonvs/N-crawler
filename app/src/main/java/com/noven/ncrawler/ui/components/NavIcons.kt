package com.noven.ncrawler.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

// Icon family for the floating nav, taken from floating_pill_navigation_bar.html:
// Lucide/Feather-style 24×24 icons — 2.2 stroke, round caps and joins — each in
// an outline version (inactive) and a solid version (active, drawn inside the
// sliding selector). The HTML only ships home / layers / folder / sparkles /
// navigation, so the icons the app needs beyond those (compass for Discover,
// search, settings gear) are drawn in exactly the same style.
//
// Built from the SVG path data with PathParser, so the shapes are the HTML's
// own. Colours are placeholders (black): always tint them via Icon(tint = …).
object NavIcons {

    // Home
    val HomeOutline: ImageVector by lazy { strokeIcon("NavHomeOutline", HOME_OUTLINE) }
    val HomeFilled: ImageVector  by lazy { fillIcon("NavHomeFilled", HOME_FILLED) }

    // Library → the HTML's folder
    val FolderOutline: ImageVector by lazy { strokeIcon("NavFolderOutline", FOLDER_OUTLINE) }
    val FolderFilled: ImageVector  by lazy { fillIcon("NavFolderFilled", FOLDER_FILLED) }

    // Discover → compass (replaces the HTML's paper-plane style navigation
    // arrow). Solid version = a filled disc with the needle cut out (even-odd).
    val DiscoverOutline: ImageVector by lazy { strokeIcon("NavDiscoverOutline", DISCOVER_OUTLINE) }
    val DiscoverFilled: ImageVector  by lazy { fillIcon("NavDiscoverFilled", DISCOVER_OUTLINE, evenOdd = true) }

    // Settings gear — solid version keeps the centre hole (even-odd fill)
    val SettingsOutline: ImageVector by lazy { strokeIcon("NavSettingsOutline", SETTINGS_OUTLINE) }
    val SettingsFilled: ImageVector  by lazy { fillIcon("NavSettingsFilled", SETTINGS_OUTLINE, evenOdd = true) }

    // Search (FAB) — one version; the FAB shows "active" with colours instead
    val Search: ImageVector by lazy { strokeIcon("NavSearch", SEARCH) }
}

private const val STROKE_WIDTH = 2.2f

private fun strokeIcon(name: String, pathData: String): ImageVector =
    ImageVector.Builder(
        name           = name,
        defaultWidth   = 24.dp,
        defaultHeight  = 24.dp,
        viewportWidth  = 24f,
        viewportHeight = 24f
    ).addPath(
        pathData        = PathParser().parsePathString(pathData).toNodes(),
        fill            = null,
        stroke          = SolidColor(Color.Black),
        strokeLineWidth = STROKE_WIDTH,
        strokeLineCap   = StrokeCap.Round,
        strokeLineJoin  = StrokeJoin.Round
    ).build()

private fun fillIcon(name: String, pathData: String, evenOdd: Boolean = false): ImageVector =
    ImageVector.Builder(
        name           = name,
        defaultWidth   = 24.dp,
        defaultHeight  = 24.dp,
        viewportWidth  = 24f,
        viewportHeight = 24f
    ).addPath(
        pathData     = PathParser().parsePathString(pathData).toNodes(),
        pathFillType = if (evenOdd) PathFillType.EvenOdd else PathFillType.NonZero,
        fill         = SolidColor(Color.Black)
    ).build()

// ── SVG path data ────────────────────────────────────────────────────────────
// Home / folder: copied verbatim from the HTML snippet.
private const val HOME_OUTLINE =
    "M3 10.25L12 3l9 7.25V20a1 1 0 0 1-1 1h-5a1 1 0 0 1-1-1v-5a1 1 0 0 0-1-1h-2a1 1 0 0 0-1 1v5a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1v-9.75z"
private const val HOME_FILLED =
    "M12 2.1 1 11h3v10a1 1 0 0 0 1 1h5v-6a1 1 0 0 1 1-1h2a1 1 0 0 1 1 1v6h5a1 1 0 0 0 1-1V11h3L12 2.1z"

private const val FOLDER_OUTLINE =
    "M20 20H4a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2h5l2 3h9a2 2 0 0 1 2 2v9a2 2 0 0 1-2 2z"
private const val FOLDER_FILLED =
    "M19.5 21h-15A2.5 2.5 0 0 1 2 18.5v-12A2.5 2.5 0 0 1 4.5 4h4.382a2 2 0 0 1 1.414.586l1.414 1.414A1 1 0 0 0 12.414 6.5H19.5A2.5 2.5 0 0 1 22 9v9.5a2.5 2.5 0 0 1-2.5 2.5z"

// Drawn in the same style (2.2 stroke, round joins) — not in the HTML.
// Compass = ring + needle.
private const val DISCOVER_OUTLINE =
    "M22 12a10 10 0 1 1-20 0 10 10 0 0 1 20 0z " +
    "M16.24 7.76l-1.804 5.411a2 2 0 0 1-1.265 1.265L7.76 16.24l1.804-5.411a2 2 0 0 1 1.265-1.265z"

private const val SEARCH =
    "M19 11a8 8 0 1 1-16 0 8 8 0 0 1 16 0z M21 21l-4.3-4.3"

private const val SETTINGS_OUTLINE =
    "M12.22 2h-.44a2 2 0 0 0-2 2v.18a2 2 0 0 1-1 1.73l-.43.25a2 2 0 0 1-2 0l-.15-.08a2 2 0 0 0-2.73.73l-.22.38a2 2 0 0 0 .73 2.73l.15.1a2 2 0 0 1 1 1.72v.51a2 2 0 0 1-1 1.74l-.15.09a2 2 0 0 0-.73 2.73l.22.38a2 2 0 0 0 2.73.73l.15-.08a2 2 0 0 1 2 0l.43.25a2 2 0 0 1 1 1.73V20a2 2 0 0 0 2 2h.44a2 2 0 0 0 2-2v-.18a2 2 0 0 1 1-1.73l.43-.25a2 2 0 0 1 2 0l.15.08a2 2 0 0 0 2.73-.73l.22-.39a2 2 0 0 0-.73-2.73l-.15-.08a2 2 0 0 1-1-1.74v-.5a2 2 0 0 1 1-1.74l.15-.09a2 2 0 0 0 .73-2.73l-.22-.38a2 2 0 0 0-2.73-.73l-.15.08a2 2 0 0 1-2 0l-.43-.25a2 2 0 0 1-1-1.73V4a2 2 0 0 0-2-2z " +
    "M15 12a3 3 0 1 1-6 0 3 3 0 0 1 6 0z"
