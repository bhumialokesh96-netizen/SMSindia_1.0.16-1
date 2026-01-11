package com.smsindia.app.data.model;

import com.google.gson.annotations.SerializedName;

/**
 * Model representing referral analytics data
 */
public class ReferralAnalytics {
    
    @SerializedName("id")
    public String id;
    
    @SerializedName("user_id")
    public String userId;
    
    @SerializedName("total_referrals")
    public int totalReferrals;
    
    @SerializedName("successful_referrals")
    public int successfulReferrals;
    
    @SerializedName("pending_referrals")
    public int pendingReferrals;
    
    @SerializedName("failed_referrals")
    public int failedReferrals;
    
    @SerializedName("total_rewards_earned")
    public double totalRewardsEarned;
    
    @SerializedName("tier_bonus_earned")
    public double tierBonusEarned;
    
    @SerializedName("conversion_rate")
    public double conversionRate;
    
    @SerializedName("avg_referral_value")
    public double avgReferralValue;
    
    @SerializedName("last_referral_date")
    public String lastReferralDate;
    
    @SerializedName("best_performing_day")
    public String bestPerformingDay;
    
    public ReferralAnalytics() {}
    
    // Helper methods
    public String getConversionRateDisplay() {
        return String.format("%.1f%%", conversionRate);
    }
    
    public String getAvgValueDisplay() {
        return "₹" + String.format("%.2f", avgReferralValue);
    }
    
    public String getTotalRewardsDisplay() {
        return "₹" + String.format("%.2f", totalRewardsEarned);
    }
    
    public int getSuccessRate() {
        if (totalReferrals == 0) return 0;
        return (int) ((successfulReferrals * 100.0) / totalReferrals);
    }
}
