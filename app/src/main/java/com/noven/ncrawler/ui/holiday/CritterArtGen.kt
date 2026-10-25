package com.noven.ncrawler.ui.holiday

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import kotlin.math.sin

// ═════════════════════════════════════════════════════════════════════════════
// GENERATED critter artwork — 14 little animals + 4 hats drawn from simple shapes on a
// 100×100 grid (y down, facing right). u = pixels per grid unit, ph = gait phase
// in radians. rim != null draws the outline pass, rim == null the fill pass
// (CritterPen.kt). To tweak a colour, edit the Color(...) constants below.
// ═════════════════════════════════════════════════════════════════════════════

private val PENGUIN_SANTA_BODY = Color(0xFF1F2937)
private val PENGUIN_SANTA_BELLY = Color(0xFFF8FAFC)
private val PENGUIN_SANTA_BEAK = Color(0xFFF59E0B)
private val PENGUIN_SANTA_FOOT = Color(0xFFF59E0B)
private val PENGUIN_SANTA_WHITE = Color(0xFFF8FAFC)
private val PENGUIN_SANTA_EYE = Color(0xFF111827)
private val PENGUIN_SANTA_HAT = Color(0xFFDC2626)
private val PENGUIN_SANTA_CHEEK = Color(0xFFF9A8D4)
private val PENGUIN_SANTA_P1 = floatArrayOf(62f, 45f, 80f, 50f, 62f, 56f)
private val PENGUIN_SANTA_P2 = floatArrayOf(34f, 28f, 72f, 28f, 64f, 6f)
internal fun DrawScope.drawPenguinSanta(u: Float, rim: Color?, ph: Float) {
    rotate(degrees = sin(ph * 1f + 0f) * 16f, pivot = Offset(46f * u, 80f * u)) {
        pOval(u, rim, 40f, 94f, 10f, 4.5f, PENGUIN_SANTA_FOOT)
    }
    rotate(degrees = sin(ph * 1f + 3.142f) * 16f, pivot = Offset(60f * u, 80f * u)) {
        pOval(u, rim, 62f, 94f, 10f, 4.5f, PENGUIN_SANTA_FOOT)
    }
    rotate(degrees = sin(ph * 1f + 3.142f) * 16f, pivot = Offset(30f * u, 44f * u)) {
        pOval(u, rim, 26f, 60f, 7f, 17f, PENGUIN_SANTA_BODY)
    }
    pOval(u, rim, 50f, 58f, 27f, 34f, PENGUIN_SANTA_BODY)
    pOval(u, rim, 53f, 64f, 18f, 25f, PENGUIN_SANTA_BELLY)
    rotate(degrees = sin(ph * 1f + 0f) * 16f, pivot = Offset(72f * u, 44f * u)) {
        pOval(u, rim, 75f, 60f, 7f, 17f, PENGUIN_SANTA_BODY)
    }
    pCircle(u, rim, 54f, 38f, 6f, PENGUIN_SANTA_WHITE)
    pCircle(u, rim, 67f, 38f, 6f, PENGUIN_SANTA_WHITE)
    pCircle(u, rim, 56f, 38.5f, 3f, PENGUIN_SANTA_EYE)
    pCircle(u, rim, 69f, 38.5f, 3f, PENGUIN_SANTA_EYE)
    pPoly(u, rim, PENGUIN_SANTA_P1, PENGUIN_SANTA_BEAK)
    pCircle(u, rim, 48f, 50f, 4f, PENGUIN_SANTA_CHEEK)
    pPoly(u, rim, PENGUIN_SANTA_P2, PENGUIN_SANTA_HAT)
    pRect(u, rim, 31f, 24f, 44f, 9f, 4.5f, PENGUIN_SANTA_WHITE)
    pCircle(u, rim, 66f, 6f, 6.5f, PENGUIN_SANTA_WHITE)
}

private val ROBIN_BODY = Color(0xFF8B5E3C)
private val ROBIN_WING = Color(0xFF6B4226)
private val ROBIN_BREAST = Color(0xFFE2552B)
private val ROBIN_BEAK = Color(0xFFF59E0B)
private val ROBIN_EYE = Color(0xFF111827)
private val ROBIN_GLINT = Color(0xFFFFFFFF)
private val ROBIN_HAT = Color(0xFFDC2626)
private val ROBIN_WHITE = Color(0xFFF8FAFC)
private val ROBIN_P3 = floatArrayOf(30f, 54f, 4f, 42f, 6f, 68f)
private val ROBIN_P4 = floatArrayOf(80f, 38f, 94f, 43f, 80f, 48f)
private val ROBIN_P5 = floatArrayOf(56f, 30f, 78f, 28f, 72f, 10f)
internal fun DrawScope.drawRobin(u: Float, rim: Color?, ph: Float) {
    pPoly(u, rim, ROBIN_P3, ROBIN_WING)
    pOval(u, rim, 48f, 56f, 26f, 20f, ROBIN_BODY)
    pOval(u, rim, 60f, 62f, 16f, 14f, ROBIN_BREAST)
    pCircle(u, rim, 68f, 42f, 15f, ROBIN_BODY)
    pCircle(u, rim, 72f, 38f, 3.4f, ROBIN_EYE)
    pCircle(u, rim, 73f, 37f, 1.1f, ROBIN_GLINT)
    pPoly(u, rim, ROBIN_P4, ROBIN_BEAK)
    rotate(degrees = sin(ph * 2.2f + 0f) * 34f, pivot = Offset(54f * u, 50f * u)) {
        pOval(u, rim, 44f, 52f, 19f, 10f, ROBIN_WING)
    }
    pPoly(u, rim, ROBIN_P5, ROBIN_HAT)
    pCircle(u, rim, 72f, 10f, 4.2f, ROBIN_WHITE)
}

