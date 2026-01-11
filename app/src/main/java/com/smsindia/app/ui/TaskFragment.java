package com.smsindia.app.ui;

import android.Manifest;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Looper;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.util.Log;
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

    private static final String TAG = "TaskFragment";
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
    private String userIdUUID; // Using UUID for Supabase
    
    // Memory leak fix - keep reference to cancel timer
    private CountDownTimer currentTimer;
    
    // Handler for UI updates from BroadcastReceiver
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    // Receiver to handle updates from Service
    private final BroadcastReceiver updateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if (SmsMiningService.ACTION_UPDATE_UI.equals(action)) {
                String log = intent.getStringExtra("log");
                int progress = intent.getIntExtra("progress", 0);

                // Post UI updates to main thread
                mainHandler.post(() -> {
                    if (log != null) {
                        logUI(log);
                        if (tvStatus != null) {
                            tvStatus.setText(log);
                        }
                        if (log.equals(getString(R.string.service_stopped))) {
                            setUIStoppedState();
                        }
                    }
                    if (progressTimer != null) {
                        progressTimer.setProgress(progress);
                    }
                    if (tvTimer != null) {
                        if (progress > 0 && progress < 100) {
                            tvTimer.setText(progress + "%");
                        } else {
                            tvTimer.setText(getString(R.string.timer_dash));
                        }
                    }
                });
            }
            else if (SmsMiningService.ACTION_BATCH_COMPLETE.equals(action)) {
                int success = intent.getIntExtra("successCount", 0);
                double earned = intent.getDoubleExtra("earned", 0.0);
                
                // Post UI updates to main thread
                mainHandler.post(() -> {
                    setUIStoppedState();
                    showSyncDialog(success, earned);
                });
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

        // Null check for getActivity()
        if (getActivity() != null) {
            SharedPreferences prefs = getActivity().getSharedPreferences("SMSINDIA_USER", 0);
            userIdUUID = prefs.getString("userId", "");
        } else {
            Log.e(TAG, "Activity is null in onCreateView");
        }

        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.SEND_SMS, Manifest.permission.READ_PHONE_STATE}, PERMISSION_REQ_CODE);
        } else {
            loadSimCards();
        }

        setupListeners();
        return v;
    }
    
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQ_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "Permissions granted");
                loadSimCards();
            } else {
                Log.w(TAG, "Permissions denied");
                if (getContext() != null) {
                    Toast.makeText(getContext(), "Permissions required for app to function", Toast.LENGTH_LONG).show();
                }
            }
        }
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
            if (getContext() != null) {
                Toast.makeText(getContext(), R.string.select_sim_card, Toast.LENGTH_SHORT).show();
            }
            return;
        }
        if (userIdUUID == null || userIdUUID.isEmpty()) {
            if (getContext() != null) {
                Toast.makeText(getContext(), R.string.session_expired, Toast.LENGTH_LONG).show();
            }
            return;
        }

        // Null check for getActivity()
        if (getActivity() == null) {
            Log.e(TAG, "Cannot start service: Activity is null");
            return;
        }

        Intent serviceIntent = new Intent(getActivity(), SmsMiningService.class);
        serviceIntent.putExtra("subId", selectedSubId);
        serviceIntent.putExtra("userId", userIdUUID);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getActivity().startForegroundService(serviceIntent);
        } else {
            getActivity().startService(serviceIntent);
        }

        isServiceRunning = true;
        btnAction.setText(R.string.stop_batch);
        btnAction.setTextColor(ContextCompat.getColor(requireContext(), R.color.button_stop_text));
        tvStatus.setText(R.string.starting_batch);
        progressTimer.setIndeterminate(false);
        progressTimer.setProgress(0);
    }

    private void stopService() {
        // Null check for getActivity()
        if (getActivity() == null) {
            Log.e(TAG, "Cannot stop service: Activity is null");
            return;
        }
        
        Intent serviceIntent = new Intent(getActivity(), SmsMiningService.class);
        serviceIntent.setAction("STOP_SERVICE");
        getActivity().startService(serviceIntent);
        setUIStoppedState();
    }

    private void setUIStoppedState() {
        isServiceRunning = false;
        btnAction.setText(R.string.start_mining);
        btnAction.setTextColor(ContextCompat.getColor(requireContext(), R.color.button_start_text));
        tvTimer.setText(R.string.timer_zero);
        progressTimer.setIndeterminate(false);
        progressTimer.setProgress(0);
    }

    private void showSyncDialog(int successCount, double earnedAmount) {
        // Null check for getActivity()
        if (getActivity() == null || !isAdded()) {
            Log.e(TAG, "Cannot show dialog: Activity is null or fragment not added");
            return;
        }

        try {
            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
            LayoutInflater inflater = getActivity().getLayoutInflater();
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

            // Cancel previous timer if exists
            if (currentTimer != null) {
                currentTimer.cancel();
            }

            // Start 60s Cooldown Timer
            currentTimer = new CountDownTimer(60000, 1000) {
                @Override
                public void onTick(long millisUntilFinished) {
                    if (dialog.isShowing() && isAdded()) {
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
                    if (dialog.isShowing() && isAdded()) {
                        try {
                            ViewGroup parentLayout = (ViewGroup) tvTimer.getParent();
                            if (parentLayout != null && parentLayout.getChildCount() > 1) {
                                parentLayout.getChildAt(1).setVisibility(View.GONE); 
                            }
                        } catch (Exception e) {
                            Log.w(TAG, "Error hiding label", e);
                        }

                        tvTimer.setTextSize(18); 
                        tvTimer.setText(String.format(Locale.US, getString(R.string.batch_result_format), successCount, earnedAmount));
                        tvTimer.setTextColor(ContextCompat.getColor(requireContext(), R.color.batch_success));
                        
                        progressBar.setProgress(0);
                        
                        new Handler(Looper.getMainLooper()).postDelayed(() -> {
                            if (dialog.isShowing()) {
                                dialog.dismiss();
                            }
                            if (tvStatus != null) {
                                tvStatus.setText(R.string.batch_complete);
                            }
                            
                            if (isAutoMode && successCount > 0) {
                                startService();
                            }
                        }, 4000);
                    }
                    currentTimer = null;
                }
            };
            currentTimer.start();
        } catch (Exception e) {
            Log.e(TAG, "Error showing sync dialog", e);
        }
    }

    private void loadSimCards() {
        if (getContext() == null) {
            Log.e(TAG, "Cannot load SIM cards: Context is null");
            return;
        }
        
        SubscriptionManager sm = (SubscriptionManager) getContext().getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE);
        if (sm == null) {
            Log.e(TAG, "SubscriptionManager is null");
            return;
        }
        
        if (ActivityCompat.checkSelfPermission(getContext(), Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "READ_PHONE_STATE permission not granted");
            return;
        }

        try {
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
            } else {
                Log.w(TAG, "No active SIM cards found");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error loading SIM cards", e);
        }
    }

    private void selectSim(int index) {
        int gold = ContextCompat.getColor(requireContext(), R.color.sim_card_selected);
        int grey = ContextCompat.getColor(requireContext(), R.color.sim_card_unselected);
        if (index == 1) {
            selectedSubId = subId1;
            cardSim1.setStrokeColor(gold); 
            cardSim1.setStrokeWidth(6);
            cardSim2.setStrokeColor(grey); 
            cardSim2.setStrokeWidth(2);
        } else {
            selectedSubId = subId2;
            cardSim2.setStrokeColor(gold); 
            cardSim2.setStrokeWidth(6);
            cardSim1.setStrokeColor(grey); 
            cardSim1.setStrokeWidth(2);
        }
    }

    private void logUI(String msg) {
        if (tvLogs == null) {
            return;
        }
        try {
            String prev = tvLogs.getText().toString();
            if (prev.length() > 500) {
                prev = prev.substring(0, 500) + "...";
            }
            tvLogs.setText("> " + msg + "\n" + prev);
        } catch (Exception e) {
            Log.e(TAG, "Error updating logs", e);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (getActivity() == null) {
            Log.e(TAG, "Activity is null in onResume");
            return;
        }
        
        try {
            IntentFilter filter = new IntentFilter();
            filter.addAction(SmsMiningService.ACTION_UPDATE_UI);
            filter.addAction(SmsMiningService.ACTION_BATCH_COMPLETE);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                getActivity().registerReceiver(updateReceiver, filter, Context.RECEIVER_EXPORTED);
            } else {
                getActivity().registerReceiver(updateReceiver, filter);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error registering receiver", e);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (getActivity() != null) {
            try {
                getActivity().unregisterReceiver(updateReceiver);
            } catch (Exception e) {
                Log.w(TAG, "Error unregistering receiver", e);
            }
        }
    }
    
    @Override
    public void onDestroyView() {
        super.onDestroyView();
        // Cancel timer to prevent memory leak
        if (currentTimer != null) {
            currentTimer.cancel();
            currentTimer = null;
        }
    }
}
