package com.example.amazonxcodebenders;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.telephony.SmsMessage;
import android.util.Base64;
import android.util.Log;
import android.widget.Toast;
import android.content.SharedPreferences;

import java.util.HashSet;
import java.util.Set;

public class SmsReceiver extends BroadcastReceiver {

    private static final String TXN_PREFS = "txn_prefs";
    private static final String TXN_SET = "txn_set";

    private boolean isTxnProcessed(Context context, String txnId) {
        SharedPreferences prefs = context.getSharedPreferences(TXN_PREFS, Context.MODE_PRIVATE);
        Set<String> set = prefs.getStringSet(TXN_SET, new HashSet<>());
        return set.contains(txnId);
    }

    private void markTxnProcessed(Context context, String txnId) {
        SharedPreferences prefs = context.getSharedPreferences(TXN_PREFS, Context.MODE_PRIVATE);
        Set<String> set = prefs.getStringSet(TXN_SET, new HashSet<>());
        set.add(txnId);
        prefs.edit().putStringSet(TXN_SET, set).apply();
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d("SMS_RECEIVER", "onReceive called!");
        Bundle bundle = intent.getExtras();
        if (bundle != null) {
            Object[] pdus = (Object[]) bundle.get("pdus");
            StringBuilder fullMessage = new StringBuilder();
            for (Object pdu : pdus) {
                SmsMessage sms = SmsMessage.createFromPdu((byte[]) pdu);
                fullMessage.append(sms.getMessageBody());
            }

            String encryptedMsg = fullMessage.toString();
            Log.d("SMS_RECEIVER", "Full Encrypted SMS: " + encryptedMsg);
            try {
                String decryptedMsg = CryptoHelper.decrypt(encryptedMsg);
                Log.d("SMS_RECEIVER", "Decrypted SMS: " + decryptedMsg);

                String[] parts = decryptedMsg.split("\\|");
                if (parts.length < 7) {
                    Toast.makeText(context, "Invalid transaction format", Toast.LENGTH_LONG).show();
                    Log.e("SMS_RECEIVER", "Invalid transaction format: " + decryptedMsg);
                    return;
                }

                String txnId = parts[0];
                String senderId = parts[1];
                String receiverId = parts[2];
                String amountStr = parts[3];
                String timestamp = parts[4];
                String txnSignatureBase64 = parts[5];
                String publicKeyBase64 = parts[6];

                String txnPayload = txnId + "|" + senderId + "|" + receiverId + "|" + amountStr + "|" + timestamp;

                if (!isTxnProcessed(context, txnId)) {
                    boolean isValid = false;
                    try {
                        isValid = KeyStoreHelper.verifyDataWithPublicKey(txnPayload, txnSignatureBase64, publicKeyBase64);
                    } catch (Exception ex) {
                        Log.e("SMS_RECEIVER", "Signature verify exception: ", ex);
                    }

                    if (isValid) {
                        double amount = Double.parseDouble(amountStr);
                        WalletHelper.addBalance(context, amount, publicKeyBase64);
                        markTxnProcessed(context, txnId);
                        Toast.makeText(context, "Received ₹" + amount + " from " + senderId, Toast.LENGTH_LONG).show();
                        Log.d("SMS_RECEIVER", "Amount credited: " + amount);
                    } else {
                        Toast.makeText(context, "Tampered transaction detected!", Toast.LENGTH_LONG).show();
                        Log.e("SMS_RECEIVER", "Invalid signature for txn: " + txnId);
                    }
                } else {
                    Log.d("SMS_RECEIVER", "Duplicate txn: " + txnId);
                }
            } catch (Exception e) {
                e.printStackTrace();
                Toast.makeText(context, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                Log.e("SMS_RECEIVER", "Exception: ", e);
            }
        }
    }
}
