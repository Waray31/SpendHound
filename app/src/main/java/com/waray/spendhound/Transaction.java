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
    private List<String> payorsList;
    private List<Integer> amountsPaidList;
    private String usernamePost;
    private int totalIndividualPayment;
    private String groupId;
    private String groupName;

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
}
