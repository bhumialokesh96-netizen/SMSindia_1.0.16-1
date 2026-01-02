package com.smsindia.app.service;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.Header;
import retrofit2.http.POST;

public interface AuthApi {
    @POST("/auth/v1/token?grant_type=password")
    Call<AuthResponse> login(@Header("apikey") String key, @Body LoginRequest request);
    
    @POST("/auth/v1/signup")
    Call<AuthResponse> signup(@Header("apikey") String key, @Body LoginRequest request);
}