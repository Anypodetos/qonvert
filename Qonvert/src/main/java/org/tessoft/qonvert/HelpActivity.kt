package org.tessoft.qonvert

/*
Copyright 2021 Anypodetos (Michael Weber)

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
import android.view.*
import android.webkit.WebView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.*
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator

private const val SECTION_NUMBER = "section_number"

class PageViewModel : ViewModel() {

    val index = MutableLiveData<Int>()
    fun setIndex(index: Int) {
        this.index.value = index
    }
}

class HelpFragment : Fragment() {

    private lateinit var pageViewModel: PageViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pageViewModel = ViewModelProvider(this)[PageViewModel::class.java].apply {
            setIndex(arguments?.getInt(SECTION_NUMBER) ?: 0)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val root = inflater.inflate(R.layout.fragment_help, container, false)
        pageViewModel.index.observe(viewLifecycleOwner, Observer {
            root.findViewById<WebView>(R.id.webView).loadData(
                getString(R.string.css) +
                (if (isNightModeActive()) getString(R.string.css_dark) else "") +
                (if (MainActivity.themeId == R.style.Theme_QonvertBlue) getString(R.string.css_blue) else "") +
                "<h1>" + when(it) {
                    0 -> getString(R.string.menu_help)
                    1 -> getString(R.string.menu_cheatSheet)
                    2 -> getString(R.string.menu_whatsNew)
                    3 -> getString(R.string.title_about, context?.packageManager?.getPackageInfo(context?.packageName ?: "", 0)?.versionName ?: "…")
                    else -> ""
                } + "</h1>" +
                getString(arrayOf(R.string.help, R.string.cheatSheet, R.string.whatsNew, R.string.about)[it]),
                "text/html", "UTF-8")
        })
        return root
    }

    private fun isNightModeActive() = when (AppCompatDelegate.getDefaultNightMode()) {
        AppCompatDelegate.MODE_NIGHT_NO -> false
        AppCompatDelegate.MODE_NIGHT_YES -> true
        else -> resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
    }

    companion object {
        @JvmStatic
        fun newInstance(sectionNumber: Int): HelpFragment {
            return HelpFragment().apply {
                arguments = Bundle().apply {
                    putInt(SECTION_NUMBER, sectionNumber)
                }
            }
        }
    }
}

class Adapter(activity: FragmentActivity) : FragmentStateAdapter(activity) {

    override fun createFragment(position: Int): Fragment = HelpFragment.newInstance(position)
    override fun getItemCount() = 4
}

class HelpActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        MainActivity.setQonvertTheme(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_help)

        val toolbar = findViewById<Toolbar>(R.id.helpToolbar)
        val tabs = findViewById<TabLayout>(R.id.tabs)
        val pager = findViewById<ViewPager2>(R.id.pager)

        toolbar.title = ""
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        pager.adapter = Adapter(this)
        TabLayoutMediator(tabs, pager) { tab, position ->
            tab.setContentDescription(arrayOf(R.string.menu_help, R.string.menu_cheatSheet, R.string.menu_whatsNew, R.string.menu_about)[position])
            tab.setIcon(arrayOf(
                R.drawable.ic_help,
                R.drawable.ic_cheat_sheet,
                R.drawable.ic_whats_new,
                R.drawable.ic_about)[position])
        }.attach()
        tabs.getTabAt(when (intent.getIntExtra("help", R.id.helpItem)) {
            R.id.helpItem -> 0
            R.id.cheatSheetItem -> 1
            R.id.whatsNewItem -> 2
            R.id.aboutItem -> 3
            else -> 0
        })?.select()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_help, menu)
        return true
    }
    override fun onOptionsItemSelected(item: MenuItem) = when (item.itemId) {
        R.id.shareQonvert -> {
            shareText(this, getString(R.string.share_app))
            true
        }
        else -> false
    }
}