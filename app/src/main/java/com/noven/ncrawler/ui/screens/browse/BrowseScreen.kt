package com.noven.ncrawler.ui.screens.browse

import android.os.Build
import android.provider.Settings
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.noven.ncrawler.data.db.NovelEntity
import com.noven.ncrawler.ui.components.AppPullToRefresh
import com.noven.ncrawler.ui.components.AnimatedArrowDownIcon
import com.noven.ncrawler.ui.components.AnimatedDownloadIcon
import com.noven.ncrawler.ui.components.CoverImage
import com.noven.ncrawler.ui.components.StaticArrowDownIcon
import com.noven.ncrawler.ui.components.GenreGlassTile
import com.noven.ncrawler.ui.components.Motion
import com.noven.ncrawler.ui.components.ShimmerScope
import com.noven.ncrawler.ui.components.errorShake
import com.noven.ncrawler.ui.components.pressable
import com.noven.ncrawler.ui.components.skeleton
import com.noven.ncrawler.ui.components.staggerIn
import com.noven.ncrawler.ui.components.NovelGlassCard
import com.noven.ncrawler.ui.components.SolarArrows
import com.noven.ncrawler.ui.components.glassBlur
import com.noven.ncrawler.ui.components.rememberReducedMotion
import com.noven.ncrawler.ui.components.novelCardWidthFor
import com.noven.ncrawler.ui.theme.*
import com.noven.ncrawler.viewmodel.BrowseUiState
import com.noven.ncrawler.viewmodel.BrowseViewModel
import com.noven.ncrawler.viewmodel.ContinueReadingInfo
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.haze
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

// ── Glass tokens — used throughout this screen ────────────────────────────────
// Two families: an on-image variant (white-translucent, for controls sitting
// over the hero cover / card thumbnails) and the light-page variant from
// Theme.kt (GlassSurfaceLight / GlassBorderLight, for chips + panels that sit
// directly on the light #F4F7F9 background — never the old dark-glass look).
private val OnImageGlassFill    = Color(0x1AFFFFFF)  // 10% white over image
private val OnImageGlassBorder  = Color(0x33FFFFFF)  // 20% white hairline
private val OnImageGlassFillMd  = Color(0x26FFFFFF)  // 15% white — slightly more opaque pills

// CHANGE (UI polish): tokens for the collapsing top bar, the recent-read backdrop,
// the curved content panel and the glass search overlay.
private const val PILL_SHOW_AT     = 0.98f   // top bar this collapsed → "Browse" pill drops in
private const val PILL_HIDE_BELOW  = 0.82f   // …and only leaves once it is clearly re-opening (no flicker at the edge)
private val PanelRadius            = 28.dp   // curve of the content panel's top corners
private const val BACKDROP_DECODE_W = 200    // backdrop cover is decoded this small, then scaled up
private const val BACKDROP_DECODE_H = 300    // = the "very slight blur" (bigger = sharper, smaller = softer)
private const val SEARCH_GLASS_ALPHA         = 0.60f  // overlay tint over the real blur (Android 12+)
private const val SEARCH_GLASS_ALPHA_NO_BLUR = 0.90f  // older Androids can't blur: tint is stronger so text stays readable
private val RECENT_CARD_W = 126.dp                    // was 140dp
private val RECENT_CARD_H = 189.dp                    // was 210dp (same 2:3 ratio)

@Composable
fun BrowseScreen(
    onNovelClick: (slug: String) -> Unit,
    onContinueReading: ((slug: String, chapterNum: Int) -> Unit)? = null,
    onDownloadsClick: (() -> Unit)? = null,
    onGenreClick: ((genre: String) -> Unit)? = null,
    // CHANGE: new optional callback — powers the trailing "See More" genre
    // card and the "See all" header link, both of which now route to the
    // existing DiscoverScreen (which already lists every genre as a card).
    onDiscoverClick: (() -> Unit)? = null,
    vm: BrowseViewModel = viewModel()
) {
    val browseState      by vm.browseState.collectAsStateWithLifecycle()
    val popularState      by vm.popularState.collectAsStateWithLifecycle()
    val continueReading  by vm.continueReading.collectAsStateWithLifecycle()
    val recentlyReading  by vm.recentlyReading.collectAsStateWithLifecycle()
    val isRefreshing     by vm.isRefreshing.collectAsStateWithLifecycle()
    // CHANGE (top bar): true while a download is running → animates the top-bar icons.
    val downloading      by vm.isDownloading.collectAsStateWithLifecycle()
    // CHANGE (progress ring): overall progress of the running downloads, 0..1.
    val downloadFraction by vm.downloadFraction.collectAsStateWithLifecycle()

    // The list's scroll state lives here (not inside BrowseContent) so the top bar
    // can follow the scroll position and collapse with it.
    val listState = rememberLazyListState()

    // CHANGE (top bar): the page content is the blur source for the glass top bar
    // (same Haze setup as the floating nav and the search overlay).
    val topHaze = remember { HazeState() }
    val glass   = rememberBarGlass()

    val density     = LocalDensity.current
    val statusBarDp = with(density) { WindowInsets.statusBars.getTop(density).toDp() }
    val barHeight   = statusBarDp + BAR_CONTENT_HEIGHT
    val barHeightPx = with(density) { barHeight.toPx() }
    val pageBg      = MaterialTheme.colorScheme.background
    val reducedMotion = rememberReducedMotion()

    // "Browse" pill: shows once the top bar has scrolled fully away, hides again
    // when it starts coming back. Two thresholds (hysteresis) so a finger
    // hovering on the boundary doesn't make the pill flicker in and out.
    var pillShown by remember { mutableStateOf(false) }
    LaunchedEffect(listState, barHeightPx) {
        snapshotFlow { collapseOf(listState, barHeightPx) }
            .collect { c -> pillShown = if (pillShown) c > PILL_HIDE_BELOW else c >= PILL_SHOW_AT }
    }

    // Light background fills the entire screen
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(pageBg)
    ) {
        // Search lives exclusively in the SearchOverlay (opened from the
        // search FAB in the floating nav) — the homepage itself is browse-only, no inline bar.
        // Pull down to reload the feed. The current rows stay on screen while
        // it loads (a silent refresh), unlike Retry / a source switch, which
        // show the skeleton.
        // The list fills the whole screen and scrolls UNDER the top bar (drawn on top,
        // below). The pull indicator's offset is the bar's height, so at rest it looks
        // identical. CHANGE (top bar): .haze(topHaze) makes this the layer the glass
        // bar blurs — the hero's blurred backdrop now continues up behind the bar.
        AppPullToRefresh(
            isRefreshing       = isRefreshing,
            onRefresh          = vm::refresh,
            failures           = vm.refreshFailed,
            indicatorTopOffset = barHeight,
            modifier           = Modifier.fillMaxSize().haze(topHaze)
        ) {
            BrowseContent(
                state             = browseState,
                popularState      = popularState,
                onNovelClick      = onNovelClick,
                onRetry           = vm::loadHomepage,
                onGenreClick      = onGenreClick ?: {},
                onDiscoverClick   = onDiscoverClick,
                continueReading   = continueReading,
                onContinueReading = onContinueReading,
                recentlyReading   = recentlyReading,
                listState         = listState,
                topInset          = barHeight
            )
        }

        // Status-bar scrim — once the bar has scrolled away, content would run
        // straight under the system clock/battery icons; this fades the page colour
        // in behind them (alpha follows the collapse, drawn without recomposing).
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(statusBarDp + 16.dp)
                .graphicsLayer { alpha = collapseOf(listState, barHeightPx) }
                .background(
                    Brush.verticalGradient(
                        listOf(pageBg.copy(alpha = 0.92f), pageBg.copy(alpha = 0f))
                    )
                )
        )

        // ── Glass top bar — logo + download button. Rides up with the page as you
        // scroll and fades over the second half of the move (see TopNavBar).
        TopNavBar(
            onDownloadsClick = onDownloadsClick,
            downloading      = downloading,
            progress         = downloadFraction,
            glass            = glass,
            hazeState        = topHaze,
            collapse         = { collapseOf(listState, barHeightPx) },
            modifier         = Modifier.align(Alignment.TopCenter)
        )

        // ── "Browse" pill + download button, centred under the status bar. Drops in
        // from the bar that just left (fade + scale 0.88→1 + a short slide, one strong
        // ease-out, 340ms) and leaves quicker and subtler (140ms). With system
        // animations off it just appears/disappears. It's only in the tree while shown,
        // so an invisible pill can never swallow taps meant for the list.
        AnimatedVisibility(
            visible  = pillShown,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = statusBarDp + BTN_MARGIN_TOP),
            enter = if (reducedMotion) EnterTransition.None else
                fadeIn(tween(Motion.BASE_MS, easing = Motion.EaseOut)) +
                scaleIn(
                    animationSpec   = tween(Motion.ENTER_MS, easing = Motion.EaseOut),
                    initialScale    = 0.88f,
                    transformOrigin = TransformOrigin(0.5f, 0f)
                ) +
                slideInVertically(tween(Motion.ENTER_MS, easing = Motion.EaseOut)) { -it / 2 },
            exit = if (reducedMotion) ExitTransition.None else
                fadeOut(tween(Motion.QUICK_MS, easing = LinearEasing)) +
                scaleOut(
                    animationSpec   = tween(Motion.QUICK_MS, easing = FastOutLinearInEasing),
                    targetScale     = 0.94f,
                    transformOrigin = TransformOrigin(0.5f, 0f)
                ) +
                slideOutVertically(tween(Motion.QUICK_MS, easing = FastOutLinearInEasing)) { -it / 4 }
        ) {
            CollapsedPill(
                onDownloadsClick = onDownloadsClick,
                downloading      = downloading,
                progress         = downloadFraction,
                glass            = glass
            )
        }
    }
}

