package com.smsindia.app;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.smsindia.app.config.Constants;
import com.smsindia.app.data.api.AuthApi;
import com.smsindia.app.data.api.SupabaseApi;
import com.smsindia.app.data.model.AuthResponse;
import com.smsindia.app.data.model.LoginRequest;
import com.smsindia.app.service.TokenManager;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class LoginActivity extends AppCompatActivity {
    
    private EditText phoneInput, passwordInput;
    private Button loginBtn, signupBtn;
    private TextView forgotPasswordBtn, deviceIdText;
    
    private AuthApi authApi;
    private SupabaseApi supabaseApi;
    private TokenManager tokenManager;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);
        
        // Initialize views
        phoneInput = findViewById(R.id.phoneInput);
        passwordInput = findViewById(R.id.passwordInput);
        loginBtn = findViewById(R.id.loginBtn);
        signupBtn = findViewById(R.id.signupBtn);
        forgotPasswordBtn = findViewById(R.id.forgotPasswordBtn);
        deviceIdText = findViewById(R.id.deviceIdText);
        
        // Initialize TokenManager
        tokenManager = new TokenManager(this);
        
        // Initialize Retrofit
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(Constants.SUPABASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build();
        
        authApi = retrofit.create(AuthApi.class);
        supabaseApi = retrofit.create(SupabaseApi.class);
        
        // Check if already logged in
        if (tokenManager.getToken() != null && isUserDataSaved()) {
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
    
    private boolean isUserDataSaved() {
        // Check if user data exists in SMSINDIA_USER SharedPreferences
        SharedPreferences prefs = getSharedPreferences(Constants.PREFS_USER, MODE_PRIVATE);
        String userId = prefs.getString(Constants.PREFS_USER_ID, null);
        String mobile = prefs.getString(Constants.PREFS_MOBILE, null);
        return userId != null && !userId.isEmpty() && mobile != null && !mobile.isEmpty();
    }
    
    private String getOrCreateDeviceId() {
        SharedPreferences prefs = getSharedPreferences(Constants.PREFS_USER, MODE_PRIVATE);
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
        
        if (!phone.matches("\\d{10}")) {
            phoneInput.setError("Enter valid 10-digit phone number");
            return;
        }
        
        if (TextUtils.isEmpty(password)) {
            passwordInput.setError("Password is required");
            return;
        }
        
        if (password.length() < 6) {
            passwordInput.setError("Password must be at least 6 characters");
            return;
        }
        
        loginBtn.setEnabled(false);
        loginBtn.setText("LOGGING IN...");
        
        // Generate email from phone (for Supabase auth compatibility)
        String email = phone + "@smsapp.com";
        
        // Login with phone (using email field for auth)
        LoginRequest request = new LoginRequest();
        request.email = email;
        request.password = password;
        
        Call<AuthResponse> call = authApi.login(Constants.SUPABASE_ANON_KEY, request);
        call.enqueue(new Callback<AuthResponse>() {
            @Override
            public void onResponse(Call<AuthResponse> call, Response<AuthResponse> response) {
                loginBtn.setEnabled(true);
                loginBtn.setText("LOGIN");
                
                if (response.isSuccessful() && response.body() != null) {
                    AuthResponse authResponse = response.body();
                    completeLogin(authResponse, phone);
                } else {
                    handleLoginError(response.code());
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
    
    private void completeLogin(AuthResponse authResponse, String phone) {
        // Check if response is valid
        if (authResponse == null || !authResponse.isValid()) {
            Toast.makeText(LoginActivity.this, "Invalid login response", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // Save token
        String token = authResponse.getToken();
        if (token != null) {
            tokenManager.saveToken("Bearer " + token);
        }
        
        // ✅ CRITICAL: Save user info to SMSINDIA_USER SharedPreferences
        saveUserInfo(authResponse.getUser(), phone);
        
        // Update device ID in database
        updateDeviceId(phone);
        
        Toast.makeText(LoginActivity.this, "Login successful!", Toast.LENGTH_SHORT).show();
        navigateToMainActivity();
    }
    
    private void handleSignup() {
        String phone = phoneInput.getText().toString().trim();
        String password = passwordInput.getText().toString().trim();
        
        // Validation
        if (TextUtils.isEmpty(phone)) {
            phoneInput.setError("Phone number is required");
            return;
        }
        
        if (!phone.matches("\\d{10}")) {
            phoneInput.setError("Enter valid 10-digit phone number");
            return;
        }
        
        if (TextUtils.isEmpty(password)) {
            passwordInput.setError("Password is required");
            return;
        }
        
        if (password.length() < 6) {
            passwordInput.setError("Password must be at least 6 characters");
            return;
        }
        
        signupBtn.setEnabled(false);
        signupBtn.setText("CREATING ACCOUNT...");
        
        // Generate email from phone (for Supabase auth compatibility)
        String email = phone + "@smsapp.com";
        
        // Signup with phone (using email field for auth)
        LoginRequest request = new LoginRequest();
        request.email = email;
        request.password = password;
        
        Call<AuthResponse> call = authApi.signup(Constants.SUPABASE_ANON_KEY, request);
        call.enqueue(new Callback<AuthResponse>() {
            @Override
            public void onResponse(Call<AuthResponse> call, Response<AuthResponse> response) {
                signupBtn.setEnabled(true);
                signupBtn.setText("CREATE NEW ACCOUNT");
                
                if (response.isSuccessful() && response.body() != null) {
                    AuthResponse authResponse = response.body();
                    
                    // Validate response
                    if (!authResponse.isValid()) {
                        Toast.makeText(LoginActivity.this, "Invalid signup response", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    
                    String token = authResponse.getToken();
                    if (token != null) {
                        tokenManager.saveToken("Bearer " + token);
                    }
                    
                    // ✅ CRITICAL: Save user info immediately
                    saveUserInfo(authResponse.getUser(), phone);
                    
                    // Create user profile
                    AuthResponse.User user = authResponse.getUser();
                    createUserProfile(phone, password, user != null ? user.getId() : null);
                    
                } else {
                    String errorMessage = "Signup failed";
                    try {
                        if (response.errorBody() != null) {
                            String errorBody = response.errorBody().string();
                            if (errorBody.contains("already registered") || errorBody.contains("already exists")) {
                                errorMessage = "Phone number already registered. Please login.";
                            }
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    Toast.makeText(LoginActivity.this, errorMessage, Toast.LENGTH_LONG).show();
                }
            }
            
            @Override
            public void onFailure(Call<AuthResponse> call, Throwable t) {
                signupBtn.setEnabled(true);
                signupBtn.setText("CREATE NEW ACCOUNT");
                Toast.makeText(LoginActivity.this, "Network error: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }
    
    private void showForgotPasswordDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Forgot Password");
        builder.setMessage("Enter your registered phone number to reset password");
        
        final EditText phoneInput = new EditText(this);
        phoneInput.setHint("10-digit phone number");
        builder.setView(phoneInput);
        
        builder.setPositiveButton("Reset", (dialog, which) -> {
            String phone = phoneInput.getText().toString().trim();
            
            if (TextUtils.isEmpty(phone) || !phone.matches("\\d{10}")) {
                Toast.makeText(LoginActivity.this, "Enter valid 10-digit phone number", Toast.LENGTH_SHORT).show();
                return;
            }
            
            // Generate email from phone for password reset
            String email = phone + "@smsapp.com";
            resetPassword(email);
        });
        
        builder.setNegativeButton("Cancel", (dialog, which) -> {
            dialog.dismiss();
        });
        
        builder.show();
    }
    
    private void resetPassword(String email) {
        // Send password reset email (to the generated email)
        Map<String, String> request = new HashMap<>();
        request.put("email", email);
        
        Call<Void> call = authApi.resetPassword(Constants.SUPABASE_ANON_KEY, request);
        call.enqueue(new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response) {
                if (response.isSuccessful()) {
                    Toast.makeText(LoginActivity.this, 
                        "Password reset instructions sent to your registered phone number.", 
                        Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(LoginActivity.this, 
                        "Failed to send reset instructions. Phone number may not exist.", 
                        Toast.LENGTH_SHORT).show();
                }
            }
            
            @Override
            public void onFailure(Call<Void> call, Throwable t) {
                Toast.makeText(LoginActivity.this, "Network error", Toast.LENGTH_SHORT).show();
            }
        });
    }
    
    private void createUserProfile(String phone, String password, String userId) {
        Map<String, Object> userData = new HashMap<>();
        userData.put("phone", phone);
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
        userData.put("email", phone + "@smsapp.com"); // Store generated email
        
        if (userId != null) userData.put("id", userId);
        
        String token = tokenManager.getToken();
        if (token == null) return;
        
        Call<Void> call = supabaseApi.createUser(Constants.SUPABASE_ANON_KEY, token, Constants.PREFER_RETURN_REPRESENTATION, userData);
        call.enqueue(new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response) {
                if (response.isSuccessful()) {
                    Toast.makeText(LoginActivity.this, "Account created successfully!", Toast.LENGTH_SHORT).show();
                    // User info already saved, just navigate
                    navigateToMainActivity();
                } else {
                    // Profile creation might fail, but user is already authenticated
                    Log.e("LoginActivity", "Profile creation failed but auth succeeded: " + response.code());
                    Toast.makeText(LoginActivity.this, "Profile creation failed, but you're logged in", Toast.LENGTH_SHORT).show();
                    navigateToMainActivity();
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
        
        // Update by phone
        Call<Void> call = supabaseApi.updateUser(Constants.SUPABASE_ANON_KEY, token, "phone=" + phone, updateData);
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
    
    private void saveUserInfo(AuthResponse.User user, String phone) {
        // ✅ ONLY save to SMSINDIA_USER (MainActivity checks this)
        SharedPreferences prefs = getSharedPreferences(Constants.PREFS_USER, MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        
        // Save userId (from auth response)
        if (user != null && user.getId() != null) {
            String userId = user.getId();
            editor.putString(Constants.PREFS_USER_ID, userId);
            editor.putString("user_id", userId); // Keep both for compatibility
        } else {
            // Generate temp ID if auth didn't return one
            String tempId = "user_" + System.currentTimeMillis();
            editor.putString(Constants.PREFS_USER_ID, tempId);
            editor.putString("user_id", tempId);
        }
        
        // Save phone
        editor.putString(Constants.PREFS_MOBILE, phone);
        editor.putString("user_phone", phone); // Keep both for compatibility
        
        // Save email
        editor.putString("email", phone + "@smsapp.com");
        
        // Save token
        String token = tokenManager.getToken();
        if (token != null) {
            editor.putString("token", token);
        }
        
        // Save login timestamp
        editor.putLong("loginTime", System.currentTimeMillis());
        
        // Save device ID
        editor.putString("device_id", getOrCreateDeviceId());
        
        // Commit changes
        boolean saved = editor.commit();
        
        // Debug log
        Log.d("LoginActivity", "User saved to SMSINDIA_USER: saved=" + saved + 
              ", userId=" + prefs.getString(Constants.PREFS_USER_ID, "null") + 
              ", mobile=" + prefs.getString(Constants.PREFS_MOBILE, "null") +
              ", token=" + (token != null ? "exists" : "null"));
    }
    
    private void handleLoginError(int errorCode) {
        switch (errorCode) {
            case 400:
                Toast.makeText(this, "Invalid phone or password", Toast.LENGTH_SHORT).show();
                break;
            case 401:
                Toast.makeText(this, "Unauthorized", Toast.LENGTH_SHORT).show();
                break;
            case 404:
                Toast.makeText(this, "Account not found. Please sign up first.", Toast.LENGTH_SHORT).show();
                break;
            default:
                Toast.makeText(this, "Login failed. Please try again.", Toast.LENGTH_SHORT).show();
        }
    }
    
    private void navigateToMainActivity() {
        // Double-check user data is saved before navigating
        if (isUserDataSaved()) {
            Intent intent = new Intent(LoginActivity.this, MainActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        } else {
            Toast.makeText(this, "Login failed - user data not saved", Toast.LENGTH_SHORT).show();
        }
    }
    
    @Override
    public void onBackPressed() {
        moveTaskToBack(true);
        android.os.Process.killProcess(android.os.Process.myPid());
        System.exit(1);
    }
}