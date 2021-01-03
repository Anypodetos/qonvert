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

import android.content.res.Configuration
import android.os.Bundle
import android.webkit.WebView
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.Toolbar

class HelpActivity : AppCompatActivity() {

    private fun isNightModeActive(): Boolean {
        return when (AppCompatDelegate.getDefaultNightMode()) {
            AppCompatDelegate.MODE_NIGHT_NO -> false
            AppCompatDelegate.MODE_NIGHT_YES -> true
            else -> application.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        MainActivity.setQonvertTheme(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_help)
        val pageId = intent.getIntExtra("help", R.id.helpItem)
        findViewById<Toolbar>(R.id.helpToolbar).title = when (pageId) {
            R.id.helpItem -> getString(R.string.menu_help)
            R.id.cheatSheetItem -> getString(R.string.menu_cheatSheet)
            R.id.whatsNewItem -> getString(R.string.menu_whatsNew)
            else -> getString(R.string.title_about, getString(R.string.app_name), packageManager.getPackageInfo(packageName, 0).versionName)
        }
        setSupportActionBar(findViewById(R.id.helpToolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        findViewById<WebView>(R.id.webView).loadData(getString(R.string.css) +
            (if (isNightModeActive()) getString(R.string.css_dark) else "") +
            (if (MainActivity.appTheme == AppTheme.BLUE) getString(R.string.css_blue) else "") +
            getString(when (pageId) {
                R.id.helpItem -> R.string.help
                R.id.whatsNewItem -> R.string.whatsNew
                R.id.cheatSheetItem -> R.string.cheatSheet
                else -> R.string.about
            }),
            "text/html", "UTF-8")

        findViewById<ImageButton>(R.id.backButton).setOnClickListener { finish() }
    }
}