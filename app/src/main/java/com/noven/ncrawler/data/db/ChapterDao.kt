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

    // CHANGE (download fix): fetches a handful of chapters (with their full
    // text) by primary key. The TXT exporter walks a novel in small batches
    // of ids instead of loading every chapter body at once — chaptersForNovel()
    // above emits ALL bodies in one list, which on a 2,700-chapter novel is
    // tens of MB. id is the primary key, so this is an index lookup, and the
    // caller keeps batches small (well under SQLite's 999-variable limit).
    @Query("SELECT * FROM chapters WHERE id IN (:ids)")
    suspend fun chaptersByIds(ids: List<String>): List<ChapterEntity>

    // CHANGE (download fix): one-time cleanup of chapters saved from the
    // placeholder strings the scrapers return on failure/paywall (see
    // ChapterFetchGuard). Returns how many rows were removed. Same prefix
    // list as ChapterFetchGuard — keep the two in sync.
    @Query(
        "DELETE FROM chapters WHERE title = 'Error' " +
        "OR content LIKE 'Failed to load chapter%' " +
        "OR content LIKE 'Could not load chapter content%' " +
        "OR content LIKE 'This chapter requires purchase%' " +
        "OR content LIKE 'This chapter is locked on%'"
    )
    suspend fun purgePlaceholderChapters(): Int
}
