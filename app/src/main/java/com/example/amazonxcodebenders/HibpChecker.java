package com.example.amazonxcodebenders;



import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.function.Consumer;

public class HibpChecker {

    public interface BreachCallback {
        void onResult(boolean isBreached);
    }

    public static void checkEmail(String email, Consumer<Boolean> callback) {
        // Real implementation requires a paid API key and HTTPS request.
        // For demo/testing, always return false (not breached).
        // Replace this with actual API call if you have access.
        callback.accept(false);
    }

    public static void checkPassword(String password, BreachCallback callback) {
        new Thread(() -> {
            try {
                String sha1 = sha1(password).toUpperCase();
                String prefix = sha1.substring(0, 5);
                String suffix = sha1.substring(5);

                URL url = new URL("https://api.pwnedpasswords.com/range/" + prefix);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");

                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                String line;
                boolean found = false;
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith(suffix)) {
                        found = true;
                        break;
                    }
                }
                reader.close();
                callback.onResult(found);
            } catch (Exception e) {
                e.printStackTrace();
                callback.onResult(false); // Fail-safe: treat as safe if error
            }
        }).start();
    }

    private static String sha1(String input) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-1");
        byte[] messageDigest = md.digest(input.getBytes("UTF-8"));

        StringBuilder sb = new StringBuilder();
        for (byte b : messageDigest) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}