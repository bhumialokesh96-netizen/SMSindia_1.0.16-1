package com.smsindia.app;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.util.Patterns;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.smsindia.app.config.Constants;
import com.smsindia.app.data.api.AuthApi;
import com.smsindia.app.data.api.SupabaseApi;
import com.smsindia.app.data.model.AuthResponse;
import com.smsindia.app.data.model.RegisterRequest;
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

public class RegisterActivity extends AppCompatActivity {
    
    private static final String TAG = "RegisterActivity";
    private static final String COMPANY_REFERRAL_CODE = "666666";
    
    private EditText emailInput, phoneInput, passwordInput, referralInput;
    private Button registerBtn;
    private TextView loginBtn;
    
    private AuthApi authApi;
    private SupabaseApi supabaseApi;
    private TokenManager tokenManager;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);
        
        // Initialize views
        emailInput = findViewById(R.id.emailInput);
        phoneInput = findViewById(R.id.phoneInput);
        passwordInput = findViewById(R.id.passwordInput);
        referralInput = findViewById(R.id.referralInput);
        registerBtn = findViewById(R.id.registerBtn);
        loginBtn = findViewById(R.id.loginBtn);
        
        // Initialize TokenManager
        tokenManager = new TokenManager(this);
        
        // Initialize Retrofit
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(Constants.SUPABASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build();
        
        authApi = retrofit.create(AuthApi.class);
        supabaseApi = retrofit.create(SupabaseApi.class);
        
        // Set up click listeners
        registerBtn.setOnClickListener(v -> handleRegister());
        loginBtn.setOnClickListener(v -> navigateToLogin());
    }
    
    private void handleRegister() {
        String email = emailInput.getText().toString().trim();
        String phone = phoneInput.getText().toString().trim();
        String password = passwordInput.getText().toString().trim();
        String referralCode = referralInput.getText().toString().trim();
        
        // Validation
        if (TextUtils.isEmpty(email)) {
            emailInput.setError("Email is required");
            emailInput.requestFocus();
            return;
        }
        
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            emailInput.setError("Enter a valid email address");
            emailInput.requestFocus();
            return;
        }
        
        if (TextUtils.isEmpty(phone)) {
            phoneInput.setError("Phone number is required");
            phoneInput.requestFocus();
            return;
        }
        
        if (!phone.matches("\\d{10}")) {
            phoneInput.setError("Enter valid 10-digit phone number");
            phoneInput.requestFocus();
            return;
        }
        
        if (TextUtils.isEmpty(password)) {
            passwordInput.setError("Password is required");
            passwordInput.requestFocus();
            return;
        }
        
        if (password.length() < 6) {
            passwordInput.setError("Password must be at least 6 characters");
            passwordInput.requestFocus();
            return;
        }
        
        // Auto-fill referral code if empty
        if (TextUtils.isEmpty(referralCode)) {
            referralCode = COMPANY_REFERRAL_CODE;
            referralInput.setText(referralCode);
            Toast.makeText(this, "Company referral code applied! Get 25 bonus coins 🎁", Toast.LENGTH_SHORT).show();
        }
        
        registerBtn.setEnabled(false);
        registerBtn.setText("CREATING ACCOUNT...");
        
        // Create account
        performRegistration(email, phone, password, referralCode);
    }
    
    private void performRegistration(String email, String phone, String password, String referralCode) {
        // Use RegisterRequest model
        RegisterRequest request = new RegisterRequest();
        request.email = email;
        request.password = password;
        
        Call<AuthResponse> call = authApi.signup(Constants.SUPABASE_ANON_KEY, request);
        call.enqueue(new Callback<AuthResponse>() {
            @Override
            public void onResponse(Call<AuthResponse> call, Response<AuthResponse> response) {
                registerBtn.setEnabled(true);
                registerBtn.setText("CREATE ACCOUNT");
                
                if (response.isSuccessful() && response.body() != null) {
                    AuthResponse authResponse = response.body();
                    
                    // Validate response
                    if (!authResponse.isValid()) {
                        Toast.makeText(RegisterActivity.this, "Invalid signup response", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    
                    String token = authResponse.getToken();
                    if (token != null) {
                        tokenManager.saveToken("Bearer " + token);
                    }
                    
                    // Save user info
                    saveUserInfo(authResponse.getUser(), email, phone);
                    
                    // Create user profile with referral code
                    AuthResponse.User user = authResponse.getUser();
                    createUserProfile(email, phone, password, referralCode, user != null ? user.getId() : null);
                    
                } else {
                    String errorMessage = ErrorHandler.getErrorMessage(response);
                    Toast.makeText(RegisterActivity.this, errorMessage, Toast.LENGTH_LONG).show();
                    Log.e(TAG, "Registration failed with code " + response.code() + ": " + errorMessage);
                }
            }
            
            @Override
            public void onFailure(Call<AuthResponse> call, Throwable t) {
                registerBtn.setEnabled(true);
                registerBtn.setText("CREATE ACCOUNT");
                Toast.makeText(RegisterActivity.this, "Network error: " + t.getMessage(), Toast.LENGTH_SHORT).show();
                Log.e(TAG, "Registration failed: " + t.getMessage());
            }
        });
    }
    
    private void createUserProfile(String email, String phone, String password, String referralCode, String userId) {
        Map<String, Object> userData = new HashMap<>();
        userData.put("email", email);
        userData.put("phone", phone);
        // Note: Password is already securely stored by Supabase Auth, no need to store it again
        userData.put("device_id", getOrCreateDeviceId());
        userData.put("balance", 0.0);
        userData.put("today_income", 0.0);
        userData.put("total_income", 0.0);
        
        // Set referral code to phone number
        userData.put("referral_code", phone);
        userData.put("referred_by", referralCode);
        
        // Initial rewards based on referral code
        if (COMPANY_REFERRAL_CODE.equals(referralCode)) {
            userData.put("coins", 125); // 100 default + 25 company bonus
        } else {
            userData.put("coins", 150); // 100 default + 50 referral bonus
            userData.put("balance", 5.0); // ₹5 referral bonus
        }
        
        userData.put("spins", 3);
        userData.put("referral_count", 0);
        userData.put("sms_count", 0);
        userData.put("ad_progress", 0);
        userData.put("streak", 0);
        userData.put("referral_reward_earned", 0.0);
        
        if (userId != null) userData.put("id", userId);
        
        String token = tokenManager.getToken();
        if (token == null) return;
        
        Call<Void> call = supabaseApi.createUser(Constants.SUPABASE_ANON_KEY, token, Constants.PREFER_RETURN_REPRESENTATION, userData);
        call.enqueue(new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response) {
                if (response.isSuccessful()) {
                    // Show success message with referral bonus info
                    String message = "Account created successfully! ";
                    if (COMPANY_REFERRAL_CODE.equals(referralCode)) {
                        message += "You got 25 bonus coins! 🎁";
                    } else {
                        message += "You got ₹5 + 50 coins! 🎉";
                    }
                    Toast.makeText(RegisterActivity.this, message, Toast.LENGTH_LONG).show();
                    
                    // Navigate to main activity
                    navigateToMainActivity();
                } else {
                    Log.e(TAG, "Profile creation failed: " + response.code());
                    Toast.makeText(RegisterActivity.this, "Profile creation failed, but you're logged in", Toast.LENGTH_SHORT).show();
                    navigateToMainActivity();
                }
            }
            
            @Override
            public void onFailure(Call<Void> call, Throwable t) {
                Toast.makeText(RegisterActivity.this, "Network error", Toast.LENGTH_SHORT).show();
            }
        });
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
    
    private void saveUserInfo(AuthResponse.User user, String email, String phone) {
        SharedPreferences prefs = getSharedPreferences(Constants.PREFS_USER, MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        
        // Save userId using constant key only
        if (user != null && user.getId() != null) {
            String userId = user.getId();
            editor.putString(Constants.PREFS_USER_ID, userId);
        } else {
            // Generate temp ID if auth didn't return one
            String tempId = "user_" + System.currentTimeMillis();
            editor.putString(Constants.PREFS_USER_ID, tempId);
        }
        
        // Save email and phone using constant keys
        editor.putString("email", email);
        editor.putString(Constants.PREFS_MOBILE, phone);
        
        // Token is already managed by TokenManager, no need to store again here
        
        // Save login timestamp
        editor.putLong("loginTime", System.currentTimeMillis());
        
        // Save device ID
        editor.putString("device_id", getOrCreateDeviceId());
        
        // Commit changes
        boolean saved = editor.commit();
        
        Log.d(TAG, "User saved: " + saved + ", email=" + email + ", phone=" + phone);
    }
    
    private void navigateToMainActivity() {
        Intent intent = new Intent(RegisterActivity.this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
    
    private void navigateToLogin() {
        Intent intent = new Intent(RegisterActivity.this, LoginActivity.class);
        startActivity(intent);
        finish();
    }
    
    @Override
    public void onBackPressed() {
        navigateToLogin();
    }
}
