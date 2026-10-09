package com.noven.ncrawler.ui.screens.reader

import com.noven.ncrawler.ui.components.staggerIn
import com.noven.ncrawler.ui.components.AnimatedSlidersIcon
import com.noven.ncrawler.ui.components.rememberReducedMotion
import androidx.compose.ui.draw.drawBehind
import com.noven.ncrawler.ui.components.SolarIcons
import android.provider.Settings
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noven.ncrawler.ui.theme.MontserratFamily

// ── Header: back | settings. Pill moved to audio overlay. ───────────────────
@Composable
internal fun ReaderHeader(
    fg: Color,
    accent: Color,
    bg: Color,
    audioSelected: Boolean,
    onBack: () -> Unit,
    onAudioClick: () -> Unit,
    onTextClick: () -> Unit,
    bookmarked: Boolean,
    onBookmarkClick: () -> Unit,
    onAutoScrollClick: () -> Unit,
    settingsOpen: Boolean,
    onSettingsClick: () -> Unit
) {
    // Little spring pop whenever this chapter becomes bookmarked.
    val pop = remember { Animatable(1f) }
    LaunchedEffect(bookmarked) {
        if (bookmarked) {
            pop.snapTo(0.6f)
            pop.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMedium))
        }
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(
                colors = listOf(bg.copy(alpha = 0.97f), bg.copy(alpha = 0.78f), bg.copy(alpha = 0f))
            ))
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().statusBarsPadding()
        ) {
            // Audio / Text segmented pill — centered at very top
            Row(
                modifier              = Modifier.fillMaxWidth().padding(top = 10.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                SegmentedPill(
                    audioSelected = audioSelected,
                    accent        = accent,
                    fg            = fg,
                    onAudioClick  = onAudioClick,
                    onTextClick   = onTextClick
                )
            }

            // Back | Settings row
            Row(
                modifier              = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                // Change 2+3: solid filled circle buttons, bigger (48dp)
                ReaderIconButton(fg = fg, onClick = onBack) {
                    Icon(SolarIcons.ArrowLeft, "Back", tint = fg, modifier = Modifier.size(24.dp))
                }
                // Bookmark | Auto scroll | Settings. A bookmarked page is shown as a
                // FILLED icon in the page's own text colour (light on dark pages, dark
                // on light ones) on a slightly stronger circle — no more yellow.
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ReaderIconButton(fg = fg, active = bookmarked, onClick = onBookmarkClick) {
                        Icon(
                            if (bookmarked) SolarIcons.BookmarkBold else SolarIcons.Bookmark,
                            contentDescription = if (bookmarked) "Remove bookmark" else "Bookmark this page",
                            tint     = fg,
                            modifier = Modifier
                                .size(24.dp)
                                .graphicsLayer { scaleX = pop.value; scaleY = pop.value }
                        )
                    }
                    ReaderIconButton(fg = fg, onClick = onAutoScrollClick) {
                        Icon(
                            SolarIcons.ArrowDown,
                            contentDescription = "Auto scroll",
                            tint     = fg,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    ReaderIconButton(fg = fg, onClick = onSettingsClick) {
                        // Animated sliders: the knobs slide when settings open, and
                        // slide back when they close.
                        AnimatedSlidersIcon(
                            active   = settingsOpen,
                            ink      = fg,
                            modifier = Modifier.semantics { contentDescription = "Settings" }
                        )
                    }
                }
            }
        }
    }
}

// ── Solid filled icon button (replaces glass) ────────────────────────────────
@Composable
private fun ReaderIconButton(
    fg: Color,
    onClick: () -> Unit,
    active: Boolean = false,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .readerGlass(fg, CircleShape)
            .then(if (active) Modifier.clip(CircleShape).background(fg.copy(alpha = 0.16f)) else Modifier)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { content() }
}

// ── Audio / Text segmented pill ──────────────────────────────────────────────
@Composable
private fun SegmentedPill(
    audioSelected: Boolean,
    accent: Color,
    fg: Color,
    onAudioClick: () -> Unit,
    onTextClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .width(172.dp)
            .height(44.dp)
            .readerGlass(fg, RoundedCornerShape(50))
            .padding(4.dp)
    ) {
        Row(Modifier.fillMaxSize()) {
            SegmentPill("Audio", audioSelected, accent, fg, Modifier.weight(1f), onAudioClick)
            SegmentPill("Text",  !audioSelected, accent, fg, Modifier.weight(1f), onTextClick)
        }
    }
}

