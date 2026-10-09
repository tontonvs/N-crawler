package com.noven.ncrawler.ui.screens.reader

import android.os.Build
import dev.chrisbanes.haze.haze
import com.noven.ncrawler.ui.components.Motion
import com.noven.ncrawler.ui.theme.GlassBase
import com.noven.ncrawler.ui.components.SolarIcons
import dev.chrisbanes.haze.HazeState
import com.noven.ncrawler.ui.components.glassBlur
import androidx.compose.runtime.CompositionLocalProvider
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noven.ncrawler.ui.components.glassTint
import com.noven.ncrawler.ui.theme.MontserratFamily
import com.noven.ncrawler.viewmodel.ReaderSettings
import com.noven.ncrawler.viewmodel.ReaderSwatch
import com.noven.ncrawler.viewmodel.ReaderTextAlign
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

// ── Sheet palette (Contents + Settings) ─────────────────────────────────────
// Both sheets share ONE glass surface, copied from the Search overlay: a real
// 30dp blur of what is behind, tinted white in light mode / the app's glass grey
// in dark mode (and a stronger tint on Androids that can't blur, so text stays
// readable). Light/dark follows the reader page — the swatch you picked — so the
// sheet always reads as part of the page behind it. Every colour below is a tone
// of `ink`, so text always counters the surface it sits on.
private const val SHEET_GLASS_ALPHA         = 0.60f   // same as the Search overlay
private const val SHEET_GLASS_ALPHA_NO_BLUR = 0.90f

@Immutable
internal class SheetPalette(val dark: Boolean) {
    val glassBase: Color  = if (dark) GlassBase else Color.White
    val ink: Color        = if (dark) Color(0xFFF2EEE8) else Color(0xFF1A1714)
    val onInk: Color      = if (dark) Color(0xFF1A1714) else Color(0xFFFAF6F0)   // text on an ink-filled pill
    val muted: Color      = ink.copy(alpha = 0.62f)
    val dim: Color        = ink.copy(alpha = 0.50f)   // chapters already read
    val chip: Color       = ink.copy(alpha = 0.09f)
    val chipStrong: Color = ink.copy(alpha = 0.15f)
    val hairline: Color   = ink.copy(alpha = 0.12f)
    val handle: Color     = ink.copy(alpha = 0.30f)
}

internal val LocalSheetPalette = staticCompositionLocalOf { SheetPalette(false) }

// ── Shared bottom-sheet chrome (settings + table of contents) ────────────────
// One implementation of the drag-handle sheet so the TOC is *exactly* the
// settings menu's look and motion, just taller (heightFraction). Motion
// weighting: Jakub primary (mobile consumer app).
// Enter: slides up from below the screen, 320ms FastOutSlowInEasing.
// Exit: slides back down, 260ms, then calls onDismiss — every dismiss path
// (scrim tap, drag past the threshold, back press, content calling dismiss())
// goes through the same animated exit.
// Drag: direct transform update on the handle; velocity-based dismissal
// (>0.3 px/ms) or >120px dragged. Handle colour changes on press.
// Reduced motion (system "Remove animations"): no slide, instant show/hide.
// heightFraction == null → wraps its content (settings); otherwise fixed to
// that fraction of the screen height (TOC).
// Room kept clear above a sheet for the header (audio/text pill + the button row).
private val SHEET_HEADER_CLEARANCE = 118.dp

