package org.tessoft.qonvert

/*
Copyright 2020, 2021, 2022, 2023 Anypodetos (Michael Weber)

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
import android.graphics.Rect
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.TypedValue
import android.view.*
import android.view.inputmethod.EditorInfo
import android.widget.*
import android.widget.SeekBar.OnSeekBarChangeListener
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.SwitchCompat
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.view.MenuCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.*
import java.lang.System.currentTimeMillis
import java.math.BigInteger
import java.math.BigInteger.*
import java.util.*
import kotlin.math.*

const val HG2G = 42
const val TAXICAB = 1729
val MONSTER = BigInteger("808017424794512875886459904961710757005754368000000000")
const val TOKENS = "@#$%&"
const val ALL_TOKENS = "@#$€£¥%&"
val BUTTON_BASES = listOf(1, 2, 3, 6, 8, 10, 12, 16, 20, 26, 60)
val DEFAULT_BUTTONS = listOf(-1, 2, 8, 10, 12, 16)

const val PLAY_DIALOG_FROM_MAIN = -1
const val PLAY_DIALOG_CLOSED = -2

enum class KeyboardId {
    DIGITS_LEFT, DIGITS_RIGHT, DIGITS_LEFT_TABLET, DIGITS_RIGHT_TABLET, ANDROID
}

enum class FontType {
    SERIF, DEFAULT, SANS_SERIF, MONOSPACE
}

class MainActivity : AppCompatActivity() {

    private lateinit var showNaturalStrings: Array<String>
    private var showNatural = setOf<String>()
    private var showRange = true
    private var autoScrollOutput = true
    private var warnNonstandardInput = true
    private val bases = intArrayOf(10, 10)
    private val numSystems = arrayOf(NumSystem.STANDARD, NumSystem.STANDARD)
    private var lastQNumber = QNumber()
    var keyboardId = KeyboardId.DIGITS_RIGHT
        private set
    var spaceComposes = false
        private set
    private lateinit var preferences: SharedPreferences
    private var showWhatsNewStar = true
    private var whatsNewStarShown = true
    private var showHistoryDialog = true
    private var rangeToast: Toast? = null
    private var launchCalcTimes = Array(5) { 0L }

    private lateinit var negaButtons: List<ToggleButton>
    private lateinit var toggleButtons: List<ToggleButton>
    private lateinit var systemButtons: List<Button>
    private lateinit var complementSwitch: SwitchCompat
    private lateinit var dmsSwitch: SwitchCompat
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

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setSupportActionBar(findViewById(R.id.toolbar))

        if (numSystemsSuper[0].isEmpty()) resources.getStringArray(R.array.num_systems_super).let {
            for (i in numSystemsSuper.indices) numSystemsSuper[i] = it[i]
        }

        showNaturalStrings = resources.getStringArray(R.array.natural_values)

        negaButtons = listOf(
            findViewById(R.id.inNegaButton),
            findViewById(R.id.outNegaButton)
        )

        toggleButtons = listOf(
            findViewById(R.id.inToggleButtonPhi),
            findViewById(R.id.inToggleButton2),
            findViewById(R.id.inToggleButton3),
            findViewById(R.id.inToggleButton6),
            findViewById(R.id.inToggleButton8),
            findViewById(R.id.inToggleButton10),
            findViewById(R.id.inToggleButton12),
            findViewById(R.id.inToggleButton16),
            findViewById(R.id.inToggleButton20),
            findViewById(R.id.inToggleButton26),
            findViewById(R.id.inToggleButton60),
            findViewById(R.id.outToggleButtonPhi),
            findViewById(R.id.outToggleButton2),
            findViewById(R.id.outToggleButton3),
            findViewById(R.id.outToggleButton6),
            findViewById(R.id.outToggleButton8),
            findViewById(R.id.outToggleButton10),
            findViewById(R.id.outToggleButton12),
            findViewById(R.id.outToggleButton16),
            findViewById(R.id.outToggleButton20),
            findViewById(R.id.outToggleButton26),
            findViewById(R.id.outToggleButton60)
        )
        systemButtons = listOf(
            findViewById(R.id.inSystemButton),
            findViewById(R.id.outSystemButton)
        )
        complementSwitch = findViewById(R.id.complementSwitch)
        dmsSwitch = findViewById(R.id.dmsSwitch)
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

        (resources.displayMetrics.widthPixels / resources.displayMetrics.scaledDensity / 16).toInt().coerceIn(36, MAX_BASE - 1).let {
            for (i in 0..1) baseBars[i].max = it
        }
        if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE &&
            resources.displayMetrics.heightPixels < 800 * resources.displayMetrics.scaledDensity)
                keyboard.layoutParams.height = (140 * resources.displayMetrics.scaledDensity).toInt()

        /*   I n t e r f a c e   */

        fun baseDialog(i: Int) {
            showBaseDialog(this, resources.getStringArray(R.array.choose_base)[i].replace("%d", MAX_BASE.toString()), bases[i], 10) { base, edit ->
                setBaseAndSystem(i, base, numSystems[i], recalculate = true)
                if (fullscreenKeyboardOpen(edit)) window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
            }
        }

        for (i in 0..1) baseBars[i].setOnSeekBarChangeListener(object : OnSeekBarChangeListener {
            override fun onStartTrackingTouch(seekBar: SeekBar) { }
            override fun onStopTrackingTouch(seekBar: SeekBar) { }
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    setBaseAndSystem(i, (progress + 1) * bases[i].sign, numSystems[i], recalculate = true)
                    if (progress == baseBars[i].max) baseDialog(i)
                }
            }
        })

        val buttonGestureDetectors = Array(2) { i ->
            GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
                override fun onFling(e1: MotionEvent, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
                    setBaseAndSystem(i, bases[i] + (velocityX * bases[i]).sign.toInt(), numSystems[i], recalculate = true)
                    return true
                }
                override fun onLongPress(e: MotionEvent) {
                    super.onLongPress(e)
                    baseBars[i].playSoundEffect(SoundEffectConstants.CLICK)
                    baseDialog(i)
                }
            })
        }

        for (i in 0..1) negaButtons[i].setOnClickListener {
            setBaseAndSystem(i, abs(bases[i]) * if (negaButtons[i].isChecked) (if (bases[i] == 1) -2 else -1) else 1, numSystems[i], recalculate = true)
        }
        for (i in 0..1) negaButtons[i].setOnTouchListener { _, event ->
            buttonGestureDetectors[i].onTouchEvent(event)
            false
        }

        for (i in 0..1) for (j in BUTTON_BASES.indices) toggleButtons[BUTTON_BASES.size * i + j].setOnClickListener {
            setBaseAndSystem(i, BUTTON_BASES[j] * (if (BUTTON_BASES[j] == 1) 1 else bases[i].sign), numSystems[i], recalculate = true)
            toggleButtons[BUTTON_BASES.size * i + j].isChecked = true /* push down again in case it was down before */
        }
        for (i in 0..1) for (j in BUTTON_BASES.indices) toggleButtons[BUTTON_BASES.size * i + j].setOnTouchListener { _, event ->
            buttonGestureDetectors[i].onTouchEvent(event)
            false
        }

        for (i in 0..1) systemButtons[i].setOnClickListener { view ->
            val popupMenu = PopupMenu(this, view)
            for ((j, res) in resources.getStringArray(R.array.num_systems).withIndex())
                popupMenu.menu.add(1, Menu.NONE, j, res).isChecked = j == numSystems[i].ordinal
            popupMenu.menu.setGroupCheckable(1, true, true)
            popupMenu.setOnMenuItemClickListener { item: MenuItem ->
                val system = NumSystem.values()[item.order]
                setBaseAndSystem(i, allowedBase(bases[i], system), system, recalculate = true)
                true
            }
            popupMenu.show()
        }
        for (i in 0..1) systemButtons[i].setOnLongClickListener {
            it.playSoundEffect(SoundEffectConstants.CLICK)
            val system = if (numSystems[i] == NumSystem.STANDARD) NumSystem.BALANCED else NumSystem.STANDARD
            setBaseAndSystem(i, allowedBase(bases[i], system), system, recalculate = true)
            true
        }

        complementSwitch.setOnClickListener {
            calculate(inputChanged = false)
        }
        dmsSwitch.setOnClickListener {
            calculate(inputChanged = false)
        }
        for (i in 0..1) baseTexts[i].setOnClickListener {
            toastRangeHint(i, always = true)
        }
        for (i in 0..1) baseTexts[i].setOnLongClickListener {
            baseDialog(i)
            true
        }

        for (i in 0..4) textOutputs[i].setOnClickListener {
            val (outBase, outSystem) = outBaseAndSystem(i)
            copyToInput(lastQNumber, textOutputs[i].text.toString(), outBase, outSystem, complementSwitch.isChecked, dmsSwitch.isChecked, switchBases = true)
        }
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager?
        for (i in 0..4) textOutputs[i].setOnLongClickListener {
            it.playSoundEffect(SoundEffectConstants.CLICK)
            val text = textOutputs[i].text.toString()
            val (outBase, outSystem) = outBaseAndSystem(i)
            val (dissectText, dissectMenuTitle) = makeDissect(text, outBase, outSystem)
            val (prettyText, prettyMenuTitle) = makePretty(text)
            val (compatText, compatMenuTitle) = makeCompatible(text, outBase, outSystem)
            if (dissectMenuTitle == 0 && prettyMenuTitle == 0 && compatMenuTitle == 0) {
                clipboard?.setPrimaryClip(ClipData.newPlainText(null, text))
                Toast.makeText(applicationContext, getString(R.string.clipboard_ok), Toast.LENGTH_SHORT).show()
            } else {
                val popupMenu = PopupMenu(this, it)
                popupMenu.menu.add(Menu.NONE, Menu.NONE, 0, getString(R.string.clipboard_asIs))
                if (dissectMenuTitle != 0) popupMenu.menu.add(Menu.NONE, Menu.NONE, 1, getString(dissectMenuTitle))
                if (prettyMenuTitle != 0) popupMenu.menu.add(Menu.NONE, Menu.NONE, 2, getString(prettyMenuTitle))
                if (compatMenuTitle != 0) popupMenu.menu.add(Menu.NONE, Menu.NONE, 3, getString(compatMenuTitle))
                popupMenu.setOnMenuItemClickListener { item: MenuItem ->
                    clipboard?.setPrimaryClip(ClipData.newPlainText(null, when (item.order) {
                        1 -> dissectText
                        2 -> prettyText
                        3 -> compatText
                        else -> text
                    }))
                    Toast.makeText(applicationContext, getString(R.string.clipboard_ok), Toast.LENGTH_SHORT).show()
                    true
                }
                popupMenu.show()
            }
            true
        }

        editInput.setOnClickListener {
            if (keyboardId != KeyboardId.ANDROID) keyboard.show()
            editInput.composeBackup = ""
        }
        editInput.setOnLongClickListener {
            editInput.composeBackup = ""
            false
        }
        editInput.setOnFocusChangeListener { _, focused ->
            if (focused && keyboardId != KeyboardId.ANDROID) keyboard.show() else keyboard.hide()
        }
        editInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) { }
            override fun afterTextChanged(s: Editable) { }
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                /* somewhat of a hack - full functionality of composeDigit() only from Qonvert’s keyboard */
                if (spaceComposes && keyboardId == KeyboardId.ANDROID && count == 1 && s.getOrNull(start) == ' ' && before == 0)
                    editInput.composeDigit(numSystems[0], handleKeystroke = false)
                val small = outputLayout.height - textOutputs[0].height / 3 < outputView.height
                calculate(inputChanged = true)
                if (small and autoScrollOutput) outputView.post { outputView.smoothScrollTo(0, outputView.bottom) }
            }
        })
        editInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) { /* Enter from Android keyboard */
                addInputToHistory()
                !fullscreenKeyboardOpen(editInput)
            } else false
        }

        clearButton.setOnClickListener {
            if (editInput.string.isNotBlank() && lastQNumber.isValid) {
                if (historyList.lastOrNull()?.let {
                    it.inputString.trim() == editInput.string.trim() && it.number.base == bases[0] && it.number.system == numSystems[0]
                } == true) historyList.removeLastOrNull()
                addInputToHistory()
            }
            editInput.string = ""
            editInput.composeBackup = ""
        }
        clearButton.setOnLongClickListener {
            it.playSoundEffect(SoundEffectConstants.CLICK)
            complementSwitch.isChecked = false
            dmsSwitch.isChecked = false
            for (i in 1 downTo 0) setBaseAndSystem(i, 10, NumSystem.STANDARD, recalculate = i == 0)
            true
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (keyboard.visibility == View.VISIBLE) keyboard.hide() else finish()
            }
        })
    }

    /*   O t h e r   e v e n t s   */

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        MenuCompat.setGroupDividerEnabled(menu, true)
        menuInflater.inflate(R.menu.menu_main, menu)
        if (showWhatsNewStar) {
            menu.findItem(R.id.intervalItem).setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
            menu.findItem(R.id.whatsNewItem).setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
        }
        whatsNewStarShown = showWhatsNewStar
        return true
    }
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.allFormatsItem -> if (lastQNumber.isValid) {
                val intent = Intent(this, ListActivity::class.java)
                val q = lastQNumber.copy()
                q.changeBase(bases[1], numSystems[1], complementSwitch.isChecked, dmsSwitch.isChecked)
                intent.putExtra("list", "m" + q.toPreferencesString() + "/")
                putListBase(preferences, q.base, q.system, q.complement, q.dms)
                startActivity(intent)
            } else lastQNumber.irrationalErrorInfo()?.let { (input, x) ->
                val title = getString(R.string.phi_formats_title, input)
                val sub = "ᵩ" + numSystemsSuper[numSystems[1].ordinal]
                val message = x.toBasePhiAll(preferredEgyptianMethod(1), groupDigits, maxDigits, complementSwitch.isChecked,
                    balancedPhiDigit(numSystems[1], complementSwitch.isChecked)).replace(':', '˙').replace("\n\n", "$sub\n\n") + sub
                AlertDialog.Builder(this)
                    .setTitle(title)
                    .setMessage(message)
                    .setPositiveButton(R.string.copy_my) { _, _ ->
                        (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText(null, title + "\n\n" + message))
                        Toast.makeText(applicationContext, getString(R.string.clipboard_ok), Toast.LENGTH_SHORT).show()
                    }
                    .setNegativeButton(R.string.close) { _, _ -> }
                    .create().show()
            } ?: Toast.makeText(this, R.string.no_q_number, Toast.LENGTH_SHORT).show()
            R.id.historyItem -> if (historyList.isNotEmpty()) {
                val intent = Intent(this, ListActivity::class.java)
                intent.putExtra("list", "H")
                startActivity(intent)
            } else if (showHistoryDialog) {
                val checkBox = CheckBox(this)
                checkBox.text = getString(R.string.dont_show_again)
                AlertDialog.Builder(this)
                    .setTitle(R.string.menu_history)
                    .setMessage(R.string.no_history)
                    .setView(checkBox)
                    .setNegativeButton(R.string.close) { _, _ -> if (checkBox.isChecked) showHistoryDialog = false }
                    .create().show()
            } else Toast.makeText(applicationContext, getString(R.string.no_history_short), Toast.LENGTH_SHORT).show()
            R.id.intervalItem -> lastQNumber.play(this)
            R.id.intervalListItem -> {
                val intent = Intent(this, ListActivity::class.java)
                intent.putExtra("list", "I")
                putListBase(preferences, bases[0], numSystems[0], complementSwitch.isChecked, dmsSwitch.isChecked)
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

    @SuppressLint("ApplySharedPref")
    override fun onResume() {
        super.onResume()

        /* update preferences from v1.4.1 and earlier */
        var input = preferences.getString("input", null)
        val updateFrom141 = preferences.getInt("thisVersion", 0) == 0 && input != null
        if (updateFrom141) {
            input = input?.replace('\'', '˙')
            val editor = preferences.edit()
            editor.putStringSet("buttons", setOf("-1") + (preferences.getStringSet("buttons", null) ?: setOf<String>()))
            editor.commit()
        }

        /* SETTINGS */
        getOutputSettingsAndFont(preferences)
        showNatural = preferences.getStringSet("natural", null) ?: showNaturalStrings.toSet()
        showRange = preferences.getBoolean("range", true)
        autoScrollOutput = preferences.getBoolean("scrollOutput", true)
        warnNonstandardInput = preferences.getBoolean("wrongDigits", true)
        getTokenSettings(preferences)
        val buttons = preferences.getStringSet("buttons", null) ?: DEFAULT_BUTTONS.toSet().map { it.toString() }
        negaButtons[0].visibility = if ("-1" in buttons) View.VISIBLE else View.GONE
        negaButtons[1].visibility = negaButtons[0].visibility
        for (j in BUTTON_BASES.indices) {
            toggleButtons[j].visibility = if (BUTTON_BASES[j].toString() in buttons) View.VISIBLE else View.GONE
            toggleButtons[j + BUTTON_BASES.size].visibility = toggleButtons[j].visibility
        }
            /* keyboard */
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            if (preferences.contains("customKeyboard")) {
                keyboardId = if (preferences.getBoolean("customKeyboard", true)) KeyboardId.DIGITS_RIGHT else KeyboardId.ANDROID
                val editor = preferences.edit()
                editor.remove("customKeyboard")
                editor.putString("keyboard", keyboardId.toString())
                editor.apply()
            } else keyboardId = KeyboardId.values().find { it.name == preferences.getString("keyboard", null) } ?: KeyboardId.DIGITS_RIGHT
            editInput.showSoftInputOnFocus = keyboardId == KeyboardId.ANDROID
        } else keyboardId = KeyboardId.ANDROID
        spaceComposes = preferences.getBoolean("spaceComposes", false)
        editInput.composeBackup = ""
        if (keyboardId != KeyboardId.ANDROID) {
            updateKeyboardToCaretPos(always = true)
            if (preferences.getBoolean("keyboardShow", true)) keyboard.show() else keyboard.hide()
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
        } else keyboard.hide()
            /* sizes */
        val textSize = preferences.getString("size", null)?.toFloatOrNull() ?: 20f
        editInput.textSize = textSize
        for (i in 0..4) textOutputs[i].textSize = textSize
        complementSwitch.textSize = textSize * 0.7f
        dmsSwitch.textSize = textSize * 0.7f
        for (i in 0..1) baseTexts[i].textSize = textSize * 0.7f
        clearButton.size = if (textSize < 25f) FloatingActionButton.SIZE_MINI else FloatingActionButton.SIZE_NORMAL
        val heights = if (preferences.getBoolean("moreSpace", false)) ((textSize + 30f) * resources.displayMetrics.scaledDensity).toInt()
            else LinearLayout.LayoutParams.WRAP_CONTENT
        baseTexts[1].layoutParams.height = heights
        complementSwitch.layoutParams.height = heights
        dmsSwitch.layoutParams.height = heights
            /* theme */
        setTheme(preferences.getString("theme", "SA") ?: "SA")

        /* HISTORY */
        getHistory(preferences, historyList, updateFrom141)
        showHistoryDialog = preferences.getBoolean("showHistoryDialog", true)

        /* MAIN SCREEN */
        complementSwitch.isChecked = preferences.getBoolean("outComplement", false)
        dmsSwitch.isChecked = preferences.getBoolean("outDMS", false)
        setBaseAndSystem(0, preferences.getInt("inBase", 10),
            NumSystem.values().find { it.name == preferences.getString("inSystem",  null) } ?: NumSystem.STANDARD,
            recalculate = false, alwaysFeedback = true)
        setBaseAndSystem(1, preferences.getInt("outBase", 10),
            NumSystem.values().find { it.name == preferences.getString("outSystem",  null) } ?: NumSystem.STANDARD,
            recalculate = false, alwaysFeedback = true)
        editInput.string = input ?: "1_3/5"
        try { editInput.setSelection(preferences.getInt("selStart", 0), preferences.getInt("selEnd", 0)) } catch (_: Exception) { }

        /* WHEN PLAY DIALOG IS SHOWING */
        if (preferences.getInt("playDialog", PLAY_DIALOG_CLOSED) == PLAY_DIALOG_FROM_MAIN) {
            playPhaseShift = preferences.getFloat("playPhaseShift", 0f)
            if (playPhaseShift.isFinite()) lastQNumber.play(this, onlyRecreate = true)
        }

        /* FROM LIST SCREEN */
        preferences.getString("listInput", null)?.let { listInput ->
            if (listInput.firstOrNull() != 'm') addInputToHistory()  /* 'm' = from output area of MainActivity */
            if (listInput.isNotEmpty()) when (listInput[0]) {
                'H' -> listInput.substring(1).toIntOrNull()?.let {
                    with(historyList[it]) {
                        copyToInput(number, inputString)
                        historyList.remove(this)
                    }
                }
                'I' -> listInput.substring(1).toIntOrNull()?.let {
                    with(INTERVALS[it % INTERVALS.size]) {
                        copyToInput(QNumber(first * (TWO.pow(it / INTERVALS.size)), second, bases[0], numSystems[0], format = QFormat.FRACTION))
                    }
                }
                in 'a'..'z' -> with(QNumber(preferencesEntry = listInput.substring(1))) {
                    val split = listInput.substring(1).split('/')
                    copyToInput(this, toString(aEgyptianMethod = EgyptianMethod.values().find { it.name == split[split.size - 2] } ?: EgyptianMethod.OFF),
                        switchBases = listInput[0] == 'm')
                    if (listInput[0] == 'h') split.lastOrNull()?.let { historyList.removeAt(it.toInt()) }
                }
            }
        }

        /* WHAT'S NEW STAR */
        showWhatsNewStar = preferences.getInt("version", 0) < BuildConfig.VERSION_CODE && input != null /* don’t show star if there was no old version */
        if (whatsNewStarShown && !showWhatsNewStar) invalidateOptionsMenu()
    }

    override fun onPause() {
        super.onPause()
        val editor = preferences.edit()
        putHistory(preferences, editor, historyList)
        editor.putBoolean("showHistoryDialog", showHistoryDialog)
        editor.putString("input", editInput.string)
        editor.putInt("selStart", editInput.selectionStart)
        editor.putInt("selEnd", editInput.selectionEnd)
        editor.putInt("inBase", bases[0])
        editor.putInt("outBase", bases[1])
        editor.putString("inSystem", numSystems[0].toString())
        editor.putString("outSystem", numSystems[1].toString())
        editor.putBoolean("outComplement", complementSwitch.isChecked)
        editor.putBoolean("outDMS", dmsSwitch.isChecked)
        editor.putBoolean("keyboardShow", !keyboard.hidden)
        editor.putInt("playDialog", if (playDialog?.isShowing == true) {
            editor.putFloat("playPhaseShift", playPhaseShift)
            PLAY_DIALOG_FROM_MAIN
        } else PLAY_DIALOG_CLOSED)
        playDialog?.cancel()
        playDialogTimer?.cancel()
        editor.remove("listInput")
        editor.putInt("thisVersion", BuildConfig.VERSION_CODE)
        if (!showWhatsNewStar) editor.putInt("version", BuildConfig.VERSION_CODE)
        editor.apply()
        keyboard.pause()
    }

    /*  C a l c u l a t e  */

    fun calculate(inputChanged: Boolean) {
        val q = if (inputChanged) QNumber(editInput.string, bases[0], numSystems[0]) else lastQNumber.copy()
        if (inputChanged) {
            editInput.setTextColor(when {
               !q.isValid -> ContextCompat.getColor(this, R.color.red_error)
                q.nonstandardInput && warnNonstandardInput -> ContextCompat.getColor(this, R.color.orange_light)
                else -> resolveColor(this, R.attr.editTextColor)
            })
            lastQNumber = q.copy()
        }
        q.changeBase(bases[1], numSystems[1], complementSwitch.isChecked, dmsSwitch.isChecked)
        val fractionUseful = q.usefulFormat(QFormat.FRACTION)
        val mixedUseful = q.usefulFormat(QFormat.MIXED)
        launchCalc(0, q.system in setOf(NumSystem.GREEK, NumSystem.ROMAN)) {
            if (q.usefulFormat(QFormat.POSITIONAL)) q.toPositional() else (if (!q.isValid) q.errorMessage(resources) else "")
        }
        launchCalc(1, q.system in setOf(NumSystem.GREEK, NumSystem.ROMAN) || !fractionUseful) {
            if (fractionUseful) q.toFraction() else (if (showNaturalStrings[0] in showNatural && q.usefulFormat(QFormat.GREEK_NATURAL)) q.toGreek() else "")
        }
        launchCalc(2, q.system in setOf(NumSystem.GREEK, NumSystem.ROMAN) || !mixedUseful) {
            if (mixedUseful) q.toMixed() else (if (showNaturalStrings[1] in showNatural && q.usefulFormat(QFormat.ROMAN_NATURAL)) q.toRoman() else "")
        }
        launchCalc(3, q.system in setOf(NumSystem.GREEK, NumSystem.ROMAN) && q.denominator > ONE /* Unicode */) {
            if (q.usefulFormat(QFormat.CONTINUED)) q.toContinued() else
                (if (showNaturalStrings[2] in showNatural && q.usefulFormat(QFormat.UNICODE)) q.toUnicode() else "")
        }
        launchCalc(4, q.system in setOf(NumSystem.GREEK, NumSystem.ROMAN)) {
            if (q.usefulFormat(QFormat.EGYPTIAN, egyptianMethod)) q.toEgyptian(egyptianMethod) else if (q.denominator == ONE) when (q.numerator) {
                HG2G.toBigInteger() -> "🐋💐"
                TAXICAB.toBigInteger() -> "🚕"
                MONSTER -> "👾"
                else -> ""
            } else ""
        }
    }

    private inline fun launchCalc(i: Int, greekOrRoman: Boolean, crossinline calc: () -> String) {
        with(textOutputs[i].animate()) { duration = 100; alpha(0.5f) }
        launchCalcTimes[i] = currentTimeMillis()
        val myLaunchTime = launchCalcTimes[i]
        lifecycleScope.launch(Dispatchers.Default) {
            val s = calc()
            if (myLaunchTime == launchCalcTimes[i]) withContext(Dispatchers.Main) {
                with(textOutputs[i]) {
                    text = s
                    typeface = resolveFont(greekOrRoman)
                    with(animate()) { duration = 100; alpha(1f) }
                    visibility = if (s.isEmpty()) View.GONE else View.VISIBLE
                }
            }
        }
    }

    /*   U t i l i t i e s   */

    private fun setBaseAndSystem(i: Int, base: Int, system: NumSystem, recalculate: Boolean, alwaysFeedback: Boolean = false) {
        val useBase = saneBase(base, bases[i])
        val feedback = alwaysFeedback || useBase != bases[i] || system != numSystems[i]
        bases[i] = useBase
        numSystems[i] = system
        systemButtons[i].text = resources.getStringArray(R.array.num_systems_short)[system.ordinal]
        negaButtons[i].isChecked = useBase < 0
        baseBars[i].progress = abs(useBase) - 1
        if (recalculate) calculate(inputChanged = i == 0)
        if (feedback) baseAndSystemFeedback(i, useBase, system)
    }

    private fun baseAndSystemFeedback(i: Int, base: Int = bases[i], system: NumSystem = numSystems[i]) {
        val (useSystem, baseAllowed) = allowedSystem(base, system)
        baseTexts[i].text = getString(R.string.base, resources.getStringArray(R.array.base_inOut)[i], baseToString(base),
            resources.getStringArray(R.array.num_systems)[useSystem.ordinal])
        for (j in BUTTON_BASES.indices) with(toggleButtons[BUTTON_BASES.size * i + j]) {
            isChecked = BUTTON_BASES[j] == abs(base)
            setTypeface(null, if (base > 0 || BUTTON_BASES[j] == 1) Typeface.BOLD else Typeface.ITALIC)
        }
        systemButtons[i].setBackgroundColor(if (baseAllowed) resolveColor(this, R.attr.colorPrimaryVariant) else ContextCompat.getColor(this, R.color.grey))
        if (i == 0) {
            editInput.typeface = resolveFont(greekOrRoman = useSystem in setOf(NumSystem.GREEK, NumSystem.ROMAN))
            updateKeyboardToCaretPos(base, useSystem)
        } else complementSwitch.isEnabled = complementAllowed(base, useSystem)
        toastRangeHint(i, base, system)
    }

    private fun toastRangeHint(i: Int, base: Int = bases[i], system: NumSystem = numSystems[i], always: Boolean = false) {
        if (always || (showRange && lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))) {
            val digitRange = digitRange(base, system)
            rangeToast?.cancel()
            rangeToast = Toast.makeText(applicationContext, getString(when {
                    system == NumSystem.GREEK && base == 10 -> if (i == 0) R.string.range_toast_greek else R.string.range_toast_onlyGreek
                    system == NumSystem.ROMAN && base == 10 -> if (i == 0) R.string.range_toast_roman else R.string.range_toast_onlyRoman
                    else -> R.string.range_toast
                },
                resources.getStringArray(R.array.base_inOut)[i],
                digitToChar(digitRange.first, base, system), digitToChar(digitRange.last, base, system)),
                Toast.LENGTH_SHORT)
            rangeToast?.setGravity(Gravity.TOP, 0, 0)
            rangeToast?.show()
        }
    }

    private fun outBaseAndSystem(i: Int): Pair<Int, NumSystem> =
        if (i in 1..2 && '/' !in textOutputs[i].text) Pair(10, if (i == 1) NumSystem.GREEK else NumSystem.ROMAN) else
            Pair(bases[1], allowedSystem(bases[1], numSystems[1]).first)

    fun updateKeyboardToCaretPos(base: Int = bases[0], actualSystem: NumSystem = allowedSystem(base, numSystems[0]).first, always: Boolean = false) {
        if (keyboardId != KeyboardId.ANDROID) keyboard.fillButtons(tokenBaseSystem(editInput.string.substring(0, editInput.selectionStart).findLast {
            it in "_/°\'[{;,$ALL_TOKENS"
        }) ?: Pair(base, actualSystem), always)
    }

    private fun copyToInput(q: QNumber, st: String = q.toString(), base: Int = q.base, system: NumSystem = q.system,
                complement: Boolean = q.complement, dms: Boolean = q.dms, switchBases: Boolean = false) {
        if (switchBases) setBaseAndSystem(1, bases[0], numSystems[0], recalculate = false)
        setBaseAndSystem(0, base, system, recalculate = false)
        if (switchBases && lastQNumber.numerator < ZERO) complementSwitch.isChecked = lastQNumber.complement
        if (switchBases) dmsSwitch.isChecked = lastQNumber.dms
        val removeToken = st.indexOfAny(ALL_TOKENS.toCharArray()) > -1 && !st.trimStart().startsWith('"') && QNumber(st, base, system).let {
            !it.isValid || it.numerator != q.numerator || it.denominator != q.denominator
        }
        val outputSt = if (removeToken) q.toString() else st
        val rounded = '…' in outputSt && !outputSt.startsWith('"')
        editInput.string = if (removeToken || rounded) {
            Toast.makeText(applicationContext, ((if (removeToken) getString(R.string.to_tokenFree) else "") +
                "\n\n" + (if (rounded) getString(R.string.to_fraction) else "")).trim(), Toast.LENGTH_SHORT).show()
            q.changeBase(base, system, complement, dms)
            if (rounded) q.toMixed() else outputSt
        } else st
        if (editInput.string.removeSuffix("°").lowercase() in setOf("0", "/", "○") + if (numSystems[0] == NumSystem.ROMAN) setOf("n") else setOf())
            editInput.setSelection(0, editInput.string.length) else editInput.setSelection(editInput.string.length)
        editInput.composeBackup = ""
    }

    fun addInputToHistory() {
        if (editInput.string.isNotBlank() && lastQNumber.isValid) historyList.add(QNumberEntry(editInput.string, lastQNumber))
        editInput.selectAll()
    }

    private fun fullscreenKeyboardOpen(edit: EditText): Boolean {
        val rect = Rect()
        edit.getWindowVisibleDisplayFrame(rect)
        return rect.height() > resources.displayMetrics.heightPixels * 0.85
    }

    companion object {
        val historyList = mutableListOf<QNumberEntry>()
        var maxDigits = 0
        var groupDigits = false
        var lowerDigits = false
        var apostrophus = 0
        var egyptianMethod = EgyptianMethod.BINARY
        private var fontType = FontType.DEFAULT
        val tokens = Array(DEFAULT_BUTTONS.size - 1) { Pair(DEFAULT_BUTTONS[it + 1], NumSystem.STANDARD) }
        val numSystemsSuper = Array(NumSystem.values().size) { "" }

        var playDialogTimer: Timer? = null
        var playDialog: AlertDialog? = null
        var playPhaseShift = 0f

        fun getOutputSettingsAndFont(preferences: SharedPreferences) {
            maxDigits = (preferences.getString("digits", null))?.toIntOrNull() ?: 300
            groupDigits = preferences.getBoolean("group", false)
            lowerDigits = preferences.getBoolean("lowercase", false)
            apostrophus = ((preferences.getString("apostrophus", null))?.toIntOrNull() ?: 0).coerceIn(0..3)
            egyptianMethod = EgyptianMethod.values().find { it.name == preferences.getString("egyptian", null) } ?: EgyptianMethod.BINARY
            fontType = FontType.values().find { it.name == preferences.getString("font", null) } ?: FontType.DEFAULT
        }

        fun preferredEgyptianMethod(base: Int) = if (egyptianExists(base, egyptianMethod)) egyptianMethod else EgyptianMethod.BINARY

        fun resolveFont(greekOrRoman: Boolean): Typeface = when (fontType) {
            FontType.SERIF -> Typeface.SERIF
            FontType.DEFAULT -> if (greekOrRoman) Typeface.SERIF else Typeface.SANS_SERIF
            FontType.SANS_SERIF -> Typeface.SANS_SERIF
            FontType.MONOSPACE -> Typeface.MONOSPACE
        }

        fun getTokenSettings(preferences: SharedPreferences) {
            for (i in tokens.indices) tokens[i] = Pair(preferences.getString("tokenBase$i", null)?.toIntOrNull() ?: tokens[i].first,
                NumSystem.values().find { it.name == preferences.getString("tokenSystem$i", null) } ?: NumSystem.STANDARD)
        }

        fun tokenBaseSystem(token: Char?): Pair<Int, NumSystem>? =
            when (token) {
                '@' -> tokens[0]
                '#' -> tokens[1]
                in setOf ('$', '€', '£', '¥') -> tokens[2]
                '%' -> tokens[3]
                '&' -> tokens[4]
                // '℅' -> Pair(10, NumSystem.STANDARD)  // history entry
                else -> null
            }

    }
}

