package com.smsindia.app.service;

import com.google.gson.annotations.SerializedName;

public class TransactionModel {
    @SerializedName("title")
    public String title;

    @SerializedName("amount")
    public double amount;

    @SerializedName("type")
    public String type; // "CREDIT" or "DEBIT"

    @SerializedName("created_at")
    public String createdAt; // ISO Date String
}
