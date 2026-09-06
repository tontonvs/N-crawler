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
    val cachedAt: Long = System.currentTimeMillis()
)
