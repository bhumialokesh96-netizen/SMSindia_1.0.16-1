package com.smsindia.app.data.model;

import com.google.gson.annotations.SerializedName;

/**
 * Model representing a leaderboard entry
 */
public class LeaderboardEntry {
    
    @SerializedName("rank")
    public long rank;
    
    @SerializedName("phone")
    public String phone;
    
    @SerializedName("referral_code")
    public String referralCode;
    
    @SerializedName("referral_count")
    public int referralCount;
    
    @SerializedName("rewards_earned")
    public double rewardsEarned;
    
    @SerializedName("tier_name")
    public String tierName;
    
    @SerializedName("badge_color")
    public String badgeColor;
    
    @SerializedName("badge_icon")
    public String badgeIcon;
    
    public LeaderboardEntry() {}
    
    // Helper methods
    public String getMaskedPhone() {
        if (phone == null || phone.length() < MIN_PHONE_LENGTH) return "****";
        return phone.substring(0, 2) + "****" + phone.substring(phone.length() - 2);
    }
    
    private static final int MIN_PHONE_LENGTH = 4;
    
    public String getRankDisplay() {
        if (rank == 1) return "🥇";
        if (rank == 2) return "🥈";
        if (rank == 3) return "🥉";
        return String.valueOf(rank);
    }
    
    public String getTierBadge() {
        if (tierName == null) return "";
        switch (tierName.toLowerCase()) {
            case "gold": return "👑";
            case "silver": return "⭐";
            case "bronze": return "🏅";
            default: return "";
        }
    }
}