@Composable
private fun SegmentPill(
    label: String,
    selected: Boolean,
    accent: Color,
    fg: Color,
    modifier: Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(50))
            .background(if (selected) accent else Color.Transparent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            fontFamily = MontserratFamily,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
            fontSize   = 14.sp,
            color      = if (selected) Color.White else fg.copy(alpha = 0.7f)
        )
    }
}

// ── Chapter nav bar: [Prev◎] [Ch.N Title] [◎Next] + progress ────────────────
@Composable
internal fun ChapterNavBar(
    fg: Color,
    accent: Color,
    title: String?,          // null → no real chapter title, show just "Ch. N"
    chapterNum: Int,
    progress: Float,
    canGoPrev: Boolean,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onOpenToc: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 10.dp)
    ) {
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            // Prev — separate circle button
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .readerGlass(fg, CircleShape, strength = if (canGoPrev) 0.33f else 0.12f, classic = if (canGoPrev) 0.13f else 0.05f)
                    .clickable(enabled = canGoPrev, onClick = onPrev),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    SolarIcons.ArrowLeft,
                    "Previous chapter",
                    tint     = fg.copy(alpha = if (canGoPrev) 0.9f else 0.25f),
                    modifier = Modifier.size(22.dp)
                )
            }

            // Chapter info pill — Ch. number + short title, taps to open TOC
            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 10.dp)
                    .readerGlass(fg, RoundedCornerShape(24.dp), classic = 0.10f)
                    .clickable(onClick = onOpenToc)
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment     = Alignment.CenterVertically,
                    modifier              = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        SolarIcons.List,
                        contentDescription = null,
                        tint     = accent,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "Ch. $chapterNum",
                        fontFamily    = MontserratFamily,
                        fontWeight    = FontWeight.ExtraBold,
                        fontSize      = 12.sp,
                        color         = accent,
                        letterSpacing = 0.4.sp
                    )
                    if (title != null) {
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "·",
                            fontFamily = MontserratFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize   = 12.sp,
                            color      = fg.copy(alpha = 0.4f)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            title,
                            fontFamily = MontserratFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize   = 12.sp,
                            color      = fg.copy(alpha = 0.75f),
                            maxLines   = 1,
                            overflow   = TextOverflow.Ellipsis,
                            modifier   = Modifier.weight(1f, fill = false)
                        )
                    }
                }
            }

            // Next — separate circle button
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .readerGlass(fg, CircleShape)
                    .clickable(onClick = onNext),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    SolarIcons.ArrowRight,
                    "Next chapter",
                    tint     = fg.copy(alpha = 0.9f),
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        // Progress bar + percentage
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(5.dp)
                    .clip(RoundedCornerShape(50))
                    .background(fg.copy(alpha = 0.16f))
            ) {
                Box(
                    Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(progress.coerceIn(0.03f, 1f))
                        .clip(RoundedCornerShape(50))
                        .background(accent)
                )
            }
            Spacer(Modifier.width(10.dp))
            Text(
                "${(progress * 100).toInt()}%",
                fontFamily = MontserratFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize   = 11.sp,
                color      = fg.copy(alpha = 0.7f)
            )
        }
    }
}

// A title that is just "Chapter 12" / "Ch. 12" carries no more information than
// the number, so it doesn't count as a real chapter title.
private val BARE_CHAPTER = Regex("""(?i)(?:chapter|ch\.?)\s*\d+""")

// First usable title wins: the TOC's entry for this chapter, then the page's
// own heading. "Usable" = not blank, not the novel's name, not a bare
// "Chapter N". Returns null when nothing qualifies — callers fall back to the
// chapter number.
internal fun resolveChapterTitle(
    listTitle: String?,
    pageTitle: String?,
    novelTitle: String
): String? {
    fun usable(raw: String?): String? {
        val t = raw?.trim().orEmpty()
        if (t.isEmpty()) return null
        if (novelTitle.isNotBlank() && t.equals(novelTitle.trim(), ignoreCase = true)) return null
        if (BARE_CHAPTER.matches(t)) return null
        return t
    }
    return usable(listTitle) ?: usable(pageTitle)
}

