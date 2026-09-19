package com.noven.ncrawler.ui.screens.reader

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
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
import kotlin.math.cos
import kotlin.math.sin

// ── Glass tokens (this screen's own — approximated blur, matching the same
// semi-opaque-fill + hairline-border approach used everywhere else in the
// app, since there's no cheap real backdrop blur pre-API 31) ─────────────────
private fun glassFill(fg: Color)   = fg.copy(alpha = 0.12f)
private fun glassBorder(fg: Color) = fg.copy(alpha = 0.22f)

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

    var showControls     by remember { mutableStateOf(true) }
    var showSettings      by remember { mutableStateOf(false) }
    var showToc           by remember { mutableStateOf(false) }
    var showAudioOverlay  by remember { mutableStateOf(false) }
    var audioSelected     by remember { mutableStateOf(false) }

    val scrollState = rememberScrollState()
    LaunchedEffect(state) { if (state is ReaderUiState.Success) scrollState.scrollTo(0) }

    // Debounced scroll-position save — waits for scrolling to pause rather
    // than writing on every pixel of movement.
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
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color    = accent
                )
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

        // ── Brightness dimming overlay ───────────────────────────────────
        if (settings.brightness > 0f) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = settings.brightness))
            )
        }

        // ── Header + chapter nav / progress ──────────────────────────────
        AnimatedVisibility(
            visible  = showControls,
            enter    = fadeIn() + slideInVertically(),
            exit     = fadeOut() + slideOutVertically(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Column {
                ReaderHeader(
                    fg              = fg,
                    accent          = accent,
                    audioSelected   = audioSelected,
                    onBack          = onBack,
                    onAudioClick    = { audioSelected = true; showAudioOverlay = true },
                    onTextClick     = { audioSelected = false },
                    onSettingsClick = { showToc = false; showSettings = !showSettings }
                )
                ChapterNavBar(
                    fg           = fg,
                    accent       = accent,
                    title        = (state as? ReaderUiState.Success)?.chapter?.title ?: "Chapter $chapterNum",
                    progress     = progress,
                    canGoPrev    = vm.currentChapterNum > 1,
                    onPrev       = vm::loadPrev,
                    onNext       = vm::loadNext,
                    onOpenToc    = { showSettings = false; showToc = !showToc }
                )
            }
        }

        // ── Settings bottom sheet ─────────────────────────────────────────
        AnimatedVisibility(
            visible  = showSettings,
            enter    = fadeIn() + slideInVertically(initialOffsetY = { it }),
            exit     = fadeOut() + slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            ReaderSettingsSheet(
                settings    = settings,
                swatches    = swatches,
                onClose     = { showSettings = false },
                onBrightness = vm::setBrightness,
                onSelectSwatch = vm::selectSwatch,
                onDecreaseFont = vm::decreaseFontSize,
                onIncreaseFont = vm::increaseFontSize,
                onSetAlign     = vm::setTextAlign
            )
        }

        // ── Chapter TOC drawer ────────────────────────────────────────────
        AnimatedVisibility(
            visible = showToc,
            enter   = fadeIn() + slideInVertically(initialOffsetY = { it }),
            exit    = fadeOut() + slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier.fillMaxSize()
        ) {
            ChapterTocDrawer(
                chapters   = chapterList,
                currentNum = vm.currentChapterNum,
                onSelect   = { num -> vm.jumpTo(num); showToc = false },
                onClose    = { showToc = false }
            )
        }

        // ── Audio "coming soon" overlay ───────────────────────────────────
        AnimatedVisibility(
            visible = showAudioOverlay,
            enter   = fadeIn(),
            exit    = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            AudioComingSoonOverlay(readerBg = bg) {
                showAudioOverlay = false
                audioSelected = false
            }
        }
    }
}

