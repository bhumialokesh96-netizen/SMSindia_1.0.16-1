package com.smsindia.app.service;

import com.google.gson.annotations.SerializedName;

public class SmsLogModel {
    @SerializedName("phone")
    public String phone;

    @SerializedName("status")
    public String status;

    @SerializedName("created_at")
    public String createdAt; // ISO Date String
}
