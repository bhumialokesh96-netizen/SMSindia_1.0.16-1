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
import com.smsindia.app.service.WhatsAppApi;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class HomeFragment extends Fragment {

    private static final String SUPABASE_URL = "https://appfwrpynfxfpcvpavso.supabase.co";
    private static final String SUPABASE_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImFwcGZ3cnB5bmZ4ZnBjdnBhdnNvIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NjIwOTQ2MTQsImV4cCI6MjA3NzY3MDYxNH0.Z-BMBjME8MVK5MS2KBgcCDgR7kXvDEjtcHrVfIUvwZY";

    // --- ADMOB VARIABLES ---
    private List<String> adUnitList = new ArrayList<>();
    private int currentAdIndex = 0;
    private static final String FALLBACK_AD_ID = "ca-app-pub-3940256099942544/5224354917";

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

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_home, container, false);

        // Init Retrofit
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
        fetchAdConfiguration(); 
        fetchAdProgress(); 

        // Click Listeners
        dailyCheckinCard.setOnClickListener(view -> showDailyCheckInDialog());
        whatsappCard.setOnClickListener(view -> showWhatsAppLoginDialog());
        earnMoreCard.setOnClickListener(view -> openEarnMoreWebTask());
        
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
        fetchUserBalance(); 
    }

    // ==========================================
    // 1. EARN MORE LOGIC
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
                        String finalUrl = targetUrl.contains("?") ? targetUrl + "&phone=" + mobileNumber : targetUrl + "?phone=" + mobileNumber;
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
    // 2. ADMOB LOGIC
    // ==========================================
    private void fetchAdConfiguration() {
        if(btnWatchAd != null) {
            btnWatchAd.setEnabled(false);
            btnWatchAd.setText("INIT ADS...");
        }

        supabaseApi.getConfig(SUPABASE_KEY, "Bearer " + SUPABASE_KEY, "eq.admob_config")
            .enqueue(new Callback<List<AppConfigModel>>() {
                @Override
                public void onResponse(Call<List<AppConfigModel>> call, Response<List<AppConfigModel>> response) {
                    boolean foundIds = false;
                    if (response.isSuccessful() && response.body() != null && !response.body().isEmpty()) {
                        Object val = response.body().get(0).value;
                        if (val instanceof LinkedTreeMap) {
                            LinkedTreeMap<?,?> map = (LinkedTreeMap<?,?>) val;
                            if (map.containsKey("rewarded_ids")) {
                                Object listObj = map.get("rewarded_ids");
                                if (listObj instanceof List) {
                                    adUnitList.clear();
                                    adUnitList.addAll((List<String>) listObj);
                                    foundIds = true;
                                }
                            }
                        }
                    }
                    if (foundIds && !adUnitList.isEmpty()) {
                        loadAd(); 
                    } else {
                        useFallbackAds(); 
                    }
                }
                @Override
                public void onFailure(Call<List<AppConfigModel>> call, Throwable t) {
                    useFallbackAds();
                }
            });
    }

    private void useFallbackAds() {
        adUnitList.clear();
        adUnitList.add(FALLBACK_AD_ID);
        loadAd();
    }

    private void loadAd() {
        if (adUnitList.isEmpty() || getContext() == null) return;

        if(btnWatchAd != null) {
            btnWatchAd.setText("LOADING...");
            btnWatchAd.setEnabled(false);
        }
        
        String currentId = adUnitList.get(currentAdIndex);
        AdRequest adRequest = new AdRequest.Builder().build();
        RewardedAd.load(getContext(), currentId, adRequest,
            new RewardedAdLoadCallback() {
                @Override
                public void onAdFailedToLoad(@NonNull LoadAdError loadAdError) {
                    mRewardedAd = null;
                    if (currentAdIndex < adUnitList.size() - 1) {
                        currentAdIndex++; 
                        loadAd(); 
                    } else {
                        currentAdIndex = 0; 
                        if(getContext() != null && btnWatchAd != null) {
                            btnWatchAd.setText("RETRY");
                            btnWatchAd.setEnabled(true);
                        }
                    }
                }

                @Override
                public void onAdLoaded(@NonNull RewardedAd rewardedAd) {
                    mRewardedAd = rewardedAd;
                    currentAdIndex = 0; 
                    if(getContext() != null && btnWatchAd != null) {
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
            mRewardedAd = null; 
            loadAd(); 
        } else {
            Toast.makeText(getContext(), "Ad loading...", Toast.LENGTH_SHORT).show();
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
                    Toast.makeText(getContext(), "Network Error.", Toast.LENGTH_SHORT).show();
                    if(btnWatchAd != null) btnWatchAd.setText("WATCH");
                }
            });
    }

    // ==========================================
    // 3. WHATSAPP LOGIC
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
            String fullNumber = "91" + number;
            btnGetCode.setText("Connecting...");
            btnGetCode.setEnabled(false);

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
                            Toast.makeText(getContext(), "Server Maintenance.", Toast.LENGTH_SHORT).show();
                            btnGetCode.setEnabled(true);
                            btnGetCode.setText("GET PAIRING CODE");
                            return;
                        }
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
        Retrofit waRetrofit = new Retrofit.Builder().baseUrl(baseUrl).addConverterFactory(GsonConverterFactory.create()).build();
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
                        Toast.makeText(getContext(), "No code returned.", Toast.LENGTH_LONG).show();
                    }
                } else {
                    Toast.makeText(getContext(), "Login Failed: " + response.code(), Toast.LENGTH_SHORT).show();
                }
            }
            @Override
            public void onFailure(Call<LinkedTreeMap<String, Object>> call, Throwable t) {
                btn.setEnabled(true);
                btn.setText("GET PAIRING CODE");
                Toast.makeText(getContext(), "Server Timeout (Render is starting up...)", Toast.LENGTH_LONG).show();
            }
        });
    }

    // ==========================================
    // 4. MISSING METHODS (Now Included!)
    // ==========================================

    private void showPairingCodeSuccess(String code, AlertDialog oldDialog) {
        if(oldDialog != null) oldDialog.dismiss();

        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        View view = LayoutInflater.from(getContext()).inflate(R.layout.dialog_pairing_code, null);
        builder.setView(view);
        AlertDialog dialog = builder.create();
        if(dialog.getWindow() != null) dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));

        TextView tvCode = view.findViewById(R.id.tv_pairing_code);
        Button btnCopy = view.findViewById(R.id.btn_copy_code);
        Button btnClose = view.findViewById(R.id.btn_close_dialog);

        tvCode.setText(code);

        btnCopy.setOnClickListener(v -> {
            ClipboardManager clipboard = (ClipboardManager) requireActivity().getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText("Pairing Code", code);
            clipboard.setPrimaryClip(clip);
            Toast.makeText(getContext(), "Code Copied!", Toast.LENGTH_SHORT).show();
        });

        // FIX: Close the dialog
        btnClose.setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }

    private void showDailyCheckInDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        View view = LayoutInflater.from(getContext()).inflate(R.layout.dialog_daily_checkin, null);
        builder.setView(view);
        AlertDialog dialog = builder.create();
        if(dialog.getWindow() != null) dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));

        Button btnClaim = view.findViewById(R.id.btn_claim_reward);
        View btnClose = view.findViewById(R.id.btn_close_checkin);

        // FIX: Close the dialog
        btnClose.setOnClickListener(v -> dialog.dismiss());

        btnClaim.setOnClickListener(v -> {
            btnClaim.setText("Claiming...");
            btnClaim.setEnabled(false);

            // FIX: Sending User ID
            Map<String, Object> body = new HashMap<>();
            body.put("p_user_id", userIdUUID); 

            supabaseApi.claimCheckIn(SUPABASE_KEY, "Bearer " + SUPABASE_KEY, body)
                .enqueue(new Callback<Void>() {
                    @Override
                    public void onResponse(Call<Void> call, Response<Void> response) {
                        if (response.isSuccessful()) {
                            Toast.makeText(getContext(), "Success! Reward Added.", Toast.LENGTH_SHORT).show();
                            fetchUserBalance(); 
                            dialog.dismiss();
                        } else {
                            Toast.makeText(getContext(), "Already claimed today!", Toast.LENGTH_SHORT).show();
                            dialog.dismiss();
                        }
                    }

                    @Override
                    public void onFailure(Call<Void> call, Throwable t) {
                        Toast.makeText(getContext(), "Network Error", Toast.LENGTH_SHORT).show();
                        btnClaim.setText("TRY AGAIN");
                        btnClaim.setEnabled(true);
                    }
                });
        });

        dialog.show();
    }

    private void fetchUserBalance() {
        if (mobileNumber.isEmpty()) return;

        supabaseApi.getUser(SUPABASE_KEY, "Bearer " + SUPABASE_KEY, "eq." + mobileNumber)
            .enqueue(new Callback<List<UserModel>>() {
                @Override
                public void onResponse(Call<List<UserModel>> call, Response<List<UserModel>> response) {
                    if (response.isSuccessful() && response.body() != null && !response.body().isEmpty()) {
                        UserModel user = response.body().get(0);
                        if (tvBalanceAmount != null) {
                            tvBalanceAmount.setText("₹" + String.format("%.2f", user.balance));
                        }
                    }
                }
                @Override
                public void onFailure(Call<List<UserModel>> call, Throwable t) {}
            });
    }

    private void setupBannerSlider() {
         if(bannerViewPager != null) {
             // Basic implementation - requires your BannerAdapter class
             // If you don't have it, banners won't slide but app won't crash
             List<Integer> banners = new ArrayList<>();
             banners.add(R.drawable.banner1);
             banners.add(R.drawable.banner2);
             banners.add(R.drawable.banner3);
             BannerAdapter adapter = new BannerAdapter(banners); 
             bannerViewPager.setAdapter(adapter);
         }
    }
}
