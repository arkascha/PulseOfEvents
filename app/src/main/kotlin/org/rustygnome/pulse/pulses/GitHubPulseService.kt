package org.rustygnome.pulse.pulses

import android.util.Log
import okhttp3.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

class GitHubPulseService {
    private val client = OkHttpClient()
    private val repoOwner = "arkascha"
    private val repoName = "PulseOfEvents"

    fun fetchAvailablePulses(versionName: String, onSuccess: (List<GitHubPulse>) -> Unit, onError: (Exception) -> Unit) {
        // Target the specific tag directly for maximum performance
        val tagName = "version-$versionName"
        val url = "https://api.github.com/repos/$repoOwner/$repoName/releases/tags/$tagName"
        
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "PulseOfEvents-App")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                onError(e)
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.code == 404) {
                    // Fallback to checking the releases list if the specific tag format isn't found
                    fetchByReleasesList(versionName, onSuccess, onError)
                    return
                }

                if (!response.isSuccessful) {
                    onError(IOException("Unexpected code $response"))
                    return
                }

                try {
                    val release = JSONObject(response.body?.string() ?: "{}")
                    processRelease(release, onSuccess, onError)
                } catch (e: Exception) {
                    onError(e)
                }
            }
        })
    }

    private fun fetchByReleasesList(versionName: String, onSuccess: (List<GitHubPulse>) -> Unit, onError: (Exception) -> Unit) {
        // Fallback: search through the most recent releases
        val url = "https://api.github.com/repos/$repoOwner/$repoName/releases?per_page=10"
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "PulseOfEvents-App")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                onError(e)
            }
            override fun onResponse(call: Call, response: Response) {
                if (!response.isSuccessful) {
                    onSuccess(emptyList())
                    return
                }
                try {
                    val body = response.body?.string() ?: "[]"
                    val releases = JSONArray(body)
                    for (i in 0 until releases.length()) {
                        val release = releases.getJSONObject(i)
                        val tag = release.getString("tag_name")
                        if (tag == versionName || tag == "v$versionName" || tag.contains(versionName)) {
                            processRelease(release, onSuccess, onError)
                            return
                        }
                    }
                    onSuccess(emptyList())
                } catch (e: Exception) {
                    onError(e)
                }
            }
        })
    }

    private fun processRelease(release: JSONObject, onSuccess: (List<GitHubPulse>) -> Unit, onError: (Exception) -> Unit) {
        val assets = release.optJSONArray("assets") ?: JSONArray()
        var indexAsset: JSONObject? = null
        val pulseAssets = mutableMapOf<String, String>()

        for (j in 0 until assets.length()) {
            val asset = assets.getJSONObject(j)
            val name = asset.getString("name")
            if (name == "pulses_index.json") {
                indexAsset = asset
            } else if (name.endsWith(".pulse")) {
                pulseAssets[name] = asset.getString("browser_download_url")
            }
        }

        if (indexAsset != null) {
            fetchIndex(indexAsset.getString("browser_download_url")) { indexJson ->
                val releaseName = release.optString("name", release.optString("tag_name", "Unknown Release"))
                val allPulses = mutableListOf<GitHubPulse>()
                for (k in 0 until indexJson.length()) {
                    val item = indexJson.getJSONObject(k)
                    val filename = item.getString("filename")
                    val downloadUrl = pulseAssets[filename]
                    if (downloadUrl != null) {
                        allPulses.add(GitHubPulse(
                            name = item.optString("name", filename.removeSuffix(".pulse")),
                            downloadUrl = downloadUrl,
                            releaseName = releaseName,
                            description = item.optString("description", "")
                        ))
                    }
                }
                onSuccess(allPulses)
            }
        } else {
            onSuccess(emptyList())
        }
    }

    private fun fetchIndex(url: String, onDownloaded: (JSONArray) -> Unit) {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "PulseOfEvents-App")
            .build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                onDownloaded(JSONArray("[]"))
            }
            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    try {
                        onDownloaded(JSONArray(response.body?.string() ?: "[]"))
                    } catch (e: Exception) {
                        onDownloaded(JSONArray("[]"))
                    }
                } else {
                    onDownloaded(JSONArray("[]"))
                }
            }
        })
    }

    data class GitHubPulse(
        val name: String,
        val downloadUrl: String,
        val releaseName: String,
        val description: String
    )
}
