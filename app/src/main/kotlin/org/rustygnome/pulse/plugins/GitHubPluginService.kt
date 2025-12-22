package org.rustygnome.pulse.plugins

import android.util.Log
import okhttp3.*
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

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                onError(e)
            }

            override fun onResponse(call: Call, response: Response) {
                if (!response.isSuccessful) {
                    onError(IOException("Unexpected code $response"))
                    return
                }

                try {
                    val releasesJson = JSONArray(response.body?.string() ?: "[]")
                    if (releasesJson.length() == 0) {
                        onSuccess(emptyList())
                        return
                    }

                    // We look into all releases for a plugins_index.json
                    // and use it to map descriptions for the assets found in that same release.
                    val allPlugins = mutableListOf<GitHubPlugin>()

                    for (i in 0 until releasesJson.length()) {
                        val release = releasesJson.getJSONObject(i)
                        val assets = release.getJSONArray("assets")
                        var indexAsset: JSONObject? = null
                        val pulseAssets = mutableMapOf<String, String>()

                        for (j in 0 until assets.length()) {
                            val asset = assets.getJSONObject(j)
                            val name = asset.getString("name")
                            if (name == "plugins_index.json") {
                                indexAsset = asset
                            } else if (name.endsWith(".pulse")) {
                                pulseAssets[name] = asset.getString("browser_download_url")
                            }
                        }

                        if (indexAsset != null) {
                            fetchIndex(indexAsset.getString("browser_download_url")) { indexJson ->
                                val releaseName = release.getString("name")
                                for (k in 0 until indexJson.length()) {
                                    val item = indexJson.getJSONObject(k)
                                    val filename = item.getString("filename")
                                    val downloadUrl = pulseAssets[filename]
                                    if (downloadUrl != null) {
                                        allPlugins.add(GitHubPlugin(
                                            name = item.optString("name", filename.removeSuffix(".pulse")),
                                            downloadUrl = downloadUrl,
                                            releaseName = releaseName,
                                            description = item.optString("description", "")
                                        ))
                                    }
                                }
                                // If this was the last release to process, we could call onSuccess.
                                // But since network calls are async, we need a better way to coordinate.
                                // Simplification: Just use the latest release that has an index.
                                if (allPlugins.isNotEmpty()) {
                                    onSuccess(allPlugins)
                                }
                            }
                            return // Exit after finding the first (latest) valid index
                        }
                    }
                    
                    // Fallback if no index found: use previous logic or empty
                    onSuccess(emptyList())

                } catch (e: Exception) {
                    onError(e)
                }
            }
        })
    }

    private fun fetchIndex(url: String, onDownloaded: (JSONArray) -> Unit) {
        val request = Request.Builder().url(url).build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {}
            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    try {
                        onDownloaded(JSONArray(response.body?.string() ?: "[]"))
                    } catch (e: Exception) {
                        Log.e("GitHubPluginService", "Error parsing index", e)
                    }
                }
            }
        })
    }

    data class GitHubPlugin(
        val name: String,
        val downloadUrl: String,
        val releaseName: String,
        val description: String
    )
}
