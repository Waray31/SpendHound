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
    private double totalBillSpent;
    private double totalBillPayment;
    private double totalIndividualSpent;
    private double totaldebt;
    private double totalreceivable;

    // Default constructor required for Firebase
    public UserBalance() {
        this.totalBillSpent = 0.0;
        this.totalBillPayment = 0.0;
        this.totalIndividualSpent = 0.0;
        this.totaldebt = 0.0;
        this.totalreceivable = 0.0;
    }

    public UserBalance(double totalBillSpent, double totalBillPayment, double totalIndividualSpent,
                      double totaldebt, double totalreceivable) {
        this.totalBillSpent = totalBillSpent;
        this.totalBillPayment = totalBillPayment;
        this.totalIndividualSpent = totalIndividualSpent;
        this.totaldebt = totaldebt;
        this.totalreceivable = totalreceivable;
    }

    // Getters and Setters
    public double getTotalBillSpent() {
        return totalBillSpent;
    }

    public void setTotalBillSpent(double totalBillSpent) {
        this.totalBillSpent = totalBillSpent;
    }

    public double getTotalBillPayment() {
        return totalBillPayment;
    }

    public void setTotalBillPayment(double totalBillPayment) {
        this.totalBillPayment = totalBillPayment;
    }

    public double getTotalIndividualSpent() {
        return totalIndividualSpent;
    }

    public void setTotalIndividualSpent(double totalIndividualSpent) {
        this.totalIndividualSpent = totalIndividualSpent;
    }

    public double getTotaldebt() {
        return totaldebt;
    }

    public void setTotaldebt(double totaldebt) {
        this.totaldebt = totaldebt;
    }

    public double getTotalreceivable() {
        return totalreceivable;
    }

    public void setTotalreceivable(double totalreceivable) {
        this.totalreceivable = totalreceivable;
    }
}
