package org.tessoft.qonvert

/*
Copyright 2020 Anypodetos (Michael Weber)

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
import android.app.Application
import android.content.*
import android.content.res.Configuration
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.view.View
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
import net.mabboud.android_tone_player.OneTimeBuzzer
import java.math.BigInteger
import java.math.BigInteger.ONE
import java.math.BigInteger.ZERO
import java.util.*
import kotlin.concurrent.schedule
import kotlin.math.*

const val hg2g = 42
const val taxiCab = 1729
val monster = BigInteger("808017424794512875886459904961710757005754368000000000")
val buttonBases = arrayOf(2, 3, 6, 8, 10, 12, 16, 20)
val defaultButtons = setOf("2", "8", "10", "12", "16")
val historyList = mutableListOf<QNumber>()
var lastQNumber = QNumber(ZERO, ONE)

var maxDigitsAfter = 0
var groupDigits = false
var lowerDigits = false
var warnInvalidDigits = true

enum class AppTheme {
    UNSET, CLASSIC, BLUE
}
var appTheme = AppTheme.UNSET

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
fun isNightModeActive(app: Application): Boolean {
    return when (AppCompatDelegate.getDefaultNightMode()) {
        AppCompatDelegate.MODE_NIGHT_NO -> false
        AppCompatDelegate.MODE_NIGHT_YES -> true
        else -> app.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
    }
}

class MainActivity : AppCompatActivity() {

    private lateinit var preferences: SharedPreferences

    private lateinit var textOutputs: Array<TextView>
    private lateinit var toggleButtons: Array<ToggleButton>
    private lateinit var balancedSwitches: Array<Switch>
    @SuppressLint("UseSwitchCompatOrMaterialCode")
    private lateinit var complementSwitch: Switch
    private lateinit var baseViews: Array<TextView>
    private lateinit var baseBars: Array<SeekBar>
    private lateinit var outputView: ScrollView
    private lateinit var outputLayout: ConstraintLayout
    private lateinit var editInput: EditText
    private lateinit var clearButton: FloatingActionButton

    override fun onCreate(savedInstanceState: Bundle?) {

        preferences = PreferenceManager.getDefaultSharedPreferences(this)
        setQonvertTheme(this, if (appTheme == AppTheme.UNSET) preferences.getString("theme", "LA") ?: "LA" else "")

        super.onCreate(savedInstanceState)
        //AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)               // bringt das was??
        setContentView(R.layout.activity_main)
        setSupportActionBar(findViewById(R.id.toolbar))

        val clipboard: ClipboardManager? = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager?
        var rangeToast: Toast? = null

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
            findViewById(R.id.outToggleButton2),
            findViewById(R.id.outToggleButton3),
            findViewById(R.id.outToggleButton6),
            findViewById(R.id.outToggleButton8),
            findViewById(R.id.outToggleButton10),
            findViewById(R.id.outToggleButton12),
            findViewById(R.id.outToggleButton16),
            findViewById(R.id.outToggleButton20)
        )
        balancedSwitches = arrayOf(
            findViewById(R.id.inBalancedSwitch),
            findViewById(R.id.outBalancedSwitch)
        )
        complementSwitch = findViewById(R.id.complementSwitch)
        baseViews = arrayOf(
            findViewById(R.id.inBaseView),
            findViewById(R.id.outBaseView)
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

        fun changeInput() {
            val q = QNumber(editInput.text.toString(), baseBars[0].progress + 2, if (balancedSwitches[0].isChecked) NumSystem.BALANCED else NumSystem.STANDARD)
            if (warnInvalidDigits) editInput.setTextColor(ContextCompat.getColor(applicationContext,
                    if (q.wrongDigits) R.color.orange_light else if (isNightModeActive(application)) R.color.white else R.color.black))
            lastQNumber = q.copy()

            q.changeBase(baseBars[1].progress + 2, if (balancedSwitches[1].isChecked) NumSystem.BALANCED else NumSystem.STANDARD, complementSwitch.isChecked)
            textOutputs[0].text = if (q.error == 0) q.toPositional() else {
                "\"${String(Character.toChars(q.error))}\" (${q.errorCode()}) " + when (q.error) {
                    0x22 -> getString(R.string.err_quote)
                    0x5B, 0x5D -> getString(R.string.err_bracket)
                    0x3B -> getString(R.string.err_semicolon)
                    0x2C -> getString(R.string.err_comma)
                    0x5F -> getString(R.string.err_underscore)
                    0x2F -> getString(R.string.err_slash)
                    0x2E -> getString(R.string.err_twoPoints)
                    0x27 -> getString(R.string.err_twoReps)
                    in 0x23..0x26, 0x40, 0xA3, 0xA5, 0x20AC, 0x2D -> getString(R.string.err_baseToken)
                    0x221E -> getString(R.string.err_infinity)
                    0x7121 -> getString(R.string.err_undefined)
                    else -> getString(R.string.err_generic)
                }
            }
            textOutputs[1].text = if (q.denominator > ONE) q.toFraction() else
                if (0x20.toBigInteger() < q.numerator && q.numerator < 0x32000.toBigInteger()) String(Character.toChars(q.numerator.toInt())) else ""
            textOutputs[2].text = if (q.denominator > ONE) {
                    if (q.numerator.abs() > q.denominator) q.toMixed() else ""
                } else when (q.numerator) {
                hg2g.toBigInteger() -> "🐋💐"
                taxiCab.toBigInteger() -> "🚕"
                monster -> "👾"
                else -> ""
            }
            textOutputs[3].text = if (q.denominator > ONE) q.toContinued() else ""
            for (i in 0..3) textOutputs[i].visibility = if (textOutputs[i].text == "") View.GONE else View.VISIBLE
        }

        fun toastRangeHint(i: Int, always: Boolean = false) {
            if (always || (preferences.getBoolean("range", true) && lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))) {
                val base = baseBars[i].progress + 2
                val minDigit = if (balancedSwitches[i].isChecked && base % 2 == 1) (1 - base) / 2 else 0
                rangeToast?.cancel()
                rangeToast = Toast.makeText(applicationContext, getString(R.string.range_toast,
                    resources.getStringArray(R.array.base_inout)[i], digitToChar(minDigit), digitToChar(minDigit + base - 1)), Toast.LENGTH_SHORT)
                rangeToast?.setGravity(Gravity.TOP, 0, 0)
                rangeToast?.show()
            }
        }

        for (i in 0..3) textOutputs[i].setOnClickListener {
            if (textOutputs[i].text != "") {
                editInput.setText(textOutputs[if (i > 0 || !textOutputs[0].text.endsWith('…')) i else {
                    val toast = Toast.makeText(applicationContext, getString(R.string.to_fraction), Toast.LENGTH_SHORT)
                    toast.view.findViewById<TextView>(android.R.id.message).gravity = Gravity.CENTER
                    toast.show()
                    if (textOutputs[2].text == "") 1 else 2
                }].text)
                balancedSwitches[0].isChecked = balancedSwitches[1].isChecked
                baseBars[0].progress = baseBars[1].progress
            }
        }
        for (i in 0..3) textOutputs[i].setOnLongClickListener {
            if (textOutputs[i].text != "") {
                clipboard?.setPrimaryClip(ClipData.newPlainText(null, textOutputs[i].text))
                Toast.makeText(applicationContext, getString(R.string.to_clipboard), Toast.LENGTH_SHORT).show()
            }
            true
        }
        for (i in 0..1) for (j in buttonBases.indices) toggleButtons[buttonBases.size * i + j].setOnClickListener {
            baseBars[i].progress = buttonBases[j] - 2
            toggleButtons[buttonBases.size * i + j].isChecked = true /* push down again in case it was pushed before */
        }
        complementSwitch.setOnClickListener {
            if (complementSwitch.isChecked) balancedSwitches[1].isChecked = false
            changeInput()
        }
        for (i in 0..1) balancedSwitches[i].setOnClickListener {
            if (i == 1 && balancedSwitches[1].isChecked) complementSwitch.isChecked = false
            toastRangeHint(i)
            changeInput()
        }
        for (i in 0..1) baseBars[i].setOnSeekBarChangeListener(object : OnSeekBarChangeListener {
            override fun onStartTrackingTouch(seekBar: SeekBar) {}
            override fun onStopTrackingTouch(seekBar: SeekBar) {}
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                baseViews[i].text = getString(R.string.base, resources.getStringArray(R.array.base_inout)[i], progress + 2)
                balancedSwitches[i].isEnabled = progress % 2 == 1
                for (j in buttonBases.indices) toggleButtons[buttonBases.size * i + j].isChecked = progress == buttonBases[j] - 2
                toastRangeHint(i)
                changeInput()
            }
        })
        for (i in 0..1) baseViews[i].setOnClickListener {
            toastRangeHint(i, true)
        }
        editInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
            override fun afterTextChanged(s: Editable) {}
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                val small = outputLayout.height - textOutputs[0].height / 3 < outputView.height
                changeInput()
                if (small) outputView.post { outputView.smoothScrollTo(0, outputView.bottom) }
            }
        })
        editInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE && editInput.text.toString() != "" && lastQNumber.error == 0) historyList.add(lastQNumber)
            false
        }
        clearButton.setOnClickListener {
            if (editInput.text.toString() != "" && lastQNumber.error == 0 &&
                (historyList.size == 0 || historyList.last().toString() != lastQNumber.toString())) historyList.add(lastQNumber)
            editInput.setText("")
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
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
                        balancedSwitches[0].isChecked = historyList[which].system == NumSystem.BALANCED
                        baseBars[0].progress = historyList[which].base - 2
                        editInput.setText(historyList[which].toString(
                            aFormat = if (itemsList[which].indexOf('…') == -1) historyList[which].format else {
                                Toast.makeText(applicationContext, getString(R.string.to_fraction), Toast.LENGTH_SHORT).show()
                                QFormat.MIXED
                            }))
                        historyList.removeAt(which)
                        dialog.dismiss()
                    }
                    historyDialog.setNeutralButton(R.string.clear_history) { dialog, _ ->
                        dialog.dismiss()
                        val confirmDialog = AlertDialog.Builder(this)
                        confirmDialog.setTitle(R.string.clear_history_q)
                        confirmDialog.setPositiveButton(R.string.yes) { _, _ -> historyList.clear() }
                        confirmDialog.setNegativeButton(R.string.no) { _, _ -> }
                        confirmDialog.create().show()
                    }
                } else historyDialog.setMessage(R.string.no_history)
                historyDialog.setNegativeButton(R.string.close) { _, _ -> }
                historyDialog.create().show()
            }
            R.id.playItem -> {
                val ratio = (lastQNumber.numerator.toDouble() / lastQNumber.denominator.toDouble()).absoluteValue
                if (1/128.0 <= ratio && ratio <= 128.0) {
                    val buzzer1 = OneTimeBuzzer()
                    buzzer1.duration = 3.4
                    buzzer1.toneFreqInHz = 440 / 2.0.pow(round(ln(ratio) / 2))
                    buzzer1.play()
                    Timer().schedule(1000) {
                        val buzzer2 = OneTimeBuzzer()
                        buzzer2.duration = 2.5
                        buzzer2.toneFreqInHz = buzzer1.toneFreqInHz * ratio
                        buzzer2.play()
                    }
                    //Toast.makeText(applicationContext, , Toast.LENGTH_SHORT).show()  /// display interval name
                } else {
                    val toast = Toast.makeText(applicationContext, getString(R.string.no_interval, QNumber(128.toBigInteger(), ONE,
                        baseBars[0].progress + 2, if (balancedSwitches[0].isChecked) NumSystem.BALANCED else NumSystem.STANDARD).toString(true)), Toast.LENGTH_SHORT)
                    toast.view.findViewById<TextView>(android.R.id.message).gravity = Gravity.CENTER
                    toast.show()
                }
            }
            R.id.settingsItem, R.id.helpItem, R.id.aboutItem -> {
                val intent = Intent(this@MainActivity, if (item.itemId == R.id.settingsItem) SettingsActivity::class.java else HelpActivity::class.java)
                if (item.itemId != R.id.settingsItem) intent.putExtra("help", item.itemId == R.id.helpItem)
                startActivity(intent)
            }
        }
        return true
    }

    override fun onResume() {
        super.onResume()
        maxDigitsAfter = (preferences.getString("digits", "300") ?: "300").toIntOrNull() ?: 300
        groupDigits = preferences.getBoolean("group", false)
        lowerDigits = preferences.getBoolean("lowercase", false)
        warnInvalidDigits = preferences.getBoolean("wrongDigits", true)
        val textSize = (preferences.getString("size", "20") ?: "20").toFloat()
        editInput.textSize = textSize
        for (i in 0..3) textOutputs[i].textSize = textSize
        for (j in buttonBases.indices) {
            val visibility = if (buttonBases[j].toString() in preferences.getStringSet("buttons", defaultButtons) ?: defaultButtons)
                View.VISIBLE else View.GONE
            toggleButtons[j]                .visibility = visibility
            toggleButtons[j + buttonBases.size].visibility = visibility
        }

        if (historyList.size == 0) for (i in 0 until preferences.getInt("historySize", 0)) {
            val q = QNumber(preferences.getString("history$i", ""))
            if (q.error == 0) historyList.add(q)
        }
        complementSwitch.isChecked = preferences.getBoolean("outComplement", false)
        balancedSwitches[0].isChecked = preferences.getString("inSystem",  "") == NumSystem.BALANCED.toString()
        balancedSwitches[1].isChecked = preferences.getString("outSystem", "") == NumSystem.BALANCED.toString()
        baseBars[0].progress = preferences.getInt("inBase",  10) - 2
        baseBars[1].progress = preferences.getInt("outBase", 10) - 2
        editInput.setText(preferences.getString("input", "1_5/6"))
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
        editor.putString("inSystem",  if (balancedSwitches[0].isChecked) NumSystem.BALANCED.toString() else NumSystem.STANDARD.toString())
        editor.putString("outSystem", if (balancedSwitches[1].isChecked) NumSystem.BALANCED.toString() else NumSystem.STANDARD.toString())
        editor.putBoolean("outComplement", complementSwitch.isChecked)
        editor.apply()
    }
}

