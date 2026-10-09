package com.noven.ncrawler.ui.screens.reader

import com.noven.ncrawler.ui.components.SolarIcons
import android.provider.Settings
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noven.ncrawler.data.scraper.ChapterLink
import com.noven.ncrawler.ui.theme.MontserratFamily
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import com.noven.ncrawler.data.db.ReaderBookmark
import com.noven.ncrawler.ui.components.BookmarkGold
import kotlin.math.roundToInt

// ── Table of contents (content of the shared ReaderSheet) ───────────────────
// No check marks: a chapter you've opened is simply DIMMED, an unread one stays
// full-strength. "Read" = the reader actually opened that chapter (readChapters)
// — individual chapters, NOT everything before the current one.
// "Current" = the chapter that's open (filled row + "Reading" badge).
// "Your place" = the chapter progress is saved at, shown only while a different
// chapter is open (you peeked ahead) so it's clear where the novel will reopen.
// Opens already scrolled to the chapter being read; flipping the sort order
// glides the list back to the top so the change is visible.
@Composable
private fun ColumnScope.ChapterTocContent(
    chapters: List<ChapterLink>,
    currentNum: Int,
    placeNum: Int,
    readChapters: Set<Int>,
    bookmarkedNums: Set<Int>,
    // Hoisted into TocTabs: the pager drops an off-screen page, and swiping back
    // to Contents must keep the sort order and scroll spot instead of resetting.
    listState: LazyListState,
    sortAscending: Boolean,
    onToggleSort: () -> Unit,
    positioned: MutableState<Boolean>,
    onSelect: (Int) -> Unit
) {
    val p = LocalSheetPalette.current

    // distinctBy: a repeated chapter number in the scraped list would show the
    // same row twice (and would crash a keyed list).
    val sorted = remember(chapters, sortAscending) {
        val unique = chapters.distinctBy { it.num }
        if (sortAscending) unique.sortedBy { it.num } else unique.sortedByDescending { it.num }
    }

    // Auto-scroll to the chapter being read, once, as soon as the list exists
    // (the chapter list loads separately from the sheet opening). Instant, not
    // animated: from chapter 1 to chapter 2000 an animation would be a blur.
    // Lands 3 rows below the top so there's context above the current row.
    LaunchedEffect(sorted.isNotEmpty()) {
        if (sorted.isNotEmpty() && !positioned.value) {
            positioned.value = true
            // "Your place" first — the chapter the reader would continue from, even
            // if a peek at another chapter is open right now — else the open chapter.
            val target = if (placeNum > 0 && sorted.any { it.num == placeNum }) placeNum else currentNum
            val index  = sorted.indexOfFirst { it.num == target }
            listState.scrollToItem((index - 3).coerceAtLeast(0))
        }
    }

    // Sort flipped → glide to the top. Skips the first run (that's just the
    // sheet opening, and it must stay on the current chapter).
    var sortSeen by remember { mutableStateOf(false) }
    LaunchedEffect(sortAscending) {
        if (!sortSeen) sortSeen = true else smoothScrollToTop(listState)
    }

    // Header: title | sort pill (no close button — drag the handle, tap
    // outside, or press back)
    Row(
        modifier              = Modifier.fillMaxWidth().padding(bottom = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically
    ) {
        Text(
            "TABLE OF CONTENTS",
            fontFamily    = MontserratFamily,
            fontWeight    = FontWeight.ExtraBold,
            fontSize      = 12.sp,
            letterSpacing = 1.sp,
            color         = p.ink
        )
        SortPill(ascending = sortAscending, onClick = onToggleSort)
    }

    HorizontalDivider(color = p.hairline)

    if (sorted.isEmpty()) {
        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = p.ink)
        }
    } else {
        LazyColumn(
            state          = listState,
            modifier       = Modifier.fillMaxWidth().weight(1f),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            // No `key` on purpose: with keys, LazyColumn re-anchors to the row
            // that WAS at the top after the sort flips — i.e. it would leap to
            // the far end of a 2000-chapter list before our glide-to-top runs.
            // Rows hold no state, so index-based reuse costs nothing.
            items(sorted) { chapter ->
                val isCurrent = chapter.num == currentNum
                val isRead    = !isCurrent && chapter.num in readChapters
                val isPlace   = !isCurrent && placeNum > 0 && chapter.num == placeNum

                // Text colour is picked against the surface the row sits on:
                // the open chapter is an ink-filled pill (paper text); read
                // chapters are dimmed ink; unread are full ink.
                val onRow = when {
                    isCurrent -> p.onInk
                    isRead    -> p.dim
                    else      -> p.ink
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (isCurrent) p.ink else Color.Transparent)
                        .clickable { onSelect(chapter.num) }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    Text(
                        "Ch.${chapter.num}",
                        fontFamily = MontserratFamily,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize   = 12.sp,
                        color      = onRow,
                        maxLines   = 1,
                        modifier   = Modifier.padding(end = 10.dp)
                    )
                    Text(
                        chapter.title,
                        fontFamily = MontserratFamily,
                        fontWeight = if (isCurrent) FontWeight.Bold else if (isRead) FontWeight.Medium else FontWeight.SemiBold,
                        fontSize   = 14.sp,
                        color      = onRow,
                        maxLines   = 1,
                        overflow   = TextOverflow.Ellipsis,
                        modifier   = Modifier.weight(1f)
                    )
                    // Gold marker: this chapter has a page bookmark
                    if (chapter.num in bookmarkedNums) {
                        Spacer(Modifier.width(8.dp))
                        Icon(
                            SolarIcons.BookmarkBold,
                            contentDescription = "Bookmarked",
                            tint     = BookmarkGold,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    // Right badge
                    when {
                        isCurrent -> {
                            Spacer(Modifier.width(8.dp))
                            Box(
                                Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(onRow.copy(alpha = 0.2f))
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text("Reading", fontFamily = MontserratFamily, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = onRow)
                            }
                        }
                        isPlace -> {
                            Spacer(Modifier.width(8.dp))
                            Box(
                                Modifier
                                    .clip(RoundedCornerShape(50))
                                    .border(1.dp, p.ink.copy(alpha = 0.4f), RoundedCornerShape(50))
                                    .padding(horizontal = 10.dp, vertical = 3.dp)
                            ) {
                                Text("Your place", fontFamily = MontserratFamily, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = p.ink)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Contents | Bookmarks tabs (content of the TOC sheet) ─────────────────────
// Tap a tab or swipe sideways between the two pages. The Contents page keeps its
// scroll spot and sort order because that state lives here, not in the page.
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ColumnScope.TocTabs(
    chapters: List<ChapterLink>,
    currentNum: Int,
    placeNum: Int,
    readChapters: Set<Int>,
    bookmarks: List<ReaderBookmark>,
    onSelect: (Int) -> Unit,
    onOpenBookmark: (ReaderBookmark) -> Unit,
    onRemoveBookmark: (Int) -> Unit
) {
    val pagerState    = rememberPagerState(pageCount = { 2 })
    val scope         = androidx.compose.runtime.rememberCoroutineScope()
    val listState     = rememberLazyListState()
    var sortAscending by remember { mutableStateOf(true) }
    val positioned    = remember { mutableStateOf(false) }
    val bookmarkedNums = remember(bookmarks) { bookmarks.map { it.chapterNum }.toSet() }

    Row(
        modifier              = Modifier.fillMaxWidth().padding(bottom = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        TocTab(
            label    = "Contents",
            selected = pagerState.currentPage == 0,
            onClick  = { scope.launch { pagerState.animateScrollToPage(0) } }
        )
        TocTab(
            label    = if (bookmarks.isEmpty()) "Bookmarks" else "Bookmarks (${bookmarks.size})",
            selected = pagerState.currentPage == 1,
            onClick  = { scope.launch { pagerState.animateScrollToPage(1) } }
        )
    }

    HorizontalPager(
        state    = pagerState,
        modifier = Modifier.fillMaxWidth().weight(1f)
    ) { page ->
        Column(Modifier.fillMaxSize()) {
            if (page == 0) {
                ChapterTocContent(
                    chapters       = chapters,
                    currentNum     = currentNum,
                    placeNum       = placeNum,
                    readChapters   = readChapters,
                    bookmarkedNums = bookmarkedNums,
                    listState      = listState,
                    sortAscending  = sortAscending,
                    onToggleSort   = { sortAscending = !sortAscending },
                    positioned     = positioned,
                    onSelect       = onSelect
                )
            } else {
                BookmarksContent(
                    bookmarks = bookmarks,
                    onOpen    = onOpenBookmark,
                    onRemove  = onRemoveBookmark
                )
            }
        }
    }
}

@Composable
private fun TocTab(label: String, selected: Boolean, onClick: () -> Unit) {
    val p = LocalSheetPalette.current
    Text(
        label,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) p.ink else p.chip)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        fontFamily = MontserratFamily,
        fontWeight = FontWeight.Bold,
        fontSize   = 12.sp,
        color      = if (selected) p.onInk else p.ink
    )
}

// One row per bookmarked chapter (a chapter has at most one bookmark), in chapter
// order. Tap = open that chapter at the saved spot; the bin removes the bookmark.
@Composable
private fun ColumnScope.BookmarksContent(
    bookmarks: List<ReaderBookmark>,
    onOpen: (ReaderBookmark) -> Unit,
    onRemove: (Int) -> Unit
) {
    val p = LocalSheetPalette.current
    if (bookmarks.isEmpty()) {
        Column(
            modifier              = Modifier.fillMaxWidth().weight(1f).padding(horizontal = 32.dp),
            horizontalAlignment   = Alignment.CenterHorizontally,
            verticalArrangement   = Arrangement.Center
        ) {
            Icon(
                SolarIcons.Bookmark,
                contentDescription = null,
                tint     = p.muted,
                modifier = Modifier.size(40.dp)
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "No bookmarks yet",
                fontFamily = MontserratFamily,
                fontWeight = FontWeight.Bold,
                fontSize   = 15.sp,
                color      = p.ink
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Tap the bookmark icon next to Settings while reading to save your spot.",
                fontFamily = MontserratFamily,
                fontSize   = 13.sp,
                color      = p.muted,
                textAlign  = TextAlign.Center
            )
        }
        return
    }

    LazyColumn(
        modifier       = Modifier.fillMaxWidth().weight(1f),
        contentPadding = PaddingValues(bottom = 12.dp)
    ) {
        items(bookmarks, key = { it.id }) { b ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(p.chip)
                    .clickable { onOpen(b) }
                    .padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    SolarIcons.BookmarkBold,
                    contentDescription = null,
                    tint     = BookmarkGold,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Ch.${b.chapterNum} · ${b.chapterTitle}",
                        fontFamily = MontserratFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize   = 14.sp,
                        color      = p.ink,
                        maxLines   = 1,
                        overflow   = TextOverflow.Ellipsis
                    )
                    Text(
                        "${(b.fraction * 100).roundToInt()}% through the chapter",
                        fontFamily = MontserratFamily,
                        fontSize   = 12.sp,
                        color      = p.muted
                    )
                }
                IconButton(onClick = { onRemove(b.chapterNum) }) {
                    Icon(
                        SolarIcons.TrashBin,
                        contentDescription = "Remove bookmark",
                        tint     = p.muted,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

// Sort button — a labelled pill with a swap icon that flips 180° when the
// order changes (replaces the bare chevron, which read as "expand/collapse").
@Composable
private fun SortPill(ascending: Boolean, onClick: () -> Unit) {
    val p = LocalSheetPalette.current
    val rotation by animateFloatAsState(
        targetValue   = if (ascending) 0f else 180f,
        animationSpec = tween(320, easing = FastOutSlowInEasing),
        label         = "sortRotation"
    )
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(p.chip)
            .border(1.dp, p.hairline, RoundedCornerShape(50))
            .clickable(onClickLabel = "Reverse chapter order", onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            SolarIcons.Sort,
            contentDescription = null,
            tint     = p.ink,
            modifier = Modifier
                .size(18.dp)
                .graphicsLayer { rotationZ = rotation }
        )
        Spacer(Modifier.width(6.dp))
        Text(
            if (ascending) "Oldest first" else "Newest first",
            fontFamily = MontserratFamily,
            fontWeight = FontWeight.Bold,
            fontSize   = 12.sp,
            color      = p.ink
        )
    }
}

// Glide to the top of a (possibly huge) list slowly enough to be seen. A list
// of 2000+ chapters can't be animated end-to-end, so if we're far down it
// jumps to 8 rows from the top first and animates the remaining distance.
private const val TOC_SORT_SCROLL_MS = 650

private suspend fun smoothScrollToTop(state: LazyListState) {
    if (state.firstVisibleItemIndex > 8) state.scrollToItem(8)

    // Row pitch (height + gap) from two neighbouring visible rows.
    val visible = state.layoutInfo.visibleItemsInfo
    val pitch = when {
        visible.size >= 2 -> (visible[1].offset - visible[0].offset).toFloat()
        visible.size == 1 -> visible[0].size.toFloat()
        else              -> 0f
    }
    val distance = state.firstVisibleItemIndex * pitch + state.firstVisibleItemScrollOffset
    if (distance > 0f) {
        state.animateScrollBy(-distance, tween(TOC_SORT_SCROLL_MS, easing = FastOutSlowInEasing))
    }
    // Pin the exact top in case the distance estimate was a few px off.
    state.scrollToItem(0)
}
