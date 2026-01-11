package com.smsindia.app.data.model;

import com.google.gson.annotations.SerializedName;

public class WithdrawModel {

    @SerializedName("amount")
    private double amount;

    @SerializedName("status")
    private String status;

    @SerializedName("created_at")
    private String createdAt; // Supabase sends date as String (ISO format)

    // Default constructor
    public WithdrawModel() {
    }
    
    public WithdrawModel(double amount, String status, String createdAt) {
        this.amount = amount;
        this.status = status;
        this.createdAt = createdAt;
    }
    
    // Getters
    public double getAmount() {
        return amount;
    }
    
    public String getStatus() {
        return status;
    }
    
    public String getCreatedAt() {
        return createdAt;
    }
    
    // Setters
    public void setAmount(double amount) {
        this.amount = amount;
    }
    
    public void setStatus(String status) {
        this.status = status;
    }
    
    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }
    
    @Override
    public String toString() {
        return "WithdrawModel{" +
                "amount=" + amount +
                ", status='" + status + '\'' +
                ", createdAt='" + createdAt + '\'' +
                '}';
    }
}
