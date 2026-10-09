package com.noven.ncrawler.ui.components

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

// ═════════════════════════════════════════════════════════════════════════════
// Shared by the Browse and Settings top bars (bar ⇄ pill morph).
// REFACTOR: these pieces were word-for-word copies in BrowseScreen.kt and
// SourceSettingsScreen.kt; they now live here once, so both screens always
// morph with the exact same geometry. Nothing about how either looks changed.
// Screen-specific extras (Browse's download button, Settings' pill padding)
// stay in each screen as a small subclass of MorphGeometryBase.
// ═════════════════════════════════════════════════════════════════════════════

internal const val MORPH_COLLAPSE_AT  = 0.40f  // scrolled this far (fraction of the bar's height) → bar morphs into the capsule
internal const val MORPH_EXPAND_BELOW = 0.20f  // …and only grows back once it is clearly near the top again (no flicker at the edge)

internal val BAR_CONTENT_HEIGHT = 66.dp      // below the status bar (open bar)
internal val BAR_FLARE          = 14.dp      // how far the inverted corners reach below the flat edge
internal val CAPSULE_H          = 40.dp      // the pill is as tall as the button
internal val CAPSULE_TOP        = 13.dp      // pill's top edge, below the status bar

internal fun mix(a: Float, b: Float, t: Float): Float = a + (b - a) * t

// 0 until [from], 1 from [to] on, linear in between — staggers parts of one morph.
internal fun window(p: Float, from: Float, to: Float): Float = ((p - from) / (to - from)).coerceIn(0f, 1f)

// How far the top bar has collapsed: 0 = fully open (list at rest), 1 = fully gone.
// Only meaningful while the list is near its top; once the first item has scrolled
// out it is simply 1. With no list on screen (skeleton / error) the state sits at 0,
// so the bar stays open. Call it from snapshotFlow (or a draw-phase lambda) only.
internal fun collapseOf(state: LazyListState, barHeightPx: Float): Float {
    if (barHeightPx <= 0f) return 0f
    if (state.firstVisibleItemIndex > 0) return 1f
    return (state.firstVisibleItemScrollOffset / barHeightPx).coerceIn(0f, 1f)
}

// All the geometry of the morph, in px, computed once per density / status-bar height.
// Everything that depends on the SCREEN width or the real "Browse" text width takes it as a
// parameter, so nothing here is a guess about how wide the text turns out.
internal open class MorphGeometryBase(private val density: Density, statusDp: Dp) {
    protected fun px(d: Dp): Float = with(density) { d.toPx() }

    val statusPx = px(statusDp)
    val barH     = px(statusDp + BAR_CONTENT_HEIGHT + BAR_FLARE)
    val flare    = px(BAR_FLARE)
    val capH     = px(CAPSULE_H)
    val capTop   = statusPx + px(CAPSULE_TOP)

    /** x of the shape's left edge: 0 = screen edge (bar), centred (pill). */
    fun bodyLeft(w: Float, p: Float, capW: Float): Float = mix(0f, (w - capW) / 2f, p)

    /**
     * The morphing outline. Rect edges: x [bodyLeft … w - bodyLeft], y [0 … barH] (bar)
     * → [capTop … capTop + capH] (pill). Top corners round off 0 → full; bottom corners
     * go from an inverted fillet of radius [flare] (negative) to a full convex round.
     */
    fun path(w: Float, p: Float, capW: Float): Path {
        val x0 = bodyLeft(w, p, capW)
        val x1 = w - x0
        val y0 = mix(0f, capTop, p)
        val y1 = y0 + mix(barH, capH, p)
        val half = minOf(y1 - y0, x1 - x0) / 2f
        val rt = mix(0f, half, p)
        val sb = mix(-flare, half, p)          // signed bottom radius: < 0 = inverted
        val path = Path()
        path.moveTo(x0 + rt, y0)
        path.lineTo(x1 - rt, y0)
        if (rt > 0f) path.arcTo(Rect(x1 - 2f * rt, y0, x1, y0 + 2f * rt), -90f, 90f, false)
        if (sb >= 0f) {
            path.lineTo(x1, y1 - sb)
            if (sb > 0f) path.arcTo(Rect(x1 - 2f * sb, y1 - 2f * sb, x1, y1), 0f, 90f, false)
            path.lineTo(x0 + sb, y1)
            if (sb > 0f) path.arcTo(Rect(x0, y1 - 2f * sb, x0 + 2f * sb, y1), 90f, 90f, false)
        } else {
            val r = -sb
            path.lineTo(x1, y1)                                                        // flare tip
            path.arcTo(Rect(x1 - 2f * r, y1 - r, x1, y1 + r), 0f, -90f, false)         // right fillet
            path.lineTo(x0 + r, y1 - r)                                                // flat bottom edge
            path.arcTo(Rect(x0, y1 - r, x0 + 2f * r, y1 + r), -90f, -90f, false)       // left fillet
        }
        path.lineTo(x0, y0 + rt)
        if (rt > 0f) path.arcTo(Rect(x0, y0, x0 + 2f * rt, y0 + 2f * rt), 180f, 90f, false)
        path.close()
        return path
    }
}

// The outline at morph value [p]. Used (a) at p = 0 as the RESTING bar's shape handed to the
// glass blur, and (b) per frame for the pill's shadow. The blur is NOT given an animated
// shape: it is only on screen while the element is the full bar (see GLASS_OFF_AT).
// (Settings uses it the same way — handed to graphicsLayer so the pill gets its shadow.)
internal class MorphBarShape(
    private val geo: MorphGeometryBase,
    private val p: Float,
    private val capW: Float
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        Outline.Generic(geo.path(size.width, p, capW))
}
