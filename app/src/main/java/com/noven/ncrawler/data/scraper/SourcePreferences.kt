package com.noven.ncrawler.data.scraper

import android.content.Context

/**
 * Stores the user's enabled sources and their priority order.
 *
 * SharedPreferences rather than a Room table on purpose — this is a single
 * small ordered list, not queryable relational data, and avoids adding a DB
 * migration for something this simple.
 *
 * Format: a single comma-joined string of source IDs, in priority order.
 * Only IDs in this list are "enabled" — anything in SourceRegistry.all()
 * but NOT in this list is disabled. A source newly added to the registry
 * (e.g. after an app update) that isn't yet in a user's saved list shows up
 * as disabled by default until they opt in — never silently starts being
 * used for browsing/search without the user choosing it.
 */
class SourcePreferences(context: Context) {

    private val prefs = context.getSharedPreferences("ncrawler_sources", Context.MODE_PRIVATE)
    private val KEY_ORDER = "enabled_priority_order"

    /** Enabled source IDs, in priority order. Falls back to every registered source, in registry order, on first run. */
    fun getPriorityOrder(): List<String> {
        val saved = prefs.getString(KEY_ORDER, null)
        if (saved.isNullOrBlank()) return SourceRegistry.all().map { it.id }
        val ids = saved.split(",").filter { it.isNotBlank() }
        return ids.ifEmpty { listOf(SourceRegistry.DEFAULT_SOURCE_ID) }
    }

    fun setPriorityOrder(orderedIds: List<String>) {
        prefs.edit().putString(KEY_ORDER, orderedIds.joinToString(",")).apply()
    }

    fun isEnabled(sourceId: String): Boolean = sourceId in getPriorityOrder()

    /**
     * Enables (appended to the end = lowest priority) or disables a source.
     * Refuses to disable the last remaining enabled source — the app always
     * needs at least one to function — and returns false in that case so
     * the UI can tell the user why the toggle didn't take.
     */
    fun setEnabled(sourceId: String, enabled: Boolean): Boolean {
        val current = getPriorityOrder().toMutableList()
        if (enabled) {
            if (sourceId !in current) current.add(sourceId)
        } else {
            if (current.size <= 1) return false
            current.remove(sourceId)
        }
        setPriorityOrder(current)
        return true
    }

    /** Swaps a source with its neighbour one position up in priority. No-op at index 0. */
    fun moveUp(sourceId: String) {
        val current = getPriorityOrder().toMutableList()
        val idx = current.indexOf(sourceId)
        if (idx <= 0) return
        current[idx] = current[idx - 1].also { current[idx - 1] = current[idx] }
        setPriorityOrder(current)
    }

    /** Swaps a source with its neighbour one position down in priority. No-op at the last index. */
    fun moveDown(sourceId: String) {
        val current = getPriorityOrder().toMutableList()
        val idx = current.indexOf(sourceId)
        if (idx == -1 || idx >= current.size - 1) return
        current[idx] = current[idx + 1].also { current[idx + 1] = current[idx] }
        setPriorityOrder(current)
    }
}
