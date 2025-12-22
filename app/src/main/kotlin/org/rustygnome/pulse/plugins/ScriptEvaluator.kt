package org.rustygnome.pulse.plugins

import android.util.Log
import org.mozilla.javascript.Context
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.ScriptableObject

class ScriptEvaluator(private val script: String) {

    private val scope: Scriptable

    data class PlaybackParams(
        val sample: String? = null,
        val pitch: Double = 1.0,
        val volume: Double = 1.0,
        val duration: Long = 0
    )

    init {
        // Initialize the scope once. We need a context just for initialization.
        val rhino = Context.enter()
        try {
            rhino.optimizationLevel = -1
            scope = rhino.initStandardObjects()
        } finally {
            Context.exit()
        }
    }

    fun evaluate(eventJson: String): PlaybackParams {
        // Associate a Rhino context with the CURRENT thread (OkHttp, Kafka, etc.)
        val rhino = Context.enter()
        try {
            rhino.optimizationLevel = -1
            
            // Parse the JSON string into a JavaScript object directly in Rhino
            val eventObject = rhino.evaluateString(scope, "($eventJson)", "event_parsing", 1, null)
            scope.put("event", scope, eventObject)
            
            // Execute the plugin script using the persistent scope
            val result = rhino.evaluateString(scope, script, "plugin_script", 1, null)
            
            if (result is ScriptableObject) {
                return PlaybackParams(
                    sample = result.get("sample", result)?.takeIf { it != Scriptable.NOT_FOUND }?.toString(),
                    pitch = getDouble(result, "pitch", 1.0),
                    volume = getDouble(result, "volume", 1.0),
                    duration = getDouble(result, "duration", 0.0).toLong()
                )
            }
        } catch (e: Exception) {
            Log.e("ScriptEvaluator", "Script execution failed on thread ${Thread.currentThread().name}", e)
        } finally {
            // ALWAYS exit the context for the current thread
            Context.exit()
        }
        return PlaybackParams()
    }

    private fun getDouble(obj: ScriptableObject, key: String, default: Double): Double {
        val value = obj.get(key, obj)
        if (value == Scriptable.NOT_FOUND || value == null) return default
        return try {
            Context.toNumber(value)
        } catch (e: Exception) {
            default
        }
    }

    fun release() {
        // No persistent global context to exit anymore, as it's managed per-thread in evaluate()
    }
}
