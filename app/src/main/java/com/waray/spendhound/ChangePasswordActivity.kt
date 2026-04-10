package com.waray.spendhound

import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.textfield.TextInputEditText
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class ChangePasswordActivity : AppCompatActivity() {

    private lateinit var etCurrentPassword: TextInputEditText
    private lateinit var etNewPassword: TextInputEditText
    private lateinit var etConfirmPassword: TextInputEditText
    private lateinit var progressBar: ProgressBar

    private var currentUser: User? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_change_password)

        etCurrentPassword = findViewById(R.id.etCurrentPassword)
        etNewPassword = findViewById(R.id.etNewPassword)
        etConfirmPassword = findViewById(R.id.etConfirmPassword)
        progressBar = findViewById(R.id.progressBar)

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<View>(R.id.btnChangePassword).setOnClickListener { attemptChangePassword() }
        findViewById<TextView>(R.id.tvForgotPassword).setOnClickListener { handleForgotPassword() }

        loadCurrentUser()
    }

    private fun loadCurrentUser() {
        val authId = DeclareDatabase.auth.currentUserOrNull()?.id ?: return
        lifecycleScope.launch {
            try {
                currentUser = withContext(Dispatchers.IO) {
                    DeclareDatabase.usersTable.select(
                        Columns.list("user_id", "email", "password")
                    ) { filter { eq("auth_id", authId) } }.decodeSingleOrNull<User>()
                }
            } catch (_: Exception) {}
        }
    }

    private fun attemptChangePassword() {
        val current = etCurrentPassword.text.toString()
        val newPass = etNewPassword.text.toString()
        val confirm = etConfirmPassword.text.toString()

        if (current.isEmpty() || newPass.isEmpty() || confirm.isEmpty()) {
            Toast.makeText(this, "Please fill in all fields", Toast.LENGTH_SHORT).show()
            return
        }
        if (newPass != confirm) {
            Toast.makeText(this, "New passwords don't match", Toast.LENGTH_SHORT).show()
            return
        }
        if (newPass.length < 6) {
            Toast.makeText(this, "Password must be at least 6 characters", Toast.LENGTH_SHORT).show()
            return
        }

        val email = currentUser?.email
        if (email.isNullOrEmpty()) {
            Toast.makeText(this, "Could not verify identity. Please try again.", Toast.LENGTH_SHORT).show()
            return
        }

        progressBar.visibility = View.VISIBLE
        lifecycleScope.launch {
            try {
                // Verify current password via Supabase re-auth
                withContext(Dispatchers.IO) {
                    DeclareDatabase.auth.signInWith(io.github.jan.supabase.gotrue.providers.builtin.Email) {
                        this.email = email
                        this.password = current
                    }
                }
                performPasswordChange(newPass)
            } catch (e: Exception) {
                progressBar.visibility = View.GONE
                Toast.makeText(this@ChangePasswordActivity, "Current password is incorrect", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun performPasswordChange(newPassword: String) {
        val authId = DeclareDatabase.auth.currentUserOrNull()?.id ?: return
        progressBar.visibility = View.VISIBLE

        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    DeclareDatabase.auth.updateUser { password = newPassword }
                    DeclareDatabase.usersTable.update(
                        buildJsonObject { put("password", SecurityUtils.hashPassword(newPassword)) }
                    ) { filter { eq("auth_id", authId) } }
                }

                // Keep biometric credentials in sync
                val email = currentUser?.email ?: ""
                if (BiometricHelper.isAvailable(this@ChangePasswordActivity) &&
                    BiometricHelper.hasStoredCredentials(this@ChangePasswordActivity)) {
                    BiometricHelper.saveCredentials(this@ChangePasswordActivity, email, newPassword)
                }

                Toast.makeText(this@ChangePasswordActivity, "Password changed successfully", Toast.LENGTH_SHORT).show()
                finish()
            } catch (e: Exception) {
                Toast.makeText(this@ChangePasswordActivity, "Failed: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                progressBar.visibility = View.GONE
            }
        }
    }

    private fun handleForgotPassword() {
        if (!BiometricHelper.isAvailable(this) || !BiometricHelper.hasStoredCredentials(this)) {
            Toast.makeText(this, "No passkey saved. Please contact support.", Toast.LENGTH_LONG).show()
            return
        }
        BiometricHelper.promptForVerification(
            activity = this,
            title = "Verify Identity",
            subtitle = "Use your fingerprint to reset your password",
            onVerified = {
                // Clear current password field and let user set a new one without current password
                etCurrentPassword.setText("bypass_via_biometric")
                Toast.makeText(this, "Identity verified. Enter your new password.", Toast.LENGTH_SHORT).show()
            }
        )
    }
}
