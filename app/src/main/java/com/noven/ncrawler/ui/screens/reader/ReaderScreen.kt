package com.noven.ncrawler.ui.screens.reader

import dev.chrisbanes.haze.haze
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.ui.platform.LocalView
import com.noven.ncrawler.ui.components.Motion
import com.noven.ncrawler.viewmodel.AUTO_SPEED_MAX
import com.noven.ncrawler.viewmodel.AUTO_SPEED_MIN
import kotlin.math.exp
import kotlin.math.abs
import com.noven.ncrawler.ui.components.SolarIcons
import dev.chrisbanes.haze.HazeState
import com.noven.ncrawler.ui.theme.GlassSpec
import com.noven.ncrawler.ui.theme.GlassMode
import com.noven.ncrawler.ui.components.glassBlur
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.composed
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.CompositionLocalProvider
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.noven.ncrawler.data.db.ChapterEntity
import com.noven.ncrawler.ui.components.errorShake
import com.noven.ncrawler.ui.components.glassTint
import com.noven.ncrawler.ui.theme.MontserratFamily
import com.noven.ncrawler.viewmodel.ReaderUiState
import com.noven.ncrawler.viewmodel.ReaderViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.roundToInt

@Composable
fun ReaderScreen(
    slug: String,
    chapterNum: Int,
    onBack: () -> Unit,
    vm: ReaderViewModel = viewModel()
) {
    LaunchedEffect(slug, chapterNum) { vm.load(slug, chapterNum) }

    val state       by vm.state.collectAsStateWithLifecycle()
    val settings    by vm.settings.collectAsStateWithLifecycle()
    val swatches    by vm.swatches.collectAsStateWithLifecycle()
    val chapterList by vm.chapterList.collectAsStateWithLifecycle()
    val readChapters by vm.readChapters.collectAsStateWithLifecycle()
    val novelTitle   by vm.novelTitle.collectAsStateWithLifecycle()
    val bookmarks    by vm.bookmarks.collectAsStateWithLifecycle()
    val placeNum     by vm.placeChapter.collectAsStateWithLifecycle()

    // vm.currentChapterNum is 0 until load() runs — fall back to the route's
    // chapter so nothing flashes "Chapter 0" on the first frame.
    val currentNum = vm.currentChapterNum.takeIf { it > 0 } ?: chapterNum

    // Real chapter title if we have one, else null → callers show "Chapter N".
    // The scraped page heading can be the NOVEL's name, so it's only trusted
    // once the novel title is known (and isn't equal to it).
    // Continuous auto-pilot: the next chapter is appended under the current one and,
    // once the page has scrolled into it, swapped in as THE chapter (see "swap" in the
    // auto-scroll section). `swapped` carries it for the frames until the ViewModel's
    // own state catches up, so the swap is never a Loading flash.
    var swapped  by remember { mutableStateOf<ChapterEntity?>(null) }
    var appended by remember { mutableStateOf<ChapterEntity?>(null) }
    val shownChapter: ChapterEntity? = swapped ?: (state as? ReaderUiState.Success)?.chapter

    val chapterTitle: String? = remember(chapterList, novelTitle, shownChapter, currentNum) {
        resolveChapterTitle(
            listTitle  = chapterList.firstOrNull { it.num == currentNum }?.title,
            pageTitle  = if (novelTitle.isBlank()) null else shownChapter?.title,
            novelTitle = novelTitle
        )
    }
    val appendedTitle: String = remember(chapterList, novelTitle, appended) {
        val a = appended
        if (a == null) "" else resolveChapterTitle(
            listTitle  = chapterList.firstOrNull { it.num == a.chapterNum }?.title,
            pageTitle  = if (novelTitle.isBlank()) null else a.title,
            novelTitle = novelTitle
        ) ?: "Chapter ${a.chapterNum}"
    }

    val swatch = swatches.getOrElse(settings.swatchIndex) { swatches.last() }
    val bg     = swatch.background
    val fg     = swatch.foreground
    val accent = swatch.accent

    var showControls    by remember { mutableStateOf(true) }
    var showSettings     by remember { mutableStateOf(false) }
    var showToc          by remember { mutableStateOf(false) }
    var showAudioOverlay by remember { mutableStateOf(false) }
    var audioSelected    by remember { mutableStateOf(false) }

    val scrollState = rememberScrollState()

    // ── Page bookmark (one per chapter) ──────────────────────────────────────
    val bookmarkedNums = remember(bookmarks) { bookmarks.map { it.chapterNum }.toSet() }
    val isBookmarked   = currentNum in bookmarkedNums

    // Little "Bookmark added" toast: shown ~1.6s, then fades. toastText is kept
    // after hiding so the text doesn't vanish mid fade-out.
    var toastVisible by remember { mutableStateOf(false) }
    var toastText    by remember { mutableStateOf("") }
    var toastStamp   by remember { mutableStateOf(0L) }
    LaunchedEffect(Unit) {
        vm.bookmarkEvent.collect { msg ->
            toastText    = msg
            toastVisible = true
            toastStamp   = System.nanoTime()   // restarts the timer on a quick second tap
        }
    }
    LaunchedEffect(toastStamp) {
        if (toastStamp != 0L) {
            delay(1600)
            toastVisible = false
        }
    }

    // CHANGE: the reading spot is remembered. A chapter that loads scrolls to
    // where you stopped (saved as a fraction of the chapter, so font-size /
    // line-height changes don't move it) instead of always jumping to the top.
    // Saving is paused (canSave = false) while a chapter loads and is being
    // scrolled to its spot, so the transient position 0 can't overwrite it.
    var canSave by remember { mutableStateOf(false) }
    // Bumped once per chapter that was really (re)loaded. The auto-scroll engine is
    // keyed on it: canSave can go false→true between two compositions (a cached
    // chapter loads in a blink), which used to leave the engine's key unchanged — the
    // old loop had ended, nothing restarted it, and the bar still said "active".
    var engineGen by remember { mutableStateOf(0) }

    LaunchedEffect(state) {
        val loaded = (state as? ReaderUiState.Success)?.chapter
        if (loaded != null && swapped != null && loaded === swapped) {
            // The chapter that auto-pilot already scrolled into: nothing to restore,
            // the page is exactly where it should be. (canSave stays true.)
            swapped = null
            return@LaunchedEffect
        }
        if (state is ReaderUiState.Success) {
            appended = null
            canSave = false
            // A tapped bookmark wins over the saved reading spot (consumed once).
            val jump  = vm.takePendingJump(currentNum)
            val saved = jump ?: vm.savedFraction(currentNum)   // read BEFORE anything can overwrite it
            scrollState.scrollTo(0)
            // A finished chapter (≥97%) reopens at the top; a barely-started one too.
            // A bookmark jump goes exactly where it was saved, even the very top/bottom.
            if (saved != null && (jump != null || saved in 0.02f..0.97f)) {
                // maxValue isn't real until the text has been measured (0, or
                // Int.MAX_VALUE, depending on the Compose version) — wait for a
                // genuine value, so the saved fraction never scales a bogus max.
                withTimeoutOrNull(1500) {
                    snapshotFlow { scrollState.maxValue }.first { it > 0 && it != Int.MAX_VALUE }
                }
                scrollState.scrollTo((saved * scrollState.maxValue).roundToInt())
            }
            canSave = true
            engineGen++
        } else {
            canSave = false
        }
    }

    // While the next chapter is appended below, scrollState.value / maxValue span BOTH
    // chapters, so a fraction taken now would be wrong for the open chapter — saving
    // pauses for that stretch (the finished chapter is stored as 100% on the swap).
    LaunchedEffect(scrollState.value, canSave, appended) {
        if (!canSave || appended != null) return@LaunchedEffect
        kotlinx.coroutines.delay(600)
        vm.saveScrollPosition(scrollState.value)
        if (scrollState.maxValue > 0 && scrollState.maxValue != Int.MAX_VALUE) {
            vm.saveReadingFraction(scrollState.value.toFloat() / scrollState.maxValue)
        }
    }

    // Leaving the reader within the 600ms debounce would lose the last bit of
    // scrolling — save once more on the way out.
    DisposableEffect(Unit) {
        onDispose {
            if (canSave && appended == null && scrollState.maxValue > 0 && scrollState.maxValue != Int.MAX_VALUE) {
                vm.saveReadingFraction(scrollState.value.toFloat() / scrollState.maxValue)
            }
        }
    }

    val progress = if (scrollState.maxValue > 0 && scrollState.maxValue != Int.MAX_VALUE)
        (scrollState.value.toFloat() / scrollState.maxValue).coerceIn(0f, 1f)
    else 0f

    // Pull-up-for-next-chapter is only offered when there IS a next chapter (or we
    // don't know yet — chapter list not loaded).
    val hasNext = chapterList.isEmpty() ||
        currentNum < (chapterList.maxOfOrNull { it.num } ?: Int.MAX_VALUE)
    // Neon-blue / red highlights pick different shades on dark vs light pages.
    val darkBg = bg.luminance() < 0.5f
    // FIX: chapter text was glaringly bright on dark pages (near-white on near-black).
    // Body text on a dark page is now blended 80% of the way from the page colour to
    // the theme's text colour — soft off-white instead of full-strength. The chapter
    // title, and light pages, keep the full-strength colour.
    val bodyFg = if (darkBg) lerp(bg, fg, DARK_BODY_TEXT_STRENGTH) else fg

    // ── Auto scroll ──────────────────────────────────────────────────────────
    // Header → "Auto scroll": a hand shows how to set the speed (swipe up = faster,
    // down = slower, shown as a big number), the page eases into motion, and a tap
    // anywhere pauses / resumes it (the control bar stays). At the end of a chapter
    // the bar turns into a "Ch. N →" button — or opens it itself in auto-pilot.
    // autoSpeed is deliberately NOT read in composition (only inside gestures and
    // the scroll loop) so a swipe never recomposes the whole reader.
    var autoScroll   by remember { mutableStateOf(false) }
    var autoSpeed    by remember { mutableStateOf(settings.autoSpeed) }   // dp per second
    var autoHint     by remember { mutableStateOf(false) }
    var atChapterEnd by remember { mutableStateOf(false) }
    val endCountdown = remember { Animatable(0f) }
    val density      = LocalDensity.current
    val screenHeightPx = with(density) { LocalConfiguration.current.screenHeightDp.dp.toPx() }
    val hostView     = LocalView.current
    val speedLevel by remember {
        derivedStateOf { autoSpeedLevel(autoSpeed, AUTO_SPEED_MIN, AUTO_SPEED_MAX) }
    }

    // A tap while auto-scroll runs PAUSES it (controls stay up, the ⏸ button turns
    // into ▶); another tap resumes. Only ✕ / Back end the session.
    var autoPaused by remember { mutableStateOf(false) }
    // Big on-page feedback: the speed number while you swipe, ⏸/▶ when a tap toggles.
    var speedStamp by remember { mutableStateOf(0L) }
    var speedFlash by remember { mutableStateOf(false) }
    var pauseFlash by remember { mutableStateOf(false) }
    // The little bubble above the speed bar that explains auto-pilot. Shown once
    // ever (first auto-scroll), then again whenever the chip is toggled.
    var autoPilotTip by remember { mutableStateOf<String?>(null) }
    val hintPrefs = remember {
        hostView.context.getSharedPreferences("reader_hints", android.content.Context.MODE_PRIVATE)
    }

    fun stopAuto() {
        if (!autoScroll) return
        autoScroll    = false
        autoHint      = false
        atChapterEnd  = false
        autoPaused    = false
        speedFlash    = false
        pauseFlash    = false
        autoPilotTip  = null
        appended      = null            // the pre-loaded next chapter goes away with the session
        showControls  = true            // show where you stopped
        vm.saveAutoSpeed(autoSpeed)
    }

    fun startAuto() {
        if (state !is ReaderUiState.Success) return
        atChapterEnd = false
        autoHint     = true
        autoPaused   = false
        showControls = false
        autoScroll   = true
        if (!hintPrefs.getBoolean("autopilot_tip_seen", false)) {
            autoPilotTip = AUTO_PILOT_TIP_INTRO
            hintPrefs.edit().putBoolean("autopilot_tip_seen", true).apply()
        }
    }

    // The tip and the big on-page readouts close themselves.
    LaunchedEffect(autoPilotTip) {
        if (autoPilotTip != null) {
            delay(4200)
            autoPilotTip = null
        }
    }
    // Every speed change restarts this, so the number stays up while you swipe
    // and fades ~0.9s after the last movement.
    LaunchedEffect(speedStamp) {
        if (speedStamp != 0L) {
            speedFlash = true
            delay(900)
            speedFlash = false
        }
    }
    LaunchedEffect(autoPaused) {
        if (autoScroll) {
            pauseFlash = true
            delay(650)
            pauseFlash = false
        }
    }

    // Auto-scroll with a screen that times out after 30s is useless.
    DisposableEffect(autoScroll) {
        hostView.keepScreenOn = autoScroll
        onDispose { hostView.keepScreenOn = false }
    }

    // Back stops auto-scroll first, before it leaves the reader.
    BackHandler(enabled = autoScroll) { stopAuto() }

    // The hint closes itself if you never touch the screen.
    LaunchedEffect(autoScroll, autoHint) {
        if (autoScroll && autoHint) {
            delay(6500)
            autoHint = false
        }
    }

    // The engine. Waits for a revealed, restored chapter (canSave), takes a beat
    // (to read the hint / see the new chapter), eases up to speed over 700ms, then
    // moves the page by speed × frame time (sub-pixel safe) until the last line.
    // Pausing cancels the loop; resuming restarts it with a short beat + ease-in.
    //
    // FIX (stopped on new chapters while still "active"): two causes.
    //  1. The page was moved with scrollState.scrollBy(), which takes the scroll
    //     mutex — the restore effect's scrollTo(0) on a freshly loaded chapter
    //     pre-empts it and cancels THIS coroutine without a trace. It now uses
    //     dispatchRawDelta (never cancelled by other scrolls) with its own
    //     sub-pixel carry.
    //  2. Its keys didn't change when a cached chapter loaded between two
    //     compositions; engineGen does, once per loaded chapter.
    val appendedNow by rememberUpdatedState(appended)
    LaunchedEffect(autoScroll, autoPaused, canSave, engineGen) {
        if (!autoScroll || autoPaused || !canSave) return@LaunchedEffect
        atChapterEnd = false
        delay(if (autoHint) 1100L else 400L)
        var last  = withFrameNanos { it }
        var ramp  = 0f
        var carry = 0f
        while (true) {
            val now = withFrameNanos { it }
            val dt  = ((now - last) / 1_000_000_000f).coerceIn(0f, 0.05f)
            last = now
            val max = scrollState.maxValue
            if (max != Int.MAX_VALUE && scrollState.value >= max) {
                // The next chapter is appended but not measured yet → just wait a frame.
                if (appendedNow != null) continue
                atChapterEnd = true
                break
            }
            ramp = (ramp + dt / 0.7f).coerceAtMost(1f)
            val eased = ramp * ramp * (3f - 2f * ramp)
            carry += autoSpeed * density.density * eased * dt
            val whole = carry.toInt()
            if (whole != 0) {
                scrollState.dispatchRawDelta(whole.toFloat())
                carry -= whole
            }
        }
    }

    // ── Continuous auto-pilot ────────────────────────────────────────────────
    // With auto-pilot on, the next chapter is fetched when the reader gets near the
    // end (≈ 2 screens left) and appended UNDER the current one, so the page just
    // keeps flowing from one chapter into the next — no stop, no countdown. When the
    // new title has scrolled up to where a chapter title normally sits, the screen
    // swaps it in as the open chapter in a single frame (same pixels, scroll offset
    // shifted by exactly the old chapter's height). A failed fetch falls back to the
    // old end-of-chapter countdown.
    val swapGeo        = remember { SwapGeometry() }
    var appendFailedAt by remember { mutableStateOf(0) }
    LaunchedEffect(autoScroll, settings.autoPilot, canSave, currentNum, hasNext, appendFailedAt) {
        if (!autoScroll || !settings.autoPilot || !canSave || !hasNext || appended != null) return@LaunchedEffect
        if (appendFailedAt == currentNum) return@LaunchedEffect
        snapshotFlow {
            val max = scrollState.maxValue
            max != Int.MAX_VALUE && max > 0 && max - scrollState.value < screenHeightPx * 2f
        }.first { it }
        val num  = currentNum
        val next = vm.fetchChapter(num + 1)
        if (next != null && next.chapterNum == num + 1 && autoScroll && settings.autoPilot && vm.currentChapterNum == num) {
            swapGeo.reset()
            appended = next
        } else if (next == null) {
            appendFailedAt = num
        }
    }
    // Auto-pilot switched off (or the session ended) before the swap → drop it again.
    LaunchedEffect(autoScroll, settings.autoPilot) {
        if (!autoScroll || !settings.autoPilot) appended = null
    }
    // The swap itself.
    LaunchedEffect(appended) {
        val next = appended ?: return@LaunchedEffect
        // (A very short next chapter can't scroll far enough to reach its title's
        // swap point — then the end of the page is the swap point.)
        snapshotFlow {
            val max = scrollState.maxValue
            swapGeo.ready && scrollState.value >= minOf(swapGeo.delta.toInt(), if (max == Int.MAX_VALUE) Int.MAX_VALUE else max)
        }.first { it }
        withFrameNanos {
            val delta = swapGeo.delta
            if (appended === next && delta > 0f) {
                swapped  = next                                  // content flips to the new chapter…
                appended = null
                scrollState.dispatchRawDelta(-delta)             // …and the offset follows, same frame
                vm.advanceTo(next, next.chapterNum)
            }
        }
    }

    // The per-frame position save above is debounced by 600ms, so it never fires
    // while the page keeps moving — save the spot every couple of seconds instead.
    LaunchedEffect(autoScroll, canSave) {
        while (autoScroll && canSave) {
            delay(2000)
            val max = scrollState.maxValue
            if (appendedNow == null && max > 0 && max != Int.MAX_VALUE) {
                vm.saveScrollPosition(scrollState.value)
                vm.saveReadingFraction(scrollState.value.toFloat() / max)
            }
        }
    }

    // End of chapter: the newest chapter has nothing to open; otherwise auto-pilot
    // counts down and opens the next one, and without it the bar waits for the tap.
    LaunchedEffect(autoScroll, atChapterEnd, settings.autoPilot, hasNext) {
        if (!autoScroll || !atChapterEnd || appendedNow != null) {
            endCountdown.snapTo(0f)
            return@LaunchedEffect
        }
        if (!hasNext) {
            toastText    = "You're up to date"
            toastVisible = true
            toastStamp   = System.nanoTime()
            stopAuto()
            return@LaunchedEffect
        }
        if (settings.autoPilot) {
            endCountdown.snapTo(0f)
            endCountdown.animateTo(1f, tween(2600, easing = LinearEasing))
            atChapterEnd = false
            vm.loadNext()
        }
    }

    // Sheets (Contents / Settings) blur the page behind them. In Glass mode the page
    // is already a blur source; in Classic mode it only becomes one while a sheet is
    // up (same approach as the Search overlay).
    val sheetOpen  = showSettings || showToc
    val sheetPalette = remember(darkBg) { SheetPalette(darkBg) }

    val noRipple = remember { MutableInteractionSource() }
    val readerHaze = remember { HazeState() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bg)
            .clickable(interactionSource = noRipple, indication = null) {
                // While auto-scroll runs a tap means pause / resume (handled by its own
                // layer) — it must never also pop the reader's bars up.
                if (!autoScroll && !showSettings && !showToc && !showAudioOverlay) showControls = !showControls
            }
    ) {
        // CHANGE (motion): Loading / Error / Content cross-fade (180ms) — a chapter
        // change no longer hard-cuts, and the error block shakes once.
        val readerPhase = when (state) {
            is ReaderUiState.Loading -> 0
            is ReaderUiState.Error   -> 1
            is ReaderUiState.Success -> 2
        }
        Crossfade(
            targetState   = readerPhase,
            modifier      = Modifier
                .fillMaxSize()
                .then(if (GlassMode.enabled || sheetOpen) Modifier.haze(readerHaze) else Modifier),   // the layer the pills / sheets blur
            animationSpec = tween(180)
        ) { p ->
            when (p) {
                0 -> Box(Modifier.fillMaxSize()) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = accent)
                }
                1 -> Box(Modifier.fillMaxSize()) {
                    Column(
                        modifier            = Modifier.align(Alignment.Center).errorShake(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(SolarIcons.Wifi, contentDescription = null,
                            modifier = Modifier.size(48.dp), tint = fg.copy(alpha = 0.5f))
                        Spacer(Modifier.height(12.dp))
                        Text((state as? ReaderUiState.Error)?.message ?: "Something went wrong",
                            color = fg.copy(alpha = 0.7f), textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 32.dp))
                        Spacer(Modifier.height(16.dp))
                        Button(
                            onClick = { vm.load(slug, chapterNum) },
                            colors  = ButtonDefaults.buttonColors(containerColor = accent, contentColor = bg)
                        ) { Text("Retry") }
                    }
                }
                else -> shownChapter?.let { shown ->
                    ReaderContent(
                        chapter     = shown,
                        appended    = appended,
                        appendedTitle = appendedTitle,
                        swapGeo     = swapGeo,
                        instantWave = shown === swapped,
                        title       = chapterTitle ?: "Chapter $currentNum",
                        settings    = settings,
                        fg          = fg,
                        bodyFg      = bodyFg,
                        accent      = accent,
                        darkBg      = darkBg,
                        scrollState = scrollState,
                        hasNext     = hasNext,
                        onPullNext  = vm::loadNext,
                        revealed    = canSave,
                        autoScrolling = autoScroll
                    )
                }
            }
        }

        // ── Brightness dimming overlay ────────────────────────────────────
        if (settings.brightness > 0f) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = settings.brightness)))
        }

        // ── Bottom scrim (always present, behind nav bar) ─────────────────
        // CHANGE: shorter — 110dp (was 180dp), with the fade compressed into its
        // top half so the nav bar still sits on a solid-enough backdrop.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(110.dp)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0f    to bg.copy(alpha = 0f),
                            0.45f to bg.copy(alpha = 0.82f),
                            1f    to bg.copy(alpha = 0.97f)
                        )
                    )
                )
        )

        // ── Bottom chapter nav bar ────────────────────────────────────────
        // Same rule as the header: out of the way while a sheet is open.
        AnimatedVisibility(
            visible  = showControls && !sheetOpen,
            enter    = fadeIn(tween(220)) + slideInVertically(tween(220, easing = FastOutSlowInEasing)) { it },
            exit     = fadeOut(tween(160)) + slideOutVertically(tween(160, easing = FastOutSlowInEasing)) { it },
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            CompositionLocalProvider(LocalReaderHaze provides readerHaze) {
                ChapterNavBar(
                    fg        = fg,
                    accent    = accent,
                    title     = chapterTitle,
                    chapterNum = currentNum,
                    progress  = progress,
                    canGoPrev = currentNum > 1,
                    onPrev    = vm::loadPrev,
                    onNext    = vm::loadNext,
                    onOpenToc = { showSettings = false; showToc = !showToc }
                )
            }
        }

        // ── Auto-scroll gesture layer ─────────────────────────────────────
        // While auto-scroll runs this layer owns the screen: a tap stops it, a
        // vertical swipe sets the speed — up = faster, down = slower (multiplicative,
        // so it feels the same at slow and fast; one screen-height swipe ≈ ×4.5).
        // Page scrolling by hand is switched off meanwhile (see ReaderContent).
        // ONE gesture handler for both jobs (two stacked detectors could fight over
        // the same touch): finger lifts before moving past the touch slop → a tap
        // (pause / resume); moves past it → a swipe that sets the speed (or, while
        // paused, scrolls the page by hand).
        if (autoScroll) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            val down     = awaitFirstDown(requireUnconsumed = false)
                            val slop     = viewConfiguration.touchSlop
                            var moved    = 0f
                            var dragging = false
                            while (true) {
                                val event  = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (change.isConsumed) break
                                if (!change.pressed) {                        // finger up
                                    // A tap pauses / resumes. (Not at the end-of-chapter
                                    // step: there the bar's own buttons decide.)
                                    if (!dragging && !atChapterEnd) {
                                        autoHint   = false
                                        autoPaused = !autoPaused
                                    }
                                    // Consumed so the screen's own tap-to-show-controls
                                    // handler (a parent) never sees this tap.
                                    change.consume()
                                    break
                                }
                                val dy = change.position.y - change.previousPosition.y
                                if (!dragging) {
                                    moved += dy
                                    if (abs(moved) > slop) {
                                        dragging = true
                                        autoHint = false
                                    }
                                } else if (autoPaused) {
                                    // Paused: a swipe moves the page by hand instead.
                                    scrollState.dispatchRawDelta(-dy)
                                } else if (!atChapterEnd) {
                                    // Multiplicative, so it feels the same slow or fast:
                                    // one screen-height swipe ≈ ×4.5 (up = faster).
                                    val factor = exp(-dy / screenHeightPx * 1.5f)
                                    autoSpeed  = (autoSpeed * factor).coerceIn(AUTO_SPEED_MIN, AUTO_SPEED_MAX)
                                    speedStamp = System.nanoTime()
                                }
                                if (dragging) change.consume()
                            }
                            if (dragging) vm.saveAutoSpeed(autoSpeed)
                        }
                    }
            )
        }

        // Hand notice — shown when auto-scroll starts (waits for the sheet to leave)
        AnimatedVisibility(
            visible  = autoScroll && autoHint,
            enter    = fadeIn(tween(240, delayMillis = 280)) +
                       slideInVertically(tween(300, delayMillis = 280, easing = Motion.EaseOut)) { it / 12 },
            exit     = fadeOut(tween(200)),
            modifier = Modifier.align(Alignment.Center)
        ) {
            CompositionLocalProvider(LocalReaderHaze provides readerHaze) {
                AutoScrollHint(fg = fg)
            }
        }

        // ── Big on-page feedback (middle of the screen, never interactive) ──
        // The speed as a big number while you swipe, like a countdown…
        AnimatedVisibility(
            visible  = autoScroll && speedFlash && !autoPaused && !atChapterEnd,
            enter    = fadeIn(tween(120)) + scaleIn(tween(160, easing = Motion.EaseOut), initialScale = 0.9f),
            exit     = fadeOut(tween(260)),
            modifier = Modifier.align(Alignment.Center)
        ) {
            CompositionLocalProvider(LocalReaderHaze provides readerHaze) {
                AutoSpeedReadout(level = speedLevel, fg = fg)
            }
        }
        // …and a quick ⏸ / ▶ when a tap pauses or resumes.
        AnimatedVisibility(
            visible  = autoScroll && pauseFlash && !atChapterEnd,
            enter    = fadeIn(tween(100)) + scaleIn(tween(160, easing = Motion.EaseOut), initialScale = 0.8f),
            exit     = fadeOut(tween(220)),
            modifier = Modifier.align(Alignment.Center)
        ) {
            CompositionLocalProvider(LocalReaderHaze provides readerHaze) {
                AutoPauseFlash(paused = autoPaused, fg = fg)
            }
        }

        // ── The one control bar for the whole auto-scroll session ─────────
        // Pausing keeps it up (⏸ becomes ▶). At the end of a chapter the SAME bar
        // morphs into [Auto-pilot] [Ch. N →] [✕] — nothing taller ever covers text.
        AnimatedVisibility(
            visible  = autoScroll,
            enter    = fadeIn(tween(220, delayMillis = 200)) +
                       slideInVertically(tween(260, delayMillis = 200, easing = Motion.EaseOut)) { it / 2 },
            exit     = fadeOut(tween(160)),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 20.dp)
        ) {
            CompositionLocalProvider(LocalReaderHaze provides readerHaze) {
                AutoScrollHud(
                    level             = speedLevel,
                    paused            = autoPaused,
                    autoPilot         = settings.autoPilot,
                    endMode           = atChapterEnd && hasNext,
                    nextNum           = currentNum + 1,
                    countdown         = { endCountdown.value },
                    fg                = fg,
                    bg                = bg,
                    accent            = accent,
                    tip               = autoPilotTip,
                    onTogglePause     = { autoHint = false; autoPaused = !autoPaused },
                    onToggleAutoPilot = {
                        val on = !settings.autoPilot
                        vm.setAutoPilot(on)
                        autoPilotTip = if (on) AUTO_PILOT_TIP_ON else AUTO_PILOT_TIP_OFF
                    },
                    onOpenNext        = { atChapterEnd = false; vm.loadNext() },
                    onStop            = { stopAuto() }
                )
            }
        }

        // ── Settings sheet (drag-to-dismiss, Search-overlay glass) ────────
        if (showSettings) {
            DraggableSettingsSheet(
                accent            = accent,
                palette           = sheetPalette,
                haze              = readerHaze,
                settings          = settings,
                swatches          = swatches,
                onDismiss         = { showSettings = false },
                onBrightness      = vm::setBrightness,
                onSelectSwatch    = vm::selectSwatch,
                onDecreaseFont    = vm::decreaseFontSize,
                onIncreaseFont    = vm::increaseFontSize,
                onSetAlign        = vm::setTextAlign
            )
        }

        // ── TOC sheet — same chrome as the settings sheet (drag handle,
        // slide-up/slide-down animation, drag-to-dismiss), just taller. No
        // close button: drag the dash down, tap outside, or press back.
        if (showToc) {
            ReaderSheet(
                accent         = accent,
                palette        = sheetPalette,
                haze           = readerHaze,
                onDismiss      = { showToc = false },
                heightFraction = 1f   // fills everything below the header
            ) { dismiss ->
                TocTabs(
                    chapters         = chapterList,
                    currentNum       = currentNum,
                    placeNum         = placeNum,
                    readChapters     = readChapters,
                    bookmarks        = bookmarks,
                    onSelect         = { num -> vm.jumpTo(num); dismiss() },
                    onOpenBookmark   = { b -> vm.jumpToBookmark(b); dismiss() },
                    onRemoveBookmark = vm::removeBookmark
                )
            }
        }

        // ── Top header ────────────────────────────────────────────────────
        // Stays up while a sheet (Settings / Contents) is open — drawn AFTER the sheets
        // (so above their scrim, still tappable) while the bottom bar steps aside.
        AnimatedVisibility(
            visible  = showControls || sheetOpen,
            enter    = fadeIn(tween(220)) + slideInVertically(tween(220, easing = FastOutSlowInEasing)),
            exit     = fadeOut(tween(160)) + slideOutVertically(tween(160, easing = FastOutSlowInEasing)),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            CompositionLocalProvider(LocalReaderHaze provides readerHaze) {
                ReaderHeader(
                    fg              = fg,
                    accent          = accent,
                    bg              = bg,
                    audioSelected   = audioSelected,
                    onBack          = onBack,
                    onAudioClick    = { audioSelected = true; showAudioOverlay = true },
                    onTextClick     = { audioSelected = false },
                    bookmarked      = isBookmarked,
                    onBookmarkClick = {
                        // Only while a chapter is actually on screen — the spot is a
                        // fraction of loaded text.
                        if (state is ReaderUiState.Success) {
                            vm.toggleBookmark(progress, chapterTitle ?: "Chapter $currentNum")
                        }
                    },
                    onAutoScrollClick = { showSettings = false; showToc = false; startAuto() },
                    settingsOpen    = showSettings,
                    onSettingsClick = { showToc = false; showSettings = !showSettings }
                )
            }
        }

        // ── Toast (bookmark added/removed, up to date) — text only, above the chapter bar ──
        AnimatedVisibility(
            visible  = toastVisible && !sheetOpen,
            enter    = fadeIn(tween(160)) + slideInVertically(tween(200)) { it / 2 },
            exit     = fadeOut(tween(220)),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 118.dp)
        ) {
            CompositionLocalProvider(LocalReaderHaze provides readerHaze) {
                // Text only — the gold bookmark icon was dropped from the toast.
                Text(
                    toastText,
                    modifier = Modifier
                        .readerGlass(fg, RoundedCornerShape(50), strength = 0.6f, classic = 0.22f)
                        .padding(horizontal = 20.dp, vertical = 11.dp),
                    color      = fg,
                    fontFamily = MontserratFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize   = 13.sp
                )
            }
        }

        // ── Audio overlay ─────────────────────────────────────────────────
        AnimatedVisibility(
            visible  = showAudioOverlay,
            enter    = fadeIn(tween(220)),
            exit     = fadeOut(tween(140)),
            modifier = Modifier.fillMaxSize()
        ) {
            AudioComingSoonOverlay(
                readerBg      = bg,
                audioSelected = audioSelected,
                accent        = accent,
                onAudioClick  = { audioSelected = true },
                onTextClick   = { audioSelected = false; showAudioOverlay = false },
                onDismiss     = { showAudioOverlay = false; audioSelected = false }
            )
        }
    }
}

// Blur layer for the reader's header / bottom bar pills (Glass mode). Provided
// only to those two bars — siblings of the page layer — never to the page itself.
internal val LocalReaderHaze = compositionLocalOf<HazeState?> { null }

// Pill / circle surface that follows the reader theme. Glass: real 30dp blur of
// the page behind it, tinted with the text colour at the 44% glass strength.
// Classic: the fixed see-through tint the reader always used.
internal fun Modifier.readerGlass(
    fg: Color,
    shape: Shape,
    strength: Float = 0.33f,
    classic: Float = 0.13f
): Modifier = composed {
    val haze = LocalReaderHaze.current
    if (GlassMode.enabled && haze != null) {
        this.clip(shape).glassBlur(haze, shape, fg.copy(alpha = GlassSpec.CARD_FILL * strength))
    } else {
        this.glassTint(fg, shape, strength, classic)
    }
}

// ── Reading content ──────────────────────────────────────────────────────────
// How much of the theme's text colour dark pages use for BODY text (1.0 = full
// strength). Lower = dimmer. The chapter title always uses full strength.
private const val DARK_BODY_TEXT_STRENGTH = 0.80f
