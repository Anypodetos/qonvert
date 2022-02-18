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

import android.annotation.SuppressLint
import android.content.*
import android.graphics.Typeface
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.*
import android.widget.PopupMenu
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.Toolbar
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import java.util.*

val canExpand = android.os.Build.VERSION.SDK_INT >= 28

class QNumberEntry(val inputString: String, val number: QNumber, val egyptianMethod: EgyptianMethod =
        if (MainActivity.egyptianMethod == EgyptianMethod.OFF) EgyptianMethod.BINARY else MainActivity.egyptianMethod,
        var selected: Boolean = false, var expanded: Boolean = false) {

    fun toStrings(activity: ListActivity?, removeQuotes: Boolean): Pair<String, String> = Pair(
        activity?.getString(R.string.item_header, inputString) ?: "",
        number.toString(withBaseSystem = true, mode = if (activity?.prettySwitch?.isChecked == true) DisplayMode.PRETTY else DisplayMode.STANDARD,
                aEgyptianMethod = egyptianMethod).let {
            if (removeQuotes) it.removeSurrounding("\"") else it
        }
    )
}

class RecyclerAdapter internal constructor(private val activity: ListActivity?, private val items: MutableList<QNumberEntry>) :
        RecyclerView.Adapter<RecyclerAdapter.ViewHolder>() {

    var selectedItems = 0
    var clickedItem = -1
        private set
    private val inflater: LayoutInflater = LayoutInflater.from(activity)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(inflater.inflate(R.layout.fragment_list, parent, false))
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        with(items[position].toStrings(activity, removeQuotes = false)) {
            holder.inputText.text = first
            holder.outputText.text = second
        }
        holder.outputText.typeface = if (items[position].number.system == NumSystem.ROMAN || items[position].number.format == QFormat.ROMAN_NATURAL)
            Typeface.SERIF else Typeface.DEFAULT

        if (!canExpand) items[position].expanded = true
        holder.outputText.post { holder.expandButton.visibility = if (holder.outputText.lineCount > 3 && canExpand) View.VISIBLE else View.GONE }
        holder.outputText.maxLines = if (items[position].expanded) 100000 else 3
        holder.expandButton.rotation = if (items[position].expanded) 180f else 0f

        activity?.applicationContext?.let {
            holder.backView.setBackgroundColor(if (items[position].selected)
                ContextCompat.getColor(it, MainActivity.resolveColor(android.R.attr.colorMultiSelectHighlight)) else 0)
        }
    }

    override fun getItemCount() = items.size

    inner class ViewHolder internal constructor(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val backView: View = itemView.findViewById(R.id.backView)
        private val listFormatsButton: FloatingActionButton = itemView.findViewById(R.id.listFormatsButton)
        val expandButton: FloatingActionButton = itemView.findViewById(R.id.expandButton)
        val inputText: TextView = itemView.findViewById(R.id.inputText)
        val outputText: TextView = itemView.findViewById(R.id.outputText)

        private fun changeSelection() {
            with(items[adapterPosition]) {
                selected = !selected
                if (selected) selectedItems++ else selectedItems--
            }
            notifyItemChanged(adapterPosition)
            activity?.updateToolbar()
        }
        init {
            val listWhatToken = activity?.listWhatToken(merge = false) ?: ""
            if (listWhatToken == "H" && activity != null) {
                val inputColor = inputText.textColors
                inputText.setTextColor(outputText.textColors)
                outputText.setTextColor(inputColor)
            }

            itemView.setOnClickListener {
                if (selectedItems == 0) {
                    clickedItem = adapterPosition
                    activity?.finish()
                } else changeSelection()
            }
            itemView.setOnLongClickListener {
                changeSelection()
                true
            }

            listFormatsButton.visibility = if (listWhatToken in "A".."Z") {
                listFormatsButton.setOnClickListener {
                    val intent = Intent(activity, ListActivity::class.java)
                    intent.putExtra("list", listWhatToken.toLowerCase(Locale.ROOT) +
                        items[adapterPosition].number.toPreferencesString() + "/$adapterPosition")
                    activity?.startActivity(intent)
                }
                View.VISIBLE
            } else View.GONE

            expandButton.setOnClickListener {
                with(items[adapterPosition]) { expanded = !expanded }
                notifyItemChanged(adapterPosition)
            }

            itemView.findViewById<TextView>(R.id.menuButton).setOnClickListener { view ->
                val popupMenu = PopupMenu(activity, view)
                popupMenu.inflate(R.menu.menu_list)
                popupMenu.menu.findItem(R.id.deleteItems).isVisible = listWhatToken == "H"
                popupMenu.menu.findItem(R.id.settingsListItem).isVisible = false
                popupMenu.setOnMenuItemClickListener { item: MenuItem ->
                    val text = if (item.itemId in setOf(R.id.copyItems, R.id.shareItems))
                        items[adapterPosition].toStrings(activity, removeQuotes = true).second else ""
                    when (item.itemId) {
                        R.id.copyItems -> activity?.let {
                            it.clipboard?.setPrimaryClip(ClipData.newPlainText(null, text))
                            Toast.makeText(it.applicationContext, it.getString(R.string.clipboard_ok), Toast.LENGTH_SHORT).show()
                        }
                        R.id.shareItems -> shareText(activity, text)
                        R.id.deleteItems -> activity?.let {
                            AlertDialog.Builder(it)
                                .setTitle(it.resources.getString(R.string.delete_this_q))
                                .setMessage(R.string.cant_be_undone)
                                .setPositiveButton(R.string.yes) { _, _ ->
                                    val pos = adapterPosition
                                    notifyItemRemoved(pos)
                                    if (items[pos].selected) selectedItems--
                                    items.remove(items[pos])
                                    it.updateToolbar()
                                }
                                .setNegativeButton(R.string.no) { _, _ -> }
                                .create().show()
                        }
                    }
                    true
                }
                popupMenu.setOnDismissListener {
                    outputText.setBackgroundColor(0)
                }
                activity?.applicationContext?.let {
                    outputText.setBackgroundColor(ContextCompat.getColor(it, MainActivity.resolveColor(android.R.attr.colorMultiSelectHighlight)))
                }
                popupMenu.show()
            }
        }
    }
}

