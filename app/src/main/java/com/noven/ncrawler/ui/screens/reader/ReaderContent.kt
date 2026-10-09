package com.noven.ncrawler.ui.screens.reader

import com.noven.ncrawler.ui.components.Motion
import com.noven.ncrawler.ui.components.rememberReducedMotion
import com.noven.ncrawler.ui.components.SolarIcons
import androidx.compose.ui.composed
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noven.ncrawler.data.db.ChapterEntity
import com.noven.ncrawler.ui.components.glassTint
import com.noven.ncrawler.ui.theme.MontserratFamily
import com.noven.ncrawler.viewmodel.ReaderSettings
import com.noven.ncrawler.viewmodel.ReaderTextAlign
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

@Composable
internal fun ReaderContent(
    chapter: ChapterEntity,
    title: String,
    settings: ReaderSettings,
    fg: Color,
    bodyFg: Color,
    accent: Color,
    darkBg: Boolean,
    scrollState: androidx.compose.foundation.ScrollState,
    hasNext: Boolean,
    onPullNext: () -> Unit,
    revealed: Boolean,          // the chapter is loaded AND scrolled to its saved spot
    autoScrolling: Boolean,     // auto-scroll owns the page: no scrolling by hand
    appended: ChapterEntity?,   // auto-pilot: the next chapter, flowing in under this one
    appendedTitle: String,
    swapGeo: SwapGeometry,      // where the two titles sit, so the screen can swap them
    instantWave: Boolean        // this chapter was scrolled into already → no wave-in
) {
    val align = when (settings.textAlign) {
        ReaderTextAlign.LEFT   -> TextAlign.Left
        ReaderTextAlign.CENTER -> TextAlign.Center
        ReaderTextAlign.RIGHT  -> TextAlign.Right
    }

    val paragraphs = remember(chapter.content) {
        chapter.content
            .split("\n")
            .map { it.trim() }
            .filter { it.isNotBlank() }
    }
    // [ … ] and * … * passages, split out once per chapter
    val parsed = remember(paragraphs) { paragraphs.map { parseFx(it) } }
    val anyFx  = remember(parsed) { parsed.any { segs -> segs.any { it.fx != Fx.PLAIN } } }
    // CHANGE (perf fix): the chapter body below is a plain Column +
    // verticalScroll, not a LazyColumn — every paragraph is composed and
    // laid out at once regardless of what's actually on screen. That's fine
    // for a normal chapter, but on a long one it means every FX paragraph
    // (not just the visible ones) keeps recomposing every single frame for
    // as long as the chapter is open, since none of them are ever
    // discarded for being off-screen. Past LONG_CHAPTER_FX_THRESHOLD
    // paragraphs, freeze to the same static look reducedMotion already
    // uses below, rather than paying that cost continuously. A real
    // windowed (LazyColumn) rewrite of the chapter body would let long
    // chapters keep the live animation too — bigger change, left for a
    // separate pass since it also means reworking the pull-to-next-chapter
    // gesture, which currently reads a plain ScrollState.
    // The chapter appended below (auto-pilot), split the same way.
    val apParagraphs = remember(appended) {
        appended?.content?.split("\n")?.map { it.trim() }?.filter { it.isNotBlank() } ?: emptyList()
    }
    val apParsed = remember(apParagraphs) { apParagraphs.map { parseFx(it) } }
    val apFx     = remember(apParsed) { apParsed.any { segs -> segs.any { it.fx != Fx.PLAIN } } }
    val fxPhases = if (anyFx || apFx)
        rememberFxPhases(freeze = paragraphs.size > LONG_CHAPTER_FX_THRESHOLD)
    else null

    // ── Elastic pull up for the next chapter ─────────────────────────────
    // At the very end of the text, dragging further up stretches the page
    // upward with growing resistance (a rubber band) and shows a hint; letting go
    // past the threshold opens the next chapter, otherwise it springs back.
    // Only a finger DRAG counts — a fling arriving at the end never triggers it.
    val density     = LocalDensity.current
    val maxPullPx   = with(density) { 200.dp.toPx() }
    val thresholdPx = with(density) { 90.dp.toPx() }
    var pull by remember { mutableStateOf(0f) }            // px the page is pulled up
    var fingerDown by remember { mutableStateOf(false) }
    val currentHasNext by rememberUpdatedState(hasNext)
    val currentOnPullNext by rememberUpdatedState(onPullNext)
    val scope = rememberCoroutineScope()

    // ── Wave-in ──────────────────────────────────────────────────────────────
    // Opening a chapter used to swap the text with no sign anything happened. Now
    // the title and the paragraphs that are ON SCREEN rise in as a wave from top
    // to bottom (the same fade + rise the other pages use for loaded content).
    // It starts once the chapter is revealed — i.e. after the saved spot has been
    // scrolled to — so the wave plays over what you will actually see.
    val reduced        = rememberReducedMotion()
    val wave           = remember(chapter) { ChapterWave(reduced, instantWave) }
    val viewportPx     = with(density) { LocalConfiguration.current.screenHeightDp.dp.toPx() }
    val risePx         = with(density) { 14.dp.toPx() }
    LaunchedEffect(chapter, revealed) {
        if (revealed) {
            // two frames so every paragraph has reported its position after the
            // scroll restore
            withFrameNanos { }
            withFrameNanos { }
            scope.launch { wave.play() }     // own scope: survives this effect re-keying
        }
    }
    // Failsafe — the text can never stay hidden if "revealed" never arrives.
    LaunchedEffect(chapter) {
        delay(2200)
        scope.launch { wave.play() }
    }

    val connection = remember(scrollState) {
        object : NestedScrollConnection {
            // Finger moving back DOWN while pulled: unwind the pull first, then scroll.
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (pull > 0f && available.y > 0f) {
                    val unwind = minOf(pull, available.y)
                    pull -= unwind
                    return Offset(0f, unwind)
                }
                return Offset.Zero
            }

            // Whatever upward drag the text couldn't use (we're at the end) becomes pull.
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                if (currentHasNext && fingerDown && available.y < 0f &&
                    scrollState.value >= scrollState.maxValue
                ) {
                    val resistance = (0.55f * (1f - pull / maxPullPx)).coerceAtLeast(0.08f)
                    pull = (pull - available.y * resistance).coerceAtMost(maxPullPx)
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(connection)
            // Watches the pointer WITHOUT consuming (Initial pass) to know when the
            // finger is down and when it lifts.
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    fingerDown = true
                    do {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                    } while (event.changes.any { it.pressed })
                    fingerDown = false
                    if (pull > 0f) {
                        val go = pull >= thresholdPx
                        scope.launch {
                            if (go) currentOnPullNext()
                            animate(
                                initialValue  = pull,
                                targetValue   = 0f,
                                animationSpec = spring(dampingRatio = 0.55f, stiffness = 220f)
                            ) { value, _ -> pull = value }
                        }
                    }
                }
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { translationY = -pull }          // the elastic stretch
                .verticalScroll(scrollState, enabled = !autoScrolling)
                // bottom 120dp (was 170dp): matches the shorter bottom scrim
                .padding(top = 150.dp, bottom = 120.dp, start = 24.dp, end = 24.dp)
        ) {
            ChapterBlock(
                title      = title,
                paragraphs = paragraphs,
                parsed     = parsed,
                fxPhases   = fxPhases,
                settings   = settings,
                fg         = fg,
                bodyFg     = bodyFg,
                accent     = accent,
                darkBg     = darkBg,
                align      = align,
                wave       = wave,
                viewportPx = viewportPx,
                risePx     = risePx,
                chapterKey = chapter,
                titleModifier = Modifier.onGloballyPositioned {
                    swapGeo.mainY   = it.positionInParent().y
                    swapGeo.mainSet = true
                }
            )

            // Auto-pilot: the next chapter continues right below, same look, no wave —
            // the screen swaps it in as the open chapter once it has scrolled up here.
            if (appended != null) {
                Spacer(Modifier.height(APPEND_GAP))
                ChapterBlock(
                    title      = appendedTitle,
                    paragraphs = apParagraphs,
                    parsed     = apParsed,
                    fxPhases   = fxPhases,
                    settings   = settings,
                    fg         = fg,
                    bodyFg     = bodyFg,
                    accent     = accent,
                    darkBg     = darkBg,
                    align      = align,
                    wave       = null,
                    viewportPx = viewportPx,
                    risePx     = risePx,
                    chapterKey = appended,
                    titleModifier = Modifier.onGloballyPositioned {
                        swapGeo.appY   = it.positionInParent().y
                        swapGeo.appSet = true
                    }
                )
            }
        }

        // Pull hint — sits in the gap the stretch opens up above the nav bar
        if (hasNext) {
            PullNextIndicator(
                pull      = { pull },
                threshold = thresholdPx,
                fg        = fg,
                accent    = accent,
                modifier  = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 120.dp)
            )
        }
    }
}

