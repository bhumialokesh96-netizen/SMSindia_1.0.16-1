package com.smsindia.app.data.model;

import com.google.gson.annotations.SerializedName;

public class LoginRequest {
    @SerializedName("email")
    public String email;
    
    @SerializedName("password")
    public String password;
}