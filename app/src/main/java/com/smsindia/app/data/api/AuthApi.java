package com.smsindia.app.data.api;

import com.smsindia.app.data.model.*;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.Header;
import retrofit2.http.POST;
import retrofit2.http.PUT;
import java.util.Map;

public interface AuthApi {
    @POST("/auth/v1/token?grant_type=password")
    Call<AuthResponse> login(@Header("apikey") String apiKey, @Body LoginRequest request);
    
    @POST("/auth/v1/signup")
    Call<AuthResponse> signup(@Header("apikey") String apiKey, @Body LoginRequest request);
    
    @POST("/auth/v1/recover")
    Call<Void> resetPassword(@Header("apikey") String apiKey, @Body Map<String, String> request);
    
    // Remove this method as Supabase doesn't have /auth/v1/verify endpoint
    // Instead, use the OTP methods in SupabaseApi
    
    // Password update using Supabase Admin API (if needed)
    @POST("/auth/v1/admin/users")
    Call<Void> updateUserPassword(
        @Header("apikey") String apiKey,
        @Header("Authorization") String token,
        @Body Map<String, Object> request
    );
}