package com.noven.ncrawler.ui.holiday

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import com.noven.ncrawler.ui.components.BAR_CONTENT_HEIGHT
import com.noven.ncrawler.ui.components.BAR_FLARE
import com.noven.ncrawler.ui.components.rememberReducedMotion
import com.noven.ncrawler.ui.components.window
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin

// ═════════════════════════════════════════════════════════════════════════════
// Small, still decorations — two of them:
//
//  1. HolidayBarDecor — trim hung along the bottom edge of Browse's top bar:
//       Christmas  a wire of twinkling bulbs        Halloween  a cobweb in the corner + a dangling spider
//       Easter / 2nd June  bunting flags in the holiday colours
//     It is drawn only while the bar is OPEN and fades out as the bar morphs into
//     the pill (the first 22% of the morph), so the pill stays exactly as designed.
//
//  2. HolidayHat — the little hat the Home icon wears in the floating nav.
//
// MOTION: only the Christmas bulbs move (they blink at ~7 fps, which is what makes
// fairy lights look like fairy lights); with reduced motion they sit steady.
// ═════════════════════════════════════════════════════════════════════════════

private val BULBS = arrayOf(Color(0xFFEF5350), Color(0xFFFFCA28), Color(0xFF66BB6A), Color(0xFF42A5F5))
private const val DEG = 0.017453292f

@Composable
fun HolidayBarDecor(
    holiday: Holiday,
    morph: () -> Float,
    modifier: Modifier = Modifier
) {
    val localDensity = LocalDensity.current
    val statusDp = with(localDensity) { WindowInsets.statusBars.getTop(localDensity).toDp() }
    val palette = holidayPalette(holiday)
    val reduced = rememberReducedMotion()

    var twinkle by remember { mutableIntStateOf(0) }
    if (holiday == Holiday.CHRISTMAS && !reduced) {
        LaunchedEffect(Unit) {
            while (true) {
                delay(140L)
                twinkle++
            }
        }
    }

    Canvas(modifier.fillMaxWidth().height(statusDp + BAR_CONTENT_HEIGHT + BAR_FLARE)) {
        val open = 1f - window(morph(), 0f, 0.22f)
        if (open <= 0.01f) return@Canvas
        val d = this.density   // DrawScope's Float density (px per dp)
        // the bar's flat bottom edge
        val y0 = (statusDp + BAR_CONTENT_HEIGHT).toPx() - 2f * d
        when (holiday) {
            Holiday.CHRISTMAS -> drawGarland(y0, d, palette.d, twinkle, open)
            Holiday.EASTER, Holiday.JUNE_2 -> drawBunting(y0, d, palette, open)
            Holiday.HALLOWEEN -> {
                drawCobweb(statusDp.toPx(), d, palette.b, open)
                drawSpider(size.width * 0.60f, y0, d, palette, open)
            }
        }
    }
}

// A sagging wire with bulbs hung along it.
private fun DrawScope.drawGarland(y0: Float, d: Float, wireColor: Color, twinkle: Int, open: Float) {
    val segs = 6
    val segW = size.width / segs
    val sag = 12f * d
    val wire = Path()
    wire.moveTo(0f, y0)
    for (s in 0 until segs) {
        wire.quadraticBezierTo(s * segW + segW / 2f, y0 + 2f * sag, (s + 1) * segW, y0)
    }
    drawPath(wire, wireColor.copy(alpha = 0.75f * open), style = Stroke(1.4f * d))
    var idx = 0
    for (s in 0 until segs) {
        for (k in 1..4) {
            val t = k / 5f
            val bx = s * segW + segW * t
            val by = y0 + 4f * sag * t * (1f - t)
            val on = (twinkle + idx * 3) % 6 < 4
            val col = BULBS[idx % BULBS.size]
            if (on) drawCircle(col, 7f * d, Offset(bx, by + 5.5f * d), alpha = 0.18f * open)
            drawLine(wireColor.copy(alpha = 0.8f * open), Offset(bx, by), Offset(bx, by + 2.5f * d), strokeWidth = 1.4f * d)
            drawCircle(col, 3.4f * d, Offset(bx, by + 5.5f * d), alpha = (if (on) 1f else 0.45f) * open)
            idx++
        }
    }
}

