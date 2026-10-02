package com.noven.ncrawler.ui.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.DrawStyle
import androidx.compose.ui.graphics.drawscope.DrawTransform
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.floor

// ─────────────────────────────────────────────────────────────────────────────
// LottieLite — a tiny native player for the two animated icons used by the
// Browse top bar (download + arrow-down). It exists so the app does NOT need the
// lottie-compose dependency: no Gradle change, no extra APK weight, and nothing
// extra for a low-end PC to compile.
//
// It supports only what those two files use, and nothing more:
//   • shape layers + precomp layers, rectangular layer masks (first mask only)
//   • groups, bezier paths (static), trim paths, strokes, fills
//   • keyframed opacity / rotation / position / scale / trim, with the file's own
//     bezier easing (spatial tangents are ignored: every move in these files is
//     a straight line)
// Anything else (parenting, mattes, gradients, animated paths, expressions…) is
// ignored, so do not feed it arbitrary Lottie files.
//
// Colours: strokes/fills that are dark in the file are drawn in [ink]; light ones
// (the white checkmark) in [paper], so the icon can be tinted for any theme.
// All state is read in the draw phase by the callers — nothing here recomposes.
// ─────────────────────────────────────────────────────────────────────────────

private val LAYER_BOUNDS = Rect(-10000f, -10000f, 10000f, 10000f)

internal class LottieIcon(json: String) {

    private val root = JSONObject(json)

    /** Native canvas of the file (32 × 32 for both icons). */
    val canvasW: Float = root.getDouble("w").toFloat()
    val canvasH: Float = root.getDouble("h").toFloat()
    val inFrame: Float = root.getDouble("ip").toFloat()
    val outFrame: Float = root.getDouble("op").toFloat()
    private val frameRate: Float = root.getDouble("fr").toFloat()

    /** One full loop, in milliseconds. */
    val durationMs: Int = (((outFrame - inFrame) / frameRate) * 1000f).toInt()

    private val assets: Map<String, List<Layer>> = parseAssets(root.optJSONArray("assets"))
    private val layers: List<Layer> = parseLayers(root.getJSONArray("layers"))
    private val measure = PathMeasure()

    /** Maps a loop position 0..1 to a frame; stops just short of the last frame
     *  (layers are hidden AT their out-point, which would flash an empty icon). */
    fun frameAt(fraction: Float): Float {
        val f = inFrame + fraction.coerceIn(0f, 1f) * (outFrame - inFrame)
        return minOf(f, outFrame - 0.01f)
    }

    fun draw(scope: DrawScope, frame: Float, ink: Color, paper: Color) {
        with(scope) {
            val w = size.width
            val h = size.height
            withTransform({ scale(w / canvasW, h / canvasH, Offset.Zero) }) {
                drawLayers(layers, frame, ink, paper)
            }
        }
    }

    // ── layers (index 0 is the TOP layer, so draw back-to-front) ──────────────
    private fun DrawScope.drawLayers(list: List<Layer>, frame: Float, ink: Color, paper: Color) {
        for (idx in list.indices.reversed()) {
            val layer = list[idx]
            if (frame < layer.inFrame || frame >= layer.outFrame) continue
            val opacity = layer.xf.opacity(frame)
            if (opacity <= 0f) continue

            withTransform({ layer.xf.applyTo(this, frame) }) {
                // A faded layer is composited as ONE unit (saveLayer), like a real
                // Lottie player — per-stroke alpha would double-darken overlaps.
                val faded = opacity < 0.999f
                val canvas = drawContext.canvas
                if (faded) canvas.saveLayer(LAYER_BOUNDS, Paint().apply { alpha = opacity })

                val body: DrawScope.() -> Unit = {
                    if (layer.type == 4) {
                        drawItems(layer.items, null, frame, 1f, ink, paper)
                    } else if (layer.type == 0) {
                        val inner = assets[layer.refId]
                        if (inner != null) drawLayers(inner, frame - layer.start, ink, paper)
                    }
                }
                val mask = layer.mask
                if (mask != null) clipPath(mask) { body() } else body()

                if (faded) canvas.restore()
            }
        }
    }

