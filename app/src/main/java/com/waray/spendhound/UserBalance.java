package com.waray.spendhound;

/**
 * Model class for user balance data stored under users/{uid}/balances
 */
public class UserBalance {
    private int currentBalance;
    private int unpaid;
    private int owed;
    private int debt;
    private int totalBorrowed;
    private int totalLent;

    // Default constructor required for Firebase
    public UserBalance() {
        this.currentBalance = 0;
        this.unpaid = 0;
        this.owed = 0;
        this.debt = 0;
        this.totalBorrowed = 0;
        this.totalLent = 0;
    }

    public UserBalance(int currentBalance, int unpaid, int owed, int debt, int totalBorrowed, int totalLent) {
        this.currentBalance = currentBalance;
        this.unpaid = unpaid;
        this.owed = owed;
        this.debt = debt;
        this.totalBorrowed = totalBorrowed;
        this.totalLent = totalLent;
    }

    // Getters and Setters
    public int getCurrentBalance() {
        return currentBalance;
    }

    public void setCurrentBalance(int currentBalance) {
        this.currentBalance = currentBalance;
    }

    public int getUnpaid() {
        return unpaid;
    }

    public void setUnpaid(int unpaid) {
        this.unpaid = unpaid;
    }

    public int getOwed() {
        return owed;
    }

    public void setOwed(int owed) {
        this.owed = owed;
    }

    public int getDebt() {
        return debt;
    }

    public void setDebt(int debt) {
        this.debt = debt;
    }

    public int getTotalBorrowed() {
        return totalBorrowed;
    }

    public void setTotalBorrowed(int totalBorrowed) {
        this.totalBorrowed = totalBorrowed;
    }

    public int getTotalLent() {
        return totalLent;
    }

    public void setTotalLent(int totalLent) {
        this.totalLent = totalLent;
    }
}

