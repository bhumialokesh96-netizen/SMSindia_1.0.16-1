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
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.fragment.app.Fragment;
import androidx.viewpager2.widget.ViewPager2;

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
        
        // ✅ NEW: Find the WhatsApp Card
        View whatsappCard = v.findViewById(R.id.card_whatsapp_auth);

        // Initialize Firebase
        db = FirebaseFirestore.getInstance();
        SharedPreferences prefs = requireActivity().getSharedPreferences("SMSINDIA_USER", 0);
        uid = prefs.getString("mobile", ""); 

        setupBannerSlider();
        fetchUserBalance();

        // Click Listeners
        dailyCheckinCard.setOnClickListener(view -> showDailyCheckInDialog());
        
        // ✅ NEW: Add Click Listener for WhatsApp
        whatsappCard.setOnClickListener(view -> showWhatsAppLoginDialog());
        
        btnHistory.setOnClickListener(view -> {
            try {
                Intent intent = new Intent(getActivity(), Class.forName("com.smsindia.app.ui.HistoryActivity"));
                startActivity(intent);
            } catch (ClassNotFoundException e) {
                 Toast.makeText(getContext(), "History Page Coming Soon", Toast.LENGTH_SHORT).show();
            }
        });
        
        return v;
    }

    // ✅ NEW: WhatsApp Logic (Controlled by Admin Panel)
    private void showWhatsAppLoginDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        View view = LayoutInflater.from(getContext()).inflate(R.layout.dialog_whatsapp_login, null);
        builder.setView(view);
        AlertDialog dialog = builder.create();
        if(dialog.getWindow() != null) dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));

        EditText etNumber = view.findViewById(R.id.et_wa_number);
        Button btnGetCode = view.findViewById(R.id.btn_get_code);
        View btnCancel = view.findViewById(R.id.btn_cancel_wa);

        btnGetCode.setOnClickListener(v -> {
            String number = etNumber.getText().toString().trim();
            if (number.length() != 10) {
                etNumber.setError("Enter valid 10-digit number");
                return;
            }

            btnGetCode.setText("Checking Server...");
            btnGetCode.setEnabled(false);

            // 1. CHECK FIREBASE ADMIN SETTINGS
            db.collection("app_settings").document("whatsapp_config").get()
                .addOnSuccessListener(documentSnapshot -> {
                    boolean isActive = false;
                    String msg = "Server Update: WhatsApp Pairing is coming soon!";
                    
                    if (documentSnapshot.exists()) {
                        if(documentSnapshot.contains("is_active")) 
                            isActive = Boolean.TRUE.equals(documentSnapshot.getBoolean("is_active"));
                        if(documentSnapshot.contains("maintenance_msg")) 
                            msg = documentSnapshot.getString("maintenance_msg");
                    }

                    if (isActive) {
                        Toast.makeText(getContext(), "Connecting to WhatsApp Server...", Toast.LENGTH_SHORT).show();
                        dialog.dismiss();
                    } else {
                        Toast.makeText(getContext(), msg, Toast.LENGTH_LONG).show();
                        dialog.dismiss();
                    }
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(getContext(), "Network Error", Toast.LENGTH_SHORT).show();
                    btnGetCode.setEnabled(true);
                    btnGetCode.setText("GET PAIRING CODE");
                });
        });

        btnCancel.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
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

                int streakToDisplay = 1;
                boolean canClaim = true;
                
                if (todayDate.equals(lastDate)) {
                    streakToDisplay = currentStreak;
                    canClaim = false;
                } else {
                    streakToDisplay = currentStreak + 1; 
                    if(streakToDisplay > 10) streakToDisplay = 1; 
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

        int[] viewIds = {R.id.day1, R.id.day2, R.id.day3, R.id.day4, R.id.day5, R.id.day6, R.id.day7, R.id.day8, R.id.day9, R.id.day10};

        for (int i = 0; i < viewIds.length; i++) {
            int dayNum = i + 1;
            View dayView = view.findViewById(viewIds[i]);
            TextView lblDay = dayView.findViewById(R.id.lbl_day);
            TextView lblAmount = dayView.findViewById(R.id.lbl_amount);
            View bgCircle = (View) dayView.findViewById(R.id.lbl_amount).getParent(); 
            
            lblDay.setText("Day " + dayNum);
            lblAmount.setText("₹" + DAILY_REWARDS[i]);

            if (dayNum < currentDay) {
                dayView.setAlpha(0.5f); 
            } else if (dayNum == currentDay) {
                bgCircle.requestLayout(); 
                lblDay.setTextColor(Color.parseColor("#1B5E20")); 
                lblDay.setTypeface(null, android.graphics.Typeface.BOLD);
            }
        }

        if (!canClaim) {
            btnClaim.setText("COME BACK TOMORROW");
            btnClaim.setEnabled(false);
            btnClaim.setBackgroundTintList(getContext().getColorStateList(android.R.color.darker_gray));
        } else {
            int rewardAmount = DAILY_REWARDS[currentDay - 1];
            btnClaim.setText("CLAIM ₹" + rewardAmount);
            btnClaim.setBackgroundResource(R.drawable.bg_gold_3d);
            btnClaim.setTextColor(Color.parseColor("#5D4037")); 
            btnClaim.setOnClickListener(v -> claimReward(currentDay, rewardAmount, todayDate, dialog));
        }

        btnClose.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

        private void claimReward(int day, int amount, String todayDate, AlertDialog dialog) {
        if (uid == null) return;
        
        final DocumentReference userRef = db.collection("users").document(uid);
        final DocumentReference historyRef = db.collection("users").document(uid).collection("transactions").document();

        db.runTransaction((Transaction.Function<Void>) transaction -> {
            // 1. READ: Get the latest data from server securely
            DocumentSnapshot snapshot = transaction.get(userRef);

            // 2. SECURITY CHECK: Did they already claim today?
            String serverLastDate = snapshot.getString("last_checkin_date");
            if (serverLastDate != null && serverLastDate.equals(todayDate)) {
                // STOP RIGHT HERE! Abort transaction.
                throw new FirebaseFirestoreException("Already Claimed Today!", FirebaseFirestoreException.Code.ABORTED);
            }

            // 3. CALCULATE: Get current balance safely
            Double currentBalance = snapshot.getDouble("balance");
            if (currentBalance == null) currentBalance = 0.0;
            double newBalance = currentBalance + amount;

            // 4. WRITE: Update everything at once
            transaction.update(userRef, "balance", newBalance);
            transaction.update(userRef, "last_checkin_date", todayDate);
            transaction.update(userRef, "streak", day);

            // 5. HISTORY: Create the record
            Map<String, Object> txData = new HashMap<>();
            txData.put("title", "Daily Check-in (Day " + day + ")");
            txData.put("amount", amount);
            txData.put("type", "CREDIT");
            txData.put("timestamp", FieldValue.serverTimestamp());
            transaction.set(historyRef, txData);

            return null;
        }).addOnSuccessListener(aVoid -> {
            // ✅ Success
            Toast.makeText(getContext(), "Claimed ₹" + amount + " successfully!", Toast.LENGTH_SHORT).show();
            if (dialog != null && dialog.isShowing()) dialog.dismiss();
        }).addOnFailureListener(e -> {
            // ❌ Failed (or Double Claim Attempt)
            if (e.getMessage() != null && e.getMessage().contains("Already Claimed")) {
                Toast.makeText(getContext(), "Nice try! You already claimed today.", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(getContext(), "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
            if (dialog != null && dialog.isShowing()) dialog.dismiss();
        });
    }


    private void setupBannerSlider() {
        List<Integer> bannerList = new ArrayList<>();
        bannerList.add(R.drawable.banner_one);   
        bannerList.add(R.drawable.banner_two);
        bannerList.add(R.drawable.banner_three);
        try {
            BannerAdapter adapter = new BannerAdapter(bannerList);
            bannerViewPager.setAdapter(adapter);
        } catch (Exception e) {}
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
