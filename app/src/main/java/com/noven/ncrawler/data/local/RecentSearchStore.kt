package com.noven.ncrawler.data.local

import android.content.Context

// Simple SharedPreferences-backed store for recent search terms.
// Capped at 5, deduped case-insensitively, most-recent-first.
class RecentSearchStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getRecent(): List<String> {
        val raw = prefs.getString(KEY_TERMS, "") ?: ""
        return if (raw.isBlank()) emptyList() else raw.split(DELIMITER)
    }

    fun addRecent(term: String) {
        val trimmed = term.trim()
        if (trimmed.isBlank()) return

        val updated = getRecent()
            .filterNot { it.equals(trimmed, ignoreCase = true) }
            .toMutableList()
            .apply { add(0, trimmed) }
            .take(MAX_RECENT)

        prefs.edit()
            .putString(KEY_TERMS, updated.joinToString(DELIMITER))
            .apply()
    }

    companion object {
        private const val PREFS_NAME = "recent_searches"
        private const val KEY_TERMS  = "terms"
        private const val DELIMITER  = "\u0001"
        private const val MAX_RECENT = 5
    }
}
