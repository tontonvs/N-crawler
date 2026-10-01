package com.noven.ncrawler.ui.components

import android.os.SystemClock
import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow

/**
 * Pull-down-to-refresh, built only on stable foundation APIs (nested scroll +
 * a small animated indicator).
 *
 * Why not Material 3's own? Its pull-to-refresh API changed between releases
 * (PullToRefreshContainer + startRefresh/endRefresh -> PullToRefreshBox), and
 * the version this project resolves doesn't have the old one. This version
 * doesn't depend on which Material 3 is on the classpath.
 *
 *   AppPullToRefresh(isRefreshing = vm.isRefreshing, onRefresh = vm::refresh) { …list… }
 *
 * The screen owns the loading flag; this only draws it. [onRefresh] fires when
 * the user releases after pulling past the threshold. [failures], if given,
 * shows a short toast each time it emits.
 *
 * [indicatorTopOffset] pushes the pull indicator down from the top of this box. Use it
 * when the box runs under a bar that is drawn on top of it (Browse's collapsing top
 * bar) so the indicator lands below that bar instead of hidden behind it.
 *
 * Only scrollable content can be pulled: while a screen shows a skeleton or an
 * error (nothing scrolls), pulling does nothing — those have their own Retry.
 */
@Composable
fun AppPullToRefresh(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    failures: Flow<Unit>? = null,
    indicatorTopOffset: Dp = 0.dp,
    content: @Composable BoxScope.() -> Unit
) {
    val density      = LocalDensity.current
    val thresholdPx  = with(density) { 80.dp.toPx() }
    val maxPullPx    = thresholdPx * 1.5f
    val indicatorPx  = with(density) { 40.dp.toPx() }

    val currentRefreshing by rememberUpdatedState(isRefreshing)
    val currentOnRefresh  by rememberUpdatedState(onRefresh)

    var pull      by remember { mutableFloatStateOf(0f) }     // finger-driven distance (px, after resistance)
    var dragging  by remember { mutableStateOf(false) }
    // Set on release so the indicator doesn't dip back to 0 in the gap before
    // the screen flips its own isRefreshing flag.
    var requested by remember { mutableStateOf(false) }

    LaunchedEffect(requested) {
        if (requested) { delay(1500); requested = false }
    }
    LaunchedEffect(isRefreshing) { if (isRefreshing) requested = false }

    val active = isRefreshing || requested

    val target = when {
        dragging -> pull
        active   -> thresholdPx * 0.75f
        else     -> 0f
    }
    val shown by animateFloatAsState(
        targetValue   = target,
        animationSpec = if (dragging) snap() else tween(220),
        label         = "pullOffset"
    )

    val connection = remember(thresholdPx, maxPullPx) {
        object : NestedScrollConnection {
            // After a release, ignore the tail of the fling (it can report leftover
            // downward scroll when the list is already at the top).
            private var ignoreUntil = 0L

            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                // Finger moving back up while the list is pulled down: shrink the
                // pull first, before the list itself starts scrolling.
                if (dragging && pull > 0f && available.y < 0f) {
                    val used = maxOf(available.y, -pull)
                    pull += used
                    return Offset(0f, used)
                }
                return Offset.Zero
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                // The list is at its top and there's downward movement left over: pull.
                if (available.y > 0f && !currentRefreshing && SystemClock.uptimeMillis() > ignoreUntil) {
                    dragging = true
                    val resistance = 0.5f
                    val newPull = (pull + available.y * resistance).coerceAtMost(maxPullPx)
                    val usedRaw = (newPull - pull) / resistance
                    pull = newPull
                    return Offset(0f, usedRaw)
                }
                return Offset.Zero
            }

            // Called when the finger lifts (always, even with no velocity).
            override suspend fun onPreFling(available: Velocity): Velocity {
                val wasPulling = dragging
                dragging = false
                if (wasPulling && pull >= thresholdPx && !currentRefreshing) {
                    requested = true
                    currentOnRefresh()
                }
                pull = 0f
                ignoreUntil = SystemClock.uptimeMillis() + 600L
                return Velocity.Zero
            }
        }
    }

    val context = LocalContext.current
    if (failures != null) {
        LaunchedEffect(failures) {
            failures.collect {
                Toast.makeText(context, "Couldn't refresh — check your connection", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Box(modifier.nestedScroll(connection)) {
        content()

        val progress = (shown / thresholdPx).coerceIn(0f, 1f)
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = indicatorTopOffset)
                .graphicsLayer {
                    translationY = shown - indicatorPx
                    alpha        = if (shown > 1f) 1f else 0f
                }
                .size(40.dp)
                .shadow(4.dp, CircleShape)
                .background(MaterialTheme.colorScheme.surface, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (active) {
                CircularProgressIndicator(
                    modifier    = Modifier.size(22.dp),
                    strokeWidth = 2.dp,
                    color       = MaterialTheme.colorScheme.primary
                )
            } else {
                Icon(
                    Icons.Filled.Refresh,
                    contentDescription = null,
                    tint     = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .size(22.dp)
                        .graphicsLayer {
                            rotationZ = progress * 270f
                            alpha     = progress.coerceAtLeast(0.35f)
                        }
                )
            }
        }
    }
}
