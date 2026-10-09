package com.pckeyboard.ime.layout

import android.view.KeyEvent
import com.pckeyboard.ime.model.Key
import com.pckeyboard.ime.model.KeyType
import com.pckeyboard.ime.model.KeyboardLayout
import com.pckeyboard.ime.model.LayoutMode

/**
 * Compact phone layouts that mirror Gboard key for key — four rows
 * instead of the PC layout's six, so far more of the screen stays free:
 *
 *  - Top:     q w e r t z u i o p      digits 1–0 as top-right hints
 *  - Home:    a s d f g h j k l        @ # $ _ & - + ( ) hints, indented
 *                                      half a key (the indent still hits
 *                                      the edge key, like Gboard)
 *  - Bottom:  ⇧ y x c v b n m ⌫        * " ' : ; ! ? hints
 *  - Control: ?123  ,  ☺  space  .  ⏎
 *
 * Long-press offers the hint first, then the accented letters
 * (o → 9 ö ó ő …). [split] turns any of these into Gboard's split
 * keyboard for the unfolded screen of a foldable.
 */
object GboardLayout {

    private val DIGITS = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0")
    private val HOME_HINTS = listOf("@", "#", "$", "_", "&", "-", "+", "(", ")")
    private val BOTTOM_HINTS = listOf("*", "\"", "'", ":", ";", "!", "?")

    /** One letter of a row: the letter plus its long-press accents. */
    private class L(val letter: String, val accents: String = "")

    private fun row(
        letters: List<L>,
        hints: List<String>,
        indent: Float = 0f
    ): List<Key> = letters.mapIndexed { i, l ->
        val hint = hints.getOrNull(i)
        Key.letter(
            l.letter,
            popup = if (hint != null || l.accents.isNotEmpty()) l.accents else null,
            hint = hint,
            gapBefore = if (i == 0) indent else 0f,
            gapAfter = if (i == letters.lastIndex) indent else 0f
        )
    }

    /** ⇧ + letters + ⌫, Shift / Backspace widened so the row spans
     *  [total] key units like the rows above it. */
    private fun shiftRow(letters: List<L>, total: Float): List<Key> {
        val side = (total - letters.size) / 2f
        return listOf(Key.fn("⇧", KeyType.SHIFT, sticky = true, weight = side)) +
            row(letters, BOTTOM_HINTS) +
            Key.fn("⌫", KeyType.BACKSPACE, KeyEvent.KEYCODE_DEL, weight = side, repeatable = true)
    }

    /** `?123 , ☺ space . ⏎` spanning [total] key units. The Space caption
     *  (enabled language codes) is filled in by the service. */
    private fun controlRow(total: Float): List<Key> = listOf(
        Key.fn("?123", KeyType.SYMBOL_SWITCH, weight = 1.5f),
        Key.char(",", popup = "!?;:"),
        Key.fn("☺", KeyType.EMOJI, icon = ICON_EMOJI),
        Key.fn("", KeyType.SPACE, KeyEvent.KEYCODE_SPACE, weight = total - 6f),
        Key.char(".", popup = "?!,'\"-:;@#"),
        Key.fn("⏎", KeyType.ENTER, KeyEvent.KEYCODE_ENTER, weight = 1.5f, icon = ICON_ENTER)
    )

    private fun layout(id: String, name: String, rows: List<List<Key>>) =
        KeyboardLayout(id = id, displayName = name, rows = rows, mode = LayoutMode.MAIN)

    fun hungarian(): KeyboardLayout = layout(
        "hu_HU_gboard", "Magyar",
        listOf(
            row(listOf(
                L("q"), L("w"), L("e", "éëèêē"), L("r"), L("t"),
                L("z", "žźż"), L("u", "üúűùûū"), L("i", "íìîïī"), L("o", "öóőòôõø"), L("p")
            ), DIGITS),
            row(listOf(
                L("a", "áàâäãåæ"), L("s", "ßš"), L("d"), L("f"), L("g"),
                L("h"), L("j"), L("k"), L("l", "ł")
            ), HOME_HINTS, indent = 0.5f),
            shiftRow(listOf(
                L("y", "ý"), L("x"), L("c", "čćç"), L("v"),
                L("b"), L("n", "ñń"), L("m")
            ), total = 10f),
            controlRow(10f)
        )
    )

    fun english(): KeyboardLayout = layout(
        "en_US_gboard", "English",
        listOf(
            row(listOf(
                L("q"), L("w"), L("e", "éèêëē"), L("r"), L("t"),
                L("y", "ýÿ"), L("u", "úüùûū"), L("i", "íìîïī"), L("o", "óöòôõøœ"), L("p")
            ), DIGITS),
            row(listOf(
                L("a", "áàâäãåæ"), L("s", "ßš"), L("d"), L("f"), L("g"),
                L("h"), L("j"), L("k"), L("l")
            ), HOME_HINTS, indent = 0.5f),
            shiftRow(listOf(
                L("z"), L("x"), L("c", "çćč"), L("v"),
                L("b"), L("n", "ñń"), L("m")
            ), total = 10f),
            controlRow(10f)
        )
    )

    fun german(): KeyboardLayout = layout(
        "de_DE_gboard", "Deutsch",
        listOf(
            row(listOf(
                L("q"), L("w"), L("e", "éèêëė"), L("r"), L("t"),
                L("z"), L("u", "úùûū"), L("i", "íìîï"), L("o", "óòôõœø"), L("p"), L("ü")
            ), DIGITS),
            row(listOf(
                L("a", "áàâãåæ"), L("s", "ßśš"), L("d"), L("f"), L("g"),
                L("h"), L("j"), L("k"), L("l"), L("ö"), L("ä")
            ), HOME_HINTS),
            shiftRow(listOf(
                L("y"), L("x"), L("c", "çćč"), L("v"),
                L("b"), L("n", "ñń"), L("m")
            ), total = 11f),
            controlRow(11f)
        )
    )

