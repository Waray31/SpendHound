package com.waray.spendhound

import android.graphics.drawable.Animatable
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import io.github.jan.supabase.gotrue.SessionStatus
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SplashActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        try {
            try {
                DeclareDatabase.initialize(applicationContext)
            } catch (t: Throwable) {
                Log.e("SplashActivity", "Failed to initialize database: ${t.message}", t)
            }

            setContentView(R.layout.splash_screen)

            val logoContainer = findViewById<LinearLayout>(R.id.logoContainer)
            val imageView = findViewById<ImageView>(R.id.imageView)
            
            val entranceAnimation = AnimationUtils.loadAnimation(this, R.anim.splash_animation)
            logoContainer?.startAnimation(entranceAnimation)

            entranceAnimation.setAnimationListener(object : Animation.AnimationListener {
                override fun onAnimationStart(animation: Animation?) {}
                override fun onAnimationEnd(animation: Animation?) {
                    val drawable = imageView?.drawable
                    if (drawable != null && drawable is Animatable) {
                        (drawable as Animatable).start()
                    }
                }
                override fun onAnimationRepeat(animation: Animation?) {}
            })

            Handler(Looper.getMainLooper()).postDelayed({
                checkSessionAndNavigate()
            }, 3500)
        } catch (t: Throwable) {
            Log.e("SplashActivity", "Crash in onCreate: ${t.message}", t)
            // Last ditch effort to go to login
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
    }

    private fun checkSessionAndNavigate() {
        Log.d("SplashActivity", "checkSessionAndNavigate: START")
        lifecycleScope.launch {
            try {
                // Ensure database is initialized
                Log.d("SplashActivity", "checkSessionAndNavigate: Initializing DeclareDatabase")
                DeclareDatabase.initialize(applicationContext)
                
                val client = DeclareDatabase.clientOrNull
                if (client == null) {
                    Log.e("SplashActivity", "checkSessionAndNavigate: Supabase client is null after initialization attempt")
                    startActivity(Intent(this@SplashActivity, LoginActivity::class.java))
                    finish()
                    return@launch
                }

                Log.d("SplashActivity", "checkSessionAndNavigate: Checking session status")
                val status = try {
                    client.auth.sessionStatus.value
                } catch (e: Exception) {
                    Log.e("SplashActivity", "checkSessionAndNavigate: Failed to get session status: ${e.message}", e)
                    null
                }
                
                Log.d("SplashActivity", "checkSessionAndNavigate: Status is $status")
                if (status != null && status is SessionStatus.Authenticated) {
                    Log.d("SplashActivity", "checkSessionAndNavigate: Authenticated. Navigating to MainActivity")
                    startActivity(Intent(this@SplashActivity, MainActivity::class.java))
                } else {
                    Log.d("SplashActivity", "checkSessionAndNavigate: Not authenticated. Navigating to LoginActivity")
                    startActivity(Intent(this@SplashActivity, LoginActivity::class.java))
                }
            } catch (t: Throwable) {
                Log.e("SplashActivity", "checkSessionAndNavigate: Fatal error: ${t.message}", t)
                // In case of error (e.g. initialization issue), fallback to LoginActivity
                try {
                    startActivity(Intent(this@SplashActivity, LoginActivity::class.java))
                } catch (e: Exception) {
                    Log.e("SplashActivity", "checkSessionAndNavigate: Failed to even start LoginActivity: ${e.message}", e)
                }
            } finally {
                Log.d("SplashActivity", "checkSessionAndNavigate: FINISH")
                finish()
            }
        }
    }
}
