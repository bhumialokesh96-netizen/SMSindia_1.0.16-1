package com.smsindia.app.service;

import com.google.gson.annotations.SerializedName;

public class TaskModel {
    // If your Supabase Table columns are simply 'id', 'phone', 'message':
    
    @SerializedName("id") 
    public String id;

    @SerializedName("phone") 
    public String phone;

    @SerializedName("message") 
    public String message;
}
