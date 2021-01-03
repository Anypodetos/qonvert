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
import java.math.BigInteger.ONE
import java.math.BigInteger.ZERO
import java.util.*
import kotlin.concurrent.schedule
import kotlin.math.*

const val hg2g = 42
const val taxiCab = 1729
val monster = BigInteger("808017424794512875886459904961710757005754368000000000")
val buttonBases = listOf(2, 3, 6, 8, 10, 12, 16, 20, 26)
val defaultButtons = setOf("2", "8", "10", "12", "16")

enum class AppTheme {
    UNSET, CLASSIC, BLUE
}

class MainActivity : AppCompatActivity() {

    private var lastQNumber = QNumber()
    private var rangeToast: Toast? = null

    private lateinit var preferences: SharedPreferences
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

    @SuppressLint("ClickableViewAccessibility")
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
        for (i in 0..1) for (j in buttonBases.indices) toggleButtons[buttonBases.size * i + j].setOnTouchListener { _, event ->
            toggleButtonGestureDetectors[i]?.onTouchEvent(event)
            return@setOnTouchListener false
        }
        for (i in 0..1) for (j in buttonBases.indices) toggleButtons[buttonBases.size * i + j].setOnClickListener {
            baseBars[i].progress = buttonBases[j] - 2
            toggleButtons[buttonBases.size * i + j].isChecked = true /* push down again in case it was down before */
        }

        for (i in 0..1) systemButtons[i].setOnClickListener { view ->
            val popupMenu = PopupMenu(this, view)
            for ((j, res) in resources.getStringArray(R.array.num_systems).withIndex())
                popupMenu.menu.add(1, Menu.NONE, j, res).isChecked = j == numSystems[i].ordinal
            popupMenu.menu.setGroupCheckable(1, true, true)
            popupMenu.setOnMenuItemClickListener { item: MenuItem ->
                NumSystem.values()[item.order].let { setBaseAndSystem(i, allowedBase(baseBars[i].progress + 2, it), it) }
                calculate(inputChanged = i == 0)
                true
            }
            popupMenu.show()
        }
        for (i in 0..1) systemButtons[i].setOnLongClickListener {
            val sys = if (numSystems[i] == NumSystem.STANDARD) NumSystem.BALANCED else NumSystem.STANDARD
            setBaseAndSystem(i, allowedBase(baseBars[i].progress + 2, sys), sys)
            calculate(inputChanged = i == 0)
            true
        }

        complementSwitch.setOnClickListener {
            calculate(inputChanged = false)
        }
        for (i in 0..1) baseTexts[i].setOnClickListener {
            toastRangeHint(i, true)
        }

        for (i in 0..3) textOutputs[i].setOnClickListener {
            if (textOutputs[i].text != "") copyToInput(lastQNumber, textOutputs[i].text, baseBars[1].progress + 2, numSystems[1],
                complementSwitch.isChecked, roman = i == 1 && !textOutputs[1].text.contains('/'), switchBases = true)
        }
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager?
        for (i in 0..3) textOutputs[i].setOnLongClickListener {
            if (textOutputs[i].text != "") {
                clipboard?.setPrimaryClip(ClipData.newPlainText(null, textOutputs[i].text.removeSurrounding("\"")))
                Toast.makeText(applicationContext, getString(R.string.to_clipboard), Toast.LENGTH_SHORT).show()
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
                if (editInput.text.toString() != "" && lastQNumber.error == 0) historyList.add(lastQNumber)
                editInput.selectAll()
                true
            } else false
        }

