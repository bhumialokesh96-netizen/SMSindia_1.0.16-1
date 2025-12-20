package com.smsindia.app.workers;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.telephony.SmsManager;
import android.util.Log;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.smsindia.app.R;
import com.smsindia.app.service.BatchClaimRequest;
import com.smsindia.app.service.SupabaseApi;
import com.smsindia.app.service.TaskModel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class SmsMiningService extends Service {

    // --- CONFIGURATION ---
    private static final String SUPABASE_URL = "https://appfwrpynfxfpcvpavso.supabase.co";
    private static final String SUPABASE_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImFwcGZ3cnB5bmZ4ZnBjdnBhdnNvIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NjIwOTQ2MTQsImV4cCI6MjA3NzY3MDYxNH0.Z-BMBjME8MVK5MS2KBgcCDgR7kXvDEjtcHrVfIUvwZY";
    
    public static final String ACTION_UPDATE_UI = "com.smsindia.UPDATE_UI";
    public static final String ACTION_BATCH_COMPLETE = "com.smsindia.BATCH_COMPLETE";
    private static final String CHANNEL_ID = "SMS_MINING_CHANNEL";

    private SupabaseApi supabaseApi;
    private PowerManager.WakeLock wakeLock;
    
    private boolean isRunning = false;
    private int selectedSubId = -1;
    private String userId; 
    
    // BATCH STATE
    private List<TaskModel> currentBatch = new ArrayList<>();
    private List<String> successfulTaskIds = new ArrayList<>();
    private int currentTaskIndex = 0;
    private final int BATCH_SIZE = 10;

    @Override
    public void onCreate() {
        super.onCreate();
        supabaseApi = new Retrofit.Builder()
                .baseUrl(SUPABASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(SupabaseApi.class);
        
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        if (pm != null) wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SMSMiner::Lock");
        
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && "STOP_SERVICE".equals(intent.getAction())) {
            stopServiceWithLog("User Stopped");
            return START_NOT_STICKY;
        }

        if (intent != null && intent.hasExtra("userId")) userId = intent.getStringExtra("userId");
        if (intent != null) selectedSubId = intent.getIntExtra("subId", -1);

        if (!isRunning && userId != null) {
            isRunning = true;
            acquireCpu();
            startForeground(1, getNotification("Mining Active", "Starting Batch..."));
            fetchBatch();
        }
        return START_STICKY;
    }

    // 1. GET 10 TASKS
    private void fetchBatch() {
        if (!isRunning) return;
        
        sendBroadcastLog("Fetching " + BATCH_SIZE + " Tasks...", 0);
        
        Map<String, Object> params = new HashMap<>();
        params.put("p_user_id", userId);
        params.put("p_size", BATCH_SIZE);

        supabaseApi.fetchBatchTasks(SUPABASE_KEY, "Bearer " + SUPABASE_KEY, params).enqueue(new Callback<List<TaskModel>>() {
            @Override
            public void onResponse(Call<List<TaskModel>> call, Response<List<TaskModel>> response) {
                if (response.isSuccessful() && response.body() != null && !response.body().isEmpty()) {
                    currentBatch = response.body();
                    successfulTaskIds.clear();
                    currentTaskIndex = 0;
                    processNextInBatch(); // Start Local Loop
                } else {
                    sendBroadcastLog("No Tasks. Waiting 10s...", 0);
                    new Handler().postDelayed(() -> fetchBatch(), 10000);
                }
            }

            @Override
            public void onFailure(Call<List<TaskModel>> call, Throwable t) {
                sendBroadcastLog("Net Error. Retrying...", 0);
                new Handler().postDelayed(() -> fetchBatch(), 5000);
            }
        });
    }

    // 2. LOOP THROUGH TASKS LOCALLY
    private void processNextInBatch() {
        if (!isRunning) return;

        // If batch is finished, go to claim
        if (currentTaskIndex >= currentBatch.size()) {
            claimBatch();
            return;
        }

        TaskModel task = currentBatch.get(currentTaskIndex);
        int progress = (currentTaskIndex * 100) / currentBatch.size();
        sendBroadcastLog("Sending SMS " + (currentTaskIndex + 1) + "/" + currentBatch.size(), progress);

        try {
            sendSMS(task);
            successfulTaskIds.add(task.id); // Assume success for simplicity (or use SentIntent)
        } catch (Exception e) {
            Log.e("BatchMiner", "SMS Error: " + e.getMessage());
        }

        // Wait 3 seconds before sending next (prevent spam block)
        currentTaskIndex++;
        new Handler(Looper.getMainLooper()).postDelayed(this::processNextInBatch, 3000);
    }

    private void sendSMS(TaskModel task) {
        SmsManager smsManager;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            smsManager = getSystemService(SmsManager.class);
            if (selectedSubId != -1) smsManager = smsManager.createForSubscriptionId(selectedSubId);
        } else {
            smsManager = SmsManager.getDefault();
            if (selectedSubId != -1) smsManager = SmsManager.getSmsManagerForSubscriptionId(selectedSubId);
        }
        // Send without waiting for PendingIntent to keep loop fast
        smsManager.sendTextMessage(task.phone, null, task.message, null, null);
    }

    // 3. CLAIM REWARD FOR ALL SENT
    private void claimBatch() {
        if (successfulTaskIds.isEmpty()) {
            fetchBatch(); // Nothing to claim, get next batch
            return;
        }

        sendBroadcastLog("Syncing Rewards...", 100);
        
        BatchClaimRequest request = new BatchClaimRequest(userId, successfulTaskIds);

        supabaseApi.claimBatchReward("Bearer " + SUPABASE_KEY, SUPABASE_KEY, request).enqueue(new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response) {
                Log.d("BatchMiner", "Batch Claimed!");
                finishBatch(successfulTaskIds.size());
            }

            @Override
            public void onFailure(Call<Void> call, Throwable t) {
                Log.e("BatchMiner", "Claim Failed, but task marked assigned. Will retry fetch.");
                finishBatch(successfulTaskIds.size());
            }
        });
    }

    private void finishBatch(int count) {
        Intent intent = new Intent(ACTION_BATCH_COMPLETE);
        intent.putExtra("successCount", count);
        intent.putExtra("earned", count * 0.16);
        sendBroadcast(intent);
        stopServiceWithLog("Done");
    }

    private void sendBroadcastLog(String log, int progress) {
        Intent intent = new Intent(ACTION_UPDATE_UI);
        intent.putExtra("log", log);
        intent.putExtra("progress", progress);
        sendBroadcast(intent);
    }

    private void stopServiceWithLog(String reason) {
        isRunning = false;
        releaseCpu();
        stopSelf();
    }
    
    private void acquireCpu() { if (wakeLock != null && !wakeLock.isHeld()) wakeLock.acquire(600000); } // 10 min lock
    private void releaseCpu() { if (wakeLock != null && wakeLock.isHeld()) wakeLock.release(); }
    
    private Notification getNotification(String title, String content) {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(title).setContentText(content)
                .setSmallIcon(R.drawable.ic_launcher).build();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getSystemService(NotificationManager.class).createNotificationChannel(
                new NotificationChannel(CHANNEL_ID, "Mining", NotificationManager.IMPORTANCE_LOW));
        }
    }

    @Override
    public void onDestroy() {
        isRunning = false;
        releaseCpu();
        super.onDestroy();
    }

    @Nullable @Override public IBinder onBind(Intent intent) { return null; }
}
