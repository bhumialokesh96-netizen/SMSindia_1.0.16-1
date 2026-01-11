package com.smsindia.app.service;

import androidx.annotation.Nullable;
import com.google.gson.annotations.SerializedName;

public class AuthResponse {
    @SerializedName("access_token")
    private String token;
    
    @SerializedName("user_id")
    private String userId;
    
    @SerializedName("user")
    private User user;
    
    // Default constructor
    public AuthResponse() {
    }
    
    // Getters with null safety
    @Nullable
    public String getToken() {
        return token;
    }
    
    @Nullable
    public String getUserId() {
        return userId;
    }
    
    @Nullable
    public User getUser() {
        return user;
    }
    
    // Setters
    public void setToken(String token) {
        this.token = token;
    }
    
    public void setUserId(String userId) {
        this.userId = userId;
    }
    
    public void setUser(User user) {
        this.user = user;
    }
    
    // Validation helper
    public boolean isValid() {
        return token != null && !token.isEmpty() && user != null;
    }
    
    public static class User {
        @SerializedName("id")
        private String id;
        
        @SerializedName("email")
        private String email;
        
        @SerializedName("phone")
        private String phone;
        
        @SerializedName("email_confirmed_at")
        private String emailConfirmedAt;
        
        // Default constructor
        public User() {
        }
        
        // Getters with null safety
        @Nullable
        public String getId() {
            return id;
        }
        
        @Nullable
        public String getEmail() {
            return email;
        }
        
        @Nullable
        public String getPhone() {
            return phone;
        }
        
        @Nullable
        public String getEmailConfirmedAt() {
            return emailConfirmedAt;
        }
        
        // Setters
        public void setId(String id) {
            this.id = id;
        }
        
        public void setEmail(String email) {
            this.email = email;
        }
        
        public void setPhone(String phone) {
            this.phone = phone;
        }
        
        public void setEmailConfirmedAt(String emailConfirmedAt) {
            this.emailConfirmedAt = emailConfirmedAt;
        }
        
        @Override
        public String toString() {
            return "User{" +
                    "id='" + id + '\'' +
                    ", email='" + email + '\'' +
                    ", phone='" + phone + '\'' +
                    ", emailConfirmedAt='" + emailConfirmedAt + '\'' +
                    '}';
        }
    }
    
    @Override
    public String toString() {
        return "AuthResponse{" +
                "token='" + (token != null ? "[REDACTED]" : "null") + '\'' +
                ", userId='" + userId + '\'' +
                ", user=" + user +
                '}';
    }
}