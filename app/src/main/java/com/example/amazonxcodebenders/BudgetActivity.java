package com.example.amazonxcodebenders;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
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

// Removed FirebaseAuth and FirebaseUser imports as they are no longer used for user-specific data
// import com.google.firebase.auth.FirebaseAuth;
// import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BudgetActivity extends AppCompatActivity {

    private static final String TAG = "BudgetActivity";
    private PieChart pieChart;
    private TextView budgetTextView;
    private LinearLayout expenseListLayout;
    private Button btnAddTransaction;

    // This will now point to a generic "transactions" node, not user-specific
    private DatabaseReference transactionsRef;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_budget);

        // --- Initialize UI elements ---
        pieChart = findViewById(R.id.pieChart);
        budgetTextView = findViewById(R.id.budgetTextView);
        expenseListLayout = findViewById(R.id.expenseListLayout);
        btnAddTransaction = findViewById(R.id.btnAddManualTransaction);

        // --- Initialize Firebase components (without user authentication) ---
        // This now points to a top-level "transactions" node.
        // Make sure your Firebase Realtime Database rules allow public read/write if you don't use Auth.
        // Example rules for testing:
        // {
        //   "rules": {
        //     "transactions": {
        //       ".read": true,
        //       ".write": true
        //     }
        //   }
        // }
        // For a deployed app, this is highly insecure!
        transactionsRef = FirebaseDatabase.getInstance().getReference("transactions");


        // --- Set up UI interactions ---
        btnAddTransaction.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Start ManualTransactionActivity to add a new transaction
                Intent intent = new Intent(BudgetActivity.this, ManualTransactionActivity.class);
                startActivity(intent);
            }
        });

        // --- Initial setup for the pie chart appearance ---
        setupPieChart();
    }

    @Override
    protected void onStart() {
        super.onStart();
        // Fetch expenses from Firebase and update the chart whenever the activity starts/resumes
        fetchExpensesFromFirebaseAndGenerateChart();
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
        pieChart.setCenterText("Monthly Budget\nBreakdown");
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
     * Fetches expense data from Firebase and then
     * calls generateBudgetAndShowChart to update the UI.
     * This method correctly handles the asynchronous nature of Firebase.
     */
    private void fetchExpensesFromFirebaseAndGenerateChart() {
        // Calculate timestamp for 3 months ago (or adjust as per your budgeting period)
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.MONTH, -3); // Get expenses from the last 3 months
        long threeMonthsAgoTimestamp = calendar.getTimeInMillis();

        // Attach a ValueEventListener to listen for all relevant expenses
        // This will now listen to the global "transactions" node.
        transactionsRef.orderByChild("date")
                .startAt(threeMonthsAgoTimestamp) // Filter transactions from the last 3 months
                .addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        // Changed to List<Transaction> to match your new class
                        List<Transaction> transactionsFromFirebase = new ArrayList<>();
                        for (DataSnapshot dataSnapshot : snapshot.getChildren()) {
                            // Fetch as Transaction.class
                            Transaction transaction = dataSnapshot.getValue(Transaction.class);
                            if (transaction != null) {
                                // Ensure the ID is set from the Firebase key.
                                // Since 'id' is public in Transaction, direct assignment is fine.
                                transaction.id = dataSnapshot.getKey();
                                transactionsFromFirebase.add(transaction);
                            }
                        }
                        Log.d(TAG, "Fetched " + transactionsFromFirebase.size() + " transactions from Firebase for charting.");
                        // Now, pass the fetched and filtered list to the chart generation method
                        // Changed to pass List<Transaction>
                        generateBudgetAndShowChart(transactionsFromFirebase);
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Log.e(TAG, "Failed to load transactions from Firebase: " + error.getMessage(), error.toException());
                        Toast.makeText(BudgetActivity.this, "Failed to load transactions: " + error.getMessage(), Toast.LENGTH_LONG).show();
                    }
                });
    }


    /**
     * Generates and displays the budget breakdown using the provided list of expenses.
     * This method is now called *after* data is fetched asynchronously from Firebase.
     *
     * @param expenses The list of Expense objects to use for generating the budget.
     */
    /**
     * Generates and displays the budget breakdown using the provided list of transactions.
     * This method is now called *after* data is fetched asynchronously from Firebase.
     *
     * @param transactions The list of Transaction objects to use for generating the budget.
     */
        private void generateBudgetAndShowChart(List<Transaction> transactions) { // Changed parameter to List<Transaction>
            // Step 1: Show expense list in the LinearLayout
            expenseListLayout.removeAllViews(); // Clear previous views
            if (transactions.isEmpty()) { // Check the transactions list
                TextView noDataTv = new TextView(this);
                noDataTv.setText("No transactions recorded for this period.");
                noDataTv.setTextSize(16f);
                noDataTv.setPadding(8, 4, 8, 4);
                expenseListLayout.addView(noDataTv);
            } else {
                for (Transaction t : transactions) { // Iterate through Transaction objects
                    TextView tv = new TextView(this);
                    try {
                        // amount is a String in your Transaction class, parse it to double
                        double amountValue = Double.parseDouble(t.amount);
                        String amountString = String.format("%.2f", amountValue);
                        // Only display expense type transactions here. You might want a separate list for income.
                        // Use .equalsIgnoreCase to match "Expense" or "expense"
                        if ("Expense".equalsIgnoreCase(t.type)) {
                            tv.setText(t.category + ": ₹" + amountString + " (" + t.description + ")");
                            tv.setTextSize(16f);
                            tv.setPadding(8, 4, 8, 4);
                            expenseListLayout.addView(tv);
                        }
                    } catch (NumberFormatException e) {
                        // Log the error if the amount string cannot be parsed
                        Log.e(TAG, "Invalid amount format for transaction ID: " + t.id + ", Amount: " + t.amount, e);
                        // You could optionally display a message to the user or skip this entry
                    }
                }
            }


            // Step 2: Sum amounts by category, *only* for 'Expense' types
            Map<String, Double> categoryTotals = new HashMap<>();
            double totalExpensesSum = 0.0;

            for (Transaction t : transactions) { // Iterate through Transaction objects
                // Only sum if it's an actual "Expense" type (case-insensitive)
                if ("Expense".equalsIgnoreCase(t.type)) {
                    try {
                        // amount is a String, parse it to double for calculations
                        double amountValue = Double.parseDouble(t.amount);
                        String category = t.category;
                        categoryTotals.put(category, categoryTotals.getOrDefault(category, 0.0) + amountValue);
                        totalExpensesSum += amountValue;
                    } catch (NumberFormatException e) {
                        // Log the error if the amount string cannot be parsed
                        Log.e(TAG, "Invalid amount format during sum for transaction ID: " + t.id + ", Amount: " + t.amount, e);
                        // Skip this transaction for sum if amount is invalid
                    }
                }
            }

            // --- Budget Calculation Logic ---
            // The "Average over 3 months" logic:
            // If your `fetchTransactionsFromFirebaseAndGenerateChart` is already fetching
            // data for the last 3 months, then `categoryTotals` already represents
            // the sum over those 3 months. To get a monthly average, you would divide each total by 3.
            //
            // For now, let's just use the current `categoryTotals` as the budget distribution.
            // For simplicity, I'll display the sum for the fetched period in the chart.
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
                pieChart.setCenterText("No Expense Data"); // Or "No Transactions"
                pieChart.invalidate();
                budgetTextView.setText("No expense data available for the selected period."); // Or "No transaction data"
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
            budgetTextView.setText(String.format("Total Expenses for Last 3 Months: ₹%.2f", totalExpensesSum));
        }
}