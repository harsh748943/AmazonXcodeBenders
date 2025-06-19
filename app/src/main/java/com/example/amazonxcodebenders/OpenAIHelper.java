//// STEP 1: Android Code - Send SMS content to OpenAI API via HTTPS
//// Create a helper class to make POST request
//
//package com.example.amazonxcodebenders;
//
//import android.os.AsyncTask;
//import org.json.JSONObject;
//import java.io.OutputStream;
//import java.net.HttpURLConnection;
//import java.net.URL;
//import java.util.Scanner;
//
//import com.example.amazonxcodebenders.BuildConfig;
//
//public class OpenAIHelper {
//
//public interface BillCallback {
//        void onExtracted(String amount, String dueDate, String description);
//        void onFailure(Exception e);
//    }
//    public static void extractBillInfo(String smsText, BillCallback callback)
//    {
//        new AsyncTask<Void, Void, String>() {
//            protected String doInBackground(Void... voids) {
//                try {
//                    URL url = new URL("https://api.openai.com/v1/chat/completions");
//                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
//                    conn.setRequestMethod("POST");
//
//                    conn.setRequestProperty("Authorization", "Bearer " + BuildConfig.OPENAI_API_KEY);
//
//                    conn.setRequestProperty("Content-Type", "application/json");
//                    conn.setDoOutput(true);
//
//                    JSONObject jsonBody = new JSONObject();
//                    jsonBody.put("model", "gpt-3.5-turbo");
//
//                    JSONObject message = new JSONObject();
//                    message.put("role", "user");
//                    message.put("content", "Extract bill info from this message: '" + smsText + "'. Reply only in JSON with keys: amount, due_date, description.");
//
//                    jsonBody.put("messages", new org.json.JSONArray().put(message));
//
//                    OutputStream os = conn.getOutputStream();
//                    os.write(jsonBody.toString().getBytes());
//                    os.flush();
//                    os.close();
//
//                    Scanner in = new Scanner(conn.getInputStream());
//                    StringBuilder sb = new StringBuilder();
//                    while (in.hasNext()) sb.append(in.nextLine());
//
//                    in.close();
//                    return sb.toString();
//
//                } catch (Exception e) {
//                    e.printStackTrace();
//                    return null;
//                }
//            }
//
//            protected void onPostExecute(String result) {
//                if (result == null) {
//                    callback.onFailure(new Exception("API call failed"));
//                    return;
//                }
//                try {
//                    JSONObject response = new JSONObject(result);
//                    String content = response.getJSONArray("choices")
//                            .getJSONObject(0)
//                            .getJSONObject("message")
//                            .getString("content");
//
//                    JSONObject data = new JSONObject(content);
//                    callback.onExtracted(data.optString("amount"), data.optString("due_date"), data.optString("description"));
//
//                } catch (Exception e) {
//                    callback.onFailure(new Exception("Parse error"));
//
//
//                }
//            }
//        }.execute();
//    }
//}

package com.example.amazonxcodebenders;

import android.os.AsyncTask;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Scanner;
import android.os.Handler;
import android.os.Looper;

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
                // 1. Use OpenRouter endpoint
                URL url = new URL("https://openrouter.ai/api/v1/chat/completions");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                // 2. Use your OpenRouter API Key here
                conn.setRequestProperty("Authorization", "Bearer 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");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);

                // 3. Model: deepseek-chat (check OpenRouter docs for exact name)
                JSONObject jsonBody = new JSONObject();
                jsonBody.put("model", "deepseek-chat");

                JSONObject message = new JSONObject();
                message.put("role", "user");
                message.put("content", "Extract bill info from this message: '" + smsText + "'. Reply only in JSON with keys: amount, due_date, description.");

                JSONArray messagesArray = new JSONArray();
                messagesArray.put(message);
                jsonBody.put("messages", messagesArray);

                OutputStream os = conn.getOutputStream();
                os.write(jsonBody.toString().getBytes());
                os.flush();
                os.close();

                Scanner in = new Scanner(conn.getInputStream());
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
                handler.post(() -> callback.onFailure(new Exception("API call failed")));
            }
        });
    }


//    public static void extractBillInfo(String smsText, BillCallback callback) {
//        new AsyncTask<Void, Void, String>() {
//            @Override
//            protected String doInBackground(Void... voids) {
//                try {
//                    URL url = new URL("https://api.openai.com/v1/chat/completions");
//                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
//                    conn.setRequestMethod("POST");
//                    conn.setRequestProperty("Authorization", "Bearer " + BuildConfig.OPENAI_API_KEY);
//                    conn.setRequestProperty("Content-Type", "application/json");
//                    conn.setDoOutput(true);
//
//                    JSONObject jsonBody = new JSONObject();
//                    jsonBody.put("model", "gpt-3.5-turbo");
//
//                    JSONObject message = new JSONObject();
//                    message.put("role", "user");
//                    message.put("content", "Extract bill info from this message: '" + smsText + "'. Reply only in JSON with keys: amount, due_date, description.");
//
//                    JSONArray messagesArray = new JSONArray();
//                    messagesArray.put(message);
//                    jsonBody.put("messages", messagesArray);
//
//                    OutputStream os = conn.getOutputStream();
//                    os.write(jsonBody.toString().getBytes());
//                    os.flush();
//                    os.close();
//
//                    Scanner in = new Scanner(conn.getInputStream());
//                    StringBuilder sb = new StringBuilder();
//                    while (in.hasNext()) sb.append(in.nextLine());
//                    in.close();
//
//                    return sb.toString();
//
//                } catch (Exception e) {
//                    e.printStackTrace();
//                    return null;
//                }
//            }
//
//            @Override
//            protected void onPostExecute(String result) {
//                if (result == null) {
//                    callback.onFailure(new Exception("API call failed"));
//                    return;
//                }
//                try {
//                    JSONObject response = new JSONObject(result);
//                    String content = response.getJSONArray("choices")
//                            .getJSONObject(0)
//                            .getJSONObject("message")
//                            .getString("content");
//
//                    JSONObject data = new JSONObject(content);
//                    callback.onExtracted(
//                            data.optString("amount", "Unknown"),
//                            data.optString("due_date", "Unknown"),
//                            data.optString("description", "Unknown")
//                    );
//
//                } catch (Exception e) {
//                    callback.onFailure(new Exception("Parse error: " + e.getMessage()));
//                }
//            }
//        }.execute();
//    }
}