fun resolveColor(context: Context, id: Int): Int {
    val typedValue = TypedValue()
    context.theme.resolveAttribute(id, typedValue, true)
    return ContextCompat.getColor(context, typedValue.resourceId)
}

fun setTheme(themeString: String) {
    if (themeString != "") AppCompatDelegate.setDefaultNightMode(when (themeString.firstOrNull()) {
        'L'  -> AppCompatDelegate.MODE_NIGHT_NO
        'D'  -> AppCompatDelegate.MODE_NIGHT_YES
        else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
    })
}

fun showBaseDialog(context: Context, dialogTitle: String, value: Int, default: Int, onOk: (Int, EditText) -> Unit) {
    val edit = EditText(context)
    val okay = {
        onOk(saneBase(edit.text.toString().toIntOrNull() ?: default, default), edit)
    }
    val dialog = AlertDialog.Builder(context)
        .setTitle(dialogTitle)
        .setMessage(R.string.base_message)
        .setView(edit)
        .setNeutralButton(context.getString(R.string.just_base, baseToString(default))) { _, _ -> onOk(default, edit) }
        .setPositiveButton(android.R.string.ok) { _, _ -> okay() }
        .setNegativeButton(R.string.cancel) { _, _ -> }
        .create()
    edit.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_SIGNED
    edit.setText(value.toString())
    edit.selectAll()
    edit.requestFocus()
    edit.setOnEditorActionListener { _, actionId, _ ->
        if (actionId == EditorInfo.IME_ACTION_DONE) {
            okay()
            dialog.cancel()
            true
        } else false
    }
    dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
    dialog.show()
}

