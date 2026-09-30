package com.noven.ncrawler

import android.app.Activity
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.work.Configuration
import com.noven.ncrawler.data.db.AppDatabase
import com.noven.ncrawler.data.local.ForegroundBudget
import com.noven.ncrawler.data.repository.NovelRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

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

    // CHANGE (download fix): app-lifetime scope for launch-time housekeeping.
    // SupervisorJob so one failing task can never cancel another.
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        createDownloadNotificationChannel()

        // CHANGE (Android 15): the system restarts its 6-hour dataSync timer when
        // the user brings the app to the foreground, so our own ledger does too.
        // Counts started activities: 0 -> 1 means the app just became visible.
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            private var started = 0
            override fun onActivityStarted(activity: Activity) {
                if (started++ == 0) ForegroundBudget(this@NCrawlerApp).reset()
            }
            override fun onActivityStopped(activity: Activity) { if (started > 0) started-- }
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityResumed(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })

        // CHANGE (download fix): a download row left as DOWNLOADING after the
        // process died (or the worker failed) counted against the concurrent
        // limit forever — every new download then sat QUEUED and "nothing was
        // downloading". Reconcile against WorkManager's real state on launch.
        appScope.launch {
            try {
                repository.reconcileDownloads()
            } catch (e: Exception) {
                Log.w("NCrawler_App", "reconcileDownloads failed: ${e.message}")
            }
        }
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
