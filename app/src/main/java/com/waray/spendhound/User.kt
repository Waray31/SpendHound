package com.waray.spendhound

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class User(
    val username: String? = null,
    val email: String? = null,
    @SerialName("profile_image_url")
    val profileImageUrl: String? = null,
    val balances: UserBalance? = null,
    @SerialName("id")
    val id: Long? = null, // Numeric primary key (int8)
    @SerialName("uid")
    val uid: String? = null // Supabase Auth UID (uuid)
) {
    // Getters for compatibility
    fun getUsername(): String? = username
    fun getEmail(): String? = email
    fun getProfileImageUrl(): String? = profileImageUrl
    fun getBalances(): UserBalance? = balances
    fun getId(): String? = uid ?: id?.toString()
    fun getNumericId(): Long? = id
}