// Pennant flags on a sagging string.
private fun DrawScope.drawBunting(y0: Float, d: Float, palette: HolidayPalette, open: Float) {
    val segs = 5
    val segW = size.width / segs
    val sag = 10f * d
    val wire = Path()
    wire.moveTo(0f, y0)
    for (s in 0 until segs) {
        wire.quadraticBezierTo(s * segW + segW / 2f, y0 + 2f * sag, (s + 1) * segW, y0)
    }
    drawPath(wire, palette.ink.copy(alpha = 0.55f * open), style = Stroke(1.2f * d))
    var idx = 0
    for (s in 0 until segs) {
        for (k in 0 until 4) {
            val t = (k + 0.5f) / 4f
            val fx = s * segW + segW * t
            val fy = y0 + 4f * sag * t * (1f - t)
            val flag = Path()
            flag.moveTo(fx - 9f * d, fy)
            flag.lineTo(fx + 9f * d, fy)
            flag.lineTo(fx, fy + 18f * d)
            flag.close()
            drawPath(flag, palette.pick(idx).copy(alpha = 0.92f * open))
            idx++
        }
    }
}

// A corner cobweb: five spokes and three sagging rings.
private fun DrawScope.drawCobweb(top: Float, d: Float, color: Color, open: Float) {
    val len = 46f * d
    val col = color.copy(alpha = 0.6f * open)
    val n = 5
    for (k in 0 until n) {
        val a = k * 22.5f * DEG
        drawLine(col, Offset(0f, top), Offset(cos(a) * len, top + sin(a) * len), strokeWidth = 1f * d)
    }
    val rings = floatArrayOf(0.35f, 0.62f, 0.9f)
    for (rr in rings) {
        val r = len * rr
        val path = Path()
        for (k in 0 until n) {
            val a = k * 22.5f * DEG
            val px = cos(a) * r
            val py = top + sin(a) * r
            if (k == 0) {
                path.moveTo(px, py)
            } else {
                val am = (k - 0.5f) * 22.5f * DEG
                path.quadraticBezierTo(cos(am) * r * 0.84f, top + sin(am) * r * 0.84f, px, py)
            }
        }
        drawPath(path, col, style = Stroke(0.9f * d))
    }
}

// A spider on a thread, dangling just below the bar's edge.
private fun DrawScope.drawSpider(x: Float, y0: Float, d: Float, palette: HolidayPalette, open: Float) {
    val ink = Color(0xFF241E36)
    val cy = y0 + 20f * d
    drawLine(ink.copy(alpha = 0.5f * open), Offset(x, y0 - 20f * d), Offset(x, cy), strokeWidth = 1f * d)
    for (side in intArrayOf(-1, 1)) {
        for (k in 0..3) {
            drawLine(
                ink.copy(alpha = open),
                Offset(x + side * 3f * d, cy),
                Offset(x + side * (9f + k * 0.8f) * d, cy + (k - 1.5f) * 4f * d),
                strokeWidth = 1.2f * d
            )
        }
    }
    drawCircle(palette.rim.copy(alpha = palette.rim.alpha * open), 5.8f * d, Offset(x, cy))
    drawCircle(ink.copy(alpha = open), 5f * d, Offset(x, cy))
    drawCircle(Color(0xFFFF9800).copy(alpha = open), 1.1f * d, Offset(x - 1.6f * d, cy - 1f * d))
    drawCircle(Color(0xFFFF9800).copy(alpha = open), 1.1f * d, Offset(x + 1.6f * d, cy - 1f * d))
}

/** The hat on the Home icon. [rim] is the outline colour — pick one that shows against the nav pill. */
@Composable
fun HolidayHat(holiday: Holiday, rim: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) { drawHat(holiday, size.width / 100f, rim) }
}