private val SNOWMAN_PEEK_SNOW = Color(0xFFF8FAFC)
private val SNOWMAN_PEEK_HAT = Color(0xFF1F2937)
private val SNOWMAN_PEEK_BAND = Color(0xFFDC2626)
private val SNOWMAN_PEEK_COAL = Color(0xFF111827)
private val SNOWMAN_PEEK_NOSE = Color(0xFFF97316)
private val SNOWMAN_PEEK_SCARF = Color(0xFFDC2626)
private val SNOWMAN_PEEK_ARM = Color(0xFF7C4A21)
private val SNOWMAN_PEEK_P6 = floatArrayOf(50f, 65f, 70f, 68f, 50f, 72f)
internal fun DrawScope.drawSnowmanPeek(u: Float, rim: Color?, ph: Float) {
    rotate(degrees = sin(ph * 1.6f + 0f) * 20f, pivot = Offset(80f * u, 70f * u)) {
        pLine(u, rim, 80f, 70f, 97f, 52f, 3.2f, SNOWMAN_PEEK_ARM)
    }
    pCircle(u, rim, 50f, 66f, 29f, SNOWMAN_PEEK_SNOW)
    pRect(u, rim, 22f, 34f, 56f, 7f, 3f, SNOWMAN_PEEK_HAT)
    pRect(u, rim, 32f, 10f, 36f, 28f, 3f, SNOWMAN_PEEK_HAT)
    pRect(u, rim, 32f, 26f, 36f, 7f, 0f, SNOWMAN_PEEK_BAND)
    pCircle(u, rim, 40f, 60f, 3.4f, SNOWMAN_PEEK_COAL)
    pCircle(u, rim, 60f, 60f, 3.4f, SNOWMAN_PEEK_COAL)
    pPoly(u, rim, SNOWMAN_PEEK_P6, SNOWMAN_PEEK_NOSE)
    pCircle(u, rim, 37f, 74f, 2f, SNOWMAN_PEEK_COAL)
    pCircle(u, rim, 44f, 79f, 2f, SNOWMAN_PEEK_COAL)
    pCircle(u, rim, 54f, 79f, 2f, SNOWMAN_PEEK_COAL)
    pCircle(u, rim, 62f, 74f, 2f, SNOWMAN_PEEK_COAL)
    pRect(u, rim, 22f, 86f, 56f, 12f, 6f, SNOWMAN_PEEK_SCARF)
}

private val BUNNY_FUR = Color(0xFFF5F5F4)
private val BUNNY_INNER = Color(0xFFF9A8D4)
private val BUNNY_EYE = Color(0xFF1F2937)
private val BUNNY_NOSE = Color(0xFFF472B6)
private val BUNNY_TAIL = Color(0xFFFFFFFF)
internal fun DrawScope.drawBunny(u: Float, rim: Color?, ph: Float) {
    pOval(u, rim, 36f, 90f, 13f, 5f, BUNNY_FUR)
    pCircle(u, rim, 17f, 68f, 7.5f, BUNNY_TAIL)
    pOval(u, rim, 46f, 67f, 27f, 20f, BUNNY_FUR)
    pOval(u, rim, 33f, 72f, 14f, 14f, BUNNY_FUR)
    rotate(degrees = sin(ph * 1f + 3.142f) * 16f, pivot = Offset(62f * u, 76f * u)) {
        pOval(u, rim, 63f, 86f, 9f, 4.5f, BUNNY_FUR)
    }
    rotate(degrees = sin(ph * 1f + 0f) * 10f, pivot = Offset(61f * u, 38f * u)) {
        pOval(u, rim, 60f, 21f, 6f, 18f, BUNNY_FUR)
    }
    rotate(degrees = sin(ph * 1f + 0f) * 10f, pivot = Offset(61f * u, 38f * u)) {
        pOval(u, rim, 60f, 22f, 3f, 12f, BUNNY_INNER)
    }
    pCircle(u, rim, 69f, 49f, 16f, BUNNY_FUR)
    rotate(degrees = sin(ph * 1f + 1.2f) * 10f, pivot = Offset(74f * u, 38f * u)) {
        pOval(u, rim, 75f, 23f, 6f, 17f, BUNNY_FUR)
    }
    rotate(degrees = sin(ph * 1f + 1.2f) * 10f, pivot = Offset(74f * u, 38f * u)) {
        pOval(u, rim, 75f, 24f, 3f, 11f, BUNNY_INNER)
    }
    pCircle(u, rim, 75f, 46f, 2.8f, BUNNY_EYE)
    pCircle(u, rim, 84f, 53f, 3f, BUNNY_NOSE)
    pCircle(u, rim, 70f, 56f, 3.6f, BUNNY_INNER)
}

