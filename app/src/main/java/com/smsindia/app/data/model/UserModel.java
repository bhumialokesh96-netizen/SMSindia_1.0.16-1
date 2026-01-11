package com.smsindia.app.data.model;

import com.google.gson.annotations.SerializedName;

public class UserModel {

    @SerializedName("id") public String id; 
    @SerializedName("phone") public String phone;
    @SerializedName("email") public String email;  // ADD THIS LINE
    @SerializedName("password") public String password; 
    @SerializedName("device_id") public String deviceId;

    @SerializedName("balance") public double balance;
    @SerializedName("today_income") public double todayIncome;
    @SerializedName("total_income") public double totalIncome;
    @SerializedName("coins") public long coins;
    @SerializedName("spins") public int spins;

    @SerializedName("referral_count") public int referralCount;
    @SerializedName("sms_count") public int smsCount;
    @SerializedName("ad_progress") public int adProgress;
    @SerializedName("streak") public int streak;
    
    @SerializedName("current_tier") public int currentTier;
    @SerializedName("tier_updated_at") public String tierUpdatedAt;

    @SerializedName("bank_details") public Object bankDetails;
    @SerializedName("claimed_milestones") public Object claimedMilestones;
    @SerializedName("last_checkin_date") public String lastCheckinDate;

    public UserModel() {}

    // REQUIRED GETTERS
    public String getId() { return id; }
    public double getBalance() { return balance; }
    public double getTodayIncome() { return todayIncome; }
    public double getTotalIncome() { return totalIncome; }
    public long getCoins() { return coins; } // Fixes Spin/Share errors
    public int getCurrentTier() { return currentTier > 0 ? currentTier : 1; }
    
    // Helper methods for tier display
    public String getTierName() {
        switch (getCurrentTier()) {
            case 3: return "Gold";
            case 2: return "Silver";
            case 1:
            default: return "Bronze";
        }
    }
    
    public String getTierBadge() {
        switch (getCurrentTier()) {
            case 3: return "👑";
            case 2: return "⭐";
            case 1:
            default: return "🏅";
        }
    }
    
    public int getReferralsToNextTier() {
        int current = referralCount;
        switch (getCurrentTier()) {
            case 1: return Math.max(0, 11 - current); // To Silver
            case 2: return Math.max(0, 51 - current); // To Gold
            case 3:
            default: return 0; // Max tier
        }
    }
}