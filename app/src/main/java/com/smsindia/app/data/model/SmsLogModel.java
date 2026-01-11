package com.smsindia.app.data.model;

import com.google.gson.annotations.SerializedName;

public class SmsLogModel {
    @SerializedName("phone") // Ensure your SQL 'sms_logs' has a 'phone' column if you want this
    public String phone;

    @SerializedName("status")
    public String status;

    @SerializedName("reward") // ✅ ADD THIS so you can see earnings in logs
    public double reward;

    @SerializedName("created_at")
    public String createdAt;
}
