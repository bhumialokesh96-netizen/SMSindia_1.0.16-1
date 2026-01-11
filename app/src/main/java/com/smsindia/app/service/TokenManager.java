package com.smsindia.app.service;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

import java.io.IOException;
import java.security.GeneralSecurityException;

public class TokenManager {
    private static final String TAG = "TokenManager";
    private static final String PREFS_NAME = "SMS_AUTH";
    private static final String KEY_JWT = "jwt";
    private SharedPreferences prefs;
    
    public TokenManager(Context context) {
        try {
            // Create or get MasterKey for encryption
            MasterKey masterKey = new MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build();
            
            // Create EncryptedSharedPreferences
            prefs = EncryptedSharedPreferences.create(
                    context,
                    PREFS_NAME,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            );
        } catch (GeneralSecurityException | IOException e) {
            Log.e(TAG, "Failed to create EncryptedSharedPreferences, falling back to standard SharedPreferences", e);
            // Fallback to standard SharedPreferences if encryption fails
            prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        }
    }
    
    public void saveToken(String token) {
        if (token == null) {
            Log.w(TAG, "Attempted to save null token");
            return;
        }
        try {
            prefs.edit().putString(KEY_JWT, token).apply();
            Log.d(TAG, "Token saved successfully");
        } catch (Exception e) {
            Log.e(TAG, "Failed to save token", e);
        }
    }
    
    public String getToken() {
        try {
            String token = prefs.getString(KEY_JWT, null);
            if (token == null) {
                Log.d(TAG, "No token found");
            }
            return token;
        } catch (Exception e) {
            Log.e(TAG, "Failed to retrieve token", e);
            return null;
        }
    }
    
    public void clear() {
        try {
            prefs.edit().clear().apply();
            Log.d(TAG, "Token cleared successfully");
        } catch (Exception e) {
            Log.e(TAG, "Failed to clear token", e);
        }
    }
}