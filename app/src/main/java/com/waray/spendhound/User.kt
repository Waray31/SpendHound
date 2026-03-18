package com.waray.spendhound

import com.google.firebase.database.Exclude

class User {
    var username: String? = null
    var email: String? = null
    var password: String? = null
    var profileImageUrl: String? = null
    var balances: UserBalance? = null

    @get:Exclude
    @set:Exclude
    var uid: String? = null

    constructor(
        username: String?,
        email: String?,
        profileImageUrl: String?,
        password: String?,
        balances: UserBalance?
    ) {
        this.username = username
        this.email = email
        this.password = password
        this.profileImageUrl = profileImageUrl
        this.balances = balances
    }

    constructor()
}
