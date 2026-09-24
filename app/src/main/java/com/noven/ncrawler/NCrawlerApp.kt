package com.noven.ncrawler

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.work.Configuration
import com.noven.ncrawler.data.db.AppDatabase
import com.noven.ncrawler.data.repository.NovelRepository

class NCrawlerApp : Application(), Configuration.Provider {

    val db         by lazy { AppDatabase.get(this) }
    val repository by lazy { NovelRepository(db, this) }

    companion object {
        // CHANGE (Downloads overhaul — reliability fix): channel for the
        // download worker's foreground notification. Must exist before the
        // first setForeground() call, so it's created here at app startup
        // rather than lazily inside the worker.
        const val DOWNLOAD_CHANNEL_ID = "chapter_downloads"
    }

    override fun onCreate() {
        super.onCreate()
        createDownloadNotificationChannel()
    }

    private fun createDownloadNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                DOWNLOAD_CHANNEL_ID,
                "Chapter downloads",
                NotificationManager.IMPORTANCE_LOW // no sound/heads-up, just a progress bar
            ).apply {
                description = "Shows progress while chapters are downloading"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    // WorkManager custom configuration — keeps it lightweight on low-end devices
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(android.util.Log.INFO)
            .build()
}
