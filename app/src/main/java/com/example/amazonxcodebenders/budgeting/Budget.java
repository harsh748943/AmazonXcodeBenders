package com.example.amazonxcodebenders.budgeting;

public class Budget {
    private String id;
    private String category;
    private double monthlyLimit;
    private long startDate; // Can be in milliseconds (epoch)
    private long endDate;   // Can be in milliseconds (epoch)

    // Constructor
    public Budget(String id, String category, double monthlyLimit, long startDate, long endDate) {
        this.id = id;
        this.category = category;
        this.monthlyLimit = monthlyLimit;
        this.startDate = startDate;
        this.endDate = endDate;
    }

    // Default constructor (required for Firebase or serialization)
    public Budget() {}

    // Getters and setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public double getMonthlyLimit() {
        return monthlyLimit;
    }

    public void setMonthlyLimit(double monthlyLimit) {
        this.monthlyLimit = monthlyLimit;
    }

    public long getStartDate() {
        return startDate;
    }

    public void setStartDate(long startDate) {
        this.startDate = startDate;
    }

    public long getEndDate() {
        return endDate;
    }

    public void setEndDate(long endDate) {
        this.endDate = endDate;
    }
}

