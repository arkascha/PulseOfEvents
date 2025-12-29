package org.rustygnome.pulse.ui.main

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.View
import org.rustygnome.pulse.ui.visualizer.VisualizerRenderer

class PulseVisualizerView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    enum class Mode { NONE, RIPPLE, PARTICLES, PROCESSING }

    private var currentMode = Mode.RIPPLE
    private var currentPulseName: String? = null
    private val renderer = VisualizerRenderer()

    fun setMode(mode: Mode) {
        currentMode = mode
        renderer.clear()
        invalidate()
    }

    fun setPulseName(name: String?) {
        currentPulseName = name
        invalidate()
    }

    fun onPulse(volume: Float, pitch: Float) {
        if (currentMode == Mode.NONE) return
        renderer.addPulse(width, height, currentMode, volume, pitch)
        postInvalidateOnAnimation()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        renderer.render(canvas, width, height, currentMode, currentPulseName)
        if (renderer.hasActiveElements()) {
            postInvalidateOnAnimation()
        }
    }
}