private val CHICK_BODY = Color(0xFFFDE047)
private val CHICK_WING = Color(0xFFFACC15)
private val CHICK_EYE = Color(0xFF111827)
private val CHICK_BEAK = Color(0xFFF97316)
private val CHICK_LEG = Color(0xFFF97316)
private val CHICK_SHELL = Color(0xFFF8FAFC)
private val CHICK_P7 = floatArrayOf(75f, 52f, 90f, 57f, 75f, 62f)
private val CHICK_P8 = floatArrayOf(32f, 34f, 38f, 20f, 45f, 31f, 51f, 18f, 57f, 31f, 64f, 20f, 69f, 34f)
internal fun DrawScope.drawChick(u: Float, rim: Color?, ph: Float) {
    rotate(degrees = sin(ph * 1f + 0f) * 20f, pivot = Offset(44f * u, 84f * u)) {
        pLine(u, rim, 44f, 84f, 42f, 96f, 3.4f, CHICK_LEG)
    }
    rotate(degrees = sin(ph * 1f + 3.142f) * 20f, pivot = Offset(58f * u, 84f * u)) {
        pLine(u, rim, 58f, 84f, 56f, 96f, 3.4f, CHICK_LEG)
    }
    rotate(degrees = sin(ph * 1f + 0f) * 20f, pivot = Offset(44f * u, 84f * u)) {
        pOval(u, rim, 42f, 96f, 6f, 2.6f, CHICK_LEG)
    }
    rotate(degrees = sin(ph * 1f + 3.142f) * 20f, pivot = Offset(58f * u, 84f * u)) {
        pOval(u, rim, 56f, 96f, 6f, 2.6f, CHICK_LEG)
    }
    pCircle(u, rim, 50f, 58f, 30f, CHICK_BODY)
    rotate(degrees = sin(ph * 1f + 0f) * 12f, pivot = Offset(42f * u, 52f * u)) {
        pOval(u, rim, 40f, 63f, 11f, 14f, CHICK_WING)
    }
    pCircle(u, rim, 63f, 48f, 3.8f, CHICK_EYE)
    pPoly(u, rim, CHICK_P7, CHICK_BEAK)
    pPoly(u, rim, CHICK_P8, CHICK_SHELL)
}

private val BUTTERFLY_BODY = Color(0xFF4B5563)
private val BUTTERFLY_UP = Color(0xFFF9A8D4)
private val BUTTERFLY_LOW = Color(0xFFC4B5FD)
private val BUTTERFLY_DOT = Color(0xFFFFFFFF)
internal fun DrawScope.drawButterfly(u: Float, rim: Color?, ph: Float) {
    scale(scaleX = 1f - 0.65f * (0.5f + 0.5f * sin(ph * 2f + 0f)), scaleY = 1f, pivot = Offset(50f * u, 50f * u)) {
        pOval(u, rim, 35f, 38f, 18f, 22f, BUTTERFLY_UP)
    }
    scale(scaleX = 1f - 0.65f * (0.5f + 0.5f * sin(ph * 2f + 0f)), scaleY = 1f, pivot = Offset(50f * u, 50f * u)) {
        pOval(u, rim, 65f, 38f, 18f, 22f, BUTTERFLY_UP)
    }
    scale(scaleX = 1f - 0.65f * (0.5f + 0.5f * sin(ph * 2f + 0f)), scaleY = 1f, pivot = Offset(50f * u, 50f * u)) {
        pOval(u, rim, 38f, 64f, 13f, 16f, BUTTERFLY_LOW)
    }
    scale(scaleX = 1f - 0.65f * (0.5f + 0.5f * sin(ph * 2f + 0f)), scaleY = 1f, pivot = Offset(50f * u, 50f * u)) {
        pOval(u, rim, 62f, 64f, 13f, 16f, BUTTERFLY_LOW)
    }
    scale(scaleX = 1f - 0.65f * (0.5f + 0.5f * sin(ph * 2f + 0f)), scaleY = 1f, pivot = Offset(50f * u, 50f * u)) {
        pCircle(u, rim, 32f, 36f, 4f, BUTTERFLY_DOT)
    }
    scale(scaleX = 1f - 0.65f * (0.5f + 0.5f * sin(ph * 2f + 0f)), scaleY = 1f, pivot = Offset(50f * u, 50f * u)) {
        pCircle(u, rim, 68f, 36f, 4f, BUTTERFLY_DOT)
    }
    pLine(u, rim, 49f, 33f, 41f, 19f, 1.8f, BUTTERFLY_BODY)
    pLine(u, rim, 51f, 33f, 59f, 19f, 1.8f, BUTTERFLY_BODY)
    pOval(u, rim, 50f, 52f, 3.4f, 18f, BUTTERFLY_BODY)
    pCircle(u, rim, 50f, 33f, 4.6f, BUTTERFLY_BODY)
}

