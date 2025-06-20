package com.example.amazonxcodebenders.budgeting;

import java.util.*;

public class BudgetHelper {

    public static Map<String, Double> calculateSuggestedBudget(List<Expense> expenses) {
        Map<String, Double> totals = new HashMap<>();

        for (Expense expense : expenses) {
            totals.put(
                    expense.getCategory(),
                    totals.getOrDefault(expense.getCategory(), 0.0) + expense.getAmount()
            );
        }

        Map<String, Double> budget = new HashMap<>();
        for (Map.Entry<String, Double> entry : totals.entrySet()) {
            budget.put(entry.getKey(), entry.getValue() / 3); // avg over 3 months
        }

        return budget;
    }
}

