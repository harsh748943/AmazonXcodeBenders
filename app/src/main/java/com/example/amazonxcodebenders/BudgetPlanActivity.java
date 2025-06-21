package com.example.amazonxcodebenders;

import android.graphics.Color;
import android.graphics.Typeface; // Import for Typeface
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONException;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Calendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap; // To keep categories sorted for display

public class BudgetPlanActivity extends AppCompatActivity {

    private static final String TAG = "BudgetPlanActivity"; // Tag for logging
    private TextView tvBudgetSummary;
    private LinearLayout llCategoryBudgetList;
    private LinearLayout llNotesList;
    private View cardNotes;

    private DatabaseReference transactionsRef; // Firebase Realtime Database reference

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_budget_plan);

        // Initialize UI elements
        tvBudgetSummary = findViewById(R.id.tvBudgetSummary);
        llCategoryBudgetList = findViewById(R.id.llCategoryBudgetList);
        llNotesList = findViewById(R.id.llNotesList);
        cardNotes = findViewById(R.id.cardNotes);
//        renderBudgetFromIntent();
        // Initialize Firebase
        transactionsRef = FirebaseDatabase.getInstance().getReference("transactions");
        // Fetch data and generate budget plan when activity is created
        fetchHistoricalTransactionsAndGenerateBudgetPlan();
    }

    private void renderBudgetFromIntent() {
        // Get data passed from BudgetActivity
        ArrayList<String> categoryNames = getIntent().getStringArrayListExtra("budgetCategoryNames");
        double[] categoryAmounts = getIntent().getDoubleArrayExtra("budgetCategoryAmounts");
        double avgIncome = getIntent().getDoubleExtra("avgIncome", 0);
        double recommendedSavings = getIntent().getDoubleExtra("recommendedSavings", 0);
        double totalRecommendedExpenses = getIntent().getDoubleExtra("totalRecommendedExpenses", 0);
        ArrayList<String> analysisNotes = getIntent().getStringArrayListExtra("analysisNotes");

        // Display summary
        tvBudgetSummary.setText(String.format(Locale.getDefault(),
                "Based on your past spending and income:\n\n" +
                        "Avg. Monthly Income: ₹%.2f\n" +
                        "Recommended Savings (15%%): ₹%.2f\n" +
                        "Total Recommended Expenses: ₹%.2f",
                avgIncome, recommendedSavings, totalRecommendedExpenses));

        // Display category-wise budget
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

        // Display analysis notes
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

    /**
     * Fetches transactions for the last 5 months from Firebase Realtime Database
     * and then calculates and displays a budget plan based on that historical data.
     */
    private void fetchHistoricalTransactionsAndGenerateBudgetPlan() {
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
                        List<Transaction> historicalTransactions = new ArrayList<>();
                        for (DataSnapshot dataSnapshot : snapshot.getChildren()) {
                            Transaction transaction = dataSnapshot.getValue(Transaction.class);
                            if (transaction != null) {
                                transaction.id = dataSnapshot.getKey(); // Ensure ID is set
                                historicalTransactions.add(transaction);
                            }
                        }
                        Log.d(TAG, "Fetched " + historicalTransactions.size() + " transactions for budget plan generation.");

                        // Now, calculate the budget plan directly in Java
                        // Assuming a 15% desired savings for now. This could be user-configurable.
//                        calculateAndDisplayBudgetPlan(historicalTransactions, 0.15);
                        sendTransactionsToDeepSeekLLM(historicalTransactions);


                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Log.e(TAG, "Failed to load historical transactions for budget plan: " + error.getMessage(), error.toException());
//                        Toast.makeText(BudgetActivity.this, "Failed to load data for budget plan.", Toast.LENGTH_LONG).show();
                        // Display empty state or error in UI
                        displayEmptyState("Failed to load data for budget plan: " + error.getMessage());
                    }
                });
    }

    /**
     * Calculates the monthly budget plan based on historical transaction data and
     * updates the UI elements in this activity. This serves as the in-app "ML model" logic.
     */


    private void displayEmptyState(String message) {
        tvBudgetSummary.setText(message);
        tvBudgetSummary.setTextColor(Color.RED); // Make error message stand out
        tvBudgetSummary.setTextSize(18f); // Slightly larger for emphasis
        tvBudgetSummary.setTypeface(null, Typeface.ITALIC); // Italic for emphasis

        llCategoryBudgetList.removeAllViews(); // Clear any existing category list
        llNotesList.removeAllViews(); // Clear any existing notes
        cardNotes.setVisibility(View.GONE); // Hide notes card
    }

    private void sendTransactionsToDeepSeekLLM(List<Transaction> transactionList) {
        // 1. Build the prompt string from the transactions
//        System.out.println("functon called");
        StringBuilder promptBuilder = new StringBuilder();
        promptBuilder.append("Based on the following list of income and expense transactions, create a smart monthly budget plan. ")
                .append("The average monthly income to be considered is ₹").append(String.format(Locale.getDefault(), "%.2f", getIntent().getDoubleExtra("avgIncome", 0))).append(". ")
                .append("Return the output strictly as a JSON object with the following structure:\n")
                .append("{\n")
                .append("  \"avgIncome\": <Average monthly income>,\n")
                .append("  \"recommendedSavings\": <Recommended monthly savings>,\n")
                .append("  \"categoryBudget\": {\n")
                .append("    \"<CategoryName>\": <Amount>,\n")
                .append("    ...\n")
                .append("  },\n")
                .append("  \"analysis\": [<String1>, <String2>, ...]\n")
                .append("}\n")
                .append("Transactions:\n\n");


        for (Transaction t : transactionList) {
            String dateStr = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(t.date);
            promptBuilder.append(String.format(Locale.getDefault(),
                    "- %s: ₹%s (%s, %s)\n", dateStr, t.amount, t.category, t.type));
        }

        String prompt = promptBuilder.toString();

        // 2. Construct JSON request payload for OpenRouter API
        JSONObject jsonBody = new JSONObject();
        try {
            jsonBody.put("model", "openai/gpt-3.5-turbo");

            JSONArray messages = new JSONArray();

            JSONObject userMessage = new JSONObject();
            userMessage.put("role", "user");
            userMessage.put("content", prompt);
            messages.put(userMessage);

            jsonBody.put("messages", messages);

        } catch (JSONException e) {
            e.printStackTrace();
            displayEmptyState("Failed to prepare the prompt.");
            return;
        }
//        System.out.println(jsonBody);
        // 3. Set the request to OpenRouter API endpoint
        String openRouterUrl = "https://openrouter.ai/api/v1/chat/completions";
        System.out.println("router link created");
        JsonObjectRequest request = new JsonObjectRequest(
                Request.Method.POST, openRouterUrl, jsonBody,
                response -> {
                    try {
                        System.out.println("reached for debugging in the function");
                        String reply = response
                                .getJSONArray("choices")
                                .getJSONObject(0)
                                .getJSONObject("message")
                                .getString("content");
                        Log.d("AI_RESPONSE", "Full reply:\n" + reply);
                        System.out.println(reply);
// Log to debug
                        Log.d("showThis", "Raw reply from DeepSeek:\n" + reply);

// Extract just the JSON part
                        int start = reply.indexOf('{');
                        int end = reply.lastIndexOf('}');
                        if (start == -1 || end == -1 || start > end) {
                            displayEmptyState("Invalid JSON format from DeepSeek.");
                            return;
                        }
                        String jsonText = reply.substring(start, end + 1);
                        JSONObject json = new JSONObject(jsonText);

                        // Show reply in the summary
                        try {
                            // Try parsing the LLM reply as JSON
//                            JSONObject json = new JSONObject(reply);

                            double avgIncome = json.getDouble("avgIncome");
                            double savings = json.getDouble("recommendedSavings");
                            JSONObject categoryBudget = json.getJSONObject("categoryBudget");

                            // Extract analysis notes
                            List<String> notes = new ArrayList<>();
                            JSONArray analysisArray = json.getJSONArray("analysis");
                            for (int i = 0; i < analysisArray.length(); i++) {
                                notes.add(analysisArray.getString(i));
                            }

                            // Display structured result
//                            displayBudgetResults(categoryBudget, avgIncome, savings);
                            displayBudgetResults(categoryBudget, avgIncome, savings, notes);

                            // Optionally show insights separately
                            llNotesList.removeAllViews();
                            if (!notes.isEmpty()) {
                                TextView header = new TextView(this);
                                header.setText("Insights & Notes:");
                                header.setTextSize(16f);
                                header.setTypeface(null, Typeface.BOLD);
                                header.setPadding(0, 10, 0, 4);
                                llNotesList.addView(header);

                                for (String note : notes) {
                                    TextView noteView = new TextView(this);
                                    noteView.setText("  • " + note);
                                    noteView.setTextSize(14f);
                                    noteView.setTextColor(Color.GRAY);
                                    noteView.setPadding(0, 2, 0, 2);
                                    llNotesList.addView(noteView);
                                }

                                cardNotes.setVisibility(View.VISIBLE);
                            }

                        } catch (JSONException je) {
                            je.printStackTrace();
                            displayEmptyState("DeepSeek replied with an invalid format. Try again.");
                        }
//
//                        llCategoryBudgetList.removeAllViews();
//                        llNotesList.removeAllViews();
//                        cardNotes.setVisibility(View.GONE);

                    } catch (Exception e) {
                        e.printStackTrace();
                        displayEmptyState("Failed to parse DeepSeek response.");
                    }
                },
                error -> {
                    error.printStackTrace();
                    displayEmptyState("Failed to connect to DeepSeek API.");
                }
        ) {
            @Override
            public Map<String, String> getHeaders() {
                Map<String, String> headers = new HashMap<>();
                headers.put("Authorization", "Bearer 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");
                headers.put("Content-Type", "application/json");
                return headers;
            }
        };

        Volley.newRequestQueue(this).add(request);
    }




    private void displayBudgetResults(JSONObject categoryBudget, double avgIncome, double savings, List<String> analysisNotes) {
        try {
            // Set the summary text from AI's result
            tvBudgetSummary.setText(String.format(Locale.getDefault(),
                    "Smart Budget Plan (via AI):\n\n" +
                            "Avg. Monthly Income: ₹%.2f\n" +
                            "Recommended Savings: ₹%.2f",
                    avgIncome, savings));

            // Clear and display category-wise budget
            llCategoryBudgetList.removeAllViews();
            Iterator<String> keys = categoryBudget.keys();

            while (keys.hasNext()) {
                String category = keys.next();
                double amount = categoryBudget.getDouble(category);

                TextView tv = new TextView(this);
                tv.setText(String.format(Locale.getDefault(), "  • %s: ₹%.2f", category, amount));
                tv.setTextSize(16f);
                tv.setTextColor(Color.DKGRAY);
                tv.setPadding(0, 4, 0, 4);
                llCategoryBudgetList.addView(tv);
            }

            // Show analysis notes from AI
            llNotesList.removeAllViews();
            if (!analysisNotes.isEmpty()) {
                TextView notesHeader = new TextView(this);
                notesHeader.setText("\nInsights & Notes:");
                notesHeader.setTextSize(16f);
                notesHeader.setTypeface(null, Typeface.BOLD);
                notesHeader.setTextColor(Color.BLACK);
                notesHeader.setPadding(0, 10, 0, 0);
                llNotesList.addView(notesHeader);

                for (String note : analysisNotes) {
                    TextView noteTv = new TextView(this);
                    noteTv.setText("  • " + note);
                    noteTv.setTextSize(14f);
                    noteTv.setTextColor(Color.GRAY);
                    noteTv.setPadding(0, 2, 0, 2);
                    llNotesList.addView(noteTv);
                }

                cardNotes.setVisibility(View.VISIBLE);
            } else {
                cardNotes.setVisibility(View.GONE);
            }

        } catch (Exception e) {
            e.printStackTrace();
            displayEmptyState("Error displaying AI-generated budget.");
        }
    }




}


