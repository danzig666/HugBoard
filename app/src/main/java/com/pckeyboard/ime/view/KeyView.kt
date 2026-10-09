package com.pckeyboard.ime.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.CornerPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Build
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import com.pckeyboard.ime.layout.GboardLayout
import com.pckeyboard.ime.model.Key
import com.pckeyboard.ime.model.KeyType
import com.pckeyboard.ime.model.ModifierState
import com.pckeyboard.ime.settings.KeyboardPrefs
import com.pckeyboard.ime.theme.KeyboardTheme

/**
 * Renders a single key. Handles its own touch (pressed) feedback so the
 * parent row only has to do hit-testing.
 */
class KeyView(
    context: Context,
    val key: Key,
    var theme: KeyboardTheme,
    var modifiers: ModifierState
) : View(context) {

    var isDown: Boolean = false
        set(value) { field = value; invalidate() }

    var listener: Listener? = null

    /** Gboard look: see [drawGboard]. Gaps are in pixels, between keys. */
    var gboard: Boolean = false
    var gboardHGapPx: Float = 0f
    var gboardVGapPx: Float = 0f
    /** Split (unfolded) keyboard: Gboard draws the corner hints larger. */
    var gboardSplit: Boolean = false

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }
    private val secondaryPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif", Typeface.NORMAL)
    }
    private val rect = RectF()

    init {
        isClickable = true
        isFocusable = true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            tooltipText = key.label
        }
    }

    override fun onDraw(canvas: Canvas) {
        if (gboard) {
            drawGboard(canvas)
            return
        }
        val pad = dp(theme.keySpacingDp / 2f)
        rect.set(pad, pad, width - pad, height - pad)
        val radius = dp(theme.keyCornerRadiusDp.toFloat())
        val modState = modifierState()

        bgPaint.color = backgroundColorForState(modState)
        bgPaint.style = Paint.Style.FILL
        canvas.drawRoundRect(rect, radius, radius, bgPaint)

        // ONCE → bold accent border so the user can see a modifier is armed
        // for the next keypress without being confused with the LOCKED state.
        if (modState == ModifierState.State.ONCE) {
            val border = dp(3f)
            val r = RectF(rect)
            r.inset(border / 2f, border / 2f)
            bgPaint.color = theme.accentColor
            bgPaint.style = Paint.Style.STROKE
            bgPaint.strokeWidth = border
            canvas.drawRoundRect(r, radius, radius, bgPaint)
            bgPaint.style = Paint.Style.FILL
        }

        val isShifted = modifiers.isShiftActive()
        val isAltActive = modifiers.isAltActive()
        val altChar = key.altLabel?.takeIf {
            isAltActive && (key.type == KeyType.LETTER || key.type == KeyType.CHAR)
        }
        val mainText = when {
            altChar != null -> if (isShifted) altChar.uppercase() else altChar
            key.type == KeyType.LETTER && isShifted -> key.label.uppercase()
            key.type == KeyType.LETTER -> key.label
            key.type == KeyType.CHAR && isShifted && key.shiftLabel != null -> key.shiftLabel
            else -> key.label
        }

        val mainSize = preferredTextSize()
        textPaint.color = textColorForState(modState)
        textPaint.textSize = mainSize
        val cy = rect.centerY() - (textPaint.descent() + textPaint.ascent()) / 2
        canvas.drawText(mainText, rect.centerX(), cy, textPaint)

        // LOCKED → underline under the label to mark "stays on until tapped
        // again", visually distinct from the ONCE border.
        if (modState == ModifierState.State.LOCKED) {
            val lineW = mainSize * 0.9f
            val lineY = cy + textPaint.descent() + dp(2f)
            bgPaint.color = textColorForState(modState)
            canvas.drawRoundRect(
                rect.centerX() - lineW / 2f, lineY,
                rect.centerX() + lineW / 2f, lineY + dp(2.5f),
                dp(1.5f), dp(1.5f),
                bgPaint
            )
        }

        // ONCE → small accent dot in the top-right corner so even without the
        // border (e.g. on themes with similar accent / modifier colours) the
        // armed state is obvious.
        if (modState == ModifierState.State.ONCE) {
            val dotR = dp(3f)
            bgPaint.color = theme.accentColor
            canvas.drawCircle(rect.right - dp(8f), rect.top + dp(8f), dotR, bgPaint)
        }

        // secondary (shifted) hint for char keys (e.g. "!" above "1")
        if (key.type == KeyType.CHAR && !isShifted && key.shiftLabel != null) {
            secondaryPaint.color = theme.secondaryTextColor
            secondaryPaint.textSize = mainSize * 0.55f
            canvas.drawText(
                key.shiftLabel,
                rect.centerX() + rect.width() * 0.28f,
                rect.top + secondaryPaint.textSize * 1.1f,
                secondaryPaint
            )
        }

        // AltGr hint — drawn at bottom-right of letter / char keys so the
        // user always knows which key produces which AltGr glyph (held
        // Alt commits it, long-press also exposes it). Suppressed while
        // Alt is currently active because the altLabel is already
        // showing as the main text of the key.
        if ((key.type == KeyType.LETTER || key.type == KeyType.CHAR)
            && !isAltActive
            && key.altLabel != null
        ) {
            secondaryPaint.color = theme.secondaryTextColor
            secondaryPaint.textSize = mainSize * 0.5f
            canvas.drawText(
                key.altLabel,
                rect.centerX() + rect.width() * 0.28f,
                rect.bottom - dp(6f),
                secondaryPaint
            )
        }
    }

    // --- Gboard rendering ---------------------------------------------------
    //
    // Every size below is a fraction of the key's drawn height, measured off
    // Gboard screenshots, so the proportions hold at any keyboard height.

    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val iconPath = Path()
    private val textBounds = Rect()
    private val iconRect = RectF()

    private fun drawGboard(canvas: Canvas) {
        val w = width.toFloat()
        val weight = key.widthWeight.takeIf { it > 0f } ?: 1f
        val left = w * key.gapBefore / weight
        val right = w - w * key.gapAfter / weight
        rect.set(
            left + gboardHGapPx / 2f, gboardVGapPx / 2f,
            right - gboardHGapPx / 2f, height - gboardVGapPx / 2f
        )
        if (rect.width() <= 0f || rect.height() <= 0f) return
        val h = rect.height()
        val pill = key.type == KeyType.SYMBOL_SWITCH || key.type == KeyType.ENTER
        val radius = if (pill) h / 2f else h * 0.17f
        val modState = modifierState()

        val isModifierKey = gboardIsModifierKey()
        bgPaint.style = Paint.Style.FILL
        bgPaint.color = when {
            isDown -> theme.keyPressedColor
            isModifierKey -> theme.modifierKeyColor
            else -> theme.keyBackgroundColor
        }
        canvas.drawRoundRect(rect, radius, radius, bgPaint)

        val fg = if (isModifierKey) theme.modifierTextColor else theme.keyTextColor
        val cx = rect.centerX()
        val cy = rect.centerY()
        when (key.type) {
            KeyType.SHIFT -> drawShiftIcon(canvas, cx, cy, h, fg, modState)
            KeyType.BACKSPACE -> drawBackspaceIcon(canvas, cx, cy, h, fg)
            KeyType.ENTER -> drawEnterIcon(canvas, cx, cy, h, fg)
            KeyType.EMOJI -> drawEmojiIcon(canvas, cx, cy, h, fg)
            KeyType.SPACE -> drawCentredText(canvas, key.label, h * 0.335f, fg, GBOARD_LETTER_FACE)
            KeyType.LETTER, KeyType.CHAR -> drawGboardChar(canvas, h, fg)
            else -> drawCentredText(canvas, key.label, h * 0.355f, fg, GBOARD_LABEL_FACE)
        }
    }

    /** Gboard paints Shift, ⌫, ?123, ⏎ and the bottom-row , . in the
     *  tinted modifier colour; letters, Space and the emoji key stay plain. */
    private fun gboardIsModifierKey(): Boolean = when (key.type) {
        KeyType.LETTER, KeyType.SPACE, KeyType.EMOJI, KeyType.LANGUAGE_SWITCH -> false
        KeyType.CHAR -> key.label == "," || key.label == "."
        else -> true
    }

    private fun drawGboardChar(canvas: Canvas, h: Float, fg: Int) {
        val isShifted = modifiers.isShiftActive()
        val altChar = key.altLabel?.takeIf { modifiers.isAltActive() }
        val text = when {
            altChar != null -> altChar
            key.type == KeyType.LETTER && isShifted -> key.label.uppercase()
            key.type == KeyType.CHAR && isShifted && key.shiftLabel != null -> key.shiftLabel
            else -> key.label
        }
        textPaint.typeface = GBOARD_LETTER_FACE
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.color = fg
        if (text == "," || text == ".") {
            // Bottom-row punctuation: smaller and sitting low, like Gboard.
            textPaint.textSize = h * 0.47f
            canvas.drawText(text, rect.centerX(), rect.centerY() + h * 0.18f, textPaint)
        } else {
            textPaint.textSize = h * 0.6f
            // Gboard centres the x-height in the key, so lowercase letters
            // sit optically centred and capitals share one baseline.
            textPaint.getTextBounds("x", 0, 1, textBounds)
            val baseline = rect.centerY() + textBounds.height() / 2f + h * 0.015f
            canvas.drawText(text, rect.centerX(), baseline, textPaint)
        }

        val hint = key.hint ?: return
        secondaryPaint.color = theme.secondaryTextColor
        secondaryPaint.textAlign = Paint.Align.LEFT
        secondaryPaint.textSize = h * (if (gboardSplit) 0.3f else 0.26f)
        val inset = h * (if (gboardSplit) 0.115f else 0.075f)
        // Align the glyph's ink (not its advance) to the corner inset.
        secondaryPaint.getTextBounds("1", 0, 1, textBounds)
        val capTop = textBounds.top
        secondaryPaint.getTextBounds(hint, 0, hint.length, textBounds)
        canvas.drawText(
            hint,
            rect.right - inset - textBounds.right,
            rect.top + inset * 1.1f - capTop,
            secondaryPaint
        )
        secondaryPaint.textAlign = Paint.Align.CENTER
    }

    private fun drawCentredText(canvas: Canvas, text: String, size: Float, color: Int, face: Typeface) {
        if (text.isEmpty()) return
        textPaint.typeface = face
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.color = color
        textPaint.textSize = size
        textPaint.getTextBounds("H", 0, 1, textBounds)
        canvas.drawText(text, rect.centerX(), rect.centerY() + textBounds.height() / 2f, textPaint)
    }

    private fun strokeIconPaint(color: Int, h: Float) {
        iconPaint.reset()
        iconPaint.isAntiAlias = true
        iconPaint.color = color
        iconPaint.style = Paint.Style.STROKE
        iconPaint.strokeWidth = h * 0.048f
        iconPaint.strokeJoin = Paint.Join.ROUND
        iconPaint.strokeCap = Paint.Cap.ROUND
        iconPaint.pathEffect = CornerPathEffect(h * 0.03f)
    }

    /** Outlined arrow; filled when Shift is armed, plus a bar under it
     *  when Caps is locked. */
    private fun drawShiftIcon(
        canvas: Canvas, cx: Float, cy: Float, h: Float, color: Int,
        state: ModifierState.State
    ) {
        strokeIconPaint(color, h)
        val hw = h * 0.244f      // half width of the arrow head
        val sw = h * 0.135f      // half width of the stem
        val top = cy - h * 0.231f
        val neck = cy + h * 0.005f
        val bottom = cy + h * 0.231f
        iconPath.reset()
        iconPath.moveTo(cx, top)
        iconPath.lineTo(cx + hw, neck)
        iconPath.lineTo(cx + sw, neck)
        iconPath.lineTo(cx + sw, bottom)
        iconPath.lineTo(cx - sw, bottom)
        iconPath.lineTo(cx - sw, neck)
        iconPath.lineTo(cx - hw, neck)
        iconPath.close()
        if (state != ModifierState.State.OFF || modifiers.capsLock) {
            iconPaint.style = Paint.Style.FILL_AND_STROKE
        }
        canvas.drawPath(iconPath, iconPaint)
        if (state == ModifierState.State.LOCKED || modifiers.capsLock) {
            iconPaint.style = Paint.Style.FILL_AND_STROKE
            canvas.drawLine(cx - sw, bottom + h * 0.09f, cx + sw, bottom + h * 0.09f, iconPaint)
        }
    }

    private fun drawBackspaceIcon(canvas: Canvas, cx: Float, cy: Float, h: Float, color: Int) {
        strokeIconPaint(color, h)
        val ic = cx - h * 0.025f
        val l = ic - h * 0.285f
        val r = ic + h * 0.285f
        val notch = l + h * 0.19f
        val t = cy - h * 0.227f
        val b = cy + h * 0.227f
        iconPath.reset()
        iconPath.moveTo(l, cy)
        iconPath.lineTo(notch, t)
        iconPath.lineTo(r, t)
        iconPath.lineTo(r, b)
        iconPath.lineTo(notch, b)
        iconPath.close()
        canvas.drawPath(iconPath, iconPaint)
        iconPaint.pathEffect = null
        val xc = cx + h * 0.037f
        val a = h * 0.085f
        canvas.drawLine(xc - a, cy - a, xc + a, cy + a, iconPaint)
        canvas.drawLine(xc - a, cy + a, xc + a, cy - a, iconPaint)
    }

    /** ↵ by default; magnifier / paper plane / tick / arrow when the editor
     *  declares Search / Send / Done / Next-Go (set by the service). */
    private fun drawEnterIcon(canvas: Canvas, cx: Float, cy: Float, h: Float, color: Int) {
        strokeIconPaint(color, h)
        iconPaint.pathEffect = null
        iconPath.reset()
        when (key.icon) {
            GboardLayout.ICON_SEARCH -> {
                val r = h * 0.13f
                val ox = cx - h * 0.05f
                val oy = cy - h * 0.05f
                canvas.drawCircle(ox, oy, r, iconPaint)
                canvas.drawLine(ox + r * 0.75f, oy + r * 0.75f, cx + h * 0.2f, cy + h * 0.2f, iconPaint)
            }
            GboardLayout.ICON_SEND -> {
                iconPaint.pathEffect = CornerPathEffect(h * 0.02f)
                iconPath.moveTo(cx - h * 0.22f, cy - h * 0.22f)
                iconPath.lineTo(cx + h * 0.25f, cy)
                iconPath.lineTo(cx - h * 0.22f, cy + h * 0.22f)
                iconPath.lineTo(cx - h * 0.15f, cy)
                iconPath.close()
                iconPaint.style = Paint.Style.FILL_AND_STROKE
                canvas.drawPath(iconPath, iconPaint)
            }
            GboardLayout.ICON_DONE -> {
                iconPath.moveTo(cx - h * 0.2f, cy + h * 0.01f)
                iconPath.lineTo(cx - h * 0.06f, cy + h * 0.15f)
                iconPath.lineTo(cx + h * 0.22f, cy - h * 0.14f)
                canvas.drawPath(iconPath, iconPaint)
            }
            GboardLayout.ICON_NEXT -> {
                val l = cx - h * 0.22f
                val r = cx + h * 0.22f
                canvas.drawLine(l, cy, r, cy, iconPaint)
                canvas.drawLine(r, cy, r - h * 0.12f, cy - h * 0.12f, iconPaint)
                canvas.drawLine(r, cy, r - h * 0.12f, cy + h * 0.12f, iconPaint)
            }
            else -> {
                val l = cx - h * 0.232f
                val r = cx + h * 0.232f
                val y = cy + h * 0.038f
                val head = h * 0.11f
                iconPath.moveTo(r, cy - h * 0.128f)
                iconPath.lineTo(r, y)
                iconPath.lineTo(l, y)
                canvas.drawPath(iconPath, iconPaint)
                canvas.drawLine(l, y, l + head, y - head, iconPaint)
                canvas.drawLine(l, y, l + head, y + head, iconPaint)
            }
        }
    }

    private fun drawEmojiIcon(canvas: Canvas, cx: Float, cy: Float, h: Float, color: Int) {
        strokeIconPaint(color, h)
        iconPaint.pathEffect = null
        val r = h * 0.212f
        canvas.drawCircle(cx, cy, r, iconPaint)
        iconPaint.style = Paint.Style.FILL
        val eyeR = h * 0.032f
        canvas.drawCircle(cx - r * 0.36f, cy - r * 0.25f, eyeR, iconPaint)
        canvas.drawCircle(cx + r * 0.36f, cy - r * 0.25f, eyeR, iconPaint)
        // Open smile: a filled half-disc, like Gboard's emoji key.
        val m = r * 0.5f
        iconRect.set(cx - m, cy + r * 0.05f - m, cx + m, cy + r * 0.05f + m)
        canvas.drawArc(iconRect, 0f, 180f, true, iconPaint)
    }

    private fun modifierState(): ModifierState.State = when (key.type) {
        KeyType.SHIFT -> modifiers.shift
        KeyType.CTRL -> modifiers.ctrl
        KeyType.ALT -> modifiers.alt
        KeyType.META -> modifiers.meta
        KeyType.FN -> modifiers.fn
        KeyType.CAPS_LOCK -> if (modifiers.capsLock) ModifierState.State.LOCKED else ModifierState.State.OFF
        else -> ModifierState.State.OFF
    }

    private fun backgroundColorForState(modState: ModifierState.State): Int {
        if (isDown) return theme.keyPressedColor
        if (modState == ModifierState.State.LOCKED) return theme.accentColor
        return when (key.type) {
            KeyType.SPACE, KeyType.ENTER, KeyType.BACKSPACE -> theme.accentColor
            KeyType.LETTER, KeyType.CHAR -> theme.keyBackgroundColor
            else -> theme.modifierKeyColor
        }
    }

    private fun textColorForState(modState: ModifierState.State): Int {
        if (modState == ModifierState.State.LOCKED) return theme.accentTextColor
        return when (key.type) {
            KeyType.SPACE, KeyType.ENTER, KeyType.BACKSPACE -> theme.accentTextColor
            KeyType.LETTER, KeyType.CHAR -> theme.keyTextColor
            else -> theme.modifierTextColor
        }
    }

    private fun preferredTextSize(): Float {
        val sz = when (key.type) {
            KeyType.SPACE -> 14f
            KeyType.FN -> 12f
            KeyType.LETTER, KeyType.CHAR -> 18f
            else -> 13f
        }
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sz, resources.displayMetrics)
    }

    private fun dp(v: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics)

    private var popupActive: Boolean = false
    private var longPressRunnable: Runnable? = null
    private val prefs by lazy { KeyboardPrefs(context) }

    private fun canLongPress(): Boolean =
        key.popupChars != null || key.hint != null || key.type == KeyType.SPACE ||
            key.type == KeyType.EMOJI ||
            key.type == KeyType.LANGUAGE_SWITCH ||
            key.code == Key.CODE_RIGHT_OF_SPACE

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                isDown = true
                listener?.onKeyDown(this)
                if (canLongPress()) scheduleLongPress()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (popupActive) {
                    listener?.onKeyPopupMove(this, event.rawX, event.rawY)
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                isDown = false
                cancelLongPressTimer()
                if (popupActive) {
                    popupActive = false
                    listener?.onKeyPopupRelease(this)
                    return true
                }
                listener?.onKeyUp(this)
                performClick()
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                isDown = false
                cancelLongPressTimer()
                if (popupActive) {
                    popupActive = false
                    listener?.onKeyPopupCancel(this)
                } else {
                    listener?.onKeyCancel(this)
                }
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun scheduleLongPress() {
        val r = Runnable {
            if (isDown && canLongPress()) {
                popupActive = true
                listener?.onKeyLongPress(this)
            }
        }
        longPressRunnable = r
        postDelayed(r, prefs.longPressDelayMs.toLong())
    }

    private fun cancelLongPressTimer() {
        longPressRunnable?.let { removeCallbacks(it) }
        longPressRunnable = null
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    companion object {
        private val GBOARD_LETTER_FACE: Typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        private val GBOARD_LABEL_FACE: Typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }

    interface Listener {
        fun onKeyDown(view: KeyView)
        fun onKeyUp(view: KeyView)
        fun onKeyCancel(view: KeyView)
        fun onKeyLongPress(view: KeyView)
        fun onKeyPopupMove(view: KeyView, rawX: Float, rawY: Float)
        fun onKeyPopupRelease(view: KeyView)
        fun onKeyPopupCancel(view: KeyView)
    }
}
