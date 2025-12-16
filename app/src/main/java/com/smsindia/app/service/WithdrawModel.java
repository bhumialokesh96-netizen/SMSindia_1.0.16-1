package com.smsindia.app.service;

import com.google.gson.annotations.SerializedName;

public class WithdrawModel {

    @SerializedName("amount")
    public double amount;

    @SerializedName("status")
    public String status;

    @SerializedName("created_at")
    public String createdAt; // Supabase sends date as String (ISO format)

    public WithdrawModel(double amount, String status, String createdAt) {
        this.amount = amount;
        this.status = status;
        this.createdAt = createdAt;
    }
}
