package com.noven.ncrawler.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

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
    val chapterUrl: String = "",            // real URL scraped from detail page
    val content: String,
    val downloadedAt: Long = System.currentTimeMillis()
)
