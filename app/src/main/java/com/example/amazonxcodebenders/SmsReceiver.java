package com.example.amazonxcodebenders;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.telephony.SmsMessage;
import android.util.Base64;
import android.widget.Toast;

import java.util.Arrays;

public class SmsReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        Bundle bundle = intent.getExtras();
        if (bundle == null) return;

        Object[] pdus = (Object[]) bundle.get("pdus");
        if (pdus == null) return;

        StringBuilder fullMessage = new StringBuilder();
        for (Object pdu : pdus) {
            SmsMessage sms = SmsMessage.createFromPdu((byte[]) pdu);
            fullMessage.append(sms.getMessageBody());
        }

        String message = fullMessage.toString();

        try {
            // Split message into parts
            String[] parts = message.split("\\|");
            if (parts.length < 7) {
                Toast.makeText(context, "Invalid transaction SMS", Toast.LENGTH_SHORT).show();
                return;
            }

            // Extract payload and signature
            String txnPayload = String.join("|", Arrays.copyOfRange(parts, 0, 5));
            String signatureBase64 = parts[5];
            String publicKeyBase64 = parts[6];

            // Verify signature
            boolean isValid = KeyStoreHelper.verifyDataWithPublicKey(txnPayload, signatureBase64, publicKeyBase64);
            if (!isValid) {
                Toast.makeText(context, "Invalid signature in transaction SMS", Toast.LENGTH_SHORT).show();
                return;
            }

            // Process transaction
            double amount = Double.parseDouble(parts[3]);

            // Check for duplicate transaction (implement your own logic)
            if (WalletHelper.isDuplicateTransaction(txnPayload)) {
                Toast.makeText(context, "Duplicate transaction ignored", Toast.LENGTH_SHORT).show();
                return;
            }

            // Credit wallet
            WalletHelper.addBalance(context, amount);

            Toast.makeText(context, "Received ₹" + amount + " successfully!", Toast.LENGTH_LONG).show();

        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(context, "Error processing transaction SMS", Toast.LENGTH_SHORT).show();
        }
    }
}
