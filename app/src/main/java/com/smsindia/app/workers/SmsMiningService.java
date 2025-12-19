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

    private static final String SUPABASE_URL = "https://appfwrpynfxfpcvpavso.supabase.co";
    // ⚠️ Ideally, keep this key secure
    private static final String SUPABASE_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImFwcGZ3cnB5bmZ4ZnBjdnBhdnNvIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NjIwOTQ2MTQsImV4cCI6MjA3NzY3MDYxNH0.Z-BMBjME8MVK5MS2KBgcCDgR7kXvDEjtcHrVfIUvwZY";

    public static final String ACTION_UPDATE_UI = "com.smsindia.UPDATE_UI";
    public static final String ACTION_BATCH_COMPLETE = "com.smsindia.BATCH_COMPLETE";
    
    private static final String SENT_ACTION = "SMS_SENT_CHECK";
    private static final String DELIVERED_ACTION = "SMS_DELIVERED_CHECK";
    
    private static final double REWARD = 0.16;
    private static final String CHANNEL_ID = "SMS_MINING_CHANNEL";

    private final Set<String> processedTaskIds = new HashSet<>();
    private boolean isRunning = false;
    private int selectedSubId = -1;
    private String userId; 
    
    private int tasksProcessedInBatch = 0;
    private int successCount = 0;
    private int consecutiveFailures = 0; 
    private final int BATCH_LIMIT = 10;
    private final int MAX_CONSECUTIVE_FAILURES = 3; 
    
    private SupabaseApi supabaseApi;
    private BroadcastReceiver sentReceiver;
    private PowerManager.WakeLock wakeLock;
    private long currentRetryDelay = 1000;

    @Override
    public void onCreate() {
        super.onCreate();
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(SUPABASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build();
        supabaseApi = retrofit.create(SupabaseApi.class);
        
        PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        if(powerManager != null) {
            wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SMSMiner::CoreWakelock");
        }
        createNotificationChannel();
        registerReceivers();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            if ("STOP_SERVICE".equals(intent.getAction())) {
                stopServiceSafely("User Stopped");
                return START_NOT_STICKY;
            }
            selectedSubId = intent.getIntExtra("subId", -1);
            userId = intent.getStringExtra("userId"); 

            if (!isRunning) {
                isRunning = true;
                tasksProcessedInBatch = 0;
                successCount = 0;
                consecutiveFailures = 0;
                startForeground(1, getNotification("Mining Active", "Starting Batch..."));
                fetchAndClaimTask(); 
            }
        }
        return START_STICKY;
    }

    private void fetchAndClaimTask() {
        if (!isRunning) return;

        if (consecutiveFailures >= MAX_CONSECUTIVE_FAILURES) {
            stopServiceSafely("Paused: Quota Exceeded");
            return;
        }

        if (tasksProcessedInBatch >= BATCH_LIMIT) {
            sendBatchCompleteSignal();
            stopServiceSafely("Batch Complete");
            return;
        }

        acquireCpu();
        int progressPercent = (tasksProcessedInBatch * 100) / BATCH_LIMIT;
        sendBroadcastUpdate("Task " + (tasksProcessedInBatch + 1) + "/10", progressPercent);

        // Fetch task using RPC (Prevents getting same task twice)
        supabaseApi.getOneTask(SUPABASE_KEY, "Bearer " + SUPABASE_KEY)
            .enqueue(new Callback<List<TaskModel>>() {
                @Override
                public void onResponse(Call<List<TaskModel>> call, Response<List<TaskModel>> response) {
                    if (response.isSuccessful() && response.body() != null && !response.body().isEmpty()) {
                        TaskModel task = response.body().get(0);
                        if (processedTaskIds.contains(task.id)) { handleSmartSleep("Duplicate"); return; }
                        
                        processedTaskIds.add(task.id);
                        currentRetryDelay = 1000; 
                        sendSmsWithDelayCheck(task.phone, task.message, task.id);
                    } else {
                        releaseCpu();
                        handleSmartSleep("No Tasks Available");
                    }
                }

                @Override
                public void onFailure(Call<List<TaskModel>> call, Throwable t) {
                    releaseCpu();
                    handleSmartSleep("Network Error");
                }
            });
    }

    private void sendSmsWithDelayCheck(String phone, String message, String taskId) {
        try {
            SmsManager smsManager;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                smsManager = getSystemService(SmsManager.class);
                if(selectedSubId != -1) smsManager = smsManager.createForSubscriptionId(selectedSubId);
            } else {
                if(selectedSubId != -1) smsManager = SmsManager.getSmsManagerForSubscriptionId(selectedSubId);
                else smsManager = SmsManager.getDefault();
            }

            int uniqueRequestCode = taskId.hashCode();
            
            Intent sentIntent = new Intent(SENT_ACTION);
            sentIntent.putExtra("phone", phone);
            sentIntent.putExtra("taskId", taskId);
            sentIntent.setPackage(getPackageName());
            PendingIntent sentPI = PendingIntent.getBroadcast(this, uniqueRequestCode, sentIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

            smsManager.sendTextMessage(phone, null, message, sentPI, null);

        } catch (Exception e) {
            consecutiveFailures++;
            returnTaskToQueue(taskId); 
            releaseCpu();
            handleSmartSleep("SIM Error");
        }
    }

    private void registerReceivers() {
        sentReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String taskId = intent.getStringExtra("taskId");
                String phone = intent.getStringExtra("phone");
                
                switch (getResultCode()) {
                    case Activity.RESULT_OK:
                        // ✅ SUCCESS
                        consecutiveFailures = 0; 
                        successCount++;
                        sendBroadcastUpdate("Sent! Adding Reward...", (tasksProcessedInBatch * 100) / BATCH_LIMIT);
                        
                        // 🚨 CRITICAL CHANGE: ONLY CALL THIS. DO NOT UPDATE STATUS MANUALLY.
                        processReward(phone, taskId); 
                        break;
                        
                    default:
                        // ❌ FAILURE
                        consecutiveFailures++;
                        returnTaskToQueue(taskId);
                        releaseCpu();
                        handleSmartSleep("SMS Failed");
                        break;
                }
            }
        };

        int flags = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) ? Context.RECEIVER_EXPORTED : 0;
        registerReceiver(sentReceiver, new IntentFilter(SENT_ACTION), flags);
    }

    // ✅ UPDATED: Added Logging and Type Safety
    private void processReward(String phone, String taskId) {
        if (userId == null || userId.isEmpty()) { 
            Log.e("SMS_MINER", "❌ Error: UserId is missing. Cannot claim reward.");
            nextTaskInBatch(); 
            return; 
        }

        Map<String, Object> params = new HashMap<>();
        params.put("p_user_id", userId);   
        params.put("p_task_id", taskId);   
        params.put("p_phone", phone);      
        params.put("p_amount", Double.valueOf(REWARD)); // FORCE DOUBLE TYPE

        Log.d("SMS_MINER", "📡 Sending Reward Request for " + phone);

        supabaseApi.claimReward("Bearer " + SUPABASE_KEY, SUPABASE_KEY, params)
            .enqueue(new Callback<Void>() {
                @Override
                public void onResponse(Call<Void> call, Response<Void> response) {
                    if (response.isSuccessful()) {
                        Log.d("SMS_MINER", "✅ SUCCESS! Reward Added.");
                    } else {
                        Log.e("SMS_MINER", "❌ SERVER ERROR: " + response.code() + " " + response.message());
                        try {
                             if(response.errorBody() != null) Log.e("SMS_MINER", "Error Body: " + response.errorBody().string());
                        } catch(Exception e) {}
                    }
                    nextTaskInBatch();
                }

                @Override
                public void onFailure(Call<Void> call, Throwable t) {
                    Log.e("SMS_MINER", "❌ NETWORK FAIL: " + t.getMessage());
                    nextTaskInBatch();
                }
            });
    }

    // FAILURE: Reset to 'pending'
    private void returnTaskToQueue(String taskId) {
        Map<String, Object> body = new HashMap<>();
        body.put("status", "pending");
        
        supabaseApi.updateTask(SUPABASE_KEY, "Bearer " + SUPABASE_KEY, "eq." + taskId, body)
            .enqueue(new Callback<Void>() {
                @Override public void onResponse(Call<Void> c, Response<Void> r) {}
                @Override public void onFailure(Call<Void> c, Throwable t) {}
            });
    }

    private void nextTaskInBatch() {
        tasksProcessedInBatch++; 
        releaseCpu();
        if (consecutiveFailures < MAX_CONSECUTIVE_FAILURES) {
            new Handler(getMainLooper()).postDelayed(this::fetchAndClaimTask, 1500);
        }
    }

    private void handleSmartSleep(String reason) {
        sendBroadcastUpdate("Retry: " + reason, (tasksProcessedInBatch * 100) / BATCH_LIMIT);
        currentRetryDelay = Math.min(currentRetryDelay * 2, 60000);
        new Handler(getMainLooper()).postDelayed(this::fetchAndClaimTask, currentRetryDelay);
    }

    private void sendBroadcastUpdate(String log, int progress) {
        Intent intent = new Intent(ACTION_UPDATE_UI);
        intent.putExtra("log", log);
        intent.putExtra("progress", progress);
        sendBroadcast(intent);
    }

    private void sendBatchCompleteSignal() {
        Intent intent = new Intent(ACTION_BATCH_COMPLETE);
        intent.putExtra("successCount", successCount);
        intent.putExtra("earned", successCount * REWARD);
        sendBroadcast(intent);
    }
    
    private void acquireCpu() { if (wakeLock != null && !wakeLock.isHeld()) wakeLock.acquire(10*60*1000L); }
    private void releaseCpu() { if (wakeLock != null && wakeLock.isHeld()) wakeLock.release(); }
    
    private Notification getNotification(String title, String content) {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(title)
                .setContentText(content)
                .setSmallIcon(R.drawable.ic_launcher)
                .setOngoing(true)
                .setSilent(true)
                .build();
    }
    
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "Mining", NotificationManager.IMPORTANCE_LOW);
            getSystemService(NotificationManager.class).createNotificationChannel(channel);
        }
    }
    
    private void stopServiceSafely(String reason) {
        isRunning = false;
        releaseCpu();
        stopSelf();
    }
    
    @Override 
    public void onDestroy() { 
        isRunning = false; 
        releaseCpu(); 
        try { if(sentReceiver!=null) unregisterReceiver(sentReceiver); } catch(Exception e){}
        super.onDestroy(); 
    }
    
    @Nullable @Override public IBinder onBind(Intent intent) { return null; }
}
