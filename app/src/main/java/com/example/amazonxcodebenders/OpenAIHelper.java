package com.example.amazonxcodebenders;

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
        void onExtracted(String amount, String dueDate, String description);

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
                message.put("content", "Extract bill info from this message: '" + smsText + "'. Reply only in JSON with keys: amount, due_date, description.");

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
                        callback.onExtracted(
                                data.optString("amount", "Unknown"),
                                data.optString("due_date", "Unknown"),
                                data.optString("description", "Unknown")
                        );

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
        // Remove starting ``````
        if (content.startsWith("```"))
            content = content.substring(7).trim();
        else if (content.startsWith("```")) {
            content = content.substring(3).trim();
        }
        // Remove ending ```
        if (content.endsWith("```")) {
            content = content.substring(0, content.length() - 3).trim();
        }
        return content;
    }

}



