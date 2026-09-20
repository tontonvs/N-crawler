package com.noven.ncrawler.ui.screens.reader

import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
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
import com.noven.ncrawler.ui.theme.MontserratFamily
import com.noven.ncrawler.viewmodel.ReaderSettings
import com.noven.ncrawler.viewmodel.ReaderSwatch
import com.noven.ncrawler.viewmodel.ReaderTextAlign
import com.noven.ncrawler.viewmodel.ReaderUiState
import com.noven.ncrawler.viewmodel.ReaderViewModel
import kotlinx.coroutines.launch
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
    LaunchedEffect(state) { if (state is ReaderUiState.Success) scrollState.scrollTo(0) }

    LaunchedEffect(scrollState.value) {
        kotlinx.coroutines.delay(600)
        vm.saveScrollPosition(scrollState.value)
    }

    val progress = if (scrollState.maxValue > 0)
        (scrollState.value.toFloat() / scrollState.maxValue).coerceIn(0f, 1f)
    else 0f

    val noRipple = remember { MutableInteractionSource() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bg)
            .clickable(interactionSource = noRipple, indication = null) {
                if (!showSettings && !showToc && !showAudioOverlay) showControls = !showControls
            }
    ) {
        when (val s = state) {
            is ReaderUiState.Loading -> {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = accent)
            }
            is ReaderUiState.Error -> {
                Column(
                    modifier            = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Default.WifiOff, contentDescription = null,
                        modifier = Modifier.size(48.dp), tint = fg.copy(alpha = 0.5f))
                    Spacer(Modifier.height(12.dp))
                    Text(s.message, color = fg.copy(alpha = 0.7f), textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 32.dp))
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = { vm.load(slug, chapterNum) },
                        colors  = ButtonDefaults.buttonColors(containerColor = accent, contentColor = bg)
                    ) { Text("Retry") }
                }
            }
            is ReaderUiState.Success -> {
                ReaderContent(
                    chapter     = s.chapter,
                    title       = chapterTitle ?: "Chapter $currentNum",
                    settings    = settings,
                    fg          = fg,
                    accent      = accent,
                    scrollState = scrollState
                )
            }
        }

        // ── Brightness dimming overlay ────────────────────────────────────
        if (settings.brightness > 0f) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = settings.brightness)))
        }

        // ── Top header ────────────────────────────────────────────────────
        AnimatedVisibility(
            visible  = showControls,
            enter    = fadeIn(tween(220)) + slideInVertically(tween(220, easing = FastOutSlowInEasing)),
            exit     = fadeOut(tween(160)) + slideOutVertically(tween(160, easing = FastOutSlowInEasing)),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            ReaderHeader(
                fg              = fg,
                accent          = accent,
                bg              = bg,
                audioSelected   = audioSelected,
                onBack          = onBack,
                onAudioClick    = { audioSelected = true; showAudioOverlay = true },
                onTextClick     = { audioSelected = false },
                onSettingsClick = { showToc = false; showSettings = !showSettings }
            )
        }

        // ── Bottom scrim (always present, behind nav bar) ─────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(bg.copy(alpha = 0f), bg.copy(alpha = 0.82f), bg.copy(alpha = 0.97f))
                    )
                )
        )

        // ── Bottom chapter nav bar ────────────────────────────────────────
        AnimatedVisibility(
            visible  = showControls,
            enter    = fadeIn(tween(220)) + slideInVertically(tween(220, easing = FastOutSlowInEasing)) { it },
            exit     = fadeOut(tween(160)) + slideOutVertically(tween(160, easing = FastOutSlowInEasing)) { it },
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
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

        // ── Settings sheet (drag-to-dismiss) ──────────────────────────────
        if (showSettings) {
            DraggableSettingsSheet(
                fg             = fg,
                accent         = accent,
                settings       = settings,
                swatches       = swatches,
                onDismiss      = { showSettings = false },
                onBrightness   = vm::setBrightness,
                onSelectSwatch = vm::selectSwatch,
                onDecreaseFont = vm::decreaseFontSize,
                onIncreaseFont = vm::increaseFontSize,
                onSetAlign     = vm::setTextAlign
            )
        }

        // ── TOC sheet — same chrome as the settings sheet (drag handle,
        // slide-up/slide-down animation, drag-to-dismiss), just taller. No
        // close button: drag the dash down, tap outside, or press back.
        if (showToc) {
            ReaderSheet(
                accent         = accent,
                onDismiss      = { showToc = false },
                heightFraction = 0.85f
            ) { dismiss ->
                ChapterTocContent(
                    chapters     = chapterList,
                    currentNum   = currentNum,
                    readChapters = readChapters,
                    onSelect     = { num -> vm.jumpTo(num); dismiss() }
                )
            }
        }

        // ── Audio overlay ─────────────────────────────────────────────────
        AnimatedVisibility(
            visible  = showAudioOverlay,
            enter    = fadeIn(),
            exit     = fadeOut(),
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

// ── Reading content ──────────────────────────────────────────────────────────
@Composable
private fun ReaderContent(
    chapter: ChapterEntity,
    title: String,
    settings: ReaderSettings,
    fg: Color,
    accent: Color,
    scrollState: androidx.compose.foundation.ScrollState
) {
    val align = when (settings.textAlign) {
        ReaderTextAlign.LEFT    -> TextAlign.Left
        ReaderTextAlign.CENTER  -> TextAlign.Center
        ReaderTextAlign.RIGHT   -> TextAlign.Right
        ReaderTextAlign.JUSTIFY -> TextAlign.Justify
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(top = 150.dp, bottom = 170.dp, start = 24.dp, end = 24.dp)
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
            modifier   = Modifier.fillMaxWidth().padding(bottom = 24.dp)
        )

        val paragraphs = chapter.content
            .split("\n")
            .map { it.trim() }
            .filter { it.isNotBlank() }

        paragraphs.forEachIndexed { index, para ->
            if (index == 0 && para.isNotEmpty()) {
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
                    color      = fg,
                    textAlign  = align,
                    lineHeight = (settings.fontSize * settings.lineHeight).sp,
                    modifier   = Modifier.fillMaxWidth().padding(bottom = (settings.fontSize * 0.8f).dp)
                )
            } else {
                Text(
                    text       = para,
                    fontFamily = MontserratFamily,
                    fontSize   = settings.fontSize.sp,
                    color      = fg,
                    textAlign  = align,
                    lineHeight = (settings.fontSize * settings.lineHeight).sp,
                    modifier   = Modifier.fillMaxWidth().padding(bottom = (settings.fontSize * 0.8f).dp)
                )
            }
        }
    }
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
    onSettingsClick: () -> Unit
) {
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
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = fg, modifier = Modifier.size(24.dp))
                }
                ReaderIconButton(fg = fg, onClick = onSettingsClick) {
                    Icon(Icons.Default.Settings, "Settings", tint = fg, modifier = Modifier.size(24.dp))
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
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(fg.copy(alpha = 0.13f))
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
            .clip(RoundedCornerShape(50))
            .background(fg.copy(alpha = 0.13f))
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
                    .clip(CircleShape)
                    .background(if (canGoPrev) fg.copy(alpha = 0.13f) else fg.copy(alpha = 0.05f))
                    .clickable(enabled = canGoPrev, onClick = onPrev),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
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
                    .clip(RoundedCornerShape(24.dp))
                    .background(fg.copy(alpha = 0.10f))
                    .clickable(onClick = onOpenToc)
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment     = Alignment.CenterVertically,
                    modifier              = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        Icons.Default.MenuBook,
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
                    .clip(CircleShape)
                    .background(fg.copy(alpha = 0.13f))
                    .clickable(onClick = onNext),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowForward,
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

// Sheet + TOC palette. Every TOC text colour is picked *against the surface it
// sits on* (see onColorFor) instead of a fixed grey / the adaptive swatch
// accent — the accent could land on nearly the same tone as the sheet.
private val SheetCream   = Color(0xFFF5F0EA)   // surface shared by settings + TOC sheets
private val TocInk       = Color(0xFF1A1714)
private val TocPaper     = Color(0xFFFAF6F0)
private val TocReadBg    = Color(0xFFEDE7DF)
private val TocReadCheck = Color(0xFF7D746B)   // warm grey — was green

// Ink on light surfaces, paper on dark ones. 0.179 is the WCAG luminance at
// which black and white text have equal contrast, so this always picks the
// better of the two.
private fun onColorFor(background: Color): Color =
    if (background.luminance() > 0.179f) TocInk else TocPaper

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
    onDismiss: () -> Unit,
    heightFraction: Float? = null,
    content: @Composable ColumnScope.(dismiss: () -> Unit) -> Unit
) {
    // FIX: Compose's AccessibilityManager has no `isEnabled` (and "any
    // accessibility service on" isn't "reduced motion" anyway). Android's
    // real signal is the system animator scale — "Remove animations" in
    // Accessibility (or Developer options) sets it to 0.
    val context = LocalContext.current
    val reducedMotion = remember(context) {
        Settings.Global.getFloat(
            context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f
        ) == 0f
    }

    // FIX: the sheet STARTS off-screen (Animatable's initial value) instead of
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
            Surface(
                modifier        = Modifier
                    .fillMaxWidth()
                    .then(fillHeight)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                shape           = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                color           = SheetCream,
                shadowElevation = 16.dp
            ) {
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
                                .background(
                                    if (handleHeld) accent else Color(0xFFB8AFA4)
                                )
                        )
                    }

                    this.content(dismiss)
                }
            }
        }
    }
}

