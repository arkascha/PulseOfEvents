package org.rustygnome.pulse.audio.player

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import okhttp3.*
import org.rustygnome.pulse.audio.Synthesizer
import org.rustygnome.pulse.data.SecurityHelper
import org.rustygnome.pulse.plugins.ScriptEvaluator
import java.util.concurrent.TimeUnit

class WebSocketPlayerService : Service(), PlayerService {

    private lateinit var synthesizer: Synthesizer
    private lateinit var securityHelper: SecurityHelper
    private var scriptEvaluator: ScriptEvaluator? = null
    private var webSocket: WebSocket? = null
    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()
    private var resourceId: Long = -1L

    companion object {
        private const val TAG = "WebSocketService"
        const val ACTION_PLAYER_STOPPED = "org.rustygnome.pulse.ACTION_PLAYER_STOPPED"
    }

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "onCreate: WebSocket service created")
        synthesizer = Synthesizer(this)
        securityHelper = SecurityHelper(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        var url = intent?.getStringExtra("ws_url")
        var payload = intent?.getStringExtra("ws_payload")
        val scriptContent = intent?.getStringExtra("script_content")
        val eventSounds = intent?.getStringArrayListExtra("event_sounds")
        val acousticStyle = intent?.getStringExtra("acoustic_style")
        val pluginId = intent?.getStringExtra("plugin_id")
        resourceId = intent?.getLongExtra("resource_id", -1L) ?: -1L

        Log.i(TAG, "onStartCommand: Starting for URL $url")

        if (url != null && scriptContent != null && eventSounds != null && acousticStyle != null) {
            
            // Resolve placeholders in URL and Payload using stored credentials
            val placeholders = findPlaceholders(url + (payload ?: ""))
            if (placeholders.isNotEmpty()) {
                val credentials = securityHelper.getCredentials(resourceId, placeholders)
                url = resolvePlaceholders(url, credentials)
                payload = payload?.let { resolvePlaceholders(it, credentials) }
            }

            synthesizer.loadStyle(acousticStyle, eventSounds, pluginId)
            scriptEvaluator = ScriptEvaluator(scriptContent)
            connectWebSocket(url, payload)
        } else {
            Log.w(TAG, "onStartCommand: Missing required parameters.")
            stopSelf()
        }

        return START_STICKY
    }

    private fun findPlaceholders(text: String): Set<String> {
        val regex = Regex("\\\$\\{([^}]+)\\}")
        return regex.findAll(text).map { it.groupValues[1] }.toSet()
    }

    private fun resolvePlaceholders(text: String, credentials: Map<String, String>): String {
        var resolvedText = text
        credentials.forEach { (key, value) ->
            resolvedText = resolvedText.replace("\${$key}", value)
        }
        return resolvedText
    }

    private fun connectWebSocket(url: String, payload: String?) {
        Log.d(TAG, "Connecting to WebSocket: $url")
        val request = Request.Builder().url(url).build()
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.i(TAG, "WebSocket Opened")
                payload?.let {
                    Log.d(TAG, "Sending resolved subscription payload")
                    webSocket.send(it)
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val params = scriptEvaluator?.evaluate(text)
                    if (params?.sample != null) {
                        synthesizer.play(params.sample, params.pitch.toFloat(), params.volume.toFloat())
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error evaluating script", e)
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "WebSocket Failure: ${t.message}")
                stopSelf()
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                stopSelf()
            }
        })
    }

    override fun onDestroy() {
        super.onDestroy()
        webSocket?.close(1000, "Service destroyed")
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
