package com.smsindia.app.workers;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.net.Uri;
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

import java.util.ArrayList;
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
    private static final double REWARD = 0.16;
    private static final String CHANNEL_ID = "SMS_MINING_CHANNEL";

    private final Set<String> processedTaskIds = new HashSet<>();
    private boolean isRunning = false;
    private int selectedSubId = -1;
    private String userId;
    
    // 🔢 COUNTERS
    private int tasksProcessedInBatch = 0;
    private int successCount = 0; // ✅ Tracks successful sends
    private final int BATCH_LIMIT = 10;
    
    private FirebaseFirestore db;
    private SupabaseApi supabaseApi;
    private BroadcastReceiver sentReceiver;
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
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SMSMiner::CoreWakelock");
        
        createNotificationChannel();
        registerSentReceiver();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            if ("STOP_SERVICE".equals(intent.getAction())) {
                stopServiceSafely();
                return START_NOT_STICKY;
            }
            selectedSubId = intent.getIntExtra("subId", -1);
            userId = intent.getStringExtra("userId");

            if (!isRunning) {
                isRunning = true;
                tasksProcessedInBatch = 0;
                successCount = 0; // ✅ Reset success count
                startForeground(1, getNotification("Mining Active", "Starting Batch..."));
                fetchAndClaimTask(); 
            }
        }
        return START_STICKY;
    }

    private void fetchAndClaimTask() {
        if (!isRunning) return;

        if (tasksProcessedInBatch >= BATCH_LIMIT) {
            sendBroadcastUpdate("Syncing...", 100);
            sendBatchCompleteSignal(); // ✅ Sends final count to UI
            stopServiceSafely();
            return;
        }

        acquireCpu();
        int progressPercent = (tasksProcessedInBatch * 100) / BATCH_LIMIT;
        sendBroadcastUpdate("Task " + (tasksProcessedInBatch + 1) + "/10", progressPercent);

        supabaseApi.getTask(SUPABASE_KEY, "Bearer " + SUPABASE_KEY)
            .enqueue(new Callback<List<TaskModel>>() {
                @Override
                public void onResponse(Call<List<TaskModel>> call, Response<List<TaskModel>> response) {
                    if (response.isSuccessful() && response.body() != null && !response.body().isEmpty()) {
                        TaskModel task = response.body().get(0);
                        if (processedTaskIds.contains(task.id)) { handleSmartSleep("Duplicate"); return; }
                        
                        processedTaskIds.add(task.id);
                        if(processedTaskIds.size() > 50) processedTaskIds.clear();

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
                smsManager = getSystemService(SmsManager.class).createForSubscriptionId(selectedSubId);
            } else {
                smsManager = SmsManager.getSmsManagerForSubscriptionId(selectedSubId);
            }

            ArrayList<String> parts = smsManager.divideMessage(message);
            ArrayList<PendingIntent> sentIntents = new ArrayList<>();

            for (int i = 0; i < parts.size(); i++) {
                if (i == parts.size() - 1) {
                    Intent sent = new Intent(SENT_ACTION);
                    sent.putExtra("phone", phone);
                    sent.putExtra("taskId", taskId);
                    sent.putExtra("msgBody", message); 
                    int token = (int) System.currentTimeMillis();
                    sentIntents.add(PendingIntent.getBroadcast(this, token, sent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
                } else {
                    sentIntents.add(null);
                }
            }
            smsManager.sendMultipartTextMessage(phone, null, parts, sentIntents, null);
        } catch (Exception e) {
            releaseCpu();
            handleSmartSleep("SIM Error");
        }
    }

    private void registerSentReceiver() {
        sentReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (getResultCode() == Activity.RESULT_OK) {
                    successCount++; // ✅ Increment Success Count
                    sendBroadcastUpdate("Crediting...", (tasksProcessedInBatch * 100) / BATCH_LIMIT);
                    processReward(intent.getStringExtra("phone"), intent.getStringExtra("taskId"));
                } else {
                    releaseCpu();
                    nextTaskInBatch();
                }
            }
        };
        int flags = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ? Context.RECEIVER_NOT_EXPORTED : 0;
        registerReceiver(sentReceiver, new IntentFilter(SENT_ACTION), flags);
    }

    private void processReward(String phone, String taskId) {
        if (userId == null) return;
        final DocumentReference userRef = db.collection("users").document(userId);
        
        db.runTransaction((Transaction.Function<Void>) transaction -> {
            DocumentSnapshot userSnap = transaction.get(userRef);
            if (!userSnap.exists()) { return null; }
            
            Double currentBalance = userSnap.getDouble("balance");
            if (currentBalance == null) currentBalance = 0.0;
            
            transaction.update(userRef, "balance", currentBalance + REWARD);
            transaction.update(userRef, "sms_count", FieldValue.increment(1));
            
            DocumentReference logRef = userRef.collection("delivery_logs").document(taskId);
            Map<String, Object> log = new HashMap<>();
            log.put("phone", phone);
            log.put("status", "SENT_OK");
            log.put("amount", REWARD);
            log.put("timestamp", FieldValue.serverTimestamp());
            transaction.set(logRef, log);
            return null;
        }).addOnSuccessListener(aVoid -> {
            nextTaskInBatch(); 
        }).addOnFailureListener(e -> nextTaskInBatch());
    }

    private void nextTaskInBatch() {
        tasksProcessedInBatch++; 
        releaseCpu();
        new Handler(getMainLooper()).postDelayed(this::fetchAndClaimTask, 1500);
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
        // ✅ Pass final stats to UI
        intent.putExtra("successCount", successCount);
        intent.putExtra("earned", successCount * REWARD);
        sendBroadcast(intent);
    }
    
    // Standard Utils
    private void acquireCpu() { if (wakeLock != null && !wakeLock.isHeld()) wakeLock.acquire(10*60*1000L); }
    private void releaseCpu() { if (wakeLock != null && wakeLock.isHeld()) wakeLock.release(); }
    private void updateNotification(String status) { 
        NotificationManager nm = getSystemService(NotificationManager.class); 
        if (nm != null) nm.notify(1, getNotification("Mining Active", status)); 
    }
    private Notification getNotification(String title, String content) {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(title)
                .setContentText(content)
                .setSmallIcon(R.drawable.ic_launcher) // ✅ Fixed Icon
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
    private void stopServiceSafely() { isRunning = false; releaseCpu(); stopSelf(); }
    @Override public void onDestroy() { isRunning = false; releaseCpu(); if(sentReceiver!=null) unregisterReceiver(sentReceiver); super.onDestroy(); }
    @Nullable @Override public IBinder onBind(Intent intent) { return null; }
}

