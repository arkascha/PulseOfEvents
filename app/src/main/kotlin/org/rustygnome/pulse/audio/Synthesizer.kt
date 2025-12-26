package org.rustygnome.pulse.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.util.Log
import org.rustygnome.pulse.pulses.PulseManager
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class Synthesizer(private val context: Context) {

    private var soundPool: SoundPool? = null
    private val soundIdMap = mutableMapOf<String, Int>()
    
    // Single background thread for the lifecycle of the loaded style
    private var playbackExecutor: ExecutorService? = null

    companion object {
        private const val TAG = "Synthesizer"
    }

    fun loadStyle(style: String, eventSounds: List<String>, pulseId: String? = null) {
        release()
        Log.i(TAG, "Loading style: $style for event sounds: $eventSounds (Pulse: $pulseId)")

        // Initialize executor for the duration of this style
        playbackExecutor = Executors.newSingleThreadExecutor()

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        soundPool = SoundPool.Builder()
            .setMaxStreams(10)
            .setAudioAttributes(audioAttributes)
            .build()

        val pulseManager = PulseManager(context)
        val pulseSoundsDir = pulseId?.let { pulseManager.getSoundsDir(it) }

        // Folders now directly contain samples, no subfolders like 'stereo'
        val folderName = when (style) {
            "99Sounds Percussion I", "99Sounds Drum Samples I" -> "99Sounds/Drum Samples I"
            "99Sounds Percussion II", "99Sounds Drum Samples II" -> "99Sounds/Drum Samples II"
            "orchestra_tada" -> "Orchestra/tada"
            "orchestra_violin" -> "Orchestra/violin"
            else -> style
        }
        
        Log.d(TAG, "Resolved style folder: $folderName")

        val allAssets = try {
            context.assets.list("sounds/$folderName") ?: emptyArray()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to list assets for sounds/$folderName", e)
            emptyArray<String>()
        }

        for (soundName in eventSounds) {
            var loaded = false

            // 1. Try loading from Pulse folder
            if (pulseSoundsDir != null && pulseSoundsDir.exists()) {
                val pulseFile = findFileFuzzy(pulseSoundsDir, soundName)
                if (pulseFile != null) {
                    try {
                        soundIdMap[soundName] = soundPool!!.load(pulseFile.absolutePath, 1)
                        Log.d(TAG, "Loaded '$soundName' from pulse: ${pulseFile.absolutePath}")
                        loaded = true
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to load pulse sound: ${pulseFile.absolutePath}", e)
                    }
                }
            }

            if (loaded) continue

            // 2. Fallback to global Assets
            val bestMatch = findAssetFuzzy(allAssets, soundName)
            if (bestMatch != null) {
                val assetPath = "sounds/$folderName/$bestMatch"
                try {
                    try {
                        val descriptor = context.assets.openFd(assetPath)
                        soundIdMap[soundName] = soundPool!!.load(descriptor, 1)
                        Log.d(TAG, "Loaded '$soundName' from assets (openFd): $assetPath")
                        loaded = true
                    } catch (e: Exception) {
                        // Fallback: Copy to cache for formats or compressed assets that openFd cannot handle
                        val cacheFile = File(context.cacheDir, "sound_cache_${normalize(bestMatch)}")
                        context.assets.open(assetPath).use { input ->
                            FileOutputStream(cacheFile).use { output ->
                                input.copyTo(output)
                            }
                        }
                        soundIdMap[soundName] = soundPool!!.load(cacheFile.absolutePath, 1)
                        Log.d(TAG, "Loaded '$soundName' from assets (cached): $assetPath")
                        loaded = true
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Asset load failed: $assetPath", e)
                }
            }

            if (!loaded) {
                Log.w(TAG, "Could not find sound for '$soundName' in style '$style' (Folder: sounds/$folderName)")
            }
        }
    }

    private fun normalize(s: String): String = 
        s.lowercase().replace(Regex("[^a-z0-9]"), "")

    private fun getTokens(s: String): Set<String> = 
        s.lowercase().split(Regex("[^a-z0-9]")).filter { it.length > 1 }.toSet()

    private fun isMatch(fileName: String, query: String): Boolean {
        val nameWithoutExt = fileName.substringBeforeLast(".")
        if (nameWithoutExt.equals(query, ignoreCase = true)) return true
        val nName = normalize(nameWithoutExt)
        val nQuery = normalize(query)
        if (nName.contains(nQuery) || nQuery.contains(nName)) return true
        val qTokens = getTokens(query)
        val fTokens = getTokens(nameWithoutExt)
        if (qTokens.isNotEmpty() && fTokens.containsAll(qTokens)) return true
        return false
    }

    private fun findFileFuzzy(dir: File, query: String): File? {
        val files = dir.listFiles() ?: return null
        return files.find { isMatch(it.name, query) }
    }

    private fun findAssetFuzzy(assets: Array<String>, query: String): String? {
        return assets.find { isMatch(it, query) }
    }

    fun play(sampleName: String, pitch: Float = 1.0f, volume: Float = 1.0f) {
        val executor = playbackExecutor
        if (executor == null || executor.isShutdown) {
            Log.w(TAG, "Cannot play: Synthesizer is released or not loaded.")
            return
        }

        executor.execute {
            val soundId = soundIdMap[sampleName]
            if (soundId != null) {
                Log.v(TAG, "Background playback for sample: $sampleName (pitch: $pitch, vol: $volume)")
                soundPool?.play(soundId, volume, volume, 1, 0, pitch)
            } else {
                Log.w(TAG, "Sample not loaded: $sampleName. Available: ${soundIdMap.keys}")
            }
        }
    }

    fun release() {
        Log.i(TAG, "Releasing synthesizer resources.")
        
        // Gracefully shut down the playback thread
        playbackExecutor?.shutdown()
        playbackExecutor = null

        soundPool?.release()
        soundPool = null
        soundIdMap.clear()
    }
}
