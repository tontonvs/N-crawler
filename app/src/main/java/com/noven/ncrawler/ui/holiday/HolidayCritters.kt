package com.noven.ncrawler.ui.holiday

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.noven.ncrawler.ui.components.rememberReducedMotion
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

// ═════════════════════════════════════════════════════════════════════════════
// The little animals. Every 22–45 seconds ONE of the holiday's critters shows up:
//   • walks / hops along the top of the floating nav bar, edge to edge,
//   • flies across the screen in a lazy wavy line, or
//   • peeks out from BEHIND the nav bar (a tab's worth of it), looks around and ducks back.
// Tap one and it pops a burst of sparkles, hops, and hurries off.
//
// MOTION (Jhey primary, Jakub for polish):
//  • Rare + optional: nothing for the first 5–9 s, then long gaps — fun, never in the way.
//  • Only the animal's own small box can be tapped; the rest of the layer ignores touches.
//  • Position is driven from ONE progress value in graphicsLayer (draw phase) so nothing is
//    re-measured while it moves; the artwork redraws from the same value.
//  • Reduced motion: the whole stage is off.
//  • The reader does not get a stage at all (see NavGraph) — reading stays calm.
// ═════════════════════════════════════════════════════════════════════════════

// The nav pill's top edge sits this far above the bottom inset: 16dp padding + 54dp pill.
private const val NAV_TOP_FROM_BOTTOM_DP = 70f
// Where PEEK critters rise from when there is no nav bar (Detail screen): just above the edge.
private const val EDGE_FROM_BOTTOM_DP = 10f
// The pill's geometry (NavBar.kt): 4 tabs of 44dp, 4dp apart, 6dp side padding, plus a 10dp gap and the 44dp search button.
private const val NAV_TOTAL_W_DP = 254f
private const val NAV_TAB_FIRST_CENTER_DP = 28f
private const val NAV_TAB_STEP_DP = 48f

private class CritterRun(
    val critter: Critter,
    val leftToRight: Boolean,
    val lane: Float,       // 0..1: how high a flyer cruises
    val tab: Int,          // 0..3: which nav tab a peeker hides behind
    val peekFrac: Float,   // 0..1: where along the width a peeker rises when there is no nav
    val durationMs: Float
)

private fun easeOutCubic(t: Float): Float {
    val u = 1f - t.coerceIn(0f, 1f)
    return 1f - u * u * u
}

@Composable
fun HolidayCritterStage(
    holiday: Holiday,
    navVisible: Boolean,
    modifier: Modifier = Modifier
) {
    val reduced = rememberReducedMotion()
    if (reduced) return

    val palette = holidayPalette(holiday)
    val density = LocalDensity.current
    val dpx = density.density
    val navInsetPx = WindowInsets.navigationBars.getBottom(density).toFloat()

    var run by remember { mutableStateOf<CritterRun?>(null) }
    var progress by remember { mutableFloatStateOf(0f) }
    var burst by remember { mutableFloatStateOf(1f) }     // 0..1 while the tap sparkle plays, 1 = idle
    var excited by remember { mutableStateOf(false) }

    BoxWithConstraints(modifier) {
        val wPx = constraints.maxWidth.toFloat()
        val hPx = constraints.maxHeight.toFloat()

        LaunchedEffect(holiday, navVisible, wPx) {
            if (wPx <= 0f) return@LaunchedEffect
            val roster = rosterFor(holiday)
            var previous: Critter? = null
            run = null
            delay(Random.nextLong(5_000L, 9_000L))
            while (true) {
                val critter = roster.filter { it != previous }.random()
                previous = critter
                val sPx = critter.sizeDp * dpx
                val duration = if (critter.behavior == CritterBehavior.PEEK) 3_800f
                               else (wPx + sPx) / (critter.speedDp * dpx) * 1000f
                excited = false
                burst = 1f
                progress = 0f
                run = CritterRun(
                    critter, Random.nextBoolean(), Random.nextFloat(),
                    Random.nextInt(4), Random.nextFloat(), duration
                )
                var elapsed = 0f
                var last = withFrameNanos { it }
                while (elapsed < duration) {
                    val now = withFrameNanos { it }
                    val dtMs = ((now - last) / 1_000_000f).coerceIn(0f, 50f)
                    last = now
                    elapsed += dtMs * (if (excited) 2.4f else 1f)
                    progress = (elapsed / duration).coerceAtMost(1f)
                    if (burst < 1f) burst = (burst + dtMs / 650f).coerceAtMost(1f)
                }
                run = null
                delay(Random.nextLong(22_000L, 45_000L))
            }
        }

        val r = run
        if (r != null) {
            val baseYpx = hPx - navInsetPx - (if (navVisible) NAV_TOP_FROM_BOTTOM_DP else EDGE_FROM_BOTTOM_DP) * dpx
            val sprite: @Composable () -> Unit = {
                CritterSprite(
                    run      = r,
                    palette  = palette,
                    wPx      = wPx,
                    hPx      = hPx,
                    baseYpx  = baseYpx,
                    navVisible = navVisible,
                    dpx      = dpx,
                    progress = { progress },
                    burst    = { burst },
                    onTap    = { if (!excited) { excited = true; burst = 0f } }
                )
            }
            if (r.critter.behavior == CritterBehavior.PEEK) {
                // Clipped at the nav bar's top edge, so the critter really is "behind" it.
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(with(density) { baseYpx.toDp() })
                        .clipToBounds()
                ) { sprite() }
            } else {
                Box(Modifier.fillMaxSize()) { sprite() }
            }
        }
    }
}

