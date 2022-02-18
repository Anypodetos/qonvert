package org.tessoft.qonvert

/*
Copyright 2020, 2021, 2022 Anypodetos (Michael Weber)

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
import android.content.res.Resources
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.TypedValue
import android.view.*
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
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

enum class ThemeId {
    UNSET, CLASSIC, BLUE
}

@SuppressLint("UseSwitchCompatOrMaterialCode")
class MainActivity : AppCompatActivity() {

    private lateinit var preferences: SharedPreferences
    private var showRange = true
    private var warnNonstandardInput = true
    private var showWhatsNewStar = true
    private val numSystems = arrayOf(NumSystem.STANDARD, NumSystem.STANDARD)
    private var lastQNumber = QNumber()
    private var rangeToast: Toast? = null

    private lateinit var toggleButtons: Array<ToggleButton>
    private lateinit var systemButtons: Array<Button>
    private lateinit var complementSwitch: Switch
    private lateinit var baseTexts: Array<TextView>
    private lateinit var baseBars: Array<SeekBar>
    private lateinit var outputView: ScrollView
    private lateinit var outputLayout: ConstraintLayout
    private lateinit var textOutputs: Array<TextView>
    private lateinit var editInput: EditText
    private lateinit var clearButton: FloatingActionButton
    private lateinit var keyboardButton: FloatingActionButton

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {

        preferences = PreferenceManager.getDefaultSharedPreferences(this)
        setQonvertTheme(this, if (themeId == ThemeId.UNSET) preferences.getString("theme", "LA") ?: "LA" else "")
        appTheme = theme

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setSupportActionBar(findViewById(R.id.toolbar))

        if (numSystemsSuper[0] == null) resources.getStringArray(R.array.num_systems_super).let {
            for (i in numSystemsSuper.indices) numSystemsSuper[i] = it[i]
        }

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
        textOutputs = arrayOf(
            findViewById(R.id.textOutput0),
            findViewById(R.id.textOutput1),
            findViewById(R.id.textOutput2),
            findViewById(R.id.textOutput3),
            findViewById(R.id.textOutput4),
        )
        editInput = findViewById(R.id.editInput)
        clearButton = findViewById(R.id.clearButton)
        //keyboardButton = findViewById(R.id.keyboardButton)

        /*   I n t e r f a c e   */

        for (i in 0..1) baseBars[i].setOnSeekBarChangeListener(object : OnSeekBarChangeListener {
            override fun onStartTrackingTouch(seekBar: SeekBar) { }
            override fun onStopTrackingTouch(seekBar: SeekBar) { }
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser) calculate(inputChanged = i == 0)
                baseAndSystemFeedback(i)
            }
        })

        val toggleButtonGestureDetectors = arrayOfNulls<GestureDetector>(2)
        for (i in 0..1) toggleButtonGestureDetectors[i] = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(e1: MotionEvent?, e2: MotionEvent?, velocityX: Float, velocityY: Float): Boolean {
                baseBars[i].progress += velocityX.sign.toInt()
                calculate(inputChanged = i == 0)
                return true
            }
        })
        for (i in 0..1) for (j in BUTTON_BASES.indices) toggleButtons[BUTTON_BASES.size * i + j].setOnTouchListener { _, event ->
            toggleButtonGestureDetectors[i]?.onTouchEvent(event)
            return@setOnTouchListener false
        }
        for (i in 0..1) for (j in BUTTON_BASES.indices) toggleButtons[BUTTON_BASES.size * i + j].setOnClickListener {
            baseBars[i].progress = BUTTON_BASES[j] - 2
            toggleButtons[BUTTON_BASES.size * i + j].isChecked = true /* push down again in case it was down before */
            calculate(inputChanged = i == 0)
        }

        for (i in 0..1) systemButtons[i].setOnClickListener { view ->
            val popupMenu = PopupMenu(this, view)
            for ((j, res) in resources.getStringArray(R.array.num_systems).withIndex())
                popupMenu.menu.add(1, Menu.NONE, j, res).isChecked = j == numSystems[i].ordinal
            popupMenu.menu.setGroupCheckable(1, true, true)
            popupMenu.setOnMenuItemClickListener { item: MenuItem ->
                val sys = NumSystem.values()[item.order]
                setBaseAndSystem(i, allowedBase(baseBars[i].progress + 2, sys), sys, recalculate = true)
                true
            }
            popupMenu.show()
        }
        for (i in 0..1) systemButtons[i].setOnLongClickListener {
            it.playSoundEffect(SoundEffectConstants.CLICK)
            val sys = if (numSystems[i] == NumSystem.STANDARD) NumSystem.BALANCED else NumSystem.STANDARD
            setBaseAndSystem(i, allowedBase(baseBars[i].progress + 2, sys), sys, recalculate = true)
            true
        }

        complementSwitch.setOnClickListener {
            calculate(inputChanged = false)
        }
        for (i in 0..1) baseTexts[i].setOnClickListener {
            toastRangeHint(i, true)
        }

        for (i in 0..4) textOutputs[i].setOnClickListener {
            val roman = i == 1 && textOutputs[1].typeface == Typeface.SERIF
            copyToInput(lastQNumber, textOutputs[i].text,
                if (roman) 10 else baseBars[1].progress + 2, if (roman) NumSystem.ROMAN else numSystems[1],
                complementSwitch.isChecked, switchBases = true)
        }
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager?
        for (i in 0..4) textOutputs[i].setOnLongClickListener { view ->
            view.playSoundEffect(SoundEffectConstants.CLICK)
            val text = textOutputs[i].text.toString().removeSurrounding("\"")
            val (prettyText, prettyMenuTitle) = pretty(text)
            if (prettyMenuTitle == 0) {
                clipboard?.setPrimaryClip(ClipData.newPlainText(null, text))
                Toast.makeText(applicationContext, getString(R.string.clipboard_ok), Toast.LENGTH_SHORT).show()
            } else {
                val popupMenu = PopupMenu(this, view)
                popupMenu.menu.add(Menu.NONE, Menu.NONE, 0, getString(R.string.clipboard_asIs))
                popupMenu.menu.add(Menu.NONE, Menu.NONE, 1, getString(prettyMenuTitle))
                popupMenu.setOnMenuItemClickListener { item: MenuItem ->
                    clipboard?.setPrimaryClip(ClipData.newPlainText(null, if (item.order == 0) text else prettyText))
                    Toast.makeText(applicationContext, getString(R.string.clipboard_ok), Toast.LENGTH_SHORT).show()
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
                if (editInput.text.isNotBlank() && lastQNumber.isValid) historyList.add(QNumberEntry(editInput.text.toString(), lastQNumber))
                editInput.selectAll()
                resources.configuration.orientation != Configuration.ORIENTATION_LANDSCAPE
            } else false
        }

        clearButton.setOnClickListener {
            if (editInput.text.isNotBlank() && lastQNumber.isValid) {
                if (historyList.lastOrNull()?.let {
                    it.inputString.trim() == editInput.text.toString().trim() && it.number.base == baseBars[0].progress + 2 && it.number.system == numSystems[0]
                } == true) historyList.removeLastOrNull()
                historyList.add(QNumberEntry(editInput.text.toString(), lastQNumber))
            }
            editInput.setText("")
        }
        clearButton.setOnLongClickListener {
            it.playSoundEffect(SoundEffectConstants.CLICK)
            complementSwitch.isChecked = false
            for (i in 1 downTo 0) setBaseAndSystem(i, 10, NumSystem.STANDARD, recalculate = i == 0)
            true
        }
        //keyboardButton.setOnClickListener {
        //    setNumKeyboard(show = true)
        //}
    }

    /*   O t h e r   e v e n t s   */

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        if (showWhatsNewStar) {
            menu.findItem(R.id.intervalItem).setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
            menu.findItem(R.id.whatsNewItem).setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
        }
        return true
    }
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.allFormatsItem -> if (lastQNumber.isValid) {
                val intent = Intent(this, ListActivity::class.java)
                val q = lastQNumber.copy()
                q.changeBase(baseBars[1].progress + 2, numSystems[1], complementSwitch.isChecked)
                intent.putExtra("list", "m" + q.toPreferencesString() + "/")
                startActivity(intent)
            } else AlertDialog.Builder(this)
                    .setMessage(R.string.no_q_number)
                    .setNegativeButton(R.string.close) { _, _ -> }
                    .create().show()
            R.id.historyItem -> if (historyList.isNotEmpty()) {
                val intent = Intent(this, ListActivity::class.java)
                intent.putExtra("list", "H")
                startActivity(intent)
            } else AlertDialog.Builder(this)
                    .setMessage(R.string.no_history)
                    .setNegativeButton(R.string.close) { _, _ -> }
                    .create().show()
            R.id.intervalItem -> {
                val ratio = abs(lastQNumber.numerator.toDouble() / lastQNumber.denominator.toDouble())
                val message = if (ratio in 1/128.0..128.0) {
                    val baseFrequency = 440 / 1.5.pow(round(ln(ratio) / ln(1.5) / 2))
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
                if (message.isNotBlank()) {
                    val toast = Toast.makeText(applicationContext, message, Toast.LENGTH_LONG)
                    toast.view.findViewById<TextView>(android.R.id.message).gravity = Gravity.CENTER
                    toast.show()
                }
            }
            R.id.settingsItem -> startActivity(Intent(this, SettingsActivity::class.java))
            R.id.helpItem, R.id.cheatSheetItem, R.id.whatsNewItem, R.id.aboutItem -> {
                val intent = Intent(this, HelpActivity::class.java)
                intent.putExtra("help", item.itemId)
                if (item.itemId == R.id.whatsNewItem) showWhatsNewStar = false
                startActivity(intent)
            }
        }
        return true
    }

    override fun onResume() {
        super.onResume()
        getOutputSettings(preferences)
        showRange = preferences.getBoolean("range", true)
        warnNonstandardInput = preferences.getBoolean("wrongDigits", true)
        val textSize = (preferences.getString("size", null))?.toFloatOrNull() ?: 20f
        editInput.textSize = textSize
        for (i in 0..3) textOutputs[i].textSize = textSize
        clearButton.size = if (textSize < 25) FloatingActionButton.SIZE_MINI else FloatingActionButton.SIZE_NORMAL
        val buttons = preferences.getStringSet("buttons", null) ?: DEFAULT_BUTTONS
        for (j in BUTTON_BASES.indices) {
            val visibility = if (BUTTON_BASES[j].toString() in buttons) View.VISIBLE else View.GONE
            toggleButtons[j].visibility = visibility
            toggleButtons[j + BUTTON_BASES.size].visibility = visibility
        }
        getHistory(preferences, historyList)
        //setNumKeyboard(preferences.getInt("keyboard", 0) != 0)
        complementSwitch.isChecked = preferences.getBoolean("outComplement", false)
        setBaseAndSystem(0, preferences.getInt("inBase", 10),
            try { NumSystem.valueOf(preferences.getString("inSystem",  null) ?: "") } catch (e: Exception) { NumSystem.STANDARD }, recalculate = false)
        setBaseAndSystem(1, preferences.getInt("outBase", 10),
            try { NumSystem.valueOf(preferences.getString("outSystem", null) ?: "") } catch (e: Exception) { NumSystem.STANDARD }, recalculate = false)
        editInput.setText(preferences.getString("input", "1_5/6"))
        try { editInput.setSelection(preferences.getInt("selStart", 0), preferences.getInt("selEnd", 0)) } catch (e: Exception) { }
        preferences.getString("listInput", null)?.let { listInput ->
            if (editInput.text.isNotBlank() && lastQNumber.isValid) historyList.add(QNumberEntry(editInput.text.toString(), lastQNumber))
            if (listInput.isNotEmpty()) when (listInput[0]) {
                'H' -> listInput.substring(1).toIntOrNull()?.let {
                    with(historyList[it]) {
                        copyToInput(number, inputString)
                        historyList.remove(this)
                    }
                }
                'I' -> listInput.substring(1).toIntOrNull()?.let {
                    with(INTERVALS[it]) { copyToInput(QNumber(first, second, baseBars[0].progress + 2, numSystems[0], format = QFormat.FRACTION)) }
                }
                in 'a'..'z' -> with(QNumber(preferencesEntry = listInput.substring(1))) {
                    val split = listInput.substring(1).split('/')
                    copyToInput(this, toString(aEgyptianMethod = try { EgyptianMethod.valueOf(split[split.size - 2]) }
                        catch (e: Exception) { EgyptianMethod.OFF } ), switchBases = listInput[0] == 'm')
                    if (listInput[0] == 'h') try { historyList.removeAt(split[split.size - 1].toInt()) } catch (e: Exception) { }
                }
            }
        }
        showWhatsNewStar = preferences.getInt("version", 0) < BuildConfig.VERSION_CODE &&
            preferences.getString("input", null) != null /* don’t show star if there was no old version */
    }

    override fun onPause() {
        super.onPause()
        val editor = preferences.edit()
        putHistory(preferences, editor, historyList)
        editor.putString("input", editInput.text.toString())
        editor.putInt("selStart", editInput.selectionStart)
        editor.putInt("selEnd", editInput.selectionEnd)
        editor.putInt("inBase", baseBars[0].progress + 2)
        editor.putInt("outBase", baseBars[1].progress + 2)
        editor.putString("inSystem", numSystems[0].toString())
        editor.putString("outSystem", numSystems[1].toString())
        editor.putBoolean("outComplement", complementSwitch.isChecked)
        //editor.putInt("keyboard", if (getNumKeyboard()) 1 else 0)
        editor.remove("listInput")
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
        textOutputs[0].text = if (q.usefulFormat(QFormat.POSITIONAL)) q.toPositional() else
            (if (!q.isValid) q.errorMessage(resources) else "")
        textOutputs[1].text = if (q.usefulFormat(QFormat.FRACTION)) q.toFraction() else
            (if (q.usefulFormat(QFormat.ROMAN_NATURAL)) q.toRoman() else "")
        textOutputs[2].text = if (q.usefulFormat(QFormat.MIXED)) q.toMixed() else
            (if (q.usefulFormat(QFormat.UNICODE)) q.toUnicode() else "")
        textOutputs[3].text = if (q.usefulFormat(QFormat.CONTINUED)) q.toContinued() else when (q.numerator) {
            HG2G.toBigInteger() -> "🐋💐"
            TAXICAB.toBigInteger() -> "🚕"
            MONSTER -> "👾"
            else -> ""
        }
        textOutputs[4].text = if (q.usefulFormat(QFormat.EGYPTIAN)) q.toEgyptian(egyptianMethod) else ""

        for (i in 0..4) {
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
        systemButtons[i].text = resources.getStringArray(R.array.num_systems_short)[system.ordinal]
        baseBars[i].progress = base - 2
        if (recalculate) calculate(inputChanged = i == 0)
        baseAndSystemFeedback(i)
    }

    private fun baseAndSystemFeedback(i: Int) {
        val base = baseBars[i].progress + 2
        val baseAllowed = allowedBase(base, numSystems[i]) == base
        val actualNumSystem = if (baseAllowed) numSystems[i] else NumSystem.STANDARD
        baseTexts[i].text = getString(R.string.base, resources.getStringArray(R.array.base_inOut)[i], base,
            resources.getStringArray(R.array.num_systems)[actualNumSystem.ordinal])
        for (j in BUTTON_BASES.indices) toggleButtons[BUTTON_BASES.size * i + j].isChecked = BUTTON_BASES[j] == base
        systemButtons[i].setBackgroundColor(ContextCompat.getColor(applicationContext,
            if (baseAllowed) resolveColor(R.attr.colorPrimaryVariant) else R.color.grey))
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
                else R.string.range_toast, resources.getStringArray(R.array.base_inOut)[i],
                digitToChar(minDigit, base, numSystems[i]), digitToChar(minDigit + base - 1, base, numSystems[i])), Toast.LENGTH_SHORT)
            rangeToast?.setGravity(Gravity.TOP, 0, 0)
            rangeToast?.show()
        }
    }

    private fun copyToInput(q: QNumber, st: CharSequence = q.toString(), base: Int = q.base, system: NumSystem = q.system,
                complement: Boolean = q.complement, switchBases: Boolean = false) {
        if (switchBases && lastQNumber.numerator < ZERO) complementSwitch.isChecked = lastQNumber.complement
        val rounded = st.endsWith("…") || st.endsWith("…}")
        val historyDependent = '?' in st && !st.trimStart().startsWith('"')
        editInput.setText(if (rounded || historyDependent) {
            val toast = Toast.makeText(applicationContext, getString(if (rounded) R.string.to_fraction else R.string.to_history_indep),
                Toast.LENGTH_SHORT)
            toast.view.findViewById<TextView>(android.R.id.message).gravity = Gravity.CENTER
            toast.show()
            q.changeBase(base, system, complement)
            if (rounded) q.toMixed() else q.toString()
        } else st)
        if (!editInput.text.trimStart().startsWith('"')) {
            if (switchBases) setBaseAndSystem(1, baseBars[0].progress + 2, numSystems[0], recalculate = false)
            setBaseAndSystem(0, base, system, recalculate = true)
        }
    }

    private fun getNumKeyboard() = editInput.inputType and InputType.TYPE_CLASS_DATETIME > 0
    private fun setNumKeyboard(toNum: Boolean = !getNumKeyboard(), show: Boolean = false) {
        editInput.setTextIsSelectable(toNum) // test (old Android versions)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) editInput.showSoftInputOnFocus = !toNum
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        if (toNum) imm.hideSoftInputFromWindow(editInput.windowToken, 0)
        if (show) {
            if (toNum) // show custom keyboard
                else imm.showSoftInput(editInput, InputMethodManager.SHOW_IMPLICIT)
        }
   }

    companion object {
        val historyList = mutableListOf<QNumberEntry>()
        var maxDigitsAfter = 0
        var groupDigits = false
        var lowerDigits = false
        var apostrophus = 0
        var egyptianMethod = EgyptianMethod.BINARY
        val numSystemsSuper = arrayOfNulls<String>(NumSystem.values().size)

        fun getOutputSettings(preferences: SharedPreferences) {
            maxDigitsAfter = (preferences.getString("digits", null))?.toIntOrNull() ?: 300
            groupDigits = preferences.getBoolean("group", false)
            lowerDigits = preferences.getBoolean("lowercase", false)
            apostrophus = ((preferences.getString("apostrophus", null))?.toIntOrNull() ?: 0).coerceIn(0..3)
            egyptianMethod = try { EgyptianMethod.valueOf(preferences.getString("egyptian", null) ?: "") } catch (e: Exception) { EgyptianMethod.BINARY }
        }

        var appTheme: Resources.Theme? = null
        fun resolveColor(id: Int): Int {
            val typedValue = TypedValue()
            appTheme?.resolveAttribute(id, typedValue, true)
            return typedValue.resourceId
        }

        fun tokenBaseSystem(token: Char): Pair<Int, NumSystem>? = // could be made customisable
            when (token) {
                '@' -> Pair(2, NumSystem.STANDARD)
                '#' -> Pair(8, NumSystem.STANDARD)
                '$', '€', '£', '¥' -> Pair(10, NumSystem.STANDARD)
                '%' -> Pair(12, NumSystem.STANDARD)
                '&' -> Pair(16, NumSystem.STANDARD)
                else -> null
            }

        var themeId = ThemeId.UNSET
            private set
        fun setQonvertTheme(activity: Activity, theme: String = "") {
            if (theme != "") {
                AppCompatDelegate.setDefaultNightMode(when (theme) {
                    in "L".."LZ" -> AppCompatDelegate.MODE_NIGHT_NO
                    in "D".."DZ" -> AppCompatDelegate.MODE_NIGHT_YES
                    else         -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                })
                themeId = if (theme.endsWith('B')) ThemeId.BLUE else ThemeId.CLASSIC
            }
            activity.setTheme(if (themeId == ThemeId.BLUE) R.style.Theme_QonvertBlue else R.style.Theme_Qonvert)
        }
    }
}