private val BAT_BODY = Color(0xFF3B2F5C)
private val BAT_WING = Color(0xFF2E2447)
private val BAT_EYE = Color(0xFFFDE047)
private val BAT_FANG = Color(0xFFFFFFFF)
private val BAT_P9 = floatArrayOf(43f, 52f, 20f, 34f, 6f, 44f, 15f, 48f, 9f, 60f, 22f, 55f, 29f, 64f, 40f, 60f)
private val BAT_P10 = floatArrayOf(57f, 52f, 80f, 34f, 94f, 44f, 85f, 48f, 91f, 60f, 78f, 55f, 71f, 64f, 60f, 60f)
private val BAT_P11 = floatArrayOf(42f, 36f, 43f, 20f, 51f, 32f)
private val BAT_P12 = floatArrayOf(58f, 36f, 57f, 20f, 49f, 32f)
private val BAT_P13 = floatArrayOf(46f, 47f, 49f, 47f, 47.5f, 52f)
private val BAT_P14 = floatArrayOf(51f, 47f, 54f, 47f, 52.5f, 52f)
internal fun DrawScope.drawBat(u: Float, rim: Color?, ph: Float) {
    rotate(degrees = sin(ph * 2.4f + 0f) * 26f, pivot = Offset(44f * u, 52f * u)) {
        pPoly(u, rim, BAT_P9, BAT_WING)
    }
    rotate(degrees = sin(ph * 2.4f + 0f) * -26f, pivot = Offset(56f * u, 52f * u)) {
        pPoly(u, rim, BAT_P10, BAT_WING)
    }
    pPoly(u, rim, BAT_P11, BAT_BODY)
    pPoly(u, rim, BAT_P12, BAT_BODY)
    pOval(u, rim, 50f, 57f, 10f, 14f, BAT_BODY)
    pCircle(u, rim, 50f, 41f, 10f, BAT_BODY)
    pCircle(u, rim, 46f, 40f, 2.4f, BAT_EYE)
    pCircle(u, rim, 54f, 40f, 2.4f, BAT_EYE)
    pPoly(u, rim, BAT_P13, BAT_FANG)
    pPoly(u, rim, BAT_P14, BAT_FANG)
}

private val BLACK_CAT_FUR = Color(0xFF241E36)
private val BLACK_CAT_EYE = Color(0xFFA3E635)
private val BLACK_CAT_PUPIL = Color(0xFF0B0814)
private val BLACK_CAT_NOSE = Color(0xFFF9A8D4)
private val BLACK_CAT_WHISK = Color(0xFFE5E7EB)
private val BLACK_CAT_BOW = Color(0xFFF97316)
private val BLACK_CAT_P15 = floatArrayOf(60f, 38f, 62f, 17f, 73f, 33f)
private val BLACK_CAT_P16 = floatArrayOf(74f, 33f, 85f, 17f, 87f, 40f)
private val BLACK_CAT_P17 = floatArrayOf(70f, 53f, 76f, 53f, 73f, 57f)
internal fun DrawScope.drawBlackCat(u: Float, rim: Color?, ph: Float) {
    rotate(degrees = sin(ph * 1.4f + 0f) * 14f, pivot = Offset(22f * u, 64f * u)) {
        pLine(u, rim, 20f, 62f, 8f, 48f, 6.5f, BLACK_CAT_FUR)
    }
    rotate(degrees = sin(ph * 1.4f + 0f) * 14f, pivot = Offset(22f * u, 64f * u)) {
        pLine(u, rim, 8f, 48f, 13f, 32f, 6.5f, BLACK_CAT_FUR)
    }
    rotate(degrees = sin(ph * 1f + 0f) * 22f, pivot = Offset(30f * u, 76f * u)) {
        pRect(u, rim, 26f, 76f, 9f, 19f, 4.5f, BLACK_CAT_FUR)
    }
    rotate(degrees = sin(ph * 1f + 3.142f) * 22f, pivot = Offset(59f * u, 76f * u)) {
        pRect(u, rim, 55f, 76f, 9f, 19f, 4.5f, BLACK_CAT_FUR)
    }
    pOval(u, rim, 44f, 66f, 28f, 18f, BLACK_CAT_FUR)
    rotate(degrees = sin(ph * 1f + 3.142f) * 22f, pivot = Offset(39f * u, 78f * u)) {
        pRect(u, rim, 35f, 78f, 9f, 17f, 4.5f, BLACK_CAT_FUR)
    }
    rotate(degrees = sin(ph * 1f + 0f) * 22f, pivot = Offset(68f * u, 78f * u)) {
        pRect(u, rim, 64f, 78f, 9f, 17f, 4.5f, BLACK_CAT_FUR)
    }
    pCircle(u, rim, 72f, 47f, 15f, BLACK_CAT_FUR)
    pPoly(u, rim, BLACK_CAT_P15, BLACK_CAT_FUR)
    pPoly(u, rim, BLACK_CAT_P16, BLACK_CAT_FUR)
    pOval(u, rim, 66f, 46f, 3.4f, 4.4f, BLACK_CAT_EYE)
    pOval(u, rim, 79f, 46f, 3.4f, 4.4f, BLACK_CAT_EYE)
    pOval(u, rim, 66.6f, 46f, 1.1f, 3.8f, BLACK_CAT_PUPIL)
    pOval(u, rim, 79.6f, 46f, 1.1f, 3.8f, BLACK_CAT_PUPIL)
    pPoly(u, rim, BLACK_CAT_P17, BLACK_CAT_NOSE)
    pLine(u, rim, 80f, 54f, 96f, 50f, 1.3f, BLACK_CAT_WHISK)
    pLine(u, rim, 80f, 56f, 96f, 57f, 1.3f, BLACK_CAT_WHISK)
}

