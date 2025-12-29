package org.rustygnome.pulse.audio.player

import android.content.Intent
import android.util.Log
import org.json.JSONObject
import java.util.Random
import kotlin.concurrent.thread

class RhythmicPlayerService : AbstractPlayerService() {

    private var workerThread: Thread? = null
    private val random = Random()

    override val tag: String = "RhythmicChaosService"
    override val actionStopped: String = ACTION_PLAYER_STOPPED

    companion object {
        const val ACTION_PLAYER_STOPPED = "org.rustygnome.pulse.ACTION_PLAYER_STOPPED"
    }

    override fun onStartPlayback(intent: Intent) {
        workerThread = thread {
            val baseTempo = 500L // 120 BPM
            val startTime = System.currentTimeMillis()
            
            while (isRunning) {
                try {
                    val now = System.currentTimeMillis()
                    val currentGlobalBeat = (now - startTime) / baseTempo
                    var targetBeat = currentGlobalBeat
                    while (targetBeat % 4 != 0L && targetBeat % 4 != 2L) {
                        targetBeat++
                    }
                    val nextBurstStartTime = startTime + targetBeat * baseTempo
                    val sleepTime = nextBurstStartTime - System.currentTimeMillis()
                    if (sleepTime > 0) Thread.sleep(sleepTime)
                    if (!isRunning) break
                    playRhythmicBurst(baseTempo)
                } catch (e: InterruptedException) {
                    break
                } catch (e: Exception) {
                    Log.e(tag, "Error in rhythmic generator", e)
                }
            }
        }
    }

    private fun playRhythmicBurst(baseTempo: Long) {
        val patterns = listOf(
            Pair(4, 0.25), Pair(2, 1.0), Pair(3, 0.333), Pair(6, 0.166),
            Pair(8, 0.125), Pair(1, 2.0), Pair(1, 1.0), Pair(2, 0.5)
        )
        val pattern = patterns[random.nextInt(patterns.size)]
        val noteCount = pattern.first
        val noteBeats = pattern.second
        val noteDuration = (baseTempo * noteBeats).toLong()
        val pitchDirection = if (random.nextBoolean()) 1 else -1
        val pitchStep = 0.15 

        for (i in 0 until noteCount) {
            if (!isRunning) break
            val dummyEvent = JSONObject().apply { put("type", "rhythmic") }
            val params = scriptEvaluator?.evaluate(dummyEvent.toString())
            if (params?.sample != null) {
                val modulatedPitch = 1.0 + (i * pitchStep * pitchDirection)
                synthesizer.play(params.sample, modulatedPitch.coerceIn(0.5, 2.0).toFloat(), params.volume.toFloat())
            }
            Thread.sleep(noteDuration)
        }
    }

    override fun onDestroy() {
        isRunning = false
        workerThread?.interrupt()
        super.onDestroy()
    }
}
