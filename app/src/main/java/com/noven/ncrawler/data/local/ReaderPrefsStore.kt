package com.noven.ncrawler.data.local

import android.content.Context

// Persists reader reading-experience settings across app restarts. Colors
// themselves are NOT stored here (they're derived per-novel from its cover
// at read time) — only which swatch slot (0-4) the user last picked, plus
// the generic settings (font size, line height, alignment, brightness).
class ReaderPrefsStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getFontSize(default: Float): Float = prefs.getFloat(KEY_FONT_SIZE, default)
    fun getLineHeight(default: Float): Float = prefs.getFloat(KEY_LINE_HEIGHT, default)
    fun getTextAlignOrdinal(default: Int): Int = prefs.getInt(KEY_TEXT_ALIGN, default)
    fun getBrightness(default: Float): Float = prefs.getFloat(KEY_BRIGHTNESS, default)
    fun getSwatchIndex(default: Int): Int = prefs.getInt(KEY_SWATCH, default)

    fun save(
        fontSize: Float,
        lineHeight: Float,
        textAlignOrdinal: Int,
        brightness: Float,
        swatchIndex: Int
    ) {
        prefs.edit()
            .putFloat(KEY_FONT_SIZE, fontSize)
            .putFloat(KEY_LINE_HEIGHT, lineHeight)
            .putInt(KEY_TEXT_ALIGN, textAlignOrdinal)
            .putFloat(KEY_BRIGHTNESS, brightness)
            .putInt(KEY_SWATCH, swatchIndex)
            .apply()
    }

    companion object {
        private const val PREFS_NAME    = "reader_prefs"
        private const val KEY_FONT_SIZE   = "font_size"
        private const val KEY_LINE_HEIGHT = "line_height"
        private const val KEY_TEXT_ALIGN  = "text_align"
        private const val KEY_BRIGHTNESS  = "brightness"
        private const val KEY_SWATCH      = "swatch_index"
    }
}
