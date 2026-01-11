package com.smsindia.app.ui;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import com.smsindia.app.R;
import com.smsindia.app.config.Constants;
import com.smsindia.app.data.api.SupabaseApi;
import com.smsindia.app.data.model.UserModel;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class SpinFragment extends Fragment {


    private LuckyWheelView wheelView;
    private Button btnSpin;
    private TextView tvTokens;
    
    private SupabaseApi supabaseApi;
    private String mobileNumber;
    
    private long spinTokens = 0;
    private double currentBalance = 0.0;
    private boolean isSpinning = false;

    // Wheel Data: 0.6, 0.8, 10.0, 0.0, 100.0, 0.6
    private Double[] rewardsValue = {0.6, 0.8, 10.0, 0.0, 100.0, 0.6};

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_spin, container, false);

        // Init Retrofit
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(Constants.SUPABASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build();
        supabaseApi = retrofit.create(SupabaseApi.class);

        wheelView = v.findViewById(R.id.wheel_view);
        btnSpin = v.findViewById(R.id.btn_spin_now);
        tvTokens = v.findViewById(R.id.tv_spin_tokens);
        
        SharedPreferences prefs = requireActivity().getSharedPreferences("SMSINDIA_USER", 0);
        mobileNumber = prefs.getString("mobile", "");

        btnSpin.setOnClickListener(view -> {
            if (isSpinning) return;
            if (spinTokens <= 0) {
                Toast.makeText(getContext(), "No Spin Tokens left! Refer friends to earn more.", Toast.LENGTH_LONG).show();
                return;
            }
            startRiggedSpin();
        });

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
        return token != null ? "Bearer " + token : "Bearer " + Constants.SUPABASE_ANON_KEY;
    }

    private void fetchUserData() {
        if(mobileNumber.isEmpty()) return;
        
        // Use JWT token for authorization
        String authHeader = getAuthHeader();
        
        supabaseApi.getUser(Constants.SUPABASE_ANON_KEY, authHeader, "eq." + mobileNumber)
            .enqueue(new Callback<List<UserModel>>() {
                @Override
                public void onResponse(Call<List<UserModel>> call, Response<List<UserModel>> response) {
                    if (response.isSuccessful() && response.body() != null && !response.body().isEmpty()) {
                        UserModel user = response.body().get(0);
                        
                        spinTokens = user.getCoins();
                        currentBalance = user.getBalance();
                        
                        tvTokens.setText(String.valueOf(spinTokens));
                    }
                }

                @Override
                public void onFailure(Call<List<UserModel>> call, Throwable t) {
                    // Fail silently
                }
            });
    }

    private void startRiggedSpin() {
        isSpinning = true;
        btnSpin.setEnabled(false);
        btnSpin.setAlpha(0.6f);

        // 1. Deduct Token Locally
        spinTokens = spinTokens - 1;
        tvTokens.setText(String.valueOf(spinTokens));

        // 2. Sync Deduction to Server
        updateUserField("coins", spinTokens);

        // --- PROBABILITY LOGIC ---
        int targetIndex;
        int rand = new Random().nextInt(100);

        // 96% chance to get index 0 (0.6) or 5 (0.6)
        if (rand < 96) {
            targetIndex = (new Random().nextBoolean()) ? 0 : 5;
        } else {
             int[] others = {1, 2, 3, 4};
             targetIndex = others[new Random().nextInt(others.length)];
        }
        
        float sectorAngle = 360f / 6f;
        float finalAngle = (360 - (targetIndex * sectorAngle)) + (360 * 10);
        finalAngle -= (sectorAngle / 2);

        ObjectAnimator animator = ObjectAnimator.ofFloat(wheelView, "rotation", 0f, finalAngle);
        animator.setDuration(4000);
        animator.setInterpolator(new DecelerateInterpolator());
        animator.start();

        Double reward = rewardsValue[targetIndex];

        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                isSpinning = false;
                btnSpin.setEnabled(true);
                btnSpin.setAlpha(1.0f);
                handleWin(reward);
            }
        });
    }

    private void handleWin(Double reward) {
        if (reward > 0) {
            // 1. Update Balance Locally
            currentBalance += reward;

            // 2. Sync Balance to Server
            updateUserField("balance", currentBalance);
            
            // 3. Show Success
            showWinDialog(reward);
        } else {
            Toast.makeText(getContext(), "Better Luck Next Time!", Toast.LENGTH_SHORT).show();
        }
    }

    // Helper to update single fields
    private void updateUserField(String fieldName, Object value) {
        Map<String, Object> body = new HashMap<>();
        body.put(fieldName, value);

        // Use JWT token for authorization
        String authHeader = getAuthHeader();

        supabaseApi.updateUser(Constants.SUPABASE_ANON_KEY, authHeader, "eq." + mobileNumber, body)
            .enqueue(new Callback<Void>() {
                @Override public void onResponse(Call<Void> c, Response<Void> r) {}
                @Override public void onFailure(Call<Void> c, Throwable t) {}
            });
    }

    // --- DIALOG FUNCTION ---
    private void showWinDialog(Double amount) {
        if(getContext() == null) return;
        
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        View view = LayoutInflater.from(getContext()).inflate(R.layout.dialog_spin_win, null);
        builder.setView(view);
        AlertDialog dialog = builder.create();
        
        if(dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        TextView tvAmount = view.findViewById(R.id.tv_win_amount);
        Button btnCollect = view.findViewById(R.id.btn_collect_win);

        tvAmount.setText(String.format("₹ %.2f", amount));

        btnCollect.setOnClickListener(v -> {
            dialog.dismiss();
        });

        dialog.setCancelable(false);
        dialog.show();
    }
}