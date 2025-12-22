package org.rustygnome.pulse.audio.player

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import org.json.JSONObject
import org.rustygnome.pulse.audio.Synthesizer
import org.rustygnome.pulse.plugins.ScriptEvaluator
import java.util.Random
import kotlin.concurrent.thread

class RhythmicChaosService : Service(), PlayerService {

    private lateinit var synthesizer: Synthesizer
    private var scriptEvaluator: ScriptEvaluator? = null
    private var isRunning = false
    private var workerThread: Thread? = null
    private var resourceId: Long = -1L
    private val random = Random()

    companion object {
        private const val TAG = "RhythmicChaosService"
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
        val pluginId = intent?.getStringExtra("plugin_id")

        if (scriptContent != null && eventSounds != null && acousticStyle != null) {
            if (!isRunning) {
                isRunning = true
                synthesizer.loadStyle(acousticStyle, eventSounds, pluginId)
                scriptEvaluator = ScriptEvaluator(scriptContent)
                
                workerThread = thread {
                    val baseTempo = 500L // 120 BPM (Quarter note = 500ms)
                    val startTime = System.currentTimeMillis()
                    
                    while (isRunning) {
                        try {
                            val now = System.currentTimeMillis()
                            // Calculate current beat in the global timeline
                            val currentGlobalBeat = (now - startTime) / baseTempo
                            
                            // Target the next allowed beat: 1st or 3rd beat of a 4/4 measure (mod 4 == 0 or 2)
                            var targetBeat = currentGlobalBeat
                            while (targetBeat % 4 != 0L && targetBeat % 4 != 2L) {
                                targetBeat++
                            }
                            
                            val nextBurstStartTime = startTime + targetBeat * baseTempo
                            val sleepTime = nextBurstStartTime - System.currentTimeMillis()
                            if (sleepTime > 0) {
                                Thread.sleep(sleepTime)
                            }
                            
                            // Double check if we are still running before starting a burst
                            if (!isRunning) break
                            
                            playRhythmicBurst(baseTempo)
                            
                            // The loop continues. It will calculate the next allowed targetBeat 
                            // based on the time it is now (after the burst). 
                            // This ensures gaps are "as short as possible".
                            
                        } catch (e: InterruptedException) {
                            break
                        } catch (e: Exception) {
                            Log.e(TAG, "Error in rhythmic generator", e)
                        }
                    }
                }
            }
        } else {
            stopSelf()
        }

        return START_STICKY
    }

    /**
     * Plays a rhythmic pattern. Each pattern is designed to occupy a whole number of beats 
     * or a specific fraction to keep the 4/4 grid integrity.
     */
    private fun playRhythmicBurst(baseTempo: Long) {
        // patterns: List<Pair<NumberOfNotes, BeatsPerNote>>
        val patterns = listOf(
            Pair(4, 0.25),  // Four quadruplets (1 beat total)
            Pair(2, 1.0),   // Two quarter notes (2 beats total)
            Pair(3, 0.333), // Triplets (approx 1 beat total)
            Pair(6, 0.166), // Hexlets (approx 1 beat total)
            Pair(8, 0.125), // Fast run (1 beat total)
            Pair(1, 2.0),   // One half note (2 beats total)
            Pair(1, 1.0),   // One quarter note (1 beat total)
            Pair(2, 0.5)    // Two eighth notes (1 beat total)
        )
        
        val pattern = patterns[random.nextInt(patterns.size)]
        val noteCount = pattern.first
        val noteBeats = pattern.second
        val noteDuration = (baseTempo * noteBeats).toLong()

        // Direction for pitch: 1 = up, -1 = down
        val pitchDirection = if (random.nextBoolean()) 1 else -1
        val pitchStep = 0.15 

        for (i in 0 until noteCount) {
            if (!isRunning) break
            
            val dummyEvent = JSONObject().apply {
                put("type", "rhythmic")
            }
            
            val params = scriptEvaluator?.evaluate(dummyEvent.toString())
            if (params?.sample != null) {
                // Ascending or descending pitch scale
                val modulatedPitch = 1.0 + (i * pitchStep * pitchDirection)
                synthesizer.play(
                    params.sample,
                    modulatedPitch.coerceIn(0.5, 2.0).toFloat(),
                    params.volume.toFloat()
                )
            }
            
            // Wait for the duration of the note before the next one
            Thread.sleep(noteDuration)
        }
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
