package com.example.amazonxcodebenders;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout; // Still used for root layout, not for list display
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.github.mikephil.charting.charts.PieChart;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.data.PieData;
import com.github.mikephil.charting.data.PieDataSet;
import com.github.mikephil.charting.data.PieEntry;
import com.github.mikephil.charting.formatter.PercentFormatter;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;

public class BudgetActivity extends AppCompatActivity {

    private static final String TAG = "BudgetActivity";
    private PieChart pieChart;
    private TextView budgetTextView;
    // Removed expenseListLayout as it's no longer used for displaying list items directly
    private Button btnAddTransaction;

    // Filter UI elements
    private Button btnSelectStartDate, btnSelectEndDate, btnApplyFilters;
    private TextView tvStartDate, tvEndDate;
    private Spinner spFilterCategory;

    // Filter data
    private long filterStartDateMillis = 0; // Default to epoch for no start filter
    private long filterEndDateMillis = Long.MAX_VALUE; // Default to max for no end filter
    private String selectedFilterCategory = "All Categories"; // Default filter
    private List<Transaction> allTransactionsFromFirebase = new ArrayList<>(); // Store all fetched transactions

    private DatabaseReference transactionsRef;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_budget);

        // --- Initialize UI elements ---
        pieChart = findViewById(R.id.pieChart);
        budgetTextView = findViewById(R.id.budgetTextView);
        btnAddTransaction = findViewById(R.id.btnAddManualTransaction);

        // Filter UI elements initialization
        btnSelectStartDate = findViewById(R.id.btnSelectStartDate);
        btnSelectEndDate = findViewById(R.id.btnSelectEndDate);
        tvStartDate = findViewById(R.id.tvStartDate);
        tvEndDate = findViewById(R.id.tvEndDate);
        spFilterCategory = findViewById(R.id.spFilterCategory);
        btnApplyFilters = findViewById(R.id.btnApplyFilters);

        // --- Initialize Firebase components ---
        transactionsRef = FirebaseDatabase.getInstance().getReference("transactions");

        // --- Set up UI interactions ---
        btnAddTransaction.setOnClickListener(v -> {
            Intent intent = new Intent(BudgetActivity.this, ManualTransactionActivity.class);
            startActivity(intent);
        });

        btnSelectStartDate.setOnClickListener(v -> showDatePickerDialog(true)); // true for start date
        btnSelectEndDate.setOnClickListener(v -> showDatePickerDialog(false)); // false for end date
        btnApplyFilters.setOnClickListener(v -> applyFilters());

        // --- Initial setup for the pie chart appearance ---
        setupPieChart();

        // Set default filter dates (e.g., last 3 months, or current month)
        setDefaultFilterDates();
    }

    @Override
    protected void onStart() {
        super.onStart();
        // Fetch ALL relevant transactions from Firebase initially.
        // We will then filter this list in memory based on user selections.
        fetchAllTransactionsFromFirebase();
    }

    /**
     * Sets up the initial visual properties of the PieChart.
     */
    private void setupPieChart() {
        pieChart.setUsePercentValues(true);
        pieChart.getDescription().setEnabled(false);
        pieChart.setExtraOffsets(5, 10, 5, 5);
        pieChart.setDragDecelerationFrictionCoef(0.95f);

        pieChart.setDrawHoleEnabled(true);
        pieChart.setHoleColor(Color.WHITE);
        pieChart.setTransparentCircleColor(Color.WHITE);
        pieChart.setTransparentCircleAlpha(110);
        pieChart.setHoleRadius(58f);
        pieChart.setTransparentCircleRadius(61f);

        pieChart.setDrawCenterText(true);
//        pieChart.setCenterText("Monthly Budget\nBreakdown"); // This text could change dynamically
        pieChart.setCenterTextSize(14f);
        pieChart.setCenterTextColor(Color.BLACK);

        pieChart.setEntryLabelColor(Color.BLACK);
        pieChart.setEntryLabelTextSize(12f);

        Legend l = pieChart.getLegend();
        l.setVerticalAlignment(Legend.LegendVerticalAlignment.TOP);
        l.setHorizontalAlignment(Legend.LegendHorizontalAlignment.RIGHT);
        l.setOrientation(Legend.LegendOrientation.VERTICAL);
        l.setDrawInside(false);
        l.setXEntrySpace(7f);
        l.setYEntrySpace(0f);
        l.setYOffset(0f);
        l.setWordWrapEnabled(true);
        l.setTextSize(12f);
    }

    /**
     * Sets default filter dates, e.g., the last 3 months from today.
     */
    private void setDefaultFilterDates() {
        Calendar endCal = Calendar.getInstance();
        // Set end date to end of today
        endCal.set(Calendar.HOUR_OF_DAY, 23);
        endCal.set(Calendar.MINUTE, 59);
        endCal.set(Calendar.SECOND, 59);
        endCal.set(Calendar.MILLISECOND, 999);
        filterEndDateMillis = endCal.getTimeInMillis();
        updateDateTextView(tvEndDate, endCal);

        Calendar startCal = Calendar.getInstance();
        startCal.add(Calendar.MONTH, -3); // 3 months ago
        // Set start date to beginning of that day
        startCal.set(Calendar.HOUR_OF_DAY, 0);
        startCal.set(Calendar.MINUTE, 0);
        startCal.set(Calendar.SECOND, 0);
        startCal.set(Calendar.MILLISECOND, 0);
        filterStartDateMillis = startCal.getTimeInMillis();
        updateDateTextView(tvStartDate, startCal);
    }

    /**
     * Shows a DatePickerDialog for selecting either start or end date.
     * @param isStartDate true if selecting start date, false for end date.
     */
    private void showDatePickerDialog(final boolean isStartDate) {
        Calendar currentCal = Calendar.getInstance();
        if (isStartDate && filterStartDateMillis != 0) {
            currentCal.setTimeInMillis(filterStartDateMillis);
        } else if (!isStartDate && filterEndDateMillis != Long.MAX_VALUE) {
            currentCal.setTimeInMillis(filterEndDateMillis);
        }

        int year = currentCal.get(Calendar.YEAR);
        int month = currentCal.get(Calendar.MONTH);
        int day = currentCal.get(Calendar.DAY_OF_MONTH);

        DatePickerDialog datePickerDialog = new DatePickerDialog(
                this,
                (view, year1, monthOfYear, dayOfMonth) -> {
                    Calendar selectedCal = Calendar.getInstance();
                    selectedCal.set(year1, monthOfYear, dayOfMonth);
                    if (isStartDate) {
                        // Set to beginning of the day for start date
                        selectedCal.set(Calendar.HOUR_OF_DAY, 0);
                        selectedCal.set(Calendar.MINUTE, 0);
                        selectedCal.set(Calendar.SECOND, 0);
                        selectedCal.set(Calendar.MILLISECOND, 0);
                        filterStartDateMillis = selectedCal.getTimeInMillis();
                        updateDateTextView(tvStartDate, selectedCal);
                    } else {
                        // Set to end of the day for end date
                        selectedCal.set(Calendar.HOUR_OF_DAY, 23);
                        selectedCal.set(Calendar.MINUTE, 59);
                        selectedCal.set(Calendar.SECOND, 59);
                        selectedCal.set(Calendar.MILLISECOND, 999);
                        filterEndDateMillis = selectedCal.getTimeInMillis();
                        updateDateTextView(tvEndDate, selectedCal);
                    }
                },
                year, month, day);
        datePickerDialog.show();
    }

    /**
     * Updates the TextView with the formatted date from a Calendar object.
     * @param tv The TextView to update.
     * @param calendar The Calendar object containing the date.
     */
    private void updateDateTextView(TextView tv, Calendar calendar) {
        tv.setText(String.format(Locale.getDefault(), "%02d/%02d/%d",
                calendar.get(Calendar.DAY_OF_MONTH),
                calendar.get(Calendar.MONTH) + 1,
                calendar.get(Calendar.YEAR)));
    }

    /**
     * Fetches ALL transaction data from Firebase. This list will be stored locally
     * and then filtered based on user selections.
     */
    private void fetchAllTransactionsFromFirebase() {
        transactionsRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                allTransactionsFromFirebase.clear(); // Clear previous data
                Set<String> categories = new HashSet<>(); // To collect unique categories

                for (DataSnapshot dataSnapshot : snapshot.getChildren()) {
                    Transaction transaction = dataSnapshot.getValue(Transaction.class);
                    if (transaction != null) {
                        transaction.id = dataSnapshot.getKey(); // Set ID
                        allTransactionsFromFirebase.add(transaction);

                        // Collect unique categories for the spinner, only for expenses
                        if ("Expense".equalsIgnoreCase(transaction.type)) {
                            categories.add(transaction.category);
                        }
                    }
                }
                Log.d(TAG, "Fetched " + allTransactionsFromFirebase.size() + " total transactions from Firebase.");

                // Populate the category spinner once data is fetched
                populateCategorySpinner(categories);

                // Apply filters to display initial data (e.g., last 3 months, all categories)
                applyFilters();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Failed to load transactions from Firebase: " + error.getMessage(), error.toException());
                Toast.makeText(BudgetActivity.this, "Failed to load transactions: " + error.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    /**
     * Populates the category spinner with unique categories from fetched transactions.
     * @param uniqueCategories A set of unique expense categories.
     */
    private void populateCategorySpinner(Set<String> uniqueCategories) {
        List<String> categoryList = new ArrayList<>(uniqueCategories);
        categoryList.add(0, "All Categories"); // Add "All Categories" option at the beginning
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, categoryList);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spFilterCategory.setAdapter(adapter);

        // Restore previously selected category if it exists in the new list
        int selectionIndex = categoryList.indexOf(selectedFilterCategory);
        if (selectionIndex != -1) {
            spFilterCategory.setSelection(selectionIndex);
        } else {
            spFilterCategory.setSelection(0); // Select "All Categories" if previous selection is gone
            selectedFilterCategory = "All Categories";
        }
    }

    /**
     * Applies the selected filters (date range and category) to the fetched transactions
     * and then updates the pie chart and total expense view.
     */
    private void applyFilters() {
        selectedFilterCategory = spFilterCategory.getSelectedItem().toString();

        List<Transaction> filteredTransactions = new ArrayList<>();
        for (Transaction t : allTransactionsFromFirebase) {
            // Apply Date Filter
            if (t.date >= filterStartDateMillis && t.date <= filterEndDateMillis) {
                // Apply Category Filter (only if it's an expense and category matches or "All Categories")
                if ("Expense".equalsIgnoreCase(t.type)) {
                    if ("All Categories".equals(selectedFilterCategory) ||
                            selectedFilterCategory.equalsIgnoreCase(t.category)) {
                        filteredTransactions.add(t);
                    }
                }
            }
        }
        Log.d(TAG, "Filtered down to " + filteredTransactions.size() + " transactions.");
        generateBudgetAndShowChart(filteredTransactions);
    }


    /**
     * Generates and displays the budget breakdown using the provided list of transactions.
     * This method is now called *after* data is fetched asynchronously from Firebase.
     *
     * @param transactions The list of Transaction objects to use for generating the budget.
     */
    private void generateBudgetAndShowChart(List<Transaction> transactions) {
        // We no longer display the list directly in expenseListLayout here.
        // The LinearLayout is removed from the budget activity.

        // Step 1: Sum amounts by category, *only* for 'Expense' types
        Map<String, Double> categoryTotals = new HashMap<>();
        double totalExpensesSum = 0.0;

        for (Transaction t : transactions) {
            if ("Expense".equalsIgnoreCase(t.type)) { // Only sum if it's an actual "Expense" type
                try {
                    double amountValue = Double.parseDouble(t.amount);
                    String category = t.category;
                    categoryTotals.put(category, categoryTotals.getOrDefault(category, 0.0) + amountValue);
                    totalExpensesSum += amountValue;
                } catch (NumberFormatException e) {
                    Log.e(TAG, "Invalid amount format during sum for transaction ID: " + t.id + ", Amount: " + t.amount, e);
                    // Skip this transaction for sum if amount is invalid
                }
            }
        }

        // --- Budget Calculation Logic ---
        // For simplicity, use current category totals for the chart.
        Map<String, Double> suggestedBudgetDistribution = categoryTotals;


        // Step 4: Show pie chart
        List<PieEntry> entries = new ArrayList<>();
        ArrayList<Integer> colors = new ArrayList<>();

        int[] materialColors = new int[]{
                ContextCompat.getColor(this, R.color.pie_red),
                ContextCompat.getColor(this, R.color.pie_blue),
                ContextCompat.getColor(this, R.color.pie_green),
                ContextCompat.getColor(this, R.color.pie_orange),
                ContextCompat.getColor(this, R.color.pie_purple),
                ContextCompat.getColor(this, R.color.pie_yellow_green),
                ContextCompat.getColor(this, R.color.pie_teal),
                ContextCompat.getColor(this, R.color.pie_dark_blue),
                ContextCompat.getColor(this, R.color.pie_brown),
                ContextCompat.getColor(this, R.color.pie_pink)
        };
        int colorIndex = 0;

        for (Map.Entry<String, Double> entry : suggestedBudgetDistribution.entrySet()) {
            if (entry.getValue() > 0) { // Only add categories with actual positive values
                entries.add(new PieEntry(entry.getValue().floatValue(), entry.getKey()));
                colors.add(materialColors[colorIndex % materialColors.length]);
                colorIndex++;
            }
        }

        if (entries.isEmpty()) {
            pieChart.clear();
            pieChart.setCenterText("No Expense Data for this filter");
            pieChart.invalidate();
            budgetTextView.setText("Total Expenses: ₹0.00 (Filtered)");
            return;
        }

        PieDataSet dataSet = new PieDataSet(entries, "Expense Categories");
        dataSet.setSliceSpace(3f);
        dataSet.setSelectionShift(5f);
        dataSet.setColors(colors);

        dataSet.setValueLinePart1OffsetPercentage(80.f);
        dataSet.setValueLinePart1Length(0.2f);
        dataSet.setValueLinePart2Length(0.4f);
        dataSet.setYValuePosition(PieDataSet.ValuePosition.OUTSIDE_SLICE);

        PieData pieData = new PieData(dataSet);
        pieData.setValueFormatter(new PercentFormatter(pieChart));
        pieData.setValueTextSize(12f);
        pieData.setValueTextColor(Color.BLACK);

        pieChart.setData(pieData);
        pieChart.animateY(1400); // Animation
        pieChart.invalidate(); // Refresh chart

        // Step 5: Update the budgetTextView with total calculated expenses
        budgetTextView.setText(String.format("Total Expenses for Filtered Period: ₹%.2f", totalExpensesSum));
    }
}