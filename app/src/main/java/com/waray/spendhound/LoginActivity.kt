package com.waray.spendhound

import com.google.firebase.auth.AuthResult

class LoginActivity : AppCompatActivity() {
    private var usernameEditText: EditText? = null
    private var passwordEditText: EditText? = null
    var mAuth: FirebaseAuth? = null
    var rememberMeCheckbox: CheckBox? = null
    private var progressBar: ProgressBar? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        mAuth = DeclareDatabase.getAuth()

        rememberMeCheckbox = findViewById<CheckBox>(R.id.rememberMeCheckbox)
        usernameEditText = findViewById<EditText>(R.id.usernameEditText)
        passwordEditText = findViewById<EditText>(R.id.passwordEditText)
        val loginButton = findViewById<android.widget.Button>(R.id.loginButton)
        progressBar = findViewById<ProgressBar>(R.id.progressBar)

        exitEditText()

        loginButton.setOnClickListener(object : android.view.View.OnClickListener {
            override fun onClick(v: android.view.View?) {
                progressBar.setVisibility(android.view.View.VISIBLE)
                val username = usernameEditText.getText().toString().trim { it <= ' ' }
                val password = passwordEditText.getText().toString().trim { it <= ' ' }

                if (username.isEmpty() || password.isEmpty()) {
                    Toast.makeText(
                        this@LoginActivity,
                        "Please enter both username and password",
                        Toast.LENGTH_SHORT
                    ).show()
                    progressBar.setVisibility(android.view.View.GONE)
                } else {
                    mAuth.signInWithEmailAndPassword(username, password)
                        .addOnCompleteListener(object : OnCompleteListener<AuthResult?> {
                            override fun onComplete(task: com.google.android.gms.tasks.Task<AuthResult?>) {
                                if (task.isSuccessful()) {
                                    progressBar.setVisibility(android.view.View.GONE)
                                    Toast.makeText(
                                        this@LoginActivity,
                                        "Login successful",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    // Transition to MainActivity
                                    val intent: Intent =
                                        Intent(this@LoginActivity, MainActivity::class.java)
                                    startActivity(intent)
                                    finish() // Optional: Finish the LoginActivity to prevent returning to it when pressing back
                                } else {
                                    Toast.makeText(
                                        this@LoginActivity,
                                        "Invalid username or password",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    progressBar.setVisibility(android.view.View.GONE)
                                }
                            }
                        })
                }
            }
        })

        val signUpHereText: TextView = findViewById<TextView>(R.id.signUpHere)
        signUpHereText.setOnClickListener(object : android.view.View.OnClickListener {
            override fun onClick(v: android.view.View?) {
                signUpHere()
            }
        })

        rememberMeCheckbox.setOnCheckedChangeListener(object :
            CompoundButton.OnCheckedChangeListener {
            override fun onCheckedChanged(buttonView: CompoundButton?, isChecked: kotlin.Boolean) {
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
        })
    }

    private fun signUpHere() {
        // Handle "Sign up here" click event
        Toast.makeText(this, "Please Sign Up", Toast.LENGTH_SHORT).show()
        val intent: Intent = Intent(this@LoginActivity, SignUpActivity::class.java)
        startActivity(intent)
    }

    override fun onStart() {
        super.onStart()
        val currentUser: FirebaseUser? = mAuth.getCurrentUser()
        if (currentUser != null) {
            // User is already signed in, redirect to MainActivity
            startActivity(Intent(this@LoginActivity, MainActivity::class.java))
            finish()
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    fun exitEditText() {
        val usernameEditText: EditText = findViewById<EditText>(R.id.usernameEditText)
        val passwordEditText: EditText = findViewById<EditText>(R.id.passwordEditText)
        usernameEditText.setOnTouchListener(object : OnTouchListener {
            override fun onTouch(v: android.view.View, event: MotionEvent?): kotlin.Boolean {
                // Consume the touch event on the EditText to prevent it from being intercepted
                v.performClick()
                return false
            }
        })

        passwordEditText.setOnTouchListener(object : OnTouchListener {
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
                hideKeyboard2(passwordEditText)
                return false
            }
        })
    }

    private fun hideKeyboard(editText: EditText) {
        val imm =
            getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
        imm.hideSoftInputFromWindow(editText.getWindowToken(), 0)
    }

    private fun hideKeyboard2(editText: EditText) {
        val imm =
            getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
        imm.hideSoftInputFromWindow(editText.getWindowToken(), 0)
    }
}
