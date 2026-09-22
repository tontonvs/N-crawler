package com.noven.ncrawler.data.local

import android.content.Context

/**
 * Download-behavior settings: whether downloading requires Wi-Fi/unmetered
 * network, and how many novels are allowed to download at once.
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
     * true = only download over Wi-Fi/unmetered connections. Defaults to
     * true — this is a background job the user isn't necessarily watching
     * start, so it shouldn't burn mobile data without being asked to.
     */
    fun isWifiOnly(): Boolean = prefs.getBoolean(KEY_WIFI_ONLY, true)

    fun setWifiOnly(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_WIFI_ONLY, enabled).apply()
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

    companion object {
        private const val PREFS_NAME = "ncrawler_downloads"
        const val KEY_WIFI_ONLY = "wifi_only"
        const val KEY_CONCURRENT_LIMIT = "concurrent_limit"
    }
}
