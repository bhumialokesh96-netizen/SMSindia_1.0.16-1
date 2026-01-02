package com.smsindia.app.ui;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.gson.internal.LinkedTreeMap;
import com.smsindia.app.R;
import com.smsindia.app.service.SupabaseApi;
import com.smsindia.app.service.UserModel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class ShareFragment extends Fragment {

    private static final String SUPABASE_URL = "https://appfwrpynfxfpcvpavso.supabase.co";
    private static final String SUPABASE_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImFwcGZ3cnB5bmZ4ZnBjdnBhdnNvIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NjIwOTQ2MTQsImV4cCI6MjA3NzY3MDYxNH0.Z-BMBjME8MVK5MS2KBgcCDgR7kXvDEjtcHrVfIUvwZY";

    private TextView tvTotalRefs, tvEarnings, tvCoins, tvCode;
    private RecyclerView recyclerMilestones;
    
    private SupabaseApi supabaseApi;
    private String mobileNumber;

    // User Stats
    private long userSmsCount = 0;
    private long userReferralCount = 0;
    private long currentCoins = 0;
    private Map<String, Boolean> claimedMilestones = new HashMap<>();
    
    // Adapter
    private MilestoneAdapter adapter;
    private List<Milestone> milestoneList;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_share, container, false);

        // Init Retrofit
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(SUPABASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build();
        supabaseApi = retrofit.create(SupabaseApi.class);

        // Init Views
        tvTotalRefs = v.findViewById(R.id.tv_total_referrals);
        tvEarnings = v.findViewById(R.id.tv_referral_earnings);
        tvCoins = v.findViewById(R.id.tv_total_coins);
        tvCode = v.findViewById(R.id.tv_share_code);
        Button btnShare = v.findViewById(R.id.btn_share_app);
        recyclerMilestones = v.findViewById(R.id.recycler_milestones);

        SharedPreferences prefs = requireActivity().getSharedPreferences("SMSINDIA_USER", 0);
        mobileNumber = prefs.getString("mobile", "");
        
        tvCode.setText(mobileNumber);

        setupMilestoneList();
        
        // --- SHARE LINK ---
        btnShare.setOnClickListener(view -> shareReferralLink());

        return v;
    }

    @Override
    public void onResume() {
        super.onResume();
        fetchUserData();
    }

    // ==========================================
    // HELPER METHOD: GET JWT TOKEN
    // ==========================================
    private String getAuthHeader() {
        SharedPreferences authPrefs = requireActivity().getSharedPreferences("SMS_AUTH", Context.MODE_PRIVATE);
        String token = authPrefs.getString("jwt", null);
        return token != null ? "Bearer " + token : "Bearer " + SUPABASE_KEY;
    }

    private void shareReferralLink() {
        if(mobileNumber == null || mobileNumber.isEmpty()) return;

        String shareUrl = "https://smsindia-web.vercel.app/?ref=" + mobileNumber;
        String message = "🔥 Earn ₹500 Daily! Download SMS India App.\n" +
                         "Use my Referral Link to get a Bonus:\n\n" + 
                         shareUrl;

        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(Intent.EXTRA_TEXT, message);
        startActivity(Intent.createChooser(shareIntent, "Share via"));
    }

    private void setupMilestoneList() {
        milestoneList = new ArrayList<>();
        milestoneList.add(new Milestone("ms_sms_20", "Send first 20 SMS", 20, 1, 0));
        milestoneList.add(new Milestone("ms_sms_100", "Send first 100 SMS", 100, 1, 0));
        milestoneList.add(new Milestone("ms_ref_1", "Invite 1st Friend", 1, 2, 1));
        milestoneList.add(new Milestone("ms_ref_5", "Invite 5 Friends", 5, 5, 1));
        milestoneList.add(new Milestone("ms_sms_500", "Send 500 SMS", 500, 5, 0));

        adapter = new MilestoneAdapter(milestoneList);
        recyclerMilestones.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerMilestones.setAdapter(adapter);
    }

    private void fetchUserData() {
        if (mobileNumber.isEmpty()) return;

        // Use JWT token for authorization
        String authHeader = getAuthHeader();

        supabaseApi.getUser(SUPABASE_KEY, authHeader, "eq." + mobileNumber)
            .enqueue(new Callback<List<UserModel>>() {
                @Override
                public void onResponse(Call<List<UserModel>> call, Response<List<UserModel>> response) {
                    if (response.isSuccessful() && response.body() != null && !response.body().isEmpty()) {
                        UserModel user = response.body().get(0);

                        // 1. Update Stats UI
                        userSmsCount = user.smsCount;
                        userReferralCount = user.referralCount;
                        currentCoins = user.getCoins();
                        
                        tvEarnings.setText("₹0.0"); 
                        tvTotalRefs.setText(String.valueOf(userReferralCount));
                        tvCoins.setText(String.valueOf(currentCoins));

                        // 2. Parse Claimed Milestones (JSONB -> Map)
                        claimedMilestones.clear();
                        if (user.claimedMilestones instanceof LinkedTreeMap) {
                            LinkedTreeMap<?,?> map = (LinkedTreeMap<?,?>) user.claimedMilestones;
                            for (Object key : map.keySet()) {
                                claimedMilestones.put(String.valueOf(key), true);
                            }
                        }

                        adapter.notifyDataSetChanged();
                    }
                }

                @Override
                public void onFailure(Call<List<UserModel>> call, Throwable t) {
                    // Fail silently
                }
            });
    }

    private void claimReward(Milestone m) {
        if (claimedMilestones.containsKey(m.id)) return;

        // Optimistic UI Update
        claimedMilestones.put(m.id, true);
        adapter.notifyDataSetChanged();

        // Prepare Update Data
        Map<String, Object> updateData = new HashMap<>();
        updateData.put("coins", currentCoins + m.reward);
        
        Map<String, Boolean> newMap = new HashMap<>(claimedMilestones);
        updateData.put("claimed_milestones", newMap);

        // Use JWT token for authorization
        String authHeader = getAuthHeader();

        supabaseApi.updateUser(SUPABASE_KEY, authHeader, "eq." + mobileNumber, updateData)
                .enqueue(new Callback<Void>() {
                    @Override
                    public void onResponse(Call<Void> call, Response<Void> response) {
                        if(response.isSuccessful()) {
                            currentCoins += m.reward;
                            tvCoins.setText(String.valueOf(currentCoins));
                            Toast.makeText(getContext(), "Claimed " + m.reward + " Coins!", Toast.LENGTH_SHORT).show();
                        } else {
                            // Revert on failure
                            claimedMilestones.remove(m.id);
                            adapter.notifyDataSetChanged();
                            Toast.makeText(getContext(), "Claim Failed", Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onFailure(Call<Void> call, Throwable t) {
                        claimedMilestones.remove(m.id);
                        adapter.notifyDataSetChanged();
                    }
                });
    }

    // --- INNER CLASS: MODEL ---
    static class Milestone {
        String id;
        String title;
        int target;
        int reward;
        int type; // 0 = SMS, 1 = Referral

        public Milestone(String id, String title, int target, int reward, int type) {
            this.id = id;
            this.title = title;
            this.target = target;
            this.reward = reward;
            this.type = type;
        }
    }

    // --- INNER CLASS: ADAPTER ---
    class MilestoneAdapter extends RecyclerView.Adapter<MilestoneAdapter.ViewHolder> {
        List<Milestone> list;

        public MilestoneAdapter(List<Milestone> list) { this.list = list; }

        @NonNull @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_milestone, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            Milestone m = list.get(position);
            
            holder.title.setText(m.title);
            holder.desc.setText("Reward: " + m.reward + " Spin Coins");

            // Determine Progress
            long current = (m.type == 0) ? userSmsCount : userReferralCount;
            int progress = (int) ((current * 100) / m.target);
            if (progress > 100) progress = 100;
            
            holder.progressBar.setProgress(progress);

            // Determine State
            boolean isClaimed = claimedMilestones.containsKey(m.id);
            boolean isCompleted = current >= m.target;

            if (isClaimed) {
                // STATE 1: ALREADY CLAIMED
                holder.btnClaim.setVisibility(View.GONE);
                holder.tvProgress.setVisibility(View.VISIBLE);
                
                holder.tvProgress.setText("CLAIMED");
                holder.tvProgress.setTextColor(Color.parseColor("#4CAF50"));
                holder.tvProgress.setBackgroundResource(R.drawable.bg_orange_border); 
                
            } else if (isCompleted) {
                // STATE 2: READY TO CLAIM
                holder.tvProgress.setVisibility(View.GONE);
                holder.btnClaim.setVisibility(View.VISIBLE);
                
                holder.btnClaim.setText("CLAIM");
                holder.btnClaim.setOnClickListener(v -> claimReward(m));
                
            } else {
                // STATE 3: IN PROGRESS
                holder.btnClaim.setVisibility(View.GONE);
                holder.tvProgress.setVisibility(View.VISIBLE);
                
                holder.tvProgress.setText(current + " / " + m.target);
                holder.tvProgress.setTextColor(Color.parseColor("#FFC107"));
                holder.tvProgress.setBackgroundResource(R.drawable.bg_orange_border); 
            }
        }

        @Override
        public int getItemCount() { return list.size(); }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView title, desc, tvProgress; 
            Button btnClaim;
            ImageView imgIcon;
            ProgressBar progressBar;

            public ViewHolder(@NonNull View itemView) {
                super(itemView);
                title = itemView.findViewById(R.id.tv_milestone_title);
                desc = itemView.findViewById(R.id.tv_milestone_desc);
                tvProgress = itemView.findViewById(R.id.tv_progress_count); 
                btnClaim = itemView.findViewById(R.id.btn_claim_milestone);
                imgIcon = itemView.findViewById(R.id.img_milestone_icon);
                progressBar = itemView.findViewById(R.id.progress_milestone);
            }
        }
    }
}