package org.rustygnome.pulse.audio.player

import android.content.Intent
import android.util.Log
import okhttp3.*
import org.rustygnome.pulse.data.SecurityHelper

class WebSocketPlayerService : AbstractPlayerService() {

    private lateinit var securityHelper: SecurityHelper
    private var webSocket: WebSocket? = null
    private val client = OkHttpClient()

    override val tag: String = "WebSocketPlayerService"
    override val actionStopped: String = ACTION_PLAYER_STOPPED

    companion object {
        const val ACTION_PLAYER_STOPPED = "org.rustygnome.pulse.ACTION_PLAYER_STOPPED"
    }

    override fun onCreate() {
        super.onCreate()
        securityHelper = SecurityHelper(this)
    }

    override fun onStartPlayback(intent: Intent) {
        var url = intent.getStringExtra("ws_url")
        var payload = intent.getStringExtra("ws_payload")
        val configContent = intent.getStringExtra("config_content")

        if (url != null) {
            val placeholders = findPlaceholders(configContent ?: "")
            if (placeholders.isNotEmpty()) {
                val credentials = securityHelper.getCredentials(resourceId, placeholders)
                url = resolvePlaceholders(url!!, credentials)
                payload = payload?.let { resolvePlaceholders(it, credentials) }
            }
            connectWebSocket(url!!, payload)
        } else {
            Log.w(tag, "Missing WebSocket URL.")
            stopSelf()
        }
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
        val request = Request.Builder().url(url).build()
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                payload?.let { webSocket.send(it) }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                val params = scriptEvaluator?.evaluate(text)
                if (params?.sample != null) {
                    synthesizer.play(params.sample, params.pitch.toFloat(), params.volume.toFloat())
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(tag, "WebSocket Failure: ${t.message}")
                stopSelf()
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(1000, null)
                stopSelf()
            }
        })
    }

    override fun onDestroy() {
        isRunning = false
        webSocket?.close(1000, "Service destroyed")
        super.onDestroy()
    }
}