    fun spanish(): KeyboardLayout = layout(
        "es_ES_gboard", "Español",
        listOf(
            row(listOf(
                L("q"), L("w"), L("e", "éèêë"), L("r"), L("t"),
                L("y"), L("u", "úüùû"), L("i", "íïìî"), L("o", "óòöôõº"), L("p")
            ), DIGITS),
            row(listOf(
                L("a", "áàâäãåªæ"), L("s"), L("d"), L("f"), L("g"),
                L("h"), L("j"), L("k"), L("l"), L("ñ")
            ), HOME_HINTS),
            shiftRow(listOf(
                L("z"), L("x"), L("c", "çćč"), L("v"),
                L("b"), L("n", "ńñ"), L("m")
            ), total = 10f),
            controlRow(10f)
        )
    )

    // --- Split keyboard (unfolded foldables) -------------------------------

    /*
     * Proportions measured off Gboard's split keyboard on an unfolded
     * Galaxy Z Fold, in units of one top-row key (key + gap) for a
     * 10-column layout: each half is 5 units wide, the centre gap is
     * 2.61, the outer margins 0.545. Wide keys (Shift, ⌫, ?123, ⏎) are
     * 1.255 units; home / bottom row letters share what is left, so they
     * end up slightly narrower than the top row — like Gboard. Rows with
     * an odd key count repeat the middle key on both halves (g and v on
     * QWERTZ), so either thumb can reach it.
     */
    private const val SPLIT_GAP = 2.61f
    private const val SPLIT_MARGIN = 0.545f
    private const val SPLIT_WIDE = 1.255f
    private const val SPLIT_INDENT = 0.317f
    private const val SPLIT_BOTTOM_KEY = 0.938f

    /** Re-flows a compact layout (letters or symbols) into Gboard's split
     *  form. Rows containing Space stay whole, with Space bridging the gap. */
    fun split(layout: KeyboardLayout): KeyboardLayout {
        val columns = layout.rows.firstOrNull()
            ?.sumOf { it.widthWeight.toDouble() }?.toFloat() ?: return layout
        val scale = columns / 10f
        val half = columns / 2f
        val rows = layout.rows.map { row ->
            if (row.any { it.type == KeyType.SPACE }) splitSpaceRow(row, scale, half)
            else splitRow(row, scale, half)
        }
        return layout.copy(id = layout.id + "_split", rows = rows)
    }

    private fun spacer(weight: Float) = Key.fn("", KeyType.SPACER, weight = weight)

    private fun isWide(key: Key) = key.type != KeyType.LETTER && key.type != KeyType.CHAR

    private fun splitRow(row: List<Key>, scale: Float, half: Float): List<Key> {
        val mid = row.size / 2
        val (left, right) = if (row.size % 2 == 0) {
            row.subList(0, mid) to row.subList(mid, row.size)
        } else {
            row.subList(0, mid + 1) to row.subList(mid, row.size)
        }
        val indented = row.first().gapBefore > 0f
        return listOf(spacer(SPLIT_MARGIN * scale)) +
            layoutHalf(left, scale, half, indented, outerIsLeft = true) +
            spacer(SPLIT_GAP * scale) +
            layoutHalf(right, scale, half, indented, outerIsLeft = false) +
            spacer(SPLIT_MARGIN * scale)
    }

    private fun layoutHalf(
        keys: List<Key>, scale: Float, half: Float,
        indented: Boolean, outerIsLeft: Boolean
    ): List<Key> {
        val indent = if (indented) SPLIT_INDENT * scale else 0f
        val wideCount = keys.count(::isWide)
        val narrowCount = keys.size - wideCount
        val wide = SPLIT_WIDE * scale
        val narrow = if (narrowCount > 0)
            (half - indent - wide * wideCount) / narrowCount else 0f
        return keys.mapIndexed { i, key ->
            val base = if (isWide(key)) wide else narrow
            val outer = if (outerIsLeft) i == 0 else i == keys.lastIndex
            val before = if (outer && outerIsLeft) indent else 0f
            val after = if (outer && !outerIsLeft) indent else 0f
            key.copy(widthWeight = base + before + after, gapBefore = before, gapAfter = after)
        }
    }

    private fun splitSpaceRow(row: List<Key>, scale: Float, half: Float): List<Key> {
        val total = 2 * half + SPLIT_GAP * scale
        val resized = row.map { key ->
            when {
                key.type == KeyType.SPACE -> key
                key.widthWeight > 1f -> key.copy(widthWeight = SPLIT_WIDE * scale)
                else -> key.copy(widthWeight = SPLIT_BOTTOM_KEY * scale)
            }
        }
        val others = resized.filter { it.type != KeyType.SPACE }
            .sumOf { it.widthWeight.toDouble() }.toFloat()
        val spaceCount = resized.count { it.type == KeyType.SPACE }.coerceAtLeast(1)
        val spaceWidth = (total - others) / spaceCount
        return listOf(spacer(SPLIT_MARGIN * scale)) +
            resized.map { if (it.type == KeyType.SPACE) it.copy(widthWeight = spaceWidth) else it } +
            spacer(SPLIT_MARGIN * scale)
    }

    const val ICON_EMOJI = "emoji"
    const val ICON_ENTER = "enter"
    const val ICON_SEARCH = "search"
    const val ICON_SEND = "send"
    const val ICON_DONE = "done"
    const val ICON_NEXT = "next"
}
