package com.noven.ncrawler.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Remembers where the user left off reading.
 * One row per novel — updated every time a chapter is opened.
 */
@Entity(tableName = "reading_progress")
data class ReadingProgress(
    @PrimaryKey val novelSlug: String,
    val lastChapterNum: Int,
    val lastChapterTitle: String,
    val scrollPosition: Int = 0,        // pixel offset for restoring scroll
    val lastReadAt: Long = System.currentTimeMillis()
)
