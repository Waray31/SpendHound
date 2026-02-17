package com.waray.spendhound;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

public class DeclareDatabase {
    private static FirebaseAuth mAuth;
    private static FirebaseDatabase mDatabase;
    private static FirebaseStorage mStorage;


    // Initialize Firebase components in a static block
    static {
        mAuth = FirebaseAuth.getInstance();
        mDatabase = FirebaseDatabase.getInstance();
        mStorage = FirebaseStorage.getInstance();
    }

    // Get the Firebase Authentication instance
    public static FirebaseAuth getAuth() {
        return mAuth;
    }

    // Get a reference to the Firebase Realtime Database
    public static DatabaseReference getDatabaseReference() {
        return FirebaseDatabase.getInstance().getReference("users");
    }
    public static DatabaseReference getDBRefTransaction() {
        return FirebaseDatabase.getInstance().getReference("transactions");
    }
    public static DatabaseReference getDBRefBorrows() {
        return FirebaseDatabase.getInstance().getReference("borrows");
    }

    public static DatabaseReference getDBRefGroups() {
        return FirebaseDatabase.getInstance().getReference("payerGroups");
    }

    // Get a reference to the Firebase Storage
    public static StorageReference getStorageReference() {
        return mStorage.getReference();
    }
}

