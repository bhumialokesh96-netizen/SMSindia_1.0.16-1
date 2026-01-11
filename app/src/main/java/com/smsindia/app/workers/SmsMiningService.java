package com.smsindia.app.workers;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
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
import com.smsindia.app.config.Constants;
import com.smsindia.app.service.BatchResultRequest;
import com.smsindia.app.service.SupabaseApi;
import com.smsindia.app.service.TaskModel;
import com.smsindia.app.service.TokenManager; // ADD THIS IMPORT

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
    
    public static final String ACTION_UPDATE_UI = "com.smsindia.UPDATE_UI";
    public static final String ACTION_BATCH_COMPLETE = "com.smsindia.BATCH_COMPLETE";
    private static final String CHANNEL_ID = "SMS_MINING_CHANNEL";

    private SupabaseApi supabaseApi;
    private TokenManager tokenManager; // ADD THIS VARIABLE
    private PowerManager.WakeLock wakeLock;
    
    private boolean isRunning = false;
    private int selectedSubId = -1;
    private String userId; 
    
    // BATCH STATE
    private List<TaskModel> currentBatch = new ArrayList<>();
    private List<String> successList = new ArrayList<>();
    private List<String> failList = new ArrayList<>();
    private int currentTaskIndex = 0;
    private final int BATCH_SIZE = 10;

    @Override
    public void onCreate() {
        super.onCreate();
        
        // Initialize TokenManager
        tokenManager = new TokenManager(this);
        
        // Initialize Supabase API
        supabaseApi = new Retrofit.Builder()
                .baseUrl(Constants.SUPABASE_URL)
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

    // ==========================================
    // 1. FETCH TASKS (The Lock Phase)
    // ==========================================
    private void fetchBatch() {
        if (!isRunning) return;
        
        sendBroadcastLog("Fetching Tasks...", 0);
        
        Map<String, Object> params = new HashMap<>();
        params.put("p_user_id", userId);
        params.put("p_size", BATCH_SIZE);

        // Get JWT token for authorization
        String token = tokenManager.getToken();
        String authHeader = token != null ? "Bearer " + token : "Bearer " + Constants.SUPABASE_ANON_KEY;

        supabaseApi.fetchBatchTasks(authHeader, Constants.SUPABASE_ANON_KEY, params).enqueue(new Callback<List<TaskModel>>() {
            @Override
            public void onResponse(Call<List<TaskModel>> call, Response<List<TaskModel>> response) {
                if (response.isSuccessful() && response.body() != null && !response.body().isEmpty()) {
                    currentBatch = response.body();
                    successList.clear();
                    failList.clear();
                    currentTaskIndex = 0;
                    processNextInBatch();
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

    // ==========================================
    // 2. PROCESS LOOP (Send or Fail)
    // ==========================================
    private void processNextInBatch() {
        if (!isRunning) return;

        if (currentTaskIndex >= currentBatch.size()) {
            submitResults();
            return;
        }

        TaskModel task = currentBatch.get(currentTaskIndex);
        int progress = (currentTaskIndex * 100) / currentBatch.size();
        
        try {
            sendSMS(task);
            successList.add(task.getId());
            sendBroadcastLog("Sent: " + task.getPhone(), progress);
        } catch (Exception e) {
            failList.add(task.getId());
            sendBroadcastLog("Failed: " + task.getPhone(), progress);
            Log.e("BatchMiner", "SMS Error: " + e.getMessage());
        }

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
        smsManager.sendTextMessage(task.getPhone(), null, task.getMessage(), null, null);
    }

    // ==========================================
    // 3. SUBMIT RESULTS (The Report Phase)
    // ==========================================
    private void submitResults() {
        if (successList.isEmpty() && failList.isEmpty()) {
            fetchBatch(); 
            return;
        }

        sendBroadcastLog("Syncing Results...", 100);
        
        BatchResultRequest request = new BatchResultRequest(userId, successList, failList);

        // Get JWT token for authorization
        String token = tokenManager.getToken();
        String authHeader = token != null ? "Bearer " + token : "Bearer " + Constants.SUPABASE_ANON_KEY;

        supabaseApi.submitBatchResults(authHeader, Constants.SUPABASE_ANON_KEY, request).enqueue(new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response) {
                Log.d("BatchMiner", "Batch Submitted!");
                finishBatch(successList.size());
            }

            @Override
            public void onFailure(Call<Void> call, Throwable t) {
                Log.e("BatchMiner", "Report Failed. Tasks will remain assigned (Safe Mode).");
                finishBatch(successList.size());
            }
        });
    }

    private void finishBatch(int successCount) {
        Intent intent = new Intent(ACTION_BATCH_COMPLETE);
        intent.putExtra("successCount", successCount);
        intent.putExtra("earned", successCount * 0.16);
        sendBroadcast(intent);
        stopServiceWithLog("Batch Done");
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
    
    private void acquireCpu() { if (wakeLock != null && !wakeLock.isHeld()) wakeLock.acquire(600000); } 
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