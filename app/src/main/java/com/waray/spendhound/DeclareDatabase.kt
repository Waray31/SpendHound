package com.waray.spendhound

import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.gotrue.Auth
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.realtime.realtime
import io.github.jan.supabase.storage.Storage
import io.github.jan.supabase.storage.storage

object DeclareDatabase {
    private const val SUPABASE_URL = "https://xgcitilgtmxtfcxpmfiz.supabase.co"
    private const val SUPABASE_KEY = "sb_publishable_8VI_opH_alc_Inj3QkASuw_PLnJ1vUc"

    val client: SupabaseClient by lazy {
        createSupabaseClient(
            supabaseUrl = SUPABASE_URL,
            supabaseKey = SUPABASE_KEY
        ) {
            install(Auth)
            install(Postgrest)
            install(Realtime)
            install(Storage)
        }
    }

    // Supabase Module Helpers
    val auth: Auth get() = client.auth
    val postgrest: Postgrest get() = client.postgrest
    val storage: Storage get() = client.storage
    val realtime: Realtime get() = client.realtime

    // Table References
    val usersTable get() = client.from("users")
    val transactionsTable get() = client.from("transactions")
    val groupsTable get() = client.from("groups")
    val borrowsTable get() = client.from("borrows")

    // Storage Bucket Reference
    val profileImagesBucket get() = client.storage.from("profile_images")

    // Compatibility getters (updated to return Supabase types where applicable or kept for reference)
    @JvmStatic fun getAuth() = auth
    @JvmStatic fun getUsersTable() = usersTable
    @JvmStatic fun getTransactionsTable() = transactionsTable
    @JvmStatic fun getGroupsTable() = groupsTable
    @JvmStatic fun getBorrowsTable() = borrowsTable
    
    // Firebase Realtime Database References
    @JvmStatic fun getDBRefBorrows(): DatabaseReference = 
        FirebaseDatabase.getInstance().reference.child("borrows")
    
    @JvmStatic fun getDBRefUserBorrows(): DatabaseReference = 
        FirebaseDatabase.getInstance().reference.child("userBorrows")
}
