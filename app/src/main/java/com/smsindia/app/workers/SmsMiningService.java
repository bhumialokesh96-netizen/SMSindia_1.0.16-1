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

import com.smsindia.app.service.SupabaseApi;
import com.smsindia.app.service.TaskModel;

import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Transaction;
import com.smsindia.app.R;

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
    
    // --- LOGIC VARS ---
    private int tasksProcessedInBatch = 0;
    private int successCount = 0;
    private int consecutiveFailures = 0; 
    private final int BATCH_LIMIT = 10;
    private final int MAX_CONSECUTIVE_FAILURES = 3; 
    
    private FirebaseFirestore db;
    private SupabaseApi supabaseApi;
    
    private BroadcastReceiver sentReceiver;
    private BroadcastReceiver deliveredReceiver;
    private PowerManager.WakeLock wakeLock;
    
    private long currentRetryDelay = 1000;
    private final long MAX_RETRY_DELAY = 60000; 

    @Override
    public void onCreate() {
        super.onCreate();
        db = FirebaseFirestore.getInstance();
        Retrofit retrofit = new Retrofit.Builder().baseUrl(SUPABASE_URL).addConverterFactory(GsonConverterFactory.create()).build();
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

        // Auto-Stop Check
        if (consecutiveFailures >= MAX_CONSECUTIVE_FAILURES) {
            stopServiceSafely("Paused: Quota Exceeded");
            return;
        }

        if (tasksProcessedInBatch >= BATCH_LIMIT) {
            sendBroadcastUpdate("Syncing...", 100);
            sendBatchCompleteSignal();
            stopServiceSafely("Batch Complete");
            return;
        }

        acquireCpu();
        int progressPercent = (tasksProcessedInBatch * 100) / BATCH_LIMIT;
        sendBroadcastUpdate("Task " + (tasksProcessedInBatch + 1) + "/10", progressPercent);

        // RPC Call: Marks as 'processing' but DOES NOT delete
        supabaseApi.getTask(SUPABASE_KEY, "Bearer " + SUPABASE_KEY)
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
                        handleSmartSleep("No Tasks");
                    }
                }

                @Override
                public void onFailure(Call<List<TaskModel>> call, Throwable t) {
                    releaseCpu();
                    handleSmartSleep("Net Error");
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

            Intent deliveryIntent = new Intent(DELIVERED_ACTION);
            deliveryIntent.putExtra("taskId", taskId);
            deliveryIntent.setPackage(getPackageName());
            PendingIntent deliveryPI = PendingIntent.getBroadcast(this, uniqueRequestCode, deliveryIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

            smsManager.sendTextMessage(phone, null, message, sentPI, deliveryPI);

        } catch (Exception e) {
            consecutiveFailures++;
            returnTaskToQueue(taskId); // Release on crash
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
                        sendBroadcastUpdate("Sent! Processing...", (tasksProcessedInBatch * 100) / BATCH_LIMIT);
                        
                        // 1. Give User Money
                        processReward(phone, taskId, "SENT_WAITING_DLR"); 
                        
                        // 2. Mark Supabase as 'sent' (DO NOT DELETE)
                        markTaskAsSent(taskId);
                        break;
                        
                    case SmsManager.RESULT_ERROR_NO_SERVICE:
                    case SmsManager.RESULT_ERROR_GENERIC_FAILURE:
                    default:
                        // ❌ FAILURE
                        consecutiveFailures++;
                        Log.e("SMS_MINER", "SMS Failed. Count: " + consecutiveFailures);
                        
                        // 1. Return to 'pending' so others can try
                        returnTaskToQueue(taskId);
                        
                        if (consecutiveFailures >= MAX_CONSECUTIVE_FAILURES) {
                            stopServiceSafely("Stopped: SMS Limit Reached?");
                        } else {
                            handleSmartSleep("Send Failed");
                        }
                        releaseCpu();
                        break;
                }
            }
        };

        deliveredReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (getResultCode() == Activity.RESULT_OK) {
                    updateTaskStatus(intent.getStringExtra("taskId"), "DELIVERED_CONFIRMED");
                }
            }
        };
        
        int flags = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) ? Context.RECEIVER_EXPORTED : 0;
        registerReceiver(sentReceiver, new IntentFilter(SENT_ACTION), flags);
        registerReceiver(deliveredReceiver, new IntentFilter(DELIVERED_ACTION), flags);
    }

    // --- SUPABASE HELPERS (UPDATED) ---

    // SUCCESS: Mark as 'sent'
    private void markTaskAsSent(String taskId) {
        Map<String, Object> body = new HashMap<>();
        body.put("status", "sent");
        
        supabaseApi.updateTask(SUPABASE_KEY, "Bearer " + SUPABASE_KEY, "eq." + taskId, body)
            .enqueue(new Callback<Void>() {
                @Override public void onResponse(Call<Void> c, Response<Void> r) {}
                @Override public void onFailure(Call<Void> c, Throwable t) {}
            });
    }

    // FAILURE: Reset to 'pending'
    private void returnTaskToQueue(String taskId) {
        Map<String, Object> body = new HashMap<>();
        body.put("status", "pending");
        body.put("locked_at", null); // Clear lock time
        
        supabaseApi.updateTask(SUPABASE_KEY, "Bearer " + SUPABASE_KEY, "eq." + taskId, body)
            .enqueue(new Callback<Void>() {
                @Override public void onResponse(Call<Void> c, Response<Void> r) {}
                @Override public void onFailure(Call<Void> c, Throwable t) {}
            });
    }

    // --- GENERIC HELPERS ---

    private void processReward(String phone, String taskId, String status) {
        if (userId == null) { nextTaskInBatch(); return; }
        final DocumentReference userRef = db.collection("users").document(userId);
        
        db.runTransaction((Transaction.Function<Void>) transaction -> {
            DocumentSnapshot userSnap = transaction.get(userRef);
            if (!userSnap.exists()) return null;
            
            Double bal = userSnap.getDouble("balance");
            transaction.update(userRef, "balance", (bal==null?0.0:bal) + REWARD);
            transaction.update(userRef, "sms_count", FieldValue.increment(1));
            
            Map<String, Object> log = new HashMap<>();
            log.put("phone", phone);
            log.put("status", status);
            log.put("amount", REWARD);
            log.put("timestamp", FieldValue.serverTimestamp());
            transaction.set(userRef.collection("delivery_logs").document(taskId), log);
            return null;
        }).addOnCompleteListener(t -> nextTaskInBatch());
    }

    private void updateTaskStatus(String taskId, String newStatus) {
        if(userId == null || taskId == null) return;
        db.collection("users").document(userId).collection("delivery_logs").document(taskId)
          .update("status", newStatus);
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
        currentRetryDelay = Math.min(currentRetryDelay * 2, MAX_RETRY_DELAY);
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
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if(nm != null) {
            Notification n = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Mining Stopped")
                .setContentText(reason)
                .setSmallIcon(R.drawable.ic_launcher)
                .setAutoCancel(true)
                .build();
            nm.notify(2, n);
        }
        stopSelf();
    }
    
    @Override 
    public void onDestroy() { 
        isRunning = false; 
        releaseCpu(); 
        try { 
            if(sentReceiver!=null) unregisterReceiver(sentReceiver); 
            if(deliveredReceiver!=null) unregisterReceiver(deliveredReceiver);
        } catch(Exception e){}
        super.onDestroy(); 
    }
    
    @Nullable @Override public IBinder onBind(Intent intent) { return null; }
}
