package com.waray.spendhound;

/**
 * Model class representing a breakdown item for financial summary display.
 * Used in the breakdown dialog to show detailed transaction information.
 */
public class BreakdownItem {

    public enum Category {
        BALANCE,
        UNPAID,
        OWE,
        DEBT
    }

    private Category category;
    private String date;
    private String personName;
    private double amount;
    private String status;
    private String description;

    public BreakdownItem() {
        // Default constructor
    }

    public BreakdownItem(Category category, String date, String personName, double amount, String status) {
        this.category = category;
        this.date = date;
        this.personName = personName;
        this.amount = amount;
        this.status = status;
    }

    public BreakdownItem(Category category, String date, String personName, double amount, String status, String description) {
        this.category = category;
        this.date = date;
        this.personName = personName;
        this.amount = amount;
        this.status = status;
        this.description = description;
    }

    public Category getCategory() {
        return category;
    }

    public void setCategory(Category category) {
        this.category = category;
    }

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }

    public String getPersonName() {
        return personName;
    }

    public void setPersonName(String personName) {
        this.personName = personName;
    }

    public double getAmount() {
        return amount;
    }

    public void setAmount(double amount) {
        this.amount = amount;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
