package com.noven.ncrawler.ui.screens.discover

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.noven.ncrawler.ui.components.GenreGlassTile

// Confirmed genre list — straight from freewebnovel.com's own genre sidebar.
// Kept simple per the ask: a flat grid of tiles, tap one → GenreScreen.
private val ALL_GENRES = listOf(
    "Action", "Adult", "Adventure", "Comedy", "Drama", "Eastern", "Ecchi",
    "Fan-fic", "Fantasy", "Game", "Gender Bender", "Harem", "Historical",
    "Horror", "Josei", "Martial Arts", "Mature", "Mecha", "Mystery",
    "Psychological", "Reincarnation", "Romance", "School Life", "Sci-fi",
    "Seinen", "Shoujo", "Shounen Ai", "Shounen", "Slice of Life", "Smut",
    "Sports", "Supernatural", "System", "Tragedy", "Wuxia", "Xianxia",
    "Xuanhuan", "Yaoi"
)

// CHANGE: tiles are now the same GenreGlassTile the homepage's genre showcase
// uses (gradient typography on frosted glass, 6 alternating styles by index)
// instead of a plain text tile. The old tile filled itself with
// GlassSurfaceLight directly, so in dark mode it drew near-white text on a
// near-white fill — GenreGlassTile goes through glassSurface(), which follows
// the system theme.
@Composable
fun DiscoverScreen(onGenreClick: (String) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Text(
            "Discover",
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold),
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 16.dp)
        )

        LazyVerticalGrid(
            columns               = GridCells.Fixed(2),
            contentPadding        = PaddingValues(16.dp, 8.dp, 16.dp, 120.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement   = Arrangement.spacedBy(14.dp)
        ) {
            itemsIndexed(ALL_GENRES, key = { _, genre -> genre }) { index, genre ->
                GenreGlassTile(
                    genre    = genre,
                    styleIdx = index % 6,
                    onClick  = { onGenreClick(genre) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