@SuppressLint("UseSwitchCompatOrMaterialCode")
class ListActivity : AppCompatActivity() {

    private var listWhat = ""
    private lateinit var toolbar: Toolbar
    private lateinit var recycler: RecyclerView
    private lateinit var adapter: RecyclerAdapter
    private lateinit var preferences: SharedPreferences
    private val items = mutableListOf<QNumberEntry>()
    private var listSel = ""
    private var listExpand = ""
    private var prefsMapping = mutableListOf<Int>()
    lateinit var prettySwitch: Switch
    var clipboard: ClipboardManager? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        MainActivity.setQonvertTheme(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_list)

        listWhat = intent.getStringExtra("list") ?: ""
        toolbar = findViewById(R.id.listToolbar)
        toolbar.contentDescription = getString(when (listWhat) {
            "H" -> R.string.menu_history
            "I" -> R.string.intervals
            in "a".."z" -> R.string.menu_formats
            else -> 0
        })
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        recycler = findViewById(R.id.recycler)
        recycler.layoutManager = LinearLayoutManager(this).apply {
            if (listWhat == "H") {
                reverseLayout = true
                stackFromEnd = true
            }
        }
        adapter = RecyclerAdapter(this, items)
        recycler.adapter = adapter
        preferences = PreferenceManager.getDefaultSharedPreferences(this)
        when (listWhat.firstOrNull()) {
            'H' -> getHistory(preferences, items)
            'I' -> {
                val base = preferences.getInt("inBase", 10)
                val system = try { NumSystem.valueOf(preferences.getString("inSystem",  null) ?: "") } catch (e: Exception) { NumSystem.STANDARD }
                for ((i, interval) in INTERVALS.withIndex()) {
                    with(QNumber(interval.first, interval.second, base, system, format = QFormat.FRACTION)) {
                        items.add(i, QNumberEntry(toInterval(resources), this))
                    }
                    with(QNumber(interval.first * TWO, interval.second, base, system, format = QFormat.FRACTION)) {
                        items.add(QNumberEntry(toInterval(resources), this))
                    }
                }
            }
            in 'a'..'z' -> {
                val q = QNumber(preferencesEntry = listWhat.substring(1))
                val formatsArray = resources.getStringArray(R.array.formats)
                for (f in QFormat.values()) if (f != QFormat.EGYPTIAN && q.usefulFormat(f)) {
                    prefsMapping.add(items.count())
                    items.add(QNumberEntry(formatsArray[f.ordinal], q.copy(f)))
                } else prefsMapping.add(-1)
                val egyptianArray = resources.getStringArray(R.array.egyptian_entries)
                for (e in EgyptianMethod.values()) if (e != EgyptianMethod.OFF && q.usefulFormat(QFormat.EGYPTIAN)) {
                    prefsMapping.add(items.count())
                    items.add(QNumberEntry(resources.getString(R.string.egyptian_method, egyptianArray[e.ordinal].substringBefore(" (")),
                        q.copy(QFormat.EGYPTIAN), egyptianMethod = e))
                } else prefsMapping.add(-1)
            }
        }
        if (prefsMapping.size == 0) prefsMapping = MutableList(items.size) { it }
        adapter.selectedItems = 0
        listSel = preferences.getString("listSel${listWhatToken(merge = true)}", null) ?: ""
        for ((i, b) in (listSel).withIndex()) if (i < prefsMapping.size && prefsMapping[i] != -1 && b == '1') {
            items[prefsMapping[i]].selected = true
            adapter.selectedItems++
        }
        listExpand = preferences.getString("listExpand${listWhatToken(merge = true)}", null) ?: ""
        for ((i, b) in (listExpand).withIndex()) if (i < prefsMapping.size && prefsMapping[i] != -1 && b == '1') {
            items[prefsMapping[i]].expanded = true
        }
        prettySwitch = findViewById(R.id.prettySwitch)
        prettySwitch.isChecked = try { DisplayMode.valueOf(preferences.getString("listDisplay", null) ?: "") }
            catch(e: Exception) { DisplayMode.STANDARD } == DisplayMode.PRETTY
        prettySwitch.setOnClickListener {
            adapter.notifyDataSetChanged()
        }

        clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager?
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_list, menu)
        menu.findItem(R.id.deleteItems).isVisible = listWhat == "H"
        updateToolbar()
        return true
    }
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        var text = ""
        if (item.itemId in setOf(R.id.copyItems, R.id.shareItems))
            for (listItem in (if (listWhat == "H") items.reversed() else items)) if (adapter.selectedItems == 0 || listItem.selected)
                text += listItem.toStrings(this, removeQuotes = true).let { it.first + "\n" + it.second } + "\n\n"
        text = text.removeSuffix("\n")
        when (item.itemId) {
            android.R.id.home -> if (adapter.selectedItems > 0) {
                for (listItem in items) listItem.selected = false
                adapter.selectedItems = 0
                adapter.notifyDataSetChanged()
                listSel = ""
                updateToolbar()
            } else finish()
            R.id.selectAllItems -> {
                for (listItem in items) listItem.selected = true
                adapter.selectedItems = items.size
                adapter.notifyDataSetChanged()
                updateToolbar()
            }
            R.id.copyItems -> {
                clipboard?.setPrimaryClip(ClipData.newPlainText(null, text))
                Toast.makeText(applicationContext, getString(if (adapter.selectedItems == 0)
                    R.string.clipboard_all_ok else R.string.clipboard_ok), Toast.LENGTH_SHORT).show()
            }
            R.id.shareItems -> shareText(this, text, if (adapter.selectedItems == 0) getString(R.string.share_all) else null)
            R.id.deleteItems -> AlertDialog.Builder(this)
                .setTitle(if (adapter.selectedItems == 0)
                    getString(R.string.delete_history_q) else resources.getQuantityString(R.plurals.delete_q, adapter.selectedItems))
                .setMessage(R.string.cant_be_undone)
                .setPositiveButton(R.string.yes) { _, _ ->
                    if (adapter.selectedItems == 0) items.clear() else
                        for ((i, listItem) in items.withIndex().reversed()) if (listItem.selected) {
                            adapter.notifyItemRemoved(i)
                            items.remove(listItem)
                        }
                    adapter.selectedItems = 0
                    if (items.size == 0) finish() else updateToolbar()
                }
                .setNegativeButton(R.string.no) { _, _ -> }
                .create().show()
            R.id.settingsListItem -> startActivity(Intent(this, SettingsActivity::class.java))
        }
        return true
    }

    override fun onResume() {
        super.onResume()
        MainActivity.getOutputSettings(preferences)
        adapter.notifyDataSetChanged()
    }

    override fun onPause() {
        super.onPause()
        val editor = preferences.edit()
        editor.putString("listSel${listWhatToken(merge = true)}", prefsMapping.mapIndexed { i, it ->
            if (it in 0 until items.size) { if (items[it].selected) '1' else '0' } else if (it == -1 && i < listSel.length) listSel[i] else '0'
        }.joinToString(""))
        editor.putString("listExpand${listWhatToken(merge = true)}", prefsMapping.mapIndexed { i, it ->
            if (it in 0 until items.size) { if (items[it].expanded) '1' else '0' } else if (it == -1 && i < listExpand.length) listExpand[i] else '0'
        }.joinToString(""))
        editor.putString("listDisplay", (if (prettySwitch.isChecked) DisplayMode.PRETTY else DisplayMode.STANDARD).toString())
        if (listWhat == "H") putHistory(preferences, editor, items)
        if (adapter.clickedItem > -1 && listWhat.isNotEmpty()) {
            editor.putString("listInput", listWhat[0] + if (listWhat[0] in 'A'..'Z') adapter.clickedItem.toString() else
                (items[adapter.clickedItem].number.toPreferencesString() +
                    "/${items[adapter.clickedItem].egyptianMethod}/${listWhat.substringAfterLast('/')}"))
            if (listWhat[0] == 'm') editor.putString("input", "")
            val intent = Intent(this, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
            startActivity(intent)
        }
        editor.apply()
    }

    fun updateToolbar() {
        supportActionBar?.setHomeAsUpIndicator(if (adapter.selectedItems == 0) 0 else R.drawable.ic_close)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP)
            toolbar.title = if (adapter.selectedItems == 0) toolbar.contentDescription else "${adapter.selectedItems}/${items.size}"
        toolbar.menu?.findItem(R.id.selectAllItems)?.isVisible = adapter.selectedItems > 0
    }

    fun listWhatToken(merge: Boolean) = listWhat.firstOrNull().let { if (merge && it in 'a'..'z') "" else it.toString() }
}
