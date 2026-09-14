package com.noven.ncrawler.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Tracks download state for a novel's chapters.
 * One row per novel — updated as chapters download.
 */
@Entity(tableName = "download_progress")
data class DownloadProgress(
    @PrimaryKey val novelSlug: String,
    val totalChapters: Int,
    val downloadedChapters: Int,
    val status: DownloadStatus,
    val lastUpdated: Long = System.currentTimeMillis()
)

enum class DownloadStatus {
    QUEUED,      // waiting to start
    DOWNLOADING, // actively downloading
    PAUSED,      // user paused or app killed
    COMPLETE,    // all chapters downloaded
    ERROR        // failed — can retry
}
