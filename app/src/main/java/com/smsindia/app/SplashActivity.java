package com.smsindia.app;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import androidx.appcompat.app.AppCompatActivity;

public class SplashActivity extends AppCompatActivity {
    
    private static final String TAG = "SplashActivity";
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        Log.d(TAG, "SplashActivity starting...");
        
        try {
            // Try to load splash screen
            setContentView(R.layout.activity_splash);
            Log.d(TAG, "Splash layout loaded successfully");
            
        } catch (Exception e) {
            // If ANY error in splash, skip to LoginActivity
            Log.e(TAG, "CRASH in splash: " + e.getMessage());
            e.printStackTrace();
            
            // Immediately go to LoginActivity
            Intent intent = new Intent(this, LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
            return;
        }
        
        new Handler().postDelayed(() -> {
            checkUserAndRedirect();
        }, 1500); // 1.5 seconds
    }
    
    private void checkUserAndRedirect() {
        try {
            SharedPreferences prefs = getSharedPreferences("SMSINDIA_USER", MODE_PRIVATE);
            String userId = prefs.getString("userId", null);
            String mobile = prefs.getString("mobile", null);
            
            Log.d(TAG, "UserID: " + userId);
            Log.d(TAG, "Mobile: " + mobile);
            
            boolean isLoggedIn = (userId != null && !userId.isEmpty()) || 
                                 (mobile != null && !mobile.isEmpty());
            
            Intent intent;
            if (isLoggedIn) {
                Log.d(TAG, "Going to MainActivity");
                intent = new Intent(this, MainActivity.class);
            } else {
                Log.d(TAG, "Going to LoginActivity");
                intent = new Intent(this, LoginActivity.class);
            }
            
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
            
        } catch (Exception e) {
            Log.e(TAG, "Error in checkUserAndRedirect: " + e.getMessage());
            // Fallback to LoginActivity
            Intent intent = new Intent(this, LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        }
    }
    
    @Override
    public void onBackPressed() {
        // Do nothing - disable back button
    }
}