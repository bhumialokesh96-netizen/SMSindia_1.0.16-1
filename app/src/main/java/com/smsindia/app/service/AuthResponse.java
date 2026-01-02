package com.smsindia.app.service;

import com.google.gson.annotations.SerializedName;

public class AuthResponse {
    @SerializedName("access_token")
    public String token;
    
    @SerializedName("user_id")
    public String userId;
    
    // ADD THIS USER INNER CLASS
    @SerializedName("user")
    public User user;
    
    public static class User {
        @SerializedName("id")
        public String id;
        
        @SerializedName("email")
        public String email;
        
        @SerializedName("phone")
        public String phone;
        
        // Add other fields if needed
        @SerializedName("email_confirmed_at")
        public String emailConfirmedAt;
    }
}