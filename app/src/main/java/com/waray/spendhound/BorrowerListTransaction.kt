package com.waray.spendhound;

import android.widget.Button;
import android.widget.ImageView;

public class BorrowerListTransaction {
    private String date;
    private String borrowee;
    private String borrowedAmountStr;
    private String borrowerImgUrl; // URL to the image
    private String status;
    private String profileImageUrl;

    // No-argument constructor
    public BorrowerListTransaction() {
    }

    public BorrowerListTransaction(String date, String borrowee, String borrowedAmountStr, String status) {
        this.date = date;
        this.borrowee = borrowee;
        this.borrowedAmountStr = borrowedAmountStr;
        this.status = status;
    }

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }

    public String getBorrowee() {
        return borrowee;
    }

    public void setBorrowee(String borrowee) {
        this.borrowee = borrowee;
    }

    public String getBorrowedAmountStr() {
        return borrowedAmountStr;
    }

    public void setBorrowedAmountStr(String borrowedAmountStr) {
        this.borrowedAmountStr = borrowedAmountStr;
    }

    public String getBorrowerImgUrl() {
        return borrowerImgUrl;
    }

    public void setBorrowerImgUrl(String borrowerImgUrl) {
        this.borrowerImgUrl = borrowerImgUrl;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getProfileImageUrl() {
        return profileImageUrl;
    }

    public void setProfileImageUrl(String profileImageUrl) {
        this.profileImageUrl = profileImageUrl;
    }

}
