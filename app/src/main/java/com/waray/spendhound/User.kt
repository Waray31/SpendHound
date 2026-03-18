package com.waray.spendhound;

import com.google.firebase.database.Exclude;

public class User {

    private String username;
    private String email;
    private String password;
    private String profileImageUrl;
    private UserBalance balances;
    private String uid;

    public User(String username, String email, String profileImageUrl, String password, UserBalance balances) {
        this.username = username;
        this.email = email;
        this.password = password;
        this.profileImageUrl = profileImageUrl;
        this.balances = balances;
    }

    public User() {
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getProfileImageUrl() {
        return profileImageUrl;
    }

    public void setProfileImageUrl(String profileImageUrl) {
        this.profileImageUrl = profileImageUrl;
    }


    public UserBalance getBalances() {
        return balances;
    }

    public void setBalances(UserBalance balances) {
        this.balances = balances;
    }

    @Exclude
    public String getUid() {
        return uid;
    }

    @Exclude
    public void setUid(String uid) {
        this.uid = uid;
    }
}
