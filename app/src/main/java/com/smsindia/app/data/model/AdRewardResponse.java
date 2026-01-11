package com.smsindia.app.data.model;

import com.google.gson.annotations.SerializedName;

/**
 * Type-safe model for ad reward API response
 */
public class AdRewardResponse {
    
    @SerializedName("success")
    private boolean success;
    
    @SerializedName("message")
    private String message;
    
    @SerializedName("new_balance")
    private Double newBalance;
    
    @SerializedName("reward_amount")
    private Double rewardAmount;
    
    // Default constructor
    public AdRewardResponse() {
    }
    
    public AdRewardResponse(boolean success, String message, Double newBalance, Double rewardAmount) {
        this.success = success;
        this.message = message;
        this.newBalance = newBalance;
        this.rewardAmount = rewardAmount;
    }
    
    // Getters
    public boolean isSuccess() {
        return success;
    }
    
    public String getMessage() {
        return message != null ? message : "";
    }
    
    public Double getNewBalance() {
        return newBalance;
    }
    
    public Double getRewardAmount() {
        return rewardAmount;
    }
    
    // Setters
    public void setSuccess(boolean success) {
        this.success = success;
    }
    
    public void setMessage(String message) {
        this.message = message;
    }
    
    public void setNewBalance(Double newBalance) {
        this.newBalance = newBalance;
    }
    
    public void setRewardAmount(Double rewardAmount) {
        this.rewardAmount = rewardAmount;
    }
    
    @Override
    public String toString() {
        return "AdRewardResponse{" +
                "success=" + success +
                ", message='" + message + '\'' +
                ", newBalance=" + newBalance +
                ", rewardAmount=" + rewardAmount +
                '}';
    }
}