// How far the top bar has collapsed: 0 = fully open (list at rest), 1 = fully gone.
// Only meaningful while the list is near its top; once the first item has scrolled
// out it is simply 1. With no list on screen (skeleton / error) the state sits at 0,
// so the bar stays open. Call it from a layout/draw-phase lambda or snapshotFlow only.
private fun collapseOf(state: LazyListState, barHeightPx: Float): Float {
    if (barHeightPx <= 0f) return 0f
    if (state.firstVisibleItemIndex > 0) return 1f
    return (state.firstVisibleItemScrollOffset / barHeightPx).coerceIn(0f, 1f)
}

// ── Top Nav Bar ───────────────────────────────────────────────────────────────
// CHANGE: the old surface-coloured bar with its cut-out "bay" is now a frosted-glass
// strip, 60dp tall below the status bar (was 100dp = 40% shorter). The "bay" can't
// exist any more: it needed ~78dp of height, and the dominant-colour behind it is
// replaced by the hero's own blurred cover showing through the glass.
// Glass = the same Haze blur as the floating nav (30dp) + a theme tint: white glass
// in light mode, the app's glass grey in dark mode. Not the nav's always-dark pill:
// the system draws DARK status icons in light mode, which a dark bar would swallow.
private val BAR_CONTENT_HEIGHT = 66.dp      // below the status bar (60dp last round, +10%)
private val BTN_SIZE           = 40.dp      // downloads button (unchanged)
private val BTN_MARGIN_END     = 14.dp      // button ↔ screen's right edge
private val BTN_MARGIN_TOP     = 13.dp      // centres the 40dp button in the 66dp bar
private val BAR_FLARE          = 14.dp      // how far the inverted corners reach below the flat edge
private val RING_STROKE        = 2.7.dp     // download-progress ring around the button
// The open bar shows the arrow-down icon held STILL (the ring shows progress). Flip to true
// to make it loop while a download runs, like the "Browse" pill's arrow does.
private const val BAR_ICON_ANIMATES_WHEN_DOWNLOADING = false
private const val TOP_GLASS_ALPHA = 0.55f   // glass tint over the blur (lower = more of the cover shows)

// CHANGE: the bar's bottom corners used to be rounded OFF (convex, like a tab). They are
// now the opposite — INVERTED fillets: the bar's flat bottom edge sits [flare] above the
// full height, and at both screen edges the glass sweeps down along a concave quarter
// circle to the full height, as if the bar were melting into the screen sides. Drawn
// inside the bar's own bounds (full height = flat edge + flare), so nothing overflows.
private class InvertedBottomCorners(private val flare: Dp) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val w = size.width
        val h = size.height
        val r = with(density) { flare.toPx() }.coerceIn(0f, minOf(w / 2f, h))
        val path = Path().apply {
            moveTo(0f, 0f)
            lineTo(w, 0f)
            lineTo(w, h)
            // right fillet: centre (w - r, h), from 0° sweeping -90° up to (w - r, h - r)
            arcTo(Rect(w - 2f * r, h - r, w, h + r), 0f, -90f, false)
            lineTo(r, h - r)
            // left fillet: centre (r, h), from -90° sweeping -90° down to (0, h)
            arcTo(Rect(0f, h - r, 2f * r, h + r), -90f, -90f, false)
            close()
        }
        return Outline.Generic(path)
    }
}

private class BarGlass(
    val tint: Color,        // fill of the glass over the blur
    val ink: Color,         // text + icon colour
    val paper: Color,       // solid version of the glass colour (the "Browse" pill)
    val discFill: Color,    // download button disc: uniform, bright, opaque
    val discInk: Color      // everything drawn on that disc: icon + progress ring
)

@Composable
private fun rememberBarGlass(): BarGlass {
    val dark = isSystemInDarkTheme()
    val ink  = MaterialTheme.colorScheme.onSurface
    return remember(dark, ink) {
        val base = if (dark) GlassBase else Color.White
        BarGlass(
            tint       = base.copy(alpha = TOP_GLASS_ALPHA),
            ink        = ink,
            paper      = base,
            // The button is the same bright disc with near-black marks in both themes.
            discFill   = if (dark) Color(0xFFE6EAF2) else Color.White,
            discInk    = Color(0xFF0D1117)
        )
    }
}

@Composable
private fun TopNavBar(
    onDownloadsClick: (() -> Unit)?,
    downloading: Boolean,
    progress: Float,
    glass: BarGlass,
    hazeState: HazeState,
    collapse: () -> Float,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val statusBarDp = with(density) { WindowInsets.statusBars.getTop(density).toDp() }
    val shape = remember { InvertedBottomCorners(BAR_FLARE) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            // Collapse = the bar is PLACED higher as you scroll (placement phase: cheap,
            // no recomposition). It has to be a layout move, not a graphicsLayer one: Haze
            // only re-reads where a glass element sits when it is laid out, so a
            // draw-phase translation would leave the blur sampling the wrong strip.
            .layout { measurable, constraints ->
                val p = measurable.measure(constraints)
                layout(p.width, p.height) {
                    p.place(0, -(collapse() * p.height).roundToInt())
                }
            }
            // fades over the second half of the move (alpha only — no position change)
            .graphicsLayer { alpha = 1f - ((collapse() - 0.45f) / 0.55f).coerceIn(0f, 1f) }
            .height(statusBarDp + BAR_CONTENT_HEIGHT + BAR_FLARE)
            .clip(shape)
            .glassBlur(hazeState, shape, glass.tint)
    ) {
        // Everything below sits in the 60dp under the status bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(statusBarDp + BAR_CONTENT_HEIGHT)
                .padding(top = statusBarDp)
        ) {
            // Logo — larger (24 → 28sp) and nudged right (36dp from the edge)
            Text(
                "nCrawler",
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 36.dp),
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight    = FontWeight.ExtraBold,
                    fontSize      = 28.sp,
                    letterSpacing = (-0.5).sp
                ),
                color = glass.ink
            )

            // Download button — static download icon on a soft disc; while a download runs
            // it becomes the looping arrow and a progress ring traces the disc's edge.
            DownloadButton(
                onClick     = onDownloadsClick,
                downloading = downloading,
                progress    = progress,
                glass       = glass,
                modifier    = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = BTN_MARGIN_END)
            )
        }
    }
}

