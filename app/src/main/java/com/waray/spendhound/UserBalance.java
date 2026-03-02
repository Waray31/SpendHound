package com.waray.spendhound;

/**
 * Model class for user balance data stored under users/{uid}/balances
 *
 * Field definitions:
 * - totalBillSpent: Sum of paymentAmount in all transactions where user is in payorsList
 * - totalBillPayment: Sum of user's individual amounts from amountsPaidList in all transactions
 * - totalIndividualSpent: Sum of totalIndividualPayment for each transaction user participated in
 * - totaldebt: Sum of borrow amounts where user is borrower with status != "Paid"
 * - totalreceivable: Sum of borrow amounts where user is lender with status != "Paid"
 */
public class UserBalance {
    private int totalBillSpent;
    private int totalBillPayment;
    private int totalIndividualSpent;
    private int totaldebt;
    private int totalreceivable;

    // Default constructor required for Firebase
    public UserBalance() {
        this.totalBillSpent = 0;
        this.totalBillPayment = 0;
        this.totalIndividualSpent = 0;
        this.totaldebt = 0;
        this.totalreceivable = 0;
    }

    public UserBalance(int totalBillSpent, int totalBillPayment, int totalIndividualSpent,
                      int totaldebt, int totalreceivable) {
        this.totalBillSpent = totalBillSpent;
        this.totalBillPayment = totalBillPayment;
        this.totalIndividualSpent = totalIndividualSpent;
        this.totaldebt = totaldebt;
        this.totalreceivable = totalreceivable;
    }

    // Getters and Setters
    public int getTotalBillSpent() {
        return totalBillSpent;
    }

    public void setTotalBillSpent(int totalBillSpent) {
        this.totalBillSpent = totalBillSpent;
    }

    public int getTotalBillPayment() {
        return totalBillPayment;
    }

    public void setTotalBillPayment(int totalBillPayment) {
        this.totalBillPayment = totalBillPayment;
    }

    public int getTotalIndividualSpent() {
        return totalIndividualSpent;
    }

    public void setTotalIndividualSpent(int totalIndividualSpent) {
        this.totalIndividualSpent = totalIndividualSpent;
    }

    public int getTotaldebt() {
        return totaldebt;
    }

    public void setTotaldebt(int totaldebt) {
        this.totaldebt = totaldebt;
    }

    public int getTotalreceivable() {
        return totalreceivable;
    }

    public void setTotalreceivable(int totalreceivable) {
        this.totalreceivable = totalreceivable;
    }
}

