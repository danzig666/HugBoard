package com.pckeyboard.ime.model

/**
 * A full keyboard layout, organised as rows of keys.
 * The layout is pluggable: a layout for another locale is just another
 * KeyboardLayout instance, so new locales can be added without touching
 * the renderer or service.
 */
data class KeyboardLayout(
    val id: String,
    val displayName: String,
    val rows: List<List<Key>>,
    val mode: LayoutMode = LayoutMode.MAIN,
    /** Render with Gboard look & feel: flat letter-coloured Space, only
     *  Enter in the accent colour, drawn Shift arrow, key-press preview. */
    val gboardStyle: Boolean = false
)

enum class LayoutMode {
    MAIN,
    SYMBOLS,
    SYMBOLS_SHIFT,
    NUMERIC,
    PHONE
}
