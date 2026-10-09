package com.noven.ncrawler.ui.screens.browse

import android.provider.Settings
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import com.noven.ncrawler.data.db.NovelEntity
import com.noven.ncrawler.ui.components.CoverImage
import com.noven.ncrawler.ui.components.GenreGlassTile
import com.noven.ncrawler.ui.components.pressable
import com.noven.ncrawler.ui.components.NovelGlassCard
import com.noven.ncrawler.ui.components.SolarArrows
import com.noven.ncrawler.ui.components.novelCardWidthFor
import com.noven.ncrawler.ui.theme.*
import com.noven.ncrawler.viewmodel.ContinueReadingInfo

internal val RECENT_CARD_W = 126.dp                    // was 140dp
internal val RECENT_CARD_H = 189.dp                    // was 210dp (same 2:3 ratio)

// Groups novels by their (comma-separated) genre tags, keeps the top
// [maxGenres] genres by how many novels carry them, and caps each row's
// sample to [perGenre] so a genre row stays a horizontal scroll, not a wall.
internal fun groupByTopGenres(
    novels: List<NovelEntity>,
    maxGenres: Int = 5,
    perGenre: Int = 10
): List<Pair<String, List<NovelEntity>>> {
    val byGenre = linkedMapOf<String, MutableList<NovelEntity>>()
    novels.forEach { novel ->
        novel.genres.split(",")
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .forEach { g -> byGenre.getOrPut(g) { mutableListOf() }.add(novel) }
    }
    return byGenre.entries
        .sortedByDescending { it.value.size }
        .take(maxGenres)
        .map { it.key to it.value.take(perGenre) }
}

// Width of one card in Discover's GenreScreen grid. Homepage rows use the same
// width so a novel card is the same size on both screens. FIX: it used to be
// "(screen − padding) / 2", which in landscape stretched each card to ~half of
// an 800dp+ screen; the shared formula adds columns instead, so a card stays
// about the same size in any orientation.
@Composable
internal fun novelCardWidth(): androidx.compose.ui.unit.Dp =
    novelCardWidthFor(LocalConfiguration.current.screenWidthDp)

// ── Genre Row — a labeled horizontal sample with a ">" chevron that opens
// the full infinite-scroll list for that genre (Discover's GenreScreen).
// CHANGE: cards are now exactly the size of the cards on Discover's genre grid
// (see novelCardWidth — same width, same 6:7 cover), longer than the old
// 130dp squares. They come from the shared NovelGlassCard (ui/components), and
// the row de-dupes by slug — LazyRow keys must be unique, so a source that
// repeats a novel inside one list used to crash with "Key was already used".
@Composable
internal fun GenreRow(
    genre: String,
    novels: List<NovelEntity>,
    onNovelClick: (String) -> Unit,
    // null = nowhere useful to go (flat Latest/Popular rows) — the link is hidden
    // instead of dumping the user on the generic genre list.
    onSeeMore: (() -> Unit)?
) {
    val uniqueNovels = remember(novels) { novels.distinctBy { it.slug } }
    val cardWidth = novelCardWidth()
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Text(
                genre,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground
            )
            if (onSeeMore != null) {
                // CHANGE (UI polish): the "See more" text link is now a thick, rounded ">"
                // (Solar "Alt Arrow Right", see SolarArrows.kt) — 40dp touch target.
                SeeMoreChevron(onClick = onSeeMore, label = "See more $genre")
            }
        }
        Spacer(Modifier.height(10.dp))
        // CHANGE: bottom = 8.dp — a LazyRow clips to its bounds, which was cutting
        // off the cards' drop shadow at the bottom edge.
        LazyRow(
            contentPadding        = PaddingValues(start = 16.dp, end = 16.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            items(uniqueNovels, key = { it.slug }) { novel ->
                NovelGlassCard(
                    novel    = novel,
                    onClick  = { onNovelClick(novel.slug) },
                    modifier = Modifier.width(cardWidth)
                )
            }
        }
    }
}

