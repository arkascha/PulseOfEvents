package org.rustygnome.pulse.audio.player

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.config.SaslConfigs
import org.apache.kafka.common.errors.WakeupException
import org.apache.kafka.common.serialization.StringDeserializer
import org.json.JSONObject
import org.rustygnome.pulse.audio.Synthesizer
import org.rustygnome.pulse.data.SecurityHelper
import org.rustygnome.pulse.plugins.ScriptEvaluator
import java.time.Duration
import java.util.Properties

class KafkaPlayerService : Service(), PlayerService {

    private var kafkaConsumer: KafkaConsumer<String, String>? = null
    private lateinit var synthesizer: Synthesizer
    private lateinit var securityHelper: SecurityHelper
    private var scriptEvaluator: ScriptEvaluator? = null
    private var isRunning = false
    private var consumerThread: Thread? = null
    private var resourceId: Long = -1L

    companion object {
        private const val TAG = "KafkaPlayerService"
        const val ACTION_PLAYER_ERROR = "org.rustygnome.pulse.ACTION_PLAYER_ERROR"
        const val ACTION_PLAYER_STOPPED = "org.rustygnome.pulse.ACTION_PLAYER_STOPPED"
        const val EXTRA_ERROR_MESSAGE = "extra_error_message"
    }

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "onCreate: Service creating.")
        synthesizer = Synthesizer(this)
        securityHelper = SecurityHelper(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(TAG, "onStartCommand: Service starting.")
        
        resourceId = intent?.getLongExtra("resource_id", -1L) ?: -1L
        var bootstrapServers = intent?.getStringExtra("bootstrap_servers")
        var topic = intent?.getStringExtra("topic")
        
        // Decrypt credentials from DB
        val encryptedKey = intent?.getStringExtra("api_key")
        val encryptedSecret = intent?.getStringExtra("api_secret")
        var apiKey = securityHelper.decrypt(encryptedKey)
        var apiSecret = securityHelper.decrypt(encryptedSecret)
        
        val eventSounds = intent?.getStringArrayListExtra("event_sounds")
        val acousticStyle = intent?.getStringExtra("acoustic_style")
        
        val pluginId = intent?.getStringExtra("plugin_id")
        val scriptContent = intent?.getStringExtra("script_content")

        if (bootstrapServers != null && topic != null && eventSounds != null && acousticStyle != null) {
            
            // Resolve placeholders in Kafka settings (for other secrets if needed)
            val combinedConfig = "$bootstrapServers $topic ${apiKey ?: ""} ${apiSecret ?: ""}"
            val placeholders = findPlaceholders(combinedConfig)
            if (placeholders.isNotEmpty()) {
                val credentials = securityHelper.getCredentials(resourceId, placeholders)
                bootstrapServers = resolvePlaceholders(bootstrapServers, credentials)
                topic = resolvePlaceholders(topic, credentials)
                apiKey = apiKey?.let { resolvePlaceholders(it, credentials) } ?: apiKey
                apiSecret = apiSecret?.let { resolvePlaceholders(it, credentials) } ?: apiSecret
            }

            if (!isRunning) {
                isRunning = true
                synthesizer.loadStyle(acousticStyle, eventSounds, pluginId)
                
                if (scriptContent != null) {
                    scriptEvaluator = ScriptEvaluator(scriptContent)
                }
                
                try {
                    setupKafkaConsumer(bootstrapServers!!, apiKey, apiSecret)
                    consumerThread = Thread { consumeEvents(topic!!) }
                    consumerThread?.start()
                } catch (e: Exception) {
                    val errorMsg = "Failed to initialize Kafka consumer: ${e.localizedMessage}"
                    Log.e(TAG, errorMsg, e)
                    sendErrorBroadcast(errorMsg)
                    stopSelf()
                }
            }
        } else {
            Log.w(TAG, "onStartCommand: Missing intent extras, stopping service.")
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

    private fun sendErrorBroadcast(message: String) {
        val intent = Intent(ACTION_PLAYER_ERROR).apply {
            putExtra("resource_id", resourceId)
            putExtra(EXTRA_ERROR_MESSAGE, message)
            setPackage(packageName)
        }
        sendBroadcast(intent)
    }

    private fun sendStoppedBroadcast() {
        val intent = Intent(ACTION_PLAYER_STOPPED).apply {
            putExtra("resource_id", resourceId)
            setPackage(packageName)
        }
        sendBroadcast(intent)
    }

    private fun setupKafkaConsumer(bootstrapServers: String, apiKey: String?, apiSecret: String?) {
        val props = Properties()
        props[ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG] = bootstrapServers
        props[ConsumerConfig.GROUP_ID_CONFIG] = "pulse-android-app"
        props[ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG] = StringDeserializer::class.java.name
        props[ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG] = StringDeserializer::class.java.name

        // IMPORTANT: Disable JMX metrics which causes the ManagementFactory crash on Android
        props["metrics.reporters"] = ""
        
        // Try using the new consumer group protocol (Kafka 3.7+) which might avoid some legacy init paths
        props["group.protocol"] = "consumer"

        if (!apiKey.isNullOrEmpty() && !apiSecret.isNullOrEmpty()) {
            props["security.protocol"] = "SASL_SSL"
            props[SaslConfigs.SASL_MECHANISM] = "PLAIN"
            props[SaslConfigs.SASL_JAAS_CONFIG] = "org.apache.kafka.common.security.plain.PlainLoginModule required username=\"$apiKey\" password=\"$apiSecret\";"
        }

        kafkaConsumer = KafkaConsumer(props)
    }

    private fun consumeEvents(topic: String) {
        try {
            Log.d(TAG, "Subscribing to topic: $topic")
            kafkaConsumer?.subscribe(listOf(topic))
            while (isRunning) {
                val records = kafkaConsumer?.poll(Duration.ofMillis(100)) ?: continue
                for (record in records) {
                    val message = record.value()
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
                            val eventType = jsonObject.getString("event_type")
                            val amount = jsonObject.optDouble("amount", 1.0).toFloat()
                            val rate = 0.5f + (amount / 100.0f) * 1.5f
                            synthesizer.play(eventType, rate.coerceIn(0.5f, 2.0f))
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error processing record: $message", e)
                    }
                }
            }
        } catch (e: WakeupException) {
            Log.i(TAG, "Consumer thread woken up, shutting down.")
        } catch (e: Exception) {
            val errorMsg = "Kafka consumer thread error: ${e.localizedMessage}"
            Log.e(TAG, errorMsg, e)
            sendErrorBroadcast(errorMsg)
        } finally {
            sendStoppedBroadcast()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.i(TAG, "onDestroy: Service destroying.")
        if (isRunning) {
            isRunning = false
            kafkaConsumer?.wakeup()
            try {
                consumerThread?.join()
            } catch (e: InterruptedException) {
                Log.w(TAG, "Interrupted while waiting for consumer thread to finish.")
            }
        }
        kafkaConsumer?.close()
        scriptEvaluator?.release()
        synthesizer.release()
        sendStoppedBroadcast()
    }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }
}
