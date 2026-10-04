package com.noven.ncrawler.ui.screens.reader

import com.noven.ncrawler.ui.components.staggerIn
import android.os.Build
import dev.chrisbanes.haze.haze
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.graphics.TransformOrigin
import com.noven.ncrawler.ui.components.AnimatedSlidersIcon
import com.noven.ncrawler.ui.components.Motion
import com.noven.ncrawler.ui.theme.GlassBase
import com.noven.ncrawler.viewmodel.AUTO_SPEED_MAX
import com.noven.ncrawler.viewmodel.AUTO_SPEED_MIN
import kotlin.math.exp
import kotlin.math.abs
import com.noven.ncrawler.ui.components.rememberReducedMotion
import androidx.compose.ui.draw.drawBehind
import com.noven.ncrawler.ui.components.SolarIcons
import dev.chrisbanes.haze.HazeState
import com.noven.ncrawler.ui.theme.GlassSpec
import com.noven.ncrawler.ui.theme.GlassMode
import com.noven.ncrawler.ui.components.glassSource
import com.noven.ncrawler.ui.components.glassBlur
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.composed
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.CompositionLocalProvider
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.LinearGradientShader
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.noven.ncrawler.data.db.ChapterEntity
import com.noven.ncrawler.data.scraper.ChapterLink
import com.noven.ncrawler.ui.components.errorShake
import com.noven.ncrawler.ui.components.glassTint
import com.noven.ncrawler.ui.theme.MontserratFamily
import com.noven.ncrawler.viewmodel.ReaderSettings
import com.noven.ncrawler.viewmodel.ReaderSwatch
import com.noven.ncrawler.viewmodel.ReaderTextAlign
import com.noven.ncrawler.viewmodel.ReaderUiState
import com.noven.ncrawler.viewmodel.ReaderViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import com.noven.ncrawler.data.db.ReaderBookmark
import com.noven.ncrawler.ui.components.BookmarkGold
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

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
    val chapterTitle: String? = remember(chapterList, novelTitle, state, currentNum) {
        resolveChapterTitle(
            listTitle  = chapterList.firstOrNull { it.num == currentNum }?.title,
            pageTitle  = if (novelTitle.isBlank()) null
                         else (state as? ReaderUiState.Success)?.chapter?.title,
            novelTitle = novelTitle
        )
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

    LaunchedEffect(state) {
        if (state is ReaderUiState.Success) {
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
        } else {
            canSave = false
        }
    }

    LaunchedEffect(scrollState.value, canSave) {
        if (!canSave) return@LaunchedEffect
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
            if (canSave && scrollState.maxValue > 0 && scrollState.maxValue != Int.MAX_VALUE) {
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
    LaunchedEffect(autoScroll, autoPaused, canSave) {
        if (!autoScroll || autoPaused || !canSave) return@LaunchedEffect
        atChapterEnd = false
        delay(if (autoHint) 1100L else 400L)
        var last = withFrameNanos { it }
        var ramp = 0f
        while (true) {
            val now = withFrameNanos { it }
            val dt  = ((now - last) / 1_000_000_000f).coerceIn(0f, 0.05f)
            last = now
            val max = scrollState.maxValue
            if (max != Int.MAX_VALUE && scrollState.value >= max) {
                atChapterEnd = true
                break
            }
            ramp = (ramp + dt / 0.7f).coerceAtMost(1f)
            val eased = ramp * ramp * (3f - 2f * ramp)
            scrollState.scrollBy(autoSpeed * density.density * eased * dt)
        }
    }

    // The per-frame position save above is debounced by 600ms, so it never fires
    // while the page keeps moving — save the spot every couple of seconds instead.
    LaunchedEffect(autoScroll, canSave) {
        while (autoScroll && canSave) {
            delay(2000)
            val max = scrollState.maxValue
            if (max > 0 && max != Int.MAX_VALUE) {
                vm.saveScrollPosition(scrollState.value)
                vm.saveReadingFraction(scrollState.value.toFloat() / max)
            }
        }
    }

    // End of chapter: the newest chapter has nothing to open; otherwise auto-pilot
    // counts down and opens the next one, and without it the bar waits for the tap.
    LaunchedEffect(autoScroll, atChapterEnd, settings.autoPilot, hasNext) {
        if (!autoScroll || !atChapterEnd) {
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
                if (!showSettings && !showToc && !showAudioOverlay) showControls = !showControls
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
                else -> (state as? ReaderUiState.Success)?.let { s ->
                    ReaderContent(
                        chapter     = s.chapter,
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

        // ── Top header ────────────────────────────────────────────────────
        // Hidden while a sheet (Settings / Contents) is open: its buttons used to
        // sit over / through the sheet, looking tappable while the scrim ate the tap.
        AnimatedVisibility(
            visible  = showControls && !sheetOpen,
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
                    onAutoScrollClick = { startAuto() },
                    settingsOpen    = showSettings,
                    onSettingsClick = { showToc = false; showSettings = !showSettings }
                )
            }
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
                heightFraction = 0.85f
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

@Composable
private fun ReaderContent(
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
    autoScrolling: Boolean      // auto-scroll owns the page: no scrolling by hand
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
    val fxPhases = if (anyFx)
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
    val wave           = remember(chapter) { ChapterWave(reduced) }
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
            // CHANGE: heading is the chapter's title ("Chapter N" if it has none) —
            // it used to print chapter.title as scraped, which could be the novel's name.
            Text(
                text       = title,
                fontFamily = MontserratFamily,
                fontWeight = FontWeight.ExtraBold,
                fontSize   = 26.sp,
                color      = fg,
                textAlign  = TextAlign.Center,
                modifier   = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp)
                    .waveItem(wave, remember(chapter) { WaveSlot() }, viewportPx, risePx)
            )

            paragraphs.forEachIndexed { index, para ->
                val segs      = parsed[index]
                val hasFx     = segs.any { it.fx != Fx.PLAIN }
                val slot      = remember(chapter, index) { WaveSlot() }
                val paraModifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = (settings.fontSize * 0.8f).dp)
                    .waveItem(wave, slot, viewportPx, risePx)

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

private class ChapterWave(private val reduced: Boolean) {
    val clock = Animatable(0f)                    // ms since the wave started
    var running by mutableStateOf(false)
        private set
    private var started = false

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

// ── Highlighted passages ─────────────────────────────────────────────────────
//   [ … ]   → neon blue with a gradient that slowly moves through the letters
//             (game-style system messages: [Level Up!], [Skill: …])
//   * … *   → red that pulses (the asterisks themselves are dropped)
// Only paragraphs that actually contain such a passage take part, and a
// paragraph only animates while it is on screen (see FxParagraph).

private enum class Fx { PLAIN, NEON, RED }
private data class FxSeg(val text: String, val fx: Fx)

// [ … ] on one line, or *word* — the opening * must be followed by a non-space and
// the closing * preceded by one, so "***" / "* * *" scene breaks and "5 * 3 * 2"
// are left alone.
private val FX_REGEX = Regex("""\[[^\]\n]{1,400}\]|\*(?![\s*])[^*\n]{1,400}?(?<![\s*])\*""")

private fun parseFx(text: String): List<FxSeg> {
    val out = ArrayList<FxSeg>()
    var last = 0
    for (m in FX_REGEX.findAll(text)) {
        if (m.range.first > last) out += FxSeg(text.substring(last, m.range.first), Fx.PLAIN)
        val raw = m.value
        out += if (raw.startsWith("[")) FxSeg(raw, Fx.NEON)                        // brackets stay
               else FxSeg(raw.substring(1, raw.length - 1), Fx.RED)                // asterisks go
        last = m.range.last + 1
    }
    if (last < text.length) out += FxSeg(text.substring(last), Fx.PLAIN)
    return out
}

// The two animation clocks, handed down as State objects and NOT read here: only
// a visible FxParagraph reads .value, so nothing else recomposes per frame.
private class FxPhases(val neon: State<Float>, val pulse: State<Float>)

// Paragraph count past which a chapter's FX animation freezes instead of
// running live — see the CHANGE note at the call site above.
private const val LONG_CHAPTER_FX_THRESHOLD = 150

@Composable
private fun rememberFxPhases(freeze: Boolean = false): FxPhases {
    val context = LocalContext.current
    val reducedMotion = remember(context) {
        Settings.Global.getFloat(
            context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f
        ) == 0f
    }
    // "Remove animations", or a long chapter where the live version would
    // mean many non-windowed paragraphs recomposing every frame: keep the
    // colours, freeze the motion.
    if (reducedMotion || freeze) return remember { FxPhases(mutableStateOf(0f), mutableStateOf(0.6f)) }

    val transition = rememberInfiniteTransition(label = "readerFx")
    val neon = transition.animateFloat(
        initialValue  = 0f,
        targetValue   = 1f,
        animationSpec = infiniteRepeatable(tween(2600, easing = LinearEasing)),
        label         = "neonPhase"
    )
    val pulse = transition.animateFloat(
        initialValue  = 0f,
        targetValue   = 1f,
        animationSpec = infiniteRepeatable(tween(950, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label         = "redPulse"
    )
    return remember(neon, pulse) { FxPhases(neon, pulse) }
}

// A gradient that slides sideways: seamless because the first and last colours
// match and the tile repeats every [periodPx].
private class MovingGradientBrush(
    private val colors: List<Color>,
    private val shiftPx: Float,
    private val periodPx: Float
) : ShaderBrush() {
    override fun createShader(size: Size): Shader =
        LinearGradientShader(
            from     = Offset(shiftPx, 0f),
            to       = Offset(shiftPx + periodPx, 0f),
            colors   = colors,
            tileMode = TileMode.Repeated
        )
}

@Composable
private fun FxParagraph(
    segments: List<FxSeg>,
    dropCap: Boolean,
    fx: FxPhases,
    darkBg: Boolean,
    settings: ReaderSettings,
    fg: Color,
    accent: Color,
    align: TextAlign,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val screenHeightPx = with(density) { LocalConfiguration.current.screenHeightDp.dp.toPx() }
    val periodPx = with(density) { 160.dp.toPx() }
    var visible by remember { mutableStateOf(true) }

    // Read the clocks only while on screen — off-screen, this paragraph subscribes
    // to nothing and is not recomposed every frame.
    val neonPhase = if (visible) fx.neon.value else 0f
    val pulse     = if (visible) fx.pulse.value else 0.5f

    // Shades per page brightness: cyan→electric blue on dark pages, deeper blues on
    // light pages (cyan would vanish on cream); glows only on dark pages.
    val neonColors = remember(darkBg) {
        if (darkBg) listOf(Color(0xFF00E5FF), Color(0xFF2979FF), Color(0xFF00B0FF), Color(0xFF00E5FF))
        else        listOf(Color(0xFF0033CC), Color(0xFF0088FF), Color(0xFF0055FF), Color(0xFF0033CC))
    }
    val redColor = if (darkBg) lerp(Color(0xFFC62828), Color(0xFFFF5252), pulse)
                   else        lerp(Color(0xFF8E0000), Color(0xFFE53935), pulse)
    val neonGlow = if (darkBg) Shadow(Color(0xFF00B0FF).copy(alpha = 0.55f), Offset.Zero, 14f) else null
    val redGlow  = if (darkBg) Shadow(redColor.copy(alpha = 0.25f + 0.4f * pulse), Offset.Zero, 8f + 10f * pulse) else null

    val annotated = buildAnnotatedString {
        var capPending = dropCap
        for (seg in segments) {
            var text = seg.text
            if (capPending && text.isNotEmpty()) {
                withStyle(SpanStyle(
                    fontSize   = (settings.fontSize * 2.4f).sp,
                    fontWeight = FontWeight.Black,
                    color      = accent
                )) { append(text.first().toString()) }
                text = text.substring(1)
                capPending = false
            }
            if (text.isEmpty()) continue
            when (seg.fx) {
                Fx.PLAIN -> append(text)
                Fx.NEON  -> withStyle(SpanStyle(
                    brush      = MovingGradientBrush(neonColors, neonPhase * periodPx, periodPx),
                    fontWeight = FontWeight.SemiBold,
                    shadow     = neonGlow
                )) { append(text) }
                Fx.RED   -> withStyle(SpanStyle(
                    color      = redColor,
                    fontWeight = FontWeight.SemiBold,
                    shadow     = redGlow
                )) { append(text) }
            }
        }
    }

    Text(
        text       = annotated,
        fontFamily = MontserratFamily,
        fontSize   = settings.fontSize.sp,
        color      = fg,
        textAlign  = align,
        lineHeight = (settings.fontSize * settings.lineHeight).sp,
        modifier   = modifier.onGloballyPositioned { coords ->
            val b = coords.boundsInWindow()
            visible = b.bottom > -300f && b.top < screenHeightPx + 300f
        }
    )
}

// ── Header: back | settings. Pill moved to audio overlay. ───────────────────
@Composable
private fun ReaderHeader(
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
private fun ChapterNavBar(
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
private fun resolveChapterTitle(
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

// ── Sheet palette (Contents + Settings) ─────────────────────────────────────
// Both sheets share ONE glass surface, copied from the Search overlay: a real
// 30dp blur of what is behind, tinted white in light mode / the app's glass grey
// in dark mode (and a stronger tint on Androids that can't blur, so text stays
// readable). Light/dark follows the reader page — the swatch you picked — so the
// sheet always reads as part of the page behind it. Every colour below is a tone
// of `ink`, so text always counters the surface it sits on.
private const val SHEET_GLASS_ALPHA         = 0.60f   // same as the Search overlay
private const val SHEET_GLASS_ALPHA_NO_BLUR = 0.90f

@Immutable
private class SheetPalette(val dark: Boolean) {
    val glassBase: Color  = if (dark) GlassBase else Color.White
    val ink: Color        = if (dark) Color(0xFFF2EEE8) else Color(0xFF1A1714)
    val onInk: Color      = if (dark) Color(0xFF1A1714) else Color(0xFFFAF6F0)   // text on an ink-filled pill
    val muted: Color      = ink.copy(alpha = 0.62f)
    val dim: Color        = ink.copy(alpha = 0.50f)   // chapters already read
    val chip: Color       = ink.copy(alpha = 0.09f)
    val chipStrong: Color = ink.copy(alpha = 0.15f)
    val hairline: Color   = ink.copy(alpha = 0.12f)
    val handle: Color     = ink.copy(alpha = 0.30f)
}

private val LocalSheetPalette = staticCompositionLocalOf { SheetPalette(false) }

// ── Shared bottom-sheet chrome (settings + table of contents) ────────────────
// One implementation of the drag-handle sheet so the TOC is *exactly* the
// settings menu's look and motion, just taller (heightFraction). Motion
// weighting: Jakub primary (mobile consumer app).
// Enter: slides up from below the screen, 320ms FastOutSlowInEasing.
// Exit: slides back down, 260ms, then calls onDismiss — every dismiss path
// (scrim tap, drag past the threshold, back press, content calling dismiss())
// goes through the same animated exit.
// Drag: direct transform update on the handle; velocity-based dismissal
// (>0.3 px/ms) or >120px dragged. Handle colour changes on press.
// Reduced motion (system "Remove animations"): no slide, instant show/hide.
// heightFraction == null → wraps its content (settings); otherwise fixed to
// that fraction of the screen height (TOC).
@Composable
private fun ReaderSheet(
    accent: Color,
    palette: SheetPalette,
    haze: HazeState,
    onDismiss: () -> Unit,
    heightFraction: Float? = null,
    content: @Composable ColumnScope.(dismiss: () -> Unit) -> Unit
) {
    // Android's real "reduced motion" signal is the system animator scale —
    // "Remove animations" in Accessibility (or Developer options) sets it to 0.
    val context = LocalContext.current
    val reducedMotion = remember(context) {
        Settings.Global.getFloat(
            context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f
        ) == 0f
    }

    // The sheet STARTS off-screen (Animatable's initial value) instead of
    // starting at 0 and being snapped down in LaunchedEffect — that left one
    // frame with the sheet fully visible before it jumped away and slid in.
    // The wrap-content sheet uses 640.dp (always clears it); the tall sheet
    // uses the full screen height so it clears itself too.
    val density       = LocalDensity.current
    val configuration = LocalConfiguration.current
    val hiddenPx = with(density) {
        (if (heightFraction == null) 640.dp else configuration.screenHeightDp.dp).toPx()
    }

    // Animate the sheet's vertical offset via Animatable for smooth snap-back
    val offsetY  = remember { Animatable(if (reducedMotion) 0f else hiddenPx) }
    val scope    = androidx.compose.runtime.rememberCoroutineScope()
    var handleHeld   by remember { mutableStateOf(false) }
    var dismissing   by remember { mutableStateOf(false) }

    // Track drag velocity for threshold dismissal
    var lastDragTime by remember { mutableStateOf(0L) }
    var lastDragY    by remember { mutableStateOf(0f) }

    // Animated exit, then tell the host to remove the sheet. Guarded so a
    // second tap / drag-end during the 260ms exit can't start it twice.
    val dismiss: () -> Unit = {
        if (!dismissing) {
            dismissing = true
            scope.launch {
                if (!reducedMotion) {
                    offsetY.animateTo(hiddenPx, tween(260, easing = FastOutLinearInEasing))
                }
                onDismiss()
            }
        }
    }
    // The drag handler below lives in pointerInput(Unit) and would otherwise
    // keep the first composition's `dismiss` forever.
    val currentDismiss by rememberUpdatedState(dismiss)

    // Back closes the sheet (animated) instead of leaving the reader.
    BackHandler(onBack = dismiss)

    // Enter animation — sheet slides up from below on first composition
    LaunchedEffect(Unit) {
        if (!reducedMotion) {
            offsetY.animateTo(
                targetValue    = 0f,
                animationSpec  = tween(320, easing = FastOutSlowInEasing)
            )
        }
    }

    val fillHeight = if (heightFraction != null) Modifier.fillMaxHeight() else Modifier

    // Search-overlay glass: real blur on Android 12+, a stronger flat tint below.
    val shape     = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    val canBlur   = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val glassTint = palette.glassBase.copy(
        alpha = if (canBlur) SHEET_GLASS_ALPHA else SHEET_GLASS_ALPHA_NO_BLUR
    )

    // Scrim — tapping outside dismisses
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication        = null,
                onClick           = dismiss
            )
    ) {
        // Sheet — anchored to bottom, consumes clicks so they don't reach scrim
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .then(if (heightFraction != null) Modifier.fillMaxHeight(heightFraction) else Modifier)
                .offset { IntOffset(x = 0, y = offsetY.value.roundToInt()) }
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication        = null,
                    onClick           = {}
                )
        ) {
            // No drop shadow: it would bleed through the translucent glass.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(fillHeight)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
                    .clip(shape)
                    .glassBlur(haze, shape, glassTint)
            ) {
                CompositionLocalProvider(LocalSheetPalette provides palette) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(fillHeight)
                            .navigationBarsPadding()
                            .padding(horizontal = 20.dp)
                    ) {
                        // Drag handle — colour changes on press (Jakub: tactile feedback)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 14.dp, bottom = 16.dp)
                                .pointerInput(Unit) {
                                    detectVerticalDragGestures(
                                        onDragStart = {
                                            handleHeld   = true
                                            lastDragTime = System.currentTimeMillis()
                                            lastDragY    = offsetY.value
                                        },
                                        onDragEnd = {
                                            handleHeld = false
                                            val elapsed  = (System.currentTimeMillis() - lastDragTime).coerceAtLeast(1)
                                            val velocity = (offsetY.value - lastDragY) / elapsed
                                            if (offsetY.value > 120f || velocity > 0.3f) {
                                                // Fast downward flick or dragged far enough → dismiss
                                                currentDismiss()
                                            } else {
                                                // Snap back up
                                                scope.launch {
                                                    offsetY.animateTo(
                                                        targetValue   = 0f,
                                                        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)
                                                    )
                                                }
                                            }
                                        },
                                        onDragCancel = {
                                            handleHeld = false
                                            scope.launch {
                                                offsetY.animateTo(0f, spring())
                                            }
                                        },
                                        onVerticalDrag = { _, dragAmount ->
                                            lastDragTime = System.currentTimeMillis()
                                            lastDragY    = offsetY.value
                                            // Only allow dragging downward; resistance when pulling up
                                            val newOffset = (offsetY.value + dragAmount).coerceAtLeast(-20f)
                                            scope.launch {
                                                offsetY.snapTo(newOffset)
                                            }
                                        }
                                    )
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            // Handle pill — accent when held, muted when idle
                            Box(
                                modifier = Modifier
                                    .width(44.dp)
                                    .height(5.dp)
                                    .clip(RoundedCornerShape(50))
                                    .background(if (handleHeld) accent else palette.handle)
                            )
                        }

                        this.content(dismiss)
                    }
                }
            }
        }
    }
}

// ── Draggable settings sheet ─────────────────────────────────────────────────
// The settings content inside the shared ReaderSheet chrome above.
@Composable
private fun DraggableSettingsSheet(
    accent: Color,
    palette: SheetPalette,
    haze: HazeState,
    settings: ReaderSettings,
    swatches: List<ReaderSwatch>,
    onDismiss: () -> Unit,
    onBrightness: (Float) -> Unit,
    onSelectSwatch: (Int) -> Unit,
    onDecreaseFont: () -> Unit,
    onIncreaseFont: () -> Unit,
    onSetAlign: (ReaderTextAlign) -> Unit
) {
    ReaderSheet(accent = accent, palette = palette, haze = haze, onDismiss = onDismiss) { _ ->
        val p = LocalSheetPalette.current

        Text(
            "READER SETTINGS",
            fontFamily    = MontserratFamily,
            fontWeight    = FontWeight.ExtraBold,
            fontSize      = 12.sp,
            letterSpacing = 1.sp,
            color         = p.ink,
            modifier      = Modifier.padding(bottom = 18.dp)
        )

        // Brightness
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier          = Modifier.padding(bottom = 20.dp)
        ) {
            Icon(SolarIcons.Sun, null, tint = p.muted, modifier = Modifier.size(18.dp))
            Slider(
                value         = settings.brightness,
                onValueChange = onBrightness,
                valueRange    = 0f..0.7f,
                modifier      = Modifier.weight(1f).padding(horizontal = 12.dp),
                colors = SliderDefaults.colors(
                    thumbColor         = p.ink,
                    activeTrackColor   = p.ink,
                    inactiveTrackColor = p.chipStrong
                )
            )
            Icon(SolarIcons.Sun, null, tint = p.ink, modifier = Modifier.size(26.dp))
        }

        // Theme swatches
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp)
        ) {
            swatches.forEachIndexed { index, swatch ->
                val selected = index == settings.swatchIndex
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(swatch.background)
                        .border(
                            width = if (selected) 2.5.dp else 1.dp,
                            color = if (selected) p.ink else p.hairline,
                            shape = RoundedCornerShape(16.dp)
                        )
                        .clickable { onSelectSwatch(index) }
                )
            }
        }

        // Font size
        Row(
            modifier              = Modifier.fillMaxWidth().padding(bottom = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            FontSizeButton("–", onDecreaseFont)
            Box(
                Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(p.chip)
                    .padding(horizontal = 28.dp, vertical = 10.dp)
            ) {
                Text(
                    settings.fontSize.toInt().toString(),
                    fontFamily = MontserratFamily,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize   = 20.sp,
                    color      = p.ink
                )
            }
            FontSizeButton("+", onIncreaseFont)
        }

        // Text alignment — a full-width segmented control now that Auto scroll
        // lives in the reader header (three equal cells, selected one highlighted).
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(p.chip)
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            AlignSegment(SolarIcons.AlignLeft,   settings.textAlign == ReaderTextAlign.LEFT,   Modifier.weight(1f)) { onSetAlign(ReaderTextAlign.LEFT) }
            AlignSegment(SolarIcons.AlignCenter, settings.textAlign == ReaderTextAlign.CENTER, Modifier.weight(1f)) { onSetAlign(ReaderTextAlign.CENTER) }
            AlignSegment(SolarIcons.AlignRight,  settings.textAlign == ReaderTextAlign.RIGHT,  Modifier.weight(1f)) { onSetAlign(ReaderTextAlign.RIGHT) }
        }
    }
}

