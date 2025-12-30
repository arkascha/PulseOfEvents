package org.rustygnome.pulse.audio.player

import android.content.Intent
import android.util.Log
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.config.SaslConfigs
import org.apache.kafka.common.errors.WakeupException
import org.apache.kafka.common.serialization.StringDeserializer
import org.json.JSONObject
import org.rustygnome.pulse.data.SecurityHelper
import java.time.Duration
import java.util.Properties

class KafkaPlayerService : AbstractPlayerService() {

    private var kafkaConsumer: KafkaConsumer<String, String>? = null
    private lateinit var securityHelper: SecurityHelper
    private var consumerThread: Thread? = null

    override val tag: String = "KafkaPlayerService"
    override val actionStopped: String = ACTION_PLAYER_STOPPED

    companion object {
        const val ACTION_PLAYER_ERROR = "org.rustygnome.pulse.ACTION_PLAYER_ERROR"
        const val ACTION_PLAYER_STOPPED = "org.rustygnome.pulse.ACTION_PLAYER_STOPPED"
        const val EXTRA_ERROR_MESSAGE = "extra_error_message"
    }

    override fun onCreate() {
        super.onCreate()
        securityHelper = SecurityHelper(this)
    }

    override fun onStartPlayback(intent: Intent) {
        val bootstrapServers = intent.getStringExtra("bootstrap_servers")
        val topic = intent.getStringExtra("topic")
        val encryptedKey = intent.getStringExtra("api_key")
        val encryptedSecret = intent.getStringExtra("api_secret")
        val apiKey = securityHelper.decrypt(encryptedKey)
        val apiSecret = securityHelper.decrypt(encryptedSecret)

        if (bootstrapServers != null && topic != null) {
            consumerThread = Thread {
                try {
                    setupKafkaConsumer(bootstrapServers, apiKey, apiSecret)
                    consumeEvents(topic)
                } catch (e: Exception) {
                    val errorMsg = "Failed to initialize Kafka consumer: ${e.localizedMessage}"
                    Log.e(tag, errorMsg, e)
                    sendErrorBroadcast(errorMsg)
                    stopSelf()
                }
            }
            consumerThread?.start()

        } else {
            Log.w(tag, "Missing Kafka specific intent extras.")
            stopSelf()
        }
    }

    private fun sendErrorBroadcast(message: String) {
        val intent = Intent(ACTION_PLAYER_ERROR).apply {
            putExtra("resource_id", resourceId)
            putExtra(EXTRA_ERROR_MESSAGE, message)
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
        props["metrics.reporters"] = ""
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
            kafkaConsumer?.subscribe(listOf(topic))
            while (isRunning) {
                val records = kafkaConsumer?.poll(Duration.ofMillis(100)) ?: continue
                for (record in records) {
                    val message = record.value()
                    try {
                        if (scriptEvaluator != null) {
                            val params = scriptEvaluator!!.evaluate(message)
                            if (params.sample != null) {
                                synthesizer.play(params.sample, params.pitch.toFloat(), params.volume.toFloat())
                            }
                        } else {
                            val jsonObject = JSONObject(message)
                            val eventType = jsonObject.getString("event_type")
                            val amount = jsonObject.optDouble("amount", 1.0).toFloat()
                            val rate = 0.5f + (amount / 100.0f) * 1.5f
                            synthesizer.play(eventType, rate.coerceIn(0.5f, 2.0f))
                        }
                    } catch (e: Exception) {
                        Log.e(tag, "Error processing record", e)
                    }
                }
            }
        } catch (e: WakeupException) {
            // expected
        } catch (e: Exception) {
            sendErrorBroadcast(e.localizedMessage ?: "Consumer error")
        } finally {
            sendStoppedBroadcast()
        }
    }

    override fun onDestroy() {
        isRunning = false
        kafkaConsumer?.wakeup()
        try { consumerThread?.join() } catch (e: Exception) {}
        kafkaConsumer?.close()
        super.onDestroy()
    }
}
