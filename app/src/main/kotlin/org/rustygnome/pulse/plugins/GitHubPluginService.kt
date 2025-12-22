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
                        val assets = release.getJSONArray("assets")
                        for (j in 0 until assets.length()) {
                            val asset = assets.getJSONObject(j)
                            val name = asset.getString("name")
                            if (name.endsWith(".pulse")) {
                                plugins.add(GitHubPlugin(
                                    name = name,
                                    downloadUrl = asset.getString("browser_download_url"),
                                    releaseName = release.getString("name")
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

    data class GitHubPlugin(
        val name: String,
        val downloadUrl: String,
        val releaseName: String
    )
}
