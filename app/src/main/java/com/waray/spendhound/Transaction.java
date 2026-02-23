package com.waray.spendhound;

import com.google.firebase.database.DatabaseReference;

import java.lang.reflect.Array;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class Transaction {
    private String transactionType;
    private int paymentAmount;
    private String multilineStr;
    private List<String> payorsList;        // Now stores UIDs (was usernames)
    private List<Integer> amountsPaidList;
    private String usernamePost;            // Now stores UID (was username)
    private int totalIndividualPayment;
    private String groupId;
    private String groupName;
    
    // Display name fields (for backward compatibility with old data)
    private List<String> payorsDisplayNames;
    private String posterDisplayName;

    public Transaction(String transactionType, int paymentAmount,String multilineStr, List<String> payorsList, List<Integer> amountsPaidList, String usernamePost, int totalIndividualPayment) {
        this.transactionType = transactionType;
        this.paymentAmount = paymentAmount;
        this.multilineStr = multilineStr;
        this.payorsList = payorsList;
        this.amountsPaidList = amountsPaidList;
        this.usernamePost= usernamePost;
        this.totalIndividualPayment= totalIndividualPayment;
    }

    public Transaction(String transactionType, int paymentAmount, String multilineStr, List<String> payorsList, List<Integer> amountsPaidList, String usernamePost, int totalIndividualPayment, String groupId, String groupName) {
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
    public Transaction(String transactionType, int paymentAmount, String multilineStr, 
                      List<String> payorsList, List<Integer> amountsPaidList, String usernamePost, 
                      int totalIndividualPayment, String groupId, String groupName,
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

    public Transaction(int paymentAmount, List<String> payorsList, List<Integer> amountsPaidList, String usernamePost) {
    }

    public String getTransactionType() {
        return transactionType;
    }

    public void setTransactionType(String transactionType) {
        this.transactionType = transactionType;
    }

    public int getPaymentAmount() {
        return paymentAmount;
    }

    public void setPaymentAmount(int paymentAmount) {
        this.paymentAmount = paymentAmount;
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
        this.amountsPaidList  = amountsPaidList;
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

    public int getTotalIndividualPayment() {
        return totalIndividualPayment;
    }

    public void setTotalIndividualPayment(int totalIndividualPayment) {
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

    /**
     * Check if user is involved in this transaction by UID.
     * Supports both new UID-based data and legacy username-based data.
     */
    public boolean isUserInvolvedByUid(String uid) {
        if (uid == null || uid.isEmpty()) {
            return false;
        }

        // Check if user is the creator (usernamePost now stores UID)
        if (uid.equals(usernamePost)) {
            return true;
        }

        // Check if user is in the payors list (payorsList now stores UIDs)
        if (payorsList != null && payorsList.contains(uid)) {
            return true;
        }

        return false;
    }

    /**
     * Legacy check for backward compatibility with old username-based data.
     */
    public boolean isUserInvolvedByUsername(String username) {
        if (username == null || username.isEmpty()) {
            return false;
        }

        // Check poster display name or usernamePost (old data)
        if (username.equals(posterDisplayName) || username.equals(usernamePost)) {
            return true;
        }

        // Check payors display names or payorsList (old data)
        if (payorsDisplayNames != null && payorsDisplayNames.contains(username)) {
            return true;
        }
        if (payorsList != null && payorsList.contains(username)) {
            return true;
        }

        return false;
    }
}
