package com.noven.ncrawler.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// ─────────────────────────────────────────────────────────────────────────────
// The two animated icons of the Browse top bar, played by LottieLite (native, no
// lottie dependency). Both are Feather-style animated icons on a 32 x 32 canvas;
// the artwork sits on the inner 24 x 24, so the default draw size (34dp) shows it
// at about 25dp — the size of the old static download icon.
//
//  * AnimatedDownloadIcon — tray + arrow at rest (frame 0); while [animating] it
//    loops (4s: the box draws, a wave fills it, a checkmark pops, it drains, the
//    arrow returns). When [animating] turns false it FINISHES the current loop and
//    rests, instead of snapping back mid-gesture.
//  * AnimatedArrowDownIcon — the collapsed pill's version, only composed while a
//    download is running: loops forever (3s: the stem draws, the head draws out
//    from its tip, hold, fade).
//
// Reduced motion (system "Remove animations"): the download icon never animates;
// the arrow shows as a still, fully drawn arrow.
// ─────────────────────────────────────────────────────────────────────────────

private val DOWNLOAD_ICON by lazy { LottieIcon(DOWNLOAD_JSON) }
private val ARROW_DOWN_ICON by lazy { LottieIcon(ARROW_DOWN_JSON) }

/** [ink] = the drawing colour; [paper] = what the white checkmark should read as
 *  (the colour of whatever the icon sits on). */
@Composable
fun AnimatedDownloadIcon(
    animating: Boolean,
    ink: Color,
    paper: Color,
    modifier: Modifier = Modifier,
    size: Dp = 34.dp
) {
    val icon = DOWNLOAD_ICON
    val reduced = rememberReducedMotion()
    val phase = remember { Animatable(0f) }

    LaunchedEffect(animating, reduced) {
        if (animating && !reduced) {
            while (true) {
                val left = ((1f - phase.value) * icon.durationMs).toInt().coerceAtLeast(1)
                phase.animateTo(1f, tween(left, easing = LinearEasing))
                phase.snapTo(0f)
            }
        } else if (phase.value > 0f) {
            // finish the loop in progress, then rest on frame 0 (frame 120 == frame 0)
            val left = ((1f - phase.value) * icon.durationMs).toInt().coerceAtLeast(1)
            phase.animateTo(1f, tween(left, easing = LinearEasing))
            phase.snapTo(0f)
        }
    }

    Canvas(modifier.size(size)) {
        icon.draw(this, icon.frameAt(phase.value), ink, paper)
    }
}

@Composable
fun AnimatedArrowDownIcon(
    ink: Color,
    modifier: Modifier = Modifier,
    size: Dp = 34.dp
) {
    val icon = ARROW_DOWN_ICON
    val reduced = rememberReducedMotion()
    val loop = rememberInfiniteTransition(label = "arrowDownLoop")
    val phase = loop.animateFloat(
        initialValue  = 0f,
        targetValue   = 1f,
        animationSpec = infiniteRepeatable(
            tween(icon.durationMs, easing = LinearEasing),
            RepeatMode.Restart
        ),
        label = "arrowDownPhase"
    )

    Canvas(modifier.size(size)) {
        // reduced motion: hold on frame 120 of 180 — the arrow fully drawn
        val f = if (reduced) 120f else icon.frameAt(phase.value)
        icon.draw(this, f, ink, ink)
    }
}

/** The arrow-down icon held still on its fully drawn frame (frame 120 of 180: stem and
 *  head complete, before the fade-out starts). Used by the Browse top bar. */
@Composable
fun StaticArrowDownIcon(
    ink: Color,
    modifier: Modifier = Modifier,
    size: Dp = 34.dp
) {
    val icon = ARROW_DOWN_ICON
    Canvas(modifier.size(size)) {
        icon.draw(this, 120f, ink, ink)
    }
}

