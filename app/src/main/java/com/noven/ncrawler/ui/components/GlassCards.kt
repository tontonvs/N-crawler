package com.noven.ncrawler.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.noven.ncrawler.data.db.NovelEntity
import com.noven.ncrawler.ui.theme.AccentBlue
import com.noven.ncrawler.ui.theme.AntonFamily
import com.noven.ncrawler.ui.theme.CinzelFamily
import com.noven.ncrawler.ui.theme.MontserratFamily
import com.noven.ncrawler.ui.theme.PacificoFamily
import com.noven.ncrawler.ui.theme.glassSurface

// ─────────────────────────────────────────────────────────────────────────────
// Shared glass cards — ONE definition used by BrowseScreen (genre rows + genre
// showcase), DiscoverScreen (genre tiles) and GenreScreen (novel grid).
//
// History carried over from the per-screen copies this file replaces:
//  - NovelCard / GenreNovelCard were two hand-copied versions of the same card
//    and had already drifted (GenreScreen's had no chapter label). 130dp-wide
//    cards in the homepage rows stay (that was a deliberate "narrower cards" ask).
//  - Later ask: keep cards simple — cover + name only, title in the reader's
//    font (Montserrat). The rating pill / chapter label / play button are gone.
//  - Then: no card behind the title (it sits on the page background), and the
//    cover is rounded on all four corners. NovelGlassCard keeps its name for its
//    callers, but the glass container now only lives on GenreGlassTile.
//  - GenreDecorativeCard: 72dp tall (was 90dp, per feedback), 6 alternating
//    decorative styles reusing the 3 bundled fonts (Pacifico / Cinzel / Anton)
//    so 8 genres in a row don't read as repetitive. Style order is unchanged.
//  - Dark mode: every glass surface goes through glassSurface() (system-theme
//    aware) — DiscoverScreen used to read GlassSurfaceLight directly, which put
//    near-white text on a near-white fill in dark mode.
//
// minSdk 26 has no reliable backdrop blur, so "glass" is the compatible kind:
// translucent fill + diagonal sheen + gradient rim light + soft tinted shadow.
// ─────────────────────────────────────────────────────────────────────────────

val GlassCardRadius = 16.dp

// Frosted-glass surface: shadow → clip → translucent fill → sheen → rim light.
// Built with composed{} (not a @Composable modifier factory) so it stays
// lint-clean while still reading the current system theme.
fun Modifier.glassCard(shape: Shape = RoundedCornerShape(GlassCardRadius)): Modifier = composed {
    val dark = isSystemInDarkTheme()
    val fill = glassSurface()
    val sheen = remember(dark) {
        if (dark) Brush.linearGradient(listOf(Color.White.copy(alpha = 0.10f), Color.Transparent))
        else      Brush.linearGradient(listOf(Color.White, AccentBlue.copy(alpha = 0.06f)))
    }
    val rim = remember(dark) {
        if (dark) Brush.linearGradient(listOf(Color.White.copy(alpha = 0.30f), Color.White.copy(alpha = 0.06f)))
        else      Brush.linearGradient(listOf(Color.White, AccentBlue.copy(alpha = 0.18f)))
    }
    this
        .shadow(
            elevation    = 6.dp,
            shape        = shape,
            clip         = false,
            ambientColor = Color(0x1A0D1117),
            spotColor    = Color(0x330D1117)
        )
        .clip(shape)
        .background(fill)
        .background(sheen)
        .border(1.dp, rim, shape)
}

// Cover ratio (width / height) shared by every novel card — homepage rows and
// Discover's genre grid — so a card is the same size on both screens.
const val NovelCardCoverAspect = 6f / 7f

// Grid geometry shared by GenreScreen's grid (GridCells.Adaptive(NovelGridMinCard),
// 16dp page padding, 20dp gaps) and the homepage rows, so a novel card is the
// same size on both screens in ANY orientation. In landscape the grid gets more
// columns instead of stretching two cards across the whole width — this mirrors
// how GridCells.Adaptive splits the width.
val NovelGridMinCard = 150.dp
private const val GRID_PAGE_PADDING_DP = 16
private const val GRID_GAP_DP = 20
private const val GRID_MIN_CARD_DP = 150

fun novelCardWidthFor(screenWidthDp: Int): Dp {
    val available = screenWidthDp - 2 * GRID_PAGE_PADDING_DP
    val columns   = maxOf(1, (available + GRID_GAP_DP) / (GRID_MIN_CARD_DP + GRID_GAP_DP))
    return ((available - GRID_GAP_DP * (columns - 1)) / columns).dp
}

