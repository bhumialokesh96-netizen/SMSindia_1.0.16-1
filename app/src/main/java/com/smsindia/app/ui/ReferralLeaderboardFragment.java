package com.smsindia.app.ui;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;
import com.smsindia.app.R;
import com.smsindia.app.config.Constants;
import com.smsindia.app.data.api.SupabaseApi;
import com.smsindia.app.data.model.LeaderboardEntry;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class ReferralLeaderboardFragment extends Fragment {

    private RecyclerView recyclerView;
    private ProgressBar progressBar;
    private TextView tvMyRank, tvMyReferrals, tvMyTier;
    private MaterialCardView cardMyPosition;
    
    private SupabaseApi supabaseApi;
    private LeaderboardAdapter adapter;
    private List<LeaderboardEntry> leaderboardList = new ArrayList<>();
    
    private String mobileNumber;
    private String userId;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_referral_leaderboard, container, false);

        // Init Retrofit
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(Constants.SUPABASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build();
        supabaseApi = retrofit.create(SupabaseApi.class);

        // Get user info
        SharedPreferences prefs = requireActivity().getSharedPreferences("SMSINDIA_USER", 0);
        mobileNumber = prefs.getString("mobile", "");
        userId = prefs.getString("user_id", "");

        // Init Views
        recyclerView = v.findViewById(R.id.recycler_leaderboard);
        progressBar = v.findViewById(R.id.progress_leaderboard);
        cardMyPosition = v.findViewById(R.id.card_my_position);
        tvMyRank = v.findViewById(R.id.tv_my_rank);
        tvMyReferrals = v.findViewById(R.id.tv_my_referrals);
        tvMyTier = v.findViewById(R.id.tv_my_tier);

        // Setup RecyclerView
        adapter = new LeaderboardAdapter(leaderboardList);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerView.setAdapter(adapter);

        // Load data
        loadLeaderboard();
        loadMyPosition();

        return v;
    }

    private String getAuthHeader() {
        SharedPreferences authPrefs = requireActivity().getSharedPreferences("SMS_AUTH", Context.MODE_PRIVATE);
        String token = authPrefs.getString("jwt", null);
        return token != null ? "Bearer " + token : "Bearer " + Constants.SUPABASE_ANON_KEY;
    }

    private void loadLeaderboard() {
        progressBar.setVisibility(View.VISIBLE);
        
        Map<String, Object> body = new HashMap<>();
        body.put("limit_count", 50);

        supabaseApi.getTopReferrers(Constants.SUPABASE_ANON_KEY, getAuthHeader(), body)
            .enqueue(new Callback<List<LeaderboardEntry>>() {
                @Override
                public void onResponse(Call<List<LeaderboardEntry>> call, Response<List<LeaderboardEntry>> response) {
                    progressBar.setVisibility(View.GONE);
                    if (response.isSuccessful() && response.body() != null) {
                        leaderboardList.clear();
                        leaderboardList.addAll(response.body());
                        adapter.notifyDataSetChanged();
                    } else {
                        Toast.makeText(getContext(), "Failed to load leaderboard", Toast.LENGTH_SHORT).show();
                    }
                }

                @Override
                public void onFailure(Call<List<LeaderboardEntry>> call, Throwable t) {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(getContext(), "Error: " + t.getMessage(), Toast.LENGTH_SHORT).show();
                }
            });
    }

    private void loadMyPosition() {
        Map<String, Object> body = new HashMap<>();
        body.put("user_phone", mobileNumber);

        supabaseApi.getUserLeaderboardPosition(Constants.SUPABASE_ANON_KEY, getAuthHeader(), body)
            .enqueue(new Callback<List<Map<String, Object>>>() {
                @Override
                public void onResponse(Call<List<Map<String, Object>>> call, Response<List<Map<String, Object>>> response) {
                    if (response.isSuccessful() && response.body() != null && !response.body().isEmpty()) {
                        Map<String, Object> position = response.body().get(0);
                        updateMyPosition(position);
                    } else {
                        cardMyPosition.setVisibility(View.GONE);
                    }
                }

                @Override
                public void onFailure(Call<List<Map<String, Object>>> call, Throwable t) {
                    cardMyPosition.setVisibility(View.GONE);
                }
            });
    }

    private void updateMyPosition(Map<String, Object> position) {
        try {
            long rank = Math.round((Double) position.get("user_rank"));
            int referrals = ((Double) position.get("user_referrals")).intValue();
            String tierName = (String) position.get("user_tier_name");

            tvMyRank.setText(String.valueOf(rank));
            tvMyReferrals.setText(String.valueOf(referrals));
            tvMyTier.setText(tierName != null ? tierName : "Bronze");
            
            cardMyPosition.setVisibility(View.VISIBLE);
        } catch (Exception e) {
            cardMyPosition.setVisibility(View.GONE);
        }
    }

    // Adapter
    class LeaderboardAdapter extends RecyclerView.Adapter<LeaderboardAdapter.ViewHolder> {
        List<LeaderboardEntry> list;

        public LeaderboardAdapter(List<LeaderboardEntry> list) {
            this.list = list;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_leaderboard, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            LeaderboardEntry entry = list.get(position);
            
            holder.tvRank.setText(entry.getRankDisplay());
            holder.tvPhone.setText(entry.getMaskedPhone());
            holder.tvReferrals.setText(entry.referralCount + " referrals");
            holder.tvTier.setText(entry.getTierBadge() + " " + (entry.tierName != null ? entry.tierName : "Bronze"));
            
            // Determine if current user
            boolean isCurrentUser = entry.phone != null && entry.phone.equals(mobileNumber);
            
            // Apply background styling
            if (isCurrentUser) {
                // Current user - always use highlight style
                holder.itemView.setBackgroundResource(R.drawable.bg_orange_border);
            } else if (entry.rank <= 3) {
                // Top 3 (but not current user) - use gold gradient
                holder.itemView.setBackgroundResource(R.drawable.bg_gradient_gold);
            } else {
                // Others - default white background
                holder.itemView.setBackgroundColor(
                    ContextCompat.getColor(holder.itemView.getContext(), R.color.white_100)
                );
            }
        }

        @Override
        public int getItemCount() {
            return list.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvRank, tvPhone, tvReferrals, tvTier;

            public ViewHolder(@NonNull View itemView) {
                super(itemView);
                tvRank = itemView.findViewById(R.id.tv_leaderboard_rank);
                tvPhone = itemView.findViewById(R.id.tv_leaderboard_phone);
                tvReferrals = itemView.findViewById(R.id.tv_leaderboard_referrals);
                tvTier = itemView.findViewById(R.id.tv_leaderboard_tier);
            }
        }
    }
}
