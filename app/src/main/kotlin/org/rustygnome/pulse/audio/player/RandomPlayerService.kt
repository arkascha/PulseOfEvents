package org.rustygnome.pulse.audio.player

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import org.json.JSONObject
import org.rustygnome.pulse.audio.Synthesizer
import org.rustygnome.pulse.pulses.ScriptEvaluator
import java.util.Random
import kotlin.concurrent.thread

class RandomPlayerService : Service(), PlayerService {

    private lateinit var synthesizer: Synthesizer
    private var scriptEvaluator: ScriptEvaluator? = null
    private var isRunning = false
    private var workerThread: Thread? = null
    private var resourceId: Long = -1L
    private val random = Random()

    companion object {
        private const val TAG = "RandomPlayerService"
        const val ACTION_PLAYER_STOPPED = "org.rustygnome.pulse.ACTION_PLAYER_STOPPED"
    }

    override fun onCreate() {
        super.onCreate()
        synthesizer = Synthesizer(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        resourceId = intent?.getLongExtra("resource_id", -1L) ?: -1L
        val scriptContent = intent?.getStringExtra("script_content")
        val eventSounds = intent?.getStringArrayListExtra("event_sounds")
        val acousticStyle = intent?.getStringExtra("acoustic_style")
        val pulseId = intent?.getStringExtra("pulse_id")

        if (scriptContent != null && eventSounds != null && acousticStyle != null) {
            if (!isRunning) {
                isRunning = true
                synthesizer.loadStyle(acousticStyle, eventSounds, pulseId)
                scriptEvaluator = ScriptEvaluator(scriptContent)
                
                workerThread = thread {
                    while (isRunning) {
                        try {
                            // Generate a dummy event to trigger the mapping script
                            val dummyEvent = JSONObject().apply {
                                put("timestamp", System.currentTimeMillis())
                                put("entropy", random.nextDouble())
                            }
                            
                            val params = scriptEvaluator?.evaluate(dummyEvent.toString())
                            if (params?.sample != null) {
                                synthesizer.play(
                                    params.sample,
                                    params.pitch.toFloat(),
                                    params.volume.toFloat()
                                )
                            }
                            
                            // Random delay between 100ms and 1000ms
                            Thread.sleep(100L + random.nextInt(900).toLong())
                        } catch (e: InterruptedException) {
                            break
                        } catch (e: Exception) {
                            Log.e(TAG, "Error in random generator", e)
                        }
                    }
                }
            }
        } else {
            stopSelf()
        }

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        workerThread?.interrupt()
        scriptEvaluator?.release()
        synthesizer.release()
        sendStoppedBroadcast()
    }

    private fun sendStoppedBroadcast() {
        val intent = Intent(ACTION_PLAYER_STOPPED).apply {
            putExtra("resource_id", resourceId)
            setPackage(packageName)
        }
        sendBroadcast(intent)
    }

    override fun onBind(intent: Intent): IBinder? = null
}
