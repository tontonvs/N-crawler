package com.noven.ncrawler.ui.screens.browse

import android.os.Build
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.noven.ncrawler.data.db.NovelEntity
import com.noven.ncrawler.ui.components.CoverImage
import com.noven.ncrawler.ui.components.ShimmerScope
import com.noven.ncrawler.ui.components.errorShake
import com.noven.ncrawler.ui.components.skeleton
import com.noven.ncrawler.ui.components.staggerIn
import com.noven.ncrawler.ui.components.NovelGlassCard
import com.noven.ncrawler.ui.components.glassBlur
import com.noven.ncrawler.ui.theme.*
import com.noven.ncrawler.viewmodel.BrowseUiState
import com.noven.ncrawler.viewmodel.BrowseViewModel
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.delay

private const val SEARCH_GLASS_ALPHA         = 0.60f  // overlay tint over the real blur (Android 12+)
private const val SEARCH_GLASS_ALPHA_NO_BLUR = 0.90f  // older Androids can't blur: tint is stronger so text stays readable

// ── Search Overlay ────────────────────────────────────────────────────────────
// Opened from the search FAB in the floating nav — full-screen, autofocused
// field, recent searches (max 5, each a rounded rectangle with its own "x") when
// empty, live results once typing, X to close. All text is Montserrat.
// CHANGE (UI polish): the background is frosted glass (real Haze blur of the screen
// behind it) instead of a solid page colour; the X clears the text first and only
// closes the overlay when the field is already empty; the cursor always sits at the
// end of the text (see the TextFieldValue below).
// [hazeState] = the blur source from NavGraph; null → a plain translucent fallback.
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SearchOverlay(
    vm: BrowseViewModel,
    onNovelClick: (String) -> Unit,
    onClose: () -> Unit,
    hazeState: HazeState? = null
) {
    val query          by vm.query.collectAsStateWithLifecycle()
    val searchState    by vm.searchState.collectAsStateWithLifecycle()
    val recentSearches by vm.recentSearches.collectAsStateWithLifecycle()

    val focusManager    = LocalFocusManager.current
    val keyboard        = LocalSoftwareKeyboardController.current
    val focusRequester   = remember { FocusRequester() }

    // The field's thick border + search glyph are black in light mode; black on
    // the dark background would disappear, so dark mode uses the text colour.
    val fieldInk = if (isSystemInDarkTheme()) MaterialTheme.colorScheme.onSurface else Color.Black

    // Autofocus + open the keyboard the moment the overlay appears
    LaunchedEffect(Unit) {
        delay(150)
        focusRequester.requestFocus()
    }

    // CHANGE (UI polish #7): the field holds a TextFieldValue (text + cursor) instead of
    // a bare String. With a String, text that arrives from outside — tapping a recent
    // search — left the cursor wherever it was (the start of an empty field). Now every
    // programmatic change puts the cursor at the END of the text, and while you type or
    // move the cursor yourself it is left alone. The effect re-syncs from the view
    // model for any other external change; it compares against the LATEST value
    // (vm.query.value), not the one captured at composition, so fast typing can never
    // be rolled back by a stale emission.
    var field by remember { mutableStateOf(TextFieldValue(query, TextRange(query.length))) }
    LaunchedEffect(query) {
        val latest = vm.query.value
        if (field.text != latest) field = TextFieldValue(latest, TextRange(latest.length))
    }
    fun pickTerm(term: String) {
        vm.onQueryChange(term)
        field = TextFieldValue(term, TextRange(term.length))
    }

    // CHANGE (UI polish #5): glass background. Light = white glass, dark = the app's
    // glass grey (GlassBase) — the same two fills the nav uses — over a real 30dp blur
    // of whatever is behind (Android 12+). Older Androids can't blur, so there the tint
    // is stronger and simply hides the screen enough to keep text readable.
    val dark    = isSystemInDarkTheme()
    val canBlur = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val glassTint = (if (dark) GlassBase else Color.White)
        .copy(alpha = if (canBlur) SEARCH_GLASS_ALPHA else SEARCH_GLASS_ALPHA_NO_BLUR)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .then(
                if (hazeState != null) Modifier.glassBlur(hazeState, RectangleShape, glassTint)
                else Modifier.background(MaterialTheme.colorScheme.background.copy(alpha = 0.94f))
            )
            // Swallows touches so nothing falls through to the screen behind the glass
            // (the old opaque Surface did this implicitly).
            .pointerInput(Unit) {}
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // ── Field + close (X) ───────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Custom-bordered field: Material3's OutlinedTextField has no
                // public "border width" knob, only color — so this uses a
                // filled TextField (indicator hidden) inside a Box with an
                // explicit thick black border for exact control.
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .border(2.5.dp, fieldInk, RoundedCornerShape(16.dp))
                ) {
                    TextField(
                        value         = field,
                        onValueChange = { v ->
                            field = v
                            if (v.text != vm.query.value) vm.onQueryChange(v.text)
                        },
                        modifier      = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester),
                        placeholder   = {
                            Text(
                                "Search novels…",
                                fontFamily = MontserratFamily,
                                fontWeight = FontWeight.Bold,
                                color      = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        leadingIcon = {
                            // Filled, solid glyph — TikTok-style, not the
                            // softer Rounded family used elsewhere.
                            Icon(Icons.Filled.Search, contentDescription = null,
                                tint = fieldInk)
                        },
                        textStyle = LocalTextStyle.current.copy(
                            fontFamily = MontserratFamily,
                            fontWeight = FontWeight.Bold,
                            color      = MaterialTheme.colorScheme.onSurface
                        ),
                        singleLine = true,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor   = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor   = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            cursorColor             = AccentBlue
                        ),
                        shape = RoundedCornerShape(16.dp),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = {
                            vm.commitSearch(field.text)
                            focusManager.clearFocus()
                            keyboard?.hide()
                        })
                    )
                }
                Spacer(Modifier.width(8.dp))
                // CHANGE (UI polish #6): same "x", two jobs. Text in the field → clear it
                // (and keep typing: focus + keyboard come back, since the IME's Search
                // key may have hidden them). Field already empty → close the overlay.
                val hasText = field.text.isNotEmpty()
                IconButton(onClick = {
                    if (hasText) {
                        vm.clearSearch()
                        field = TextFieldValue("")
                        focusRequester.requestFocus()
                        keyboard?.show()
                    } else {
                        vm.clearSearch()
                        onClose()
                    }
                }) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = if (hasText) "Clear search" else "Close search",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            if (query.isBlank()) {
                // ── Recent searches (max 5) ─────────────────────────────────
                // CHANGE: small rounded rectangles laid out side by side and
                // wrapping onto the next line (FlowRow) — not stacked full-width
                // rows. Same look as the reader's controls: a soft fill of the text
                // colour at 10% (the reader pill's alpha), no border, Montserrat,
                // icon at 90%. Long terms are cut short with an ellipsis (chip is
                // capped at 168dp). Tap = search again, "x" = remove.
                if (recentSearches.isNotEmpty()) {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Text(
                            "Recent Searches",
                            fontFamily = MontserratFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize   = 14.sp,
                            color      = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(10.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement   = Arrangement.spacedBy(8.dp)
                        ) {
                            // CHANGE (motion): chips pop in one after another (30ms apart).
                            recentSearches.take(5).forEachIndexed { chipIndex, term ->
                                Row(
                                    modifier = Modifier
                                        .staggerIn(chipIndex, distance = 8.dp, stepMs = 30)
                                        .widthIn(max = 168.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f))
                                        .clickable { pickTerm(term) }
                                        .padding(start = 12.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        term,
                                        fontFamily = MontserratFamily,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize   = 14.sp,
                                        color      = MaterialTheme.colorScheme.onSurface,
                                        maxLines   = 1,
                                        overflow   = TextOverflow.Ellipsis,
                                        modifier   = Modifier.weight(1f, fill = false)
                                    )
                                    // Plain box, not IconButton: IconButton's 48dp minimum
                                    // touch size would balloon the small chip.
                                    Box(
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(CircleShape)
                                            .clickable { vm.removeRecentSearch(term) },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            Icons.Filled.Close,
                                            contentDescription = "Remove \"$term\" from recent searches",
                                            tint     = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f),
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            "Search by title",
                            fontFamily = MontserratFamily,
                            fontSize   = 15.sp,
                            color      = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                // ── Live results ─────────────────────────────────────────────
                SearchContent(
                    state        = searchState,
                    query        = query,
                    onNovelClick = { slug ->
                        vm.commitSearch(query)
                        onNovelClick(slug)
                        onClose()
                    }
                )
            }
        }
    }
}

// ── Novel Card ────────────────────────────────────────────────────────────────
// CHANGE: moved to ui/components/GlassCards.kt as NovelGlassCard — GenreScreen
// carried a hand-copied duplicate that had already drifted from this one.

// ── Search content ────────────────────────────────────────────────────────────
@Composable
private fun SearchContent(
    state: BrowseUiState,
    query: String,
    onNovelClick: (String) -> Unit
) {
    when (state) {
        is BrowseUiState.Loading -> ShimmerScope { SearchSkeleton() }
        is BrowseUiState.Empty   -> SearchEmpty(query)
        is BrowseUiState.Error   -> Box(Modifier.fillMaxSize(), Alignment.Center) {
            Text(
                state.message,
                modifier   = Modifier.errorShake(trigger = state.message),   // CHANGE (motion)
                fontFamily = MontserratFamily,
                color      = MaterialTheme.colorScheme.error
            )
        }
        is BrowseUiState.Success -> LazyColumn(
            contentPadding      = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            items(state.novels, key = { it.slug }) { novel ->
                SearchRow(novel = novel, onClick = { onNovelClick(novel.slug) })
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.08f)
                )
            }
        }
    }
}

