package com.waray.spendhound;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthUserCollisionException;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

public class SignUpActivity extends AppCompatActivity {

    private EditText emailEditText, passwordEditText, confirmPasswordEditText, usernameEditText;
    private Button signUpButton;
    private ProgressBar progressBar;
    private FirebaseAuth mAuth;
    private Uri profileImageUri;
    private ImageView profileImageView;
    private static final int PICK_IMAGE_REQUEST = 1;
    private String userId;
    private int balanced = 0;
    private int unpaid = 0;
    private int owed = 0;
    private int debt = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.sign_up_layout);

        usernameEditText = findViewById(R.id.usernameSignUp);
        emailEditText = findViewById(R.id.emailSignup);
        passwordEditText = findViewById(R.id.passwordSignup);
        confirmPasswordEditText = findViewById(R.id.confirmPasswordSignup);
        signUpButton = findViewById(R.id.signUpButton);
        progressBar = findViewById(R.id.progressBar);
        profileImageView = findViewById(R.id.profileImageView);

        mAuth = FirebaseAuth.getInstance();

        exitEditText();

        signUpButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                signUp();
            }
        });
    }

    public void onAddProfileImageClicked(View view) {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        startActivityForResult(intent, PICK_IMAGE_REQUEST);
    }

    private void signUp() {
        String username = usernameEditText.getText().toString().trim();
        String email = emailEditText.getText().toString().trim();
        String password = passwordEditText.getText().toString().trim();
        String confirmPassword = confirmPasswordEditText.getText().toString().trim();

        if (TextUtils.isEmpty(username) || TextUtils.isEmpty(password) || TextUtils.isEmpty(confirmPassword) || TextUtils.isEmpty(email)){
            Toast.makeText(this, "Please fill in all fields", Toast.LENGTH_SHORT).show();
        } else if (!password.equals(confirmPassword)) {
            Toast.makeText(this, "Passwords don't match", Toast.LENGTH_SHORT).show();
        } else if (password.length() < 6) {
            Toast.makeText(this, "Password must be at least 6 characters long", Toast.LENGTH_SHORT).show();
        }else {
            progressBar.setVisibility(View.VISIBLE);

            mAuth.createUserWithEmailAndPassword(email, password)
                    .addOnCompleteListener(this, new OnCompleteListener<AuthResult>() {
                        @Override
                        public void onComplete(@NonNull Task<AuthResult> task) {
                            if (task.isSuccessful()) {
                                userId = mAuth.getCurrentUser().getUid();

                                if (profileImageUri != null && profileImageUri.getPath() != null) {
                                    uploadProfileImage(userId);
                                } else {
                                    String profileImageUrl = "android.resource://" + getPackageName() + "/drawable/placeholder_profile_image";
                                    saveUserToDatabase(username, email, profileImageUrl, password, balanced, unpaid, owed, debt);
                                    signUpSuccess();
                                }

                            } else {
                                if (task.getException() instanceof FirebaseAuthUserCollisionException) {
                                    progressBar.setVisibility(View.GONE);
                                    Toast.makeText(SignUpActivity.this, "Email is already in use by another account", Toast.LENGTH_SHORT).show();
                                } else {
                                    progressBar.setVisibility(View.GONE);
                                    Toast.makeText(SignUpActivity.this, "Sign up failed", Toast.LENGTH_SHORT).show();
                                }
                            }
                        }
                    });
        }
    }

    private void uploadProfileImage(final String userId) {
        StorageReference storageRef = FirebaseStorage.getInstance().getReference("profile_images/" + userId);

        storageRef.putFile(profileImageUri)
                .addOnSuccessListener(new OnSuccessListener<UploadTask.TaskSnapshot>() {
                    @Override
                    public void onSuccess(UploadTask.TaskSnapshot taskSnapshot) {
                        storageRef.getDownloadUrl().addOnSuccessListener(new OnSuccessListener<Uri>() {
                            @Override
                            public void onSuccess(Uri downloadUri) {
                                saveUserToDatabase(usernameEditText.getText().toString().trim(), emailEditText.getText().toString().trim(), downloadUri.toString(), passwordEditText.getText().toString().trim(), 0, 0,0,0);
                            }
                        });
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception exception) {
                        progressBar.setVisibility(View.GONE);
                        Toast.makeText(SignUpActivity.this, "Image upload failed", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void saveUserToDatabase(String username, String email, String profileImageUrl, String password, int balanced, int unpaid, int owed, int debt) {
        DatabaseReference usersRef = FirebaseDatabase.getInstance().getReference("users");

        // Create UserBalance with initial values
        UserBalance initialBalance = new UserBalance(balanced, unpaid, owed, debt, 0, 0);
        User user = new User(username, email, profileImageUrl, password, initialBalance);

        usersRef.child(userId).setValue(user)
                .addOnCompleteListener(new OnCompleteListener<Void>() {
                    @Override
                    public void onComplete(@NonNull Task<Void> task) {
                        if (task.isSuccessful()) {
                            // Initialize balances node explicitly
                            BalanceHelper.initializeBalancesForNewUser(userId, new BalanceHelper.BalanceCallback() {
                                @Override
                                public void onSuccess() {
                                    // Initialize userBorrows node
                                    BalanceHelper.initializeUserBorrowsForNewUser(userId, new BalanceHelper.BalanceCallback() {
                                        @Override
                                        public void onSuccess() {
                                            progressBar.setVisibility(View.GONE);
                                            signUpSuccess();
                                        }

                                        @Override
                                        public void onFailure(String error) {
                                            progressBar.setVisibility(View.GONE);
                                            // Still allow signup even if userBorrows init fails
                                            signUpSuccess();
                                        }
                                    });
                                }

                                @Override
                                public void onFailure(String error) {
                                    progressBar.setVisibility(View.GONE);
                                    // Still allow signup even if balances init fails
                                    signUpSuccess();
                                }
                            });
                        } else {
                            progressBar.setVisibility(View.GONE);
                            Toast.makeText(SignUpActivity.this, "Sign up failed", Toast.LENGTH_SHORT).show();
                        }
                    }
                });
    }
    private void signUpSuccess() {
        Toast.makeText(SignUpActivity.this, "Sign up successful", Toast.LENGTH_SHORT).show();
        Intent intent = new Intent(SignUpActivity.this, MainActivity.class);
        startActivity(intent);
        finish();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_IMAGE_REQUEST && resultCode == RESULT_OK && data != null && data.getData() != null) {
            profileImageUri = data.getData();
            profileImageView.setImageURI(profileImageUri);
        }
    }
    @SuppressLint("ClickableViewAccessibility")
    public void exitEditText(){
        final EditText usernameSignUp = findViewById(R.id.usernameSignUp);
        final EditText emailSignup = findViewById(R.id.emailSignup);
        final EditText passwordSignup = findViewById(R.id.passwordSignup);
        final EditText confirmPasswordSignup = findViewById(R.id.confirmPasswordSignup);
        usernameSignUp.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                // Consume the touch event on the EditText to prevent it from being intercepted
                v.performClick();
                return false;
            }
        });

        emailSignup.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                // Consume the touch event on the EditText to prevent it from being intercepted
                v.performClick();
                return false;
            }
        });

        passwordSignup.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                // Consume the touch event on the EditText to prevent it from being intercepted
                v.performClick();
                return false;
            }
        });

        confirmPasswordSignup.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                // Consume the touch event on the EditText to prevent it from being intercepted
                v.performClick();
                return false;
            }
        });

        // Add an OnTouchListener to the root layout (or any other layout that covers the whole screen)
        View rootView = findViewById(android.R.id.content);
        rootView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                // Hide the keyboard when the user touches outside the EditText
                hideKeyboard(usernameEditText);
                hideKeyboard(passwordEditText);
                return false;
            }
        });
    }

    private void hideKeyboard(EditText editText) {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(editText.getWindowToken(), 0);
    }
}