// ── Audio "coming soon" overlay ──────────────────────────────────────────────
// REDESIGNED (was: six icons spinning round a ring, with text squeezed below).
// Now one calm, readable screen: a headphones badge with two soft pulse rings
// (one slow ease-out loop), then "Coming soon", one short line and the
// Audio/Text pill rise in one after another (the app's usual stack-in).
//  • Rings are drawn in the draw phase from one infinite transition, so nothing
//    recomposes while it runs; with system animations off they sit still.
//  • The backdrop still inverts the reader theme (light page → deep blue, dark
//    page → warm cream) so it reads as a separate mode.
//  • Tap anywhere to go back; the pill switches back to Text without closing.
@Composable
internal fun AudioComingSoonOverlay(
    readerBg: Color,
    audioSelected: Boolean,
    accent: Color,
    onAudioClick: () -> Unit,
    onTextClick: () -> Unit,
    onDismiss: () -> Unit
) {
    val luminance = 0.299f * readerBg.red + 0.587f * readerBg.green + 0.114f * readerBg.blue
    val backdrop  = if (luminance < 0.5f) Color(0xFFF5F0EA) else Color(0xFF0B1E3D)
    val content   = if (luminance < 0.5f) Color(0xFF1A1714) else Color(0xFFEAF1FF)

    val reduced = rememberReducedMotion()
    val phase: State<Float> =
        if (reduced) {
            remember { mutableStateOf(0.35f) }
        } else {
            rememberInfiniteTransition(label = "audioPulse").animateFloat(
                initialValue  = 0f,
                targetValue   = 1f,
                animationSpec = infiniteRepeatable(tween(2600, easing = LinearEasing)),
                label         = "audioPulsePhase"
            )
        }

    val noRipple = remember { MutableInteractionSource() }

    Box(
        modifier         = Modifier
            .fillMaxSize()
            .background(backdrop)
            .clickable(interactionSource = noRipple, indication = null, onClick = onDismiss),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier            = Modifier.padding(horizontal = 32.dp)
        ) {
            // Badge + pulse rings
            Box(
                modifier = Modifier
                    .staggerIn(0, distance = 10.dp, stepMs = 90, maxAnimated = 4)
                    .size(220.dp)
                    .drawBehind {
                        val base  = 48.dp.toPx()                       // badge radius
                        val reach = size.minDimension / 2f - base
                        if (reduced) {
                            drawCircle(
                                color  = content.copy(alpha = 0.14f),
                                radius = base + reach * 0.5f,
                                style  = Stroke(width = 1.5.dp.toPx())
                            )
                        } else {
                            for (offset in floatArrayOf(0f, 0.5f)) {
                                val t      = (phase.value + offset) % 1f
                                val eased  = 1f - (1f - t) * (1f - t)  // ease-out: quick out, soft finish
                                drawCircle(
                                    color  = content.copy(alpha = 0.28f * (1f - t)),
                                    radius = base + reach * eased,
                                    style  = Stroke(width = 1.5.dp.toPx())
                                )
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(96.dp)
                        .clip(CircleShape)
                        .background(content.copy(alpha = 0.10f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        SolarIcons.HeadphonesBold,
                        contentDescription = null,
                        tint     = content,
                        modifier = Modifier.size(44.dp)
                    )
                }
            }

            Text(
                "AUDIO",
                modifier      = Modifier.staggerIn(1, distance = 10.dp, stepMs = 90, maxAnimated = 4),
                fontFamily    = MontserratFamily,
                fontWeight    = FontWeight.Bold,
                fontSize      = 12.sp,
                letterSpacing = 2.sp,
                color         = content.copy(alpha = 0.55f)
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Coming soon",
                modifier   = Modifier.staggerIn(1, distance = 10.dp, stepMs = 90, maxAnimated = 4),
                fontFamily = MontserratFamily,
                fontWeight = FontWeight.ExtraBold,
                fontSize   = 28.sp,
                color      = content
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Hear chapters read aloud",
                modifier   = Modifier.staggerIn(2, distance = 10.dp, stepMs = 90, maxAnimated = 4),
                fontFamily = MontserratFamily,
                fontSize   = 14.sp,
                textAlign  = TextAlign.Center,
                color      = content.copy(alpha = 0.65f)
            )
            Spacer(Modifier.height(28.dp))
            Box(Modifier.staggerIn(3, distance = 10.dp, stepMs = 90, maxAnimated = 4)) {
                SegmentedPill(
                    audioSelected = audioSelected,
                    accent        = accent,
                    fg            = content,
                    onAudioClick  = onAudioClick,
                    onTextClick   = onTextClick
                )
            }
        }
    }
}
