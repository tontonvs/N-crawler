package com.noven.ncrawler.data.repository

import com.noven.ncrawler.data.db.NovelEntity

/** Where one source is in a search. */
enum class SectionState {
    LOADING,   // request still running
    DONE,      // finished (novels may be empty = no matches)
    FAILED     // couldn't be reached / timed out (novels = any cached matches)
}

/**
 * One source's slice of a search: its name and the novels it returned for the
 * keyword. Sections come out in the user's source-priority order, and each
 * fills in on its own as soon as that source answers.
 */
data class SearchSection(
    val sourceId: String,
    val sourceName: String,
    val state: SectionState,
    val novels: List<NovelEntity>
)
