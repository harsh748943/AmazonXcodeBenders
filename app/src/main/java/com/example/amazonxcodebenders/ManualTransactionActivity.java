package com.example.amazonxcodebenders;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.database.FirebaseDatabase;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

public class ManualTransactionActivity extends AppCompatActivity {

    private EditText etAmount, etDescription;
    private TextView tvSelectedDate;
    private Spinner spinnerCategory, spinnerPaymentMethod;
    private RadioGroup rgType;
    private Button btnPickDate, btnAddCategory, btnSave;

    private String selectedDate = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_manual_transaction);

        // Initialize views
        etAmount = findViewById(R.id.etAmount);
        etDescription = findViewById(R.id.etDescription);
        tvSelectedDate = findViewById(R.id.tvSelectedDate);
        spinnerCategory = findViewById(R.id.spinnerCategory);
        spinnerPaymentMethod = findViewById(R.id.spinnerPaymentMethod);
        rgType = findViewById(R.id.rgType);
        btnPickDate = findViewById(R.id.btnPickDate);
        btnAddCategory = findViewById(R.id.btnAddCategory);
        btnSave = findViewById(R.id.btnSaveTransaction);

        // Setup default spinner values
        setupDefaultSpinners();

        // Date Picker Dialog
        btnPickDate.setOnClickListener(v -> showDatePicker());

        // Add Category (just a placeholder for now)
        btnAddCategory.setOnClickListener(v -> showAddCategoryDialog());

        // Save Button (placeholder logic)
        btnSave.setOnClickListener(v -> saveTransaction());

    }

    private void setupDefaultSpinners() {
        // Dummy categories and methods (replace with Firebase logic)
        List<String> categories = new ArrayList<>();
        categories.add("Food");
        categories.add("Transport");
        categories.add("Rent");
        categories.add("Utilities");

        ArrayAdapter<String> catAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, categories);
        catAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerCategory.setAdapter(catAdapter);

        List<String> paymentMethods = new ArrayList<>();
        paymentMethods.add("Cash");
        paymentMethods.add("UPI");
        paymentMethods.add("Credit Card");

        ArrayAdapter<String> payAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, paymentMethods);
        payAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerPaymentMethod.setAdapter(payAdapter);
    }

    private void showDatePicker() {
        final Calendar calendar = Calendar.getInstance();
        DatePickerDialog datePickerDialog = new DatePickerDialog(
                this,
                (view, year, month, dayOfMonth) -> {
                    selectedDate = dayOfMonth + "/" + (month + 1) + "/" + year;
                    tvSelectedDate.setText(selectedDate);
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
        );
        datePickerDialog.show();
    }

    private void showAddCategoryDialog() {
        EditText input = new EditText(this);
        new android.app.AlertDialog.Builder(this)
                .setTitle("Add New Category")
                .setView(input)
                .setPositiveButton("Add", (dialog, which) -> {
                    String newCat = input.getText().toString().trim();
                    if (!newCat.isEmpty()) {
                        ArrayAdapter<String> adapter = (ArrayAdapter<String>) spinnerCategory.getAdapter();
                        adapter.add(newCat);
                        adapter.notifyDataSetChanged();
                        spinnerCategory.setSelection(adapter.getPosition(newCat));
                        // TODO: Push newCat to Firebase under users/{uid}/custom_categories
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void saveTransaction() {
        String amountStr = etAmount.getText().toString().trim();
        String description = etDescription.getText().toString().trim();
        String category = spinnerCategory.getSelectedItem().toString();
        String paymentMethod = spinnerPaymentMethod.getSelectedItem().toString();
        String date = tvSelectedDate.getText().toString(); // Get selected date from TextView

        int selectedTypeId = rgType.getCheckedRadioButtonId();
        if (amountStr.isEmpty() || description.isEmpty() || date.isEmpty() || selectedTypeId == -1) {
            Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show();
            return;
        }

        String type = (selectedTypeId == R.id.rbIncome) ? "Income" : "Expense";
        double amount;

        try {
            amount = Double.parseDouble(amountStr);
        } catch (NumberFormatException e) {
            Toast.makeText(this, "Invalid amount entered", Toast.LENGTH_SHORT).show();
            return;
        }

        String id = FirebaseDatabase.getInstance().getReference("transactions").push().getKey();
        if (id == null) {
            Toast.makeText(this, "Failed to generate transaction ID", Toast.LENGTH_SHORT).show();
            return;
        }

        // Create transaction object
        Transaction transaction = new Transaction(id, amountStr, description, date, category, paymentMethod, type);

        // Save to Firebase
        FirebaseDatabase.getInstance().getReference("transactions")
                .child(id)
                .setValue(transaction)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        Toast.makeText(this, "Transaction saved!", Toast.LENGTH_SHORT).show();
                        finish(); // Close activity after saving
                    } else {
                        Toast.makeText(this, "Failed to save. Try again.", Toast.LENGTH_SHORT).show();
                    }
                });
    }

}
