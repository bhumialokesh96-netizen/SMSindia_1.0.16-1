package com.smsindia.app.service;

import com.google.gson.annotations.SerializedName;

public class TaskModel {
    @SerializedName("id") 
    public String id;

    @SerializedName("phone") 
    public String phone;

    @SerializedName("message") 
    public String message;

    // ✅ Recommended: Add this just in case you need to check state later
    @SerializedName("status") 
    public String status; 
}
