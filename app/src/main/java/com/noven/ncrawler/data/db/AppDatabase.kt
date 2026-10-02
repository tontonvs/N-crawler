package com.noven.ncrawler.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        NovelEntity::class,
        ChapterEntity::class,
        DownloadProgress::class,
        ReadingProgress::class
    ],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun novelDao(): NovelDao
    abstract fun chapterDao(): ChapterDao
    abstract fun downloadProgressDao(): DownloadProgressDao
    abstract fun readingProgressDao(): ReadingProgressDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        // 3 -> 4: novels.author. A real migration (not the destructive fallback)
        // so downloaded chapters, library and reading progress survive the update.
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE novels ADD COLUMN author TEXT NOT NULL DEFAULT ''")
            }
        }

        fun get(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "ncrawler.db"
                )
                .addMigrations(MIGRATION_3_4)
                .fallbackToDestructiveMigration()
                .build()
                .also { INSTANCE = it }
            }
    }
}
