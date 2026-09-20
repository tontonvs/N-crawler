package com.noven.ncrawler.ui.screens.reader

import android.provider.Settings
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
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
                title     = (state as? ReaderUiState.Success)?.chapter?.title ?: "Chapter $chapterNum",
                chapterNum = vm.currentChapterNum,
                progress  = progress,
                canGoPrev = vm.currentChapterNum > 1,
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

        // ── TOC drawer ────────────────────────────────────────────────────
        AnimatedVisibility(
            visible  = showToc,
            enter    = fadeIn() + slideInVertically(initialOffsetY = { it }),
            exit     = fadeOut() + slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier.fillMaxSize()
        ) {
            ChapterTocDrawer(
                chapters   = chapterList,
                currentNum = vm.currentChapterNum,
                onSelect   = { num -> vm.jumpTo(num); showToc = false },
                onClose    = { showToc = false }
            )
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
        Text(
            text       = chapter.title,
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
    title: String,
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

// ── Draggable settings sheet (change 7) ──────────────────────────────────────
// Motion weighting: Jakub primary (mobile consumer app).
// Enter: slideInVertically upward + fadeIn, 320ms FastOutSlowInEasing.
// Exit: slideOutVertically downward, 260ms. Subtler exit per Jakub.
// Drag: direct transform update (no CSS variable cascade). Velocity-based
// dismissal (>0.3 px/ms threshold). Handle colour changes on press.
// prefers-reduced-motion: when accessibility service flags reduced motion,
// skip slide animations — instant show/hide only.
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

    // FIX: the sheet now STARTS off-screen (Animatable's initial value) instead
    // of starting at 0 and being snapped down in LaunchedEffect — that left one
    // frame with the sheet fully visible before it jumped away and slid in.
    // 640.dp (converted to px) always clears the sheet, unlike the old flat
    // 1000px, which on dense screens was shorter than the sheet itself.
    val startOffsetPx = with(LocalDensity.current) { 640.dp.toPx() }

    // Animate the sheet's vertical offset via Animatable for smooth snap-back
    val offsetY  = remember { Animatable(if (reducedMotion) 0f else startOffsetPx) }
    val scope    = androidx.compose.runtime.rememberCoroutineScope()
    var isDragging   by remember { mutableStateOf(false) }
    var handleHeld   by remember { mutableStateOf(false) }

    // Track drag velocity for threshold dismissal
    var lastDragTime by remember { mutableStateOf(0L) }
    var lastDragY    by remember { mutableStateOf(0f) }

    // Enter animation — sheet slides up from bottom on first composition
    LaunchedEffect(Unit) {
        if (!reducedMotion) {
            offsetY.animateTo(
                targetValue    = 0f,
                animationSpec  = tween(320, easing = FastOutSlowInEasing)
            )
        }
    }

    // Scrim — tapping outside dismisses
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication        = null,
                onClick           = onDismiss
            )
    ) {
        // Sheet — anchored to bottom, consumes clicks so they don't reach scrim
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .offset { IntOffset(x = 0, y = offsetY.value.roundToInt()) }
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication        = null,
                    onClick           = {}
                )
        ) {
            Surface(
                modifier        = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                shape           = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                color           = Color(0xFFF5F0EA),
                shadowElevation = 16.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
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
                                        isDragging   = true
                                        lastDragTime = System.currentTimeMillis()
                                        lastDragY    = offsetY.value
                                    },
                                    onDragEnd = {
                                        handleHeld = false
                                        isDragging = false
                                        val elapsed  = (System.currentTimeMillis() - lastDragTime).coerceAtLeast(1)
                                        val velocity = (offsetY.value - lastDragY) / elapsed
                                        if (offsetY.value > 120f || velocity > 0.3f) {
                                            // Fast downward flick or dragged far enough → dismiss
                                            onDismiss()
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
                                        isDragging = false
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

// ── Table of contents drawer ─────────────────────────────────────────────────
// Changes: sort toggle (asc/desc by chapter number) + read/current indicators.
// "Read" = chapter.num < currentNum (linear reading assumption).
// "Current" = chapter.num == currentNum (existing "Reading" badge).
@Composable
private fun ChapterTocDrawer(
    chapters: List<ChapterLink>,
    currentNum: Int,
    onSelect: (Int) -> Unit,
    onClose: () -> Unit
) {
    var sortAscending by remember { mutableStateOf(true) }

    Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFFF5F0EA)) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {

            // Header row: title | sort button | close
            Row(
                modifier              = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Text(
                    "Table of Contents",
                    fontFamily = MontserratFamily,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize   = 18.sp,
                    color      = Color(0xFF1A1714)
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Sort toggle
                    IconButton(onClick = { sortAscending = !sortAscending }) {
                        Icon(
                            if (sortAscending) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                            contentDescription = if (sortAscending) "Sort descending" else "Sort ascending",
                            tint = Color(0xFF6B6259)
                        )
                    }
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.Close, "Close", tint = Color(0xFF9C9188))
                    }
                }
            }

            HorizontalDivider(color = Color(0xFFE3DCD2))

            if (chapters.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color(0xFF3C1D18))
                }
            } else {
                val sorted = remember(chapters, sortAscending) {
                    if (sortAscending) chapters.sortedBy { it.num }
                    else               chapters.sortedByDescending { it.num }
                }

                LazyColumn(
                    modifier       = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    items(sorted, key = { it.num }) { chapter ->
                        val isCurrent = chapter.num == currentNum
                        val isRead    = chapter.num < currentNum

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 6.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(
                                    when {
                                        isCurrent -> Color(0xFF1A1714)
                                        isRead    -> Color(0xFFEDE7DF)
                                        else      -> Color.Transparent
                                    }
                                )
                                .clickable { onSelect(chapter.num) }
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment     = Alignment.CenterVertically
                        ) {
                            // Chapter number badge
                            Text(
                                "Ch.${chapter.num}",
                                fontFamily = MontserratFamily,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize   = 10.sp,
                                color      = when {
                                    isCurrent -> Color.White
                                    isRead    -> Color(0xFF9C9188)
                                    else      -> Color(0xFF9C9188)
                                },
                                modifier   = Modifier.padding(end = 10.dp)
                            )
                            // Title — takes available space
                            Text(
                                chapter.title,
                                fontFamily = MontserratFamily,
                                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                                fontSize   = 14.sp,
                                color      = when {
                                    isCurrent -> Color.White
                                    isRead    -> Color(0xFF9C9188)
                                    else      -> Color(0xFF1A1714)
                                },
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
                                            .background(Color.White.copy(alpha = 0.2f))
                                            .padding(horizontal = 10.dp, vertical = 4.dp)
                                    ) {
                                        Text("Reading", fontFamily = MontserratFamily, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                                    }
                                }
                                isRead -> {
                                    Spacer(Modifier.width(8.dp))
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = "Read",
                                        tint     = Color(0xFF9C9188),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
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
