package org.rustygnome.pulse.plugins

import android.content.Context
import android.net.Uri
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipInputStream

class PluginManager(private val context: Context) {

    private val pluginsDir = File(context.filesDir, "plugins")

    init {
        if (!pluginsDir.exists()) {
            pluginsDir.mkdirs()
        }
    }

    fun unpackPlugin(uri: Uri): PluginData? {
        val pluginId = System.currentTimeMillis().toString()
        val targetDir = File(pluginsDir, pluginId)
        targetDir.mkdirs()

        var scriptContent: String? = null
        var configContent: String? = null

        try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
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
            }
        } catch (e: Exception) {
            Log.e("PluginManager", "Error unpacking plugin", e)
            targetDir.deleteRecursively()
            return null
        }

        return if (scriptContent != null && configContent != null) {
            PluginData(pluginId, scriptContent!!, configContent!!, targetDir.absolutePath)
        } else {
            Log.e("PluginManager", "Plugin missing mapping.js or config.json")
            targetDir.deleteRecursively()
            null
        }
    }

    fun getSoundsDir(pluginId: String): File {
        return File(File(pluginsDir, pluginId), "sounds")
    }

    fun deletePlugin(pluginId: String) {
        File(pluginsDir, pluginId).deleteRecursively()
    }

    data class PluginData(
        val id: String,
        val script: String,
        val config: String,
        val rootPath: String
    )
}
