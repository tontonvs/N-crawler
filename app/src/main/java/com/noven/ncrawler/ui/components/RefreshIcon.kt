package com.noven.ncrawler.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// The animated refresh icon (refresh.json, Feather "refresh-cw": a quick wind-up,
// then two full spins, 2s per loop), played natively by LottieLite — no lottie
// dependency. Compose it while refreshing and until it reports onRested; the
// caller then swaps to the static SolarIcons.Refresh.

private val REFRESH_ICON by lazy { LottieIcon(REFRESH_JSON) }

/**
 * [animating] true  → loops (a quick wind-up, two spins, 2s per loop).
 * [animating] false → does NOT stop dead: the spin decelerates to the resting
 * pose (ease-out, 150–600ms, never longer than the loop that was left) and then
 * calls [onRested], so the caller swaps to the static icon at a moment the two
 * line up. Frame 59 of the file is a 720° turn = the resting pose.
 */
@Composable
fun AnimatedRefreshIcon(
    animating: Boolean,
    ink: Color,
    modifier: Modifier = Modifier,
    size: Dp = 32.dp,
    onRested: () -> Unit = {}
) {
    val icon = REFRESH_ICON
    val reduced = rememberReducedMotion()
    val phase = remember { Animatable(0f) }
    val rested by rememberUpdatedState(onRested)

    LaunchedEffect(animating, reduced) {
        if (animating && !reduced) {
            while (true) {
                val left = ((1f - phase.value) * icon.durationMs).toInt().coerceAtLeast(1)
                phase.animateTo(1f, tween(left, easing = LinearEasing))
                phase.snapTo(0f)
            }
        } else {
            if (!reduced && phase.value > 0f) {
                val left = ((1f - phase.value) * icon.durationMs).toInt().coerceIn(150, 600)
                phase.animateTo(1f, tween(left, easing = LinearOutSlowInEasing))
                phase.snapTo(0f)
            }
            rested()
        }
    }

    Canvas(modifier.size(size)) {
        // phase is read here, in the draw phase — no recomposition per frame.
        // Reduced motion: a still, fully drawn icon (the skeleton + the swap show progress).
        val f = if (reduced) 0f else icon.frameAt(phase.value)
        icon.draw(this, f, ink, ink)
    }
}

private const val REFRESH_JSON = """{"v":"5.6.5","fr":30,"ip":0,"op":60,"w":32,"h":32,"nm":"refresh-cw","ddd":0,"assets":[],"layers":[{"ddd":0,"ind":1,"ty":4,"nm":"refresh-cw","sr":1,"ks":{"o":{"a":0,"k":100,"ix":11},"r":{"a":1,"k":[{"i":{"x":[0.7],"y":[1]},"o":{"x":[0.7],"y":[0]},"t":0,"s":[0]},{"i":{"x":[0.7],"y":[1]},"o":{"x":[0.7],"y":[0]},"t":10,"s":[-20]},{"i":{"x":[0.355],"y":[1]},"o":{"x":[0.334],"y":[0]},"t":13,"s":[-20]},{"t":59,"s":[720]}],"ix":10},"p":{"a":0,"k":[16,16,0],"ix":2},"a":{"a":0,"k":[16,16,0],"ix":1},"s":{"a":0,"k":[100,100,100],"ix":6}},"ao":0,"shapes":[{"ty":"gr","it":[{"ind":0,"ty":"sh","ix":1,"ks":{"a":0,"k":{"i":[[0,0],[0,0],[-3.5,3.5],[-0.4,1.3]],"o":[[0,0],[3.5,3.5],[0.9,-0.9],[0,0]],"v":[[-11,2.1],[-6.4,6.5],[6.3,6.5],[8.4,3.1]],"c":false},"ix":2},"nm":"Path 1","mn":"ADBE Vector Shape - Group","hd":false},{"ind":1,"ty":"sh","ix":2,"ks":{"a":0,"k":{"i":[[0,0],[-4.7,-1.6],[-1,-0.9],[0,0]],"o":[[1.7,-4.7],[1.3,0.4],[0,0],[0,0]],"v":[[-8.5,-2.9],[3,-8.4],[6.4,-6.3],[11,-1.9]],"c":false},"ix":2},"nm":"Path 2","mn":"ADBE Vector Shape - Group","hd":false},{"ind":2,"ty":"sh","ix":3,"ks":{"a":0,"k":{"i":[[0,0],[0,0],[0,0]],"o":[[0,0],[0,0],[0,0]],"v":[[-11,8.1],[-11,2.1],[-5,2.1]],"c":false},"ix":2},"nm":"Path 3","mn":"ADBE Vector Shape - Group","hd":false},{"ind":3,"ty":"sh","ix":4,"ks":{"a":0,"k":{"i":[[0,0],[0,0],[0,0]],"o":[[0,0],[0,0],[0,0]],"v":[[11,-7.9],[11,-1.9],[5,-1.9]],"c":false},"ix":2},"nm":"Path 4","mn":"ADBE Vector Shape - Group","hd":false},{"ty":"mm","mm":1,"nm":"Merge Paths 1","mn":"ADBE Vector Filter - Merge","hd":false},{"ty":"st","c":{"a":0,"k":[0,0,0,1],"ix":3},"o":{"a":0,"k":100,"ix":4},"w":{"a":0,"k":2,"ix":5},"lc":2,"lj":2,"bm":0,"nm":"Stroke 1","mn":"ADBE Vector Graphic - Stroke","hd":false},{"ty":"tr","p":{"a":0,"k":[16,15.9],"ix":2},"a":{"a":0,"k":[0,0],"ix":1},"s":{"a":0,"k":[100,100],"ix":3},"r":{"a":0,"k":0,"ix":6},"o":{"a":0,"k":100,"ix":7},"sk":{"a":0,"k":0,"ix":4},"sa":{"a":0,"k":0,"ix":5},"nm":"Transform"}],"nm":"arrow","np":6,"cix":2,"bm":0,"ix":1,"mn":"ADBE Vector Group","hd":false}],"ip":0,"op":60,"st":0,"bm":0}],"markers":[]}"""
