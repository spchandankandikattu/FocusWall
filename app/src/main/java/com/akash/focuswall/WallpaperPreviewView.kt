package com.akash.focuswall

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.Build
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import kotlin.math.max
import kotlin.math.min

/** A miniature, live-animated copy of the phone screen showing exactly what the wallpaper draws. */
class WallpaperPreviewView(context: Context) : View(context) {

    val renderer = WallpaperRenderer(resources.displayMetrics.density)
    var onTaskTapped: ((Long) -> Unit)? = null
    var previewHeightPx = 0

    private val screenW: Int
    private val screenH: Int
    private val clip = Path()
    private val frameRect = RectF()
    private val framePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val startMs = SystemClock.uptimeMillis()
    private var running = false
    private val tick = object : Runnable {
        override fun run() {
            if (!running) return
            invalidate()
            postDelayed(this, renderer.frameDelayMs())
        }
    }

    init {
        var w = resources.displayMetrics.widthPixels
        var h = resources.displayMetrics.heightPixels
        if (Build.VERSION.SDK_INT >= 30) {
            val wm = context.getSystemService(WindowManager::class.java)
            if (wm != null) {
                val b = wm.maximumWindowMetrics.bounds
                w = b.width()
                h = b.height()
            }
        }
        screenW = min(w, h)
        screenH = max(w, h)
        renderer.setSize(screenW, screenH)
        framePaint.style = Paint.Style.STROKE
        framePaint.strokeWidth = 2f * resources.displayMetrics.density
        framePaint.color = 0x33FFFFFF
    }

    fun start() {
        if (running) return
        running = true
        post(tick)
    }

    fun stop() {
        running = false
        removeCallbacks(tick)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val h = if (previewHeightPx > 0) previewHeightPx else MeasureSpec.getSize(heightMeasureSpec)
        val w = (h * screenW.toFloat() / screenH).toInt()
        setMeasuredDimension(w, h)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val r = 22f * resources.displayMetrics.density
        clip.reset()
        clip.addRoundRect(0f, 0f, w.toFloat(), h.toFloat(), r, r, Path.Direction.CW)
        val inset = framePaint.strokeWidth / 2f
        frameRect.set(inset, inset, w - inset, h - inset)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width == 0) return
        val scale = width.toFloat() / screenW
        val now = SystemClock.uptimeMillis()
        canvas.save()
        canvas.clipPath(clip)
        canvas.scale(scale, scale)
        renderer.draw(canvas, (now - startMs) / 1000f, now)
        canvas.restore()
        val r = 22f * resources.displayMetrics.density
        canvas.drawRoundRect(frameRect, r, r, framePaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> return true
            MotionEvent.ACTION_UP -> {
                val scale = width.toFloat() / screenW
                val id = renderer.hitTest(event.x / scale, event.y / scale)
                if (id != null) {
                    renderer.markPending(id, SystemClock.uptimeMillis())
                    postDelayed({ onTaskTapped?.invoke(id) }, 650)
                    invalidate()
                }
                performClick()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean = super.performClick()

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stop()
    }
}
