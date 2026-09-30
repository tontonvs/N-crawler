package com.noven.ncrawler.data.repository

import java.io.IOException

/**
 * Thrown for a chapter the site refuses to serve (paid / locked).
 * Deliberately NOT retried by the download worker: retrying can never help.
 */
class ChapterUnavailableException(message: String) : IOException(message)

/**
 * CHANGE (download fix): every source's fetchChapterByUrl() catches its own
 * errors and RETURNS a fake chapter instead of throwing:
 *   Pair("Error", "Failed to load chapter: <reason>")
 *   Pair(title,   "Could not load chapter content. Please try again.")
 *   Pair(title,   "This chapter requires purchase on <site>.")
 * NovelRepository.downloadChapter() then saved those strings as if they were
 * the chapter, the worker counted them as downloaded, and "already saved"
 * meant they were never retried. One network blip permanently poisoned a
 * chapter (and the reader showed the error text as the story).
 *
 * This guard turns those placeholder returns back into exceptions BEFORE
 * anything is saved. One place instead of editing seven scrapers; the
 * scrapers themselves are untouched.
 *
 * The prefixes below must match the strings the sources return. If a source
 * ever gets a new placeholder message, add it here (and to
 * ChapterDao.purgePlaceholderChapters, which uses the same list).
 */
object ChapterFetchGuard {

    private val paywallPrefixes = listOf(
        "This chapter requires purchase",
        "This chapter is locked on"
    )

    private val failurePrefixes = listOf(
        "Failed to load chapter",
        "Could not load chapter content"
    )

    /** Throws if [content] is really an error/paywall placeholder or empty. */
    fun check(content: String) {
        val body = content.trim()
        if (paywallPrefixes.any { body.startsWith(it) }) {
            throw ChapterUnavailableException(body)
        }
        if (failurePrefixes.any { body.startsWith(it) }) {
            throw IOException(body)
        }
        if (body.isBlank()) {
            throw IOException("Chapter came back empty")
        }
    }
}
