package com.noven.ncrawler.ui.screens.reader

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noven.ncrawler.R
import com.noven.ncrawler.ui.components.SolarIcons
import com.noven.ncrawler.ui.components.rememberReducedMotion
import com.noven.ncrawler.ui.theme.MontserratFamily
import kotlin.math.floor
import kotlin.math.ln

// ─────────────────────────────────────────────────────────────────────────────
// Reader auto-scroll — the pieces you SEE. The scrolling engine itself lives in
// ReaderScreen (it needs the chapter's ScrollState).
//
// Motion weighting: Jakub primary (shipped consumer app), Emil secondary (the
// speed bar is touched constantly, so it only fades), Jhey for the one
// delighter (the hand). Everything honours system "Remove animations".
// ─────────────────────────────────────────────────────────────────────────────

/** Speed → a 1..10 label on a log scale (speed is multiplicative, so is the swipe). */
internal fun autoSpeedLevel(speed: Float, min: Float, max: Float): Int {
    val t = (ln(speed / min) / ln(max / min)).coerceIn(0f, 1f)
    return 1 + (t * 9f).toInt().coerceIn(0, 9)
}

// The hand's up/down bob, taken frame-by-frame from scroll.gif (36 frames, 1.48s):
// vertical offset of the hand in the GIF's own 640px canvas, relative to its rest
// pose. Arrows stay still, exactly like the GIF. The GIF has an opaque white
// background, so the hand + arrows ship as transparent PNGs (drawable-nodpi) and
// the hand is tinted with the reader's text colour to work on any page colour.
private val HAND_OFFSETS = floatArrayOf(
    0f, 5f, 18f, 35f, 51f, 61f, 65f, 65f, 61f, 57f, 53f, 51f,
    47f, 38f, 26f, 13f, 4f, 0f, 3f, 7f, 10f, 13f, 13f, 11f,
    2f, -12f, -28f, -40f, -45f, -39f, -29f, -19f, -10f, -3f, 0f, 0f
)
private const val HAND_LOOP_MS = 1480
private const val HAND_CANVAS  = 640f

@Composable
internal fun ScrollHintHand(
    ink: Color,
    modifier: Modifier = Modifier,
    size: Dp = 112.dp
) {
    val reduced = rememberReducedMotion()
    val sizePx  = with(LocalDensity.current) { size.toPx() }
    val phase   = if (reduced) null else
        rememberInfiniteTransition(label = "hintHand").animateFloat(
            initialValue  = 0f,
            targetValue   = 1f,
            animationSpec = infiniteRepeatable(tween(HAND_LOOP_MS, easing = LinearEasing)),
            label         = "hintHandPhase"
        )

    Box(modifier.size(size)) {
        Image(
            painter            = painterResource(R.drawable.scroll_hint_arrows),
            contentDescription = null,
            contentScale       = ContentScale.Fit,
            modifier           = Modifier.matchParentSize()
        )
        Image(
            painter            = painterResource(R.drawable.scroll_hint_hand),
            contentDescription = null,
            contentScale       = ContentScale.Fit,
            colorFilter        = ColorFilter.tint(ink),
            modifier           = Modifier
                .matchParentSize()
                .graphicsLayer {
                    // Read in the draw phase — the loop never recomposes anything.
                    val p = phase?.value ?: 0f
                    val f = p * HAND_OFFSETS.size
                    val i = floor(f).toInt().coerceIn(0, HAND_OFFSETS.lastIndex)
                    val j = (i + 1) % HAND_OFFSETS.size
                    val t = f - i
                    val offset = HAND_OFFSETS[i] + (HAND_OFFSETS[j] - HAND_OFFSETS[i]) * t
                    translationY = offset / HAND_CANVAS * sizePx
                }
        )
    }
}

// The notice shown the moment auto-scroll starts: the hand + what it means. Not
// interactive on purpose — touches pass through to the gesture layer below, so
// the very first swipe both sets the speed and dismisses it.
@Composable
internal fun AutoScrollHint(
    fg: Color,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .readerGlass(fg, RoundedCornerShape(28.dp), strength = 0.75f, classic = 0.16f)
            .padding(start = 12.dp, end = 22.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ScrollHintHand(ink = fg, size = 96.dp)
        Spacer(Modifier.width(8.dp))
        Column {
            Text(
                "Set the speed",
                fontFamily = MontserratFamily,
                fontWeight = FontWeight.ExtraBold,
                fontSize   = 15.sp,
                color      = fg
            )
            Spacer(Modifier.height(6.dp))
            HintLine(SolarIcons.ArrowUp,   "Swipe up · faster", fg)
            Spacer(Modifier.height(3.dp))
            HintLine(SolarIcons.ArrowDown, "Swipe down · slower", fg)
            Spacer(Modifier.height(3.dp))
            Text(
                "Tap to stop",
                fontFamily = MontserratFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize   = 12.sp,
                color      = fg.copy(alpha = 0.6f)
            )
        }
    }
}

@Composable
private fun HintLine(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, fg: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = fg.copy(alpha = 0.85f), modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(6.dp))
        Text(
            text,
            fontFamily = MontserratFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize   = 12.sp,
            color      = fg.copy(alpha = 0.85f)
        )
    }
}

