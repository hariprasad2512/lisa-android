package com.hpsdstudio.lisa

import android.app.Activity
import android.app.SearchManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import net.openid.appauth.AuthState
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationResponse

private const val TAG = "Lisa"
private const val SPOTIFY_PACKAGE = "com.spotify.music"

class MainActivity : ComponentActivity() {
    private lateinit var spotifyAuth: SpotifyAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        spotifyAuth = SpotifyAuth(this)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    LisaApp(
                        onUnstructured = ::fireUnstructured,
                        onStructured = ::fireStructured,
                        onViewFallback = ::fireViewFallback,
                        auth = spotifyAuth,
                        clientId = BuildConfig.SPOTIFY_CLIENT_ID,
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        spotifyAuth.close()
        super.onDestroy()
    }

    private fun fireUnstructured(query: String): String {
        // Unstructured search mode: artist + title + album in one free-text string.
        // Ref: developer.android.com "Common intents — Play music based on a search query".
        val intent = Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH).apply {
            setPackage(SPOTIFY_PACKAGE)
            putExtra(MediaStore.EXTRA_MEDIA_FOCUS, "vnd.android.cursor.item/*")
            putExtra(SearchManager.QUERY, query)
        }
        return fire("play-from-search", intent, query)
    }

    private fun fireStructured(query: String): String {
        // Structured "Song" search mode: explicit title extra.
        val intent = Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH).apply {
            setPackage(SPOTIFY_PACKAGE)
            putExtra(MediaStore.EXTRA_MEDIA_FOCUS, MediaStore.Audio.Media.ENTRY_CONTENT_TYPE)
            putExtra(MediaStore.EXTRA_MEDIA_TITLE, query)
            putExtra(SearchManager.QUERY, query)
        }
        return fire("structured", intent, query)
    }

    private fun fireViewFallback(query: String): String {
        // Spec section 5 fallback: ACTION_VIEW on a spotify:search: URI.
        val intent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("spotify:search:" + Uri.encode(query)),
        ).apply {
            setPackage(SPOTIFY_PACKAGE)
        }
        return fire("view-fallback", intent, query)
    }

    private fun fire(label: String, intent: Intent, query: String): String {
        return try {
            if (intent.resolveActivity(packageManager) != null) {
                startActivity(intent)
                Log.i(TAG, "$label fired query=\"$query\"")
                "[$label] Fired for \"$query\" — check Spotify."
            } else {
                Log.w(TAG, "$label: no handler (Spotify missing or not visible?)")
                "[$label] Spotify not reachable — is it installed?"
            }
        } catch (e: ActivityNotFoundException) {
            Log.e(TAG, "$label failed", e)
            "[$label] Failed: ${e.message}"
        }
    }
}

@Composable
fun LisaApp(
    onUnstructured: (String) -> String,
    onStructured: (String) -> String,
    onViewFallback: (String) -> String,
    auth: SpotifyAuth,
    clientId: String,
) {
    var query by remember { mutableStateOf("Shape of You") }
    var status by remember { mutableStateOf("Type a song, tap a variant.") }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.Top,
    ) {
        Text("Lisa — M0 intent spike", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Song query") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = { status = onUnstructured(query.trim()) },
            enabled = query.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Play: unstructured")
        }
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = { status = onStructured(query.trim()) },
            enabled = query.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Play: structured title")
        }
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = { status = onViewFallback(query.trim()) },
            enabled = query.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Open: spotify:search:")
        }
        Spacer(Modifier.height(8.dp))
        Text(status, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(16.dp))
        HorizontalDivider()
        Spacer(Modifier.height(16.dp))
        SpotifyApiSection(auth = auth, clientId = clientId)
    }
}

/**
 * M8-spike debug UI: PKCE login, track search, tap-to-play by URI.
 * Tokens stay in DataStore; every step logs under [TAG].
 */
