package com.smsindia.app.workers;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.telephony.SmsManager;
import android.util.Log;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.smsindia.app.R;
import com.smsindia.app.service.ClaimRequest;
import com.smsindia.app.service.SupabaseApi;
import com.smsindia.app.service.TaskModel;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
    
    private static final String SENT_ACTION = "SMS_SENT_CHECK";
    private static final double REWARD_AMOUNT = 0.16;
    private static final String CHANNEL_ID = "SMS_MINING_CHANNEL";

    // --- STATE VARIABLES ---
    private SupabaseApi supabaseApi;
    private PowerManager.WakeLock wakeLock;
    private BroadcastReceiver sentReceiver;
    
    private boolean isRunning = false;
    private int selectedSubId = -1;
    private String userId; 
    
    private int tasksDone = 0;
    private int successCount = 0;
    private final int BATCH_SIZE = 10;
    
    private final Set<String> processedIds = new HashSet<>();

    @Override
    public void onCreate() {
        super.onCreate();
        // 1. Init API
        supabaseApi = new Retrofit.Builder()
                .baseUrl(SUPABASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(SupabaseApi.class);
        
        // 2. Init WakeLock (Prevents CPU from sleeping during mining)
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        if (pm != null) wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SMSMiner::Lock");
        
        // 3. Init System
        createNotificationChannel();
        registerReceivers();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && "STOP_SERVICE".equals(intent.getAction())) {
            stopServiceWithLog("User Stopped");
            return START_NOT_STICKY;
        }

        // 4. PERSISTENCE: Restore UserId if service was restarted
        if (intent != null && intent.hasExtra("userId")) {
            userId = intent.getStringExtra("userId");
            getSharedPreferences("SMS_PREFS", MODE_PRIVATE).edit().putString("saved_uid", userId).apply();
        } else {
            userId = getSharedPreferences("SMS_PREFS", MODE_PRIVATE).getString("saved_uid", null);
        }

        if (intent != null) selectedSubId = intent.getIntExtra("subId", -1);

        if (!isRunning) {
            isRunning = true;
            tasksDone = 0;
            successCount = 0;
            startForeground(1, getNotification("Mining Active", "Initializing..."));
            
            if (userId != null) {
                fetchNextTask();
            } else {
                stopServiceWithLog("Error: No User ID");
            }
        }
        return START_STICKY;
    }

    // ==========================================
    // STEP 1: FETCH TASK
    // ==========================================
    private void fetchNextTask() {
        if (!isRunning) return;
        
        if (tasksDone >= BATCH_SIZE) {
            finishBatch();
            return;
        }

        acquireCpu();
        sendBroadcastLog("Fetching Task " + (tasksDone + 1) + "...", (tasksDone * 100) / BATCH_SIZE);

        supabaseApi.getOneTask(SUPABASE_KEY, "Bearer " + SUPABASE_KEY).enqueue(new Callback<List<TaskModel>>() {
            @Override
            public void onResponse(Call<List<TaskModel>> call, Response<List<TaskModel>> response) {
                if (response.isSuccessful() && response.body() != null && !response.body().isEmpty()) {
                    TaskModel task = response.body().get(0);
                    
                    if (processedIds.contains(task.id)) {
                        new Handler().postDelayed(() -> fetchNextTask(), 1500); // Skip Duplicate
                        return;
                    }
                    processedIds.add(task.id);
                    sendSMS(task);
                } else {
                    sendBroadcastLog("No Tasks. Waiting...", (tasksDone * 100) / BATCH_SIZE);
                    new Handler().postDelayed(() -> fetchNextTask(), 5000); // Wait 5s before retry
                }
            }

            @Override
            public void onFailure(Call<List<TaskModel>> call, Throwable t) {
                sendBroadcastLog("Network Error. Retrying...", (tasksDone * 100) / BATCH_SIZE);
                new Handler().postDelayed(() -> fetchNextTask(), 5000);
            }
        });
    }

    // ==========================================
    // STEP 2: SEND SMS
    // ==========================================
    private void sendSMS(TaskModel task) {
        try {
            SmsManager smsManager;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                smsManager = getSystemService(SmsManager.class);
                if (selectedSubId != -1) smsManager = smsManager.createForSubscriptionId(selectedSubId);
            } else {
                smsManager = SmsManager.getDefault();
                if (selectedSubId != -1) smsManager = SmsManager.getSmsManagerForSubscriptionId(selectedSubId);
            }

            Intent sentIntent = new Intent(SENT_ACTION);
            sentIntent.putExtra("taskId", task.id);
            sentIntent.putExtra("phone", task.phone);
            sentIntent.setPackage(getPackageName()); // Security
            
            PendingIntent sentPI = PendingIntent.getBroadcast(this, task.id.hashCode(), sentIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

            smsManager.sendTextMessage(task.phone, null, task.message, sentPI, null);
            sendBroadcastLog("Sending SMS...", (tasksDone * 100) / BATCH_SIZE);

        } catch (Exception e) {
            Log.e("SMS_MINER", "Send Error: " + e.getMessage());
            // Fail silently and move to next task
            next();
        }
    }

    // ==========================================
    // STEP 3: HANDLE SMS RESULT
    // ==========================================
    private void registerReceivers() {
        sentReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String taskId = intent.getStringExtra("taskId");
                String phone = intent.getStringExtra("phone");

                if (getResultCode() == Activity.RESULT_OK) {
                    successCount++;
                    sendBroadcastLog("Sent! Claiming Reward...", (tasksDone * 100) / BATCH_SIZE);
                    claimReward(taskId, phone);
                } else {
                    Log.e("SMS_MINER", "SMS Failed for: " + taskId);
                    next(); // SMS Failed, move to next
                }
            }
        };
        
        int flags = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) ? Context.RECEIVER_EXPORTED : 0;
        registerReceiver(sentReceiver, new IntentFilter(SENT_ACTION), flags);
    }

    // ==========================================
    // STEP 4: CLAIM REWARD (THE CRITICAL PART)
    // ==========================================
    private void claimReward(String taskId, String phone) {
        // Create Safe Request Object
        ClaimRequest request = new ClaimRequest(userId, taskId, phone, REWARD_AMOUNT);

        Log.d("SMS_MINER", "Claiming for User: " + userId + " Task: " + taskId);

        supabaseApi.claimReward("Bearer " + SUPABASE_KEY, SUPABASE_KEY, request).enqueue(new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response) {
                if (response.isSuccessful()) {
                    Log.d("SMS_MINER", "✅ Reward Claimed Successfully!");
                } else {
                    Log.e("SMS_MINER", "❌ Claim Failed: " + response.code());
                    try {
                         if(response.errorBody() != null) Log.e("SMS_MINER", "Err: " + response.errorBody().string());
                    } catch(Exception e){}
                }
                next(); // Move to next task regardless of result
            }

            @Override
            public void onFailure(Call<Void> call, Throwable t) {
                Log.e("SMS_MINER", "❌ Claim Net Error: " + t.getMessage());
                next();
            }
        });
    }

    private void next() {
        tasksDone++;
        releaseCpu(); // Release lock briefly
        new Handler().postDelayed(this::fetchNextTask, 1500);
    }

    private void finishBatch() {
        Intent intent = new Intent(ACTION_BATCH_COMPLETE);
        intent.putExtra("successCount", successCount);
        intent.putExtra("earned", successCount * REWARD_AMOUNT);
        sendBroadcast(intent);
        stopServiceWithLog("Batch Complete");
    }

    // --- UTILS ---
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
    
    private void acquireCpu() { if (wakeLock != null && !wakeLock.isHeld()) wakeLock.acquire(60000); }
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
        try { unregisterReceiver(sentReceiver); } catch (Exception e) {}
        super.onDestroy();
    }

    @Nullable @Override public IBinder onBind(Intent intent) { return null; }
}
