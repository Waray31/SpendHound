package com.waray.spendhound.ui.settings

import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.waray.spendhound.R

class SettingsActivity : AppCompatActivity() {

    private lateinit var btnBack: ImageButton
    private lateinit var layoutCurrency: View
    private lateinit var tvCurrentCurrency: TextView
    private lateinit var toggleNotifications: FrameLayout
    private lateinit var thumbNotifications: View
    private lateinit var toggleDarkMode: FrameLayout
    private lateinit var thumbDarkMode: View

    private var notificationsEnabled = true
    private var darkModeEnabled = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        // Check current system night mode state
        val currentNightMode = resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
        darkModeEnabled = currentNightMode == android.content.res.Configuration.UI_MODE_NIGHT_YES

        btnBack = findViewById(R.id.btnBack)
        layoutCurrency = findViewById(R.id.layoutCurrency)
        tvCurrentCurrency = findViewById(R.id.tvCurrentCurrency)
        toggleNotifications = findViewById(R.id.toggleNotifications)
        thumbNotifications = findViewById(R.id.thumbNotifications)
        toggleDarkMode = findViewById(R.id.toggleDarkMode)
        thumbDarkMode = findViewById(R.id.thumbDarkMode)

        btnBack.setOnClickListener { finish() }

        layoutCurrency.setOnClickListener {
            changeCurrency()
        }

        // Initialize Dark Mode toggle as OFF
        updateToggleAppearance(toggleDarkMode, thumbDarkMode, darkModeEnabled)
        // Initialize Notifications toggle as ON
        updateToggleAppearance(toggleNotifications, thumbNotifications, notificationsEnabled)

        toggleNotifications.setOnClickListener {
            notificationsEnabled = !notificationsEnabled
            updateToggleAppearance(toggleNotifications, thumbNotifications, notificationsEnabled)
            handleNotifications(notificationsEnabled)
        }

        toggleDarkMode.setOnClickListener {
            darkModeEnabled = !darkModeEnabled
            updateToggleAppearance(toggleDarkMode, thumbDarkMode, darkModeEnabled)
            handleDarkMode(darkModeEnabled)
        }
    }

    private fun changeCurrency() {
        // Simple implementation: toggle between PHP and USD
        val current = tvCurrentCurrency.text.toString()
        if (current == "PHP" || current == getString(R.string.label_php)) {
            tvCurrentCurrency.text = "USD"
            Toast.makeText(this, "Currency changed to USD", Toast.LENGTH_SHORT).show()
        } else {
            tvCurrentCurrency.text = getString(R.string.label_php)
            Toast.makeText(this, "Currency changed to PHP", Toast.LENGTH_SHORT).show()
        }
    }

    private fun handleNotifications(enabled: Boolean) {
        // Handle logic here
    }

    private fun handleDarkMode(enabled: Boolean) {
        if (enabled) {
            androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES)
        } else {
            androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO)
        }
    }

    private fun updateToggleAppearance(track: FrameLayout, thumb: View, isOn: Boolean) {
        val density = resources.displayMetrics.density
        val thumbWidth = (28 * density).toInt()
        val toggleWidth = (60 * density).toInt()

        if (isOn) {
            track.setBackgroundResource(R.drawable.bg_toggle_track_on)
            val maxTravel = toggleWidth - thumbWidth - (2 * density)
            thumb.animate()
                .translationX(maxTravel)
                .setDuration(200)
                .start()
        } else {
            track.setBackgroundResource(R.drawable.bg_toggle_track)
            thumb.animate()
                .translationX(0f)
                .setDuration(200)
                .start()
        }
    }
}
