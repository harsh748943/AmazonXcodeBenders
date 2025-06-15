package com.example.amazonxcodebenders;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class HomeActivity extends AppCompatActivity {

    private ImageView sendMoneyImage;
    private TextView sendMoneyText;
    private boolean isConnected = false;
    private NetworkChangeReceiver networkChangeReceiver;

    private TextView offlineBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        sendMoneyImage = findViewById(R.id.sendMoneyImage);
        sendMoneyText = findViewById(R.id.sendMoneyText);
        offlineBar = findViewById(R.id.offlineBar);

        // Initial check and set text
        isConnected = isInternetAvailable();
        updateSendMoneyText(isConnected);

        // Click listener for ImageView
        sendMoneyImage.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (isConnected) {
                    startActivity(new Intent(HomeActivity.this, SendMoneyOnlineActivity.class));
                } else {
                    startActivity(new Intent(HomeActivity.this, SendMoneyOfflineActivity.class));
                }
            }
        });

        // Register network receiver
        networkChangeReceiver = new NetworkChangeReceiver();
        registerReceiver(networkChangeReceiver, new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (networkChangeReceiver != null) {
            unregisterReceiver(networkChangeReceiver);
        }
    }

    private boolean isInternetAvailable() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm != null) {
            NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
            return activeNetwork != null && activeNetwork.isConnected();
        }
        return false;
    }

    private void updateSendMoneyText(boolean connected) {
        if (connected) {
            sendMoneyText.setText("Send Money");
        } else {
            sendMoneyText.setText("Send Money Offline");
        }
    }

    private class NetworkChangeReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            isConnected = isInternetAvailable();
            updateSendMoneyText(isConnected);
            updateOfflineBar(isConnected);
        }
    }

    private void updateOfflineBar(boolean isConnected) {
        if (isConnected) {
            offlineBar.setVisibility(View.GONE);
        } else {
            offlineBar.setVisibility(View.VISIBLE);
        }
    }
}
