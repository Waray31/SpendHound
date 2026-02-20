package com.waray.spendhound;

public class BorrowNowTransaction {
    private String borrowId;
    private String borrowerID;
    private String lenderID;
    private String borrowerName;
    private String date;
    private String lender;
    private String borrowedAmountStr;
    private String status;
    private long timestamp;

    public BorrowNowTransaction() {
        // Default constructor required by Firebase Realtime Database
    }

    public BorrowNowTransaction(String borrowId, String borrowerID, String lenderID, String borrowerName, String date, String lender, String borrowedAmountStr, String status, long timestamp) {
        this.borrowId = borrowId;
        this.borrowerID = borrowerID;
        this.lenderID = lenderID;
        this.borrowerName = borrowerName;
        this.date = String.valueOf(date);
        this.lender = lender;
        this.borrowedAmountStr = borrowedAmountStr;
        this.status = status;
        this.timestamp = timestamp;
    }

    // Legacy constructor for backwards compatibility
    public BorrowNowTransaction(String borrowerID, String lenderID, String date, String borrowee, String borrowedAmountStr, String status) {
        this.borrowerID = borrowerID;
        this.lenderID = lenderID;
        this.date = String.valueOf(date);
        this.lender = borrowee;
        this.borrowedAmountStr = borrowedAmountStr;
        this.status = status;
        this.timestamp = System.currentTimeMillis();
    }

    public String getBorrowId() {
        return borrowId;
    }

    public void setBorrowId(String borrowId) {
        this.borrowId = borrowId;
    }

    public String getBorrowerID() {
        return borrowerID;
    }

    public void setBorrowerID(String borrowerID) {
        this.borrowerID = borrowerID;
    }

    public String getLenderID() {
        return lenderID;
    }

    public void setLenderID(String lenderID) {
        this.lenderID = lenderID;
    }

    public String getBorrowerName() {
        return borrowerName;
    }

    public void setBorrowerName(String borrowerName) {
        this.borrowerName = borrowerName;
    }

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }

    public String getLender() {
        return lender;
    }

    public void setLender(String lender) {
        this.lender = lender;
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

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
}
