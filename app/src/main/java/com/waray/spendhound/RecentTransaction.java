package com.waray.spendhound;

import java.util.List;

public class RecentTransaction {
    private String mostRecentDate;
    private String mostRecentTransactionType;
    private String mostRecentDetails;
    private String mostRecentPaymentAmountStr;
    private int iconResource;
    private String sortDateTime; // For sorting by date and time (format: "yyyy-MM-dd HH:mm:ss")
    private List<String> payorsList; // Display names
    private List<String> payorUids;  // UIDs for profile images
    private List<Double> amountsPaidList;
    private double totalIndividualPayment;
    private String fullDateWithYear; // Full date including year for details dialog
    private String createdBy; // Name of the person who created the transaction
    private String createdByUid; // UID of the person who created the transaction (for profile image)
    
    // Database reference keys
    private String monthYear;
    private String day;
    private String timeKey;

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
                            List<String> payorsList, List<Double> amountsPaidList, String fullDateWithYear,
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

    public RecentTransaction(String mostRecentDate, String mostRecentTransactionType, String mostRecentDetails,
                             String mostRecentPaymentAmountStr, int iconResource, String sortDateTime,
                             List<String> payorsList, List<String> payorUids, List<Double> amountsPaidList,
                             double totalIndividualPayment, String fullDateWithYear,
                             String createdBy, String createdByUid,
                             String monthYear, String day, String timeKey) {
        this.mostRecentDate = mostRecentDate;
        this.mostRecentTransactionType = mostRecentTransactionType;
        this.mostRecentDetails = mostRecentDetails;
        this.mostRecentPaymentAmountStr = mostRecentPaymentAmountStr;
        this.iconResource = iconResource;
        this.sortDateTime = sortDateTime;
        this.payorsList = payorsList;
        this.payorUids = payorUids;
        this.amountsPaidList = amountsPaidList;
        this.totalIndividualPayment = totalIndividualPayment;
        this.fullDateWithYear = fullDateWithYear;
        this.createdBy = createdBy;
        this.createdByUid = createdByUid;
        this.monthYear = monthYear;
        this.day = day;
        this.timeKey = timeKey;
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

    public List<Double> getAmountsPaidList() {
        return amountsPaidList;
    }

    public void setAmountsPaidList(List<Double> amountsPaidList) {
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

    public List<String> getPayorUids() {
        return payorUids;
    }

    public void setPayorUids(List<String> payorUids) {
        this.payorUids = payorUids;
    }

    public double getTotalIndividualPayment() {
        return totalIndividualPayment;
    }

    public void setTotalIndividualPayment(double totalIndividualPayment) {
        this.totalIndividualPayment = totalIndividualPayment;
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

    public String getTimeKey() {
        return timeKey;
    }

    public void setTimeKey(String timeKey) {
        this.timeKey = timeKey;
    }
}