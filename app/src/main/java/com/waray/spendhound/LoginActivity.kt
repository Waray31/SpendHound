package com.waray.spendhound

import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.CheckBox
import android.widget.CompoundButton
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import io.github.jan.supabase.gotrue.Auth
import io.github.jan.supabase.gotrue.providers.builtin.Email
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {
    private var usernameEditText: EditText? = null
    private var passwordEditText: EditText? = null
    private var mAuth: Auth? = null
    private var rememberMeCheckbox: CheckBox? = null
    private var progressBar: ProgressBar? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        mAuth = DeclareDatabase.auth

        usernameEditText = findViewById<EditText>(R.id.usernameEditText)
        passwordEditText = findViewById<EditText>(R.id.passwordEditText)
        val loginButton = findViewById<Button>(R.id.loginButton)
        progressBar = findViewById<ProgressBar>(R.id.progressBar)

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
            progressBar?.visibility = View.VISIBLE
            val username = usernameEditText?.text.toString().trim()
            val password = passwordEditText?.text.toString().trim()

            if (username.isEmpty() || password.isEmpty()) {
                Toast.makeText(
                    this@LoginActivity,
                    "Please enter both username and password",
                    Toast.LENGTH_SHORT
                ).show()
                progressBar?.visibility = View.GONE
            } else {
                lifecycleScope.launch {
                    try {
                        mAuth?.signInWith(Email) {
                            email = username
                            this.password = password
                        }
                        progressBar?.visibility = View.GONE
                        Toast.makeText(
                            this@LoginActivity,
                            "Login successful",
                            Toast.LENGTH_SHORT
                        ).show()
                        // Transition to MainActivity
                        val intent = Intent(this@LoginActivity, MainActivity::class.java)
                        startActivity(intent)
                        finish()
                    } catch (e: Exception) {
                        Toast.makeText(
                            this@LoginActivity,
                            "Invalid username or password: ${e.message}",
                            Toast.LENGTH_SHORT
                        ).show()
                        progressBar?.visibility = View.GONE
                    }
                }
            }
        }

        val signUpHereText: TextView = findViewById(R.id.signUpHere)
        signUpHereText.setOnClickListener {
            signUpHere()
        }

        rememberMeCheckbox?.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                Toast.makeText(this@LoginActivity, "Remember account", Toast.LENGTH_SHORT)
                    .show()
            } else {
                Toast.makeText(
                    this@LoginActivity,
                    "Do not remember account",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun signUpHere() {
        // Handle "Sign up here" click event
        Toast.makeText(this, "Please Sign Up", Toast.LENGTH_SHORT).show()
        val intent = Intent(this@LoginActivity, SignUpActivity::class.java)
        startActivity(intent)
    }

    override fun onStart() {
        super.onStart()
        val currentSession = mAuth?.currentSessionOrNull()
        if (currentSession != null) {
            // User is already signed in, redirect to MainActivity
            startActivity(Intent(this@LoginActivity, MainActivity::class.java))
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
