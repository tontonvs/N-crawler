package com.noven.ncrawler.ui.holiday

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color

// Theme-aware colours for every holiday effect. The same holiday gets a different
// set in light and dark mode: pale confetti that glows on a dark page would vanish
// on a white one, so light mode uses deeper, more saturated tones and dark mode
// lighter, softer ones.
//   a, b = the main particle colours      c, d = accents
//   ink  = greeting / label colour         glow = soft halo behind the greeting
//   rim  = the thin outline around animals (dark line on light pages, light line on dark ones)
//   card = the greeting card's background
@Immutable
class HolidayPalette(
    val a: Color,
    val b: Color,
    val c: Color,
    val d: Color,
    val ink: Color,
    val glow: Color,
    val rim: Color,
    val card: Color
) {
    fun pick(i: Int): Color = when (i and 3) { 0 -> a; 1 -> b; 2 -> c; else -> d }
}

@Composable
fun holidayPalette(holiday: Holiday): HolidayPalette {
    val dark = isSystemInDarkTheme()
    return remember(holiday, dark) { buildPalette(holiday, dark) }
}

private fun buildPalette(h: Holiday, dark: Boolean): HolidayPalette {
    val rim  = if (dark) Color(0x99FFFFFF) else Color(0x70202030)
    val card = if (dark) Color(0xF01B2230) else Color(0xF2FFFFFF)
    return when (h) {
        Holiday.CHRISTMAS -> if (dark) HolidayPalette(
            a = Color(0xFFFFFFFF), b = Color(0xFFD6E4F5), c = Color(0xFFEF5350), d = Color(0xFF66BB6A),
            ink = Color(0xFFFF8A80), glow = Color(0xFFEF5350), rim = rim, card = card
        ) else HolidayPalette(
            a = Color(0xFF7FA6D1), b = Color(0xFFA9C6E3), c = Color(0xFFC62828), d = Color(0xFF2E7D32),
            ink = Color(0xFFB71C1C), glow = Color(0xFFE53935), rim = rim, card = card
        )
        Holiday.EASTER -> if (dark) HolidayPalette(
            a = Color(0xFFF8BBD0), b = Color(0xFFD1C4E9), c = Color(0xFFB2DFDB), d = Color(0xFFFFE082),
            ink = Color(0xFFE1BEE7), glow = Color(0xFFCE93D8), rim = rim, card = card
        ) else HolidayPalette(
            a = Color(0xFFEC6FA0), b = Color(0xFF9575CD), c = Color(0xFF4DB6AC), d = Color(0xFFF9A825),
            ink = Color(0xFF8E24AA), glow = Color(0xFFBA68C8), rim = rim, card = card
        )
        Holiday.HALLOWEEN -> if (dark) HolidayPalette(
            a = Color(0xFFFF9800), b = Color(0xFFB388FF), c = Color(0xFF9CCC65), d = Color(0xFFFFD54F),
            ink = Color(0xFFFFB74D), glow = Color(0xFFFF9800), rim = rim, card = card
        ) else HolidayPalette(
            a = Color(0xFFE65100), b = Color(0xFF6A1B9A), c = Color(0xFF33691E), d = Color(0xFFF57F17),
            ink = Color(0xFFBF360C), glow = Color(0xFFFB8C00), rim = rim, card = card
        )
        Holiday.JUNE_2 -> if (dark) HolidayPalette(
            a = Color(0xFFFF80AB), b = Color(0xFF4DD0E1), c = Color(0xFFFFD54F), d = Color(0xFFB39DDB),
            ink = Color(0xFFFF80AB), glow = Color(0xFFFF4081), rim = rim, card = card
        ) else HolidayPalette(
            a = Color(0xFFE91E63), b = Color(0xFF00ACC1), c = Color(0xFFFFB300), d = Color(0xFF7E57C2),
            ink = Color(0xFFAD1457), glow = Color(0xFFF06292), rim = rim, card = card
        )
    }
}
