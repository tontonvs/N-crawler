package com.noven.ncrawler.ui.holiday

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.runtime.withFrameNanos
import com.noven.ncrawler.ui.components.rememberReducedMotion
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

// ═════════════════════════════════════════════════════════════════════════════
// The falling / rising particles that float over the whole app on a holiday:
//   Christmas  snowflakes (a few six-pointed) and the odd coloured bauble
//   Easter     drifting petals and painted eggs
//   Halloween  glowing embers rising like fireflies, faint purple mist
//   2nd June   tumbling confetti and a few rising balloons
//
// MOTION (Jhey primary — this is pure delight — with Jakub's restraint):
//  • Never touches input: one Canvas, no pointer handlers, touches pass straight through.
//  • Every particle's position is a pure function of (seed, time) — no per-frame
//    objects, no lists — so it costs a few dozen draw calls per frame.
//  • Capped at ~30 fps (one state write per ~33 ms) to spare the battery.
//  • The frame clock only runs while the screen is on and the app visible;
//    when the system asks for reduced motion nothing is drawn at all.
//  • [intensity] thins it out where calm matters (the reader uses 0.35).
// ═════════════════════════════════════════════════════════════════════════════
private const val BASE_COUNT = 34
private const val TWO_PI = 6.2831855f

@Composable
fun HolidayAmbient(
    holiday: Holiday,
    intensity: Float,
    modifier: Modifier = Modifier
) {
    val reduced = rememberReducedMotion()
    if (reduced) return

    val palette = holidayPalette(holiday)
    val count = (BASE_COUNT * intensity).roundToInt().coerceIn(8, 48)
    val alphaScale = (0.45f + 0.55f * intensity).coerceIn(0.3f, 1f)
    val seeds = remember(holiday, count) {
        val r = Random(holiday.ordinal * 7919 + count)
        FloatArray(count * 6) { r.nextFloat() }
    }

    var timeMs by remember { mutableLongStateOf(0L) }
    LaunchedEffect(holiday) {
        var last = 0L
        var acc = 0L
        while (true) {
            withFrameNanos { now ->
                if (last == 0L) last = now
                val dt = (now - last) / 1_000_000L
                if (dt >= 33L) {
                    acc += min(dt, 100L)
                    last = now
                    timeMs = acc
                }
            }
        }
    }

    Canvas(modifier.fillMaxSize()) {
        val t = timeMs / 1000f
        val d = density
        val w = size.width
        val h = size.height
        val margin = 28f * d
        val span = h + 2f * margin

        for (i in 0 until count) {
            val o = i * 6
            val s0 = seeds[o]
            val s1 = seeds[o + 1]
            val s2 = seeds[o + 2]
            val s3 = seeds[o + 3]
            val s4 = seeds[o + 4]
            val s5 = seeds[o + 5]
            // sideways drift shared by everything
            val x = s2 * w + sin(t * (0.4f + 0.6f * s3) + s4 * TWO_PI) * (10f + 16f * s5) * d

            when (holiday) {
                Holiday.CHRISTMAS -> {
                    val y = ((s0 * span + t * (24f + 46f * s1) * d) % span) - margin
                    if (i % 11 == 5) {
                        // a tiny bauble
                        val col = (if (i % 2 == 0) palette.c else palette.d).copy(alpha = 0.85f * alphaScale)
                        drawCircle(col, 3f * d, Offset(x, y))
                    } else if (i % 7 == 0) {
                        // six-pointed flake: three crossing lines, slowly turning
                        val r = (4.2f + 2f * s1) * d
                        val col = palette.a.copy(alpha = (0.6f + 0.35f * s5) * alphaScale)
                        val ang = t * (0.4f + s3) + s4 * TWO_PI
                        for (k in 0 until 3) {
                            val a = ang + k * 1.0471976f
                            val dx = cos(a) * r * 1.6f
                            val dy = sin(a) * r * 1.6f
                            drawLine(col, Offset(x - dx, y - dy), Offset(x + dx, y + dy), strokeWidth = 1.3f * d, cap = StrokeCap.Round)
                        }
                    } else {
                        val r = (1.4f + 2.2f * s1) * d
                        val col = (if (i % 3 == 0) palette.b else palette.a).copy(alpha = (0.55f + 0.4f * s5) * alphaScale)
                        drawCircle(col, r, Offset(x, y))
                    }
                }

                Holiday.EASTER -> {
                    val y = ((s0 * span + t * (18f + 30f * s1) * d) % span) - margin
                    val col = palette.pick(i).copy(alpha = (0.7f + 0.25f * s5) * alphaScale)
                    if (i % 9 == 0) {
                        // a painted egg, gently rocking
                        val ew = 5.5f * d
                        val eh = 7.5f * d
                        rotate(sin(t * 0.9f + s4 * TWO_PI) * 14f, Offset(x, y)) {
                            drawOval(col, Offset(x - ew, y - eh), Size(2f * ew, 2f * eh))
                            val stripe = palette.pick(i + 2).copy(alpha = 0.9f * alphaScale)
                            drawLine(stripe, Offset(x - ew * 0.78f, y + eh * 0.15f), Offset(x + ew * 0.78f, y + eh * 0.15f), strokeWidth = 2f * d, cap = StrokeCap.Round)
                            drawLine(stripe, Offset(x - ew * 0.55f, y - eh * 0.35f), Offset(x + ew * 0.55f, y - eh * 0.35f), strokeWidth = 1.6f * d, cap = StrokeCap.Round)
                        }
                    } else {
                        // a petal, tumbling
                        val deg = t * (30f + 40f * s3) + s4 * 360f
                        rotate(deg, Offset(x, y)) {
                            drawOval(col, Offset(x - 5f * d, y - 2.6f * d), Size(10f * d, 5.2f * d))
                        }
                    }
                }

                Holiday.HALLOWEEN -> {
                    // embers: rise from the bottom, flicker
                    val y = h + margin - ((s0 * span + t * (22f + 38f * s1) * d) % span)
                    val flick = 0.35f + 0.65f * abs(sin(t * (1.2f + 2f * s3) + s4 * TWO_PI))
                    val r = (1.3f + 2.2f * s1) * d
                    val col = if (i % 3 == 0) palette.d else palette.a
                    drawCircle(col, r * 2.8f, Offset(x, y), alpha = 0.10f * flick * alphaScale)
                    drawCircle(col, r, Offset(x, y), alpha = 0.85f * flick * alphaScale)
                    if (i % 10 == 0) {
                        drawCircle(palette.b, 9f * d, Offset(x, y), alpha = 0.07f * alphaScale)
                    }
                }

                Holiday.JUNE_2 -> {
                    if (i % 8 == 0) {
                        // a balloon, drifting up
                        val by = h + margin - ((s0 * span + t * (16f + 14f * s1) * d) % span)
                        val col = palette.pick(i / 8)
                        val sway = sin(t * 1.4f + s4 * TWO_PI) * 5f * d
                        drawLine(col.copy(alpha = 0.5f * alphaScale), Offset(x, by + 12f * d), Offset(x + sway, by + 34f * d), strokeWidth = 1f * d)
                        drawOval(col.copy(alpha = 0.9f * alphaScale), Offset(x - 9f * d, by - 12f * d), Size(18f * d, 24f * d))
                        drawCircle(Color.White.copy(alpha = 0.5f * alphaScale), 2.6f * d, Offset(x - 3.5f * d, by - 5f * d))
                    } else {
                        // confetti: a little rectangle tumbling and flipping
                        val y = ((s0 * span + t * (30f + 50f * s1) * d) % span) - margin
                        val col = palette.pick(i).copy(alpha = (0.75f + 0.2f * s5) * alphaScale)
                        val flip = abs(cos(t * (2f + 2f * s3) + s4 * TWO_PI))
                        val cw = (3.2f + 3f * s5) * d
                        val ch = 7f * d
                        rotate(t * (40f + 120f * s3) + s4 * 360f, Offset(x, y)) {
                            drawRect(col, Offset(x - cw * flip / 2f, y - ch / 2f), Size(cw * flip + 1f, ch))
                        }
                    }
                }
            }
        }
    }
}