// One chapter's heading + paragraphs. Used for the open chapter (with its wave-in)
// and for the next chapter appended below it during auto-pilot (wave = null, so
// it looks exactly like the open chapter will once it is swapped in).
@Composable
private fun ChapterBlock(
    title: String,
    paragraphs: List<String>,
    parsed: List<List<FxSeg>>,
    fxPhases: FxPhases?,
    settings: ReaderSettings,
    fg: Color,
    bodyFg: Color,
    accent: Color,
    darkBg: Boolean,
    align: TextAlign,
    wave: ChapterWave?,
    viewportPx: Float,
    risePx: Float,
    chapterKey: Any?,
    titleModifier: Modifier = Modifier
) {
    fun Modifier.waved(slot: WaveSlot): Modifier =
        if (wave == null) this else this.waveItem(wave, slot, viewportPx, risePx)

    // CHANGE: heading is the chapter's title ("Chapter N" if it has none) —
    // it used to print chapter.title as scraped, which could be the novel's name.
    Text(
        text       = title,
        fontFamily = MontserratFamily,
        fontWeight = FontWeight.ExtraBold,
        fontSize   = 26.sp,
        color      = fg,
        textAlign  = TextAlign.Center,
        modifier   = titleModifier
            .fillMaxWidth()
            .padding(bottom = 24.dp)
            .waved(remember(chapterKey) { WaveSlot() })
    )

    paragraphs.forEachIndexed { index, para ->
        val segs      = parsed[index]
        val hasFx     = segs.any { it.fx != Fx.PLAIN }
        val slot      = remember(chapterKey, index) { WaveSlot() }
        val paraModifier = Modifier
            .fillMaxWidth()
            .padding(bottom = (settings.fontSize * 0.8f).dp)
            .waved(slot)

        if (hasFx && fxPhases != null) {
            // [ … ] neon-blue gradient / * … * pulsing red
            FxParagraph(
                segments = segs,
                dropCap  = index == 0,
                fx       = fxPhases,
                darkBg   = darkBg,
                settings = settings,
                fg       = bodyFg,
                accent   = accent,
                align    = align,
                modifier = paraModifier
            )
        } else if (index == 0 && para.isNotEmpty()) {
            val annotated = buildAnnotatedString {
                withStyle(SpanStyle(
                    fontSize   = (settings.fontSize * 2.4f).sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = MontserratFamily,
                    color      = accent
                )) { append(para.first().toString()) }
                withStyle(SpanStyle(fontFamily = MontserratFamily)) {
                    append(para.substring(1))
                }
            }
            Text(
                text       = annotated,
                fontSize   = settings.fontSize.sp,
                color      = bodyFg,
                textAlign  = align,
                lineHeight = (settings.fontSize * settings.lineHeight).sp,
                modifier   = paraModifier
            )
        } else {
            Text(
                text       = para,
                fontFamily = MontserratFamily,
                fontSize   = settings.fontSize.sp,
                color      = bodyFg,
                textAlign  = align,
                lineHeight = (settings.fontSize * settings.lineHeight).sp,
                modifier   = paraModifier
            )
        }
    }
}

