package com.example.amazonxcodebenders;

public class Expense {
    private String category;
    private double amount;
    private long timestamp;

    public Expense(String category, double amount, long timestamp) {
        this.category = category;
        this.amount = amount;
        this.timestamp = timestamp;
    }

    public String getCategory() {
        return category;
    }

    public double getAmount() {
        return amount;
    }

    public long getTimestamp() {
        return timestamp;
    }
}

