package com.noven.ncrawler.ui.screens.settings

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.noven.ncrawler.data.local.DownloadNetwork
import com.noven.ncrawler.ui.components.GlassModeCard
import com.noven.ncrawler.ui.components.Motion
import com.noven.ncrawler.ui.components.errorShake
import com.noven.ncrawler.ui.components.glassCard
import com.noven.ncrawler.ui.components.rememberReducedMotion
import com.noven.ncrawler.ui.components.staggerIn
import com.noven.ncrawler.ui.theme.GlassBase
import com.noven.ncrawler.ui.theme.GlassMode
import com.noven.ncrawler.ui.theme.MontserratFamily
import com.noven.ncrawler.ui.theme.glassBorder
import com.noven.ncrawler.ui.theme.glassSurface
import com.noven.ncrawler.viewmodel.SourceSettingsViewModel
import com.noven.ncrawler.viewmodel.SourceUiItem
import kotlinx.coroutines.launch

// CHANGE (this pass):
//  1. Sources = ONE rounded card. The separate row cards (and the gaps between
//     them) are gone; rows are separated only by a thin horizontal line.
//  2. Simpler: no URL under a source name; the network card is a single row
//     (label + current choice) and its menu is a rounded list of plain labels,
//     with no explanatory sub-text.
//  3. Scrolling: the title bar morphs into a floating "Settings" pill, exactly
//     like Browse's "Browse" pill (same geometry, same timing, nothing drawn
//     behind the pill) and grows back at the top.
//  4. The DEFAULT badge now looks like the reader's "Your place" badge: an
//     outlined capsule, sentence case, Montserrat 10sp semibold.
//
// Kept from before: dark-mode lifted cards, Montserrat everywhere, the slow
// 700ms reorder glide, the shake when the last source can't be switched off.

// Slow, deliberate: a long ease-out — quick to leave, then settles gently.
private const val MOVE_MS = 700
private const val GLOW_MS = 1400
private val MoveEasing = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)

// Every source row is exactly this tall, so the divider lines can be drawn by
// the card at fixed multiples of it and the reorder glide travels one row.
private val ROW_H = 68.dp
private val CARD_RADIUS = 20.dp

