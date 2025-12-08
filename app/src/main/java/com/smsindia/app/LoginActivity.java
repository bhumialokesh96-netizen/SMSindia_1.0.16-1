package com.smsindia.app;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.MediaDrm;
import android.media.UnsupportedSchemeException;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Base64;
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
import java.util.UUID;

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

        // 🔒 GENERATE PERMANENT HARDWARE ID
        deviceId = getHardwareDeviceId(this);
        
        // Show only last 6 chars for privacy/cleanliness
        String displayId = (deviceId.length() > 6) ? deviceId.substring(0, 6) : deviceId;
        deviceIdText.setText("HwID: " + displayId);

        checkClipboardForReferral();

        loginBtn.setOnClickListener(v -> loginUser());
        signupBtn.setOnClickListener(v -> registerUser());
    }

    // 🔒 CORE SECURITY: GET PERMANENT HARDWARE ID
    private String getHardwareDeviceId(Context context) {
        // 1. Try Widevine ID (Physical Hardware ID - Persists after reinstall)
        UUID widevineUuid = new UUID(-0x121074568629b532L, -0x5c37d8232ae2de13L);
        try {
            MediaDrm mediaDrm = new MediaDrm(widevineUuid);
            byte[] widevineId = mediaDrm.getPropertyByteArray(MediaDrm.PROPERTY_DEVICE_UNIQUE_ID);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                mediaDrm.close();
            } else {
                mediaDrm.release();
            }
            // Return Hash of Hardware ID
            return Base64.encodeToString(widevineId, Base64.NO_WRAP).trim();
        } catch (Exception e) {
            // 2. Fallback to Android ID if device doesn't support DRM (Rare)
            return Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID);
        }
    }

    private void checkClipboardForReferral() {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null && clipboard.hasPrimaryClip()) {
            ClipData.Item item = clipboard.getPrimaryClip().getItemAt(0);
            if (item.getText() != null) {
                String pasteData = item.getText().toString().trim();
                if (pasteData.length() >= 6 && pasteData.matches("\\d+")) {
                    referInput.setText(pasteData);
                    Toast.makeText(this, "Referral Code Applied", Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    // --- LOGIN LOGIC ---
    private void loginUser() {
        String phoneRaw = phoneInput.getText().toString().trim();
        String password = passwordInput.getText().toString().trim();
        final String phone = phoneRaw.replace("+91", "").replace(" ", "");

        if (TextUtils.isEmpty(phone) || TextUtils.isEmpty(password)) {
            Toast.makeText(this, "Enter phone and password", Toast.LENGTH_SHORT).show();
            return;
        }

        loginBtn.setEnabled(false);

        db.collection("users").document(phone).get().addOnSuccessListener(snapshot -> {
            loginBtn.setEnabled(true);
            
            if (!snapshot.exists()) {
                Toast.makeText(this, "User not found! Please Register.", Toast.LENGTH_SHORT).show();
                return;
            }

            String storedPass = snapshot.getString("password");
            String storedDevice = snapshot.getString("deviceId");

            if (storedPass != null && storedPass.equals(password)) {
                
                // LOGIC: Since we use Hardware ID now, it SHOULD match.
                // But if they are coming from an OLD version of the app (which used Android ID),
                // the IDs might be different. 
                
                // Allow login if Password is correct
                // Update DB with the new Hardware ID to lock them to this physical phone
                if (storedDevice == null || !storedDevice.equals(deviceId)) {
                    db.collection("users").document(phone).update("deviceId", deviceId);
                }

                saveLoginAndRedirect(phone);
            } else {
                Toast.makeText(this, "Incorrect password", Toast.LENGTH_SHORT).show();
            }
        }).addOnFailureListener(e -> {
            loginBtn.setEnabled(true);
            Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        });
    }

    // --- REGISTER LOGIC (ANTI-SPAM) ---
    private void registerUser() {
        String phoneRaw = phoneInput.getText().toString().trim();
        String password = passwordInput.getText().toString().trim();
        String referCode = referInput.getText().toString().trim();
        final String phone = phoneRaw.replace("+91", "").replace(" ", "");

        if (TextUtils.isEmpty(phone) || TextUtils.isEmpty(password)) {
            Toast.makeText(this, "Enter phone and password", Toast.LENGTH_SHORT).show();
            return;
        }

        signupBtn.setEnabled(false);

        // 1. STRICT CHECK: IS THIS PHYSICAL DEVICE ALREADY USED?
        db.collection("users")
                .whereEqualTo("deviceId", deviceId)
                .get()
                .addOnSuccessListener(query -> {
                    
                    if (!query.isEmpty()) {
                        // DEVICE FOUND IN DB
                        DocumentSnapshot existingDoc = query.getDocuments().get(0);
                        String existingPhone = existingDoc.getId();

                        if (existingPhone.equals(phone)) {
                            // Same user, Same device -> Allow Login
                            saveLoginAndRedirect(phone);
                        } else {
                            // DIFFERENT USER, SAME DEVICE -> BLOCK SPAM
                            signupBtn.setEnabled(true);
                            Toast.makeText(this, "⚠️ Device Limit! This phone is already linked to: " + existingPhone, Toast.LENGTH_LONG).show();
                        }
                        return;
                    }

                    // 2. DEVICE IS CLEAN. CHECK PHONE NUMBER
                    db.collection("users").document(phone).get()
                            .addOnSuccessListener(snapshot -> {
                                if (snapshot.exists()) {
                                    signupBtn.setEnabled(true);
                                    Toast.makeText(this, "Phone Number already registered!", Toast.LENGTH_LONG).show();
                                    return;
                                }

                                // 3. ALL CLEAN -> REGISTER
                                createNewUser(phone, password, referCode);
                            });
                })
                .addOnFailureListener(e -> {
                    signupBtn.setEnabled(true);
                    Toast.makeText(this, "Check Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    private void createNewUser(String phone, String password, String referCode) {
        Map<String, Object> user = new HashMap<>();
        user.put("phone", phone);
        user.put("password", password);
        user.put("deviceId", deviceId); // SAVES HARDWARE ID
        user.put("createdAt", System.currentTimeMillis());
        user.put("balance", 0.0);
        user.put("coins", 0);       
        user.put("sms_count", 0);   
        user.put("referral_count", 0);

        if (!TextUtils.isEmpty(referCode) && !referCode.equals(phone)) {
            user.put("referredBy", referCode);
            updateReferrer(referCode);
        }

        db.collection("users").document(phone).set(user)
                .addOnSuccessListener(unused -> saveLoginAndRedirect(phone))
                .addOnFailureListener(e -> {
                    signupBtn.setEnabled(true);
                    Toast.makeText(this, "Register Failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    private void updateReferrer(String referrerPhone) {
        db.collection("users").document(referrerPhone).update("referral_count", FieldValue.increment(1));
    }

    private void saveLoginAndRedirect(String phone) {
        SharedPreferences prefs = getSharedPreferences("SMSINDIA_USER", MODE_PRIVATE);
        prefs.edit().putString("mobile", phone).putString("deviceId", deviceId).apply();

        showLoadingAndProceed("Securing Device...", () -> {
            startActivity(new Intent(LoginActivity.this, MainActivity.class));
            finish();
        });
    }

    private void showLoadingAndProceed(String message, Runnable onComplete) {
        if(isFinishing()) return;
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_loading, null);
        TextView tvMessage = dialogView.findViewById(R.id.tv_loading_message);
        tvMessage.setText(message);
        builder.setView(dialogView);
        builder.setCancelable(false);
        android.app.AlertDialog dialog = builder.create();
        dialog.show();

        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
            if(dialog.isShowing()) dialog.dismiss();
            onComplete.run();
        }, 1500);
    }
}