@Composable
fun SpotifyApiSection(auth: SpotifyAuth, clientId: String) {
    val scope = rememberCoroutineScope()
    var authed by remember { mutableStateOf<Boolean?>(null) }
    var apiQuery by remember { mutableStateOf("Sahiba") }
    var results by remember { mutableStateOf(listOf<Track>()) }
    var apiStatus by remember { mutableStateOf("Checking login state…") }

    LaunchedEffect(Unit) {
        val state = auth.load()
        authed = state?.isAuthorized == true
        apiStatus = if (authed == true) "Logged in — search and tap a track." else "Not logged in."
    }

    val loginLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode != Activity.RESULT_OK || result.data == null) {
                apiStatus = "Login cancelled."
                Log.i(TAG, "spotify login cancelled")
                return@rememberLauncherForActivityResult
            }
            val resp = AuthorizationResponse.fromIntent(result.data!!)
            val ex = AuthorizationException.fromIntent(result.data!!)
            if (resp == null) {
                apiStatus = "Login failed: ${ex?.message}"
                Log.w(TAG, "spotify login failed: $ex")
                return@rememberLauncherForActivityResult
            }
            val state = AuthState(resp, ex)
            auth.service.performTokenRequest(resp.createTokenExchangeRequest()) { tokenResp, tokenEx ->
                state.update(tokenResp, tokenEx)
                scope.launch {
                    if (state.isAuthorized) {
                        auth.save(state)
                        authed = true
                        apiStatus = "Logged in — search and tap a track."
                        Log.i(TAG, "spotify login ok")
                    } else {
                        apiStatus = "Token exchange failed: ${tokenEx?.message}"
                        Log.w(TAG, "spotify token exchange failed: $tokenEx")
                    }
                }
            }
        }

    /** Loads state, refreshes the token if needed, then runs [action] with it. */
    fun withToken(action: suspend (String) -> Unit) {
        scope.launch {
            val state = auth.load()
            if (state == null || !state.isAuthorized) {
                apiStatus = "Not logged in — log in first."
                return@launch
            }
            state.performActionWithFreshTokens(auth.service) { access, _, ex ->
                if (access == null) {
                    apiStatus = "Token refresh failed: ${ex?.message}"
                    Log.w(TAG, "spotify token refresh failed: $ex")
                } else {
                    scope.launch {
                        try {
                            action(access)
                        } catch (e: Exception) {
                            apiStatus = "API error: ${e.message}"
                            Log.e(TAG, "spotify api call failed", e)
                        }
                    }
                }
            }
        }
    }

    Text("Spotify API (M8 spike)", style = MaterialTheme.typography.headlineSmall)
    Spacer(Modifier.height(8.dp))
    Text(
        when (authed) {
            true -> "Status: logged in"
            false -> "Status: logged out"
            null -> "Status: checking…"
        },
        style = MaterialTheme.typography.bodyMedium,
    )
    Spacer(Modifier.height(8.dp))
    if (authed != true) {
        Button(
            onClick = {
                if (clientId.isBlank()) {
                    apiStatus = "Set spotify.clientId in local.properties and rebuild."
                    Log.w(TAG, "login blocked: no client ID configured")
                    return@Button
                }
                loginLauncher.launch(auth.service.getAuthorizationRequestIntent(auth.loginRequest(clientId)))
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Log in with Spotify")
        }
    } else {
        Button(
            onClick = {
                scope.launch {
                    auth.clear()
                    authed = false
                    results = emptyList()
                    apiStatus = "Logged out."
                    Log.i(TAG, "spotify logged out")
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Log out")
        }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = apiQuery,
            onValueChange = { apiQuery = it },
            label = { Text("API search") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = {
                withToken { token ->
                    results = SpotifyApi.searchTracks(token, apiQuery.trim())
                    apiStatus = if (results.isEmpty()) "No results." else "${results.size} result(s) — tap one to play."
                }
            },
            enabled = apiQuery.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Search tracks")
        }
        results.forEach { track ->
            TextButton(
                onClick = {
                    withToken { token ->
                        apiStatus = when (val r = SpotifyApi.playTrack(token, track.uri)) {
                            is PlayResult.Success -> "Playing \"${track.name}\"."
                            is PlayResult.NoActiveDevice ->
                                "No active device — open Spotify on the phone first, then retry."
                            is PlayResult.Error -> "Play failed: HTTP ${r.code} ${r.body}"
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("${track.name} — ${track.artists}")
            }
        }
    }
    Spacer(Modifier.height(8.dp))
    Text(apiStatus, style = MaterialTheme.typography.bodyMedium)
}
