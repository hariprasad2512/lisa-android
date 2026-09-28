package com.hpsdstudio.lisa

import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

private const val TAG = "Lisa"
private const val API_BASE = "https://api.spotify.com/v1"

data class Track(val uri: String, val name: String, val artists: String)

sealed interface PlayResult {
    data object Success : PlayResult
    /** HTTP 404 from /me/player/play: no Spotify client is currently active. */
    data object NoActiveDevice : PlayResult
    data class Error(val code: Int, val body: String) : PlayResult
}

/**
 * M8-spike: minimal Spotify Web API client over [HttpURLConnection].
 * All calls run on [Dispatchers.IO]; errors throw except [playTrack],
 * which models the no-active-device case explicitly.
 */
object SpotifyApi {

    suspend fun searchTracks(token: String, query: String): List<Track> = withContext(Dispatchers.IO) {
        val url = URL("$API_BASE/search?q=${Uri.encode(query)}&type=track&limit=10")
        val body = get(url, token)
        val items = JSONObject(body).getJSONObject("tracks").getJSONArray("items")
        buildList {
            for (i in 0 until items.length()) {
                val t = items.getJSONObject(i)
                val artists = t.getJSONArray("artists")
                add(
                    Track(
                        uri = t.getString("uri"),
                        name = t.getString("name"),
                        artists = buildString {
                            for (a in 0 until artists.length()) {
                                if (a > 0) append(", ")
                                append(artists.getJSONObject(a).getString("name"))
                            }
                        },
                    ),
                )
            }
        }.also { Log.i(TAG, "spotify search query=\"$query\" hits=${it.size}") }
    }

    suspend fun playTrack(token: String, uri: String): PlayResult = withContext(Dispatchers.IO) {
        val conn = (URL("$API_BASE/me/player/play").openConnection() as HttpURLConnection).apply {
            requestMethod = "PUT"
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Content-Type", "application/json")
            doOutput = true
            outputStream.bufferedWriter().use { it.write("{\"uris\":[\"$uri\"]}") }
        }
        val code = conn.responseCode
        val body = try {
            (if (code in 200..299) conn.inputStream else conn.errorStream)?.bufferedReader()?.readText() ?: ""
        } catch (_: Exception) {
            ""
        } finally {
            conn.disconnect()
        }
        Log.i(TAG, "spotify play uri=\"$uri\" code=$code body=$body")
        when {
            code in 200..299 -> PlayResult.Success
            code == 404 -> PlayResult.NoActiveDevice
            else -> PlayResult.Error(code, body)
        }
    }

    private fun get(url: URL, token: String): String {
        val conn = (url.openConnection() as HttpURLConnection).apply {
            setRequestProperty("Authorization", "Bearer $token")
        }
        try {
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val body = stream?.bufferedReader()?.readText() ?: ""
            if (code !in 200..299) throw SpotifyHttpException(code, body)
            return body
        } finally {
            conn.disconnect()
        }
    }
}

class SpotifyHttpException(val code: Int, val body: String) : Exception("HTTP $code: $body")
