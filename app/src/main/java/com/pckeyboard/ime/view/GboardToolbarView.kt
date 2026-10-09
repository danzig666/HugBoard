package com.pckeyboard.ime.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.pckeyboard.ime.theme.KeyboardTheme

/**
 * Gboard's strip above the keys: a 2×2 grid button on the left (opens the
 * keyboard menu), three suggestion slots separated by thin dividers with
 * the best candidate in the middle, and a round microphone button on the
 * right. Always present in Gboard style so the keyboard never changes
 * height while typing.
 */
class GboardToolbarView(
    context: Context,
    val theme: KeyboardTheme,
    private val onPick: (String) -> Unit,
    private val onMenu: (View) -> Unit,
    private val onMic: () -> Unit
) : LinearLayout(context) {

    private val slots = List(3) { makeSlot() }
    private val dividers = List(2) { makeDivider() }
    private val slotRow = LinearLayout(context).apply {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }

    /** Last words shown, in engine order (best first). */
    var words: List<String> = emptyList()
        private set

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setBackgroundColor(theme.backgroundColor)

        val grid = IconButton(context, IconKind.GRID).apply {
            setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                onMenu(it)
            }
        }
        val mic = IconButton(context, IconKind.MIC).apply {
            setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                onMic()
            }
        }
        slotRow.addView(slots[0], LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))
        slotRow.addView(dividers[0])
        slotRow.addView(slots[1], LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))
        slotRow.addView(dividers[1])
        slotRow.addView(slots[2], LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))

        addView(grid)
        addView(slotRow, LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))
        addView(mic)
        setSuggestions(emptyList())
    }

    private var insetFraction = 0f
    private var textFraction = 0.35f

    /** [split]: the unfolded keyboard, where Gboard gives the best
     *  suggestion a wider middle slot; [insetFraction]: side inset as a
     *  fraction of the width, lining the toolbar up with the split keys. */
    fun configure(split: Boolean, insetFraction: Float) {
        this.insetFraction = insetFraction
        textFraction = if (split) 0.42f else 0.35f
        (slots[1].layoutParams as LayoutParams).weight = if (split) 2.36f else 1f
        requestLayout()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // Square icon buttons as tall as the strip, dividers half its
        // height, words at Gboard's size relative to the strip. The
        // content sits slightly above centre, leaving a sliver of gap
        // above the first key row.
        val inset = (MeasureSpec.getSize(widthMeasureSpec) * insetFraction).toInt()
        val bottom = (MeasureSpec.getSize(heightMeasureSpec) * BOTTOM_GAP_FRACTION).toInt()
        if (paddingLeft != inset || paddingBottom != bottom) setPadding(inset, 0, inset, bottom)
        val h = MeasureSpec.getSize(heightMeasureSpec) - bottom
        if (h > 0) {
            for (i in 0 until childCount) {
                val child = getChildAt(i) as? IconButton ?: continue
                val lp = child.layoutParams as LayoutParams
                lp.width = h
                lp.height = h
            }
            for (d in dividers) (d.layoutParams as LayoutParams).height = (h * 0.5f).toInt()
            slots.forEach { it.setTextSize(TypedValue.COMPLEX_UNIT_PX, h * textFraction) }
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    /** Shows up to three candidates Gboard-style: best in the middle,
     *  second on the left, third on the right. */
    fun setSuggestions(words: List<String>) {
        this.words = words
        val ordered = listOf(words.getOrNull(1), words.getOrNull(0), words.getOrNull(2))
        for ((i, word) in ordered.withIndex()) {
            slots[i].text = word ?: ""
            slots[i].isClickable = word != null
        }
        dividers[0].visibility = if (ordered[0] != null && ordered[1] != null) VISIBLE else INVISIBLE
        dividers[1].visibility = if (ordered[1] != null && ordered[2] != null) VISIBLE else INVISIBLE
    }

    private companion object {
        /** Share of the strip's height left empty under its content. */
        const val BOTTOM_GAP_FRACTION = 0.06f
    }

    private fun makeSlot(): TextView = TextView(context).apply {
        gravity = Gravity.CENTER
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
        setTextColor(theme.keyTextColor)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
        setPadding(dp(6f), 0, dp(6f), 0)
        setOnClickListener { v ->
            val word = (v as TextView).text?.toString().orEmpty()
            if (word.isNotEmpty()) {
                v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                onPick(word)
            }
        }
    }

    private fun makeDivider(): View = View(context).apply {
        setBackgroundColor(theme.dividerColor)
        layoutParams = LayoutParams(dp(1f).coerceAtLeast(1), dp(24f))
    }

    private fun dp(v: Float): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics).toInt()

    /** The grid and microphone buttons, drawn as vectors so they match
     *  Gboard at any size. */
    private inner class IconButton(context: Context, private val kind: IconKind) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val r = RectF()

        init {
            isClickable = true
            layoutParams = LayoutParams(dp(48f), dp(48f))
        }

        override fun onDraw(canvas: Canvas) {
            val s = height.toFloat()
            val cx = width / 2f
            val cy = height / 2f
            when (kind) {
                IconKind.GRID -> {
                    // Four rounded squares, 0.39 × the strip height overall.
                    paint.style = Paint.Style.FILL
                    paint.color = theme.keyTextColor
                    val total = s * 0.42f
                    val gap = total * 0.13f
                    val cell = (total - gap) / 2f
                    val left = cx - total / 2f
                    val top = cy - total / 2f
                    val rad = cell * 0.32f
                    for (row in 0..1) for (col in 0..1) {
                        val x = left + col * (cell + gap)
                        val y = top + row * (cell + gap)
                        r.set(x, y, x + cell, y + cell)
                        canvas.drawRoundRect(r, rad, rad, paint)
                    }
                }
                IconKind.MIC -> {
                    // Round key-coloured button with a microphone glyph.
                    paint.style = Paint.Style.FILL
                    paint.color = if (isPressed) theme.keyPressedColor else theme.keyBackgroundColor
                    canvas.drawCircle(cx, cy, s * 0.384f, paint)
                    paint.color = theme.keyTextColor
                    val capW = s * 0.072f
                    r.set(cx - capW, cy - s * 0.21f, cx + capW, cy + s * 0.04f)
                    canvas.drawRoundRect(r, capW, capW, paint)
                    paint.style = Paint.Style.STROKE
                    paint.strokeWidth = s * 0.032f
                    paint.strokeCap = Paint.Cap.ROUND
                    val arcR = s * 0.13f
                    r.set(cx - arcR, cy - s * 0.11f, cx + arcR, cy - s * 0.11f + 2 * arcR)
                    canvas.drawArc(r, 0f, 180f, false, paint)
                    canvas.drawLine(cx, cy + s * 0.15f, cx, cy + s * 0.21f, paint)
                }
            }
        }

        override fun drawableStateChanged() {
            super.drawableStateChanged()
            invalidate()
        }
    }
}

private enum class IconKind { GRID, MIC }