    // ── shape items ───────────────────────────────────────────────────────────
    // Forward pass (a trim only affects the paths ABOVE it; a stroke/fill paints
    // the paths above it), then paint in reverse (first item = topmost).
    private fun DrawScope.drawItems(
        items: List<Item>, xf: Xf?, frame: Float, alpha: Float, ink: Color, paper: Color
    ) {
        val ops = ArrayList<Op>()
        var refs: List<PathRef> = emptyList()
        for (item in items) {
            when (item) {
                is PathItem -> refs = refs + PathRef(item.path, null)
                is TrimItem -> {
                    val s = item.s.at(frame)[0] / 100f
                    val e = item.e.at(frame)[0] / 100f
                    val off = item.o.at(frame)[0] / 360f
                    refs = refs.map { PathRef(it.path, floatArrayOf(s, e, off)) }
                }
                is PaintItem -> ops.add(PaintOp(item, refs))
                is GroupItem -> ops.add(GroupOp(item))
            }
        }

        val groupAlpha = alpha * (xf?.opacity(frame) ?: 1f)
        val body: DrawScope.() -> Unit = {
            for (idx in ops.indices.reversed()) {
                val op = ops[idx]
                if (op is GroupOp) {
                    drawItems(op.group.items, op.group.xf, frame, groupAlpha, ink, paper)
                } else if (op is PaintOp) {
                    paintOp(op, frame, groupAlpha, ink, paper)
                }
            }
        }
        if (xf != null) withTransform({ xf.applyTo(this, frame) }, body) else body()
    }

    private fun DrawScope.paintOp(op: PaintOp, frame: Float, alpha: Float, ink: Color, paper: Color) {
        val p = op.paint
        val color = if (p.light) paper else ink
        val a = alpha * (p.opacity.at(frame)[0] / 100f)
        if (a <= 0f) return
        val style: DrawStyle =
            if (p.fill) Fill
            else Stroke(width = (p.width?.at(frame)?.get(0) ?: 1f), cap = p.cap, join = p.join)
        for (ref in op.refs) {
            val trim = ref.trim
            if (trim == null) {
                drawPath(ref.path, color, a, style)
            } else {
                for (piece in trimPieces(ref.path, trim[0], trim[1], trim[2])) {
                    drawPath(piece, color, a, style)
                }
            }
        }
    }

    /**
     * Trim Paths. [s0]/[e0] = start/end as fractions, [off] = offset as a fraction of
     * the contour. A window that runs past the end of the contour comes back as TWO
     * separate pieces (appending a second segment into one Path is not reliable); they
     * meet on a straight edge, so round caps hide the seam. Empty windows draw nothing:
     * a zero-length segment with a round cap would otherwise paint a stray dot.
     */
    private fun trimPieces(path: Path, s0: Float, e0: Float, off: Float): List<Path> {
        var s = minOf(s0, e0) + off
        val width = maxOf(s0, e0) + off - s
        if (width >= 0.9999f) return listOf(path)
        if (width <= 0.0001f) return emptyList()
        measure.setPath(path, false)
        val total = measure.length
        if (total <= 0f) return emptyList()
        s -= floor(s)
        val e = s + width
        val out = ArrayList<Path>(2)
        fun seg(a: Float, b: Float) {
            if (b - a <= 0.0001f) return
            val piece = Path()
            if (measure.getSegment(a * total, b * total, piece, true)) out.add(piece)
        }
        if (e <= 1f) {
            seg(s, e)
        } else {
            seg(s, 1f)
            seg(0f, e - 1f)
        }
        return out
    }
}

// ── parsed model ─────────────────────────────────────────────────────────────

private sealed class Item
private class GroupItem(val items: List<Item>, val xf: Xf?) : Item()
private class PathItem(val path: Path) : Item()
private class TrimItem(val s: Prop, val e: Prop, val o: Prop) : Item()
private class PaintItem(
    val fill: Boolean,
    val light: Boolean,
    val opacity: Prop,
    val width: Prop?,
    val cap: StrokeCap,
    val join: StrokeJoin
) : Item()

private class PathRef(val path: Path, val trim: FloatArray?)

private sealed class Op
private class PaintOp(val paint: PaintItem, val refs: List<PathRef>) : Op()
private class GroupOp(val group: GroupItem) : Op()

private class Layer(j: JSONObject) {
    val type: Int = j.getInt("ty")
    val inFrame: Float = j.optDouble("ip", 0.0).toFloat()
    val outFrame: Float = j.optDouble("op", 1.0e9).toFloat()
    val start: Float = j.optDouble("st", 0.0).toFloat()
    val xf: Xf = Xf(j.getJSONObject("ks"))
    val refId: String = j.optString("refId", "")
    val mask: Path? = parseMask(j)
    val items: List<Item> = if (type == 4) parseItems(j.getJSONArray("shapes")) else emptyList()
}

