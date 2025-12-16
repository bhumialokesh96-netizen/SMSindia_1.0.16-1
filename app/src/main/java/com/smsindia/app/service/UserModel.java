package com.smsindia.app.service;

import com.google.gson.annotations.SerializedName;

public class UserModel {

    @SerializedName("id")
    public String id; // The User UUID

    @SerializedName("phone")
    public String phone;

    @SerializedName("balance")
    public double balance;

    @SerializedName("coins")
    public long coins;

    @SerializedName("device_id")
    public String deviceId;

    @SerializedName("referral_count")
    public int referralCount;

    @SerializedName("sms_count")
    public int smsCount;
    
    @SerializedName("bank_details")
    public Object bankDetails;
    @SerializedName("ad_progress")
    public int adProgress;

    @SerializedName("spins")
    public int spins;// Supabase returns this as JSON Object
    @SerializedName("claimed_milestones")
public Object claimedMilestones;
@SerializedName("last_checkin_date")
    public String lastCheckinDate;

    @SerializedName("streak")
    public int streak;

    // Default Constructor
    public UserModel() {}

    // Getters for UI
    public double getBalance() { return balance; }
    public long getCoins() { return coins; }
    public String getId() { return id; }
}
