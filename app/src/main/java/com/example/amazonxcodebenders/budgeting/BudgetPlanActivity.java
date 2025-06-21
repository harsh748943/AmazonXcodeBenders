package com.example.amazonxcodebenders.budgeting;

import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.example.amazonxcodebenders.R;


import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.android.volley.Request;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.HashMap;

public class BudgetPlanActivity extends AppCompatActivity {

    private static final String TAG = "BudgetPlanActivity";
    private TextView tvBudgetSummary;
    private LinearLayout llCategoryBudgetList;
    private LinearLayout llNotesList;
    private View cardNotes;

    private DatabaseReference transactionsRef;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_budget_plan);

        // Initialize UI
        tvBudgetSummary = findViewById(R.id.tvBudgetSummary);
        llCategoryBudgetList = findViewById(R.id.llCategoryBudgetList);
        llNotesList = findViewById(R.id.llNotesList);
        cardNotes = findViewById(R.id.cardNotes);

        transactionsRef = FirebaseDatabase.getInstance().getReference("transactions");

        fetchHistoricalTransactionsAndGenerateBudgetPlan();
    }

    private void renderBudgetFromIntent() {
        ArrayList<String> categoryNames = getIntent().getStringArrayListExtra("budgetCategoryNames");
        double[] categoryAmounts = getIntent().getDoubleArrayExtra("budgetCategoryAmounts");
        double avgIncome = getIntent().getDoubleExtra("avgIncome", 0);
        double recommendedSavings = getIntent().getDoubleExtra("recommendedSavings", 0);
        double totalRecommendedExpenses = getIntent().getDoubleExtra("totalRecommendedExpenses", 0);
        ArrayList<String> analysisNotes = getIntent().getStringArrayListExtra("analysisNotes");

        tvBudgetSummary.setText(String.format(Locale.getDefault(),
                "Based on your past spending and income:\n\n" +
                        "Avg. Monthly Income: ₹%.2f\n" +
                        "Recommended Savings (15%%): ₹%.2f\n" +
                        "Total Recommended Expenses: ₹%.2f",
                avgIncome, recommendedSavings, totalRecommendedExpenses));

        llCategoryBudgetList.removeAllViews();
        if (categoryNames != null && categoryAmounts != null && categoryNames.size() == categoryAmounts.length) {
            for (int i = 0; i < categoryNames.size(); i++) {
                String category = categoryNames.get(i);
                double amount = categoryAmounts[i];

                TextView tv = new TextView(this);
                tv.setText(String.format(Locale.getDefault(), "  • %s: ₹%.2f", category, amount));
                tv.setTextSize(16f);
                tv.setTextColor(Color.DKGRAY);
                tv.setPadding(0, 4, 0, 4);
                llCategoryBudgetList.addView(tv);
            }
        } else {
            TextView emptyTv = new TextView(this);
            emptyTv.setText("No category-wise budget recommendations.");
            emptyTv.setTextSize(16f);
            emptyTv.setTextColor(Color.GRAY);
            llCategoryBudgetList.addView(emptyTv);
        }

        llNotesList.removeAllViews();
        if (analysisNotes != null && !analysisNotes.isEmpty()) {
            TextView notesHeader = new TextView(this);
            notesHeader.setText("\nInsights & Notes:");
            notesHeader.setTextSize(16f);
            notesHeader.setTypeface(null, Typeface.BOLD);
            notesHeader.setTextColor(Color.BLACK);
            notesHeader.setPadding(0, 10, 0, 0);
            llNotesList.addView(notesHeader);

            for (String note : analysisNotes) {
                TextView noteTv = new TextView(this);
                noteTv.setText(String.format(Locale.getDefault(), "  • %s", note));
                noteTv.setTextSize(14f);
                noteTv.setTextColor(Color.GRAY);
                noteTv.setPadding(0, 2, 0, 2);
                llNotesList.addView(noteTv);
            }

            cardNotes.setVisibility(View.VISIBLE);
        } else {
            cardNotes.setVisibility(View.GONE);
        }

        Log.d(TAG, "Budget plan displayed successfully.");
    }

    private void fetchHistoricalTransactionsAndGenerateBudgetPlan() {
        Calendar startCal = Calendar.getInstance();
        startCal.add(Calendar.MONTH, -5);
        startCal.set(Calendar.DAY_OF_MONTH, 1);
        startCal.set(Calendar.HOUR_OF_DAY, 0);
        startCal.set(Calendar.MINUTE, 0);
        startCal.set(Calendar.SECOND, 0);
        startCal.set(Calendar.MILLISECOND, 0);
        long fiveMonthsAgoStart = startCal.getTimeInMillis();

        Calendar endCal = Calendar.getInstance();
        endCal.set(Calendar.DAY_OF_MONTH, endCal.getActualMaximum(Calendar.DAY_OF_MONTH));
        endCal.set(Calendar.HOUR_OF_DAY, 23);
        endCal.set(Calendar.MINUTE, 59);
        endCal.set(Calendar.SECOND, 59);
        endCal.set(Calendar.MILLISECOND, 999);
        long endOfCurrentMonth = endCal.getTimeInMillis();

        transactionsRef.orderByChild("date")
                .startAt(fiveMonthsAgoStart)
                .endAt(endOfCurrentMonth)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        List<Transaction> transactionList = new ArrayList<>();
                        for (DataSnapshot dataSnapshot : snapshot.getChildren()) {
                            Transaction t = dataSnapshot.getValue(Transaction.class);
                            if (t != null) {
                                t.id = dataSnapshot.getKey();
                                transactionList.add(t);
                            }
                        }
                        sendTransactionsToDeepSeekLLM(transactionList);
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        displayEmptyState("Failed to load data: " + error.getMessage());
                    }
                });
    }

    private void sendTransactionsToDeepSeekLLM(List<Transaction> transactionList) {
        StringBuilder promptBuilder = new StringBuilder();
        double avgIncome = getIntent().getDoubleExtra("avgIncome", 0);

        promptBuilder.append("Based on the following list of income and expense transactions, create a smart monthly budget plan. and keep the overall sum of the expenses of all categories you are recommending to be exactly as input of average monthly income. There must be some savings. ")
                .append("Also identify if there are any over expenditure among the expenses. Give me one or 2 extra analysis also related to expenses.")
                .append("The average monthly income to be considered is ₹").append(String.format(Locale.getDefault(), "%.2f", avgIncome)).append(". ")
                .append("Return the output strictly as a JSON object with the following structure:\n")
                .append("{\n")
                .append("  \"avgIncome\": <Average monthly income>,\n")
                .append("  \"recommendedSavings\": <Recommended monthly savings>,\n")
                .append("  \"categoryBudget\": {\n")
                .append("    \"<CategoryName>\": <Amount>,\n")
                .append("    ...\n")
                .append("  },\n")
                .append("  \"analysis\": [<String1>, <String2>, ...]\n")
                .append("}\n\nTransactions:\n");


        for (Transaction t : transactionList) {
            String date = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(t.date);
            promptBuilder.append(String.format(Locale.getDefault(),
                    "- %s: ₹%s (%s, %s)\n", date, t.amount, t.category, t.type));
        }

        JSONObject jsonBody = new JSONObject();
        try {
            jsonBody.put("model", "openai/gpt-3.5-turbo");

            JSONArray messages = new JSONArray();
            JSONObject userMessage = new JSONObject();
            userMessage.put("role", "user");
            userMessage.put("content", promptBuilder.toString());
            messages.put(userMessage);

            jsonBody.put("messages", messages);
        } catch (JSONException e) {
            displayEmptyState("Failed to prepare prompt.");
            return;
        }
        Log.d("AI_RESPONSE", "this" + promptBuilder);
        String url = "https://openrouter.ai/api/v1/chat/completions";

        JsonObjectRequest request = new JsonObjectRequest(Request.Method.POST, url, jsonBody,
                response -> {
                    try {
                        String reply = response.getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content");
                        int start = reply.indexOf('{');
                        int end = reply.lastIndexOf('}');
                        if (start == -1 || end == -1) {
                            displayEmptyState("Invalid response format.");
                            return;
                        }
                        Log.d("AI_RESPONSE", "this" + reply);
                        String jsonText = reply.substring(start, end + 1);
                        JSONObject json = new JSONObject(jsonText);

                        double parsedIncome = json.getDouble("avgIncome");
                        double savings = json.getDouble("recommendedSavings");
                        JSONObject categoryBudget = json.getJSONObject("categoryBudget");

                        List<String> notes = new ArrayList<>();
                        JSONArray analysis = json.getJSONArray("analysis");
                        for (int i = 0; i < analysis.length(); i++) {
                            notes.add(analysis.getString(i));
                        }

                        displayBudgetResults(categoryBudget, parsedIncome, savings, notes);
                    } catch (Exception e) {
                        displayEmptyState("Error parsing AI response.");
                    }
                },
                error -> displayEmptyState("Failed to connect to Server.")
        ) {
            @Override
            public Map<String, String> getHeaders() {
                Map<String, String> headers = new HashMap<>();
                headers.put("Authorization", "Bearer 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"); // Replace with valid key
                headers.put("Content-Type", "application/json");
                return headers;
            }
        };

        Volley.newRequestQueue(this).add(request);
    }

    private void displayBudgetResults(JSONObject categoryBudget, double avgIncome, double savings, List<String> analysisNotes) {
        try {
            tvBudgetSummary.setText(String.format(Locale.getDefault(),
                    "Smart Budget Plan (via AI):\n\n" +
                            "Avg. Monthly Income: ₹%.2f\n" +
                            "Recommended Savings: ₹%.2f",
                    avgIncome, savings));

            llCategoryBudgetList.removeAllViews();
            Iterator<String> keys = categoryBudget.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                double amount = categoryBudget.getDouble(key);
                TextView tv = new TextView(this);
                tv.setText(String.format(Locale.getDefault(), "  • %s: ₹%.2f", key, amount));
                tv.setTextSize(16f);
                tv.setTextColor(Color.DKGRAY);
                tv.setPadding(0, 4, 0, 4);
                llCategoryBudgetList.addView(tv);
            }

            llNotesList.removeAllViews();
            if (!analysisNotes.isEmpty()) {
                TextView header = new TextView(this);
                header.setText("\nInsights & Notes:");
                header.setTextSize(16f);
                header.setTypeface(null, Typeface.BOLD);
                header.setTextColor(Color.BLACK);
                header.setPadding(0, 10, 0, 0);
                llNotesList.addView(header);

                for (String note : analysisNotes) {
                    TextView tv = new TextView(this);
                    tv.setText("  • " + note);
                    tv.setTextSize(14f);
                    tv.setTextColor(Color.GRAY);
                    tv.setPadding(0, 2, 0, 2);
                    llNotesList.addView(tv);
                }

                cardNotes.setVisibility(View.VISIBLE);
            } else {
                cardNotes.setVisibility(View.GONE);
            }

        } catch (Exception e) {
            displayEmptyState("Error displaying budget results.");
        }
    }

    private void displayEmptyState(String message) {
        tvBudgetSummary.setText(message);
        tvBudgetSummary.setTextColor(Color.RED);
        tvBudgetSummary.setTextSize(18f);
        tvBudgetSummary.setTypeface(null, Typeface.ITALIC);

        llCategoryBudgetList.removeAllViews();
        llNotesList.removeAllViews();
        cardNotes.setVisibility(View.GONE);
    }
}