// The ">" that replaced the "See more" text link. Tinted with the theme's primary (so
// it brightens in dark mode, where AccentBlue is too dim), with the app's standard
// press-in feedback (scale 0.86, 90ms in / soft release).
@Composable
private fun SeeMoreChevron(onClick: () -> Unit, label: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(40.dp)
            .pressable(onClick = onClick, pressedScale = 0.86f),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            SolarArrows.ChevronRight,
            contentDescription = label,
            tint     = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
    }
}

// ── Hero Banner — inset card, rounded corners ─────────────────────────────────
// NOT full-bleed: horizontal page padding + ~20dp corner radius.
// CHANGE: 2× its old height (160 → 320dp — it already spans the page width, so
// height is the only dimension that can double) and no glass left on it: no
// rim border, and the frosted "Start Reading / Resume" pill is now plain
// icon + text with no rectangle behind it.
// CHANGE: the cover slowly drifts (a Ken Burns pan + zoom) instead of sitting
// still — see the heroDrift block below.
// resumeChapter != null → this IS the continue-reading novel: CTA becomes
// "Resume Chapter N" and taps the given onClick (which jumps straight to
// that chapter), instead of "Start Reading" opening the detail page.
@Composable
internal fun HeroBanner(novel: NovelEntity, resumeChapter: Int?, onClick: () -> Unit) {
    // ── Slow cover drift ────────────────────────────────────────────────────
    // Three unhurried, back-and-forth loops with different lengths (zoom 22s,
    // pan-x 17s, pan-y 23s), so the motion never visibly repeats in lockstep.
    // The image is always zoomed at least 12% and only pans inside the slack
    // that zoom creates (80% of it), so no edge is ever exposed. Values are
    // read inside graphicsLayer {} — the draw phase — so this never triggers
    // recomposition. Honours the system "Remove animations" setting.
    val context = LocalContext.current
    val reducedMotion = remember(context) {
        Settings.Global.getFloat(
            context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f
        ) == 0f
    }
    val drift = rememberInfiniteTransition(label = "heroDrift")
    val zoom by drift.animateFloat(
        initialValue  = 0f,
        targetValue   = 1f,
        animationSpec = infiniteRepeatable(tween(22000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label         = "heroZoom"
    )
    val panX by drift.animateFloat(
        initialValue  = -1f,
        targetValue   = 1f,
        animationSpec = infiniteRepeatable(tween(17000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label         = "heroPanX"
    )
    val panY by drift.animateFloat(
        initialValue  = -1f,
        targetValue   = 1f,
        animationSpec = infiniteRepeatable(tween(23000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label         = "heroPanY"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(320.dp)
            .pressable(onClick = onClick, pressedScale = 0.98f)   // CHANGE (motion)
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        // Cover image — drifting (the card's clip() trims the overscan)
        CoverImage(
            url                = novel.coverUrl,
            contentDescription = novel.title,
            modifier           = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    if (!reducedMotion) {
                        val scale = 1.12f + 0.08f * zoom          // 1.12 → 1.20
                        scaleX = scale
                        scaleY = scale
                        translationX = panX * (scale - 1f) * size.width  * 0.4f
                        translationY = panY * (scale - 1f) * size.height * 0.4f
                    }
                }
        )

        // Scrim — transparent top, dark bottom, so the text reads over any cover
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0f    to Color.Black.copy(alpha = 0.05f),
                            0.45f to Color.Black.copy(alpha = 0.15f),
                            1f    to Color.Black.copy(alpha = 0.82f)
                        )
                    )
                )
        )

        // Content — bottom-aligned
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 18.dp, end = 18.dp, bottom = 18.dp)
        ) {
            // Title — up to two lines now that the card is taller
            Text(
                novel.title,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight    = FontWeight.ExtraBold,
                    fontSize      = 28.sp,
                    lineHeight    = 34.sp,
                    letterSpacing = (-0.3).sp
                ),
                color    = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(Modifier.height(12.dp))

            // Row: play + label (plain — no pill), rating
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Rounded.PlayArrow,
                        contentDescription = null,
                        tint     = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        if (resumeChapter != null) "Resume Ch. $resumeChapter" else "Start Reading",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize   = 15.sp
                        ),
                        color = Color.White
                    )
                }

                // Rating badge
                if (novel.rating.isNotBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Rounded.Star,
                            contentDescription = null,
                            tint     = StarGold,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            novel.rating,
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}