fun getHistory(preferences: SharedPreferences, historyList: MutableList<QNumberEntry>, updateFrom141: Boolean = false) {
    historyList.clear()
    for (i in 0 until preferences.getInt("historySize", 0))
        with(QNumber(preferencesEntry = preferences.getString("history$i", null) ?: "")) {
            if (isValid) historyList.add(QNumberEntry((preferences.getString("historyInput$i", null) ?: "").let {
                if (updateFrom141) it.replace('\'', '˙') else it
            }, this))
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
        activity?.startActivity(Intent.createChooser(this, title)) /* title works only up to API ~28 ≈ Android Oreo/Pie */
    }
}

fun makeDissect(text: String, base: Int, system: NumSystem): Pair<String, Int> {
    if (when (system) {
        NumSystem.STANDARD -> abs(base) <= 10
        NumSystem.BALANCED, NumSystem.BIJECTIVE_A -> false
        NumSystem.BIJECTIVE_1 -> abs(base) <= 9
        NumSystem.GREEK, NumSystem.ROMAN -> true
    } || text.startsWith("\"")) return Pair(text, 0)
    val bijectiveSub = if (system == NumSystem.BIJECTIVE_A) 9 else 0
    val s = StringBuilder()
    for (c in text.lowercase()) if (c in DIGITS)
        s.append((if (system == NumSystem.BALANCED) BAL_DIGITS.indexOf(c) - MAX_BAL_BASE / 2 else DIGITS.indexOf(c) - bijectiveSub).toString() + ' ') else {
            if (c != ' ' && s.endsWith(' ')) s.deleteAt(s.lastIndex)
            s.append(c)
            if (c =='-') s.append(' ')
        }
    val dissectText = s.toString().trimEnd()
    return Pair(dissectText, if (dissectText == text) 0 else R.string.clipboard_dissect)
}

