package com.waray.spendhound

import android.content.Context
import androidx.core.content.edit
import io.github.jan.supabase.gotrue.SessionManager
import io.github.jan.supabase.gotrue.user.UserSession
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class SharedPreferencesSessionManager(context: Context) : SessionManager {
    private val sharedPreferences = context.getSharedPreferences("supabase_session", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun saveSession(session: UserSession) {
        sharedPreferences.edit {
            putString("session", json.encodeToString(session))
        }
    }

    override suspend fun loadSession(): UserSession? {
        val sessionString = sharedPreferences.getString("session", null) ?: return null
        return try {
            json.decodeFromString<UserSession>(sessionString)
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun deleteSession() {
        sharedPreferences.edit {
            remove("session")
        }
    }
}
