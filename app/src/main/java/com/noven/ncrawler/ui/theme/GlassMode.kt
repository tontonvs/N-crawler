package com.noven.ncrawler.ui.theme

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

// Glass mode switch (Settings -> Look). OFF = the classic look (default);
// ON = tv3 glass: 44% cards, 51% nav, no borders, real backdrop blur on the
// nav, the Detail top buttons and the Reader pills.
//
// A tiny singleton on purpose: every surface reads GlassMode.enabled (Compose
// snapshot state), so flipping the switch restyles the whole app instantly with
// no ViewModel plumbing. Saved in SharedPreferences like the other small
// settings; init() runs once from MainActivity before the UI starts, so the
// first frame is already in the right mode (no flash of the other look).
object GlassMode {
    private const val FILE = "ui_prefs"
    private const val KEY  = "glass_mode"

    private var prefs: SharedPreferences? = null

    var enabled by mutableStateOf(false)
        private set

    fun init(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        prefs = p
        enabled = p.getBoolean(KEY, false)
    }

    fun set(on: Boolean) {
        enabled = on
        prefs?.edit()?.putBoolean(KEY, on)?.apply()
    }
}