// The downloads button, shared by the open top bar (soft disc on the glass) and the
// collapsed pill row (solid disc + soft shadow: there it floats over scrolling covers).
// CHANGE: a bright, uniform disc with near-black marks. A 2.7dp dark ring traces the
// disc's circumference showing overall progress (no track behind it). The open bar holds
// the arrow-down icon still; the pill keeps its static download icon that swaps to the
// looping arrow while downloading. (The 4-second box/wave/checkmark animation lives on
// the Detail screen's download button.)
@Composable
private fun DownloadButton(
    onClick: (() -> Unit)?,
    downloading: Boolean,
    progress: Float,
    glass: BarGlass,
    modifier: Modifier = Modifier,
    floating: Boolean = false
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
            .then(if (floating) Modifier.shadow(6.dp, CircleShape) else Modifier)
            .clip(CircleShape)
            .background(glass.discFill)
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
        // progress ring — drawn on the circumference (inset by half the stroke so it
        // sits fully inside the disc's edge). Never recomposes: both values are read
        // in the draw phase. A tiny minimum sweep shows the ring has started.
        Canvas(
            Modifier
                .matchParentSize()
                .graphicsLayer { alpha = ringAlpha }
        ) {
            val stroke = RING_STROKE.toPx()
            val inset  = stroke / 2f
            val arc    = Size(size.width - stroke, size.height - stroke)
            // progress arc only — no background track, just the dark arc on the bright disc
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
            // Open bar: the arrow-down held still (loops only if the token above is true).
            // Pill: static download icon, swapping to the looping arrow while downloading.
            val animated = downloading && (floating || BAR_ICON_ANIMATES_WHEN_DOWNLOADING)
            Crossfade(
                targetState   = animated,
                animationSpec = tween(Motion.BASE_MS),
                label         = "downloadIconSwap"
            ) { running ->
                if (running) {
                    AnimatedArrowDownIcon(ink = glass.discInk)
                } else if (floating) {
                    AnimatedDownloadIcon(animating = false, ink = glass.discInk, paper = glass.discFill)
                } else {
                    StaticArrowDownIcon(ink = glass.discInk)
                }
            }
        }
    }
}

// "Browse" pill + download button, shown once the top bar has scrolled away. The
// pill is exactly centred on the screen: an invisible spacer the width of the
// button + gap on its left balances the button on its right. Same font as the
// "nCrawler" logo (Montserrat ExtraBold via titleLarge), 20sp instead of 28sp.
@Composable
private fun CollapsedPill(
    onDownloadsClick: (() -> Unit)?,
    downloading: Boolean,
    progress: Float,
    glass: BarGlass
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Spacer(Modifier.width(BTN_SIZE + 8.dp))
        Box(
            modifier = Modifier
                .height(BTN_SIZE)
                .shadow(6.dp, CircleShape)
                .clip(CircleShape)
                .background(glass.paper.copy(alpha = 0.94f))
                .padding(horizontal = 20.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "Browse",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight    = FontWeight.ExtraBold,
                    fontSize      = 20.sp,
                    lineHeight    = 24.sp,
                    letterSpacing = (-0.3).sp
                ),
                color    = glass.ink,
                maxLines = 1,
                softWrap = false
            )
        }
        Spacer(Modifier.width(8.dp))
        DownloadButton(
            onClick     = onDownloadsClick,
            downloading = downloading,
            progress    = progress,
            glass       = glass,
            floating    = true
        )
    }
}

