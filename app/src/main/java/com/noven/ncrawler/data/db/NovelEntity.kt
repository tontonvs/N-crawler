package com.noven.ncrawler.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "novels")
data class NovelEntity(
    @PrimaryKey val slug: String,
    val title: String,
    val coverUrl: String,
    val synopsis: String,
    val status: String,
    val rating: String,
    val genres: String,
    val chapterCount: Int,
    val latestChapter: String,
    // Tab-separated list of "chapterNum|url" pairs scraped from detail page
    // e.g. "1|https://novelarrow.com/novel/slug/c1-...\t2|https://..."
    val chapterUrls: String = "",
    val isInLibrary: Boolean = false,
    val cachedAt: Long = System.currentTimeMillis(),
    // CHANGE (Detail redesign): shown in the meta row where "Latest" used to be.
    // Defaulted so every existing NovelEntity(...) call keeps compiling.
    val author: String = "",
    // CHANGE (updates): the range of chapters found by an update check that the user
    // hasn't read through / dismissed yet. 0/0 = nothing new. Kept on the novel row so
    // Library, Detail and the notification all read one source of truth.
    val newFromChapter: Int = 0,
    val newToChapter: Int = 0,
    val updateFoundAt: Long = 0L
)

/** How many chapters the pending update covers (0 when there is none). */
fun NovelEntity.newChapterCount(): Int =
    if (newToChapter > 0 && newToChapter >= newFromChapter) newToChapter - newFromChapter + 1 else 0
