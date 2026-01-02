package com.smsindia.app;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.smsindia.app.service.*;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class LoginActivity extends AppCompatActivity {
    
    private EditText phoneInput, passwordInput, referInput;
    private Button loginBtn, signupBtn;
    private TextView forgotPasswordBtn, deviceIdText;
    
    private AuthApi authApi;
    private SupabaseApi supabaseApi;
    private TokenManager tokenManager;
    
    private static final String BASE_URL = "https://appfwrpynfxfpcvpavso.supabase.co";
    private static final String API_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImFwcGZ3cnB5bmZ4ZnBjdnBhdnNvIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NjIwOTQ2MTQsImV4cCI6MjA3NzY3MDYxNH0.Z-BMBjME8MVK5MS2KBgcCDgR7kXvDEjtcHrVfIUvwZY";
    
    // OTP related variables
    private String pendingPhoneForOTP;
    private String pendingPassword;
    private String pendingReferralCode;
    private boolean isSignupFlow = false;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);
        
        // Initialize views
        phoneInput = findViewById(R.id.phoneInput);
        passwordInput = findViewById(R.id.passwordInput);
        referInput = findViewById(R.id.referInput);
        loginBtn = findViewById(R.id.loginBtn);
        signupBtn = findViewById(R.id.signupBtn);
        forgotPasswordBtn = findViewById(R.id.forgotPasswordBtn);
        deviceIdText = findViewById(R.id.deviceIdText);
        
        // Initialize TokenManager
        tokenManager = new TokenManager(this);
        
        // Initialize Retrofit
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build();
        
        authApi = retrofit.create(AuthApi.class);
        supabaseApi = retrofit.create(SupabaseApi.class);
        
        // Check if already logged in
        if (tokenManager.getToken() != null) {
            navigateToMainActivity();
        }
        
        // Generate and display device ID
        String deviceId = getOrCreateDeviceId();
        deviceIdText.setText("Secure Device ID: " + deviceId);
        
        // Set up click listeners
        loginBtn.setOnClickListener(v -> handleLogin());
        signupBtn.setOnClickListener(v -> handleSignup());
        forgotPasswordBtn.setOnClickListener(v -> showForgotPasswordDialog());
    }
    
    // Renamed method to avoid conflict with ContextWrapper.getDeviceId()
    private String getOrCreateDeviceId() {
        SharedPreferences prefs = getSharedPreferences("SMS_APP", MODE_PRIVATE);
        String deviceId = prefs.getString("device_id", null);
        
        if (deviceId == null || deviceId.isEmpty()) {
            deviceId = UUID.randomUUID().toString();
            prefs.edit().putString("device_id", deviceId).apply();
        }
        
        return deviceId;
    }
    
    private void handleLogin() {
        String phone = phoneInput.getText().toString().trim();
        String password = passwordInput.getText().toString().trim();
        
        // Validation
        if (TextUtils.isEmpty(phone)) {
            phoneInput.setError("Phone number is required");
            return;
        }
        
        if (TextUtils.isEmpty(password)) {
            passwordInput.setError("Password is required");
            return;
        }
        
        if (!phone.matches("\\d{10}")) {
            phoneInput.setError("Enter valid 10-digit phone number");
            return;
        }
        
        loginBtn.setEnabled(false);
        loginBtn.setText("LOGGING IN...");
        
        // Try login with email format
        LoginRequest request = new LoginRequest();
        request.email = phone + "@smsindia.app";
        request.password = password;
        
        Call<AuthResponse> call = authApi.login(API_KEY, request);
        call.enqueue(new Callback<AuthResponse>() {
            @Override
            public void onResponse(Call<AuthResponse> call, Response<AuthResponse> response) {
                loginBtn.setEnabled(true);
                loginBtn.setText("LOGIN");
                
                if (response.isSuccessful() && response.body() != null) {
                    AuthResponse authResponse = response.body();
                    
                    // Check if email is verified
                    if (authResponse.user != null && authResponse.user.emailConfirmedAt == null) {
                        // Email not verified, show OTP dialog
                        showEmailNotVerifiedDialog(phone);
                    } else {
                        // Email verified, proceed with login
                        completeLogin(authResponse, phone);
                    }
                } else {
                    // Try alternative login without @smsindia.app suffix
                    tryAlternativeLogin(phone, password);
                }
            }
            
            @Override
            public void onFailure(Call<AuthResponse> call, Throwable t) {
                loginBtn.setEnabled(true);
                loginBtn.setText("LOGIN");
                Toast.makeText(LoginActivity.this, "Network error: " + t.getMessage(), Toast.LENGTH_SHORT).show();
                Log.e("LoginActivity", "Login failed: " + t.getMessage());
            }
        });
    }
    
    private void tryAlternativeLogin(String phone, String password) {
        LoginRequest request = new LoginRequest();
        request.email = phone; // Try without @smsindia.app suffix
        request.password = password;
        
        Call<AuthResponse> call = authApi.login(API_KEY, request);
        call.enqueue(new Callback<AuthResponse>() {
            @Override
            public void onResponse(Call<AuthResponse> call, Response<AuthResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    AuthResponse authResponse = response.body();
                    
                    if (authResponse.user != null && authResponse.user.emailConfirmedAt == null) {
                        showEmailNotVerifiedDialog(phone);
                    } else {
                        completeLogin(authResponse, phone);
                    }
                } else {
                    handleLoginError(response.code());
                }
            }
            
            @Override
            public void onFailure(Call<AuthResponse> call, Throwable t) {
                Toast.makeText(LoginActivity.this, "Login failed. Check credentials.", Toast.LENGTH_SHORT).show();
            }
        });
    }
    
    private void showEmailNotVerifiedDialog(String phone) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Email Not Verified");
        builder.setMessage("Your email is not verified. Do you want to verify now?");
        
        builder.setPositiveButton("Verify", (dialog, which) -> {
            // Store phone for OTP verification
            pendingPhoneForOTP = phone;
            isSignupFlow = false;
            
            // Request OTP for email verification
            sendEmailVerificationOTP(phone);
        });
        
        builder.setNegativeButton("Later", (dialog, which) -> {
            dialog.dismiss();
            // User can still login but with limited features
            Toast.makeText(LoginActivity.this, 
                "Please verify your email soon for full access", 
                Toast.LENGTH_LONG).show();
        });
        
        builder.show();
    }
    
    private void sendEmailVerificationOTP(String phone) {
        // Get temp token
        getTempTokenAndSendOTP(phone, "email_verification");
    }
    
    private void getTempTokenAndSendOTP(String phone, String purpose) {
        // Create a temporary guest login for OTP operations
        LoginRequest tempRequest = new LoginRequest();
        tempRequest.email = "guest@temp.com";
        tempRequest.password = "temp123";
        
        Call<AuthResponse> call = authApi.login(API_KEY, tempRequest);
        call.enqueue(new Callback<AuthResponse>() {
            @Override
            public void onResponse(Call<AuthResponse> call, Response<AuthResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    String tempToken = "Bearer " + response.body().token;
                    createOTP(phone, purpose, tempToken);
                } else {
                    // If guest login fails, use API key as auth
                    createOTP(phone, purpose, "Bearer " + API_KEY);
                }
            }
            
            @Override
            public void onFailure(Call<AuthResponse> call, Throwable t) {
                createOTP(phone, purpose, "Bearer " + API_KEY);
            }
        });
    }
    
    private void createOTP(String phone, String purpose, String token) {
        Map<String, Object> otpData = new HashMap<>();
        otpData.put("phone", phone);
        otpData.put("otp_code", generateOTP());
        otpData.put("purpose", purpose);
        otpData.put("expires_at", System.currentTimeMillis() + 600000); // 10 minutes
        
        Call<Void> call = supabaseApi.createOtp(API_KEY, token, "return=representation", otpData);
        call.enqueue(new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response) {
                if (response.isSuccessful()) {
                    showOTPVerificationDialog(phone, purpose);
                } else {
                    Toast.makeText(LoginActivity.this, "Failed to send OTP", Toast.LENGTH_SHORT).show();
                }
            }
            
            @Override
            public void onFailure(Call<Void> call, Throwable t) {
                Toast.makeText(LoginActivity.this, "Network error: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }
    
    private void showOTPVerificationDialog(String phone, String purpose) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_otp_verification, null);
        builder.setView(view);
        
        EditText otpInput1 = view.findViewById(R.id.otp1);
        EditText otpInput2 = view.findViewById(R.id.otp2);
        EditText otpInput3 = view.findViewById(R.id.otp3);
        EditText otpInput4 = view.findViewById(R.id.otp4);
        EditText otpInput5 = view.findViewById(R.id.otp5);
        EditText otpInput6 = view.findViewById(R.id.otp6);
        
        TextView resendOtpBtn = view.findViewById(R.id.resendOtpBtn);
        TextView timerText = view.findViewById(R.id.timerText);
        
        AlertDialog dialog = builder.create();
        dialog.setCancelable(false);
        
        // Setup OTP input auto-focus
        setupOTPInputs(otpInput1, otpInput2, otpInput3, otpInput4, otpInput5, otpInput6);
        
        // Resend OTP button
        resendOtpBtn.setOnClickListener(v -> {
            resendOTP(phone, purpose);
        });
        
        // Start timer
        startOTPTimer(timerText, resendOtpBtn);
        
        dialog.setButton(DialogInterface.BUTTON_POSITIVE, "Verify", (dialogInterface, which) -> {
            // Get OTP from inputs
            String otp = otpInput1.getText().toString() +
                        otpInput2.getText().toString() +
                        otpInput3.getText().toString() +
                        otpInput4.getText().toString() +
                        otpInput5.getText().toString() +
                        otpInput6.getText().toString();
            
            if (otp.length() != 6) {
                Toast.makeText(LoginActivity.this, "Enter complete OTP", Toast.LENGTH_SHORT).show();
                return;
            }
            
            verifyOTP(phone, otp, purpose, dialog);
        });
        
        dialog.setButton(DialogInterface.BUTTON_NEGATIVE, "Cancel", (dialogInterface, which) -> {
            dialog.dismiss();
        });
        
        dialog.show();
    }
    
    private void setupOTPInputs(EditText... inputs) {
        for (int i = 0; i < inputs.length; i++) {
            final int current = i;
            final int next = i < inputs.length - 1 ? i + 1 : i;
            final int prev = i > 0 ? i - 1 : i;
            
            inputs[i].addTextChangedListener(new android.text.TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                
                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    if (s.length() == 1 && current < inputs.length - 1) {
                        inputs[next].requestFocus();
                    } else if (s.length() == 0 && current > 0) {
                        inputs[prev].requestFocus();
                    }
                }
                
                @Override
                public void afterTextChanged(android.text.Editable s) {}
            });
        }
    }
    
    private void startOTPTimer(TextView timerText, TextView resendBtn) {
        new android.os.CountDownTimer(60000, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                timerText.setText("Resend in " + millisUntilFinished / 1000 + "s");
                resendBtn.setEnabled(false);
                resendBtn.setTextColor(getResources().getColor(android.R.color.darker_gray));
            }
            
            @Override
            public void onFinish() {
                timerText.setText("");
                resendBtn.setEnabled(true);
                resendBtn.setTextColor(getResources().getColor(android.R.color.holo_blue_dark));
            }
        }.start();
    }
    
    private void verifyOTP(String phone, String otp, String purpose, AlertDialog dialog) {
        // Get token for verification
        String token = tokenManager.getToken();
        if (token == null) {
            token = "Bearer " + API_KEY; // Use API key if no token
        }
        
        // Verify OTP from database
        Call<List<Map<String, Object>>> call = supabaseApi.verifyOtp(
            API_KEY, 
            token, 
            "phone,otp_code,expires_at,purpose"
        );
        
        call.enqueue(new Callback<List<Map<String, Object>>>() {
            @Override
            public void onResponse(Call<List<Map<String, Object>>> call, Response<List<Map<String, Object>>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    boolean verified = false;
                    for (Map<String, Object> otpRecord : response.body()) {
                        String recordPhone = (String) otpRecord.get("phone");
                        String recordOtp = (String) otpRecord.get("otp_code");
                        String recordPurpose = (String) otpRecord.get("purpose");
                        long expiresAt = ((Double) otpRecord.get("expires_at")).longValue();
                        
                        if (recordPhone.equals(phone) && 
                            recordOtp.equals(otp) && 
                            recordPurpose.equals(purpose) &&
                            expiresAt > System.currentTimeMillis()) {
                            verified = true;
                            break;
                        }
                    }
                    
                    if (verified) {
                        dialog.dismiss();
                        handleVerifiedOTP(phone, purpose);
                    } else {
                        Toast.makeText(LoginActivity.this, "Invalid or expired OTP", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Toast.makeText(LoginActivity.this, "OTP verification failed", Toast.LENGTH_SHORT).show();
                }
            }
            
            @Override
            public void onFailure(Call<List<Map<String, Object>>> call, Throwable t) {
                Toast.makeText(LoginActivity.this, "Network error", Toast.LENGTH_SHORT).show();
            }
        });
    }
    
    private void handleVerifiedOTP(String phone, String purpose) {
        switch (purpose) {
            case "email_verification":
                markEmailAsVerified(phone);
                break;
            case "signup":
                completeSignupAfterOTP();
                break;
            case "password_reset":
                showNewPasswordDialog(phone);
                break;
        }
    }
    
    private void markEmailAsVerified(String phone) {
        // Update user record to mark email as verified
        String token = tokenManager.getToken();
        if (token == null) {
            Toast.makeText(this, "Please login again", Toast.LENGTH_SHORT).show();
            return;
        }
        
        Map<String, Object> updateData = new HashMap<>();
        updateData.put("email_confirmed", true);
        
        Call<Void> call = supabaseApi.updateUser(API_KEY, token, phone, updateData);
        call.enqueue(new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response) {
                if (response.isSuccessful()) {
                    Toast.makeText(LoginActivity.this, "Email verified successfully!", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(LoginActivity.this, "Failed to update verification status", Toast.LENGTH_SHORT).show();
                }
            }
            
            @Override
            public void onFailure(Call<Void> call, Throwable t) {
                Toast.makeText(LoginActivity.this, "Network error", Toast.LENGTH_SHORT).show();
            }
        });
    }
    
    private void completeLogin(AuthResponse authResponse, String phone) {
        // Save token
        tokenManager.saveToken("Bearer " + authResponse.token);
        
        // Save user info if available
        if (authResponse.user != null) {
            saveUserInfo(authResponse.user);
        }
        
        // Update device ID in database
        updateDeviceId(phone);
        
        Toast.makeText(LoginActivity.this, "Login successful!", Toast.LENGTH_SHORT).show();
        navigateToMainActivity();
    }
    
    private void handleSignup() {
        String phone = phoneInput.getText().toString().trim();
        String password = passwordInput.getText().toString().trim();
        String referralCode = referInput.getText().toString().trim();
        
        // Validation
        if (TextUtils.isEmpty(phone)) {
            phoneInput.setError("Phone number is required");
            return;
        }
        
        if (TextUtils.isEmpty(password)) {
            passwordInput.setError("Password is required");
            return;
        }
        
        if (!phone.matches("\\d{10}")) {
            phoneInput.setError("Enter valid 10-digit phone number");
            return;
        }
        
        if (password.length() < 6) {
            passwordInput.setError("Password must be at least 6 characters");
            return;
        }
        
        // Store for OTP verification
        pendingPhoneForOTP = phone;
        pendingPassword = password;
        pendingReferralCode = referralCode;
        isSignupFlow = true;
        
        // Start OTP verification before signup
        getTempTokenAndSendOTP(phone, "signup");
    }
    
    private void completeSignupAfterOTP() {
        if (pendingPhoneForOTP == null || pendingPassword == null) return;
        
        signupBtn.setEnabled(false);
        signupBtn.setText("CREATING ACCOUNT...");
        
        LoginRequest request = new LoginRequest();
        request.email = pendingPhoneForOTP + "@smsindia.app";
        request.password = pendingPassword;
        
        Call<AuthResponse> call = authApi.signup(API_KEY, request);
        call.enqueue(new Callback<AuthResponse>() {
            @Override
            public void onResponse(Call<AuthResponse> call, Response<AuthResponse> response) {
                signupBtn.setEnabled(true);
                signupBtn.setText("CREATE NEW ACCOUNT");
                
                if (response.isSuccessful() && response.body() != null) {
                    AuthResponse authResponse = response.body();
                    tokenManager.saveToken("Bearer " + authResponse.token);
                    
                    // Create user profile
                    createUserProfile(pendingPhoneForOTP, pendingPassword, pendingReferralCode, 
                                     authResponse.user != null ? authResponse.user.id : null);
                    
                    // Send verification email (optional)
                    sendVerificationEmail(pendingPhoneForOTP);
                    
                } else {
                    String errorMessage = "Signup failed";
                    try {
                        if (response.errorBody() != null) {
                            errorMessage = response.errorBody().string();
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    Toast.makeText(LoginActivity.this, errorMessage, Toast.LENGTH_LONG).show();
                }
                clearPendingCredentials();
            }
            
            @Override
            public void onFailure(Call<AuthResponse> call, Throwable t) {
                signupBtn.setEnabled(true);
                signupBtn.setText("CREATE NEW ACCOUNT");
                Toast.makeText(LoginActivity.this, "Network error: " + t.getMessage(), Toast.LENGTH_SHORT).show();
                clearPendingCredentials();
            }
        });
    }
    
    private void showForgotPasswordDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_forgot_password, null);
        builder.setView(view);
        
        EditText phoneInput = view.findViewById(R.id.forgotPhoneInput);
        Button sendOtpBtn = view.findViewById(R.id.sendOtpBtn);
        
        AlertDialog dialog = builder.create();
        
        sendOtpBtn.setOnClickListener(v -> {
            String phone = phoneInput.getText().toString().trim();
            
            if (TextUtils.isEmpty(phone) || !phone.matches("\\d{10}")) {
                phoneInput.setError("Enter valid 10-digit phone number");
                return;
            }
            
            // Store phone for password reset
            pendingPhoneForOTP = phone;
            isSignupFlow = false;
            
            // Send OTP for password reset
            getTempTokenAndSendOTP(phone, "password_reset");
            dialog.dismiss();
        });
        
        dialog.show();
    }
    
    private void showNewPasswordDialog(String phone) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_new_password, null);
        builder.setView(view);
        
        EditText newPasswordInput = view.findViewById(R.id.newPasswordInput);
        EditText confirmPasswordInput = view.findViewById(R.id.confirmPasswordInput);
        
        builder.setPositiveButton("Reset Password", (dialog, which) -> {
            String newPassword = newPasswordInput.getText().toString().trim();
            String confirmPassword = confirmPasswordInput.getText().toString().trim();
            
            if (!newPassword.equals(confirmPassword)) {
                Toast.makeText(LoginActivity.this, "Passwords don't match", Toast.LENGTH_SHORT).show();
                return;
            }
            
            if (newPassword.length() < 6) {
                Toast.makeText(LoginActivity.this, "Password must be at least 6 characters", Toast.LENGTH_SHORT).show();
                return;
            }
            
            resetPassword(phone, newPassword);
        });
        
        builder.setNegativeButton("Cancel", (dialog, which) -> {
            dialog.dismiss();
        });
        
        builder.show();
    }
    
    private void resetPassword(String phone, String newPassword) {
        // Use Supabase Auth's recover endpoint to send password reset email
        Map<String, String> request = new HashMap<>();
        request.put("email", phone + "@smsindia.app");
        
        Call<Void> call = authApi.resetPassword(API_KEY, request);
        call.enqueue(new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response) {
                if (response.isSuccessful()) {
                    Toast.makeText(LoginActivity.this, 
                        "Password reset email sent. Please check your email.", 
                        Toast.LENGTH_LONG).show();
                    
                    // Also update password in users table if user is logged in
                    updatePasswordInDatabase(phone, newPassword);
                } else {
                    Toast.makeText(LoginActivity.this, 
                        "Failed to send reset email. User may not exist.", 
                        Toast.LENGTH_SHORT).show();
                }
            }
            
            @Override
            public void onFailure(Call<Void> call, Throwable t) {
                Toast.makeText(LoginActivity.this, "Network error", Toast.LENGTH_SHORT).show();
            }
        });
    }
    
    private void updatePasswordInDatabase(String phone, String newPassword) {
        // This requires admin token - only do this if you have proper access
        // For security, it's better to use Supabase Auth's password reset flow
        Toast.makeText(this, "Please use the link sent to your email to reset password", Toast.LENGTH_LONG).show();
    }
    
    // Helper methods
    private String generateOTP() {
        return String.valueOf((int)(Math.random() * 900000) + 100000);
    }
    
    private void resendOTP(String phone, String purpose) {
        getTempTokenAndSendOTP(phone, purpose);
    }
    
    private void sendVerificationEmail(String phone) {
        // Supabase automatically sends verification email on signup
        // This is just for resend functionality if needed
        Map<String, String> request = new HashMap<>();
        request.put("email", phone + "@smsindia.app");
        
        Call<Void> call = authApi.resetPassword(API_KEY, request); // Reusing for email verification
        call.enqueue(new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response) {
                if (response.isSuccessful()) {
                    Toast.makeText(LoginActivity.this, 
                        "Verification email sent. Please check your email.", 
                        Toast.LENGTH_LONG).show();
                }
            }
            
            @Override
            public void onFailure(Call<Void> call, Throwable t) {
                // Silent failure - not critical
            }
        });
    }
    
    private void createUserProfile(String phone, String password, String referralCode, String userId) {
        Map<String, Object> userData = new HashMap<>();
        userData.put("phone", phone);
        userData.put("email", phone + "@smsindia.app");
        userData.put("password", password);
        userData.put("device_id", getOrCreateDeviceId());
        userData.put("balance", 0.0);
        userData.put("today_income", 0.0);
        userData.put("total_income", 0.0);
        userData.put("coins", 100); // Bonus coins for new users
        userData.put("spins", 3);
        userData.put("referral_count", 0);
        userData.put("sms_count", 0);
        userData.put("ad_progress", 0);
        userData.put("streak", 0);
        userData.put("email_confirmed", false); // Will be true after email verification
        
        if (userId != null) userData.put("id", userId);
        if (!TextUtils.isEmpty(referralCode)) userData.put("referred_by", referralCode);
        
        String token = tokenManager.getToken();
        if (token == null) return;
        
        Call<Void> call = supabaseApi.createUser(API_KEY, token, "return=representation", userData);
        call.enqueue(new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response) {
                if (response.isSuccessful()) {
                    Toast.makeText(LoginActivity.this, "Account created successfully!", Toast.LENGTH_SHORT).show();
                    navigateToMainActivity();
                } else {
                    Toast.makeText(LoginActivity.this, "Profile creation failed", Toast.LENGTH_SHORT).show();
                }
            }
            
            @Override
            public void onFailure(Call<Void> call, Throwable t) {
                Toast.makeText(LoginActivity.this, "Network error", Toast.LENGTH_SHORT).show();
            }
        });
    }
    
    private void updateDeviceId(String phone) {
        String token = tokenManager.getToken();
        if (token == null) return;
        
        Map<String, Object> updateData = new HashMap<>();
        updateData.put("device_id", getOrCreateDeviceId());
        
        Call<Void> call = supabaseApi.updateUser(API_KEY, token, phone, updateData);
        call.enqueue(new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response) {
                Log.d("LoginActivity", "Device ID updated");
            }
            
            @Override
            public void onFailure(Call<Void> call, Throwable t) {
                Log.e("LoginActivity", "Device update failed");
            }
        });
    }
    
    private void saveUserInfo(AuthResponse.User user) {
        SharedPreferences prefs = getSharedPreferences("SMS_APP", MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString("user_id", user.id);
        editor.putString("user_email", user.email);
        editor.putString("user_phone", user.phone);
        editor.apply();
    }
    
    private void handleLoginError(int errorCode) {
        switch (errorCode) {
            case 400:
                Toast.makeText(this, "Invalid credentials", Toast.LENGTH_SHORT).show();
                break;
            case 401:
                Toast.makeText(this, "Unauthorized", Toast.LENGTH_SHORT).show();
                break;
            case 404:
                Toast.makeText(this, "Account not found", Toast.LENGTH_SHORT).show();
                break;
            default:
                Toast.makeText(this, "Login failed", Toast.LENGTH_SHORT).show();
        }
    }
    
    private void clearPendingCredentials() {
        pendingPhoneForOTP = null;
        pendingPassword = null;
        pendingReferralCode = null;
        isSignupFlow = false;
    }
    
    private void navigateToMainActivity() {
        Intent intent = new Intent(LoginActivity.this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
    
    @Override
    public void onBackPressed() {
        moveTaskToBack(true);
        android.os.Process.killProcess(android.os.Process.myPid());
        System.exit(1);
    }
}