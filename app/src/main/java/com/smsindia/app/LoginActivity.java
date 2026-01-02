package com.smsindia.app;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.MediaDrm;
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

import com.smsindia.app.service.AuthApi;
import com.smsindia.app.service.AuthResponse;
import com.smsindia.app.service.LoginRequest;
import com.smsindia.app.service.TokenManager;
import com.smsindia.app.service.SupabaseApi;
import com.smsindia.app.service.UserModel;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class LoginActivity extends AppCompatActivity {

    private static final String SUPABASE_URL = "https://appfwrpynfxfpcvpavso.supabase.co";
    private static final String SUPABASE_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImFwcGZ3cnB5bmZ4ZnBjdnBhdnNvIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NjIwOTQ2MTQsImV4cCI6MjA3NzY3MDYxNH0.Z-BMBjME8MVK5MS2KBgcCDgR7kXvDEjtcHrVfIUvwZY";

    private EditText phoneInput, passwordInput, referInput;
    private Button loginBtn, signupBtn, forgotPasswordBtn;
    private TextView deviceIdText;

    private SupabaseApi supabaseApi;
    private AuthApi authApi;
    private TokenManager tokenManager;
    private String deviceId;
    
    // For OTP handling
    private String generatedOtp = "";
    private String resetPhone = "";
    private String resetEmail = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        // Init Retrofit for main API
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(SUPABASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build();
        supabaseApi = retrofit.create(SupabaseApi.class);

        // Init Retrofit for Auth API
        authApi = retrofit.create(AuthApi.class);

        // Init Token Manager
        tokenManager = new TokenManager(this);

        phoneInput = findViewById(R.id.phoneInput);
        passwordInput = findViewById(R.id.passwordInput);
        referInput = findViewById(R.id.referInput);
        loginBtn = findViewById(R.id.loginBtn);
        signupBtn = findViewById(R.id.signupBtn);
        deviceIdText = findViewById(R.id.deviceIdText);
        forgotPasswordBtn = findViewById(R.id.forgotPasswordBtn);

        // 🔒 GENERATE PERMANENT HARDWARE ID
        deviceId = getHardwareDeviceId(this);
        
        String displayId = (deviceId.length() > 6) ? deviceId.substring(0, 6) : deviceId;
        deviceIdText.setText("HwID: " + displayId);

        checkClipboardForReferral();

        loginBtn.setOnClickListener(v -> loginUser());
        signupBtn.setOnClickListener(v -> showEmailVerificationDialog());
        forgotPasswordBtn.setOnClickListener(v -> showForgotPasswordDialog());
    }

    // ===================== OTP-BASED FORGOT PASSWORD =====================
    private void showForgotPasswordDialog() {
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        builder.setTitle("Forgot Password");
        builder.setMessage("Enter your phone number to reset password");

        final EditText input = new EditText(this);
        input.setHint("Enter phone number");
        input.setInputType(android.text.InputType.TYPE_CLASS_PHONE);
        builder.setView(input);

        builder.setPositiveButton("Send OTP", (dialog, which) -> {
            String phone = input.getText().toString().trim().replace("+91", "").replace(" ", "");
            if (TextUtils.isEmpty(phone)) {
                Toast.makeText(this, "Enter phone number", Toast.LENGTH_SHORT).show();
                return;
            }
            
            resetPhone = phone;
            // First get user's email from database
            getEmailForPasswordReset(phone);
        });

        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void getEmailForPasswordReset(String phone) {
        supabaseApi.getUser(SUPABASE_KEY, "Bearer " + SUPABASE_KEY, "eq." + phone)
            .enqueue(new Callback<List<UserModel>>() {
                @Override
                public void onResponse(Call<List<UserModel>> call, Response<List<UserModel>> response) {
                    if (response.isSuccessful() && response.body() != null && !response.body().isEmpty()) {
                        UserModel user = response.body().get(0);
                        if (user.email != null && !user.email.isEmpty()) {
                            resetEmail = user.email;
                            sendOtpForPasswordReset(user.email, phone);
                        } else {
                            resetEmail = phone + "@smsindia.com";
                            sendOtpForPasswordReset(resetEmail, phone);
                        }
                    } else {
                        Toast.makeText(LoginActivity.this, "User not found", Toast.LENGTH_SHORT).show();
                    }
                }

                @Override
                public void onFailure(Call<List<UserModel>> call, Throwable t) {
                    Toast.makeText(LoginActivity.this, "Network error", Toast.LENGTH_SHORT).show();
                }
            });
    }

    private void sendOtpForPasswordReset(String email, String phone) {
        // Generate 6-digit OTP
        Random random = new Random();
        generatedOtp = String.format("%06d", random.nextInt(999999));
        
        // Save OTP in database for verification
        saveOtpToDatabase(email, phone, generatedOtp);
        
        // Send OTP via email using Supabase SMTP
        sendOtpEmail(email, generatedOtp, "Password Reset");
        
        // Show OTP input dialog
        showOtpVerificationDialog();
    }

    private void saveOtpToDatabase(String email, String phone, String otp) {
        // Create OTP record in your database
        Map<String, Object> otpMap = new HashMap<>();
        otpMap.put("email", email);
        otpMap.put("phone", phone);
        otpMap.put("otp", otp);
        otpMap.put("purpose", "password_reset");
        otpMap.put("expires_at", new java.sql.Timestamp(System.currentTimeMillis() + 10 * 60 * 1000)); // 10 minutes
        
        supabaseApi.createOtp(SUPABASE_KEY, "Bearer " + SUPABASE_KEY, "return=minimal", otpMap)
            .enqueue(new Callback<Void>() {
                @Override
                public void onResponse(Call<Void> call, Response<Void> response) {
                    if (!response.isSuccessful()) {
                        Toast.makeText(LoginActivity.this, "Failed to save OTP", Toast.LENGTH_SHORT).show();
                    }
                }

                @Override
                public void onFailure(Call<Void> call, Throwable t) {
                    // Log error
                }
            });
    }

    private void showOtpVerificationDialog() {
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        builder.setTitle("Verify OTP");
        builder.setMessage("Enter 6-digit OTP sent to your email");

        final EditText otpInput = new EditText(this);
        otpInput.setHint("000000");
        otpInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        otpInput.setMaxLines(1);
        builder.setView(otpInput);

        builder.setPositiveButton("Verify OTP", (dialog, which) -> {
            String enteredOtp = otpInput.getText().toString().trim();
            verifyOtpForPasswordReset(enteredOtp);
        });

        builder.setNegativeButton("Resend OTP", (dialog, which) -> {
            resendOtp();
        });

        builder.setNeutralButton("Cancel", null);
        builder.show();
    }

    private void verifyOtpForPasswordReset(String enteredOtp) {
        // Verify OTP from database
        supabaseApi.verifyOtp(SUPABASE_KEY, "Bearer " + SUPABASE_KEY, 
                "and(eq.phone," + resetPhone + ",eq.otp," + enteredOtp + ",eq.purpose,password_reset)")
            .enqueue(new Callback<List<Map<String, Object>>>() {
                @Override
                public void onResponse(Call<List<Map<String, Object>>> call, Response<List<Map<String, Object>>> response) {
                    if (response.isSuccessful() && response.body() != null && !response.body().isEmpty()) {
                        // OTP verified, show password change dialog
                        showNewPasswordDialog();
                    } else {
                        Toast.makeText(LoginActivity.this, "Invalid OTP", Toast.LENGTH_SHORT).show();
                    }
                }

                @Override
                public void onFailure(Call<List<Map<String, Object>>> call, Throwable t) {
                    Toast.makeText(LoginActivity.this, "Verification failed", Toast.LENGTH_SHORT).show();
                }
            });
    }

    private void showNewPasswordDialog() {
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        builder.setTitle("Set New Password");

        final EditText newPasswordInput = new EditText(this);
        newPasswordInput.setHint("Enter new password");
        newPasswordInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT | 
                                     android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        
        final EditText confirmPasswordInput = new EditText(this);
        confirmPasswordInput.setHint("Confirm new password");
        confirmPasswordInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT | 
                                         android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);

        android.widget.LinearLayout layout = new android.widget.LinearLayout(this);
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        layout.addView(newPasswordInput);
        layout.addView(confirmPasswordInput);
        
        int padding = (int) (16 * getResources().getDisplayMetrics().density);
        layout.setPadding(padding, padding, padding, padding);
        
        builder.setView(layout);

        builder.setPositiveButton("Change Password", (dialog, which) -> {
            String newPassword = newPasswordInput.getText().toString().trim();
            String confirmPassword = confirmPasswordInput.getText().toString().trim();
            
            if (TextUtils.isEmpty(newPassword) || newPassword.length() < 6) {
                Toast.makeText(this, "Password must be at least 6 characters", Toast.LENGTH_SHORT).show();
                return;
            }
            
            if (!newPassword.equals(confirmPassword)) {
                Toast.makeText(this, "Passwords do not match", Toast.LENGTH_SHORT).show();
                return;
            }
            
            updatePasswordInDatabase(newPassword);
        });

        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void updatePasswordInDatabase(String newPassword) {
        // Update password in users table
        Map<String, Object> updateMap = new HashMap<>();
        updateMap.put("password", newPassword);
        
        // FIXED: Changed updateUserPassword to updateUser
        supabaseApi.updateUser(SUPABASE_KEY, "Bearer " + SUPABASE_KEY, 
                resetPhone, updateMap)
            .enqueue(new Callback<Void>() {
                @Override
                public void onResponse(Call<Void> call, Response<Void> response) {
                    if (response.isSuccessful()) {
                        // Also update in Supabase Auth
                        updateAuthPassword(newPassword);
                    } else {
                        Toast.makeText(LoginActivity.this, "Failed to update password", Toast.LENGTH_SHORT).show();
                    }
                }

                @Override
                public void onFailure(Call<Void> call, Throwable t) {
                    Toast.makeText(LoginActivity.this, "Network error", Toast.LENGTH_SHORT).show();
                }
            });
    }

    private void updateAuthPassword(String newPassword) {
        Toast.makeText(this, "Password updated successfully!", Toast.LENGTH_LONG).show();
        Toast.makeText(this, "Please login with new password", Toast.LENGTH_SHORT).show();
        
        // Clear OTP from database
        clearUsedOtp();
    }

    private void clearUsedOtp() {
        // Delete used OTP
        Map<String, Object> updateMap = new HashMap<>();
        updateMap.put("verified", true);
        
        // FIXED: Changed parameters
        supabaseApi.updateOtp(SUPABASE_KEY, "Bearer " + SUPABASE_KEY, 
                resetPhone, updateMap)
            .enqueue(new Callback<Void>() {
                @Override
                public void onResponse(Call<Void> call, Response<Void> response) {
                    // OTP cleared
                }

                @Override
                public void onFailure(Call<Void> call, Throwable t) {
                    // Log error
                }
            });
    }

    private void resendOtp() {
        sendOtpForPasswordReset(resetEmail, resetPhone);
        Toast.makeText(this, "OTP resent to your email", Toast.LENGTH_SHORT).show();
    }

    private void sendPasswordResetViaSupabase(String email) {
        Map<String, String> resetRequest = new HashMap<>();
        resetRequest.put("email", email);
        
        authApi.resetPassword(SUPABASE_KEY, resetRequest).enqueue(new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response) {
                if (response.isSuccessful()) {
                    Toast.makeText(LoginActivity.this, 
                        "Reset instructions sent to your email", 
                        Toast.LENGTH_LONG).show();
                }
            }
            
            @Override
            public void onFailure(Call<Void> call, Throwable t) {
                Toast.makeText(LoginActivity.this, "Failed to send email", Toast.LENGTH_SHORT).show();
            }
        });
    }

    // ===================== REGISTRATION WITH OTP =====================
    private void showEmailVerificationDialog() {
        String phoneRaw = phoneInput.getText().toString().trim();
        final String phone = phoneRaw.replace("+91", "").replace(" ", "");
        
        if (TextUtils.isEmpty(phone)) {
            Toast.makeText(this, "Enter phone number first", Toast.LENGTH_SHORT).show();
            return;
        }

        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        builder.setTitle("Email Verification Required");
        builder.setMessage("Please enter your email for verification");

        final EditText emailInput = new EditText(this);
        emailInput.setHint("your@email.com");
        emailInput.setInputType(android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        builder.setView(emailInput);

        builder.setPositiveButton("Send OTP", (dialog, which) -> {
            String email = emailInput.getText().toString().trim();
            if (TextUtils.isEmpty(email) || !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                Toast.makeText(this, "Enter valid email", Toast.LENGTH_SHORT).show();
                return;
            }
            
            // Send OTP for registration
            sendRegistrationOtp(phone, email);
        });

        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void sendRegistrationOtp(String phone, String email) {
        // Generate OTP
        Random random = new Random();
        generatedOtp = String.format("%06d", random.nextInt(999999));
        
        // Save OTP for registration
        Map<String, Object> otpMap = new HashMap<>();
        otpMap.put("email", email);
        otpMap.put("phone", phone);
        otpMap.put("otp", generatedOtp);
        otpMap.put("purpose", "registration");
        otpMap.put("expires_at", new java.sql.Timestamp(System.currentTimeMillis() + 10 * 60 * 1000));
        
        supabaseApi.createOtp(SUPABASE_KEY, "Bearer " + SUPABASE_KEY, "return=minimal", otpMap)
            .enqueue(new Callback<Void>() {
                @Override
                public void onResponse(Call<Void> call, Response<Void> response) {
                    if (response.isSuccessful()) {
                        sendOtpEmail(email, generatedOtp, "Registration");
                        showRegistrationOtpDialog(phone, email);
                    }
                }

                @Override
                public void onFailure(Call<Void> call, Throwable t) {
                    Toast.makeText(LoginActivity.this, "Failed to send OTP", Toast.LENGTH_SHORT).show();
                }
            });
    }

    private void showRegistrationOtpDialog(String phone, String email) {
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        builder.setTitle("Verify Email");
        builder.setMessage("Enter OTP sent to " + email);

        final EditText otpInput = new EditText(this);
        otpInput.setHint("6-digit OTP");
        otpInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        builder.setView(otpInput);

        builder.setPositiveButton("Verify & Register", (dialog, which) -> {
            String enteredOtp = otpInput.getText().toString().trim();
            verifyRegistrationOtp(phone, email, enteredOtp);
        });

        builder.setNegativeButton("Resend OTP", (dialog, which) -> {
            sendRegistrationOtp(phone, email);
        });

        builder.show();
    }

    private void verifyRegistrationOtp(String phone, String email, String enteredOtp) {
        supabaseApi.verifyOtp(SUPABASE_KEY, "Bearer " + SUPABASE_KEY, 
                "and(eq.email," + email + ",eq.otp," + enteredOtp + ",eq.purpose,registration)")
            .enqueue(new Callback<List<Map<String, Object>>>() {
                @Override
                public void onResponse(Call<List<Map<String, Object>>> call, Response<List<Map<String, Object>>> response) {
                    if (response.isSuccessful() && response.body() != null && !response.body().isEmpty()) {
                        // OTP verified, proceed with registration
                        String password = passwordInput.getText().toString().trim();
                        String referCode = referInput.getText().toString().trim();
                        completeRegistration(phone, email, password, referCode);
                    } else {
                        Toast.makeText(LoginActivity.this, "Invalid OTP", Toast.LENGTH_SHORT).show();
                    }
                }

                @Override
                public void onFailure(Call<List<Map<String, Object>>> call, Throwable t) {
                    Toast.makeText(LoginActivity.this, "Verification failed", Toast.LENGTH_SHORT).show();
                }
            });
    }

    private void completeRegistration(String phone, String email, String password, String referCode) {
        signupBtn.setEnabled(false);
        signupBtn.setText("Creating Account...");

        String newUserId = UUID.randomUUID().toString();

        // Create user in Supabase Auth
        LoginRequest signupRequest = new LoginRequest();
        signupRequest.email = email;
        signupRequest.password = password;

        authApi.signup(SUPABASE_KEY, signupRequest).enqueue(new Callback<AuthResponse>() {
            @Override
            public void onResponse(Call<AuthResponse> call, Response<AuthResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    tokenManager.saveToken(response.body().token);
                    // FIXED: Get user ID properly
                    String userId = response.body().user != null ? response.body().user.id : newUserId;
                    createUserInDatabase(phone, email, password, referCode, userId);
                } else {
                    signupBtn.setEnabled(true);
                    signupBtn.setText("REGISTER");
                    Toast.makeText(LoginActivity.this, "Registration failed", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<AuthResponse> call, Throwable t) {
                signupBtn.setEnabled(true);
                signupBtn.setText("REGISTER");
                Toast.makeText(LoginActivity.this, "Network error", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void createUserInDatabase(String phone, String email, String password, String referCode, String userId) {
        Map<String, Object> userMap = new HashMap<>();
        userMap.put("id", userId);
        userMap.put("phone", phone);
        userMap.put("email", email);
        userMap.put("device_id", deviceId);
        userMap.put("password", password);
        userMap.put("balance", 0.00);
        userMap.put("coins", 0);
        userMap.put("sms_count", 0);
        
        if (!TextUtils.isEmpty(referCode) && !referCode.equals(phone)) {
            userMap.put("referred_by", referCode);
        }

        String token = tokenManager.getToken();
        String authHeader = token != null ? "Bearer " + token : "Bearer " + SUPABASE_KEY;

        supabaseApi.createUser(SUPABASE_KEY, authHeader, "return=minimal", userMap)
            .enqueue(new Callback<Void>() {
                @Override
                public void onResponse(Call<Void> call, Response<Void> response) {
                    signupBtn.setEnabled(true);
                    signupBtn.setText("REGISTER");
                    
                    if (response.isSuccessful()) {
                        UserModel newUser = new UserModel();
                        newUser.id = userId;
                        newUser.phone = phone;
                        newUser.deviceId = deviceId;
                        saveLoginAndRedirect(newUser);
                    } else {
                        Toast.makeText(LoginActivity.this, "Registration failed", Toast.LENGTH_SHORT).show();
                    }
                }

                @Override
                public void onFailure(Call<Void> call, Throwable t) {
                    signupBtn.setEnabled(true);
                    signupBtn.setText("REGISTER");
                    Toast.makeText(LoginActivity.this, "Error: " + t.getMessage(), Toast.LENGTH_SHORT).show();
                }
            });
    }

    // ===================== EMAIL SENDING =====================
    private void sendOtpEmail(String email, String otp, String purpose) {
        // Since Supabase SMTP is configured, show OTP in toast for testing
        Toast.makeText(this, purpose + " OTP for " + email + ": " + otp, Toast.LENGTH_LONG).show();
    }

    // ===================== REST OF YOUR EXISTING CODE =====================
    // 🔒 CORE SECURITY
    private String getHardwareDeviceId(Context context) {
    try {
        UUID uuid = new UUID(-0x121074568629b532L, -0x5c37d8232ae2de13L);
        MediaDrm drm = new MediaDrm(uuid);
        byte[] id = drm.getPropertyByteArray(MediaDrm.PROPERTY_DEVICE_UNIQUE_ID);
        drm.close();
        return Base64.encodeToString(id, Base64.NO_WRAP);
    } catch (Throwable t) {   // IMPORTANT
        return Settings.Secure.getString(
            context.getContentResolver(),
            Settings.Secure.ANDROID_ID
        );
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

    // --- LOGIN LOGIC WITH JWT ---
    private void loginUser() {
        String phoneRaw = phoneInput.getText().toString().trim();
        String password = passwordInput.getText().toString().trim();
        final String phone = phoneRaw.replace("+91", "").replace(" ", "");

        if (TextUtils.isEmpty(phone) || TextUtils.isEmpty(password)) {
            Toast.makeText(this, "Enter phone and password", Toast.LENGTH_SHORT).show();
            return;
        }

        loginBtn.setEnabled(false);
        loginBtn.setText("Signing in...");

        // Convert phone to email for Supabase Auth
        String email = phone + "@smsindia.com";

        // Create login request
        LoginRequest loginRequest = new LoginRequest();
        loginRequest.email = email;
        loginRequest.password = password;

        // Step 1: Get JWT token from Supabase Auth
        authApi.login(SUPABASE_KEY, loginRequest).enqueue(new Callback<AuthResponse>() {
            @Override
            public void onResponse(Call<AuthResponse> call, Response<AuthResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    // Save JWT token
                    tokenManager.saveToken(response.body().token);
                    
                    // Step 2: Fetch user data with JWT token
                    fetchUserWithJWT(phone);
                } else {
                    loginBtn.setEnabled(true);
                    loginBtn.setText("LOGIN");
                    
                    // Try fallback to old method (for existing users without auth)
                    if (response.code() == 400) {
                        fallbackOldLogin(phone, password);
                    } else {
                        Toast.makeText(LoginActivity.this, "Login failed", Toast.LENGTH_SHORT).show();
                    }
                }
            }

            @Override
            public void onFailure(Call<AuthResponse> call, Throwable t) {
                loginBtn.setEnabled(true);
                loginBtn.setText("LOGIN");
                Toast.makeText(LoginActivity.this, "Network error", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void fetchUserWithJWT(String phone) {
        // Get JWT token
        String token = tokenManager.getToken();
        String authHeader = token != null ? "Bearer " + token : "Bearer " + SUPABASE_KEY;

        // Fetch user with JWT
        supabaseApi.getUser(SUPABASE_KEY, authHeader, "eq." + phone)
            .enqueue(new Callback<List<UserModel>>() {
                @Override
                public void onResponse(Call<List<UserModel>> call, Response<List<UserModel>> response) {
                    loginBtn.setEnabled(true);
                    loginBtn.setText("LOGIN");
                    
                    if (response.isSuccessful() && response.body() != null && !response.body().isEmpty()) {
                        UserModel user = response.body().get(0);
                        
                        // Check Device Lock
                        if (user.deviceId != null && !user.deviceId.equals(deviceId)) {
                            Toast.makeText(LoginActivity.this, "This account is locked to another device!", Toast.LENGTH_LONG).show();
                            return; 
                        }
                        
                        // Login Success
                        saveLoginAndRedirect(user);
                    } else {
                        Toast.makeText(LoginActivity.this, "User not found", Toast.LENGTH_SHORT).show();
                    }
                }

                @Override
                public void onFailure(Call<List<UserModel>> call, Throwable t) {
                    loginBtn.setEnabled(true);
                    loginBtn.setText("LOGIN");
                    Toast.makeText(LoginActivity.this, "Connection Error", Toast.LENGTH_SHORT).show();
                }
            });
    }

    // Fallback for existing users without auth record
    private void fallbackOldLogin(String phone, String password) {
        supabaseApi.getUser(SUPABASE_KEY, "Bearer " + SUPABASE_KEY, "eq." + phone)
            .enqueue(new Callback<List<UserModel>>() {
                @Override
                public void onResponse(Call<List<UserModel>> call, Response<List<UserModel>> response) {
                    if (response.isSuccessful() && response.body() != null && !response.body().isEmpty()) {
                        UserModel user = response.body().get(0);
                        
                        if (user.password != null && user.password.equals(password)) {
                            // Create auth record for this user
                            createAuthRecordForExistingUser(phone, password, user);
                        } else {
                            loginBtn.setEnabled(true);
                            loginBtn.setText("LOGIN");
                            Toast.makeText(LoginActivity.this, "Incorrect Password", Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        loginBtn.setEnabled(true);
                        loginBtn.setText("LOGIN");
                        Toast.makeText(LoginActivity.this, "User not found", Toast.LENGTH_SHORT).show();
                    }
                }

                @Override
                public void onFailure(Call<List<UserModel>> call, Throwable t) {
                    loginBtn.setEnabled(true);
                    loginBtn.setText("LOGIN");
                    Toast.makeText(LoginActivity.this, "Connection Error", Toast.LENGTH_SHORT).show();
                }
            });
    }

    private void createAuthRecordForExistingUser(String phone, String password, UserModel user) {
        String email = phone + "@smsindia.com";
        
        LoginRequest signupRequest = new LoginRequest();
        signupRequest.email = email;
        signupRequest.password = password;

        authApi.signup(SUPABASE_KEY, signupRequest).enqueue(new Callback<AuthResponse>() {
            @Override
            public void onResponse(Call<AuthResponse> call, Response<AuthResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    tokenManager.saveToken(response.body().token);
                    saveLoginAndRedirect(user);
                } else {
                    loginBtn.setEnabled(true);
                    loginBtn.setText("LOGIN");
                    Toast.makeText(LoginActivity.this, "Failed to create auth record", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<AuthResponse> call, Throwable t) {
                loginBtn.setEnabled(true);
                loginBtn.setText("LOGIN");
                Toast.makeText(LoginActivity.this, "Network error", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void saveLoginAndRedirect(UserModel user) {
        SharedPreferences prefs = getSharedPreferences("SMSINDIA_USER", MODE_PRIVATE);
        prefs.edit()
             .putString("userId", user.id)    
             .putString("mobile", user.phone) 
             .putString("deviceId", user.deviceId)
             .apply();

        // Also save JWT token separately
        SharedPreferences authPrefs = getSharedPreferences("SMS_AUTH", MODE_PRIVATE);
        authPrefs.edit().putString("jwt", tokenManager.getToken()).apply();

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