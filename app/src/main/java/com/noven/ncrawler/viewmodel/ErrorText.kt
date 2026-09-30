package com.noven.ncrawler.viewmodel

import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

// Short, plain error text for the screen. The full exception still goes to
// Logcat (each ViewModel logs it) — the user just doesn't need to read
// "UnknownHostException: Unable to resolve host …".
// A scraper's own message is kept when it's already short and readable.
fun friendlyError(e: Throwable, fallback: String = "Something went wrong"): String = when (e) {
    is UnknownHostException, is ConnectException -> "No internet"
    is SocketTimeoutException                    -> "Timed out"
    is IOException                               -> "Network error"
    else -> e.message
        ?.trim()
        ?.takeIf { it.isNotEmpty() && it.length <= 48 && !it.contains("Exception") && !it.contains("java.") }
        ?: fallback
}