// Extra space between the open chapter's last paragraph and the title of the next
// one flowing in below it (auto-pilot).
private val APPEND_GAP = 56.dp

// Where the open chapter's title and the appended chapter's title sit in the scroll
// content. Plain fields on purpose (written from layout callbacks, read by the swap
// effect each frame) — their DIFFERENCE is how far the page must be shifted back
// when the appended chapter becomes the open one.
internal class SwapGeometry {
    var mainY = 0f
    var appY = 0f
    var mainSet = false
    var appSet = false
    val ready: Boolean get() = mainSet && appSet && appY > mainY
    val delta: Float get() = appY - mainY
    fun reset() { appSet = false; appY = 0f }
}

// ── Chapter wave (the animation behind the wave-in above) ───────────────────
// One clock per chapter; each visible paragraph starts a little later the lower
// it sits on screen (so it reads as a wave, top → bottom) and rises 14dp while
// fading in with the app's strong ease-out. Everything is read in the draw phase
// (graphicsLayer) — no recomposition, no layout. Paragraphs that are off screen
// when the chapter opens are not animated (nobody sees it) and just appear.
// "Remove animations" → no wave, text simply shown.
private const val WAVE_UNSET     = -1f
private const val WAVE_SKIP      = -2f
private const val WAVE_SPREAD_MS = 380f      // top-of-screen → bottom-of-screen delay
private const val WAVE_ITEM_MS   = 340f      // one paragraph's fade + rise
private const val WAVE_TOTAL_MS  = WAVE_SPREAD_MS + WAVE_ITEM_MS

