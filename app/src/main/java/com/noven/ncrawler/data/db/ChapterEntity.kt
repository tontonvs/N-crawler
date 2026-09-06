package com.noven.ncrawler.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Downloaded chapter content stored offline.
 */
@Entity(
    tableName = "chapters",
    foreignKeys = [
        ForeignKey(
            entity        = NovelEntity::class,
            parentColumns = ["slug"],
            childColumns  = ["novelSlug"],
            onDelete      = ForeignKey.CASCADE
        )
    ],
    indices = [Index("novelSlug")]
)
data class ChapterEntity(
    @PrimaryKey val id: String,             // "{novelSlug}::{chapterNum}"
    val novelSlug: String,
    val chapterNum: Int,
    val title: String,
    val content: String,                    // full HTML stripped to plain text
    val downloadedAt: Long = System.currentTimeMillis()
)
