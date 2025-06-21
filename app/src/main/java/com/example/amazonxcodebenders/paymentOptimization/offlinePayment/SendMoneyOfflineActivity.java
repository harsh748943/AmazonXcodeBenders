package com.example.amazonxcodebenders.paymentOptimization.offlinePayment;

import android.Manifest;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.telephony.SmsManager;
import android.util.Base64;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;

import com.example.amazonxcodebenders.LoginActivity;
import com.example.amazonxcodebenders.R;
import com.google.android.material.button.MaterialButton;

import java.security.PublicKey;
import java.util.ArrayList;
import java.util.UUID;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;

public class SendMoneyOfflineActivity extends AppCompatActivity {

    private static final int SMS_PERMISSION_CODE = 101;
    private static final int QR_SCAN_CODE = 201;

    private WalletViewModel walletViewModel;
    private EditText etPhone, etAmount;
    private TextView tvWalletBalance, tvFeedback;

    private String recipientNumber = null;
    private String userId;

    // For buffering and confirmation
    private AlertDialog loadingDialog;
    private String pendingTxnId = null;
    private double pendingAmount = 0.0;
    private String pendingRecipient = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_send_money_offline);

        walletViewModel = new ViewModelProvider(
                this,
                ViewModelProvider.AndroidViewModelFactory.getInstance(getApplication())
        ).get(WalletViewModel.class);

        tvWalletBalance = findViewById(R.id.tvWalletBalance);
        tvFeedback = findViewById(R.id.tvFeedback);
        etPhone = findViewById(R.id.etPhone);
        etAmount = findViewById(R.id.etAmount);
        MaterialButton btnSendSMS = findViewById(R.id.btnSendSMS);

        userId = LoginActivity.getLoggedInUserPhone(this);

        etPhone.setEnabled(false);
        etPhone.setFocusable(false);

        // Wallet & Key Initialization
        try {
            boolean valid = WalletHelper.isBalanceValid(this, userId);
            Log.d("WalletInit", "isBalanceValid=" + valid + ", balance=" + WalletHelper.getBalance(this, userId));
            if (!valid) {
                WalletHelper.setBalance(this, userId, 2000.0);
                Log.d("WalletInit", "Initial balance set to 2000");
            }

            double currentBalance = WalletHelper.getBalance(this, userId);
            walletViewModel.setBalance(currentBalance);
        } catch (Exception e) {
            Log.e("WalletInit", "Initialization failed", e);
            Toast.makeText(this, "Wallet initialization failed!", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        walletViewModel.getBalance().observe(this, newBalance ->
                tvWalletBalance.setText("₹" + String.format("%.2f", newBalance))
        );

        findViewById(R.id.btnScanQr).setOnClickListener(v -> {
            Intent intent = new Intent(this, QRScannerActivity.class);
            startActivityForResult(intent, QR_SCAN_CODE);
        });

        btnSendSMS.setOnClickListener(v -> {
            String amountStr = etAmount.getText().toString().trim();
            if (recipientNumber == null) {
                showFeedback("Please scan recipient QR first.");
                return;
            }
            if (amountStr.isEmpty()) {
                showFeedback("Enter amount");
                return;
            }
            double amount;
            try {
                amount = Double.parseDouble(amountStr);
            } catch (NumberFormatException e) {
                showFeedback("Invalid amount format");
                return;
            }
            Double currentBalance = walletViewModel.getBalance().getValue();
            if (currentBalance == null) {
                showFeedback("Balance not loaded");
                return;
            }
            if (amount <= 0) {
                showFeedback("Amount must be > 0");
                return;
            }
            if (currentBalance < amount) {
                showFeedback("Insufficient balance");
                return;
            }
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.SEND_SMS},
                        SMS_PERMISSION_CODE);
                return;
            }
            authenticateAndSend(recipientNumber, amount);
        });

        // Register confirmation SMS receiver
        registerReceiver(confirmationReceiver, new IntentFilter("com.example.amazonxcodebenders.CONFIRMATION_SMS"));
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == QR_SCAN_CODE && resultCode == RESULT_OK && data != null) {
            String qrResult = data.getStringExtra("qr_result");
            if (qrResult != null && qrResult.startsWith("PubKey:")) {
                String[] parts = qrResult.substring(7).split("\\|");
                if (parts.length == 2) {
                    recipientNumber = parts[0];
                    String publicKeyBase64 = parts[1];
                    saveUserPublicKey(this, recipientNumber, publicKeyBase64);
                    etPhone.setText(recipientNumber);
                    showFeedback("Recipient set: " + recipientNumber);
                } else {
                    showFeedback("Invalid QR code format.");
                }
            } else {
                showFeedback("Not a valid payment QR.");
            }
        }
    }

    private void authenticateAndSend(final String phone, final double amount) {
        BiometricManager biometricManager = BiometricManager.from(this);
        int authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG
                | BiometricManager.Authenticators.DEVICE_CREDENTIAL;
        if (biometricManager.canAuthenticate(authenticators) == BiometricManager.BIOMETRIC_SUCCESS) {
            BiometricPrompt.PromptInfo promptInfo = new BiometricPrompt.PromptInfo.Builder()
                    .setTitle("Confirm Transaction")
                    .setSubtitle("Authenticate to send money")
                    .setAllowedAuthenticators(authenticators)
                    .build();
            BiometricPrompt biometricPrompt = new BiometricPrompt(this,
                    ContextCompat.getMainExecutor(this),
                    new BiometricPrompt.AuthenticationCallback() {
                        @Override
                        public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult result) {
                            showPinDialog(phone, amount);
                        }
                        @Override
                        public void onAuthenticationError(int errorCode, @NonNull CharSequence errString) {
                            showFeedback("Authentication cancelled");
                        }
                    });
            biometricPrompt.authenticate(promptInfo);
        } else {
            showPinDialog(phone, amount);
        }
    }

    private void showPinDialog(final String phone, final double amount) {
        LayoutInflater inflater = LayoutInflater.from(this);
        final View dialogView = inflater.inflate(R.layout.dialog_pin, null);
        final EditText etPin = dialogView.findViewById(R.id.etPin);
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setView(dialogView)
                .setTitle("PIN Required")
                .setPositiveButton("Confirm", (dialog, which) -> {
                    String enteredPin = etPin.getText().toString();
                    if (validatePin(enteredPin)) {
                        showLoadingAndSend(phone, amount);
                    } else {
                        showFeedback("Invalid PIN");
                    }
                })
                .setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss())
                .setCancelable(false);
        AlertDialog dialog = builder.create();
        dialog.show();
    }

    private boolean validatePin(String enteredPin) {
        return "2468".equals(enteredPin);
    }

    // Show buffering/loading dialog and send SMS
    private void showLoadingAndSend(final String phone, final double amount) {
        loadingDialog = new AlertDialog.Builder(this)
                .setView(getLayoutInflater().inflate(R.layout.dialog_loading, null))
                .setCancelable(false)
                .create();
        loadingDialog.show();

        new Handler().postDelayed(() -> {
            sendOfflinePaymentSMS(phone, amount);
            // Do NOT dismiss loading dialog yet; wait for confirmation SMS
        }, 1500);
    }

    private void showAnimatedSuccessDialog() {
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(getLayoutInflater().inflate(R.layout.dailog_success, null))
                .setCancelable(false)
                .create();
        dialog.show();
        new Handler().postDelayed(dialog::dismiss, 1500);
    }

    private void resetTransactionFields() {
        etPhone.setText("");
        etAmount.setText("");
        tvFeedback.setText("");
        recipientNumber = null;
    }

    // Send payment SMS and save pending txn info for confirmation
    private void sendOfflinePaymentSMS(String receiverPhone, double amount) {
        try {
            Log.d("SenderFlow", "Starting transaction...");

            String receiverPublicKeyBase64 = getReceiverPublicKey(receiverPhone);
            if (receiverPublicKeyBase64 == null) {
                showFeedback("Receiver's public key not found. Please scan their QR.");
                if (loadingDialog != null) loadingDialog.dismiss();
                return;
            }

            // 1. Generate NEW AES key for this transaction (not from KeyStore)
            KeyGenerator keyGen = KeyGenerator.getInstance("AES");
            keyGen.init(256); // 256-bit key
            SecretKey aesKey = keyGen.generateKey();
            byte[] aesKeyBytes = aesKey.getEncoded();

            Log.d("SenderFlow", "AES Key generated (raw bytes): " + Base64.encodeToString(aesKeyBytes, Base64.NO_WRAP));
            // 1. Encrypt payload with AES
            String txnId = UUID.randomUUID().toString();
            String txnPayload = txnId + "|" + userId + "|" + receiverPhone + "|" + amount + "|" + System.currentTimeMillis();
            Log.d("SenderFlow", "Transaction payload: " + txnPayload);
            // 3. Add null check before encryption
            if (txnPayload == null) {
                throw new IllegalArgumentException("Transaction payload is null");
            }
            String aesEncryptedPayload = CryptoHelper.encryptWithKey(txnPayload,aesKey);

            Log.d("SenderFlow", "AES encrypted payload: " + aesEncryptedPayload);
            // 3. Encrypt AES key with receiver's public key
            PublicKey receiverPublicKey = KeyStoreHelper.getPublicKeyFromBase64(receiverPublicKeyBase64);

            Log.d("SenderFlow", "Receiver's public key (Base64): " + receiverPublicKeyBase64);
            Cipher rsaCipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
            rsaCipher.init(Cipher.ENCRYPT_MODE, receiverPublicKey);
            byte[] encryptedAesKey = rsaCipher.doFinal(aesKeyBytes);
            String encryptedAesKeyBase64 = Base64.encodeToString(encryptedAesKey, Base64.NO_WRAP);

            Log.d("SenderFlow", "Encrypted AES key (RSA, Base64): " + encryptedAesKeyBase64);
            // 3. Sign the AES-encrypted payload with sender's private key
            byte[] signature = KeyStoreHelper.signData(userId, aesEncryptedPayload);
            String signatureBase64 = Base64.encodeToString(signature, Base64.NO_WRAP);
            Log.d("SenderFlow", "Signature (Base64): " + signatureBase64);
            String publicKeyBase64 = KeyStoreHelper.getPublicKeyBase64(userId);
            Log.d("SenderFlow", "Sender's public key (Base64): " + publicKeyBase64);
            // 4. SMS format: <encAESKey>|<aesEncPayload>|<signature>|<userId>|<pubKey>
            String smsBody = encryptedAesKeyBase64 + "|" + aesEncryptedPayload + "|" + signatureBase64 + "|" + userId + "|" + publicKeyBase64;
            Log.d("SenderFlow", "SMS body to send: " + smsBody);
            SmsManager smsManager = SmsManager.getDefault();
            if (smsBody.length() > 160) {
                ArrayList<String> parts = smsManager.divideMessage(smsBody);
                smsManager.sendMultipartTextMessage(receiverPhone, null, parts, null, null);
            } else {
                smsManager.sendTextMessage(receiverPhone, null, smsBody, null, null);
            }
            Log.d("SenderFlow", "SMS sent to: " + receiverPhone);
            // Save pending transaction info for confirmation
            pendingTxnId = txnId;
            pendingAmount = amount;
            pendingRecipient = receiverPhone;

            showFeedback("Waiting for confirmation from " + receiverPhone + "...");

            // After sending SMS and before showFeedback("Waiting for confirmation...")
// Save transaction locally for offline history and future sync
            WalletHelper.TransactionRecord txnRecord = new WalletHelper.TransactionRecord(
                    txnId, aesEncryptedPayload, signatureBase64, userId, receiverPhone, System.currentTimeMillis(), false
            );
            WalletHelper.saveTransaction(this, txnRecord);


        } catch (Exception e) {
            Log.e("SendSMS", "Transaction failed", e);
            showFeedback("Error: " + e.getMessage());
            if (loadingDialog != null) loadingDialog.dismiss();
        }
    }

    // Receiver for confirmation SMS
    private final BroadcastReceiver confirmationReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String txnId = intent.getStringExtra("txnId");
            double amount = intent.getDoubleExtra("amount", 0.0);
            String senderPhone = intent.getStringExtra("senderPhone");
            boolean success = intent.getBooleanExtra("success", false);

            if (pendingTxnId != null && txnId.equals(pendingTxnId) && success) {
                // Debit after confirmation
                WalletHelper.subtractBalance(SendMoneyOfflineActivity.this, userId, pendingAmount);
                walletViewModel.setBalance(WalletHelper.getBalance(SendMoneyOfflineActivity.this, userId));
                showFeedback("₹" + pendingAmount + " sent to " + pendingRecipient + " successfully!");
                showAnimatedSuccessDialog();
                Intent intent1 = new Intent("com.example.amazonxcodebenders.WALLET_UPDATED");
                context.sendBroadcast(intent1);
            } else if (!success) {
                showFeedback("Transaction failed or rejected by recipient.");
            }

            if (loadingDialog != null) loadingDialog.dismiss();
            pendingTxnId = null;
            pendingAmount = 0.0;
            pendingRecipient = null;
        }
    };

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(confirmationReceiver);
    }

    private void showFeedback(String msg) {
        tvFeedback.setText(msg);
        tvFeedback.setVisibility(TextView.VISIBLE);
    }

    // --- PUBLIC KEY STORAGE/RETRIEVAL HELPERS ---
    public static void saveUserPublicKey(android.content.Context context, String userId, String publicKeyBase64) {
        android.content.SharedPreferences prefs = context.getSharedPreferences("public_keys", android.content.Context.MODE_PRIVATE);
        prefs.edit().putString(userId, publicKeyBase64).apply();
    }

    private String getReceiverPublicKey(String userId) {
        android.content.SharedPreferences prefs = getSharedPreferences("public_keys", MODE_PRIVATE);
        return prefs.getString(userId, null);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == SMS_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                if (recipientNumber != null && !etAmount.getText().toString().trim().isEmpty()) {
                    try {
                        double amount = Double.parseDouble(etAmount.getText().toString().trim());
                        authenticateAndSend(recipientNumber, amount);
                    } catch (Exception e) {
                        showFeedback("Retry failed: Invalid input");
                    }
                }
            } else {
                showFeedback("SMS permission denied");
            }
        }
    }
}