private class WaveSlot {
    var top    = 0f
    var bottom = 0f
    var delay  = WAVE_UNSET
}

private class ChapterWave(private val reduced: Boolean, played: Boolean = false) {
    // played = the chapter was already on screen (auto-pilot swapped it in): start
    // at the END of the wave so nothing fades in a second time.
    val clock = Animatable(if (played) WAVE_TOTAL_MS else 0f)   // ms since the wave started
    var running by mutableStateOf(played)
        private set
    private var started = played

    suspend fun play() {
        if (started) return
        started = true
        running = true
        if (reduced) clock.snapTo(WAVE_TOTAL_MS)
        else clock.animateTo(WAVE_TOTAL_MS, tween(WAVE_TOTAL_MS.toInt(), easing = LinearEasing))
    }
}

private fun Modifier.waveItem(
    wave: ChapterWave,
    slot: WaveSlot,
    viewportPx: Float,
    risePx: Float
): Modifier = this
    // Before graphicsLayer on purpose: the position it reports is the layout
    // position, not the animated one.
    .onGloballyPositioned { c ->
        val b = c.boundsInWindow()
        slot.top    = b.top
        slot.bottom = b.bottom
    }
    .graphicsLayer {
        if (!wave.running) {
            alpha = 0f
        } else {
            if (slot.delay == WAVE_UNSET) {
                val visible = slot.bottom > 0f && slot.top < viewportPx
                slot.delay = if (visible)
                    slot.top.coerceIn(0f, viewportPx) / viewportPx * WAVE_SPREAD_MS
                else WAVE_SKIP
            }
            if (slot.delay != WAVE_SKIP) {
                val p = ((wave.clock.value - slot.delay) / WAVE_ITEM_MS).coerceIn(0f, 1f)
                val e = Motion.EaseOut.transform(p)
                alpha        = e
                translationY = (1f - e) * risePx
            }
        }
    }

// The "pull up for next chapter" pill: the reader's soft-filled style. It fades
// in as the pull grows; the arrow flips and turns accent-coloured once releasing
// will actually open the next chapter.
@Composable
private fun PullNextIndicator(
    pull: () -> Float,
    threshold: Float,
    fg: Color,
    accent: Color,
    modifier: Modifier = Modifier
) {
    val ready = pull() >= threshold
    val arrowRotation by animateFloatAsState(
        targetValue   = if (ready) 180f else 0f,
        animationSpec = tween(200),
        label         = "pullArrow"
    )
    Row(
        modifier = modifier
            .graphicsLayer { alpha = (pull() / threshold).coerceIn(0f, 1f) }
            .glassTint(fg, RoundedCornerShape(24.dp), classic = 0.10f)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            SolarIcons.ArrowUp,
            contentDescription = null,
            tint     = if (ready) accent else fg.copy(alpha = 0.9f),
            modifier = Modifier
                .size(20.dp)
                .graphicsLayer { rotationZ = arrowRotation }
        )
        Spacer(Modifier.width(8.dp))
        Text(
            if (ready) "Release for next chapter" else "Pull up for next chapter",
            fontFamily = MontserratFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize   = 13.sp,
            color      = fg.copy(alpha = 0.9f)
        )
    }
}
