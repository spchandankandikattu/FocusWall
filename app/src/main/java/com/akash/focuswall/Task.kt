package com.akash.focuswall

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

data class Task(
    val id: Long,
    val title: String,
    val priority: Int,          // 0 = High, 1 = Medium, 2 = Low
    val dueEpochDay: Long?,     // null = no due date
    val done: Boolean,
    val createdAt: Long,
    val doneAt: Long?
)

object Priority {
    const val HIGH = 0
    const val MEDIUM = 1
    const val LOW = 2
    val labels = arrayOf("High", "Medium", "Low")
    val colors = intArrayOf(0xFFFF5C6C.toInt(), 0xFFFFB547.toInt(), 0xFF4FD1A5.toInt())
}

data class WallSettings(
    val theme: Int = 0,          // 0 Aurora, 1 Particles, 2 Calm
    val position: Int = 0,       // 0 Top, 1 Middle, 2 Bottom
    val maxTasks: Int = 5,
    val batterySaver: Boolean = false
)

enum class Urgency { OVERDUE, TODAY, LATER }

class DueInfo(val text: String, val urgency: Urgency)

/** All tasks and settings live in one SharedPreferences file, shared by the app and the wallpaper. */
object TaskStore {
    private const val PREFS = "focuswall"
    private const val KEY_TASKS = "tasks"
    const val KEY_THEME = "theme"
    const val KEY_POSITION = "position"
    const val KEY_MAX = "max_tasks"
    const val KEY_SAVER = "battery_saver"

    fun prefs(ctx: Context): SharedPreferences =
        ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(ctx: Context): List<Task> {
        val raw = prefs(ctx).getString(KEY_TASKS, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Task(
                    id = o.getLong("id"),
                    title = o.optString("title"),
                    priority = o.optInt("priority", Priority.MEDIUM).coerceIn(0, 2),
                    dueEpochDay = if (o.has("due") && !o.isNull("due")) o.getLong("due") else null,
                    done = o.optBoolean("done", false),
                    createdAt = o.optLong("created", 0L),
                    doneAt = if (o.has("doneAt") && !o.isNull("doneAt")) o.getLong("doneAt") else null
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun save(ctx: Context, tasks: List<Task>) {
        val arr = JSONArray()
        for (t in tasks) {
            val o = JSONObject()
            o.put("id", t.id)
            o.put("title", t.title)
            o.put("priority", t.priority)
            if (t.dueEpochDay != null) o.put("due", t.dueEpochDay)
            o.put("done", t.done)
            o.put("created", t.createdAt)
            if (t.doneAt != null) o.put("doneAt", t.doneAt)
            arr.put(o)
        }
        prefs(ctx).edit().putString(KEY_TASKS, arr.toString()).apply()
    }

    fun add(ctx: Context, title: String, priority: Int, due: Long?) {
        val tasks = load(ctx)
        val now = System.currentTimeMillis()
        val ids = tasks.map { it.id }.toHashSet()
        var id = now
        while (id in ids) id++
        save(ctx, tasks + Task(id, title.trim(), priority, due, false, now, null))
    }

    fun update(ctx: Context, id: Long, title: String, priority: Int, due: Long?) =
        save(ctx, load(ctx).map {
            if (it.id == id) it.copy(title = title.trim(), priority = priority, dueEpochDay = due) else it
        })

    fun setDone(ctx: Context, id: Long, done: Boolean) =
        save(ctx, load(ctx).map {
            if (it.id == id && it.done != done)
                it.copy(done = done, doneAt = if (done) System.currentTimeMillis() else null)
            else it
        })

    fun delete(ctx: Context, id: Long) = save(ctx, load(ctx).filter { it.id != id })

    fun clearDone(ctx: Context) = save(ctx, load(ctx).filter { !it.done })

    /** Open tasks in wallpaper order: priority first, then nearest due date, then oldest. */
    fun forWallpaper(tasks: List<Task>): List<Task> =
        tasks.filter { !it.done }.sortedWith(
            compareBy<Task>({ it.priority }, { it.dueEpochDay ?: Long.MAX_VALUE }, { it.createdAt })
        )

    fun doneTodayCount(tasks: List<Task>, today: LocalDate): Int {
        val zone = ZoneId.systemDefault()
        return tasks.count { t ->
            val at = t.doneAt
            t.done && at != null && Instant.ofEpochMilli(at).atZone(zone).toLocalDate() == today
        }
    }

    fun dueInfo(due: Long?, todayEpoch: Long): DueInfo? {
        if (due == null) return null
        val d = due - todayEpoch
        val date = LocalDate.ofEpochDay(due)
        return when {
            d < 0 -> DueInfo("Overdue · ${-d} day" + (if (-d > 1) "s" else ""), Urgency.OVERDUE)
            d == 0L -> DueInfo("Due today", Urgency.TODAY)
            d == 1L -> DueInfo("Due tomorrow", Urgency.LATER)
            d < 7 -> DueInfo("Due " + date.format(DateTimeFormatter.ofPattern("EEEE", Locale.getDefault())), Urgency.LATER)
            else -> DueInfo("Due " + date.format(DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())), Urgency.LATER)
        }
    }

    fun settings(ctx: Context): WallSettings {
        val p = prefs(ctx)
        return WallSettings(
            theme = p.getInt(KEY_THEME, 0),
            position = p.getInt(KEY_POSITION, 0),
            maxTasks = p.getInt(KEY_MAX, 5),
            batterySaver = p.getBoolean(KEY_SAVER, false)
        )
    }

    fun putInt(ctx: Context, key: String, value: Int) = prefs(ctx).edit().putInt(key, value).apply()
    fun putBool(ctx: Context, key: String, value: Boolean) = prefs(ctx).edit().putBoolean(key, value).apply()
}
