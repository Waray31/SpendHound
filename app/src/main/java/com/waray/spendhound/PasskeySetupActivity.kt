package com.waray.spendhound

import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.textfield.TextInputEditText
import io.github.jan.supabase.gotrue.providers.builtin.Email
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PasskeySetupActivity : AppCompatActivity() {

    private lateinit var progressBar: ProgressBar
    private var userEmail: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_passkey_setup)

        userEmail = intent.getStringExtra(EditProfileActivity.EXTRA_USER_EMAIL)
        progressBar = findViewById(R.id.progressBar)

        findViewById<ImageButton>(R.id.btnClose).setOnClickListener { finish() }
        findViewById<TextView>(R.id.btnNotNow).setOnClickListener { finish() }
        findViewById<View>(R.id.btnCreatePasskey).setOnClickListener { showPasswordConfirmThenEnroll() }
    }

    private fun showPasswordConfirmThenEnroll() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_unlock_email, null)
        val etInput = dialogView.findViewById<TextInputEditText>(R.id.etUnlockEmailPassword)

        AlertDialog.Builder(this)
            .setView(dialogView)
            .setPositiveButton("Confirm") { _, _ ->
                val entered = etInput.text.toString()
                if (entered.isEmpty()) {
                    Toast.makeText(this, "Password cannot be empty", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                verifyThenEnroll(entered)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun verifyThenEnroll(enteredPassword: String) {
        val email = userEmail ?: run {
            Toast.makeText(this, "User email not found", Toast.LENGTH_SHORT).show()
            return
        }
        progressBar.visibility = View.VISIBLE
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    DeclareDatabase.auth.signInWith(Email) {
                        this.email = email
                        this.password = enteredPassword
                    }
                }
                enrollPasskey(email, enteredPassword)
            } catch (e: Exception) {
                progressBar.visibility = View.GONE
                Toast.makeText(this@PasskeySetupActivity, "Incorrect password", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun enrollPasskey(email: String, password: String) {
        if (!BiometricHelper.isAvailable(this)) {
            Toast.makeText(this, "Biometric authentication is not available on this device", Toast.LENGTH_LONG).show()
            return
        }

        progressBar.visibility = View.VISIBLE

        BiometricHelper.promptToSaveCredentials(
            activity = this,
            email = email,
            password = password,
            onSaved = {
                BiometricHelper.saveCredentials(this, email, password)
                progressBar.visibility = View.GONE
                Toast.makeText(this, "Passkey created successfully!", Toast.LENGTH_SHORT).show()
                setResult(RESULT_OK)
                finish()
            },
            onCancelled = {
                progressBar.visibility = View.GONE
                Toast.makeText(this, "Passkey setup cancelled", Toast.LENGTH_SHORT).show()
            }
        )
    }
}
