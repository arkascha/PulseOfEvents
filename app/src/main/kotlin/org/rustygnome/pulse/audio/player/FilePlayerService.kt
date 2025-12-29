package org.rustygnome.pulse.audio.player

import android.content.Intent
import android.util.Log
import kotlinx.coroutines.*
import org.json.JSONArray
import org.rustygnome.pulse.pulses.PulseManager
import java.io.File

class FilePlayerService : AbstractPlayerService() {

    private var job: Job? = null

    override val tag: String = "FilePlayerService"
    override val actionStopped: String = ACTION_PLAYER_STOPPED

    companion object {
        const val ACTION_PLAYER_STOPPED = "org.rustygnome.pulse.ACTION_PLAYER_STOPPED"
    }

    override fun onStartPlayback(intent: Intent) {
        val eventFileUri = intent.getStringExtra("event_file_uri")
        val timestampProperty = intent.getStringExtra("timestamp_property")
        val pulseId = intent.getStringExtra("pulse_id")

        if (eventFileUri != null) {
            job = CoroutineScope(Dispatchers.IO).launch {
                try {
                    val eventsJson = loadEvents(pulseId, eventFileUri)
                    if (eventsJson != null) {
                        playEvents(eventsJson, timestampProperty)
                    }
                } catch (e: Exception) {
                    Log.e(tag, "Error playing file events", e)
                } finally {
                    withContext(Dispatchers.Main) { stopSelf() }
                }
            }
        } else {
            Log.w(tag, "Missing file URI.")
            stopSelf()
        }
    }

    private fun loadEvents(pulseId: String?, fileName: String): JSONArray? {
        if (pulseId == null) return null
        val pulseManager = PulseManager(this)
        val internalFile = File(pulseManager.getPulseDir(pulseId), fileName)
        return if (internalFile.exists()) {
            try {
                JSONArray(internalFile.readText())
            } catch (e: Exception) {
                Log.e(tag, "Error parsing JSON from $fileName", e)
                null
            }
        } else {
            Log.e(tag, "Internal pulse file not found: ${internalFile.absolutePath}")
            null
        }
    }

    private suspend fun playEvents(events: JSONArray, timestampProperty: String?) {
        var lastTimestamp = -1L
        
        for (i in 0 until events.length()) {
            if (!isRunning) break
            val event = events.getJSONObject(i)
            
            if (timestampProperty != null && event.has(timestampProperty)) {
                val currentTimestamp = event.getLong(timestampProperty)
                if (lastTimestamp != -1L) {
                    val delayMs = currentTimestamp - lastTimestamp
                    if (delayMs > 0) delay(delayMs)
                }
                lastTimestamp = currentTimestamp
            } else {
                delay(1000)
            }

            val params = scriptEvaluator?.evaluate(event.toString())
            if (params?.sample != null) {
                synthesizer.play(params.sample, params.pitch.toFloat(), params.volume.toFloat())
            }
        }
    }

    override fun onDestroy() {
        isRunning = false
        job?.cancel()
        super.onDestroy()
    }
}
