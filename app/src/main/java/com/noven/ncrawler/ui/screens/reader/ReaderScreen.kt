package com.noven.ncrawler.ui.screens.reader

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.noven.ncrawler.data.db.ChapterEntity
import com.noven.ncrawler.viewmodel.ReaderSettings
import com.noven.ncrawler.viewmodel.ReaderUiState
import com.noven.ncrawler.viewmodel.ReaderViewModel

private val ReaderBgDark    = Color(0xFF0D0C0A)
private val ReaderBgLight   = Color(0xFFF5F0E8)
private val ReaderTextDark  = Color(0xFFE8E4DC)
private val ReaderTextLight = Color(0xFF1A1714)
private val ReaderAmber     = Color(0xFFFFCA28)

@Composable
fun ReaderScreen(
    slug: String,
    chapterNum: Int,
    onBack: () -> Unit,
    vm: ReaderViewModel = viewModel()
) {
    LaunchedEffect(slug, chapterNum) { vm.load(slug, chapterNum) }

    val state    by vm.state.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()

    val bg = if (settings.darkMode) ReaderBgDark  else ReaderBgLight
    val fg = if (settings.darkMode) ReaderTextDark else ReaderTextLight

    var showControls by remember { mutableStateOf(true) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bg)
            // Simple no-arg clickable — toggles controls visibility
            .clickable { showControls = !showControls }
    ) {
        when (val s = state) {
            is ReaderUiState.Loading -> {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color    = ReaderAmber
                )
            }

            is ReaderUiState.Error -> {
                Column(
                    modifier            = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Default.WifiOff,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint     = Color.Gray
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text      = s.message,
                        color     = Color.Gray,
                        textAlign = TextAlign.Center,
                        modifier  = Modifier.padding(horizontal = 32.dp)
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = { vm.load(slug, chapterNum) },
                        colors  = ButtonDefaults.buttonColors(
                            containerColor = ReaderAmber,
                            contentColor   = ReaderBgDark
                        )
                    ) { Text("Retry") }
                }
            }

            is ReaderUiState.Success -> {
                ReaderContent(
                    chapter  = s.chapter,
                    settings = settings,
                    fg       = fg
                )
            }
        }

        // ── Top bar ───────────────────────────────────────────────────────
        AnimatedVisibility(
            visible  = showControls,
            enter    = fadeIn() + slideInVertically(),
            exit     = fadeOut() + slideOutVertically(),
            modifier = Modifier.align(Alignment.TopStart)
        ) {
            ReaderTopBar(
                title  = (state as? ReaderUiState.Success)?.chapter?.title
                    ?: "Chapter $chapterNum",
                bg     = bg.copy(alpha = 0.95f),
                fg     = fg,
                onBack = onBack
            )
        }

        // ── Bottom controls ───────────────────────────────────────────────
        AnimatedVisibility(
            visible  = showControls,
            enter    = fadeIn() + slideInVertically(initialOffsetY = { it }),
            exit     = fadeOut() + slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            ReaderBottomBar(
                settings     = settings,
                bg           = bg.copy(alpha = 0.95f),
                fg           = fg,
                onDecrease   = vm::decreaseFontSize,
                onIncrease   = vm::increaseFontSize,
                onToggleDark = vm::toggleDarkMode,
                onPrev       = vm::loadPrev,
                onNext       = vm::loadNext,
                canGoPrev    = vm.currentChapterNum > 1
            )
        }
    }
}

@Composable
private fun ReaderContent(
    chapter: ChapterEntity,
    settings: ReaderSettings,
    fg: Color
) {
    val scrollState = rememberScrollState()
    LaunchedEffect(chapter.id) { scrollState.scrollTo(0) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(top = 80.dp, bottom = 120.dp, start = 20.dp, end = 20.dp)
    ) {
        Text(
            text       = chapter.title,
            fontSize   = (settings.fontSize + 2).sp,
            fontFamily = FontFamily.Serif,
            color      = ReaderAmber,
            modifier   = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp)
        )

        val paragraphs = chapter.content
            .split("\n")
            .map { it.trim() }
            .filter { it.isNotBlank() }

        paragraphs.forEach { para ->
            Text(
                text       = para,
                fontSize   = settings.fontSize.sp,
                fontFamily = FontFamily.Serif,
                color      = fg,
                lineHeight = (settings.fontSize * settings.lineHeight).sp,
                modifier   = Modifier.padding(bottom = (settings.fontSize * 0.8f).dp)
            )
        }
    }
}

@Composable
private fun ReaderTopBar(
    title: String,
    bg: Color,
    fg: Color,
    onBack: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(bg)
            .statusBarsPadding()
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = fg
            )
        }
        Text(
            text     = title,
            color    = fg,
            fontSize = 15.sp,
            maxLines = 1,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp)
        )
    }
}

@Composable
private fun ReaderBottomBar(
    settings: ReaderSettings,
    bg: Color,
    fg: Color,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    onToggleDark: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    canGoPrev: Boolean
) {
    Surface(
        modifier       = Modifier.fillMaxWidth(),
        color          = bg,
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            // Font size + theme toggle
            Row(
                modifier              = Modifier.fillMaxWidth(),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                IconButton(onClick = onDecrease) {
                    Text("A", color = fg, fontSize = 13.sp)
                }
                Text(
                    text     = "${settings.fontSize.toInt()}sp",
                    color    = fg.copy(alpha = 0.6f),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
                IconButton(onClick = onIncrease) {
                    Text("A", color = fg, fontSize = 18.sp)
                }
                Spacer(Modifier.width(16.dp))
                IconButton(onClick = onToggleDark) {
                    Icon(
                        if (settings.darkMode) Icons.Default.LightMode
                        else Icons.Default.DarkMode,
                        contentDescription = "Toggle theme",
                        tint = fg
                    )
                }
            }

            // Prev / Next
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick  = onPrev,
                    enabled  = canGoPrev,
                    shape    = RoundedCornerShape(8.dp),
                    colors   = ButtonDefaults.outlinedButtonColors(
                        contentColor         = fg,
                        disabledContentColor = fg.copy(alpha = 0.3f)
                    ),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("Prev", fontSize = 13.sp)
                }

                Spacer(Modifier.width(12.dp))

                Button(
                    onClick  = onNext,
                    shape    = RoundedCornerShape(8.dp),
                    colors   = ButtonDefaults.buttonColors(
                        containerColor = ReaderAmber,
                        contentColor   = ReaderBgDark
                    ),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Next", fontSize = 13.sp)
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}