// ── Reading content, with a drop-cap first paragraph ───────────────────────
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
            .padding(top = 128.dp, bottom = 140.dp, start = 24.dp, end = 24.dp)
    ) {
        Text(
            text       = chapter.title,
            fontFamily = MontserratFamily,
            fontWeight = FontWeight.ExtraBold,
            fontSize   = 26.sp,
            color      = fg,
            textAlign  = TextAlign.Center,
            modifier   = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp)
        )

        val paragraphs = chapter.content
            .split("\n")
            .map { it.trim() }
            .filter { it.isNotBlank() }

        paragraphs.forEachIndexed { index, para ->
            if (index == 0 && para.isNotEmpty()) {
                // Drop-cap approximation: the first letter set large and
                // bold inline — Compose has no CSS-style float/wrap, so this
                // reads as "big first letter" rather than a true multi-line
                // wrap-around, but carries the same visual intent.
                val annotated = buildAnnotatedString {
                    withStyle(SpanStyle(fontSize = (settings.fontSize * 2.4f).sp, fontWeight = FontWeight.Black,
                        fontFamily = MontserratFamily, color = accent)) {
                        append(para.first().toString())
                    }
                    append(para.substring(1))
                }
                Text(
                    text      = annotated,
                    fontSize  = settings.fontSize.sp,
                    color     = fg,
                    textAlign = align,
                    lineHeight = (settings.fontSize * settings.lineHeight).sp,
                    modifier  = Modifier
                        .fillMaxWidth()
                        .padding(bottom = (settings.fontSize * 0.8f).dp)
                )
            } else {
                Text(
                    text       = para,
                    fontSize   = settings.fontSize.sp,
                    color      = fg,
                    textAlign  = align,
                    lineHeight = (settings.fontSize * settings.lineHeight).sp,
                    modifier   = Modifier
                        .fillMaxWidth()
                        .padding(bottom = (settings.fontSize * 0.8f).dp)
                )
            }
        }
    }
}

// ── Header: back / Audio-Text segmented control / settings gear ───────────
@Composable
private fun ReaderHeader(
    fg: Color,
    accent: Color,
    audioSelected: Boolean,
    onBack: () -> Unit,
    onAudioClick: () -> Unit,
    onTextClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Back button
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(glassFill(fg))
                .border(1.dp, glassBorder(fg), RoundedCornerShape(16.dp))
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = fg, modifier = Modifier.size(20.dp))
        }

        // Audio / Text segmented pill
        Box(
            modifier = Modifier
                .width(144.dp)
                .height(36.dp)
                .clip(RoundedCornerShape(50))
                .background(glassFill(fg))
                .border(1.dp, glassBorder(fg), RoundedCornerShape(50))
                .padding(3.dp)
        ) {
            Row(Modifier.fillMaxSize()) {
                SegmentPill("Audio", audioSelected, accent, fg, Modifier.weight(1f), onAudioClick)
                SegmentPill("Text", !audioSelected, accent, fg, Modifier.weight(1f), onTextClick)
            }
        }

        // Settings gear
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(glassFill(fg))
                .border(1.dp, glassBorder(fg), RoundedCornerShape(16.dp))
                .clickable(onClick = onSettingsClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Settings, "Reader settings", tint = fg, modifier = Modifier.size(20.dp))
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
            fontSize   = 12.sp,
            color      = if (selected) Color.White else fg.copy(alpha = 0.7f)
        )
    }
}

// ── Chapter nav row (prev / selector / next) + progress bar ────────────────
@Composable
private fun ChapterNavBar(
    fg: Color,
    accent: Color,
    title: String,
    progress: Float,
    canGoPrev: Boolean,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onOpenToc: () -> Unit
) {
    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(glassFill(fg))
                .border(1.dp, glassBorder(fg), RoundedCornerShape(18.dp))
                .padding(6.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = onPrev, enabled = canGoPrev, modifier = Modifier.size(32.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Previous chapter",
                    tint = fg.copy(alpha = if (canGoPrev) 0.9f else 0.3f), modifier = Modifier.size(16.dp))
            }
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClick = onOpenToc)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Menu, contentDescription = null, tint = fg, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    title.uppercase(),
                    fontFamily = MontserratFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize   = 11.sp,
                    letterSpacing = 1.sp,
                    color      = fg,
                    maxLines   = 1
                )
            }
            IconButton(onClick = onNext, modifier = Modifier.size(32.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowForward, "Next chapter",
                    tint = fg.copy(alpha = 0.9f), modifier = Modifier.size(16.dp))
            }
        }

        Spacer(Modifier.height(10.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(6.dp)
                    .clip(RoundedCornerShape(50))
                    .background(fg.copy(alpha = 0.18f))
            ) {
                Box(
                    Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(progress.coerceIn(0.05f, 1f))
                        .clip(RoundedCornerShape(50))
                        .background(accent)
                )
            }
            Spacer(Modifier.width(10.dp))
            Text(
                "${(progress * 100).toInt()}%",
                fontFamily = MontserratFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize   = 10.sp,
                color      = fg.copy(alpha = 0.8f)
            )
        }
    }
}