fun makePretty(text: String): Pair<String, Int> {
    if (text.startsWith("\"")) return if (text.endsWith("\"") && Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT)
        Pair("$text " + (Character.getName(text.codePointAt(1)) ?: ""), R.string.clipboard_withUnicodeName) else Pair(text, 0)
    val dms = text.endsWith('"')
    var prettyText = text.removeSuffix("\"")
    var prettyMenuTitle = 0
    val rep = prettyText.indexOf('˙')
    if (rep > -1) {
        val s = StringBuilder(prettyText).deleteAt(rep)
        for (i in s.length downTo rep + 1) if (s[i - 1] != ' ') s.insert(i, '\u0305') /* overline */
        prettyText = s.toString()
        prettyMenuTitle = R.string.clipboard_rep
    }
    val fractionStart = prettyText.indexOfLast { it in "_°'" }
    val slash = prettyText.indexOf('/')
    if (slash > -1 && prettyText.substring(fractionStart + 1).all { it in '0'..'9' || it in "/-. " }) {
        prettyText = "_${prettyText}_"
        for ((unicode, frac) in FRACTION_CHARS)
            prettyText = prettyText.replace("_${frac.first}/${frac.second}_", unicode.toString())
                                   .replace("-${frac.first}/${frac.second}_", "⁻$unicode")
        prettyText = prettyText.removePrefix("_").removeSuffix("_")
        if (text.startsWith(prettyText)) prettyText = (if (fractionStart > -1)
                prettyText.substring(0, fractionStart + if (prettyText[fractionStart] == '_') 0 else 1) else "") +
                prettyText.substring(fractionStart + 1, slash).map {
            when (it) {
                in '0'..'9' -> SUPERSCRIPT_DIGITS[it.digitToInt()]
                '-' -> '⁻'
                '.' -> '·'
                else -> '\u202F' /* narrow Space */
                }
            }.joinToString("") + '⁄' + prettyText.substring(slash + 1).map {
                if (it in '0'..'9') SUBSCRIPT_DIGITS[it.digitToInt()] else '\u202F'
            }.joinToString("")
        prettyMenuTitle = R.string.clipboard_fraction
    }
    if (dms) prettyText = prettyText.replace('\'', '′') + '″'
    return Pair(prettyText, prettyMenuTitle)
}

