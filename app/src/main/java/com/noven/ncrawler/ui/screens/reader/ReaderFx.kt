package com.noven.ncrawler.ui.screens.reader

import android.provider.Settings
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.LinearGradientShader
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noven.ncrawler.ui.theme.MontserratFamily
import com.noven.ncrawler.viewmodel.ReaderSettings
import kotlinx.coroutines.flow.first

// ── Highlighted passages ─────────────────────────────────────────────────────
//   [ … ]   → neon blue with a gradient that slowly moves through the letters
//             (game-style system messages: [Level Up!], [Skill: …])
//   * … *   → red that pulses (the asterisks themselves are dropped)
// Only paragraphs that actually contain such a passage take part, and a
// paragraph only animates while it is on screen (see FxParagraph).

internal enum class Fx { PLAIN, NEON, RED }
internal data class FxSeg(val text: String, val fx: Fx)

// [ … ] on one line, or *word* — the opening * must be followed by a non-space and
// the closing * preceded by one, so "***" / "* * *" scene breaks and "5 * 3 * 2"
// are left alone.
private val FX_REGEX = Regex("""\[[^\]\n]{1,400}\]|\*(?![\s*])[^*\n]{1,400}?(?<![\s*])\*""")

internal fun parseFx(text: String): List<FxSeg> {
    val out = ArrayList<FxSeg>()
    var last = 0
    for (m in FX_REGEX.findAll(text)) {
        if (m.range.first > last) out += FxSeg(text.substring(last, m.range.first), Fx.PLAIN)
        val raw = m.value
        out += if (raw.startsWith("[")) FxSeg(raw, Fx.NEON)                        // brackets stay
               else FxSeg(raw.substring(1, raw.length - 1), Fx.RED)                // asterisks go
        last = m.range.last + 1
    }
    if (last < text.length) out += FxSeg(text.substring(last), Fx.PLAIN)
    return out
}

// The two animation clocks, handed down as State objects and NOT read here: only
// a visible FxParagraph reads .value, so nothing else recomposes per frame.
internal class FxPhases(val neon: State<Float>, val pulse: State<Float>)

// Paragraph count past which a chapter's FX animation freezes instead of
// running live — see the CHANGE note at the call site above.
internal const val LONG_CHAPTER_FX_THRESHOLD = 150

@Composable
internal fun rememberFxPhases(freeze: Boolean = false): FxPhases {
    val context = LocalContext.current
    val reducedMotion = remember(context) {
        Settings.Global.getFloat(
            context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f
        ) == 0f
    }
    // "Remove animations", or a long chapter where the live version would
    // mean many non-windowed paragraphs recomposing every frame: keep the
    // colours, freeze the motion.
    if (reducedMotion || freeze) return remember { FxPhases(mutableStateOf(0f), mutableStateOf(0.6f)) }

    val transition = rememberInfiniteTransition(label = "readerFx")
    val neon = transition.animateFloat(
        initialValue  = 0f,
        targetValue   = 1f,
        animationSpec = infiniteRepeatable(tween(2600, easing = LinearEasing)),
        label         = "neonPhase"
    )
    val pulse = transition.animateFloat(
        initialValue  = 0f,
        targetValue   = 1f,
        animationSpec = infiniteRepeatable(tween(950, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label         = "redPulse"
    )
    return remember(neon, pulse) { FxPhases(neon, pulse) }
}

// A gradient that slides sideways: seamless because the first and last colours
// match and the tile repeats every [periodPx].
private class MovingGradientBrush(
    private val colors: List<Color>,
    private val shiftPx: Float,
    private val periodPx: Float
) : ShaderBrush() {
    override fun createShader(size: Size): Shader =
        LinearGradientShader(
            from     = Offset(shiftPx, 0f),
            to       = Offset(shiftPx + periodPx, 0f),
            colors   = colors,
            tileMode = TileMode.Repeated
        )
}

@Composable
internal fun FxParagraph(
    segments: List<FxSeg>,
    dropCap: Boolean,
    fx: FxPhases,
    darkBg: Boolean,
    settings: ReaderSettings,
    fg: Color,
    accent: Color,
    align: TextAlign,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val screenHeightPx = with(density) { LocalConfiguration.current.screenHeightDp.dp.toPx() }
    val periodPx = with(density) { 160.dp.toPx() }
    var visible by remember { mutableStateOf(true) }

    // Read the clocks only while on screen — off-screen, this paragraph subscribes
    // to nothing and is not recomposed every frame.
    val neonPhase = if (visible) fx.neon.value else 0f
    val pulse     = if (visible) fx.pulse.value else 0.5f

    // Shades per page brightness: cyan→electric blue on dark pages, deeper blues on
    // light pages (cyan would vanish on cream); glows only on dark pages.
    val neonColors = remember(darkBg) {
        if (darkBg) listOf(Color(0xFF00E5FF), Color(0xFF2979FF), Color(0xFF00B0FF), Color(0xFF00E5FF))
        else        listOf(Color(0xFF0033CC), Color(0xFF0088FF), Color(0xFF0055FF), Color(0xFF0033CC))
    }
    val redColor = if (darkBg) lerp(Color(0xFFC62828), Color(0xFFFF5252), pulse)
                   else        lerp(Color(0xFF8E0000), Color(0xFFE53935), pulse)
    val neonGlow = if (darkBg) Shadow(Color(0xFF00B0FF).copy(alpha = 0.55f), Offset.Zero, 14f) else null
    val redGlow  = if (darkBg) Shadow(redColor.copy(alpha = 0.25f + 0.4f * pulse), Offset.Zero, 8f + 10f * pulse) else null

    val annotated = buildAnnotatedString {
        var capPending = dropCap
        for (seg in segments) {
            var text = seg.text
            if (capPending && text.isNotEmpty()) {
                withStyle(SpanStyle(
                    fontSize   = (settings.fontSize * 2.4f).sp,
                    fontWeight = FontWeight.Black,
                    color      = accent
                )) { append(text.first().toString()) }
                text = text.substring(1)
                capPending = false
            }
            if (text.isEmpty()) continue
            when (seg.fx) {
                Fx.PLAIN -> append(text)
                Fx.NEON  -> withStyle(SpanStyle(
                    brush      = MovingGradientBrush(neonColors, neonPhase * periodPx, periodPx),
                    fontWeight = FontWeight.SemiBold,
                    shadow     = neonGlow
                )) { append(text) }
                Fx.RED   -> withStyle(SpanStyle(
                    color      = redColor,
                    fontWeight = FontWeight.SemiBold,
                    shadow     = redGlow
                )) { append(text) }
            }
        }
    }

    Text(
        text       = annotated,
        fontFamily = MontserratFamily,
        fontSize   = settings.fontSize.sp,
        color      = fg,
        textAlign  = align,
        lineHeight = (settings.fontSize * settings.lineHeight).sp,
        modifier   = modifier.onGloballyPositioned { coords ->
            val b = coords.boundsInWindow()
            visible = b.bottom > -300f && b.top < screenHeightPx + 300f
        }
    )
}
