package com.waray.spendhound

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import io.github.jan.supabase.gotrue.SessionStatus
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SplashActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.splash_screen)

        Handler(Looper.getMainLooper()).postDelayed({
            checkSessionAndNavigate()
        }, 2000)
    }

    private fun checkSessionAndNavigate() {
        lifecycleScope.launch {
            val status = DeclareDatabase.auth.sessionStatus.value
            
            if (status is SessionStatus.Authenticated) {
                val user = DeclareDatabase.auth.currentUserOrNull()
                val userId = user?.id
                
                if (userId != null) {
                    val prefs = getSharedPreferences("SpendHoundPrefs", Context.MODE_PRIVATE)
                    val isStep2Done = prefs.getBoolean("step2_completed_$userId", false)
                    val isRedirectAttempted = prefs.getBoolean("step2_redirect_attempted_$userId", false)
                    
                    if (!isStep2Done && !isRedirectAttempted) {
                        try {
                            // Mark redirect as attempted (one-time only)
                            prefs.edit().putBoolean("step2_redirect_attempted_$userId", true).apply()
                            
                            val dbUser = withContext(Dispatchers.IO) {
                                DeclareDatabase.usersTable.select(Columns.list("user_id", "email")) {
                                    filter { eq("auth_id", userId) }
                                }.decodeSingleOrNull<User>()
                            }
                            
                            if (dbUser != null) {
                                val intent = Intent(this@SplashActivity, SignUpActivity::class.java)
                                intent.putExtra("REDIRECTED_TO_STEP_2", true)
                                intent.putExtra("USER_EMAIL", dbUser.email)
                                intent.putExtra("USER_AUTH_ID", userId)
                                intent.putExtra("USER_INTERNAL_ID", dbUser.id)
                                startActivity(intent)
                                finish()
                                return@launch
                            }
                        } catch (e: Exception) {
                            Log.e("SplashActivity", "Error checking user status: ${e.message}")
                        }
                    }
                }
                // User is signed in and finished Step 2 (or redirect was already attempted once)
                startActivity(Intent(this@SplashActivity, MainActivity::class.java))
            } else {
                // No session found or unauthenticated, go to LoginActivity
                startActivity(Intent(this@SplashActivity, LoginActivity::class.java))
            }
            finish()
        }
    }
}
