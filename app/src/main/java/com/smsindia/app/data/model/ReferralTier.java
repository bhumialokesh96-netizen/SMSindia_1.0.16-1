package com.smsindia.app.data.model;

import com.google.gson.annotations.SerializedName;

/**
 * Model representing a referral tier level
 */
public class ReferralTier {
    
    @SerializedName("id")
    public String id;
    
    @SerializedName("tier_name")
    public String tierName;
    
    @SerializedName("tier_level")
    public int tierLevel;
    
    @SerializedName("min_referrals")
    public int minReferrals;
    
    @SerializedName("max_referrals")
    public Integer maxReferrals;
    
    @SerializedName("reward_multiplier")
    public double rewardMultiplier;
    
    @SerializedName("bonus_coins")
    public int bonusCoins;
    
    @SerializedName("badge_color")
    public String badgeColor;
    
    @SerializedName("badge_icon")
    public String badgeIcon;
    
    @SerializedName("benefits")
    public String benefits;
    
    public ReferralTier() {}
    
    // Helper methods
    public String getTierName() {
        return tierName;
    }
    
    public int getTierLevel() {
        return tierLevel;
    }
    
    public String getBadgeColor() {
        return badgeColor != null ? badgeColor : "#757575";
    }
    
    public String getDisplayReward() {
        double baseReward = 10.0;
        double actualReward = baseReward * rewardMultiplier;
        return "₹" + String.format("%.2f", actualReward);
    }
}
