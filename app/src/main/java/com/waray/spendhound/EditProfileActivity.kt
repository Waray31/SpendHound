package com.waray.spendhound

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import io.github.jan.supabase.postgrest.query.Columns
import com.waray.spendhound.utils.ImageUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class EditProfileActivity : AppCompatActivity() {

    private lateinit var etNickname: TextInputEditText
    private lateinit var etEmail: TextInputEditText
    private lateinit var emailInputLayout: TextInputLayout
    private lateinit var editProfileImage: ImageView
    private lateinit var editProfileCardView: View
    private lateinit var profileSkeletonLayout: View
    private lateinit var loadingOverlay_editProfile: LinearLayout
    private lateinit var tvPasskeyAction: TextView
    private lateinit var tvPasskeyStatus: TextView

    private var currentUser: User? = null
    private var pendingImageUri: Uri? = null
    private var sensitiveFieldsUnlocked = false

    companion object {
        private const val PICK_IMAGE_REQUEST = 1
        const val EXTRA_USER_PASSWORD_HASH = "user_password_hash"
        const val EXTRA_USER_EMAIL = "user_email"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_profile)

        etNickname = findViewById(R.id.etNickname)
        etEmail = findViewById(R.id.etEmail)
        emailInputLayout = findViewById(R.id.emailInputLayout)
        editProfileImage = findViewById(R.id.editProfileImage)
        editProfileCardView = findViewById(R.id.editProfileCardView)
        profileSkeletonLayout = findViewById(R.id.profile_skeleton_layout)
        loadingOverlay_editProfile = findViewById(R.id.loadingOverlay_editProfile)
        tvPasskeyAction = findViewById(R.id.tvPasskeyAction)
        tvPasskeyStatus = findViewById(R.id.tvPasskeyStatus)

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<View>(R.id.changePhotoText).setOnClickListener { pickImage() }
        findViewById<View>(R.id.btnSaveProfile).setOnClickListener { showSaveConfirmation() }
        findViewById<View>(R.id.btnCancel).setOnClickListener { finish() }
        emailInputLayout.setEndIconOnClickListener { promptUnlockEmail() }

        findViewById<TextView>(R.id.tvChangePassword).setOnClickListener {
            startActivity(Intent(this, ChangePasswordActivity::class.java))
        }

        tvPasskeyAction.setOnClickListener {
            val intent = Intent(this, PasskeySetupActivity::class.java).apply {
                putExtra(EXTRA_USER_PASSWORD_HASH, currentUser?.password)
                putExtra(EXTRA_USER_EMAIL, currentUser?.email)
            }
            startActivity(intent)
        }

        loadCurrentUser()
    }

    override fun onResume() {
        super.onResume()
        updatePasskeyRow()
    }

    private fun updatePasskeyRow() {
        val hasPasskey = BiometricHelper.isAvailable(this) && BiometricHelper.hasStoredCredentials(this)
        tvPasskeyAction.text = if (hasPasskey) "Change Passkey" else "Add Passkey"
        tvPasskeyStatus.text = if (hasPasskey) "Active on this device" else "Not set up"
        if (!BiometricHelper.isAvailable(this)) {
            tvPasskeyAction.visibility = View.GONE
            tvPasskeyStatus.text = "Not available on this device"
        }
    }

    private fun loadCurrentUser() {
        val authId = DeclareDatabase.auth.currentUserOrNull()?.id ?: return
        profileSkeletonLayout.visibility = View.VISIBLE
        editProfileCardView.visibility = View.GONE
        loadingOverlay_editProfile.visibility = View.VISIBLE
        lifecycleScope.launch {
            try {
                currentUser = withContext(Dispatchers.IO) {
                    DeclareDatabase.usersTable.select(
                        Columns.list("user_id", "username", "email", "password", "profile_image_url")
                    ) { filter { eq("auth_id", authId) } }.decodeSingleOrNull<User>()
                }
                currentUser?.let {
                    etNickname.setText(it.username)
                    etEmail.setText(it.email)
                    
                    profileSkeletonLayout.visibility = View.GONE
                    editProfileCardView.visibility = View.VISIBLE

                    // Get CardView reference
                    val cardView = findViewById<androidx.cardview.widget.CardView>(R.id.editProfileCardView)
                    
                    if (!it.profileImageUrl.isNullOrEmpty() && it.profileImageUrl != "placeholder_profile_image") {
                        Glide.with(this@EditProfileActivity)
                            .load(it.profileImageUrl)
                            .centerCrop()
                            .listener(object : com.bumptech.glide.request.RequestListener<android.graphics.drawable.Drawable> {
                                override fun onLoadFailed(
                                    e: com.bumptech.glide.load.engine.GlideException?,
                                    model: Any?,
                                    target: com.bumptech.glide.request.target.Target<android.graphics.drawable.Drawable>?,
                                    isFirstResource: Boolean
                                ): Boolean {
                                    // Error loading image - remove tint, add padding, and set orange background
                                    editProfileImage.imageTintList = null
                                    editProfileImage.setPadding(4.dpToPx(), 4.dpToPx(), 4.dpToPx(), 4.dpToPx())
                                    cardView.setCardBackgroundColor(androidx.core.content.ContextCompat.getColor(this@EditProfileActivity, R.color.orange))
                                    return false
                                }
                                
                                override fun onResourceReady(
                                    resource: android.graphics.drawable.Drawable?,
                                    model: Any?,
                                    target: com.bumptech.glide.request.target.Target<android.graphics.drawable.Drawable>?,
                                    dataSource: com.bumptech.glide.load.DataSource?,
                                    isFirstResource: Boolean
                                ): Boolean {
                                    // Successfully loaded image - remove tint, remove padding, and set orange background
                                    editProfileImage.imageTintList = null
                                    editProfileImage.setPadding(0, 0, 0, 0)
                                    cardView.setCardBackgroundColor(androidx.core.content.ContextCompat.getColor(this@EditProfileActivity, R.color.orange))
                                    return false
                                }
                            })
                            .into(editProfileImage)
                    } else {
                        // No uploaded image - remove tint, add padding, and set orange background
                        editProfileImage.imageTintList = null
                        editProfileImage.setPadding(4.dpToPx(), 4.dpToPx(), 4.dpToPx(), 4.dpToPx())
                        cardView.setCardBackgroundColor(androidx.core.content.ContextCompat.getColor(this@EditProfileActivity, R.color.orange))
                    }
                }
                updatePasskeyRow()
            } catch (e: Exception) {
                Toast.makeText(this@EditProfileActivity, "Failed to load profile", Toast.LENGTH_SHORT).show()
            } finally {
                loadingOverlay_editProfile.visibility = View.GONE
            }
        }
    }

    private fun promptUnlockEmail() {
        if (sensitiveFieldsUnlocked) return
        if (BiometricHelper.isAvailable(this) && BiometricHelper.hasStoredCredentials(this)) {
            BiometricHelper.promptForVerification(
                activity = this,
                title = "Verify Identity",
                subtitle = "Authenticate to edit your email",
                onVerified = { unlockEmail() }
            )
        } else {
            showPasswordDialog("Verify Identity") { unlockEmail() }
        }
    }

    private fun unlockEmail() {
        sensitiveFieldsUnlocked = true
        etEmail.isEnabled = true
        emailInputLayout.endIconMode = TextInputLayout.END_ICON_NONE
        Toast.makeText(this, "Email field unlocked", Toast.LENGTH_SHORT).show()
    }

    private fun showPasswordDialog(title: String, onVerified: () -> Unit) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_unlock_email, null)
        val etInput = dialogView.findViewById<TextInputEditText>(R.id.etUnlockEmailPassword)

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setView(dialogView)
            .setPositiveButton("Confirm") { _, _ ->
                val entered = etInput.text.toString()
                if (entered.isEmpty()) {
                    Toast.makeText(this, "Password cannot be empty", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                verifyPasswordThenRun(entered, onVerified)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun verifyPasswordThenRun(enteredPassword: String, onVerified: () -> Unit) {
        val email = currentUser?.email
        if (email.isNullOrEmpty()) {
            Toast.makeText(this, "Could not verify identity. Please try again.", Toast.LENGTH_SHORT).show()
            return
        }
        loadingOverlay_editProfile.visibility = View.VISIBLE
        lifecycleScope.launch {
            try {
                // Re-authenticate with Supabase — most reliable way to verify password
                withContext(Dispatchers.IO) {
                    DeclareDatabase.auth.signInWith(io.github.jan.supabase.gotrue.providers.builtin.Email) {
                        this.email = email
                        this.password = enteredPassword
                    }
                }
                onVerified()
            } catch (e: Exception) {
                Toast.makeText(this@EditProfileActivity, "Incorrect password", Toast.LENGTH_SHORT).show()
            } finally {
                loadingOverlay_editProfile.visibility = View.GONE
            }
        }
    }

    private fun pickImage() {
        startActivityForResult(
            Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI),
            PICK_IMAGE_REQUEST
        )
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICK_IMAGE_REQUEST && resultCode == RESULT_OK && data?.data != null) {
            pendingImageUri = data.data
            Glide.with(this).load(pendingImageUri).centerCrop().into(editProfileImage)
        }
    }

    private fun showSaveConfirmation() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Save Changes")
            .setMessage("Are you sure you want to save your profile changes?")
            .setPositiveButton("Save") { _, _ -> saveChanges() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun saveChanges() {
        val newNickname = etNickname.text.toString().trim()
        val newEmail = etEmail.text.toString().trim()

        if (newNickname.isEmpty()) {
            Toast.makeText(this, "Nickname cannot be empty", Toast.LENGTH_SHORT).show()
            return
        }

        val authId = DeclareDatabase.auth.currentUserOrNull()?.id ?: return
        loadingOverlay_editProfile.visibility = View.VISIBLE

        lifecycleScope.launch {
            try {
                val user = currentUser ?: return@launch
                val numericId = user.id ?: return@launch

                val taken = withContext(Dispatchers.IO) {
                    DeclareDatabase.usersTable.select(Columns.list("user_id")) {
                        filter { eq("username", newNickname); neq("auth_id", authId) }
                    }.decodeSingleOrNull<User>()
                }
                if (taken != null) {
                    Toast.makeText(this@EditProfileActivity, "Username already taken", Toast.LENGTH_SHORT).show()
                    loadingOverlay_editProfile.visibility = View.GONE
                    return@launch
                }

                if (pendingImageUri != null) {
                    val bytes = withContext(Dispatchers.IO) {
                        ImageUtils.compressImage(contentResolver, pendingImageUri!!)
                    } ?: throw Exception("Failed to compress image")
                    val path = "$numericId/$numericId.jpg"
                    withContext(Dispatchers.IO) {
                        DeclareDatabase.profileImagesBucket.upload(path, bytes, upsert = true)
                    }
                    val publicUrl = "${DeclareDatabase.profileImagesBucket.publicUrl(path)}?t=${System.currentTimeMillis()}"
                    PayorAdapter.sDownloadUrlCache[authId] = publicUrl
                    withContext(Dispatchers.IO) {
                        DeclareDatabase.usersTable.update(buildJsonObject { put("profile_image_url", publicUrl) }) {
                            filter { eq("auth_id", authId) }
                        }
                    }
                }

                withContext(Dispatchers.IO) {
                    DeclareDatabase.usersTable.update(buildJsonObject { put("username", newNickname) }) {
                        filter { eq("auth_id", authId) }
                    }
                    UserHelper.updateCache(numericId, newNickname)
                }

                if (sensitiveFieldsUnlocked && newEmail != user.email) {
                    withContext(Dispatchers.IO) {
                        DeclareDatabase.auth.updateUser { email = newEmail }
                        DeclareDatabase.usersTable.update(buildJsonObject { put("email", newEmail) }) {
                            filter { eq("auth_id", authId) }
                        }
                    }
                }

                Toast.makeText(this@EditProfileActivity, "Profile updated", Toast.LENGTH_SHORT).show()
                setResult(RESULT_OK)
                finish()
            } catch (e: Exception) {
                Toast.makeText(this@EditProfileActivity, "Failed: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                loadingOverlay_editProfile.visibility = View.GONE
            }
        }
    }

    private fun Int.dpToPx(): Int {
        return (this * resources.displayMetrics.density).toInt()
    }
}
