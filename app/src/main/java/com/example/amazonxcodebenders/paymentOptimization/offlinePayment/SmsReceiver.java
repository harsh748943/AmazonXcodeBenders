package com.example.amazonxcodebenders.paymentOptimization.offlinePayment;

import static com.example.amazonxcodebenders.LoginActivity.getLoggedInUserPhone;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.telephony.SmsManager;
import android.telephony.SmsMessage;
import android.util.Base64;
import android.widget.Toast;

import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.ArrayList;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

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
        String[] parts = message.split("\\|");

        // Distinguish between transaction SMS and confirmation SMS
        if (parts.length == 5) {
            handleTransactionSms(context, parts);
        } else if (parts.length >= 10 && "CONFIRM".equals(parts[0])) {
            handleConfirmationSms(context, parts);
        }
    }

    private void handleTransactionSms(Context context, String[] parts) {
        String encryptedAesKeyBase64 = parts[0];
        String aesEncryptedPayload = parts[1];
        String signatureBase64 = parts[2];
        String senderUserId = parts[3];
        String senderPublicKeyBase64 = parts[4];

        try {
            String myUserId = getLoggedInUserPhone(context);
            PrivateKey privateKey = KeyStoreHelper.getPrivateKey(myUserId);
            Cipher rsaCipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
            rsaCipher.init(Cipher.DECRYPT_MODE, privateKey);
            byte[] encryptedAesKey = Base64.decode(encryptedAesKeyBase64, Base64.NO_WRAP);
            byte[] aesKeyBytes = rsaCipher.doFinal(encryptedAesKey);

            SecretKeySpec aesKeySpec = new SecretKeySpec(aesKeyBytes, "AES");
            String txnPayload = CryptoHelper.decrypt(aesEncryptedPayload, aesKeySpec);

            boolean isValid = KeyStoreHelper.verifyDataWithPublicKey(aesEncryptedPayload, signatureBase64, senderPublicKeyBase64);
            if (!isValid) {
                Toast.makeText(context, "Invalid signature in transaction SMS", Toast.LENGTH_SHORT).show();
                return;
            }

            String[] payloadParts = txnPayload.split("\\|");
            String txnId = payloadParts[0];
            String senderPhone = payloadParts[1];
            double amount = Double.parseDouble(payloadParts[3]);

            if (WalletHelper.isDuplicateTransaction(context, txnId)) {
                Toast.makeText(context, "Duplicate transaction ignored", Toast.LENGTH_SHORT).show();
                return;
            }

            WalletHelper.addBalance(context, myUserId, amount);
            WalletHelper.markTransactionProcessed(context, txnId);
            Toast.makeText(context, "Received ₹" + amount + " successfully!", Toast.LENGTH_LONG).show();

            // Send confirmation SMS to sender
            sendConfirmationSms(context, senderPhone, txnId, amount, true, senderPublicKeyBase64, myUserId);

        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(context, "Error processing transaction SMS", Toast.LENGTH_SHORT).show();
        }
    }

    private void sendConfirmationSms(Context context, String senderPhone, String txnId, double amount, boolean success, String senderPublicKeyBase64, String myUserId) {
        try {
            // Generate NEW AES key for this confirmation
            KeyGenerator keyGen = KeyGenerator.getInstance("AES");
            keyGen.init(256);
            SecretKey aesKey = keyGen.generateKey();
            byte[] aesKeyBytes = aesKey.getEncoded();

            // Prepare confirmation payload
            // 2. Build payload with null checks
            String confirmPayload = "CONFIRM|"
                    + (txnId != null ? txnId : "INVALID_TXN") + "|"
                    + amount + "|"
                    + System.currentTimeMillis() + "|"
                    + (success ? "SUCCESS" : "FAIL");

            // Encrypt payload with new AES key
            String aesEncryptedPayload = CryptoHelper.encryptWithKey(confirmPayload, aesKey);
            // Encrypt AES key with sender's RSA public key
            PublicKey senderPublicKey = KeyStoreHelper.getPublicKeyFromBase64(senderPublicKeyBase64);
            Cipher rsaCipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
            rsaCipher.init(Cipher.ENCRYPT_MODE, senderPublicKey);
            byte[] encryptedAesKey = rsaCipher.doFinal(aesKeyBytes);
            String encryptedAesKeyBase64 = Base64.encodeToString(encryptedAesKey, Base64.NO_WRAP);

            byte[] signature = KeyStoreHelper.signData(myUserId, aesEncryptedPayload);
            String signatureBase64 = Base64.encodeToString(signature, Base64.NO_WRAP);
            String myPublicKeyBase64 = KeyStoreHelper.getPublicKeyBase64(myUserId);

            // SMS format: CONFIRM|txnId|amount|timestamp|status|encAESKey|aesEncPayload|signature|receiverUserId|receiverPubKey
            String smsBody = "CONFIRM|" + txnId + "|" + amount + "|" + System.currentTimeMillis() + "|" + (success ? "SUCCESS" : "FAIL")
                    + "|" + encryptedAesKeyBase64 + "|" + aesEncryptedPayload + "|" + signatureBase64 + "|" + myUserId + "|" + myPublicKeyBase64;

            SmsManager smsManager = SmsManager.getDefault();
            if (smsBody.length() > 160) {
                ArrayList<String> parts = smsManager.divideMessage(smsBody);
                smsManager.sendMultipartTextMessage(senderPhone, null, parts, null, null);
            } else {
                smsManager.sendTextMessage(senderPhone, null, smsBody, null, null);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void handleConfirmationSms(Context context, String[] parts) {
        // Format: CONFIRM|txnId|amount|timestamp|status|encAESKey|aesEncPayload|signature|receiverUserId|receiverPubKey
        if (parts.length < 10) return;
        String txnId = parts[1];
        double amount = Double.parseDouble(parts[2]);
        String status = parts[4];
        String encryptedAesKeyBase64 = parts[5];
        String aesEncryptedPayload = parts[6];
        String signatureBase64 = parts[7];
        String receiverUserId = parts[8];
        String receiverPublicKeyBase64 = parts[9];

        try {
            String myUserId = getLoggedInUserPhone(context);
            PrivateKey privateKey = KeyStoreHelper.getPrivateKey(myUserId);
            Cipher rsaCipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
            rsaCipher.init(Cipher.DECRYPT_MODE, privateKey);
            byte[] encryptedAesKey = Base64.decode(encryptedAesKeyBase64, Base64.NO_WRAP);
            byte[] aesKeyBytes = rsaCipher.doFinal(encryptedAesKey);

            SecretKeySpec aesKeySpec = new SecretKeySpec(aesKeyBytes, "AES");
            String confirmPayload = CryptoHelper.decrypt(aesEncryptedPayload, aesKeySpec);

            boolean isValid = KeyStoreHelper.verifyDataWithPublicKey(aesEncryptedPayload, signatureBase64, receiverPublicKeyBase64);
            if (!isValid) return;

            // Broadcast to activity for UI update & balance debit
            Intent uiIntent = new Intent("com.example.amazonxcodebenders.CONFIRMATION_SMS");
            uiIntent.putExtra("txnId", txnId);
            uiIntent.putExtra("amount", amount);
            uiIntent.putExtra("senderPhone", receiverUserId);
            uiIntent.putExtra("success", "SUCCESS".equals(status));
            context.sendBroadcast(uiIntent);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