// ── Recently Read row ─────────────────────────────────────────────────────────
// Mirrors the "Waiting to watch" carousel design: portrait media cards over
// the cover art, title top, footer with an info badge + progress bar. Two
// deliberate departures from the reference:
//  - No center play button — the whole card (minus the "i" badge) IS the
//    play target, so a redundant button would just clutter the cover art.
//  - The "i" badge opens the detail page; everywhere else on the card opens
//    the reader at the exact last-read chapter. Nested clickables handle
//    this correctly — the inner "i" click consumes the tap before it can
//    bubble to the card's own onClick.
@Composable
internal fun RecentlyReadRow(
    items: List<ContinueReadingInfo>,
    onOpenReader: (slug: String, chapterNum: Int) -> Unit,
    onOpenDetail: (String) -> Unit
) {
    Column {
        Text(
            "Recently Read",
            modifier = Modifier.padding(horizontal = 16.dp),
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight    = FontWeight.ExtraBold,
                letterSpacing = (-0.2).sp
            ),
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(14.dp))
        LazyRow(
            contentPadding        = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            items(items, key = { it.novel.slug }) { info ->
                RecentCard(
                    info         = info,
                    onOpenReader = { onOpenReader(info.novel.slug, info.progress.lastChapterNum) },
                    onOpenDetail = { onOpenDetail(info.novel.slug) }
                )
            }
        }
    }
}

// Recently Read card: 126×189dp portrait (CHANGE: slightly smaller, was 140×210 — see
// RECENT_CARD_W/H; title 12sp, padding 10dp, "i" badge 22dp to match). CHANGE (kept): the frosted rectangle
// behind the info badge / progress bar is gone — badge, chapter label and
// progress sit straight on the cover's dark scrim. Title uses the reader's
// font (Montserrat).
@Composable
private fun RecentCard(
    info: ContinueReadingInfo,
    onOpenReader: () -> Unit,
    onOpenDetail: () -> Unit
) {
    val novel = info.novel
    val progress = (info.progress.lastChapterNum.toFloat() / novel.chapterCount.coerceAtLeast(1))
        .coerceIn(0f, 1f)
    // CHANGE: a novel with an unknown chapter count (0) used to read "Ch.5/0".
    val chapterText = if (novel.chapterCount > 0)
        "Ch.${info.progress.lastChapterNum}/${novel.chapterCount}"
    else
        "Ch.${info.progress.lastChapterNum}"

    Box(
        modifier = Modifier
            .width(RECENT_CARD_W)
            .height(RECENT_CARD_H)
            .pressable(onClick = onOpenReader, pressedScale = 0.97f)   // CHANGE (motion)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        CoverImage(
            url                = novel.coverUrl,
            contentDescription = novel.title,
            modifier           = Modifier.fillMaxSize()
        )

        // Scrim — light top, dark bottom, so the title reads over any cover
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0f    to Color.Black.copy(alpha = 0.10f),
                            0.5f  to Color.Black.copy(alpha = 0.20f),
                            1f    to Color.Black.copy(alpha = 0.88f)
                        )
                    )
                )
        )

        Column(
            modifier            = Modifier
                .fillMaxSize()
                .padding(10.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Title — top
            Text(
                novel.title,
                fontFamily = MontserratFamily,
                fontWeight = FontWeight.ExtraBold,
                fontSize   = 12.sp,
                lineHeight = 16.sp,
                color      = Color.White,
                maxLines   = 2,
                overflow   = TextOverflow.Ellipsis,
                textAlign  = TextAlign.Start
            )

            // Footer — info badge + chapter label, then progress bar
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.55f))
                            // Nested clickable — consumes the tap here so the
                            // outer card's onOpenReader never fires for this spot.
                            .clickable(onClick = onOpenDetail),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "i",
                            color      = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontStyle  = androidx.compose.ui.text.font.FontStyle.Italic,
                            fontSize   = 13.sp
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        chapterText,
                        color      = Color.White.copy(alpha = 0.85f),
                        fontSize   = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Spacer(Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color.White.copy(alpha = 0.28f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progress)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(2.dp))
                            .background(Color.White)
                    )
                }
            }
        }
    }
}

