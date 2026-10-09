package com.noven.ncrawler.ui.screens.browse

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import com.noven.ncrawler.ui.components.AnimatedArrowDownIcon
import com.noven.ncrawler.ui.components.StaticArrowDownIcon
import com.noven.ncrawler.ui.components.Motion
import com.noven.ncrawler.ui.components.glassBlur
import com.noven.ncrawler.ui.components.MorphGeometryBase
import com.noven.ncrawler.ui.components.MorphBarShape
import com.noven.ncrawler.ui.components.window
import com.noven.ncrawler.ui.components.BAR_CONTENT_HEIGHT
import com.noven.ncrawler.ui.theme.*
import dev.chrisbanes.haze.HazeState
import kotlin.math.roundToInt

// ── Top chrome: glass bar ⇄ "Browse" capsule ─────────────────────────────────────
// ONE glass element. At rest it is the full-width bar (66dp under the status bar, with
// inverted bottom corners). Scroll a little and it shrinks in from both sides to the
// centre until it is the "Browse" PILL (the logo text crossfades to "Browse"). The
// download button does NOT go inside it: it slides out of the bar to sit as its own disc
// right beside the pill, with its own shadow and its progress ring. Scroll back to the top
// and the pill grows back into the bar while the button slides home.
//
// MOTION (Jakub primary, Emil secondary — the app's Motion kit):
//  • Nothing is measured while it moves. The element keeps ONE fixed layout rectangle
//    (screen width × bar height). What animates is its clip OUTLINE (drawn from the
//    morph value in the draw phase) and graphicsLayer transforms/alpha on the contents —
//    so no layout runs mid-animation, and the Haze blur region never moves (a size
//    animation would have forced the blur to re-read its bounds every frame).
//  • Shape: rectangle edges close in from the sides while the bottom corners go from
//    INVERTED fillets (bar) to fully round (capsule) — one outline, interpolated.
//  • Content is staggered on the morph value: "nCrawler" leaves in the first 38%,
//    "Browse" arrives in the last 38%, so the two words never overlap mid-way.
//  • 340ms in / 300ms out, Motion.EaseOut, interruptible; reduced motion = snap.
private val BTN_SIZE           = 40.dp      // downloads button
private val BTN_MARGIN_END     = 14.dp      // open bar: button ↔ screen's right edge
private val TITLE_INSET        = 36.dp      // open bar: logo ↔ screen's left edge
private val CAPSULE_PAD_START  = 20.dp      // pill: left edge ↔ "Browse"
private val CAPSULE_PAD_END    = 20.dp      // pill: "Browse" ↔ right edge
private val CAPSULE_GAP        = 8.dp       // pill ↔ the separate download button beside it
private val RING_STROKE        = 2.7.dp     // download-progress ring around the button
// The open bar shows the arrow-down icon held STILL (the ring shows progress). Flip to true
// to make it loop while a download runs; beside the pill the arrow always loops while downloading.
private const val BAR_ICON_ANIMATES_WHEN_DOWNLOADING = false
private const val TOP_GLASS_ALPHA = 0.58f   // glass tint over the blur (lower = more of the cover shows)
// The blur exists ONLY while the element is (almost) the full bar: from this morph value on it is
// removed outright, so nothing blurred can remain behind the pill or the button.
private const val GLASS_OFF_AT = 0.10f

// Browse's extras on top of the shared MorphGeometryBase (TopBarMorph.kt): the download
// button's spot, the title inset and the pill's padding. Everything is in px, computed once
// per density / status-bar height; anything that depends on the SCREEN width or the real
// "Browse" text width takes it as a parameter.
private class MorphGeometry(density: Density, statusDp: Dp) : MorphGeometryBase(density, statusDp) {
    val btn         = px(BTN_SIZE)
    val marginEnd   = px(BTN_MARGIN_END)
    val titleInset  = px(TITLE_INSET)
    val padStart    = px(CAPSULE_PAD_START)
    val padEnd      = px(CAPSULE_PAD_END)
    val gap         = px(CAPSULE_GAP)
    val barCenterY  = statusPx + px(BAR_CONTENT_HEIGHT) / 2f
    val capCenterY  = capTop + capH / 2f

    /** Width of the pill = equal padding either side of the REAL laid-out text width. */
    fun capW(textPx: Float): Float = padStart + textPx + padEnd

    /**
     * How far the button travels (px) from its open-bar spot (right edge, [marginEnd]) to
     * its resting spot right beside the centred pill: [gap] past the pill's right edge.
     */
    fun buttonDx(w: Float, capW: Float): Float = (w + capW) / 2f + gap - (w - marginEnd - btn)
}

internal class BarGlass(
    val tint: Color,        // fill of the glass over the blur
    val ink: Color,         // text colour
    val paper: Color,       // solid pill colour (the glass turns near-solid as it becomes the pill)
    val discFill: Color,    // download button disc: uniform and opaque
    val discInk: Color      // everything drawn on that disc: icon + progress ring
)

