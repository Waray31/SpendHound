package com.waray.spendhound;

import java.util.List;

public class RecentTransaction {
    private String mostRecentDate;
    private String mostRecentTransactionType;
    private String mostRecentDetails;
    private String mostRecentPaymentAmountStr;
    private int iconResource;
    private String sortDateTime; // For sorting by date and time (format: "yyyy-MM-dd HH:mm:ss")
    private List<String> payorsList;
    private List<Integer> amountsPaidList;
    private String fullDateWithYear; // Full date including year for details dialog
    private String createdBy; // Name of the person who created the transaction
    private String createdByUid; // UID of the person who created the transaction (for profile image)

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

    public RecentTransaction(String mostRecentDate, String mostRecentTransactionType, String mostRecentDetails,
                            String mostRecentPaymentAmountStr, int iconResource, String sortDateTime,
                            List<String> payorsList, List<Integer> amountsPaidList, String fullDateWithYear,
                            String createdBy, String createdByUid) {
        this.mostRecentDate = mostRecentDate;
        this.mostRecentTransactionType = mostRecentTransactionType;
        this.mostRecentDetails = mostRecentDetails;
        this.mostRecentPaymentAmountStr = mostRecentPaymentAmountStr;
        this.iconResource = iconResource;
        this.sortDateTime = sortDateTime;
        this.payorsList = payorsList;
        this.amountsPaidList = amountsPaidList;
        this.fullDateWithYear = fullDateWithYear;
        this.createdBy = createdBy;
        this.createdByUid = createdByUid;
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

    public List<String> getPayorsList() {
        return payorsList;
    }

    public void setPayorsList(List<String> payorsList) {
        this.payorsList = payorsList;
    }

    public List<Integer> getAmountsPaidList() {
        return amountsPaidList;
    }

    public void setAmountsPaidList(List<Integer> amountsPaidList) {
        this.amountsPaidList = amountsPaidList;
    }

    public String getFullDateWithYear() {
        return fullDateWithYear;
    }

    public void setFullDateWithYear(String fullDateWithYear) {
        this.fullDateWithYear = fullDateWithYear;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public String getCreatedByUid() {
        return createdByUid;
    }

    public void setCreatedByUid(String createdByUid) {
        this.createdByUid = createdByUid;
    }
}
