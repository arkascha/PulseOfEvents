package org.rustygnome.pulse.audio.player

import android.app.Service
import android.content.Intent
import android.net.Uri
import android.os.IBinder
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import org.rustygnome.pulse.audio.Synthesizer
import org.rustygnome.pulse.data.FileFormat
import org.rustygnome.pulse.pulses.ScriptEvaluator
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

class FilePlayerService : Service(), PlayerService {

    private lateinit var synthesizer: Synthesizer
    private var scriptEvaluator: ScriptEvaluator? = null
    private var isPlaying = false
    private var playerThread: Thread? = null
    private var resourceId: Long = -1L

    companion object {
        private const val TAG = "FilePlayerService"
        const val ACTION_PLAYER_STOPPED = "org.rustygnome.pulse.ACTION_PLAYER_STOPPED"
    }

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "onCreate: Service creating.")
        synthesizer = Synthesizer(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(TAG, "onStartCommand: Service starting.")
        
        resourceId = intent?.getLongExtra("resource_id", -1L) ?: -1L
        val eventFileUri = intent?.getStringExtra("event_file_uri")
        val fileFormat = intent?.getStringExtra("file_format")
        val eventSounds = intent?.getStringArrayListExtra("event_sounds")
        val acousticStyle = intent?.getStringExtra("acoustic_style")
        val timestampProperty = intent?.getStringExtra("timestamp_property")
        
        val pulseId = intent?.getStringExtra("pulse_id")
        val scriptContent = intent?.getStringExtra("script_content")

        if (eventFileUri != null && fileFormat != null && eventSounds != null && acousticStyle != null) {
            if (!isPlaying) {
                isPlaying = true
                synthesizer.loadStyle(acousticStyle, eventSounds, pulseId)
                
                if (scriptContent != null) {
                    scriptEvaluator = ScriptEvaluator(scriptContent)
                }
                
                playerThread = thread {
                    playEvents(eventFileUri, fileFormat, pulseId, timestampProperty)
                }
            }
        } else {
            Log.w(TAG, "onStartCommand: Missing intent extras, stopping service.")
            stopSelf()
        }

        return START_NOT_STICKY
    }

    private fun sendStoppedBroadcast() {
        val intent = Intent(ACTION_PLAYER_STOPPED).apply {
            putExtra("resource_id", resourceId)
            setPackage(packageName)
        }
        sendBroadcast(intent)
    }

    private fun playEvents(uriString: String, format: String, pulseId: String?, timestampProperty: String?) {
        try {
            val content = when {
                uriString.startsWith("content://") -> {
                    val uri = Uri.parse(uriString)
                    contentResolver.openInputStream(uri)?.use { inputStream ->
                        BufferedReader(InputStreamReader(inputStream)).readText()
                    }
                }
                pulseId != null -> {
                    val internalFile = File(File(filesDir, "pulses/$pulseId"), uriString)
                    if (internalFile.exists()) {
                        internalFile.readText()
                    } else {
                        Log.e(TAG, "Internal pulse file not found: ${internalFile.absolutePath}")
                        null
                    }
                }
                else -> {
                    val resourceId = resources.getIdentifier(uriString, "raw", packageName)
                    if (resourceId != 0) {
                        resources.openRawResource(resourceId).use { inputStream ->
                            BufferedReader(InputStreamReader(inputStream)).readText()
                        }
                    } else null
                }
            }

            if (content == null) {
                Log.e(TAG, "Could not read content from $uriString")
                stopSelf()
                return
            }

            if (format == FileFormat.JSON.name) {
                playJson(content, timestampProperty)
            } else if (format == FileFormat.CSV.name) {
                playCsv(content)
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error playing events", e)
        } finally {
            Log.i(TAG, "Finished playing events.")
            isPlaying = false
            sendStoppedBroadcast()
            stopSelf()
        }
    }

    private fun playJson(content: String, timestampProperty: String?) {
        val jsonArray = JSONArray(content)
        var lastTimestamp: Long? = null

        for (i in 0 until jsonArray.length()) {
            if (!isPlaying) break
            val jsonObject = jsonArray.getJSONObject(i)
            
            if (timestampProperty != null && jsonObject.has(timestampProperty)) {
                val currentTimestamp = jsonObject.getLong(timestampProperty)
                if (lastTimestamp != null) {
                    val diffMicros = currentTimestamp - lastTimestamp
                    if (diffMicros > 0) {
                        val diffMillis = TimeUnit.MICROSECONDS.toMillis(diffMicros)
                        if (diffMillis > 0) {
                            Thread.sleep(diffMillis)
                        }
                    }
                }
                lastTimestamp = currentTimestamp
            } else {
                if (i > 0) Thread.sleep(1000)
            }

            processEvent(jsonObject.toString())
        }
    }

    private fun playCsv(content: String) {
        val lines = content.split("\n")
        for (line in lines) {
            if (!isPlaying || line.isBlank()) continue
            val parts = line.split(",")
            if (parts.size >= 2) {
                val message = JSONObject().apply {
                    put("event_type", parts[0].trim())
                    put("amount", parts[1].trim().toDoubleOrNull() ?: 1.0)
                }.toString()
                processEvent(message)
                Thread.sleep(1000)
            }
        }
    }

    private fun processEvent(message: String) {
        try {
            if (scriptEvaluator != null) {
                val params = scriptEvaluator!!.evaluate(message)
                if (params.sample != null) {
                    synthesizer.play(
                        params.sample,
                        params.pitch.toFloat(),
                        params.volume.toFloat()
                    )
                }
            } else {
                val jsonObject = JSONObject(message)
                val eventType = jsonObject.optString("event_type", "default")
                val amount = jsonObject.optDouble("amount", 1.0).toFloat()
                val rate = 0.5f + (amount / 100.0f) * 1.5f
                synthesizer.play(eventType, rate.coerceIn(0.5f, 2.0f))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing event: $message", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.i(TAG, "onDestroy: Service destroying.")
        if (isPlaying) {
            isPlaying = false
            try {
                playerThread?.join()
            } catch (e: InterruptedException) {
                Log.w(TAG, "Interrupted while waiting for player thread to finish.")
            }
        }
        scriptEvaluator?.release()
        synthesizer.release()
        sendStoppedBroadcast()
    }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }
}
