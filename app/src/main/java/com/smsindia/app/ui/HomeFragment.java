package com.smsindia.app.ui;

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
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
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.widget.ViewPager2;

// --- ADMOB IMPORTS ---
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.OnUserEarnedRewardListener;
import com.google.android.gms.ads.rewarded.RewardItem;
import com.google.android.gms.ads.rewarded.RewardedAd;
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback;

import com.google.gson.internal.LinkedTreeMap;
import com.smsindia.app.R;
import com.smsindia.app.service.AppConfigModel;
import com.smsindia.app.service.SupabaseApi;
import com.smsindia.app.service.UserModel;
import com.smsindia.app.service.WhatsAppApi; // Ensure you created this file!

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class HomeFragment extends Fragment {

    private static final String SUPABASE_URL = "https://appfwrpynfxfpcvpavso.supabase.co";
    private static final String SUPABASE_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImFwcGZ3cnB5bmZ4ZnBjdnBhdnNvIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NjIwOTQ2MTQsImV4cCI6MjA3NzY3MDYxNH0.Z-BMBjME8MVK5MS2KBgcCDgR7kXvDEjtcHrVfIUvwZY";

    // ✅ REAL ADMOB ID
    private static final String AD_UNIT_ID = "ca-app-pub-9828067292234660/7863146672";

    private TextView tvBalanceAmount, tvUserMobile;
    private ViewPager2 bannerViewPager;
    
    // AdMob Views
    private Button btnWatchAd;
    private TextView tvAdStatus;
    private ProgressBar pbAdProgress;
    private RewardedAd mRewardedAd;
    
    private SupabaseApi supabaseApi;
    private String mobileNumber;
    private String userIdUUID; 

    // Rewards for 10 Days
    private final int[] DAILY_REWARDS = {2, 5, 2, 2, 5, 2, 10, 5, 5, 20};

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_home, container, false);

        // Init Retrofit for Supabase
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(SUPABASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build();
        supabaseApi = retrofit.create(SupabaseApi.class);

        // Initialize Views
        tvBalanceAmount = v.findViewById(R.id.tv_balance_amount);
        tvUserMobile = v.findViewById(R.id.tv_user_mobile);
        bannerViewPager = v.findViewById(R.id.banner_viewpager);
        Button btnHistory = v.findViewById(R.id.btn_history);
        
        // AdMob Views
        btnWatchAd = v.findViewById(R.id.btn_watch_ad);
        tvAdStatus = v.findViewById(R.id.tv_ad_status);
        pbAdProgress = v.findViewById(R.id.pb_ad_progress);
        
        // Cards
        View dailyCheckinCard = v.findViewById(R.id.card_daily_checkin);
        View whatsappCard = v.findViewById(R.id.card_whatsapp_auth);
        View earnMoreCard = v.findViewById(R.id.card_earn_more);
        
        // Get User Info
        SharedPreferences prefs = requireActivity().getSharedPreferences("SMSINDIA_USER", 0);
        mobileNumber = prefs.getString("mobile", "");
        userIdUUID = prefs.getString("userId", "");

        tvUserMobile.setText(mobileNumber);

        setupBannerSlider();

        // --- INIT ADMOB ---
        MobileAds.initialize(getContext(), initializationStatus -> {});
        loadAd(); // Preload Ad
        fetchAdProgress(); // Get current 0/10 status

        // Click Listeners
        dailyCheckinCard.setOnClickListener(view -> showDailyCheckInDialog());
        whatsappCard.setOnClickListener(view -> showWhatsAppLoginDialog());
        earnMoreCard.setOnClickListener(view -> openEarnMoreWebTask());
        
        // AdMob Listener
        if(btnWatchAd != null) {
            btnWatchAd.setOnClickListener(view -> showAd());
        }

        btnHistory.setOnClickListener(view -> {
             startActivity(new Intent(getActivity(), WithdrawalHistoryActivity.class));
        });
        
        return v;
    }

    @Override
    public void onResume() {
        super.onResume();
        fetchUserBalance(); // Refresh balance when returning
    }

    // ==========================================
    // 1. EARN MORE LOGIC (AUTO-LOGIN URL)
    // ==========================================
    private void openEarnMoreWebTask() {
        supabaseApi.getConfig(SUPABASE_KEY, "Bearer " + SUPABASE_KEY, "eq.earn_more_config")
            .enqueue(new Callback<List<AppConfigModel>>() {
                @Override
                public void onResponse(Call<List<AppConfigModel>> call, Response<List<AppConfigModel>> response) {
                    String targetUrl = "";
                    boolean isActive = false;

                    if (response.isSuccessful() && response.body() != null && !response.body().isEmpty()) {
                        Object val = response.body().get(0).value;
                        if (val instanceof LinkedTreeMap) {
                            LinkedTreeMap<?,?> map = (LinkedTreeMap<?,?>) val;
                            if (map.containsKey("url")) targetUrl = (String) map.get("url");
                            if (map.containsKey("is_active")) isActive = (boolean) map.get("is_active");
                        }
                    }

                    if (isActive && targetUrl.length() > 0) {
                        String finalUrl;
                        if (targetUrl.contains("?")) {
                            finalUrl = targetUrl + "&phone=" + mobileNumber;
                        } else {
                            finalUrl = targetUrl + "?phone=" + mobileNumber;
                        }
                        
                        Intent intent = new Intent(getActivity(), WebTaskActivity.class);
                        intent.putExtra("TARGET_URL", finalUrl);
                        startActivity(intent);
                    } else {
                        Toast.makeText(getContext(), "Tasks currently unavailable.", Toast.LENGTH_SHORT).show();
                    }
                }

                @Override
                public void onFailure(Call<List<AppConfigModel>> call, Throwable t) {
                    Toast.makeText(getContext(), "Network Error", Toast.LENGTH_SHORT).show();
                }
            });
    }

    // ==========================================
    // 2. ADMOB LOGIC (WATCH & EARN)
    // ==========================================
    private void loadAd() {
        if(btnWatchAd == null) return;
        btnWatchAd.setText("LOADING...");
        btnWatchAd.setEnabled(false);
        
        AdRequest adRequest = new AdRequest.Builder().build();
        RewardedAd.load(getContext(), AD_UNIT_ID, adRequest,
            new RewardedAdLoadCallback() {
                @Override
                public void onAdFailedToLoad(@NonNull LoadAdError loadAdError) {
                    mRewardedAd = null;
                    if(getContext() != null) {
                        btnWatchAd.setText("RETRY");
                        btnWatchAd.setEnabled(true);
                    }
                }

                @Override
                public void onAdLoaded(@NonNull RewardedAd rewardedAd) {
                    mRewardedAd = rewardedAd;
                    if(getContext() != null) {
                        btnWatchAd.setText("WATCH");
                        btnWatchAd.setEnabled(true);
                    }
                }
            });
    }

    private void showAd() {
        if (mRewardedAd != null) {
            mRewardedAd.show(getActivity(), new OnUserEarnedRewardListener() {
                @Override
                public void onUserEarnedReward(@NonNull RewardItem rewardItem) {
                    updateProgressOnServer();
                }
            });
            mRewardedAd = null; // Clear used ad
            loadAd(); // Load next one
        } else {
            Toast.makeText(getContext(), "Ad not ready yet. Please wait.", Toast.LENGTH_SHORT).show();
            loadAd();
        }
    }

    private void fetchAdProgress() {
        if(mobileNumber.isEmpty()) return;
        supabaseApi.getUser(SUPABASE_KEY, "Bearer " + SUPABASE_KEY, "eq." + mobileNumber)
            .enqueue(new Callback<List<UserModel>>() {
                @Override
                public void onResponse(Call<List<UserModel>> call, Response<List<UserModel>> response) {
                    if (response.isSuccessful() && response.body() != null && !response.body().isEmpty()) {
                        UserModel user = response.body().get(0);
                        if(tvAdStatus != null && pbAdProgress != null) {
                            try {
                                int prog = user.adProgress; 
                                pbAdProgress.setProgress(prog);
                                tvAdStatus.setText("Progress: " + prog + "/10");
                            } catch (Exception e) {}
                        }
                    }
                }
                @Override public void onFailure(Call<List<UserModel>> call, Throwable t) {}
            });
    }

    private void updateProgressOnServer() {
        if (userIdUUID.isEmpty()) return;

        Map<String, Object> body = new HashMap<>();
        body.put("p_user_id", userIdUUID);

        if(btnWatchAd != null) btnWatchAd.setText("SAVING...");
        
        supabaseApi.watchAdReward(SUPABASE_KEY, "Bearer " + SUPABASE_KEY, body)
            .enqueue(new Callback<LinkedTreeMap<String, Object>>() {
                @Override
                public void onResponse(Call<LinkedTreeMap<String, Object>> call, Response<LinkedTreeMap<String, Object>> response) {
                    if (response.isSuccessful() && response.body() != null) {
                        LinkedTreeMap<String, Object> data = response.body();
                        double progressD = (double) data.get("progress");
                        int progress = (int) progressD;
                        String msg = (String) data.get("message");
                        
                        if(pbAdProgress != null) pbAdProgress.setProgress(progress);
                        if(tvAdStatus != null) tvAdStatus.setText("Progress: " + progress + "/10");
                        
                        Toast.makeText(getContext(), msg, Toast.LENGTH_LONG).show();
                    }
                    if(btnWatchAd != null) btnWatchAd.setText("WATCH");
                }

                @Override
                public void onFailure(Call<LinkedTreeMap<String, Object>> call, Throwable t) {
                    Toast.makeText(getContext(), "Network Error. Progress not saved.", Toast.LENGTH_SHORT).show();
                    if(btnWatchAd != null) btnWatchAd.setText("WATCH");
                }
            });
    }

    // ==========================================
    // 3. WHATSAPP LOGIC (CONNECT TO RENDER)
    // ==========================================

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

            // Prepare Format: 91 + Number
            String fullNumber = "91" + number;

            btnGetCode.setText("Connecting...");
            btnGetCode.setEnabled(false);

            // 1. Get Server URL from Supabase
            supabaseApi.getConfig(SUPABASE_KEY, "Bearer " + SUPABASE_KEY, "eq.whatsapp_config")
                .enqueue(new Callback<List<AppConfigModel>>() {
                    @Override
                    public void onResponse(Call<List<AppConfigModel>> call, Response<List<AppConfigModel>> response) {
                        String waServerUrl = "";
                        boolean isActive = false;

                        if (response.isSuccessful() && response.body() != null && !response.body().isEmpty()) {
                            Object val = response.body().get(0).value;
                            if (val instanceof LinkedTreeMap) {
                                LinkedTreeMap<?,?> map = (LinkedTreeMap<?,?>) val;
                                if (map.containsKey("base_url")) waServerUrl = (String) map.get("base_url");
                                if (map.containsKey("is_active")) isActive = (boolean) map.get("is_active");
                            }
                        }

                        if (!isActive || waServerUrl.isEmpty()) {
                            Toast.makeText(getContext(), "Server Maintenance. Try later.", Toast.LENGTH_SHORT).show();
                            btnGetCode.setEnabled(true);
                            btnGetCode.setText("GET PAIRING CODE");
                            return;
                        }

                        // 2. Connect to Render Server
                        connectToRenderServer(waServerUrl, fullNumber, btnGetCode, dialog);
                    }

                    @Override
                    public void onFailure(Call<List<AppConfigModel>> call, Throwable t) {
                        Toast.makeText(getContext(), "Check Internet", Toast.LENGTH_SHORT).show();
                        btnGetCode.setEnabled(true);
                        btnGetCode.setText("GET PAIRING CODE");
                    }
                });
        });

        btnCancel.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    private void connectToRenderServer(String baseUrl, String phone, Button btn, AlertDialog dialog) {
        // Create temporary Retrofit client for WhatsApp Server
        Retrofit waRetrofit = new Retrofit.Builder()
                .baseUrl(baseUrl) // The URL from Supabase
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        WhatsAppApi waApi = waRetrofit.create(WhatsAppApi.class);

        Map<String, Object> body = new HashMap<>();
        body.put("phone", phone);

        waApi.login(body).enqueue(new Callback<LinkedTreeMap<String, Object>>() {
            @Override
            public void onResponse(Call<LinkedTreeMap<String, Object>> call, Response<LinkedTreeMap<String, Object>> response) {
                btn.setEnabled(true);
                btn.setText("GET PAIRING CODE");

                if (response.isSuccessful() && response.body() != null) {
                    LinkedTreeMap<String, Object> res = response.body();
                    
                    if(res.containsKey("code")) {
                        String pairCode = (String) res.get("code");
                        showPairingCodeSuccess(pairCode, dialog);
                    } else {
                        Toast.makeText(getContext(), "Connected, but no code returned.", Toast.LENGTH_LONG).show();
                    }
                } else {
                    Toast.makeText(getContext(), "Login Failed: " + response.code(), Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<LinkedTreeMap<String, Object>> call, Throwable t) {
                btn.setEnabled(true);
                btn.setText("GET PAIRING CODE");
                Toast.makeText(getContext(), "Server Timeout (Render is waking up... try again)", Toast.LENGTH_LONG).show();
            }
        });
    }

    private void showPairingCodeSuccess(String code, AlertDialog oldDialog) {
        if(oldDialog != null) oldDialog.dismiss();

        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setTitle("Your Pairing Code");
        
        // Format code: ABCD-1234
        String formattedCode = code.replace(":", "-"); 

        builder.setMessage("1. Open WhatsApp on another phone\n2. Go to Linked Devices > Link a Device\n3. Tap 'Link with phone number'\n4. Enter this code:\n\n" + formattedCode);
        
        builder.setPositiveButton("COPY CODE", (d, w) -> {
            ClipboardManager clipboard = (ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText("Pair Code", formattedCode);
            clipboard.setPrimaryClip(clip);
            Toast.makeText(getContext(), "Copied!", Toast.LENGTH_SHORT).show();
        });
        
        builder.setNegativeButton("DONE", null);
        builder.show();
    }

    // ==========================================
    // 4. DAILY CHECK-IN
    // ==========================================

    private void showDailyCheckInDialog() {
        if (mobileNumber.isEmpty()) return;

        supabaseApi.getUser(SUPABASE_KEY, "Bearer " + SUPABASE_KEY, "eq." + mobileNumber)
            .enqueue(new Callback<List<UserModel>>() {
                @Override
                public void onResponse(Call<List<UserModel>> call, Response<List<UserModel>> response) {
                    if (response.isSuccessful() && response.body() != null && !response.body().isEmpty()) {
                        UserModel user = response.body().get(0);
                        String lastDate = user.lastCheckinDate != null ? user.lastCheckinDate : "";
                        int currentStreak = user.streak;
                        String todayDate = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());

                        int streakToDisplay;
                        boolean canClaim = true;

                        if (todayDate.equals(lastDate)) {
                            streakToDisplay = currentStreak; 
                            canClaim = false;
                        } else {
                            streakToDisplay = currentStreak + 1;
                            if(streakToDisplay > 10) streakToDisplay = 1; 
                        }
                        
                        launchDialogUI(streakToDisplay, canClaim, todayDate);
                    }
                }
                @Override public void onFailure(Call<List<UserModel>> call, Throwable t) {}
            });
    }

    private void launchDialogUI(int currentDay, boolean canClaim, String todayDate) {
        if(getContext() == null) return;
        
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
            
            btnClaim.setOnClickListener(v -> {
                btnClaim.setEnabled(false);
                btnClaim.setText("Processing...");
                claimReward(currentDay, rewardAmount, todayDate, dialog);
            });
        }

        btnClose.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    private void claimReward(int day, int amount, String todayDate, AlertDialog dialog) {
        if (userIdUUID.isEmpty()) return;

        Map<String, Object> params = new HashMap<>();
        params.put("p_user_id", userIdUUID);
        params.put("p_amount", amount);
        params.put("p_streak", day);
        params.put("p_date", todayDate);

        supabaseApi.claimCheckIn(SUPABASE_KEY, "Bearer " + SUPABASE_KEY, params)
            .enqueue(new Callback<Void>() {
                @Override
                public void onResponse(Call<Void> call, Response<Void> response) {
                    if (response.isSuccessful()) {
                        Toast.makeText(getContext(), "Claimed ₹" + amount + " successfully!", Toast.LENGTH_SHORT).show();
                        if(dialog.isShowing()) dialog.dismiss();
                        fetchUserBalance(); // Refresh UI
                    } else {
                        Toast.makeText(getContext(), "Claim Failed", Toast.LENGTH_SHORT).show();
                        if(dialog.isShowing()) dialog.dismiss();
                    }
                }

                @Override
                public void onFailure(Call<Void> call, Throwable t) {
                    Toast.makeText(getContext(), "Network Error", Toast.LENGTH_SHORT).show();
                    if(dialog.isShowing()) dialog.dismiss();
                }
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
        if (mobileNumber.isEmpty()) return;
        
        supabaseApi.getUser(SUPABASE_KEY, "Bearer " + SUPABASE_KEY, "eq." + mobileNumber)
            .enqueue(new Callback<List<UserModel>>() {
                @Override
                public void onResponse(Call<List<UserModel>> call, Response<List<UserModel>> response) {
                    if (response.isSuccessful() && response.body() != null && !response.body().isEmpty()) {
                        UserModel user = response.body().get(0);
                        tvBalanceAmount.setText(String.format("₹ %.2f", user.getBalance()));
                    }
                }
                @Override public void onFailure(Call<List<UserModel>> call, Throwable t) {}
            });
    }
}
