package com.noven.ncrawler.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

// Solar icon set (480 Design) — "Alt Arrow Right", Line Duotone family.
// Icons by 480 Design, licensed CC BY 4.0 (https://creativecommons.org/licenses/by/4.0/);
// credit: "Solar Icon Set" — https://github.com/480-Design/Solar-Icon-Set
//
// Same geometry as the set's own chevron (M9 5 L15 12 L9 19, 24×24 viewport, round
// caps + joins) but drawn THICK: the set ships it at 1.5 stroke, which reads too
// thin next to the app's 2.2-stroke nav icons, so it is 2.8 here. Colour is a
// placeholder (black): always tint via Icon(tint = ...), like SolarIcons / NavIcons.
//
// Lives in its own file (not SolarIcons.kt) so that 580-line file stays untouched.
object SolarArrows {

    /** Alt Arrow Right — thick, rounded ">" used for every "see more" affordance. */
    val ChevronRight: ImageVector by lazy {
        ImageVector.Builder(
            name           = "SolarChevronRightThick",
            defaultWidth   = 24.dp,
            defaultHeight  = 24.dp,
            viewportWidth  = 24f,
            viewportHeight = 24f
        ).addPath(
            pathData        = PathParser().parsePathString("M9 5L15 12L9 19").toNodes(),
            fill            = null,
            stroke          = SolidColor(Color.Black),
            strokeLineWidth = 2.8f,
            strokeLineCap   = StrokeCap.Round,
            strokeLineJoin  = StrokeJoin.Round
        ).build()
    }
}
