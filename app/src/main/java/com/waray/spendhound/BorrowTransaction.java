package com.waray.spendhound;

public class BorrowTransaction {
    private String date;
    private String borrowee;                // Now stores UID (was username/lender name)
    private String borrowedAmountStr;
    private String status;
    private String borroweeDisplayName;     // Display name for UI
    private String paymentSentDate;
    private String borrowId;
    private String monthYear;
    private String day;

    public BorrowTransaction() {
        // Default constructor required by Firebase Realtime Database
    }

    public BorrowTransaction(String date, String borrowee, String borrowedAmountStr, String status) {
        this.date = String.valueOf(date);
        this.borrowee = borrowee;
        this.borrowedAmountStr = borrowedAmountStr;
        this.status = status;
    }

    // New constructor with display name
    public BorrowTransaction(String date, String borrowee, String borrowedAmountStr, String status, String borroweeDisplayName) {
        this.date = String.valueOf(date);
        this.borrowee = borrowee;
        this.borrowedAmountStr = borrowedAmountStr;
        this.status = status;
        this.borroweeDisplayName = borroweeDisplayName;
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
    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getBorroweeDisplayName() {
        return borroweeDisplayName;
    }

    public void setBorroweeDisplayName(String borroweeDisplayName) {
        this.borroweeDisplayName = borroweeDisplayName;
    }

    public String getPaymentSentDate() {
        return paymentSentDate;
    }

    public void setPaymentSentDate(String paymentSentDate) {
        this.paymentSentDate = paymentSentDate;
    }

    public String getBorrowId() {
        return borrowId;
    }

    public void setBorrowId(String borrowId) {
        this.borrowId = borrowId;
    }

    public String getMonthYear() {
        return monthYear;
    }

    public void setMonthYear(String monthYear) {
        this.monthYear = monthYear;
    }

    public String getDay() {
        return day;
    }

    public void setDay(String day) {
        this.day = day;
    }

}
