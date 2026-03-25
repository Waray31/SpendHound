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
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
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
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class SignUpActivity : AppCompatActivity() {
    private var emailEditText: EditText? = null
    private var passwordEditText: EditText? = null
    private var confirmPasswordEditText: EditText? = null
    private var usernameEditText: EditText? = null
    private var signUpButton: Button? = null
    private var btnNextStep: Button? = null
    private var profileImageView: ImageView? = null
    private var progressBar: ProgressBar? = null
    private var mAuth: Auth? = null
    private var profileImageUri: Uri? = null
    private var userId: String? = null // Supabase Auth UID
    private var internalUserId: Long? = null // DB internal user_id
    
    private var layoutStep1: LinearLayout? = null
    private var layoutStep2: LinearLayout? = null
    private var tvSignUpTitle: TextView? = null

    private val tag = "SignUpActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.sign_up_layout)

        layoutStep1 = findViewById(R.id.layoutStep1)
        layoutStep2 = findViewById(R.id.layoutStep2)
        tvSignUpTitle = findViewById(R.id.tvSignUpTitle)
        
        usernameEditText = findViewById(R.id.usernameSignUp)
        emailEditText = findViewById(R.id.emailSignup)
        passwordEditText = findViewById(R.id.passwordSignup)
        confirmPasswordEditText = findViewById(R.id.confirmPasswordSignup)
        btnNextStep = findViewById(R.id.btnNextStep)
        signUpButton = findViewById(R.id.signUpButton)
        profileImageView = findViewById(R.id.profileImageView)
        progressBar = findViewById(R.id.progressBar)

        mAuth = DeclareDatabase.auth

        btnNextStep?.setOnClickListener {
            if (validateStep1()) {
                signUpStep1()
            }
        }

        exitEditText()
    }

    private fun validateStep1(): Boolean {
        val email = emailEditText?.text.toString().trim()
        val password = passwordEditText?.text.toString().trim()
        val confirmPassword = confirmPasswordEditText?.text.toString().trim()

        if (TextUtils.isEmpty(email) || TextUtils.isEmpty(password)) {
            Toast.makeText(this, "Please fill in all fields", Toast.LENGTH_SHORT).show()
            return false
        }
        if (password != confirmPassword) {
            Toast.makeText(this, "Passwords don't match", Toast.LENGTH_SHORT).show()
            return false
        }
        return true
    }

    private fun signUpStep1() {
        val email = emailEditText?.text.toString().trim()
        val password = passwordEditText?.text.toString().trim()

        progressBar?.visibility = View.VISIBLE
        btnNextStep?.isEnabled = false

        lifecycleScope.launch {
            try {
                Log.d(tag, "Starting Auth Sign Up (Step 1)...")
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
                        btnNextStep?.isEnabled = true
                        Toast.makeText(this@SignUpActivity, "Success! Check your email to confirm your account.", Toast.LENGTH_LONG).show()
                        finish()
                    } else {
                        val hashedPassword = SecurityUtils.hashPassword(password)
                        val tempUsername = email.substringBefore("@")
                        saveInitialUserToDatabase(tempUsername, email, "placeholder_profile_image", hashedPassword)
                    }
                } else {
                    throw Exception("Could not retrieve user ID from Supabase.")
                }
            } catch (e: Exception) {
                Log.e(tag, "Step 1 Sign Up Error: ${e.message}")
                progressBar?.visibility = View.GONE
                btnNextStep?.isEnabled = true
                Toast.makeText(this@SignUpActivity, "Sign up failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private suspend fun saveInitialUserToDatabase(username: String, email: String, profileImageUrl: String, hashedPass: String) {
        try {
            Log.d(tag, "Inserting initial user data into Database...")
            
            val userData = buildJsonObject {
                put("auth_id", userId)
                put("username", username)
                put("email", email)
                put("password", hashedPass)
                put("profile_image_url", profileImageUrl)
            }

            withContext(Dispatchers.IO) {
                val createdUser = DeclareDatabase.usersTable.insert(userData) {
                    select(Columns.list("user_id"))
                }.decodeSingle<User>()
                
                internalUserId = createdUser.id
                Log.d(tag, "Initial user created. Internal ID: $internalUserId")

                val initialBalanceData = buildJsonObject {
                    put("user_id", internalUserId ?: 0L)
                    put("unpaid_total_group", 0.0)
                    put("unpaid_total_individual", 0.0)
                    put("receivable_total_group", 0.0)
                    put("receivable_total_individual", 0.0)
                    put("balance_total_group", 0.0)
                    put("balance_total_individual", 0.0)
                }
                DeclareDatabase.userBalanceTable.insert(initialBalanceData)
                
                internalUserId?.let { UserHelper.updateCache(it, username) }
            }
            
            withContext(Dispatchers.Main) {
                progressBar?.visibility = View.GONE
                showStep2(username)
            }
        } catch (e: Exception) {
            Log.e(tag, "Database Error in Step 1: ${e.message}")
            withContext(Dispatchers.Main) {
                progressBar?.visibility = View.GONE
                btnNextStep?.isEnabled = true
                Toast.makeText(this@SignUpActivity, "Initial database save failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun showStep2(tempUsername: String) {
        layoutStep1?.visibility = View.GONE
        layoutStep2?.visibility = View.VISIBLE
        tvSignUpTitle?.text = "Complete Your Profile"
        usernameEditText?.setText(tempUsername)
    }

    fun onAddProfileImageClicked(view: View?) {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        startActivityForResult(intent, PICK_IMAGE_REQUEST)
    }

    fun onSignUpClicked(view: View?) {
        completeSignUp()
    }

    private fun completeSignUp() {
        val username = usernameEditText?.text.toString().trim()

        if (TextUtils.isEmpty(username)) {
            Toast.makeText(this, "Please enter a username", Toast.LENGTH_SHORT).show()
            return
        }

        progressBar?.visibility = View.VISIBLE
        signUpButton?.isEnabled = false

        lifecycleScope.launch {
            try {
                // Check if the NEW username already exists (excluding current user)
                val existingUser = withContext(Dispatchers.IO) {
                    DeclareDatabase.usersTable.select(Columns.list("user_id")) {
                        filter { 
                            eq("username", username)
                            neq("user_id", internalUserId ?: -1L)
                        }
                    }.decodeSingleOrNull<User>()
                }
                
                if (existingUser != null) {
                    progressBar?.visibility = View.GONE
                    signUpButton?.isEnabled = true
                    Toast.makeText(this@SignUpActivity, "Username already taken", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                var finalProfileUrl = "placeholder_profile_image"
                if (profileImageUri != null) {
                    finalProfileUrl = uploadProfileImageAndGetUrl(userId!!) ?: "placeholder_profile_image"
                }

                updateUserInDatabase(username, finalProfileUrl)
                
                withContext(Dispatchers.Main) {
                    progressBar?.visibility = View.GONE
                    signUpSuccess()
                }
            } catch (e: Exception) {
                Log.e(tag, "Update Error (Step 2): ${e.message}")
                progressBar?.visibility = View.GONE
                signUpButton?.isEnabled = true
                Toast.makeText(this@SignUpActivity, "Update failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private suspend fun uploadProfileImageAndGetUrl(authUid: String): String? {
        return try {
            val bytes = withContext(Dispatchers.IO) {
                contentResolver.openInputStream(profileImageUri!!)?.use { it.readBytes() }
            }
            
            if (bytes != null) {
                val bucket = DeclareDatabase.profileImagesBucket
                val path = "$authUid.jpg"
                Log.d(tag, "Uploading to Storage: $path")
                
                bucket.upload(path, bytes, upsert = true)
                val publicUrl = bucket.publicUrl(path)
                Log.d(tag, "Upload success. URL: $publicUrl")
                publicUrl
            } else null
        } catch (e: Exception) {
            Log.e(tag, "Storage Error: ${e.message}")
            null
        }
    }

    private suspend fun updateUserInDatabase(username: String, profileImageUrl: String) {
        try {
            Log.d(tag, "Updating user in Database...")
            val updateData = buildJsonObject {
                put("username", username)
                put("profile_image_url", profileImageUrl)
            }

            withContext(Dispatchers.IO) {
                DeclareDatabase.usersTable.update(updateData) {
                    filter { eq("user_id", internalUserId ?: -1L) }
                }
                
                // Update local UserHelper cache
                internalUserId?.let { UserHelper.updateCache(it, username) }
            }
        } catch (e: Exception) {
            throw e
        }
    }

    fun onSignInClicked(view: View?) {
        if (layoutStep2?.visibility == View.VISIBLE) {
            layoutStep2?.visibility = View.GONE
            layoutStep1?.visibility = View.VISIBLE
            tvSignUpTitle?.text = "Create Account"
        } else {
            finish()
        }
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
