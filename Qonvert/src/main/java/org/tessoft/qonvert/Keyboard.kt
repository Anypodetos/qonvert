package org.tessoft.qonvert

/*
Copyright 2022 Anypodetos (Michael Weber)

This file is part of Qonvert.

Qonvert is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

Qonvert is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with Qonvert. If not, see <https://www.gnu.org/licenses/>.

Contact: <https://lemizh.conlang.org/home/contact.php?about=qonvert>
*/

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.SoundEffectConstants
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.core.content.ContextCompat
import java.util.*
import kotlin.concurrent.schedule
import kotlin.math.*

class EditInput(context: Context, attrs: AttributeSet) : androidx.appcompat.widget.AppCompatEditText(context, attrs) {

    var string
        get() = text.toString()
        set(value) { setText(value) }

    private val mainActivity = if (context is MainActivity) context else null

    override fun onSelectionChanged(selStart: Int, selEnd: Int) {
        super.onSelectionChanged(selStart, selEnd)
        mainActivity?.updateKeyboardToCaretPos()
    }
}

@SuppressLint("ClickableViewAccessibility")
class KeyboardView(context: Context, attrs: AttributeSet) : View(context, attrs) {

    private var mainActivity: MainActivity? = if (context is MainActivity) context else null
    private var base = 1
    private var system = NumSystem.STANDARD
    var hidden = false
        private set

    private var touchDown = false
    private var touchX  = -1
    private var touchY  = -1

    private var landscape = true
    private var buttonCols = 12
    private val buttonRows = 4
    private var buttonW = 1f
    private var buttonH = 1f
    private var padding = 0f
    private val buttonRects = Array(buttonCols) { Array(buttonRows) { RectF(0f, 0f, 0f, 0f) } }
    private val buttonTextX = Array(buttonCols) { Array(buttonRows) { arrayOf(0f, 0f) } }
    private val buttonTextY = Array(buttonCols) { Array(buttonRows) { 0f } }
    private val buttonTexts = Array(buttonCols) { Array(buttonRows) { arrayOf(' ', ' ') } }
    private var showPopup = ' '
    private var popupX = 0
    private var popupY = 0
    private var popupLong = 0
    private val popupWidths = Array(buttonCols) { Array(buttonRows) { arrayOf(0, 0) } }
    private var popupRect = RectF(0f, 0f, 0f, 0f)

