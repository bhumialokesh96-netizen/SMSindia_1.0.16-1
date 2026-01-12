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
import com.smsindia.app.utils.ErrorHandler;
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
        signupBtn.setOnClickListener(v -> navigateToRegister());
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
                    handleLoginError(response);
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
        // Redirect to new RegisterActivity with better UX
        navigateToRegister();
    }
    
    private void navigateToRegister() {
        Intent intent = new Intent(LoginActivity.this, RegisterActivity.class);
        startActivity(intent);
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
        createUserProfileWithRetry(phone, password, userId, 0);
    }
    
    private void createUserProfileWithRetry(String phone, String password, String userId, int attemptCount) {
        // Validate inputs before making API call
        if (phone == null || phone.isEmpty() || !phone.matches("\\d{10}")) {
            Log.e("LoginActivity", "Profile creation failed: invalid phone number");
            Toast.makeText(LoginActivity.this, "Invalid phone number data", Toast.LENGTH_SHORT).show();
            navigateToMainActivity();
            return;
        }
        
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
        
        // Use anon key for authorization instead of JWT token
        String authHeader = "Bearer " + Constants.SUPABASE_ANON_KEY;
        
        Log.d("LoginActivity", "Creating user profile (attempt " + (attemptCount + 1) + "): phone=" + phone + ", userId=" + userId);
        
        Call<Void> call = supabaseApi.createUser(Constants.SUPABASE_ANON_KEY, authHeader, Constants.PREFER_RETURN_REPRESENTATION, userData);
        call.enqueue(new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response) {
                if (response.isSuccessful()) {
                    Log.d("LoginActivity", "Profile created successfully on attempt " + (attemptCount + 1));
                    Toast.makeText(LoginActivity.this, "Account created successfully!", Toast.LENGTH_SHORT).show();
                    // User info already saved, just navigate
                    navigateToMainActivity();
                } else {
                    String errorMsg = "Profile creation failed with code: " + response.code();
                    try {
                        if (response.errorBody() != null) {
                            errorMsg += ", error: " + response.errorBody().string();
                        }
                    } catch (Exception e) {
                        Log.e("LoginActivity", "Error reading error body", e);
                    }
                    Log.e("LoginActivity", errorMsg);
                    
                    // Retry logic: retry up to 3 times for server errors (5xx) or specific client errors
                    if (attemptCount < Constants.NETWORK_RETRY_ATTEMPTS - 1 && 
                        (response.code() >= 500 || response.code() == 408 || response.code() == 429)) {
                        Log.d("LoginActivity", "Retrying profile creation, attempt " + (attemptCount + 2));
                        // Retry after a delay
                        new android.os.Handler().postDelayed(() -> 
                            createUserProfileWithRetry(phone, password, userId, attemptCount + 1),
                            1000 * (attemptCount + 1) // Exponential backoff: 1s, 2s, 3s
                        );
                    } else {
                        // Failed after retries or non-retryable error
                        String userMessage = "Profile setup incomplete (error " + response.code() + "), but you're logged in";
                        Toast.makeText(LoginActivity.this, userMessage, Toast.LENGTH_LONG).show();
                        navigateToMainActivity();
                    }
                }
            }
            
            @Override
            public void onFailure(Call<Void> call, Throwable t) {
                Log.e("LoginActivity", "Network error on profile creation attempt " + (attemptCount + 1) + ": " + t.getMessage(), t);
                
                // Retry logic for network failures
                if (attemptCount < Constants.NETWORK_RETRY_ATTEMPTS - 1) {
                    Log.d("LoginActivity", "Retrying profile creation due to network error, attempt " + (attemptCount + 2));
                    new android.os.Handler().postDelayed(() -> 
                        createUserProfileWithRetry(phone, password, userId, attemptCount + 1),
                        1000 * (attemptCount + 1) // Exponential backoff
                    );
                } else {
                    // Failed after retries
                    Toast.makeText(LoginActivity.this, 
                        "Network error: " + t.getMessage() + ". Profile setup incomplete, but you're logged in.", 
                        Toast.LENGTH_LONG).show();
                    navigateToMainActivity();
                }
            }
        });
    }
    
    private void updateDeviceId(String phone) {
        // Use anon key for authorization instead of JWT token
        String authHeader = "Bearer " + Constants.SUPABASE_ANON_KEY;
        
        Map<String, Object> updateData = new HashMap<>();
        updateData.put("device_id", getOrCreateDeviceId());
        
        // Update by phone
        Call<Void> call = supabaseApi.updateUser(Constants.SUPABASE_ANON_KEY, authHeader, "phone=" + phone, updateData);
        call.enqueue(new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response) {
                if (response.isSuccessful()) {
                    Log.d("LoginActivity", "Device ID updated successfully");
                } else {
                    Log.w("LoginActivity", "Device ID update failed with code: " + response.code());
                }
            }
            
            @Override
            public void onFailure(Call<Void> call, Throwable t) {
                Log.e("LoginActivity", "Device update failed: " + t.getMessage());
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
    
    private void handleLoginError(Response<AuthResponse> response) {
        String errorMessage = ErrorHandler.getErrorMessage(response);
        Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show();
        Log.e("LoginActivity", "Login failed with code " + response.code() + ": " + errorMessage);
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