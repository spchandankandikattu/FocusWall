package com.akash.focuswall

import android.app.WallpaperManager
import android.content.SharedPreferences
import android.graphics.Canvas
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder

/** The live wallpaper itself. Redraws only while visible, so it costs nothing when the screen is off. */
class FocusWallpaperService : WallpaperService() {

    override fun onCreateEngine(): Engine = FocusEngine()

    inner class FocusEngine : Engine(), SharedPreferences.OnSharedPreferenceChangeListener {

        private val handler = Handler(Looper.getMainLooper())
        private val renderer = WallpaperRenderer(resources.displayMetrics.density)
        private val startMs = SystemClock.uptimeMillis()
        private val drawRunnable = Runnable { drawFrame() }
        private var visible = false
        private var useHardwareCanvas = true

        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)
            reload()
            // Strong reference: this engine is the listener, and it lives as long as the wallpaper does.
            TaskStore.prefs(this@FocusWallpaperService).registerOnSharedPreferenceChangeListener(this)
        }

        override fun onDestroy() {
            super.onDestroy()
            handler.removeCallbacks(drawRunnable)
            TaskStore.prefs(this@FocusWallpaperService).unregisterOnSharedPreferenceChangeListener(this)
        }

        override fun onSharedPreferenceChanged(prefs: SharedPreferences?, key: String?) {
            reload()
            if (visible) schedule(0)
        }

        private fun reload() {
            val ctx = this@FocusWallpaperService
            renderer.update(TaskStore.load(ctx), TaskStore.settings(ctx))
        }

        override fun onVisibilityChanged(isVisible: Boolean) {
            visible = isVisible
            if (isVisible) {
                reload()
                schedule(0)
            } else {
                handler.removeCallbacks(drawRunnable)
            }
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            renderer.setSize(width, height)
            schedule(0)
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            super.onSurfaceDestroyed(holder)
            visible = false
            handler.removeCallbacks(drawRunnable)
        }

        override fun onOffsetsChanged(
            xOffset: Float, yOffset: Float, xOffsetStep: Float, yOffsetStep: Float,
            xPixelOffset: Int, yPixelOffset: Int
        ) {
            renderer.xOffset = xOffset
        }

        /** Most launchers forward a tap on empty home-screen space to the wallpaper as COMMAND_TAP. */
        override fun onCommand(
            action: String?, x: Int, y: Int, z: Int, extras: Bundle?, resultRequested: Boolean
        ): Bundle? {
            if (action == WallpaperManager.COMMAND_TAP) {
                val id = renderer.hitTest(x.toFloat(), y.toFloat())
                if (id != null) {
                    renderer.markPending(id, SystemClock.uptimeMillis())
                    handler.postDelayed({ TaskStore.setDone(this@FocusWallpaperService, id, true) }, 650)
                    schedule(0)
                }
            }
            return super.onCommand(action, x, y, z, extras, resultRequested)
        }

        private fun schedule(delayMs: Long) {
            handler.removeCallbacks(drawRunnable)
            handler.postDelayed(drawRunnable, delayMs)
        }

        private fun drawFrame() {
            val holder = surfaceHolder
            val now = SystemClock.uptimeMillis()
            var canvas: Canvas? = null
            try {
                canvas = lockCanvas(holder)
                if (canvas != null) renderer.draw(canvas, (now - startMs) / 1000f, now)
            } catch (e: Exception) {
                // Surface can disappear mid-frame (e.g. screen off); just try again next frame.
            } finally {
                if (canvas != null) {
                    try {
                        holder.unlockCanvasAndPost(canvas)
                    } catch (e: Exception) {
                    }
                }
            }
            if (visible) {
                val spent = SystemClock.uptimeMillis() - now
                schedule((renderer.frameDelayMs() - spent).coerceAtLeast(0L))
            }
        }

        private fun lockCanvas(holder: SurfaceHolder): Canvas? {
            if (useHardwareCanvas) {
                try {
                    return holder.lockHardwareCanvas()
                } catch (e: Exception) {
                    useHardwareCanvas = false
                }
            }
            return holder.lockCanvas()
        }
    }
}
