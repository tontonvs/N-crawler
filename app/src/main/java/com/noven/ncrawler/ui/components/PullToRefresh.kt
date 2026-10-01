package com.noven.ncrawler.ui.components

import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshContainer
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.flow.Flow

/**
 * Pull-down-to-refresh that works with this project's Material 3 (1.2.x, from
 * Compose BOM 2024.04.01). The one-line `PullToRefreshBox` only exists from
 * 1.3.0, so this wraps the older state + container API behind the same shape:
 *
 *   AppPullToRefresh(isRefreshing = vm.isRefreshing, onRefresh = vm::refresh) { …list… }
 *
 * The screen owns the loading flag; this just mirrors it into the indicator.
 * [onRefresh] fires only for a pull the user made (not when the flag was
 * already set by the screen itself), so a button-triggered refresh can't
 * trigger a second one. [failures], if given, shows a short toast whenever it
 * emits — keeps "Couldn't refresh" handling out of every screen.
 *
 * Note: only scrollable content can be pulled. If the screen is showing a
 * skeleton or an error (nothing scrolls), pulling does nothing — those states
 * have their own Retry.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppPullToRefresh(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    failures: Flow<Unit>? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val state           = rememberPullToRefreshState()
    val latestOnRefresh = rememberUpdatedState(onRefresh)

    // The user pulled past the threshold and the screen isn't refreshing yet.
    if (state.isRefreshing && !isRefreshing) {
        LaunchedEffect(Unit) { latestOnRefresh.value() }
    }

    // Mirror the screen's loading flag into the indicator.
    LaunchedEffect(isRefreshing) {
        if (isRefreshing) state.startRefresh() else state.endRefresh()
    }

    val context = LocalContext.current
    if (failures != null) {
        LaunchedEffect(failures) {
            failures.collect {
                Toast.makeText(context, "Couldn't refresh — check your connection", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Box(modifier.nestedScroll(state.nestedScrollConnection)) {
        content()
        PullToRefreshContainer(
            state    = state,
            modifier = Modifier.align(Alignment.TopCenter)
        )
    }
}