@Composable
internal fun ReaderSheet(
    accent: Color,
    palette: SheetPalette,
    haze: HazeState,
    onDismiss: () -> Unit,
    heightFraction: Float? = null,
    content: @Composable ColumnScope.(dismiss: () -> Unit) -> Unit
) {
    // Android's real "reduced motion" signal is the system animator scale —
    // "Remove animations" in Accessibility (or Developer options) sets it to 0.
    val context = LocalContext.current
    val reducedMotion = remember(context) {
        Settings.Global.getFloat(
            context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f
        ) == 0f
    }

    // The sheet STARTS off-screen (Animatable's initial value) instead of
    // starting at 0 and being snapped down in LaunchedEffect — that left one
    // frame with the sheet fully visible before it jumped away and slid in.
    // The wrap-content sheet uses 640.dp (always clears it); the tall sheet
    // uses the full screen height so it clears itself too.
    val density       = LocalDensity.current
    val configuration = LocalConfiguration.current
    val hiddenPx = with(density) {
        (if (heightFraction == null) 640.dp else configuration.screenHeightDp.dp).toPx()
    }

    // Animate the sheet's vertical offset via Animatable for smooth snap-back
    val offsetY  = remember { Animatable(if (reducedMotion) 0f else hiddenPx) }
    val scope    = androidx.compose.runtime.rememberCoroutineScope()
    var handleHeld   by remember { mutableStateOf(false) }
    var dismissing   by remember { mutableStateOf(false) }

    // Track drag velocity for threshold dismissal
    var lastDragTime by remember { mutableStateOf(0L) }
    var lastDragY    by remember { mutableStateOf(0f) }

    // Animated exit, then tell the host to remove the sheet. Guarded so a
    // second tap / drag-end during the 260ms exit can't start it twice.
    val dismiss: () -> Unit = {
        if (!dismissing) {
            dismissing = true
            scope.launch {
                if (!reducedMotion) {
                    offsetY.animateTo(hiddenPx, tween(260, easing = FastOutLinearInEasing))
                }
                onDismiss()
            }
        }
    }
    // The drag handler below lives in pointerInput(Unit) and would otherwise
    // keep the first composition's `dismiss` forever.
    val currentDismiss by rememberUpdatedState(dismiss)

    // Back closes the sheet (animated) instead of leaving the reader.
    BackHandler(onBack = dismiss)

    // Enter animation — sheet slides up from below on first composition
    LaunchedEffect(Unit) {
        if (!reducedMotion) {
            offsetY.animateTo(
                targetValue    = 0f,
                animationSpec  = tween(320, easing = FastOutSlowInEasing)
            )
        }
    }

    val fillHeight = if (heightFraction != null) Modifier.fillMaxHeight() else Modifier

    // Search-overlay glass: real blur on Android 12+, a stronger flat tint below.
    val shape     = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    val canBlur   = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val glassTint = palette.glassBase.copy(
        alpha = if (canBlur) SHEET_GLASS_ALPHA else SHEET_GLASS_ALPHA_NO_BLUR
    )

    // Scrim — tapping outside dismisses. The sheet never grows into the header
    // (which now stays visible above it): padding AFTER clickable keeps the whole
    // screen tappable but shrinks the area the sheet can occupy.
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication        = null,
                onClick           = dismiss
            )
            .statusBarsPadding()
            .padding(top = SHEET_HEADER_CLEARANCE)
    ) {
        // Sheet — anchored to bottom, consumes clicks so they don't reach scrim
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .then(if (heightFraction != null) Modifier.fillMaxHeight(heightFraction) else Modifier)
                .offset { IntOffset(x = 0, y = offsetY.value.roundToInt()) }
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication        = null,
                    onClick           = {}
                )
        ) {
            // No drop shadow: it would bleed through the translucent glass.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(fillHeight)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
                    .clip(shape)
                    .glassBlur(haze, shape, glassTint)
            ) {
                CompositionLocalProvider(LocalSheetPalette provides palette) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(fillHeight)
                            .navigationBarsPadding()
                            .padding(horizontal = 20.dp)
                    ) {
                        // Drag handle — colour changes on press (Jakub: tactile feedback)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 14.dp, bottom = 16.dp)
                                .pointerInput(Unit) {
                                    detectVerticalDragGestures(
                                        onDragStart = {
                                            handleHeld   = true
                                            lastDragTime = System.currentTimeMillis()
                                            lastDragY    = offsetY.value
                                        },
                                        onDragEnd = {
                                            handleHeld = false
                                            val elapsed  = (System.currentTimeMillis() - lastDragTime).coerceAtLeast(1)
                                            val velocity = (offsetY.value - lastDragY) / elapsed
                                            if (offsetY.value > 120f || velocity > 0.3f) {
                                                // Fast downward flick or dragged far enough → dismiss
                                                currentDismiss()
                                            } else {
                                                // Snap back up
                                                scope.launch {
                                                    offsetY.animateTo(
                                                        targetValue   = 0f,
                                                        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)
                                                    )
                                                }
                                            }
                                        },
                                        onDragCancel = {
                                            handleHeld = false
                                            scope.launch {
                                                offsetY.animateTo(0f, spring())
                                            }
                                        },
                                        onVerticalDrag = { _, dragAmount ->
                                            lastDragTime = System.currentTimeMillis()
                                            lastDragY    = offsetY.value
                                            // Only allow dragging downward; resistance when pulling up
                                            val newOffset = (offsetY.value + dragAmount).coerceAtLeast(-20f)
                                            scope.launch {
                                                offsetY.snapTo(newOffset)
                                            }
                                        }
                                    )
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            // Handle pill — accent when held, muted when idle
                            Box(
                                modifier = Modifier
                                    .width(44.dp)
                                    .height(5.dp)
                                    .clip(RoundedCornerShape(50))
                                    .background(if (handleHeld) accent else palette.handle)
                            )
                        }

                        this.content(dismiss)
                    }
                }
            }
        }
    }
}

