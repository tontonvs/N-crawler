package com.noven.ncrawler.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// ── Brand palette — blue accent, NO yellow (StarGold is the sole exception,
//    reserved strictly for the rating icon — see design note in chat) ─────────
val NavBlue        = Color(0xFF08519C)   // top nav title, active icons
val NavBgColor     = Color(0xFFA8EEFF)   // top nav background
val AccentBlue     = Color(0xFF1565C0)   // buttons, FAB
val AccentBlueDark = Color(0xFF1E88E5)   // dark mode accent
val StarGold       = Color(0xFFFFB300)   // star rating only

// ── Glass tokens — translucent surfaces for frosted-glass cards/bars ──────────
// minSdk 26 means true backdrop blur (RenderEffect, API 31+) isn't reliably
// available, so "glass" here is done the compatible way: a semi-opaque fill +
// a soft light border + a diffuse shadow, which reads as frosted glass on both
// the sky-blue top bar and white content areas without needing a blur pass.
val GlassSurfaceLight  = Color(0xF2FFFFFF)  // ~95% white — cards over content
val GlassSurfaceMuted  = Color(0xCCFFFFFF)  // ~80% white — bars over imagery
val GlassBorderLight   = Color(0x59FFFFFF)  // hairline highlight, light mode
val GlassSurfaceDark   = Color(0xE6111827)  // dark-mode glass fill
val GlassBorderDark    = Color(0x33FFFFFF)  // dark-mode hairline highlight

// ── Light scheme ──────────────────────────────────────────────────────────────
private val LightColors = lightColorScheme(
    primary            = AccentBlue,
    onPrimary          = Color.White,
    primaryContainer   = Color(0xFFDCEEFF),
    onPrimaryContainer = NavBlue,
    secondary          = Color(0xFF546E7A),
    onSecondary        = Color.White,
    background         = Color(0xFFF4F7F9),
    onBackground       = Color(0xFF0D1117),
    surface            = Color.White,
    onSurface          = Color(0xFF0D1117),
    surfaceVariant     = Color(0xFFEEF4FB),
    onSurfaceVariant   = Color(0xFF5C6B7A),
    outline            = Color(0xFFCDD8E3),
    error              = Color(0xFFBA1A1A)
)

// ── Dark scheme ───────────────────────────────────────────────────────────────
private val DarkColors = darkColorScheme(
    primary            = AccentBlueDark,
    onPrimary          = Color.White,
    primaryContainer   = Color(0xFF0A3060),
    onPrimaryContainer = Color(0xFF90CAF9),
    secondary          = Color(0xFF90A4AE),
    onSecondary        = Color(0xFF0D1117),
    background         = Color(0xFF0A0E1A),
    onBackground       = Color(0xFFE8EDF5),
    surface            = Color(0xFF111827),
    onSurface          = Color(0xFFE8EDF5),
    surfaceVariant     = Color(0xFF1C2A3A),
    onSurfaceVariant   = Color(0xFF8EA8C3),
    outline            = Color(0xFF2A3F55),
    error              = Color(0xFFFFB4AB)
)

// ── Typography — system sans, bold/rounded feel ───────────────────────────────
// Using system default (no custom font assets needed) with tight tracking
// for the playful bold feel requested
val NCrawlerTypography = Typography(
    displaySmall = TextStyle(
        fontFamily   = FontFamily.Default,
        fontWeight   = FontWeight.ExtraBold,
        fontSize     = 28.sp,
        lineHeight   = 34.sp,
        letterSpacing = (-0.5).sp
    ),
    titleLarge = TextStyle(
        fontFamily   = FontFamily.Default,
        fontWeight   = FontWeight.Bold,
        fontSize     = 20.sp,
        lineHeight   = 26.sp,
        letterSpacing = (-0.3).sp
    ),
    titleMedium = TextStyle(
        fontFamily   = FontFamily.Default,
        fontWeight   = FontWeight.Bold,
        fontSize     = 16.sp,
        lineHeight   = 22.sp,
        letterSpacing = (-0.2).sp
    ),
    titleSmall = TextStyle(
        fontFamily   = FontFamily.Default,
        fontWeight   = FontWeight.SemiBold,
        fontSize     = 14.sp,
        lineHeight   = 20.sp
    ),
    bodyLarge = TextStyle(
        fontFamily   = FontFamily.Default,
        fontWeight   = FontWeight.Normal,
        fontSize     = 16.sp,
        lineHeight   = 26.sp
    ),
    bodyMedium = TextStyle(
        fontFamily   = FontFamily.Default,
        fontWeight   = FontWeight.Normal,
        fontSize     = 14.sp,
        lineHeight   = 20.sp
    ),
    labelLarge = TextStyle(
        fontFamily   = FontFamily.Default,
        fontWeight   = FontWeight.Bold,
        fontSize     = 13.sp,
        lineHeight   = 18.sp,
        letterSpacing = 0.2.sp
    ),
    labelMedium = TextStyle(
        fontFamily   = FontFamily.Default,
        fontWeight   = FontWeight.SemiBold,
        fontSize     = 11.sp,
        lineHeight   = 16.sp,
        letterSpacing = 0.3.sp
    ),
    labelSmall = TextStyle(
        fontFamily   = FontFamily.Default,
        fontWeight   = FontWeight.Medium,
        fontSize     = 10.sp,
        lineHeight   = 14.sp,
        letterSpacing = 0.4.sp
    )
)

@Composable
fun NCrawlerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography  = NCrawlerTypography,
        content     = content
    )
}