// ── Search Overlay ────────────────────────────────────────────────────────────
// Opened from the search FAB in the floating nav — full-screen, autofocused
// field, recent searches (max 5, each a rounded rectangle with its own "x") when
// empty, live results once typing, X to close. All text is Montserrat.
// CHANGE (UI polish): the background is frosted glass (real Haze blur of the screen
// behind it) instead of a solid page colour; the X clears the text first and only
// closes the overlay when the field is already empty; the cursor always sits at the
// end of the text (see the TextFieldValue below).
// [hazeState] = the blur source from NavGraph; null → a plain translucent fallback.
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SearchOverlay(
    vm: BrowseViewModel,
    onNovelClick: (String) -> Unit,
    onClose: () -> Unit,
    hazeState: HazeState? = null
) {
    val query          by vm.query.collectAsStateWithLifecycle()
    val searchState    by vm.searchState.collectAsStateWithLifecycle()
    val recentSearches by vm.recentSearches.collectAsStateWithLifecycle()

    val focusManager    = LocalFocusManager.current
    val keyboard        = LocalSoftwareKeyboardController.current
    val focusRequester   = remember { FocusRequester() }

    // The field's thick border + search glyph are black in light mode; black on
    // the dark background would disappear, so dark mode uses the text colour.
    val fieldInk = if (isSystemInDarkTheme()) MaterialTheme.colorScheme.onSurface else Color.Black

    // Autofocus + open the keyboard the moment the overlay appears
    LaunchedEffect(Unit) {
        delay(150)
        focusRequester.requestFocus()
    }

    // CHANGE (UI polish #7): the field holds a TextFieldValue (text + cursor) instead of
    // a bare String. With a String, text that arrives from outside — tapping a recent
    // search — left the cursor wherever it was (the start of an empty field). Now every
    // programmatic change puts the cursor at the END of the text, and while you type or
    // move the cursor yourself it is left alone. The effect re-syncs from the view
    // model for any other external change; it compares against the LATEST value
    // (vm.query.value), not the one captured at composition, so fast typing can never
    // be rolled back by a stale emission.
    var field by remember { mutableStateOf(TextFieldValue(query, TextRange(query.length))) }
    LaunchedEffect(query) {
        val latest = vm.query.value
        if (field.text != latest) field = TextFieldValue(latest, TextRange(latest.length))
    }
    fun pickTerm(term: String) {
        vm.onQueryChange(term)
        field = TextFieldValue(term, TextRange(term.length))
    }

    // CHANGE (UI polish #5): glass background. Light = white glass, dark = the app's
    // glass grey (GlassBase) — the same two fills the nav uses — over a real 30dp blur
    // of whatever is behind (Android 12+). Older Androids can't blur, so there the tint
    // is stronger and simply hides the screen enough to keep text readable.
    val dark    = isSystemInDarkTheme()
    val canBlur = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val glassTint = (if (dark) GlassBase else Color.White)
        .copy(alpha = if (canBlur) SEARCH_GLASS_ALPHA else SEARCH_GLASS_ALPHA_NO_BLUR)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .then(
                if (hazeState != null) Modifier.glassBlur(hazeState, RectangleShape, glassTint)
                else Modifier.background(MaterialTheme.colorScheme.background.copy(alpha = 0.94f))
            )
            // Swallows touches so nothing falls through to the screen behind the glass
            // (the old opaque Surface did this implicitly).
            .pointerInput(Unit) {}
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // ── Field + close (X) ───────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Custom-bordered field: Material3's OutlinedTextField has no
                // public "border width" knob, only color — so this uses a
                // filled TextField (indicator hidden) inside a Box with an
                // explicit thick black border for exact control.
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .border(2.5.dp, fieldInk, RoundedCornerShape(16.dp))
                ) {
                    TextField(
                        value         = field,
                        onValueChange = { v ->
                            field = v
                            if (v.text != vm.query.value) vm.onQueryChange(v.text)
                        },
                        modifier      = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester),
                        placeholder   = {
                            Text(
                                "Search novels…",
                                fontFamily = MontserratFamily,
                                fontWeight = FontWeight.Bold,
                                color      = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        leadingIcon = {
                            // Filled, solid glyph — TikTok-style, not the
                            // softer Rounded family used elsewhere.
                            Icon(Icons.Filled.Search, contentDescription = null,
                                tint = fieldInk)
                        },
                        textStyle = LocalTextStyle.current.copy(
                            fontFamily = MontserratFamily,
                            fontWeight = FontWeight.Bold,
                            color      = MaterialTheme.colorScheme.onSurface
                        ),
                        singleLine = true,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor   = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor   = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            cursorColor             = AccentBlue
                        ),
                        shape = RoundedCornerShape(16.dp),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = {
                            vm.commitSearch(field.text)
                            focusManager.clearFocus()
                            keyboard?.hide()
                        })
                    )
                }
                Spacer(Modifier.width(8.dp))
                // CHANGE (UI polish #6): same "x", two jobs. Text in the field → clear it
                // (and keep typing: focus + keyboard come back, since the IME's Search
                // key may have hidden them). Field already empty → close the overlay.
                val hasText = field.text.isNotEmpty()
                IconButton(onClick = {
                    if (hasText) {
                        vm.clearSearch()
                        field = TextFieldValue("")
                        focusRequester.requestFocus()
                        keyboard?.show()
                    } else {
                        vm.clearSearch()
                        onClose()
                    }
                }) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = if (hasText) "Clear search" else "Close search",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            if (query.isBlank()) {
                // ── Recent searches (max 5) ─────────────────────────────────
                // CHANGE: small rounded rectangles laid out side by side and
                // wrapping onto the next line (FlowRow) — not stacked full-width
                // rows. Same look as the reader's controls: a soft fill of the text
                // colour at 10% (the reader pill's alpha), no border, Montserrat,
                // icon at 90%. Long terms are cut short with an ellipsis (chip is
                // capped at 168dp). Tap = search again, "x" = remove.
                if (recentSearches.isNotEmpty()) {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Text(
                            "Recent Searches",
                            fontFamily = MontserratFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize   = 14.sp,
                            color      = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(10.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement   = Arrangement.spacedBy(8.dp)
                        ) {
                            // CHANGE (motion): chips pop in one after another (30ms apart).
                            recentSearches.take(5).forEachIndexed { chipIndex, term ->
                                Row(
                                    modifier = Modifier
                                        .staggerIn(chipIndex, distance = 8.dp, stepMs = 30)
                                        .widthIn(max = 168.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f))
                                        .clickable { pickTerm(term) }
                                        .padding(start = 12.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        term,
                                        fontFamily = MontserratFamily,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize   = 14.sp,
                                        color      = MaterialTheme.colorScheme.onSurface,
                                        maxLines   = 1,
                                        overflow   = TextOverflow.Ellipsis,
                                        modifier   = Modifier.weight(1f, fill = false)
                                    )
                                    // Plain box, not IconButton: IconButton's 48dp minimum
                                    // touch size would balloon the small chip.
                                    Box(
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(CircleShape)
                                            .clickable { vm.removeRecentSearch(term) },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            Icons.Filled.Close,
                                            contentDescription = "Remove \"$term\" from recent searches",
                                            tint     = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f),
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            "Search by title",
                            fontFamily = MontserratFamily,
                            fontSize   = 15.sp,
                            color      = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                // ── Live results ─────────────────────────────────────────────
                SearchContent(
                    state        = searchState,
                    query        = query,
                    onNovelClick = { slug ->
                        vm.commitSearch(query)
                        onNovelClick(slug)
                        onClose()
                    }
                )
            }
        }
    }
}

// ── Browse Content ────────────────────────────────────────────────────────────
@Composable
private fun BrowseContent(
    state: BrowseUiState,
    popularState: BrowseUiState,
    onNovelClick: (String) -> Unit,
    onRetry: () -> Unit,
    onGenreClick: (String) -> Unit,
    onDiscoverClick: (() -> Unit)?,
    continueReading: ContinueReadingInfo?,
    onContinueReading: ((slug: String, chapterNum: Int) -> Unit)?,
    recentlyReading: List<ContinueReadingInfo>,
    // CHANGE (UI polish #1): hoisted so the top bar can follow the scroll; topInset = the
    // top bar's height — the list scrolls under the bar, so its content starts below it.
    listState: LazyListState,
    topInset: Dp
) {
    // CHANGE (motion): skeleton -> content is a short cross-fade (not a hard cut),
    // and inside the content the top blocks stack in one after another (once).
    // Errors get a quick shake. The phase is what animates; the content itself
    // reads the latest state.
    val phase = when (state) {
        is BrowseUiState.Loading -> 0
        is BrowseUiState.Error, is BrowseUiState.Empty -> 1
        is BrowseUiState.Success -> 2
    }
    // A reload that goes through the skeleton (source switch / Retry) must not come back
    // scrolled to the old position now that the list state lives outside this screen.
    LaunchedEffect(phase) { if (phase != 2) listState.scrollToItem(0) }
    Crossfade(
        targetState   = phase,
        modifier      = Modifier.fillMaxSize(),
        animationSpec = tween(Motion.QUICK_MS + 40)
    ) { p ->
    when (p) {
        0 -> ShimmerScope { BrowseSkeleton(topInset) }
        1 -> BrowseError((state as? BrowseUiState.Error)?.message ?: "No novels found", onRetry, topInset)
        else -> (state as? BrowseUiState.Success)?.let { success ->
            val novels = success.novels
            val hero   = novels.firstOrNull()

            // Group into up to 5 genre rows each, samples of ~10 per genre —
            // "Latest" from novels.drop(1), "Popular" from the separate
            // /sort/most-popular fetch (a genuinely different source, not a
            // slice of the same list).
            // FIX: some sources (NovelArrow's homepage/listing scrape) can't
            // supply per-card genre tags — those cards come back with
            // genres = "" and groupByTopGenres() drops them entirely, which
            // silently renders NOTHING even though novels/popularNovels are
            // non-empty. Each section below now falls back to one flat,
            // ungrouped row instead of disappearing whenever grouping
            // yields zero rows.
            // FIX: drop(1) ran unconditionally, but the hero above only takes the
            // first novel when there is no Continue Reading card — so with one
            // showing, the newest release was silently missing from Latest.
            val latestList        = if (continueReading != null && onContinueReading != null) novels else novels.drop(1)
            val latestGenreRows   = remember(novels) { groupByTopGenres(latestList) }
            val showFlatLatest    = latestGenreRows.isEmpty() && latestList.isNotEmpty()
            val popularNovels     = (popularState as? BrowseUiState.Success)?.novels ?: emptyList()
            val popularGenreRows  = remember(popularNovels) { groupByTopGenres(popularNovels) }
            val showFlatPopular   = popularGenreRows.isEmpty() && popularNovels.isNotEmpty()

            // CHANGE: showcase genres now come from the real data (top 8 by
            // how many novels carry them) instead of a hardcoded 3-item list.
            // perGenre=1 because the showcase only needs genre NAMES, not
            // the novel samples — cheap to compute independently of the
            // other two groupings above.
            val showcaseGenres = remember(novels) {
                groupByTopGenres(novels, maxGenres = 8, perGenre = 1).map { it.first }
            }

            // Order in the entrance stack. Only the first screenful animates; rows
            // further down just appear as you scroll to them (no lag while flicking).
            val hasRecent   = recentlyReading.isNotEmpty() && onContinueReading != null
            val genreOrder  = if (hasRecent) 2 else 1
            val latestOrder = genreOrder + (if (showcaseGenres.isNotEmpty()) 1 else 0)

            // CHANGE (UI polish #2/#4): the novel whose cover is blurred behind the hero +
            // Recently Read area = the most recently read one (the hero IS that novel when
            // there is reading history). With no history it falls back to the featured
            // novel in the hero, so the area never looks different from one launch to the next.
            val resumeActive = continueReading != null && onContinueReading != null
            val topNovel     = if (resumeActive) continueReading?.novel else hero
            val panelColor   = MaterialTheme.colorScheme.background

            LazyColumn(
                state          = listState,
                modifier       = Modifier.fillMaxSize(),
                // CHANGE (top bar): no top padding any more — the bar's height is added INSIDE
                // the first item (see the spacer below), so the hero's blurred backdrop starts
                // at the very top of the screen and shows through the glass bar.
                contentPadding = PaddingValues(bottom = 120.dp)
            ) {
                // Everything from the genre showcase down sits on ONE panel that has the
                // page colour and a curved top. The first panel item gets the curve and
                // is pulled up by the curve's radius so the backdrop shows through its
                // corners; every item after it just continues the same colour.
                // On the first item the panel (layout/clip/background) wraps the entrance
                // stagger, not the other way round: a fading layer would clip the curved
                // corners that overhang the item, so only the CONTENT fades and rises.
                var panelOpen = false
                fun panel(): Modifier {
                    val m = if (panelOpen) Modifier.background(panelColor)
                            else Modifier.sheetTop(PanelRadius, panelColor)
                    panelOpen = true
                    return m
                }

                // ── Hero + Recently Read, on the blurred backdrop. The hero resumes the
                // last-read novel at its exact chapter when there's reading history;
                // otherwise it features the top of the feed and opens its detail page.
                // Recently Read = everything read EXCEPT the hero item. Tapping a card
                // opens the reader at the exact chapter; the small "i" badge opens the
                // detail page instead. No center play button — the whole card (minus
                // the "i") is the tap target.
                if (topNovel == null) {
                    item(key = "sec_top_inset") { Spacer(Modifier.height(topInset)) }
                }
                if (topNovel != null) {
                    item(key = "sec_top") {
                        Box(Modifier.fillMaxWidth()) {
                            BlurredBackdrop(url = topNovel.coverUrl, modifier = Modifier.matchParentSize())
                            Column {
                                Spacer(Modifier.height(topInset + 12.dp))
                                Box(Modifier.staggerIn(0, maxAnimated = 5)) {
                                    if (resumeActive && continueReading != null && onContinueReading != null) {
                                        HeroBanner(
                                            novel         = continueReading.novel,
                                            resumeChapter = continueReading.progress.lastChapterNum,
                                            onClick       = {
                                                onContinueReading(
                                                    continueReading.novel.slug,
                                                    continueReading.progress.lastChapterNum
                                                )
                                            }
                                        )
                                    } else {
                                        HeroBanner(
                                            novel         = topNovel,
                                            resumeChapter = null,
                                            onClick       = { onNovelClick(topNovel.slug) }
                                        )
                                    }
                                }
                                if (hasRecent && onContinueReading != null) {
                                    Column(Modifier.staggerIn(1, maxAnimated = 5)) {
                                        Spacer(Modifier.height(24.dp))
                                        RecentlyReadRow(
                                            items          = recentlyReading,
                                            onOpenReader   = onContinueReading,
                                            onOpenDetail   = onNovelClick
                                        )
                                    }
                                }
                                // visible gap above the panel + the part the panel overlaps
                                Spacer(Modifier.height(PanelRadius + 20.dp))
                            }
                        }
                    }
                }

                // ── Genre showcase — decorative gradient-font cards, now a
                // horizontally scrolling row (up to 8 genres, 6 alternating
                // decorative styles) ending in a "See More" card that opens
                // the existing DiscoverScreen (every genre, full list).
                if (showcaseGenres.isNotEmpty()) {
                    val panelMod = panel()
                    item(key = "sec_genre") {
                        Column(panelMod.staggerIn(genreOrder, distance = 8.dp, maxAnimated = 5)) {
                            Spacer(Modifier.height(24.dp))
                            GenreShowcaseRow(
                                genres          = showcaseGenres,
                                onGenreClick    = onGenreClick,
                                onDiscoverClick = onDiscoverClick
                            )
                        }
                    }
                }

                // ── Latest Updates — up to 5 genre rows, horizontal samples,
                // a chevron on each opens that genre's full list ───────────────
                if (latestGenreRows.isNotEmpty()) {
                    val panelMod = panel()
                    item(key = "sec_latest_header") {
                        Column(panelMod.staggerIn(latestOrder, distance = 8.dp, maxAnimated = 5)) {
                            Spacer(Modifier.height(24.dp))
                            SectionHeader("Latest Updates")
                        }
                    }
                    itemsIndexed(latestGenreRows, key = { _, it -> "latest_${it.first}" }) { i, (genre, rowNovels) ->
                        Column(Modifier.staggerIn(latestOrder + 1 + i, maxAnimated = 5).background(panelColor)) {
                            Spacer(Modifier.height(16.dp))
                            GenreRow(
                                genre        = genre,
                                novels       = rowNovels,
                                onNovelClick = onNovelClick,
                                onSeeMore    = { onGenreClick(genre) }
                            )
                        }
                    }
                } else if (showFlatLatest) {
                    // FIX: no genre data to group by (e.g. NovelArrow) —
                    // show everything fetched as one flat row instead of
                    // nothing at all.
                    val panelMod = panel()
                    item(key = "sec_latest_flat_header") {
                        Column(panelMod) {
                            Spacer(Modifier.height(24.dp))
                            SectionHeader("Latest Updates")
                        }
                    }
                    item(key = "sec_latest_flat_row") {
                        Column(Modifier.background(panelColor)) {
                            Spacer(Modifier.height(16.dp))
                            GenreRow(
                                genre        = "Latest",
                                novels       = latestList,
                                onNovelClick = onNovelClick,
                                onSeeMore    = null
                            )
                        }
                    }
                }

                // ── Popular — same pattern, sourced from /sort/most-popular ──
                if (popularGenreRows.isNotEmpty()) {
                    val panelMod = panel()
                    item(key = "sec_popular_header") {
                        Column(panelMod) {
                            Spacer(Modifier.height(28.dp))
                            SectionHeader("Popular")
                        }
                    }
                    items(popularGenreRows, key = { "popular_${it.first}" }) { (genre, rowNovels) ->
                        Column(Modifier.background(panelColor)) {
                            Spacer(Modifier.height(16.dp))
                            GenreRow(
                                genre        = genre,
                                novels       = rowNovels,
                                onNovelClick = onNovelClick,
                                onSeeMore    = { onGenreClick(genre) }
                            )
                        }
                    }
                    item(key = "sec_popular_end") { Spacer(Modifier.fillMaxWidth().height(20.dp).background(panelColor)) }
                } else if (showFlatPopular) {
                    // FIX: same fallback as Latest — flat row when there's
                    // no genre data to group by.
                    val panelMod = panel()
                    item(key = "sec_popular_flat_header") {
                        Column(panelMod) {
                            Spacer(Modifier.height(28.dp))
                            SectionHeader("Popular")
                        }
                    }
                    item(key = "sec_popular_flat_row") {
                        Column(Modifier.background(panelColor)) {
                            Spacer(Modifier.height(16.dp))
                            GenreRow(
                                genre        = "Popular",
                                novels       = popularNovels,
                                onNovelClick = onNovelClick,
                                onSeeMore    = null
                            )
                        }
                    }
                    item(key = "sec_popular_flat_end") { Spacer(Modifier.fillMaxWidth().height(20.dp).background(panelColor)) }
                }
            }
        }
    }
    }
}

// ── Curved content panel ──────────────────────────────────────────────────────
// CHANGE (UI polish #4): the panel behind the genre showcase / Latest Updates / Popular.
// Its top corners are rounded (an "n"-shaped arch). It is pulled UP over the item above
// by [radius]: the item reports a height that many dp shorter and draws that many dp
// higher, so the blurred backdrop is what shows in the two cut-off corners and the
// panel reads as a sheet sliding over the Recently Read area. Only the top [radius] dp
// of the item is drawn outside its measured bounds, and that strip is plain spacing —
// nothing tappable lives there.
private fun Modifier.sheetTop(radius: Dp, color: Color): Modifier =
    this
        .layout { measurable, constraints ->
            val r = radius.roundToPx()
            val placeable = measurable.measure(constraints)
            layout(placeable.width, (placeable.height - r).coerceAtLeast(0)) {
                placeable.place(0, -r)
            }
        }
        .clip(RoundedCornerShape(topStart = radius, topEnd = radius))
        .background(color)

// ── Blurred backdrop for the hero + Recently Read area ───────────────────────
// CHANGE (UI polish #2): the most recently read novel's cover, softly blurred, behind
// the hero banner and the Recently Read cards. The blur comes from decoding the cover
// SMALL (BACKDROP_DECODE_W × BACKDROP_DECODE_H) and letting it scale up smoothly — not
// from a RenderEffect — so it costs nothing per frame while scrolling and looks the
// same on every Android version (RenderEffect blur only exists on 12+). A wash of the
// page colour sits on top (55% light / 65% dark) so the "Recently Read" title and the
// hero's edges stay readable in both themes. A new novel cross-fades in (500ms).
@Composable
private fun BlurredBackdrop(url: String?, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val dark    = isSystemInDarkTheme()
    val reduced = rememberReducedMotion()
    val wash    = MaterialTheme.colorScheme.background.copy(alpha = if (dark) 0.65f else 0.55f)

    Box(modifier.clipToBounds()) {
        Crossfade(
            targetState   = url,
            modifier      = Modifier.fillMaxSize(),
            animationSpec = if (reduced) snap() else tween(Motion.ENTER_MS + 160, easing = Motion.EaseOut)
        ) { u ->
            if (!u.isNullOrBlank()) {
                val request = remember(u) {
                    ImageRequest.Builder(context)
                        .data(u)
                        .size(BACKDROP_DECODE_W, BACKDROP_DECODE_H)
                        .crossfade(false)
                        .build()
                }
                AsyncImage(
                    model              = request,
                    contentDescription = null,
                    contentScale       = ContentScale.Crop,
                    modifier           = Modifier.fillMaxSize()
                )
            }
        }
        Box(Modifier.matchParentSize().background(wash))
    }
}

// Groups novels by their (comma-separated) genre tags, keeps the top
// [maxGenres] genres by how many novels carry them, and caps each row's
// sample to [perGenre] so a genre row stays a horizontal scroll, not a wall.
private fun groupByTopGenres(
    novels: List<NovelEntity>,
    maxGenres: Int = 5,
    perGenre: Int = 10
): List<Pair<String, List<NovelEntity>>> {
    val byGenre = linkedMapOf<String, MutableList<NovelEntity>>()
    novels.forEach { novel ->
        novel.genres.split(",")
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .forEach { g -> byGenre.getOrPut(g) { mutableListOf() }.add(novel) }
    }
    return byGenre.entries
        .sortedByDescending { it.value.size }
        .take(maxGenres)
        .map { it.key to it.value.take(perGenre) }
}

// Width of one card in Discover's GenreScreen grid. Homepage rows use the same
// width so a novel card is the same size on both screens. FIX: it used to be
// "(screen − padding) / 2", which in landscape stretched each card to ~half of
// an 800dp+ screen; the shared formula adds columns instead, so a card stays
// about the same size in any orientation.
@Composable
private fun novelCardWidth(): androidx.compose.ui.unit.Dp =
    novelCardWidthFor(LocalConfiguration.current.screenWidthDp)

// ── Genre Row — a labeled horizontal sample with a ">" chevron that opens
// the full infinite-scroll list for that genre (Discover's GenreScreen).
// CHANGE: cards are now exactly the size of the cards on Discover's genre grid
// (see novelCardWidth — same width, same 6:7 cover), longer than the old
// 130dp squares. They come from the shared NovelGlassCard (ui/components), and
// the row de-dupes by slug — LazyRow keys must be unique, so a source that
// repeats a novel inside one list used to crash with "Key was already used".
@Composable
private fun GenreRow(
    genre: String,
    novels: List<NovelEntity>,
    onNovelClick: (String) -> Unit,
    // null = nowhere useful to go (flat Latest/Popular rows) — the link is hidden
    // instead of dumping the user on the generic genre list.
    onSeeMore: (() -> Unit)?
) {
    val uniqueNovels = remember(novels) { novels.distinctBy { it.slug } }
    val cardWidth = novelCardWidth()
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Text(
                genre,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground
            )
            if (onSeeMore != null) {
                // CHANGE (UI polish): the "See more" text link is now a thick, rounded ">"
                // (Solar "Alt Arrow Right", see SolarArrows.kt) — 40dp touch target.
                SeeMoreChevron(onClick = onSeeMore, label = "See more $genre")
            }
        }
        Spacer(Modifier.height(10.dp))
        // CHANGE: bottom = 8.dp — a LazyRow clips to its bounds, which was cutting
        // off the cards' drop shadow at the bottom edge.
        LazyRow(
            contentPadding        = PaddingValues(start = 16.dp, end = 16.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            items(uniqueNovels, key = { it.slug }) { novel ->
                NovelGlassCard(
                    novel    = novel,
                    onClick  = { onNovelClick(novel.slug) },
                    modifier = Modifier.width(cardWidth)
                )
            }
        }
    }
}

