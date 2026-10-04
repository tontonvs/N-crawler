package com.noven.ncrawler.data.local

import android.content.Context

// Remembers WHERE in a chapter the reader stopped, so reopening the novel drops
// you back at the same part of the text instead of the top.
//
// Stored as a fraction of the chapter (0..1), not a pixel offset, so it still
// lands in the right place after the font size / line height changes (all of
// which reflow the text). Plain SharedPreferences: no Room migration.
//
// TWO entries per novel, on purpose:
//   • place — the chapter you are really reading (what "continue reading" uses).
//   • peek  — a chapter you only jumped to (e.g. a glance at the newest update)
//             before reading it properly.
// Peeking used to overwrite the single entry, so after a look at chapter 78 the
// saved spot for chapter 56 was gone and the novel reopened at 78. Now the peek
// has its own slot and the place is only replaced once the peeked chapter is
// actually read (see ReaderViewModel).
class ReadingPositionStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("ncrawler_reading_position", Context.MODE_PRIVATE)

    private fun placeKey(slug: String) = "pos_$slug"
    private fun peekKey(slug: String)  = "peek_$slug"

    private fun write(key: String, chapterNum: Int, fraction: Float) {
        prefs.edit().putString(key, "$chapterNum:${fraction.coerceIn(0f, 1f)}").apply()
    }

    private fun read(key: String, chapterNum: Int): Float? {
        val raw = prefs.getString(key, null) ?: return null
        val parts = raw.split(":")
        if (parts.size != 2) return null
        val chapter  = parts[0].toIntOrNull() ?: return null
        val fraction = parts[1].toFloatOrNull() ?: return null
        return if (chapter == chapterNum) fraction else null
    }

    /** Saves the spot in the chapter you are really reading. */
    fun save(slug: String, chapterNum: Int, fraction: Float) {
        if (slug.isBlank() || chapterNum <= 0) return
        write(placeKey(slug), chapterNum, fraction)
    }

    /** Saves the spot in a chapter you only jumped to — never touches the place. */
    fun savePeek(slug: String, chapterNum: Int, fraction: Float) {
        if (slug.isBlank() || chapterNum <= 0) return
        write(peekKey(slug), chapterNum, fraction)
    }

    // The saved fraction for THIS chapter: the place first, then the peek.
    fun get(slug: String, chapterNum: Int): Float? =
        read(placeKey(slug), chapterNum) ?: read(peekKey(slug), chapterNum)
}
