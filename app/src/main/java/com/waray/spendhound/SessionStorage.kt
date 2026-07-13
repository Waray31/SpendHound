package com.waray.spendhound

import android.content.Context
import androidx.core.content.edit
import io.github.jan.supabase.gotrue.SessionManager
import io.github.jan.supabase.gotrue.user.UserSession
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class SharedPreferencesSessionManager(context: Context) : SessionManager {
    private val sharedPreferences = context.applicationContext.getSharedPreferences("supabase_session", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun saveSession(session: UserSession) {
        android.util.Log.d("SessionManager", "saveSession: Saving session")
        sharedPreferences.edit {
            putString("session", json.encodeToString(session))
        }
    }

    override suspend fun loadSession(): UserSession? {
        android.util.Log.d("SessionManager", "loadSession: Loading session")
        val sessionString = sharedPreferences.getString("session", null) ?: run {
            android.util.Log.d("SessionManager", "loadSession: No session string found")
            return null
        }
        return try {
            val session = json.decodeFromString<UserSession>(sessionString)
            android.util.Log.d("SessionManager", "loadSession: Session decoded successfully")
            session
        } catch (e: Exception) {
            android.util.Log.e("SessionManager", "loadSession: Failed to decode session: ${e.message}", e)
            null
        }
    }

    override suspend fun deleteSession() {
        android.util.Log.d("SessionManager", "deleteSession: Deleting session")
        sharedPreferences.edit {
            remove("session")
        }
    }
}