// ── Draggable settings sheet (change 7) ──────────────────────────────────────
// Now just the settings content inside the shared ReaderSheet chrome above.
@Composable
private fun DraggableSettingsSheet(
    fg: Color,
    accent: Color,
    settings: ReaderSettings,
    swatches: List<ReaderSwatch>,
    onDismiss: () -> Unit,
    onBrightness: (Float) -> Unit,
    onSelectSwatch: (Int) -> Unit,
    onDecreaseFont: () -> Unit,
    onIncreaseFont: () -> Unit,
    onSetAlign: (ReaderTextAlign) -> Unit
) {
    ReaderSheet(accent = accent, onDismiss = onDismiss) { _ ->
        Text(
            "READER SETTINGS",
            fontFamily    = MontserratFamily,
            fontWeight    = FontWeight.ExtraBold,
            fontSize      = 12.sp,
            letterSpacing = 1.sp,
            color         = Color(0xFF1A1714),
            modifier      = Modifier.padding(bottom = 18.dp)
        )

        // Brightness
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier          = Modifier.padding(bottom = 20.dp)
        ) {
            Icon(Icons.Default.LightMode, null, tint = Color(0xFF6B6259), modifier = Modifier.size(18.dp))
            Slider(
                value         = settings.brightness,
                onValueChange = onBrightness,
                valueRange    = 0f..0.7f,
                modifier      = Modifier.weight(1f).padding(horizontal = 12.dp),
                colors = SliderDefaults.colors(
                    thumbColor         = Color.White,
                    activeTrackColor   = Color(0xFF2D2420),
                    inactiveTrackColor = Color(0xFFDCD5CB)
                )
            )
            Icon(Icons.Default.LightMode, null, tint = Color(0xFF1A1714), modifier = Modifier.size(26.dp))
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
                            width = if (selected) 2.5.dp else 0.dp,
                            color = if (selected) Color(0xFF1A1714) else Color.Transparent,
                            shape = RoundedCornerShape(16.dp)
                        )
                        .shadow(if (selected) 4.dp else 0.dp, RoundedCornerShape(16.dp))
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
                    .background(Color(0xFFEDE7DF))
                    .padding(horizontal = 28.dp, vertical = 10.dp)
            ) {
                Text(
                    settings.fontSize.toInt().toString(),
                    fontFamily = MontserratFamily,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize   = 20.sp,
                    color      = Color(0xFF1A1714)
                )
            }
            FontSizeButton("+", onIncreaseFont)
        }

        // Text alignment
        Row(
            modifier              = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            AlignButton(Icons.Filled.FormatAlignLeft,    settings.textAlign == ReaderTextAlign.LEFT)    { onSetAlign(ReaderTextAlign.LEFT) }
            AlignButton(Icons.Filled.FormatAlignCenter,  settings.textAlign == ReaderTextAlign.CENTER)  { onSetAlign(ReaderTextAlign.CENTER) }
            AlignButton(Icons.Filled.FormatAlignRight,   settings.textAlign == ReaderTextAlign.RIGHT)   { onSetAlign(ReaderTextAlign.RIGHT) }
            AlignButton(Icons.Filled.FormatAlignJustify, settings.textAlign == ReaderTextAlign.JUSTIFY) { onSetAlign(ReaderTextAlign.JUSTIFY) }
        }
    }
}

