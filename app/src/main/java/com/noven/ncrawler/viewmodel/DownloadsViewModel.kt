package com.noven.ncrawler.viewmodel

import android.app.Application
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.noven.ncrawler.NCrawlerApp
import com.noven.ncrawler.data.db.DownloadProgress
import com.noven.ncrawler.data.db.NovelEntity
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

// CHANGE (Downloads overhaul): Downloads used to piggyback on
// LibraryViewModel/LibraryItem. Split out into its own ViewModel so future
// edits to Library don't risk breaking Downloads and vice versa — the two
// screens still read the same underlying tables (via NovelRepository), they
// just no longer share a ViewModel class.
data class DownloadItem(
    val novel: NovelEntity,
    val progress: DownloadProgress
)

class DownloadsViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = (app as NCrawlerApp).repository

    val downloadItems: StateFlow<List<DownloadItem>> = combine(
        repo.allDownloadProgressFlow(),
        repo.libraryFlow()
    ) { progressList, novels ->
        progressList.mapNotNull { progress ->
            val novel = novels.find { it.slug == progress.novelSlug } ?: return@mapNotNull null
            DownloadItem(novel = novel, progress = progress)
        }
    }.stateIn(
        scope        = viewModelScope,
        started      = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // CHANGE (reliability fix): true when "Wi-Fi only" downloads is on and
    // the device isn't currently on an unmetered network. This is the
    // silent-block bug found in logcat — WorkManager just holds a QUEUED
    // download forever with no error surfaced anywhere, if this condition
    // is true. DownloadsScreen uses it to swap the generic "Queued..."
    // label for "Waiting for Wi-Fi…" on affected items.
    val downloadsBlockedByNetwork: StateFlow<Boolean> = callbackFlow {
        val cm = getApplication<Application>().getSystemService(ConnectivityManager::class.java)

        fun emitCurrent() {
            val wifiOnly = repo.downloadPreferences().isWifiOnly()
            if (!wifiOnly) {
                trySend(false)
                return
            }
            val caps = cm.getNetworkCapabilities(cm.activeNetwork)
            val unmetered = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) == true
            trySend(!unmetered)
        }

        emitCurrent()

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) = emitCurrent()
            override fun onAvailable(network: Network) = emitCurrent()
            override fun onLost(network: Network) = emitCurrent()
        }
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        cm.registerNetworkCallback(request, callback)

        awaitClose { cm.unregisterNetworkCallback(callback) }
    }.stateIn(
        scope        = viewModelScope,
        started      = SharingStarted.WhileSubscribed(5000),
        initialValue = false
    )

    /** Pauses an active or queued download. */
    fun pause(slug: String) {
        viewModelScope.launch { repo.cancelDownload(slug) }
    }

    /**
     * Resumes a paused download, or retries one with failed chapters.
     * Re-queuing is safe either way — the worker skips any chapter already
     * saved and only re-attempts what's actually missing.
     */
    fun resume(slug: String) {
        viewModelScope.launch { repo.queueDownloadAll(slug) }
    }

    /** Deletes downloaded chapters for a novel. Leaves it in the Library. */
    fun delete(slug: String) {
        viewModelScope.launch { repo.deleteDownload(slug) }
    }

    suspend fun sizeBytesFor(slug: String): Long = repo.downloadedSizeBytes(slug)
}