private const val DOWNLOAD_JSON = """{"v":"5.6.5","fr":30,"ip":0,"op":120,"w":32,"h":32,"nm":"download","ddd":0,"assets":[{"id":"comp_0","layers":[{"ddd":0,"ind":1,"ty":4,"nm":"wave","sr":1,"ks":{"o":{"a":0,"k":100,"ix":11},"r":{"a":0,"k":0,"ix":10},"p":{"a":1,"k":[{"i":{"x":0.833,"y":0.833},"o":{"x":0.167,"y":0.167},"t":10,"s":[140,155,0],"to":[-96.667,0,0],"ti":[96.667,0,0]},{"i":{"x":0.6,"y":0.6},"o":{"x":0.167,"y":0.167},"t":90,"s":[-440,155,0],"to":[0,0,0],"ti":[0,0,0]},{"i":{"x":0.6,"y":1},"o":{"x":0.6,"y":0},"t":100,"s":[-440,155,0],"to":[0,63.333,0],"ti":[0,-63.333,0]},{"t":113,"s":[-440,535,0]}],"ix":2},"a":{"a":0,"k":[12,12,0],"ix":1},"s":{"a":0,"k":[1600,1600,100],"ix":6}},"ao":0,"shapes":[{"ty":"gr","it":[{"ind":0,"ty":"sh","ix":1,"ks":{"a":0,"k":{"i":[[3.373,0],[3.373,0],[3.374,0],[3.375,0],[3.372,0],[3.376,0],[3.378,0],[3.378,0],[0,0],[0,0],[0,0],[0,0],[0,0],[0,0],[0,0]],"o":[[-3.374,0],[-3.374,0],[-3.374,0],[-3.373,0],[-3.376,0],[-3.378,0],[-3.378,0],[0,0],[0,0],[0,0],[0,0],[0,0],[0,0],[0,0],[-3.374,0]],"v":[[39.253,-12.216],[32.506,-9.667],[25.758,-12.216],[19.009,-9.667],[12.264,-12.216],[5.512,-9.667],[-1.244,-12.216],[-8,-9.667],[-8,-9.144],[-8,-8.507],[-8,11.601],[46,11.601],[46,-8.507],[46,-9.144],[46,-9.667]],"c":true},"ix":2},"nm":"Path 1","mn":"ADBE Vector Shape - Group","hd":false},{"ty":"fl","c":{"a":0,"k":[0,0,0,1],"ix":4},"o":{"a":0,"k":100,"ix":5},"r":1,"bm":0,"nm":"Fill 1","mn":"ADBE Vector Graphic - Fill","hd":false},{"ty":"tr","p":{"a":1,"k":[{"i":{"x":0.4,"y":1},"o":{"x":0.4,"y":0},"t":10,"s":[11,34.601],"to":[0,-1.667],"ti":[0,1.667]},{"i":{"x":0.4,"y":0.4},"o":{"x":0.4,"y":0.4},"t":30,"s":[11,24.601],"to":[0,0],"ti":[0,0]},{"i":{"x":0.4,"y":1},"o":{"x":0.4,"y":0},"t":50,"s":[11,24.601],"to":[0,-2.333],"ti":[0,2.333]},{"t":90,"s":[11,10.601]}],"ix":2},"a":{"a":0,"k":[0,0],"ix":1},"s":{"a":0,"k":[100,100],"ix":3},"r":{"a":1,"k":[{"i":{"x":[0.667],"y":[1]},"o":{"x":[0.333],"y":[0]},"t":10,"s":[0]},{"i":{"x":[0.667],"y":[1]},"o":{"x":[0.333],"y":[0]},"t":20,"s":[-10]},{"i":{"x":[0.667],"y":[1]},"o":{"x":[0.333],"y":[0]},"t":40,"s":[0]},{"i":{"x":[0.667],"y":[1]},"o":{"x":[0.333],"y":[0]},"t":50,"s":[0]},{"i":{"x":[0.667],"y":[1]},"o":{"x":[0.333],"y":[0]},"t":60,"s":[-5]},{"t":90,"s":[0]}],"ix":6},"o":{"a":0,"k":100,"ix":7},"sk":{"a":0,"k":0,"ix":4},"sa":{"a":0,"k":0,"ix":5},"nm":"Transform"}],"nm":"wave","np":2,"cix":2,"bm":0,"ix":1,"mn":"ADBE Vector Group","hd":false}],"ip":0,"op":120,"st":0,"bm":0}]}],"layers":[{"ddd":0,"ind":1,"ty":4,"nm":"frame","sr":1,"ks":{"o":{"a":0,"k":100,"ix":11},"r":{"a":0,"k":0,"ix":10},"p":{"a":0,"k":[16,16,0],"ix":2},"a":{"a":0,"k":[12,12,0],"ix":1},"s":{"a":0,"k":[100,100,100],"ix":6}},"ao":0,"shapes":[{"ty":"gr","it":[{"ind":0,"ty":"sh","ix":1,"ks":{"a":0,"k":{"i":[[-1.104,0],[0,0],[0,0],[0,-1.104],[0,0],[1.104,0],[0,0],[0,1.104],[0,0]],"o":[[0,0],[0,0],[1.104,0],[0,0],[0,1.104],[0,0],[-1.104,0],[0,0],[0,-1.104]],"v":[[-7,-10],[0,-10],[7,-10],[9,-8],[9,8],[7,10],[-7,10],[-9,8],[-9,-8]],"c":true},"ix":2},"nm":"Path 1","mn":"ADBE Vector Shape - Group","hd":false},{"ty":"tm","s":{"a":1,"k":[{"i":{"x":[0.3],"y":[1]},"o":{"x":[0.3],"y":[0]},"t":0,"s":[36]},{"i":{"x":[0.4],"y":[1]},"o":{"x":[0.167],"y":[0]},"t":20,"s":[0]},{"i":{"x":[0.4],"y":[1]},"o":{"x":[0.4],"y":[0]},"t":104,"s":[0]},{"t":120,"s":[36]}],"ix":1},"e":{"a":1,"k":[{"i":{"x":[0.3],"y":[1]},"o":{"x":[0.3],"y":[0]},"t":0,"s":[64]},{"i":{"x":[0.4],"y":[1]},"o":{"x":[0.167],"y":[0]},"t":20,"s":[100]},{"i":{"x":[0.4],"y":[1]},"o":{"x":[0.4],"y":[0]},"t":104,"s":[100]},{"t":120,"s":[64]}],"ix":2},"o":{"a":0,"k":35,"ix":3},"m":1,"ix":2,"nm":"Trim Paths 1","mn":"ADBE Vector Filter - Trim","hd":false},{"ty":"st","c":{"a":0,"k":[0,0,0,1],"ix":3},"o":{"a":0,"k":100,"ix":4},"w":{"a":0,"k":2,"ix":5},"lc":2,"lj":2,"bm":0,"nm":"Stroke 1","mn":"ADBE Vector Graphic - Stroke","hd":false},{"ty":"tr","p":{"a":0,"k":[12,12],"ix":2},"a":{"a":0,"k":[0,0],"ix":1},"s":{"a":0,"k":[100,100],"ix":3},"r":{"a":0,"k":0,"ix":6},"o":{"a":0,"k":100,"ix":7},"sk":{"a":0,"k":0,"ix":4},"sa":{"a":0,"k":0,"ix":5},"nm":"Transform"}],"nm":"frame","np":3,"cix":2,"bm":0,"ix":1,"mn":"ADBE Vector Group","hd":false},{"ty":"gr","it":[{"ind":0,"ty":"sh","ix":1,"ks":{"a":0,"k":{"i":[[0,0],[0,0],[-1.104,0],[0,0],[0,1.104],[0,0]],"o":[[0,0],[0,1.104],[0,0],[1.104,0],[0,0],[0,0]],"v":[[-9,-2.5],[-9,0.5],[-7,2.5],[7,2.5],[9,0.5],[9,-2.5]],"c":false},"ix":2},"nm":"Path 1","mn":"ADBE Vector Shape - Group","hd":false},{"ty":"st","c":{"a":0,"k":[0,0,0,1],"ix":3},"o":{"a":0,"k":100,"ix":4},"w":{"a":0,"k":2,"ix":5},"lc":2,"lj":2,"bm":0,"nm":"Stroke 1","mn":"ADBE Vector Graphic - Stroke","hd":false},{"ty":"tr","p":{"a":0,"k":[12,19.5],"ix":2},"a":{"a":0,"k":[0,0],"ix":1},"s":{"a":0,"k":[100,100],"ix":3},"r":{"a":0,"k":0,"ix":6},"o":{"a":0,"k":100,"ix":7},"sk":{"a":0,"k":0,"ix":4},"sa":{"a":0,"k":0,"ix":5},"nm":"Transform"}],"nm":"bottom","np":2,"cix":2,"bm":0,"ix":2,"mn":"ADBE Vector Group","hd":false},{"ty":"gr","it":[{"ind":0,"ty":"sh","ix":1,"ks":{"a":0,"k":{"i":[[0,0],[0,0],[0,0]],"o":[[0,0],[0,0],[0,0]],"v":[[4.062,-2.708],[-1.355,2.708],[-4.061,0.001]],"c":false},"ix":2},"nm":"Path 1","mn":"ADBE Vector Shape - Group","hd":false},{"ty":"tm","s":{"a":1,"k":[{"i":{"x":[0.6],"y":[1]},"o":{"x":[0.6],"y":[0]},"t":70,"s":[100]},{"t":90,"s":[0]}],"ix":1},"e":{"a":0,"k":100,"ix":2},"o":{"a":0,"k":0,"ix":3},"m":1,"ix":2,"nm":"Trim Paths 1","mn":"ADBE Vector Filter - Trim","hd":false},{"ty":"st","c":{"a":0,"k":[1,1,1,1],"ix":3},"o":{"a":0,"k":100,"ix":4},"w":{"a":0,"k":2,"ix":5},"lc":2,"lj":2,"bm":0,"nm":"Stroke 1","mn":"ADBE Vector Graphic - Stroke","hd":false},{"ty":"tr","p":{"a":1,"k":[{"i":{"x":0.6,"y":1},"o":{"x":0.6,"y":0},"t":100,"s":[12.562,12],"to":[0,3.333],"ti":[0,-3.333]},{"t":110,"s":[12.562,32]}],"ix":2},"a":{"a":0,"k":[0,0],"ix":1},"s":{"a":0,"k":[100,100],"ix":3},"r":{"a":0,"k":0,"ix":6},"o":{"a":1,"k":[{"i":{"x":[0.833],"y":[0.833]},"o":{"x":[0.167],"y":[0.167]},"t":70,"s":[0]},{"t":80,"s":[100]}],"ix":7},"sk":{"a":0,"k":0,"ix":4},"sa":{"a":0,"k":0,"ix":5},"nm":"Transform"}],"nm":"checkmark","np":3,"cix":2,"bm":0,"ix":3,"mn":"ADBE Vector Group","hd":false}],"ip":0,"op":120,"st":0,"bm":0},{"ddd":0,"ind":2,"ty":0,"nm":"wave","refId":"comp_0","sr":1,"ks":{"o":{"a":0,"k":100,"ix":11},"r":{"a":0,"k":0,"ix":10},"p":{"a":0,"k":[16,16,0],"ix":2},"a":{"a":0,"k":[140,155,0],"ix":1},"s":{"a":0,"k":[6.25,6.25,100],"ix":6}},"ao":0,"hasMask":true,"masksProperties":[{"inv":false,"mode":"a","pt":{"a":0,"k":{"i":[[0,0],[0,0],[0,0],[0,0]],"o":[[0,0],[0,0],[0,0],[0,0]],"v":[[0,0],[0,310],[280,310],[280,0]],"c":true},"ix":1},"o":{"a":0,"k":100,"ix":3},"x":{"a":0,"k":0,"ix":4},"nm":"Mask 1"}],"w":280,"h":310,"ip":0,"op":120,"st":0,"bm":0},{"ddd":0,"ind":3,"ty":4,"nm":"arrow","sr":1,"ks":{"o":{"a":0,"k":100,"ix":11},"r":{"a":0,"k":0,"ix":10},"p":{"a":0,"k":[16,16,0],"ix":2},"a":{"a":0,"k":[12,12,0],"ix":1},"s":{"a":0,"k":[100,100,100],"ix":6}},"ao":0,"hasMask":true,"masksProperties":[{"inv":false,"mode":"i","pt":{"a":0,"k":{"i":[[0,0],[0,0],[0,0],[0,0]],"o":[[0,0],[0,0],[0,0],[0,0]],"v":[[3.25,0.5],[3.25,21.5],[20.55,21.5],[20.55,0.5]],"c":true},"ix":1},"o":{"a":0,"k":100,"ix":3},"x":{"a":0,"k":0,"ix":4},"nm":"Mask 1"}],"shapes":[{"ty":"gr","it":[{"ty":"gr","it":[{"ind":0,"ty":"sh","ix":1,"ks":{"a":0,"k":{"i":[[0,0],[0,0],[0,0]],"o":[[0,0],[0,0],[0,0]],"v":[[-4,-2],[0,2],[4,-2]],"c":false},"ix":2},"nm":"Path 1","mn":"ADBE Vector Shape - Group","hd":false},{"ty":"st","c":{"a":0,"k":[0,0,0,1],"ix":3},"o":{"a":0,"k":100,"ix":4},"w":{"a":0,"k":2,"ix":5},"lc":2,"lj":2,"bm":0,"nm":"Stroke 1","mn":"ADBE Vector Graphic - Stroke","hd":false},{"ty":"tr","p":{"a":0,"k":[12,14],"ix":2},"a":{"a":0,"k":[0,0],"ix":1},"s":{"a":0,"k":[100,100],"ix":3},"r":{"a":0,"k":0,"ix":6},"o":{"a":0,"k":100,"ix":7},"sk":{"a":0,"k":0,"ix":4},"sa":{"a":0,"k":0,"ix":5},"nm":"Transform"}],"nm":"head","np":2,"cix":2,"bm":0,"ix":1,"mn":"ADBE Vector Group","hd":false},{"ty":"gr","it":[{"ind":0,"ty":"sh","ix":1,"ks":{"a":0,"k":{"i":[[0,0],[0,0]],"o":[[0,0],[0,0]],"v":[[12,2],[12,16]],"c":false},"ix":2},"nm":"Path 1","mn":"ADBE Vector Shape - Group","hd":false},{"ty":"st","c":{"a":0,"k":[0,0,0,1],"ix":3},"o":{"a":0,"k":100,"ix":4},"w":{"a":0,"k":2,"ix":5},"lc":2,"lj":2,"bm":0,"nm":"Stroke 1","mn":"ADBE Vector Graphic - Stroke","hd":false},{"ty":"tr","p":{"a":0,"k":[0,0],"ix":2},"a":{"a":0,"k":[0,0],"ix":1},"s":{"a":0,"k":[100,100],"ix":3},"r":{"a":0,"k":0,"ix":6},"o":{"a":0,"k":100,"ix":7},"sk":{"a":0,"k":0,"ix":4},"sa":{"a":0,"k":0,"ix":5},"nm":"Transform"}],"nm":"leg","np":2,"cix":2,"bm":0,"ix":2,"mn":"ADBE Vector Group","hd":false},{"ty":"tr","p":{"a":1,"k":[{"i":{"x":0.7,"y":1},"o":{"x":0.7,"y":0},"t":0,"s":[12,9],"to":[0,3.333],"ti":[0,-3.333]},{"i":{"x":0.7,"y":0.7},"o":{"x":0.167,"y":0.167},"t":8,"s":[12,29],"to":[0,0],"ti":[0,0]},{"i":{"x":0.7,"y":1},"o":{"x":0.167,"y":0},"t":100,"s":[12,29],"to":[0,-3.333],"ti":[0,3.333]},{"t":110,"s":[12,9]}],"ix":2},"a":{"a":0,"k":[12,9],"ix":1},"s":{"a":0,"k":[100,100],"ix":3},"r":{"a":0,"k":0,"ix":6},"o":{"a":0,"k":100,"ix":7},"sk":{"a":0,"k":0,"ix":4},"sa":{"a":0,"k":0,"ix":5},"nm":"Transform"}],"nm":"arrow","np":2,"cix":2,"bm":0,"ix":1,"mn":"ADBE Vector Group","hd":false}],"ip":0,"op":120,"st":0,"bm":0}],"markers":[]}"""

