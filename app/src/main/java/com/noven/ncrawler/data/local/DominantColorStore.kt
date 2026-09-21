package com.noven.ncrawler.data.local

import android.content.Context

// The dominant cover colour of the novel the user most recently opened on the
// Detail screen. Detail computes it (Palette) when the cover loads and saves it
// here; the Browse screen reads it to colour the cut-out behind the downloads
// button. Only the latest one is kept. Plain SharedPreferences — no Room migration.
class DominantColorStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("ncrawler_dominant_color", Context.MODE_PRIVATE)

    fun saveLast(slug: String, argb: Int) {
        prefs.edit()
            .putString(KEY_SLUG, slug)
            .putInt(KEY_ARGB, argb)
            .apply()
    }

    // ARGB of the last opened novel's dominant colour, or null if no novel has
    // been opened on the Detail screen yet.
    fun getLast(): Int? = if (prefs.contains(KEY_ARGB)) prefs.getInt(KEY_ARGB, 0) else null

    private companion object {
        const val KEY_SLUG = "last_slug"
        const val KEY_ARGB = "last_argb"
    }
}
