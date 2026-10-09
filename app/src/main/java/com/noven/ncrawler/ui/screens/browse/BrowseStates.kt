package com.noven.ncrawler.ui.screens.browse

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.*
import com.noven.ncrawler.ui.components.errorShake
import com.noven.ncrawler.ui.components.skeleton
import com.noven.ncrawler.ui.components.NovelGlassCard
import com.noven.ncrawler.ui.theme.*

// ── Skeleton states ───────────────────────────────────────────────────────────
@Composable
internal fun BrowseSkeleton(topInset: Dp) {
    val shimmer = MaterialTheme.colorScheme.surfaceVariant

    // topInset: the list now runs under the top bar, so the skeleton starts below it too.
    Column(modifier = Modifier.fillMaxSize().padding(top = topInset)) {
        Spacer(Modifier.height(12.dp))   // same breathing room as the real hero section
        // Hero skeleton — inset, rounded, 320dp (matches HeroBanner)
        Box(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .height(320.dp)
                .clip(RoundedCornerShape(20.dp))
                .skeleton(shimmer)
        )
        Spacer(Modifier.height(24.dp))

        // Recently Read skeleton — label + row of portrait cards (RECENT_CARD_W × RECENT_CARD_H)
        SkeletonLabel(shimmer, width = 130.dp)
        Spacer(Modifier.height(14.dp))
        Row(
            Modifier.padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            repeat(2) {
                Box(
                    Modifier
                        .width(RECENT_CARD_W)
                        .height(RECENT_CARD_H)
                        .clip(RoundedCornerShape(16.dp))
                        .skeleton(shimmer)
                )
            }
        }
        Spacer(Modifier.height(24.dp))

        // Genre showcase skeleton — "Genre"/"See all" header + row of
        // 104x72dp cards, matching GenreShowcaseRow exactly
        SkeletonLabel(shimmer, width = 90.dp)
        Spacer(Modifier.height(14.dp))
        Row(
            Modifier.padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            repeat(4) {
                Box(
                    Modifier
                        .width(104.dp)
                        .height(72.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .skeleton(shimmer)
                )
            }
        }
        Spacer(Modifier.height(24.dp))

        // Two genre-row sections (Latest Updates / Popular) — section label
        // + a labeled row + a horizontal scroll of novel cards, matching
        // GenreRow exactly (same width, 6:7 cover, 48dp title block)
        val skeletonCardWidth = novelCardWidth()
        repeat(2) {
            SkeletonLabel(shimmer, width = 140.dp, height = 16.dp)
            Spacer(Modifier.height(16.dp))
            SkeletonLabel(shimmer, width = 100.dp)
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier.padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                repeat(3) {
                    // Rounded cover + a title bar underneath (no card behind the
                    // title any more, matching NovelGlassCard)
                    Column(Modifier.width(skeletonCardWidth)) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(skeletonCardWidth * (7f / 6f))
                                .clip(RoundedCornerShape(16.dp))
                                .skeleton(shimmer)
                        )
                        Spacer(Modifier.height(12.dp))
                        Box(
                            Modifier
                                .fillMaxWidth(0.8f)
                                .height(12.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .skeleton(shimmer)
                        )
                        Spacer(Modifier.height(20.dp))
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

// Small shimmer bar standing in for a text label — used throughout the
// skeleton above instead of repeating the same Box(...).skeleton(shimmer)
// four times with slightly different sizes.
@Composable
private fun SkeletonLabel(shimmer: Color, width: androidx.compose.ui.unit.Dp, height: androidx.compose.ui.unit.Dp = 20.dp) {
    Box(
        Modifier
            .padding(horizontal = 16.dp)
            .size(width, height)
            .clip(RoundedCornerShape(4.dp))
            .skeleton(shimmer)
    )
}

@Composable
internal fun BrowseError(message: String, onRetry: () -> Unit, topInset: Dp) {
    // topInset keeps the message centred in the visible area, below the top bar
    Box(Modifier.fillMaxSize().padding(top = topInset), Alignment.Center) {
        // CHANGE (motion): sharp quick shake when the error lands.
        Column(
            modifier            = Modifier.errorShake(trigger = message),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Rounded.WifiOff,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint     = MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
            )
            Spacer(Modifier.height(12.dp))
            Text(
                message,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = onRetry,
                shape   = RoundedCornerShape(12.dp),
                colors  = ButtonDefaults.buttonColors(containerColor = AccentBlue)
            ) {
                Text("Retry")
            }
        }
    }
}
