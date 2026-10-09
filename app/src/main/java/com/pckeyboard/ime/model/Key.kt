package com.pckeyboard.ime.model

/**
 * Represents a single key on the keyboard.
 * Mirrors the structure used by full PC-style layouts so we can express
 * every modifier (Ctrl, Alt, Meta, Shift) plus normal characters.
 */
data class Key(
    val code: Int,
    val label: String,
    val shiftLabel: String? = null,
    /** Character produced when Alt (= AltGr) is held while tapping this key.
     *  Independent of [popupChars] — popups handle long-press, altLabel is
     *  the modifier-held output the way a physical PC keyboard works. */
    val altLabel: String? = null,
    val popupChars: String? = null,
    val widthWeight: Float = 1f,
    val type: KeyType = KeyType.CHAR,
    val keyCode: Int = 0,
    val repeatable: Boolean = false,
    val sticky: Boolean = false,
    val icon: String? = null,
    /** Small glyph drawn in the key's top-right corner (Gboard-style
     *  digit hint on the top letter row). Long-press offers it first. */
    val hint: String? = null,
    /** Part of [widthWeight] on the left / right that is touchable but not
     *  drawn — how Gboard indents the home row by half a key while still
     *  letting a tap in the indent hit the edge key. */
    val gapBefore: Float = 0f,
    val gapAfter: Float = 0f
) {
    companion object {
        const val CODE_UNSPECIFIED = 0

        /** Marker code for the configurable slot right of Space. Whatever
         *  the slot is currently set to (123 / emoji / Alt), long-pressing
         *  it opens the chooser popup, so the view needs to recognise the
         *  key independently of its current type and label. */
        const val CODE_RIGHT_OF_SPACE = -10

        fun char(label: String, shift: String? = null, alt: String? = null,
                 popup: String? = null, weight: Float = 1f): Key =
            Key(
                code = label.codePointAt(0),
                label = label,
                shiftLabel = shift,
                altLabel = alt,
                popupChars = popup,
                widthWeight = weight,
                type = KeyType.CHAR
            )

        fun letter(lower: String, alt: String? = null, popup: String? = null,
                   weight: Float = 1f, hint: String? = null,
                   gapBefore: Float = 0f, gapAfter: Float = 0f): Key =
            Key(
                code = lower.codePointAt(0),
                label = lower,
                shiftLabel = lower.uppercase(),
                altLabel = alt,
                popupChars = popup,
                widthWeight = weight + gapBefore + gapAfter,
                type = KeyType.LETTER,
                hint = hint,
                gapBefore = gapBefore,
                gapAfter = gapAfter
            )

        fun fn(label: String, type: KeyType, keyCode: Int = 0, weight: Float = 1f,
               sticky: Boolean = false, repeatable: Boolean = false, icon: String? = null): Key =
            Key(
                code = CODE_UNSPECIFIED,
                label = label,
                widthWeight = weight,
                type = type,
                keyCode = keyCode,
                sticky = sticky,
                repeatable = repeatable,
                icon = icon
            )
    }
}

enum class KeyType {
    CHAR,
    LETTER,
    SHIFT,
    CTRL,
    ALT,
    META,
    FN,
    SPACE,
    ENTER,
    BACKSPACE,
    DELETE,
    TAB,
    ESC,
    CAPS_LOCK,
    ARROW_LEFT,
    ARROW_RIGHT,
    ARROW_UP,
    ARROW_DOWN,
    HOME,
    END,
    PAGE_UP,
    PAGE_DOWN,
    INSERT,
    LAYOUT_SWITCH,
    SYMBOL_SWITCH,
    LANGUAGE_SWITCH,
    EMOJI,
    SETTINGS,
    HIDE,
    /** Empty, untouchable filler (Gboard split-keyboard centre gap). */
    SPACER
}
