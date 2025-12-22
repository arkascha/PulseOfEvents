package org.rustygnome.pulse.plugins

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

class GitHubPluginService {
    private val client = OkHttpClient()
    private val repoOwner = "arkascha"
    private val repoName = "PulseOfEvents"

    fun fetchAvailablePlugins(onSuccess: (List<GitHubPlugin>) -> Unit, onError: (Exception) -> Unit) {
        val url = "https://api.github.com/repos/$repoOwner/$repoName/releases"
        val request = Request.Builder().url(url).build()

        client.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: IOException) {
                onError(e)
            }

            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                if (!response.isSuccessful) {
                    onError(IOException("Unexpected code $response"))
                    return
                }

                try {
                    val releasesJson = JSONArray(response.body?.string() ?: "[]")
                    val plugins = mutableListOf<GitHubPlugin>()

                    for (i in 0 until releasesJson.length()) {
                        val release = releasesJson.getJSONObject(i)
                        val body = release.optString("body", "")
                        val assets = release.getJSONArray("assets")
                        for (j in 0 until assets.length()) {
                            val asset = assets.getJSONObject(j)
                            val name = asset.getString("name")
                            val label = asset.optString("label", "")
                            
                            if (name.endsWith(".pulse")) {
                                // Prefer asset label (if set in GitHub Release UI)
                                // Otherwise try to parse the release body for "pluginname: description"
                                val individualDescription = when {
                                    label.isNotEmpty() -> label
                                    else -> findDescriptionInBody(body, name) ?: body
                                }

                                plugins.add(GitHubPlugin(
                                    name = name.removeSuffix(".pulse"),
                                    downloadUrl = asset.getString("browser_download_url"),
                                    releaseName = release.getString("name"),
                                    description = individualDescription
                                ))
                            }
                        }
                    }
                    onSuccess(plugins)
                } catch (e: Exception) {
                    onError(e)
                }
            }
        })
    }

    private fun findDescriptionInBody(body: String, fileName: String): String? {
        val nameWithoutExt = fileName.removeSuffix(".pulse")
        val patterns = listOf("$nameWithoutExt:", "$fileName:")
        return body.lines()
            .map { it.trim() }
            .firstOrNull { line -> 
                patterns.any { p -> line.startsWith(p, ignoreCase = true) } 
            }
            ?.substringAfter(":")?.trim()
    }

    data class GitHubPlugin(
        val name: String,
        val downloadUrl: String,
        val releaseName: String,
        val description: String
    )
}
