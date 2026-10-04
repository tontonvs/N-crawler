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

    fun markChecked(slug: String) {
        prefs.edit().putLong("last_$slug", System.currentTimeMillis()).apply()
    }
}
