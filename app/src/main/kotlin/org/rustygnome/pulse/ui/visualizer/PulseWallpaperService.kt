package org.rustygnome.pulse.ui.visualizer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Canvas
import android.graphics.Color
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder
import org.rustygnome.pulse.audio.player.PlayerService
import org.rustygnome.pulse.ui.main.PulseVisualizerView

class PulseWallpaperService : WallpaperService() {

    override fun onCreateEngine(): Engine = PulseEngine()

    inner class PulseEngine : Engine() {
        private val renderer = VisualizerRenderer()
        private val handler = Handler(Looper.getMainLooper())
        private var isVisible = false
        private var currentMode = PulseVisualizerView.Mode.RIPPLE
        
        private val drawRunnable = object : Runnable {
            override fun run() {
                draw()
            }
        }

        private val pulseReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == PlayerService.ACTION_PULSE_FIRE) {
                    val vol = intent.getFloatExtra(PlayerService.EXTRA_VOLUME, 1.0f)
                    val pitch = intent.getFloatExtra(PlayerService.EXTRA_PITCH, 1.0f)
                    
                    renderer.addPulse(surfaceHolder.surfaceFrame.width(), surfaceHolder.surfaceFrame.height(), currentMode, vol, pitch)
                    startAnimationLoop()
                }
            }
        }

        override fun onCreate(surfaceHolder: SurfaceHolder?) {
            super.onCreate(surfaceHolder)
            val filter = IntentFilter(PlayerService.ACTION_PULSE_FIRE)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(pulseReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                registerReceiver(pulseReceiver, filter)
            }
            updateMode()
        }

        override fun onDestroy() {
            super.onDestroy()
            handler.removeCallbacks(drawRunnable)
            unregisterReceiver(pulseReceiver)
        }

        override fun onVisibilityChanged(visible: Boolean) {
            isVisible = visible
            if (visible) {
                updateMode()
                startAnimationLoop()
            } else {
                handler.removeCallbacks(drawRunnable)
            }
        }

        override fun onSurfaceChanged(holder: SurfaceHolder?, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            draw()
        }

        private fun updateMode() {
            val prefs = getSharedPreferences("pulse_prefs", Context.MODE_PRIVATE)
            val modeStr = prefs.getString("visualizer_mode", "RIPPLE") ?: "RIPPLE"
            currentMode = try { PulseVisualizerView.Mode.valueOf(modeStr) } catch (e: Exception) { PulseVisualizerView.Mode.RIPPLE }
            if (currentMode == PulseVisualizerView.Mode.NONE) {
                renderer.clear()
            }
        }

        private fun startAnimationLoop() {
            handler.removeCallbacks(drawRunnable)
            if (isVisible && renderer.hasActiveElements()) {
                handler.post(drawRunnable)
            }
        }

        private fun draw() {
            val holder = surfaceHolder
            var canvas: Canvas? = null
            try {
                canvas = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    holder.lockHardwareCanvas()
                } else {
                    holder.lockCanvas()
                }
                if (canvas != null) {
                    canvas.drawColor(Color.BLACK) 
                    renderer.render(canvas, canvas.width, canvas.height, currentMode, null)
                }
            } finally {
                if (canvas != null) {
                    holder.unlockCanvasAndPost(canvas)
                }
            }

            if (isVisible && renderer.hasActiveElements()) {
                handler.postDelayed(drawRunnable, 16) 
            }
        }
    }
}
