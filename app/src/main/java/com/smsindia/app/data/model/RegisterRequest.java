package com.smsindia.app.data.model;

import com.google.gson.annotations.SerializedName;

public class RegisterRequest {
    @SerializedName("email")
    public String email;
    
    @SerializedName("password")
    public String password;
    
    @SerializedName("phone")
    public String phone;
    
    @SerializedName("options")
    public Options options;
    
    public static class Options {
        @SerializedName("data")
        public Data data;
    }
    
    public static class Data {
        @SerializedName("phone")
        public String phone;
        
        @SerializedName("referral_code")
        public String referralCode;
    }
}
