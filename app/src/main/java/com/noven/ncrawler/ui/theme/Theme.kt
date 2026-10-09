package com.noven.ncrawler.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.text.googlefonts.Font as GoogleFontRef
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noven.ncrawler.R

// ── Brand palette — blue accent, NO yellow (StarGold is the sole exception,
//    reserved strictly for the rating icon — see design note in chat) ─────────
val NavBlue        = Color(0xFF08519C)   // top nav title, active icons
val AccentBlue     = Color(0xFF1565C0)   // buttons, FAB
val AccentBlueDark = Color(0xFF1E88E5)   // dark mode accent
val StarGold       = Color(0xFFFFB300)   // star rating only

// ── Glass tokens — translucent surfaces for frosted-glass cards/bars ──────────
// minSdk 26 means true backdrop blur (RenderEffect, API 31+) isn't reliably
// available, so "glass" here is done the compatible way: a semi-opaque fill +
// a soft light border + a diffuse shadow, which reads as frosted glass on both
// the sky-blue top bar and white content areas without needing a blur pass.
// Two looks, switched in Settings (see GlassMode):
//   CLASSIC (default) — the original near-opaque fill + light rim (values below).
//   GLASS (opt-in)    — tv3: blur 30 · card fill 44% · nav fill 51% · border 0%,
//                       fill = tv3's neutral 12% grey (dark) / white (light).
object GlassSpec {
    const val CARD_FILL = 0.44f
    const val NAV_FILL  = 0.51f
    const val BORDER    = 0f               // 0% — no edge line anywhere
    val BLUR            = 30.dp            // real backdrop blur (Haze), Android 12+
}
val GlassBase          = Color(0xFF1F1F1F)  // tv3 --secondary (12% grey)

// Glass mode values
val GlassSurfaceLight  = Color(0x70FFFFFF)  // white @ 44%
val GlassSurfaceDark   = Color(0x701F1F1F)  // 12% grey @ 44%

// Classic values (the look before Glass mode existed)
val ClassicSurfaceLight = Color(0xF2FFFFFF)  // ~95% white — cards over content
val ClassicSurfaceDark  = Color(0xE6111827)  // dark-mode glass fill
val ClassicBorderLight  = Color(0x59FFFFFF)  // hairline highlight, light mode
val ClassicBorderDark   = Color(0x33FFFFFF)  // dark-mode hairline highlight

// These two existed but nothing actually read them — every glass surface on
// Home was hardcoded to the light variant regardless of system theme. Use
// these instead of GlassSurfaceLight/GlassBorderLight directly anywhere a
// glass card can appear in dark mode too.
@Composable
fun glassSurface(): Color {
    val dark = isSystemInDarkTheme()
    return if (GlassMode.enabled) { if (dark) GlassSurfaceDark else GlassSurfaceLight }
           else                   { if (dark) ClassicSurfaceDark else ClassicSurfaceLight }
}

// Glass mode has no border (0%) — transparent.
@Composable
fun glassBorder(): Color {
    val dark = isSystemInDarkTheme()
    return when {
        GlassMode.enabled -> Color.Transparent
        dark              -> ClassicBorderDark
        else              -> ClassicBorderLight
    }
}

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

// ── Typography — Montserrat for titles/headers, system sans for body ──────────
// Montserrat is a genuinely open (SIL OFL) Google Font, fetched at runtime via
// Android's standard Downloadable Fonts provider — the exact same mechanism
// used across the Android ecosystem, not anything bundled/proprietary. Titles
// only: body/label text stays on the system font for max legibility at small
// sizes — matches the bold-geometric-title / plain-body pattern this was
// modeled on.
private val montserratGoogleFont = GoogleFont("Montserrat")
private val montserratProvider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage   = "com.google.android.gms",
    certificates      = R.array.com_google_android_gms_fonts_certs
)
val MontserratFamily = FontFamily(
    GoogleFontRef(googleFont = montserratGoogleFont, fontProvider = montserratProvider, weight = FontWeight.Black),
    GoogleFontRef(googleFont = montserratGoogleFont, fontProvider = montserratProvider, weight = FontWeight.ExtraBold),
    GoogleFontRef(googleFont = montserratGoogleFont, fontProvider = montserratProvider, weight = FontWeight.Bold),
    GoogleFontRef(googleFont = montserratGoogleFont, fontProvider = montserratProvider, weight = FontWeight.SemiBold),
    GoogleFontRef(googleFont = montserratGoogleFont, fontProvider = montserratProvider, weight = FontWeight.Medium),
    GoogleFontRef(googleFont = montserratGoogleFont, fontProvider = montserratProvider, weight = FontWeight.Normal)
)

val NCrawlerTypography = Typography(
    displaySmall = TextStyle(
        fontFamily   = MontserratFamily,
        fontWeight   = FontWeight.ExtraBold,
        fontSize     = 28.sp,
        lineHeight   = 34.sp,
        letterSpacing = (-0.5).sp
    ),
    titleLarge = TextStyle(
        fontFamily   = MontserratFamily,
        fontWeight   = FontWeight.Bold,
        fontSize     = 20.sp,
        lineHeight   = 26.sp,
        letterSpacing = (-0.3).sp
    ),
    titleMedium = TextStyle(
        fontFamily   = MontserratFamily,
        fontWeight   = FontWeight.Bold,
        fontSize     = 16.sp,
        lineHeight   = 22.sp,
        letterSpacing = (-0.2).sp
    ),
    titleSmall = TextStyle(
        fontFamily   = MontserratFamily,
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