// The ">" that replaced the "See more" text link. Tinted with the theme's primary (so
// it brightens in dark mode, where AccentBlue is too dim), with the app's standard
// press-in feedback (scale 0.86, 90ms in / soft release).
@Composable
private fun SeeMoreChevron(onClick: () -> Unit, label: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(40.dp)
            .pressable(onClick = onClick, pressedScale = 0.86f),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            SolarArrows.ChevronRight,
            contentDescription = label,
            tint     = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
    }
}

// ── Hero Banner — inset card, rounded corners ─────────────────────────────────
// NOT full-bleed: horizontal page padding + ~20dp corner radius.
// CHANGE: 2× its old height (160 → 320dp — it already spans the page width, so
// height is the only dimension that can double) and no glass left on it: no
// rim border, and the frosted "Start Reading / Resume" pill is now plain
// icon + text with no rectangle behind it.
// CHANGE: the cover slowly drifts (a Ken Burns pan + zoom) instead of sitting
// still — see the heroDrift block below.
// resumeChapter != null → this IS the continue-reading novel: CTA becomes
// "Resume Chapter N" and taps the given onClick (which jumps straight to
// that chapter), instead of "Start Reading" opening the detail page.
@Composable
private fun HeroBanner(novel: NovelEntity, resumeChapter: Int?, onClick: () -> Unit) {
    // ── Slow cover drift ────────────────────────────────────────────────────
    // Three unhurried, back-and-forth loops with different lengths (zoom 22s,
    // pan-x 17s, pan-y 23s), so the motion never visibly repeats in lockstep.
    // The image is always zoomed at least 12% and only pans inside the slack
    // that zoom creates (80% of it), so no edge is ever exposed. Values are
    // read inside graphicsLayer {} — the draw phase — so this never triggers
    // recomposition. Honours the system "Remove animations" setting.
    val context = LocalContext.current
    val reducedMotion = remember(context) {
        Settings.Global.getFloat(
            context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f
        ) == 0f
    }
    val drift = rememberInfiniteTransition(label = "heroDrift")
    val zoom by drift.animateFloat(
        initialValue  = 0f,
        targetValue   = 1f,
        animationSpec = infiniteRepeatable(tween(22000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label         = "heroZoom"
    )
    val panX by drift.animateFloat(
        initialValue  = -1f,
        targetValue   = 1f,
        animationSpec = infiniteRepeatable(tween(17000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label         = "heroPanX"
    )
    val panY by drift.animateFloat(
        initialValue  = -1f,
        targetValue   = 1f,
        animationSpec = infiniteRepeatable(tween(23000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label         = "heroPanY"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(320.dp)
            .pressable(onClick = onClick, pressedScale = 0.98f)   // CHANGE (motion)
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        // Cover image — drifting (the card's clip() trims the overscan)
        CoverImage(
            url                = novel.coverUrl,
            contentDescription = novel.title,
            modifier           = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    if (!reducedMotion) {
                        val scale = 1.12f + 0.08f * zoom          // 1.12 → 1.20
                        scaleX = scale
                        scaleY = scale
                        translationX = panX * (scale - 1f) * size.width  * 0.4f
                        translationY = panY * (scale - 1f) * size.height * 0.4f
                    }
                }
        )

        // Scrim — transparent top, dark bottom, so the text reads over any cover
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0f    to Color.Black.copy(alpha = 0.05f),
                            0.45f to Color.Black.copy(alpha = 0.15f),
                            1f    to Color.Black.copy(alpha = 0.82f)
                        )
                    )
                )
        )

        // Content — bottom-aligned
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 18.dp, end = 18.dp, bottom = 18.dp)
        ) {
            // Title — up to two lines now that the card is taller
            Text(
                novel.title,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight    = FontWeight.ExtraBold,
                    fontSize      = 28.sp,
                    lineHeight    = 34.sp,
                    letterSpacing = (-0.3).sp
                ),
                color    = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(Modifier.height(12.dp))

            // Row: play + label (plain — no pill), rating
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Rounded.PlayArrow,
                        contentDescription = null,
                        tint     = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        if (resumeChapter != null) "Resume Ch. $resumeChapter" else "Start Reading",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize   = 15.sp
                        ),
                        color = Color.White
                    )
                }

                // Rating badge
                if (novel.rating.isNotBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Rounded.Star,
                            contentDescription = null,
                            tint     = StarGold,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            novel.rating,
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}

