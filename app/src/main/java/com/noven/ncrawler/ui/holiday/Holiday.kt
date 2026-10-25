package com.noven.ncrawler.ui.holiday

import java.time.LocalDate

// ═════════════════════════════════════════════════════════════════════════════
// The four celebrations the app dresses up for, and WHEN each one is "on".
//
// Windows are inclusive and deliberately a few days wide so the fun is not over
// in a blink:
//   Christmas  18 Dec – 27 Dec
//   Easter     Good Friday – Easter Monday  (Easter Sunday is worked out per year)
//   Halloween  24 Oct – 1 Nov
//   2nd June   1 Jun – 3 Jun
// To change a window or a greeting, this is the only file to edit.
// ═════════════════════════════════════════════════════════════════════════════
enum class Holiday(val label: String, val emoji: String, val greeting: String, val tagline: String) {
    CHRISTMAS("Christmas", "🎄", "Merry Christmas!", "Snow's on its way"),
    EASTER("Easter", "🐣", "Happy Easter!", "Egg-cellent reading ahead"),
    HALLOWEEN("Halloween", "🎃", "Happy Halloween!", "Read if you dare"),
    JUNE_2("2nd June", "🎉", "Happy 2nd June!", "Make it a good one")
}

object HolidayCalendar {

    /** Easter Sunday of [year] (Gregorian "anonymous" algorithm). */
    fun easterSunday(year: Int): LocalDate {
        val a = year % 19
        val b = year / 100
        val c = year % 100
        val d = b / 4
        val e = b % 4
        val f = (b + 8) / 25
        val g = (b - f + 1) / 3
        val h = (19 * a + b - d - g + 15) % 30
        val i = c / 4
        val k = c % 4
        val l = (32 + 2 * e + 2 * i - h - k) % 7
        val m = (a + 11 * h + 22 * l) / 451
        val month = (h + l - 7 * m + 114) / 31
        val day = (h + l - 7 * m + 114) % 31 + 1
        return LocalDate.of(year, month, day)
    }

    private fun inRange(d: LocalDate, from: LocalDate, to: LocalDate) =
        !d.isBefore(from) && !d.isAfter(to)

    /** The holiday whose window contains [date], or null on an ordinary day. */
    fun forDate(date: LocalDate): Holiday? {
        val y = date.year
        val easter = easterSunday(y)
        return when {
            inRange(date, LocalDate.of(y, 12, 18), LocalDate.of(y, 12, 27)) -> Holiday.CHRISTMAS
            inRange(date, easter.minusDays(2), easter.plusDays(1))          -> Holiday.EASTER
            inRange(date, LocalDate.of(y, 10, 24), LocalDate.of(y, 11, 1))  -> Holiday.HALLOWEEN
            inRange(date, LocalDate.of(y, 6, 1), LocalDate.of(y, 6, 3))     -> Holiday.JUNE_2
            else -> null
        }
    }
}
