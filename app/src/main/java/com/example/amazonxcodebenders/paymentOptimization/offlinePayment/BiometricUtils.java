package com.example.amazonxcodebenders.paymentOptimization.offlinePayment;

import android.content.Context;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;

public class BiometricUtils {

    // Call this to show biometric prompt
    public static void authenticate(Context context, BiometricPrompt.AuthenticationCallback callback) {
        BiometricPrompt biometricPrompt = new BiometricPrompt(
                (androidx.fragment.app.FragmentActivity) context,
                ContextCompat.getMainExecutor(context),
                callback
        );
        BiometricPrompt.PromptInfo promptInfo = new BiometricPrompt.PromptInfo.Builder()
                .setTitle("Biometric Login")
                .setSubtitle("Authenticate to proceed")
                .setNegativeButtonText("Cancel")
                .build();
        biometricPrompt.authenticate(promptInfo);
    }
}
