package com.example.amazonxcodebenders;

import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.auth.api.phone.SmsRetriever;
import com.google.android.gms.auth.api.phone.SmsRetrieverClient;
import com.google.android.gms.common.api.CommonStatusCodes;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.tasks.Task;
import com.google.firebase.database.*;
import okhttp3.*;

import java.util.regex.Pattern;
import java.util.regex.Matcher;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.IOException;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Matcher;

public class LoginActivity extends AppCompatActivity {

    // Constants
    private static final String TAG = "LoginActivity";
    private static final String PREFS_NAME = "user_prefs";
    private static final String PREF_USER_PHONE = "user_phone";
    private static final int RISK_THRESHOLD = 70;
    private static final String OPENROUTER_URL = "https://openrouter.ai/api/v1/chat/completions";
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private AlertDialog otpVerificationDialog;
    // Network client
    private final OkHttpClient httpClient = new OkHttpClient();
    private EditText otpInput;
    private String currentPhone;
    private String currentSessionId;
    private DataSnapshot currentUserData;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        EditText etPhone = findViewById(R.id.etPhone);
        EditText etPassword = findViewById(R.id.etPassword);
        Button btnLogin = findViewById(R.id.btnLogin);

        btnLogin.setOnClickListener(v -> {
            String phone = etPhone.getText().toString().trim();
            String password = etPassword.getText().toString().trim();

            if (TextUtils.isEmpty(phone) || TextUtils.isEmpty(password)) {
                showToast("Enter phone and password");
                return;
            }

            authenticateUser(phone, password);
        });
    }

    public static String getLoggedInUserPhone(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(
                PREFS_NAME,
                Context.MODE_PRIVATE
        );
        String phoneNumber = prefs.getString(PREF_USER_PHONE, null);
        if (phoneNumber == null || phoneNumber.trim().isEmpty()) {
            Log.w(TAG, "No logged-in user found in SharedPreferences");
            return null;
        }
        return phoneNumber;
    }

    private void authenticateUser(String phone, String password) {
        DatabaseReference userRef = FirebaseDatabase.getInstance()
                .getReference("users")
                .child(phone);

        userRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot userSnapshot) {
                if (!validateCredentials(userSnapshot, password)) return;

                Query sessionsRef = FirebaseDatabase.getInstance()
                        .getReference("user_sessions")
                        .child(phone)
                        .limitToLast(3);

                sessionsRef.addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot sessionsSnapshot) {
                        String newSessionId = recordLoginSession(phone);
                        analyzeSessionRisk(phone, newSessionId, sessionsSnapshot, userSnapshot);
                    }

                    @Override
                    public void onCancelled(DatabaseError error) {
                        Log.e(TAG, "Sessions fetch failed", error.toException());
                        proceedAfterVerification(phone, userSnapshot);
                    }
                });
            }

            @Override
            public void onCancelled(DatabaseError error) {
                showToast("Login failed. Try again.");
                Log.e(TAG, "User auth failed", error.toException());
            }
        });
    }

    private String recordLoginSession(String phone) {
        DatabaseReference sessionRef = FirebaseDatabase.getInstance()
                .getReference("user_sessions")
                .child(phone)
                .push();

        Map<String, Object> sessionData = new HashMap<>();
        sessionData.put("timestamp", getCurrentTimestamp());
        sessionData.put("device", getDeviceInfo());
        sessionData.put("ip", getIPAddress());
        sessionData.put("os", getOSVersion());
        sessionData.put("status", "pending_verification");

        sessionRef.setValue(sessionData);
        return sessionRef.getKey();
    }

    private void analyzeSessionRisk(String phone, String sessionId,
                                    DataSnapshot pastSessions, DataSnapshot userData) {
        new Thread(() -> {
            try {
                JSONObject analysisData = new JSONObject();
                analysisData.put("current_device", getDeviceInfo());
                analysisData.put("current_ip", getIPAddress());

                if (pastSessions.exists()) {
                    List<Map<String, String>> history = new ArrayList<>();
                    for (DataSnapshot session : pastSessions.getChildren()) {
                        Map<String, String> pastSession = new HashMap<>();
                        pastSession.put("device", session.child("device").getValue(String.class));
                        pastSession.put("ip", session.child("ip").getValue(String.class));
                        pastSession.put("time", session.child("timestamp").getValue(String.class));
                        history.add(pastSession);
                    }
                    analysisData.put("history", history);
                }

                String response = callOpenRouterAPI(analysisData.toString());
                int riskScore = Integer.parseInt(response);
                Log.d(TAG, "Risk score: " + riskScore);

                runOnUiThread(() -> {
                    updateSessionStatus(phone, sessionId, riskScore);
                    if (riskScore < RISK_THRESHOLD) {
                        proceedAfterVerification(phone, userData);
                    } else {
                        showSecurityChallenge(phone, sessionId, riskScore, userData);
                    }
                });

            } catch (Exception e) {
                Log.e(TAG, "Risk analysis failed", e);
                runOnUiThread(() -> proceedAfterVerification(phone, userData));
            }
        }).start();
    }

    private String callOpenRouterAPI(String contextData) throws IOException, JSONException {
        String prompt = String.format(
                "Analyze this login attempt for fraud risk (0-100):\n" +
                        "Context: %s\n\n" +
                        "Respond ONLY with a number between 0-100 where:\n" +
                        "0-30 = Safe\n31-70 = Suspicious\n71-100 = High risk",
                contextData
        );

        RequestBody body = RequestBody.create(
                String.format("{\"model\":\"deepseek/deepseek-r1:free\"," +
                        "\"messages\":[{\"role\":\"system\",\"content\":\"%s\"}]}", prompt),
                JSON
        );

        Request request = new Request.Builder()
                .url(OPENROUTER_URL)
                .post(body)
                .addHeader("Authorization", "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")
                .addHeader("HTTP-Referer", "https://amazonxcodebenders.com")
                .addHeader("X-Title", "AmazonXcodeBenders")
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            String responseBody = response.body().string();
            return new JSONObject(responseBody)
                    .getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content")
                    .replaceAll("\\D+", "");
        }
    }

    private void updateSessionStatus(String phone, String sessionId, int riskScore) {
        DatabaseReference sessionRef = FirebaseDatabase.getInstance()
                .getReference("user_sessions")
                .child(phone)
                .child(sessionId);

        Map<String, Object> updates = new HashMap<>();
        updates.put("risk_score", riskScore);
        updates.put("status", riskScore < RISK_THRESHOLD ? "verified" : "suspicious");
        sessionRef.updateChildren(updates);
    }

    private void showSecurityChallenge(String phone, String sessionId,
                                       int riskScore, DataSnapshot userData) {
        new AlertDialog.Builder(this)
                .setTitle("Security Verification")
                .setMessage(String.format(
                        "Unusual login detected (Risk score: %d/%d).\n" +
                                "Device: %s\nIP: %s\n\nVerify it's you:",
                        riskScore, RISK_THRESHOLD, getDeviceInfo(), getIPAddress()))
                .setPositiveButton("Send OTP", (d, w) -> sendOTP(phone, sessionId, userData))
                .setNegativeButton("Cancel", (d, w) -> {
                    // Log failed verification attempt
                    FirebaseDatabase.getInstance()
                            .getReference("user_sessions")
                            .child(phone)
                            .child(sessionId)
                            .child("status")
                            .setValue("verification_failed");

                    // Optional: Logout user or take other security actions
                    Toast.makeText(this, "Verification required to proceed", Toast.LENGTH_LONG).show();
                })
                .setCancelable(false)
                .show();
    }

    private void sendOTP(String phone, String sessionId, DataSnapshot userData) {
        // Show progress dialog
        ProgressDialog progressDialog = new ProgressDialog(this);
        progressDialog.setMessage("Sending OTP...");
        progressDialog.setCancelable(false);
        progressDialog.show();

        // Generate OTP (6-digit random number)
        String otp = String.format("%06d", new Random().nextInt(999999));

        // Store OTP in Firebase with expiration (5 minutes)
        DatabaseReference otpRef = FirebaseDatabase.getInstance()
                .getReference("otp_verifications")
                .child(phone)
                .child(sessionId);

        Map<String, Object> otpData = new HashMap<>();
        otpData.put("otp", otp);
        otpData.put("timestamp", System.currentTimeMillis());
        otpData.put("expires_at", System.currentTimeMillis() + (5 * 60 * 1000)); // 5 minutes

        otpRef.setValue(otpData)
                .addOnSuccessListener(aVoid -> {
                    // Send OTP via SMS (implement your SMS gateway integration)
                    sendOtpViaSms(phone, otp);

                    // Update session status
                    FirebaseDatabase.getInstance()
                            .getReference("user_sessions")
                            .child(phone)
                            .child(sessionId)
                            .child("status")
                            .setValue("otp_sent");

                    progressDialog.dismiss();
                    showOtpVerificationDialog(phone, sessionId, userData);
                })
                .addOnFailureListener(e -> {
                    progressDialog.dismiss();
                    Toast.makeText(this, "Failed to send OTP. Please try again.", Toast.LENGTH_SHORT).show();
                    Log.e(TAG, "OTP send failed", e);
                });
    }

    private void sendOtpViaSms(String phoneNumber, String otp) {
        // Implement your SMS gateway integration here
        // This could be Twilio, AWS SNS, or your custom API
        Log.d(TAG, "OTP for " + phoneNumber + ": " + otp); // For testing

        // Example using SMS Retriever API (auto-read)
        SmsRetrieverClient client = SmsRetriever.getClient(this);
        Task<Void> task = client.startSmsRetriever();
        task.addOnSuccessListener(aVoid -> Log.d(TAG, "SMS retriever started"));
        task.addOnFailureListener(e -> Log.e(TAG, "Failed to start SMS retriever", e));
    }

    private void showOtpVerificationDialog(String phone, String sessionId, DataSnapshot userData) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Enter OTP");

        // Set up the input
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setHint("6-digit OTP");
        builder.setView(input);

        // Set up the buttons
        builder.setPositiveButton("Verify", (dialog, which) -> {
            String otp = input.getText().toString().trim();
            verifyOtp(phone, sessionId, otp, userData);
        });
        builder.setNegativeButton("Cancel", (dialog, which) -> {
            FirebaseDatabase.getInstance()
                    .getReference("user_sessions")
                    .child(phone)
                    .child(sessionId)
                    .child("status")
                    .setValue("otp_verification_cancelled");
            dialog.cancel();
        });

        builder.show();
    }

    private void verifyOtp(String phone, String sessionId, String otp, DataSnapshot userData) {
        ProgressDialog progressDialog = new ProgressDialog(this);
        progressDialog.setMessage("Verifying OTP...");
        progressDialog.setCancelable(false);
        progressDialog.show();

        DatabaseReference otpRef = FirebaseDatabase.getInstance()
                .getReference("otp_verifications")
                .child(phone)
                .child(sessionId);

        otpRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                progressDialog.dismiss();

                if (!snapshot.exists()) {
                    Toast.makeText(LoginActivity.this, "OTP expired. Please request a new one.", Toast.LENGTH_SHORT).show();
                    return;
                }

                String storedOtp = snapshot.child("otp").getValue(String.class);
                long expiresAt = snapshot.child("expires_at").getValue(Long.class);

                if (storedOtp == null || !storedOtp.equals(otp)) {
                    Toast.makeText(LoginActivity.this, "Invalid OTP", Toast.LENGTH_SHORT).show();
                    return;
                }

                if (System.currentTimeMillis() > expiresAt) {
                    Toast.makeText(LoginActivity.this, "OTP expired", Toast.LENGTH_SHORT).show();
                    return;
                }

                // OTP verification successful
                FirebaseDatabase.getInstance()
                        .getReference("user_sessions")
                        .child(phone)
                        .child(sessionId)
                        .child("status")
                        .setValue("verified_otp");

                // Clean up OTP record
                otpRef.removeValue();

                proceedAfterVerification(phone, userData);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                progressDialog.dismiss();
                Toast.makeText(LoginActivity.this, "Verification failed. Please try again.", Toast.LENGTH_SHORT).show();
                Log.e(TAG, "OTP verification failed", error.toException());
            }
        });
    }

    // SMS Receiver for auto OTP reading (add to your activity)
    private final BroadcastReceiver smsReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(SmsRetriever.SMS_RETRIEVED_ACTION)) {
                Bundle extras = intent.getExtras();
                if (extras != null) {
                    Status status = (Status) extras.get(SmsRetriever.EXTRA_STATUS);
                    if (status != null) {
                        switch (status.getStatusCode()) {
                            case CommonStatusCodes.SUCCESS:
                                // Get SMS message
                                String message = (String) extras.get(SmsRetriever.EXTRA_SMS_MESSAGE);
                                if (message != null) {
                                    // Extract OTP
                                    Pattern pattern = Pattern.compile("\\d{6}");
                                    Matcher matcher = pattern.matcher(message);
                                    if (matcher.find()) {
                                        String otp = matcher.group(0);
                                        // Auto-fill OTP if verification dialog is open
                                        if (otpVerificationDialog != null && otpVerificationDialog.isShowing()) {
                                            otpInput.setText(otp);
                                            verifyOtp(currentPhone, currentSessionId, otp, currentUserData);
                                        }
                                    }
                                }
                                break;
                            case CommonStatusCodes.TIMEOUT:
                                // Timeout occurred
                                break;
                        }
                    }
                }
            }
        }
    };

    private void proceedAfterVerification(String phone, DataSnapshot userData) {
        saveLoggedInUser(phone);
        Double balance = userData.child("balance").getValue(Double.class);
        String welcomeMsg = "Welcome! Balance: ₹" + (balance != null ? balance : 0);
        showToast(welcomeMsg);
        startActivity(new Intent(this, HomeActivity.class));
        finish();
    }

    private boolean validateCredentials(DataSnapshot snapshot, String password) {
        if (!snapshot.exists()) {
            showToast("User not found");
            return false;
        }
        String storedPassword = snapshot.child("password").getValue(String.class);
        if (storedPassword == null || !storedPassword.equals(password)) {
            showToast("Invalid credentials");
            return false;
        }
        return true;
    }

    private void saveLoggedInUser(String phone) {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .edit()
                .putString(PREF_USER_PHONE, phone)
                .apply();
    }

    private String getCurrentTimestamp() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                .format(new Date());
    }

    private String getDeviceInfo() {
        return Build.MANUFACTURER + " " + Build.MODEL + " (" + Build.DEVICE + ")";
    }

    private String getIPAddress() {
        try {
            for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces();
                 en.hasMoreElements();) {
                NetworkInterface intf = en.nextElement();
                for (Enumeration<InetAddress> enumIpAddr = intf.getInetAddresses();
                     enumIpAddr.hasMoreElements();) {
                    InetAddress inetAddress = enumIpAddr.nextElement();
                    if (!inetAddress.isLoopbackAddress()) {
                        return inetAddress.getHostAddress();
                    }
                }
            }
        } catch (SocketException e) {
            Log.e(TAG, "IP detection failed", e);
        }
        return "unknown";
    }

    private String getOSVersion() {
        return "Android " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")";
    }

    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    public static String getLoggedInUser(Context context) {
        return context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .getString(PREF_USER_PHONE, null);
    }
}