@Composable
private fun FontSizeButton(label: String, onClick: () -> Unit) {
    val p = LocalSheetPalette.current
    Box(
        modifier = Modifier
            .size(52.dp, 44.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(p.chipStrong)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(label, fontFamily = MontserratFamily, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = p.ink)
    }
}

@Composable
private fun AlignSegment(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit
) {
    val p = LocalSheetPalette.current
    Box(
        modifier = modifier
            .height(44.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) p.chipStrong else Color.Transparent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null,
            tint = if (selected) p.ink else p.ink.copy(alpha = 0.35f),
            modifier = Modifier.size(22.dp))
    }
}

// ── Table of contents (content of the shared ReaderSheet) ───────────────────
// No check marks: a chapter you've opened is simply DIMMED, an unread one stays
// full-strength. "Read" = the reader actually opened that chapter (readChapters)
// — individual chapters, NOT everything before the current one.
// "Current" = the chapter that's open (filled row + "Reading" badge).
// "Your place" = the chapter progress is saved at, shown only while a different
// chapter is open (you peeked ahead) so it's clear where the novel will reopen.
// Opens already scrolled to the chapter being read; flipping the sort order
// glides the list back to the top so the change is visible.
@Composable
private fun ColumnScope.ChapterTocContent(
    chapters: List<ChapterLink>,
    currentNum: Int,
    placeNum: Int,
    readChapters: Set<Int>,
    bookmarkedNums: Set<Int>,
    // Hoisted into TocTabs: the pager drops an off-screen page, and swiping back
    // to Contents must keep the sort order and scroll spot instead of resetting.
    listState: LazyListState,
    sortAscending: Boolean,
    onToggleSort: () -> Unit,
    positioned: MutableState<Boolean>,
    onSelect: (Int) -> Unit
) {
    val p = LocalSheetPalette.current

    // distinctBy: a repeated chapter number in the scraped list would show the
    // same row twice (and would crash a keyed list).
    val sorted = remember(chapters, sortAscending) {
        val unique = chapters.distinctBy { it.num }
        if (sortAscending) unique.sortedBy { it.num } else unique.sortedByDescending { it.num }
    }

    // Auto-scroll to the chapter being read, once, as soon as the list exists
    // (the chapter list loads separately from the sheet opening). Instant, not
    // animated: from chapter 1 to chapter 2000 an animation would be a blur.
    // Lands 3 rows below the top so there's context above the current row.
    LaunchedEffect(sorted.isNotEmpty()) {
        if (sorted.isNotEmpty() && !positioned.value) {
            positioned.value = true
            val index = sorted.indexOfFirst { it.num == currentNum }
            listState.scrollToItem((index - 3).coerceAtLeast(0))
        }
    }

    // Sort flipped → glide to the top. Skips the first run (that's just the
    // sheet opening, and it must stay on the current chapter).
    var sortSeen by remember { mutableStateOf(false) }
    LaunchedEffect(sortAscending) {
        if (!sortSeen) sortSeen = true else smoothScrollToTop(listState)
    }

    // Header: title | sort pill (no close button — drag the handle, tap
    // outside, or press back)
    Row(
        modifier              = Modifier.fillMaxWidth().padding(bottom = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically
    ) {
        Text(
            "TABLE OF CONTENTS",
            fontFamily    = MontserratFamily,
            fontWeight    = FontWeight.ExtraBold,
            fontSize      = 12.sp,
            letterSpacing = 1.sp,
            color         = p.ink
        )
        SortPill(ascending = sortAscending, onClick = onToggleSort)
    }

    HorizontalDivider(color = p.hairline)

    if (sorted.isEmpty()) {
        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = p.ink)
        }
    } else {
        LazyColumn(
            state          = listState,
            modifier       = Modifier.fillMaxWidth().weight(1f),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            // No `key` on purpose: with keys, LazyColumn re-anchors to the row
            // that WAS at the top after the sort flips — i.e. it would leap to
            // the far end of a 2000-chapter list before our glide-to-top runs.
            // Rows hold no state, so index-based reuse costs nothing.
            items(sorted) { chapter ->
                val isCurrent = chapter.num == currentNum
                val isRead    = !isCurrent && chapter.num in readChapters
                val isPlace   = !isCurrent && placeNum > 0 && chapter.num == placeNum

                // Text colour is picked against the surface the row sits on:
                // the open chapter is an ink-filled pill (paper text); read
                // chapters are dimmed ink; unread are full ink.
                val onRow = when {
                    isCurrent -> p.onInk
                    isRead    -> p.dim
                    else      -> p.ink
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (isCurrent) p.ink else Color.Transparent)
                        .clickable { onSelect(chapter.num) }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    Text(
                        "Ch.${chapter.num}",
                        fontFamily = MontserratFamily,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize   = 12.sp,
                        color      = onRow,
                        maxLines   = 1,
                        modifier   = Modifier.padding(end = 10.dp)
                    )
                    Text(
                        chapter.title,
                        fontFamily = MontserratFamily,
                        fontWeight = if (isCurrent) FontWeight.Bold else if (isRead) FontWeight.Medium else FontWeight.SemiBold,
                        fontSize   = 14.sp,
                        color      = onRow,
                        maxLines   = 1,
                        overflow   = TextOverflow.Ellipsis,
                        modifier   = Modifier.weight(1f)
                    )
                    // Gold marker: this chapter has a page bookmark
                    if (chapter.num in bookmarkedNums) {
                        Spacer(Modifier.width(8.dp))
                        Icon(
                            SolarIcons.BookmarkBold,
                            contentDescription = "Bookmarked",
                            tint     = BookmarkGold,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    // Right badge
                    when {
                        isCurrent -> {
                            Spacer(Modifier.width(8.dp))
                            Box(
                                Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(onRow.copy(alpha = 0.2f))
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text("Reading", fontFamily = MontserratFamily, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = onRow)
                            }
                        }
                        isPlace -> {
                            Spacer(Modifier.width(8.dp))
                            Box(
                                Modifier
                                    .clip(RoundedCornerShape(50))
                                    .border(1.dp, p.ink.copy(alpha = 0.4f), RoundedCornerShape(50))
                                    .padding(horizontal = 10.dp, vertical = 3.dp)
                            ) {
                                Text("Your place", fontFamily = MontserratFamily, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = p.ink)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Contents | Bookmarks tabs (content of the TOC sheet) ─────────────────────
// Tap a tab or swipe sideways between the two pages. The Contents page keeps its
// scroll spot and sort order because that state lives here, not in the page.
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ColumnScope.TocTabs(
    chapters: List<ChapterLink>,
    currentNum: Int,
    placeNum: Int,
    readChapters: Set<Int>,
    bookmarks: List<ReaderBookmark>,
    onSelect: (Int) -> Unit,
    onOpenBookmark: (ReaderBookmark) -> Unit,
    onRemoveBookmark: (Int) -> Unit
) {
    val pagerState    = rememberPagerState(pageCount = { 2 })
    val scope         = androidx.compose.runtime.rememberCoroutineScope()
    val listState     = rememberLazyListState()
    var sortAscending by remember { mutableStateOf(true) }
    val positioned    = remember { mutableStateOf(false) }
    val bookmarkedNums = remember(bookmarks) { bookmarks.map { it.chapterNum }.toSet() }

    Row(
        modifier              = Modifier.fillMaxWidth().padding(bottom = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        TocTab(
            label    = "Contents",
            selected = pagerState.currentPage == 0,
            onClick  = { scope.launch { pagerState.animateScrollToPage(0) } }
        )
        TocTab(
            label    = if (bookmarks.isEmpty()) "Bookmarks" else "Bookmarks (${bookmarks.size})",
            selected = pagerState.currentPage == 1,
            onClick  = { scope.launch { pagerState.animateScrollToPage(1) } }
        )
    }

    HorizontalPager(
        state    = pagerState,
        modifier = Modifier.fillMaxWidth().weight(1f)
    ) { page ->
        Column(Modifier.fillMaxSize()) {
            if (page == 0) {
                ChapterTocContent(
                    chapters       = chapters,
                    currentNum     = currentNum,
                    placeNum       = placeNum,
                    readChapters   = readChapters,
                    bookmarkedNums = bookmarkedNums,
                    listState      = listState,
                    sortAscending  = sortAscending,
                    onToggleSort   = { sortAscending = !sortAscending },
                    positioned     = positioned,
                    onSelect       = onSelect
                )
            } else {
                BookmarksContent(
                    bookmarks = bookmarks,
                    onOpen    = onOpenBookmark,
                    onRemove  = onRemoveBookmark
                )
            }
        }
    }
}

@Composable
private fun TocTab(label: String, selected: Boolean, onClick: () -> Unit) {
    val p = LocalSheetPalette.current
    Text(
        label,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) p.ink else p.chip)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        fontFamily = MontserratFamily,
        fontWeight = FontWeight.Bold,
        fontSize   = 12.sp,
        color      = if (selected) p.onInk else p.ink
    )
}

// One row per bookmarked chapter (a chapter has at most one bookmark), in chapter
// order. Tap = open that chapter at the saved spot; the bin removes the bookmark.
@Composable
private fun ColumnScope.BookmarksContent(
    bookmarks: List<ReaderBookmark>,
    onOpen: (ReaderBookmark) -> Unit,
    onRemove: (Int) -> Unit
) {
    val p = LocalSheetPalette.current
    if (bookmarks.isEmpty()) {
        Column(
            modifier              = Modifier.fillMaxWidth().weight(1f).padding(horizontal = 32.dp),
            horizontalAlignment   = Alignment.CenterHorizontally,
            verticalArrangement   = Arrangement.Center
        ) {
            Icon(
                SolarIcons.Bookmark,
                contentDescription = null,
                tint     = p.muted,
                modifier = Modifier.size(40.dp)
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "No bookmarks yet",
                fontFamily = MontserratFamily,
                fontWeight = FontWeight.Bold,
                fontSize   = 15.sp,
                color      = p.ink
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Tap the bookmark icon next to Settings while reading to save your spot.",
                fontFamily = MontserratFamily,
                fontSize   = 13.sp,
                color      = p.muted,
                textAlign  = TextAlign.Center
            )
        }
        return
    }

    LazyColumn(
        modifier       = Modifier.fillMaxWidth().weight(1f),
        contentPadding = PaddingValues(bottom = 12.dp)
    ) {
        items(bookmarks, key = { it.id }) { b ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(p.chip)
                    .clickable { onOpen(b) }
                    .padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    SolarIcons.BookmarkBold,
                    contentDescription = null,
                    tint     = BookmarkGold,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Ch.${b.chapterNum} · ${b.chapterTitle}",
                        fontFamily = MontserratFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize   = 14.sp,
                        color      = p.ink,
                        maxLines   = 1,
                        overflow   = TextOverflow.Ellipsis
                    )
                    Text(
                        "${(b.fraction * 100).roundToInt()}% through the chapter",
                        fontFamily = MontserratFamily,
                        fontSize   = 12.sp,
                        color      = p.muted
                    )
                }
                IconButton(onClick = { onRemove(b.chapterNum) }) {
                    Icon(
                        SolarIcons.TrashBin,
                        contentDescription = "Remove bookmark",
                        tint     = p.muted,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

// Sort button — a labelled pill with a swap icon that flips 180° when the
// order changes (replaces the bare chevron, which read as "expand/collapse").
@Composable
private fun SortPill(ascending: Boolean, onClick: () -> Unit) {
    val p = LocalSheetPalette.current
    val rotation by animateFloatAsState(
        targetValue   = if (ascending) 0f else 180f,
        animationSpec = tween(320, easing = FastOutSlowInEasing),
        label         = "sortRotation"
    )
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(p.chip)
            .border(1.dp, p.hairline, RoundedCornerShape(50))
            .clickable(onClickLabel = "Reverse chapter order", onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            SolarIcons.Sort,
            contentDescription = null,
            tint     = p.ink,
            modifier = Modifier
                .size(18.dp)
                .graphicsLayer { rotationZ = rotation }
        )
        Spacer(Modifier.width(6.dp))
        Text(
            if (ascending) "Oldest first" else "Newest first",
            fontFamily = MontserratFamily,
            fontWeight = FontWeight.Bold,
            fontSize   = 12.sp,
            color      = p.ink
        )
    }
}

// Glide to the top of a (possibly huge) list slowly enough to be seen. A list
// of 2000+ chapters can't be animated end-to-end, so if we're far down it
// jumps to 8 rows from the top first and animates the remaining distance.
private const val TOC_SORT_SCROLL_MS = 650

private suspend fun smoothScrollToTop(state: LazyListState) {
    if (state.firstVisibleItemIndex > 8) state.scrollToItem(8)

    // Row pitch (height + gap) from two neighbouring visible rows.
    val visible = state.layoutInfo.visibleItemsInfo
    val pitch = when {
        visible.size >= 2 -> (visible[1].offset - visible[0].offset).toFloat()
        visible.size == 1 -> visible[0].size.toFloat()
        else              -> 0f
    }
    val distance = state.firstVisibleItemIndex * pitch + state.firstVisibleItemScrollOffset
    if (distance > 0f) {
        state.animateScrollBy(-distance, tween(TOC_SORT_SCROLL_MS, easing = FastOutSlowInEasing))
    }
    // Pin the exact top in case the distance estimate was a few px off.
    state.scrollToItem(0)
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
private fun AudioComingSoonOverlay(
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
