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
import android.os.Handler;
import android.os.Looper;
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

import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.MobileAds;
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

    // --- CONFIG ---
    private static final String SUPABASE_URL = "https://appfwrpynfxfpcvpavso.supabase.co";
    private static final String SUPABASE_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImFwcGZ3cnB5bmZ4ZnBjdnBhdnNvIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NjIwOTQ2MTQsImV4cCI6MjA3NzY3MDYxNH0.Z-BMBjME8MVK5MS2KBgcCDgR7kXvDEjtcHrVfIUvwZY";
    private static final String FALLBACK_AD_ID = "ca-app-pub-3940256099942544/5224354917"; 

    // --- UI VARIABLES ---
    private TextView tvBalanceAmount, tvTodayEarnings, tvTotalEarnings, tvUserMobile;
    private ViewPager2 bannerViewPager;
    private Button btnWatchAd;
    private TextView tvAdStatus;
    private ProgressBar pbAdProgress;

    // --- DATA ---
    private SupabaseApi supabaseApi;
    private String mobileNumber;
    private String userIdUUID; 
    private List<String> adUnitList = new ArrayList<>();
    private int currentAdIndex = 0;
    private RewardedAd mRewardedAd;

    // --- SLIDER HANDLER ---
    private final Handler sliderHandler = new Handler(Looper.getMainLooper());
    private final Runnable sliderRunnable = new Runnable() {
        @Override
        public void run() {
            if (bannerViewPager != null && bannerViewPager.getAdapter() != null) {
                int nextItem = (bannerViewPager.getCurrentItem() + 1) % bannerViewPager.getAdapter().getItemCount();
                bannerViewPager.setCurrentItem(nextItem, true);
                sliderHandler.postDelayed(this, 3000);
            }
        }
    };

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_home, container, false);

        // 1. Setup API
        supabaseApi = new Retrofit.Builder()
                .baseUrl(SUPABASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(SupabaseApi.class);

        // 2. Bind UI
        tvBalanceAmount = v.findViewById(R.id.tv_balance_amount);
        tvTodayEarnings = v.findViewById(R.id.tv_today_earnings);
        tvTotalEarnings = v.findViewById(R.id.tv_total_earnings);
        tvUserMobile = v.findViewById(R.id.tv_user_mobile);
        bannerViewPager = v.findViewById(R.id.banner_viewpager);
        
        btnWatchAd = v.findViewById(R.id.btn_watch_ad);
        tvAdStatus = v.findViewById(R.id.tv_ad_status);
        pbAdProgress = v.findViewById(R.id.pb_ad_progress);
        Button btnHistory = v.findViewById(R.id.btn_history);

        View dailyCheckinCard = v.findViewById(R.id.card_daily_checkin);
        View whatsappCard = v.findViewById(R.id.card_whatsapp_auth);
        View earnMoreCard = v.findViewById(R.id.card_earn_more);

        // 3. Load User
        SharedPreferences prefs = requireActivity().getSharedPreferences("SMSINDIA_USER", 0);
        mobileNumber = prefs.getString("mobile", "");
        userIdUUID = prefs.getString("userId", "");
        tvUserMobile.setText(mobileNumber);

        // 4. Init Features
        setupBannerSlider();
        MobileAds.initialize(getContext(), s -> {});
        fetchAdConfiguration(); 
        fetchAdProgress(); 

        // 5. Listeners
        dailyCheckinCard.setOnClickListener(view -> showDailyCheckInDialog());
        whatsappCard.setOnClickListener(view -> showWhatsAppLoginDialog());
        earnMoreCard.setOnClickListener(view -> openEarnMoreWebTask());
        
        if(btnWatchAd != null) btnWatchAd.setOnClickListener(view -> showAd());
        btnHistory.setOnClickListener(view -> startActivity(new Intent(getActivity(), WithdrawalHistoryActivity.class)));

        return v;
    }

    @Override
    public void onResume() {
        super.onResume();
        fetchUserBalance(); 
    }

    @Override
    public void onPause() {
        super.onPause();
        sliderHandler.removeCallbacks(sliderRunnable);
    }

    // ==========================================
    // HELPER METHOD: GET JWT TOKEN
    // ==========================================
    private String getAuthHeader() {
        SharedPreferences authPrefs = requireActivity().getSharedPreferences("SMS_AUTH", Context.MODE_PRIVATE);
        String token = authPrefs.getString("jwt", null);
        return token != null ? "Bearer " + token : "Bearer " + SUPABASE_KEY;
    }

    private void fetchUserBalance() {
        if(mobileNumber.isEmpty()) return;
        
        // Use JWT token for authorization
        String authHeader = getAuthHeader();
        
        supabaseApi.getUser(SUPABASE_KEY, authHeader, "eq." + mobileNumber)
            .enqueue(new Callback<List<UserModel>>() {
                @Override
                public void onResponse(Call<List<UserModel>> call, Response<List<UserModel>> response) {
                    if (response.isSuccessful() && response.body() != null && !response.body().isEmpty()) {
                        UserModel user = response.body().get(0);
                        if(tvBalanceAmount != null) tvBalanceAmount.setText(String.format("₹ %.2f", user.getBalance()));
                        if(tvTodayEarnings != null) tvTodayEarnings.setText(String.format("₹ %.2f", user.getTodayIncome()));
                        if(tvTotalEarnings != null) tvTotalEarnings.setText(String.format("₹ %.2f", user.getTotalIncome()));
                    }
                }
                @Override public void onFailure(Call<List<UserModel>> c, Throwable t) {}
            });
    }

    private void setupBannerSlider() {
        if (bannerViewPager == null) return;
        List<Integer> banners = new ArrayList<>();
        banners.add(R.drawable.banner1);
        banners.add(R.drawable.banner2);
        banners.add(R.drawable.banner3);

        bannerViewPager.setAdapter(new BannerAdapter(getContext(), banners));

        bannerViewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override public void onPageSelected(int pos) {
                sliderHandler.removeCallbacks(sliderRunnable);
                sliderHandler.postDelayed(sliderRunnable, 3000);
            }
        });
    }

    private void showWhatsAppLoginDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        View view = LayoutInflater.from(getContext()).inflate(R.layout.dialog_whatsapp_login, null);
        builder.setView(view);
        AlertDialog dialog = builder.create();
        if(dialog.getWindow() != null) dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));

        EditText etNumber = view.findViewById(R.id.et_wa_number);
        Button btnGetCode = view.findViewById(R.id.btn_get_code);
        view.findViewById(R.id.btn_cancel_wa).setOnClickListener(v -> dialog.dismiss());

        btnGetCode.setOnClickListener(v -> {
            String number = etNumber.getText().toString().trim();
            if (number.length() != 10) { etNumber.setError("Invalid"); return; }
            String fullNumber = "91" + number;
            btnGetCode.setText("Connecting...");
            btnGetCode.setEnabled(false);

            // Use JWT token for authorization
            String authHeader = getAuthHeader();

            supabaseApi.getConfig(SUPABASE_KEY, authHeader, "eq.whatsapp_config")
                .enqueue(new Callback<List<AppConfigModel>>() {
                    @Override public void onResponse(Call<List<AppConfigModel>> c, Response<List<AppConfigModel>> r) {
                        String url = ""; boolean active = false;
                        if (r.isSuccessful() && r.body() != null && !r.body().isEmpty()) {
                            Object val = r.body().get(0).value;
                            if (val instanceof LinkedTreeMap) {
                                url = (String)((LinkedTreeMap)val).get("base_url");
                                active = (boolean)((LinkedTreeMap)val).get("is_active");
                            }
                        }
                        if(active && url != null && !url.isEmpty()) connectToRenderServer(url, fullNumber, btnGetCode, dialog);
                        else { Toast.makeText(getContext(), "Server Offline", Toast.LENGTH_SHORT).show(); btnGetCode.setEnabled(true); }
                    }
                    @Override public void onFailure(Call<List<AppConfigModel>> c, Throwable t) { btnGetCode.setEnabled(true); }
                });
        });
        dialog.show();
    }

    private void connectToRenderServer(String baseUrl, String phone, Button btn, AlertDialog dialog) {
        if (!baseUrl.endsWith("/")) baseUrl += "/";
        Retrofit waRetrofit = new Retrofit.Builder().baseUrl(baseUrl).addConverterFactory(GsonConverterFactory.create()).build();
        WhatsAppApi waApi = waRetrofit.create(WhatsAppApi.class);
        Map<String, Object> body = new HashMap<>(); body.put("phone", phone);

        waApi.login(body).enqueue(new Callback<LinkedTreeMap<String, Object>>() {
            @Override public void onResponse(Call<LinkedTreeMap<String, Object>> c, Response<LinkedTreeMap<String, Object>> r) {
                btn.setEnabled(true); btn.setText("GET CODE");
                if (r.isSuccessful() && r.body() != null && r.body().containsKey("code")) showPairingCodeSuccess((String)r.body().get("code"), dialog);
                else Toast.makeText(getContext(), "Failed: " + r.code(), Toast.LENGTH_SHORT).show();
            }
            @Override public void onFailure(Call<LinkedTreeMap<String, Object>> c, Throwable t) { btn.setEnabled(true); btn.setText("GET CODE"); }
        });
    }

    private void showPairingCodeSuccess(String code, AlertDialog oldDialog) {
        if(oldDialog != null) oldDialog.dismiss();
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        View view = LayoutInflater.from(getContext()).inflate(R.layout.dialog_pairing_code, null);
        builder.setView(view);
        AlertDialog dialog = builder.create();
        if(dialog.getWindow() != null) dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));

        TextView tvCode = view.findViewById(R.id.tv_pairing_code); tvCode.setText(code);
        view.findViewById(R.id.btn_copy_code).setOnClickListener(v -> {
            ((ClipboardManager)requireActivity().getSystemService(Context.CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("Code", code));
            Toast.makeText(getContext(), "Copied!", Toast.LENGTH_SHORT).show();
        });
        view.findViewById(R.id.btn_close_dialog).setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    private void showDailyCheckInDialog() {
        if(userIdUUID.isEmpty()) return;
        Map<String, Object> body = new HashMap<>(); body.put("p_user_id", userIdUUID);
        
        // Use JWT token for authorization
        String authHeader = getAuthHeader();
        
        supabaseApi.claimDailyCheckin(SUPABASE_KEY, authHeader, body).enqueue(new Callback<Void>() {
            @Override public void onResponse(Call<Void> c, Response<Void> r) {
                if(r.isSuccessful()) { 
                    Toast.makeText(getContext(), "✅ Success!", Toast.LENGTH_LONG).show(); 
                    fetchUserBalance(); 
                } else {
                    Toast.makeText(getContext(), "Already Claimed!", Toast.LENGTH_SHORT).show();
                }
            }
            @Override public void onFailure(Call<Void> c, Throwable t) {}
        });
    }

    private void openEarnMoreWebTask() {
        // Use JWT token for authorization
        String authHeader = getAuthHeader();
        
        supabaseApi.getConfig(SUPABASE_KEY, authHeader, "eq.earn_more_config").enqueue(new Callback<List<AppConfigModel>>() {
            @Override public void onResponse(Call<List<AppConfigModel>> c, Response<List<AppConfigModel>> r) {
                if(r.isSuccessful() && r.body() != null && !r.body().isEmpty()) {
                    String url = (String)((LinkedTreeMap)r.body().get(0).value).get("url");
                    if(url != null) {
                        String finalUrl = url + (url.contains("?") ? "&" : "?") + "phone=" + mobileNumber;
                        startActivity(new Intent(getActivity(), WebTaskActivity.class).putExtra("TARGET_URL", finalUrl));
                        return;
                    }
                }
                Toast.makeText(getContext(), "No Tasks", Toast.LENGTH_SHORT).show();
            }
            @Override public void onFailure(Call<List<AppConfigModel>> c, Throwable t) {}
        });
    }

    private void fetchAdConfiguration() {
        // Use JWT token for authorization
        String authHeader = getAuthHeader();
        
        supabaseApi.getConfig(SUPABASE_KEY, authHeader, "eq.admob_config").enqueue(new Callback<List<AppConfigModel>>() {
            @Override public void onResponse(Call<List<AppConfigModel>> c, Response<List<AppConfigModel>> r) {
                try {
                    if(r.isSuccessful() && r.body() != null && !r.body().isEmpty()) {
                        List<String> ids = (List<String>)((LinkedTreeMap)r.body().get(0).value).get("rewarded_ids");
                        if(ids != null && !ids.isEmpty()) { 
                            adUnitList.clear(); 
                            adUnitList.addAll(ids); 
                            loadAd(); 
                            return; 
                        }
                    }
                } catch(Exception e) {}
                useFallbackAds();
            }
            @Override public void onFailure(Call<List<AppConfigModel>> c, Throwable t) { useFallbackAds(); }
        });
    }

    private void useFallbackAds() { adUnitList.add(FALLBACK_AD_ID); loadAd(); }

    private void loadAd() {
        if (adUnitList.isEmpty() || getContext() == null) return;
        if(btnWatchAd != null) btnWatchAd.setText("LOADING...");
        RewardedAd.load(getContext(), adUnitList.get(currentAdIndex), new AdRequest.Builder().build(), new RewardedAdLoadCallback() {
            @Override public void onAdFailedToLoad(@NonNull LoadAdError e) {
                mRewardedAd = null; currentAdIndex = (currentAdIndex + 1) % adUnitList.size();
                if(btnWatchAd != null) { btnWatchAd.setText("RETRY"); btnWatchAd.setEnabled(true); }
            }
            @Override public void onAdLoaded(@NonNull RewardedAd ad) {
                mRewardedAd = ad; if(btnWatchAd != null) { btnWatchAd.setText("WATCH"); btnWatchAd.setEnabled(true); }
            }
        });
    }

    private void showAd() {
        if (mRewardedAd != null) {
            mRewardedAd.show(getActivity(), rewardItem -> updateAdProgress());
            mRewardedAd = null; loadAd();
        } else loadAd();
    }

    private void updateAdProgress() {
        if(userIdUUID.isEmpty()) return;
        Map<String, Object> body = new HashMap<>(); body.put("p_user_id", userIdUUID);
        
        // Use JWT token for authorization
        String authHeader = getAuthHeader();
        
        supabaseApi.watchAdReward(SUPABASE_KEY, authHeader, body).enqueue(new Callback<LinkedTreeMap<String, Object>>() {
            @Override public void onResponse(Call<LinkedTreeMap<String, Object>> c, Response<LinkedTreeMap<String, Object>> r) {
                if(r.isSuccessful() && r.body() != null) {
                    int p = ((Double)r.body().get("progress")).intValue();
                    if(pbAdProgress != null) pbAdProgress.setProgress(p);
                    if(tvAdStatus != null) tvAdStatus.setText("Progress: " + p + "/10");
                    Toast.makeText(getContext(), (String)r.body().get("message"), Toast.LENGTH_SHORT).show();
                }
            }
            @Override public void onFailure(Call<LinkedTreeMap<String, Object>> c, Throwable t) {}
        });
    }
    
    private void fetchAdProgress() {
        // Use JWT token for authorization
        String authHeader = getAuthHeader();
        
        supabaseApi.getUser(SUPABASE_KEY, authHeader, "eq." + mobileNumber).enqueue(new Callback<List<UserModel>>() {
            @Override public void onResponse(Call<List<UserModel>> c, Response<List<UserModel>> r) {
                if(r.isSuccessful() && r.body() != null && !r.body().isEmpty()) {
                    int p = r.body().get(0).adProgress;
                    if(pbAdProgress != null) pbAdProgress.setProgress(p);
                    if(tvAdStatus != null) tvAdStatus.setText("Progress: " + p + "/10");
                }
            }
            @Override public void onFailure(Call<List<UserModel>> c, Throwable t) {}
        });
    }
}