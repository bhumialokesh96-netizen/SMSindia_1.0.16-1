package com.smsindia.app.service;

import com.google.gson.annotations.SerializedName;

public class UserModel {

    @SerializedName("id")
    public String id; 

    @SerializedName("phone")
    public String phone;

    @SerializedName("password") 
    public String password; 

    @SerializedName("device_id")
    public String deviceId;

    // --- WALLET SECTION ---
    @SerializedName("balance")
    public double balance;

    @SerializedName("today_income")  // ✅ ADDED: Needed for Home Screen
    public double todayIncome;

    @SerializedName("total_income")  // ✅ ADDED: Needed for Home Screen
    public double totalIncome;

    @SerializedName("coins")
    public long coins;

    // --- STATS ---
    @SerializedName("referral_count")
    public int referralCount;

    @SerializedName("sms_count")
    public int smsCount;
    
    @SerializedName("ad_progress")
    public int adProgress;

    @SerializedName("spins")
    public int spins;

    // --- REWARDS & BANK ---
    @SerializedName("bank_details")
    public Object bankDetails; // stored as JSONb in Supabase

    @SerializedName("claimed_milestones")
    public Object claimedMilestones;

    @SerializedName("last_checkin_date")
    public String lastCheckinDate;

    @SerializedName("streak")
    public int streak;

    // Default Constructor
    public UserModel() {}

    // Getters
    public double getBalance() { return balance; }
    public double getTodayIncome() { return todayIncome; }
    public double getTotalIncome() { return totalIncome; }
}
