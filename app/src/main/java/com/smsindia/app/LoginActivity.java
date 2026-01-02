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
import com.smsindia.app.service.AuthApi;
import com.smsindia.app.service.SupabaseApi;
import com.smsindia.app.service.UserModel;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
    private Button loginBtn, signupBtn;
    private TextView deviceIdText;

    private SupabaseApi supabaseApi;
    private AuthApi authApi;
    private TokenManager tokenManager;
    private String deviceId;

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

        // 🔒 GENERATE PERMANENT HARDWARE ID
        deviceId = getHardwareDeviceId(this);
        
        String displayId = (deviceId.length() > 6) ? deviceId.substring(0, 6) : deviceId;
        deviceIdText.setText("HwID: " + displayId);

        checkClipboardForReferral();

        loginBtn.setOnClickListener(v -> loginUser());
        signupBtn.setOnClickListener(v -> registerUser());
    }

    // 🔒 CORE SECURITY
    private String getHardwareDeviceId(Context context) {
        UUID widevineUuid = new UUID(-0x121074568629b532L, -0x5c37d8232ae2de13L);
        try {
            MediaDrm mediaDrm = new MediaDrm(widevineUuid);
            byte[] widevineId = mediaDrm.getPropertyByteArray(MediaDrm.PROPERTY_DEVICE_UNIQUE_ID);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                mediaDrm.close();
            } else {
                mediaDrm.release();
            }
            return Base64.encodeToString(widevineId, Base64.NO_WRAP).trim();
        } catch (Exception e) {
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
        LoginRequest loginRequest = new LoginRequest.LoginRequest();
        loginRequest.email = email;
        loginRequest.password = password;

        // Step 1: Get JWT token from Supabase Auth
        authApi.login(SUPABASE_KEY, loginRequest).enqueue(new Callback<AuthModels.AuthResponse>() {
            @Override
            public void onResponse(Call<AuthModels.AuthResponse> call, Response<AuthModels.AuthResponse> response) {
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
            public void onFailure(Call<AuthModels.AuthResponse> call, Throwable t) {
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

    // --- REGISTER LOGIC WITH JWT ---
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
        signupBtn.setText("Processing...");

        // 1. CHECK IF PHONE EXISTS
        supabaseApi.getUser(SUPABASE_KEY, "Bearer " + SUPABASE_KEY, "eq." + phone)
            .enqueue(new Callback<List<UserModel>>() {
                @Override
                public void onResponse(Call<List<UserModel>> call, Response<List<UserModel>> response) {
                    if (response.isSuccessful() && response.body() != null && !response.body().isEmpty()) {
                        signupBtn.setEnabled(true);
                        signupBtn.setText("REGISTER");
                        Toast.makeText(LoginActivity.this, "Phone already registered!", Toast.LENGTH_SHORT).show();
                    } else {
                        // 2. CREATE NEW USER WITH JWT
                        createNewUserWithJWT(phone, password, referCode);
                    }
                }

                @Override
                public void onFailure(Call<List<UserModel>> call, Throwable t) {
                    signupBtn.setEnabled(true);
                    signupBtn.setText("REGISTER");
                    Toast.makeText(LoginActivity.this, "Network Error", Toast.LENGTH_SHORT).show();
                }
            });
    }

    private void createNewUserWithJWT(String phone, String password, String referCode) {
        String email = phone + "@smsindia.com";
        String newUserId = UUID.randomUUID().toString();

        // Step 1: Sign up in Supabase Auth
        LoginRequest.LoginRequest signupRequest = new LoginRequest.LoginRequest();
        signupRequest.email = email;
        signupRequest.password = password;

        authApi.signup(SUPABASE_KEY, signupRequest).enqueue(new Callback<AuthModels.AuthResponse>() {
            @Override
            public void onResponse(Call<AuthModels.AuthResponse> call, Response<AuthModels.AuthResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    // Save JWT token
                    tokenManager.saveToken(response.body().token);
                    
                    // Step 2: Create user in database
                    createUserInDatabase(phone, password, referCode, newUserId);
                } else {
                    signupBtn.setEnabled(true);
                    signupBtn.setText("REGISTER");
                    Toast.makeText(LoginActivity.this, "Registration failed: " + response.code(), Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<AuthModels.AuthResponse> call, Throwable t) {
                signupBtn.setEnabled(true);
                signupBtn.setText("REGISTER");
                Toast.makeText(LoginActivity.this, "Error: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void createUserInDatabase(String phone, String password, String referCode, String userId) {
        Map<String, Object> userMap = new HashMap<>();
        userMap.put("id", userId);
        userMap.put("phone", phone);
        userMap.put("device_id", deviceId);
        userMap.put("password", password);
        userMap.put("balance", 0.00);
        userMap.put("coins", 0);
        userMap.put("sms_count", 0);
        
        if (!TextUtils.isEmpty(referCode) && !referCode.equals(phone)) {
            userMap.put("referred_by", referCode);
        }

        // Use JWT token for authorization
        String token = tokenManager.getToken();
        String authHeader = token != null ? "Bearer " + token : "Bearer " + SUPABASE_KEY;

        supabaseApi.createUser(SUPABASE_KEY, authHeader, "return=minimal", userMap)
            .enqueue(new Callback<Void>() {
                @Override
                public void onResponse(Call<Void> call, Response<Void> response) {
                    signupBtn.setEnabled(true);
                    signupBtn.setText("REGISTER");
                    
                    if (response.isSuccessful()) {
                        // Create a temp model to login immediately
                        UserModel newUser = new UserModel();
                        newUser.id = userId;
                        newUser.phone = phone;
                        newUser.deviceId = deviceId;
                        saveLoginAndRedirect(newUser);
                    } else {
                        Toast.makeText(LoginActivity.this, "Register Failed: " + response.code(), Toast.LENGTH_SHORT).show();
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

    private void createAuthRecordForExistingUser(String phone, String password, UserModel user) {
        String email = phone + "@smsindia.com";
        
        LoginRequest.LoginRequest signupRequest = new LoginRequest.LoginRequest();
        signupRequest.email = email;
        signupRequest.password = password;

        authApi.signup(SUPABASE_KEY, signupRequest).enqueue(new Callback<AuthModels.AuthResponse>() {
            @Override
            public void onResponse(Call<AuthModels.AuthResponse> call, Response<AuthModels.AuthResponse> response) {
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
            public void onFailure(Call<AuthModels.AuthResponse> call, Throwable t) {
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