package com.example.amazonxcodebenders.budgeting;

import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.amazonxcodebenders.R;

import java.util.ArrayList;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap; // To keep categories sorted for display

public class BudgetPlanActivity extends AppCompatActivity {

    private TextView tvBudgetSummary;
    private LinearLayout llCategoryBudgetList;
    private LinearLayout llNotesList;
    private View cardNotes; // Reference to the notes CardView

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_budget_plan);

        // Initialize UI elements
        tvBudgetSummary = findViewById(R.id.tvBudgetSummary);
        llCategoryBudgetList = findViewById(R.id.llCategoryBudgetList);
        llNotesList = findViewById(R.id.llNotesList);
        cardNotes = findViewById(R.id.cardNotes);

        // Get data from Intent
        Bundle extras = getIntent().getExtras();
        if (extras != null) {
            double avgIncome = extras.getDouble("avgIncome", 0.0);
            double recommendedSavings = extras.getDouble("recommendedSavings", 0.0);
            double totalRecommendedExpenses = extras.getDouble("totalRecommendedExpenses", 0.0);
            ArrayList<String> categoryNames = extras.getStringArrayList("budgetCategoryNames");
            ArrayList<Double> categoryAmounts = (ArrayList<Double>) extras.getSerializable("budgetCategoryAmounts"); // Cast needed for ArrayList<Double>
            ArrayList<String> analysisNotes = extras.getStringArrayList("analysisNotes");

            displayBudgetPlan(
                    categoryNames,
                    categoryAmounts,
                    avgIncome,
                    recommendedSavings,
                    totalRecommendedExpenses,
                    analysisNotes
            );
        } else {
            // Handle case where no data is passed (e.g., show error message)
            tvBudgetSummary.setText("No budget plan data available.");
            llCategoryBudgetList.removeAllViews();
            llNotesList.removeAllViews();
            cardNotes.setVisibility(View.GONE); // Hide notes card if no data
        }
    }

    /**
     * Displays the budget plan details in the activity's UI.
     */
    private void displayBudgetPlan(
            ArrayList<String> categoryNames,
            ArrayList<Double> categoryAmounts,
            double avgIncome,
            double recommendedSavings,
            double totalRecommendedExpenses,
            ArrayList<String> analysisNotes) {

        // Populate Summary
        tvBudgetSummary.setText(String.format(Locale.getDefault(),
                "Based on your past spending and income:\n\n" +
                        "Avg. Monthly Income: ₹%.2f\n" +
                        "Recommended Savings (15%%): ₹%.2f\n" +
                        "Total Recommended Expenses: ₹%.2f",
                avgIncome, recommendedSavings, totalRecommendedExpenses));

        // Populate Category Budget List
        if (categoryNames != null && categoryAmounts != null && categoryNames.size() == categoryAmounts.size()) {
            // Use a TreeMap to sort categories alphabetically for display
            Map<String, Double> sortedBudgetPlan = new TreeMap<>();
            for (int i = 0; i < categoryNames.size(); i++) {
                sortedBudgetPlan.put(categoryNames.get(i), categoryAmounts.get(i));
            }

            llCategoryBudgetList.removeAllViews(); // Clear existing views
            for (Map.Entry<String, Double> entry : sortedBudgetPlan.entrySet()) {
                TextView tv = new TextView(this);
                tv.setText(String.format(Locale.getDefault(), "  • %s: ₹%.2f", entry.getKey(), entry.getValue()));
                tv.setTextSize(16f);
                tv.setTextColor(Color.DKGRAY);
                tv.setPadding(0,4,0,4); // Add some padding
                llCategoryBudgetList.addView(tv);
            }
        } else {
            TextView noCategoriesTv = new TextView(this);
            noCategoriesTv.setText("No category-wise budget recommendations.");
            noCategoriesTv.setTextSize(16f);
            noCategoriesTv.setTextColor(Color.GRAY);
            llCategoryBudgetList.addView(noCategoriesTv);
        }

        // Populate Analysis Notes
        if (analysisNotes != null && !analysisNotes.isEmpty()) {
            llNotesList.removeAllViews(); // Clear existing views
            for (String note : analysisNotes) {
                TextView noteTv = new TextView(this);
                noteTv.setText(String.format(Locale.getDefault(), "  • %s", note));
                noteTv.setTextSize(14f);
                noteTv.setTextColor(Color.GRAY);
                noteTv.setPadding(0,2,0,2); // Add some padding
                llNotesList.addView(noteTv);
            }
            cardNotes.setVisibility(View.VISIBLE); // Ensure notes card is visible
        } else {
            cardNotes.setVisibility(View.GONE); // Hide notes card if no notes
        }
    }
}
