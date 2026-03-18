package com.waray.spendhound

import com.google.firebase.auth.FirebaseAuth

object DeclareDatabase {
    private val mAuth: FirebaseAuth?
    private val mDatabase: FirebaseDatabase?
    private val mStorage: FirebaseStorage


    // Initialize Firebase components in a static block
    init {
        mAuth = FirebaseAuth.getInstance()
        mDatabase = FirebaseDatabase.getInstance()
        mStorage = FirebaseStorage.getInstance()
    }

    val auth: FirebaseAuth?
        // Get the Firebase Authentication instance
        get() = mAuth

    val databaseReference: DatabaseReference
        // Get a reference to the Firebase Realtime Database
        get() = FirebaseDatabase.getInstance().getReference("users")
    val dBRefTransaction: DatabaseReference
        get() = FirebaseDatabase.getInstance().getReference("transactions")
    val dBRefBorrows: DatabaseReference
        get() = FirebaseDatabase.getInstance().getReference("borrows")

    val dBRefGroups: DatabaseReference
        get() = FirebaseDatabase.getInstance().getReference("payerGroups")

    val dBRefUserBorrows: DatabaseReference
        get() = FirebaseDatabase.getInstance().getReference("userBorrows")

    val storageReference: StorageReference
        // Get a reference to the Firebase Storage
        get() = mStorage.getReference()
}

