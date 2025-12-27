package org.rustygnome.pulse.ui.main

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.hypot
import kotlin.random.Random

class PulseVisualizerView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    enum class Mode {
        NONE, RIPPLE, PARTICLES, PROCESSING
    }

    private var currentMode = Mode.RIPPLE
    private var currentPulseName: String? = null
    private val appName = "Pulse of Events"

    private val ripples = CopyOnWriteArrayList<ActiveRipple>()
    private val particles = CopyOnWriteArrayList<Particle>()
    private val nodes = CopyOnWriteArrayList<Node>()
    
    private val paint = Paint().apply {
        isAntiAlias = true
    }

    private val scriptTypeface = Typeface.create("serif", Typeface.ITALIC)

    private val textPaint = Paint().apply {
        isAntiAlias = true
        color = Color.GRAY
        alpha = 70
        textSize = 24f
        textAlign = Paint.Align.RIGHT
        typeface = scriptTypeface
    }

    private val namePaint = Paint().apply {
        isAntiAlias = true
        color = Color.GRAY
        alpha = 70
        textSize = 22f
        textAlign = Paint.Align.LEFT
        typeface = scriptTypeface
    }

    private data class ActiveRipple(
        val x: Float,
        val y: Float,
        val startTime: Long,
        val color: Int,
        val initialRadius: Float,
        val duration: Long = 800L
    )

    private data class Particle(
        var x: Float,
        var y: Float,
        var vx: Float,
        var vy: Float,
        val startTime: Long,
        val color: Int,
        val size: Float,
        val duration: Long = 1200L
    )

    private data class Node(
        var x: Float,
        var y: Float,
        var vx: Float,
        var vy: Float,
        val startTime: Long,
        val color: Int,
        val size: Float,
        val duration: Long = 3000L
    )

    fun setMode(mode: Mode) {
        currentMode = mode
        ripples.clear()
        particles.clear()
        nodes.clear()
        invalidate()
    }

    fun setPulseName(name: String?) {
        currentPulseName = name
        invalidate()
    }

    fun onPulse(volume: Float, pitch: Float) {
        if (currentMode == Mode.NONE) return
        
        val now = System.currentTimeMillis()
        val hue = ((pitch - 0.5f) / 1.5f * 300f) % 360f
        val color = Color.HSVToColor(floatArrayOf(hue, 0.8f, 1.0f))

        when (currentMode) {
            Mode.RIPPLE -> {
                ripples.add(ActiveRipple(
                    x = (Math.random() * width).toFloat(),
                    y = (Math.random() * height).toFloat(),
                    startTime = now,
                    color = color,
                    initialRadius = 20f + (volume * 100f)
                ))
            }
            Mode.PARTICLES -> {
                val burstX = (Math.random() * width).toFloat()
                val burstY = (Math.random() * height).toFloat()
                val count = (10 + (volume * 20)).toInt()
                for (i in 0 until count) {
                    val angle = Random.nextDouble(0.0, Math.PI * 2)
                    val speed = Random.nextDouble(2.0, 8.0).toFloat()
                    particles.add(Particle(
                        x = burstX,
                        y = burstY,
                        vx = (Math.cos(angle) * speed).toFloat(),
                        vy = (Math.sin(angle) * speed).toFloat(),
                        startTime = now,
                        color = color,
                        size = 4f + Random.nextFloat() * 10f
                    ))
                }
            }
            Mode.PROCESSING -> {
                val count = (1 + (volume * 3)).toInt()
                for (i in 0 until count) {
                    nodes.add(Node(
                        x = (Math.random() * width).toFloat(),
                        y = (Math.random() * height).toFloat(),
                        vx = (Random.nextFloat() - 0.5f) * 4f,
                        vy = (Random.nextFloat() - 0.5f) * 4f,
                        startTime = now,
                        color = color,
                        size = 8f + (volume * 12f)
                    ))
                }
            }
            else -> {}
        }
        postInvalidateOnAnimation()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val now = System.currentTimeMillis()

        when (currentMode) {
            Mode.RIPPLE -> drawRipples(canvas, now)
            Mode.PARTICLES -> drawParticles(canvas, now)
            Mode.PROCESSING -> drawGenerativeNetwork(canvas, now)
            else -> {}
        }

        if (currentMode != Mode.NONE) {
            drawInscriptions(canvas)
        }

        if (ripples.isNotEmpty() || particles.isNotEmpty() || nodes.isNotEmpty()) {
            postInvalidateOnAnimation()
        }
    }

    private fun drawInscriptions(canvas: Canvas) {
        canvas.drawText(appName, width - 15f, 25f, textPaint)
        currentPulseName?.let {
            canvas.drawText(it, 15f, height - 10f, namePaint)
        }
    }

    private fun drawGenerativeNetwork(canvas: Canvas, now: Long) {
        val nodeIterator = nodes.iterator()
        val activeNodes = mutableListOf<Node>()
        
        while (nodeIterator.hasNext()) {
            val node = nodeIterator.next()
            val elapsed = now - node.startTime
            if (elapsed > node.duration) {
                nodes.remove(node)
                continue
            }
            
            val progress = elapsed.toFloat() / node.duration
            node.x += node.vx
            node.y += node.vy
            
            // Bounce off edges
            if (node.x < 0 || node.x > width) node.vx *= -1
            if (node.y < 0 || node.y > height) node.vy *= -1
            
            activeNodes.add(node)
            
            paint.style = Paint.Style.FILL
            paint.color = node.color
            paint.alpha = (150 * (1f - progress)).toInt()
            canvas.drawCircle(node.x, node.y, node.size * (1f - progress), paint)
        }
        
        // Draw connections
        paint.style = Paint.Style.STROKE
        val connectionDistance = 300f
        for (i in activeNodes.indices) {
            for (j in i + 1 until activeNodes.size) {
                val n1 = activeNodes[i]
                val n2 = activeNodes[j]
                val dist = hypot((n1.x - n2.x).toDouble(), (n1.y - n2.y).toDouble()).toFloat()
                
                if (dist < connectionDistance) {
                    val p1 = 1f - (now - n1.startTime).toFloat() / n1.duration
                    val p2 = 1f - (now - n2.startTime).toFloat() / n2.duration
                    val lineAlpha = (100 * (1f - dist / connectionDistance) * (p1 + p2) / 2f).toInt()
                    
                    paint.color = n1.color // Take color from first node
                    paint.alpha = lineAlpha
                    paint.strokeWidth = 2f
                    canvas.drawLine(n1.x, n1.y, n2.x, n2.y, paint)
                }
            }
        }
    }

    private fun drawRipples(canvas: Canvas, now: Long) {
        paint.style = Paint.Style.STROKE
        val iterator = ripples.iterator()
        while (iterator.hasNext()) {
            val ripple = iterator.next()
            val elapsed = now - ripple.startTime
            if (elapsed > ripple.duration) {
                ripples.remove(ripple)
                continue
            }
            val progress = elapsed.toFloat() / ripple.duration
            paint.color = ripple.color
            paint.alpha = (255 * (1f - progress)).toInt()
            paint.strokeWidth = 8f * (1f - progress)
            canvas.drawCircle(ripple.x, ripple.y, ripple.initialRadius + (progress * 300f), paint)
        }
    }

    private fun drawParticles(canvas: Canvas, now: Long) {
        paint.style = Paint.Style.FILL
        val iterator = particles.iterator()
        while (iterator.hasNext()) {
            val p = iterator.next()
            val elapsed = now - p.startTime
            if (elapsed > p.duration) {
                particles.remove(p)
                continue
            }
            val progress = elapsed.toFloat() / p.duration
            
            p.x += p.vx
            p.y += p.vy
            p.vy += 0.05f 

            paint.color = p.color
            paint.alpha = (255 * (1f - progress)).toInt()
            canvas.drawCircle(p.x, p.y, p.size * (1f - progress), paint)
        }
    }
}
