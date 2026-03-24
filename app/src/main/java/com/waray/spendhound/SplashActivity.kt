package com.waray.spendhound

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import io.github.jan.supabase.gotrue.SessionStatus
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

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
            // Wait for the first session status that isn't Loading
            val status = DeclareDatabase.auth.sessionStatus.value
            
            if (status is SessionStatus.Authenticated) {
                // User is already signed in, go to MainActivity
                startActivity(Intent(this@SplashActivity, MainActivity::class.java))
            } else {
                // No session found or unauthenticated, go to LoginActivity
                startActivity(Intent(this@SplashActivity, LoginActivity::class.java))
            }
            finish()
        }
    }
}
