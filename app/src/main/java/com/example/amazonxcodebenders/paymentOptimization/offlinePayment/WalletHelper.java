// WalletHelper.java
package com.example.amazonxcodebenders.paymentOptimization.offlinePayment;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;
import com.google.gson.Gson;
import com.google.common.reflect.TypeToken;
import java.util.*;

public class WalletHelper {
    private static final String PREFS_NAME = "wallet_prefs";
    private static final String KEY_BALANCE = "balance_";
    private static final String KEY_SIGNATURE = "balance_signature_";
    private static final String KEY_PROCESSED_TXNS = "processed_txns";
    private static final String KEY_TXN_HISTORY = "txn_history";

    // Get balance (verifies signature)
    public static double getBalance(Context context, String userId) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return Double.longBitsToDouble(prefs.getLong(KEY_BALANCE + userId, Double.doubleToLongBits(0.0)));
    }

    // Set balance (signs value)
    public static void setBalance(Context context, String userId, double balance) {
        try {
            String balanceStr = Double.toString(balance);
            byte[] signature = KeyStoreHelper.signData(userId, balanceStr);
            String signatureBase64 = Base64.encodeToString(signature, Base64.NO_WRAP);
            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            prefs.edit()
                    .putLong(KEY_BALANCE + userId, Double.doubleToLongBits(balance))
                    .putString(KEY_SIGNATURE + userId, signatureBase64)
                    .apply();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Check signature validity
    public static boolean isBalanceValid(Context context, String userId) {
        try {
            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            double balance = Double.longBitsToDouble(prefs.getLong(KEY_BALANCE + userId, Double.doubleToLongBits(0.0)));
            String signatureBase64 = prefs.getString(KEY_SIGNATURE + userId, "");
            if (signatureBase64 == null || signatureBase64.isEmpty()) return false;
            String balanceStr = Double.toString(balance);
            return KeyStoreHelper.verifyDataWithPublicKey(balanceStr, signatureBase64, KeyStoreHelper.getPublicKeyBase64(userId));
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    // Add money
    public static void addBalance(Context context, String userId, double amount) {
        double current = getBalance(context, userId);
        setBalance(context, userId, current + amount);
    }

    // Subtract money
    public static void subtractBalance(Context context, String userId, double amount) {
        double current = getBalance(context, userId);
        setBalance(context, userId, current - amount);
    }

    // Transaction record model
    public static class TransactionRecord {
        public String txnId, encryptedPayload, signature, senderId, receiverId;
        public long timestamp;
        public boolean synced;
        public TransactionRecord(String txnId, String encryptedPayload, String signature, String senderId, String receiverId, long timestamp, boolean synced) {
            this.txnId = txnId;
            this.encryptedPayload = encryptedPayload;
            this.signature = signature;
            this.senderId = senderId;
            this.receiverId = receiverId;
            this.timestamp = timestamp;
            this.synced = synced;
        }
    }

    // Save transaction
    public static void saveTransaction(Context context, TransactionRecord txn) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        Gson gson = new Gson();
        List<TransactionRecord> txns = getAllTransactions(context);
        txns.add(txn);
        prefs.edit().putString(KEY_TXN_HISTORY, gson.toJson(txns)).apply();
    }

    // Get all transactions
    public static List<TransactionRecord> getAllTransactions(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String json = prefs.getString(KEY_TXN_HISTORY, "[]");
        Gson gson = new Gson();
        return gson.fromJson(json, new TypeToken<List<TransactionRecord>>(){}.getType());
    }

    // Duplicate txn check
    public static boolean isDuplicateTransaction(Context context, String txnId) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        Set<String> processed = prefs.getStringSet(KEY_PROCESSED_TXNS, new HashSet<>());
        return processed.contains(txnId);
    }

    // Mark txn processed
    public static void markTransactionProcessed(Context context, String txnId) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        Set<String> processed = prefs.getStringSet(KEY_PROCESSED_TXNS, new HashSet<>());
        processed.add(txnId);
        prefs.edit().putStringSet(KEY_PROCESSED_TXNS, processed).apply();
    }

    // WalletHelper.java के अंदर
    public static List<TransactionRecord> getUnsyncedTransactions(Context context) {
        List<TransactionRecord> all = getAllTransactions(context);
        List<TransactionRecord> unsynced = new ArrayList<>();
        for (TransactionRecord t : all) {
            if (!t.synced) unsynced.add(t);
        }
        return unsynced;
    }

    public static void markTransactionSynced(Context context, String txnId) {
        List<TransactionRecord> all = getAllTransactions(context);
        for (TransactionRecord t : all) {
            if (t.txnId.equals(txnId)) t.synced = true;
        }
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        Gson gson = new Gson();
        prefs.edit().putString(KEY_TXN_HISTORY, gson.toJson(all)).apply();
    }


}
