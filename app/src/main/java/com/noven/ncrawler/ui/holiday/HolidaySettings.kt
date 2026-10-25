package com.noven.ncrawler.ui.holiday

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.time.LocalDate

// What the Settings picker offers. AUTO follows the calendar (the real behaviour);
// the rest force one look so each can be tried today.
// TESTING ONLY: once you are happy, the picker goes (see HolidayPickerCard.kt) and
// this can shrink to "always AUTO".
enum class HolidayChoice(val label: String, val emoji: String, val holiday: Holiday?) {
    AUTO("Automatic (by date)", "📅", null),
    OFF("Off", "🚫", null),
    CHRISTMAS("Christmas", "🎄", Holiday.CHRISTMAS),
    EASTER("Easter", "🐣", Holiday.EASTER),
    HALLOWEEN("Halloween", "🎃", Holiday.HALLOWEEN),
    JUNE_2("2nd June", "🎉", Holiday.JUNE_2)
}

// Same pattern as GlassMode: a tiny singleton holding Compose state, saved in
// SharedPreferences, init() once from MainActivity so the first frame is right.
object HolidaySettings {
    private const val FILE = "ui_prefs"
    private const val KEY_CHOICE = "holiday_choice"
    private const val KEY_SPLASH = "holiday_splash_day"

    private var prefs: SharedPreferences? = null

    var choice by mutableStateOf(HolidayChoice.AUTO)
        private set

    /** Goes up on every manual pick; the app-open greeting replays whenever it changes. */
    var previewTick by mutableIntStateOf(0)
        private set

    fun init(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        prefs = p
        val saved = p.getString(KEY_CHOICE, null)
        choice = HolidayChoice.values().firstOrNull { it.name == saved } ?: HolidayChoice.AUTO
    }

    fun setChoice(c: HolidayChoice) {
        choice = c
        previewTick++
        prefs?.edit()?.putString(KEY_CHOICE, c.name)?.apply()
    }

    /** The holiday to show right now (null = plain app). Reads Compose state, so callers recompose on a pick. */
    fun active(today: LocalDate = LocalDate.now()): Holiday? = when (choice) {
        HolidayChoice.AUTO -> HolidayCalendar.forDate(today)
        HolidayChoice.OFF  -> null
        else               -> choice.holiday
    }

    /** True the first time it is asked on a given day for [h]; the app-open greeting uses it so a holiday week greets you once a day, not on every launch. */
    fun consumeDailyGreeting(h: Holiday): Boolean {
        val key = "${LocalDate.now()}:${h.name}"
        val p = prefs ?: return true
        if (p.getString(KEY_SPLASH, null) == key) return false
        p.edit().putString(KEY_SPLASH, key).apply()
        return true
    }
}
