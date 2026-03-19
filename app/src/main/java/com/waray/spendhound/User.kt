package com.waray.spendhound

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Data model for the 'users' table, aligned with the Supabase schema provided.
 * Database Types:
 * user_id: int8 (Primary Key)
 * username: text
 * email: text
 * password: text
 * profile_image_url: varchar
 * created_at: timestamptz
 */
@Serializable
data class User(
    @SerialName("user_id")
    val id: Long? = null,
    val username: String? = null,
    val email: String? = null,
    val password: String? = null,
    @SerialName("profile_image_url")
    val profileImageUrl: String? = null,
    @SerialName("created_at")
    val createdAt: String? = null
)
