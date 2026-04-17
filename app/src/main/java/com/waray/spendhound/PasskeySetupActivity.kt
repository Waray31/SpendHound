package com.waray.spendhound

import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import io.github.jan.supabase.gotrue.providers.builtin.Email
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PasskeySetupActivity : AppCompatActivity() {

    private lateinit var loadingOverlay_changeKey: LinearLayout
    private lateinit var btnCreatePasskey: MaterialButton
    private lateinit var btnEditPasskey: MaterialButton
    private lateinit var btnRemovePasskey: MaterialButton
    private lateinit var tvPasskeyTitle: TextView
    private lateinit var tvPasskeyActiveBadge: TextView

    private var userEmail: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_passkey_setup)

        userEmail = intent.getStringExtra(EditProfileActivity.EXTRA_USER_EMAIL)
        loadingOverlay_changeKey = findViewById(R.id.loadingOverlay_changeKey)
        btnCreatePasskey = findViewById(R.id.btnCreatePasskey)
        btnEditPasskey = findViewById(R.id.btnEditPasskey)
        btnRemovePasskey = findViewById(R.id.btnRemovePasskey)
        tvPasskeyTitle = findViewById(R.id.tvPasskeyTitle)
        tvPasskeyActiveBadge = findViewById(R.id.tvPasskeyActiveBadge)

        findViewById<ImageButton>(R.id.btnClose).setOnClickListener { finish() }
        findViewById<TextView>(R.id.btnNotNow).setOnClickListener { finish() }

        btnCreatePasskey.setOnClickListener { showPasswordConfirmThenEnroll() }
        btnEditPasskey.setOnClickListener { showPasswordConfirmThenEnroll() }
        btnRemovePasskey.setOnClickListener { confirmRemovePasskey() }

        updateUI()
    }

    private fun updateUI() {
        val hasPasskey = BiometricHelper.hasStoredCredentials(this)
        if (hasPasskey) {
            tvPasskeyTitle.text = "Your passkey is active"
            tvPasskeyActiveBadge.visibility = View.VISIBLE
            btnCreatePasskey.visibility = View.GONE
            btnEditPasskey.visibility = View.VISIBLE
            btnRemovePasskey.visibility = View.VISIBLE
        } else {
            tvPasskeyTitle.text = "Next time, skip your password"
            tvPasskeyActiveBadge.visibility = View.GONE
            btnCreatePasskey.visibility = View.VISIBLE
            btnEditPasskey.visibility = View.GONE
            btnRemovePasskey.visibility = View.GONE
        }
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
        loadingOverlay_changeKey.visibility = View.VISIBLE
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    DeclareDatabase.auth.signInWith(Email) {
                        this.email = email
                        this.password = enteredPassword
                    }
                }
                loadingOverlay_changeKey.visibility = View.GONE
                enrollPasskey(email, enteredPassword)
            } catch (e: Exception) {
                loadingOverlay_changeKey.visibility = View.GONE
                Toast.makeText(this@PasskeySetupActivity, "Incorrect password", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun enrollPasskey(email: String, password: String) {
        if (!BiometricHelper.isAvailable(this)) {
            loadingOverlay_changeKey.visibility = View.GONE
            Toast.makeText(this, "Biometric authentication is not available on this device", Toast.LENGTH_LONG).show()
            return
        }

        // Clear old credentials first so a fresh key is generated
        BiometricHelper.clearCredentials(this)

        BiometricHelper.promptToSaveCredentials(
            activity = this,
            email = email,
            password = password,
            onSaved = {
                BiometricHelper.saveCredentials(this, email, password)
                loadingOverlay_changeKey.visibility = View.GONE
                val msg = if (BiometricHelper.hasStoredCredentials(this)) "Passkey updated!" else "Passkey created!"
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                setResult(RESULT_OK)
                updateUI()
            },
            onCancelled = {
                loadingOverlay_changeKey.visibility = View.GONE
                Toast.makeText(this, "Passkey setup cancelled", Toast.LENGTH_SHORT).show()
            }
        )
    }

    private fun confirmRemovePasskey() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_unlock_email, null)
        val etInput = dialogView.findViewById<TextInputEditText>(R.id.etUnlockEmailPassword)

        AlertDialog.Builder(this)
            .setTitle("Remove Passkey")
            .setView(dialogView)
            .setPositiveButton("Remove") { _, _ ->
                val entered = etInput.text.toString()
                if (entered.isEmpty()) {
                    Toast.makeText(this, "Password cannot be empty", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                verifyThenRemove(entered)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun verifyThenRemove(enteredPassword: String) {
        val email = userEmail ?: run {
            Toast.makeText(this, "User email not found", Toast.LENGTH_SHORT).show()
            return
        }
        loadingOverlay_changeKey.visibility = View.VISIBLE
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    DeclareDatabase.auth.signInWith(Email) {
                        this.email = email
                        this.password = enteredPassword
                    }
                }
                BiometricHelper.clearCredentials(this@PasskeySetupActivity)
                loadingOverlay_changeKey.visibility = View.GONE
                Toast.makeText(this@PasskeySetupActivity, "Passkey removed", Toast.LENGTH_SHORT).show()
                setResult(RESULT_OK)
                updateUI()
            } catch (e: Exception) {
                loadingOverlay_changeKey.visibility = View.GONE
                Toast.makeText(this@PasskeySetupActivity, "Incorrect password", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