// Light mode: white glass, bright disc with near-black marks.
// Dark mode:  dark glass, dark disc with light marks (a bright disc glares on dark glass).
@Composable
internal fun rememberBarGlass(): BarGlass {
    val dark = isSystemInDarkTheme()
    val ink  = MaterialTheme.colorScheme.onSurface
    return remember(dark, ink) {
        BarGlass(
            tint     = (if (dark) GlassBase else Color.White).copy(alpha = TOP_GLASS_ALPHA),
            ink      = ink,
            paper    = if (dark) GlassBase else Color.White,
            discFill = if (dark) Color(0xFF2A303D) else Color.White,
            discInk  = if (dark) Color(0xFFF2F5FA) else Color(0xFF0D1117)
        )
    }
}

@Composable
internal fun TopChrome(
    onDownloadsClick: (() -> Unit)?,
    downloading: Boolean,
    collapsed: Boolean,
    progress: Float,
    glass: BarGlass,
    hazeState: HazeState,
    morph: () -> Float,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val statusBarDp = with(density) { WindowInsets.statusBars.getTop(density).toDp() }

    val titleStyle = MaterialTheme.typography.titleLarge.copy(
        fontWeight    = FontWeight.ExtraBold,
        fontSize      = 28.sp,
        letterSpacing = (-0.5).sp
    )
    val browseStyle = MaterialTheme.typography.titleLarge.copy(
        fontWeight    = FontWeight.ExtraBold,
        fontSize      = 20.sp,
        lineHeight    = 24.sp,
        letterSpacing = (-0.3).sp
    )
    val geo = remember(density, statusBarDp) { MorphGeometry(density, statusBarDp) }

    // Measured, not guessed: screen width and the REAL width of the "Browse" text, both
    // reported by layout. The pill is sized from the latter, so the text is centred in it.
    var rootW by remember { mutableStateOf(0f) }
    var browseW by remember { mutableStateOf(0f) }
    val capW = { geo.capW(browseW) }

    // The resting bar's shape, for the glass blur only.
    val barShape = remember(geo) { MorphBarShape(geo, 0f, 0f) }
    // Glass exists only while it is (almost) the full bar. Reading the morph through
    // derivedStateOf means this recomposes only when the boolean flips.
    val glassOn by remember(morph) { derivedStateOf { morph() < GLASS_OFF_AT } }

    Layout(
        modifier = modifier
            .fillMaxWidth()
            .onSizeChanged { rootW = it.width.toFloat() },
        content = {
            // 0 — the glass: the RESTING bar only. Given the bar's real (inverted-corner)
            // shape directly; gone for the rest of the morph, so no blur is ever left
            // behind the pill or the button.
            Box(
                if (glassOn) Modifier.clip(barShape).glassBlur(hazeState, barShape, glass.tint)
                else Modifier
            )

            // 1 — the visible body: an explicit path, filled and outlined by hand (no
            // reliance on clipping a blur). Transparent at rest (the glass shows), solid
            // from the first moments of the morph, shrinking to the pill; a soft shadow
            // fades in once the outline is convex (pill).
            Box(
                Modifier
                    .graphicsLayer {
                        val p = morph()
                        shape = MorphBarShape(geo, p, capW())
                        clip = false
                        shadowElevation = 6.dp.toPx() * window(p, 0.55f, 1f)
                    }
                    .drawWithContent {
                        drawContent()
                        val p = morph()
                        val path = geo.path(size.width, p, capW())
                        val solid = window(p, 0f, GLASS_OFF_AT)
                        if (solid > 0f) drawPath(path, glass.paper.copy(alpha = 0.94f * solid))
                        // hairline edge so the bar / pill reads against a light page
                        drawPath(path, glass.ink.copy(alpha = 0.10f), style = Stroke(width = 1.5.dp.toPx()))
                    }
            )

            // 2 — "nCrawler": leaves first, riding the shape's left edge inward while it
            // shrinks a little (origin = its left-centre).
            Text(
                "nCrawler",
                modifier = Modifier
                    .then(if (collapsed) Modifier.clearAndSetSemantics { } else Modifier)
                    .graphicsLayer {
                        val p = morph()
                        val gone = window(p, 0f, 0.38f)
                        alpha        = 1f - gone
                        translationX = geo.bodyLeft(rootW, p, capW())
                        scaleX       = 1f - 0.15f * gone
                        scaleY       = 1f - 0.15f * gone
                        transformOrigin = TransformOrigin(0f, 0.5f)
                    },
                style    = titleStyle,
                color    = glass.ink,
                maxLines = 1,
                softWrap = false
            )

            // 3 — "Browse": waits, then arrives in the pill (fade + settle 0.92 → 1)
            Text(
                "Browse",
                modifier = Modifier
                    .onSizeChanged { browseW = it.width.toFloat() }
                    .then(if (collapsed) Modifier else Modifier.clearAndSetSemantics { })
                    .graphicsLayer {
                        val a = window(morph(), 0.62f, 1f)
                        alpha  = a
                        scaleX = 0.92f + 0.08f * a
                        scaleY = 0.92f + 0.08f * a
                    },
                style    = browseStyle,
                color    = glass.ink,
                maxLines = 1,
                softWrap = false
            )

            // 4 — the download button, placed at its open-bar spot and carried out to its
            // own spot beside the pill by translation.
            DownloadButton(
                onClick     = onDownloadsClick,
                downloading = downloading,
                collapsed   = collapsed,
                progress    = progress,
                glass       = glass,
                modifier    = Modifier.graphicsLayer {
                    val p = morph()
                    translationX = geo.buttonDx(rootW, capW()) * p
                    translationY = (geo.capCenterY - geo.barCenterY) * p
                    // its own disc beside the pill, so it gets its own soft shadow
                    shape = CircleShape
                    clip = false
                    shadowElevation = 6.dp.toPx() * window(p, 0.55f, 1f)
                }
            )
        }
    ) { measurables, constraints ->
        val w = constraints.maxWidth
        val h = geo.barH.roundToInt()
        val loose = constraints.copy(minWidth = 0, minHeight = 0, maxHeight = Constraints.Infinity)
        val glassBox = measurables[0].measure(Constraints.fixed(w, h))
        val bodyBox  = measurables[1].measure(Constraints.fixed(w, h))
        val title    = measurables[2].measure(loose)
        val browse   = measurables[3].measure(loose)
        val btn      = measurables[4].measure(loose)
        layout(w, h) {
            glassBox.place(0, 0)
            bodyBox.place(0, 0)
            title.place(geo.titleInset.roundToInt(), (geo.barCenterY - title.height / 2f).roundToInt())
            // the pill is centred on the screen, so its text is centred on the screen
            browse.place(((w - browse.width) / 2f).roundToInt(), (geo.capCenterY - browse.height / 2f).roundToInt())
            btn.place((w - geo.marginEnd - btn.width).roundToInt(), (geo.barCenterY - btn.height / 2f).roundToInt())
        }
    }
}

