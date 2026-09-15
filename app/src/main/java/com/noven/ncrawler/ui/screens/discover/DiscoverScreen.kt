package com.noven.ncrawler.ui.screens.discover

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.noven.ncrawler.ui.theme.GlassBorderLight
import com.noven.ncrawler.ui.theme.GlassSurfaceLight

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
            contentPadding        = PaddingValues(16.dp, 0.dp, 16.dp, 120.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement   = Arrangement.spacedBy(12.dp)
        ) {
            items(ALL_GENRES) { genre ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(GlassSurfaceLight)
                        .border(1.dp, GlassBorderLight, RoundedCornerShape(16.dp))
                        .clickable { onGenreClick(genre) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        genre,
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}