/*

* beta-test
* Release variant: View → Tool windows → Build variants
  + sign it: https://stackoverflow.com/questions/20268520/generate-signed-apk-android-studio
    - C:\Users\Michael\Documents\Qonvert\qonvert.jks
    - alias: first_qonvert
    - Warning: Der JKS-Keystore verwendet ein proprietäres Format. Es wird empfohlen, auf PKCS12 zu migrieren, das ein Industriestandardformat mit
      "keytool -importkeystore -srckeystore C:\Users\Michael\Documents\Qonvert\qonvert.jks -destkeystore C:\Users\Michael\Documents\Qonvert\qonvert.jks -deststoretype pkcs12" ist.
  + make sure the source is distributed with it

V 1.1+
* DMS
* Roman numerals
* SCI/ENG input/output
* other non-standard positional systems (s. WP), e.g. bijective numerals, non-adjacent forms, Egyptian fractions, quote notation, …
* alternative positional notation output (0.1 = 0.0'9, balanced ternary: 0.'Z = Z.'1)
* base symbols $%&... customisable
* play [https://stackoverflow.com/questions/2413426/playing-an-arbitrary-tone-with-android]
  + get rid of clicks
  + display interval name
* themes
  + simpler method for input text colour?
  + colour of title bar in overview
  + icon adaptive (blue): https://www.youtube.com/watch?v=ts98gL1JCQU
  + icon shadowed, C in Q
* lemizh.conlang.org/qonvert?

V 2+
* simple calculations?

*/