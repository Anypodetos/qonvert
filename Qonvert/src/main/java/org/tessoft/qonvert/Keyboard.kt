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
import android.app.Application
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
        set(value) = setText(value)

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
    private var hidden = false
    private var touchDown = false
    private var touchI  = -1
    private var touchJ  = -1

    private var landscape = true
    private var buttonCols = 12
    private val buttonRows = 4
    private var buttonW = 1f
    private var buttonH = 1f
    private var padding = 0f
    private val buttonRects = Array(buttonCols) { Array(buttonRows) { RectF(0f, 0f, 0f, 0f) } }
    private val buttonTextX = Array(buttonCols) { Array(buttonRows) { arrayOf(0f, 0f) } }
    private val buttonTextY = Array(buttonCols) { Array(buttonRows) { 0f } }
    private val buttonTexts = Array(buttonCols) { Array(buttonRows) { arrayOf("", "") } }

    private val buttonPaints = Array(3) {
        Paint(0).apply {
            style = Paint.Style.FILL
            color = listOf(0x30808080, 0x60808080, ContextCompat.getColor(context, MainActivity.resolveColor(R.attr.colorSecondary)))[it]
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
            textSize = 12 * resources.displayMetrics.scaledDensity
            textAlign = Paint.Align.RIGHT
            color = textColor and 0xa0ffffff.toInt()
        })

    init {
        setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                    val i = floor(event.x / buttonW).roundToInt()
                    val j = floor(event.y / buttonH).roundToInt()
                    touchDown = event.actionMasked == MotionEvent.ACTION_DOWN || (touchDown && touchI == i && touchJ == j)
                    if (touchDown) { touchI = i; touchJ = j }
                        else if (touchJ == j - 2) hide()
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> touchDown = false
            }
            invalidate()
            buttonTexts[touchI][touchJ][0] == "" || hidden /* don’t process click if empty button or keyboard was hidden */
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
        if (touchI > -1 && touchJ > -1) {
            var t = buttonTexts[touchI][touchJ][long]
            if (long == 1 && t == "") t = buttonTexts[touchI][touchJ][0]
            mainActivity?.editInput?.let {
                when (t) {
                    "⏎" -> it.onEditorAction(EditorInfo.IME_ACTION_DONE)
                    "⌫" -> backspace(it, repeat = false)
                    "\u200A" -> backspace(it, repeat = true)
                    "" -> { }
                    else -> {
                        val caret = it.selectionStart
                        it.string = it.string.substring(0, caret) + (if (t == "␣") " " else t) + it.string.substring(it.selectionEnd)
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
        if (baseAndSystem.first != base || baseAndSystem.second != system || always) {
            base = baseAndSystem.first
            system = baseAndSystem.second
            val oldButtonCols = buttonCols
            buttonCols = if (landscape) 12 else if (system == NumSystem.GREEK) 9 else 8
            if (oldButtonCols != buttonCols || always) {
                buttonW = width.toFloat() / buttonCols
                buttonH = height.toFloat() / buttonRows
                padding = height / 50f
                for (i in 0 until buttonCols) for (j in 0 until buttonRows) {
                    buttonRects[i][j] = RectF(i * buttonW + padding, j * buttonH + padding, (i + 1) * buttonW - padding, (j + 1) * buttonH - padding)
                    for (k in 0..1) {
                        buttonTextX[i][j][k] = if (k == 0) buttonRects[i][j].centerX() else buttonRects[i][j].right - padding
                        buttonTextY[i][j]    = buttonRects[i][j].centerY() + padding
                    }
                }
            }
            for (i in 0 until buttonCols) for (j in 0 until buttonRows) for (k in 0..1) buttonTexts[i][j][k] = ""
            for (i in 0..1) textPaints[i].typeface = Typeface.DEFAULT
            val bijectiveAdd = if (system == NumSystem.BIJECTIVE_A) 9 else 0
            val minDigit = minDigit(base, system) + bijectiveAdd
            val maxDigit = minDigit + base - 1
            var bottomRow = ""
            when (system) {
                NumSystem.GREEK -> {
                    var block = 0
                    for ((d, digit) in GREEK_DIGITS.withIndex()) if (digit == '\u0000') block++ else
                        buttonTexts[((d - block) % 3 + 3 * block) % (if (landscape) 12 else 9)][2 - ((d - block) / 3 % 3)][0] = digit.toString()
                    for (d in 1..9) buttonTexts[(d - 1) % 3 + 3][(9 - d) / 3][1] = d.toString()
                    buttonTexts[3][3][1] = "0"
                    buttonTexts[5][3][1] = "'"
                    bottomRow = if (landscape) "○.͵ʹ∞\"␣" else "○.͵ʹ"
                    for (i in 0..1) textPaints[i].typeface = Typeface.SERIF
                }
                NumSystem.ROMAN -> {
                    for ((d, digit) in ROMAN_DIGITS.keys.withIndex()) buttonTexts[d % 4 + if (landscape) 6 else 3][(11 - d) / 4][0] = digit.toString()
                    if (APOSTROPHUS_OPTIONS[MainActivity.apostrophus][1] == '+' || APOSTROPHUS_OPTIONS[MainActivity.apostrophus][4] == '+')
                        buttonTexts[if (landscape) 8 else 5][1][0] = "ↀ"
                    buttonTexts[if (landscape) 9 else 6][0][0] = "|"
                    for (j in 0..2) for (k in 0..1) buttonTexts[if (landscape) 10 + k else 7][j][if (landscape) 0 else k] = "·∶∴∷⁙S"[2 - j + 3 * k].toString()
                    for (d in 1..9) buttonTexts[(d - 1) % 3 + 3][(9 - d) / 3][if (landscape) 0 else 1] = d.toString()
                    bottomRow = if (landscape) "0.'N∞\"␣" else { buttonTexts[3][3][1] = "0"; "N.'" }
                    for (i in 0..1) textPaints[i].typeface = Typeface.SERIF
                }
                else -> {
                    if (system != NumSystem.BIJECTIVE_A) for (d in minDigit.coerceAtLeast(1)..maxDigit.coerceAtMost(9))
                        buttonTexts[(d - 1) % 3 + if (landscape) 7 else 3][(9 - d) / 3][0] = d.toString()
                    for (d in minDigit.coerceAtLeast(10)..maxDigit)
                        buttonTexts[(d - bijectiveAdd + if (landscape) 20 else 8) / 3 % buttonCols][(d + 2) % 3][if (landscape) 0 else (d - bijectiveAdd) / 25] =
                            (d + 55).toChar().toString()
                    for (d in minDigit..-1) {
                        val i = (d + if (landscape) 21 else 33) / 3 % buttonCols
                        val j = (d + 18) % 3
                        buttonTexts[i][j][if (i == 0 || buttonTexts[i][j][0] == "") 0 else 1] = (d + 91).toChar().toString()
                    }
                    if (!landscape && system == NumSystem.BALANCED) for (d in 16..maxDigit) buttonTexts[3][d - 16][1] = (d + 55).toChar().toString()
                    bottomRow = (if (landscape) "∞無\"␣" else "") + "0.'"
                }
            }
            for (i in 0..2) for (j in 0..3) buttonTexts[i][j][if (buttonTexts[i][j][0] == "") 0 else 1] = "[{;,@#_/\$%&-"[4 * i + j].toString()
            for (i in 3 until buttonCols) if (i != (if (landscape) 7 else 3) || minDigit <= 0) buttonTexts[i][3][0] = "$bottomRow⏎⌫".getOrNull(i - 3).toString()
            if (landscape) buttonTexts[11][3][1] = "\u200A" else for (i in 0..2) buttonTexts[i + buttonCols - 3][3][1] = "\"␣\u200A"[i].toString()

            if (MainActivity.lowerDigits) for (i in 0 until buttonCols) for (j in 0 until buttonRows) for (k in 0..1)
                buttonTexts[i][j][k] = buttonTexts[i][j][k].lowercase()
            invalidate()
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
                    buttonTexts[i][j][0] == "" -> 0
                    !touchDown || touchI != i || touchJ != j -> 1
                    else -> 2
                }])
                for (k in 0..1) it.drawText(buttonTexts[i][j][k], buttonTextX[i][j][k], buttonTextY[i][j], textPaints[k])
            }
        }
    }

    fun show() {
        hidden = false
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
    }
}
