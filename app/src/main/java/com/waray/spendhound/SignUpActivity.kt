package com.waray.spendhound

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.text.TextUtils
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import io.github.jan.supabase.gotrue.Auth
import io.github.jan.supabase.gotrue.providers.builtin.Email
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

class SignUpActivity : AppCompatActivity() {
    private var emailEditText: EditText? = null
    private var passwordEditText: EditText? = null
    private var confirmPasswordEditText: EditText? = null
    private var usernameEditText: EditText? = null
    private var signUpButton: Button? = null
    private var progressBar: ProgressBar? = null
    private var mAuth: Auth? = null
    private var profileImageUri: Uri? = null
    private var userId: String? = null
    private val balanced = 0
    private val unpaid = 0
    private val owed = 0
    private val debt = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.sign_up_layout)

        usernameEditText = findViewById(R.id.usernameSignUp)
        emailEditText = findViewById(R.id.emailSignup)
        passwordEditText = findViewById(R.id.passwordSignup)
        confirmPasswordEditText = findViewById(R.id.confirmPasswordSignup)
        signUpButton = findViewById(R.id.signUpButton)
        progressBar = findViewById(R.id.progressBar)

        mAuth = DeclareDatabase.auth

        exitEditText()

        signUpButton?.setOnClickListener {
            signUp()
        }
    }

    fun onAddProfileImageClicked(view: View?) {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        startActivityForResult(intent, PICK_IMAGE_REQUEST)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICK_IMAGE_REQUEST && resultCode == RESULT_OK && data != null && data.data != null) {
            profileImageUri = data.data
            // Optional: Update UI to show selected image
            Toast.makeText(this, "Image selected", Toast.LENGTH_SHORT).show()
        }
    }

    private fun signUp() {
        val username = usernameEditText?.text.toString().trim()
        val email = emailEditText?.text.toString().trim()
        val password = passwordEditText?.text.toString().trim()
        val confirmPassword = confirmPasswordEditText?.text.toString().trim()

        if (TextUtils.isEmpty(username) || TextUtils.isEmpty(password) || TextUtils.isEmpty(
                confirmPassword
            ) || TextUtils.isEmpty(email)
        ) {
            Toast.makeText(this, "Please fill in all fields", Toast.LENGTH_SHORT).show()
        } else if (password != confirmPassword) {
            Toast.makeText(this, "Passwords don't match", Toast.LENGTH_SHORT).show()
        } else if (password.length < 6) {
            Toast.makeText(this, "Password must be at least 6 characters long", Toast.LENGTH_SHORT)
                .show()
        } else {
            progressBar?.visibility = View.VISIBLE

            lifecycleScope.launch {
                try {
                    val response = mAuth?.signUpWith(Email) {
                        this.email = email
                        this.password = password
                    }
                    userId = mAuth?.currentUserOrNull()?.id

                    if (userId != null) {
                        if (profileImageUri != null) {
                            uploadProfileImage(userId!!)
                        } else {
                            val profileImageUrl = "placeholder_profile_image" // Use a default or empty
                            saveUserToDatabase(
                                username,
                                email,
                                profileImageUrl,
                                password
                            )
                        }
                    } else {
                        throw Exception("Failed to get user ID")
                    }
                } catch (e: Exception) {
                    progressBar?.visibility = View.GONE
                    Toast.makeText(
                        this@SignUpActivity,
                        "Sign up failed: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private suspend fun uploadProfileImage(userId: String) {
        try {
            val bytes = withContext(Dispatchers.IO) {
                contentResolver.openInputStream(profileImageUri!!)?.use { it.readBytes() }
            }
            
            if (bytes != null) {
                val bucket = DeclareDatabase.profileImagesBucket
                val path = "$userId.jpg"
                bucket.upload(path, bytes, upsert = true)
                val publicUrl = bucket.publicUrl(path)
                
                saveUserToDatabase(
                    usernameEditText?.text.toString().trim(),
                    emailEditText?.text.toString().trim(),
                    publicUrl,
                    passwordEditText?.text.toString().trim()
                )
            } else {
                throw Exception("Could not read image bytes")
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                progressBar?.visibility = View.GONE
                Toast.makeText(this@SignUpActivity, "Image upload failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private suspend fun saveUserToDatabase(
        username: String,
        email: String,
        profileImageUrl: String,
        password: String
    ) {
        try {
            val initialBalance = UserBalance(0.0, 0.0, 0.0, 0.0, 0.0)
            val user = User(username, email, profileImageUrl, initialBalance, userId)

            DeclareDatabase.usersTable.insert(user)
            
            withContext(Dispatchers.Main) {
                progressBar?.visibility = View.GONE
                signUpSuccess()
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                progressBar?.visibility = View.GONE
                Toast.makeText(this@SignUpActivity, "Failed to save user: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun signUpSuccess() {
        Toast.makeText(this@SignUpActivity, "Sign up successful", Toast.LENGTH_SHORT).show()
        val intent = Intent(this@SignUpActivity, MainActivity::class.java)
        startActivity(intent)
        finish()
    }

    @SuppressLint("ClickableViewAccessibility")
    fun exitEditText() {
        val usernameSignUp: EditText = findViewById(R.id.usernameSignUp)
        val emailSignup: EditText = findViewById(R.id.emailSignup)
        val passwordSignup: EditText = findViewById(R.id.passwordSignup)
        val confirmPasswordSignup: EditText = findViewById(R.id.confirmPasswordSignup)
        
        val touchListener = View.OnTouchListener { v, _ ->
            v.performClick()
            false
        }

        usernameSignUp.setOnTouchListener(touchListener)
        emailSignup.setOnTouchListener(touchListener)
        passwordSignup.setOnTouchListener(touchListener)
        confirmPasswordSignup.setOnTouchListener(touchListener)

        val rootView = findViewById<View>(android.R.id.content)
        rootView.setOnTouchListener { _, _ ->
            hideKeyboard(usernameEditText)
            hideKeyboard(passwordEditText)
            false
        }
    }

    private fun hideKeyboard(editText: EditText?) {
        editText?.let {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(it.windowToken, 0)
        }
    }

    companion object {
        private const val PICK_IMAGE_REQUEST = 1
    }
}