// Position / rotation / scale / anchor / opacity — used for layers AND shape groups.
private class Xf(j: JSONObject) {
    private val o = Prop(j.optJSONObject("o"), 100f)
    private val r = Prop(j.optJSONObject("r"), 0f)
    private val p = Prop(j.optJSONObject("p"), 0f)
    private val a = Prop(j.optJSONObject("a"), 0f)
    private val s = Prop(j.optJSONObject("s"), 100f)

    fun opacity(frame: Float): Float = (o.at(frame)[0] / 100f).coerceIn(0f, 1f)

    // translate(position) · rotate · scale · translate(-anchor). Pivots are
    // explicit: DrawTransform defaults them to the centre of the canvas.
    fun applyTo(t: DrawTransform, frame: Float) {
        val pos = p.at(frame)
        val anc = a.at(frame)
        val sc = s.at(frame)
        val rot = r.at(frame)[0]
        t.translate(pos[0], pos.getOrElse(1) { 0f })
        if (rot != 0f) t.rotate(rot, Offset.Zero)
        t.scale(sc[0] / 100f, sc.getOrElse(1) { sc[0] } / 100f, Offset.Zero)
        t.translate(-anc[0], -anc.getOrElse(1) { 0f })
    }
}

private class Kf(j: JSONObject) {
    val t: Float = j.getDouble("t").toFloat()
    val s: FloatArray = j.optJSONArray("s")?.floats() ?: floatArrayOf(0f)
    val hold: Boolean = j.optInt("h", 0) == 1
    /** [outX, outY, inX, inY] of the segment that STARTS at this keyframe, or null = linear. */
    val ease: FloatArray? = run {
        val o = j.optJSONObject("o")
        val i = j.optJSONObject("i")
        if (o == null || i == null) null
        else floatArrayOf(easeNum(o, "x"), easeNum(o, "y"), easeNum(i, "x"), easeNum(i, "y"))
    }
}

private class Prop(j: JSONObject?, default: Float) {
    private val fixed: FloatArray?
    private val kfs: List<Kf>?

    init {
        if (j == null) {
            fixed = floatArrayOf(default)
            kfs = null
        } else if (j.optInt("a", 0) == 1) {
            val arr = j.getJSONArray("k")
            fixed = null
            kfs = (0 until arr.length()).map { Kf(arr.getJSONObject(it)) }
        } else {
            val k = j.opt("k")
            fixed = if (k is JSONArray) k.floats()
                    else floatArrayOf(((k as? Number)?.toFloat()) ?: default)
            kfs = null
        }
    }

    fun at(frame: Float): FloatArray {
        val list = kfs ?: return fixed ?: floatArrayOf(0f)
        val first = list.first()
        val last = list.last()
        if (frame <= first.t) return first.s
        if (frame >= last.t) return last.s
        var n = 0
        while (!(list[n].t <= frame && frame < list[n + 1].t)) n++
        val a = list[n]
        val b = list[n + 1]
        if (a.hold) return a.s
        val p = (frame - a.t) / (b.t - a.t)
        val ease = a.ease
        val e = if (ease != null) bezier(ease[0], ease[1], ease[2], ease[3], p) else p
        val m = minOf(a.s.size, b.s.size)
        return FloatArray(m) { a.s[it] + (b.s[it] - a.s[it]) * e }
    }
}

// Cubic-bezier easing through (0,0) (ox,oy) (ix,iy) (1,1): solve x(u) = p, return y(u).
private fun bezier(ox: Float, oy: Float, ix: Float, iy: Float, p: Float): Float {
    if (p <= 0f) return 0f
    if (p >= 1f) return 1f
    var lo = 0f
    var hi = 1f
    repeat(30) {
        val u = (lo + hi) / 2f
        val v = 1f - u
        val x = 3f * v * v * u * ox + 3f * v * u * u * ix + u * u * u
        if (x < p) lo = u else hi = u
    }
    val u = (lo + hi) / 2f
    val v = 1f - u
    return 3f * v * v * u * oy + 3f * v * u * u * iy + u * u * u
}

private fun easeNum(o: JSONObject, key: String): Float {
    val v = o.opt(key)
    return when (v) {
        is JSONArray -> v.getDouble(0).toFloat()
        is Number -> v.toFloat()
        else -> 0f
    }
}

