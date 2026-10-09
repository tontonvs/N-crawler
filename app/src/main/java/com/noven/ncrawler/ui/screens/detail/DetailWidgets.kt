package com.noven.ncrawler.ui.screens.detail

import com.noven.ncrawler.ui.components.AnimatedRefreshIcon
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.snap
import com.noven.ncrawler.ui.components.rememberReducedMotion
import com.noven.ncrawler.ui.components.SolarStars
import com.noven.ncrawler.ui.components.Motion
import androidx.compose.animation.core.spring
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.animation.core.RepeatMode
import androidx.compose.ui.graphics.Shape
import com.noven.ncrawler.ui.components.SolarIcons
import com.noven.ncrawler.ui.theme.GlassMode
import com.noven.ncrawler.ui.components.glassBlur
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.core.Animatable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import com.noven.ncrawler.ui.components.FavouritePink
import com.noven.ncrawler.ui.components.BookmarkGold
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noven.ncrawler.data.db.NovelEntity
import com.noven.ncrawler.ui.theme.GlassSurfaceDark
import com.noven.ncrawler.ui.components.pressScale
import com.noven.ncrawler.ui.components.pressable
import com.noven.ncrawler.ui.theme.MontserratFamily
import com.noven.ncrawler.ui.theme.StarGold
import androidx.compose.foundation.shape.GenericShape
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first

// ── Star rating ───────────────────────────────────────────────────────────────

