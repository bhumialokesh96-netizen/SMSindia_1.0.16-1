package com.smsindia.app.service;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.Header;
import retrofit2.http.POST;
import java.util.Map;

public interface AuthApi {
    @POST("/auth/v1/token?grant_type=password")
    Call<AuthResponse> login(@Header("apikey") String apiKey, @Body LoginRequest request);
    
    @POST("/auth/v1/signup")
    Call<AuthResponse> signup(@Header("apikey") String apiKey, @Body LoginRequest request);
    
    // ADD THIS METHOD:
    @POST("/auth/v1/recover")
    Call<Void> resetPassword(@Header("apikey") String apiKey, @Body Map<String, String> request);
}