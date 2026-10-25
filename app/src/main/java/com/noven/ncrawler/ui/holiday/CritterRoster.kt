package com.noven.ncrawler.ui.holiday

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope

// How a critter moves on screen.
//   RUN  — walks/hops along the top of the floating nav bar, edge to edge
//   FLY  — crosses the screen in a lazy wavy line
//   PEEK — rises from behind the nav bar (or the screen's bottom edge), looks around, ducks back
internal enum class CritterBehavior { RUN, FLY, PEEK }

// One animal. sizeDp = on-screen size; speedDp = travel speed (RUN/FLY);
// gait = how many leg/wing cycles per second; hop = how high it bounces (fraction of its size).
internal enum class Critter(
    val behavior: CritterBehavior,
    val sizeDp: Int,
    val speedDp: Float,
    val gait: Float,
    val hop: Float,
    val rock: Float
) {
    PENGUIN_SANTA(CritterBehavior.RUN, 56, 62f, 2.4f, 0.04f, 7f),
    ROBIN(CritterBehavior.FLY, 50, 92f, 3.6f, 0f, 4f),
    SNOWMAN_PEEK(CritterBehavior.PEEK, 66, 0f, 1.2f, 0f, 3f),

    BUNNY(CritterBehavior.RUN, 58, 78f, 1.5f, 0.30f, 5f),
    CHICK(CritterBehavior.RUN, 48, 70f, 3.2f, 0.06f, 8f),
    BUTTERFLY(CritterBehavior.FLY, 46, 58f, 2.2f, 0f, 9f),
    BUNNY_PEEK(CritterBehavior.PEEK, 64, 0f, 1.2f, 0f, 4f),

    BAT(CritterBehavior.FLY, 56, 100f, 2.8f, 0f, 6f),
    BLACK_CAT(CritterBehavior.RUN, 60, 84f, 2.2f, 0.05f, 2f),
    GHOST(CritterBehavior.FLY, 56, 42f, 1.2f, 0f, 5f),
    PUMPKIN_PEEK(CritterBehavior.PEEK, 64, 0f, 1.2f, 0f, 5f),

    BIRD_PARTY(CritterBehavior.FLY, 52, 90f, 3.6f, 0f, 4f),
    PUPPY_PARTY(CritterBehavior.RUN, 60, 80f, 2.6f, 0.08f, 3f),
    PUPPY_PEEK(CritterBehavior.PEEK, 64, 0f, 1.6f, 0f, 4f);
}

internal fun rosterFor(holiday: Holiday): List<Critter> = when (holiday) {
    Holiday.CHRISTMAS -> listOf(Critter.PENGUIN_SANTA, Critter.ROBIN, Critter.SNOWMAN_PEEK)
    Holiday.EASTER    -> listOf(Critter.BUNNY, Critter.CHICK, Critter.BUTTERFLY, Critter.BUNNY_PEEK)
    Holiday.HALLOWEEN -> listOf(Critter.BAT, Critter.BLACK_CAT, Critter.GHOST, Critter.PUMPKIN_PEEK)
    Holiday.JUNE_2    -> listOf(Critter.BIRD_PARTY, Critter.PUPPY_PARTY, Critter.PUPPY_PEEK)
}

private fun DrawScope.drawArt(c: Critter, u: Float, rim: Color?, ph: Float) {
    when (c) {
        Critter.PENGUIN_SANTA -> drawPenguinSanta(u, rim, ph)
        Critter.ROBIN         -> drawRobin(u, rim, ph)
        Critter.SNOWMAN_PEEK  -> drawSnowmanPeek(u, rim, ph)
        Critter.BUNNY         -> drawBunny(u, rim, ph)
        Critter.CHICK         -> drawChick(u, rim, ph)
        Critter.BUTTERFLY     -> drawButterfly(u, rim, ph)
        Critter.BUNNY_PEEK    -> drawBunnyPeek(u, rim, ph)
        Critter.BAT           -> drawBat(u, rim, ph)
        Critter.BLACK_CAT     -> drawBlackCat(u, rim, ph)
        Critter.GHOST         -> drawGhost(u, rim, ph)
        Critter.PUMPKIN_PEEK  -> drawPumpkinPeek(u, rim, ph)
        Critter.BIRD_PARTY    -> drawBirdParty(u, rim, ph)
        Critter.PUPPY_PARTY   -> drawPuppyParty(u, rim, ph)
        Critter.PUPPY_PEEK    -> drawPuppyPeek(u, rim, ph)
    }
}

/** Outline pass, then the colours on top. [ph] = gait phase in radians. */
internal fun DrawScope.drawCritter(c: Critter, u: Float, rim: Color, ph: Float) {
    drawArt(c, u, rim, ph)
    drawArt(c, u, null, ph)
}

/** The little hat the Home icon wears. */
internal fun DrawScope.drawHat(holiday: Holiday, u: Float, rim: Color) {
    when (holiday) {
        Holiday.CHRISTMAS -> { drawHatSanta(u, rim, 0f); drawHatSanta(u, null, 0f) }
        Holiday.EASTER    -> { drawHatBunny(u, rim, 0f); drawHatBunny(u, null, 0f) }
        Holiday.HALLOWEEN -> { drawHatWitch(u, rim, 0f); drawHatWitch(u, null, 0f) }
        Holiday.JUNE_2    -> { drawHatParty(u, rim, 0f); drawHatParty(u, null, 0f) }
    }
}
