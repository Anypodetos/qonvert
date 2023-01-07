package org.tessoft.qonvert

/*
Copyright 2022, 2023 Anypodetos (Michael Weber)

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

    private var buttonCols = 12
    private val buttonRows = 4
    private var buttonW = 1f
    private var buttonH = 1f
    private var padding = 0f
    private val buttonRects = Array(buttonCols) { Array(buttonRows) { RectF(0f, 0f, 0f, 0f) } }
    private val buttonTextX = Array(buttonCols) { Array(buttonRows) { arrayOf(0f, 0f) } }
    private val buttonTextY = Array(buttonCols) { Array(buttonRows) { arrayOf(0f, 0f) } }
    private val buttonTexts = Array(buttonCols) { Array(buttonRows) { arrayOf(' ', ' ') } }
    private var showPopup = ' '
    private var popupX = 0
    private var popupY = 0
    private var popupLong = 0
    private val popupWidths = Array(buttonCols) { Array(buttonRows) { arrayOf(0, 0) } }
    private val popupValues = Array(buttonCols) { 0 }
    private var popupRect = RectF(0f, 0f, 0f, 0f)

    private val paintEmpty = 0
    private val paintNormal = 1
    private val paintClose = 2
    private val paintHighlight = 3
    private val highlightColor = resolveColor(context, R.attr.colorSecondary)
    private val buttonPaints = List(4) {
        Paint().apply {
            style = Paint.Style.FILL
            color = listOf(0x30808080, 0x60808080, highlightColor and 0xa0ffffff.toInt(), highlightColor)[it]
        }
    }
    private val textColor = resolveColor(context, R.attr.editTextColor)
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
        },
        Paint(Paint.ANTI_ALIAS_FLAG).apply { /* Enter font */
            textSize = 15 * resources.displayMetrics.scaledDensity
            textAlign = Paint.Align.RIGHT
            color = textColor
        })
    private val decimalPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 11 * resources.displayMetrics.scaledDensity
        textAlign = Paint.Align.RIGHT
        color = textColor
    }
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
        val landscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE ||
            mainActivity?.keyboardId in setOf(KeyboardId.DIGITS_LEFT_TABLET, KeyboardId.DIGITS_RIGHT_TABLET)
        val oldButtonCols = buttonCols
        buttonCols = if (landscape) 12 else if (system == NumSystem.GREEK && showPopup != '½') 9 else 8
        if (oldButtonCols != buttonCols || always) positionButtons()
        for (i in 0 until buttonCols) for (j in 0 until buttonRows) for (k in 0..1) buttonTexts[i][j][k] = ' '
        for (i in 0 until buttonCols) for (j in 0 until buttonRows) for (k in 0..1) popupWidths[i][j][k] = 0
        for (i in 0 until buttonCols) popupValues[i] = 0
        for (i in 0..1) textPaints[i].typeface = Typeface.DEFAULT
        if (showPopup == '½') {
            val left = (buttonCols - 8) / 2
            for (i in 0..7) for (j in 0..3) buttonTexts[i + left][j][0] = "½⅓¼⅕⅙⅐⅛⅑ ⅔¾⅖⅚ ⅜⅒   ⅗  ⅝ ∞無 ⅘  ⅞×"[i + 8 * j]
            popupRect = RectF(buttonRects[left][0].left - padding / 2, buttonRects[left][0].top - padding / 2,
                buttonRects[7 + left][3].right + padding / 2, buttonRects[7 + left][3].bottom + padding / 2)
            invalidate()
            return
        }
        val digitsLeft = mainActivity?.keyboardId in setOf(KeyboardId.DIGITS_LEFT, KeyboardId.DIGITS_LEFT_TABLET)
        val bijectiveAdd = if (system == NumSystem.BIJECTIVE_A) 9 else 0
        val bottomRow = arrayOf("", "")
        when (system) {
            NumSystem.GREEK -> {
                var block = 0
                for ((d, digit) in GREEK_DIGITS.withIndex()) if (digit == '\u0000') block++ else
                    buttonTexts[((d - block) % 3 + 3 * block - if (digitsLeft) 3 else 0) % buttonCols][2 - ((d - block) / 3 % 3)][0] = digit
                for (d in 1..9) buttonTexts[(d - 1) % 3 + if (digitsLeft) 0 else 3][(9 - d) / 3][1] = d.digitToChar()
                bottomRow[0] = if (digitsLeft) ("○.˙͵-␣" + (if (landscape) "°'\"" else "") + ",/") else (",/-○.˙͵" + (if (landscape) "∞無␣" else "") + "⏎")
                bottomRow[1] = if (digitsLeft) ("0½ ʹ" + (if (landscape) "      " else "°'\"") + "⏎") else ("°'\"0½ ʹ" + (if (landscape) "    " else "␣"))
                for (i in 0..1) textPaints[i].typeface = Typeface.SERIF
            }
            NumSystem.ROMAN -> {
                val romanStart = if (digitsLeft) 0 else (if (landscape) 6 else 3)
                for ((d, digit) in ROMAN_DIGITS.keys.withIndex()) buttonTexts[d % 4 + romanStart][(11 - d) / 4][0] = digit
                if (APOSTROPHUS_OPTIONS[MainActivity.apostrophus][1] == '+' || APOSTROPHUS_OPTIONS[MainActivity.apostrophus][4] == '+')
                    buttonTexts[romanStart + 2][1][0] = 'ↀ'
                buttonTexts[romanStart + 3][0][0] = '|'
                buttonTexts[romanStart + 3][1][1] = 'ↄ'
                for (j in 0..2) for (k in 0..1) buttonTexts[(if (landscape) 11 - k else 7) - if (digitsLeft) 3 else 0][j][if (landscape) 0 else k] =
                    "∷⁙s·∶∴"[2 - j + 3 * k]
                for (d in 1..9) buttonTexts[(d - 1) % 3 + if (digitsLeft) (if (landscape) 4 else 0) else 3][(9 - d) / 3][if (landscape) 0 else 1] =
                    d.digitToChar()
                bottomRow[0] = if (digitsLeft) ("n" + if (landscape) "°'\"0" else "") + ".˙-␣,/" else (",/-" + (if (landscape) "0.˙n∞無␣" else "n.˙") + "⏎")
                bottomRow[1] = if (digitsLeft) (if (landscape) "∞    ½    " else "0½ °'\"") + "⏎" else (if (landscape) "°'\" ½      " else "°'\"0½ ␣")
                for (i in 0..1) textPaints[i].typeface = Typeface.SERIF
            }
            else -> {
                val digitsStart = if (digitsLeft) 0 else buttonCols - 5
                val digitRange = digitRange(base, system, bijectiveAdd)
                if (system != NumSystem.BIJECTIVE_A) for (d in digitRange.first.coerceAtLeast(1)..digitRange.last.coerceAtMost(9))
                    buttonTexts[(d - 1) % 3 + digitsStart][(9 - d) / 3][0] = d.digitToChar()
                for (d in digitRange.first.coerceAtLeast(10)..digitRange.last.coerceAtMost(39)) {
                    val i = (d - bijectiveAdd - 1 + 3 * digitsStart) / 3 % buttonCols
                    val j = (d + 2) % 3
                    val k = if (buttonTexts[i][j][0] == ' ') 0 else 1
                    buttonTexts[i][j][k] = DIGITS[d]
                    popupWidths[i][j][k] = (digitRange.last - d) / 30 + 2
                }
                for (d in digitRange.first..-1) {
                    val i = (d + 3 * ((if (digitsLeft) 21 else 19) - buttonCols)) / 3 % buttonCols
                    val j = (d + 18) % 3
                    buttonTexts[i][j][if (buttonTexts[i][j][0] == ' ' || (!digitsLeft && i == 0)) 0 else 1] = (d + 123).toChar()
                }
                if (system == NumSystem.BALANCED && !digitsLeft && !landscape) for (d in 16..digitRange.last) buttonTexts[3][d - 16][1] = (d + 87).toChar()
                bottomRow[0] = (if (digitsLeft) ("0.˙-␣" + (if (landscape) " °'\"" else "") +  ",/") else (",/-" + (if (landscape) "°'\"␣" else "") + "0.˙⏎")).
                        let {
                    if (digitRange.first <= 0) it else it.replaceFirst('0', '∞')
                }
                bottomRow[1] = if (landscape) (if (digitsLeft) "∞½        ⏎" else "       ∞½  ").let {
                    if (digitRange.first <= 0) it else it.replaceFirst('∞', ' ')
                } else (if (digitsLeft) " ½ °'\"⏎" else "°'\" ½ ␣")
            }
        }
        popupWidths[if (digitsLeft) (if (!landscape || system != NumSystem.ROMAN) 1 else 5) else
            (if (!landscape || system in setOf(NumSystem.GREEK, NumSystem.ROMAN)) 4 else 8)][3][1] = 24 /* fraction chars popup */
        val brackets = "[{;@#_\$%&"
        val bracketsStart = if (digitsLeft) buttonCols - 3 else 0
        for (i in 0..2) for (j in 0..2) buttonTexts[i + bracketsStart][j][if (buttonTexts[i + bracketsStart][j][0] == ' ') 0 else 1] = brackets[3 * i + j]
        for (k in 0..1) {
            bottomRow[k] += if (k == 0) "⌫" else "\u200A"
            for (i in 0 until buttonCols) buttonTexts[i][3][k] = bottomRow[k].getOrNull(i) ?: '☹'
        }
        if (showPopup !in " ½") {
            val leftEdge = popupX - (popupX + popupWidths[popupX][popupY][popupLong] - buttonCols).coerceAtLeast(0)
            popupRect = buttonRects[leftEdge][popupY].let {
                RectF(it.left - padding / 2, it.top - padding / 2,
                    buttonRects[leftEdge + popupWidths[popupX][popupY][popupLong] - 1][popupY].right + padding / 2, it.bottom + padding / 2)
            }
            for (i in 0 until popupWidths[popupX][popupY][popupLong] - 1) {
                buttonTexts[leftEdge + i][popupY][0] = DIGITS.getOrNull(DIGITS.indexOf(showPopup) + 30 * i) ?: '☣'
                buttonTexts[leftEdge + i][popupY][1] = ' '
                popupValues[leftEdge + i] = DIGITS.indexOf(showPopup) + 30 * i - bijectiveAdd
            }
            buttonTexts[leftEdge + popupWidths[popupX][popupY][popupLong] - 1][popupY][0] = '×'
            buttonTexts[leftEdge + popupWidths[popupX][popupY][popupLong] - 1][popupY][1] = ' '
            for (i in 0 until popupWidths[popupX][popupY][popupLong]) for (k in 0..1) popupWidths[leftEdge + i][popupY][k] = 0
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
            for (k in 0..1)
                buttonTextX[i][j][k] = if (k == 0) buttonRects[i][j].centerX() - (if (system == NumSystem.GREEK && showPopup != '½') padding else 0f)
                    else buttonRects[i][j].right - padding
            buttonTextY[i][j][0] = buttonRects[i][j].centerY() + padding
            buttonTextY[i][j][1] = buttonRects[i][j].bottom - padding
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldW: Int, oldH: Int) {
        super.onSizeChanged(w, h, oldW, oldH)
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
                for (k in 0..1) if (buttonTexts[i][j][k] != ' ') it.drawText(buttonTexts[i][j][k].toString(), buttonTextX[i][j][k], buttonTextY[i][j][0],
                    textPaints[if (k == 1 && buttonTexts[i][j][k] == '⏎') 2 else k])
            }
            if (showPopup != ' ') {
                if (showPopup != '½') for (i in 0 until buttonCols) if (popupValues[i] > 0)
                    it.drawText(popupValues[i].toString(), buttonTextX[i][popupY][1], buttonTextY[i][popupY][1], decimalPaint)
                it.drawRoundRect(popupRect, padding, padding, popupPaint)
            }
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
