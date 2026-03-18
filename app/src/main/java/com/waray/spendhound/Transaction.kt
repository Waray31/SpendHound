package com.waray.spendhound;

import java.util.List;

public class Transaction {
    private String transactionType;
    private double paymentAmount;
    private String multilineStr;
    private List<String> payorsList;        // Now stores UIDs (was usernames)
    private List<Double> amountsPaidList;
    private String usernamePost;            // Now stores UID (was username)
    private double totalIndividualPayment;
    private String groupId;
    private String groupName;
    
    // Display name fields (for backward compatibility with old data)
    private List<String> payorsDisplayNames;
    private String posterDisplayName;

    public Transaction(String transactionType, double paymentAmount, String multilineStr, List<String> payorsList, List<Double> amountsPaidList, String usernamePost, double totalIndividualPayment) {
        this.transactionType = transactionType;
        this.paymentAmount = paymentAmount;
        this.multilineStr = multilineStr;
        this.payorsList = payorsList;
        this.amountsPaidList = amountsPaidList;
        this.usernamePost = usernamePost;
        this.totalIndividualPayment = totalIndividualPayment;
    }

    public Transaction(String transactionType, double paymentAmount, String multilineStr, List<String> payorsList, List<Double> amountsPaidList, String usernamePost, double totalIndividualPayment, String groupId, String groupName) {
        this.transactionType = transactionType;
        this.paymentAmount = paymentAmount;
        this.multilineStr = multilineStr;
        this.payorsList = payorsList;
        this.amountsPaidList = amountsPaidList;
        this.usernamePost = usernamePost;
        this.totalIndividualPayment = totalIndividualPayment;
        this.groupId = groupId;
        this.groupName = groupName;
    }

    // New constructor with display names for UID-based storage
    public Transaction(String transactionType, double paymentAmount, String multilineStr, 
                      List<String> payorsList, List<Double> amountsPaidList, String usernamePost, 
                      double totalIndividualPayment, String groupId, String groupName,
                      List<String> payorsDisplayNames, String posterDisplayName) {
        this.transactionType = transactionType;
        this.paymentAmount = paymentAmount;
        this.multilineStr = multilineStr;
        this.payorsList = payorsList;
        this.amountsPaidList = amountsPaidList;
        this.usernamePost = usernamePost;
        this.totalIndividualPayment = totalIndividualPayment;
        this.groupId = groupId;
        this.groupName = groupName;
        this.payorsDisplayNames = payorsDisplayNames;
        this.posterDisplayName = posterDisplayName;
    }

    // Add an empty constructor
    public Transaction() {
        // Default constructor required for Firebase
    }

    public Transaction(double paymentAmount, List<String> payorsList, List<Double> amountsPaidList, String usernamePost) {
    }

    public String getTransactionType() {
        return transactionType;
    }

    public void setTransactionType(String transactionType) {
        this.transactionType = transactionType;
    }

    public double getPaymentAmount() {
        return paymentAmount;
    }

    public void setPaymentAmount(double paymentAmount) {
        this.paymentAmount = paymentAmount;
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

    public String getUsernamePost() {
        return usernamePost;
    }

    public void setUsernamePost(String usernamePost) {
        this.usernamePost = usernamePost;
    }

    public String getMultilineStr() {
        return multilineStr;
    }

    public void setMultilineStr(String multilineStr) {
        this.multilineStr = multilineStr;
    }

    public double getTotalIndividualPayment() {
        return totalIndividualPayment;
    }

    public void setTotalIndividualPayment(double totalIndividualPayment) {
        this.totalIndividualPayment = totalIndividualPayment;
    }

    public String getGroupId() {
        return groupId;
    }

    public void setGroupId(String groupId) {
        this.groupId = groupId;
    }

    public String getGroupName() {
        return groupName;
    }

    public void setGroupName(String groupName) {
        this.groupName = groupName;
    }

    public List<String> getPayorsDisplayNames() {
        return payorsDisplayNames;
    }

    public void setPayorsDisplayNames(List<String> payorsDisplayNames) {
        this.payorsDisplayNames = payorsDisplayNames;
    }

    public String getPosterDisplayName() {
        return posterDisplayName;
    }

    public void setPosterDisplayName(String posterDisplayName) {
        this.posterDisplayName = posterDisplayName;
    }

    public boolean isUserInvolvedByUid(String uid) {
        if (uid == null || uid.isEmpty()) {
            return false;
        }
        if (uid.equals(usernamePost)) {
            return true;
        }
        if (payorsList != null && payorsList.contains(uid)) {
            return true;
        }
        return false;
    }

    public boolean isUserInvolvedByUsername(String username) {
        if (username == null || username.isEmpty()) {
            return false;
        }
        if (username.equals(posterDisplayName) || username.equals(usernamePost)) {
            return true;
        }
        if (payorsDisplayNames != null && payorsDisplayNames.contains(username)) {
            return true;
        }
        if (payorsList != null && payorsList.contains(username)) {
            return true;
        }
        return false;
    }
}