// ── Settings bottom sheet ───────────────────────────────────────────────────
@Composable
private fun ReaderSettingsSheet(
    settings: ReaderSettings,
    swatches: List<ReaderSwatch>,
    onClose: () -> Unit,
    onBrightness: (Float) -> Unit,
    onSelectSwatch: (Int) -> Unit,
    onDecreaseFont: () -> Unit,
    onIncreaseFont: () -> Unit,
    onSetAlign: (ReaderTextAlign) -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        shape           = RoundedCornerShape(28.dp),
        color           = Color(0xFFF5F0EA),
        shadowElevation = 12.dp
    ) {
        Column(Modifier.padding(20.dp).navigationBarsPadding()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Text(
                    "READER SETTINGS",
                    fontFamily    = MontserratFamily,
                    fontWeight    = FontWeight.ExtraBold,
                    fontSize      = 12.sp,
                    letterSpacing = 1.sp,
                    color         = Color(0xFF1A1714)
                )
                IconButton(onClick = onClose, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Close, "Close", tint = Color(0xFF9C9188))
                }
            }

            // Brightness
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 20.dp)) {
                Icon(Icons.Default.LightMode, null, tint = Color(0xFF6B6259), modifier = Modifier.size(16.dp))
                Slider(
                    value         = settings.brightness,
                    onValueChange = onBrightness,
                    valueRange    = 0f..0.7f,
                    modifier      = Modifier.weight(1f).padding(horizontal = 10.dp),
                    colors = SliderDefaults.colors(
                        thumbColor        = Color.White,
                        activeTrackColor  = Color(0xFF2D2420),
                        inactiveTrackColor = Color(0xFFDCD5CB)
                    )
                )
                Icon(Icons.Default.LightMode, null, tint = Color(0xFF1A1714), modifier = Modifier.size(24.dp))
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
                            .height(44.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(swatch.background)
                            .border(
                                width = if (selected) 2.dp else 1.dp,
                                color = if (selected) Color(0xFF1A1714) else Color.Transparent,
                                shape = RoundedCornerShape(16.dp)
                            )
                            .clickable { onSelectSwatch(index) }
                    )
                }
            }

            // Font size
            Row(
                Modifier.fillMaxWidth().padding(bottom = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                FontSizeButton("–", onDecreaseFont)
                Box(
                    Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFFEDE7DF))
                        .padding(horizontal = 24.dp, vertical = 8.dp)
                ) {
                    Text(
                        settings.fontSize.toInt().toString(),
                        fontFamily = MontserratFamily,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize   = 18.sp,
                        color      = Color(0xFF1A1714)
                    )
                }
                FontSizeButton("+", onIncreaseFont)
            }

            // Text alignment
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .border(0.dp, Color.Transparent),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                AlignButton(Icons.Filled.FormatAlignLeft, settings.textAlign == ReaderTextAlign.LEFT)
                    { onSetAlign(ReaderTextAlign.LEFT) }
                AlignButton(Icons.Filled.FormatAlignCenter, settings.textAlign == ReaderTextAlign.CENTER)
                    { onSetAlign(ReaderTextAlign.CENTER) }
                AlignButton(Icons.Filled.FormatAlignRight, settings.textAlign == ReaderTextAlign.RIGHT)
                    { onSetAlign(ReaderTextAlign.RIGHT) }
                AlignButton(Icons.Filled.FormatAlignJustify, settings.textAlign == ReaderTextAlign.JUSTIFY)
                    { onSetAlign(ReaderTextAlign.JUSTIFY) }
            }
        }
    }
}

@Composable
private fun FontSizeButton(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(48.dp, 40.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFFE3DCD2))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(label, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1A1714))
    }
}

@Composable
private fun AlignButton(icon: androidx.compose.ui.graphics.vector.ImageVector, selected: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(icon, contentDescription = null,
            tint = if (selected) Color(0xFF1A1714) else Color(0xFFB8AFA4))
    }
}

