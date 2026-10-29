package com.akash.focuswall

import android.app.Activity
import android.app.AlertDialog
import android.app.DatePickerDialog
import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.inputmethod.EditorInfo
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

class MainActivity : Activity(), SharedPreferences.OnSharedPreferenceChangeListener {

    private val bg = 0xFF0B0F1A.toInt()
    private val surface = 0xFF151B2B.toInt()
    private val surface2 = 0xFF1F2738.toInt()
    private val textColor = 0xFFF2F4F8.toInt()
    private val muted = 0xFF8A93A6.toInt()
    private val accent = 0xFF7C5CFF.toInt()
    private val good = 0xFF4FD1A5.toInt()
    private val danger = 0xFFFF7A86.toInt()
    private val medium: Typeface by lazy { Typeface.create("sans-serif-medium", Typeface.NORMAL) }

    private lateinit var preview: WallpaperPreviewView
    private lateinit var listBox: LinearLayout
    private lateinit var countText: TextView
    private lateinit var statusText: TextView
    private lateinit var applyButton: TextView
    private lateinit var input: EditText
    private lateinit var newDuePicker: DuePicker
    private var newPriority = Priority.MEDIUM
    private var newDue: Long? = null
    private var refreshQueued = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 30) window.setDecorFitsSystemWindows(false)

        val scroll = ScrollView(this).apply {
            setBackgroundColor(bg)
            isFillViewport = true
            clipToPadding = false
        }
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(12), dp(20), dp(32))
        }
        scroll.addView(col, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        setContentView(scroll)
        if (Build.VERSION.SDK_INT >= 30) {
            scroll.setOnApplyWindowInsetsListener { v, insets ->
                val b = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.ime())
                v.setPadding(b.left, b.top, b.right, b.bottom)
                insets
            }
        }

        // ---- header + live preview ----
        col.addView(tv("FocusWall", 28f, textColor, bold = true), lp())
        statusText = tv("", 13f, muted)
        col.addView(statusText, lp(top = 2))

        preview = WallpaperPreviewView(this).apply {
            previewHeightPx = dp(380)
            onTaskTapped = { id -> TaskStore.setDone(this@MainActivity, id, true) }
        }
        col.addView(preview, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { gravity = Gravity.CENTER_HORIZONTAL; topMargin = dp(18) })
        col.addView(tv("Tap a task here or on your home screen to complete it", 12f, muted).apply {
            gravity = Gravity.CENTER
        }, lp(top = 10))

        applyButton = button("Set as live wallpaper", filled = true) { applyWallpaper() }
        col.addView(applyButton, lp(top = 16))

        // ---- add a task ----
        col.addView(sectionTitle("Add a task"), lp(top = 28))
        val addCard = card()
        input = EditText(this).apply {
            hint = "What needs to get done?"
            setHintTextColor(muted)
            setTextColor(textColor)
            textSize = 16f
            background = rounded(surface2, 12)
            setPadding(dp(14), dp(12), dp(14), dp(12))
            isSingleLine = true
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            imeOptions = EditorInfo.IME_ACTION_DONE
            setOnEditorActionListener { _, action, _ ->
                if (action == EditorInfo.IME_ACTION_DONE) { addTask(); true } else false
            }
        }
        addCard.addView(input, lp())
        addCard.addView(fieldLabel("Priority"), lp(top = 14))
        addCard.addView(Segmented(Priority.labels.toList(), newPriority) { newPriority = it }.row, lp(top = 6))
        addCard.addView(fieldLabel("Due"), lp(top = 14))
        newDuePicker = DuePicker(null) { newDue = it }
        addCard.addView(newDuePicker.seg.row, lp(top = 6))
        addCard.addView(button("Add task", filled = true) { addTask() }, lp(top = 16))
        col.addView(addCard, lp(top = 10))

        // ---- task list ----
        val listHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        listHeader.addView(sectionTitle("Your tasks"), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        countText = tv("", 12f, muted)
        listHeader.addView(countText)
        col.addView(listHeader, lp(top = 28))
        listBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(listBox, lp(top = 10))
        col.addView(button("Clear completed", filled = false) { TaskStore.clearDone(this) }, lp(top = 10))

        // ---- wallpaper style ----
        val s = TaskStore.settings(this)
        col.addView(sectionTitle("Wallpaper"), lp(top = 28))
        val styleCard = card()
        styleCard.addView(fieldLabel("Background"), lp())
        styleCard.addView(Segmented(listOf("Aurora", "Particles", "Calm"), s.theme) {
            TaskStore.putInt(this, TaskStore.KEY_THEME, it)
        }.row, lp(top = 6))
        styleCard.addView(fieldLabel("Card position"), lp(top = 14))
        styleCard.addView(Segmented(listOf("Top", "Middle", "Bottom"), s.position) {
            TaskStore.putInt(this, TaskStore.KEY_POSITION, it)
        }.row, lp(top = 6))
        styleCard.addView(fieldLabel("Tasks shown"), lp(top = 14))
        val counts = listOf(3, 5, 7)
        styleCard.addView(Segmented(counts.map { "$it" }, counts.indexOf(s.maxTasks).coerceAtLeast(0)) {
            TaskStore.putInt(this, TaskStore.KEY_MAX, counts[it])
        }.row, lp(top = 6))

        val saverRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val saverText = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        saverText.addView(tv("Battery saver", 15f, textColor))
        saverText.addView(tv("Slower animation, same tasks", 12f, muted))
        saverRow.addView(saverText, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val saverSwitch = Switch(this)
        saverSwitch.isChecked = s.batterySaver
        saverSwitch.setOnCheckedChangeListener { _, checked -> TaskStore.putBool(this, TaskStore.KEY_SAVER, checked) }
        saverRow.addView(saverSwitch)
        styleCard.addView(saverRow, lp(top = 18))
        col.addView(styleCard, lp(top = 10))

        col.addView(tv(
            "Tips: the animation pauses whenever the wallpaper is hidden, so battery cost is small. " +
                "If the wallpaper ever resets to a default after a reboot, open this app and tap \"Re-apply\", " +
                "and allow FocusWall to run in the background in your phone's battery settings.",
            12f, muted
        ), lp(top = 20))
    }

    override fun onResume() {
        super.onResume()
        TaskStore.prefs(this).registerOnSharedPreferenceChangeListener(this)
        refreshAll()
        preview.start()
    }

    override fun onPause() {
        super.onPause()
        TaskStore.prefs(this).unregisterOnSharedPreferenceChangeListener(this)
        preview.stop()
    }

    override fun onSharedPreferenceChanged(prefs: SharedPreferences?, key: String?) {
        if (refreshQueued) return
        refreshQueued = true
        listBox.post { refreshAll() }
    }

    // ------------------------------------------------------------------ actions

    private fun addTask() {
        val title = input.text.toString().trim()
        if (title.isEmpty()) {
            input.error = "Type a task first"
            return
        }
        TaskStore.add(this, title, newPriority, newDue)
        input.setText("")
        newDue = null
        newDuePicker.reset()
    }

    private fun applyWallpaper() {
        val component = ComponentName(this, FocusWallpaperService::class.java)
        try {
            startActivity(
                Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER)
                    .putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, component)
            )
            return
        } catch (e: Exception) {
        }
        try {
            startActivity(Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER))
            Toast.makeText(this, "Choose FocusWall from the list", Toast.LENGTH_LONG).show()
            return
        } catch (e: Exception) {
        }
        AlertDialog.Builder(this)
            .setTitle("Set it manually")
            .setMessage("Open Settings › Wallpaper (or the Themes app) › Live wallpapers, and choose FocusWall.")
            .setPositiveButton("OK", null)
            .show()
    }

    private fun editTask(t: Task) {
        var priority = t.priority
        var due = t.dueEpochDay
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), dp(0))
        }
        val et = EditText(this).apply {
            setText(t.title)
            setTextColor(textColor)
            textSize = 16f
            background = rounded(surface2, 12)
            setPadding(dp(14), dp(12), dp(14), dp(12))
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        }
        box.addView(et, lp())
        box.addView(fieldLabel("Priority"), lp(top = 14))
        box.addView(Segmented(Priority.labels.toList(), priority) { priority = it }.row, lp(top = 6))
        box.addView(fieldLabel("Due"), lp(top = 14))
        box.addView(DuePicker(due) { due = it }.seg.row, lp(top = 6))

        AlertDialog.Builder(this)
            .setTitle("Edit task")
            .setView(box)
            .setPositiveButton("Save") { _, _ ->
                val title = et.text.toString().trim()
                if (title.isNotEmpty()) TaskStore.update(this, t.id, title, priority, due)
            }
            .setNeutralButton("Delete") { _, _ -> TaskStore.delete(this, t.id) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ------------------------------------------------------------------ rendering the list

    private fun refreshAll() {
        refreshQueued = false
        val tasks = TaskStore.load(this)
        val s = TaskStore.settings(this)
        preview.renderer.update(tasks, s)
        preview.invalidate()

        val active = TaskStore.forWallpaper(tasks)
        val done = tasks.filter { it.done }.sortedByDescending { it.doneAt ?: 0L }
        countText.text = "${active.size} open · ${done.size} done"

        listBox.removeAllViews()
        if (tasks.isEmpty()) {
            listBox.addView(tv("No tasks yet. Add your first one above.", 14f, muted), lp())
        }
        val today = LocalDate.now().toEpochDay()
        active.forEachIndexed { i, t ->
            listBox.addView(taskRow(t, today, onWall = i < s.maxTasks), lp(top = if (i == 0) 0 else 8))
        }
        done.forEachIndexed { i, t ->
            listBox.addView(taskRow(t, today, onWall = false), lp(top = if (i == 0 && active.isEmpty()) 0 else 8))
        }
        updateStatus()
    }

    private fun taskRow(t: Task, today: Long, onWall: Boolean): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = rounded(surface, 14)
            setPadding(dp(6), dp(6), dp(4), dp(6))
        }
        val check = CheckBox(this)
        check.isChecked = t.done
        check.buttonTintList = ColorStateList.valueOf(Priority.colors[t.priority])
        check.setOnCheckedChangeListener { _, checked -> TaskStore.setDone(this, t.id, checked) }
        row.addView(check)

        val textCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(4), dp(4), dp(4), dp(4))
            setOnClickListener { editTask(t) }
        }
        val title = tv(t.title, 15f, if (t.done) muted else textColor).apply {
            if (t.done) paintFlags = paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
        }
        val due = TaskStore.dueInfo(t.dueEpochDay, today)
        val parts = mutableListOf(Priority.labels[t.priority])
        if (due != null && !t.done) parts.add(due.text)
        if (onWall) parts.add("on wallpaper")
        val overdue = due != null && due.urgency == Urgency.OVERDUE && !t.done
        textCol.addView(title)
        textCol.addView(tv(parts.joinToString("  ·  "), 12f, if (overdue) danger else muted), lp(top = 2))
        row.addView(textCol, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        val delete = tv("✕", 16f, muted).apply {
            setPadding(dp(12), dp(8), dp(12), dp(8))
            setOnClickListener { TaskStore.delete(this@MainActivity, t.id) }
        }
        row.addView(delete)
        return row
    }

    private fun updateStatus() {
        val active = try {
            WallpaperManager.getInstance(this).wallpaperInfo?.packageName == packageName
        } catch (e: Exception) {
            false
        }
        statusText.text = if (active) "● Live on your wallpaper" else "○ Not set as your wallpaper yet"
        statusText.setTextColor(if (active) good else muted)
        applyButton.text = if (active) "Re-apply live wallpaper" else "Set as live wallpaper"
    }

    // ------------------------------------------------------------------ small UI building blocks

    /** A pill-style single-choice control. */
    private inner class Segmented(options: List<String>, initial: Int, private val onSelect: (Int) -> Unit) {
        val row = LinearLayout(this@MainActivity)
        val views = ArrayList<TextView>()
        private var selected = initial

        init {
            row.orientation = LinearLayout.HORIZONTAL
            row.background = rounded(surface2, 12)
            row.setPadding(dp(4), dp(4), dp(4), dp(4))
            options.forEachIndexed { i, label ->
                val v = TextView(this@MainActivity)
                v.text = label
                v.gravity = Gravity.CENTER
                v.textSize = 13f
                v.typeface = medium
                v.isSingleLine = true
                v.setPadding(dp(4), dp(9), dp(4), dp(9))
                v.setOnClickListener { select(i) }
                row.addView(v, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                views.add(v)
            }
            restyle()
        }

        fun select(i: Int, notify: Boolean = true) {
            selected = i
            restyle()
            if (notify) onSelect(i)
        }

        private fun restyle() {
            views.forEachIndexed { i, v ->
                v.background = if (i == selected) rounded(accent, 10) else null
                v.setTextColor(if (i == selected) Color.WHITE else muted)
            }
        }
    }

    /** None / Today / Tomorrow / Pick a date. */
    private inner class DuePicker(initial: Long?, private val onChange: (Long?) -> Unit) {
        private val today = LocalDate.now().toEpochDay()
        private var current = when (initial) {
            null -> 0
            today -> 1
            today + 1 -> 2
            else -> 3
        }
        val seg = Segmented(
            listOf("None", "Today", "Tomorrow", if (current == 3 && initial != null) shortDate(initial) else "Pick…"),
            current
        ) { i -> choose(i) }

        private fun choose(i: Int) {
            when (i) {
                0 -> { current = 0; onChange(null) }
                1 -> { current = 1; onChange(today) }
                2 -> { current = 2; onChange(today + 1) }
                else -> pickDate()
            }
        }

        private fun pickDate() {
            val start = LocalDate.now().plusDays(2)
            val dialog = DatePickerDialog(this@MainActivity, { _, y, m, d ->
                val epoch = LocalDate.of(y, m + 1, d).toEpochDay()
                seg.views[3].text = shortDate(epoch)
                current = 3
                onChange(epoch)
            }, start.year, start.monthValue - 1, start.dayOfMonth)
            dialog.datePicker.minDate = System.currentTimeMillis() - 1000
            dialog.setOnCancelListener { seg.select(current, notify = false) }
            dialog.show()
        }

        fun reset() {
            current = 0
            seg.views[3].text = "Pick…"
            seg.select(0, notify = false)
        }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun lp(top: Int = 0) = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
    ).apply { topMargin = dp(top) }

    private fun rounded(color: Int, radiusDp: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radiusDp).toFloat()
    }

    private fun tv(s: String, sizeSp: Float, color: Int, bold: Boolean = false): TextView {
        val v = TextView(this)
        v.text = s
        v.textSize = sizeSp
        v.setTextColor(color)
        if (bold) v.typeface = Typeface.DEFAULT_BOLD
        return v
    }

    private fun sectionTitle(s: String) = tv(s.uppercase(Locale.getDefault()), 12f, muted, bold = true).apply {
        letterSpacing = 0.12f
    }

    private fun fieldLabel(s: String) = tv(s, 13f, muted)

    private fun card() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = rounded(surface, 18)
        setPadding(dp(16), dp(16), dp(16), dp(16))
    }

    private fun button(label: String, filled: Boolean, onClick: () -> Unit): TextView {
        val v = TextView(this)
        v.text = label
        v.gravity = Gravity.CENTER
        v.textSize = 15f
        v.typeface = medium
        v.setTextColor(if (filled) Color.WHITE else textColor)
        v.setPadding(dp(16), dp(14), dp(16), dp(14))
        v.background = RippleDrawable(ColorStateList.valueOf(0x33FFFFFF), rounded(if (filled) accent else surface2, 14), null)
        v.isClickable = true
        v.isFocusable = true
        v.setOnClickListener { onClick() }
        return v
    }

    private fun shortDate(epoch: Long): String =
        LocalDate.ofEpochDay(epoch).format(DateTimeFormatter.ofPattern("d MMM", Locale.getDefault()))
}