// Small bar shown while auto-scroll runs: current speed, the auto-pilot switch,
// and a stop button. (Tapping anywhere else on the page stops it too.)
@Composable
internal fun AutoScrollHud(
    level: Int,
    autoPilot: Boolean,
    fg: Color,
    accent: Color,
    onToggleAutoPilot: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier
) {
    // A soft pop whenever the level changes — feedback for the swipe, no more.
    val pop = remember { Animatable(1f) }
    LaunchedEffect(level) {
        pop.snapTo(0.4f)
        pop.animateTo(1f, spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium))
    }
    Row(
        modifier = modifier
            .readerGlass(fg, RoundedCornerShape(50), strength = 0.6f, classic = 0.16f)
            .padding(start = 16.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.graphicsLayer {
                val s = 0.94f + 0.06f * pop.value
                scaleX = s; scaleY = s
            }
        ) {
            Icon(SolarIcons.ArrowDown, null, tint = fg.copy(alpha = 0.85f), modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                "Speed $level",
                fontFamily = MontserratFamily,
                fontWeight = FontWeight.Bold,
                fontSize   = 13.sp,
                color      = fg
            )
        }

        AutoPilotChip(on = autoPilot, fg = fg, accent = accent, onClick = onToggleAutoPilot)

        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(fg.copy(alpha = 0.14f))
                .clickable(onClickLabel = "Stop auto scroll", onClick = onStop),
            contentAlignment = Alignment.Center
        ) {
            Icon(SolarIcons.PauseBold, "Stop auto scroll", tint = fg, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
internal fun AutoPilotChip(
    on: Boolean,
    fg: Color,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(if (on) accent else fg.copy(alpha = 0.12f))
            .clickable(onClickLabel = "Toggle auto-pilot", onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(if (on) Color.White else fg.copy(alpha = 0.35f))
        )
        Spacer(Modifier.width(7.dp))
        Text(
            "Auto-pilot",
            fontFamily = MontserratFamily,
            fontWeight = FontWeight.Bold,
            fontSize   = 12.sp,
            color      = if (on) Color.White else fg.copy(alpha = 0.8f)
        )
    }
}

// The end-of-chapter step. Auto-scroll stops at the last line and asks:
//   • auto-pilot OFF → a button; nothing opens until it is tapped (the confirm).
//   • auto-pilot ON  → the same bar counts down and opens the next chapter by
//     itself; Cancel (or a tap) stops auto-scroll instead.
@Composable
internal fun AutoScrollEndBar(
    nextNum: Int,
    autoPilot: Boolean,
    countdown: () -> Float,          // 0..1, read in the draw phase
    fg: Color,
    accent: Color,
    onConfirm: () -> Unit,
    onToggleAutoPilot: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier
) {
    val currentConfirm by rememberUpdatedState(onConfirm)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .readerGlass(fg, RoundedCornerShape(26.dp), strength = 0.7f, classic = 0.16f)
            .padding(16.dp)
    ) {
        Row(
            modifier              = Modifier.fillMaxWidth(),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "END OF CHAPTER",
                    fontFamily    = MontserratFamily,
                    fontWeight    = FontWeight.ExtraBold,
                    fontSize      = 10.sp,
                    letterSpacing = 1.sp,
                    color         = fg.copy(alpha = 0.6f)
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "Next · Ch. $nextNum",
                    fontFamily = MontserratFamily,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize   = 16.sp,
                    color      = fg
                )
            }
            AutoPilotChip(on = autoPilot, fg = fg, accent = accent, onClick = onToggleAutoPilot)
        }

        Spacer(Modifier.height(14.dp))

        if (autoPilot) {
            // Countdown line: fills left → right, then the chapter opens.
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .clip(RoundedCornerShape(50))
                    .background(fg.copy(alpha = 0.16f))
            ) {
                Box(
                    Modifier
                        .fillMaxHeight()
                        .fillMaxWidth()
                        .graphicsLayer {
                            // scaleX from the left edge — no layout, read in draw
                            scaleX = countdown().coerceIn(0.02f, 1f)
                            transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0.5f)
                        }
                        .clip(RoundedCornerShape(50))
                        .background(accent)
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(
                modifier              = Modifier.fillMaxWidth(),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "Opening next chapter…",
                    fontFamily = MontserratFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize   = 13.sp,
                    color      = fg.copy(alpha = 0.8f)
                )
                Text(
                    "Cancel",
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .clickable(onClick = onStop)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    fontFamily = MontserratFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize   = 13.sp,
                    color      = fg
                )
            }
        } else {
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Text(
                    "Stop",
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(fg.copy(alpha = 0.12f))
                        .clickable(onClick = onStop)
                        .padding(horizontal = 18.dp, vertical = 13.dp),
                    fontFamily = MontserratFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize   = 14.sp,
                    color      = fg
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(50))
                        .background(accent)
                        .clickable { currentConfirm() }
                        .padding(vertical = 13.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "Open next chapter",
                        fontFamily = MontserratFamily,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize   = 14.sp,
                        color      = Color.White,
                        textAlign  = TextAlign.Center
                    )
                }
            }
        }
    }
}
