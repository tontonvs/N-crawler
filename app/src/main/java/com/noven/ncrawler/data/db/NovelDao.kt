package com.noven.ncrawler.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface NovelDao {

    // ── Browse / Search cache ────────────────────────────────────────────
    @Upsert
    suspend fun upsertAll(novels: List<NovelEntity>)

    @Upsert
    suspend fun upsert(novel: NovelEntity)

    @Query("SELECT * FROM novels WHERE slug = :slug LIMIT 1")
    suspend fun getBySlug(slug: String): NovelEntity?

    // FIX: @Upsert replaces the WHOLE row, so every browse/search refresh used to
    // reset isInLibrary (and blank fields). The repository now reads the cached
    // rows through this and merges before upserting.
    @Query("SELECT * FROM novels WHERE slug IN (:slugs)")
    suspend fun getBySlugs(slugs: List<String>): List<NovelEntity>

    // Drives the bookmark button on the Detail screen.
    @Query("SELECT isInLibrary FROM novels WHERE slug = :slug")
    fun isInLibraryFlow(slug: String): Flow<Boolean?>

    // Downloads no longer depend on the library flag (removing a novel from
    // the library must not make its downloads vanish from the Downloads tab).
    @Query("SELECT * FROM novels WHERE slug IN (SELECT novelSlug FROM download_progress)")
    fun downloadedNovelsFlow(): Flow<List<NovelEntity>>

    // Live query — UI collects as Flow
    @Query("SELECT * FROM novels WHERE isInLibrary = 1 ORDER BY title ASC")
    fun libraryFlow(): Flow<List<NovelEntity>>

    @Query("""
        SELECT * FROM novels
        WHERE title LIKE '%' || :q || '%'
        ORDER BY title ASC
        LIMIT 40
    """)
    suspend fun searchLocal(q: String): List<NovelEntity>

    // ── Updates (new chapters) ───────────────────────────────────────────
    @Query("SELECT * FROM novels WHERE slug = :slug LIMIT 1")
    fun observeBySlug(slug: String): Flow<NovelEntity?>

    // Novels worth checking: favourites plus anything with a download.
    @Query("""
        SELECT * FROM novels
        WHERE isInLibrary = 1 OR slug IN (SELECT novelSlug FROM download_progress)
    """)
    suspend fun trackedNovels(): List<NovelEntity>

    // Library "Updates" section: tracked novels with an unread new-chapter range.
    @Query("""
        SELECT * FROM novels
        WHERE newToChapter > 0
          AND (isInLibrary = 1 OR slug IN (SELECT novelSlug FROM download_progress))
        ORDER BY updateFoundAt DESC
    """)
    fun updatesFlow(): Flow<List<NovelEntity>>

    @Query("UPDATE novels SET newFromChapter = :from, newToChapter = :to, updateFoundAt = :at WHERE slug = :slug")
    suspend fun setUpdate(slug: String, from: Int, to: Int, at: Long)

    @Query("UPDATE novels SET lastActivityAt = :at WHERE slug = :slug")
    suspend fun touch(slug: String, at: Long)

    @Query("UPDATE novels SET newFromChapter = 0, newToChapter = 0, updateFoundAt = 0 WHERE slug = :slug")
    suspend fun clearUpdate(slug: String)

    // Reading up to (or past) the newest new chapter counts as "seen".
    @Query("""
        UPDATE novels SET newFromChapter = 0, newToChapter = 0, updateFoundAt = 0
        WHERE slug = :slug AND newToChapter > 0 AND newToChapter <= :readChapter
    """)
    suspend fun clearUpdateIfRead(slug: String, readChapter: Int)

    // ── Library ──────────────────────────────────────────────────────────
    @Query("UPDATE novels SET isInLibrary = :inLibrary WHERE slug = :slug")
    suspend fun setLibrary(slug: String, inLibrary: Boolean)

}
