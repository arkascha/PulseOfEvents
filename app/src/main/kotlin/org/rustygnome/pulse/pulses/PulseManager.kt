package org.rustygnome.pulse.pulses

import android.content.Context
import android.net.Uri
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.zip.ZipInputStream

class PulseManager(private val context: Context) {

    private val pulsesDir = File(context.filesDir, "pulses")

    init {
        if (!pulsesDir.exists()) {
            pulsesDir.mkdirs()
        }
    }

    fun unpackPulse(uri: Uri): PulseData? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                unpackPulse(inputStream)
            }
        } catch (e: Exception) {
            Log.e("PulseManager", "Error opening URI stream", e)
            null
        }
    }

    fun unpackPulse(inputStream: InputStream): PulseData? {
        val pulseId = System.currentTimeMillis().toString()
        val targetDir = File(pulsesDir, pulseId)
        targetDir.mkdirs()

        var scriptContent: String? = null
        var configContent: String? = null

        try {
            ZipInputStream(inputStream).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    val file = File(targetDir, entry.name)
                    if (entry.isDirectory) {
                        file.mkdirs()
                    } else {
                        file.parentFile?.mkdirs()
                        FileOutputStream(file).use { fos ->
                            zis.copyTo(fos)
                        }
                        
                        // Check for special files
                        when (entry.name) {
                            "mapping.js" -> scriptContent = file.readText()
                            "config.json" -> configContent = file.readText()
                        }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
        } catch (e: Exception) {
            Log.e("PulseManager", "Error unpacking pulse", e)
            targetDir.deleteRecursively()
            return null
        }

        return if (scriptContent != null && configContent != null) {
            PulseData(pulseId, scriptContent!!, configContent!!, targetDir.absolutePath)
        } else {
            Log.e("PulseManager", "Pulse missing mapping.js or config.json")
            targetDir.deleteRecursively()
            null
        }
    }

    fun getSoundsDir(pulseId: String): File {
        return File(File(pulsesDir, pulseId), "sounds")
    }

    fun deletePulse(pulseId: String) {
        File(pulsesDir, pulseId).deleteRecursively()
    }

    data class PulseData(
        val id: String,
        val script: String,
        val config: String,
        val rootPath: String
    )
}