@Composable
private fun FontSizeButton(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(52.dp, 44.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFFE3DCD2))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(label, fontFamily = MontserratFamily, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1A1714))
    }
}

@Composable
private fun AlignButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit
) {
    IconButton(onClick = onClick) {
        Icon(icon, contentDescription = null,
            tint = if (selected) Color(0xFF1A1714) else Color(0xFFB8AFA4))
    }
}

// ── Table of contents (content of the shared ReaderSheet) ───────────────────
// "Read" = the reader actually opened that chapter (readChapters) — individual
// chapters, NOT everything before the current one. Shown as a grey check.
// "Current" = chapter.num == currentNum (the "Reading" badge).
// Opens already scrolled to the chapter being read; flipping the sort order
// glides the list back to the top so the change is visible.
@Composable
private fun ColumnScope.ChapterTocContent(
    chapters: List<ChapterLink>,
    currentNum: Int,
    readChapters: Set<Int>,
    onSelect: (Int) -> Unit
) {
    var sortAscending by remember { mutableStateOf(true) }
    val listState = rememberLazyListState()

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
    var positioned by remember { mutableStateOf(false) }
    LaunchedEffect(sorted.isNotEmpty()) {
        if (sorted.isNotEmpty() && !positioned) {
            positioned = true
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
            color         = TocInk
        )
        SortPill(ascending = sortAscending, onClick = { sortAscending = !sortAscending })
    }

    HorizontalDivider(color = Color(0xFFE3DCD2))

    if (sorted.isEmpty()) {
        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Color(0xFF3C1D18))
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

                // The surface this row actually sits on — text colours below
                // are derived from it, so they always counter the background.
                val rowBg = when {
                    isCurrent -> TocInk
                    isRead    -> TocReadBg
                    else      -> SheetCream
                }
                val onRow = onColorFor(rowBg)

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (isCurrent || isRead) rowBg else Color.Transparent)
                        .clickable { onSelect(chapter.num) }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    // Chapter number — full-contrast ink/paper, 12sp (was a
                    // 10sp grey that nearly vanished on the read-row tint)
                    Text(
                        "Ch.${chapter.num}",
                        fontFamily = MontserratFamily,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize   = 12.sp,
                        color      = onRow,
                        maxLines   = 1,
                        modifier   = Modifier.padding(end = 10.dp)
                    )
                    // Title — takes available space. Read titles are muted but
                    // stay above AA contrast (the old grey was ~2.4:1).
                    Text(
                        chapter.title,
                        fontFamily = MontserratFamily,
                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                        fontSize   = 14.sp,
                        color      = if (isRead) Color(0xFF6B6259) else onRow,
                        maxLines   = 1,
                        overflow   = TextOverflow.Ellipsis,
                        modifier   = Modifier.weight(1f)
                    )
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
                        isRead -> {
                            Spacer(Modifier.width(8.dp))
                            ThickCheck(color = TocReadCheck)
                        }
                    }
                }
            }
        }
    }
}