// ── Recently Read row ─────────────────────────────────────────────────────────
// Mirrors the "Waiting to watch" carousel design: portrait media cards over
// the cover art, title top, footer with an info badge + progress bar. Two
// deliberate departures from the reference:
//  - No center play button — the whole card (minus the "i" badge) IS the
//    play target, so a redundant button would just clutter the cover art.
//  - The "i" badge opens the detail page; everywhere else on the card opens
//    the reader at the exact last-read chapter. Nested clickables handle
//    this correctly — the inner "i" click consumes the tap before it can
//    bubble to the card's own onClick.
@Composable
private fun RecentlyReadRow(
    items: List<ContinueReadingInfo>,
    onOpenReader: (slug: String, chapterNum: Int) -> Unit,
    onOpenDetail: (String) -> Unit
) {
    Column {
        Text(
            "Recently Read",
            modifier = Modifier.padding(horizontal = 16.dp),
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight    = FontWeight.ExtraBold,
                letterSpacing = (-0.2).sp
            ),
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(14.dp))
        LazyRow(
            contentPadding        = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            items(items, key = { it.novel.slug }) { info ->
                RecentCard(
                    info         = info,
                    onOpenReader = { onOpenReader(info.novel.slug, info.progress.lastChapterNum) },
                    onOpenDetail = { onOpenDetail(info.novel.slug) }
                )
            }
        }
    }
}

