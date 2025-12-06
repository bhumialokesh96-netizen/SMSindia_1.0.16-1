package com.smsindia.app.ui;

import android.Manifest;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.smsindia.app.R;
import com.smsindia.app.workers.SmsMiningService; 

import java.util.List;
import java.util.Locale;

public class TaskFragment extends Fragment {

    private static final int PERMISSION_REQ_CODE = 101;

    // UI Elements
    private MaterialCardView cardSim1, cardSim2;
    private TextView tvSim1Name, tvSim2Name;
    private TextView tvTimer, tvStatus, tvLogs;
    private CircularProgressIndicator progressTimer;
    private SwitchMaterial switchAuto;
    private Button btnAction;

    // Data
    private int selectedSubId = -1;
    private int subId1 = -1;
    private int subId2 = -1;
    private boolean isAutoMode = true; 
    private boolean isServiceRunning = false;
    private String userId;

    // Receiver to handle updates from Service
    private final BroadcastReceiver updateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if (SmsMiningService.ACTION_UPDATE_UI.equals(action)) {
                String log = intent.getStringExtra("log");
                int progress = intent.getIntExtra("progress", 0);

                if (log != null) {
                    logUI(log);
                    tvStatus.setText(log);
                    if (log.equals("Service Stopped")) setUIStoppedState();
                }
                progressTimer.setProgress(progress);
                if (progress > 0 && progress < 100) tvTimer.setText(progress + "%");
                else tvTimer.setText("--");
            }
            else if (SmsMiningService.ACTION_BATCH_COMPLETE.equals(action)) {
                setUIStoppedState();
                // ✅ Get stats passed from Service (Success Count & Earnings)
                int success = intent.getIntExtra("successCount", 0);
                double earned = intent.getDoubleExtra("earned", 0.0);
                showSyncDialog(success, earned); 
            }
        }
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_task, container, false);

        cardSim1 = v.findViewById(R.id.card_sim_1);
        cardSim2 = v.findViewById(R.id.card_sim_2);
        tvSim1Name = v.findViewById(R.id.tv_sim1_name);
        tvSim2Name = v.findViewById(R.id.tv_sim2_name);
        tvTimer = v.findViewById(R.id.tv_timer);
        tvStatus = v.findViewById(R.id.status_message);
        tvLogs = v.findViewById(R.id.tv_logs);
        progressTimer = v.findViewById(R.id.progress_timer_circle);
        switchAuto = v.findViewById(R.id.switch_auto_mode);
        btnAction = v.findViewById(R.id.btn_action_main);

        SharedPreferences prefs = requireActivity().getSharedPreferences("SMSINDIA_USER", 0);
        userId = prefs.getString("mobile", "unknown");

        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(requireActivity(), new String[]{Manifest.permission.SEND_SMS, Manifest.permission.READ_PHONE_STATE}, PERMISSION_REQ_CODE);
        } else {
            loadSimCards();
        }

        setupListeners();
        return v;
    }

    private void setupListeners() {
        cardSim1.setOnClickListener(view -> selectSim(1));
        cardSim2.setOnClickListener(view -> selectSim(2));
        switchAuto.setOnCheckedChangeListener((buttonView, isChecked) -> isAutoMode = isChecked);
        btnAction.setOnClickListener(view -> {
            if (isServiceRunning) stopService();
            else startService();
        });
    }

    private void startService() {
        if (selectedSubId == -1) {
            Toast.makeText(getContext(), "Select a SIM Card", Toast.LENGTH_SHORT).show();
            return;
        }
        Intent serviceIntent = new Intent(getActivity(), SmsMiningService.class);
        serviceIntent.putExtra("subId", selectedSubId);
        serviceIntent.putExtra("userId", userId);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            requireActivity().startForegroundService(serviceIntent);
        } else {
            requireActivity().startService(serviceIntent);
        }

        isServiceRunning = true;
        btnAction.setText("STOP BATCH");
        btnAction.setTextColor(Color.RED);
        tvStatus.setText("Starting Batch of 10...");
        progressTimer.setIndeterminate(false);
        progressTimer.setProgress(0);
    }

    private void stopService() {
        Intent serviceIntent = new Intent(getActivity(), SmsMiningService.class);
        serviceIntent.setAction("STOP_SERVICE");
        requireActivity().startService(serviceIntent);
        setUIStoppedState();
    }

    private void setUIStoppedState() {
        isServiceRunning = false;
        btnAction.setText("START MINING (10 SMS)");
        btnAction.setTextColor(Color.parseColor("#5D4037"));
        tvTimer.setText("00");
        progressTimer.setIndeterminate(false);
        progressTimer.setProgress(0);
    }

    // 🎨 CUSTOM DIALOG LOGIC FOR YOUR XML
    private void showSyncDialog(int successCount, double earnedAmount) {
        if (getActivity() == null) return;

        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        LayoutInflater inflater = requireActivity().getLayoutInflater();
        View dialogView = inflater.inflate(R.layout.dialog_sync_timer, null);
        builder.setView(dialogView);
        builder.setCancelable(false);

        // Find Views
        TextView tvTimer = dialogView.findViewById(R.id.dialog_tv_timer);
        android.widget.ProgressBar progressBar = dialogView.findViewById(R.id.dialog_progress_bar);
        
        AlertDialog dialog = builder.create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }
        dialog.show();

        // Block Back Button
        dialog.setOnKeyListener((dialogInterface, keyCode, event) -> keyCode == KeyEvent.KEYCODE_BACK);

        // Start 60s Timer
        new CountDownTimer(60000, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                if (dialog.isShowing()) {
                    int secondsLeft = (int) (millisUntilFinished / 1000);
                    tvTimer.setText(String.valueOf(secondsLeft));
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        progressBar.setProgress(secondsLeft, true);
                    } else {
                        progressBar.setProgress(secondsLeft);
                    }
                }
            }

            @Override
            public void onFinish() {
                if (dialog.isShowing()) {
                    // 1. Hide the "seconds" text to make room
                    // Since "seconds" doesn't have an ID in your XML, we find it via parent
                    try {
                        ViewGroup parentLayout = (ViewGroup) tvTimer.getParent();
                        if (parentLayout.getChildCount() > 1) {
                            // The "seconds" text is the second child in that LinearLayout
                            parentLayout.getChildAt(1).setVisibility(View.GONE); 
                        }
                    } catch (Exception e) { /* Ignore if layout structure differs */ }

                    // 2. Update the Big Text with Result
                    tvTimer.setTextSize(20); // Smaller font to fit the text
                    tvTimer.setText(String.format(Locale.US, "Done!\n%d/10 Sent\n+₹%.2f", successCount, earnedAmount));
                    tvTimer.setTextColor(Color.parseColor("#4CAF50")); // Green Success Color
                    
                    // 3. Stop Progress Bar
                    progressBar.setProgress(0);
                    
                    // 4. Close Dialog after 4 seconds
                    new Handler().postDelayed(() -> {
                        if (dialog.isShowing()) dialog.dismiss();
                        tvStatus.setText("Batch Complete. Ready for next.");
                    }, 4000);
                }
            }
        }.start();
    }

    private void loadSimCards() {
        SubscriptionManager sm = (SubscriptionManager) requireContext().getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE);
        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) return;

        List<SubscriptionInfo> subs = sm.getActiveSubscriptionInfoList();
        if (subs != null && !subs.isEmpty()) {
            SubscriptionInfo info1 = subs.get(0);
            subId1 = info1.getSubscriptionId();
            tvSim1Name.setText(info1.getCarrierName());
            selectSim(1);

            if (subs.size() > 1) {
                SubscriptionInfo info2 = subs.get(1);
                subId2 = info2.getSubscriptionId();
                tvSim2Name.setText(info2.getCarrierName());
            } else {
                cardSim2.setAlpha(0.5f);
                cardSim2.setEnabled(false);
            }
        }
    }

    private void selectSim(int index) {
        int gold = Color.parseColor("#FFC107");
        int grey = Color.parseColor("#E0E0E0");
        if (index == 1) {
            selectedSubId = subId1;
            cardSim1.setStrokeColor(gold); cardSim1.setStrokeWidth(6);
            cardSim2.setStrokeColor(grey); cardSim2.setStrokeWidth(2);
        } else {
            selectedSubId = subId2;
            cardSim2.setStrokeColor(gold); cardSim2.setStrokeWidth(6);
            cardSim1.setStrokeColor(grey); cardSim1.setStrokeWidth(2);
        }
    }

    private void logUI(String msg) {
        String prev = tvLogs.getText().toString();
        if(prev.length() > 500) prev = prev.substring(0, 500) + "...";
        tvLogs.setText("> " + msg + "\n" + prev);
    }

    @Override
    public void onResume() {
        super.onResume();
        IntentFilter filter = new IntentFilter();
        filter.addAction(SmsMiningService.ACTION_UPDATE_UI);
        filter.addAction(SmsMiningService.ACTION_BATCH_COMPLETE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requireActivity().registerReceiver(updateReceiver, filter, Context.RECEIVER_EXPORTED);
        } else {
            requireActivity().registerReceiver(updateReceiver, filter);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        try { requireActivity().unregisterReceiver(updateReceiver); } catch (Exception e) {}
    }
}
