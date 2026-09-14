package com.noven.ncrawler

import android.app.Application
import androidx.work.Configuration
import com.noven.ncrawler.data.db.AppDatabase
import com.noven.ncrawler.data.repository.NovelRepository

class NCrawlerApp : Application(), Configuration.Provider {

    val db         by lazy { AppDatabase.get(this) }
    val repository by lazy { NovelRepository(db, this) }

    // WorkManager custom configuration — keeps it lightweight on low-end devices
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(android.util.Log.INFO)
            .build()
}
