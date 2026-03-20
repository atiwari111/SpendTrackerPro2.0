package com.spendtracker.pro;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.*;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.camera.core.*;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import java.util.concurrent.ExecutionException;
import java.util.regex.*;

/**
 * P5: OCR Receipt Scanner using CameraX + ML Kit Text Recognition.
 *
 * Captures a photo, extracts the largest amount and merchant name
 * from the receipt text, and returns them to AddExpenseActivity via Intent extras.
 *
 * Result extras:
 *   OcrScanActivity.EXTRA_AMOUNT   (double)
 *   OcrScanActivity.EXTRA_MERCHANT (String)
 */
public class OcrScanActivity extends AppCompatActivity {

    public static final String EXTRA_AMOUNT   = "ocr_amount";
    public static final String EXTRA_MERCHANT = "ocr_merchant";

    private PreviewView previewView;
    private CardView cardResult;
    private TextView tvOcrAmount, tvOcrMerchant, tvOcrHint;
    private Button btnCapture, btnUseResult;

    private ImageCapture imageCapture;
    private TextRecognizer recognizer;

    private double extractedAmount  = 0;
    private String extractedMerchant = "";

    // Amount pattern — e.g. "Total: 349.00", "Rs.299", "₹ 1,250.50"
    private static final Pattern AMOUNT_PAT = Pattern.compile(
            "(?i)(?:total|amount|grand\\s*total|rs\\.?|inr|₹)[\\s:]*([0-9,]+(?:\\.[0-9]{1,2})?)");

    // Fallback — largest bare number on the receipt
    private static final Pattern NUMBER_PAT = Pattern.compile(
            "\\b([0-9]{2,6}(?:\\.[0-9]{1,2})?)\\b");

    private final ActivityResultLauncher<String> cameraPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) startCamera();
                else {
                    Toast.makeText(this, "Camera permission required", Toast.LENGTH_LONG).show();
                    finish();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ocr_scan);

        Toolbar tb = findViewById(R.id.toolbar);
        setSupportActionBar(tb);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Scan Receipt");
        }

        previewView  = findViewById(R.id.previewView);
        cardResult   = findViewById(R.id.cardResult);
        tvOcrAmount  = findViewById(R.id.tvOcrAmount);
        tvOcrMerchant= findViewById(R.id.tvOcrMerchant);
        tvOcrHint    = findViewById(R.id.tvOcrHint);
        btnCapture   = findViewById(R.id.btnCapture);
        btnUseResult = findViewById(R.id.btnUseResult);

        recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);

        btnCapture.setOnClickListener(v -> captureAndScan());
        btnUseResult.setOnClickListener(v -> returnResult());

        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        } else {
            cameraPermissionLauncher.launch(android.Manifest.permission.CAMERA);
        }
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> future =
                ProcessCameraProvider.getInstance(this);
        future.addListener(() -> {
            try {
                ProcessCameraProvider provider = future.get();
                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());

                imageCapture = new ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                        .build();

                provider.unbindAll();
                provider.bindToLifecycle(this,
                        new androidx.camera.core.CameraSelector.Builder()
                                .requireLensFacing(CameraSelector.LENS_FACING_BACK).build(),
                        preview, imageCapture);

            } catch (ExecutionException | InterruptedException e) {
                Toast.makeText(this, "Camera failed: " + e.getMessage(),
                        Toast.LENGTH_LONG).show();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void captureAndScan() {
        if (imageCapture == null) return;
        btnCapture.setEnabled(false);
        tvOcrHint.setText("Scanning...");

        imageCapture.takePicture(ContextCompat.getMainExecutor(this),
                new ImageCapture.OnImageCapturedCallback() {
                    @Override
                    public void onCaptureSuccess(@NonNull ImageProxy proxy) {
                        @SuppressWarnings("UnsafeOptInUsageError")
                        InputImage image = InputImage.fromMediaImage(
                                proxy.getImage(), proxy.getImageInfo().getRotationDegrees());

                        recognizer.process(image)
                                .addOnSuccessListener(result -> {
                                    proxy.close();
                                    parseOcrResult(result.getText());
                                })
                                .addOnFailureListener(e -> {
                                    proxy.close();
                                    btnCapture.setEnabled(true);
                                    tvOcrHint.setText("Scan failed — try again");
                                });
                    }

                    @Override
                    public void onError(@NonNull ImageCaptureException e) {
                        btnCapture.setEnabled(true);
                        tvOcrHint.setText("Capture failed — try again");
                    }
                });
    }

    private void parseOcrResult(String text) {
        // 1. Try labelled amount pattern first (Total, Rs., etc.)
        Matcher m = AMOUNT_PAT.matcher(text);
        double bestAmount = 0;
        while (m.find()) {
            try {
                double a = Double.parseDouble(m.group(1).replace(",", ""));
                if (a > bestAmount) bestAmount = a;
            } catch (NumberFormatException ignored) {
                // Expected — not every regex match is a valid number
            }
        }

        // 2. Fallback — largest number on the receipt
        if (bestAmount == 0) {
            Matcher nm = NUMBER_PAT.matcher(text);
            while (nm.find()) {
                try {
                    double a = Double.parseDouble(nm.group(1).replace(",", ""));
                    if (a > bestAmount && a < 100_000) bestAmount = a;
                } catch (NumberFormatException ignored) {
                    // Expected — scanning raw text for numeric tokens
                }
            }
        }

        // 3. Merchant — first non-numeric line (typically the shop/restaurant name)
        String merchant = "";
        for (String line : text.split("\n")) {
            String clean = line.trim();
            if (clean.length() >= 3 && !clean.matches(".*[0-9]{4,}.*")
                    && !clean.toLowerCase().matches("(total|amount|date|time|receipt|bill|gst|tax|invoice).*")) {
                merchant = clean;
                break;
            }
        }

        extractedAmount  = bestAmount;
        extractedMerchant = merchant;

        if (bestAmount > 0) {
            tvOcrAmount.setText("Amount: ₹" + String.format("%.2f", bestAmount));
            tvOcrMerchant.setText("Merchant: " + (merchant.isEmpty() ? "—" : merchant));
            cardResult.setVisibility(View.VISIBLE);
            btnUseResult.setVisibility(View.VISIBLE);
            btnCapture.setText("🔄  Rescan");
            tvOcrHint.setText("Tap 'Use This' to fill the form, or rescan");
        } else {
            tvOcrHint.setText("No amount found — try better lighting or closer");
        }
        btnCapture.setEnabled(true);
    }

    private void returnResult() {
        Intent result = new Intent();
        result.putExtra(EXTRA_AMOUNT,   extractedAmount);
        result.putExtra(EXTRA_MERCHANT, extractedMerchant);
        setResult(RESULT_OK, result);
        finish();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            getOnBackPressedDispatcher().onBackPressed(); return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        recognizer.close();
    }
}