private val GHOST_BODY = Color(0xFFF8FAFC)
private val GHOST_EYE = Color(0xFF1F2937)
private val GHOST_CHEEK = Color(0xFFFBCFE8)
internal fun DrawScope.drawGhost(u: Float, rim: Color?, ph: Float) {
    rotate(degrees = sin(ph * 1.5f + 0f) * 16f, pivot = Offset(26f * u, 52f * u)) {
        pOval(u, rim, 21f, 60f, 7f, 12f, GHOST_BODY)
    }
    rotate(degrees = sin(ph * 1.5f + 0f) * -16f, pivot = Offset(74f * u, 52f * u)) {
        pOval(u, rim, 79f, 60f, 7f, 12f, GHOST_BODY)
    }
    pCircle(u, rim, 50f, 44f, 27f, GHOST_BODY)
    pRect(u, rim, 23f, 44f, 54f, 40f, 0f, GHOST_BODY)
    pCircle(u, rim, 31f, 84f, 8.5f, GHOST_BODY)
    pCircle(u, rim, 50f, 87f, 8.5f, GHOST_BODY)
    pCircle(u, rim, 69f, 84f, 8.5f, GHOST_BODY)
    pOval(u, rim, 40f, 46f, 4.2f, 6f, GHOST_EYE)
    pOval(u, rim, 60f, 46f, 4.2f, 6f, GHOST_EYE)
    pOval(u, rim, 50f, 61f, 5f, 7f, GHOST_EYE)
    pCircle(u, rim, 33f, 57f, 4.2f, GHOST_CHEEK)
    pCircle(u, rim, 67f, 57f, 4.2f, GHOST_CHEEK)
}

private val PUMPKIN_PEEK_SIDE = Color(0xFFEA580C)
private val PUMPKIN_PEEK_MID = Color(0xFFF97316)
private val PUMPKIN_PEEK_STEM = Color(0xFF65A30D)
private val PUMPKIN_PEEK_GLOW = Color(0xFFFDE047)
private val PUMPKIN_PEEK_P18 = floatArrayOf(54f, 34f, 70f, 22f, 66f, 36f)
private val PUMPKIN_PEEK_P19 = floatArrayOf(31f, 58f, 47f, 58f, 39f, 42f)
private val PUMPKIN_PEEK_P20 = floatArrayOf(53f, 58f, 69f, 58f, 61f, 42f)
private val PUMPKIN_PEEK_P21 = floatArrayOf(46f, 67f, 54f, 67f, 50f, 60f)
private val PUMPKIN_PEEK_P22 = floatArrayOf(29f, 72f, 40f, 68f, 46f, 77f, 54f, 68f, 60f, 77f, 71f, 72f, 66f, 88f, 50f, 83f, 34f, 88f)
internal fun DrawScope.drawPumpkinPeek(u: Float, rim: Color?, ph: Float) {
    pOval(u, rim, 32f, 66f, 26f, 28f, PUMPKIN_PEEK_SIDE)
    pOval(u, rim, 68f, 66f, 26f, 28f, PUMPKIN_PEEK_SIDE)
    pOval(u, rim, 50f, 66f, 22f, 30f, PUMPKIN_PEEK_MID)
    pRect(u, rim, 46f, 26f, 8f, 14f, 3f, PUMPKIN_PEEK_STEM)
    pPoly(u, rim, PUMPKIN_PEEK_P18, PUMPKIN_PEEK_STEM)
    pPoly(u, rim, PUMPKIN_PEEK_P19, PUMPKIN_PEEK_GLOW)
    pPoly(u, rim, PUMPKIN_PEEK_P20, PUMPKIN_PEEK_GLOW)
    pPoly(u, rim, PUMPKIN_PEEK_P21, PUMPKIN_PEEK_GLOW)
    pPoly(u, rim, PUMPKIN_PEEK_P22, PUMPKIN_PEEK_GLOW)
}

