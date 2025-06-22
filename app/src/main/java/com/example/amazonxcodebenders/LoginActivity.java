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

import org.json.JSONArray;
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

                // First, fetch only the last session
                Query lastSessionRef = FirebaseDatabase.getInstance()
                        .getReference("user_sessions")
                        .child(phone)
                        .limitToLast(1);

                lastSessionRef.addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot lastSessionSnapshot) {
                        String lastSessionStatus = null;
                        for (DataSnapshot sessionSnap : lastSessionSnapshot.getChildren()) {
                            lastSessionStatus = sessionSnap.child("status").getValue(String.class);
                        }

                        if ("verified_otp".equals(lastSessionStatus)||"verified".equals(lastSessionStatus)) {
                            // Skip LLM for OTP-verified sessions
                            String newSessionId = recordLoginSession(phone);
                            proceedAfterVerification(phone, userSnapshot);
                        } else {

                            // Fetch last 3 sessions for LLM analysis
                            Query sessionsRef = FirebaseDatabase.getInstance()
                                    .getReference("user_sessions")
                                    .child(phone)
                                    .limitToLast(3);

                            sessionsRef.addListenerForSingleValueEvent(new ValueEventListener() {
                                @Override
                                public void onDataChange(DataSnapshot sessionsSnapshot) {
                                    String newSessionId = recordLoginSession(phone);
                                   analyzeSessionRiskLLM(phone, newSessionId, sessionsSnapshot, userSnapshot);
                                }

                                @Override
                                public void onCancelled(DatabaseError error) {
                                    Log.e(TAG, "Sessions fetch failed", error.toException());
                                    proceedAfterVerification(phone, userSnapshot);
                                }
                            });
                        }
                    }

                    @Override
                    public void onCancelled(DatabaseError error) {
                        Log.e(TAG, "Last session fetch failed", error.toException());

                        // Fallback: fetch last 3 sessions
                        Query sessionsRef = FirebaseDatabase.getInstance()
                                .getReference("user_sessions")
                                .child(phone)
                                .limitToLast(3);

                        sessionsRef.addListenerForSingleValueEvent(new ValueEventListener() {
                            @Override
                            public void onDataChange(DataSnapshot sessionsSnapshot) {
                                String newSessionId = recordLoginSession(phone);
                                analyzeSessionRiskLLM(phone, newSessionId, sessionsSnapshot, userSnapshot);
                            }

                            @Override
                            public void onCancelled(DatabaseError error) {
                                Log.e(TAG, "Sessions fetch failed", error.toException());
                                proceedAfterVerification(phone, userSnapshot);
                            }
                        });
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

    // --- LLM-Based Risk Scoring Implementation ---
    private void analyzeSessionRiskLLM(String phone, String sessionId,
                                       DataSnapshot pastSessions, DataSnapshot userData) {
        new Thread(() -> {
            try {
                JSONObject analysisData = new JSONObject();
                analysisData.put("current_device", getDeviceInfo());
                analysisData.put("current_ip", getIPAddress());
                analysisData.put("shipping_address", userData.child("shipping_address").getValue(String.class));
                analysisData.put("payment_method", userData.child("last_payment_method").getValue(String.class));

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

                String llmResponse = callOpenRouterLLM(analysisData.toString());
                // Start of fixes: Validate response before parsing
                if (TextUtils.isEmpty(llmResponse)) {
                    throw new JSONException("Empty LLM response");
                }

                JSONObject llmResult = new JSONObject(llmResponse);

                // 1. Check if risk_score exists
                if (!llmResult.has("risk_score")) {
                    Log.e(TAG, "risk_score missing in response: " + llmResponse);
                    throw new JSONException("Missing risk_score in LLM response");
                }


                int riskScore = llmResult.getInt("risk_score");
                String confidence = llmResult.optString("confidence", "medium");
                String reason = llmResult.optString("reason", "No reason provided");
                String action = llmResult.optString("action", "allow");

                runOnUiThread(() -> {
                    updateSessionStatus(phone, sessionId, riskScore, confidence, reason, action);
                    handleLLMSecurityAction(action, phone, sessionId, userData, reason, riskScore);
                });

            } catch (Exception e) {
                Log.e(TAG, "LLM risk analysis failed", e);
                runOnUiThread(() -> proceedAfterVerification(phone, userData));
            }
        }).start();
    }

    private String callOpenRouterLLM(String contextData) throws IOException {
        try {
            // 1. Build JSON payload properly
            JSONObject requestBody = new JSONObject();
            requestBody.put("model", "deepseek/deepseek-r1:free");

            JSONArray messages = new JSONArray();
            JSONObject message = new JSONObject();
            message.put("role", "system");
            String safePrompt = "Analyze this login/session/payment for fraud risk.\n" +
                    "Context (escaped JSON): " + JSONObject.quote(contextData) + "\n" +
                    "If the IP address is link-local (starts with fe80::), ignore it and do not increase risk.\n" +
                    "Respond ONLY in JSON format:\n" +
                    "{\"risk_score\": <0-100>, \"confidence\": \"<low|medium|high>\", \"reason\": \"<short explanation>\", \"action\": \"<allow|force_otp|block>\"}";

           // JSONArray messages = new JSONArray();

            message.put("content", safePrompt);
            messages.put(message);
            requestBody.put("messages", messages);

            // 3. Create request body
            RequestBody body = RequestBody.create(
                    requestBody.toString(),
                    JSON
            );

            // 4. Build request with headers
            Request request = new Request.Builder()
                    .url(OPENROUTER_URL)
                    .post(body)
                    .addHeader("Authorization", "Bearer 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")
                    .addHeader("HTTP-Referer", "https://amazonxcodebenders.com")
                    .addHeader("X-Title", "AmazonXcodeBenders")
                    .build();


                try (Response response = httpClient.newCall(request).execute()) {
                    if (!response.isSuccessful()) {
                        throw new IOException("Unexpected code " + response);
                    }

                    String responseBody = response.body().string();
                    JSONObject root = new JSONObject(responseBody);
                    String content = root.getJSONArray("choices")
                            .getJSONObject(0)
                            .getJSONObject("message")
                            .getString("content");

                    // Strip Markdown code blocks
                    if (content.startsWith("```json")) {
                        content = content.substring(7, content.length() - 3).trim();
                    }
                    return content;
                }
            } catch (JSONException e) {
                throw new IOException("JSON creation failed: " + e.getMessage());
            }
    }


    private void updateSessionStatus(String phone, String sessionId, int riskScore, String confidence, String reason, String action) {
        DatabaseReference sessionRef = FirebaseDatabase.getInstance()
                .getReference("user_sessions")
                .child(phone)
                .child(sessionId);

        Map<String, Object> updates = new HashMap<>();
        updates.put("risk_score", riskScore);
        updates.put("confidence", confidence);
        updates.put("reason", reason);
        updates.put("action", action);
        updates.put("status", action.equals("allow") ? "verified" : action);
        sessionRef.updateChildren(updates);
    }

    private void handleLLMSecurityAction(String action, String phone, String sessionId, DataSnapshot userData, String reason, int riskScore) {
        switch (action) {
            case "allow":
                proceedAfterVerification(phone, userData);
                break;
            case "force_otp":
                showSecurityChallenge(phone, sessionId, riskScore, userData, reason);
                break;
            case "block":
                blockSession(phone, sessionId, reason);
                Toast.makeText(this, "Login blocked: " + reason, Toast.LENGTH_LONG).show();
                break;
            default:
                proceedAfterVerification(phone, userData);
        }
    }

    private void blockSession(String phone, String sessionId, String reason) {
        DatabaseReference sessionRef = FirebaseDatabase.getInstance()
                .getReference("user_sessions")
                .child(phone)
                .child(sessionId);
        sessionRef.child("status").setValue("blocked");
        sessionRef.child("block_reason").setValue(reason);
    }

    private void showSecurityChallenge(String phone, String sessionId,
                                       int riskScore, DataSnapshot userData, String reason) {
        new AlertDialog.Builder(this)
                .setTitle("Security Verification")
                .setMessage(String.format(
                        "Unusual login detected (Risk score: %d).\nReason: %s\nDevice: %s\nIP: %s\n\nVerify it's you:",
                        riskScore, reason, getDeviceInfo(), getIPAddress()))
                .setPositiveButton("Send OTP", (d, w) -> sendOTP(phone, sessionId, userData))
                .setNegativeButton("Cancel", (d, w) -> {
                    FirebaseDatabase.getInstance()
                            .getReference("user_sessions")
                            .child(phone)
                            .child(sessionId)
                            .child("status")
                            .setValue("verification_failed");

                    Toast.makeText(this, "Verification required to proceed", Toast.LENGTH_LONG).show();
                })
                .setCancelable(false)
                .show();
    }

    private void sendOTP(String phone, String sessionId, DataSnapshot userData) {
        ProgressDialog progressDialog = new ProgressDialog(this);
        progressDialog.setMessage("Sending OTP...");
        progressDialog.setCancelable(false);
        progressDialog.show();

        String otp = "12345";

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
                    sendOtpViaSms(phone, otp);

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
        // SMS gateway integration required here
        Log.d(TAG, "OTP for " + phoneNumber + ": " + otp); // Testing purpose

        SmsRetrieverClient client = SmsRetriever.getClient(this);
        Task<Void> task = client.startSmsRetriever();
        task.addOnSuccessListener(aVoid -> Log.d(TAG, "SMS retriever started"));
        task.addOnFailureListener(e -> Log.e(TAG, "Failed to start SMS retriever", e));
    }

    private void showOtpVerificationDialog(String phone, String sessionId, DataSnapshot userData) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Enter OTP");

        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setHint("6-digit OTP");
        builder.setView(input);

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
                Long expiresAt = snapshot.child("expires_at").getValue(Long.class);

                if (storedOtp == null || !storedOtp.equals(otp)) {
                    Toast.makeText(LoginActivity.this, "Invalid OTP", Toast.LENGTH_SHORT).show();
                    return;
                }

                if (expiresAt == null || System.currentTimeMillis() > expiresAt) {
                    Toast.makeText(LoginActivity.this, "OTP expired", Toast.LENGTH_SHORT).show();
                    return;
                }

                FirebaseDatabase.getInstance()
                        .getReference("user_sessions")
                        .child(phone)
                        .child(sessionId)
                        .child("status")
                        .setValue("verified_otp");

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
                                String message = (String) extras.get(SmsRetriever.EXTRA_SMS_MESSAGE);
                                if (message != null) {
                                    Pattern pattern = Pattern.compile("\\d{6}");
                                    Matcher matcher = pattern.matcher(message);
                                    if (matcher.find()) {
                                        String otp = matcher.group(0);
                                        if (otpVerificationDialog != null && otpVerificationDialog.isShowing()) {
                                            otpInput.setText(otp);
                                            verifyOtp(currentPhone, currentSessionId, otp, currentUserData);
                                        }
                                    }
                                }
                                break;
                            case CommonStatusCodes.TIMEOUT:
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