@Composable
internal fun StarRating(
    rawRating: String,                     // e.g. "8.7" from NovelEntity.rating
    modifier: Modifier = Modifier,
    starColor: Color = StarGold,   // was a hardcoded near-duplicate of StarGold
    emptyColor: Color = Color.White.copy(alpha = 0.30f),
) {
    val score = rawRating.toFloatOrNull() ?: 0f
    // NovelArrow ratings are out of 10 — map to 5 stars
    val stars = (score / 2f).coerceIn(0f, 5f)

    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        for (i in 1..5) {
            when {
                // full: solid duotone star
                stars >= i -> Icon(
                    imageVector        = SolarStars.StarBold,
                    contentDescription = null,
                    tint               = starColor,
                    modifier           = Modifier.size(21.dp),
                )
                // half: outline star with the solid one clipped to its left half
                stars >= i - 0.5f -> Box(Modifier.size(21.dp)) {
                    Icon(
                        imageVector        = SolarStars.Star,
                        contentDescription = null,
                        tint               = starColor,
                        modifier           = Modifier.fillMaxSize(),
                    )
                    Icon(
                        imageVector        = SolarStars.StarBold,
                        contentDescription = null,
                        tint               = starColor,
                        modifier           = Modifier
                            .fillMaxSize()
                            .drawWithContent {
                                clipRect(right = size.width / 2f) {
                                    this@drawWithContent.drawContent()
                                }
                            },
                    )
                }
                // empty: line duotone star
                else -> Icon(
                    imageVector        = SolarStars.Star,
                    contentDescription = null,
                    tint               = emptyColor,
                    modifier           = Modifier.size(21.dp),
                )
            }
        }
        Spacer(Modifier.width(9.dp))
        Text(
            text       = rawRating.ifBlank { "—" },
            color      = Color.White,
            fontFamily = MontserratFamily,
            fontSize   = 17.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

// ── Reader-style circle button (back / refresh / play) ────────────────────────
// The reader's buttons are soft filled circles: the foreground colour at 13%,
// no border, icon at 90%. Here the circle sits over cover art rather than the
// reader's solid page, so a dark base tint sits under the 13% white to keep it
// visible on bright covers.

// ── Favourite heart pop ──────────────────────────────────────────────────────
// A large heart springs in (overshoots, settles), holds a beat, then fades —
// the "like" feel from social apps. Drawn only through graphicsLayer, so the
// animation never recomposes; it is invisible (alpha 0) and non-interactive
// whenever it is not playing. [trigger] 0 = never played.
@Composable
internal fun HeartBurst(trigger: Int, modifier: Modifier = Modifier) {
    val heartScale = remember { Animatable(0f) }
    val heartAlpha = remember { Animatable(0f) }
    LaunchedEffect(trigger) {
        if (trigger == 0) return@LaunchedEffect
        heartAlpha.snapTo(1f)
        heartScale.snapTo(0.3f)
        heartScale.animateTo(1.1f, spring(dampingRatio = 0.4f, stiffness = 380f))
        delay(180)
        heartAlpha.animateTo(0f, tween(260))
    }
    Icon(
        Icons.Filled.Favorite,
        contentDescription = null,
        tint     = FavouritePink,
        modifier = modifier
            .size(120.dp)
            .graphicsLayer {
                scaleX = heartScale.value
                scaleY = heartScale.value
                alpha = heartAlpha.value
            }
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ReaderCircleBtn(
    onClick: () -> Unit,
    // CHANGE (partial downloads): optional — every existing caller (Back,
    // Refresh, Play) passes nothing here and keeps behaving exactly as
    // before. Only the new download button uses it, to open the
    // download-options sheet without disturbing its single-tap action.
    onLongClick: (() -> Unit)? = null,
    size: Dp = 48.dp,
    content: @Composable () -> Unit,
) {
    // CHANGE (motion): press-in scale (90ms, no bounce) instead of a ripple —
    // same feel as the floating nav. The scale sits outside the clip so the
    // whole circle shrinks.
    // Glass mode: real 30dp blur over the cover (when a blur layer is provided
    // by the top row) tinted with the 44% glass fill; otherwise just the fill.
    // Classic: the original dark scrim (black 40% + white 13%).
    val source = remember { MutableInteractionSource() }
    val haze   = LocalDetailHaze.current
    val glass  = GlassMode.enabled
    Box(
        contentAlignment = Alignment.Center,
        modifier         = Modifier
            .size(size)
            .pressScale(source, 0.92f)
            .clip(CircleShape)
            .then(
                when {
                    !glass        -> Modifier
                        .background(Color.Black.copy(alpha = 0.40f))
                        .background(Color.White.copy(alpha = 0.13f))
                    haze != null  -> Modifier.glassBlur(haze, CircleShape, GlassSurfaceDark)
                    else          -> Modifier.background(GlassSurfaceDark)
                }
            )
            .combinedClickable(
                interactionSource = source,
                indication        = null,
                onClick           = onClick,
                onLongClick       = onLongClick
            ),
    ) { content() }
}

// ── Play button ───────────────────────────────────────────────────────────────

@Composable
internal fun PlayButton(label: String, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier            = Modifier
            .pressable(onClick = onClick, pressedScale = 0.95f)   // CHANGE (motion)
            .padding(horizontal = 20.dp, vertical = 6.dp),
    ) {
        ReaderCircleBtn(onClick = onClick, size = 76.dp) {
            Icon(
                imageVector        = SolarIcons.PlayBold,
                contentDescription = label,
                tint               = Color.White.copy(alpha = 0.9f),
                modifier           = Modifier.size(40.dp),
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text       = label,
            color      = Color.White.copy(alpha = 0.9f),
            fontFamily = MontserratFamily,
            fontSize   = 15.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

// ── Meta chip (Status | Genre | Latest) ──────────────────────────────────────
// value = the info ("Completed"), label = its title ("STATUS"). Both are larger
// than before (16sp / 12sp, were 13sp / 9sp).

@Composable
internal fun MetaChip(label: String, value: String, accent: Color, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text       = value,
            color      = Color.White,
            fontFamily = MontserratFamily,
            fontSize   = 16.sp,
            fontWeight = FontWeight.Bold,
            textAlign  = TextAlign.Center,
            maxLines   = 2,
            overflow   = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text          = label,
            color         = accent.copy(alpha = 0.85f),
            fontFamily    = MontserratFamily,
            fontSize      = 12.sp,
            fontWeight    = FontWeight.SemiBold,
            letterSpacing = 1.sp,
            textAlign     = TextAlign.Center,
        )
    }
}

// ── Vertical separator ────────────────────────────────────────────────────────

@Composable
internal fun MetaSeparator() {
    Box(
        modifier = Modifier
            .height(36.dp)
            .width(1.dp)
            .background(Color.White.copy(alpha = 0.18f)),
    )
}

// ── New-chapter pill + "download new ch." speech bubble ───────────────────────

// Small filled "NEW" tag on every chapter in the unread new range.
@Composable
internal fun NewPill() {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(BookmarkGold)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(
            text       = "NEW",
            color      = Color(0xFF1B1405),
            fontFamily = MontserratFamily,
            fontSize   = 10.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 0.6.sp,
        )
    }
}

private val BubbleTail: Shape = GenericShape { size, _ ->
    moveTo(0f, size.height)
    lineTo(size.width / 2f, 0f)
    lineTo(size.width, size.height)
    close()
}

// Cartoon speech bubble hanging under the download button. A gentle bob keeps it
// noticeable without being loud. INFO ONLY: it says how many chapters are new; the ✕
// dismisses it. (The download button itself switches to an update icon — that is what
// downloads the new chapters.)
@Composable
internal fun NewChaptersBubble(
    label: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bob by rememberInfiniteTransition(label = "bubbleBob").animateFloat(
        initialValue  = 0f,
        targetValue   = 1f,
        animationSpec = infiniteRepeatable(tween(1100, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label         = "bob",
    )
    val ink = Color(0xFF14161A)
    Column(
        modifier            = modifier.graphicsLayer { translationY = bob * 4.dp.toPx() },
        horizontalAlignment = Alignment.End,
    ) {
        // Tail points up at the download button (its centre is 24dp from the bubble's right edge).
        Box(
            Modifier
                .padding(end = 16.dp)
                .size(width = 16.dp, height = 9.dp)
                .background(Color.White, BubbleTail)
        )
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(18.dp))
                .background(Color.White)
                .padding(start = 14.dp, top = 7.dp, bottom = 7.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text       = label,
                color      = ink,
                fontFamily = MontserratFamily,
                fontSize   = 13.sp,
                fontWeight = FontWeight.ExtraBold,
            )
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(ink.copy(alpha = 0.08f))
                    .clickable(onClick = onDismiss),
                contentAlignment = Alignment.Center,
            ) {
                Text("✕", color = ink.copy(alpha = 0.6f), fontSize = 11.sp)
            }
        }
    }
}

// ── Sort toggle ───────────────────────────────────────────────────────────────
// Solar "Sort Vertical" (line duotone, like every other icon on this screen).
// It turns 180° when the order flips, which also swaps its two tones — the solid
// arrow marks the direction in use. 260ms, ease-out; instant with reduced motion.

@Composable
internal fun SortToggle(newestFirst: Boolean, onClick: () -> Unit) {
    val reduced = rememberReducedMotion()
    val turn by animateFloatAsState(
        targetValue   = if (newestFirst) 0f else 180f,
        animationSpec = if (reduced) snap() else tween(260, easing = Motion.EaseOut),
        label         = "sortTurn",
    )
    ReaderCircleBtn(onClick = onClick, size = 40.dp) {
        Icon(
            SolarIcons.Sort,
            contentDescription = if (newestFirst) "Newest first — tap for oldest first"
                                 else "Oldest first — tap for newest first",
            tint               = Color.White.copy(alpha = 0.9f),
            modifier           = Modifier
                .size(22.dp)
                .graphicsLayer { rotationZ = turn },
        )
    }
}

// ── Refresh button ────────────────────────────────────────────────────────────
// Motion (Jakub primary, Emil secondary): refresh is occasional, so one calm,
// functional motion and nothing decorative.
//  • tap     → static icon swaps to the spinning one: fade + scale 0.85→1, 200ms
//  • running → the spin IS the loading indicator (no ring/pulse on top of it)
//  • done    → the spin decelerates to the resting pose (never stops dead), and
//              only then swaps back: exit is subtler than enter (120ms, 0.9)
//  • reduced motion → no spin; swaps are instant; the skeleton shows progress

@Composable
internal fun RefreshButton(refreshing: Boolean, onClick: () -> Unit) {
    val reduced = rememberReducedMotion()
    var spinning by remember { mutableStateOf(false) }
    LaunchedEffect(refreshing) { if (refreshing) spinning = true }
    val showSpinner = refreshing || spinning

    ReaderCircleBtn(onClick = onClick) {
        AnimatedContent(
            targetState    = showSpinner,
            transitionSpec = {
                if (reduced) {
                    EnterTransition.None togetherWith ExitTransition.None
                } else {
                    (fadeIn(tween(200, easing = Motion.EaseOut)) +
                        scaleIn(initialScale = 0.85f, animationSpec = tween(200, easing = Motion.EaseOut)))
                        .togetherWith(
                            fadeOut(tween(120, easing = Motion.EaseOut)) +
                                scaleOut(targetScale = 0.9f, animationSpec = tween(120, easing = Motion.EaseOut))
                        )
                }
            },
            label = "refreshIconSwap",
        ) { busy ->
            if (busy) {
                AnimatedRefreshIcon(
                    animating = refreshing,
                    ink       = Color.White.copy(alpha = 0.9f),
                    size      = 32.dp,
                    onRested  = { spinning = false },
                )
            } else {
                Icon(
                    SolarIcons.Refresh,
                    contentDescription = "Check updates",
                    tint               = Color.White.copy(alpha = 0.9f),
                    modifier           = Modifier.size(24.dp),
                )
            }
        }
    }
}
