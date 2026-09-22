package com.noven.ncrawler.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ChapterDao {

    @Upsert
    suspend fun upsert(chapter: ChapterEntity)

    @Query("SELECT * FROM chapters WHERE novelSlug = :slug ORDER BY chapterNum ASC")
    fun chaptersForNovel(slug: String): Flow<List<ChapterEntity>>

    @Query("SELECT * FROM chapters WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): ChapterEntity?

    @Query("SELECT COUNT(*) FROM chapters WHERE novelSlug = :slug")
    suspend fun downloadedCount(slug: String): Int

    // CHANGE (Downloads overhaul): chapter numbers actually saved for a
    // novel — used by NovelRepository.checkForUpdates() to work out what's
    // really missing instead of assuming downloaded chapters are always a
    // contiguous 1..N block from the start (they aren't, once any chapter
    // has ever failed and been skipped).
    @Query("SELECT chapterNum FROM chapters WHERE novelSlug = :slug")
    suspend fun downloadedChapterNums(slug: String): List<Int>

    // CHANGE (Downloads overhaul): frees a novel's downloaded chapter text.
    // Deliberately does NOT touch the novels table — the novel stays in
    // the user's Library, it's just no longer downloaded for offline
    // reading. Foreign key CASCADE on ChapterEntity means this alone is
    // enough; nothing else references chapters directly.
    @Query("DELETE FROM chapters WHERE novelSlug = :slug")
    suspend fun deleteForNovel(slug: String)

    // CHANGE (Downloads overhaul): rough on-disk size estimate for the
    // Downloads screen. LENGTH() on stored TEXT is a proxy for bytes, not
    // an exact filesystem size (SQLite may store it as UTF-8 or UTF-16
    // internally) — good enough for a "~2.1 MB downloaded" readout, not
    // meant for precise storage accounting.
    @Query("SELECT SUM(LENGTH(content)) FROM chapters WHERE novelSlug = :slug")
    suspend fun totalContentBytes(slug: String): Long?
}
