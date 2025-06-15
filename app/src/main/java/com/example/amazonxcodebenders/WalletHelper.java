package com.example.amazonxcodebenders;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;

public class WalletHelper {

    private static final String PREFS = "wallet_prefs";
    private static final String BALANCE_KEY = "wallet_balance";
    private static final String SIGNATURE_KEY = "wallet_signature";
    private static final String PUBKEY_KEY = "wallet_pubkey";

    // Get wallet balance
    public static double getBalance(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        return Double.longBitsToDouble(prefs.getLong(BALANCE_KEY, Double.doubleToLongBits(0.0)));
    }

    // Store balance, its signature, and public key together
    public static void setBalance(Context context, double amount, String publicKeyBase64) {
        try {
            String data = "balance:" + amount;
            byte[] sig = KeyStoreHelper.signData(data); // Sign with local private key
            SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            prefs.edit()
                    .putLong(BALANCE_KEY, Double.doubleToLongBits(amount))
                    .putString(SIGNATURE_KEY, Base64.encodeToString(sig, Base64.NO_WRAP))
                    .putString(PUBKEY_KEY, publicKeyBase64)
                    .apply();
        } catch (Exception e) { }
    }

    // Overloaded for local use (when device itself updates balance)
    public static void setBalance(Context context, double amount) {
        try {
            String data = "balance:" + amount;
            byte[] sig = KeyStoreHelper.signData(data);
            String publicKeyBase64 = KeyStoreHelper.getPublicKeyBase64();
            SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            prefs.edit()
                    .putLong(BALANCE_KEY, Double.doubleToLongBits(amount))
                    .putString(SIGNATURE_KEY, Base64.encodeToString(sig, Base64.NO_WRAP))
                    .putString(PUBKEY_KEY, publicKeyBase64)
                    .apply();
        } catch (Exception e) { }
    }

    // Verify signature with the stored public key
    public static boolean isBalanceValid(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        double amount = getBalance(context);
        String data = "balance:" + amount;
        String sigBase64 = prefs.getString(SIGNATURE_KEY, "");
        String publicKeyBase64 = prefs.getString(PUBKEY_KEY, "");
        if (sigBase64.isEmpty() || publicKeyBase64.isEmpty()) return false;
        try {
            return KeyStoreHelper.verifyDataWithPublicKey(data, sigBase64, publicKeyBase64);
        } catch (Exception e) { return false; }
    }

    public static void addBalance(Context context, double amount, String publicKeyBase64) {
        double bal = getBalance(context) + amount;
        setBalance(context, bal, publicKeyBase64);
    }

    // Overloaded for local use (when device itself updates balance)
    public static void addBalance(Context context, double amount) {
        double bal = getBalance(context) + amount;
        setBalance(context, bal);
    }

    public static boolean subtractBalance(Context context, double amount) {
        double bal = getBalance(context);
        if (bal < amount) return false;
        setBalance(context, bal - amount);
        return true;
    }
}
