package com.waray.spendhound

import kotlinx.serialization.Serializable

@Serializable
data class User(
    val username: String? = null,
    val email: String? = null,
    val profileImageUrl: String? = null,
    val balances: UserBalance? = null,
    // Supabase often uses 'id' as the primary key
    val id: String? = null
) {
    // Getters aligned with database schema
    fun getUsername(): String? = username
    fun getEmail(): String? = email
    fun getProfileImageUrl(): String? = profileImageUrl
    fun getBalances(): UserBalance? = balances
    fun getId(): String? = id
}
