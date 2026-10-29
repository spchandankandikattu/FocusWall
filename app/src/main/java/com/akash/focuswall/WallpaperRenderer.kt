package com.akash.focuswall

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.TextPaint
import android.text.TextUtils
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.Random
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Draws the animated background and the task card.
 * Used by both the live wallpaper and the in-app preview, so the preview is exactly what the phone shows.
 */
class WallpaperRenderer(private val density: Float) {

    var xOffset = 0.5f   // home-screen page position, used for a subtle parallax

    private var width = 0
    private var height = 0

    private var settings = WallSettings()
    private var allTasks: List<Task> = emptyList()
    private var shown: List<Task> = emptyList()
    private var hiddenCount = 0
    private var doneToday = 0
    private var cachedDay = Long.MIN_VALUE
    private var dateLabel = ""

    private val pending = HashMap<Long, Long>()          // task id -> tap time, for the tick animation
    private val rectPool = ArrayList<RectF>()
    private val hitRects = ArrayList<RectF>()
    private val hitIds = ArrayList<Long>()
    private val ellipsisCache = HashMap<String, String>()

    // ---- background ----
    private val bgPaint = Paint()
    private val blobPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val blobColors = intArrayOf(0x7C5CFF, 0x00C2FF, 0xFF4D9D, 0x2EE6A6)
    private val blobShaders = arrayOfNulls<Shader>(4)
    private var blobRadius = 0f
    private var auroraBase: Shader? = null
    private var particleBase: Shader? = null

    private val particleCount = 70
    private val px = FloatArray(particleCount)
    private val py = FloatArray(particleCount)
    private val pSpeed = FloatArray(particleCount)
    private val pSize = FloatArray(particleCount)
    private val pPhase = FloatArray(particleCount)
    private val xs = FloatArray(particleCount)
    private val ys = FloatArray(particleCount)
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val hsv = FloatArray(3)

