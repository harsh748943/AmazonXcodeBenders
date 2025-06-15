package com.example.amazonxcodebenders;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
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
import java.util.Locale;
import java.util.UUID;

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

        // ================== Key Fixes ==================
        try {
            // 1. Ensure keys are generated
            KeyStoreHelper.generateKey();

            // 2. Initialize wallet only if invalid
            if (!WalletHelper.isBalanceValid(this)) {
                WalletHelper.setBalance(this, 2000.0);
                Log.d("WalletInit", "Initial balance set to 2000");
            }

            // 3. Sync ViewModel with actual balance
            double currentBalance = WalletHelper.getBalance(this);
            walletViewModel.setBalance(currentBalance);
            Log.d("WalletInit", "ViewModel synced: " + currentBalance);

        } catch (Exception e) {
            Log.e("WalletInit", "Initialization failed", e);
            Toast.makeText(this, "Wallet initialization failed!", Toast.LENGTH_LONG).show();
            finish(); // Critical error, close activity
            return;
        }

        // Observe balance changes
        walletViewModel.getBalance().observe(this, newBalance -> {
            tvWalletBalance.setText("₹" + String.format("%.2f", newBalance));
            Log.d("BalanceUpdate", "UI updated: " + newBalance);
        });

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

            // Convert amount
            double amount;
            try {
                amount = Double.parseDouble(amountStr);
            } catch (NumberFormatException e) {
                showFeedback("Invalid amount format");
                return;
            }

            // Get latest balance from ViewModel
            Double currentBalance = walletViewModel.getBalance().getValue();
            if (currentBalance == null) {
                showFeedback("Balance not loaded");
                return;
            }

            // Balance checks
            if (amount <= 0) {
                showFeedback("Amount must be > 0");
                return;
            }
            if (currentBalance < amount) {
                showFeedback("Insufficient balance");
                return;
            }

            // Permission check
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.SEND_SMS},
                        SMS_PERMISSION_CODE);
                return;
            }

            sendOfflinePaymentSMS(phone, amount);
        });
    }

    private void sendOfflinePaymentSMS(String receiverPhone, double amount) {
        try {
            // 1. Prepare transaction payload
            String txnId = UUID.randomUUID().toString();
            String txnPayload = txnId + "|Me|" + receiverPhone + "|" + amount + "|" + System.currentTimeMillis();

            // 2. Sign payload
            byte[] signature = KeyStoreHelper.signData(txnPayload);
            String signatureBase64 = Base64.encodeToString(signature, Base64.NO_WRAP);
            String publicKeyBase64 = KeyStoreHelper.getPublicKeyBase64();

            // 3. Build final message
            String fullPayload = txnPayload + "|" + signatureBase64 + "|" + publicKeyBase64;

            // 4. Send SMS
            SmsManager smsManager = SmsManager.getDefault();
            if (fullPayload.length() > 160) {
                ArrayList<String> parts = smsManager.divideMessage(fullPayload);
                smsManager.sendMultipartTextMessage(receiverPhone, null, parts, null, null);
            } else {
                smsManager.sendTextMessage(receiverPhone, null, fullPayload, null, null);
            }

            // 5. Update balance
            WalletHelper.subtractBalance(this, amount);
            walletViewModel.setBalance(WalletHelper.getBalance(this)); // Sync ViewModel

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
                // Retry if fields are valid
                String phone = etPhone.getText().toString().trim();
                String amountStr = etAmount.getText().toString().trim();
                if (!phone.isEmpty() && !amountStr.isEmpty()) {
                    try {
                        double amount = Double.parseDouble(amountStr);
                        sendOfflinePaymentSMS(phone, amount);
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
