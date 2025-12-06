package com.smsindia.app.ui;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.fragment.app.Fragment;
import androidx.viewpager2.widget.ViewPager2;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.WriteBatch;
import com.smsindia.app.R;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class HomeFragment extends Fragment {

    private TextView tvBalanceAmount, tvUserMobile;
    private ViewPager2 bannerViewPager;
    private FirebaseFirestore db;
    private String uid;
    
    // Rewards for 10 Days
    private final int[] DAILY_REWARDS = {2, 5, 2, 2, 5, 2, 10, 5, 5, 20};

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_home, container, false);

        // Initialize Views
        tvBalanceAmount = v.findViewById(R.id.tv_balance_amount);
        tvUserMobile = v.findViewById(R.id.tv_user_mobile);
        bannerViewPager = v.findViewById(R.id.banner_viewpager);
        Button btnHistory = v.findViewById(R.id.btn_history);
        View dailyCheckinCard = v.findViewById(R.id.card_daily_checkin);

        // Initialize Firebase
        db = FirebaseFirestore.getInstance();
        SharedPreferences prefs = requireActivity().getSharedPreferences("SMSINDIA_USER", 0);
        uid = prefs.getString("mobile", ""); 

        // Setup
        setupBannerSlider();
        fetchUserBalance();

        // Click Listeners
        dailyCheckinCard.setOnClickListener(view -> showDailyCheckInDialog());
        
        btnHistory.setOnClickListener(view -> {
            // Check if activity exists before launching
            try {
                Intent intent = new Intent(getActivity(), Class.forName("com.smsindia.app.ui.HistoryActivity"));
                startActivity(intent);
            } catch (ClassNotFoundException e) {
                 Toast.makeText(getContext(), "History Page Coming Soon", Toast.LENGTH_SHORT).show();
            }
        });
        
        return v;
    }

    private void showDailyCheckInDialog() {
        if (uid == null || uid.isEmpty()) return;
        
        db.collection("users").document(uid).get()
            .addOnSuccessListener(documentSnapshot -> {
                if (!documentSnapshot.exists()) return;

                String lastDate = documentSnapshot.getString("last_checkin_date");
                Long streakLong = documentSnapshot.getLong("streak");
                int currentStreak = (streakLong != null) ? streakLong.intValue() : 0;

                String todayDate = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());

                // Logic to calculate streak
                int streakToDisplay = 1;
                boolean canClaim = true;
                
                if (todayDate.equals(lastDate)) {
                    // Already claimed today
                    streakToDisplay = currentStreak;
                    canClaim = false;
                } else {
                    streakToDisplay = currentStreak + 1; 
                    if(streakToDisplay > 10) streakToDisplay = 1; // Reset after 10 days
                }

                launchDialogUI(streakToDisplay, canClaim, todayDate);
            });
    }

    private void launchDialogUI(int currentDay, boolean canClaim, String todayDate) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        View view = LayoutInflater.from(getContext()).inflate(R.layout.dialog_daily_checkin, null);
        builder.setView(view);
        AlertDialog dialog = builder.create();
        if(dialog.getWindow() != null) dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));

        TextView tvStreak = view.findViewById(R.id.tv_streak_status);
        Button btnClaim = view.findViewById(R.id.btn_claim_reward);
        View btnClose = view.findViewById(R.id.btn_close_dialog); 

        tvStreak.setText("Current Streak: Day " + currentDay);

        // --- UPDATE GRID VISUALS ---
        int[] viewIds = {
            R.id.day1, R.id.day2, R.id.day3, R.id.day4, R.id.day5,
            R.id.day6, R.id.day7, R.id.day8, R.id.day9, R.id.day10
        };

        for (int i = 0; i < viewIds.length; i++) {
            int dayNum = i + 1;
            View dayView = view.findViewById(viewIds[i]);
            
            TextView lblDay = dayView.findViewById(R.id.lbl_day);
            TextView lblAmount = dayView.findViewById(R.id.lbl_amount);
            
            View bgCircle = (View) dayView.findViewById(R.id.lbl_amount).getParent(); 
            
            lblDay.setText("Day " + dayNum);
            lblAmount.setText("₹" + DAILY_REWARDS[i]);

            // Styling Logic
            if (dayNum < currentDay) {
                // PAST DAYS
                dayView.setAlpha(0.5f); 
            } else if (dayNum == currentDay) {
                // TODAY (Active) - Changed from Purple to Green
                bgCircle.requestLayout(); 
                lblDay.setTextColor(Color.parseColor("#1B5E20")); // Dark Green
                lblDay.setTypeface(null, android.graphics.Typeface.BOLD);
            }
        }
        // ---------------------------

        if (!canClaim) {
            btnClaim.setText("COME BACK TOMORROW");
            btnClaim.setEnabled(false);
            // Gray is okay for disabled state
            btnClaim.setBackgroundTintList(getContext().getColorStateList(android.R.color.darker_gray));
        } else {
            int rewardAmount = DAILY_REWARDS[currentDay - 1];
            btnClaim.setText("CLAIM ₹" + rewardAmount);
            
            // --- IMPORTANT: Set 3D Gold Background ---
            btnClaim.setBackgroundResource(R.drawable.bg_gold_3d);
            btnClaim.setTextColor(Color.parseColor("#5D4037")); // Brown text
            
            btnClaim.setOnClickListener(v -> {
                claimReward(currentDay, rewardAmount, todayDate, dialog);
            });
        }

        btnClose.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    private void claimReward(int day, int amount, String todayDate, AlertDialog dialog) {
        WriteBatch batch = db.batch();
        DocumentReference userRef = db.collection("users").document(uid);
        DocumentReference historyRef = db.collection("users").document(uid).collection("transactions").document();

        // 1. Update User Balance & Streak
        Map<String, Object> userUpdates = new HashMap<>();
        userUpdates.put("balance", FieldValue.increment(amount));
        userUpdates.put("last_checkin_date", todayDate);
        userUpdates.put("streak", day);
        batch.update(userRef, userUpdates);

        // 2. Add History Record
        Map<String, Object> txData = new HashMap<>();
        txData.put("title", "Daily Check-in (Day " + day + ")");
        txData.put("amount", amount);
        txData.put("type", "CREDIT");
        txData.put("timestamp", FieldValue.serverTimestamp());
        batch.set(historyRef, txData);

        batch.commit().addOnSuccessListener(aVoid -> {
            Toast.makeText(getContext(), "Claimed ₹" + amount + " successfully!", Toast.LENGTH_SHORT).show();
            dialog.dismiss();
        }).addOnFailureListener(e -> {
            Toast.makeText(getContext(), "Failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        });
    }

       private void setupBannerSlider() {
        List<Integer> bannerList = new ArrayList<>();
        // Make sure these images exist in res/drawable!
        bannerList.add(R.drawable.banner_one);   
        bannerList.add(R.drawable.banner_two);
        bannerList.add(R.drawable.banner_three);

        // If you don't have these, you can remove them or the app will crash on start
        // Ensure BannerAdapter exists
        try {
            BannerAdapter adapter = new BannerAdapter(bannerList);
            bannerViewPager.setAdapter(adapter);
        } catch (Exception e) {
            // Handle adapter error
        }
    }

    private void fetchUserBalance() {
        if (uid == null || uid.isEmpty()) return;
        db.collection("users").document(uid).addSnapshotListener((snapshot, e) -> {
            if (e == null && snapshot != null && snapshot.exists()) {
                Double bal = snapshot.getDouble("balance");
                if (bal != null) tvBalanceAmount.setText(String.format("₹ %.2f", bal));
                String name = snapshot.getString("name");
                if(name != null) tvUserMobile.setText(name);
            }
        });
    }
}
