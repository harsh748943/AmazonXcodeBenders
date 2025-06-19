package com.example.amazonxcodebenders;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.bottomsheet.BottomSheetDialog;

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
        String initials = "";
        if (recipient != null && recipient.length() > 0) {
            String[] parts = recipient.split(" ");
            for (String part : parts) {
                if (part.length() > 0) initials += part.charAt(0);
            }
            userInitials.setText(initials.toUpperCase());
        }

        // Only show bottom sheet if launched via voice
        boolean showBottomSheet = getIntent().getBooleanExtra("showBottomSheet", false);
        if (showBottomSheet) {
            showPaymentBottomSheet(initials.toUpperCase(), recipient, upi, bankingName, amount);
        }
    }

    private void showPaymentBottomSheet(String initials, String name, String upi, String bankingName, String amount) {
        BottomSheetDialog bottomSheetDialog = new BottomSheetDialog(this, R.style.BottomSheetDialogTheme);
        View sheetView = LayoutInflater.from(this).inflate(R.layout.bottom_sheet_payment, null);

        ((TextView) sheetView.findViewById(R.id.bsUserInitials)).setText(initials);
        ((TextView) sheetView.findViewById(R.id.bsUserName)).setText(name);
        ((TextView) sheetView.findViewById(R.id.bsUserUpi)).setText(upi);
        ((TextView) sheetView.findViewById(R.id.bsBankingName)).setText(bankingName);
        ((TextView) sheetView.findViewById(R.id.bsAmount)).setText("₹" + amount);

        Button payBtn = sheetView.findViewById(R.id.bsPayBtn);
        payBtn.setText("Pay ₹" + amount);
        payBtn.setOnClickListener(v -> {
            // TODO: Handle payment action
            bottomSheetDialog.dismiss();
        });

        bottomSheetDialog.setContentView(sheetView);
        bottomSheetDialog.show();
    }
}
