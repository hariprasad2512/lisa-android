package com.hpsdstudio.lisa

import android.app.SearchManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

private const val TAG = "Lisa"
private const val SPOTIFY_PACKAGE = "com.spotify.music"

/**
 * M0 — Spotify spike. One text field, one button. Fires
 * [MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH] at the Spotify app and
 * reports what happened. See docs/PROJECT_SPEC.md section 5.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    M0Screen(onPlay = ::firePlayFromSearch)
                }
            }
        }
    }

    private fun firePlayFromSearch(query: String): String {
        // Unstructured search mode: artist + title + album in one free-text string.
        // Ref: developer.android.com "Common intents — Play music based on a search query".
        val intent = Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH).apply {
            setPackage(SPOTIFY_PACKAGE)
            putExtra(MediaStore.EXTRA_MEDIA_FOCUS, "vnd.android.cursor.item/*")
            putExtra(SearchManager.QUERY, query)
        }
        return try {
            if (intent.resolveActivity(packageManager) != null) {
                startActivity(intent)
                Log.i(TAG, "play-from-search fired query=\"$query\"")
                "Fired for \"$query\" — check Spotify."
            } else {
                Log.w(TAG, "no handler for Spotify play-from-search (Spotify missing or not visible?)")
                "Spotify not reachable — is it installed?"
            }
        } catch (e: ActivityNotFoundException) {
            Log.e(TAG, "Spotify play-from-search failed", e)
            "Failed: ${e.message}"
        }
    }
}

@Composable
fun M0Screen(onPlay: (String) -> String) {
    var query by remember { mutableStateOf("Shape of You") }
    var status by remember { mutableStateOf("Type a song, tap Play.") }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Lisa — M0 Spotify spike", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Song query") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = { status = onPlay(query.trim()) },
            enabled = query.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Play on Spotify")
        }
        Spacer(Modifier.height(12.dp))
        Text(status, style = MaterialTheme.typography.bodyMedium)
    }
}
