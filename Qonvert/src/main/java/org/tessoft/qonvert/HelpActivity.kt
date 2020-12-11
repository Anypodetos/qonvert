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

import android.os.Bundle
import android.webkit.WebView
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar

class HelpActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        setQonvertTheme(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_help)
        val isHelp = intent.getBooleanExtra("help", true)
        findViewById<Toolbar>(R.id.helpToolbar).title = if (isHelp) getString(R.string.menu_help)
            else getString(R.string.title_about) + " " + getString(R.string.app_name) + " " + packageManager.getPackageInfo(packageName, 0).versionName
        setSupportActionBar(findViewById(R.id.helpToolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        findViewById<WebView>(R.id.webView).loadData(getString(R.string.css) +
            (if (isNightModeActive(application)) getString(R.string.css_dark) else "") +
            (if (appTheme == AppTheme.BLUE) getString(R.string.css_blue) else "") +
            getString(if (isHelp) R.string.help else R.string.about),
            "text/html", "UTF-8")

        findViewById<ImageButton>(R.id.backButton).setOnClickListener { finish() }
    }
}
