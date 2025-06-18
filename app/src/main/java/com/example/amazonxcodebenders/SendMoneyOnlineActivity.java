package com.example.amazonxcodebenders;

import android.os.Bundle;
import android.widget.EditText;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class SendMoneyOnlineActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_send_money_online);

        String recipient = getIntent().getStringExtra("recipient");
        String upi = getIntent().getStringExtra("upi");
        String bankingName = getIntent().getStringExtra("bankingName");
        String amount = getIntent().getStringExtra("amount");

        TextView userName = findViewById(R.id.userName);
        TextView userUpi = findViewById(R.id.userUpi);
        EditText amountInput = findViewById(R.id.amountInput);
        TextView userInitials = findViewById(R.id.userInitials);
        TextView bankingNameView = findViewById(R.id.bankingName);

        userName.setText(recipient);
        userUpi.setText(upi);
        amountInput.setText(amount);
        if (bankingNameView != null) bankingNameView.setText(bankingName);

        // Set initials
        if (recipient != null && recipient.length() > 0) {
            String[] parts = recipient.split(" ");
            String initials = "";
            for (String part : parts) {
                if (part.length() > 0) initials += part.charAt(0);
            }
            userInitials.setText(initials.toUpperCase());
        }
    }
}