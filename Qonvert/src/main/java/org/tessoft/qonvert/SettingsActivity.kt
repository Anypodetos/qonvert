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
import java.util.*
import kotlin.math.min

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        setQonvertTheme(this)
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

        private fun buttonSummary() {
            val buttonSelect = findPreference<MultiSelectListPreference>("buttons")
            if (buttonSelect != null) {
                var st = ""
                for (i in buttonSelect.values.sortedBy {id -> id.toInt()})
                    st += " • " + getString(resources.getIdentifier("b$i", "string", context?.packageName)).toUpperCase(Locale.ROOT)
                buttonSelect.summary = st.substring(min(3, st.length))
            }
        }

        private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            when (key) {
                "buttons" -> buttonSummary()
                "theme" -> {
                    val thisActivity = activity
                    if (thisActivity != null) {
                        setQonvertTheme(thisActivity, findPreference<ListPreference>("theme")?.value ?: "")
                        thisActivity.recreate()
                    }
                }
            }
        }

        override fun onResume() {
            super.onResume()
            preferenceManager.sharedPreferences.registerOnSharedPreferenceChangeListener(listener)
            buttonSummary()
         }

        override fun onPause() {
            preferenceManager.sharedPreferences.unregisterOnSharedPreferenceChangeListener(listener)
            super.onPause()
        }
    }
}