package com.noven.ncrawler.data.local

import android.content.Context

// Remembers which chapters of each novel the reader has actually opened, so the
// table of contents can tick individual chapters instead of assuming "everything
// before the current chapter is read".
//
// Plain SharedPreferences (one string-set per novel slug) on purpose: no Room
// entity means no schema version bump / migration. A novel with 2,000 chapters
// read is ~2,000 short strings — fine for prefs.
class ReadChaptersStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("ncrawler_read_chapters", Context.MODE_PRIVATE)

    private fun key(slug: String) = "read_$slug"

    fun getRead(slug: String): Set<Int> =
        prefs.getStringSet(key(slug), null)
            .orEmpty()
            .mapNotNull { it.toIntOrNull() }
            .toSet()

    // Adds [chapterNum] and returns the updated set. The set from getStringSet
    // must never be mutated in place (Android may hand back its live copy), so
    // a fresh set is built and written back.
    fun markRead(slug: String, chapterNum: Int): Set<Int> {
        val current = getRead(slug)
        if (chapterNum in current) return current
        val updated = current + chapterNum
        prefs.edit()
            .putStringSet(key(slug), updated.map { it.toString() }.toSet())
            .apply()
        return updated
    }
}