fun getHistory(preferences: SharedPreferences, historyList: MutableList<QNumberEntry>) {
    historyList.clear()
    for (i in 0 until preferences.getInt("historySize", 0))
        with(QNumber(preferencesEntry = preferences.getString("history$i", null) ?: "")) {
            if (isValid) historyList.add(QNumberEntry(preferences.getString("historyInput$i", null) ?: "", this))
    }
}
fun putHistory(preferences: SharedPreferences, editor: SharedPreferences.Editor, historyList: MutableList<QNumberEntry>) {
    val prunedSize = historyList.size.coerceAtMost(TAXICAB)
    for (i in prunedSize until preferences.getInt("historySize", 0)) {
        editor.remove("historyInput$i")
        editor.remove("history$i")
    }
    editor.putInt("historySize", prunedSize)
    for (i in 0 until prunedSize) {
        editor.putString("historyInput$i", historyList[historyList.size - prunedSize + i].inputString)
        editor.putString("history$i", historyList[historyList.size - prunedSize + i].number.toPreferencesString())
    }
}

fun shareText(activity: Activity?, text: String, title: String? = null) {
    Intent().apply {
        action = Intent.ACTION_SEND
        putExtra(Intent.EXTRA_TEXT, text)
        type = "text/plain"
        activity?.startActivity(Intent.createChooser(this, title))
    }
}

