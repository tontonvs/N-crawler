package com.noven.ncrawler.ui.holiday

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noven.ncrawler.ui.components.rememberReducedMotion
import com.noven.ncrawler.ui.components.window
import com.noven.ncrawler.ui.theme.MontserratFamily
import kotlin.math.cos
import kotlin.math.sin

// ═════════════════════════════════════════════════════════════════════════════
// The greeting when the app opens on a holiday (once a day in Automatic mode, and
// every time you pick a holiday in the test picker).
//
// MOTION (Jakub — it should feel finished, not loud — with a little Jhey sparkle):
//  • 2.1 s in total: card scales 0.86 → 1 and fades in over the first 380 ms,
//    holds, then fades out over the last 420 ms (leaving is quicker and quieter).
//  • A soft glow in the holiday's colour behind it and a ring of dots that
//    bursts outward once.
//  • It never blocks touches — you can use the app straight away.
//  • Reduced motion: no scale, no burst — a 1.5 s fade only.
// ═════════════════════════════════════════════════════════════════════════════
private const val TWO_PI = 6.2831855f

private fun easeOut(t: Float): Float {
    val u = 1f - t.coerceIn(0f, 1f)
    return 1f - u * u * u
}

private fun envelope(t: Float): Float =
    easeOut(window(t, 0f, 0.18f)) * (1f - window(t, 0.80f, 1f))

@Composable
fun HolidaySplash(
    holiday: Holiday,
    replayKey: Int,
    onDone: () -> Unit
) {
    val palette = holidayPalette(holiday)
    val reduced = rememberReducedMotion()
    val t = remember { Animatable(0f) }
    val onDoneNow by rememberUpdatedState(onDone)

    LaunchedEffect(holiday, replayKey, reduced) {
        t.snapTo(0f)
        t.animateTo(1f, tween(if (reduced) 1_500 else 2_100, easing = LinearEasing))
        onDoneNow()
    }

    val shape = RoundedCornerShape(26.dp)
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val a = envelope(t.value)
            drawRect(
                Brush.radialGradient(
                    colors = listOf(palette.glow.copy(alpha = 0.30f * a), Color.Transparent),
                    center = center,
                    radius = size.minDimension * 0.75f
                )
            )
            if (!reduced) {
                val p = easeOut(window(t.value, 0.05f, 0.7f))
                val dist = size.minDimension * (0.12f + 0.38f * p)
                for (i in 0 until 18) {
                    val ang = i * (TWO_PI / 18f)
                    drawCircle(
                        color  = palette.pick(i),
                        radius = 4.5f * density * (1f - 0.5f * p),
                        center = Offset(center.x + cos(ang) * dist, center.y + sin(ang) * dist),
                        alpha  = (1f - p) * a
                    )
                }
            }
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .graphicsLayer {
                    alpha = envelope(t.value)
                    val s = if (reduced) 1f
                            else 0.86f + 0.14f * easeOut(window(t.value, 0f, 0.18f)) + 0.04f * window(t.value, 0.8f, 1f)
                    scaleX = s
                    scaleY = s
                }
                .clip(shape)
                .background(palette.card)
                .border(1.dp, palette.ink.copy(alpha = 0.35f), shape)
                .padding(horizontal = 32.dp, vertical = 20.dp)
        ) {
            Text(holiday.emoji, fontSize = 46.sp, textAlign = TextAlign.Center)
            Spacer(Modifier.height(4.dp))
            Text(
                holiday.greeting,
                fontFamily = MontserratFamily,
                fontWeight = FontWeight.ExtraBold,
                fontSize   = 22.sp,
                color      = palette.ink,
                textAlign  = TextAlign.Center
            )
            Text(
                holiday.tagline,
                fontFamily = MontserratFamily,
                fontSize   = 13.sp,
                color      = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign  = TextAlign.Center
            )
        }
    }
}
