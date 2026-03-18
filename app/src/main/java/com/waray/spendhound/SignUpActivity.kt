package com.waray.spendhound

class SignUpActivity : AppCompatActivity() {
    private var emailEditText: EditText? = null
    private var passwordEditText: EditText? = null
    private var confirmPasswordEditText: EditText? = null
    private var usernameEditText: EditText? = null
    private var signUpButton: android.widget.Button? = null
    private var progressBar: ProgressBar? = null
    private var mAuth: FirebaseAuth? = null
    private val profileImageUri: android.net.Uri? = null
    private var userId: kotlin.String? = null
    private val balanced = 0
    private val unpaid = 0
    private val owed = 0
    private val debt = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.sign_up_layout)

        usernameEditText = findViewById<EditText>(R.id.usernameSignUp)
        emailEditText = findViewById<EditText>(R.id.emailSignup)
        passwordEditText = findViewById<EditText>(R.id.passwordSignup)
        confirmPasswordEditText = findViewById<EditText>(R.id.confirmPasswordSignup)
        signUpButton = findViewById<android.widget.Button>(R.id.signUpButton)
        progressBar = findViewById<ProgressBar>(R.id.progressBar)

        mAuth = FirebaseAuth.getInstance()

        exitEditText()

        signUpButton!!.setOnClickListener(object : android.view.View.OnClickListener {
            override fun onClick(v: android.view.View?) {
                signUp()
            }
        })
    }

    fun onAddProfileImageClicked(view: android.view.View?) {
        val intent: Intent =
            Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        startActivityForResult(intent, SignUpActivity.Companion.PICK_IMAGE_REQUEST)
    }

    private fun signUp() {
        val username = usernameEditText.getText().toString().trim { it <= ' ' }
        val email = emailEditText.getText().toString().trim { it <= ' ' }
        val password = passwordEditText.getText().toString().trim { it <= ' ' }
        val confirmPassword = confirmPasswordEditText.getText().toString().trim { it <= ' ' }

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
            progressBar.setVisibility(android.view.View.VISIBLE)

            mAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, object : OnCompleteListener<AuthResult?> {
                    override fun onComplete(task: com.google.android.gms.tasks.Task<AuthResult?>) {
                        if (task.isSuccessful()) {
                            userId = mAuth.getCurrentUser().getUid()

                            if (profileImageUri != null && profileImageUri.getPath() != null) {
                                uploadProfileImage(userId)
                            } else {
                                val profileImageUrl =
                                    "android.resource://" + getPackageName() + "/drawable/placeholder_profile_image"
                                saveUserToDatabase(
                                    username,
                                    email,
                                    profileImageUrl,
                                    password,
                                    balanced,
                                    unpaid,
                                    owed,
                                    debt
                                )
                                signUpSuccess()
                            }
                        } else {
                            if (task.getException() is FirebaseAuthUserCollisionException) {
                                progressBar.setVisibility(android.view.View.GONE)
                                Toast.makeText(
                                    this@SignUpActivity,
                                    "Email is already in use by another account",
                                    Toast.LENGTH_SHORT
                                ).show()
                            } else {
                                progressBar.setVisibility(android.view.View.GONE)
                                Toast.makeText(
                                    this@SignUpActivity,
                                    "Sign up failed",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    }
                })
        }
    }

    private fun uploadProfileImage(userId: kotlin.String?) {
        val storageRef: StorageReference =
            FirebaseStorage.getInstance().getReference("profile_images/" + userId)

        storageRef.putFile(profileImageUri)
            .addOnSuccessListener(object : OnSuccessListener<UploadTask.TaskSnapshot?> {
                override fun onSuccess(taskSnapshot: UploadTask.TaskSnapshot?) {
                    storageRef.getDownloadUrl()
                        .addOnSuccessListener(object : OnSuccessListener<android.net.Uri?> {
                            override fun onSuccess(downloadUri: android.net.Uri) {
                                saveUserToDatabase(
                                    usernameEditText.getText().toString().trim { it <= ' ' },
                                    emailEditText.getText().toString().trim { it <= ' ' },
                                    downloadUri.toString(),
                                    passwordEditText.getText().toString().trim { it <= ' ' },
                                    0,
                                    0,
                                    0,
                                    0
                                )
                            }
                        })
                }
            })
            .addOnFailureListener(object : OnFailureListener {
                override fun onFailure(exception: java.lang.Exception) {
                    progressBar.setVisibility(android.view.View.GONE)
                    Toast.makeText(this@SignUpActivity, "Image upload failed", Toast.LENGTH_SHORT)
                        .show()
                }
            })
    }

    private fun saveUserToDatabase(
        username: kotlin.String?,
        email: kotlin.String?,
        profileImageUrl: kotlin.String?,
        password: kotlin.String?,
        balanced: Int,
        unpaid: Int,
        owed: Int,
        debt: Int
    ) {
        val usersRef: DatabaseReference = FirebaseDatabase.getInstance().getReference("users")

        // Create UserBalance with initial values
        val initialBalance = UserBalance(0.0, 0.0, 0.0, 0.0, 0.0)
        val user =
            com.waray.spendhound.User(username, email, profileImageUrl, password, initialBalance)

        usersRef.child(userId).setValue(user)
            .addOnCompleteListener(object : OnCompleteListener<java.lang.Void?> {
                override fun onComplete(task: com.google.android.gms.tasks.Task<java.lang.Void?>) {
                    if (task.isSuccessful()) {
                        // Initialize balances node explicitly
                        BalanceHelper.initializeBalancesForNewUser(
                            userId,
                            object : BalanceCallback {
                                override fun onSuccess() {
                                    // Initialize userBorrows node
                                    BalanceHelper.initializeUserBorrowsForNewUser(
                                        userId,
                                        object : BalanceCallback {
                                            override fun onSuccess() {
                                                progressBar.setVisibility(android.view.View.GONE)
                                                signUpSuccess()
                                            }

                                            override fun onFailure(error: kotlin.String?) {
                                                progressBar.setVisibility(android.view.View.GONE)
                                                // Still allow signup even if userBorrows init fails
                                                signUpSuccess()
                                            }
                                        })
                                }

                                override fun onFailure(error: kotlin.String?) {
                                    progressBar.setVisibility(android.view.View.GONE)
                                    // Still allow signup even if balances init fails
                                    signUpSuccess()
                                }
                            })
                    } else {
                        progressBar.setVisibility(android.view.View.GONE)
                        Toast.makeText(this@SignUpActivity, "Sign up failed", Toast.LENGTH_SHORT)
                            .show()
                    }
                }
            })
    }

    private fun signUpSuccess() {
        Toast.makeText(this@SignUpActivity, "Sign up successful", Toast.LENGTH_SHORT).show()
        val intent: Intent = Intent(this@SignUpActivity, MainActivity::class.java)
        startActivity(intent)
        finish()
    }

    @SuppressLint("ClickableViewAccessibility")
    fun exitEditText() {
        val usernameSignUp: EditText = findViewById<EditText>(R.id.usernameSignUp)
        val emailSignup: EditText = findViewById<EditText>(R.id.emailSignup)
        val passwordSignup: EditText = findViewById<EditText>(R.id.passwordSignup)
        val confirmPasswordSignup: EditText = findViewById<EditText>(R.id.confirmPasswordSignup)
        usernameSignUp.setOnTouchListener(object : OnTouchListener {
            override fun onTouch(v: android.view.View, event: MotionEvent?): kotlin.Boolean {
                // Consume the touch event on the EditText to prevent it from being intercepted
                v.performClick()
                return false
            }
        })

        emailSignup.setOnTouchListener(object : OnTouchListener {
            override fun onTouch(v: android.view.View, event: MotionEvent?): kotlin.Boolean {
                // Consume the touch event on the EditText to prevent it from being intercepted
                v.performClick()
                return false
            }
        })

        passwordSignup.setOnTouchListener(object : OnTouchListener {
            override fun onTouch(v: android.view.View, event: MotionEvent?): kotlin.Boolean {
                // Consume the touch event on the EditText to prevent it from being intercepted
                v.performClick()
                return false
            }
        })

        confirmPasswordSignup.setOnTouchListener(object : OnTouchListener {
            override fun onTouch(v: android.view.View, event: MotionEvent?): kotlin.Boolean {
                // Consume the touch event on the EditText to prevent it from being intercepted
                v.performClick()
                return false
            }
        })

        // Add an OnTouchListener to the root layout (or any other layout that covers the whole screen)
        val rootView = findViewById<android.view.View>(android.R.id.content)
        rootView.setOnTouchListener(object : OnTouchListener {
            override fun onTouch(v: android.view.View?, event: MotionEvent?): kotlin.Boolean {
                // Hide the keyboard when the user touches outside the EditText
                hideKeyboard(usernameEditText)
                hideKeyboard(passwordEditText)
                return false
            }
        })
    }

    private fun hideKeyboard(editText: EditText) {
        val imm =
            getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
        imm.hideSoftInputFromWindow(editText.getWindowToken(), 0)
    }

    companion object {
        private const val PICK_IMAGE_REQUEST = 1
    }
}

