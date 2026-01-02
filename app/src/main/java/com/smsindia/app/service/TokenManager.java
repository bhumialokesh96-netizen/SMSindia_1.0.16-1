package com.smsindia.app.service;

import android.content.Context;
import android.content.SharedPreferences;

public class TokenManager {
    private SharedPreferences prefs;
    
    public TokenManager(Context context) {
        prefs = context.getSharedPreferences("SMS_AUTH", Context.MODE_PRIVATE);
    }
    
    public void saveToken(String token) {
        prefs.edit().putString("jwt", token).apply();
    }
    
    public String getToken() {
        return prefs.getString("jwt", null);
    }
    
    public void clear() {
        prefs.edit().clear().apply();
    }
}