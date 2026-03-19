package com.spendtracker.pro;

import android.content.*;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.biometric.*;
import androidx.core.content.ContextCompat;

public class SplashActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle s) {
        SharedPreferences prefs = getSharedPreferences("stp_prefs", MODE_PRIVATE);
        String mode = prefs.getString("theme_mode",
                prefs.getBoolean("dark_theme_enabled", true) ? "dark" : "light");
        boolean dark = !"light".equals(mode);
        AppCompatDelegate.setDefaultNightMode(
                dark ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO);
        super.onCreate(s);
        setContentView(R.layout.activity_splash);

        NotificationHelper.createChannels(this);
        DailySummaryWorker.schedule(this);

        new Handler(Looper.getMainLooper()).postDelayed(this::checkFirstLaunch, 1200);
    }

    // 🔹 Check if onboarding should be shown
    private void checkFirstLaunch() {

        SharedPreferences appPrefs = getSharedPreferences("app", MODE_PRIVATE);
        boolean firstLaunch = appPrefs.getBoolean("firstLaunch", true);

        if (firstLaunch) {

            // Open onboarding screens
            startActivity(new Intent(this, OnboardingActivity.class));
            finish();

        } else {

            // Continue normal flow
            checkBiometric();
        }
    }

    private void checkBiometric() {
        SharedPreferences prefs = getSharedPreferences("stp_prefs", MODE_PRIVATE);
        boolean bioEnabled = prefs.getBoolean("bio_enabled", false);

        if (bioEnabled) {
            showBiometric();
        } else {
            goToMain();
        }
    }

    private void showBiometric() {

        BiometricPrompt.PromptInfo info = new BiometricPrompt.PromptInfo.Builder()
                .setTitle("SpendTracker Pro")
                .setSubtitle("Verify your identity")
                .setNegativeButtonText("Cancel")
                .build();

        new BiometricPrompt(this, ContextCompat.getMainExecutor(this),
                new BiometricPrompt.AuthenticationCallback() {

                    @Override
                    public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult r) {
                        goToMain();
                    }

                    @Override
                    public void onAuthenticationError(int code, CharSequence msg) {
                        finish();
                    }

                    @Override
                    public void onAuthenticationFailed() {}
                }).authenticate(info);
    }

    private void goToMain() {
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }
}
