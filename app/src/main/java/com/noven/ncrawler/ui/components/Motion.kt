package com.noven.ncrawler.ui.components

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.delay

// ─────────────────────────────────────────────────────────────────────────────
// Motion kit — ONE place for every animation rule in the app.
//
// Weighting (mobile reading app): Jakub primary (polish, 200–500ms enters),
// Emil secondary (fast + restrained on things you tap all day), Jhey only for
// the rare delighters that already exist (hero drift, audio overlay).
//
// Rules baked in here:
//  • Only transform / alpha are animated (graphicsLayer, read in the draw
//    phase) — no layout properties, so nothing re-measures mid-animation.
//  • One custom ease-out curve; no bare `ease`, no bounce on frequent taps.
//  • Everything honours the system "Remove animations" switch (animator
//    scale = 0) — same signal the reader + hero already use.
//  • Lists animate once per item, never again on scroll-back or return.
// ─────────────────────────────────────────────────────────────────────────────

object Motion {
    /** Strong ease-out: fast start, long soft landing. For things arriving. */
    val EaseOut = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)

    const val PRESS_MS = 90
    const val QUICK_MS = 140
    const val BASE_MS  = 240
    const val ENTER_MS = 340
    const val SCREEN_MS = 300
}

/** True when the user turned system animations off. */
@Composable
fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        Settings.Global.getFloat(
            context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f
        ) == 0f
    }
}

// ── 1. Smooth stack ──────────────────────────────────────────────────────────
// Fade + 14dp rise, each item a beat after the one before it. Only the first
// [maxAnimated] items animate (the first screenful) and only ONCE per item:
// the "already played" flag is saved with the list item, so scrolling back or
// returning to the screen never replays it, and items that scroll in later
// simply appear (no lag while flicking through a long list).
fun Modifier.staggerIn(
    index: Int,
    distance: Dp = 14.dp,
    stepMs: Int = 40,
    maxAnimated: Int = 10
): Modifier = composed {
    val reduced = rememberReducedMotion()
    var played by rememberSaveable { mutableStateOf(false) }
    val skip = played || reduced || index >= maxAnimated
    val progress = remember { Animatable(if (skip) 1f else 0f) }

    LaunchedEffect(Unit) {
        if (!skip) {
            delay(index.coerceAtLeast(0) * stepMs.toLong())
            progress.animateTo(1f, tween(Motion.ENTER_MS, easing = Motion.EaseOut))
        }
        played = true
    }

    val distancePx = with(LocalDensity.current) { distance.toPx() }
    graphicsLayer {
        val p = progress.value
        alpha = p
        translationY = (1f - p) * distancePx
    }
}

// ── 2. Press feedback ────────────────────────────────────────────────────────
// Instant press-in (90ms), soft release. No bounce — you tap these all day.
// The scale is read in the draw phase, so a press never recomposes the card.
// pressScale() only animates (for controls that need their own click handling,
// e.g. long-press); pressable() = pressScale() + click, for everything else.
fun Modifier.pressScale(
    source: InteractionSource,
    pressedScale: Float = 0.97f
): Modifier = composed {
    val pressed by source.collectIsPressedAsState()
    val scale   = remember { Animatable(1f) }

    LaunchedEffect(pressed) {
        val spec: AnimationSpec<Float> =
            if (pressed) tween(Motion.PRESS_MS, easing = Motion.EaseOut)
            else spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMedium)
        scale.animateTo(if (pressed) pressedScale else 1f, spec)
    }

    graphicsLayer {
        scaleX = scale.value
        scaleY = scale.value
    }
}

fun Modifier.pressable(
    onClick: () -> Unit,
    pressedScale: Float = 0.97f,
    enabled: Boolean = true,
    role: Role? = Role.Button
): Modifier = composed {
    val source = remember { MutableInteractionSource() }
    this
        .pressScale(source, pressedScale)
        .clickable(
            interactionSource = source,
            indication        = null,
            enabled           = enabled,
            role              = role,
            onClick           = onClick
        )
}

// ── 3. Errors + warnings: sharp, fast, shaky ─────────────────────────────────
// Five hard reversals that decay (−1, +.85, −.6, +.35, 0), linear so each one
// snaps, ~260ms total. Re-fires whenever [trigger] changes. [onEnter] = false
// skips the run that happens when the item first appears (use it on list rows
// so scrolling an already-failed row into view doesn't shake it).
fun Modifier.errorShake(
    trigger: Any? = Unit,
    amplitude: Dp = 9.dp,
    onEnter: Boolean = true
): Modifier = composed {
    val reduced = rememberReducedMotion()
    val x       = remember { Animatable(0f) }
    val ampPx   = with(LocalDensity.current) { amplitude.toPx() }
    val first   = remember { booleanArrayOf(true) }   // plain holder: no recomposition

    LaunchedEffect(trigger) {
        val skipThisRun = first[0] && !onEnter
        first[0] = false
        if (reduced || skipThisRun) return@LaunchedEffect
        for (f in floatArrayOf(-1f, 0.85f, -0.6f, 0.35f, 0f)) {
            x.animateTo(f * ampPx, tween(52, easing = LinearEasing))
        }
    }
    graphicsLayer { translationX = x.value }
}

// ── 4. Skeleton shimmer ──────────────────────────────────────────────────────
// ONE infinite transition per skeleton screen (ShimmerScope); every placeholder
// reads it in the draw phase. Frozen when animations are off.
val LocalShimmerPhase = compositionLocalOf<State<Float>?> { null }

@Composable
fun ShimmerScope(content: @Composable () -> Unit) {
    val reduced = rememberReducedMotion()
    val phase: State<Float> =
        if (reduced) {
            remember { mutableStateOf(0f) }
        } else {
            rememberInfiniteTransition(label = "shimmer").animateFloat(
                initialValue  = 0f,
                targetValue   = 1f,
                animationSpec = infiniteRepeatable(tween(1300, easing = LinearEasing)),
                label         = "shimmerPhase"
            )
        }
    CompositionLocalProvider(LocalShimmerPhase provides phase) { content() }
}

/** Placeholder fill with a soft light sweep. Apply AFTER clip(). */
fun Modifier.skeleton(base: Color): Modifier = composed {
    val phase = LocalShimmerPhase.current
    val dark  = isSystemInDarkTheme()
    val sheen = if (dark) Color.White.copy(alpha = 0.07f) else Color.White.copy(alpha = 0.6f)

    this
        .background(base)
        .drawWithContent {
            drawContent()
            val p = phase?.value ?: return@drawWithContent
            val w = size.width
            val start = (p * 2f - 1f) * w
            drawRect(
                Brush.horizontalGradient(
                    colors = listOf(Color.Transparent, sheen, Color.Transparent),
                    startX = start,
                    endX   = start + w
                )
            )
        }
}

// ── 5. Cover image that fades in instead of popping ──────────────────────────
@Composable
fun CoverImage(
    url: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val request = remember(url) {
        ImageRequest.Builder(context)
            .data(url)
            .crossfade(220)
            .build()
    }
    AsyncImage(
        model              = request,
        contentDescription = contentDescription,
        contentScale       = ContentScale.Crop,
        modifier           = modifier
    )
}

/** Muted tint for skeleton fills — one definition so every screen matches. */
@Composable
fun skeletonBase(): Color = MaterialTheme.colorScheme.surfaceVariant
