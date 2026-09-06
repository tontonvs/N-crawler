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
}
