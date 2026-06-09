package com.waray.spendhound

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
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SplashActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.splash_screen)

        val logoContainer = findViewById<LinearLayout>(R.id.logoContainer)
        val imageView = findViewById<ImageView>(R.id.imageView)
        
        val entranceAnimation = AnimationUtils.loadAnimation(this, R.anim.splash_animation)
        logoContainer.startAnimation(entranceAnimation)

        entranceAnimation.setAnimationListener(object : Animation.AnimationListener {
            override fun onAnimationStart(animation: Animation?) {}
            override fun onAnimationEnd(animation: Animation?) {
                val wiggle = AnimationUtils.loadAnimation(this@SplashActivity, R.anim.logo_wiggle)
                imageView.startAnimation(wiggle)
            }
            override fun onAnimationRepeat(animation: Animation?) {}
        })

        Handler(Looper.getMainLooper()).postDelayed({
            checkSessionAndNavigate()
        }, 3500)
    }

    private fun checkSessionAndNavigate() {
        lifecycleScope.launch {
            val status = DeclareDatabase.auth.sessionStatus.value
            
            if (status is SessionStatus.Authenticated) {
                // User is signed in, go to MainActivity
                startActivity(Intent(this@SplashActivity, MainActivity::class.java))
            } else {
                // No session found or unauthenticated, go to LoginActivity
                startActivity(Intent(this@SplashActivity, LoginActivity::class.java))
            }
            finish()
        }
    }
}
