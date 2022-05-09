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
import androidx.core.view.MenuCompat
import androidx.lifecycle.Lifecycle
import androidx.preference.PreferenceManager
import com.google.android.material.floatingactionbutton.FloatingActionButton
import java.math.BigInteger
import java.math.BigInteger.*
import java.util.*
import kotlin.math.*

const val HG2G = 42
const val TAXICAB = 1729
val MONSTER = BigInteger("808017424794512875886459904961710757005754368000000000")
val BUTTON_BASES = listOf(2, 3, 6, 8, 10, 12, 16, 20, 26)
val DEFAULT_BUTTONS = listOf(2, 8, 10, 12, 16)
val THEMES = mapOf('A' to R.style.Theme_Qonvert, 'B' to R.style.Theme_QonvertBlue)

enum class KeyboardId {
    ANDROID, CUSTOM
}

@SuppressLint("UseSwitchCompatOrMaterialCode")
class MainActivity : AppCompatActivity() {

    private lateinit var preferences: SharedPreferences
    private lateinit var showNaturalStrings: Array<String>
    private var showNatural = setOf<String>()
    private var showRange = true
    private var warnNonstandardInput = true
    private var keyboardId = KeyboardId.CUSTOM
    private var showWhatsNewStar = true
    private val numSystems = arrayOf(NumSystem.STANDARD, NumSystem.STANDARD)
    private var lastQNumber = QNumber()
    private var rangeToast: Toast? = null