// Recently Read card: 126×189dp portrait (CHANGE: slightly smaller, was 140×210 — see
// RECENT_CARD_W/H; title 12sp, padding 10dp, "i" badge 22dp to match). CHANGE (kept): the frosted rectangle
// behind the info badge / progress bar is gone — badge, chapter label and
// progress sit straight on the cover's dark scrim. Title uses the reader's
// font (Montserrat).
@Composable
private fun RecentCard(
    info: ContinueReadingInfo,
    onOpenReader: () -> Unit,
    onOpenDetail: () -> Unit
) {
    val novel = info.novel
    val progress = (info.progress.lastChapterNum.toFloat() / novel.chapterCount.coerceAtLeast(1))
        .coerceIn(0f, 1f)
    // CHANGE: a novel with an unknown chapter count (0) used to read "Ch.5/0".
    val chapterText = if (novel.chapterCount > 0)
        "Ch.${info.progress.lastChapterNum}/${novel.chapterCount}"
    else
        "Ch.${info.progress.lastChapterNum}"

    Box(
        modifier = Modifier
            .width(RECENT_CARD_W)
            .height(RECENT_CARD_H)
            .pressable(onClick = onOpenReader, pressedScale = 0.97f)   // CHANGE (motion)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        CoverImage(
            url                = novel.coverUrl,
            contentDescription = novel.title,
            modifier           = Modifier.fillMaxSize()
        )

        // Scrim — light top, dark bottom, so the title reads over any cover
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0f    to Color.Black.copy(alpha = 0.10f),
                            0.5f  to Color.Black.copy(alpha = 0.20f),
                            1f    to Color.Black.copy(alpha = 0.88f)
                        )
                    )
                )
        )

        Column(
            modifier            = Modifier
                .fillMaxSize()
                .padding(10.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Title — top
            Text(
                novel.title,
                fontFamily = MontserratFamily,
                fontWeight = FontWeight.ExtraBold,
                fontSize   = 12.sp,
                lineHeight = 16.sp,
                color      = Color.White,
                maxLines   = 2,
                overflow   = TextOverflow.Ellipsis,
                textAlign  = TextAlign.Start
            )

            // Footer — info badge + chapter label, then progress bar
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.55f))
                            // Nested clickable — consumes the tap here so the
                            // outer card's onOpenReader never fires for this spot.
                            .clickable(onClick = onOpenDetail),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "i",
                            color      = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontStyle  = androidx.compose.ui.text.font.FontStyle.Italic,
                            fontSize   = 13.sp
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        chapterText,
                        color      = Color.White.copy(alpha = 0.85f),
                        fontSize   = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Spacer(Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color.White.copy(alpha = 0.28f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progress)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(2.dp))
                            .background(Color.White)
                    )
                }
            }
        }
    }
}

// ── Genre Showcase — decorative, colour-gradient typography per card ─────────
// Horizontally scrolling row (was a fixed 3-card row) so any number of real
// genres fit, each in one of 6 alternating decorative styles (was 3) — see
// GenreGlassTile (ui/components). Ends in a "See More" card that opens the
// existing DiscoverScreen, which already lists every genre.
@Composable
private fun GenreShowcaseRow(
    genres: List<String>,
    onGenreClick: (String) -> Unit,
    onDiscoverClick: (() -> Unit)?
) {
    Column {
        Row(
            modifier              = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Text(
                "Genre",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight    = FontWeight.ExtraBold,
                    letterSpacing = (-0.2).sp
                ),
                color = MaterialTheme.colorScheme.onBackground
            )
            // FIX: the "See all" link that used to sit here opened the same place as
            // the trailing "See More" card — one route is enough.
        }
        Spacer(Modifier.height(14.dp))
        // CHANGE: fixed 3-card Row → LazyRow so the showcase can hold up to
        // 8 real genres plus a trailing "See More" card without squeezing.
        LazyRow(
            contentPadding        = PaddingValues(start = 16.dp, end = 16.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            itemsIndexed(genres, key = { _, g -> g }) { i, genre ->
                GenreGlassTile(
                    genre    = genre,
                    styleIdx = i % 6,
                    onClick  = { onGenreClick(genre) },
                    modifier = Modifier.width(104.dp)
                )
            }
            if (onDiscoverClick != null) {
                item(key = "genre_see_more") {
                    SeeMoreGenreCard(
                        onClick  = onDiscoverClick,
                        modifier = Modifier.width(104.dp)
                    )
                }
            }
        }
    }
}

// CHANGE: GenreDecorativeCard moved to ui/components/GlassCards.kt as
// GenreGlassTile so Discover's genre grid can use the exact same tile (with
// dark-mode-safe gradients). Its 72dp height / 6-style history lives there.

// New — trailing card at the end of the genre showcase row. Distinct from
// the decorative cards on purpose (outlined accent style, not white glass)
// so it reads as an action, not another genre.
@Composable
private fun SeeMoreGenreCard(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .height(72.dp)
            .pressable(onClick = onClick, pressedScale = 0.96f)   // CHANGE (motion)
            .clip(RoundedCornerShape(16.dp))
            .background(AccentBlue.copy(alpha = 0.10f))
            .border(1.5.dp, AccentBlue.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
            .padding(6.dp),
        contentAlignment = Alignment.Center
    ) {
        // CHANGE (UI polish): the small arrow + "See More" label became a single big,
        // thick, rounded ">" — same icon as the row headers.
        Icon(
            SolarArrows.ChevronRight,
            contentDescription = "See more genres",
            tint     = AccentBlue,
            modifier = Modifier.size(32.dp)
        )
    }
}