// ── Genre Showcase — decorative, colour-gradient typography per card ─────────
// Horizontally scrolling row (was a fixed 3-card row) so any number of real
// genres fit, each in one of 6 alternating decorative styles (was 3) — see
// GenreGlassTile (ui/components). Ends in a "See More" card that opens the
// existing DiscoverScreen, which already lists every genre.
@Composable
internal fun GenreShowcaseRow(
    genres: List<String>,
    onGenreClick: (String) -> Unit,
    onDiscoverClick: (() -> Unit)?
) {
    Column {
        Row(
            modifier              = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Text(
                "Genre",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight    = FontWeight.ExtraBold,
                    letterSpacing = (-0.2).sp
                ),
                color = MaterialTheme.colorScheme.onBackground
            )
            // FIX: the "See all" link that used to sit here opened the same place as
            // the trailing "See More" card — one route is enough.
        }
        Spacer(Modifier.height(14.dp))
        // CHANGE: fixed 3-card Row → LazyRow so the showcase can hold up to
        // 8 real genres plus a trailing "See More" card without squeezing.
        LazyRow(
            contentPadding        = PaddingValues(start = 16.dp, end = 16.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            itemsIndexed(genres, key = { _, g -> g }) { i, genre ->
                GenreGlassTile(
                    genre    = genre,
                    styleIdx = i % 6,
                    onClick  = { onGenreClick(genre) },
                    modifier = Modifier.width(104.dp)
                )
            }
            if (onDiscoverClick != null) {
                item(key = "genre_see_more") {
                    SeeMoreGenreCard(
                        onClick  = onDiscoverClick,
                        modifier = Modifier.width(104.dp)
                    )
                }
            }
        }
    }
}

// CHANGE: GenreDecorativeCard moved to ui/components/GlassCards.kt as
// GenreGlassTile so Discover's genre grid can use the exact same tile (with
// dark-mode-safe gradients). Its 72dp height / 6-style history lives there.

// New — trailing card at the end of the genre showcase row. Distinct from
// the decorative cards on purpose (outlined accent style, not white glass)
// so it reads as an action, not another genre.
@Composable
private fun SeeMoreGenreCard(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .height(72.dp)
            .pressable(onClick = onClick, pressedScale = 0.96f)   // CHANGE (motion)
            .clip(RoundedCornerShape(16.dp))
            .background(AccentBlue.copy(alpha = 0.10f))
            .border(1.5.dp, AccentBlue.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
            .padding(6.dp),
        contentAlignment = Alignment.Center
    ) {
        // CHANGE (UI polish): the small arrow + "See More" label became a single big,
        // thick, rounded ">" — same icon as the row headers.
        Icon(
            SolarArrows.ChevronRight,
            contentDescription = "See more genres",
            tint     = AccentBlue,
            modifier = Modifier.size(32.dp)
        )
    }
}

// ── Section Header ────────────────────────────────────────────────────────────
@Composable
internal fun SectionHeader(title: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight    = FontWeight.ExtraBold,
                letterSpacing = (-0.2).sp
            ),
            color = MaterialTheme.colorScheme.onBackground
        )
        // FIX: removed the "See all" label that was here — it had no click
        // handler, so it looked tappable and did nothing.
    }
}
