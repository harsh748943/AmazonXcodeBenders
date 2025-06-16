package com.example.amazonxcodebenders;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.telephony.SmsManager;
import android.util.Base64;
import android.util.Log;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.UUID;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;
import android.app.AlertDialog;
import android.view.LayoutInflater;
import android.view.View;

public class SendMoneyOfflineActivity extends AppCompatActivity {

    private static final int SMS_PERMISSION_CODE = 101;
    private WalletViewModel walletViewModel;
    private EditText etPhone, etAmount;
    private TextView tvWalletBalance, tvFeedback;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_send_money_offline);

        walletViewModel = new ViewModelProvider(this).get(WalletViewModel.class);
        tvWalletBalance = findViewById(R.id.tvWalletBalance);
        tvFeedback = findViewById(R.id.tvFeedback);
        etPhone = findViewById(R.id.etPhone);
        etAmount = findViewById(R.id.etAmount);
        MaterialButton btnSendSMS = findViewById(R.id.btnSendSMS);

        // Wallet & Key Initialization
        try {
            KeyStoreHelper.generateKey();
            if (!WalletHelper.isBalanceValid(this)) {
                WalletHelper.setBalance(this, 2000.0);
                Log.d("WalletInit", "Initial balance set to 2000");
            }
            double currentBalance = WalletHelper.getBalance(this);
            walletViewModel.setBalance(currentBalance);
        } catch (Exception e) {
            Log.e("WalletInit", "Initialization failed", e);
            Toast.makeText(this, "Wallet initialization failed!", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        // Observe balance changes for UI
        walletViewModel.getBalance().observe(this, newBalance ->
                tvWalletBalance.setText("₹" + String.format("%.2f", newBalance))
        );

        btnSendSMS.setOnClickListener(v -> {
            String phone = etPhone.getText().toString().trim();
            String amountStr = etAmount.getText().toString().trim();

            // Input validation
            if (phone.isEmpty() || phone.length() < 10) {
                showFeedback("Invalid phone number");
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
            // Premium: Always require biometric + PIN before transaction
            authenticateAndSend(phone, amount);
        });
    }

    // Biometric + PIN authentication before sending SMS
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
            // Fallback: PIN only
            showPinDialog(phone, amount);
        }
    }

    // Premium PIN dialog -> loading -> animated success
    private void showPinDialog(final String phone, final double amount) {
        LayoutInflater inflater = LayoutInflater.from(this);
        final View dialogView = inflater.inflate(R.layout.dialog_pin, null);
        final EditText etPin = dialogView.findViewById(R.id.etPin);

        AlertDialog.Builder builder = new AlertDialog.Builder(
                this,
                com.google.android.material.R.style.Theme_Material3_DayNight_Dialog
        );
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

        // Create and show the dialog
        AlertDialog dialog = builder.create();
        dialog.show();

        // Set button colors after showing the dialog
        int orangeYellowColor = ContextCompat.getColor(this, R.color.orange_yellow);
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(orangeYellowColor);
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(orangeYellowColor);
    }


    // Show loading, then animated success, then reset fields
    private void showLoadingAndSend(final String phone, final double amount) {
        // Show loading dialog
        AlertDialog loadingDialog = new AlertDialog.Builder(this,
                com.google.android.material.R.style.Theme_Material3_DayNight_Dialog)
                .setView(getLayoutInflater().inflate(R.layout.dialog_loading, null))
                .setCancelable(false)
                .create();
        loadingDialog.show();

        // Simulate transaction
        new Handler().postDelayed(() -> {
            sendOfflinePaymentSMS(phone, amount);
            loadingDialog.dismiss();
            showAnimatedSuccessDialog();
        }, 1500);
    }

    private void showAnimatedSuccessDialog() {
        AlertDialog dialog = new AlertDialog.Builder(this,
                com.google.android.material.R.style.Theme_Material3_DayNight_Dialog)
                .setView(getLayoutInflater().inflate(R.layout.dailog_success, null))
                .setCancelable(false)
                .create();
        dialog.show();
        new Handler().postDelayed(() -> {
            dialog.dismiss();
            resetTransactionFields();
        }, 1500);
    }

    private void resetTransactionFields() {
        etPhone.setText("");
        etAmount.setText("");
        tvFeedback.setText("");
    }

    // Replace this with your actual PIN validation logic
    private boolean validatePin(String enteredPin) {
        // For demo: PIN is "2468"
        return "2468".equals(enteredPin);
    }

    private void sendOfflinePaymentSMS(String receiverPhone, double amount) {
        try {
            String txnId = UUID.randomUUID().toString();
            String txnPayload = txnId + "|Me|" + receiverPhone + "|" + amount + "|" + System.currentTimeMillis();
            byte[] signature = KeyStoreHelper.signData(txnPayload);
            String signatureBase64 = Base64.encodeToString(signature, Base64.NO_WRAP);
            String publicKeyBase64 = KeyStoreHelper.getPublicKeyBase64();
            String fullPayload = txnPayload + "|" + signatureBase64 + "|" + publicKeyBase64;

            SmsManager smsManager = SmsManager.getDefault();
            if (fullPayload.length() > 160) {
                ArrayList<String> parts = smsManager.divideMessage(fullPayload);
                smsManager.sendMultipartTextMessage(receiverPhone, null, parts, null, null);
            } else {
                smsManager.sendTextMessage(receiverPhone, null, fullPayload, null, null);
            }

            WalletHelper.subtractBalance(this, amount);
            walletViewModel.setBalance(WalletHelper.getBalance(this));
            showFeedback("₹" + amount + " sent to " + receiverPhone);
        } catch (Exception e) {
            Log.e("SendSMS", "Transaction failed", e);
            showFeedback("Error: " + e.getMessage());
        }
    }

    private void showFeedback(String msg) {
        tvFeedback.setText(msg);
        tvFeedback.setVisibility(TextView.VISIBLE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == SMS_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                String phone = etPhone.getText().toString().trim();
                String amountStr = etAmount.getText().toString().trim();
                if (!phone.isEmpty() && !amountStr.isEmpty()) {
                    try {
                        double amount = Double.parseDouble(amountStr);
                        authenticateAndSend(phone, amount);
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
