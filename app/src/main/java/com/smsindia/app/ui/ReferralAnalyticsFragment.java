package com.smsindia.app.ui;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import com.google.android.material.card.MaterialCardView;
import com.smsindia.app.R;
import com.smsindia.app.config.Constants;
import com.smsindia.app.data.api.SupabaseApi;
import com.smsindia.app.data.model.ReferralAnalytics;
import com.smsindia.app.data.model.ReferralTier;

import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class ReferralAnalyticsFragment extends Fragment {

    private TextView tvTotalReferrals, tvSuccessful, tvPending, tvFailed;
    private TextView tvTotalRewards, tvConversionRate, tvAvgValue;
    private TextView tvCurrentTier, tvTierBenefits, tvProgressToNext;
    private ProgressBar progressBar, progressTier;
    private MaterialCardView cardTierInfo;
    
    private SupabaseApi supabaseApi;
    private String userId;
    private int referralCount = 0;
    private int currentTier = 1;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_referral_analytics, container, false);

        // Init Retrofit
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(Constants.SUPABASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build();
        supabaseApi = retrofit.create(SupabaseApi.class);

        // Get user info
        SharedPreferences prefs = requireActivity().getSharedPreferences("SMSINDIA_USER", 0);
        userId = prefs.getString("user_id", "");
        referralCount = prefs.getInt("referral_count", 0);
        currentTier = prefs.getInt("current_tier", 1);

        // Init Views
        progressBar = v.findViewById(R.id.progress_analytics);
        
        // Analytics Stats
        tvTotalReferrals = v.findViewById(R.id.tv_analytics_total);
        tvSuccessful = v.findViewById(R.id.tv_analytics_successful);
        tvPending = v.findViewById(R.id.tv_analytics_pending);
        tvFailed = v.findViewById(R.id.tv_analytics_failed);
        tvTotalRewards = v.findViewById(R.id.tv_analytics_rewards);
        tvConversionRate = v.findViewById(R.id.tv_analytics_conversion);
        tvAvgValue = v.findViewById(R.id.tv_analytics_avg_value);
        
        // Tier Info
        cardTierInfo = v.findViewById(R.id.card_tier_info);
        tvCurrentTier = v.findViewById(R.id.tv_current_tier);
        tvTierBenefits = v.findViewById(R.id.tv_tier_benefits);
        tvProgressToNext = v.findViewById(R.id.tv_progress_to_next);
        progressTier = v.findViewById(R.id.progress_tier);

        // Load data
        loadAnalytics();
        loadTierInfo();

        return v;
    }

    private String getAuthHeader() {
        SharedPreferences authPrefs = requireActivity().getSharedPreferences("SMS_AUTH", Context.MODE_PRIVATE);
        String token = authPrefs.getString("jwt", null);
        return token != null ? "Bearer " + token : "Bearer " + Constants.SUPABASE_ANON_KEY;
    }

    private void loadAnalytics() {
        if (userId.isEmpty()) return;
        
        progressBar.setVisibility(View.VISIBLE);

        supabaseApi.getReferralAnalytics(Constants.SUPABASE_ANON_KEY, getAuthHeader(), "eq." + userId)
            .enqueue(new Callback<List<ReferralAnalytics>>() {
                @Override
                public void onResponse(Call<List<ReferralAnalytics>> call, Response<List<ReferralAnalytics>> response) {
                    progressBar.setVisibility(View.GONE);
                    if (response.isSuccessful() && response.body() != null && !response.body().isEmpty()) {
                        ReferralAnalytics analytics = response.body().get(0);
                        updateAnalyticsUI(analytics);
                    } else {
                        // Show default values
                        updateDefaultAnalytics();
                    }
                }

                @Override
                public void onFailure(Call<List<ReferralAnalytics>> call, Throwable t) {
                    progressBar.setVisibility(View.GONE);
                    updateDefaultAnalytics();
                }
            });
    }

    private void updateAnalyticsUI(ReferralAnalytics analytics) {
        tvTotalReferrals.setText(String.valueOf(analytics.totalReferrals));
        tvSuccessful.setText(String.valueOf(analytics.successfulReferrals));
        tvPending.setText(String.valueOf(analytics.pendingReferrals));
        tvFailed.setText(String.valueOf(analytics.failedReferrals));
        tvTotalRewards.setText(analytics.getTotalRewardsDisplay());
        tvConversionRate.setText(analytics.getConversionRateDisplay());
        tvAvgValue.setText(analytics.getAvgValueDisplay());
    }

    private void updateDefaultAnalytics() {
        tvTotalReferrals.setText("0");
        tvSuccessful.setText("0");
        tvPending.setText("0");
        tvFailed.setText("0");
        tvTotalRewards.setText("₹0.00");
        tvConversionRate.setText("0.0%");
        tvAvgValue.setText("₹0.00");
    }

    private void loadTierInfo() {
        supabaseApi.getReferralTiers(Constants.SUPABASE_ANON_KEY, getAuthHeader(), "tier_level.asc")
            .enqueue(new Callback<List<ReferralTier>>() {
                @Override
                public void onResponse(Call<List<ReferralTier>> call, Response<List<ReferralTier>> response) {
                    if (response.isSuccessful() && response.body() != null) {
                        updateTierUI(response.body());
                    }
                }

                @Override
                public void onFailure(Call<List<ReferralTier>> call, Throwable t) {
                    // Fail silently
                }
            });
    }

    private void updateTierUI(List<ReferralTier> tiers) {
        // Find current tier
        ReferralTier currentTierObj = null;
        ReferralTier nextTierObj = null;
        
        for (ReferralTier tier : tiers) {
            if (tier.tierLevel == currentTier) {
                currentTierObj = tier;
            }
            if (tier.tierLevel == currentTier + 1) {
                nextTierObj = tier;
            }
        }

        if (currentTierObj != null) {
            String tierEmoji = getTierEmoji(currentTier);
            tvCurrentTier.setText(tierEmoji + " " + currentTierObj.tierName + " Tier");
            tvTierBenefits.setText(currentTierObj.benefits);
            
            // Calculate progress to next tier
            if (nextTierObj != null) {
                int needed = nextTierObj.minReferrals - referralCount;
                tvProgressToNext.setText("Progress to " + nextTierObj.tierName + ": " + 
                    referralCount + " / " + nextTierObj.minReferrals + 
                    " (" + needed + " more needed)");
                
                int progress = (int) ((referralCount * 100.0) / nextTierObj.minReferrals);
                progressTier.setProgress(Math.min(progress, 100));
            } else {
                // Max tier reached
                tvProgressToNext.setText("🎉 Maximum tier reached! Keep earning!");
                progressTier.setProgress(100);
            }
        }
    }

    private String getTierEmoji(int tier) {
        switch (tier) {
            case 3: return "👑";
            case 2: return "⭐";
            case 1:
            default: return "🏅";
        }
    }
}
