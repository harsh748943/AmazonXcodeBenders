package com.example.amazonxcodebenders;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.telephony.SmsManager;
import android.util.Log;
import android.widget.*;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    EditText phoneInput, amountInput;
    Button sendBtn, checkBalanceBtn;
    TextView balanceText;

    private static final int SMS_PERMISSION_REQUEST = 100;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        phoneInput = findViewById(R.id.phoneInput);
        amountInput = findViewById(R.id.amountInput);
        sendBtn = findViewById(R.id.sendBtn);
        checkBalanceBtn = findViewById(R.id.checkBalanceBtn);
        balanceText = findViewById(R.id.balanceText);

        // Runtime permissions for SMS send/receive/read
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.SEND_SMS}, SMS_PERMISSION_REQUEST);
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECEIVE_SMS}, 101);
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_SMS}, 102);
        }

        try { KeyStoreHelper.generateKey(); } catch (Exception e) { }

        if (!WalletHelper.isBalanceValid(this)) {
            WalletHelper.setBalance(this, 2000.0); // First time setup
        }

        updateBalance();

        sendBtn.setOnClickListener(v -> {
            String phone = phoneInput.getText().toString().trim();
            String amountStr = amountInput.getText().toString().trim();

            // Input validation
            if (phone.isEmpty() || phone.length() < 10) {
                Toast.makeText(this, "Please enter a valid phone number", Toast.LENGTH_SHORT).show();
                return;
            }

            if (amountStr.isEmpty()) {
                Toast.makeText(this, "Please enter amount", Toast.LENGTH_SHORT).show();
                return;
            }

            double amount;
            try {
                amount = Double.parseDouble(amountStr);
            } catch (NumberFormatException e) {
                Toast.makeText(this, "Invalid amount", Toast.LENGTH_SHORT).show();
                return;
            }

            if (!WalletHelper.isBalanceValid(this)) {
                Toast.makeText(this, "Wallet tampered! Resetting.", Toast.LENGTH_LONG).show();
                WalletHelper.setBalance(this, 2000.0);
                updateBalance();
                return;
            }

            if (WalletHelper.getBalance(this) >= amount) {
                String txnId = java.util.UUID.randomUUID().toString();
                String formattedAmount = String.format(Locale.US, "%.2f", amount);
                String payload = txnId + "|" + "Me" + "|" + phone + "|" + formattedAmount + "|" + System.currentTimeMillis();

                try {
                    byte[] txnSig = KeyStoreHelper.signData(payload);
                    String txnSigBase64 = android.util.Base64.encodeToString(txnSig, android.util.Base64.NO_WRAP);
                    String publicKeyBase64 = KeyStoreHelper.getPublicKeyBase64();

                    // Final payload: payload|signature|publicKey
                    String fullPayload = payload + "|" + txnSigBase64 + "|" + publicKeyBase64;
                    String encryptedPayload = CryptoHelper.encrypt(fullPayload);

                    // Debug logs
                    Log.d("SENDER", "Payload: " + payload);
                    Log.d("SENDER", "Signature (Base64): " + txnSigBase64);
                    Log.d("SENDER", "PublicKey (Base64): " + publicKeyBase64);
                    Log.d("ENCRYPTED_SMS", encryptedPayload);

                    WalletHelper.subtractBalance(this, amount);
                    updateBalance();

                    SmsManager smsManager = SmsManager.getDefault();
                    // Use multipart for long SMS
                    if (encryptedPayload.length() > 160) {
                        java.util.ArrayList<String> parts = smsManager.divideMessage(encryptedPayload);
                        smsManager.sendMultipartTextMessage(phone, null, parts, null, null);
                    } else {
                        smsManager.sendTextMessage(phone, null, encryptedPayload, null, null);
                    }

                    Toast.makeText(this, "Payment Sent! ₹" + amount, Toast.LENGTH_LONG).show();
                    // Reset fields for better UX
                    amountInput.setText("");
                    phoneInput.setText("");
                } catch (Exception e) {
                    Log.e("ENCRYPTION_ERROR", "Encryption error", e);
                    Toast.makeText(this, "Encryption error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(this, "Insufficient balance!", Toast.LENGTH_SHORT).show();
            }
        });

        checkBalanceBtn.setOnClickListener(v -> updateBalance());
    }

    private void updateBalance() {
        balanceText.setText("Wallet Balance: ₹" + WalletHelper.getBalance(this));
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == SMS_PERMISSION_REQUEST) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "SMS Permission Granted!", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "SMS Permission Denied!", Toast.LENGTH_SHORT).show();
            }
        }
        if (requestCode == 101) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Receive SMS Permission Granted!", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Receive SMS Permission Denied!", Toast.LENGTH_SHORT).show();
            }
        }
        if (requestCode == 102) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Read SMS Permission Granted!", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Read SMS Permission Denied!", Toast.LENGTH_SHORT).show();
            }
        }
    }
}