@Composable
fun SourceSettingsScreen(
    vm: SourceSettingsViewModel = viewModel()
) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val snackbarHost = remember { SnackbarHostState() }

    // Same transient-message pattern as DetailScreen's updateMessage — show
    // once, then clear, rather than leaving a stale snackbar re-shown on
    // every recomposition.
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHost.showSnackbar(it)
            vm.clearMessage()
        }
    }

    // The row the user just tapped an arrow on. Set BEFORE the ViewModel call so
    // the row already knows it is "the moved one" when the list reorders.
    var movedKey by remember { mutableStateOf<Any?>(null) }

    // When a switch is refused ("Keep one source on") the row you tapped gives a
    // quick sharp shake, in step with the message.
    var blockedKey  by remember { mutableStateOf<Any?>(null) }
    var blockedTick by remember { mutableStateOf(0) }
    LaunchedEffect(state.message) {
        if (state.message != null && blockedKey != null) blockedTick++
    }

    // ── Title bar ⇄ pill (same recipe as Browse) ─────────────────────────
    val listState     = rememberLazyListState()
    val density       = LocalDensity.current
    val statusBarDp   = with(density) { WindowInsets.statusBars.getTop(density).toDp() }
    val barHeight     = statusBarDp + BAR_CONTENT_HEIGHT
    val barHeightPx   = with(density) { barHeight.toPx() }
    val reducedMotion = rememberReducedMotion()

    // The scroll only flips [collapsed] (two thresholds = hysteresis, so a finger
    // hovering on the boundary can't flicker); the morph itself is time-based and
    // interruptible.
    var collapsed by remember { mutableStateOf(false) }
    LaunchedEffect(listState, barHeightPx) {
        snapshotFlow { collapseOf(listState, barHeightPx) }
            .collect { c -> collapsed = if (collapsed) c > MORPH_EXPAND_BELOW else c >= MORPH_COLLAPSE_AT }
    }
    val morph = remember { Animatable(0f) }
    LaunchedEffect(collapsed, reducedMotion) {
        val target = if (collapsed) 1f else 0f
        if (reducedMotion) {
            morph.snapTo(target)
        } else {
            morph.animateTo(
                target,
                tween(if (collapsed) Motion.ENTER_MS else Motion.SCREEN_MS, easing = Motion.EaseOut)
            )
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        LazyColumn(
            state               = listState,
            // The list fills the screen and scrolls UNDER the title bar. This screen is
            // a tab of the floating nav, so 120dp of bottom padding keeps the last
            // card scrollable clear of it.
            contentPadding      = PaddingValues(start = 16.dp, top = barHeight + 16.dp, end = 16.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier            = Modifier.fillMaxSize()
        ) {
            item { SectionLabel("Sources") }
            item {
                Text(
                    "The top source is tried first. Reorder or switch off.",
                    fontFamily = MontserratFamily,
                    fontSize   = 13.sp,
                    lineHeight = 20.sp,
                    color      = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(6.dp))
            }

            item {
                SourcesCard(
                    items       = state.items,
                    movedKey    = movedKey,
                    blockedKey  = blockedKey,
                    blockedTick = blockedTick,
                    // A toggle can reorder rows too — clear the "moved" marker so a
                    // previously moved row doesn't glow for a change it didn't cause.
                    onToggle = { id, enabled ->
                        movedKey = null
                        blockedKey = id
                        vm.toggle(id, enabled)
                    },
                    onUp   = { id -> movedKey = id; vm.moveUp(id) },
                    onDown = { id -> movedKey = id; vm.moveDown(id) }
                )
            }

            item {
                Spacer(Modifier.height(8.dp))
                SectionLabel("Downloads")
            }
            item { NetworkCard(mode = state.networkMode, onChange = vm::setNetworkMode) }

            item {
                Spacer(Modifier.height(8.dp))
                SectionLabel("Look")
            }
            item { GlassModeCard() }

            // Credit required by the icon licence (CC BY 4.0)
            item {
                Text(
                    "Icons: Solar by 480 Design (CC BY 4.0)",
                    fontFamily = MontserratFamily,
                    fontSize   = 11.sp,
                    color      = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier   = Modifier.padding(top = 8.dp)
                )
            }
        }

        // Drawn on top of the list: the bar at rest, the floating pill once scrolled.
        SettingsTopChrome(
            morph    = { morph.value },
            modifier = Modifier.align(Alignment.TopCenter)
        )

        // The floating nav sits over the bottom edge, so lift the snackbar above it.
        SnackbarHost(
            snackbarHost,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 76.dp)
        ) { data ->
            Snackbar(modifier = Modifier.padding(12.dp)) {
                Text(
                    data.visuals.message,
                    fontFamily = MontserratFamily,
                    fontSize   = 14.sp
                )
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text.uppercase(),
        fontFamily    = MontserratFamily,
        fontWeight    = FontWeight.Bold,
        fontSize      = 12.sp,
        letterSpacing = 1.sp,
        color         = MaterialTheme.colorScheme.primary
    )
}

// The shared card surface of this screen. Glass mode: the shared glass card.
// Classic: the original fill + outline. Light: the frosted white card. Dark:
// glassSurface() is ~the page colour, so the card would vanish — lift it to
// surfaceVariant and outline it instead. Always clipped to [shape].
@Composable
private fun settingsCardModifier(shape: Shape): Modifier {
    val colors = MaterialTheme.colorScheme
    val dark   = isSystemInDarkTheme()
    return if (GlassMode.enabled) {
        Modifier.glassCard(shape, elevation = 3.dp)
    } else {
        Modifier
            .clip(shape)
            .background(if (dark) colors.surfaceVariant else glassSurface())
            .border(1.dp, if (dark) colors.outline else glassBorder(), shape)
    }
}

// ── Sources: ONE card, rows split by a thin line ─────────────────────────────
@Composable
private fun SourcesCard(
    items: List<SourceUiItem>,
    movedKey: Any?,
    blockedKey: Any?,
    blockedTick: Int,
    onToggle: (id: String, enabled: Boolean) -> Unit,
    onUp: (id: String) -> Unit,
    onDown: (id: String) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val dark   = isSystemInDarkTheme()
    val shape  = RoundedCornerShape(CARD_RADIUS)
    val line   = colors.onSurface.copy(alpha = if (dark) 0.14f else 0.09f)
    val count  = items.size

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(settingsCardModifier(shape))
            // The dividers belong to the card, not the rows: they stay put while a
            // row glides past them. Drawn behind the rows.
            .drawBehind {
                val rowPx = ROW_H.toPx()
                for (k in 1 until count) {
                    val y = k * rowPx
                    drawLine(
                        color       = line,
                        start       = Offset(0f, y),
                        end         = Offset(size.width, y),
                        strokeWidth = 0.8.dp.toPx()
                    )
                }
            }
    ) {
        items.forEachIndexed { index, item ->
            // key: the row keeps its own animation state when the list reorders.
            key(item.id) {
                SourceRow(
                    item      = item,
                    index     = index,
                    isMoved   = movedKey == item.id,
                    shakeTick = if (blockedKey == item.id) blockedTick else 0,
                    onToggle  = { enabled -> onToggle(item.id, enabled) },
                    onUp      = { onUp(item.id) },
                    onDown    = { onDown(item.id) }
                )
            }
        }
    }
}

@Composable
private fun SourceRow(
    item: SourceUiItem,
    index: Int,
    isMoved: Boolean,
    shakeTick: Int,
    onToggle: (Boolean) -> Unit,
    onUp: () -> Unit,
    onDown: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val accent = colors.primary                 // brighter blue in dark mode

    // ── Reorder animation ────────────────────────────────────────────────
    // Rows are keyed, so when the list reorders each row keeps its state and
    // just receives a new [index]. The frame where index != shownIndex is the
    // first frame at the new position: draw the row still at its OLD place
    // (no one-frame flash at the destination), then glide it over. Every row
    // is exactly ROW_H tall, so one row = one pitch.
    val pitch = with(LocalDensity.current) { ROW_H.toPx() }
    var shownIndex by remember { mutableStateOf(index) }
    val slide = remember { Animatable(0f) }
    val glow  = remember { Animatable(0f) }

    val pendingDelta = shownIndex - index          // rows the row still has to travel

    LaunchedEffect(index) {
        if (shownIndex != index) {
            val delta = shownIndex - index
            // Continue from wherever the row is drawn right now (a second tap
            // mid-glide shouldn't make it jump), then hand over to the animation.
            slide.snapTo(slide.value + delta * pitch)
            shownIndex = index
            if (isMoved) {
                launch {
                    glow.snapTo(1f)
                    glow.animateTo(0f, tween(GLOW_MS))
                }
            }
            slide.animateTo(0f, tween(MOVE_MS, easing = MoveEasing))
        }
    }

    Box(
        modifier = Modifier
            // Rows stack in on first open (once), and shake when a switch is refused.
            // Both are separate transform layers, so neither fights the reorder slide.
            .staggerIn(index, distance = 8.dp, stepMs = 45, maxAnimated = 8)
            .errorShake(trigger = shakeTick, amplitude = 7.dp, onEnter = false)
            .zIndex(if (isMoved) 1f else 0f)      // the moved row passes over its neighbour
            .graphicsLayer {
                translationY = if (pendingDelta != 0) pendingDelta * pitch else slide.value
            }
            .fillMaxWidth()
            .height(ROW_H)
            // Accent wash on the row you moved, fading out. (No outline: inside one
            // joined card it would be clipped at the card's edge.)
            .drawWithContent {
                val g = glow.value
                if (g > 0f) drawRect(color = accent.copy(alpha = 0.16f * g))
                drawContent()
            }
            .padding(horizontal = 14.dp)
    ) {
        Row(
            modifier          = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {

            // Reorder arrows — only meaningful (and shown) for enabled sources
            if (item.enabled) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(onClick = onUp, modifier = Modifier.size(28.dp)) {
                        Icon(
                            Icons.Filled.KeyboardArrowUp,
                            contentDescription = "Move up",
                            tint     = colors.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(onClick = onDown, modifier = Modifier.size(28.dp)) {
                        Icon(
                            Icons.Filled.KeyboardArrowDown,
                            contentDescription = "Move down",
                            tint     = colors.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                Spacer(Modifier.width(6.dp))
            } else {
                Spacer(Modifier.width(34.dp))
            }

            // Name (+ badge). The URL line is gone — just the name.
            Row(
                modifier          = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    item.displayName,
                    fontFamily = MontserratFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize   = 16.sp,
                    // Disabled: dimmed, but still readable in dark mode
                    color      = if (item.enabled) colors.onSurface
                                 else colors.onSurfaceVariant.copy(alpha = 0.65f),
                    maxLines   = 1,
                    overflow   = TextOverflow.Ellipsis,
                    modifier   = Modifier.weight(1f, fill = false)
                )
                if (item.isDefault) {
                    Spacer(Modifier.width(10.dp))
                    DefaultBadge(ink = colors.onSurface)
                }
            }

            Switch(
                checked = item.enabled,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(
                    checkedThumbColor   = Color.White,
                    checkedTrackColor   = accent,
                    checkedBorderColor  = accent,
                    // Explicit unchecked colours: the M3 default track is
                    // surfaceVariant — the same colour as the dark-mode card.
                    uncheckedThumbColor  = colors.onSurfaceVariant,
                    uncheckedTrackColor  = colors.background,
                    uncheckedBorderColor = colors.outline
                )
            )
        }
    }
}

// DEFAULT badge — the same badge as "Your place" in the reader's contents sheet:
// an outlined capsule (1dp ink @ 40%), 10dp/3dp padding, Montserrat 10sp semibold,
// sentence case. Ink = onSurface here (the reader's sheet ink is its own palette).
@Composable
private fun DefaultBadge(ink: Color) {
    val shape = RoundedCornerShape(50)
    Box(
        Modifier
            .clip(shape)
            .border(1.dp, ink.copy(alpha = 0.4f), shape)
            .padding(horizontal = 10.dp, vertical = 3.dp)
    ) {
        Text(
            "Default",
            fontFamily = MontserratFamily,
            fontSize   = 10.sp,
            fontWeight = FontWeight.SemiBold,
            color      = ink
        )
    }
}

// ── Download network: one row, rounded plain-label menu ──────────────────────
// Built from the stable DropdownMenu (not ExposedDropdownMenuBox, whose API
// changed between Material3 versions). The menu's corner radius comes from the
// theme's extraSmall shape (what DropdownMenu reads on every Material3 version),
// overridden just around the menu, so no version-specific parameter is needed.
@Composable
private fun NetworkCard(mode: DownloadNetwork, onChange: (DownloadNetwork) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val accent = colors.primary
    var expanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(settingsCardModifier(RoundedCornerShape(CARD_RADIUS)))
            .clickable { expanded = true }
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "Download network",
            modifier   = Modifier.weight(1f),
            fontFamily = MontserratFamily,
            fontWeight = FontWeight.Bold,
            fontSize   = 15.sp,
            color      = colors.onSurface
        )

        Box {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    mode.label,
                    fontFamily = MontserratFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize   = 14.sp,
                    color      = accent
                )
                Spacer(Modifier.width(2.dp))
                Icon(
                    Icons.Default.KeyboardArrowDown,
                    contentDescription = "Choose download network",
                    tint     = colors.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }

            MaterialTheme(
                shapes = MaterialTheme.shapes.copy(extraSmall = RoundedCornerShape(18.dp))
            ) {
                DropdownMenu(
                    expanded         = expanded,
                    onDismissRequest = { expanded = false },
                    offset           = DpOffset(0.dp, 6.dp)
                ) {
                    DownloadNetwork.values().forEach { option ->
                        val selected = option == mode
                        DropdownMenuItem(
                            text = {
                                Text(
                                    option.label,
                                    fontFamily = MontserratFamily,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                    fontSize   = 14.sp
                                )
                            },
                            trailingIcon = if (selected) {
                                { Icon(Icons.Default.Check, contentDescription = "Selected", tint = accent) }
                            } else null,
                            onClick = {
                                expanded = false
                                if (!selected) onChange(option)
                            },
                            // Rounded highlight on the chosen row, inset from the menu's edge
                            modifier = Modifier
                                .padding(horizontal = 6.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (selected) accent.copy(alpha = 0.12f) else Color.Transparent)
                        )
                    }
                }
            }
        }
    }
}

// ═════════════════════════════════════════════════════════════════════════════
// Title bar ⇄ "Settings" pill — the Browse recipe, minus the download button and
// the glass blur (this screen has no photo behind it, so the bar at rest is a
// solid surface).
//
//  • ONE element with ONE fixed layout rectangle (screen width × bar height).
//    What animates is its OUTLINE (drawn from the morph value in the draw phase)
//    and the title's translation — nothing is re-measured mid-animation.
//  • Outline: the bar's edges close in from both sides to the centre while the
//    bottom corners go from inverted fillets (bar) to fully round (pill).
//  • The title is the SAME word at the SAME size before and after, so unlike Browse
//    (nCrawler → Browse) it simply glides from the left to the centre.
//  • 340ms in / 300ms out, Motion.EaseOut, interruptible; reduced motion = snap.
//  • Pill geometry is identical to Browse's, so it lands in the same spot when you
//    flip between the tabs. Once scrolled, ONLY the pill is drawn up top.
// ═════════════════════════════════════════════════════════════════════════════
private const val MORPH_COLLAPSE_AT  = 0.40f   // scrolled this far (fraction of the bar's height) → bar morphs into the pill
private const val MORPH_EXPAND_BELOW = 0.20f   // …and only grows back once it is clearly near the top again

private val BAR_CONTENT_HEIGHT = 66.dp         // below the status bar (open bar) — same as Browse
private val TITLE_INSET        = 20.dp         // open bar: title ↔ screen's left edge
private val BAR_FLARE          = 14.dp         // how far the inverted corners reach below the flat edge
private val CAPSULE_H          = 40.dp         // the pill's height
private val CAPSULE_TOP        = 13.dp         // pill's top edge, below the status bar
private val CAPSULE_PAD        = 20.dp         // pill: edge ↔ text, both sides

private fun mix(a: Float, b: Float, t: Float): Float = a + (b - a) * t

// 0 until [from], 1 from [to] on, linear in between.
private fun window(p: Float, from: Float, to: Float): Float = ((p - from) / (to - from)).coerceIn(0f, 1f)

// How far the bar has collapsed: 0 = fully open (list at rest), 1 = fully gone.
private fun collapseOf(state: androidx.compose.foundation.lazy.LazyListState, barHeightPx: Float): Float {
    if (barHeightPx <= 0f) return 0f
    if (state.firstVisibleItemIndex > 0) return 1f
    return (state.firstVisibleItemScrollOffset / barHeightPx).coerceIn(0f, 1f)
}

private class MorphGeometry(private val density: Density, statusDp: Dp) {
    private fun px(d: Dp): Float = with(density) { d.toPx() }

    val statusPx = px(statusDp)
    val barH     = px(statusDp + BAR_CONTENT_HEIGHT + BAR_FLARE)
    val flare    = px(BAR_FLARE)
    val capH     = px(CAPSULE_H)
    val capTop   = statusPx + px(CAPSULE_TOP)
    val pad      = px(CAPSULE_PAD)

    /** Width of the pill = equal padding either side of the REAL laid-out text width. */
    fun capW(textPx: Float): Float = pad + textPx + pad

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

// The outline at morph value [p] — handed to graphicsLayer so the pill gets its shadow.
private class MorphBarShape(
    private val geo: MorphGeometry,
    private val p: Float,
    private val capW: Float
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        Outline.Generic(geo.path(size.width, p, capW))
}

@Composable
private fun SettingsTopChrome(
    morph: () -> Float,
    modifier: Modifier = Modifier
) {
    val density     = LocalDensity.current
    val statusBarDp = with(density) { WindowInsets.statusBars.getTop(density).toDp() }
    val colors      = MaterialTheme.colorScheme
    val dark        = isSystemInDarkTheme()
    val geo         = remember(density, statusBarDp) { MorphGeometry(density, statusBarDp) }

    val rest  = colors.surface                         // the bar at rest (as before)
    val paper = if (dark) GlassBase else Color.White   // the pill — same as Browse's
    val ink   = colors.onSurface

    // Measured, not guessed: the screen width and the REAL width of the title text.
    var rootW by remember { mutableStateOf(0f) }
    var textW by remember { mutableStateOf(0f) }
    val capW = { geo.capW(textW) }
    val titleInsetPx = with(density) { TITLE_INSET.toPx() }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(with(density) { geo.barH.toDp() })
            .onSizeChanged { rootW = it.width.toFloat() }
    ) {
        // The body: an explicit path, filled and outlined by hand. A soft shadow
        // fades in once the outline is convex (the pill).
        Box(
            Modifier
                .fillMaxSize()
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
                    drawPath(path, lerp(rest, paper, p).copy(alpha = mix(1f, 0.94f, p)))
                    // hairline edge so the bar / pill reads against the page
                    drawPath(path, ink.copy(alpha = 0.10f), style = Stroke(width = 1.5.dp.toPx()))
                }
        )

        // The title: one word, one size — it glides from the left inset to the centre.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = statusBarDp)
                .height(BAR_CONTENT_HEIGHT),
            contentAlignment = Alignment.CenterStart
        ) {
            Text(
                "Settings",
                modifier = Modifier
                    .padding(start = TITLE_INSET)
                    .onSizeChanged { textW = it.width.toFloat() }
                    .graphicsLayer {
                        translationX = ((rootW - textW) / 2f - titleInsetPx) * morph()
                    }
                    .semantics { heading() },
                fontFamily    = MontserratFamily,
                fontWeight    = FontWeight.ExtraBold,
                fontSize      = 20.sp,
                lineHeight    = 24.sp,
                letterSpacing = (-0.3).sp,
                color         = ink,
                maxLines      = 1,
                softWrap      = false
            )
        }
    }
}
