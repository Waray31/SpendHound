package com.waray.spendhound

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.text.TextUtils
import android.util.Log
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import io.github.jan.supabase.gotrue.Auth
import io.github.jan.supabase.gotrue.providers.builtin.Email
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SignUpActivity : AppCompatActivity() {
    private var emailEditText: EditText? = null
    private var passwordEditText: EditText? = null
    private var confirmPasswordEditText: EditText? = null
    private var usernameEditText: EditText? = null
    private var signUpButton: Button? = null
    private var profileImageView: ImageView? = null
    private var progressBar: ProgressBar? = null
    private var mAuth: Auth? = null
    private var profileImageUri: Uri? = null
    private var userId: String? = null

    private val tag = "SignUpActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.sign_up_layout)

        usernameEditText = findViewById(R.id.usernameSignUp)
        emailEditText = findViewById(R.id.emailSignup)
        passwordEditText = findViewById(R.id.passwordSignup)
        confirmPasswordEditText = findViewById(R.id.confirmPasswordSignup)
        signUpButton = findViewById(R.id.signUpButton)
        profileImageView = findViewById(R.id.profileImageView)
        progressBar = findViewById(R.id.progressBar)

        mAuth = DeclareDatabase.auth

        exitEditText()
    }

    fun onAddProfileImageClicked(view: View?) {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        startActivityForResult(intent, PICK_IMAGE_REQUEST)
    }

    fun onSignUpClicked(view: View?) {
        signUp()
    }

    fun onSignInClicked(view: View?) {
        finish()
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICK_IMAGE_REQUEST && resultCode == RESULT_OK && data != null && data.data != null) {
            profileImageUri = data.data
            profileImageView?.let {
                Glide.with(this)
                    .load(profileImageUri)
                    .centerCrop()
                    .into(it)
            }
        }
    }

    private fun signUp() {
        val username = usernameEditText?.text.toString().trim()
        val email = emailEditText?.text.toString().trim()
        val password = passwordEditText?.text.toString().trim()
        val confirmPassword = confirmPasswordEditText?.text.toString().trim()

        if (TextUtils.isEmpty(username) || TextUtils.isEmpty(password) || TextUtils.isEmpty(email)) {
            Toast.makeText(this, "Please fill in all fields", Toast.LENGTH_SHORT).show()
            return
        }
        if (password != confirmPassword) {
            Toast.makeText(this, "Passwords don't match", Toast.LENGTH_SHORT).show()
            return
        }

        progressBar?.visibility = View.VISIBLE
        signUpButton?.isEnabled = false

        lifecycleScope.launch {
            try {
                Log.d(tag, "Starting Auth Sign Up...")
                mAuth?.signUpWith(Email) {
                    this.email = email
                    this.password = password
                }
                
                val session = mAuth?.currentSessionOrNull()
                userId = mAuth?.currentUserOrNull()?.id
                Log.d(tag, "Auth success. UserID: $userId, Session active: ${session != null}")

                if (userId != null) {
                    if (session == null) {
                        progressBar?.visibility = View.GONE
                        signUpButton?.isEnabled = true
                        Toast.makeText(this@SignUpActivity, "Success! Check your email to confirm your account.", Toast.LENGTH_LONG).show()
                        finish()
                    } else {
                        if (profileImageUri != null) {
                            uploadProfileImage(userId!!, username, email, password)
                        } else {
                            saveUserToDatabase(username, email, "placeholder_profile_image", password)
                        }
                    }
                } else {
                    throw Exception("Could not retrieve user ID from Supabase.")
                }
            } catch (e: Exception) {
                Log.e(tag, "Sign Up Error: ${e.message}")
                progressBar?.visibility = View.GONE
                signUpButton?.isEnabled = true
                Toast.makeText(this@SignUpActivity, "Sign up failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private suspend fun uploadProfileImage(userId: String, username: String, email: String, pass: String) {
        try {
            val bytes = withContext(Dispatchers.IO) {
                contentResolver.openInputStream(profileImageUri!!)?.use { it.readBytes() }
            }
            
            if (bytes != null) {
                val bucket = DeclareDatabase.profileImagesBucket
                val path = "$userId.jpg"
                Log.d(tag, "Uploading to Storage: $path")
                
                bucket.upload(path, bytes, upsert = true)
                val publicUrl = bucket.publicUrl(path)
                Log.d(tag, "Upload success. URL: $publicUrl")
                
                saveUserToDatabase(username, email, publicUrl, pass)
            }
        } catch (e: Exception) {
            Log.e(tag, "Storage Error: ${e.message}")
            withContext(Dispatchers.Main) {
                progressBar?.visibility = View.GONE
                signUpButton?.isEnabled = true
                Toast.makeText(this@SignUpActivity, "Image upload failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private suspend fun saveUserToDatabase(username: String, email: String, profileImageUrl: String, pass: String) {
        try {
            Log.d(tag, "Inserting into Database 'users' table...")
            
            val userData = mapOf(
                "auth_id" to userId,
                "username" to username,
                "email" to email,
                "password" to pass,
                "profile_image_url" to profileImageUrl
            )

            withContext(Dispatchers.IO) {
                // Using insert instead of upsert to avoid constraint errors
                val createdUser = DeclareDatabase.usersTable.insert(userData) {
                    select(Columns.list("user_id"))
                }.decodeSingle<User>()
                
                Log.d(tag, "User table entry created. Internal ID: ${createdUser.id}")

                // Initialize balance row
                val initialBalance = UserBalance(userId = createdUser.id)
                DeclareDatabase.userBalanceTable.insert(initialBalance)
                
                // Update local UserHelper cache
                createdUser.id?.let { UserHelper.updateCache(it, username) }
            }
            
            withContext(Dispatchers.Main) {
                progressBar?.visibility = View.GONE
                signUpSuccess()
            }
        } catch (e: Exception) {
            Log.e(tag, "Database Table Error: ${e.message}")
            withContext(Dispatchers.Main) {
                progressBar?.visibility = View.GONE
                signUpButton?.isEnabled = true
                Toast.makeText(this@SignUpActivity, "Database failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun signUpSuccess() {
        Toast.makeText(this, "Welcome to SpendHound!", Toast.LENGTH_SHORT).show()
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    @SuppressLint("ClickableViewAccessibility")
    fun exitEditText() {
        val rootView = findViewById<View>(android.R.id.content)
        rootView.setOnTouchListener { _, _ ->
            hideKeyboard(usernameEditText)
            hideKeyboard(emailEditText)
            hideKeyboard(passwordEditText)
            hideKeyboard(confirmPasswordEditText)
            false
        }
    }

    private fun hideKeyboard(editText: EditText?) {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(editText?.windowToken, 0)
    }

    companion object {
        private const val PICK_IMAGE_REQUEST = 1
    }
}
