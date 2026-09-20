package com.noven.ncrawler.data.local

import android.content.Context

// Remembers WHERE in a chapter the reader stopped, so reopening the novel drops
// you back at the same part of the text instead of the top.
//
// Stored as a fraction of the chapter (0..1), not a pixel offset, so it still
// lands in the right place after the font size / line height / alignment changes
// (all of which reflow the text). One entry per novel — the chapter being read —
// which is all "continue reading" needs. Plain SharedPreferences: no Room
// migration.
class ReadingPositionStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("ncrawler_reading_position", Context.MODE_PRIVATE)

    private fun key(slug: String) = "pos_$slug"

    fun save(slug: String, chapterNum: Int, fraction: Float) {
        if (slug.isBlank() || chapterNum <= 0) return
        prefs.edit()
            .putString(key(slug), "$chapterNum:${fraction.coerceIn(0f, 1f)}")
            .apply()
    }

    // The saved fraction, but only if it was saved for THIS chapter.
    fun get(slug: String, chapterNum: Int): Float? {
        val raw = prefs.getString(key(slug), null) ?: return null
        val parts = raw.split(":")
        if (parts.size != 2) return null
        val chapter  = parts[0].toIntOrNull() ?: return null
        val fraction = parts[1].toFloatOrNull() ?: return null
        return if (chapter == chapterNum) fraction else null
    }
}
