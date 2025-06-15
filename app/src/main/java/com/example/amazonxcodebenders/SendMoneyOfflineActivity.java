package com.example.amazonxcodebenders;

import android.os.Bundle;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;

public class SendMoneyOfflineActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        System.out.println("offline");
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_send_money_offline);
        WalletViewModel walletViewModel = new ViewModelProvider(this).get(WalletViewModel.class);
        TextView tvWalletBalance = findViewById(R.id.tvWalletBalance);

        walletViewModel.getBalance().observe(this, newBalance -> {
            tvWalletBalance.setText("₹" + String.format("%.2f", newBalance));
        });


    }


}