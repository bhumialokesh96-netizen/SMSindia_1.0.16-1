package com.smsindia.app;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.smsindia.app.ui.HomeFragment;
import com.smsindia.app.ui.ProfileFragment;
import com.smsindia.app.ui.ShareFragment;
import com.smsindia.app.ui.SpinFragment;
import com.smsindia.app.ui.TaskFragment;

public class MainActivity extends AppCompatActivity {

    private BottomNavigationView navView;

    // Permission Launchers
    private final ActivityResultLauncher<String> smsPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(),
                    granted -> {
                        if (!granted) {
                            Toast.makeText(this, "SMS permission denied. Tasks may not work.", Toast.LENGTH_SHORT).show();
                        }
                    });

    private final ActivityResultLauncher<String> phonePermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(),
                    granted -> {
                        if (!granted) {
                            Toast.makeText(this, "Phone permission denied.", Toast.LENGTH_SHORT).show();
                        }
                    });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        try {
            // FIRST set the layout
            setContentView(R.layout.activity_main);
            
            // THEN check permissions and user data
            checkUserAndPermissions();
            
        } catch (Exception e) {
            // If ANY error occurs, redirect to login
            e.printStackTrace();
            redirectToLogin();
        }
    }

    private void checkUserAndPermissions() {
        // ✅ FIXED: More flexible user check
        SharedPreferences prefs = getSharedPreferences("SMSINDIA_USER", MODE_PRIVATE);
        String userId = prefs.getString("userId", null);
        String mobile = prefs.getString("mobile", null);
        
        // Debug: Show what's stored
        android.util.Log.d("MAIN_ACTIVITY", "UserID: " + userId);
        android.util.Log.d("MAIN_ACTIVITY", "Mobile: " + mobile);
        
        // Check if we have SOME user data
        boolean hasUserData = (userId != null && !userId.isEmpty()) || 
                              (mobile != null && !mobile.isEmpty());
        
        if (!hasUserData) {
            // No user data found, redirect to login
            Toast.makeText(this, "Please login to continue", Toast.LENGTH_SHORT).show();
            redirectToLogin();
            return; // IMPORTANT: Stop execution here
        }
        
        // If we reach here, we have user data - continue setup
        setupUI();
    }

    private void setupUI() {
        navView = findViewById(R.id.bottomNavigationView);

        // Load default fragment (Home)
        loadFragment(new HomeFragment());

        // NAVIGATION LOGIC
        navView.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            Fragment selectedFragment = null;

            if (id == R.id.navigation_home) {
                selectedFragment = new HomeFragment();
            } else if (id == R.id.navigation_spin) {
                selectedFragment = new SpinFragment();
            } else if (id == R.id.navigation_tasks) {
                selectedFragment = new TaskFragment();
            } else if (id == R.id.navigation_share) {
                selectedFragment = new ShareFragment();
            } else if (id == R.id.navigation_profile) {
                selectedFragment = new ProfileFragment();
            }

            if (selectedFragment != null) {
                loadFragment(selectedFragment);
                return true;
            }
            return false;
        });
        
        // Request necessary permissions
        checkPermissions();
    }

    private void checkPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS)
                != PackageManager.PERMISSION_GRANTED) {
            smsPermissionLauncher.launch(Manifest.permission.SEND_SMS);
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE)
                != PackageManager.PERMISSION_GRANTED) {
            phonePermissionLauncher.launch(Manifest.permission.READ_PHONE_STATE);
        }
    }

    private void loadFragment(Fragment fragment) {
        try {
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.fragment_container, fragment)
                    .commitAllowingStateLoss();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void redirectToLogin() {
        try {
            Intent intent = new Intent(this, LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