private const val ARROW_DOWN_JSON = """{"v":"5.6.5","fr":60,"ip":0,"op":180,"w":32,"h":32,"nm":"arrow-down","ddd":0,"assets":[],"layers":[{"ddd":0,"ind":1,"ty":4,"nm":"arrow-down","sr":1,"ks":{"o":{"a":1,"k":[{"i":{"x":[0.667],"y":[1]},"o":{"x":[0.725],"y":[0]},"t":150,"s":[100]},{"t":179,"s":[0]}],"ix":11},"r":{"a":0,"k":0,"ix":10},"p":{"a":0,"k":[16,16,0],"ix":2},"a":{"a":0,"k":[12,12,0],"ix":1},"s":{"a":0,"k":[100,100,100],"ix":6}},"ao":0,"shapes":[{"ty":"gr","it":[{"ind":0,"ty":"sh","ix":1,"ks":{"a":0,"k":{"i":[[0,0],[0,0],[0,0]],"o":[[0,0],[0,0],[0,0]],"v":[[6,-3],[0,3],[-6,-3]],"c":false},"ix":2},"nm":"Path 1","mn":"ADBE Vector Shape - Group","hd":false},{"ty":"tm","s":{"a":1,"k":[{"i":{"x":[0],"y":[1]},"o":{"x":[0.167],"y":[0.167]},"t":60,"s":[50]},{"t":120,"s":[0]}],"ix":1},"e":{"a":1,"k":[{"i":{"x":[0],"y":[1]},"o":{"x":[0.167],"y":[0.167]},"t":60,"s":[50]},{"t":120,"s":[100]}],"ix":2},"o":{"a":0,"k":0,"ix":3},"m":1,"ix":2,"nm":"Trim Paths 1","mn":"ADBE Vector Filter - Trim","hd":false},{"ty":"st","c":{"a":0,"k":[0,0,0,1],"ix":3},"o":{"a":0,"k":100,"ix":4},"w":{"a":0,"k":2,"ix":5},"lc":2,"lj":2,"bm":0,"nm":"Stroke 1","mn":"ADBE Vector Graphic - Stroke","hd":false},{"ty":"tr","p":{"a":0,"k":[12,17],"ix":2},"a":{"a":0,"k":[0,0],"ix":1},"s":{"a":0,"k":[100,100],"ix":3},"r":{"a":0,"k":0,"ix":6},"o":{"a":0,"k":100,"ix":7},"sk":{"a":0,"k":0,"ix":4},"sa":{"a":0,"k":0,"ix":5},"nm":"Transform"}],"nm":"arrow","np":3,"cix":2,"bm":0,"ix":1,"mn":"ADBE Vector Group","hd":false},{"ty":"gr","it":[{"ind":0,"ty":"sh","ix":1,"ks":{"a":0,"k":{"i":[[0,0],[0,0]],"o":[[0,0],[0,0]],"v":[[12,4],[12,20]],"c":false},"ix":2},"nm":"Path 1","mn":"ADBE Vector Shape - Group","hd":false},{"ty":"tm","s":{"a":0,"k":0,"ix":1},"e":{"a":1,"k":[{"i":{"x":[0.833],"y":[0.833]},"o":{"x":[1],"y":[0]},"t":0,"s":[0]},{"t":60,"s":[100]}],"ix":2},"o":{"a":0,"k":0,"ix":3},"m":1,"ix":2,"nm":"Trim Paths 1","mn":"ADBE Vector Filter - Trim","hd":false},{"ty":"st","c":{"a":0,"k":[0,0,0,1],"ix":3},"o":{"a":0,"k":100,"ix":4},"w":{"a":0,"k":2,"ix":5},"lc":2,"lj":2,"bm":0,"nm":"Stroke 1","mn":"ADBE Vector Graphic - Stroke","hd":false},{"ty":"tr","p":{"a":0,"k":[0,0],"ix":2},"a":{"a":0,"k":[0,0],"ix":1},"s":{"a":0,"k":[100,100],"ix":3},"r":{"a":0,"k":0,"ix":6},"o":{"a":1,"k":[{"i":{"x":[0],"y":[1]},"o":{"x":[0.505],"y":[0]},"t":0,"s":[0]},{"t":60,"s":[100]}],"ix":7},"sk":{"a":0,"k":0,"ix":4},"sa":{"a":0,"k":0,"ix":5},"nm":"Transform"}],"nm":"arrow leg","np":3,"cix":2,"bm":0,"ix":2,"mn":"ADBE Vector Group","hd":false}],"ip":0,"op":180,"st":0,"bm":0}],"markers":[]}"""