private val BIRD_PARTY_BODY = Color(0xFF38BDF8)
private val BIRD_PARTY_BELLY = Color(0xFFE0F2FE)
private val BIRD_PARTY_TAIL = Color(0xFF0EA5E9)
private val BIRD_PARTY_WING = Color(0xFF0284C7)
private val BIRD_PARTY_BEAK = Color(0xFFF59E0B)
private val BIRD_PARTY_EYE = Color(0xFF111827)
private val BIRD_PARTY_GLINT = Color(0xFFFFFFFF)
private val BIRD_PARTY_HAT = Color(0xFFE879F9)
private val BIRD_PARTY_STRIPE = Color(0xFFFACC15)
private val BIRD_PARTY_POM = Color(0xFFFACC15)
private val BIRD_PARTY_P23 = floatArrayOf(30f, 54f, 4f, 42f, 6f, 68f)
private val BIRD_PARTY_P24 = floatArrayOf(80f, 42f, 94f, 47f, 80f, 52f)
private val BIRD_PARTY_P25 = floatArrayOf(60f, 34f, 80f, 32f, 71f, 6f)
internal fun DrawScope.drawBirdParty(u: Float, rim: Color?, ph: Float) {
    pPoly(u, rim, BIRD_PARTY_P23, BIRD_PARTY_TAIL)
    pOval(u, rim, 48f, 58f, 26f, 20f, BIRD_PARTY_BODY)
    pOval(u, rim, 58f, 64f, 16f, 12f, BIRD_PARTY_BELLY)
    pCircle(u, rim, 68f, 45f, 15f, BIRD_PARTY_BODY)
    pCircle(u, rim, 72f, 41f, 3.4f, BIRD_PARTY_EYE)
    pCircle(u, rim, 73f, 40f, 1.1f, BIRD_PARTY_GLINT)
    pPoly(u, rim, BIRD_PARTY_P24, BIRD_PARTY_BEAK)
    rotate(degrees = sin(ph * 2.2f + 0f) * 36f, pivot = Offset(54f * u, 52f * u)) {
        pOval(u, rim, 44f, 54f, 19f, 10f, BIRD_PARTY_WING)
    }
    pPoly(u, rim, BIRD_PARTY_P25, BIRD_PARTY_HAT)
    pLine(u, rim, 64f, 26f, 76f, 25f, 2.6f, BIRD_PARTY_STRIPE)
    pLine(u, rim, 66f, 18f, 75f, 17f, 2.6f, BIRD_PARTY_STRIPE)
    pCircle(u, rim, 71f, 6f, 4.4f, BIRD_PARTY_POM)
}

private val PUPPY_PARTY_FUR = Color(0xFFD9A066)
private val PUPPY_PARTY_EAR = Color(0xFFA16207)
private val PUPPY_PARTY_SNOUT = Color(0xFFF3E0C4)
private val PUPPY_PARTY_NOSE = Color(0xFF111827)
private val PUPPY_PARTY_EYE = Color(0xFF111827)
private val PUPPY_PARTY_TONGUE = Color(0xFFFB7185)
private val PUPPY_PARTY_HAT = Color(0xFF22D3EE)
private val PUPPY_PARTY_STRIPE = Color(0xFFFDE047)
private val PUPPY_PARTY_POM = Color(0xFFFDE047)
private val PUPPY_PARTY_P26 = floatArrayOf(64f, 36f, 82f, 34f, 72f, 10f)
internal fun DrawScope.drawPuppyParty(u: Float, rim: Color?, ph: Float) {
    rotate(degrees = sin(ph * 3.2f + 0f) * 24f, pivot = Offset(22f * u, 66f * u)) {
        pLine(u, rim, 20f, 64f, 8f, 50f, 6.5f, PUPPY_PARTY_FUR)
    }
    rotate(degrees = sin(ph * 1f + 0f) * 22f, pivot = Offset(31f * u, 76f * u)) {
        pRect(u, rim, 26f, 76f, 10f, 19f, 5f, PUPPY_PARTY_FUR)
    }
    rotate(degrees = sin(ph * 1f + 3.142f) * 22f, pivot = Offset(59f * u, 76f * u)) {
        pRect(u, rim, 54f, 76f, 10f, 19f, 5f, PUPPY_PARTY_FUR)
    }
    pOval(u, rim, 44f, 66f, 28f, 18f, PUPPY_PARTY_FUR)
    rotate(degrees = sin(ph * 1f + 3.142f) * 22f, pivot = Offset(41f * u, 78f * u)) {
        pRect(u, rim, 36f, 78f, 10f, 17f, 5f, PUPPY_PARTY_FUR)
    }
    rotate(degrees = sin(ph * 1f + 0f) * 22f, pivot = Offset(69f * u, 78f * u)) {
        pRect(u, rim, 64f, 78f, 10f, 17f, 5f, PUPPY_PARTY_FUR)
    }
    pCircle(u, rim, 72f, 48f, 15f, PUPPY_PARTY_FUR)
    rotate(degrees = sin(ph * 1.6f + 0f) * 10f, pivot = Offset(64f * u, 38f * u)) {
        pOval(u, rim, 64f, 48f, 6.5f, 13f, PUPPY_PARTY_EAR)
    }
    pOval(u, rim, 83f, 54f, 8.5f, 6.5f, PUPPY_PARTY_SNOUT)
    pCircle(u, rim, 89f, 52f, 3.2f, PUPPY_PARTY_NOSE)
    pCircle(u, rim, 75f, 44f, 3f, PUPPY_PARTY_EYE)
    pOval(u, rim, 84f, 62f, 3.6f, 5.2f, PUPPY_PARTY_TONGUE)
    pPoly(u, rim, PUPPY_PARTY_P26, PUPPY_PARTY_HAT)
    pLine(u, rim, 67f, 28f, 78f, 27f, 2.6f, PUPPY_PARTY_STRIPE)
    pLine(u, rim, 69f, 20f, 76f, 19f, 2.6f, PUPPY_PARTY_STRIPE)
    pCircle(u, rim, 72f, 10f, 4.4f, PUPPY_PARTY_POM)
}

