package org.rustygnome.pulse.audio.player

import android.content.Intent
import android.util.Log
import org.json.JSONObject
import java.util.Random
import kotlin.concurrent.thread

class RandomPlayerService : AbstractPlayerService() {

    private var workerThread: Thread? = null
    private val random = Random()

    override val tag: String = "RandomPlayerService"
    override val actionStopped: String = ACTION_PLAYER_STOPPED

    companion object {
        const val ACTION_PLAYER_STOPPED = "org.rustygnome.pulse.ACTION_PLAYER_STOPPED"
    }

    override fun onStartPlayback(intent: Intent) {
        workerThread = thread {
            while (isRunning) {
                try {
                    val dummyEvent = JSONObject().apply {
                        put("type", "random")
                        put("timestamp", System.currentTimeMillis())
                    }
                    
                    val params = scriptEvaluator?.evaluate(dummyEvent.toString())
                    if (params?.sample != null) {
                        synthesizer.play(params.sample, params.pitch.toFloat(), params.volume.toFloat())
                    }
                    
                    Thread.sleep(200L + random.nextInt(1800).toLong())
                } catch (e: InterruptedException) {
                    break
                } catch (e: Exception) {
                    Log.e(tag, "Error in random generator", e)
                }
            }
        }
    }

    override fun onDestroy() {
        isRunning = false
        workerThread?.interrupt()
        super.onDestroy()
    }
}
