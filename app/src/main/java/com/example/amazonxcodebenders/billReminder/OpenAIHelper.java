package com.example.amazonxcodebenders.billReminder;

import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.OutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class OpenAIHelper {

    public interface BillCallback {
        void onExtracted(String amount, String dueDate, String billType, String fullMessage);
        void onFailure(Exception e);
    }

    public static void extractBillInfo(String smsText, BillCallback callback) {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Handler handler = new Handler(Looper.getMainLooper());

        executor.execute(() -> {
            try {
                // 1. OpenRouter endpoint
                URL url = new URL("https://openrouter.ai/api/v1/chat/completions");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                // 2. Add your OpenRouter API Key here
                conn.setRequestProperty("Authorization", "Bearer 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");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);

                // 3. Use deepseek/deepseek-r1:free model
                JSONObject jsonBody = new JSONObject();
                jsonBody.put("model", "deepseek/deepseek-r1:free");

                JSONObject message = new JSONObject();
                message.put("role", "user");
                // Prompt: Ask for only JSON reply
                message.put("content",
                        "Extract bill info from: '" + smsText + "'. " +
                                "Reply ONLY in JSON with keys: amount, due_date, description, bill_type. " +
                                "bill_type must be one of: Electricity Bill, Gas Bill, Water Bill, Credit Card Bill, Mobile Bill, Internet Bill, Other."
                );


                JSONArray messagesArray = new JSONArray();
                messagesArray.put(message);
                jsonBody.put("messages", messagesArray);

                try (OutputStream os = conn.getOutputStream()) {
                    os.write(jsonBody.toString().getBytes(StandardCharsets.UTF_8));
                }

                // Read the response
                InputStream inputStream = conn.getInputStream();
                Scanner in = new Scanner(inputStream, "UTF-8");
                StringBuilder sb = new StringBuilder();
                while (in.hasNext()) sb.append(in.nextLine());
                in.close();

                String result = sb.toString();

                handler.post(() -> {
                    try {
                        JSONObject response = new JSONObject(result);
                        String content = response.getJSONArray("choices")
                                .getJSONObject(0)
                                .getJSONObject("message")
                                .getString("content");

                        // Remove markdown if present
                        content = cleanJsonMarkdown(content);

                        JSONObject data = new JSONObject(content);

                        String amount = data.optString("amount", "Unknown");
                        String dueDate = data.optString("due_date", "Unknown");
//                        String fullDescription = data.optString("description", "Unknown");
//
//                        String billType = summarizeType(fullDescription);

                        String fullDescription = data.optString("description", "Unknown");
                        String billType = data.optString("bill_type", "Other Bill");


                        callback.onExtracted(amount, dueDate, billType, fullDescription);

                    } catch (Exception e) {
                        callback.onFailure(new Exception("Parse error: " + e.getMessage()));
                    }
                });

            } catch (Exception e) {
                e.printStackTrace();
                handler.post(() -> callback.onFailure(new Exception("API call failed: " + e.getMessage())));
            }
        });
    }

    private static String cleanJsonMarkdown(String content) {
        content = content.trim();
        if (content.startsWith("```json")) content = content.substring(7).trim();
        else if (content.startsWith("```")) content = content.substring(3).trim();
        if (content.endsWith("```")) content = content.substring(0, content.length() - 3).trim();
        return content;
    }

    private static String summarizeType(String description) {
        description = description.toLowerCase();
        if (description.contains("electricity")) return "Electricity Bill";
        if (description.contains("gas")) return "Gas Bill";
        if (description.contains("water")) return "Water Bill";
        if (description.contains("credit card")) return "Credit Card Bill";
        if (description.contains("mobile") || description.contains("recharge")) return "Mobile Recharge";
        if (description.contains("internet") || description.contains("data")) return "Internet Bill";
        return "Other Bill";
    }
}