// ── Novel card ────────────────────────────────────────────────────────────────
// Deliberately simple: a cover with all four corners rounded, and the novel's
// name printed straight on the page background — no card behind it, so there is
// no "chin" under the image (the title block used to sit in the glass card).
// The title uses the reader's font (Montserrat) and always reserves two lines of
// height so cards in the same row stay the same height whatever the title length.
// Pass Modifier.width(..) in a row, or Modifier.fillMaxWidth() in a grid cell —
// the cover keeps [coverAspect] (width / height) so it scales with the card
// instead of a fixed dp height.
@Composable
fun NovelGlassCard(
    novel: NovelEntity,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    coverAspect: Float = NovelCardCoverAspect
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue   = if (isPressed) 0.96f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessHigh),
        label         = "novelCardPress"
    )
    val coverShape = RoundedCornerShape(GlassCardRadius)

    Column(
        modifier = modifier
            .graphicsLayer(scaleX = scale, scaleY = scale)
            .clickable(
                interactionSource = interactionSource,
                indication        = null,
                role              = Role.Button,
                onClick           = onClick
            )
    ) {
        // Cover — rounded on ALL corners, with a soft shadow for depth.
        // surfaceVariant shows while loading / if the URL is blank.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(coverAspect)
                .shadow(
                    elevation    = 6.dp,
                    shape        = coverShape,
                    ambientColor = Color(0x1A0D1117),
                    spotColor    = Color(0x330D1117)
                )
                .clip(coverShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            AsyncImage(
                model              = novel.coverUrl,
                contentDescription = novel.title,
                contentScale       = ContentScale.Crop,
                modifier           = Modifier.matchParentSize()
            )
        }

        Text(
            novel.title,
            fontFamily = MontserratFamily,
            fontWeight = FontWeight.Bold,
            fontSize   = 12.sp,
            lineHeight = 16.sp,
            color      = MaterialTheme.colorScheme.onBackground,
            maxLines   = 2,
            overflow   = TextOverflow.Ellipsis,
            modifier   = Modifier
                .padding(start = 2.dp, end = 2.dp, top = 10.dp)
                .heightIn(min = 32.dp)
        )
    }
}

// ── Genre tile — decorative gradient typography on glass ─────────────────────
// styleIdx picks one of 6 looks (mod 6, so any index is safe): Pacifico 0/3,
// Cinzel 1/4, Anton 2/5. Each style has a light-mode pair and a brighter
// dark-mode pair — the deep purple/amber pairs vanish on the dark glass fill.
@Composable
fun GenreGlassTile(
    genre: String,
    styleIdx: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 72.dp
) {
    val dark  = isSystemInDarkTheme()
    val slot  = styleIdx.mod(6)
    val brush = remember(slot, dark) {
        val (from, to) = genreGradient(slot, dark)
        Brush.linearGradient(listOf(from, to))
    }

    val style = when (slot) {
        0, 3 -> TextStyle(
            fontFamily = PacificoFamily,
            fontSize   = 16.sp,
            brush      = brush
        )
        1, 4 -> TextStyle(
            fontFamily    = CinzelFamily,
            fontWeight    = FontWeight.Bold,
            fontSize      = 13.sp,
            letterSpacing = 1.5.sp,
            brush         = brush
        )
        else -> TextStyle(
            fontFamily    = AntonFamily,
            fontSize      = 16.sp,
            letterSpacing = 0.5.sp,
            brush         = brush
        )
    }

    Box(
        modifier = modifier
            .height(height)
            .glassCard()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text      = if (slot == 2 || slot == 5) genre.uppercase() else genre,
            style     = style,
            textAlign = TextAlign.Center,
            maxLines  = 2,
            overflow  = TextOverflow.Ellipsis
        )
    }
}

private fun genreGradient(slot: Int, dark: Boolean): Pair<Color, Color> = when (slot) {
    // 0 — Pacifico, warm pink-orange
    0 -> if (dark) Color(0xFFFF7A93) to Color(0xFFFF8A6B) else Color(0xFFFF416C) to Color(0xFFFF4B2B)
    // 1 — Cinzel, epic purple
    1 -> if (dark) Color(0xFFC084FC) to Color(0xFFA78BFA) else Color(0xFF8E2DE2) to Color(0xFF4A00E0)
    // 2 — Anton, fiery orange-red
    2 -> if (dark) Color(0xFFFB923C) to Color(0xFFF87171) else Color(0xFFF97316) to Color(0xFFDC2626)
    // 3 — Pacifico, cool teal-blue
    3 -> if (dark) Color(0xFF22D3EE) to Color(0xFF60A5FA) else Color(0xFF06B6D4) to Color(0xFF3B82F6)
    // 4 — Cinzel, gold-amber
    4 -> if (dark) Color(0xFFFBBF24) to Color(0xFFF59E0B) else Color(0xFFF59E0B) to Color(0xFFD97706)
    // 5 — Anton, green-emerald
    else -> if (dark) Color(0xFF34D399) to Color(0xFF10B981) else Color(0xFF10B981) to Color(0xFF059669)
}
