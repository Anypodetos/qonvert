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
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.util.AttributeSet
import android.view.MenuItem
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.preference.*
import com.google.android.material.snackbar.Snackbar

class SettingsActivity : AppCompatActivity(), PreferenceFragmentCompat.OnPreferenceStartFragmentCallback {

    lateinit var layout: CoordinatorLayout
    private lateinit var toolbar: Toolbar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        layout = findViewById(R.id.settings)
        toolbar = findViewById(R.id.settingsToolbar)
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.settings, SettingsFragment())
                .commit()
        }
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                goUp()
            }
        })
    }

    override fun onOptionsItemSelected(item: MenuItem) = if (item.itemId == android.R.id.home) {
        goUp()
        true
    } else false

    private fun goUp() {
        if (supportFragmentManager.backStackEntryCount > 0) {
            supportFragmentManager.popBackStack()
            toolbar.title = getString(R.string.menu_settings)
        } else finish()
    }

    override fun onPreferenceStartFragment(caller: PreferenceFragmentCompat, pref: Preference): Boolean {
        supportFragmentManager.beginTransaction()
            .replace(R.id.settings, TokenSettingsFragment())
            .addToBackStack(null)
            .commit()
        toolbar.title = getString(R.string.tokens_title)
        return true
    }

    override fun onResume() {
        super.onResume()
        toolbar.title = getString(if (supportFragmentManager.backStackEntryCount == 0) R.string.menu_settings else R.string.tokens_title)
    }

    class SettingsFragment : PreferenceFragmentCompat() {

        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.root_preferences, rootKey)
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP)
                findPreference<SwitchPreference>("customKeyboard")?.isVisible = false
        }

        private fun naturalSummary() {
            findPreference<MultiSelectListPreference>("natural")?.let {
                it.summary = resources.getStringArray(R.array.natural_entries).filterIndexed {
                    i, _ -> resources.getStringArray(R.array.natural_values)[i] in it.values
                }.joinToString(" • ")
            }
        }

        private fun apostrophusSummary() {
            val apostrophus = findPreference<ListPreference>("apostrophus")
            apostrophus?.entries = resources.getStringArray(R.array.apostrophus_entries).map {
                if (findPreference<SwitchPreference>("lowercase")?.isChecked == true) it.lowercase() else it
            }.toTypedArray()
            apostrophus?.summary = apostrophus?.entry.toString()
        }

        @SuppressLint("DiscouragedApi")
        private fun buttonSummary() {
            findPreference<MultiSelectListPreference>("buttons")?.let {
                it.summary = it.values.sortedBy { i -> i.toInt() }.joinToString(" • ") { i ->
                    getString(resources.getIdentifier(if (i == "-1") "bPosNeg" else "b$i", "string", it.context.packageName)).uppercase()
                }
            }
        }

        private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            when (key) {
                "natural" -> naturalSummary()
                "lowercase", "apostrophus" -> apostrophusSummary()
                "buttons" -> buttonSummary()
                "theme" -> setTheme(findPreference<ListPreference>("theme")?.value ?: "")
            }
        }

        override fun onResume() {
            super.onResume()
            preferenceManager.sharedPreferences?.registerOnSharedPreferenceChangeListener(listener)
            naturalSummary()
            apostrophusSummary()
            buttonSummary()
            findPreference<Preference>("baseTokens")?.summary = TOKENS.mapIndexed { index, char ->
                preferenceManager.sharedPreferences?.let { MainActivity.getTokenSettings(it) }
                MainActivity.tokens[index].let {
                    char.toString() +
                    (if (it.second !in setOf(NumSystem.GREEK, NumSystem.ROMAN)) "\u00A0" + baseToString(it.first) else "") +
                    (if (it.second != NumSystem.STANDARD) "\u00A0" + resources.getStringArray(R.array.num_systems)[it.second.ordinal] else "") }
            }.joinToString(" • ")
         }

        override fun onPause() {
            preferenceManager.sharedPreferences?.unregisterOnSharedPreferenceChangeListener(listener)
            super.onPause()
        }
    }

    class TokenSettingsFragment : PreferenceFragmentCompat() {

        private var snackbar: Snackbar? = null

        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.token_preferences, rootKey)
            val basePreferences = List(5) { findPreference<EditBasePreference>("tokenBase$it") }
            val systemPreferences = List(5) { findPreference<ListPreference>("tokenSystem$it") }

            findPreference<Preference>("tokensReset")?.setOnPreferenceClickListener {
                val backupBase = List(5) { basePreferences[it]?.value }
                val backupSystem = List(5) { systemPreferences[it]?.value }
                for (i in 0..4) {
                    basePreferences[i]?.value = DEFAULT_BUTTONS[i + 1]
                    systemPreferences[i]?.value = NumSystem.STANDARD.toString()
                }
                (context as? SettingsActivity)?.layout?.let {
                    snackbar = Snackbar.make(it, R.string.tokens_reset_ok, Snackbar.LENGTH_INDEFINITE)
                        .setAction(R.string.undo) {
                            for (i in 0..4) {
                                basePreferences[i]?.value = backupBase[i] ?: DEFAULT_BUTTONS[i + 1]
                                systemPreferences[i]?.value = backupSystem[i]
                            }
                        }
                    snackbar?.show()
                }
                true
            }

            for (i in 0..4) basePreferences[i]?.let { preference ->
                preference.dialogTitle = TOKENS[i].toString() + " – " + (context?.getString(R.string.base_dialogTitle)?.replace("%d", MAX_BASE.toString()) ?:
                    preference.title)
                preference.setOnPreferenceClickListener {
                    snackbar?.dismiss()
                    false
                }
           }

            val systemEntries = resources.getStringArray(R.array.num_systems).map { entry ->
                entry.replaceFirstChar { it.uppercase() }
            }.toTypedArray()
            for (i in 0..4) systemPreferences[i]?.let {
                it.dialogTitle = TOKENS[i].toString() + " – " + it.title
                it.entries = systemEntries
                it.setOnPreferenceClickListener {
                    snackbar?.dismiss()
                    false
                }
            }
        }

        private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            val id = key.last().digitToIntOrNull() ?: -1
            if (key.startsWith("tokenBase")) findPreference<EditBasePreference>(key)?.let {
                val systemPreference = findPreference<ListPreference>("tokenSystem$id")
                try {
                    if (allowedBase(it.value, NumSystem.valueOf(systemPreference?.value ?: "")) != it.value)
                        systemPreference?.value = NumSystem.STANDARD.toString()
                } catch (_: Exception) { }
            }
            if (key.startsWith("tokenSystem")) findPreference<ListPreference>(key)?.let {
                val basePreference = findPreference<EditBasePreference>("tokenBase$id")
                try {
                    basePreference?.value = allowedBase(basePreference?.value ?: DEFAULT_BUTTONS[id + 1], NumSystem.valueOf(it.value ?: ""))
                } catch (_: Exception) { }
            }
        }

        override fun onResume() {
            super.onResume()
            preferenceManager.sharedPreferences?.registerOnSharedPreferenceChangeListener(listener)
        }

        override fun onPause() {
            preferenceManager.sharedPreferences?.unregisterOnSharedPreferenceChangeListener(listener)
            snackbar?.dismiss()
            super.onPause()
        }
    }
}

class EditBasePreference(context: Context, attrs: AttributeSet) : Preference(context, attrs) {

    private val default = DEFAULT_BUTTONS[(key.last().digitToIntOrNull() ?: -1) + 1]

    var dialogTitle: String = ""
    var value: Int = default
        set(value) {
            field = value
            summary = baseToString(field)
            val editor = preferenceManager.sharedPreferences?.edit()
            editor?.putString(key, field.toString())
            editor?.apply()
        }

    override fun onAttached() {
        super.onAttached()
        value = preferenceManager.sharedPreferences?.getString(key, null)?.toIntOrNull() ?: default
    }

    override fun onClick() {
        super.onClick()
        showBaseDialog(context, dialogTitle, value, default) { base, _ ->
            value = base
        }
    }
}