private val PUPPY_PEEK_FUR = Color(0xFFD9A066)
private val PUPPY_PEEK_EAR = Color(0xFFA16207)
private val PUPPY_PEEK_SNOUT = Color(0xFFF3E0C4)
private val PUPPY_PEEK_NOSE = Color(0xFF111827)
private val PUPPY_PEEK_EYE = Color(0xFF111827)
private val PUPPY_PEEK_TONGUE = Color(0xFFFB7185)
private val PUPPY_PEEK_HAT = Color(0xFF22D3EE)
private val PUPPY_PEEK_STRIPE = Color(0xFFFDE047)
private val PUPPY_PEEK_POM = Color(0xFFFDE047)
private val PUPPY_PEEK_P27 = floatArrayOf(38f, 42f, 62f, 42f, 50f, 10f)
internal fun DrawScope.drawPuppyPeek(u: Float, rim: Color?, ph: Float) {
    rotate(degrees = sin(ph * 1.8f + 0f) * 26f, pivot = Offset(80f * u, 94f * u)) {
        pOval(u, rim, 80f, 82f, 8f, 13f, PUPPY_PEEK_FUR)
    }
    rotate(degrees = sin(ph * 1.4f + 0f) * 8f, pivot = Offset(28f * u, 50f * u)) {
        pOval(u, rim, 24f, 62f, 9f, 19f, PUPPY_PEEK_EAR)
    }
    rotate(degrees = sin(ph * 1.4f + 0f) * -8f, pivot = Offset(72f * u, 50f * u)) {
        pOval(u, rim, 76f, 62f, 9f, 19f, PUPPY_PEEK_EAR)
    }
    pCircle(u, rim, 50f, 66f, 27f, PUPPY_PEEK_FUR)
    pOval(u, rim, 50f, 78f, 14f, 10.5f, PUPPY_PEEK_SNOUT)
    pCircle(u, rim, 50f, 72f, 4.8f, PUPPY_PEEK_NOSE)
    pCircle(u, rim, 39f, 60f, 3.6f, PUPPY_PEEK_EYE)
    pCircle(u, rim, 61f, 60f, 3.6f, PUPPY_PEEK_EYE)
    pOval(u, rim, 50f, 88f, 4.2f, 5.8f, PUPPY_PEEK_TONGUE)
    pPoly(u, rim, PUPPY_PEEK_P27, PUPPY_PEEK_HAT)
    pLine(u, rim, 42f, 32f, 58f, 32f, 2.8f, PUPPY_PEEK_STRIPE)
    pLine(u, rim, 45f, 23f, 55f, 23f, 2.8f, PUPPY_PEEK_STRIPE)
    pCircle(u, rim, 50f, 10f, 4.6f, PUPPY_PEEK_POM)
}

private val BUNNY_PEEK_FUR = Color(0xFFF5F5F4)
private val BUNNY_PEEK_INNER = Color(0xFFF9A8D4)
private val BUNNY_PEEK_EYE = Color(0xFF1F2937)
private val BUNNY_PEEK_NOSE = Color(0xFFF472B6)
private val BUNNY_PEEK_WHISK = Color(0xFF9CA3AF)
private val BUNNY_PEEK_EGG = Color(0xFFA78BFA)
private val BUNNY_PEEK_EGG2 = Color(0xFFFDE047)
private val BUNNY_PEEK_P28 = floatArrayOf(46.5f, 71f, 53.5f, 71f, 50f, 76f)
internal fun DrawScope.drawBunnyPeek(u: Float, rim: Color?, ph: Float) {
    rotate(degrees = sin(ph * 1.2f + 0f) * 7f, pivot = Offset(40f * u, 46f * u)) {
        pOval(u, rim, 38f, 26f, 8.5f, 23f, BUNNY_PEEK_FUR)
    }
    rotate(degrees = sin(ph * 1.2f + 0f) * 7f, pivot = Offset(40f * u, 46f * u)) {
        pOval(u, rim, 38f, 27f, 4.2f, 16f, BUNNY_PEEK_INNER)
    }
    rotate(degrees = sin(ph * 1.2f + 3.142f) * -7f, pivot = Offset(60f * u, 46f * u)) {
        pOval(u, rim, 62f, 26f, 8.5f, 23f, BUNNY_PEEK_FUR)
    }
    rotate(degrees = sin(ph * 1.2f + 3.142f) * -7f, pivot = Offset(60f * u, 46f * u)) {
        pOval(u, rim, 62f, 27f, 4.2f, 16f, BUNNY_PEEK_INNER)
    }
    pCircle(u, rim, 50f, 68f, 25f, BUNNY_PEEK_FUR)
    pCircle(u, rim, 35f, 74f, 4.4f, BUNNY_PEEK_INNER)
    pCircle(u, rim, 65f, 74f, 4.4f, BUNNY_PEEK_INNER)
    pCircle(u, rim, 41f, 65f, 3.2f, BUNNY_PEEK_EYE)
    pCircle(u, rim, 59f, 65f, 3.2f, BUNNY_PEEK_EYE)
    pPoly(u, rim, BUNNY_PEEK_P28, BUNNY_PEEK_NOSE)
    pLine(u, rim, 30f, 74f, 18f, 71f, 1.2f, BUNNY_PEEK_WHISK)
    pLine(u, rim, 70f, 74f, 82f, 71f, 1.2f, BUNNY_PEEK_WHISK)
    rotate(degrees = sin(ph * 1.5f + 0f) * 10f, pivot = Offset(84f * u, 96f * u)) {
        pOval(u, rim, 84f, 86f, 10f, 13f, BUNNY_PEEK_EGG)
    }
    rotate(degrees = sin(ph * 1.5f + 0f) * 10f, pivot = Offset(84f * u, 96f * u)) {
        pLine(u, rim, 75f, 84f, 93f, 84f, 3.2f, BUNNY_PEEK_EGG2)
    }
}

