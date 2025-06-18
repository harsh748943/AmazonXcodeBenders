package com.example.amazonxcodebenders;

import android.Manifest;
import android.content.*;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.speech.RecognizerIntent;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import okhttp3.*;
import org.json.*;

public class HomeActivity extends AppCompatActivity {

    private static final String TAG = "HomeActivity";
    private ImageView sendMoneyImage, voiceCommandImage;
    private TextView sendMoneyText, offlineBar, tvWalletBalance;
    private boolean isConnected = false;
    private NetworkChangeReceiver networkChangeReceiver;


    private WalletViewModel walletViewModel;

    private HashMap<String, String> contactsMap = new HashMap<>();
    private HashMap<String, String> numberMap = new HashMap<>();
    private ActivityResultLauncher<String> requestPermissionLauncher;
    private ActivityResultLauncher<Intent> voiceLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        sendMoneyImage = findViewById(R.id.sendMoneyImage);
        sendMoneyText = findViewById(R.id.sendMoneyText);
        offlineBar = findViewById(R.id.offlineBar);
        tvWalletBalance = findViewById(R.id.walletBalance);
        voiceCommandImage = findViewById(R.id.voice_cmd);
        LinearLayout budgetLayout = findViewById(R.id.layout_budget);

        // Initial check and set text
        isConnected = isInternetAvailable();
        updateSendMoneyText(isConnected);

        sendMoneyImage.setOnClickListener(v -> {
            if (isConnected) {
                startActivity(new Intent(HomeActivity.this, SendMoneyOnlineActivity.class));
            } else {
                startActivity(new Intent(HomeActivity.this, SendMoneyOfflineActivity.class));
            }
        });

        voiceLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        ArrayList<String> results = result.getData().getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
                        if (results != null && results.size() > 0) {
                            String spokenText = results.get(0);
                            Log.d(TAG, "User said: " + spokenText);
                            processVoiceWithAI(spokenText);
                        }
                    }
                }
        );

        budgetLayout.setOnClickListener(v -> {
            Intent intent = new Intent(HomeActivity.this, BudgetActivity.class);

            // Dummy generated budget (in a real app, call generateBudgetUsingAI() here)
            HashMap<String, Double> dummyBudget = new HashMap<>();
            dummyBudget.put("Food", 1200.0);
            dummyBudget.put("Travel", 600.0);
            dummyBudget.put("Rent", 8000.0);
            dummyBudget.put("Entertainment", 500.0);
            dummyBudget.put("Savings", 2500.0);

            for (Map.Entry<String, Double> entry : dummyBudget.entrySet()) {
                intent.putExtra(entry.getKey(), entry.getValue());
            }

            startActivity(intent);
        });

        voiceCommandImage.setOnClickListener(v -> startVoiceRecognition());

        walletViewModel = new ViewModelProvider(
                this,
                ViewModelProvider.AndroidViewModelFactory.getInstance(getApplication())
        ).get(WalletViewModel.class);
         tvWalletBalance = findViewById(R.id.walletBalance);

        walletViewModel.getBalance().observe(this, newBalance -> {
            tvWalletBalance.setText("₹" + String.format("%.2f", newBalance));
        });

        // Register network receiver
        networkChangeReceiver = new NetworkChangeReceiver();
        registerReceiver(networkChangeReceiver, new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));

        requestPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                isGranted -> {
                    if (isGranted) {
                        loadContacts();
                    } else {
                        Toast.makeText(this, "Contacts permission denied!", Toast.LENGTH_SHORT).show();
                    }
                }
        );

        checkAndRequestContactsPermission();
    }

    private void checkAndRequestContactsPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS)
                == PackageManager.PERMISSION_GRANTED) {
            loadContacts();
        } else {
            requestPermissionLauncher.launch(Manifest.permission.READ_CONTACTS);
        }
    }

    private void loadContacts() {
        contactsMap.clear();
        numberMap.clear();
        Cursor cursor = getContentResolver().query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                null, null, null, null
        );
        if (cursor != null) {
            while (cursor.moveToNext()) {
                String name = cursor.getString(cursor.getColumnIndex(
                        ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME));
                String phone = cursor.getString(cursor.getColumnIndex(
                        ContactsContract.CommonDataKinds.Phone.NUMBER));
                if (name != null && phone != null) {
                    String key = name.toLowerCase().trim();
                    contactsMap.put(key, phone.trim());
                    numberMap.put(key, phone.trim());
                    Log.d(TAG, "Loaded contact: " + name + " -> " + phone);
                }
            }
            cursor.close();
            Log.i(TAG, "Total contacts loaded: " + contactsMap.size());
        }
    }

    private void startVoiceRecognition() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak your payment command...");
        voiceLauncher.launch(intent);
    }

    private void processVoiceWithAI(String spokenText) {
        Log.d(TAG, "Processing voice: " + spokenText);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            try {
                OkHttpClient client = new OkHttpClient();

                JSONArray messages = new JSONArray();
                messages.put(new JSONObject()
                        .put("role", "system")
                        .put("content", "You are a payment assistant. Extract intent (money_transfer/pay_electricity_bill), recipient name, and amount. IMP: give the output in a valid JSON string (not markdown, just plain json object) and stick to the schema: {\"intent\":\"...\",\"recipient\":\"...\",\"amount\":\"...\"}."));
                messages.put(new JSONObject()
                        .put("role", "user")
                        .put("content", spokenText));

                JSONObject payload = new JSONObject();
                payload.put("model", "deepseek/deepseek-r1:free");
                payload.put("messages", messages);
                payload.put("max_tokens", 150);
                payload.put("response_format", new JSONObject().put("type", "json_object"));

                RequestBody body = RequestBody.create(
                        payload.toString(), MediaType.get("application/json; charset=utf-8"));

                Request request = new Request.Builder()
                        .url("https://openrouter.ai/api/v1/chat/completions")
                        .addHeader("Authorization", "Bearer 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")
                        .addHeader("HTTP-Referer", "https://yourapp.com")
                        .addHeader("X-Title", "YourAppName")
                        .post(body)
                        .build();

                Log.d(TAG, "OpenRouter DeepSeek API request: " + payload.toString());

                Response response = client.newCall(request).execute();
                String responseBody = response.body().string();

                Log.d(TAG, "OpenRouter DeepSeek API response: " + responseBody);

                JSONObject json = new JSONObject(responseBody);
                if (json.has("error")) {
                    JSONObject error = json.getJSONObject("error");
                    String errorMsg = error.optString("message", "Unknown error");
                    Log.e(TAG, "OpenRouter API error: " + errorMsg);
                    runOnUiThread(() -> Toast.makeText(this, "AI Error: " + errorMsg, Toast.LENGTH_LONG).show());
                    return;
                }

                String content = json.getJSONArray("choices")
                        .getJSONObject(0)
                        .getJSONObject("message")
                        .optString("content", "");
                if (content.isEmpty()) {
                    content = json.getJSONArray("choices")
                            .getJSONObject(0)
                            .getJSONObject("message")
                            .optString("reasoning", "");
                }
                Log.d(TAG, "Raw AI response content: " + content);

                if (content.trim().isEmpty()) {
                    throw new Exception("Empty response from AI");
                }

                JSONObject resultObj = null;
                try {
                    int jsonStart = content.indexOf('{');
                    int jsonEnd = content.lastIndexOf('}');
                    if (jsonStart != -1 && jsonEnd != -1 && jsonEnd > jsonStart) {
                        content = content.substring(jsonStart, jsonEnd + 1);
                    }
                    resultObj = new JSONObject(content);
                } catch (JSONException e) {
                    Log.e(TAG, "Failed to parse JSON from: " + content);
                    throw new Exception("Invalid JSON response");
                }

                String intent = resultObj.optString("intent", "error");
                String recipient = resultObj.optString("recipient", "");
                String amount = resultObj.optString("amount", "");

                Log.i(TAG, "Parsed DeepSeek: intent=" + intent + ", recipient=" + recipient + ", amount=" + amount);

                // Lookup number for UPI generation
                String key = recipient.toLowerCase().trim();
                String number = numberMap.get(key);
                String upi = generateUpiId(recipient, number != null ? number : "0000");
                String bankingName = recipient;

                // Pass all details to SendMoneyActivity
                Intent intentObj = new Intent(this, SendMoneyOnlineActivity.class);
                intentObj.putExtra("recipient", recipient);
                intentObj.putExtra("upi", upi);
                intentObj.putExtra("bankingName", bankingName);
                intentObj.putExtra("amount", amount);
                startActivity(intentObj);

            } catch (Exception e) {
                Log.e(TAG, "OpenRouter DeepSeek API exception", e);
                runOnUiThread(() -> Toast.makeText(this, "AI Error: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        });


    }




    // UPI ID generation logic
    public static String generateUpiId(String name, String number) {
        String username = name.toLowerCase().replaceAll("\\s+", "");
        String digits = number.replaceAll("\\D", "");
        String last4 = digits.length() >= 4 ? digits.substring(digits.length() - 4) : digits;
        return username + last4 + "@oksbi";




    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (networkChangeReceiver != null) {
            unregisterReceiver(networkChangeReceiver);
        }
    }

    private boolean isInternetAvailable() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm != null) {
            NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
            return activeNetwork != null && activeNetwork.isConnected();
        }
        return false;
    }

    private void updateSendMoneyText(boolean connected) {
        if (connected) {
            sendMoneyText.setText("Send Money");
        } else {
            sendMoneyText.setText("Send Money Offline");
        }
    }

    private class NetworkChangeReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            isConnected = isInternetAvailable();
            updateSendMoneyText(isConnected);
            updateOfflineBar(isConnected);
        }
    }

    private void updateOfflineBar(boolean isConnected) {
        if (isConnected) {
            offlineBar.setVisibility(View.GONE);
        } else {
            offlineBar.setVisibility(View.VISIBLE);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Always refresh from persistent storage
        double currentBalance = WalletHelper.getBalance(this);
        walletViewModel.setBalance(currentBalance);
    }

}
