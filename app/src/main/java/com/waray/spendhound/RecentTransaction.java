package com.waray.spendhound;

public class RecentTransaction {
    private String mostRecentDate;
    private String mostRecentTransactionType;
    private String mostRecentDetails;
    private String mostRecentPaymentAmountStr;
    private int iconResource;
    private String sortDateTime; // For sorting by date and time (format: "yyyy-MM-dd HH:mm:ss")

    public RecentTransaction(String mostRecentDate, String mostRecentTransactionType, String mostRecentDetails, String mostRecentPaymentAmountStr, int iconResource) {
        this.mostRecentDate = mostRecentDate;
        this.mostRecentTransactionType = mostRecentTransactionType;
        this.mostRecentDetails = mostRecentDetails;
        this.mostRecentPaymentAmountStr = mostRecentPaymentAmountStr;
        this.iconResource = iconResource;
    }

    public RecentTransaction(String mostRecentDate, String mostRecentTransactionType, String mostRecentDetails, String mostRecentPaymentAmountStr, int iconResource, String sortDateTime) {
        this.mostRecentDate = mostRecentDate;
        this.mostRecentTransactionType = mostRecentTransactionType;
        this.mostRecentDetails = mostRecentDetails;
        this.mostRecentPaymentAmountStr = mostRecentPaymentAmountStr;
        this.iconResource = iconResource;
        this.sortDateTime = sortDateTime;
    }

    public String getSortDateTime() {
        return sortDateTime;
    }

    public void setSortDateTime(String sortDateTime) {
        this.sortDateTime = sortDateTime;
    }

    public String getMostRecentDate() {
        return mostRecentDate;
    }

    public void setMostRecentDate(String mostRecentDate) {
        this.mostRecentDate = mostRecentDate;
    }

    public String getMostRecentTransactionType() {
        return mostRecentTransactionType;
    }

    public void setMostRecentTransactionType(String mostRecentTransactionType) {
        this.mostRecentTransactionType = mostRecentTransactionType;
    }

    public String getMostRecentDetails() {
        return mostRecentDetails;
    }

    public void setMostRecentDetails(String mostRecentDetails) {
        this.mostRecentDetails = mostRecentDetails;
    }

    public String getMostRecentPaymentAmountStr() {
        return mostRecentPaymentAmountStr;
    }

    public void setMostRecentPaymentAmountStr(String mostRecentPaymentAmountStr) {
        this.mostRecentPaymentAmountStr = mostRecentPaymentAmountStr;
    }

    public int getIconResource() {
        return iconResource;
    }

    public void setIconResource(int iconResource) {
        this.iconResource = iconResource;
    }
}
