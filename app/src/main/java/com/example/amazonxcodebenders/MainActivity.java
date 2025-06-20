package com.example.amazonxcodebenders;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.telephony.SmsManager;
import android.util.Log;
import android.widget.*;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.example.amazonxcodebenders.paymentOptimization.offlinePayment.CryptoHelper;
import com.example.amazonxcodebenders.paymentOptimization.offlinePayment.KeyStoreHelper;
import com.example.amazonxcodebenders.paymentOptimization.offlinePayment.WalletHelper;

import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    EditText phoneInput, amountInput;
    Button sendBtn, checkBalanceBtn;
    TextView balanceText;

    private static final int SMS_PERMISSION_REQUEST = 100;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);







    }





}