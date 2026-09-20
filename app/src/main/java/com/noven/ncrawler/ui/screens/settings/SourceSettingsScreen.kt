package com.noven.ncrawler.ui.screens.settings

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.noven.ncrawler.ui.theme.MontserratFamily
import com.noven.ncrawler.ui.theme.glassBorder
import com.noven.ncrawler.ui.theme.glassSurface
import com.noven.ncrawler.viewmodel.SourceSettingsViewModel
import com.noven.ncrawler.viewmodel.SourceUiItem
import kotlinx.coroutines.launch

// CHANGE (this pass):
//  • Dark mode: the old glass cards were ~the same colour as the page (and the
//    unchecked switch track the same colour as the card), and the blue accents
//    were the light-mode blue. Cards now use a lifted surface + outline in dark
//    mode, switches have explicit unchecked colours, and every accent comes from
//    colorScheme.primary (which is the brighter blue in dark mode).
//  • Every piece of text is Montserrat (the reader / detail font).
//  • Moving a source up or down is now animated slowly and deliberately: the row
//    you moved (and the row it swapped with) glide from their old positions to
//    the new ones over 700ms, and the row you moved glows in the accent colour
//    and fades out over 1.4s.
//  • The DEFAULT badge is restyled: a small rounded rectangle with a status dot.

// Slow, deliberate: a long ease-out — quick to leave, then settles gently.
private const val MOVE_MS = 700
private const val GLOW_MS = 1400
private val MoveEasing = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)
private val ROW_GAP = 10.dp

@Composable
fun SourceSettingsScreen(
    onBack: () -> Unit,
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

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost   = {
            SnackbarHost(snackbarHost) { data ->
                Snackbar(modifier = Modifier.padding(12.dp)) {
                    Text(
                        data.visuals.message,
                        fontFamily = MontserratFamily,
                        fontSize   = 14.sp
                    )
                }
            }
        },
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
                Text(
                    "Sources",
                    fontFamily    = MontserratFamily,
                    fontWeight    = FontWeight.ExtraBold,
                    fontSize      = 20.sp,
                    letterSpacing = (-0.3).sp,
                    color         = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    ) { padding ->
        LazyColumn(
            modifier            = Modifier.padding(padding),
            // This screen is a tab of the floating nav (the gear), so the nav
            // floats over its bottom edge — 120dp of bottom padding keeps the
            // last source row scrollable clear of it.
            contentPadding      = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(ROW_GAP)
        ) {
            item {
                Text(
                    "Novels are pulled from the highest-priority source that has them. Reorder or turn sources off below — at least one has to stay on.",
                    fontFamily = MontserratFamily,
                    fontSize   = 13.sp,
                    lineHeight = 20.sp,
                    color      = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(6.dp))
            }

            itemsIndexed(state.items, key = { _, item -> item.id }) { index, item ->
                SourceRow(
                    item     = item,
                    index    = index,
                    isMoved  = movedKey == item.id,
                    // A toggle can reorder rows too — clear the "moved" marker so a
                    // previously moved row doesn't glow for a change it didn't cause.
                    onToggle = { enabled -> movedKey = null; vm.toggle(item.id, enabled) },
                    onUp     = { movedKey = item.id; vm.moveUp(item.id) },
                    onDown   = { movedKey = item.id; vm.moveDown(item.id) }
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
    onToggle: (Boolean) -> Unit,
    onUp: () -> Unit,
    onDown: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val dark   = isSystemInDarkTheme()
    val accent = colors.primary                 // brighter blue in dark mode
    val shape  = RoundedCornerShape(16.dp)

    // Light: the frosted white card. Dark: glassSurface() is ~the page colour, so
    // the card would vanish — lift it to surfaceVariant and outline it instead.
    val cardFill   = if (dark) colors.surfaceVariant else glassSurface()
    val cardBorder = if (dark) colors.outline else glassBorder()

    // ── Reorder animation ────────────────────────────────────────────────
    // Rows are keyed, so when the list reorders each row keeps its state and
    // just receives a new [index]. The frame where index != shownIndex is the
    // first frame at the new position: draw the row still at its OLD place
    // (no one-frame flash at the destination), then glide it over.
    val density = LocalDensity.current
    val gapPx   = with(density) { ROW_GAP.toPx() }
    var rowHeightPx by remember { mutableStateOf(0) }
    var shownIndex  by remember { mutableStateOf(index) }
    val slide = remember { Animatable(0f) }
    val glow  = remember { Animatable(0f) }

    val pendingDelta = shownIndex - index          // rows the row still has to travel
    val pitch        = rowHeightPx + gapPx

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
            .zIndex(if (isMoved) 1f else 0f)      // the moved row passes over its neighbour
            .graphicsLayer {
                translationY = if (pendingDelta != 0) pendingDelta * pitch else slide.value
            }
            .onSizeChanged { rowHeightPx = it.height }
            .fillMaxWidth()
            .clip(shape)
            .background(cardFill)
            .border(1.dp, cardBorder, shape)
            // Accent glow on the row you moved: a soft tint under the content and
            // an outline over it, both fading with [glow].
            .drawWithContent {
                val g = glow.value
                val radius = CornerRadius(16.dp.toPx())
                if (g > 0f) drawRoundRect(color = accent.copy(alpha = 0.16f * g), cornerRadius = radius)
                drawContent()
                if (g > 0f) {
                    drawRoundRect(
                        color        = accent.copy(alpha = 0.9f * g),
                        cornerRadius = radius,
                        style        = Stroke(width = 2.dp.toPx())
                    )
                }
            }
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {

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

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
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
                        DefaultBadge(accent = accent)
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    item.baseUrl,
                    fontFamily = MontserratFamily,
                    fontSize   = 12.sp,
                    color      = colors.onSurfaceVariant.copy(alpha = 0.85f),
                    maxLines   = 1,
                    overflow   = TextOverflow.Ellipsis
                )
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

// DEFAULT badge — restyled: a small rounded rectangle (not a capsule) in a soft
// tint of the accent, with a status dot, in Montserrat. The accent is
// colorScheme.primary, so it stays legible on the dark card.
@Composable
private fun DefaultBadge(accent: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(accent.copy(alpha = 0.16f))
            .padding(horizontal = 9.dp, vertical = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(accent)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            "DEFAULT",
            color         = accent,
            fontFamily    = MontserratFamily,
            fontSize      = 11.sp,
            fontWeight    = FontWeight.Bold,
            letterSpacing = 0.8.sp
        )
    }
}
