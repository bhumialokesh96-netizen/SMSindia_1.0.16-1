package com.smsindia.app.service;

import com.google.gson.annotations.SerializedName;

public class ClaimRequest {
    @SerializedName("p_user_id") final String userId;
    @SerializedName("p_task_id") final String taskId;
    @SerializedName("p_phone") final String phone;
    @SerializedName("p_amount") final double amount;

    public ClaimRequest(String userId, String taskId, String phone, double amount) {
        this.userId = userId;
        this.taskId = taskId;
        this.phone = phone;
        this.amount = amount;
    }
}
