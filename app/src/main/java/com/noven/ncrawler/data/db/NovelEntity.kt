package com.noven.ncrawler.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Persisted novel record.
 * Novels are cached after first browse/search fetch.
 * [isInLibrary] is the only flag the user controls directly.
 */
@Entity(tableName = "novels")
data class NovelEntity(
    @PrimaryKey val slug: String,           // e.g. "shadow-slave"
    val title: String,
    val coverUrl: String,
    val synopsis: String,
    val status: String,                     // "Ongoing" | "Completed"
    val rating: String,                     // e.g. "4.6"
    val genres: String,                     // comma-separated
    val chapterCount: Int,
    val latestChapter: String,
    val isInLibrary: Boolean = false,
    val cachedAt: Long = System.currentTimeMillis()
)