// ── Draggable settings sheet ─────────────────────────────────────────────────
// The settings content inside the shared ReaderSheet chrome above.
@Composable
internal fun DraggableSettingsSheet(
    accent: Color,
    palette: SheetPalette,
    haze: HazeState,
    settings: ReaderSettings,
    swatches: List<ReaderSwatch>,
    onDismiss: () -> Unit,
    onBrightness: (Float) -> Unit,
    onSelectSwatch: (Int) -> Unit,
    onDecreaseFont: () -> Unit,
    onIncreaseFont: () -> Unit,
    onSetAlign: (ReaderTextAlign) -> Unit
) {
    ReaderSheet(accent = accent, palette = palette, haze = haze, onDismiss = onDismiss) { _ ->
        val p = LocalSheetPalette.current

        Text(
            "READER SETTINGS",
            fontFamily    = MontserratFamily,
            fontWeight    = FontWeight.ExtraBold,
            fontSize      = 12.sp,
            letterSpacing = 1.sp,
            color         = p.ink,
            modifier      = Modifier.padding(bottom = 18.dp)
        )

        // Brightness
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier          = Modifier.padding(bottom = 20.dp)
        ) {
            Icon(SolarIcons.Sun, null, tint = p.muted, modifier = Modifier.size(18.dp))
            Slider(
                value         = settings.brightness,
                onValueChange = onBrightness,
                valueRange    = 0f..0.7f,
                modifier      = Modifier.weight(1f).padding(horizontal = 12.dp),
                colors = SliderDefaults.colors(
                    thumbColor         = p.ink,
                    activeTrackColor   = p.ink,
                    inactiveTrackColor = p.chipStrong
                )
            )
            Icon(SolarIcons.Sun, null, tint = p.ink, modifier = Modifier.size(26.dp))
        }

        // Theme swatches
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp)
        ) {
            swatches.forEachIndexed { index, swatch ->
                val selected = index == settings.swatchIndex
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(swatch.background)
                        .border(
                            width = if (selected) 2.5.dp else 1.dp,
                            color = if (selected) p.ink else p.hairline,
                            shape = RoundedCornerShape(16.dp)
                        )
                        .clickable { onSelectSwatch(index) }
                )
            }
        }

        // Font size
        Row(
            modifier              = Modifier.fillMaxWidth().padding(bottom = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            FontSizeButton("–", onDecreaseFont)
            Box(
                Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(p.chip)
                    .padding(horizontal = 28.dp, vertical = 10.dp)
            ) {
                Text(
                    settings.fontSize.toInt().toString(),
                    fontFamily = MontserratFamily,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize   = 20.sp,
                    color      = p.ink
                )
            }
            FontSizeButton("+", onIncreaseFont)
        }

        // Text alignment — a full-width segmented control now that Auto scroll
        // lives in the reader header (three equal cells, selected one highlighted).
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(p.chip)
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            AlignSegment(SolarIcons.AlignLeft,   settings.textAlign == ReaderTextAlign.LEFT,   Modifier.weight(1f)) { onSetAlign(ReaderTextAlign.LEFT) }
            AlignSegment(SolarIcons.AlignCenter, settings.textAlign == ReaderTextAlign.CENTER, Modifier.weight(1f)) { onSetAlign(ReaderTextAlign.CENTER) }
            AlignSegment(SolarIcons.AlignRight,  settings.textAlign == ReaderTextAlign.RIGHT,  Modifier.weight(1f)) { onSetAlign(ReaderTextAlign.RIGHT) }
        }
    }
}

@Composable
private fun FontSizeButton(label: String, onClick: () -> Unit) {
    val p = LocalSheetPalette.current
    Box(
        modifier = Modifier
            .size(52.dp, 44.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(p.chipStrong)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(label, fontFamily = MontserratFamily, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = p.ink)
    }
}

@Composable
private fun AlignSegment(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit
) {
    val p = LocalSheetPalette.current
    Box(
        modifier = modifier
            .height(44.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) p.chipStrong else Color.Transparent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null,
            tint = if (selected) p.ink else p.ink.copy(alpha = 0.35f),
            modifier = Modifier.size(22.dp))
    }
}
