package com.noven.ncrawler.ui.screens.discover

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.noven.ncrawler.data.db.NovelEntity
import com.noven.ncrawler.ui.theme.AccentBlue
import com.noven.ncrawler.ui.theme.StarGold
import com.noven.ncrawler.viewmodel.GenreViewModel

// Simple infinite-scroll grid for one genre. Kept deliberately plain per the
// "keep it simple for now" ask — no sort toggle, no filters, just more pages
// as you scroll.
@Composable
fun GenreScreen(
    genre: String,
    onBack: () -> Unit,
    onNovelClick: (String) -> Unit,
    vm: GenreViewModel = viewModel()
) {
    LaunchedEffect(genre) { vm.load(genre) }

    val state by vm.state.collectAsStateWithLifecycle()
    val gridState = rememberLazyGridState()

    // Trigger the next page a couple rows before the actual end
    LaunchedEffect(gridState, state.novels.size) {
        snapshotFlow {
            val last = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            last to state.novels.size
        }.collect { (lastVisible, total) ->
            if (total > 0 && lastVisible >= total - 4) vm.loadNextPage()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // ── Top bar — back + genre name ─────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back",
                    tint = MaterialTheme.colorScheme.onSurface)
            }
            Text(
                genre,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        when {
            state.isLoading && state.novels.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = AccentBlue)
                }
            }
            state.error != null && state.novels.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Rounded.WifiOff, null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(40.dp))
                        Spacer(Modifier.height(8.dp))
                        Text(state.error ?: "Something went wrong",
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(12.dp))
                        TextButton(onClick = { vm.load(genre) }) { Text("Retry") }
                    }
                }
            }
            state.novels.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No novels found in $genre",
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            else -> {
                LazyVerticalGrid(
                    columns         = GridCells.Fixed(2),
                    state           = gridState,
                    contentPadding  = PaddingValues(16.dp, 12.dp, 16.dp, 100.dp),
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                    verticalArrangement   = Arrangement.spacedBy(16.dp)
                ) {
                    items(state.novels, key = { it.slug }) { novel ->
                        GenreNovelCard(novel = novel, onClick = { onNovelClick(novel.slug) })
                    }
                    if (state.isLoadingMore) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Box(Modifier.fillMaxWidth().padding(16.dp), Alignment.Center) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(22.dp),
                                    strokeWidth = 2.dp,
                                    color = AccentBlue
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// Compact card — same visual language as the homepage's landscape NovelCard
// (white rounded card, cover on top, title + rating below).
@Composable
private fun GenreNovelCard(novel: NovelEntity, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        label = "genreCardPress"
    )

    Surface(
        modifier = Modifier
            .graphicsLayer(scaleX = scale, scaleY = scale)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
        shape           = RoundedCornerShape(16.dp),
        color           = MaterialTheme.colorScheme.surface,
        shadowElevation = 3.dp
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                AsyncImage(
                    model              = novel.coverUrl,
                    contentDescription = novel.title,
                    contentScale       = ContentScale.Crop,
                    modifier           = Modifier.fillMaxSize()
                )
            }
            Column(modifier = Modifier.padding(10.dp)) {
                Text(
                    novel.title,
                    style    = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold, fontSize = 12.sp
                    ),
                    color    = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(AccentBlue),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Rounded.PlayArrow,
                            contentDescription = "Read",
                            tint     = Color.White,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                    if (novel.rating.isNotBlank()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Star, null, tint = StarGold, modifier = Modifier.size(10.dp))
                            Spacer(Modifier.width(2.dp))
                            Text(
                                novel.rating,
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}
