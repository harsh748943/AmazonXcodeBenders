package com.example.amazonxcodebenders;

public class Transaction {
    public String id;
    public String amount;
    public String description;
    public long date;
    public String category;
    public String paymentMethod;
    public String type;

    public Transaction() {
        // Required empty constructor for Firebase
    }

    public Transaction(String id, String amount, String description, long date,
                       String category, String paymentMethod, String type) {
        this.id = id;
        this.amount = amount;
        this.description = description;
        this.date = date;
        this.category = category;
        this.paymentMethod = paymentMethod;
        this.type = type;
    }
}

