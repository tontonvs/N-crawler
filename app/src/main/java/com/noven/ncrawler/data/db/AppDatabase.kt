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
        ReadingProgress::class,
        ReaderBookmark::class
    ],
    version = 5,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun novelDao(): NovelDao
    abstract fun chapterDao(): ChapterDao
    abstract fun downloadProgressDao(): DownloadProgressDao
    abstract fun readingProgressDao(): ReadingProgressDao
    abstract fun readerBookmarkDao(): ReaderBookmarkDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        // 3 -> 4: novels.author. A real migration (not the destructive fallback)
        // so downloaded chapters, library and reading progress survive the update.
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE novels ADD COLUMN author TEXT NOT NULL DEFAULT ''")
            }
        }

        // 4 -> 5: reader page bookmarks. A real migration (the destructive fallback below
        // would wipe downloaded chapters). The SQL must match ReaderBookmark exactly.
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `reader_bookmarks` (" +
                    "`id` TEXT NOT NULL, `novelSlug` TEXT NOT NULL, `chapterNum` INTEGER NOT NULL, " +
                    "`chapterTitle` TEXT NOT NULL, `fraction` REAL NOT NULL, `createdAt` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`id`))"
                )
            }
        }

        fun get(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "ncrawler.db"
                )
                .addMigrations(MIGRATION_3_4, MIGRATION_4_5)
                .fallbackToDestructiveMigration()
                .build()
                .also { INSTANCE = it }
            }
    }
}
