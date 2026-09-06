package com.noven.ncrawler

import android.app.Application
import com.noven.ncrawler.data.db.AppDatabase
import com.noven.ncrawler.data.repository.NovelRepository

/** Application singleton — holds shared instances without DI framework. */
class NCrawlerApp : Application() {
    val db         by lazy { AppDatabase.get(this) }
    val repository by lazy { NovelRepository(db) }
}
