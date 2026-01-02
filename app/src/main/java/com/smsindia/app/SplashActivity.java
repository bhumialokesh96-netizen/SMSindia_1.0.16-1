package com.smsindia.app;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import androidx.appcompat.app.AppCompatActivity;

public class SplashActivity extends AppCompatActivity {
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);
        
        // Optional: Add fade-in animation
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        
        new Handler().postDelayed(() -> {
            checkUserAndRedirect();
        }, 2000); // 2 seconds
    }
    
    private void checkUserAndRedirect() {
        SharedPreferences prefs = getSharedPreferences("SMSINDIA_USER", MODE_PRIVATE);
        String userId = prefs.getString("userId", null);
        String mobile = prefs.getString("mobile", null);
        
        boolean isLoggedIn = (userId != null && !userId.isEmpty()) || 
                             (mobile != null && !mobile.isEmpty());
        
        Intent intent;
        if (isLoggedIn) {
            intent = new Intent(this, MainActivity.class);
        } else {
            intent = new Intent(this, LoginActivity.class);
        }
        
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        
        // Add fade-out animation
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        finish();
    }
    
    @Override
    public void onBackPressed() {
        // Disable back button during splash
        // Do nothing
    }
}
