package com.noven.ncrawler.viewmodel

import android.app.Application
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.noven.ncrawler.NCrawlerApp
import com.noven.ncrawler.data.db.DownloadProgress
import com.noven.ncrawler.data.db.DownloadStatus
import com.noven.ncrawler.data.db.NovelEntity
import com.noven.ncrawler.data.scraper.SourceRegistry
import com.noven.ncrawler.data.local.DownloadNetwork
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
    val progress: DownloadProgress,
    // CHANGE (source folders): which site this novel came from, derived from its slug.
    val sourceId: String = SourceRegistry.DEFAULT_SOURCE_ID,
    val sourceName: String = ""
)

// CHANGE (source folders): completed downloads of one source, newest first —
// backs the folder card on the Downloads screen.
data class SourceFolder(
    val sourceId: String,
    val sourceName: String,
    val items: List<DownloadItem>
)

// CHANGE (network choice): why a QUEUED download isn't moving. Replaces the old
// Wi-Fi-only Boolean so the label can name the network the user actually asked for.
enum class NetworkWait(val label: String) {
    NONE(""),
    WIFI("Waiting for Wi-Fi"),
    CELLULAR("Waiting for mobile data"),
    OFFLINE("Waiting for a connection")
}

class DownloadsViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = (app as NCrawlerApp).repository

    // FIX: joined against the LIBRARY flow, so removing a novel from the library
    // (or any refresh that reset its flag) made its downloads disappear from
    // this screen. Downloads now join against the novels that have a download row.
    val downloadItems: StateFlow<List<DownloadItem>> = combine(
        repo.allDownloadProgressFlow(),
        repo.downloadedNovelsFlow()
    ) { progressList, novels ->
        val bySlug = novels.associateBy { it.slug }
        progressList.mapNotNull { progress ->
            val novel = bySlug[progress.novelSlug] ?: return@mapNotNull null
            val sourceId = SourceRegistry.sourceIdOf(progress.novelSlug)
            DownloadItem(
                novel      = novel,
                progress   = progress,
                sourceId   = sourceId,
                sourceName = SourceRegistry.displayNameOf(sourceId)
            )
        }
    }.stateIn(
        scope        = viewModelScope,
        started      = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // CHANGE (source folders): finished downloads grouped by source. Folders follow
    // registry order (stable, so they don't jump around); "Unknown source" is last.
    // Inside a folder the most recently updated novel comes first, so the folder's
    // 3 peeking covers are always the latest ones.
    val sourceFolders: StateFlow<List<SourceFolder>> = downloadItems
        .map { items ->
            val order = SourceRegistry.all().map { it.id } + SourceRegistry.UNKNOWN_SOURCE_ID
            items.filter { it.progress.status == DownloadStatus.COMPLETE }
                .groupBy { it.sourceId }
                .map { (id, list) ->
                    SourceFolder(
                        sourceId   = id,
                        sourceName = SourceRegistry.displayNameOf(id),
                        items      = list.sortedByDescending { it.progress.lastUpdated }
                    )
                }
                .sortedBy { order.indexOf(it.sourceId) }
        }
        .stateIn(
            scope        = viewModelScope,
            started      = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // CHANGE (reliability fix, generalised for the network choice): what, if
    // anything, a QUEUED download is currently waiting for. WorkManager holds a
    // download whose network constraint isn't met with no error and no feedback
    // (the silent block found in logcat), so the Downloads screen uses this to
    // say "Waiting for Wi-Fi / mobile data / a connection" instead of an
    // ambiguous "Queued".
    //   ANY           -> waits only when there is no connection at all
    //   WIFI_ONLY     -> waits unless on an unmetered network
    //   CELLULAR_ONLY -> waits unless on a metered network
    // Re-evaluated on every network change AND when the user changes the mode
    // in Settings (previously the setting change alone never updated the label).
    val networkWait: StateFlow<NetworkWait> = callbackFlow {
        val cm = getApplication<Application>().getSystemService(ConnectivityManager::class.java)
        val prefs = repo.downloadPreferences()

        fun emitCurrent() {
            val caps = cm.getNetworkCapabilities(cm.activeNetwork)
            val hasNet = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
            val unmetered = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) == true
            val wait = when (prefs.getNetworkMode()) {
                DownloadNetwork.ANY           -> if (hasNet) NetworkWait.NONE else NetworkWait.OFFLINE
                DownloadNetwork.WIFI_ONLY     -> if (hasNet && unmetered) NetworkWait.NONE else NetworkWait.WIFI
                DownloadNetwork.CELLULAR_ONLY -> if (hasNet && !unmetered) NetworkWait.NONE else NetworkWait.CELLULAR
            }
            trySend(wait)
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
        val modeWatcher = prefs.observeNetworkMode { emitCurrent() }

        awaitClose {
            cm.unregisterNetworkCallback(callback)
            modeWatcher.close()
        }
    }.stateIn(
        scope        = viewModelScope,
        started      = SharingStarted.WhileSubscribed(5000),
        initialValue = NetworkWait.NONE
    )

    /** Pauses an active or queued download. */
    fun pause(slug: String) {
        viewModelScope.launch { repo.cancelDownload(slug) }
    }

    /**
     * Resumes a paused download, or retries one with failed chapters.
     * Re-queuing is safe either way — the worker skips any chapter already
     * saved and only re-attempts what's actually missing. FIX: re-runs the
     * range originally requested (was: the whole novel, even after "Last 50").
     */
    fun resume(slug: String) {
        viewModelScope.launch { repo.resumeDownload(slug) }
    }

    /** Deletes downloaded chapters for a novel. Leaves it in the Library. */
    fun delete(slug: String) {
        viewModelScope.launch { repo.deleteDownload(slug) }
    }

    suspend fun sizeBytesFor(slug: String): Long = repo.downloadedSizeBytes(slug)

    // ── EPUB export ────────────────────────────────────────────────────────────
    // CHANGE (export): the folder is chosen once with the system folder
    // picker and remembered. takePersistableUriPermission() is what makes the
    // grant survive an app restart — without it the export would work once and
    // then fail with a SecurityException next launch.

    fun hasExportFolder(): Boolean = repo.hasExportFolder()

    /** Saves the picked folder. Returns false if access couldn't be persisted. */
    fun onExportFolderChosen(uri: Uri): Boolean {
        return try {
            getApplication<Application>().contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            repo.setExportFolder(uri.toString())
            true
        } catch (e: SecurityException) {
            Log.e("NCrawler_Downloads", "Couldn't persist export folder access: ${e.message}")
            false
        }
    }

    /** Starts (or, if already running, leaves running) an EPUB export for a novel. */
    fun exportEpub(slug: String) = repo.enqueueEpubExport(slug)
}
