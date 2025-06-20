package com.example.amazonxcodebenders.paymentOptimization.offlinePayment;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;

import java.util.HashSet;
import java.util.Set;

public class WalletHelper {
    private static final String PREFS_NAME = "wallet_prefs";
    private static final String KEY_BALANCE = "balance_";
    private static final String KEY_SIGNATURE = "balance_signature_";
    private static final String KEY_PROCESSED_TXNS = "processed_txns";

    public static double getBalance(Context context, String userId) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return Double.longBitsToDouble(prefs.getLong(KEY_BALANCE + userId, Double.doubleToLongBits(0.0)));
    }

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

    public static void addBalance(Context context, String userId, double amount) {
        double current = getBalance(context, userId);
        setBalance(context, userId, current + amount);
    }

    public static void subtractBalance(Context context, String userId, double amount) {
        double current = getBalance(context, userId);
        setBalance(context, userId, current - amount);
    }

    // Duplicate txn check using txnId (persistent)
    public static boolean isDuplicateTransaction(Context context, String txnId) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        Set<String> processed = prefs.getStringSet(KEY_PROCESSED_TXNS, new HashSet<>());
        return processed.contains(txnId);
    }

    public static void markTransactionProcessed(Context context, String txnId) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        Set<String> processed = prefs.getStringSet(KEY_PROCESSED_TXNS, new HashSet<>());
        processed.add(txnId);
        prefs.edit().putStringSet(KEY_PROCESSED_TXNS, processed).apply();
    }
}
