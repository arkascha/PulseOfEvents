package org.rustygnome.pulse.ui.visualizer

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import org.rustygnome.pulse.ui.main.PulseVisualizerView
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.hypot
import kotlin.random.Random

class VisualizerRenderer {

    private val ripples = CopyOnWriteArrayList<ActiveRipple>()
    private val particles = CopyOnWriteArrayList<Particle>()
    private val nodes = CopyOnWriteArrayList<Node>()
    
    private val paint = Paint().apply { isAntiAlias = true }
    private val textPaint = Paint().apply {
        isAntiAlias = true
        color = Color.GRAY
        alpha = 70
        textSize = 24f
        textAlign = Paint.Align.RIGHT
        typeface = Typeface.create("serif", Typeface.ITALIC)
    }
    private val namePaint = Paint().apply {
        isAntiAlias = true
        color = Color.GRAY
        alpha = 70
        textSize = 22f
        textAlign = Paint.Align.LEFT
        typeface = Typeface.create("serif", Typeface.ITALIC)
    }

    private data class ActiveRipple(val x: Float, val y: Float, val startTime: Long, val color: Int, val initialRadius: Float, val duration: Long = 800L)
    private data class Particle(var x: Float, var y: Float, var vx: Float, var vy: Float, val startTime: Long, val color: Int, val size: Float, val duration: Long = 1200L)
    private data class Node(var x: Float, var y: Float, var vx: Float, var vy: Float, val startTime: Long, val color: Int, val size: Float, val duration: Long = 3000L)

    fun clear() {
        ripples.clear()
        particles.clear()
        nodes.clear()
    }

    fun addPulse(width: Int, height: Int, mode: PulseVisualizerView.Mode, volume: Float, pitch: Float) {
        val now = System.currentTimeMillis()
        val hue = ((pitch - 0.5f) / 1.5f * 300f) % 360f
        val color = Color.HSVToColor(floatArrayOf(hue, 0.8f, 1.0f))

        when (mode) {
            PulseVisualizerView.Mode.RIPPLE -> {
                ripples.add(ActiveRipple((Math.random() * width).toFloat(), (Math.random() * height).toFloat(), now, color, 20f + (volume * 100f)))
            }
            PulseVisualizerView.Mode.PARTICLES -> {
                val bx = (Math.random() * width).toFloat()
                val by = (Math.random() * height).toFloat()
                repeat((10 + (volume * 20)).toInt()) {
                    val angle = Random.nextDouble(0.0, Math.PI * 2)
                    val speed = Random.nextDouble(2.0, 8.0).toFloat()
                    particles.add(Particle(bx, by, (Math.cos(angle) * speed).toFloat(), (Math.sin(angle) * speed).toFloat(), now, color, 4f + Random.nextFloat() * 10f))
                }
            }
            PulseVisualizerView.Mode.PROCESSING -> {
                repeat((1 + (volume * 3)).toInt()) {
                    nodes.add(Node((Math.random() * width).toFloat(), (Math.random() * height).toFloat(), (Random.nextFloat() - 0.5f) * 4f, (Random.nextFloat() - 0.5f) * 4f, now, color, 8f + (volume * 12f)))
                }
            }
            else -> {}
        }
    }

    fun render(canvas: Canvas, width: Int, height: Int, mode: PulseVisualizerView.Mode, pulseName: String?) {
        val now = System.currentTimeMillis()
        when (mode) {
            PulseVisualizerView.Mode.RIPPLE -> drawRipples(canvas, now)
            PulseVisualizerView.Mode.PARTICLES -> drawParticles(canvas, now)
            PulseVisualizerView.Mode.PROCESSING -> drawGenerativeNetwork(canvas, width, height, now)
            else -> {}
        }
        if (mode != PulseVisualizerView.Mode.NONE) {
            canvas.drawText("Pulse of Events", width - 15f, 25f, textPaint)
            pulseName?.let { canvas.drawText(it, 15f, height - 10f, namePaint) }
        }
    }

    fun hasActiveElements(): Boolean = ripples.isNotEmpty() || particles.isNotEmpty() || nodes.isNotEmpty()

    private fun drawRipples(canvas: Canvas, now: Long) {
        paint.style = Paint.Style.STROKE
        ripples.forEach { ripple ->
            val elapsed = now - ripple.startTime
            if (elapsed > ripple.duration) ripples.remove(ripple)
            else {
                val progress = elapsed.toFloat() / ripple.duration
                paint.color = ripple.color
                paint.alpha = (255 * (1f - progress)).toInt()
                paint.strokeWidth = 8f * (1f - progress)
                canvas.drawCircle(ripple.x, ripple.y, ripple.initialRadius + (progress * 300f), paint)
            }
        }
    }

    private fun drawParticles(canvas: Canvas, now: Long) {
        paint.style = Paint.Style.FILL
        particles.forEach { p ->
            val elapsed = now - p.startTime
            if (elapsed > p.duration) particles.remove(p)
            else {
                val progress = elapsed.toFloat() / p.duration
                p.x += p.vx; p.y += p.vy; p.vy += 0.05f
                paint.color = p.color
                paint.alpha = (255 * (1f - progress)).toInt()
                canvas.drawCircle(p.x, p.y, p.size * (1f - progress), paint)
            }
        }
    }

    private fun drawGenerativeNetwork(canvas: Canvas, width: Int, height: Int, now: Long) {
        val activeNodes = mutableListOf<Node>()
        nodes.forEach { node ->
            val elapsed = now - node.startTime
            if (elapsed > node.duration) nodes.remove(node)
            else {
                val progress = elapsed.toFloat() / node.duration
                node.x += node.vx; node.y += node.vy
                if (node.x < 0 || node.x > width) node.vx *= -1
                if (node.y < 0 || node.y > height) node.vy *= -1
                activeNodes.add(node)
                paint.style = Paint.Style.FILL
                paint.color = node.color
                paint.alpha = (150 * (1f - progress)).toInt()
                canvas.drawCircle(node.x, node.y, node.size * (1f - progress), paint)
            }
        }
        paint.style = Paint.Style.STROKE
        val connectionDistance = 300f
        for (i in activeNodes.indices) {
            for (j in i + 1 until activeNodes.size) {
                val n1 = activeNodes[i]; val n2 = activeNodes[j]
                val dist = hypot((n1.x - n2.x).toDouble(), (n1.y - n2.y).toDouble()).toFloat()
                if (dist < connectionDistance) {
                    val p1 = 1f - (now - n1.startTime).toFloat() / n1.duration
                    val p2 = 1f - (now - n2.startTime).toFloat() / n2.duration
                    paint.color = n1.color
                    paint.alpha = (100 * (1f - dist / connectionDistance) * (p1 + p2) / 2f).toInt()
                    paint.strokeWidth = 2f
                    canvas.drawLine(n1.x, n1.y, n2.x, n2.y, paint)
                }
            }
        }
    }
}
