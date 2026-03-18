package com.waray.spendhound

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import androidx.appcompat.app.AppCompatActivity

class SplashActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.splash_screen)

        Handler().postDelayed(object : Runnable {
            override fun run() {
                // Create an Intent to start the MainActivity
                val intent = Intent(this@SplashActivity, LoginActivity::class.java)
                startActivity(intent)

                // Finish the current activity (splash screen)
                finish()
            }
        }, 2000) // Specify the delay in milliseconds (e.g., 2000 = 2 seconds)
    }
}