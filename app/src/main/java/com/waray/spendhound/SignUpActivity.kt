package com.waray.spendhound

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
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
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import coil.load
import coil.transform.CircleCropTransformation
import io.github.jan.supabase.gotrue.Auth
import io.github.jan.supabase.gotrue.providers.builtin.Email
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.storage.storage
import com.waray.spendhound.utils.ImageUtils
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
    private var loadingOverlay: View? = null
    private var ivBack: ImageView? = null
    private var layoutFooter: LinearLayout? = null
    private var mAuth: Auth? = null
    private var profileImageUri: Uri? = null
    private var userId: String? = null // Supabase Auth UID
    private var internalUserId: Long? = null // DB internal user_id
    
    private var layoutStep1: LinearLayout? = null
    private var layoutStep2: LinearLayout? = null
    private var layoutStep3: LinearLayout? = null
    private var tvSignUpTitle: TextView? = null
    private var pendingEmail: String = ""
    private var pendingPassword: String = ""

    private val tag = "SignUpActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_signup)

        layoutStep1 = findViewById(R.id.layoutStep1)
        layoutStep2 = findViewById(R.id.layoutStep2)
        layoutStep3 = findViewById(R.id.layoutStep3)
        tvSignUpTitle = findViewById(R.id.tvSignUpTitle)
        ivBack = findViewById(R.id.ivBack)
        layoutFooter = findViewById(R.id.layoutFooter)
        
        usernameEditText = findViewById(R.id.usernameSignUp)
        emailEditText = findViewById(R.id.emailSignup)
        passwordEditText = findViewById(R.id.passwordSignup)
        confirmPasswordEditText = findViewById(R.id.confirmPasswordSignup)
        btnNextStep = findViewById(R.id.btnNextStep)
        signUpButton = findViewById(R.id.signUpButton)
        profileImageView = findViewById(R.id.profileImageView)
        progressBar = findViewById(R.id.progressBar)
        loadingOverlay = findViewById(R.id.loadingOverlay)

        mAuth = DeclareDatabase.auth

        btnNextStep?.isEnabled = false
        val step1Watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                btnNextStep?.isEnabled = emailEditText?.text?.isNotBlank() == true &&
                        passwordEditText?.text?.isNotBlank() == true &&
                        confirmPasswordEditText?.text?.isNotBlank() == true
            }
        }
        emailEditText?.addTextChangedListener(step1Watcher)
        passwordEditText?.addTextChangedListener(step1Watcher)
        confirmPasswordEditText?.addTextChangedListener(step1Watcher)

        signUpButton?.isEnabled = false
        usernameEditText?.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                signUpButton?.isEnabled = s?.isNotBlank() == true
            }
        })

        btnNextStep?.setOnClickListener {
            if (validateStep1()) {
                signUpStep1()
            }
        }

        // Handle back button behavior
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (layoutStep2?.visibility == View.VISIBLE || layoutStep3?.visibility == View.VISIBLE) {
                    // Do nothing - back is disabled after Step 1
                } else {
                    finish()
                }
            }
        })
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
        
        pendingEmail = email
        pendingPassword = password

        progressBar?.visibility = View.VISIBLE
        loadingOverlay?.visibility = View.VISIBLE
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
                        loadingOverlay?.visibility = View.GONE
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
                loadingOverlay?.visibility = View.GONE
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
                put("user_type", 1) // 1 = registered user
            }

            withContext(Dispatchers.IO) {
                // Use insert for initial save to avoid needing UPDATE permissions for upsert
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
                loadingOverlay?.visibility = View.GONE
                showStep2(username)
            }
        } catch (e: Exception) {
            Log.e(tag, "Database Error in Step 1: ${e.message}", e)
            withContext(Dispatchers.Main) {
                progressBar?.visibility = View.GONE
                loadingOverlay?.visibility = View.GONE
                btnNextStep?.isEnabled = true
                Toast.makeText(this@SignUpActivity, "Initial database save failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun showStep2(tempUsername: String) {
        // Scroll to top
        findViewById<android.widget.ScrollView>(R.id.signUpScrollView)?.smoothScrollTo(0, 0)

        layoutStep1?.visibility = View.GONE
        layoutStep2?.visibility = View.VISIBLE
        ivBack?.visibility = View.GONE // Remove back button on Step 2
        layoutFooter?.visibility = View.GONE // Remove footer on Step 2
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
        loadingOverlay?.visibility = View.VISIBLE
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
                    loadingOverlay?.visibility = View.GONE
                    signUpButton?.isEnabled = true
                    Toast.makeText(this@SignUpActivity, "Username already taken", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                var finalProfileUrl = "placeholder_profile_image"
                val userId = internalUserId
                if (profileImageUri != null && userId != null) {
                    Log.d(tag, "Attempting to upload profile image for user: $userId")
                    finalProfileUrl = uploadProfileImageAndGetUrl(userId) ?: "placeholder_profile_image"
                    Log.d(tag, "Profile image upload result: $finalProfileUrl")
                } else {
                    Log.w(tag, "Skipping profile image upload - profileImageUri: ${profileImageUri != null}, internalUserId: $userId")
                }

                updateUserInDatabase(username, finalProfileUrl)
                
                // Mark Step 2 as completed in SharedPreferences
                userId?.let {
                    val prefs = getSharedPreferences("SpendHoundPrefs", Context.MODE_PRIVATE)
                    prefs.edit().putBoolean("step2_completed_$it", true).apply()
                }

                withContext(Dispatchers.Main) {
                    progressBar?.visibility = View.GONE
                    loadingOverlay?.visibility = View.GONE
                    signUpSuccess()
                }
            } catch (e: Exception) {
                Log.e(tag, "Update Error (Step 2): ${e.message}", e)
                progressBar?.visibility = View.GONE
                loadingOverlay?.visibility = View.GONE
                signUpButton?.isEnabled = true
                Toast.makeText(this@SignUpActivity, "Update failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private suspend fun uploadProfileImageAndGetUrl(userInternalId: Long): String? {
        Log.d(tag, "🔍 ===== STARTING PROFILE IMAGE UPLOAD DIAGNOSTICS =====")
        Log.d(tag, "🔍 User ID: $userInternalId")
        Log.d(tag, "🔍 Image URI: $profileImageUri")
        Log.d(tag, "🔍 Current timestamp: ${System.currentTimeMillis()}")

        return try {
            // Step 1: Validate inputs
            Log.d(tag, "🔍 Step 1: Validating inputs...")
            if (profileImageUri == null) {
                Log.e(tag, "❌ FAILED Step 1: profileImageUri is null")
                return null
            }
            Log.d(tag, "🔍 ✓ Step 1 Complete: Inputs validated")

            // Step 2: Read image bytes
            Log.d(tag, "🔍 Step 2: Reading image bytes from URI...")
            val bytes = withContext(Dispatchers.IO) {
                try {
                    val byteArray = ImageUtils.compressImage(contentResolver, profileImageUri!!)
                    if (byteArray == null) {
                        Log.e(tag, "❌ FAILED Step 2: Cannot compress image from URI: $profileImageUri")
                        return@withContext null
                    }
                    Log.d(tag, "🔍 ✓ Step 2 Complete: Compressed image (${byteArray.size} bytes)")
                    byteArray
                } catch (e: Exception) {
                    Log.e(tag, "❌ FAILED Step 2: Exception compressing image: ${e.message}", e)
                    throw e
                }
            }

            if (bytes == null || bytes.isEmpty()) {
                Log.e(tag, "❌ FAILED Step 2: Bytes array is null or empty")
                return null
            }
            Log.d(tag, "🔍 ✓ Step 2 Complete: Image bytes read (${bytes.size} bytes)")

            // Step 3: Get Supabase client
            Log.d(tag, "🔍 Step 3: Getting Supabase client...")
            val client = try {
                DeclareDatabase.client
            } catch (e: Exception) {
                Log.e(tag, "❌ FAILED Step 3: Cannot get Supabase client: ${e.message}", e)
                throw e
            }
            Log.d(tag, "🔍 ✓ Step 3a: Supabase client obtained")

            // Step 4: Get storage module
            Log.d(tag, "🔍 Step 4: Getting storage module...")
            val storage = try {
                client.storage
            } catch (e: Exception) {
                Log.e(tag, "❌ FAILED Step 4: Cannot get storage module: ${e.message}", e)
                throw e
            }
            Log.d(tag, "🔍 ✓ Step 4a: Storage module obtained")

            // Step 5: Get bucket reference
            Log.d(tag, "🔍 Step 5: Getting bucket reference...")
            val bucket = try {
                DeclareDatabase.profileImagesBucket
            } catch (e: Exception) {
                Log.e(tag, "❌ FAILED Step 5: Cannot get bucket reference: ${e.message}", e)
                throw e
            }
            Log.d(tag, "🔍 ✓ Step 5a: Bucket reference obtained")

            // Step 6: Prepare upload path
            val path = "$userInternalId/$userInternalId.jpg"
            Log.d(tag, "🔍 Step 6: Upload path prepared: $path")

            // Step 7: Upload to storage
            Log.d(tag, "🔍 Step 7: Starting upload to storage...")
            try {
                bucket.upload(path, bytes, upsert = true)
                Log.d(tag, "🔍 ✓ Step 7a: Upload API call completed")
            } catch (e: Exception) {
                Log.e(tag, "❌ FAILED Step 7: Upload failed: ${e.message}", e)
                e.printStackTrace()
                throw e
            }
            Log.d(tag, "🔍 ✓ Step 7 Complete: Upload completed for path: $path")

            // Step 8: Generate public URL
            Log.d(tag, "🔍 Step 8: Generating public URL...")
            val publicUrl = try {
                bucket.publicUrl(path)
            } catch (e: Exception) {
                Log.e(tag, "❌ FAILED Step 8: Cannot generate public URL: ${e.message}", e)
                throw e
            }

            Log.d(tag, "🔍 Generated URL: $publicUrl")

            // Step 9: Validate URL
            if (publicUrl.isNullOrEmpty()) {
                Log.e(tag, "❌ FAILED Step 9: Public URL is null or empty")
                return null
            }

            if (!publicUrl.startsWith("http")) {
                Log.e(tag, "❌ FAILED Step 9: Public URL is invalid (doesn't start with http): $publicUrl")
                return null
            }

            Log.d(tag, "🔍 ✓ Step 9 Complete: URL validation passed")
            Log.d(tag, "🔍 ===== UPLOAD SUCCESS =====")
            Log.d(tag, "🔍 Final URL: $publicUrl")

            publicUrl

        } catch (e: Exception) {
            Log.e(tag, "🔍 ===== UPLOAD FAILED =====")
            Log.e(tag, "🔍 Exception Type: ${e::class.simpleName}")
            Log.e(tag, "🔍 Exception Message: ${e.message}")
            Log.e(tag, "🔍 Full Stack Trace:", e)
            e.printStackTrace()
            null
        }
    }

    private suspend fun updateUserInDatabase(username: String, profileImageUrl: String) {
        try {
            Log.d(tag, "Updating user in Database for user ID: $internalUserId")
            Log.d(tag, "Setting username: $username, profileImageUrl: $profileImageUrl")
            
            val updateData = buildJsonObject {
                put("username", username)
                put("profile_image_url", profileImageUrl)
            }

            withContext(Dispatchers.IO) {
                DeclareDatabase.usersTable.update(updateData) {
                    filter { eq("user_id", internalUserId ?: -1L) }
                }
                
                Log.d(tag, "Database update completed. Verifying update...")
                
                // Verify the update was successful
                val updatedUser = DeclareDatabase.usersTable.select {
                    filter { eq("user_id", internalUserId ?: -1L) }
                }.decodeSingleOrNull<User>()
                
                if (updatedUser != null) {
                    Log.d(tag, "Verification successful - Username: ${updatedUser.username}, ProfileImageUrl: ${updatedUser.profileImageUrl}")
                } else {
                    Log.w(tag, "Could not verify updated user record")
                }
                
                // Update local UserHelper cache
                internalUserId?.let { UserHelper.updateCache(it, username) }
            }
        } catch (e: Exception) {
            Log.e(tag, "Error updating user in database: ${e.message}", e)
            e.printStackTrace()
            throw e
        }
    }

    fun onSignInClicked(view: View?) {
        if (layoutStep2?.visibility == View.VISIBLE) {
            // This button should actually be hidden/disabled if we are in Step 2 according to logic,
            // but just in case, we do nothing.
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
                it.load(profileImageUri) {
                    crossfade(true)
                    transformations(CircleCropTransformation())
                }
            }
        }
    }

    private fun signUpSuccess() {
        pendingEmail = emailEditText?.text.toString().trim()
        pendingPassword = passwordEditText?.text.toString().trim()
        showStep3()
    }

    private fun showStep3() {
        // Scroll to top when showing new step
        findViewById<android.widget.ScrollView>(R.id.signUpScrollView)?.smoothScrollTo(0, 0)
        
        layoutStep2?.visibility = View.GONE
        layoutStep3?.visibility = View.VISIBLE
        tvSignUpTitle?.text = "One Last Step"

        findViewById<View>(R.id.btnEnableBiometric).setOnClickListener {
            if (!BiometricHelper.isAvailable(this)) {
                Toast.makeText(this, "Fingerprint sensor not available or setup on this device.", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            BiometricHelper.promptToSaveCredentials(
                activity = this,
                email = pendingEmail,
                password = pendingPassword,
                onSaved = {
                    BiometricHelper.saveCredentials(this, pendingEmail, pendingPassword)
                    Toast.makeText(this, "Fingerprint login enabled!", Toast.LENGTH_SHORT).show()
                    goToMain()
                },
                onCancelled = { 
                    // If they cancelled the biometric prompt, don't force them out, 
                    // let them try again or skip.
                }
            )
        }

        findViewById<View>(R.id.btnSkipBiometric).setOnClickListener { goToMain() }
    }

    private fun goToMain() {
        Toast.makeText(this, "Welcome to SpendHound!", Toast.LENGTH_SHORT).show()
        startActivity(Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        })
        finish()
    }

    override fun dispatchTouchEvent(event: android.view.MotionEvent): Boolean {
        if (event.action == android.view.MotionEvent.ACTION_DOWN) {
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

    companion object {
        private const val PICK_IMAGE_REQUEST = 1
    }
}
