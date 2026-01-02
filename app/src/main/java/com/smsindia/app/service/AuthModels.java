package com.smsindia.app.service;

import com.google.gson.annotations.SerializedName;

public class AuthResponse {
    @SerializedName("access_token") public String token;
    @SerializedName("user_id") public String userId;
}

class LoginRequest {
    @SerializedName("email") public String email;
    @SerializedName("password") public String password;
}