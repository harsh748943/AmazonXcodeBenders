package com.example.amazonxcodebenders;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

public class MoneyTransferActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_money_transfer);

        TextView tvDetails = findViewById(R.id.tvDetails);
        Button btnPay = findViewById(R.id.btnPay);

        String recipient = getIntent().getStringExtra("recipient");
        String phone = getIntent().getStringExtra("phone");
        String amount = getIntent().getStringExtra("amount");

        String details = "Recipient: " + recipient + "\nPhone: " + phone + "\nAmount: ₹" + amount;
        tvDetails.setText(details);

        btnPay.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Show payment methods, PIN dialog, etc.
                Toast.makeText(MoneyTransferActivity.this, "Money sent to " + recipient + "!", Toast.LENGTH_SHORT).show();
                finish();
            }
        });
    }
}
