package com.example.amazonxcodebenders;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.amazonxcodebenders.paymentOptimization.voicePayment.ChooseContactActivity;
import com.google.firebase.auth.FirebaseAuth;

public class RegisterActivity extends AppCompatActivity {

    private EditText inputPhoneRegister, inputPasswordRegister;
    private Button btnRegister;
    private TextView tvRegister;
    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        inputPhoneRegister = findViewById(R.id.inputPhoneRegister);
        inputPasswordRegister = findViewById(R.id.inputPasswordRegister);
        btnRegister = findViewById(R.id.btnRegister);
        tvRegister = findViewById(R.id.tvRegister);
        mAuth = FirebaseAuth.getInstance();

        btnRegister.setOnClickListener(v -> {
            String password = inputPasswordRegister.getText().toString();
            String email = inputPhoneRegister.getText().toString() + "@example.com";

            // Step 1: Check password breach
            HibpChecker.checkPassword(password, isPasswordBreached -> runOnUiThread(() -> {
                if (isPasswordBreached) {
                    // Password breached - block registration
                    Toast.makeText(RegisterActivity.this,
                            "⚠️ This password has been leaked. Please choose a different one.",
                            Toast.LENGTH_LONG).show();
                } else {
                    // Password safe - now check email
                    HibpChecker.checkEmail(email, isEmailBreached -> runOnUiThread(() -> {
                        if (isEmailBreached) {
                            // Email breached - warn but allow registration
                            Toast.makeText(RegisterActivity.this,
                                    "⚠️ This email has appeared in a data breach. Please enable two-factor authentication.",
                                    Toast.LENGTH_LONG).show();
                        }

                        // Proceed with registration (password is safe)
                        Intent intent = new Intent(RegisterActivity.this, LoginActivity.class);

                        startActivity(intent);

                    }));
                }
            }));
        });
    }

    private void registerUser(String email, String password) {
        mAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        Toast.makeText(RegisterActivity.this,
                                "✅ Registered Successfully",
                                Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(RegisterActivity.this,
                                "❌ Registration Failed: " + task.getException().getMessage(),
                                Toast.LENGTH_LONG).show();
                    }
                });
    }
}