@Composable
private fun CritterSprite(
    run: CritterRun,
    palette: HolidayPalette,
    wPx: Float,
    hPx: Float,
    baseYpx: Float,
    navVisible: Boolean,
    dpx: Float,
    progress: () -> Float,
    burst: () -> Float,
    onTap: () -> Unit
) {
    val c = run.critter
    val sPx = c.sizeDp * dpx
    val interaction = remember { MutableInteractionSource() }

    Canvas(
        modifier = Modifier
            .size(c.sizeDp.dp)
            .graphicsLayer {
                val p = progress()
                val ph = p * run.durationMs / 1000f * c.gait * 2f * PI.toFloat()
                val span = wPx + sPx
                val travel = if (run.leftToRight) -sPx + p * span else wPx - p * span
                var x = travel
                var y = 0f
                var rotation = 0f
                when (c.behavior) {
                    CritterBehavior.RUN -> {
                        val bounce = if (c == Critter.BUNNY) abs(sin(ph * 0.5f)) else abs(sin(ph))
                        y = baseYpx - sPx + 0.03f * sPx - bounce * c.hop * sPx
                        rotation = sin(ph) * c.rock
                    }
                    CritterBehavior.FLY -> {
                        val a = p * 2f * PI.toFloat() * 2.3f + run.lane * 6.2831855f
                        y = hPx * (0.16f + 0.34f * run.lane) + sin(a) * 26f * dpx + sin(p * 31f) * 6f * dpx
                        rotation = cos(a) * c.rock
                    }
                    CritterBehavior.PEEK -> {
                        x = if (navVisible) {
                            (wPx - NAV_TOTAL_W_DP * dpx) / 2f + (NAV_TAB_FIRST_CENTER_DP + run.tab * NAV_TAB_STEP_DP) * dpx - sPx / 2f
                        } else {
                            (0.15f + 0.7f * run.peekFrac) * wPx - sPx / 2f
                        }
                        val rise = when {
                            p < 0.2f -> easeOutCubic(p / 0.2f)
                            p > 0.8f -> easeOutCubic((1f - p) / 0.2f)
                            else     -> 1f
                        }
                        y = baseYpx - sPx * 0.86f * rise + sin(ph * 0.5f) * 0.02f * sPx
                        rotation = sin(ph * 0.7f) * c.rock * rise
                    }
                }
                // tap: a little hop
                val b = burst()
                if (b < 1f) y -= sin(b * PI.toFloat()) * 0.3f * sPx
                translationX = x
                translationY = y
                rotationZ = rotation
                scaleX = if (c.behavior != CritterBehavior.PEEK && !run.leftToRight) -1f else 1f
            }
            .clickable(interactionSource = interaction, indication = null, onClick = onTap)
            .clearAndSetSemantics { }
    ) {
        val p = progress()
        val ph = p * run.durationMs / 1000f * c.gait * 2f * PI.toFloat()
        drawCritter(c, size.width / 100f, palette.rim, ph)

        val b = burst()
        if (b < 1f) {
            val e = 1f - (1f - b) * (1f - b)
            val cx = size.width / 2f
            val cy = size.height / 2f
            val dist = size.width * (0.45f + 0.75f * e)
            for (i in 0 until 10) {
                val a = i * 0.62831855f
                drawCircle(
                    color  = palette.pick(i),
                    radius = size.width * 0.055f * (1f - b * 0.6f),
                    center = Offset(cx + cos(a) * dist, cy + sin(a) * dist),
                    alpha  = 1f - b
                )
            }
        }
    }
}
