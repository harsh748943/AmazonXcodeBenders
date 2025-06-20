package com.example.amazonxcodebenders.billReminder;

public class Bill {

    private String Key;
    private String billType;
    private String amount;
    private String dueDate;
    private String source;

    private String description;


    public Bill(String billType, String amount, String dueDate, String source, String description) {
        this.billType = billType;
        this.amount = amount;
        this.dueDate = dueDate;
        this.source = source;
        this.description = description;
    }

    // Required for Firebase deserialization
    public Bill() {}


    public String getKey() { return Key; }
    public String getBillType() {
        return billType;
    }

    public String getAmount() {
        return amount;
    }

    public String getDueDate() {
        return dueDate;
    }

    public String getSource() {
        return source;
    }

    public String getDescription()
    {
        return description;
    }

    public void setKey(String Key) {
        this.Key = Key;
    }
}
