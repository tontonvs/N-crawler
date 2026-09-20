package com.noven.ncrawler.ui.screens.discover

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.noven.ncrawler.ui.components.NovelGlassCard
import com.noven.ncrawler.ui.theme.AccentBlue
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

    // CHANGE: de-dupe by slug. Grid keys must be unique, and pagination can
    // repeat a novel across pages when the site's list shifts between page
    // fetches (a new update pushes old items down a page) — that used to crash
    // with "Key was already used". The next-page trigger below counts the
    // de-duped list too, so repeats can't make it think the end is farther off.
    val novels = remember(state.novels) { state.novels.distinctBy { it.slug } }

    // Trigger the next page a couple rows before the actual end.
    // Keyed on the raw list size so a page that only returned repeats still
    // re-arms the trigger instead of stalling.
    LaunchedEffect(gridState, state.novels.size) {
        snapshotFlow {
            val last = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            last to novels.size
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
            state.isLoading && novels.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = AccentBlue)
                }
            }
            state.error != null && novels.isEmpty() -> {
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
            novels.isEmpty() -> {
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
                    // CHANGE: shared NovelGlassCard (same card as the homepage
                    // rows) replaces the private GenreNovelCard copy. In this
                    // grid the card is ~half the screen wide, so the cover
                    // uses a slightly taller 6:7 ratio than the homepage's
                    // 130dp square.
                    items(novels, key = { it.slug }) { novel ->
                        NovelGlassCard(
                            novel       = novel,
                            onClick     = { onNovelClick(novel.slug) },
                            modifier    = Modifier.fillMaxWidth(),
                            coverAspect = 6f / 7f
                        )
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
