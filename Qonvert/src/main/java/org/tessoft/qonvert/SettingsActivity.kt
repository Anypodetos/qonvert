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

import android.content.SharedPreferences
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.ListPreference
import androidx.preference.MultiSelectListPreference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreference
import java.util.*

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        MainActivity.setQonvertTheme(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        if (savedInstanceState == null) {
            supportFragmentManager
                .beginTransaction()
                .replace(R.id.settings, SettingsFragment())
                .commit()
        }
        setSupportActionBar(findViewById(R.id.settingsToolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    class SettingsFragment : PreferenceFragmentCompat() {
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.root_preferences, rootKey)
        }

        private fun apostrophusSummary() {
            val lowercase = findPreference<SwitchPreference>("lowercase")
            val apostrophus = findPreference<ListPreference>("apostrophus")
            apostrophus?.entries = resources.getStringArray(R.array.apostrophus_entries).map {
                    if (lowercase?.isChecked == true) it.toLowerCase(Locale.ROOT) else it
                }.toTypedArray()
            apostrophus?.summary = if (lowercase?.isChecked == true) apostrophus?.entry.toString().toLowerCase(Locale.ROOT)
                else apostrophus?.entry.toString().toUpperCase(Locale.ROOT)
        }

        private fun buttonSummary() {
            findPreference<MultiSelectListPreference>("buttons")?.let {
                it.summary = it.values.sortedBy { i -> i.toInt() }.joinToString(" • ") { i ->
                    getString(resources.getIdentifier("b$i", "string", context?.packageName)).toUpperCase(Locale.ROOT)
                }
            }
        }

        private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            when (key) {
               "lowercase", "apostrophus" -> apostrophusSummary()
                "buttons" -> buttonSummary()
                "theme" -> activity?.let {
                    MainActivity.setQonvertTheme(it, findPreference<ListPreference>("theme")?.value ?: "")
                    it.recreate()
                }
            }
        }

        override fun onResume() {
            super.onResume()
            preferenceManager.sharedPreferences.registerOnSharedPreferenceChangeListener(listener)
            apostrophusSummary()
            buttonSummary()
         }

        override fun onPause() {
            preferenceManager.sharedPreferences.unregisterOnSharedPreferenceChangeListener(listener)
            super.onPause()
        }
    }
}