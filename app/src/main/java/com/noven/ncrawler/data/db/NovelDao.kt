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

    // ── Library ──────────────────────────────────────────────────────────
    @Query("UPDATE novels SET isInLibrary = :inLibrary WHERE slug = :slug")
    suspend fun setLibrary(slug: String, inLibrary: Boolean)

    // ── Cache management ─────────────────────────────────────────────────
    @Query("DELETE FROM novels WHERE isInLibrary = 0 AND cachedAt < :before")
    suspend fun pruneOldCache(before: Long)
}
