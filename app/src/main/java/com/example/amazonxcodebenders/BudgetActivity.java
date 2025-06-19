package com.example.amazonxcodebenders;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface; // Import for Typeface
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.github.mikephil.charting.charts.PieChart;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.data.PieData;
import com.github.mikephil.charting.data.PieDataSet;
import com.github.mikephil.charting.data.PieEntry;
import com.github.mikephil.charting.formatter.PercentFormatter;

import com.google.android.material.button.MaterialButton;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap; // For sorted map by category

public class BudgetActivity extends AppCompatActivity {

    private static final String TAG = "BudgetActivity";
    private PieChart pieChart;
    private TextView budgetTextView;
    private Button btnAddTransaction;
    private MaterialButton btnGenerateBudget;

    // Filter UI elements
    private Button btnSelectStartDate, btnSelectEndDate, btnApplyFilters;
    private TextView tvStartDate, tvEndDate;
    private Spinner spFilterCategory;

    // Filter data
    private long filterStartDateMillis = 0;
    private long filterEndDateMillis = Long.MAX_VALUE;
    private String selectedFilterCategory = "All Categories";
    private List<Transaction> allTransactionsFromFirebase = new ArrayList<>();

    private DatabaseReference transactionsRef;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_budget);

        // --- Initialize UI elements ---
        pieChart = findViewById(R.id.pieChart);
        budgetTextView = findViewById(R.id.budgetTextView);
        btnAddTransaction = findViewById(R.id.btnAddManualTransaction);
        btnGenerateBudget = findViewById(R.id.btnGenerateBudget);

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

        btnSelectStartDate.setOnClickListener(v -> showDatePickerDialog(true));
        btnSelectEndDate.setOnClickListener(v -> showDatePickerDialog(false));
        btnApplyFilters.setOnClickListener(v -> applyFilters());

        btnGenerateBudget.setOnClickListener(v -> {
//            Toast.makeText(BudgetActivity.this, "Generating monthly budget plan...", Toast.LENGTH_SHORT).show();
            fetchAndGenerateBudgetPlan();
        });


        // --- Initial setup for the pie chart appearance ---
        setupPieChart();

        // Set default filter dates (e.g., last 3 months, or current month)
        setDefaultFilterDates();
    }

    @Override
    protected void onStart() {
        super.onStart();
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
        pieChart.setCenterText("Expense Breakdown\n(Filtered)");
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
        endCal.set(Calendar.HOUR_OF_DAY, 23);
        endCal.set(Calendar.MINUTE, 59);
        endCal.set(Calendar.SECOND, 59);
        endCal.set(Calendar.MILLISECOND, 999);
        filterEndDateMillis = endCal.getTimeInMillis();
        updateDateTextView(tvEndDate, endCal);

        Calendar startCal = Calendar.getInstance();
        startCal.add(Calendar.MONTH, -3); // Default filter is last 3 months
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
                        selectedCal.set(Calendar.HOUR_OF_DAY, 0);
                        selectedCal.set(Calendar.MINUTE, 0);
                        selectedCal.set(Calendar.SECOND, 0);
                        selectedCal.set(Calendar.MILLISECOND, 0);
                        filterStartDateMillis = selectedCal.getTimeInMillis();
                        updateDateTextView(tvStartDate, selectedCal);
                    } else {
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
        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
        tv.setText(sdf.format(calendar.getTime()));
    }

    /**
     * Fetches ALL transaction data from Firebase. This list will be stored locally
     * and then filtered based on user selections. This is for the main filterable chart.
     */
    private void fetchAllTransactionsFromFirebase() {
        transactionsRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                allTransactionsFromFirebase.clear();
                Set<String> categories = new HashSet<>();

                for (DataSnapshot dataSnapshot : snapshot.getChildren()) {
                    Transaction transaction = dataSnapshot.getValue(Transaction.class);
                    if (transaction != null) {
                        transaction.id = dataSnapshot.getKey();
                        allTransactionsFromFirebase.add(transaction);

                        if ("Expense".equalsIgnoreCase(transaction.type)) {
                            categories.add(transaction.category);
                        }
                    }
                }
                Log.d(TAG, "Fetched " + allTransactionsFromFirebase.size() + " total transactions from Firebase for main chart filters.");

                populateCategorySpinner(categories);
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
        categoryList.add(0, "All Categories");
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, categoryList);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spFilterCategory.setAdapter(adapter);

        int selectionIndex = categoryList.indexOf(selectedFilterCategory);
        if (selectionIndex != -1) {
            spFilterCategory.setSelection(selectionIndex);
        } else {
            spFilterCategory.setSelection(0);
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
            if (t.date >= filterStartDateMillis && t.date <= filterEndDateMillis) {
                if ("Expense".equalsIgnoreCase(t.type)) {
                    if ("All Categories".equals(selectedFilterCategory) ||
                            selectedFilterCategory.equalsIgnoreCase(t.category)) {
                        filteredTransactions.add(t);
                    }
                }
            }
        }
        Log.d(TAG, "Filtered down to " + filteredTransactions.size() + " transactions for display.");
        generateBudgetAndShowChart(filteredTransactions);
    }


    /**
     * Generates and displays the budget breakdown using the provided list of transactions.
     * This method is now called *after* data is fetched asynchronously from Firebase.
     *
     * @param transactions The list of Transaction objects to use for generating the budget.
     */
    private void generateBudgetAndShowChart(List<Transaction> transactions) {
        Map<String, Double> categoryTotals = new HashMap<>();
        double totalExpensesSum = 0.0;

        for (Transaction t : transactions) {
            if ("Expense".equalsIgnoreCase(t.type)) {
                try {
                    double amountValue = Double.parseDouble(t.amount);
                    String category = t.category;
                    categoryTotals.put(category, categoryTotals.getOrDefault(category, 0.0) + amountValue);
                    totalExpensesSum += amountValue;
                } catch (NumberFormatException e) {
                    Log.e(TAG, "Invalid amount format during sum for transaction ID: " + t.id + ", Amount: " + t.amount, e);
                }
            }
        }

        Map<String, Double> chartDistribution = categoryTotals;

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

        for (Map.Entry<String, Double> entry : chartDistribution.entrySet()) {
            if (entry.getValue() > 0) {
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
        pieChart.animateY(1400);
        pieChart.invalidate();

        budgetTextView.setText(String.format("Total Expenses for Filtered Period: ₹%.2f", totalExpensesSum));
    }

    /**
     * Fetches transactions for the last 5 months and then calculates and displays
     * a budget plan based on historical data.
     */
    private void fetchAndGenerateBudgetPlan() {
        // Calculate timestamp for the start of the month, 5 months ago
        Calendar startCal = Calendar.getInstance();
        startCal.add(Calendar.MONTH, -5);
        startCal.set(Calendar.DAY_OF_MONTH, 1); // Start from the 1st of the month
        startCal.set(Calendar.HOUR_OF_DAY, 0);
        startCal.set(Calendar.MINUTE, 0);
        startCal.set(Calendar.SECOND, 0);
        startCal.set(Calendar.MILLISECOND, 0);
        long fiveMonthsAgoStartOfMonthTimestamp = startCal.getTimeInMillis();

        // Calculate timestamp for the end of the current month
        Calendar endCal = Calendar.getInstance();
        endCal.set(Calendar.DAY_OF_MONTH, endCal.getActualMaximum(Calendar.DAY_OF_MONTH)); // Last day of current month
        endCal.set(Calendar.HOUR_OF_DAY, 23);
        endCal.set(Calendar.MINUTE, 59);
        endCal.set(Calendar.SECOND, 59);
        endCal.set(Calendar.MILLISECOND, 999);
        long endOfCurrentMonthTimestamp = endCal.getTimeInMillis();

        transactionsRef.orderByChild("date")
                .startAt(fiveMonthsAgoStartOfMonthTimestamp)
                .endAt(endOfCurrentMonthTimestamp)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        List<Transaction> historicalTransactionsForBudgetPlan = new ArrayList<>(); // Renamed for clarity
                        for (DataSnapshot dataSnapshot : snapshot.getChildren()) {
                            Transaction transaction = dataSnapshot.getValue(Transaction.class);
                            if (transaction != null) {
                                transaction.id = dataSnapshot.getKey();
                                historicalTransactionsForBudgetPlan.add(transaction);
                            }
                        }
                        Log.d(TAG, "Fetched " + historicalTransactionsForBudgetPlan.size() + " transactions for budget plan generation.");

                        // Now, calculate the budget plan directly in Java
                        calculateAndDisplayBudgetPlan(historicalTransactionsForBudgetPlan, 0.15); // 15% desired savings
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Log.e(TAG, "Failed to load historical transactions for budget plan: " + error.getMessage(), error.toException());
                        Toast.makeText(BudgetActivity.this, "Failed to load data for budget plan.", Toast.LENGTH_LONG).show();
                    }
                });
    }

    /**
     * Calculates the monthly budget plan based on historical transaction data.
     * This logic is directly implemented in Java.
     * @param historicalTransactions The list of historical transactions.
     * @param desiredSavingsPercentage The percentage of average income to save.
     */
    private void calculateAndDisplayBudgetPlan(List<Transaction> historicalTransactions, double desiredSavingsPercentage) {
        // Map to store monthly income: "YYYY-MM" -> total income
        Map<String, Double> monthlyIncome = new HashMap<>();
        // Map to store monthly expenses by category: "YYYY-MM" -> "Category" -> total amount
        Map<String, Map<String, Double>> monthlyCategoryExpenses = new HashMap<>();

        // Group transactions by month
        for (Transaction t : historicalTransactions) {
            Calendar cal = Calendar.getInstance();
            cal.setTimeInMillis(t.date);
            String yearMonth = String.format(Locale.getDefault(), "%d-%02d",
                    cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1);

            double amountNumeric = 0;
            try {
                amountNumeric = Double.parseDouble(t.amount);
            } catch (NumberFormatException e) {
                Log.e(TAG, "Invalid amount format in transaction: " + t.id + ", amount: " + t.amount);
                continue; // Skip transaction with invalid amount
            }

            if ("Income".equalsIgnoreCase(t.type)) {
                monthlyIncome.put(yearMonth, monthlyIncome.getOrDefault(yearMonth, 0.0) + amountNumeric);
            } else if ("Expense".equalsIgnoreCase(t.type)) {
                monthlyCategoryExpenses
                        .computeIfAbsent(yearMonth, k -> new HashMap<>())
                        .put(t.category, monthlyCategoryExpenses.get(yearMonth).getOrDefault(t.category, 0.0) + amountNumeric);
            }
        }

        // Calculate average monthly income
        double avgMonthlyIncome = 0;
        if (!monthlyIncome.isEmpty()) {
            double totalIncomeSum = 0;
            for (double income : monthlyIncome.values()) {
                totalIncomeSum += income;
            }
            avgMonthlyIncome = totalIncomeSum / monthlyIncome.size();
        }

        // Collect all unique expense categories
        Set<String> allExpenseCategories = new HashSet<>();
        for (Map<String, Double> categoryMap : monthlyCategoryExpenses.values()) {
            allExpenseCategories.addAll(categoryMap.keySet());
        }

        // Calculate average monthly expenses per category
        Map<String, Double> avgMonthlyExpensesPerCategory = new TreeMap<>(); // TreeMap for sorted output
        for (String category : allExpenseCategories) {
            double categoryTotalAcrossMonths = 0;
            int monthCountForCategory = 0; // Count months where this category had expenses

            for (Map<String, Double> monthData : monthlyCategoryExpenses.values()) {
                if (monthData.containsKey(category)) {
                    categoryTotalAcrossMonths += monthData.get(category);
                    monthCountForCategory++;
                }
            }
            // Only average if there was spending in this category over some months
            if (monthCountForCategory > 0) {
                avgMonthlyExpensesPerCategory.put(category, categoryTotalAcrossMonths / monthCountForCategory);
            } else {
                avgMonthlyExpensesPerCategory.put(category, 0.0); // No spending in this category
            }
        }

        // Calculate recommended savings and total expenses based on averages
        double recommendedMonthlySavings = avgMonthlyIncome * desiredSavingsPercentage;
        double totalRecommendedMonthlyExpenseBasedOnHistory = 0;
        for (double amount : avgMonthlyExpensesPerCategory.values()) {
            totalRecommendedMonthlyExpenseBasedOnHistory += amount;
        }

        // Generate analysis notes
        List<String> analysisNotes = new ArrayList<>();
        analysisNotes.add(String.format(Locale.getDefault(), "Budget generated based on your spending and income over the last %d months.", monthlyIncome.size() > 0 ? monthlyIncome.size() : monthlyCategoryExpenses.size() > 0 ? monthlyCategoryExpenses.size() : 0));

        if (avgMonthlyIncome == 0) {
            analysisNotes.add("Historical income data is missing or insufficient. Budget recommendations for expenses are based solely on average past spending, without considering savings goals.");
        } else {
            double availableForExpensesAfterSavings = avgMonthlyIncome - recommendedMonthlySavings;
            if (totalRecommendedMonthlyExpenseBasedOnHistory > availableForExpensesAfterSavings) {
                analysisNotes.add(String.format(Locale.getDefault(), "Warning: Your average expenses (₹%.2f) exceed your income after desired savings (₹%.2f). Consider adjusting your budget or increasing income.", totalRecommendedMonthlyExpenseBasedOnHistory, availableForExpensesAfterSavings));
            } else if (totalRecommendedMonthlyExpenseBasedOnHistory < availableForExpensesAfterSavings) {
                analysisNotes.add(String.format(Locale.getDefault(), "Your average expenses (₹%.2f) are less than your income after desired savings (₹%.2f), indicating good financial health or room for more savings.", totalRecommendedMonthlyExpenseBasedOnHistory, availableForExpensesAfterSavings));
            }
        }


        // *** CHANGE HERE: Start new activity instead of showing AlertDialog ***
        startBudgetPlanActivity(
                avgMonthlyExpensesPerCategory,
                avgMonthlyIncome,
                recommendedMonthlySavings,
                totalRecommendedMonthlyExpenseBasedOnHistory,
                analysisNotes
        );
    }


    /**
     * Starts the BudgetPlanActivity to display the generated budget plan.
     * @param budgetPlan A map of category to recommended monthly amount.
     * @param avgIncome Average monthly income.
     * @param recommendedSavings Recommended monthly savings.
     * @param totalRecommendedExpenses Total recommended expenses.
     * @param analysisNotes Notes from the analysis.
     */
    private void startBudgetPlanActivity(Map<String, Double> budgetPlan, double avgIncome, double recommendedSavings, double totalRecommendedExpenses, List<String> analysisNotes) {
        Intent intent = new Intent(BudgetActivity.this, BudgetPlanActivity.class);

        // Convert Map to ArrayList of Strings/Doubles for Intent (Bundles can't put Map<String, Double> directly)
        // You could also convert Map to a custom Parcelable object if this becomes complex
        ArrayList<String> categoryNames = new ArrayList<>();
        ArrayList<Double> categoryAmounts = new ArrayList<>();
        for (Map.Entry<String, Double> entry : budgetPlan.entrySet()) {
            categoryNames.add(entry.getKey());
            categoryAmounts.add(entry.getValue());
        }

        intent.putStringArrayListExtra("budgetCategoryNames", categoryNames);
        intent.putExtra("budgetCategoryAmounts", categoryAmounts); // ArrayList<Double> can be put directly
        intent.putExtra("avgIncome", avgIncome);
        intent.putExtra("recommendedSavings", recommendedSavings);
        intent.putExtra("totalRecommendedExpenses", totalRecommendedExpenses);
        intent.putStringArrayListExtra("analysisNotes", new ArrayList<>(analysisNotes)); // Ensure it's an ArrayList

        startActivity(intent);
    }

    // This method is now effectively replaced by startBudgetPlanActivity.
    // It's kept for reference but is no longer called.
    /*
    private void showBudgetPlanDialog(Map<String, Double> budgetPlan, double avgIncome, double recommendedSavings, double totalRecommendedExpenses, List<String> analysisNotes) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Your Monthly Budget Plan");

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(30, 20, 30, 20);

        // Add overall summary
        TextView summaryTv = new TextView(this);
        summaryTv.setText(String.format(Locale.getDefault(),
                "Based on your past spending and income:\n\n" +
                        "Avg. Monthly Income: ₹%.2f\n" +
                        "Recommended Savings (15%%): ₹%.2f\n" +
                        "Total Recommended Expenses: ₹%.2f\n\n" +
                        "Recommended Budget per Category:",
                avgIncome, recommendedSavings, totalRecommendedExpenses));
        summaryTv.setTextSize(16f);
        summaryTv.setTextColor(Color.BLACK);
        summaryTv.setPadding(0,0,0,10);
        layout.addView(summaryTv);

        // Add category-wise budget items
        for (Map.Entry<String, Double> entry : budgetPlan.entrySet()) {
            TextView tv = new TextView(this);
            tv.setText(String.format(Locale.getDefault(), "  • %s: ₹%.2f", entry.getKey(), entry.getValue()));
            tv.setTextSize(16f);
            tv.setTextColor(Color.DKGRAY);
            layout.addView(tv);
        }

        // Add analysis notes
        if (analysisNotes != null && !analysisNotes.isEmpty()) {
            TextView notesHeader = new TextView(this);
            notesHeader.setText("\nInsights & Notes:");
            notesHeader.setTextSize(16f);
            notesHeader.setTextStyle(Typeface.BOLD); // CORRECTED LINE
            notesHeader.setTextColor(Color.BLACK);
            notesHeader.setPadding(0,10,0,0);
            layout.addView(notesHeader);

            for (String note : analysisNotes) {
                TextView noteTv = new TextView(this);
                noteTv.setText(String.format(Locale.getDefault(), "  • %s", note));
                noteTv.setTextSize(14f);
                noteTv.setTextColor(Color.GRAY);
                layout.addView(noteTv);
            }
        }


        builder.setView(layout);
        builder.setPositiveButton("OK", (dialog, which) -> dialog.dismiss());
        builder.show();
    }
    */
}