private fun JSONArray.floats(): FloatArray = FloatArray(length()) { getDouble(it).toFloat() }

// ── parsing ──────────────────────────────────────────────────────────────────

private fun parseAssets(arr: JSONArray?): Map<String, List<Layer>> {
    val out = HashMap<String, List<Layer>>()
    if (arr == null) return out
    for (i in 0 until arr.length()) {
        val a = arr.getJSONObject(i)
        val inner = a.optJSONArray("layers") ?: continue
        out[a.getString("id")] = parseLayers(inner)
    }
    return out
}

private fun parseLayers(arr: JSONArray): List<Layer> =
    (0 until arr.length()).map { Layer(arr.getJSONObject(it)) }

private fun parseItems(arr: JSONArray): List<Item> {
    val out = ArrayList<Item>()
    for (i in 0 until arr.length()) {
        val j = arr.getJSONObject(i)
        when (j.optString("ty")) {
            "gr" -> {
                val inner = j.getJSONArray("it")
                var xf: Xf? = null
                for (k in 0 until inner.length()) {
                    val c = inner.getJSONObject(k)
                    if (c.optString("ty") == "tr") xf = Xf(c)
                }
                out.add(GroupItem(parseItems(inner), xf))
            }
            "sh" -> {
                val ks = j.getJSONObject("ks")
                if (ks.optInt("a", 0) == 0) out.add(PathItem(buildPath(ks.getJSONObject("k"))))
            }
            "tm" -> out.add(
                TrimItem(
                    Prop(j.optJSONObject("s"), 0f),
                    Prop(j.optJSONObject("e"), 100f),
                    Prop(j.optJSONObject("o"), 0f)
                )
            )
            "st" -> out.add(
                PaintItem(
                    fill    = false,
                    light   = isLight(j),
                    opacity = Prop(j.optJSONObject("o"), 100f),
                    width   = Prop(j.optJSONObject("w"), 1f),
                    cap     = capOf(j.optInt("lc", 2)),
                    join    = joinOf(j.optInt("lj", 2))
                )
            )
            "fl" -> out.add(
                PaintItem(
                    fill    = true,
                    light   = isLight(j),
                    opacity = Prop(j.optJSONObject("o"), 100f),
                    width   = null,
                    cap     = StrokeCap.Butt,
                    join    = StrokeJoin.Miter
                )
            )
        }
    }
    return out
}

private fun capOf(v: Int): StrokeCap = when (v) {
    2 -> StrokeCap.Round
    3 -> StrokeCap.Square
    else -> StrokeCap.Butt
}

private fun joinOf(v: Int): StrokeJoin = when (v) {
    2 -> StrokeJoin.Round
    3 -> StrokeJoin.Bevel
    else -> StrokeJoin.Miter
}

private fun isLight(j: JSONObject): Boolean {
    val c = j.optJSONObject("c")?.opt("k") as? JSONArray ?: return false
    if (c.length() < 3) return false
    return 0.299 * c.getDouble(0) + 0.587 * c.getDouble(1) + 0.114 * c.getDouble(2) > 0.5
}

private fun parseMask(j: JSONObject): Path? {
    val masks = j.optJSONArray("masksProperties") ?: return null
    if (masks.length() == 0) return null
    val pt = masks.getJSONObject(0).optJSONObject("pt") ?: return null
    if (pt.optInt("a", 0) != 0) return null
    return buildPath(pt.getJSONObject("k"))
}

// Lottie bezier path: vertices v, in-tangents i, out-tangents o (tangents are
// relative to their vertex).
private fun buildPath(k: JSONObject): Path {
    val v = k.getJSONArray("v")
    val ti = k.getJSONArray("i")
    val to = k.getJSONArray("o")
    val closed = k.optBoolean("c", false)
    val n = v.length()
    val path = Path()
    if (n == 0) return path
    fun px(a: JSONArray, idx: Int): Float = a.getJSONArray(idx).getDouble(0).toFloat()
    fun py(a: JSONArray, idx: Int): Float = a.getJSONArray(idx).getDouble(1).toFloat()
    path.moveTo(px(v, 0), py(v, 0))
    val segs = if (closed) n else n - 1
    for (j in 0 until segs) {
        val m = (j + 1) % n
        path.cubicTo(
            px(v, j) + px(to, j), py(v, j) + py(to, j),
            px(v, m) + px(ti, m), py(v, m) + py(ti, m),
            px(v, m), py(v, m)
        )
    }
    if (closed) path.close()
    return path
}