        clearButton.setOnClickListener {
            if (editInput.text.toString() != "" && lastQNumber.error == 0 &&
                (historyList.size == 0 || historyList.last().toString(withBase = true) != lastQNumber.toString(withBase = true)))
                    historyList.add(lastQNumber)
            editInput.setText("")
        }
        clearButton.setOnLongClickListener {
            for (i in 0..1) setBaseAndSystem(i, 10, NumSystem.STANDARD)
            complementSwitch.isChecked = false
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
                for (q in historyList) itemsList.add(q.toString(withBase = true))
                val historyDialog = AlertDialog.Builder(this)
                historyDialog.setTitle(R.string.menu_history)
                if (itemsList.isNotEmpty()) {
                    historyDialog.setSingleChoiceItems(itemsList.toTypedArray(), -1) { dialog, which ->
                        if (editInput.text.toString() != "" && lastQNumber.error == 0) historyList.add(lastQNumber)
                        copyToInput(historyList[which])
                        historyList.removeAt(which)
                        dialog.dismiss()
                    }
                    historyDialog.setNeutralButton(R.string.clear_history) { dialog, _ ->
                        dialog.dismiss()
                        AlertDialog.Builder(this)
                            .setTitle(R.string.clear_history_q)
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
                    OneTimeBuzzer(baseFrequency, 1.5).play()
                    Timer().schedule(1500) { OneTimeBuzzer(baseFrequency * ratio, 1.5).play() }
                    Timer().schedule(3000) {
                        OneTimeBuzzer(baseFrequency, 2.0).play()
                        OneTimeBuzzer(baseFrequency * ratio, 2.0).play()
                    }
                    lastQNumber.toInterval(resources)
                } else getString(R.string.no_interval,
                    QNumber(ONE, 128.toBigInteger(), baseBars[0].progress + 2, numSystems[0], format = QFormat.FRACTION).toString(),
                    QNumber(128.toBigInteger(), ONE, baseBars[0].progress + 2, numSystems[0]).toString(withBase = true))
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
        apostrophus = (preferences.getString("apostrophus", null))?.toIntOrNull() ?: 0
        warnNonstandardInput = preferences.getBoolean("wrongDigits", true)
        val textSize = (preferences.getString("size", null))?.toFloatOrNull() ?: 20f
        editInput.textSize = textSize
        for (i in 0..3) textOutputs[i].textSize = textSize
        clearButton.size = if (textSize < 25) FloatingActionButton.SIZE_MINI else FloatingActionButton.SIZE_NORMAL
        (preferences.getStringSet("buttons", null) ?: defaultButtons).let {
            for (j in buttonBases.indices) {
                val visibility = if (buttonBases[j].toString() in it) View.VISIBLE else View.GONE
                toggleButtons[j].visibility = visibility
                toggleButtons[j + buttonBases.size].visibility = visibility
            }
        }
        if (historyList.size == 0) for (i in 0 until preferences.getInt("historySize", 0))
            QNumber(preferencesEntry = preferences.getString("history$i", null) ?: "").let { if (it.error == 0) historyList.add(it) }
        complementSwitch.isChecked = preferences.getBoolean("outComplement", false)
        setBaseAndSystem(0, preferences.getInt("inBase",  10),
            try { NumSystem.valueOf(preferences.getString("inSystem",  null) ?: "") } catch (e: Exception) { NumSystem.STANDARD })
        setBaseAndSystem(1, preferences.getInt("outBase",  10),
            try { NumSystem.valueOf(preferences.getString("outSystem", null) ?: "") } catch (e: Exception) { NumSystem.STANDARD })
        editInput.setText(preferences.getString("input", "1_5/6"))
        showWhatsNewStar = preferences.getString("version", null)?: "" < packageManager.getPackageInfo(packageName, 0).versionName &&
            preferences.getString("input", null) != null /* don’t show if there was no old version */
    }

    override fun onPause() {
        super.onPause()
        val editor = preferences.edit()
        val prunedSize = historyList.size.coerceAtMost(taxiCab)
        for (i in prunedSize until preferences.getInt("historySize", 0)) editor.remove("history$i")
        editor.putInt("historySize", prunedSize)
        for (i in 0 until prunedSize) editor.putString("history$i", historyList[historyList.size - prunedSize + i].toSaveString())
        editor.putString("input", editInput.text.toString())
        editor.putInt("inBase",  baseBars[0].progress + 2)
        editor.putInt("outBase", baseBars[1].progress + 2)
        editor.putString("inSystem",  numSystems[0].toString())
        editor.putString("outSystem", numSystems[1].toString())
        editor.putBoolean("outComplement", complementSwitch.isChecked)
        if (!showWhatsNewStar) editor.putString("version", packageManager.getPackageInfo(packageName, 0).versionName)
        editor.apply()
    }

    /*  C a l c u l a t e  */

    fun calculate(inputChanged: Boolean) {
        val q = if (inputChanged) QNumber(editInput.text.toString(), baseBars[0].progress + 2, numSystems[0])
            else lastQNumber.copy()
        if (inputChanged) {
            if (warnNonstandardInput) editInput.setTextColor(ContextCompat.getColor(applicationContext,
                if (q.nonstandardInput) R.color.orange_light else resolveColor(R.attr.editTextColor)))
            lastQNumber = q.copy()
        }
        q.changeBase(baseBars[1].progress + 2, numSystems[1], complementSwitch.isChecked)

        textOutputs[0].text = if (q.error != 0)
            when (q.error) {
                0x22 -> R.string.err_quote
                0x5B, 0x5D -> R.string.err_bracket
                0x7B, 0x7D -> R.string.err_brace
                0x3B -> R.string.err_semicolon
                0x2C -> R.string.err_comma
                0x5F -> R.string.err_underscore
                0x2F -> R.string.err_slash
                0x2E -> R.string.err_twoPoints
                0x27 -> R.string.err_twoReps
                in 0x23..0x26, 0x40, 0xA3, 0xA5, 0x20AC, 0x2D -> R.string.err_baseTokenOrMinus
                0x221E -> R.string.err_infinity
                0x7121 -> R.string.err_undefined
                0x3BB -> R.string.err_empty
                0x28, 0x29, in 0x2180..0x2184, 0x2187, 0x2188, 0x3A, 0xB7, 0x2234, 0x2237, 0x2059 ->
                    if (numSystems[0] == NumSystem.ROMAN) R.string.err_noRoman else R.string.err_onlyRoman
                in 0x41..0x5A, in 0x61..0x7A -> R.string.err_noRoman
                else -> R.string.err_generic
            }.let {
                if (it == R.string.err_noRoman) "\"${editInput.text}\" " + getString(it) else
                    "\"${q.error.toChar()}\" (${q.errorCode()}) " + getString(it)
            }
            else if (q.system !in NumSystem.BIJECTIVE_1..NumSystem.BIJECTIVE_A || q.denominator <= ONE) q.toPositional() else ""

        textOutputs[1].text = if (q.denominator > ONE) q.toFraction() else
            if (q.system != NumSystem.ROMAN) q.toRoman(showNonPositive = false) else ""
                /* textOutputs[1].onClickListener relies on here being either a Roman or a fraction */

        textOutputs[2].text = if (q.denominator > ONE) {
            if (q.numerator.abs() > q.denominator) q.toMixed() else ""
        } else q.toUnicode()

        textOutputs[3].text = if (q.denominator > ONE) q.toContinued() else when (q.numerator) {
            hg2g.toBigInteger() -> "🐋💐"
            taxiCab.toBigInteger() -> "🚕"
            monster -> "👾"
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

    private fun setBaseAndSystem(i: Int, base: Int, system: NumSystem) {
        numSystems[i] = system
        systemButtons[i].text = system.short
        baseBars[i].progress = base - 2
        baseAndSystemFeedback(i)
    }

    private fun baseAndSystemFeedback(i: Int) {
        val base = baseBars[i].progress + 2
        val baseAllowed = allowedBase(base, numSystems[i]) == base
        baseTexts[i].text = getString(R.string.base, resources.getStringArray(R.array.base_inout)[i], base,
            resources.getStringArray(R.array.num_systems)[if (baseAllowed) numSystems[i].ordinal else 0])
        for (j in buttonBases.indices) toggleButtons[buttonBases.size * i + j].isChecked = buttonBases[j] == base
        systemButtons[i].setBackgroundColor(ContextCompat.getColor(applicationContext,
            if (baseAllowed) resolveColor(R.attr.colorPrimaryVariant) else R.color.grey))
        val actualNumSystem = if (baseAllowed) numSystems[i] else NumSystem.STANDARD
        if (i == 0) editInput.typeface = if (actualNumSystem == NumSystem.ROMAN) Typeface.SERIF else Typeface.DEFAULT
        if (i == 1) complementSwitch.isEnabled = actualNumSystem != NumSystem.BALANCED
        toastRangeHint(i)
    }

    private fun toastRangeHint(i: Int, always: Boolean = false) {
        if (always || (preferences.getBoolean("range", true) && lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))) {
            val base = baseBars[i].progress + 2
            val minDigit = minDigit(base, numSystems[i])
            rangeToast?.cancel()
            rangeToast = Toast.makeText(applicationContext, getString(
                if (numSystems[i] == NumSystem.ROMAN && base == 10) R.string.range_toast_roman else R.string.range_toast,
                resources.getStringArray(R.array.base_inout)[i],
                digitToChar(minDigit, base, numSystems[i]), digitToChar(minDigit + base - 1, base, numSystems[i])), Toast.LENGTH_SHORT)
            rangeToast?.setGravity(Gravity.TOP, 0, 0)
            rangeToast?.show()
        }
    }

    private fun copyToInput(q: QNumber, st: CharSequence = q.toString(), base: Int = q.base, system: NumSystem = q.system,
                complement: Boolean = q.complement, roman: Boolean = false, switchBases: Boolean = false) {
        if (switchBases && lastQNumber.numerator < ZERO) complementSwitch.isChecked = lastQNumber.complement
        editInput.setText(if (st.endsWith('…')) {
            val toast = Toast.makeText(applicationContext, getString(R.string.to_fraction), Toast.LENGTH_SHORT)
            toast.view.findViewById<TextView>(android.R.id.message).gravity = Gravity.CENTER
            toast.show()
            q.changeBase(base, system, complement)
            q.toMixed()
        } else st)
        if (!(editInput.text.startsWith('"') && editInput.text.endsWith('"'))) {
            if (switchBases) setBaseAndSystem(1, baseBars[0].progress + 2, numSystems[0])
            setBaseAndSystem(0, if (roman) 10 else base, if (roman) NumSystem.ROMAN else system)
            calculate(inputChanged = true)
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