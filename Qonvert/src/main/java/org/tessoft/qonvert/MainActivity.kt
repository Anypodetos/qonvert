package org.tessoft.qonvert

/*
Copyright 2020, 2021 Anypodetos (Michael Weber)

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
import android.app.Activity
import android.content.*
import android.content.res.Configuration
import android.graphics.Typeface
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.TypedValue
import android.view.*
import android.view.inputmethod.EditorInfo
import android.widget.*
import android.widget.SeekBar.OnSeekBarChangeListener
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.preference.PreferenceManager
import com.google.android.material.floatingactionbutton.FloatingActionButton
import java.math.BigInteger
import java.math.BigInteger.*
import java.util.*
import kotlin.concurrent.schedule
import kotlin.math.*

const val HG2G = 42
const val TAXICAB = 1729
val MONSTER = BigInteger("808017424794512875886459904961710757005754368000000000")
val BUTTON_BASES = listOf(2, 3, 6, 8, 10, 12, 16, 20, 26)
val DEFAULT_BUTTONS = setOf("2", "8", "10", "12", "16")

enum class AppTheme {
    UNSET, CLASSIC, BLUE
}

class MainActivity : AppCompatActivity() {

    private var lastQNumber = QNumber()
    private var rangeToast: Toast? = null

    private lateinit var preferences: SharedPreferences
    private var showRange = true
    private var warnNonstandardInput = true
    private val historyList = mutableListOf<QNumber>()
    private val numSystems = arrayOf(NumSystem.STANDARD, NumSystem.STANDARD)
    private var showWhatsNewStar = true

    private lateinit var textOutputs: Array<TextView>
    private lateinit var toggleButtons: Array<ToggleButton>
    private lateinit var systemButtons: Array<Button>
    @SuppressLint("UseSwitchCompatOrMaterialCode")
    private lateinit var complementSwitch: Switch
    private lateinit var baseTexts: Array<TextView>
    private lateinit var baseBars: Array<SeekBar>
    private lateinit var outputView: ScrollView
    private lateinit var outputLayout: ConstraintLayout
    private lateinit var editInput: EditText
    private lateinit var clearButton: FloatingActionButton

    override fun onCreate(savedInstanceState: Bundle?) {
        preferences = PreferenceManager.getDefaultSharedPreferences(this)
        setQonvertTheme(this, if (appTheme == AppTheme.UNSET) preferences.getString("theme", "LA") ?: "LA" else "")

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setSupportActionBar(findViewById(R.id.toolbar))

        textOutputs = arrayOf(
            findViewById(R.id.textOutput0),
            findViewById(R.id.textOutput1),
            findViewById(R.id.textOutput2),
            findViewById(R.id.textOutput3),
        )
        toggleButtons = arrayOf(
            findViewById(R.id.inToggleButton2),
            findViewById(R.id.inToggleButton3),
            findViewById(R.id.inToggleButton6),
            findViewById(R.id.inToggleButton8),
            findViewById(R.id.inToggleButton10),
            findViewById(R.id.inToggleButton12),
            findViewById(R.id.inToggleButton16),
            findViewById(R.id.inToggleButton20),
            findViewById(R.id.inToggleButton26),
            findViewById(R.id.outToggleButton2),
            findViewById(R.id.outToggleButton3),
            findViewById(R.id.outToggleButton6),
            findViewById(R.id.outToggleButton8),
            findViewById(R.id.outToggleButton10),
            findViewById(R.id.outToggleButton12),
            findViewById(R.id.outToggleButton16),
            findViewById(R.id.outToggleButton20),
            findViewById(R.id.outToggleButton26)
        )
        systemButtons = arrayOf(
            findViewById(R.id.inSystemButton),
            findViewById(R.id.outSystemButton)
        )
        complementSwitch = findViewById(R.id.complementSwitch)
        baseTexts = arrayOf(
            findViewById(R.id.inBaseText),
            findViewById(R.id.outBaseText)
        )
        baseBars = arrayOf(
            findViewById(R.id.inBaseBar),
            findViewById(R.id.outBaseBar)
        )
        outputView = findViewById(R.id.outputView)
        outputLayout = findViewById(R.id.outputLayout)
        editInput = findViewById(R.id.editInput)
        clearButton = findViewById(R.id.clearButton)

        /*   I n t e r f a c e   */

        for (i in 0..1) baseBars[i].setOnSeekBarChangeListener(object : OnSeekBarChangeListener {
            override fun onStartTrackingTouch(seekBar: SeekBar) { }
            override fun onStopTrackingTouch(seekBar: SeekBar) { }
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                baseAndSystemFeedback(i)
                calculate(inputChanged = i == 0)
            }
        })

        val toggleButtonGestureDetectors = arrayOfNulls<GestureDetector>(2)
        for (i in 0..1) toggleButtonGestureDetectors[i] = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(e1: MotionEvent?, e2: MotionEvent?, velocityX: Float, velocityY: Float): Boolean {
                baseBars[i].progress += velocityX.sign.toInt()
                return true
            }
        })
        for (i in 0..1) for (j in BUTTON_BASES.indices) toggleButtons[BUTTON_BASES.size * i + j].setOnTouchListener { view, event ->
            toggleButtonGestureDetectors[i]?.onTouchEvent(event)
            return@setOnTouchListener false
        }
        for (i in 0..1) for (j in BUTTON_BASES.indices) toggleButtons[BUTTON_BASES.size * i + j].setOnClickListener {
            baseBars[i].progress = BUTTON_BASES[j] - 2
            toggleButtons[BUTTON_BASES.size * i + j].isChecked = true /* push down again in case it was down before */
        }

        for (i in 0..1) systemButtons[i].setOnClickListener { view ->
            val popupMenu = PopupMenu(this, view)
            for ((j, res) in resources.getStringArray(R.array.num_systems).withIndex())
                popupMenu.menu.add(1, Menu.NONE, j, res).isChecked = j == numSystems[i].ordinal
            popupMenu.menu.setGroupCheckable(1, true, true)
            popupMenu.setOnMenuItemClickListener { item: MenuItem ->
                with(NumSystem.values()[item.order]) { setBaseAndSystem(i, allowedBase(baseBars[i].progress + 2, this), this, true) }
                true
            }
            popupMenu.show()
        }
        for (i in 0..1) systemButtons[i].setOnLongClickListener {
            val sys = if (numSystems[i] == NumSystem.STANDARD) NumSystem.BALANCED else NumSystem.STANDARD
            setBaseAndSystem(i, allowedBase(baseBars[i].progress + 2, sys), sys, true)
            true
        }

        complementSwitch.setOnClickListener {
            calculate(inputChanged = false)
        }
        for (i in 0..1) baseTexts[i].setOnClickListener {
            toastRangeHint(i, true)
        }

        for (i in 0..3) textOutputs[i].setOnClickListener {
            val roman = i == 1 && textOutputs[i].typeface == Typeface.SERIF
            copyToInput(lastQNumber, textOutputs[i].text,
                if (roman) 10 else baseBars[1].progress + 2, if (roman) NumSystem.ROMAN else numSystems[1],
                complementSwitch.isChecked, switchBases = true)
        }
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager?
        for (i in 0..3) textOutputs[i].setOnLongClickListener { view ->
            val text = textOutputs[i].text.toString().removeSurrounding("\"")
            var altText = text
            var altMenuTitle = R.string.clipboard_asIs
            val slash = altText.indexOf('/')
            val under = altText.indexOf('_')
            if (slash > -1 && altText.substring(under + 1).all { it in '0'..'9' || it in "/-. " }) {
                altText = "_${altText}_"
                for ((unicode, frac) in FRACTION_CHARS)
                    altText = altText.replace("_${frac.first}/${frac.second}_", unicode.toString())
                                     .replace("-${frac.first}/${frac.second}_", "⁻$unicode")
                altText = altText.removePrefix("_").removeSuffix("_")
                if (altText == text) altText = (if (under > -1) altText.substring(0, under) else "") +
                        altText.substring(under + 1, slash).map {
                    when (it) {
                        in '0'..'9' -> SUPERSCRIPT_DIGITS[it.toInt() - 48]
                        '-' -> '⁻'
                        '.' -> '·'
                        else -> '\u202F' /* narrow space */
                        }
                    }.joinToString("") + '⁄' + altText.substring(slash + 1).map {
                        if (it in '0'..'9') SUBSCRIPT_DIGITS[it.toInt() - 48] else '\u202F'
                    }.joinToString("")

                altMenuTitle = R.string.clipboard_fraction
            }
            /*if (altText == text && i == 0 && numSystems[1] !in setOf(NumSystem.BIJECTIVE_1, NumSystem.BIJECTIVE_A, NumSystem.ROMAN) &&
                    altText.startsWith("..")) {
                val q = lastQNumber.copy()
                q.changeBase(baseBars[1].progress + 2, numSystems[1], true)
                altText = q.toPositional(intDigits = ...) //////////////
                altMenuTitle = R.string.clipboard_complement
            }*/
            if (altText == text) {
                clipboard?.setPrimaryClip(ClipData.newPlainText(null, text))
                Toast.makeText(applicationContext, getString(R.string.to_clipboard), Toast.LENGTH_SHORT).show()
            } else {
                val popupMenu = PopupMenu(this, view)
                popupMenu.menu.add(Menu.NONE, Menu.NONE, 0, getString(R.string.clipboard_asIs))
                popupMenu.menu.add(Menu.NONE, Menu.NONE, 1, getString(altMenuTitle))
                popupMenu.setOnMenuItemClickListener { item: MenuItem ->
                    clipboard?.setPrimaryClip(ClipData.newPlainText(null, if (item.order == 0) text else altText))
                    Toast.makeText(applicationContext, getString(R.string.to_clipboard), Toast.LENGTH_SHORT).show()
                    true
                }
                popupMenu.show()
            }
            true
        }

        editInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) { }
            override fun afterTextChanged(s: Editable) { }
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                val small = outputLayout.height - textOutputs[0].height / 3 < outputView.height
                calculate(inputChanged = true)
                if (small) outputView.post { outputView.smoothScrollTo(0, outputView.bottom) }
            }
        })
        editInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                if (editInput.text.toString() != "" && lastQNumber.isValid) historyList.add(lastQNumber)
                editInput.selectAll()
                resources.configuration.orientation != Configuration.ORIENTATION_LANDSCAPE
            } else false
        }

        clearButton.setOnClickListener {
            if (editInput.text.toString() != "" && lastQNumber.isValid &&
                (historyList.size == 0 || historyList.last().toString(withBaseSystem = true) != lastQNumber.toString(withBaseSystem = true)))
                    historyList.add(lastQNumber)
            editInput.setText("")
        }
        clearButton.setOnLongClickListener {
            complementSwitch.isChecked = false
            for (i in 1 downTo 0) setBaseAndSystem(i, 10, NumSystem.STANDARD, i == 0)
            true
        }

    }

    /*   O t h e r   e v e n t s   */

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        if (showWhatsNewStar) menu.findItem(R.id.whatsNewItem).setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
        return true
    }
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.historyItem -> {
                val itemsList = mutableListOf<String>()
                for (q in historyList) itemsList.add(q.toString(withBaseSystem = true))
                val historyDialog = AlertDialog.Builder(this)
                historyDialog.setTitle(R.string.menu_history)
                if (itemsList.isNotEmpty()) {
                    historyDialog.setSingleChoiceItems(itemsList.toTypedArray(), -1) { dialog, which ->
                        if (editInput.text.toString() != "" && lastQNumber.isValid) historyList.add(lastQNumber)
                        copyToInput(historyList[which])
                        historyList.removeAt(which)
                        dialog.dismiss()
                    }
                    historyDialog.setNeutralButton(R.string.clear_history) { dialog, _ ->
                        dialog.dismiss()
                        AlertDialog.Builder(this)
                            .setTitle(R.string.clear_history_q)
                            .setMessage(R.string.clear_history_sub)
                            .setPositiveButton(R.string.yes) { _, _ -> historyList.clear() }
                            .setNegativeButton(R.string.no) { _, _ -> }
                            .create().show()
                    }
                } else historyDialog.setMessage(R.string.no_history)
                historyDialog.setNegativeButton(R.string.close) { _, _ -> }
                historyDialog.create().show()
            }
            R.id.playItem -> {
                val ratio = (lastQNumber.numerator.toDouble() / lastQNumber.denominator.toDouble()).absoluteValue
                val message = if (ratio in 1/128.0..128.0) {
                    val baseFrequency = 440 / 2.0.pow(round(ln(ratio) / ln(2.0)))
                    OneTimeBuzzer(baseFrequency, 100, 1.5).play()
                    Timer().schedule(1500) { OneTimeBuzzer(baseFrequency * ratio, 100, 1.5).play() }
                    Timer().schedule(3000) {
                        OneTimeBuzzer(baseFrequency, 50, 2.0).play()
                        OneTimeBuzzer(baseFrequency * ratio, 50, 2.0).play()
                    }
                    lastQNumber.toInterval(resources)
                } else getString(R.string.no_interval,
                    QNumber(ONE, 128.toBigInteger(), baseBars[0].progress + 2, numSystems[0], format = QFormat.FRACTION).toString(),
                    QNumber(128.toBigInteger(), ONE, baseBars[0].progress + 2, numSystems[0]).toString(withBaseSystem = true))
                if (message != "") {
                    val toast = Toast.makeText(applicationContext, message, Toast.LENGTH_LONG)
                    toast.view.findViewById<TextView>(android.R.id.message).gravity = Gravity.CENTER
                    toast.show()
                }
            }
            R.id.settingsItem, R.id.helpItem, R.id.cheatSheetItem, R.id.whatsNewItem, R.id.aboutItem -> {
                val intent = Intent(this@MainActivity, if (item.itemId == R.id.settingsItem)
                    SettingsActivity::class.java else HelpActivity::class.java)
                if (item.itemId != R.id.settingsItem) intent.putExtra("help", item.itemId)
                if (item.itemId == R.id.whatsNewItem) showWhatsNewStar = false
                startActivity(intent)
            }
        }
        return true
    }

    override fun onResume() {
        super.onResume()
        maxDigitsAfter = (preferences.getString("digits", null))?.toIntOrNull() ?: 300
        groupDigits = preferences.getBoolean("group", false)
        lowerDigits = preferences.getBoolean("lowercase", false)
        apostrophus = ((preferences.getString("apostrophus", null))?.toIntOrNull() ?: 0).coerceIn(0..3)
        showRange = preferences.getBoolean("range", true)
        warnNonstandardInput = preferences.getBoolean("wrongDigits", true)
        val textSize = (preferences.getString("size", null))?.toFloatOrNull() ?: 20f
        editInput.textSize = textSize
        for (i in 0..3) textOutputs[i].textSize = textSize
        clearButton.size = if (textSize < 25) FloatingActionButton.SIZE_MINI else FloatingActionButton.SIZE_NORMAL
        with(preferences.getStringSet("buttons", null) ?: DEFAULT_BUTTONS) {
            for (j in BUTTON_BASES.indices) {
                val visibility = if (BUTTON_BASES[j].toString() in this) View.VISIBLE else View.GONE
                toggleButtons[j].visibility = visibility
                toggleButtons[j + BUTTON_BASES.size].visibility = visibility
            }
        }
        if (historyList.size == 0) for (i in 0 until preferences.getInt("historySize", 0))
            with(QNumber(preferencesEntry = preferences.getString("history$i", null) ?: "")) { if (isValid) historyList.add(this) }
        complementSwitch.isChecked = preferences.getBoolean("outComplement", false)
        setBaseAndSystem(0, preferences.getInt("inBase",  10),
            try { NumSystem.valueOf(preferences.getString("inSystem",  null) ?: "") } catch (e: Exception) { NumSystem.STANDARD }, false)
        setBaseAndSystem(1, preferences.getInt("outBase",  10),
            try { NumSystem.valueOf(preferences.getString("outSystem", null) ?: "") } catch (e: Exception) { NumSystem.STANDARD }, false)
        editInput.setText(preferences.getString("input", "1_5/6"))
        showWhatsNewStar = preferences.getInt("version", 0) < BuildConfig.VERSION_CODE &&
            preferences.getString("input", null) != null /* don’t show star if there was no old version */
    }

    override fun onPause() {
        super.onPause()
        val editor = preferences.edit()
        val prunedSize = historyList.size.coerceAtMost(TAXICAB)
        for (i in prunedSize until preferences.getInt("historySize", 0)) editor.remove("history$i")
        editor.putInt("historySize", prunedSize)
        for (i in 0 until prunedSize) editor.putString("history$i", historyList[historyList.size - prunedSize + i].toPreferencesString())
        editor.putString("input", editInput.text.toString())
        editor.putInt("inBase",  baseBars[0].progress + 2)
        editor.putInt("outBase", baseBars[1].progress + 2)
        editor.putString("inSystem",  numSystems[0].toString())
        editor.putString("outSystem", numSystems[1].toString())
        editor.putBoolean("outComplement", complementSwitch.isChecked)
        if (!showWhatsNewStar) editor.putInt("version", BuildConfig.VERSION_CODE)
        editor.apply()
    }

    /*  C a l c u l a t e  */

    fun calculate(inputChanged: Boolean) {
        val q = if (inputChanged) QNumber(editInput.text.toString(), baseBars[0].progress + 2, numSystems[0])
            else lastQNumber.copy()
        if (inputChanged) {
            editInput.setTextColor(ContextCompat.getColor(applicationContext, when {
               !q.isValid -> R.color.red_error
                q.nonstandardInput && warnNonstandardInput -> R.color.orange_light
                else -> resolveColor(R.attr.editTextColor)
            }))
            lastQNumber = q.copy()
        }
        q.changeBase(baseBars[1].progress + 2, numSystems[1], complementSwitch.isChecked)

        textOutputs[0].text = if (q.isValid) {
            if (q.system !in NumSystem.BIJECTIVE_1..NumSystem.BIJECTIVE_A || q.denominator <= ONE) q.toPositional() else ""
        } else q.errorMessage(resources)

        textOutputs[1].text = if (q.denominator > ONE) q.toFraction() else
            (if (q.system != NumSystem.ROMAN) q.toRoman(showNonPositive = false) else "")

        textOutputs[2].text = if (q.denominator > ONE) {
            if (q.numerator.abs() > q.denominator) q.toMixed() else ""
        } else q.toUnicode()

        textOutputs[3].text = if (q.denominator > ONE) q.toContinued() else when (q.numerator) {
            HG2G.toBigInteger() -> "🐋💐"
            TAXICAB.toBigInteger() -> "🚕"
            MONSTER -> "👾"
            else -> ""
        }
        for (i in 0..3) {
            textOutputs[i].typeface = if (when (i) {
                1    -> q.system == NumSystem.ROMAN || q.denominator == ONE
                2    -> q.system == NumSystem.ROMAN && q.denominator > ONE
                else -> q.system == NumSystem.ROMAN
            }) Typeface.SERIF else Typeface.DEFAULT
            textOutputs[i].visibility = if (textOutputs[i].text == "") View.GONE else View.VISIBLE
        }
    }

    /*   U t i l i t i e s   */

    private fun setBaseAndSystem(i: Int, base: Int, system: NumSystem, recalculate: Boolean) {
        numSystems[i] = system
        systemButtons[i].text = system.short
        baseBars[i].progress = base - 2
        if (recalculate) calculate(inputChanged = i == 0)
        baseAndSystemFeedback(i)
    }

    private fun baseAndSystemFeedback(i: Int) {
        val base = baseBars[i].progress + 2
        val baseAllowed = allowedBase(base, numSystems[i]) == base
        baseTexts[i].text = getString(R.string.base, resources.getStringArray(R.array.base_inout)[i], base,
            resources.getStringArray(R.array.num_systems)[if (baseAllowed) numSystems[i].ordinal else 0])
        for (j in BUTTON_BASES.indices) toggleButtons[BUTTON_BASES.size * i + j].isChecked = BUTTON_BASES[j] == base
        systemButtons[i].setBackgroundColor(ContextCompat.getColor(applicationContext,
            if (baseAllowed) resolveColor(R.attr.colorPrimaryVariant) else R.color.grey))
        val actualNumSystem = if (baseAllowed) numSystems[i] else NumSystem.STANDARD
        if (i == 0) editInput.typeface = if (actualNumSystem == NumSystem.ROMAN) Typeface.SERIF else Typeface.DEFAULT
        if (i == 1) complementSwitch.isEnabled = actualNumSystem != NumSystem.BALANCED
        toastRangeHint(i)
    }

    private fun toastRangeHint(i: Int, always: Boolean = false) {
        if (always || (showRange && lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))) {
            val base = baseBars[i].progress + 2
            val minDigit = minDigit(base, numSystems[i])
            rangeToast?.cancel()
            rangeToast = Toast.makeText(applicationContext, getString(
                if (numSystems[i] == NumSystem.ROMAN && base == 10) { if (i == 0) R.string.range_toast_roman else R.string.range_toast_onlyRoman }
                else R.string.range_toast, resources.getStringArray(R.array.base_inout)[i],
                digitToChar(minDigit, base, numSystems[i]), digitToChar(minDigit + base - 1, base, numSystems[i])), Toast.LENGTH_SHORT)
            rangeToast?.setGravity(Gravity.TOP, 0, 0)
            rangeToast?.show()
        }
    }

    private fun copyToInput(q: QNumber, st: CharSequence = q.toString(), base: Int = q.base, system: NumSystem = q.system,
                complement: Boolean = q.complement, switchBases: Boolean = false) {
        if (switchBases && lastQNumber.numerator < ZERO) complementSwitch.isChecked = lastQNumber.complement
        editInput.setText(if (st.endsWith('…')) {
            val toast = Toast.makeText(applicationContext, getString(R.string.to_fraction), Toast.LENGTH_SHORT)
            toast.view.findViewById<TextView>(android.R.id.message).gravity = Gravity.CENTER
            toast.show()
            q.changeBase(base, system, complement)
            q.toMixed()
        } else st)
        if (!(editInput.text.startsWith('"') && editInput.text.endsWith('"'))) {
            if (switchBases) setBaseAndSystem(1, baseBars[0].progress + 2, numSystems[0], false)
            setBaseAndSystem(0, base, system, true)
        }
    }

    private fun resolveColor(id: Int): Int {
        val typedValue = TypedValue()
        theme.resolveAttribute(id, typedValue, true)
        return typedValue.resourceId
    }

    companion object {
        var maxDigitsAfter = 0
            private set
        var groupDigits = false
            private set
        var lowerDigits = false
            private set
        var apostrophus = 0
            private set

        fun tokenBaseSystem(token: Char): Pair<Int, NumSystem>? = // could be made customisable
            when (token) {
                '@' -> Pair(2, NumSystem.STANDARD)
                '#' -> Pair(8, NumSystem.STANDARD)
                '$', '€', '£', '¥' -> Pair(10, NumSystem.STANDARD)
                '%' -> Pair(12, NumSystem.STANDARD)
                '&' -> Pair(16, NumSystem.STANDARD)
                else -> null
            }

        var appTheme = AppTheme.UNSET
            private set
        fun setQonvertTheme(activity: Activity, theme: String = "") {
            if (theme != "") {
                AppCompatDelegate.setDefaultNightMode(when (theme) {
                    in "L".."LZ" -> AppCompatDelegate.MODE_NIGHT_NO
                    in "D".."DZ" -> AppCompatDelegate.MODE_NIGHT_YES
                    else         -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                })
                appTheme = if (theme.endsWith('B')) AppTheme.BLUE else AppTheme.CLASSIC
            }
            activity.setTheme(if (appTheme == AppTheme.BLUE) R.style.Theme_QonvertBlue else R.style.Theme_Qonvert)
        }
    }
}