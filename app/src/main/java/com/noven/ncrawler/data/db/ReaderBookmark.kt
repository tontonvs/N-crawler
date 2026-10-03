package com.noven.ncrawler.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * A page bookmark made in the reader. The reader scrolls instead of paging, so a
 * "page" is the reading spot: [fraction] (0..1) of the way down the chapter. A
 * fraction (not pixels) survives font-size and line-height changes — the same
 * trick ReadingPositionStore uses.
 *
 * One bookmark per chapter: the primary key is "{novelSlug}::{chapterNum}", so
 * bookmarking a chapter again replaces/toggles it instead of piling up rows.
 * No foreign key on purpose — it keeps the v4 -> v5 migration a single CREATE TABLE.
 */
@Entity(tableName = "reader_bookmarks")
data class ReaderBookmark(
    @PrimaryKey val id: String,
    val novelSlug: String,
    val chapterNum: Int,
    val chapterTitle: String,
    val fraction: Float,
    val createdAt: Long = System.currentTimeMillis()
) {
    companion object {
        fun idFor(novelSlug: String, chapterNum: Int) = "$novelSlug::$chapterNum"
    }
}

@Dao
interface ReaderBookmarkDao {

    @Query("SELECT * FROM reader_bookmarks WHERE novelSlug = :slug ORDER BY chapterNum ASC")
    fun observe(slug: String): Flow<List<ReaderBookmark>>

    @Query("SELECT * FROM reader_bookmarks WHERE id = :id LIMIT 1")
    suspend fun get(id: String): ReaderBookmark?

    @Upsert
    suspend fun upsert(bookmark: ReaderBookmark)

    @Query("DELETE FROM reader_bookmarks WHERE id = :id")
    suspend fun delete(id: String)
}
