package com.smsindia.app.service;

public interface AuthApi {
    @POST("/auth/v1/token?grant_type=password")
    Call<AuthResponse> login(@Header("apikey") String apiKey, @Body LoginRequest request);
    
    @POST("/auth/v1/signup")
    Call<AuthResponse> signup(@Header("apikey") String apiKey, @Body LoginRequest request);
    
    @POST("/auth/v1/recover")
    Call<Void> resetPassword(@Header("apikey") String apiKey, @Body Map<String, String> request);
    
    // ADD THIS METHOD FOR OTP VERIFICATION
    @POST("/auth/v1/verify")
    Call<AuthResponse> verifyEmail(@Header("apikey") String apiKey, @Body Map<String, String> request);
    
    // ADD THIS METHOD FOR PASSWORD UPDATE
    @PUT("/auth/v1/user")
    Call<Void> updatePassword(
        @Header("apikey") String apiKey,
        @Header("Authorization") String token,
        @Body Map<String, String> request
    );
}