package com.noven.ncrawler.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// ── Palette ───────────────────────────────────────────────────────────────
// Deep ink dark with a single amber accent — suits a night-reading novel app
private val Amber400   = Color(0xFFFFCA28)
private val Amber500   = Color(0xFFFFB300)
private val Ink950     = Color(0xFF0D0C0A)
private val Ink900     = Color(0xFF1A1916)
private val Ink800     = Color(0xFF272520)
private val Ink700     = Color(0xFF343129)
private val Stone400   = Color(0xFFA8A29E)
private val Stone200   = Color(0xFFE7E5E4)

private val DarkColors = darkColorScheme(
    primary          = Amber400,
    onPrimary        = Ink950,
    primaryContainer = Ink800,
    onPrimaryContainer = Amber400,
    background       = Ink950,
    onBackground     = Stone200,
    surface          = Ink900,
    onSurface        = Stone200,
    surfaceVariant   = Ink800,
    onSurfaceVariant = Stone400,
    outline          = Ink700,
    secondary        = Stone400,
    onSecondary      = Ink950
)

private val LightColors = lightColorScheme(
    primary          = Amber500,
    onPrimary        = Color.White,
    primaryContainer = Color(0xFFFFF8E1),
    onPrimaryContainer = Color(0xFF3E2A00),
    background       = Color(0xFFFAF9F7),
    onBackground     = Color(0xFF1C1B19),
    surface          = Color.White,
    onSurface        = Color(0xFF1C1B19),
    surfaceVariant   = Color(0xFFF5F3EE),
    onSurfaceVariant = Color(0xFF6B6560),
    outline          = Color(0xFFD6D3CE),
    secondary        = Color(0xFF7C776F),
    onSecondary      = Color.White
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
