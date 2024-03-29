package org.tessoft.qonvert

/*
Copyright 2021, 2022, 2023 Anypodetos (Michael Weber)

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
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.widget.SwitchCompat
import androidx.appcompat.widget.Toolbar
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.content.ContextCompat
import androidx.core.view.MenuCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.BaseTransientBottomBar
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.*
import java.math.BigInteger.*
import kotlin.math.*

val MIN_PIE = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P

enum class DisplayMode {
    STANDARD, DISSECT, PRETTY, COMPATIBLE
}

class QNumberEntry(val inputString: String, val number: QNumber, val egyptianMethod: EgyptianMethod = EgyptianMethod.OFF) {

    var selected = false
    var expanded = false
    var outputBuffer = ""

    fun toInString(activity: ListActivity?) = activity?.getString(R.string.item_header, inputString) ?: ""

    fun toOutString(activity: ListActivity?): String {
        if (outputBuffer.isEmpty()) outputBuffer = number.toString(withBaseSystem = true,
            mode = if (activity != null) DisplayMode.values()[activity.outputRadioIds.indexOf(activity.outputRadioGroup.checkedRadioButtonId)]
                else DisplayMode.STANDARD,
            aEgyptianMethod = egyptianMethod)
        return outputBuffer
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

    private val rDigitsInt  = listOf(R.string.digits_int, R.string.digits_int_phi)
    private val rDigitsFrac = listOf(R.string.digits_frac, R.string.digits_frac_phi)
    private val rDigitsPre  = listOf(R.string.digits_pre, R.string.digits_pre_phi)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(inflater.inflate(R.layout.fragment_list, parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.inputText.text = items[position].toInString(activity)
        if (holder.outputText.text != items[position].outputBuffer || holder.outputText.text.isEmpty()) {
            holder.outputText.text = "🕰"
            holder.expandButton.visibility = View.GONE
            holder.extraButton.visibility = View.GONE
            holder.menuButton.isEnabled = false
        }
        activity?.lifecycleScope?.launch(Dispatchers.Default) {
            val s = items[position].toOutString(activity)
            withContext(Dispatchers.Main) {
                holder.outputText.text = s
                holder.outputText.typeface = MainActivity.resolveFont(greekOrRoman = items[position].number.system in setOf(NumSystem.GREEK, NumSystem.ROMAN) &&
                    items[position].number.format != QFormat.UNICODE || items[position].number.format in setOf(QFormat.GREEK_NATURAL, QFormat.ROMAN_NATURAL))

                if (!MIN_PIE) items[position].expanded = true
                holder.outputText.post { holder.expandButton.visibility = if (holder.outputText.lineCount > 3 && MIN_PIE) View.VISIBLE else View.GONE }
                holder.outputText.maxLines = if (items[position].expanded) 100_000 else 3
                holder.expandButton.rotation = if (items[position].expanded) 180f else 0f

                holder.extraButton.visibility = if (holder.listWhatToken == "I" || items[position].number.format == QFormat.UNICODE) View.VISIBLE else View.GONE
                if (holder.extraButton.visibility == View.VISIBLE) holder.extraButton.text = if (holder.listWhatToken == "I") "♫" else "🌐\uFE0E"

                holder.menuButton.isEnabled = true
                holder.backView.setBackgroundColor(if (items[position].selected) resolveColor(activity, android.R.attr.colorMultiSelectHighlight) else 0)
            }
        }
    }

    override fun getItemCount() = items.size

    inner class ViewHolder internal constructor(itemView: View) : RecyclerView.ViewHolder(itemView) {

        val listWhatToken = activity?.listWhatToken(mergeFormatsScreens = false) ?: ""
        val backView: View = itemView.findViewById(R.id.backView)
        private val listFormatsButton: FloatingActionButton = itemView.findViewById(R.id.listFormatsButton)
        val inputText: TextView = itemView.findViewById(R.id.inputText)
        val outputText: TextView = itemView.findViewById(R.id.outputText)

        val extraButton: TextView = itemView.findViewById(R.id.extraButton)
        val expandButton: FloatingActionButton = itemView.findViewById(R.id.expandButton)
        val menuButton: TextView = itemView.findViewById(R.id.menuButton)

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
                    if (listWhatToken == "H" && activity != null)
                        with(items[adapterPosition].number) { putListBase(activity.preferences, base, system, complement, dms) }
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
                    with(items[adapterPosition].number) {
                        if (listWhatToken == "I") {
                            play(it)
                            it.lastPlayedInterval = adapterPosition
                        } else it.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.fileformat.info/info/unicode/char/" + numerator.toString(16))))
                    }
                }
            }

            menuButton.setOnClickListener { view ->
                val popupMenu = PopupMenu(activity, view)
                MenuCompat.setGroupDividerEnabled(popupMenu.menu, true)
                popupMenu.inflate(R.menu.menu_list)
                popupMenu.menu.findItem(R.id.deleteItems).isVisible = listWhatToken == "H"
                popupMenu.menu.findItem(R.id.playItem).isVisible = listWhatToken == "H" &&
                    items[adapterPosition].number.let { abs(it.numerator.toDouble() / it.denominator.toDouble()).toFloat() } in 1/128.0..128.0
                popupMenu.menu.findItem(R.id.settingsListItem).isVisible = false
                val countDenominators = items[adapterPosition].let {
                    (it.number.format == QFormat.CONTINUED && it.number.usefulFormat(QFormat.CONTINUED)) ||
                        (it.number.format == QFormat.EGYPTIAN && it.number.usefulFormat(QFormat.EGYPTIAN, useEgyptian(it.number.base, it.egyptianMethod)))
                }
                with(popupMenu.menu.findItem(R.id.countItem)) {
                    isVisible = items[adapterPosition].number.format != QFormat.UNICODE
                    setTitle(if (countDenominators) R.string.count_denominators else R.string.count_digits)
                }
                popupMenu.setOnMenuItemClickListener { item: MenuItem ->
                    val text = if (item.itemId in setOf(R.id.copyItems, R.id.shareItems)) items[adapterPosition].toOutString(activity) else ""
                    when (item.itemId) {
                        R.id.copyItems -> activity?.let {
                            it.clipboard?.setPrimaryClip(ClipData.newPlainText(null, text))
                            Toast.makeText(it.applicationContext, it.getString(R.string.clipboard_ok), Toast.LENGTH_SHORT).show()
                        }
                        R.id.shareItems -> shareText(activity, text)
                        R.id.deleteItems -> activity?.delete(adapterPosition)
                        R.id.countItem -> activity?.let { it ->
                            val title = it.getString(if (countDenominators) R.string.count_denominators_title else R.string.count_digits_title)
                            var message: String? = null
                            val countDialog = AlertDialog.Builder(it)
                                .setTitle(title)
                                .setMessage(R.string.counting)
                                .setPositiveButton(R.string.copy_my) { _, _ ->
                                    it.clipboard?.setPrimaryClip(ClipData.newPlainText(null, title + "\n\n" + message))
                                    Toast.makeText(it.applicationContext, it.getString(R.string.clipboard_ok), Toast.LENGTH_SHORT).show()
                                }
                                .setNegativeButton(R.string.close) { _, _ -> }
                                .create()
                            countDialog.show()
                            countDialog.getButton(AlertDialog.BUTTON_POSITIVE).isVisible = false
                            it.lifecycleScope.launch(Dispatchers.Default) {
                                var countSt = items[adapterPosition].number.toString(aEgyptianMethod = items[adapterPosition].egyptianMethod).lowercase()
                                for ((key, value) in ROMAN_APOSTROPHUS) countSt = countSt.replace(key, value)
                                if (items[adapterPosition].number.system == NumSystem.ROMAN)
                                    countSt.findAnyOf(ROMAN_1_12.drop(1) + "…", ignoreCase = true)?.first?.let {
                                        countSt = countSt.substring(0, it) + "." + countSt.substring(it)
                                    }
                                countSt = countSt.replace(". ", "").filterNot { it in " -\"͵ʹ|" }
                                message = if (countDenominators) {
                                    val phinary = abs(items[adapterPosition].number.base) == 1
                                    val cutOff = '…' in countSt
                                    val list = countSt.substring(1, countSt.lastIndex).split(";", ",").let { if (cutOff) it.dropLast(1) else it }
                                    val n = list.size - 1
                                    it.resources.getQuantityString(R.plurals.denominators, n, if (cutOff) "> $n" else "$n") + "\n\u2003—\n" +
                                        (it.getString(rDigitsInt[if (phinary) 1 else 0], list.firstOrNull()?.let { st ->
                                            formatDigits(st.length, st.startsWith(".."))
                                        }) +
                                        if (list.size > 1) "\n" + it.resources.getQuantityString(R.plurals.denom_digit_list, list.size - 1,
                                            list.drop(1).joinToString(", ") { it.length.toString() } + (if (cutOff) ", …" else ""))
                                            else "")
                                } else if (countSt in listOf("∞", "無" , "/", "/°")) formatDigits(0) else {
                                    val deg = countSt.indexOf('°')
                                    val min = countSt.indexOf('\'')
                                    val degMin = max(deg, min)
                                    (if (deg > -1) countDigits(if (deg == countSt.lastIndex) 0 else R.string.digits_deg,
                                            countSt.substring(0, deg)) + "\n\n" else "") +
                                        (if (min > -1) countDigits(if (min == countSt.lastIndex && deg == -1) 0 else R.string.digits_min,
                                            countSt.substring(deg + 1, min)) + "\n\n" else "") +
                                        (if (countSt.lastIndex > degMin) countDigits(if (degMin == -1) 0 else R.string.digits_sec,
                                            countSt.substring(degMin + 1)) else "")
                                }
                                withContext(Dispatchers.Main) {
                                    countDialog.setMessage(message)
                                    countDialog.getButton(AlertDialog.BUTTON_POSITIVE).isVisible = true
                                }
                            }
                        }
                        R.id.playItem -> activity?.let {
                            items[adapterPosition].number.play(it)
                            it.lastPlayedInterval = adapterPosition
                        }
                    }
                    true
                }
                popupMenu.setOnDismissListener {
                    outputText.setBackgroundColor(0)
                }
                activity?.let {
                    outputText.setBackgroundColor(resolveColor(it, android.R.attr.colorMultiSelectHighlight))
                    it.snackbar?.dismiss()
                }
                popupMenu.show()
            }
        }

        private fun changeSelection(range: Boolean) {
            if (range && lastSelectedItem > -1) {
                val start = min(adapterPosition, lastSelectedItem)
                val end = max(adapterPosition, lastSelectedItem)
                for (i in start..end) with(items[i]) {
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

        private fun countDigits(header: Int, st: String): String = activity?.let {
            val complement = st.startsWith("..")
            val slash = st.indexOf('/')
            val rType = if (abs(items[adapterPosition].number.base) == 1) 1 else 0
            (if (header == 0) "" else it.getString(header) + "\n") + if (slash == -1) {
                val point = "$st.".indexOf('.', if (complement) 2 else 0)
                val rep = st.indexOf('˙')
                val cutOff = '…' in st
                it.getString(if (point < st.length) rDigitsInt[rType] else R.string.digits_all, formatDigits(point, complement)) +
                    (if (point < st.length && rep == -1)
                        "\n" + it.getString(rDigitsFrac[rType], formatDigits(st.length - point - 1, cutOff = cutOff)) else "") +
                    (if (rep > -1) (if (rep - point > 1) "\n" + it.getString(rDigitsPre[rType], formatDigits(rep - point - 1)) else "") +
                        "\n" + it.getString(R.string.digits_rep, formatDigits(st.length - rep - 1, cutOff = cutOff)) else "")
            } else {
                val under = st.indexOf('_')
                (if (under > -1) it.getString(rDigitsInt[rType], formatDigits(under, complement)) + "\n" else "") +
                    it.getString(R.string.digits_numer, formatDigits(slash - under - 1, complement && under == -1)) + "\n" +
                    it.getString(R.string.digits_denom, formatDigits(st.length - slash - 1))
            }
        } ?: ""

        private fun formatDigits(n: Int, complement: Boolean = false, cutOff: Boolean = false): String? {
            val m = if (cutOff) n - 1 else n
            return if (complement) activity?.getString(R.string.complement_digits) else
                activity?.resources?.getQuantityString(R.plurals.digits, m, if (cutOff) "> $m" else "$m")
        }
    }
}

@SuppressLint("NotifyDataSetChanged")
class ListActivity : AppCompatActivity() {

    private var listWhat = ""
    private lateinit var coordinator: CoordinatorLayout
    private lateinit var toolbar: Toolbar
    private lateinit var baseSelector: LinearLayout
    private lateinit var baseDivider: View
    private lateinit var listBaseButton: Button
    private lateinit var listSystemButton: Button
    private lateinit var listComplementSwitch: SwitchCompat
    private lateinit var listDmsSwitch: SwitchCompat
    private lateinit var recycler: RecyclerView
    private lateinit var adapter: RecyclerAdapter
    lateinit var outputRadioGroup: RadioGroup
    var outputRadioIds = listOf(R.id.defaultRadio, R.id.dissectRadio, R.id.prettyRadio, R.id.compatibleRadio)
    lateinit var preferences: SharedPreferences
    var clipboard: ClipboardManager? = null
    var snackbar: Snackbar? = null
    var base = 10
    var system = NumSystem.STANDARD
    private val items = mutableListOf<QNumberEntry>()
    private var listSel = ""
    private var listExpand = ""
    private var prefsMapping = mutableListOf<Int>()
    var lastPlayedInterval = -1

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_list)

        coordinator = findViewById(R.id.coordinator)
        toolbar = findViewById(R.id.listToolbar)
        baseSelector = findViewById(R.id.baseSelector)
        listBaseButton = findViewById(R.id.listBaseButton)
        listSystemButton = findViewById(R.id.listSystemButton)
        listComplementSwitch = findViewById(R.id.listComplementSwitch)
        listDmsSwitch = findViewById(R.id.listDmsSwitch)
        baseDivider = findViewById(R.id.baseDivider)
        recycler = findViewById(R.id.recycler)
        outputRadioGroup = findViewById(R.id.outputRadioGroup)

        preferences = PreferenceManager.getDefaultSharedPreferences(this)
        clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager?
        listWhat = intent.getStringExtra("list") ?: ""

        toolbar.contentDescription = getString(when (listWhat) {
            "H" -> R.string.menu_history
            "I" -> R.string.menu_interval_list
            in "a".."z" -> R.string.menu_formats
            else -> R.string.menu_undefined
        })
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        /* recycler */
        adapter = RecyclerAdapter(this, items)
        recycler.adapter = adapter
        recycler.layoutManager = LinearLayoutManager(this).apply {
            if (listWhat == "H") {
                reverseLayout = true
                stackFromEnd = true
            }
        }
        recycler.itemAnimator = object : DefaultItemAnimator() {
            override fun canReuseUpdatedViewHolder(viewHolder: RecyclerView.ViewHolder) = true
        }
        if (listWhat == "H") ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.END) {

            private val deletePaint = Paint().apply {
                style = Paint.Style.FILL
                color = resolveColor(this@ListActivity, R.attr.colorSecondary)
            }

            override fun onMove(view: RecyclerView, holder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean = false

            override fun onChildDraw(c: Canvas, recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, dX: Float, dY: Float, actionState: Int,
                                     isCurrentlyActive: Boolean) {
                viewHolder.itemView.findViewById<View>(R.id.layout)?.let { layout ->
                    c.drawRect(layout.left.toFloat(), layout.top.toFloat(), layout.left.toFloat() + dX, layout.bottom.toFloat(), deletePaint)
                    AppCompatResources.getDrawable(this@ListActivity, R.drawable.ic_delete)?.let {
                        val left = min(layout.left + it.intrinsicWidth, dX.toInt() - 2 * it.intrinsicWidth)
                        val top = layout.top + (layout.height - it.intrinsicHeight) / 2
                        it.setBounds(left, top, left + it.intrinsicWidth, top + it.intrinsicHeight)
                        it.draw(c)
                    }
                }
                super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                if (direction == ItemTouchHelper.END) delete(viewHolder.adapterPosition)
            }

        }).attachToRecyclerView(recycler)

        /* base, system etc. */
        base = preferences.getInt("listBase", 10)
        system = NumSystem.values().find { it.name == preferences.getString("listSystem",  null) } ?: NumSystem.STANDARD
        listComplementSwitch.isChecked = preferences.getBoolean("listComplement", false)
        listDmsSwitch.isChecked = preferences.getBoolean("listDMS", false)

        baseSelector.visibility = if (listWhat == "H") View.GONE else View.VISIBLE
        baseDivider.visibility = baseSelector.visibility

        val gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(e1: MotionEvent, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
                base = saneBase(base + (velocityX * base).sign.toInt(), base)
                fillItemsAndBase()
                return true
            }
        })

        listBaseButton.setOnClickListener {
            showBaseDialog(this, getString(R.string.base_dialogTitle, MAX_BASE), base, 10) { newBase, _ ->
                base = newBase
                fillItemsAndBase()
            }
        }
        listBaseButton.setOnLongClickListener {
            listBaseButton.playSoundEffect(SoundEffectConstants.CLICK)
            base = 10
            fillItemsAndBase()
            true
        }
        listBaseButton.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            false
        }

        listSystemButton.setOnClickListener { view ->
            val popupMenu = PopupMenu(this, view)
            for ((j, res) in resources.getStringArray(R.array.num_systems).withIndex()) popupMenu.menu.add(1, Menu.NONE, j, res).isChecked = j == system.ordinal
            popupMenu.menu.setGroupCheckable(1, true, true)
            popupMenu.setOnMenuItemClickListener { item: MenuItem ->
                system = NumSystem.values()[item.order]
                base = allowedBase(base, system)
                fillItemsAndBase()
                true
            }
            popupMenu.show()
        }
        listSystemButton.setOnLongClickListener {
            it.playSoundEffect(SoundEffectConstants.CLICK)
            system = if (system == NumSystem.STANDARD) NumSystem.BALANCED else NumSystem.STANDARD
            base = allowedBase(base, system)
            fillItemsAndBase()
            true
        }
        listSystemButton.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            false
        }

        listComplementSwitch.setOnClickListener { fillItemsAndBase() }
        listDmsSwitch.setOnClickListener { fillItemsAndBase() }
        listDmsSwitch.isEnabled = listWhat != "I"

        fillItemsAndBase()  /* item list */

        if (prefsMapping.size == 0) prefsMapping = MutableList(items.size) { it }
        adapter.selectedItems = 0
        listSel = preferences.getString("listSel${listWhatToken(mergeFormatsScreens = true)}", null) ?: ""
        for ((i, b) in (listSel).withIndex()) if (i < prefsMapping.size && prefsMapping[i] != -1 && b == '1') {
            items[prefsMapping[i]].selected = true
            adapter.selectedItems++
        }
        listExpand = preferences.getString("listExpand${listWhatToken(mergeFormatsScreens = true)}", null) ?: ""
        for ((i, b) in (listExpand).withIndex()) if (i < prefsMapping.size && prefsMapping[i] != -1 && b == '1') {
            items[prefsMapping[i]].expanded = true
        }
        outputRadioGroup.check(outputRadioIds[DisplayMode.values().find { it.name == preferences.getString("listDisplay", null) }?.ordinal ?: 0])
        for (r in outputRadioIds) findViewById<RadioButton>(r).setOnClickListener {
            snackbar?.dismiss()
            for (listItem in items) listItem.outputBuffer = ""
            adapter.notifyDataSetChanged()
        }
    }

    private fun fillItemsAndBase() {
        items.clear()
        var complementUseful = false
        when (listWhat.firstOrNull()) {
            'H' -> getHistory(preferences, items)
            'I' -> {
                for (i in listOf(ONE, TWO)) for (interval in INTERVALS)
                    with(QNumber(interval.first * i, interval.second, base, system, format = QFormat.FRACTION)) {
                        items.add(QNumberEntry(toInterval(resources), this))
                    }
                with(QNumber(4.toBigInteger(), ONE, base, system, format = QFormat.FRACTION)) {
                    items.add(QNumberEntry(toInterval(resources), this))
                }
            }
            in 'a'..'z' -> {                                               /* 'm' = from output area of MainActivity */
                val q = QNumber(preferencesEntry = listWhat.substring(1))
                q.changeBase(base, system, listComplementSwitch.isChecked, listDmsSwitch.isChecked)
                complementUseful = q.numerator < ZERO
                val formatsArray = resources.getStringArray(R.array.formats)
                for (f in QFormat.values()) if (q.usefulFormat(f)) {
                    prefsMapping.add(items.count())
                    items.add(QNumberEntry(formatsArray[f.ordinal], q.copy(f)))
                } else {
                    prefsMapping.add(-1)
                    if (f == QFormat.EGYPTIAN) {
                        val egyptianArray = resources.getStringArray(R.array.egyptian_entries).mapIndexed { i, st ->
                            if (abs(base) > 1 || i != EgyptianMethod.BINARY.ordinal) st.substringBefore(" (") else resources.getString(R.string.egyptian_phinary)
                        }
                        for (e in EgyptianMethod.values()) if (q.usefulFormat(QFormat.EGYPTIAN, e)) {
                            prefsMapping.add(items.count())
                            items.add(QNumberEntry(resources.getString(R.string.egyptian_method, egyptianArray[e.ordinal]),
                                q.copy(QFormat.EGYPTIAN), egyptianMethod = e))
                        } else prefsMapping.add(-1)
                    }
                }
            }
        }
        adapter.notifyDataSetChanged()
        if (listWhat != "H") {
            listBaseButton.text = baseToString(base)
            listBaseButton.setTypeface(null, if (base > 0) Typeface.BOLD else Typeface.ITALIC)
            listSystemButton.text = resources.getStringArray(R.array.num_systems_short)[system.ordinal]
            listSystemButton.setBackgroundColor(if (allowedSystem(base, system).second)
                resolveColor(this, R.attr.colorPrimaryVariant) else ContextCompat.getColor(this,R.color.grey))
            listComplementSwitch.isEnabled = complementUseful && complementAllowed(base, system)
        }
    }


    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_list, menu)
        menu.findItem(R.id.deleteItems).isVisible = listWhat == "H"
        updateToolbar()
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu?): Boolean {
        menu?.findItem(R.id.deleteItems)?.setTitle(if (adapter.selectedItems == 0) R.string.clear_history else R.string.delete)
        if (items.size > 0) snackbar?.dismiss()
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (items.size > 0) snackbar?.dismiss()
        var text = ""
        if (item.itemId in setOf(R.id.copyItems, R.id.shareItems))
            for (listItem in (if (listWhat == "H") items.reversed() else items)) if (adapter.selectedItems == 0 || listItem.selected)
                text += listItem.toInString(this) + "\n" + listItem.toOutString(this) + "\n\n"
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
                Toast.makeText(applicationContext, getString(if ((adapter.selectedItems == 0 || adapter.selectedItems == items.size) && items.size > 1)
                    R.string.clipboard_all_ok else R.string.clipboard_ok, items.size), Toast.LENGTH_SHORT).show()
            }
            R.id.shareItems -> shareText(this, text, if ((adapter.selectedItems == 0 || adapter.selectedItems == items.size) && items.size > 1)
                getString(R.string.share_all, items.size) else null)
            R.id.deleteItems -> {
                if (adapter.selectedItems == 0) {
                    backup(items.size, 0)
                    adapter.notifyDataSetChanged()
                    items.clear()
                } else {
                    backup(adapter.selectedItems, adapter.selectedItems)
                    for ((i, listItem) in items.withIndex().reversed()) if (listItem.selected) {
                        adapter.notifyItemRemoved(i)
                        items.remove(listItem)
                    }
                }
                adapter.selectedItems = 0
                updateToolbar()
            }
            R.id.settingsListItem -> startActivity(Intent(this, SettingsActivity::class.java))
        }
        return true
    }

    override fun onResume() {
        super.onResume()
        MainActivity.getOutputSettingsAndFont(preferences)
        for (listItem in items) listItem.outputBuffer = ""
        adapter.notifyDataSetChanged()
        lastPlayedInterval = preferences.getInt("playDialog", PLAY_DIALOG_CLOSED)
        if (lastPlayedInterval >= 0) {
            MainActivity.playPhaseShift = preferences.getFloat("playPhaseShift", 0f)
            if (MainActivity.playPhaseShift.isFinite()) items[lastPlayedInterval].number.play(this, onlyRecreate = true)
        }
    }

    override fun onPause() {
        super.onPause()
        snackbar?.dismiss()
        val editor = preferences.edit()
        editor.putString("listSel${listWhatToken(mergeFormatsScreens = true)}", prefsMapping.mapIndexed { i, it ->
            if (it in 0 until items.size) { if (items[it].selected) '1' else '0' } else if (it == -1 && i < listSel.length) listSel[i] else '0'
        }.joinToString(""))
        editor.putString("listExpand${listWhatToken(mergeFormatsScreens = true)}", prefsMapping.mapIndexed { i, it ->
            if (it in 0 until items.size) { if (items[it].expanded) '1' else '0' } else if (it == -1 && i < listExpand.length) listExpand[i] else '0'
        }.joinToString(""))
        editor.putString("listDisplay", (DisplayMode.values()[outputRadioIds.indexOf(outputRadioGroup.checkedRadioButtonId)]).toString())
        if (listWhat == "H") putHistory(preferences, editor, items) else
            putListBase(preferences, base, system, listComplementSwitch.isChecked, listDmsSwitch.isChecked)
        if (adapter.clickedItem > -1 && listWhat.isNotEmpty()) {
            editor.putString("listInput", listWhat[0] + if (listWhat[0] in 'A'..'Z') adapter.clickedItem.toString() else
                (items[adapter.clickedItem].number.toPreferencesString() +
                    "/${items[adapter.clickedItem].egyptianMethod}/${listWhat.substringAfterLast('/')}"))
            val intent = Intent(this, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
            startActivity(intent)
        }
        editor.putInt("playDialog", if (MainActivity.playDialog?.isShowing == true) {
            editor.putFloat("playPhaseShift", MainActivity.playPhaseShift)
            lastPlayedInterval
        } else PLAY_DIALOG_CLOSED)
        MainActivity.playDialog?.cancel()
        MainActivity.playDialogTimer?.cancel()
        editor.apply()
    }

    fun delete(pos: Int) {
        backup(1, if (items[pos].selected) 1 else 0, pos)
        adapter.notifyItemRemoved(pos)
        if (items[pos].selected) adapter.selectedItems--
        items.remove(items[pos])
        updateToolbar()
    }

    private fun backup(count: Int, selectedCount: Int, pos: Int = -1) {
        val backupItems = mutableListOf<QNumberEntry>()
        backupItems.clear()
        backupItems.addAll(items)
        snackbar = Snackbar.make(coordinator, if (count > 1 && count == items.size) resources.getString(R.string.deleted_all, count) else
                resources.getQuantityString(R.plurals.deleted, count, count), Snackbar.LENGTH_INDEFINITE)
            .addCallback(object : BaseTransientBottomBar.BaseCallback<Snackbar>() {
                override fun onDismissed(transientBottomBar: Snackbar?, event: Int) {
                    if (items.size == 0 && event != DISMISS_EVENT_CONSECUTIVE) finish()
                    super.onDismissed(transientBottomBar, event)
                }
            })
            .setAction(R.string.undo) {
                items.clear()
                items.addAll(backupItems)
                adapter.selectedItems += selectedCount
                if (count == 1 && pos > -1) adapter.notifyItemInserted(pos) else adapter.notifyDataSetChanged()
                updateToolbar()
            }
        snackbar?.show()
    }

    fun updateToolbar() {
        supportActionBar?.setHomeAsUpIndicator(if (adapter.selectedItems == 0) 0 else R.drawable.ic_close)
        supportActionBar?.setHomeActionContentDescription(if (adapter.selectedItems == 0) 0 else R.string.clear_selection)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
            toolbar.title = if (adapter.selectedItems == 0) toolbar.contentDescription else "${adapter.selectedItems}/${items.size}"
        toolbar.menu?.findItem(R.id.copyItems)?.isEnabled = items.size > 0
        toolbar.menu?.findItem(R.id.shareItems)?.isEnabled = items.size > 0
        toolbar.menu?.findItem(R.id.deleteItems)?.isEnabled = items.size > 0
        toolbar.menu?.findItem(R.id.selectAllItems)?.isVisible = adapter.selectedItems in 1 until items.size
    }

    fun listWhatToken(mergeFormatsScreens: Boolean) = listWhat.firstOrNull().let { if (mergeFormatsScreens && it in 'a'..'z') "" else it.toString() }
}

fun putListBase(preferences: SharedPreferences, base: Int, system: NumSystem, complement: Boolean, dms: Boolean) {
    val editor = preferences.edit()
    editor.putInt("listBase", base)
    editor.putString("listSystem", system.toString())
    editor.putBoolean("listComplement", complement)
    editor.putBoolean("listDMS", dms)
    editor.apply()
}