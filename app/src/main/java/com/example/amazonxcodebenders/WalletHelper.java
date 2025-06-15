package com.example.amazonxcodebenders;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;

import java.nio.charset.StandardCharsets;

public class WalletHelper {

    private static final String PREFS_NAME = "wallet_prefs";
    private static final String KEY_BALANCE = "balance";
    private static final String KEY_SIGNATURE = "balance_signature";

    public static double getBalance(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return Double.longBitsToDouble(prefs.getLong(KEY_BALANCE, Double.doubleToLongBits(0.0)));
    }

    public static void setBalance(Context context, double balance) {
        try {
            String balanceStr = Double.toString(balance);
            byte[] signature = KeyStoreHelper.signData(balanceStr);
            String signatureBase64 = Base64.encodeToString(signature, Base64.NO_WRAP);

            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            prefs.edit()
                    .putLong(KEY_BALANCE, Double.doubleToLongBits(balance))
                    .putString(KEY_SIGNATURE, signatureBase64)
                    .apply();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static boolean isBalanceValid(Context context) {
        try {
            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            double balance = Double.longBitsToDouble(prefs.getLong(KEY_BALANCE, Double.doubleToLongBits(0.0)));
            String signatureBase64 = prefs.getString(KEY_SIGNATURE, "");
            if (signatureBase64 == null || signatureBase64.isEmpty()) return false;

            String balanceStr = Double.toString(balance);
            return KeyStoreHelper.verifyDataWithPublicKey(balanceStr, signatureBase64, KeyStoreHelper.getPublicKeyBase64());
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public static void addBalance(Context context, double amount) {
        double current = getBalance(context);
        setBalance(context, current + amount);
    }

    public static void subtractBalance(Context context, double amount) {
        double current = getBalance(context);
        setBalance(context, current - amount);
    }

    // Implement your own duplicate transaction check logic
    public static boolean isDuplicateTransaction(String txnPayload) {
        // For demo, always return false
        return false;
    }
}