    private lateinit var toggleButtons: List<ToggleButton>
    private lateinit var systemButtons: List<Button>
    private lateinit var complementSwitch: Switch
    private lateinit var baseTexts: List<TextView>
    private lateinit var baseBars: List<SeekBar>
    private lateinit var outputView: ScrollView
    private lateinit var outputLayout: ConstraintLayout
    private lateinit var textOutputs: List<TextView>
    lateinit var editInput: EditInput
        private set
    private lateinit var clearButton: FloatingActionButton
    private lateinit var keyboard: KeyboardView

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {

        preferences = PreferenceManager.getDefaultSharedPreferences(this)
        setQonvertTheme(this, if (themeId == 0) preferences.getString("theme", "LA") ?: "LA" else "")
        appTheme = theme

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setSupportActionBar(findViewById(R.id.toolbar))

        if (numSystemsSuper[0] == "") with(resources.getStringArray(R.array.num_systems_super)) {
            for (i in numSystemsSuper.indices) numSystemsSuper[i] = this[i]
        }

        showNaturalStrings = resources.getStringArray(R.array.natural_values)

        toggleButtons = listOf(
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
        systemButtons = listOf(
            findViewById(R.id.inSystemButton),
            findViewById(R.id.outSystemButton)
        )
        complementSwitch = findViewById(R.id.complementSwitch)
        baseTexts = listOf(
            findViewById(R.id.inBaseText),
            findViewById(R.id.outBaseText)
        )
        baseBars = listOf(
            findViewById(R.id.inBaseBar),
            findViewById(R.id.outBaseBar)
        )
        outputView = findViewById(R.id.outputView)
        outputLayout = findViewById(R.id.outputLayout)
        textOutputs = listOf(
            findViewById(R.id.textOutput0),
            findViewById(R.id.textOutput1),
            findViewById(R.id.textOutput2),
            findViewById(R.id.textOutput3),
            findViewById(R.id.textOutput4),
        )
        editInput = findViewById(R.id.editInput)
        clearButton = findViewById(R.id.clearButton)
        keyboard = findViewById(R.id.keyboard)
        if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE)
            keyboard.layoutParams.height = (140 * resources.displayMetrics.scaledDensity).toInt()

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
                val system = NumSystem.values()[item.order]
                setBaseAndSystem(i, allowedBase(baseBars[i].progress + 2, system), system, recalculate = true)
                true
            }
            popupMenu.show()
        }
        for (i in 0..1) systemButtons[i].setOnLongClickListener {
            it.playSoundEffect(SoundEffectConstants.CLICK)
            val system = if (numSystems[i] == NumSystem.STANDARD) NumSystem.BALANCED else NumSystem.STANDARD
            setBaseAndSystem(i, allowedBase(baseBars[i].progress + 2, system), system, recalculate = true)
            true
        }

        complementSwitch.setOnClickListener {
            calculate(inputChanged = false)
        }
        for (i in 0..1) baseTexts[i].setOnClickListener {
            toastRangeHint(i, always = true)
        }

        for (i in 0..4) textOutputs[i].setOnClickListener {
            val greekOrRoman = i in 1..2 && textOutputs[i].typeface == Typeface.SERIF && lastQNumber.denominator == ONE
            copyToInput(lastQNumber, textOutputs[i].text.toString(),
                if (greekOrRoman) 10 else baseBars[1].progress + 2, if (greekOrRoman) (if (i == 1) NumSystem.GREEK else NumSystem.ROMAN) else numSystems[1],
                complementSwitch.isChecked, switchBases = true)
        }
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager?
        for (i in 0..4) textOutputs[i].setOnLongClickListener {
            it.playSoundEffect(SoundEffectConstants.CLICK)
            val text = textOutputs[i].text.toString()
            val (prettyText, prettyMenuTitle) = makePretty(text)
            val (compatText, compatMenuTitle) = makeCompatible(text, numSystems[1])
            if (prettyMenuTitle == 0 && compatMenuTitle == 0) {
                clipboard?.setPrimaryClip(ClipData.newPlainText(null, text))
                Toast.makeText(applicationContext, getString(R.string.clipboard_ok), Toast.LENGTH_SHORT).show()
            } else {
                val popupMenu = PopupMenu(this, it)
                popupMenu.menu.add(Menu.NONE, Menu.NONE, 0, getString(R.string.clipboard_asIs))
                if (prettyMenuTitle != 0) popupMenu.menu.add(Menu.NONE, Menu.NONE, 1, getString(prettyMenuTitle))
                if (compatMenuTitle != 0) popupMenu.menu.add(Menu.NONE, Menu.NONE, 2, getString(compatMenuTitle))
                popupMenu.setOnMenuItemClickListener { item: MenuItem ->
                    clipboard?.setPrimaryClip(ClipData.newPlainText(null, when (item.order) {
                        0    -> text
                        1    -> prettyText
                        else -> compatText
                    }))
                    Toast.makeText(applicationContext, getString(R.string.clipboard_ok), Toast.LENGTH_SHORT).show()
                    true
                }
                popupMenu.show()
            }
            true
        }

        editInput.setOnClickListener {
            if (keyboardId == KeyboardId.CUSTOM) keyboard.show()
        }
        editInput.setOnFocusChangeListener { _, focused ->
            if (focused && keyboardId == KeyboardId.CUSTOM) keyboard.show() else keyboard.hide()
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
                if (editInput.string.isNotBlank() && lastQNumber.isValid) historyList.add(QNumberEntry(editInput.string, lastQNumber))
                editInput.selectAll()
                resources.configuration.orientation != Configuration.ORIENTATION_LANDSCAPE
            } else false
        }

        clearButton.setOnClickListener {
            if (editInput.string.isNotBlank() && lastQNumber.isValid) {
                if (historyList.lastOrNull()?.let {
                    it.inputString.trim() == editInput.string.trim() && it.number.base == baseBars[0].progress + 2 && it.number.system == numSystems[0]
                } == true) historyList.removeLastOrNull()
                historyList.add(QNumberEntry(editInput.string, lastQNumber))
            }
            editInput.string = ""
        }
        clearButton.setOnLongClickListener {
            it.playSoundEffect(SoundEffectConstants.CLICK)
            complementSwitch.isChecked = false
            for (i in 1 downTo 0) setBaseAndSystem(i, 10, NumSystem.STANDARD, recalculate = i == 0)
            true
        }
    }

    /*   O t h e r   e v e n t s   */

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        MenuCompat.setGroupDividerEnabled(menu, true)
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
            R.id.intervalItem -> lastQNumber.play(this)
            R.id.intervalListItem -> {
                val intent = Intent(this, ListActivity::class.java)
                intent.putExtra("list", "I")
                startActivity(intent)
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

    override fun onBackPressed() {
        if (keyboard.visibility == View.VISIBLE) keyboard.hide() else super.onBackPressed()
    }

    override fun onResume() {
        super.onResume()
        getOutputSettings(preferences)
        showNatural = preferences.getStringSet("natural", null) ?: showNaturalStrings.toSet()
        showRange = preferences.getBoolean("range", true)
        warnNonstandardInput = preferences.getBoolean("wrongDigits", true)
        val buttons = preferences.getStringSet("buttons", null) ?: DEFAULT_BUTTONS.toSet().map { it.toString() }
        for (j in BUTTON_BASES.indices) {
            val visibility = if (BUTTON_BASES[j].toString() in buttons) View.VISIBLE else View.GONE
            toggleButtons[j].visibility = visibility
            toggleButtons[j + BUTTON_BASES.size].visibility = visibility
        }
        val textSize = (preferences.getString("size", null))?.toFloatOrNull() ?: 20f
        editInput.textSize = textSize
        for (i in 0..4) textOutputs[i].textSize = textSize
        clearButton.size = if (textSize < 25) FloatingActionButton.SIZE_MINI else FloatingActionButton.SIZE_NORMAL

        getHistory(preferences, historyList)
        complementSwitch.isChecked = preferences.getBoolean("outComplement", false)
        setBaseAndSystem(0, preferences.getInt("inBase", 10),
            try { NumSystem.valueOf(preferences.getString("inSystem",  null) ?: "") } catch (e: Exception) { NumSystem.STANDARD },
            recalculate = false, alwaysFeedback = true)
        setBaseAndSystem(1, preferences.getInt("outBase", 10),
            try { NumSystem.valueOf(preferences.getString("outSystem", null) ?: "") } catch (e: Exception) { NumSystem.STANDARD },
            recalculate = false, alwaysFeedback = true)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            keyboardId = if (preferences.getBoolean("customKeyboard", true)) KeyboardId.CUSTOM else KeyboardId.ANDROID
            editInput.showSoftInputOnFocus = keyboardId == KeyboardId.ANDROID
        } else keyboardId = KeyboardId.ANDROID
        if (keyboardId == KeyboardId.CUSTOM) {
            updateKeyboardToCaretPos(always = true)
            keyboard.show()
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
        } else keyboard.hide()
        editInput.string = preferences.getString("input", "1_5/6") ?: ""
        try { editInput.setSelection(preferences.getInt("selStart", 0), preferences.getInt("selEnd", 0)) } catch (e: Exception) { }

        preferences.getString("listInput", null)?.let { listInput ->
            if (editInput.string.isNotBlank() && lastQNumber.isValid) historyList.add(QNumberEntry(editInput.string, lastQNumber))
            if (listInput.isNotEmpty()) when (listInput[0]) {
                'H' -> listInput.substring(1).toIntOrNull()?.let {
                    with(historyList[it]) {
                        copyToInput(number, inputString)
                        historyList.remove(this)
                    }
                }
                'I' -> listInput.substring(1).toIntOrNull()?.let {
                    with(INTERVALS[it % INTERVALS.size]) {
                        copyToInput(QNumber(first * (TWO.pow(it / INTERVALS.size)), second, baseBars[0].progress + 2, numSystems[0], format = QFormat.FRACTION))
                    }
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
        editor.putString("input", editInput.string)
        editor.putInt("selStart", editInput.selectionStart)
        editor.putInt("selEnd", editInput.selectionEnd)
        editor.putInt("inBase", baseBars[0].progress + 2)
        editor.putInt("outBase", baseBars[1].progress + 2)
        editor.putString("inSystem", numSystems[0].toString())
        editor.putString("outSystem", numSystems[1].toString())
        editor.putBoolean("outComplement", complementSwitch.isChecked)
        editor.remove("listInput")
        if (!showWhatsNewStar) editor.putInt("version", BuildConfig.VERSION_CODE)
        editor.apply()
    }

    /*  C a l c u l a t e  */

    fun calculate(inputChanged: Boolean) {
        val q = if (inputChanged) QNumber(editInput.string, baseBars[0].progress + 2, numSystems[0])
            else lastQNumber.copy()
        if (inputChanged) {
            editInput.setTextColor(ContextCompat.getColor(this, when {
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
            (if (showNaturalStrings[0] in showNatural && q.usefulFormat(QFormat.GREEK_NATURAL)) q.toGreek() else "")
        textOutputs[2].text = if (q.usefulFormat(QFormat.MIXED)) q.toMixed() else
            (if (showNaturalStrings[1] in showNatural && q.usefulFormat(QFormat.ROMAN_NATURAL)) q.toRoman() else "")
        textOutputs[3].text = if (q.usefulFormat(QFormat.CONTINUED)) q.toContinued() else
            (if (showNaturalStrings[2] in showNatural && q.usefulFormat(QFormat.UNICODE)) q.toUnicode() else "")
        textOutputs[4].text = if (q.usefulFormat(QFormat.EGYPTIAN)) q.toEgyptian(egyptianMethod) else when (q.numerator) {
            HG2G.toBigInteger() -> "🐋💐"
            TAXICAB.toBigInteger() -> "🚕"
            MONSTER -> "👾"
            else -> ""
        }
        for (i in 0..4) {
            textOutputs[i].typeface = if (when (i) {
                1, 2 -> q.system in setOf(NumSystem.GREEK, NumSystem.ROMAN) || q.denominator == ONE /* natural Greek/Roman output */
                3    -> q.system in setOf(NumSystem.GREEK, NumSystem.ROMAN) && q.denominator >  ONE /* Unicode */
                else -> q.system in setOf(NumSystem.GREEK, NumSystem.ROMAN)
            }) Typeface.SERIF else Typeface.DEFAULT
            textOutputs[i].visibility = if (textOutputs[i].text == "") View.GONE else View.VISIBLE
        }
    }

    /*   U t i l i t i e s   */

    private fun setBaseAndSystem(i: Int, base: Int, system: NumSystem, recalculate: Boolean, alwaysFeedback: Boolean = false) {
        val feedback = alwaysFeedback || base != baseBars[i].progress + 2 || system != numSystems[i]
        numSystems[i] = system
        systemButtons[i].text = resources.getStringArray(R.array.num_systems_short)[system.ordinal]
        baseBars[i].progress = base - 2
        if (recalculate) calculate(inputChanged = i == 0)
        if (feedback) baseAndSystemFeedback(i, base, system)
    }

    private fun baseAndSystemFeedback(i: Int, base: Int = baseBars[i].progress + 2, system: NumSystem = numSystems[i]) {
        val (actualSystem, baseAllowed) = allowedSystem(base, system)
        baseTexts[i].text = getString(R.string.base, resources.getStringArray(R.array.base_inOut)[i], base,
            resources.getStringArray(R.array.num_systems)[actualSystem.ordinal])
        for (j in BUTTON_BASES.indices) toggleButtons[BUTTON_BASES.size * i + j].isChecked = BUTTON_BASES[j] == base
        systemButtons[i].setBackgroundColor(ContextCompat.getColor(this,
            if (baseAllowed) resolveColor(R.attr.colorPrimaryVariant) else R.color.grey))
        if (i == 0) {
            editInput.typeface = if (actualSystem in setOf(NumSystem.GREEK, NumSystem.ROMAN)) Typeface.SERIF else Typeface.DEFAULT
            updateKeyboardToCaretPos(base, actualSystem)
        } else complementSwitch.isEnabled = actualSystem != NumSystem.BALANCED
        toastRangeHint(i, base, system)
    }

    private fun toastRangeHint(i: Int, base: Int = baseBars[i].progress + 2, system: NumSystem = numSystems[i], always: Boolean = false) {
        if (always || (showRange && lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))) {
            val minDigit = minDigit(base, system)
            rangeToast?.cancel()
            rangeToast = Toast.makeText(applicationContext, getString(when {
                    system == NumSystem.GREEK && base == 10 -> if (i == 0) R.string.range_toast_greek else R.string.range_toast_onlyGreek
                    system == NumSystem.ROMAN && base == 10 -> if (i == 0) R.string.range_toast_roman else R.string.range_toast_onlyRoman
                    else -> R.string.range_toast
                },
                resources.getStringArray(R.array.base_inOut)[i], digitToChar(minDigit, base, system), digitToChar(minDigit + base - 1, base, system)),
                Toast.LENGTH_SHORT)
            rangeToast?.setGravity(Gravity.TOP, 0, 0)
            rangeToast?.show()
        }
    }

    fun updateKeyboardToCaretPos(base: Int = baseBars[0].progress + 2, actualSystem: NumSystem = allowedSystem(base, numSystems[0]).first,
                     always: Boolean = false) {
        if (keyboardId == KeyboardId.CUSTOM) keyboard.fillButtons(tokenBaseSystem(editInput.string.substring(0, editInput.selectionStart).findLast {
            it in "_/[{;,@#$€£¥%&"
        }) ?: Pair(base, actualSystem), always)
    }

    private fun copyToInput(q: QNumber, st: String = q.toString(), base: Int = q.base, system: NumSystem = q.system,
                complement: Boolean = q.complement, switchBases: Boolean = false) {
        if (switchBases && lastQNumber.numerator < ZERO) complementSwitch.isChecked = lastQNumber.complement
        val rounded = st.endsWith("…") || st.endsWith("…}")
        val historyDependent = '?' in st && !st.trimStart().startsWith('"')
        editInput.string = if (rounded || historyDependent) {
            val toast = Toast.makeText(applicationContext, getString(if (rounded) R.string.to_fraction else R.string.to_history_indep),
                Toast.LENGTH_SHORT)
            toast.view?.findViewById<TextView>(android.R.id.message)?.gravity = Gravity.CENTER
            toast.show()
            q.changeBase(base, system, complement)
            if (rounded) q.toMixed() else q.toString()
        } else st
        if (switchBases) setBaseAndSystem(1, baseBars[0].progress + 2, numSystems[0], recalculate = false)
        setBaseAndSystem(0, base, system, recalculate = true)
    }

    companion object {
        val historyList = mutableListOf<QNumberEntry>()
        var maxDigitsAfter = 0
        var groupDigits = false
        var lowerDigits = false
        var apostrophus = 0
        var egyptianMethod = EgyptianMethod.BINARY
        private val tokens = Array(DEFAULT_BUTTONS.size) { Pair(DEFAULT_BUTTONS[it], NumSystem.STANDARD) }
        val numSystemsSuper = Array(NumSystem.values().size) { "" }

        fun getOutputSettings(preferences: SharedPreferences) {
            maxDigitsAfter = (preferences.getString("digits", null))?.toIntOrNull() ?: 300
            groupDigits = preferences.getBoolean("group", false)
            lowerDigits = preferences.getBoolean("lowercase", false)
            apostrophus = ((preferences.getString("apostrophus", null))?.toIntOrNull() ?: 0).coerceIn(0..3)
            egyptianMethod = try { EgyptianMethod.valueOf(preferences.getString("egyptian", null) ?: "") }
                catch (e: Exception) { EgyptianMethod.BINARY }
            //for (i in tokens.indices) tokens[i] = Pair(preferences.getString("tokenBase$i", DEFAULT_BUTTONS[i].toString())?.toIntOrNull() ?: tokens[i].first,
            //    try { NumSystem.valueOf(preferences.getString("tokenSystem$i", null) ?: "") } catch (e: Exception) { NumSystem.STANDARD } )
        }

        var appTheme: Resources.Theme? = null
        fun resolveColor(id: Int): Int {
            val typedValue = TypedValue()
            appTheme?.resolveAttribute(id, typedValue, true)
            return typedValue.resourceId
        }

        fun tokenBaseSystem(token: Char?): Pair<Int, NumSystem>? =
            when (token) {
                '@' -> tokens[0]
                '#' -> tokens[1]
                in listOf ('$', '€', '£', '¥') -> tokens[2]
                '%' -> tokens[3]
                '&' -> tokens[4]
                // '?' -> Pair(10, NumSystem.STANDARD)  // history entry
                else -> null
            }

        var themeId = 0
            private set
        fun setQonvertTheme(activity: Activity, themeString: String = "") {
            if (themeString != "") {
                val nightMode = AppCompatDelegate.getDefaultNightMode()
                if (nightMode != AppCompatDelegate.MODE_NIGHT_NO)  AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
                if (nightMode != AppCompatDelegate.MODE_NIGHT_YES) AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
                AppCompatDelegate.setDefaultNightMode(when (themeString.firstOrNull()) {
                    'L'  -> AppCompatDelegate.MODE_NIGHT_NO
                    'D'  -> AppCompatDelegate.MODE_NIGHT_YES
                    else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                })
                themeId = THEMES[themeString.lastOrNull() ?: ' '] ?: R.style.Theme_Qonvert
            }
            activity.setTheme(themeId)
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

fun makePretty(text: String): Pair<String, Int> {
    if (text.startsWith("\""))
        return if (text.endsWith("\"")) Pair(text.removeSurrounding("\""), R.string.clipboard_noQuotes) else Pair(text, 0)
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
                in '0'..'9' -> SUPERSCRIPT_DIGITS[it.digitToInt()]
                '-' -> '⁻'
                '.' -> '·'
                else -> '\u202F' /* narrow space */
                }
            }.joinToString("") + '⁄' + prettyText.substring(slash + 1).map {
                if (it in '0'..'9') SUBSCRIPT_DIGITS[it.digitToInt()] else '\u202F'
            }.joinToString("")
        prettyMenuTitle = R.string.clipboard_fraction
    }
    return Pair(prettyText, prettyMenuTitle)
}

fun makeCompatible(text: String, system: NumSystem): Pair<String, Int> {
    if (text.startsWith("\"")) return Pair(text, 0)
    var compatText = text.removeSuffix("…")
        .replace("_", (if (system == NumSystem.GREEK) "ʹ" else "") + (if (text.firstOrNull() == '-') '-' else '+'))
    var one = when (system) {
        NumSystem.BIJECTIVE_A -> "A"
        NumSystem.GREEK -> "Α"
        NumSystem.ROMAN -> "I"
        else -> "1"
    }
    if (MainActivity.lowerDigits) one = one.lowercase()
    when (compatText.firstOrNull()) {
        '[' -> {
            val lastDelim = compatText.lastIndexOfAny(charArrayOf(';', ','))
            if (lastDelim in compatText.indices && lastDelim + 1 in compatText.indices) {
                compatText = compatText.replaceRange(lastDelim..lastDelim + 1, "+$one/").removeSurrounding("[", "]")
                    .replace("; ", "+$one/(").replace(", ", "+$one/(")
                compatText += ")".repeat(compatText.filter { it == '(' }.length)
            }
        }
        '{' -> compatText = compatText.removeSurrounding("{", "}").replace("; ", "+$one/").replace(", ", "+$one/")
    }
    compatText = compatText.removePrefix(when (system) {
        NumSystem.BIJECTIVE_1, NumSystem.BIJECTIVE_A -> "/+"
        NumSystem.GREEK -> "○+"
        NumSystem.ROMAN -> if (MainActivity.lowerDigits) "n+" else "N+"
        else -> "0+"
    })
    if (system !in setOf(NumSystem.GREEK, NumSystem.ROMAN)) {
        compatText = compatText.filter { it != ' ' }
        val rep = compatText.indexOf('\'')
        if (rep > -1) {
            compatText = compatText.removeRange(rep, rep + 1)
            with (compatText.substring(rep).filter { it != '.' }) {
                if (length > 0) compatText += repeat(MainActivity.maxDigitsAfter / length)
            }
        }
    }
    if (compatText.startsWith("..")) when (system) {
        NumSystem.GREEK -> {
            val leading9 = (compatText.getOrNull(2) ?: ' ').uppercaseChar()
            val dots = compatText.substring(2, compatText.indexOfAny(charArrayOf('/', 'ʹ')).coerceAtLeast(2)).count { it == '.' }
            compatText = if (dots != 0 && dots % 2 == 0 && leading9 in "ΘϞ") compatText.substring(if (leading9 == 'Θ') 6 else 7)
                else "͵ΘϠϞΘ . ".let {
                    if (MainActivity.lowerDigits) it.lowercase() else it
                }.repeat(2 - dots % 2)
                    .dropLast(mapOf('Θ' to 4, 'Ϟ' to 5, 'Ϡ' to 6, '͵' to 8)[leading9] ?: 0) + compatText.substring(2)
        }
        NumSystem.ROMAN -> {
            val withoutPipes = compatText.dropWhile { it in ".|" }
            var standardApostrophus = withoutPipes
            for ((key, value) in ROMAN_APOSTROPHUS) standardApostrophus = standardApostrophus.replace(key, value)
            val leadingPlace = mapOf('I' to 0, 'X' to 1, 'C' to 2, 'M' to 3, 'ↂ' to 4)[(standardApostrophus.getOrNull(0) ?: ' ').uppercaseChar()] ?: 0
            val pipes = withoutPipes.count { it == '|' }
            compatText = if (leadingPlace == 0 && withoutPipes.length > 2) "|".repeat(pipes - 1) + withoutPipes.substring(3)
                else "|".repeat(pipes) + listOf(
                    "ↂↈMↂCMXCIX", "ↂↈↀↂCↀXCIX", "CCIↃↃ CCCIↃↃↃ MCCIↃↃ CMXCIX", "CCIↃↃ CCCIↃↃↃ CIↃ CCIↃↃ CCIↃ XCIX")[MainActivity.apostrophus].let {
                        if (MainActivity.lowerDigits) it.lowercase() else it
                    }.dropLast(listOf(2, 4, 6,  8, 10,
                                      2, 4, 6,  8, 10,
                                      2, 4, 6, 13, 27,
                                      2, 4, 9, 19, 33)[leadingPlace + 5 * MainActivity.apostrophus]) + withoutPipes
        }
        else -> {
            val intLength = "$compatText.".indexOfAny(charArrayOf('.', '/', '+'), 2) - 4
            compatText = compatText.getOrNull(2).toString().repeat(if (intLength == 0) 8 else 7 - (intLength - 1) % 8) + compatText.substring(4)
        }
    }
    return Pair(compatText, if (compatText == text) 0 else R.string.clipboard_compat)
}