fun pretty(text: String): Pair<String, Int> {
    var prettyText = text
    var prettyMenuTitle = 0
    val rep = prettyText.indexOf('\'')
    if (rep > -1) {
        val s = StringBuilder(prettyText).deleteAt(rep)
        for (i in s.length downTo rep + 1) if (s[i - 1] != ' ') s.insert(i, '\u0305')
        prettyText = s.toString()
        prettyMenuTitle = R.string.clipboard_rep
    }
    val slash = prettyText.indexOf('/')
    val under = prettyText.indexOf('_')
    if (slash > -1 && prettyText.substring(under + 1).all { it in '0'..'9' || it in "/-. " }) {
        prettyText = "_${prettyText}_"
        for ((unicode, frac) in FRACTION_CHARS)
            prettyText = prettyText.replace("_${frac.first}/${frac.second}_", unicode.toString())
                                   .replace("-${frac.first}/${frac.second}_", "⁻$unicode")
        prettyText = prettyText.removePrefix("_").removeSuffix("_")
        if (prettyText == text) prettyText = (if (under > -1) prettyText.substring(0, under) else "") +
                prettyText.substring(under + 1, slash).map {
            when (it) {
                in '0'..'9' -> SUPERSCRIPT_DIGITS[it.toInt() - 48]
                '-' -> '⁻'
                '.' -> '·'
                else -> '\u202F' /* narrow space */
                }
            }.joinToString("") + '⁄' + prettyText.substring(slash + 1).map {
                if (it in '0'..'9') SUBSCRIPT_DIGITS[it.toInt() - 48] else '\u202F'
            }.joinToString("")
        prettyMenuTitle = R.string.clipboard_fraction
    }

    // That's not Pretty but Compatible
    /*if (prettyText == "" && i == 0 && numSystems[1] !in setOf(NumSystem.BIJECTIVE_1, NumSystem.BIJECTIVE_A, NumSystem.ROMAN) &&
            text.startsWith("..")) {
        val q = lastQNumber.copy()
        q.changeBase(baseBars[1].progress + 2, numSystems[1], true)
        prettyText = q.toPositional(intDigits = ...) //////////////
        prettyMenuTitle = R.string.clipboard_complement
    }*/
    return Pair(prettyText, prettyMenuTitle)
}
