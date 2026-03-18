package com.waray.spendhound;

public class OwedTransaction {
    private String date, borrower, borrowedAmountStr, status;
    private String paymentSentDate;
    private String borrowId;
    private String monthYear;
    private String day;

    public OwedTransaction() {
        // Default constructor required by Firebase Realtime Database
    }

    public OwedTransaction(String date, String borrower, String borrowedAmountStr, String status) {
        this.date = String.valueOf(date);
        this.borrower = borrower;
        this.borrowedAmountStr = borrowedAmountStr;
        this.status = status;
    }

    public OwedTransaction(String date, String borrower, String borrowedAmountStr, String status, String paymentSentDate, String borrowId, String monthYear, String day) {
        this.date = String.valueOf(date);
        this.borrower = borrower;
        this.borrowedAmountStr = borrowedAmountStr;
        this.status = status;
        this.paymentSentDate = paymentSentDate;
        this.borrowId = borrowId;
        this.monthYear = monthYear;
        this.day = day;
    }

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }

    public String getBorrower() {
        return borrower;
    }

    public void setBorrower(String borrower) {
        this.borrower = borrower;
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