// ── Section Header ────────────────────────────────────────────────────────────
@Composable
private fun SectionHeader(title: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight    = FontWeight.ExtraBold,
                letterSpacing = (-0.2).sp
            ),
            color = MaterialTheme.colorScheme.onBackground
        )
        // FIX: removed the "See all" label that was here — it had no click
        // handler, so it looked tappable and did nothing.
    }
}

// ── Genre Chip ────────────────────────────────────────────────────────────────
// Kept for any external reference (e.g. a future filter strip inside
// Discover's GenreScreen) — no longer used on the homepage; the homepage now
// uses GenreShowcaseRow above instead.
@Composable
private fun GenreChip(name: String, isActive: Boolean, onClick: () -> Unit) {
    val bg        = if (isActive) AccentBlue else glassSurface()
    val border    = if (isActive) Color.Transparent else glassBorder()
    val textColor = if (isActive) Color.White else MaterialTheme.colorScheme.onSurface

    Box(
        modifier = Modifier
            .height(50.dp)
            .clip(RoundedCornerShape(25.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(25.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            name,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            color = textColor
        )
    }
}

// ── Novel Card ────────────────────────────────────────────────────────────────
// CHANGE: moved to ui/components/GlassCards.kt as NovelGlassCard — GenreScreen
// carried a hand-copied duplicate that had already drifted from this one.

// ── Search content ────────────────────────────────────────────────────────────
@Composable
private fun SearchContent(
    state: BrowseUiState,
    query: String,
    onNovelClick: (String) -> Unit
) {
    when (state) {
        is BrowseUiState.Loading -> ShimmerScope { SearchSkeleton() }
        is BrowseUiState.Empty   -> SearchEmpty(query)
        is BrowseUiState.Error   -> Box(Modifier.fillMaxSize(), Alignment.Center) {
            Text(
                state.message,
                modifier   = Modifier.errorShake(trigger = state.message),   // CHANGE (motion)
                fontFamily = MontserratFamily,
                color      = MaterialTheme.colorScheme.error
            )
        }
        is BrowseUiState.Success -> LazyColumn(
            contentPadding      = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            items(state.novels, key = { it.slug }) { novel ->
                SearchRow(novel = novel, onClick = { onNovelClick(novel.slug) })
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.08f)
                )
            }
        }
    }
}

@Composable
private fun SearchRow(novel: NovelEntity, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment     = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp, 66.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            CoverImage(
            url                = novel.coverUrl,
            contentDescription = novel.title,
            modifier           = Modifier.fillMaxSize()
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                novel.title,
                fontFamily = MontserratFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize   = 15.sp,
                lineHeight = 20.sp,
                maxLines   = 2,
                overflow   = TextOverflow.Ellipsis,
                color      = MaterialTheme.colorScheme.onBackground
            )
            if (novel.genres.isNotBlank()) {
                Spacer(Modifier.height(3.dp))
                Text(
                    novel.genres.split(",").take(2).joinToString(" · "),
                    fontFamily = MontserratFamily,
                    fontSize   = 12.sp,
                    color      = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Icon(
            Icons.Rounded.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp)
        )
    }
}

// ── Skeleton states ───────────────────────────────────────────────────────────
@Composable
private fun BrowseSkeleton(topInset: Dp) {
    val shimmer = MaterialTheme.colorScheme.surfaceVariant

    // topInset: the list now runs under the top bar, so the skeleton starts below it too.
    Column(modifier = Modifier.fillMaxSize().padding(top = topInset)) {
        Spacer(Modifier.height(12.dp))   // same breathing room as the real hero section
        // Hero skeleton — inset, rounded, 320dp (matches HeroBanner)
        Box(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .height(320.dp)
                .clip(RoundedCornerShape(20.dp))
                .skeleton(shimmer)
        )
        Spacer(Modifier.height(24.dp))

        // Recently Read skeleton — label + row of portrait cards (RECENT_CARD_W × RECENT_CARD_H)
        SkeletonLabel(shimmer, width = 130.dp)
        Spacer(Modifier.height(14.dp))
        Row(
            Modifier.padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            repeat(2) {
                Box(
                    Modifier
                        .width(RECENT_CARD_W)
                        .height(RECENT_CARD_H)
                        .clip(RoundedCornerShape(16.dp))
                        .skeleton(shimmer)
                )
            }
        }
        Spacer(Modifier.height(24.dp))

        // Genre showcase skeleton — "Genre"/"See all" header + row of
        // 104x72dp cards, matching GenreShowcaseRow exactly
        SkeletonLabel(shimmer, width = 90.dp)
        Spacer(Modifier.height(14.dp))
        Row(
            Modifier.padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            repeat(4) {
                Box(
                    Modifier
                        .width(104.dp)
                        .height(72.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .skeleton(shimmer)
                )
            }
        }
        Spacer(Modifier.height(24.dp))

        // Two genre-row sections (Latest Updates / Popular) — section label
        // + a labeled row + a horizontal scroll of novel cards, matching
        // GenreRow exactly (same width, 6:7 cover, 48dp title block)
        val skeletonCardWidth = novelCardWidth()
        repeat(2) {
            SkeletonLabel(shimmer, width = 140.dp, height = 16.dp)
            Spacer(Modifier.height(16.dp))
            SkeletonLabel(shimmer, width = 100.dp)
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier.padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                repeat(3) {
                    // Rounded cover + a title bar underneath (no card behind the
                    // title any more, matching NovelGlassCard)
                    Column(Modifier.width(skeletonCardWidth)) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(skeletonCardWidth * (7f / 6f))
                                .clip(RoundedCornerShape(16.dp))
                                .skeleton(shimmer)
                        )
                        Spacer(Modifier.height(12.dp))
                        Box(
                            Modifier
                                .fillMaxWidth(0.8f)
                                .height(12.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .skeleton(shimmer)
                        )
                        Spacer(Modifier.height(20.dp))
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

// Small shimmer bar standing in for a text label — used throughout the
// skeleton above instead of repeating the same Box(...).skeleton(shimmer)
// four times with slightly different sizes.
@Composable
private fun SkeletonLabel(shimmer: Color, width: androidx.compose.ui.unit.Dp, height: androidx.compose.ui.unit.Dp = 20.dp) {
    Box(
        Modifier
            .padding(horizontal = 16.dp)
            .size(width, height)
            .clip(RoundedCornerShape(4.dp))
            .skeleton(shimmer)
    )
}

@Composable
private fun SearchSkeleton() {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        repeat(6) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Box(
                    Modifier
                        .size(48.dp, 66.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .skeleton(MaterialTheme.colorScheme.surfaceVariant)
                )
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(
                        Modifier
                            .size(140.dp, 13.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .skeleton(MaterialTheme.colorScheme.surfaceVariant)
                    )
                    Box(
                        Modifier
                            .size(90.dp, 10.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .skeleton(MaterialTheme.colorScheme.surfaceVariant)
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchEmpty(query: String) {
    Box(Modifier.fillMaxSize(), Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Rounded.SearchOff,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint     = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "No results for \"$query\"",
                fontFamily = MontserratFamily,
                fontSize   = 15.sp,
                color      = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun BrowseError(message: String, onRetry: () -> Unit, topInset: Dp) {
    // topInset keeps the message centred in the visible area, below the top bar
    Box(Modifier.fillMaxSize().padding(top = topInset), Alignment.Center) {
        // CHANGE (motion): sharp quick shake when the error lands.
        Column(
            modifier            = Modifier.errorShake(trigger = message),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Rounded.WifiOff,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint     = MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
            )
            Spacer(Modifier.height(12.dp))
            Text(
                message,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = onRetry,
                shape   = RoundedCornerShape(12.dp),
                colors  = ButtonDefaults.buttonColors(containerColor = AccentBlue)
            ) {
                Text("Retry")
            }
        }
    }
}
