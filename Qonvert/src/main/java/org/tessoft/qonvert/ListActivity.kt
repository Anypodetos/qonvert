package org.tessoft.qonvert

/*
Copyright 2021, 2022 Anypodetos (Michael Weber)

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
import android.net.Uri
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.appcompat.widget.Toolbar
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.view.MenuCompat
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import java.math.BigInteger.*
import kotlin.math.*

val MIN_PIE = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P

class QNumberEntry(val inputString: String, val number: QNumber, val egyptianMethod: EgyptianMethod =
        if (MainActivity.egyptianMethod == EgyptianMethod.OFF) EgyptianMethod.BINARY else MainActivity.egyptianMethod,
        var trustInput: Boolean = true) {

    var selected = false
    var expanded = false
    var outputBuffer = ""

    fun toStrings(activity: ListActivity?): Pair<String, String> {
        if (outputBuffer == "") outputBuffer = number.toString(withBaseSystem = true,
            mode = if (activity != null) DisplayMode.values()[activity.outputRadioIds.indexOf(activity.outputRadioGroup.checkedRadioButtonId)]
                else DisplayMode.STANDARD,
            aEgyptianMethod = egyptianMethod)
        return Pair(activity?.getString(R.string.item_header, inputString) ?: "", outputBuffer)
    }
}

class RecyclerAdapter internal constructor(private val activity: ListActivity?, private val items: MutableList<QNumberEntry>) :
        RecyclerView.Adapter<RecyclerAdapter.ViewHolder>() {

    var selectedItems = 0
    var lastSelectedItem = -1
        private set
    var clickedItem = -1
        private set
    private val inflater: LayoutInflater = LayoutInflater.from(activity)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(inflater.inflate(R.layout.fragment_list, parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        with(items[position].toStrings(activity)) {
            holder.inputText.text = first
            holder.outputText.text = second
        }
        holder.outputText.typeface = if (items[position].number.system in setOf(NumSystem.GREEK, NumSystem.ROMAN) && items[position].number.format != QFormat.UNICODE
            || items[position].number.format in setOf(QFormat.GREEK_NATURAL, QFormat.ROMAN_NATURAL)) Typeface.SERIF else Typeface.DEFAULT

        if (!MIN_PIE) items[position].expanded = true
        holder.outputText.post { holder.expandButton.visibility = if (holder.outputText.lineCount > 3 && MIN_PIE) View.VISIBLE else View.GONE }
        holder.outputText.maxLines = if (items[position].expanded) 100_000 else 3
        holder.expandButton.rotation = if (items[position].expanded) 180f else 0f

        holder.extraButton.visibility = if (holder.listWhatToken == "I" || items[position].number.format == QFormat.UNICODE) View.VISIBLE else View.GONE
        if (holder.extraButton.visibility == View.VISIBLE) holder.extraButton.text = if (holder.listWhatToken == "I") "♫" else "🌐\uFE0E"

        activity?.let {
            holder.backView.setBackgroundColor(if (items[position].selected)
                ContextCompat.getColor(it, MainActivity.resolveColor(android.R.attr.colorMultiSelectHighlight)) else 0)
        }
    }

    override fun getItemCount() = items.size

    inner class ViewHolder internal constructor(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val listWhatToken = activity?.listWhatToken(merge = false) ?: ""
        val backView: View = itemView.findViewById(R.id.backView)
        private val listFormatsButton: FloatingActionButton = itemView.findViewById(R.id.listFormatsButton)
        val inputText: TextView = itemView.findViewById(R.id.inputText)
        val outputText: TextView = itemView.findViewById(R.id.outputText)
        val extraButton: TextView = itemView.findViewById(R.id.extraButton)
        val expandButton: FloatingActionButton = itemView.findViewById(R.id.expandButton)

        private fun changeSelection(range: Boolean) {
            if (range) {
                val start = min(adapterPosition, lastSelectedItem)
                val end   = max(adapterPosition, lastSelectedItem)
                for (i in start..end) with (items[i]) {
                    if (!selected) {
                        selected = true
                        selectedItems++
                    }
                }
                lastSelectedItem = adapterPosition
                notifyItemRangeChanged(start, end - start + 1)
            } else with(items[adapterPosition]) {
                selected = !selected
                if (selected) {
                    lastSelectedItem = adapterPosition
                    selectedItems++
                } else selectedItems--
                notifyItemChanged(adapterPosition)
            }
            activity?.updateToolbar()
        }

        init {
            if (listWhatToken == "H" && activity != null) {
                val inputColor = inputText.textColors
                inputText.setTextColor(outputText.textColors)
                outputText.setTextColor(inputColor)
            }

            itemView.setOnClickListener {
                if (selectedItems == 0) {
                    clickedItem = adapterPosition
                    activity?.finish()
                } else changeSelection(range = false)
            }
            itemView.setOnLongClickListener {
                changeSelection(range = selectedItems > 0)
                true
            }

            listFormatsButton.visibility = if (listWhatToken in "A".."Z") {
                listFormatsButton.setOnClickListener {
                    val intent = Intent(activity, ListActivity::class.java)
                    intent.putExtra("list", listWhatToken.lowercase() + items[adapterPosition].number.toPreferencesString() + "/$adapterPosition")
                    activity?.startActivity(intent)
                }
                View.VISIBLE
            } else View.GONE

            expandButton.setOnClickListener {
                with(items[adapterPosition]) { expanded = !expanded }
                notifyItemChanged(adapterPosition)
            }

            extraButton.setOnClickListener {
                activity?.let {
                    with (items[adapterPosition].number) {
                        if (listWhatToken == "I") play(it) else
                            it.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.fileformat.info/info/unicode/char/" + numerator.toString(16))))
                    }
                }
            }

            itemView.findViewById<TextView>(R.id.menuButton).setOnClickListener { view ->
                val popupMenu = PopupMenu(activity, view)
                MenuCompat.setGroupDividerEnabled(popupMenu.menu, true)
                popupMenu.inflate(R.menu.menu_list)
                popupMenu.menu.findItem(R.id.deleteItems).isVisible = listWhatToken == "H"
                popupMenu.menu.findItem(R.id.playItem).isVisible = listWhatToken == "H"
                popupMenu.menu.findItem(R.id.settingsListItem).isVisible = false
                popupMenu.setOnMenuItemClickListener { item: MenuItem ->
                    val text = if (item.itemId in setOf(R.id.copyItems, R.id.shareItems))
                        items[adapterPosition].toStrings(activity).second else ""
                    when (item.itemId) {
                        R.id.copyItems -> activity?.let {
                            it.clipboard?.setPrimaryClip(ClipData.newPlainText(null, text))
                            Toast.makeText(it.applicationContext, it.getString(R.string.clipboard_ok), Toast.LENGTH_SHORT).show()
                        }
                        R.id.shareItems -> shareText(activity, text)
                        R.id.deleteItems -> activity?.let {
                            AlertDialog.Builder(it)
                                .setTitle(R.string.delete_this_q)
                                .setMessage(R.string.cant_be_undone)
                                .setPositiveButton(R.string.delete) { _, _ ->
                                    val pos = adapterPosition
                                    notifyItemRemoved(pos)
                                    if (items[pos].selected) selectedItems--
                                    items.remove(items[pos])
                                    if (items.size == 0) it.finish() else it.updateToolbar()
                                }
                                .setNegativeButton(R.string.cancel) { _, _ -> }
                                .create().show()
                        }
                        R.id.playItem -> activity?.let {
                            items[adapterPosition].number.play(it)
                        }
                    }
                    true
                }
                popupMenu.setOnDismissListener {
                    outputText.setBackgroundColor(0)
                }
                activity?.let {
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
    private lateinit var baseText: TextView
    private lateinit var recycler: RecyclerView
    private lateinit var adapter: RecyclerAdapter
    lateinit var outputRadioGroup: RadioGroup
    var outputRadioIds = arrayOf(R.id.standardRadio, R.id.prettyRadio, R.id.compatibleRadio)
    private lateinit var preferences: SharedPreferences
    private val items = mutableListOf<QNumberEntry>()
    private var listSel = ""
    private var listExpand = ""
    private var prefsMapping = mutableListOf<Int>()
    var clipboard: ClipboardManager? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        MainActivity.setQonvertTheme(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_list)

        toolbar = findViewById(R.id.listToolbar)
        baseText = findViewById(R.id.baseText)
        recycler = findViewById(R.id.recycler)
        outputRadioGroup = findViewById(R.id.outputRadioGroup)

        listWhat = intent.getStringExtra("list") ?: ""
        toolbar.contentDescription = getString(when (listWhat) {
            "H" -> R.string.menu_history
            "I" -> R.string.menu_interval_list
            in "a".."z" -> R.string.menu_formats
            else -> 0
        })
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        recycler.layoutManager = LinearLayoutManager(this).apply {
            if (listWhat == "H") {
                reverseLayout = true
                stackFromEnd = true
            }
        }
        adapter = RecyclerAdapter(this, items)
        recycler.adapter = adapter
        preferences = PreferenceManager.getDefaultSharedPreferences(this)
        var base = 10
        var system = NumSystem.STANDARD
        when (listWhat.firstOrNull()) {
            'H' -> getHistory(preferences, items)
            'I' -> {
                base = preferences.getInt("inBase", 10)
                system = try { NumSystem.valueOf(preferences.getString("inSystem",  null) ?: "") } catch (e: Exception) { NumSystem.STANDARD }
                for (i in listOf(ONE, TWO)) for (interval in INTERVALS)
                    with(QNumber(interval.first * i, interval.second, base, system, format = QFormat.FRACTION)) {
                        items.add(QNumberEntry(toInterval(resources), this))
                    }
                with(QNumber(4.toBigInteger(), ONE, base, system, format = QFormat.FRACTION)) {
                    items.add(QNumberEntry(toInterval(resources), this))
                }
            }
            in 'a'..'z' -> {
                val q = QNumber(preferencesEntry = listWhat.substring(1))
                base = q.base
                system = q.system
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
        baseText.visibility = if (listWhat == "H") View.GONE else {
            baseText.text =  getString(R.string.bare_base, base, resources.getStringArray(R.array.num_systems)[system.ordinal])
            View.VISIBLE
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
        outputRadioGroup.check(outputRadioIds[(try { DisplayMode.valueOf(preferences.getString("listDisplay", null) ?: "") }
            catch(e: Exception) { DisplayMode.STANDARD }).ordinal])
        for (r in outputRadioIds) findViewById<RadioButton>(r).setOnClickListener {
            for (listItem in items) listItem.outputBuffer = ""
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
                text += listItem.toStrings(this).run { first + "\n" + second } + "\n\n"
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
                Toast.makeText(applicationContext, getString(if (adapter.selectedItems == 0 && items.size > 1)
                    R.string.clipboard_all_ok else R.string.clipboard_ok, items.size), Toast.LENGTH_SHORT).show()
            }
            R.id.shareItems -> shareText(this, text, if (adapter.selectedItems == 0 && items.size > 1) getString(R.string.share_all, items.size) else null)
            R.id.deleteItems -> AlertDialog.Builder(this)
                .setTitle(if (adapter.selectedItems == 0)
                    getString(R.string.delete_history_q) else resources.getQuantityString(R.plurals.delete_q, adapter.selectedItems))
                .setMessage(R.string.cant_be_undone)
                .setPositiveButton(R.string.delete) { _, _ ->
                    if (adapter.selectedItems == 0) items.clear() else
                        for ((i, listItem) in items.withIndex().reversed()) if (listItem.selected) {
                            adapter.notifyItemRemoved(i)
                            items.remove(listItem)
                        }
                    adapter.selectedItems = 0
                    if (items.size == 0) finish() else updateToolbar()
                }
                .setNegativeButton(R.string.cancel) { _, _ -> }
                .create().show()
            R.id.settingsListItem -> startActivity(Intent(this, SettingsActivity::class.java))
        }
        return true
    }

    override fun onResume() {
        super.onResume()
        MainActivity.getOutputSettings(preferences)
        for (listItem in items) listItem.outputBuffer = ""
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
        editor.putString("listDisplay", (DisplayMode.values()[outputRadioIds.indexOf(outputRadioGroup.checkedRadioButtonId)]).toString())
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
            toolbar.title = if (adapter.selectedItems == 0) toolbar.contentDescription else "${adapter.selectedItems}/${items.size}"
        toolbar.menu?.findItem(R.id.selectAllItems)?.isVisible = adapter.selectedItems > 0
    }

    fun listWhatToken(merge: Boolean) = listWhat.firstOrNull().let { if (merge && it in 'a'..'z') "" else it.toString() }
}
