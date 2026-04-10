package com.waray.spendhound

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import io.github.jan.supabase.gotrue.Auth
import io.github.jan.supabase.gotrue.providers.builtin.Email
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class LoginActivity : AppCompatActivity() {

    private var usernameEditText: EditText? = null
    private var passwordEditText: EditText? = null
    private var mAuth: Auth? = null
    private var progressBar: ProgressBar? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        mAuth = DeclareDatabase.auth
        usernameEditText = findViewById(R.id.usernameEditText)
        passwordEditText = findViewById(R.id.passwordEditText)
        progressBar = findViewById(R.id.progressBar)

        val loginButton = findViewById<Button>(R.id.loginButton)
        val biometricButton = findViewById<Button>(R.id.biometricLoginButton)
        val forgotPasswordText = findViewById<TextView>(R.id.forgotPasswordText)

        if (BiometricHelper.isAvailable(this) && BiometricHelper.hasStoredCredentials(this)) {
            biometricButton.visibility = View.VISIBLE
        }

        loginButton.isEnabled = false
        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                loginButton.isEnabled = usernameEditText?.text?.isNotBlank() == true &&
                        passwordEditText?.text?.isNotBlank() == true
            }
        }
        usernameEditText?.addTextChangedListener(watcher)
        passwordEditText?.addTextChangedListener(watcher)

        loginButton.setOnClickListener {
            val email = usernameEditText?.text.toString().trim()
            val password = passwordEditText?.text.toString().trim()
            performLogin(email, password, saveForBiometric = true)
        }

        biometricButton.setOnClickListener {
            BiometricHelper.promptToGetCredentials(
                activity = this,
                title = "Login to SpendHound",
                subtitle = "Use your fingerprint to log in",
                onSuccess = { email, password -> performLogin(email, password, saveForBiometric = false) }
            )
        }

        forgotPasswordText.setOnClickListener { showForgotPasswordDialog() }

        findViewById<TextView>(R.id.signUpHere).setOnClickListener {
            startActivity(Intent(this, SignUpActivity::class.java))
        }
    }

    private fun performLogin(email: String, password: String, saveForBiometric: Boolean) {
        progressBar?.visibility = View.VISIBLE
        lifecycleScope.launch {
            try {
                mAuth?.signInWith(Email) {
                    this.email = email
                    this.password = password
                }
                if (saveForBiometric && BiometricHelper.isAvailable(this@LoginActivity)) {
                    BiometricHelper.saveCredentials(this@LoginActivity, email, password)
                }
                progressBar?.visibility = View.GONE
                startActivity(Intent(this@LoginActivity, MainActivity::class.java))
                finish()
            } catch (e: Exception) {
                progressBar?.visibility = View.GONE
                Toast.makeText(this@LoginActivity, "Invalid email or password", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showForgotPasswordDialog() {
        if (!BiometricHelper.isAvailable(this) || !BiometricHelper.hasStoredCredentials(this)) {
            Toast.makeText(this, "No biometric credentials saved. Please log in first to enable this feature.", Toast.LENGTH_LONG).show()
            return
        }
        BiometricHelper.promptForVerification(
            activity = this,
            title = "Verify Identity",
            subtitle = "Confirm it's you before resetting your password",
            onVerified = { showNewPasswordDialog() }
        )
    }

    private fun showNewPasswordDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_change_password, null)
        val etNew = dialogView.findViewById<TextInputEditText>(R.id.etNewPassword)
        val etConfirm = dialogView.findViewById<TextInputEditText>(R.id.etConfirmPassword)

        AlertDialog.Builder(this)
            .setTitle("Set New Password")
            .setView(dialogView)
            .setPositiveButton("Save") { _, _ ->
                val newPass = etNew.text.toString()
                val confirmPass = etConfirm.text.toString()
                if (newPass != confirmPass) {
                    Toast.makeText(this, "Passwords don't match", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                if (newPass.length < 6) {
                    Toast.makeText(this, "Password must be at least 6 characters", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                updatePassword(newPass)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun updatePassword(newPassword: String) {
        progressBar?.visibility = View.VISIBLE
        lifecycleScope.launch {
            try {
                val storedEmail = BiometricHelper.getStoredEmail(this@LoginActivity)
                    ?: throw Exception("No stored email")
                val storedPassword = BiometricHelper.getStoredPassword(this@LoginActivity)
                    ?: throw Exception("No stored password")

                mAuth?.signInWith(Email) {
                    email = storedEmail
                    password = storedPassword
                }
                withContext(Dispatchers.IO) {
                    mAuth?.updateUser { password = newPassword }
                    DeclareDatabase.usersTable.update(
                        buildJsonObject { put("password", SecurityUtils.hashPassword(newPassword)) }
                    ) { filter { eq("email", storedEmail) } }
                }
                BiometricHelper.saveCredentials(this@LoginActivity, storedEmail, newPassword)
                progressBar?.visibility = View.GONE
                Toast.makeText(this@LoginActivity, "Password updated successfully", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                progressBar?.visibility = View.GONE
                Toast.makeText(this@LoginActivity, "Failed to update password: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        if (mAuth?.currentSessionOrNull() != null) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN) {
            val v = currentFocus
            if (v is EditText) {
                val outRect = android.graphics.Rect()
                v.getGlobalVisibleRect(outRect)
                if (!outRect.contains(event.rawX.toInt(), event.rawY.toInt())) {
                    v.clearFocus()
                    val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                    imm.hideSoftInputFromWindow(v.windowToken, 0)
                }
            }
        }
        return super.dispatchTouchEvent(event)
    }
}
