package com.noven.ncrawler.data.local

import android.content.Context
import android.os.Build

/**
 * Tracks how long this app has held a dataSync foreground service, so the
 * download worker can stay inside Android 15's limit instead of being killed
 * by it.
 *
 * THE LIMIT (Android 15+, apps targeting 35+): a dataSync foreground service
 * may run 6 hours in total per 24 hours while the app is in the background.
 * At the limit the system calls Service.onTimeout() and the service has a few
 * seconds to stop, otherwise the process is killed with
 * "A foreground service of type dataSync did not stop within its timeout".
 * Once the 6 hours are used, starting another dataSync foreground service
 * throws ForegroundServiceStartNotAllowedException until the user brings the
 * app to the foreground (which resets the timer).
 *
 * WHAT THIS DOES: keeps a rolling 24-hour ledger of our own foreground time
 * (intervals in SharedPreferences, so it survives process death). Workers ask:
 *   canStartForeground()  - enough time left to be worth starting one?
 *   mustYield()           - close to the limit; hand over to a background job
 *   reset()               - user opened the app; the system timer reset
 * It deliberately over-counts (it also counts time while the app is visible,
 * and counts overlapping sessions twice) so it errs on the safe side.
 *
 * Below Android 15 everything here is a no-op: no limit exists there.
 */
class ForegroundBudget(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("ncrawler_fgs_budget", Context.MODE_PRIVATE)

    // Literal 35 (Android 15) so this compiles regardless of compileSdk.
    private val enabled = Build.VERSION.SDK_INT >= 35

    companion object {
        private const val KEY_INTERVALS = "intervals"   // "start:end;start:end"
        private const val KEY_RESET_AT  = "reset_at"

        private const val DAY_MS   = 24L * 60 * 60 * 1000
        private const val LIMIT_MS = 6L * 60 * 60 * 1000

        // Don't begin a foreground session with less than this left...
        private const val MIN_START_REMAINING_MS = 30L * 60 * 1000
        // ...and hand over to a background job when this little is left.
        // (Start threshold > yield threshold, so a fresh session can't yield
        // straight away.)
        private const val YIELD_REMAINING_MS = 15L * 60 * 1000

        private const val MAX_INTERVALS = 64
    }

    @Synchronized
    fun canStartForeground(now: Long = System.currentTimeMillis()): Boolean =
        !enabled || remaining(now) > MIN_START_REMAINING_MS

    @Synchronized
    fun mustYield(now: Long = System.currentTimeMillis()): Boolean =
        enabled && remaining(now) <= YIELD_REMAINING_MS

    /** Starts a session; returns its id, or -1 when tracking is off (Android < 15). */
    @Synchronized
    fun beginSession(now: Long = System.currentTimeMillis()): Long {
        if (!enabled) return -1L
        val list = read()
        var id = now
        while (list.any { it[0] == id }) id++
        list.add(longArrayOf(id, id))
        write(list, now)
        return id
    }

    /** Moves the session's end to "now". Cheap; call every so often and at the end. */
    @Synchronized
    fun touch(sessionId: Long, now: Long = System.currentTimeMillis()) {
        if (!enabled || sessionId < 0) return
        val list = read()
        val hit = list.firstOrNull { it[0] == sessionId }
        if (hit != null) hit[1] = now else list.add(longArrayOf(sessionId, now))
        write(list, now)
    }

    /** The user brought the app to the foreground: the system timer restarts. */
    @Synchronized
    fun reset(now: Long = System.currentTimeMillis()) {
        if (!enabled) return
        prefs.edit().putLong(KEY_RESET_AT, now).apply()
    }

    // ── internals ──────────────────────────────────────────────────────────
    private fun remaining(now: Long): Long = LIMIT_MS - used(now)

    private fun used(now: Long): Long {
        val from = maxOf(now - DAY_MS, prefs.getLong(KEY_RESET_AT, 0L))
        return read().sumOf { (minOf(it[1], now) - maxOf(it[0], from)).coerceAtLeast(0L) }
    }

    private fun read(): MutableList<LongArray> {
        val raw = prefs.getString(KEY_INTERVALS, null).orEmpty()
        if (raw.isBlank()) return mutableListOf()
        return raw.split(";").mapNotNull { part ->
            val bits = part.split(":")
            val s = bits.getOrNull(0)?.toLongOrNull()
            val e = bits.getOrNull(1)?.toLongOrNull()
            if (s != null && e != null) longArrayOf(s, e) else null
        }.toMutableList()
    }

    private fun write(list: List<LongArray>, now: Long) {
        val keepFrom = now - DAY_MS
        val kept = list.filter { it[1] >= keepFrom }.takeLast(MAX_INTERVALS)
        prefs.edit()
            .putString(KEY_INTERVALS, kept.joinToString(";") { "${it[0]}:${it[1]}" })
            .apply()
    }
}