    // ---- task card ----
    private val cardRect = RectF()
    private val cardFill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val cardStroke = Paint(Paint.ANTI_ALIAS_FLAG)
    private val headerPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)
    private val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG)
    private val metaPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val checkPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val strikePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val dividerPaint = Paint()
    private val checkPath = Path()

    init {
        val rnd = Random(7)
        for (i in 0 until particleCount) {
            px[i] = rnd.nextFloat()
            py[i] = rnd.nextFloat()
            pSpeed[i] = 0.008f + rnd.nextFloat() * 0.02f
            pSize[i] = dp(1.2f + rnd.nextFloat() * 2.2f)
            pPhase[i] = rnd.nextFloat() * 6.283f
        }
        linePaint.strokeWidth = dp(1f)

        cardFill.color = 0x66000000
        cardStroke.style = Paint.Style.STROKE
        cardStroke.strokeWidth = dp(1f)
        cardStroke.color = 0x33FFFFFF

        headerPaint.textSize = dp(11.5f)
        headerPaint.letterSpacing = 0.16f
        headerPaint.typeface = Typeface.DEFAULT_BOLD
        headerPaint.color = 0xB3FFFFFF.toInt()

        titlePaint.textSize = dp(16.5f)
        titlePaint.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        titlePaint.color = Color.WHITE
        titlePaint.setShadowLayer(dp(3f), 0f, dp(1f), 0x55000000)

        metaPaint.textSize = dp(12.5f)

        ringPaint.style = Paint.Style.STROKE
        ringPaint.strokeWidth = dp(2f)

        checkPaint.style = Paint.Style.STROKE
        checkPaint.strokeWidth = dp(2.2f)
        checkPaint.strokeCap = Paint.Cap.ROUND
        checkPaint.strokeJoin = Paint.Join.ROUND
        checkPaint.color = Color.WHITE

        strikePaint.color = 0xCCFFFFFF.toInt()
        strikePaint.strokeWidth = dp(1.6f)

        dividerPaint.color = 0x17FFFFFF
        dividerPaint.strokeWidth = dp(1f)
    }

    fun setSize(w: Int, h: Int) {
        if (w <= 0 || h <= 0 || (w == width && h == height)) return
        width = w
        height = h
        blobRadius = max(w, h) * 0.6f
        for (i in blobColors.indices) {
            val c = blobColors[i]
            blobShaders[i] = RadialGradient(
                0f, 0f, blobRadius,
                intArrayOf(withAlpha(c, 0x8C), withAlpha(c, 0x33), withAlpha(c, 0)),
                floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP
            )
        }
        auroraBase = LinearGradient(0f, 0f, 0f, h.toFloat(), 0xFF0B1026.toInt(), 0xFF1A0B2E.toInt(), Shader.TileMode.CLAMP)
        particleBase = LinearGradient(0f, 0f, 0f, h.toFloat(), 0xFF04060D.toInt(), 0xFF0D1B2E.toInt(), Shader.TileMode.CLAMP)
        ellipsisCache.clear()
    }

    fun update(tasks: List<Task>, s: WallSettings) {
        allTasks = tasks
        settings = s
        val active = TaskStore.forWallpaper(tasks)
        shown = active.take(s.maxTasks.coerceIn(1, 10))
        hiddenCount = active.size - shown.size
        pending.keys.retainAll(active.map { it.id }.toHashSet())
        cachedDay = Long.MIN_VALUE   // forces "done today" + date label to recompute
        if (ellipsisCache.size > 200) ellipsisCache.clear()
    }

    fun draw(canvas: Canvas, t: Float, nowMs: Long) {
        if (width == 0 || height == 0) return
        refreshDay()
        when (settings.theme) {
            1 -> drawParticles(canvas, t)
            2 -> drawCalm(canvas, t)
            else -> drawAurora(canvas, t)
        }
        drawCard(canvas, nowMs)
    }

    /** Returns the id of the task row at (x, y), if any. */
    fun hitTest(x: Float, y: Float): Long? {
        for (i in hitRects.indices) if (hitRects[i].contains(x, y)) return hitIds[i]
        return null
    }

    fun markPending(id: Long, nowMs: Long) {
        if (!pending.containsKey(id)) pending[id] = nowMs
    }

    fun frameDelayMs(): Long {
        val base = when (settings.theme) {
            2 -> if (settings.batterySaver) 200L else 100L
            else -> if (settings.batterySaver) 66L else 33L
        }
        return if (pending.isNotEmpty()) minOf(base, 33L) else base
    }

    // ------------------------------------------------------------------ backgrounds

    private fun drawAurora(canvas: Canvas, t: Float) {
        bgPaint.shader = auroraBase
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)
        val parallax = (xOffset - 0.5f) * width * 0.25f
        for (i in 0 until 4) {
            val sp = 0.10f + i * 0.035f
            val cx = width * (0.5f + 0.42f * sin(t * sp + i * 1.7f)) - parallax * (0.4f + i * 0.2f)
            val cy = height * (0.5f + 0.40f * cos(t * sp * 0.8f + i * 2.3f))
            blobPaint.shader = blobShaders[i]
            canvas.save()
            canvas.translate(cx, cy)
            canvas.drawCircle(0f, 0f, blobRadius, blobPaint)
            canvas.restore()
        }
    }

    private fun drawParticles(canvas: Canvas, t: Float) {
        val w = width.toFloat()
        val h = height.toFloat()
        bgPaint.shader = particleBase
        canvas.drawRect(0f, 0f, w, h, bgPaint)

        blobPaint.shader = blobShaders[1]
        blobPaint.alpha = 90
        canvas.save()
        canvas.translate(w * (0.5f + 0.3f * sin(t * 0.05f)), h * 0.9f)
        canvas.drawCircle(0f, 0f, blobRadius, blobPaint)
        canvas.restore()
        blobPaint.alpha = 255

        val parallax = (xOffset - 0.5f) * w * 0.12f
        for (i in 0 until particleCount) {
            var y = (py[i] - t * pSpeed[i]) % 1f
            if (y < 0f) y += 1f
            ys[i] = y * h
            xs[i] = (px[i] + 0.015f * sin(t * 0.6f + pPhase[i])) * w - parallax * (pSize[i] / dp(3.4f))
        }
        val maxD = dp(110f)
        val maxD2 = maxD * maxD
        for (i in 0 until particleCount) {
            for (j in i + 1 until particleCount) {
                val dx = xs[i] - xs[j]
                val dy = ys[i] - ys[j]
                val d2 = dx * dx + dy * dy
                if (d2 < maxD2) {
                    val a = ((1f - sqrt(d2) / maxD) * 70f).toInt()
                    linePaint.color = Color.argb(a, 140, 190, 255)
                    canvas.drawLine(xs[i], ys[i], xs[j], ys[j], linePaint)
                }
            }
        }
        for (i in 0 until particleCount) {
            val twinkle = 0.45f + 0.55f * (0.5f + 0.5f * sin(t * 1.8f + pPhase[i]))
            dotPaint.color = Color.argb((twinkle * 255).toInt(), 220, 235, 255)
            canvas.drawCircle(xs[i], ys[i], pSize[i], dotPaint)
        }
    }

    private fun drawCalm(canvas: Canvas, t: Float) {
        val hue = (t * 2.5f) % 360f
        hsv[0] = hue; hsv[1] = 0.55f; hsv[2] = 0.26f
        val c1 = Color.HSVToColor(hsv)
        hsv[0] = (hue + 50f) % 360f; hsv[1] = 0.65f; hsv[2] = 0.09f
        val c2 = Color.HSVToColor(hsv)
        val sway = width * 0.35f * sin(t * 0.04f)
        bgPaint.shader = LinearGradient(
            width * 0.5f + sway, 0f, width * 0.5f - sway, height.toFloat(), c1, c2, Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)
    }

    // ------------------------------------------------------------------ task card

    private fun drawCard(canvas: Canvas, nowMs: Long) {
        hitRects.clear()
        hitIds.clear()

        val margin = dp(20f)
        val pad = dp(18f)
        val headerH = dp(16f)
        val gap = dp(14f)
        val rowH = dp(56f)
        val footerH = dp(30f)
        val n = shown.size
        val bodyH = if (n == 0) dp(58f) else n * rowH
        val hasFooter = hiddenCount > 0 || doneToday > 0
        val cardH = pad + headerH + gap + bodyH + (if (hasFooter) footerH else 0f) + pad * 0.7f
        val top = when (settings.position) {
            1 -> (height - cardH) / 2f
            2 -> height - cardH - height * 0.17f
            else -> height * 0.13f
        }
        val left = margin
        val right = width - margin
        val radius = dp(24f)
        cardRect.set(left, top, right, top + cardH)
        canvas.drawRoundRect(cardRect, radius, radius, cardFill)
        canvas.drawRoundRect(cardRect, radius, radius, cardStroke)

        // Header
        val headBase = top + pad + headerPaint.textSize
        headerPaint.textAlign = Paint.Align.LEFT
        canvas.drawText("TODAY'S FOCUS", left + pad, headBase, headerPaint)
        headerPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText(dateLabel, right - pad, headBase, headerPaint)
        headerPaint.textAlign = Paint.Align.LEFT

        var y = top + pad + headerH + gap
        val textX = left + pad + dp(22f) + dp(14f)
        val maxW = right - pad - textX

        if (n == 0) {
            titlePaint.alpha = 255
            canvas.drawText("All clear ✨", left + pad, y + dp(22f), titlePaint)
            metaPaint.color = 0x99FFFFFF.toInt()
            canvas.drawText("Add tasks in the FocusWall app", left + pad, y + dp(44f), metaPaint)
            y += bodyH
        }

        for ((index, task) in shown.withIndex()) {
            val rect = rectAt(index)
            rect.set(left, y, right, y + rowH)
            hitRects.add(rect)
            hitIds.add(task.id)

            val cy = y + rowH / 2f
            val cx = left + pad + dp(11f)
            val color = Priority.colors[task.priority]
            val start = pending[task.id]
            val p = if (start == null) 0f else ((nowMs - start) / 450f).coerceIn(0f, 1f)

            ringPaint.color = color
            canvas.drawCircle(cx, cy, dp(11f), ringPaint)
            if (p > 0f) {
                fillPaint.color = color
                fillPaint.alpha = (255 * p).toInt()
                canvas.drawCircle(cx, cy, dp(11f) * (0.5f + 0.5f * p), fillPaint)
                checkPath.reset()
                checkPath.moveTo(cx - dp(5f), cy + dp(0.5f))
                checkPath.lineTo(cx - dp(1.5f), cy + dp(4f))
                checkPath.lineTo(cx + dp(5.5f), cy - dp(4f))
                checkPaint.alpha = (255 * p).toInt()
                canvas.drawPath(checkPath, checkPaint)
            }

            val title = ellipsize(task.title, maxW)
            val due = TaskStore.dueInfo(task.dueEpochDay, cachedDay)
            titlePaint.alpha = (255 * (1f - 0.55f * p)).toInt()
            val titleBase = if (due != null) cy - dp(3f) else cy + titlePaint.textSize * 0.35f
            canvas.drawText(title, textX, titleBase, titlePaint)
            if (p > 0f) {
                val sy = titleBase - titlePaint.textSize * 0.32f
                canvas.drawLine(textX, sy, textX + titlePaint.measureText(title) * p, sy, strikePaint)
            }
            if (due != null) {
                metaPaint.color = when (due.urgency) {
                    Urgency.OVERDUE -> 0xFFFF7A86.toInt()
                    Urgency.TODAY -> 0xFFFFC266.toInt()
                    Urgency.LATER -> 0x99FFFFFF.toInt()
                }
                if (p > 0f) metaPaint.alpha = (metaPaint.alpha * (1f - 0.55f * p)).toInt()
                canvas.drawText(due.text, textX, cy + dp(15f), metaPaint)
            }
            if (index < n - 1) canvas.drawLine(textX, y + rowH, right - pad, y + rowH, dividerPaint)
            y += rowH
        }

        if (hasFooter) {
            val base = y + dp(20f)
            if (hiddenCount > 0) {
                metaPaint.color = 0x80FFFFFF.toInt()
                canvas.drawText("+$hiddenCount more", left + pad, base, metaPaint)
            }
            if (doneToday > 0) {
                metaPaint.color = 0xFF4FD1A5.toInt()
                metaPaint.textAlign = Paint.Align.RIGHT
                canvas.drawText("✓ $doneToday done today", right - pad, base, metaPaint)
                metaPaint.textAlign = Paint.Align.LEFT
            }
        }
    }

    // ------------------------------------------------------------------ helpers

    private fun refreshDay() {
        val today = LocalDate.now()
        val epoch = today.toEpochDay()
        if (epoch == cachedDay) return
        cachedDay = epoch
        dateLabel = today.format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault()))
            .uppercase(Locale.getDefault())
        doneToday = TaskStore.doneTodayCount(allTasks, today)
    }

    private fun rectAt(i: Int): RectF {
        while (rectPool.size <= i) rectPool.add(RectF())
        return rectPool[i]
    }

    private fun ellipsize(text: String, maxW: Float): String {
        val key = text + "\u0000" + maxW.toInt()
        return ellipsisCache.getOrPut(key) {
            TextUtils.ellipsize(text, titlePaint, maxW, TextUtils.TruncateAt.END).toString()
        }
    }

    private fun withAlpha(rgb: Int, a: Int) = (a shl 24) or (rgb and 0xFFFFFF)

    private fun dp(v: Float) = v * density
}
