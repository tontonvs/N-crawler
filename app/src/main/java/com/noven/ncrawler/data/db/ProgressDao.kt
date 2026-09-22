package com.noven.ncrawler.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadProgressDao {

    @Upsert
    suspend fun upsert(progress: DownloadProgress)

    @Query("SELECT * FROM download_progress WHERE novelSlug = :slug LIMIT 1")
    fun observe(slug: String): Flow<DownloadProgress?>

    @Query("SELECT * FROM download_progress WHERE novelSlug = :slug LIMIT 1")
    suspend fun get(slug: String): DownloadProgress?

    @Query("SELECT * FROM download_progress ORDER BY lastUpdated DESC")
    fun observeAll(): Flow<List<DownloadProgress>>

    @Query("UPDATE download_progress SET downloadedChapters = :count, status = :status, lastUpdated = :now WHERE novelSlug = :slug")
    suspend fun updateProgress(slug: String, count: Int, status: DownloadStatus, now: Long = System.currentTimeMillis())

    @Query("DELETE FROM download_progress WHERE novelSlug = :slug")
    suspend fun delete(slug: String)

    // CHANGE (Downloads overhaul): powers the concurrent-download guard —
    // counts how many novels are actively DOWNLOADING right now so a new
    // download request knows whether to start immediately or stay QUEUED.
    @Query("SELECT COUNT(*) FROM download_progress WHERE status = :status")
    suspend fun countByStatus(status: DownloadStatus): Int
}

@Dao
interface ReadingProgressDao {

    @Upsert
    suspend fun upsert(progress: ReadingProgress)

    @Query("SELECT * FROM reading_progress WHERE novelSlug = :slug LIMIT 1")
    suspend fun get(slug: String): ReadingProgress?

    @Query("SELECT * FROM reading_progress ORDER BY lastReadAt DESC")
    fun observeAll(): Flow<List<ReadingProgress>>

    @Query("DELETE FROM reading_progress WHERE novelSlug = :slug")
    suspend fun delete(slug: String)
}
