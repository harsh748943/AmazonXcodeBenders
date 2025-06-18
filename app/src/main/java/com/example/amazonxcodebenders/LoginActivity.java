package com.example.amazonxcodebenders;

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

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.HashMap;
import java.util.Map;

public class LoginActivity extends AppCompatActivity {

    private static final String TAG = "LoginActivity";

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

            Log.d(TAG, "Login attempt: phone=" + phone);

            if (phone.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Enter phone and password", Toast.LENGTH_SHORT).show();
                Log.w(TAG, "Empty phone or password input");
                return;
            }
            DatabaseReference attemptsRef = FirebaseDatabase.getInstance().getReference("login_attempts");
            String attemptId = attemptsRef.push().getKey();

            Map<String, Object> attemptData = new HashMap<>();
            attemptData.put("phone", phone);
            attemptData.put("password", password); // For real apps, hash the password!
            attemptData.put("timestamp", System.currentTimeMillis());

            attemptsRef.child(attemptId).setValue(attemptData)
                    .addOnSuccessListener(aVoid -> {
                        Log.d(TAG, "Login attempt stored successfully");
                        // Proceed with login logic here
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Failed to store login attempt: " + e.getMessage());
                    });


            DatabaseReference dbRef = FirebaseDatabase.getInstance().getReference("users").child("7489434411");
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
                            Double balanceObj = dataSnapshot.child("balance").getValue(Double.class);
                            double balance = (balanceObj != null) ? balanceObj : 0.0;
                            Toast.makeText(LoginActivity.this, "Login successful! Balance: ₹" + balance, Toast.LENGTH_LONG).show();
                            Log.i(TAG, "Login successful for user: " + phone + ", Balance: " + balance);
                            // TODO: Navigate to home screen or next activity
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
}