// ── Table of contents drawer ────────────────────────────────────────────────
@Composable
private fun ChapterTocDrawer(
    chapters: List<ChapterLink>,
    currentNum: Int,
    onSelect: (Int) -> Unit,
    onClose: () -> Unit
) {
    Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFFF5F0EA)) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp)
                    .border(width = 0.dp, color = Color.Transparent),
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
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, "Close", tint = Color(0xFF9C9188))
                }
            }
            HorizontalDivider(color = Color(0xFFE3DCD2))

            if (chapters.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color(0xFF3C1D18))
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp)
                ) {
                    items(chapters, key = { it.num }) { chapter ->
                        val isSelected = chapter.num == currentNum
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (isSelected) Color(0xFF1A1714) else Color.Transparent)
                                .clickable { onSelect(chapter.num) }
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment     = Alignment.CenterVertically
                        ) {
                            Text(
                                chapter.title,
                                fontFamily = MontserratFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize   = 14.sp,
                                color      = if (isSelected) Color.White else Color(0xFF1A1714),
                                maxLines   = 1
                            )
                            if (isSelected) {
                                Box(
                                    Modifier
                                        .clip(RoundedCornerShape(50))
                                        .background(Color.White.copy(alpha = 0.2f))
                                        .padding(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Text("Reading", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Audio "coming soon" overlay ─────────────────────────────────────────────
// Backdrop is the inverse of the current reading background (light backdrop
// if the reader is currently dark, dark blue if the reader is currently
// light) so it always reads as a distinct, deliberate interstitial rather
// than "did the page just go blank". Icons spawn just below center, spread
// out linearly into a ring, then the whole ring spins continuously.
@Composable
private fun AudioComingSoonOverlay(readerBg: Color, onDismiss: () -> Unit) {
    val luminance = (0.299f * readerBg.red + 0.587f * readerBg.green + 0.114f * readerBg.blue)
    val backdrop = if (luminance < 0.5f) Color(0xFFF5F0EA) else Color(0xFF0B1E3D)
    val content  = if (luminance < 0.5f) Color(0xFF1A1714) else Color(0xFFEAF1FF)

    var started by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { started = true }

    val spread by animateFloatAsState(
        targetValue   = if (started) 1f else 0f,
        animationSpec = tween(950, easing = FastOutSlowInEasing),
        label = "spread"
    )
    val textAlpha by animateFloatAsState(
        targetValue   = if (spread > 0.8f) 1f else 0f,
        animationSpec = tween(500),
        label = "text"
    )
    val infinite = rememberInfiniteTransition(label = "spin")
    val rotation by infinite.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(7000, easing = LinearEasing)),
        label = "rotation"
    )

    val orbitIcons = remember {
        listOf(
            Icons.Default.Headphones, Icons.Default.GraphicEq, Icons.Default.PlayArrow,
            Icons.Default.VolumeUp, Icons.Default.Mic, Icons.Default.MusicNote
        )
    }

    val noRipple = remember { MutableInteractionSource() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backdrop)
            .clickable(interactionSource = noRipple, indication = null, onClick = onDismiss),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .offset(y = 16.dp)
                .size(240.dp)
                .graphicsLayer { rotationZ = rotation * spread },
            contentAlignment = Alignment.Center
        ) {
            orbitIcons.forEachIndexed { i, icon ->
                val angle  = (360f / orbitIcons.size) * i
                val radius = 100f
                val rad    = Math.toRadians(angle.toDouble())
                val tx     = (radius * cos(rad)).toFloat()
                val ty     = (radius * sin(rad)).toFloat()
                Box(
                    modifier = Modifier
                        .offset(x = (tx * spread).dp, y = (ty * spread).dp)
                        .size(40.dp)
                        .alpha(spread)
                        .clip(CircleShape)
                        .background(content.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(20.dp))
                }
            }
            // Centerpiece music note — stays upright while the ring spins
            Box(
                modifier = Modifier.graphicsLayer { rotationZ = -rotation * spread },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.MusicNote,
                    contentDescription = null,
                    tint     = content,
                    modifier = Modifier.size(52.dp).alpha(spread)
                )
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(top = 200.dp)
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
                fontSize = 12.sp,
                color    = content.copy(alpha = 0.6f)
            )
        }
    }
}
