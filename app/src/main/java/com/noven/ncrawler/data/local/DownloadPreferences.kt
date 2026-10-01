package com.noven.ncrawler.data.local

import android.content.Context
import android.content.SharedPreferences

/**
 * CHANGE (network choice): which network downloads are allowed to use. Replaces
 * the old Wi-Fi-only on/off switch.
 *   ANY           - Wi-Fi or mobile data, whichever is available (default)
 *   WIFI_ONLY     - only unmetered networks (Wi-Fi)
 *   CELLULAR_ONLY - only metered networks (mobile data; a metered Wi-Fi
 *                   hotspot also counts, that is how Android defines it)
 */
enum class DownloadNetwork(val label: String, val hint: String) {
    ANY("Any network", "Wi-Fi or mobile data, whichever is available."),
    WIFI_ONLY("Wi-Fi only", "Downloads wait for Wi-Fi. No mobile data."),
    CELLULAR_ONLY("Mobile data only", "Downloads wait for mobile data, even if Wi-Fi is on.")
}

/**
 * Download-behavior settings: which network downloads may use, and how many
 * novels are allowed to download at once.
 *
 * SharedPreferences rather than a Room table, same reasoning as
 * SourcePreferences — a couple of small scalar settings, not queryable
 * relational data, no DB migration needed.
 *
 * Key names are intentionally public knowledge (see companion object) so
 * a separate Settings screen can read/write the same store without this
 * class needing to expose anything beyond get/set pairs.
 */
class DownloadPreferences(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Which network downloads may use. Defaults to ANY.
     *
     * Migration: the old "Wi-Fi only" switch was stored as a boolean. If the
     * user ever touched it, that choice is honoured (true -> WIFI_ONLY,
     * false -> ANY). If they never did, they get the new default (ANY) rather
     * than the old built-in "Wi-Fi only".
     */
    fun getNetworkMode(): DownloadNetwork {
        prefs.getString(KEY_NETWORK_MODE, null)?.let { saved ->
            return DownloadNetwork.values().firstOrNull { it.name == saved } ?: DownloadNetwork.ANY
        }
        if (prefs.contains(KEY_WIFI_ONLY)) {
            return if (prefs.getBoolean(KEY_WIFI_ONLY, false)) DownloadNetwork.WIFI_ONLY else DownloadNetwork.ANY
        }
        return DownloadNetwork.ANY
    }

    fun setNetworkMode(mode: DownloadNetwork) {
        prefs.edit().putString(KEY_NETWORK_MODE, mode.name).apply()
    }

    /**
     * Calls [onChange] whenever the network mode is changed. Close the returned
     * handle to stop listening. (SharedPreferences only keeps listeners weakly,
     * so the handle also keeps the listener alive - hold on to it.)
     */
    fun observeNetworkMode(onChange: () -> Unit): AutoCloseable {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == KEY_NETWORK_MODE) onChange()
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        return AutoCloseable { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    /**
     * How many novels may download simultaneously. Defaults to 1 —
     * deliberately conservative for a low-end device: parsing and writing
     * multiple chapter streams to disk at once is the expensive part, not
     * the network wait between chapters.
     */
    fun getConcurrentLimit(): Int = prefs.getInt(KEY_CONCURRENT_LIMIT, 1)

    fun setConcurrentLimit(limit: Int) {
        prefs.edit().putInt(KEY_CONCURRENT_LIMIT, limit.coerceAtLeast(1)).apply()
    }

    /**
     * The chapter range the user last asked for on a novel. Needed so a queued
     * download can start later, and so Resume/Retry re-downloads that range
     * instead of the whole novel. Prefs (not a Room column) to avoid a schema
     * bump — the DB uses destructive migration, which would wipe the library.
     */
    fun saveRange(slug: String, start: Int, end: Int) {
        prefs.edit().putString(rangeKey(slug), "$start:$end").apply()
    }

    fun getRange(slug: String): Pair<Int, Int>? {
        val parts = prefs.getString(rangeKey(slug), null)?.split(":") ?: return null
        val start = parts.getOrNull(0)?.toIntOrNull() ?: return null
        val end   = parts.getOrNull(1)?.toIntOrNull() ?: return null
        return start to end
    }

    fun clearRange(slug: String) {
        prefs.edit().remove(rangeKey(slug)).apply()
    }

    private fun rangeKey(slug: String) = "range_$slug"

    /**
     * An arbitrary set of chapters (picked in the chapter list, "First N",
     * "Missing only") as opposed to one contiguous range. Stored here rather
     * than in the worker's input Data, which is capped at 10 KB — a few
     * thousand chapter numbers wouldn't fit. Encoded as runs: "1-50,60,70-80".
     * The worker reads it when it starts; null means "the whole saved range".
     */
    fun saveSelection(slug: String, nums: Collection<Int>) {
        val sorted = nums.distinct().sorted()
        val sb = StringBuilder()
        var i = 0
        while (i < sorted.size) {
            var j = i
            while (j + 1 < sorted.size && sorted[j + 1] == sorted[j] + 1) j++
            if (sb.isNotEmpty()) sb.append(',')
            if (j == i) sb.append(sorted[i]) else sb.append(sorted[i]).append('-').append(sorted[j])
            i = j + 1
        }
        prefs.edit().putString(selectionKey(slug), sb.toString()).apply()
    }

    fun getSelection(slug: String): Set<Int>? {
        val raw = prefs.getString(selectionKey(slug), null)?.takeIf { it.isNotBlank() } ?: return null
        val out = HashSet<Int>()
        for (part in raw.split(",")) {
            val bits = part.split("-")
            val a = bits.getOrNull(0)?.toIntOrNull() ?: continue
            val b = bits.getOrNull(1)?.toIntOrNull() ?: a
            if (b >= a && b - a < 100_000) for (n in a..b) out.add(n)
        }
        return out.ifEmpty { null }
    }

    fun clearSelection(slug: String) {
        prefs.edit().remove(selectionKey(slug)).apply()
    }

    private fun selectionKey(slug: String) = "selection_$slug"

    /**
     * CHANGE (TXT export): the folder the user picked (Storage Access
     * Framework tree URI, stored as a string). Access is persisted by
     * DownloadsViewModel.onExportFolderChosen(), so this survives restarts.
     */
    fun getExportTreeUri(): String? = prefs.getString(KEY_EXPORT_TREE, null)

    fun setExportTreeUri(uri: String) {
        prefs.edit().putString(KEY_EXPORT_TREE, uri).apply()
    }

    /**
     * CHANGE (download fix): true once the one-time cleanup of placeholder
     * chapters (see ChapterFetchGuard) has run, so the full-table LIKE scan
     * never repeats.
     */
    fun isPlaceholderPurgeDone(): Boolean = prefs.getBoolean(KEY_PURGE_DONE, false)

    fun setPlaceholderPurgeDone() {
        prefs.edit().putBoolean(KEY_PURGE_DONE, true).apply()
    }

    companion object {
        private const val PREFS_NAME = "ncrawler_downloads"
        const val KEY_WIFI_ONLY = "wifi_only"   // legacy boolean, read once for migration
        private const val KEY_NETWORK_MODE = "network_mode"
        const val KEY_CONCURRENT_LIMIT = "concurrent_limit"
        private const val KEY_EXPORT_TREE = "export_tree_uri"
        private const val KEY_PURGE_DONE = "placeholder_purge_done_v1"
    }
}