private val HAT_SANTA_RED = Color(0xFFDC2626)
private val HAT_SANTA_WHITE = Color(0xFFF8FAFC)
private val HAT_SANTA_P29 = floatArrayOf(10f, 88f, 88f, 88f, 74f, 46f, 82f, 24f, 64f, 12f, 46f, 26f, 24f, 58f)
internal fun DrawScope.drawHatSanta(u: Float, rim: Color?, ph: Float) {
    pPoly(u, rim, HAT_SANTA_P29, HAT_SANTA_RED)
    pCircle(u, rim, 80f, 18f, 11f, HAT_SANTA_WHITE)
    pRect(u, rim, 6f, 76f, 88f, 17f, 8.5f, HAT_SANTA_WHITE)
}

private val HAT_BUNNY_FUR = Color(0xFFF5F5F4)
private val HAT_BUNNY_INNER = Color(0xFFF9A8D4)
internal fun DrawScope.drawHatBunny(u: Float, rim: Color?, ph: Float) {
    rotate(degrees = sin(ph * 0f + 1.571f) * 12f, pivot = Offset(34f * u, 86f * u)) {
        pOval(u, rim, 34f, 52f, 11f, 36f, HAT_BUNNY_FUR)
    }
    rotate(degrees = sin(ph * 0f + 1.571f) * 12f, pivot = Offset(34f * u, 86f * u)) {
        pOval(u, rim, 34f, 54f, 5.5f, 27f, HAT_BUNNY_INNER)
    }
    rotate(degrees = sin(ph * 0f + 1.571f) * -12f, pivot = Offset(66f * u, 86f * u)) {
        pOval(u, rim, 66f, 52f, 11f, 36f, HAT_BUNNY_FUR)
    }
    rotate(degrees = sin(ph * 0f + 1.571f) * -12f, pivot = Offset(66f * u, 86f * u)) {
        pOval(u, rim, 66f, 54f, 5.5f, 27f, HAT_BUNNY_INNER)
    }
}

private val HAT_WITCH_HAT = Color(0xFF4C1D95)
private val HAT_WITCH_BAND = Color(0xFFF97316)
private val HAT_WITCH_BUCKLE = Color(0xFFFDE047)
private val HAT_WITCH_P30 = floatArrayOf(27f, 86f, 73f, 86f, 60f, 42f, 68f, 16f, 50f, 6f, 46f, 36f)
internal fun DrawScope.drawHatWitch(u: Float, rim: Color?, ph: Float) {
    pOval(u, rim, 50f, 86f, 47f, 9f, HAT_WITCH_HAT)
    pPoly(u, rim, HAT_WITCH_P30, HAT_WITCH_HAT)
    pRect(u, rim, 29f, 66f, 42f, 11f, 0f, HAT_WITCH_BAND)
    pRect(u, rim, 45f, 64f, 10f, 15f, 2.5f, HAT_WITCH_BUCKLE)
}

private val HAT_PARTY_HAT = Color(0xFFE879F9)
private val HAT_PARTY_STRIPE = Color(0xFF22D3EE)
private val HAT_PARTY_POM = Color(0xFFFACC15)
private val HAT_PARTY_P31 = floatArrayOf(20f, 92f, 80f, 92f, 50f, 10f)
internal fun DrawScope.drawHatParty(u: Float, rim: Color?, ph: Float) {
    pPoly(u, rim, HAT_PARTY_P31, HAT_PARTY_HAT)
    pLine(u, rim, 34f, 70f, 66f, 70f, 6f, HAT_PARTY_STRIPE)
    pLine(u, rim, 40f, 50f, 60f, 50f, 6f, HAT_PARTY_STRIPE)
    pCircle(u, rim, 50f, 10f, 10f, HAT_PARTY_POM)
}