@Composable
private fun SearchRow(novel: NovelEntity, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment     = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp, 66.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            CoverImage(
            url                = novel.coverUrl,
            contentDescription = novel.title,
            modifier           = Modifier.fillMaxSize()
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                novel.title,
                fontFamily = MontserratFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize   = 15.sp,
                lineHeight = 20.sp,
                maxLines   = 2,
                overflow   = TextOverflow.Ellipsis,
                color      = MaterialTheme.colorScheme.onBackground
            )
            if (novel.genres.isNotBlank()) {
                Spacer(Modifier.height(3.dp))
                Text(
                    novel.genres.split(",").take(2).joinToString(" · "),
                    fontFamily = MontserratFamily,
                    fontSize   = 12.sp,
                    color      = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Icon(
            Icons.Rounded.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun SearchSkeleton() {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        repeat(6) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Box(
                    Modifier
                        .size(48.dp, 66.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .skeleton(MaterialTheme.colorScheme.surfaceVariant)
                )
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(
                        Modifier
                            .size(140.dp, 13.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .skeleton(MaterialTheme.colorScheme.surfaceVariant)
                    )
                    Box(
                        Modifier
                            .size(90.dp, 10.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .skeleton(MaterialTheme.colorScheme.surfaceVariant)
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchEmpty(query: String) {
    Box(Modifier.fillMaxSize(), Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Rounded.SearchOff,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint     = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "No results for \"$query\"",
                fontFamily = MontserratFamily,
                fontSize   = 15.sp,
                color      = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
