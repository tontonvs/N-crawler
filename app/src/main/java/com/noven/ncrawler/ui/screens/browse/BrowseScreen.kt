package com.noven.ncrawler.ui.screens.browse

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.noven.ncrawler.data.db.NovelEntity
import com.noven.ncrawler.viewmodel.BrowseUiState
import com.noven.ncrawler.viewmodel.BrowseViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowseScreen(
    onNovelClick: (slug: String) -> Unit,
    vm: BrowseViewModel = viewModel()
) {
    val browseState by vm.browseState.collectAsStateWithLifecycle()
    val searchState by vm.searchState.collectAsStateWithLifecycle()
    val query       by vm.query.collectAsStateWithLifecycle()

    val isSearching   = query.isNotBlank()
    val activeState   = if (isSearching) searchState else browseState
    val focusManager  = LocalFocusManager.current
    val keyboard      = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }

    Scaffold(
        topBar = {
            Column(
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.surface)
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                // ── App title ──────────────────────────────────────────
                AnimatedVisibility(visible = !isSearching) {
                    Text(
                        text  = "N-Crawler",
                        style = MaterialTheme.typography.displaySmall.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }

                // ── Search bar ─────────────────────────────────────────
                OutlinedTextField(
                    value         = query,
                    onValueChange = vm::onQueryChange,
                    modifier      = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                    placeholder   = { Text("Search novels…") },
                    leadingIcon   = {
                        Icon(Icons.Default.Search, contentDescription = null)
                    },
                    trailingIcon  = {
                        AnimatedVisibility(visible = isSearching) {
                            IconButton(onClick = {
                                vm.clearSearch()
                                focusManager.clearFocus()
                                keyboard?.hide()
                            }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear")
                            }
                        }
                    },
                    singleLine    = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = {
                        focusManager.clearFocus()
                        keyboard?.hide()
                    }),
                    shape  = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor   = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    )
                )
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (val state = activeState) {
                is BrowseUiState.Loading -> LoadingGrid()

                is BrowseUiState.Empty ->
                    EmptyState(isSearch = isSearching, query = query)

                is BrowseUiState.Error ->
                    ErrorState(message = state.message, onRetry = vm::loadHomepage)

                is BrowseUiState.Success ->
                    NovelGrid(novels = state.novels, onNovelClick = onNovelClick)
            }
        }
    }
}

// ── Novel grid ────────────────────────────────────────────────────────────
@Composable
private fun NovelGrid(
    novels: List<NovelEntity>,
    onNovelClick: (String) -> Unit
) {
    LazyVerticalGrid(
        columns       = GridCells.Fixed(3),
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement   = Arrangement.spacedBy(10.dp)
    ) {
        items(novels, key = { it.slug }) { novel ->
            NovelCard(novel = novel, onClick = { onNovelClick(novel.slug) })
        }
    }
}

// ── Novel card ────────────────────────────────────────────────────────────
@Composable
private fun NovelCard(novel: NovelEntity, onClick: () -> Unit) {
    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier = Modifier
            .aspectRatio(0.65f)
            .clip(shape)
            .clickable(onClick = onClick)
    ) {
        // Cover image
        AsyncImage(
            model             = novel.coverUrl,
            contentDescription = novel.title,
            contentScale      = ContentScale.Crop,
            modifier          = Modifier.fillMaxSize()
        )

        // Bottom gradient + title
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.45f)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
                    )
                )
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 6.dp, vertical = 8.dp)
        ) {
            Text(
                text      = novel.title,
                style     = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.SemiBold
                ),
                color     = Color.White,
                maxLines  = 2,
                overflow  = TextOverflow.Ellipsis
            )
            if (novel.rating.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Star,
                        contentDescription = null,
                        tint   = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(10.dp)
                    )
                    Spacer(Modifier.width(2.dp))
                    Text(
                        text  = novel.rating,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        // Status chip
        if (novel.status.isNotBlank()) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp),
                shape = RoundedCornerShape(4.dp),
                color = if (novel.status.contains("Complete", ignoreCase = true))
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.9f)
                else
                    MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
            ) {
                Text(
                    text     = if (novel.status.contains("Complete", ignoreCase = true))
                        "Done" else "Live",
                    style    = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                    color    = if (novel.status.contains("Complete", ignoreCase = true))
                        MaterialTheme.colorScheme.onPrimary
                    else
                        MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

// ── States ─────────────────────────────────────────────────────────────────
@Composable
private fun LoadingGrid() {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement   = Arrangement.spacedBy(10.dp)
    ) {
        items(12) {
            Box(
                modifier = Modifier
                    .aspectRatio(0.65f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            )
        }
    }
}

@Composable
private fun EmptyState(isSearch: Boolean, query: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                if (isSearch) Icons.Default.SearchOff else Icons.Default.MenuBook,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text  = if (isSearch) "No results for \"$query\"" else "Nothing here yet",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ErrorState(message: String, onRetry: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Default.WifiOff,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.error
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text  = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(16.dp))
            Button(onClick = onRetry) { Text("Retry") }
        }
    }
}
