package com.hpsdstudio.lisa

import android.content.Context
import android.net.Uri
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import net.openid.appauth.AuthState
import net.openid.appauth.AuthorizationRequest
import net.openid.appauth.AuthorizationService
import net.openid.appauth.AuthorizationServiceConfiguration
import net.openid.appauth.ResponseTypeValues

/** Redirect URI. Must exactly match the allowlist entry in the Spotify dashboard. */
const val SPOTIFY_REDIRECT_URI = "com.hpsdstudio.lisa://oauth2redirect"

private const val AUTH_ENDPOINT = "https://accounts.spotify.com/authorize"
private const val TOKEN_ENDPOINT = "https://accounts.spotify.com/api/token"

/** Play requires modify; device checks require read. Search works with any valid token. */
private const val SCOPES = "user-modify-playback-state user-read-playback-state"

private val Context.authStore by preferencesDataStore(name = "spotify_auth")
private val AUTH_STATE_JSON = stringPreferencesKey("auth_state_json")

/**
 * M8-spike: Spotify Authorization Code + PKCE via AppAuth.
 * Tokens ([AuthState] JSON) persist in DataStore (app sandbox).
 */
class SpotifyAuth(private val context: Context) : AutoCloseable {
    val service = AuthorizationService(context)
    val config = AuthorizationServiceConfiguration(
        Uri.parse(AUTH_ENDPOINT),
        Uri.parse(TOKEN_ENDPOINT),
    )

    fun loginRequest(clientId: String): AuthorizationRequest =
        AuthorizationRequest.Builder(
            config,
            clientId,
            ResponseTypeValues.CODE,
            Uri.parse(SPOTIFY_REDIRECT_URI),
        ).setScope(SCOPES).build()

    suspend fun load(): AuthState? {
        val json = context.authStore.data.map { it[AUTH_STATE_JSON] }.first() ?: return null
        return try {
            AuthState.jsonDeserialize(json)
        } catch (_: Exception) {
            null
        }
    }

    suspend fun save(state: AuthState) {
        context.authStore.edit { it[AUTH_STATE_JSON] = state.jsonSerializeString() }
    }

    suspend fun clear() {
        context.authStore.edit { it.remove(AUTH_STATE_JSON) }
    }

    override fun close() = service.dispose()
}
