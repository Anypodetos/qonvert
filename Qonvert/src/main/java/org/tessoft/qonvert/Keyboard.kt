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
import java.util.*
import kotlin.concurrent.schedule
import kotlin.math.*

const val THORN_CLUSTER = "ꝥҹʡƾ"

class EditInput(context: Context, attrs: AttributeSet) : androidx.appcompat.widget.AppCompatEditText(context, attrs) {

    var string
        get() = text.toString()
        set(value) { setText(value) }

    private val mainActivity = if (context is MainActivity) context else null
    var composeBackup = ""
    var composeBackupCaret = 0

    override fun onSelectionChanged(selStart: Int, selEnd: Int) {
        super.onSelectionChanged(selStart, selEnd)
        mainActivity?.updateKeyboardToCaretPos()
    }

    fun keyStroke(c: Char) {
        val caret = selectionStart
        string = string.substring(0, caret) + c + string.substring(selectionEnd)
        setSelection(caret + 1)
    }

    fun composeDigit(system: NumSystem, handleKeystroke: Boolean = true) {
        composeBackup = ""
        if (system !in setOf(NumSystem.GREEK, NumSystem.ROMAN)) {
            var pos = selectionStart - 1
            var spaces = 0
            if (!hasSelection()) {
                while (string.getOrNull(pos) == ' ') { pos--; spaces++ }
                var p = pos
                while (string.getOrNull(p) in '0'..'9') { p--; if (string.getOrNull(p + 1) != '0') pos = p }
                if (system == NumSystem.BALANCED && string.getOrNull(pos) == '-') pos--
            }
            val decimal = string.substring(pos + 1, selectionEnd).trim().toIntOrNull() ?: Int.MAX_VALUE
            if ((if (system != NumSystem.BALANCED) DIGITS.getOrNull(decimal + if (system == NumSystem.BIJECTIVE_A && decimal != 0) 9 else 0) else
                BAL_DIGITS.getOrNull(decimal + MAX_BAL_BASE / 2))?.let {
                    if (!handleKeystroke) spaces = (spaces - 1).coerceAtLeast(0)
                    if (it in '0'..'9') spaces++ else {
                        composeBackup = string
                        composeBackupCaret = selectionEnd
                    }
                    string = string.substring(0, pos + 1) + (if (MainActivity.lowerDigits) it else it.uppercaseChar()) + " ".repeat(spaces) +
                        string.substring(selectionEnd)
                    setSelection(pos + spaces + 2)
                } == null && handleKeystroke && !hasSelection()) keyStroke(' ')
        } else if (handleKeystroke) keyStroke(' ')
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
    private val popupSizes = Array(buttonCols) { Array(buttonRows) { arrayOf(0, 0) } }
    private val popupValues = Array(buttonCols) { Array(buttonRows) { 0 } }
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
    private val textColor = if (digitsUniqueAndBalanced) resolveColor(context, R.attr.editTextColor) else 0xffff0000.toInt()
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
            val hasPopup = popupSizes[touchX][touchY][long] > 1
            if (showPopup != ' ') {
                showPopup = ' '
                fillButtons(always = true)
            }
            mainActivity?.editInput?.let { edit ->
                when (t) {
                    '⏎' -> mainActivity?.addInputToHistory()
                    '␣' -> if (mainActivity?.spaceComposes == true) edit.composeDigit(system) else edit.keyStroke(' ')
                    '⌫' -> if (edit.composeBackup.isEmpty()) backspace(edit, repeat = false) else {
                        edit.string = edit.composeBackup.substring(0, edit.composeBackupCaret) + " " + edit.composeBackup.substring(edit.composeBackupCaret)
                        edit.setSelection(edit.composeBackupCaret + 1)
                    }
                    '\u200A' -> backspace(edit, repeat = true)
                    ' ', '×' -> Unit
                    else -> if (hasPopup) {
                        showPopup = t.lowercaseChar()
                        popupX = touchX
                        popupY = touchY
                        popupLong = long
                        fillButtons(always = true)
                    } else edit.keyStroke(t)
                }
                if (t != '␣') edit.composeBackup = ""
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
        for (i in 0 until buttonCols) for (j in 0 until buttonRows) for (k in 0..1) popupSizes[i][j][k] = 0
        for (i in 0 until buttonCols) for (j in 0 until buttonRows) popupValues[i][j] = 0
        with(MainActivity.resolveFont(greekOrRoman = system in setOf(NumSystem.GREEK, NumSystem.ROMAN))) { for (i in 0..1) textPaints[i].typeface = this }
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
        val digitRange = digitRange(base, system, bijectiveAdd)
        val bottomRow = arrayOf("", "")
        when (system) {
            NumSystem.GREEK -> {
                var block = 0
                for ((d, digit) in GREEK_DIGITS.withIndex()) if (digit == '\u0000') block++ else
                    buttonTexts[((d - block) % 3 + 3 * block - if (digitsLeft) 3 else 0) % buttonCols][2 - ((d - block) / 3 % 3)][0] = digit
                for (d in 1..9) buttonTexts[(d - 1) % 3 + if (digitsLeft) 0 else 3][(9 - d) / 3][1] = d.digitToChar()
                bottomRow[0] = if (digitsLeft) ("○.˙͵-␣" + (if (landscape) "°'\"" else "") + ",/") else (",/-○.˙͵" + (if (landscape) "∞無␣" else "") + "⏎")
                bottomRow[1] = if (digitsLeft) ("0½ ʹ" + (if (landscape) "      " else "°'\"") + "⏎") else ("°'\"0½ ʹ" + (if (landscape) "    " else "␣"))
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
            }
            else -> {
                val digitsStart = if (digitsLeft) 0 else buttonCols - 5
                if (system != NumSystem.BIJECTIVE_A || mainActivity?.spaceComposes == true)
                    for (d in (digitRange.first - bijectiveAdd).coerceAtLeast(1)..(digitRange.last - bijectiveAdd).coerceAtMost(9))
                        buttonTexts[(d - 1) % 3 + digitsStart][(9 - d) / 3][0] = d.digitToChar()
                for (d in digitRange.first.coerceAtLeast(10)..digitRange.last.coerceAtMost(if (system != NumSystem.BALANCED) 39 else 18)) {
                    val i = if (d in 16..18 && system == NumSystem.BALANCED && !digitsLeft && !landscape) 3 else
                        ((d - 1 - if (mainActivity?.spaceComposes == true) 0 else bijectiveAdd) / 3 + digitsStart) % buttonCols
                    val j = (d + 2) % 3
                    val k = if (buttonTexts[i][j][0] == ' ') 0 else 1
                    buttonTexts[i][j][k] = DIGITS[d]
                    popupSizes[i][j][k] = if (system != NumSystem.BALANCED) (if (d + 30 > digitRange.last) 0 else (digitRange.last - d) / 30 + 2) else
                        max(digitRange.last - d, (d - 6) - digitRange.first) / 30 + 1
                }
                for (d in digitRange.first.coerceAtLeast(-17)..-1) {
                    val i = (d + 3 * ((if (digitsLeft) 21 else 19) - buttonCols)) / 3 % buttonCols
                    val j = (d + 18) % 3
                    val k = if (buttonTexts[i][j][0] == ' ') 0 else 1
                    buttonTexts[i][j][k] = (d + 123).toChar()
                    popupSizes[i][j][k] = if (d + 36 > digitRange.last && d - 30 < digitRange.first) 0 else
                        max((digitRange.last - (d + 36)) / 30 + 1, ((d - digitRange.first) / 30 + 2))
                }
                if (system == NumSystem.BALANCED) for (d in digitRange.first.coerceAtLeast(-30)..-27) {
                    val i = (d + 30) / 3 + (if (digitsLeft) 6 else 4) + (if (landscape) 3 else 0)
                    val j = (d + 30) % 3
                    val k = if (digitsLeft) 0 else 1
                    buttonTexts[i][j][k] = THORN_CLUSTER[d + 30]
                    popupSizes[i][j][k] = if (d + 66 > digitRange.last) 0 else ((d + 30) - digitRange.first) / 30 + 2
                }
                bottomRow[0] = (if (digitsLeft) ("0.˙-␣" + (if (landscape) " °'\"" else "") +  ",/") else (",/-" + (if (landscape) "°'\"␣" else "") + "0.˙⏎")).
                    let {
                        if (digitRange.first <= 0 || mainActivity?.spaceComposes == true) it else it.replaceFirst('0', '∞')
                    }
                bottomRow[1] = if (landscape) (if (digitsLeft) "∞½        ⏎" else "       ∞½  ").
                    let {
                        if (digitRange.first <= 0 || mainActivity?.spaceComposes == true) it else it.replaceFirst('∞', ' ')
                    } else (if (digitsLeft) " ½ °'\"⏎" else "°'\" ½ ␣")
            }
        }
        popupSizes[if (digitsLeft) (if (!landscape || system != NumSystem.ROMAN) 1 else 5) else
            (if (!landscape || system in setOf(NumSystem.GREEK, NumSystem.ROMAN)) 4 else 8)][3][1] = 24 /* fraction chars popup */
        val brackets = "[{;@#_\$%&"
        val bracketsStart = if (digitsLeft) buttonCols - 3 else 0
        for (i in 0..2) for (j in 0..2) buttonTexts[i + bracketsStart][j][if (buttonTexts[i + bracketsStart][j][0] == ' ') 0 else 1] = brackets[3 * i + j]
        for (k in 0..1) {
            bottomRow[k] += if (k == 0) "⌫" else "\u200A"
            for (i in 0 until buttonCols) buttonTexts[i][3][k] = bottomRow[k].getOrNull(i) ?: '☹'
        }
        if (showPopup !in " ½") {
            val popupWidth = popupSizes[popupX][popupY][popupLong].coerceAtMost(8)
            val popupHeight = if (system != NumSystem.BALANCED) (popupSizes[popupX][popupY][popupLong] + 7) / 8 else 2
            val leftEdge = popupX - (popupX + popupWidth - buttonCols).coerceAtLeast(0)
            val topEdge = popupY - (popupY + popupHeight - buttonRows).coerceAtLeast(0)
            popupRect = RectF(buttonRects[leftEdge][topEdge].left - padding / 2, buttonRects[leftEdge][topEdge].top - padding / 2,
                buttonRects[leftEdge + popupWidth - 1][topEdge].right + padding / 2, buttonRects[leftEdge][topEdge + popupHeight - 1].bottom + padding / 2)
            val digitIndexStart = if (system != NumSystem.BALANCED) DIGITS.indexOf(showPopup) else
                if (showPopup in 'a'..'z') showPopup.code - 87 else THORN_CLUSTER.indexOf(showPopup) + 36
            for (i in 0 until popupWidth) for (j in 0 until popupHeight) {
                val digitIndex = digitIndexStart + if (system != NumSystem.BALANCED) 30 * (i + popupWidth * j) else if (j % 2 == 0) 30 * i else -30 * i - 36
                val displayDigit = digitIndex in digitRange && digitIndex !in 0..3 /* exclude bottom left button for thorn cluster popups */
                buttonTexts[leftEdge + i][topEdge + j][0] = if (displayDigit)
                    (if (system != NumSystem.BALANCED) DIGITS.getOrNull(digitIndex) else BAL_DIGITS.getOrNull(digitIndex + MAX_BAL_BASE / 2)) ?: ' ' else ' '
                buttonTexts[leftEdge + i][topEdge + j][1] = ' '
                popupValues[leftEdge + i][topEdge + j] = if (displayDigit) digitIndex - bijectiveAdd else 0
            }
            buttonTexts[leftEdge + popupWidth - 1][topEdge + popupHeight - 1][0] = '×'
            for (i in leftEdge until leftEdge + popupWidth) for (j in topEdge until topEdge + popupHeight) for (k in 0..1) popupSizes[i][j][k] = 0
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
                it.drawRoundRect(popupRect, padding, padding, popupPaint)
                if (showPopup != '½') for (i in 0 until buttonCols) for (j in 0 until buttonRows) if (popupValues[i][j] != 0)
                    it.drawText(popupValues[i][j].toString(), buttonTextX[i][j][1], buttonTextY[i][j][1], decimalPaint)
            }
        }
    }

    fun show() {
        hidden = false
        if (visibility != VISIBLE) with(animate()) {
            fillButtons(always = true)
            duration = 150
            withStartAction { visibility = VISIBLE }
            translationY(0f)
            alpha(1f)
        }
    }

    fun hide() {
        hidden = true
        if (visibility != GONE) with(animate()) {
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
