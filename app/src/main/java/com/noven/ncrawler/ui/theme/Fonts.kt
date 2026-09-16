package com.noven.ncrawler.ui.theme

import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import com.noven.ncrawler.R

// ── Decorative fonts for genre showcase cards ──────────────────────────────────
// All three are OFL-licensed (Google Fonts) — free to bundle, no runtime
// download, no attribution required in-app.
//
// Pacifico — cursive script, used for warm/adventurous genres
val PacificoFamily = FontFamily(Font(R.font.pacifico_regular))

// Cinzel — serif variable font (weight axis). Pinned to Bold(700) via
// FontVariation to match the engraved/epic look fantasy genres want.
// CHANGE: added @OptIn(ExperimentalTextApi::class) — FontVariation.Settings
// is marked experimental in Compose UI text; the API itself is stable and
// widely used, the annotation is just Google's stability disclaimer.
// Advantage : single file covers the whole weight range, no extra files.
// Disadvantage: variable-font rendering is a hair heavier at layout time
//   than a static weight file — imperceptible at this text size. Also,
//   being an experimental API, a future Compose version could change its
//   signature — low risk, but worth knowing if a future BOM bump breaks it.
@OptIn(ExperimentalTextApi::class)
val CinzelFamily = FontFamily(
    Font(
        resId             = R.font.cinzel_variable,
        weight            = FontWeight.Bold,
        variationSettings = FontVariation.Settings(FontVariation.weight(700)),
    )
)

// Anton — bold condensed display font. Stand-in for Impact, which is a
// Microsoft core font and not freely redistributable. Anton is visually
// near-identical (same genre of ultra-bold condensed display face) and
// is OFL-licensed, so it's safe to bundle.
val AntonFamily = FontFamily(Font(R.font.anton_regular))
