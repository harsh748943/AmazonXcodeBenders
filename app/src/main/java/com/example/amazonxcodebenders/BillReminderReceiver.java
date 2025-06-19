package com.example.amazonxcodebenders;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.telephony.SmsMessage;
import android.util.Log;

import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

public class BillReminderReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        Bundle bundle = intent.getExtras();
        if (bundle == null) return;

        Object[] pdus = (Object[]) bundle.get("pdus");
        if (pdus == null) return;

        for (Object pdu : pdus) {
            SmsMessage sms = SmsMessage.createFromPdu((byte[]) pdu);
            String sender = sms.getOriginatingAddress();
            String messageBody = sms.getMessageBody();

            // Basic filter
            if (messageBody.toLowerCase().contains("due") ||
                    messageBody.toLowerCase().contains("bill") ||
                    messageBody.toLowerCase().contains("payment")) {

                // Call AI to extract bill details
                OpenAIHelper.extractBillInfo(messageBody, new OpenAIHelper.BillCallback() {
                    @Override
                    public void onExtracted(String amount, String dueDate, String description) {
                        DatabaseReference ref = FirebaseDatabase.getInstance()
                                .getReference("bills")
                                .push();

                        ref.setValue(new BillModel(sender, messageBody, amount, dueDate, description));
                        Log.d("BillReminderReceiver", "Bill saved with AI: " + messageBody);
                    }

                    @Override
                    public void onFailure(Exception e) {
                        e.printStackTrace();
                        Log.e("BillReminderReceiver", "AI extraction failed: " + e.getMessage());
                    }
                });
            }
        }
    }

    public static class BillModel {
        public String sender, message, amount, dueDate, description;

        public BillModel() {}

        public BillModel(String sender, String message, String amount, String dueDate, String description) {
            this.sender = sender;
            this.message = message;
            this.amount = amount;
            this.dueDate = dueDate;
            this.description = description;
        }
    }
}
