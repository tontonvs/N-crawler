package com.noven.ncrawler.ui.holiday

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke

// The five shape helpers the generated artwork (CritterArtGen.kt) is built from.
// Everything is in a 100×100 grid; [u] converts grid units to pixels.
// Each critter is drawn TWICE: first with rim != null (only a thin outline, so the
// animal reads on light AND dark pages), then with rim == null (the colours on top).
private const val RIM_W = 3.6f

internal fun DrawScope.pOval(u: Float, rim: Color?, cx: Float, cy: Float, rx: Float, ry: Float, c: Color) {
    val topLeft = Offset((cx - rx) * u, (cy - ry) * u)
    val size = Size(rx * 2f * u, ry * 2f * u)
    if (rim != null) drawOval(rim, topLeft, size, style = Stroke(RIM_W * u)) else drawOval(c, topLeft, size)
}

internal fun DrawScope.pCircle(u: Float, rim: Color?, cx: Float, cy: Float, r: Float, c: Color) {
    val center = Offset(cx * u, cy * u)
    if (rim != null) drawCircle(rim, r * u, center, style = Stroke(RIM_W * u)) else drawCircle(c, r * u, center)
}

internal fun DrawScope.pRect(u: Float, rim: Color?, x: Float, y: Float, w: Float, h: Float, r: Float, c: Color) {
    val topLeft = Offset(x * u, y * u)
    val size = Size(w * u, h * u)
    val corner = CornerRadius(r * u, r * u)
    if (rim != null) drawRoundRect(rim, topLeft, size, corner, style = Stroke(RIM_W * u))
    else drawRoundRect(c, topLeft, size, corner)
}

internal fun DrawScope.pLine(u: Float, rim: Color?, x1: Float, y1: Float, x2: Float, y2: Float, w: Float, c: Color) {
    val a = Offset(x1 * u, y1 * u)
    val b = Offset(x2 * u, y2 * u)
    if (rim != null) drawLine(rim, a, b, strokeWidth = (w + RIM_W) * u, cap = StrokeCap.Round)
    else drawLine(c, a, b, strokeWidth = w * u, cap = StrokeCap.Round)
}

internal fun DrawScope.pPoly(u: Float, rim: Color?, pts: FloatArray, c: Color) {
    val path = Path()
    path.moveTo(pts[0] * u, pts[1] * u)
    var i = 2
    while (i < pts.size) {
        path.lineTo(pts[i] * u, pts[i + 1] * u)
        i += 2
    }
    path.close()
    if (rim != null) drawPath(path, rim, style = Stroke(RIM_W * u, join = StrokeJoin.Round))
    else drawPath(path, c)
}
