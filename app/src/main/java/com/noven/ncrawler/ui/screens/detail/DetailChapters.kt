package com.noven.ncrawler.ui.screens.detail

import androidx.compose.animation.core.snap
import com.noven.ncrawler.ui.components.rememberReducedMotion
import com.noven.ncrawler.ui.components.Motion
import androidx.compose.animation.core.RepeatMode
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.runtime.State
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Shape
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noven.ncrawler.data.scraper.ChapterLink
import com.noven.ncrawler.ui.theme.MontserratFamily
import androidx.compose.foundation.layout.offset
import kotlinx.coroutines.flow.first
import kotlin.math.PI
import kotlin.math.sin

// ── Chapter row ───────────────────────────────────────────────────────────────

@Composable
internal fun ChapterRow(chapter: ChapterLink, accent: Color, onClick: () -> Unit, isNew: Boolean = false) {
    Row(
        modifier          = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp, horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(accent.copy(alpha = 0.75f)),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text       = chapter.title.ifBlank { "Chapter ${chapter.num}" },
            color      = Color.White.copy(alpha = 0.90f),
            fontFamily = MontserratFamily,
            fontSize   = 16.sp,
            maxLines   = 1,
            overflow   = TextOverflow.Ellipsis,
            modifier   = Modifier.weight(1f),
        )
        if (isNew) {
            Spacer(Modifier.width(8.dp))
            NewPill()
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text       = "Ch.${chapter.num}",
            color      = accent.copy(alpha = 0.85f),
            fontFamily = MontserratFamily,
            fontSize   = 13.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
    HorizontalDivider(
        color     = Color.White.copy(alpha = 0.07f),
        thickness = 0.5.dp,
    )
}

// ── Reveal on scroll ──────────────────────────────────────────────────────────
// The item stays invisible until the user has actually scrolled it at least
// 72dp into the viewport — merely being composed (LazyColumn can compose an
// item just outside the viewport) doesn't reveal it. Then it fades + rises
// 24dp. Alpha/translation only, so the item's layout height never changes and
// the scroll position can't jump.

@Composable
internal fun RevealOnScroll(
    listState: LazyListState,
    itemIndex: Int,
    content: @Composable () -> Unit,
) {
    var shown by remember { mutableStateOf(false) }
    val thresholdPx = with(LocalDensity.current) { 72.dp.toPx() }

    LaunchedEffect(listState, itemIndex) {
        snapshotFlow {
            val info = listState.layoutInfo
            val item = info.visibleItemsInfo.firstOrNull { it.index == itemIndex }
            item != null && item.offset + thresholdPx < info.viewportEndOffset
        }.first { it }
        shown = true
    }

    val progress by animateFloatAsState(
        targetValue   = if (shown) 1f else 0f,
        animationSpec = tween(550, easing = FastOutSlowInEasing),
        label         = "revealProgress",
    )
    Box(
        modifier = Modifier.graphicsLayer {
            alpha        = progress
            translationY = (1f - progress) * 24.dp.toPx()
        },
    ) { content() }
}

// ── Scroll hint: a chevron inside a chevron (like the road arrows painted on
// walls), 3dp stroke, slightly transparent. The two chevrons light up one after
// the other, top then bottom, so the motion reads as "downwards". Fades in, and
// out again when [visible] goes false; once fully faded it stops drawing.

internal fun wave(p: Float): Float {
    val x = ((p % 1f) + 1f) % 1f
    return sin(PI.toFloat() * x)
}

@Composable
internal fun ScrollHint(visible: Boolean, modifier: Modifier = Modifier) {
    val fade by animateFloatAsState(
        targetValue   = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = if (visible) 350 else 600),
        label         = "scrollHintFade",
    )
    if (!visible && fade <= 0.01f) return   // fully faded away — nothing left to draw

    val transition = rememberInfiniteTransition(label = "scrollHintWave")
    val phase by transition.animateFloat(
        initialValue  = 0f,
        targetValue   = 1f,
        animationSpec = infiniteRepeatable(tween(1000, easing = LinearEasing)),
        label         = "scrollHintPhase",
    )

    Canvas(
        modifier = modifier
            .size(width = 36.dp, height = 44.dp)
            .graphicsLayer { alpha = fade },
    ) {
        val stroke = 3.dp.toPx()
        val w      = size.width
        val chevH  = 12.dp.toPx()
        val gap    = 11.dp.toPx()
        val drift  = phase * 5.dp.toPx()
        val maxA   = 0.7f                       // "slightly transparent"

        fun chevron(top: Float, alpha: Float) {
            val path = Path().apply {
                moveTo(stroke, top)
                lineTo(w / 2f, top + chevH)
                lineTo(w - stroke, top)
            }
            drawPath(
                path  = path,
                color = Color.White.copy(alpha = maxA * alpha),
                style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }

        chevron(top = 3.dp.toPx() + drift,        alpha = wave(phase))
        chevron(top = 3.dp.toPx() + gap + drift,  alpha = wave(phase - 0.28f))
    }
}

// ── Entrance stagger ──────────────────────────────────────────────────────────
// After the skeleton, the first screen builds top → bottom: the card, then genre,
// title, meta, rating and play, each fading up 14dp, 60ms apart (~0.8s in total,
// ease-out, no bounce). Alpha + translation only — layout never moves. Plays when
// the content first appears and again each time a refresh hands back from the
// skeleton. Reduced motion: everything is simply there.

private const val ENTRANCE_START_MS = 80
private const val ENTRANCE_STEP_MS  = 60
private const val ENTRANCE_MS       = 450

@Composable
internal fun staggerProgress(go: Boolean, index: Int): State<Float> {
    val reduced = rememberReducedMotion()
    return animateFloatAsState(
        targetValue   = if (go) 1f else 0f,
        // in: staggered ease-out. out (a refresh starting under the skeleton): instant.
        animationSpec = if (go && !reduced)
            tween(ENTRANCE_MS, delayMillis = ENTRANCE_START_MS + index * ENTRANCE_STEP_MS, easing = Motion.EaseOut)
        else snap(),
        label         = "stagger$index",
    )
}

internal fun Modifier.staggerIn(progress: State<Float>): Modifier = this.graphicsLayer {
    val p = progress.value
    alpha        = p
    translationY = (1f - p) * 14.dp.toPx()
}

// ── Skeleton ──────────────────────────────────────────────────────────────────
// One shared pulse for every block (a single transition, read in the DRAW phase,
// so the skeleton never recomposes per frame).

@Composable
internal fun rememberSkeletonAlpha(): State<Float> {
    val t = rememberInfiniteTransition(label = "skeleton")
    return t.animateFloat(
        initialValue  = 0.07f,
        targetValue   = 0.17f,
        animationSpec = infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label         = "skeletonAlpha",
    )
}

private fun Modifier.skelBlock(alpha: State<Float>, shape: Shape = RoundedCornerShape(8.dp)): Modifier =
    this.clip(shape).drawBehind { drawRect(Color.White.copy(alpha = alpha.value)) }

// Mirrors the real layout: top buttons row, the long image card with its details
// (genre, title, 3 meta columns, rating, play + label), and the chapter header.
@Composable
internal fun DetailSkeleton(bgTop: Color, modifier: Modifier = Modifier) {
    val a = rememberSkeletonAlpha()
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(bgTop, Color(0xFF000000)))),
    ) {
        // top row: back | download, bookmark, refresh
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Box(Modifier.size(48.dp).skelBlock(a, CircleShape))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(3) { Box(Modifier.size(48.dp).skelBlock(a, CircleShape)) }
            }
        }

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            val cardHeight = maxHeight * CARD_HEIGHT_FRACTION
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = CARD_TOP_PADDING, start = 16.dp, end = 16.dp)
                    .fillMaxWidth()
                    .height(cardHeight)
                    .skelBlock(a, RoundedCornerShape(24.dp)),
            ) {
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(Modifier.width(90.dp).height(14.dp).skelBlock(a))
                    Spacer(Modifier.height(12.dp))
                    Box(Modifier.fillMaxWidth(0.85f).height(30.dp).skelBlock(a))
                    Spacer(Modifier.height(8.dp))
                    Box(Modifier.fillMaxWidth(0.55f).height(30.dp).skelBlock(a))
                    Spacer(Modifier.height(24.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        repeat(3) {
                            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(Modifier.fillMaxWidth(0.8f).height(16.dp).skelBlock(a))
                                Spacer(Modifier.height(8.dp))
                                Box(Modifier.fillMaxWidth(0.5f).height(11.dp).skelBlock(a))
                            }
                        }
                    }
                    Spacer(Modifier.height(22.dp))
                    Box(Modifier.width(170.dp).height(20.dp).skelBlock(a))
                    Spacer(Modifier.height(26.dp))
                    Box(Modifier.size(76.dp).skelBlock(a, CircleShape))
                    Spacer(Modifier.height(10.dp))
                    Box(Modifier.width(110.dp).height(15.dp).skelBlock(a))
                }
            }
        }
    }
}

@Composable
internal fun ChapterRowSkeleton(a: State<Float>) {
    Row(
        modifier          = Modifier
            .fillMaxWidth()
            .padding(vertical = 17.dp, horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(7.dp).skelBlock(a, CircleShape))
        Spacer(Modifier.width(12.dp))
        Box(Modifier.weight(1f).height(14.dp).skelBlock(a))
        Spacer(Modifier.width(24.dp))
        Box(Modifier.width(38.dp).height(12.dp).skelBlock(a))
    }
}
