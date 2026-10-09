package com.noven.ncrawler.ui

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
import androidx.compose.material3.Text
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.noven.ncrawler.ui.components.BookmarkGold
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
import androidx.navigation.compose.*
import com.noven.ncrawler.ui.components.glassBlur
import com.noven.ncrawler.ui.theme.GlassBase
import com.noven.ncrawler.ui.theme.GlassMode
import dev.chrisbanes.haze.HazeState
import com.noven.ncrawler.ui.theme.GlassSpec
import com.noven.ncrawler.ui.components.SolarIcons

// ── Floating nav: pill (Home · Library · Discover · Settings) + search FAB ───
// CHANGE: rebuilt after floating_pill_navigation_bar.html — a solid pill with a
// circular selector that slides (with a slight overshoot) behind the active
// icon; the active icon crossfades from outline to solid. The old glass
// capsule, the Search tab inside it and the blue "continue reading" play FAB
// are gone: search is the FAB now, in the reader's circle-button style, and the
// profile avatar became the Settings gear.
@Composable
internal fun FloatingNavBar(
    hazeState: HazeState,
    selectedIndex: Int,
    searchOpen: Boolean,
    badges: Map<String, Int>,
    onTab: (String) -> Unit,
    onSearchClick: () -> Unit
) {
    Row(
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        PillNav(hazeState = hazeState, selectedIndex = selectedIndex, badges = badges, onTab = onTab)
        SearchFab(hazeState = hazeState, active = searchOpen, onClick = onSearchClick)
    }
}

private data class NavTab(
    val route: String,
    val label: String,
    val outline: ImageVector,
    val filled: ImageVector
)

private val navTabs = listOf(
    // Solar duotone pairs: Line Duotone = inactive, Bold Duotone = active (same
    // geometry, so the crossfade never jumps).
    NavTab(Routes.BROWSE,   "Home",     SolarIcons.Home,    SolarIcons.HomeBold),
    NavTab(Routes.LIBRARY,  "Library",  SolarIcons.Library, SolarIcons.LibraryBold),
    NavTab(Routes.DISCOVER, "Discover", SolarIcons.Compass, SolarIcons.CompassBold),
    NavTab(Routes.SETTINGS, "Settings", SolarIcons.Settings, SolarIcons.SettingsBold)
)

// CHANGE: a bit smaller than the HTML's geometry (48dp buttons, 8dp apart,
// 12/10dp pill padding) — 44dp buttons, 4dp apart, and only 6/5dp between the
// pill's edge and the buttons inside it. Everything (selector slide distance,
// FAB size) derives from these, so resizing again is a few numbers.
private val NavItemSize = 44.dp
private val NavItemGap  = 4.dp
private val NavIconSize = 24.dp     // Solar is drawn on a 24dp grid
// Classic: the pill and the search FAB are 87% opaque. Glass mode: tv3's 51%.
private const val NavOpacityClassic = 0.87f
private val NavPadH     = 6.dp
private val NavPadV     = 5.dp

// Glass mode: one look for light AND dark — the tv3 nav is dark glass with
// white icons and a white selector. Classic mode keeps the per-theme inversion.
private class NavPalette(
    val pill: Color,
    val selector: Color,
    val inactiveIcon: Color,
    val activeIcon: Color,
    val edge: Color
)

private val NavInk = Color(0xFF1E232D)

private val NavGlassPalette = NavPalette(
    pill         = GlassBase,
    selector     = Color.White,
    inactiveIcon = Color.White.copy(alpha = 0.60f),
    activeIcon   = Color.Black,
    edge         = Color.Transparent                    // border 0%
)

@Composable
private fun navPalette(): NavPalette = when {
    GlassMode.enabled          -> NavGlassPalette
    isSystemInDarkTheme()      -> NavPalette(          // classic: inverted on dark
        pill         = Color(0xFFE6EAF2),
        selector     = NavInk,
        inactiveIcon = NavInk,
        activeIcon   = Color.White,
        edge         = Color.Black.copy(alpha = 0.08f)
    )
    else                       -> NavPalette(          // classic: dark ink on light
        pill         = NavInk,
        selector     = Color.White,
        inactiveIcon = Color.White,
        activeIcon   = NavInk,
        edge         = Color.White.copy(alpha = 0.12f)
    )
}