    private val paintEmpty = 0
    private val paintNormal = 1
    private val paintClose = 2
    private val paintHighlight = 3
    private val highlightColor = ContextCompat.getColor(context, MainActivity.resolveColor(R.attr.colorSecondary))
    private val buttonPaints = List(4) {
        Paint().apply {
            style = Paint.Style.FILL
            color = listOf(0x30808080, 0x60808080, highlightColor and 0xa0ffffff.toInt(), highlightColor)[it]
        }
    }
    private val textColor = ContextCompat.getColor(context, MainActivity.resolveColor(R.attr.editTextColor))
    private val textPaints = listOf(
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 18 * resources.displayMetrics.scaledDensity
            textAlign = Paint.Align.CENTER
            color = textColor
        },
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 13 * resources.displayMetrics.scaledDensity
            textAlign = Paint.Align.RIGHT
            color = textColor and 0xa0ffffff.toInt()
        })
    private val popupPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2 * resources.displayMetrics.scaledDensity
        color = buttonPaints[paintHighlight].color
    }

    init {
        setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                    val i = floor(event.x / buttonW).roundToInt()
                    val j = floor(event.y / buttonH).roundToInt()
                    touchDown = event.actionMasked == MotionEvent.ACTION_DOWN || (touchDown && touchX == i && touchY == j)
                    if (touchDown) { touchX = i; touchY = j }
                        else if (touchY == j - 2) hide()
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> touchDown = false
            }
            invalidate()
            buttonTexts[touchX][touchY][0] == ' ' || hidden /* don't process click if empty button or keyboard was hidden */
        }
        setOnClickListener {
            pressButton(0)
        }
        setOnLongClickListener {
            if (touchDown) {
                playSoundEffect(SoundEffectConstants.CLICK)
                pressButton(1)
            }
            true
        }
    }

    private fun pressButton(long: Int) {
        if (touchX > -1 && touchY > -1) {
            var t = buttonTexts[touchX][touchY][long]
            if (long == 1 && t == ' ') t = buttonTexts[touchX][touchY][0]
            val hasPopup = popupWidths[touchX][touchY][long] > 2
            if (showPopup != ' ') {
                showPopup = ' '
                fillButtons(always = true)
            }
            mainActivity?.editInput?.let {
                when (t) {
                    '⏎' -> {
                        it.onEditorAction(EditorInfo.IME_ACTION_DONE)
                    }
                    '⌫' -> backspace(it, repeat = false)
                    '\u200A' -> backspace(it, repeat = true)
                    ' ', '×' -> Unit
                    else -> if (hasPopup) {
                        showPopup = t.lowercaseChar()
                        popupX = touchX
                        popupY = touchY
                        popupLong = long
                        fillButtons(always = true)
                    } else {
                        val caret = it.selectionStart
                        it.string = it.string.substring(0, caret) + (if (t == '␣') ' ' else t) + it.string.substring(it.selectionEnd)
                        it.setSelection(caret + 1)
                    }
                }
            }
        }
    }

    private fun backspace(edit: EditInput, repeat: Boolean) {
        if (!repeat || touchDown) {
            val caret = edit.selectionStart
            if (caret != edit.selectionEnd) {
                edit.string = edit.string.removeRange(caret, edit.selectionEnd)
                edit.setSelection(caret)
            } else if (caret > 0) {
                val start = caret - if (edit.string[caret - 1].isLowSurrogate()) 2 else 1
                edit.string = edit.string.removeRange(start, caret)
                edit.setSelection(start)
            }
            if (repeat && touchDown && edit.selectionStart > 0) Timer().schedule(100) {
                mainActivity?.runOnUiThread { backspace(edit, repeat = true) }
            }
        }
    }

    fun fillButtons(baseAndSystem: Pair<Int, NumSystem> = Pair(base, system), always: Boolean = false) {
        if (abs(baseAndSystem.first) == base && baseAndSystem.second == system && !always) return
        if (!always) showPopup = ' '
        base = abs(baseAndSystem.first)
        system = baseAndSystem.second
        val oldButtonCols = buttonCols
        buttonCols = if (landscape) 12 else if (system == NumSystem.GREEK && showPopup != '½') 9 else 8
        if (oldButtonCols != buttonCols || always) positionButtons()
        for (i in 0 until buttonCols) for (j in 0 until buttonRows) for (k in 0..1) buttonTexts[i][j][k] = ' '
        for (i in 0 until buttonCols) for (j in 0 until buttonRows) for (k in 0..1) popupWidths[i][j][k] = 0
        for (i in 0..1) textPaints[i].typeface = Typeface.DEFAULT
        if (showPopup == '½') {
            val left = (buttonCols - 8) / 2
            for (i in 0..7) for (j in 0..3) buttonTexts[i + left][j][0] = "½⅓¼⅕⅙⅐⅛⅑ ⅔¾⅖⅚ ⅜⅒   ⅗  ⅝ ∞無 ⅘  ⅞×"[i + 8 * j]
            popupRect = RectF(buttonRects[left][0].left - padding / 2, buttonRects[left][0].top - padding / 2,
                buttonRects[7 + left][3].right + padding / 2, buttonRects[7 + left][3].bottom + padding / 2)
            invalidate()
            return
        }
        val bottomRow = arrayOf("", "")
        when (system) {
            NumSystem.GREEK -> {
                var block = 0
                for ((d, digit) in GREEK_DIGITS.withIndex()) if (digit == '\u0000') block++ else
                    buttonTexts[((d - block) % 3 + 3 * block) % buttonCols][2 - ((d - block) / 3 % 3)][0] = digit
                for (d in 1..9) buttonTexts[(d - 1) % 3 + 3][(9 - d) / 3][1] = d.digitToChar()
                bottomRow[0] = if (landscape) "○.˙͵∞無␣" else "○.˙͵"
                bottomRow[1] = if (landscape) "0½ ʹ    " else "0½ ʹ␣"
                for (i in 0..1) textPaints[i].typeface = Typeface.SERIF
            }
            NumSystem.ROMAN -> {
                for ((d, digit) in ROMAN_DIGITS.keys.withIndex()) buttonTexts[d % 4 + if (landscape) 6 else 3][(11 - d) / 4][0] = digit
                if (APOSTROPHUS_OPTIONS[MainActivity.apostrophus][1] == '+' || APOSTROPHUS_OPTIONS[MainActivity.apostrophus][4] == '+')
                    buttonTexts[if (landscape) 8 else 5][1][0] = 'ↀ'
                buttonTexts[if (landscape) 9 else 6][0][0] = '|'
                buttonTexts[if (landscape) 9 else 6][1][1] = 'ↄ'
                for (j in 0..2) for (k in 0..1) buttonTexts[if (landscape) 11 - k else 7][j][if (landscape) 0 else k] = "∷⁙s·∶∴"[2 - j + 3 * k]
                for (d in 1..9) buttonTexts[(d - 1) % 3 + 3][(9 - d) / 3][if (landscape) 0 else 1] = d.digitToChar()
                bottomRow[0] = if (landscape) "0.˙n∞無␣" else "n.˙"
                bottomRow[1] = if (landscape) " ½      " else "0½ ␣"
                for (i in 0..1) textPaints[i].typeface = Typeface.SERIF
            }
            else -> {
                val bijectiveAdd = if (system == NumSystem.BIJECTIVE_A) 9 else 0
                val digitRange = digitRange(base, system, bijectiveAdd)
                if (system != NumSystem.BIJECTIVE_A) for (d in digitRange.first.coerceAtLeast(1)..digitRange.last.coerceAtMost(9))
                    buttonTexts[(d - 1) % 3 + buttonCols - 5][(9 - d) / 3][0] = d.digitToChar()
                for (d in digitRange.first.coerceAtLeast(10)..digitRange.last.coerceAtMost(39)) {
                    val i = (d - bijectiveAdd + 3 * buttonCols - 16) / 3 % buttonCols + if (landscape && d - bijectiveAdd >= 52) 3 else 0
                    val j = (d + 2) % 3
                    val k = (d - bijectiveAdd) / (3 * buttonCols + 1)
                    buttonTexts[i][j][k] = DIGITS[d]
                    popupWidths[i][j][k] = if (d < 40) (digitRange.last - d) / 30 + 2 else 0
                }
                for (d in digitRange.first..-1) {
                    val i = (d - 3 * buttonCols + 57) / 3 % buttonCols
                    val j = (d + 18) % 3
                    buttonTexts[i][j][if (i == 0 || buttonTexts[i][j][0] == ' ') 0 else 1] = (d + 123).toChar()
                }
                if (!landscape && system == NumSystem.BALANCED) for (d in 16..digitRange.last) buttonTexts[3][d - 16][1] = (d + 87).toChar()
                bottomRow[0] = (if (landscape) "°'\"␣0.˙" else "0.˙").let {
                    if (digitRange.first <= 0) it else it.replace('0', '∞')
                }
                bottomRow[1] = if (landscape) "    ∞½  ".let {
                    if (digitRange.first <= 0) it else it.replace('∞', ' ')
                } else " ½ ␣"
            }
        }
        popupWidths[if (!landscape || system in setOf(NumSystem.GREEK, NumSystem.ROMAN)) 4 else 8][3][1] = 24
        for (i in 0..2) {
            for (j in 0..3) buttonTexts[i][j][if (buttonTexts[i][j][0] == ' ') 0 else 1] = "[{;,@#_/\$%&-"[4 * i + j]
            if (!landscape || system in setOf(NumSystem.GREEK, NumSystem.ROMAN)) buttonTexts[i][3][1] = "°'\""[i]
        }
        for (k in 0..1) {
            bottomRow[k] += listOf("⏎⌫", "\u200A")[k]
            for (i in 3 until buttonCols) buttonTexts[i][3][k] = bottomRow[k].getOrNull(i - 3) ?: '☹'
        }
        if (showPopup !in " ½") {
            val shiftLeft = (popupX + popupWidths[popupX][popupY][popupLong] - buttonCols).coerceAtLeast(0)
            popupRect = buttonRects[popupX - shiftLeft][popupY].let {
                RectF(it.left - padding / 2, it.top - padding / 2,
                    buttonRects[popupX - shiftLeft + popupWidths[popupX][popupY][popupLong] - 1][popupY].right + padding / 2, it.bottom + padding / 2)
            }
            for (i in 0 until popupWidths[popupX][popupY][popupLong]) {
                buttonTexts[popupX - shiftLeft + i][popupY][0] = DIGITS.getOrNull(DIGITS.indexOf(showPopup) + 30 * i) ?: '☹'
                buttonTexts[popupX - shiftLeft + i][popupY][1] = ' '
            }
            buttonTexts[popupX - shiftLeft + popupWidths[popupX][popupY][popupLong] - 1][popupY][0] = '×'
            for (i in 0 until popupWidths[popupX][popupY][popupLong]) for (k in 0..1) popupWidths[popupX - shiftLeft + i][popupY][k] = 0
        }
        if (!MainActivity.lowerDigits) for (i in 0 until buttonCols) for (j in 0 until buttonRows) for (k in 0..1)
            buttonTexts[i][j][k] = buttonTexts[i][j][k].uppercaseChar()
        invalidate()
    }

    private fun positionButtons() {
        buttonW = width.toFloat() / buttonCols
        buttonH = height.toFloat() / buttonRows
        padding = height / 50f
        for (i in 0 until buttonCols) for (j in 0 until buttonRows) {
            buttonRects[i][j] = RectF(i * buttonW + padding, j * buttonH + padding, (i + 1) * buttonW - padding, (j + 1) * buttonH - padding)
            for (k in 0..1) {
                buttonTextX[i][j][k] = if (k == 0) buttonRects[i][j].centerX() - (if (system == NumSystem.GREEK && showPopup != '½') padding else 0f)
                    else buttonRects[i][j].right - padding
                buttonTextY[i][j]    = buttonRects[i][j].centerY() + padding
            }
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldW: Int, oldH: Int) {
        super.onSizeChanged(w, h, oldW, oldH)
        landscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        fillButtons(always = true)
    }

    override fun onDraw(canvas: Canvas?) {
        super.onDraw(canvas)
        canvas?.let {
            for (i in 0 until buttonCols) for (j in 0 until buttonRows) {
                it.drawRoundRect(buttonRects[i][j], padding, padding, buttonPaints[when {
                    buttonTexts[i][j][0] == ' ' -> paintEmpty
                    touchDown && touchX == i && touchY == j -> paintHighlight
                    buttonTexts[i][j][0] == '×' -> paintClose
                    else -> paintNormal
                }])
                for (k in 0..1) if (buttonTexts[i][j][k] != ' ')
                    it.drawText(buttonTexts[i][j][k].toString(), buttonTextX[i][j][k], buttonTextY[i][j], textPaints[k])
            }
            if (showPopup != ' ') it.drawRoundRect(popupRect, padding, padding, popupPaint)
        }
    }

    fun show() {
        hidden = false
        fillButtons(always = true)
        with(animate()) {
            duration = 150
            withStartAction { visibility = VISIBLE }
            translationY(0f)
            alpha(1f)
        }
    }

    fun hide() {
        hidden = true
        with(animate()) {
            duration = 150
            withEndAction { visibility = GONE }
            translationY(layoutParams.height.toFloat() / 2)
            alpha(0f)
        }
        showPopup = ' '
    }

    fun pause() {
        showPopup = ' '
    }
}
