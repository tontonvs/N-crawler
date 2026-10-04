package com.noven.ncrawler.data.local

import android.content.Context

/**
 * When each novel was last checked for new chapters. SharedPreferences (a few
 * timestamps, no relations) so the background check and the pull-to-refresh can
 * skip novels checked a moment ago instead of hammering the sources.
 */
class UpdateCheckStore(context: Context) {

    private val prefs = context.getSharedPreferences("ncrawler_update_checks", Context.MODE_PRIVATE)

    fun lastChecked(slug: String): Long = prefs.getLong("last_$slug", 0L)

    // When the user last opened the Library tab — the nav-bar badge counts only updates
    // found after this, so it clears once you have looked.
    fun librarySeenAt(): Long = prefs.getLong("library_seen_at", 0L)

    fun markLibrarySeen(at: Long = System.currentTimeMillis()) {
        prefs.edit().putLong("library_seen_at", at).apply()
    }

    fun markChecked(slug: String) {
        prefs.edit().putLong("last_$slug", System.currentTimeMillis()).apply()
    }
}