// Sort button — a labelled pill with a swap icon that flips 180° when the
// order changes (replaces the bare chevron, which read as "expand/collapse").
@Composable
private fun SortPill(ascending: Boolean, onClick: () -> Unit) {
    val rotation by animateFloatAsState(
        targetValue   = if (ascending) 0f else 180f,
        animationSpec = tween(320, easing = FastOutSlowInEasing),
        label         = "sortRotation"
    )
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(TocReadBg)
            .border(1.dp, Color(0xFFDCD5CB), RoundedCornerShape(50))
            .clickable(onClickLabel = "Reverse chapter order", onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Default.SwapVert,
            contentDescription = null,
            tint     = TocInk,
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
            color      = TocInk
        )
    }
}

// Hand-drawn check so the stroke width is exact: 5dp, round caps, in a 24dp box
// (the Material check icon is fixed-weight and can't be thickened).
@Composable
private fun ThickCheck(
    color: Color,
    modifier: Modifier = Modifier,
    boxSize: Dp = 24.dp,
    strokeWidth: Dp = 5.dp
) {
    Canvas(
        modifier = modifier
            .size(boxSize)
            .semantics { contentDescription = "Read" }
    ) {
        val w = size.width
        val h = size.height
        val check = Path().apply {
            // points sit far enough inside the box that the 5dp round caps
            // (2.5dp past each end point) never get clipped
            moveTo(w * 0.15f, h * 0.55f)
            lineTo(w * 0.40f, h * 0.78f)
            lineTo(w * 0.86f, h * 0.24f)
        }
        drawPath(
            path  = check,
            color = color,
            style = Stroke(width = strokeWidth.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
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
// Change 1: pill moved here so user can switch back to Text without dismissing.
// Ring radius widened to 130f (from 100f) so it doesn't cover the info text.
// Info text pushed further down (padding top 280dp → well below the ring).
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

    var started by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { started = true }

    val spread by animateFloatAsState(
        targetValue   = if (started) 1f else 0f,
        animationSpec = tween(950, easing = FastOutSlowInEasing),
        label         = "spread"
    )
    val textAlpha by animateFloatAsState(
        targetValue   = if (spread > 0.8f) 1f else 0f,
        animationSpec = tween(500),
        label         = "text"
    )
    val infinite = rememberInfiniteTransition(label = "spin")
    val rotation by infinite.animateFloat(
        initialValue  = 0f,
        targetValue   = 360f,
        animationSpec = infiniteRepeatable(tween(7000, easing = LinearEasing)),
        label         = "rotation"
    )

    val orbitIcons = remember {
        listOf(
            Icons.Default.Headphones, Icons.Default.GraphicEq, Icons.Default.PlayArrow,
            Icons.Default.VolumeUp,   Icons.Default.Mic,       Icons.Default.MusicNote
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
        // Orbit ring — wider (300dp container, 130f radius)
        Box(
            modifier         = Modifier
                .offset(y = (-30).dp)
                .size(300.dp)
                .graphicsLayer { rotationZ = rotation * spread },
            contentAlignment = Alignment.Center
        ) {
            orbitIcons.forEachIndexed { i, icon ->
                val angle  = (360f / orbitIcons.size) * i
                val radius = 130f
                val rad    = Math.toRadians(angle.toDouble())
                val tx     = (radius * cos(rad)).toFloat()
                val ty     = (radius * sin(rad)).toFloat()
                Box(
                    modifier = Modifier
                        .offset(x = (tx * spread).dp, y = (ty * spread).dp)
                        .size(44.dp)
                        .alpha(spread)
                        .clip(CircleShape)
                        .background(content.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(22.dp))
                }
            }
            Box(
                modifier         = Modifier.graphicsLayer { rotationZ = -rotation * spread },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.MusicNote,
                    contentDescription = null,
                    tint     = content,
                    modifier = Modifier.size(56.dp).alpha(spread)
                )
            }
        }

        // Info text + pill — below the ring, clear of the orbit
        Column(
            modifier            = Modifier
                .align(Alignment.Center)
                .padding(top = 280.dp)
                .alpha(textAlpha),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "Audio coming soon",
                fontFamily = MontserratFamily,
                fontWeight = FontWeight.ExtraBold,
                fontSize   = 16.sp,
                color      = content
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Tap anywhere to go back to reading",
                fontFamily = MontserratFamily,
                fontSize   = 12.sp,
                color      = content.copy(alpha = 0.6f)
            )
            Spacer(Modifier.height(20.dp))
            // Pill in the overlay so user can switch back to Text mode
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
