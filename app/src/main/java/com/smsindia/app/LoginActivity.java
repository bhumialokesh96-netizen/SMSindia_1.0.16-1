package com.smsindia.app;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class LoginActivity extends AppCompatActivity {

    private EditText phoneInput, passwordInput, referInput;
    private Button loginBtn, signupBtn;
    private TextView deviceIdText;

    private FirebaseFirestore db;
    private String deviceId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        db = FirebaseFirestore.getInstance();

        phoneInput = findViewById(R.id.phoneInput);
        passwordInput = findViewById(R.id.passwordInput);
        referInput = findViewById(R.id.referInput);
        loginBtn = findViewById(R.id.loginBtn);
        signupBtn = findViewById(R.id.signupBtn);
        deviceIdText = findViewById(R.id.deviceIdText);

        // Get Unique Device ID
        deviceId = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
        deviceIdText.setText("Device ID: " + deviceId);

        // 1. CHECK CLIPBOARD FOR REFERRAL CODE (From Vercel Website)
        checkClipboardForReferral();

        loginBtn.setOnClickListener(v -> loginUser());
        signupBtn.setOnClickListener(v -> registerUser());
    }

    // --- NEW: Auto-paste Referral Code from Website ---
    private void checkClipboardForReferral() {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null && clipboard.hasPrimaryClip()) {
            ClipData.Item item = clipboard.getPrimaryClip().getItemAt(0);
            if (item.getText() != null) {
                String pasteData = item.getText().toString().trim();
                // Basic check: Is it a number? Is it 10 digits?
                if (pasteData.length() >= 6 && pasteData.matches("\\d+")) {
                    referInput.setText(pasteData);
                    Toast.makeText(this, "Referral Code Applied: " + pasteData, Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    private void loginUser() {
        String phone = phoneInput.getText().toString().trim();
        String password = passwordInput.getText().toString().trim();

        if (TextUtils.isEmpty(phone) || TextUtils.isEmpty(password)) {
            Toast.makeText(this, "Enter phone and password", Toast.LENGTH_SHORT).show();
            return;
        }

        db.collection("users").document(phone).get().addOnSuccessListener(snapshot -> {
            if (!snapshot.exists()) {
                Toast.makeText(this, "User not found! Please Register.", Toast.LENGTH_SHORT).show();
                return;
            }

            String storedPass = snapshot.getString("password");
            String storedDevice = snapshot.getString("deviceId");

            if (storedPass != null && storedPass.equals(password)) {
                // Allow login if it's the SAME device OR if storedDevice is null (legacy users)
                if (storedDevice == null || storedDevice.equals(deviceId)) {
                    // If device ID was missing in DB, update it now
                    if(storedDevice == null) {
                         db.collection("users").document(phone).update("deviceId", deviceId);
                    }
                    saveLoginAndRedirect(phone);
                } else {
                    Toast.makeText(this, "Login denied: Account linked to another device.", Toast.LENGTH_LONG).show();
                }
            } else {
                Toast.makeText(this, "Incorrect password", Toast.LENGTH_SHORT).show();
            }
        }).addOnFailureListener(e ->
                Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    private void registerUser() {
        String phone = phoneInput.getText().toString().trim();
        String password = passwordInput.getText().toString().trim();
        String referCode = referInput.getText().toString().trim();

        if (TextUtils.isEmpty(phone) || TextUtils.isEmpty(password)) {
            Toast.makeText(this, "Enter phone and password", Toast.LENGTH_SHORT).show();
            return;
        }

        // 1. CHECK IF DEVICE IS ALREADY REGISTERED
        db.collection("users")
                .whereEqualTo("deviceId", deviceId)
                .get()
                .addOnSuccessListener(query -> {
                    
                    // --- FIX STARTS HERE ---
                    if (!query.isEmpty()) {
                        // Device ID exists. But is it YOURS?
                        DocumentSnapshot existingDoc = query.getDocuments().get(0);
                        String existingPhone = existingDoc.getId(); // Assuming doc ID is phone

                        if (existingPhone.equals(phone)) {
                            // YES! It's the same user on the same device.
                            // Instead of blocking, we just Log them in.
                            Toast.makeText(this, "Account exists! Logging you in...", Toast.LENGTH_SHORT).show();
                            saveLoginAndRedirect(phone);
                            return;
                        } else {
                            // NO! This device belongs to someone else.
                            Toast.makeText(this, "Device limit reached! This phone is registered to: " + existingPhone, Toast.LENGTH_LONG).show();
                            return;
                        }
                    }
                    // --- FIX ENDS HERE ---

                    // 2. Check if Phone Number is taken (by a different device)
                    db.collection("users").document(phone).get()
                            .addOnSuccessListener(snapshot -> {
                                if (snapshot.exists()) {
                                    Toast.makeText(this, "Phone already registered! Please Login.", Toast.LENGTH_LONG).show();
                                    return;
                                }

                                // 3. Create New User
                                Map<String, Object> user = new HashMap<>();
                                user.put("phone", phone);
                                user.put("password", password);
                                user.put("deviceId", deviceId);
                                user.put("createdAt", System.currentTimeMillis());
                                user.put("balance", 0.0);
                                user.put("coins", 0);       
                                user.put("sms_count", 0);   
                                user.put("referral_count", 0);

                                // Handle Referral
                                if (!TextUtils.isEmpty(referCode)) {
                                    if (referCode.equals(phone)) {
                                        Toast.makeText(this, "You cannot refer yourself!", Toast.LENGTH_SHORT).show();
                                        return;
                                    }
                                    user.put("referredBy", referCode);
                                    updateReferrer(referCode);
                                }

                                // 4. Save to Firestore
                                db.collection("users").document(phone).set(user)
                                        .addOnSuccessListener(unused -> saveLoginAndRedirect(phone))
                                        .addOnFailureListener(e -> Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show());
                            });
                });
    }

    private void updateReferrer(String referrerPhone) {
        db.collection("users").document(referrerPhone)
                .update("referral_count", FieldValue.increment(1))
                .addOnFailureListener(e -> { });
    }

    private void saveLoginAndRedirect(String phone) {
        SharedPreferences prefs = getSharedPreferences("SMSINDIA_USER", MODE_PRIVATE);
        prefs.edit()
                .putString("mobile", phone)
                .putString("deviceId", deviceId)
                .apply();

        showLoadingAndProceed("Welcome! Logging you in...", () -> {
            startActivity(new Intent(LoginActivity.this, MainActivity.class));
            finish();
        });
    }

    private void showLoadingAndProceed(String message, Runnable onComplete) {
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_loading, null);
        TextView tvMessage = dialogView.findViewById(R.id.tv_loading_message);
        tvMessage.setText(message);
        builder.setView(dialogView);
        builder.setCancelable(false);

        android.app.AlertDialog dialog = builder.create();
        dialog.show();

        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
            dialog.dismiss();
            onComplete.run();
        }, 1500);
    }
}
