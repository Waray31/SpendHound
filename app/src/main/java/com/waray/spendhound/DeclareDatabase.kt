package com.waray.spendhound

import android.content.Context
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.annotations.SupabaseInternal
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.gotrue.Auth
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.realtime.realtime
import io.github.jan.supabase.serializer.KotlinXSerializer
import io.github.jan.supabase.storage.Storage
import io.github.jan.supabase.storage.storage
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import kotlinx.serialization.json.Json

object DeclareDatabase {
    private const val SUPABASE_URL = "https://xgcitilgtmxtfcxpmfiz.supabase.co"
    private const val SUPABASE_KEY = "sb_publishable_8VI_opH_alc_Inj3QkASuw_PLnJ1vUc"

    private var _client: SupabaseClient? = null

    @Synchronized
    @OptIn(SupabaseInternal::class)
    fun initialize(context: Context) {
        android.util.Log.d("DeclareDatabase", "initialize: START")
        if (_client != null) {
            android.util.Log.d("DeclareDatabase", "initialize: Client already exists")
            return
        }
        
        try {
            android.util.Log.d("DeclareDatabase", "initialize: Creating Supabase client")
            val client = createSupabaseClient(
                supabaseUrl = SUPABASE_URL,
                supabaseKey = SUPABASE_KEY
            ) {
                httpEngine = OkHttp.create()
                install(Auth) {
                    sessionManager = SharedPreferencesSessionManager(context.applicationContext)
                }
                install(Postgrest)
                install(Realtime)
                install(Storage)
                
                defaultSerializer = KotlinXSerializer(Json {
                    ignoreUnknownKeys = true
                    coerceInputValues = true
                    encodeDefaults = true
                })
                
                httpConfig {
                    install(HttpTimeout) {
                        requestTimeoutMillis = 60000L
                        connectTimeoutMillis = 60000L
                        socketTimeoutMillis = 60000L
                    }
                }
            }
            _client = client
            android.util.Log.d("DeclareDatabase", "initialize: Supabase client initialized successfully")
        } catch (e: Exception) {
            android.util.Log.e("DeclareDatabase", "initialize: Failed to initialize Supabase client: ${e.message}", e)
        }
    }

    val clientOrNull: SupabaseClient? get() = _client
    val client: SupabaseClient get() = _client ?: throw IllegalStateException("Supabase client is not initialized. Ensure initialize() is called successfully.")

    // Supabase Module Helpers - Safer getters that return null instead of crashing
    val auth: Auth get() = _client?.auth ?: throw IllegalStateException("Supabase Auth is not initialized. Ensure initialize() is called successfully.")
    val postgrest: Postgrest get() = _client?.postgrest ?: throw IllegalStateException("Supabase Postgrest is not initialized. Ensure initialize() is called successfully.")
    val storage: Storage get() = _client?.storage ?: throw IllegalStateException("Supabase Storage is not initialized. Ensure initialize() is called successfully.")
    val realtime: Realtime get() = _client?.realtime ?: throw IllegalStateException("Supabase Realtime is not initialized. Ensure initialize() is called successfully.")

    // Table References - Use property syntax but handle potential null client
    val usersTable get() = _client?.from("users") ?: throw IllegalStateException("Supabase client is not initialized.")
    val userBalanceTable get() = _client?.from("user_balance") ?: throw IllegalStateException("Supabase client is not initialized.")
    val transactionsTable get() = _client?.from("transactions") ?: throw IllegalStateException("Supabase client is not initialized.")
    val transactionItemsTable get() = _client?.from("transaction_items") ?: throw IllegalStateException("Supabase client is not initialized.")
    val transactionPayorsTable get() = _client?.from("transaction_payors") ?: throw IllegalStateException("Supabase client is not initialized.")
    val transactionSplitsTable get() = _client?.from("transaction_splits") ?: throw IllegalStateException("Supabase client is not initialized.")
    val groupsTable get() = _client?.from("groups") ?: throw IllegalStateException("Supabase client is not initialized.")
    val groupMembersTable get() = _client?.from("group_members") ?: throw IllegalStateException("Supabase client is not initialized.")
    val groupMessagesTable get() = _client?.from("group_messages") ?: throw IllegalStateException("Supabase client is not initialized.")
    val messageReadsTable get() = _client?.from("message_reads") ?: throw IllegalStateException("Supabase client is not initialized.")
    val transactionReadsTable get() = _client?.from("transaction_reads") ?: throw IllegalStateException("Supabase client is not initialized.")
    val groupMessageReactionsTable get() = _client?.from("group_message_reactions") ?: throw IllegalStateException("Supabase client is not initialized.")
    val messageReactionsTable get() = _client?.from("message_reactions") ?: throw IllegalStateException("Supabase client is not initialized.")
    val transactionHistoryTable get() = _client?.from("transaction_history") ?: throw IllegalStateException("Supabase client is not initialized.")
    val borrowsTable get() = _client?.from("borrows") ?: throw IllegalStateException("Supabase client is not initialized.")
    val userBorrowsTable get() = _client?.from("userBorrows") ?: throw IllegalStateException("Supabase client is not initialized.")
    val crewMembersTable get() = _client?.from("crew_members") ?: throw IllegalStateException("Supabase client is not initialized.")
    val directMessagesTable get() = _client?.from("direct_messages") ?: throw IllegalStateException("Supabase client is not initialized.")

    // Storage Bucket References
    val profileImagesBucket get() = _client?.storage?.from("profile_images") ?: throw IllegalStateException("Supabase client is not initialized.")
    val groupImagesBucket get() = _client?.storage?.from("group_images") ?: throw IllegalStateException("Supabase client is not initialized.")

    // Legacy method names updated for Supabase compatibility
    @JvmStatic fun getDatabaseReference() = usersTable
    @JvmStatic fun getDBRefTransaction() = transactionsTable
    @JvmStatic fun getDBRefGroups() = groupsTable
    @JvmStatic fun getDBRefBorrows() = borrowsTable
    @JvmStatic fun getDBRefUserBorrows() = userBorrowsTable
}
