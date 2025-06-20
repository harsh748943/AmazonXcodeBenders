package com.example.amazonxcodebenders;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.amazonxcodebenders.paymentOptimization.offlinePayment.KeyStoreHelper;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

public class LoginActivity extends AppCompatActivity {

    private static final String TAG = "LoginActivity";
    private static final String PREFS_NAME = "user_prefs";
    private static final String PREF_USER_PHONE = "user_phone";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_login);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        EditText etPhone = findViewById(R.id.etPhone);
        EditText etPassword = findViewById(R.id.etPassword);
        Button btnLogin = findViewById(R.id.btnLogin);

        btnLogin.setOnClickListener(v -> {
            String phone = etPhone.getText().toString().trim();
            String password = etPassword.getText().toString().trim();

            if (phone.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Enter phone and password", Toast.LENGTH_SHORT).show();
                Log.w(TAG, "Empty phone or password input");
                return;
            }

            // Firebase reference for the user
            DatabaseReference dbRef = FirebaseDatabase.getInstance().getReference("users").child(phone);
            Log.d(TAG, "Querying Firebase at path: users/" + phone);

            dbRef.addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(DataSnapshot dataSnapshot) {
                    Log.d(TAG, "Firebase onDataChange triggered");
                    if (dataSnapshot.exists()) {
                        String storedPassword = dataSnapshot.child("password").getValue(String.class);
                        Log.d(TAG, "User found. Stored password: " + storedPassword);

                        if (storedPassword == null) {
                            Toast.makeText(LoginActivity.this, "Password not set for user", Toast.LENGTH_SHORT).show();
                            Log.e(TAG, "Password field is null for user: " + phone);
                            return;
                        }

                        if (storedPassword.equals(password)) {
                            // Store user phone in SharedPreferences for global access
                            saveLoggedInUserPhone(LoginActivity.this, phone);

                            // Generate per-user RSA keypair (if not already exists)
                            try {
                                KeyStoreHelper.generateKey(phone);
                                Log.i(TAG, "RSA keypair ensured for user: " + phone);
                            } catch (Exception e) {
                                Log.e(TAG, "RSA keypair generation failed", e);
                                Toast.makeText(LoginActivity.this, "Key generation failed!", Toast.LENGTH_LONG).show();
                                return;
                            }

                            // Optionally, get balance from Firebase
                            Double balanceObj = dataSnapshot.child("balance").getValue(Double.class);
                            double balance = (balanceObj != null) ? balanceObj : 0.0;

                            Toast.makeText(LoginActivity.this, "Login successful! Balance: ₹" + balance, Toast.LENGTH_LONG).show();
                            Log.i(TAG, "Login successful for user: " + phone + ", Balance: " + balance);

                            // Go to HomeActivity
                            startActivity(new Intent(LoginActivity.this, HomeActivity.class));
                            finish();
                        } else {
                            Toast.makeText(LoginActivity.this, "Invalid password", Toast.LENGTH_SHORT).show();
                            Log.w(TAG, "Invalid password for user: " + phone);
                        }
                    } else {
                        Toast.makeText(LoginActivity.this, "User not found", Toast.LENGTH_SHORT).show();
                        Log.w(TAG, "User not found in database: " + phone);
                    }
                }

                @Override
                public void onCancelled(DatabaseError databaseError) {
                    Toast.makeText(LoginActivity.this, "Login failed: " + databaseError.getMessage(), Toast.LENGTH_SHORT).show();
                    Log.e(TAG, "Database error: " + databaseError.getMessage());
                }
            });
        });
    }

    /**
     * Store the logged-in user's phone number in SharedPreferences for global access.
     */
    public static void saveLoggedInUserPhone(Context context, String phone) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        prefs.edit().putString(PREF_USER_PHONE, phone).apply();
    }

    /**
     * Retrieve the logged-in user's phone number from SharedPreferences.
     */
    public static String getLoggedInUserPhone(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        return prefs.getString(PREF_USER_PHONE, null);
    }
}
