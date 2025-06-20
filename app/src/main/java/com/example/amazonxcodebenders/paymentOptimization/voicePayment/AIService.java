package com.example.amazonxcodebenders.paymentOptimization.voicePayment;

public class AIService {
    public static class AIResult {
        public String intent, recipient, amount;
        public AIResult(String i, String r, String a) { intent = i; recipient = r; amount = a; }
    }
}
