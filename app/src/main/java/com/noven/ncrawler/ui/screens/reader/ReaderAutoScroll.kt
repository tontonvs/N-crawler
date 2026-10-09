package com.noven.ncrawler.ui.screens.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
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
// control bar is touched constantly, so it only fades), Jhey for the one
// delighter (the hand). Everything honours system "Remove animations".
//
// The control bar is ONE row at all times. At the end of a chapter it does not
// grow a second card over the text — it morphs in place into
// [Auto-pilot] [Ch. N →] [✕], so nothing extra ever covers the page.
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
                "Tap to pause",
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

// Auto-pilot explanations (kept to one short line each).
internal const val AUTO_PILOT_TIP_INTRO = "Auto-pilot opens the next chapter by itself when this one ends."
internal const val AUTO_PILOT_TIP_ON    = "Auto-pilot on · the next chapter opens by itself."
internal const val AUTO_PILOT_TIP_OFF   = "Auto-pilot off · you'll be asked before the next chapter."

// The one control bar that lives at the bottom for the whole auto-scroll session.
//   normal   : [ ▼ speed ] [ Auto-pilot ] [ ⏸ / ▶ ] [ ✕ ]
//   chapter end (endMode): [ Auto-pilot ] [ Ch. N → ] [ ✕ ]
//     – auto-pilot ON : the Ch. N button fills left → right (countdown), then opens
//     – auto-pilot OFF: it waits; tapping it opens the next chapter
// Pause keeps the bar on screen (the play/pause button just swaps). ✕ ends the session.
// `tip` non-null → a speech bubble above the bar points at the auto-pilot chip.
@Composable
internal fun AutoScrollHud(
    level: Int,
    paused: Boolean,
    autoPilot: Boolean,
    endMode: Boolean,
    nextNum: Int,
    countdown: () -> Float,          // 0..1, read in the draw phase
    fg: Color,
    bg: Color,
    accent: Color,
    tip: String?,
    onTogglePause: () -> Unit,
    onToggleAutoPilot: () -> Unit,
    onOpenNext: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier
) {
    // A soft pop whenever the level changes — feedback for the swipe, no more.
    val pop = remember { Animatable(1f) }
    LaunchedEffect(level) {
        pop.snapTo(0.4f)
        pop.animateTo(1f, spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium))
    }
    // Window-space x of the chip's centre: where the bubble's tail points.
    var chipCenterX by remember { mutableStateOf(0f) }

    Column(
        modifier            = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AutoPilotTip(text = tip, fg = fg, bg = bg, chipCenterX = { chipCenterX })

        Crossfade(targetState = endMode, animationSpec = tween(160), label = "hudMode") { end ->
            Row(
                modifier = Modifier
                    .readerGlass(fg, RoundedCornerShape(50), strength = 0.6f, classic = 0.16f)
                    .padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (!end) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.graphicsLayer {
                            val s = 0.94f + 0.06f * pop.value
                            scaleX = s; scaleY = s
                        }
                    ) {
                        Icon(SolarIcons.ArrowDown, null, tint = fg.copy(alpha = 0.85f), modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(
                            "$level",
                            fontFamily = MontserratFamily,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize   = 15.sp,
                            color      = fg
                        )
                    }
                }

                AutoPilotChip(
                    on       = autoPilot,
                    fg       = fg,
                    accent   = accent,
                    onClick  = onToggleAutoPilot,
                    modifier = Modifier.onGloballyPositioned {
                        chipCenterX = it.positionInWindow().x + it.size.width / 2f
                    }
                )

                if (!end) {
                    HudCircleButton(
                        fg           = fg,
                        label        = if (paused) "Resume auto scroll" else "Pause auto scroll",
                        onClick      = onTogglePause
                    ) {
                        Icon(
                            if (paused) SolarIcons.PlayBold else SolarIcons.PauseBold,
                            contentDescription = null,
                            tint     = fg,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                } else {
                    NextChapterButton(
                        nextNum   = nextNum,
                        autoPilot = autoPilot,
                        countdown = countdown,
                        accent    = accent,
                        onClick   = onOpenNext
                    )
                }

                HudCircleButton(fg = fg, label = "Stop auto scroll", onClick = onStop) {
                    Icon(SolarIcons.Close, contentDescription = null, tint = fg, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

@Composable
private fun HudCircleButton(
    fg: Color,
    label: String,
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(fg.copy(alpha = 0.14f))
            .clickable(onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center
    ) { content() }
}

// "Ch. N →". With auto-pilot the button is its own countdown: a brighter fill
// grows left → right over a dim base, and the chapter opens when it is full.
@Composable
private fun NextChapterButton(
    nextNum: Int,
    autoPilot: Boolean,
    countdown: () -> Float,
    accent: Color,
    onClick: () -> Unit
) {
    // Without auto-pilot nothing opens the next chapter by itself, so the button
    // breathes (soft glow + slight swell) to say "your move". With auto-pilot the
    // countdown fill is the signal instead, so it stays still.
    val blink = rememberInfiniteTransition(label = "nextBlink")
    val pulse by blink.animateFloat(
        initialValue  = 0f,
        targetValue   = 1f,
        animationSpec = infiniteRepeatable(tween(750, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label         = "nextPulse"
    )
    Box(
        modifier = Modifier
            .graphicsLayer {
                if (!autoPilot) {
                    val k = 1f + 0.06f * pulse
                    scaleX = k
                    scaleY = k
                    alpha  = 1f - 0.28f * pulse
                }
            }
            .height(40.dp)
            .clip(RoundedCornerShape(50))
            .background(if (autoPilot) accent.copy(alpha = 0.38f) else accent)
            .clickable(onClickLabel = "Open next chapter", onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (autoPilot) {
            Box(
                Modifier
                    .matchParentSize()
                    .graphicsLayer {
                        scaleX = countdown().coerceIn(0f, 1f)
                        transformOrigin = TransformOrigin(0f, 0.5f)
                    }
                    .background(accent)
            )
        }
        Row(
            modifier          = Modifier.padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Ch. $nextNum",
                fontFamily = MontserratFamily,
                fontWeight = FontWeight.ExtraBold,
                fontSize   = 14.sp,
                color      = Color.White
            )
            Spacer(Modifier.width(6.dp))
            Icon(SolarIcons.ArrowRight, null, tint = Color.White, modifier = Modifier.size(16.dp))
        }
    }
}

// Speech bubble with a tail, drawn in the inverted page colours so it reads on any
// theme. Slides/fades in above the bar; the tail is aimed at the auto-pilot chip.
@Composable
private fun AutoPilotTip(
    text: String?,
    fg: Color,
    bg: Color,
    chipCenterX: () -> Float
) {
    // Keep the last text while the bubble fades out.
    var last by remember { mutableStateOf("") }
    LaunchedEffect(text) { if (text != null) last = text }
    val shown = text ?: last

    AnimatedVisibility(
        visible = text != null,
        enter   = fadeIn(tween(160)) + slideInVertically(tween(200)) { it / 3 },
        exit    = fadeOut(tween(160))
    ) {
        var centerX by remember { mutableStateOf(0f) }
        val fill = fg.copy(alpha = 0.92f)
        Box(
            modifier = Modifier
                .padding(bottom = 8.dp)
                .widthIn(max = 264.dp)
                .onGloballyPositioned { centerX = it.positionInWindow().x + it.size.width / 2f }
                .drawBehind {
                    val tailH = 7.dp.toPx()
                    val tailW = 7.dp.toPx()
                    val reach = (size.width / 2f - 22.dp.toPx()).coerceAtLeast(0f)
                    val cx    = size.width / 2f + (chipCenterX() - centerX).coerceIn(-reach, reach)
                    val tail  = Path().apply {
                        moveTo(cx - tailW, size.height - tailH - 1f)
                        lineTo(cx + tailW, size.height - tailH - 1f)
                        lineTo(cx, size.height)
                        close()
                    }
                    drawPath(tail, fill)
                }
                .padding(bottom = 7.dp)
        ) {
            Text(
                shown,
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(fill)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                fontFamily = MontserratFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize   = 12.sp,
                color      = bg,
                textAlign  = TextAlign.Center
            )
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

// The big on-page speed readout, like a countdown: it pops in the middle of the
// screen while you swipe and fades ~1s after you stop. Not interactive (touches
// pass through to the gesture layer), so a swipe never gets blocked by it.
@Composable
internal fun AutoSpeedReadout(
    level: Int,
    fg: Color,
    modifier: Modifier = Modifier
) {
    val pop = remember { Animatable(1f) }
    LaunchedEffect(level) {
        pop.snapTo(0.82f)
        pop.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMedium))
    }
    Column(
        modifier = modifier
            .graphicsLayer { scaleX = pop.value; scaleY = pop.value }
            .readerGlass(fg, RoundedCornerShape(36.dp), strength = 0.8f, classic = 0.18f)
            .padding(horizontal = 38.dp, vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "SPEED",
            fontFamily    = MontserratFamily,
            fontWeight    = FontWeight.ExtraBold,
            fontSize      = 11.sp,
            letterSpacing = 2.sp,
            color         = fg.copy(alpha = 0.6f)
        )
        Text(
            "$level",
            fontFamily = MontserratFamily,
            fontWeight = FontWeight.ExtraBold,
            fontSize   = 88.sp,
            color      = fg
        )
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            repeat(10) { i ->
                Box(
                    Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(fg.copy(alpha = if (i < level) 0.9f else 0.2f))
                )
            }
        }
    }
}

// A brief big ⏸ / ▶ in the middle of the page when a tap pauses or resumes.
@Composable
internal fun AutoPauseFlash(
    paused: Boolean,
    fg: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(96.dp)
            .readerGlass(fg, CircleShape, strength = 0.8f, classic = 0.18f),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            if (paused) SolarIcons.PauseBold else SolarIcons.PlayBold,
            contentDescription = null,
            tint     = fg,
            modifier = Modifier.size(44.dp)
        )
    }
}
