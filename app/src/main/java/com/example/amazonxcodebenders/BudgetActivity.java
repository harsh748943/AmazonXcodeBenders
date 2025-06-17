package com.example.amazonxcodebenders;

import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.github.mikephil.charting.charts.PieChart;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.data.PieData;
import com.github.mikephil.charting.data.PieDataSet;
import com.github.mikephil.charting.data.PieEntry;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BudgetActivity extends AppCompatActivity {

    private PieChart pieChart;
    private TextView budgetTextView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_budget);

        pieChart = findViewById(R.id.pieChart);
        budgetTextView = findViewById(R.id.budgetTextView);

        generateBudgetAndShowChart();
    }

    private void generateBudgetAndShowChart() {
        List<Expense> expenses = new ArrayList<>();
        expenses.add(new Expense("Food", 1200, System.currentTimeMillis()));
        expenses.add(new Expense("Food", 1100, System.currentTimeMillis()));
        expenses.add(new Expense("Travel", 500, System.currentTimeMillis()));
        expenses.add(new Expense("Rent", 8000, System.currentTimeMillis()));
        expenses.add(new Expense("Entertainment", 900, System.currentTimeMillis()));
        expenses.add(new Expense("Savings", 2000, System.currentTimeMillis()));
        expenses.add(new Expense("Food", 1300, System.currentTimeMillis()));
        expenses.add(new Expense("Travel", 700, System.currentTimeMillis()));
        expenses.add(new Expense("Rent", 8000, System.currentTimeMillis()));

        // Step 1: Show expense list
        LinearLayout expenseListLayout = findViewById(R.id.expenseListLayout);
        for (Expense e : expenses) {
            TextView tv = new TextView(this);
            tv.setText(e.getCategory() + ": ₹" + String.format("%.2f", e.getAmount()));
            tv.setTextSize(16f);
            tv.setPadding(8, 4, 8, 4);
            expenseListLayout.addView(tv);
        }

        // Step 2: Sum amounts by category
        Map<String, Double> totals = new HashMap<>();
        for (Expense e : expenses) {
            String category = e.getCategory();
            totals.put(category, totals.getOrDefault(category, 0.0) + e.getAmount());
        }

        // Step 3: Average over 3 months
        Map<String, Double> budget = new HashMap<>();
        for (Map.Entry<String, Double> entry : totals.entrySet()) {
            budget.put(entry.getKey(), entry.getValue() / 3);
        }

        // Step 4: Show pie chart
        List<PieEntry> entries = new ArrayList<>();
        for (Map.Entry<String, Double> entry : budget.entrySet()) {
            entries.add(new PieEntry(entry.getValue().floatValue(), entry.getKey()));
        }

        PieDataSet dataSet = new PieDataSet(entries, "Suggested Budget");
        dataSet.setColors(new int[]{
                R.color.purple_200, R.color.teal_200, R.color.teal_700, R.color.purple_500, R.color.black
        }, this);
        PieData pieData = new PieData(dataSet);

        PieChart pieChart = findViewById(R.id.pieChart);
        pieChart.setData(pieData);
        pieChart.setUsePercentValues(true);
        pieChart.setDrawHoleEnabled(true);
        pieChart.setEntryLabelTextSize(12f);
        pieChart.getDescription().setEnabled(false);
        pieChart.getLegend().setWordWrapEnabled(true);
        pieChart.getLegend().setTextSize(12f);
        pieChart.invalidate(); // refresh chart
    }

}