fun makeCompatible(text: String, base: Int, system: NumSystem): Pair<String, Int> {
    /* Unicode */
    if (text.startsWith("\""))
        return if (text.endsWith("\"")) Pair(text.removeSurrounding("\""), R.string.clipboard_noQuotes) else Pair(text, 0)

    val seconds = text.endsWith("\"")
    val cutOffPositional = text.endsWith("…") || text.endsWith("…\"")
    var compatText = text.removeSuffix("\"").removeSuffix("…").trimEnd().
        replace("_", (if (system == NumSystem.GREEK) "ʹ" else "") + (if (text.startsWith('-') && '°' !in text && '\'' !in text) '-' else '+'))
    /* continued and Egyptian fractions */
    val one = when (system) {
        NumSystem.BIJECTIVE_A -> 'a'
        NumSystem.GREEK -> 'α'
        NumSystem.ROMAN -> 'i'
        else -> '1'
    }.let { if (MainActivity.lowerDigits) it else it.uppercaseChar() }
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
        NumSystem.STANDARD, NumSystem.BALANCED -> "0+"
        NumSystem.BIJECTIVE_1, NumSystem.BIJECTIVE_A -> "/+"
        NumSystem.GREEK -> "○+"
        NumSystem.ROMAN -> if (MainActivity.lowerDigits) "n+" else "N+"
    })
    /* repeating digits and DMS */
    if (system !in setOf(NumSystem.GREEK, NumSystem.ROMAN)) {
        compatText = compatText.filter { it != ' ' }
        val rep = compatText.indexOf('˙')
        if (rep > -1) {
            compatText = compatText.removeRange(rep, rep + 1)
            if (!cutOffPositional) {
                val repPart = compatText.substring(rep)
                if (repPart.isNotEmpty()) compatText +=
                    repPart.repeat((MainActivity.maxDigits - (rep - compatText.indexOfLast { it == '.' })) / repPart.length)
            }
        }
        compatText = compatText.replace("°", "° ").replace("'", "' ").trimEnd()
    }
    if (seconds) compatText += "\""
    /* complement notation */
    if (compatText.startsWith("..")) when (system) {
        NumSystem.GREEK -> {
            val leading9 = (compatText.getOrNull(2) ?: ' ').lowercaseChar()
            val dots = compatText.substring(2, compatText.indexOfAny(charArrayOf('/', 'ʹ')).coerceAtLeast(2)).count { it == '.' }
            compatText = if (dots != 0 && dots % 2 == 0 && leading9 in "θϟ") compatText.substring(if (leading9 == 'θ') 6 else 7)
                else "͵θϡϟθ . ".let {
                    if (MainActivity.lowerDigits) it else it.uppercase()
                }.repeat(2 - dots % 2)
                    .dropLast(mapOf('θ' to 4, 'ϟ' to 5, 'ϡ' to 6, '͵' to 8)[leading9] ?: 0) + compatText.substring(2)
        }
        NumSystem.ROMAN -> {
            val withoutPipes = compatText.trimStart('.', '|')
            var standardApostrophus = withoutPipes
            for ((key, value) in ROMAN_APOSTROPHUS) standardApostrophus = standardApostrophus.replace(key, value)
            val leadingPlace = mapOf('i' to 0, 'x' to 1, 'c' to 2, 'm' to 3, 'ↂ' to 4)[(standardApostrophus.getOrNull(0))?.lowercaseChar()] ?: 0
            val pipes = withoutPipes.count { it == '|' }
            compatText = if (leadingPlace == 0 && withoutPipes.length > 2) "|".repeat(pipes - 1) + withoutPipes.substring(3)
                else "|".repeat(pipes) + listOf(
                    "ↂↈmↂcmxcix", "ↂↈↀↂcↀxcix", "cciↄↄ ccciↄↄↄ mcciↄↄ cmxcix", "cciↄↄ ccciↄↄↄ ciↄ cciↄↄ cciↄ xcix")[MainActivity.apostrophus].let {
                        if (MainActivity.lowerDigits) it else it.uppercase()
                    }.dropLast(listOf(2, 4, 6,  8, 10,
                                      2, 4, 6,  8, 10,
                                      2, 4, 6, 13, 27,
                                      2, 4, 9, 19, 33)[leadingPlace + 5 * MainActivity.apostrophus]) + withoutPipes
        }
        else -> {
            val preChars = if (abs(base) == 1) 6 else 4
            val intLength = "$compatText.".indexOfAny(charArrayOf('.', '/', '+'), 2) - preChars
            compatText = compatText.substring(2, 4).repeat(if (intLength == 0) 4 else 3 - (intLength - 1) / 2 % 4) +
                compatText.substring(preChars - if (intLength % 2 == 0) 0 else 1)
        }
    }
    return Pair(compatText, if (compatText == text) 0 else R.string.clipboard_compat)
}