// The downloads button: a uniform disc with a 2.7dp progress ring on its circumference
// (no track — just the arc) and the arrow-down icon. Colours follow the theme (see
// rememberBarGlass). The arrow is held still unless a download is running AND the bar
// has become the pill (or BAR_ICON_ANIMATES_WHEN_DOWNLOADING is on); then it cross-fades to the
// looping arrow. The 4s box/wave/checkmark animation lives on the Detail screen.
@Composable
private fun DownloadButton(
    onClick: (() -> Unit)?,
    downloading: Boolean,
    collapsed: Boolean,
    progress: Float,
    glass: BarGlass,
    modifier: Modifier = Modifier
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()

    // Ring: fades in/out with the download. While it fades out it holds the LAST progress
    // (the live fraction drops to 0 the instant the download stops being "active"), and
    // the sweep eases toward each new value instead of jumping per chapter.
    val ringAlpha by animateFloatAsState(
        targetValue   = if (downloading) 1f else 0f,
        animationSpec = tween(Motion.BASE_MS),
        label         = "ringAlpha"
    )
    var shownProgress by remember { mutableStateOf(0f) }
    LaunchedEffect(downloading, progress) { if (downloading) shownProgress = progress }
    val sweep by animateFloatAsState(
        targetValue   = shownProgress,
        animationSpec = tween(400, easing = Motion.EaseOut),
        label         = "ringSweep"
    )

    Box(
        modifier = modifier
            .size(BTN_SIZE)
            .clip(CircleShape)
            .background(glass.discFill)
            .border(0.75.dp, glass.discInk.copy(alpha = 0.12f), CircleShape)
            .clickable(
                enabled           = onClick != null,
                interactionSource = interaction,
                indication        = null,
                role              = Role.Button,
                onClickLabel      = "Downloads"
            ) {
                onClick?.invoke()
            },
        contentAlignment = Alignment.Center
    ) {
        // progress ring — on the circumference (inset by half the stroke so it sits fully
        // inside the disc's edge). Both values are read in the draw phase.
        Canvas(
            Modifier
                .matchParentSize()
                .graphicsLayer { alpha = ringAlpha }
        ) {
            val stroke = RING_STROKE.toPx()
            val inset  = stroke / 2f
            val arc    = Size(size.width - stroke, size.height - stroke)
            drawArc(
                color      = glass.discInk,
                startAngle = -90f,
                sweepAngle = 360f * sweep.coerceIn(0.02f, 1f),
                useCenter  = false,
                topLeft    = Offset(inset, inset),
                size       = arc,
                style      = Stroke(width = stroke, cap = StrokeCap.Round)
            )
        }

        Box(
            modifier = Modifier.graphicsLayer { alpha = if (pressed) 0.7f else 1f },
            contentAlignment = Alignment.Center
        ) {
            val animated = downloading && (collapsed || BAR_ICON_ANIMATES_WHEN_DOWNLOADING)
            Crossfade(
                targetState   = animated,
                animationSpec = tween(Motion.BASE_MS),
                label         = "downloadIconSwap"
            ) { running ->
                if (running) AnimatedArrowDownIcon(ink = glass.discInk)
                else StaticArrowDownIcon(ink = glass.discInk)
            }
        }
    }
}