@Composable
private fun PillNav(hazeState: HazeState, selectedIndex: Int, badges: Map<String, Int>, onTab: (String) -> Unit) {
    val palette = navPalette()
    val shape   = RoundedCornerShape(50)
    val glass   = GlassMode.enabled

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
                ambientColor = if (glass) Color(0x33000000) else Color(0x330F172A),
                spotColor    = if (glass) Color(0x99000000) else Color(0x660F172A)  // glass = tv3: 0 8 32 rgba(0,0,0,.6)
            )
            .clip(shape)
            .then(
                if (glass) {
                    // Real 30dp blur behind the pill, tinted with the 51% glass fill
                    Modifier.glassBlur(hazeState, shape, palette.pill.copy(alpha = GlassSpec.NAV_FILL))
                } else {
                    Modifier
                        .background(palette.pill.copy(alpha = NavOpacityClassic))
                        .border(1.dp, palette.edge, shape)
                }
            )
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
                    badge    = badges[tab.route] ?: 0,
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
    badge: Int,
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
        // Gold count badge (e.g. new chapters in Library): a plain 16dp circle, no
        // border. It overlaps the icon's top-right corner but stays inside this
        // 44dp button (so inside the pill) — never peeks outside the nav.
        if (badge > 0) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(x = 9.dp, y = (-9).dp)
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(BookmarkGold),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text       = if (badge > 9) "9+" else badge.toString(),
                    color      = Color(0xFF1B1405),
                    fontSize   = 9.sp,
                    lineHeight = 10.sp,
                    maxLines   = 1,
                    softWrap   = false,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }
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
private fun SearchFab(hazeState: HazeState, active: Boolean, onClick: () -> Unit) {
    val glass  = GlassMode.enabled
    val scheme = MaterialTheme.colorScheme
    val fg     = scheme.onSurface
    val idleBg = fg.copy(alpha = 0.13f).compositeOver(scheme.surface)

    // Glass: same as the pill (dark 51% fill, white icon), inverting to solid
    // white + black icon while search is open. Classic: the scheme-coloured FAB.
    val bg by animateColorAsState(
        targetValue   = when {
            glass  -> if (active) Color.White else GlassBase.copy(alpha = GlassSpec.NAV_FILL)
            else   -> if (active) fg else idleBg
        },
        animationSpec = tween(200),
        label         = "searchFabBg"
    )
    val iconTint by animateColorAsState(
        targetValue   = when {
            glass  -> if (active) Color.Black else Color.White
            else   -> if (active) scheme.surface else fg
        },
        animationSpec = tween(200),
        label         = "searchFabIcon"
    )

    // CHANGE: no bounce (was MediumBouncy) — this is tapped constantly, so it
    // just presses in and settles.
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue   = if (isPressed) 0.90f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium),
        label         = "searchFabPress"
    )

    Box(
        modifier = Modifier
            .size(NavItemSize)
            .graphicsLayer(scaleX = scale, scaleY = scale)
            .shadow(
                elevation    = 10.dp,
                shape        = CircleShape,
                ambientColor = if (glass) Color(0x26000000) else Color(0x260F172A),
                spotColor    = if (glass) Color(0x80000000) else Color(0x480F172A)
            )
            .clip(CircleShape)
            .then(
                when {
                    !glass -> Modifier.background(bg.copy(alpha = NavOpacityClassic))
                    active -> Modifier.background(bg)           // solid white while search is open
                    else   -> Modifier.glassBlur(hazeState, CircleShape, bg)
                }
            )
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
            imageVector        = if (active) SolarIcons.SearchBold else SolarIcons.Search,
            contentDescription = "Search",
            tint               = iconTint,
            modifier           = Modifier.size(NavIconSize)
        )
    }
}
