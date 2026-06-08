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

    @OptIn(SupabaseInternal::class)
    fun initialize(context: Context) {
        if (_client != null) return
        
        _client = createSupabaseClient(
            supabaseUrl = SUPABASE_URL,
            supabaseKey = SUPABASE_KEY
        ) {
            httpEngine = OkHttp.create()
            install(Auth) {
                sessionManager = SharedPreferencesSessionManager(context)
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
    }

    val client: SupabaseClient
        get() = _client ?: throw IllegalStateException("DeclareDatabase not initialized. Call initialize(context) first.")

    // Supabase Module Helpers
    val auth: Auth get() = client.auth
    val postgrest: Postgrest get() = client.postgrest
    val storage: Storage get() = client.storage
    val realtime: Realtime get() = client.realtime

    // Table References
    val usersTable get() = client.from("users")
    val userBalanceTable get() = client.from("user_balance")
    val transactionsTable get() = client.from("transactions")
    val transactionItemsTable get() = client.from("transaction_items")
    val transactionPayorsTable get() = client.from("transaction_payors")
    val transactionSplitsTable get() = client.from("transaction_splits")
    val groupsTable get() = client.from("groups")
    val groupMembersTable get() = client.from("group_members")
    val groupMessagesTable get() = client.from("group_messages")
    val messageReadsTable get() = client.from("message_reads")
    val transactionReadsTable get() = client.from("transaction_reads")
    val groupMessageReactionsTable get() = client.from("group_message_reactions")
    val messageReactionsTable get() = client.from("message_reactions")
    val transactionHistoryTable get() = client.from("transaction_history")
    val borrowsTable get() = client.from("borrows")
    val userBorrowsTable get() = client.from("userBorrows")
    val crewMembersTable get() = client.from("crew_members")
    val directMessagesTable get() = client.from("direct_messages")

    // Storage Bucket References
    val profileImagesBucket get() = client.storage.from("profile_images")
    val groupImagesBucket get() = client.storage.from("group_images")

    // Legacy method names updated for Supabase compatibility
    @JvmStatic fun getDatabaseReference() = usersTable
    @JvmStatic fun getDBRefTransaction() = transactionsTable
    @JvmStatic fun getDBRefGroups() = groupsTable
    @JvmStatic fun getDBRefBorrows() = borrowsTable
    @JvmStatic fun getDBRefUserBorrows() = userBorrowsTable
}
