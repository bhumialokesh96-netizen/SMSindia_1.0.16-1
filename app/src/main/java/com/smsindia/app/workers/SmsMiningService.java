package com.smsindia.app.service;

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

    // 🔴 SYSTEM CONFIGURATION
    private static final String SUPABASE_URL = "https://appfwrpynfxfpcvpavso.supabase.co";
    private static final String SUPABASE_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImFwcGZ3cnB5bmZ4ZnBjdnBhdnNvIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NjIwOTQ2MTQsImV4cCI6MjA3NzY3MDYxNH0.Z-BMBjME8MVK5MS2KBgcCDgR7kXvDEjtcHrVfIUvwZY";
    
    public static final String ACTION_UPDATE_UI = "com.smsindia.UPDATE_UI";
    private static final String SENT_ACTION = "SMS_VERIFIED_SENT";
    private static final double REWARD = 0.16;
    private static final String CHANNEL_ID = "SMS_MINING_CHANNEL";

    // 🛡️ ANTI-SPAM MEMORY (Prevents Duplicate Sends)
    private final Set<String> processedTaskIds = new HashSet<>();

    private boolean isRunning = false;
    private boolean isAutoMode = false;
    private int selectedSubId = -1;
    private String userId;
    
    private FirebaseFirestore db;
    private SupabaseApi supabaseApi;
    private BroadcastReceiver sentReceiver;
    private PowerManager.WakeLock wakeLock;
    
    // 🧠 SMART BRAIN VARIABLES
    private long currentRetryDelay = 1000; // Start fast (1s)
    private final long MAX_RETRY_DELAY = 300000; // Max sleep (5 mins)

    @Override
    public void onCreate() {
        super.onCreate();
        db = FirebaseFirestore.getInstance();
        
        // 1. Init High-Speed Supabase Connection
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(SUPABASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build();
        supabaseApi = retrofit.create(SupabaseApi.class);

        // 2. Init Battery Manager
        PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SMSMiner::CoreWakelock");

        createNotificationChannel();
        registerStrictReceiver();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            if ("STOP_SERVICE".equals(intent.getAction())) {
                stopServiceSafely();
                return START_NOT_STICKY;
            }
            selectedSubId = intent.getIntExtra("subId", -1);
            isAutoMode = intent.getBooleanExtra("autoMode", false);
            userId = intent.getStringExtra("userId");

            if (!isRunning) {
                isRunning = true;
                startForeground(1, getNotification("Mining Active", "Initializing Safe Protocol..."));
                fetchAndClaimTask(); 
            }
        }
        return START_STICKY;
    }

    // =====================================================================
    // 🚀 PHASE 1: FETCH FROM SUPABASE (WITH DEDUPLICATION)
    // =====================================================================
    private void fetchAndClaimTask() {
        if (!isRunning) return;
        
        acquireCpu(); // Wake up CPU to network call

        sendBroadcastUpdate("Syncing with Cloud...", 5);

        supabaseApi.getTask(SUPABASE_KEY, "Bearer " + SUPABASE_KEY)
            .enqueue(new Callback<List<TaskModel>>() {
                @Override
                public void onResponse(Call<List<TaskModel>> call, Response<List<TaskModel>> response) {
                    if (response.isSuccessful() && response.body() != null && !response.body().isEmpty()) {
                        TaskModel task = response.body().get(0);
                        
                        // 🛡️ HIGH-TECH CHECK: Have we seen this ID before?
                        if (processedTaskIds.contains(task.id)) {
                            handleSmartSleep("Duplicate Task Prevented");
                            return;
                        }
                        
                        // Add to memory (Prevent loops)
                        processedTaskIds.add(task.id);
                        if(processedTaskIds.size() > 50) processedTaskIds.clear(); // Keep memory clean

                        currentRetryDelay = 1000; // Reset speed
                        sendStrictSMS(task.phone, task.message, task.id);
                    } else {
                        releaseCpu();
                        handleSmartSleep("No Tasks. Standing By.");
                    }
                }

                @Override
                public void onFailure(Call<List<TaskModel>> call, Throwable t) {
                    releaseCpu();
                    handleSmartSleep("Network Unstable. Retrying...");
                }
            });
    }

    // =====================================================================
    // 🚀 PHASE 2: SEND SMS (STRICT MULTIPART CHECK)
    // =====================================================================
    private void sendStrictSMS(String phone, String message, String taskId) {
        sendBroadcastUpdate("Processing: " + phone, 15);
        updateNotification("Sending Secure SMS...");

        try {
            SmsManager smsManager;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                smsManager = getSystemService(SmsManager.class).createForSubscriptionId(selectedSubId);
            } else {
                smsManager = SmsManager.getSmsManagerForSubscriptionId(selectedSubId);
            }

            ArrayList<String> parts = smsManager.divideMessage(message);
            ArrayList<PendingIntent> sentIntents = new ArrayList<>();

            // 🛡️ CRITICAL FIX: Only attach the "Money Trigger" to the FINAL part of the SMS.
            // This prevents double-paying for long messages.
            for (int i = 0; i < parts.size(); i++) {
                if (i == parts.size() - 1) {
                    // This is the LAST part. Attach the trigger.
                    Intent sent = new Intent(SENT_ACTION);
                    sent.putExtra("phone", phone);
                    sent.putExtra("taskId", taskId); 
                    
                    // Unique Request Code is vital
                    int uniqueToken = (int) System.currentTimeMillis(); 
                    PendingIntent pi = PendingIntent.getBroadcast(this, uniqueToken, sent, 
                            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                    sentIntents.add(pi);
                } else {
                    // Intermediate part. No trigger.
                    sentIntents.add(null);
                }
            }
            
            smsManager.sendMultipartTextMessage(phone, null, parts, sentIntents, null);
            
        } catch (Exception e) {
            releaseCpu();
            handleSmartSleep("SIM Error: " + e.getMessage());
        }
    }

    // =====================================================================
    // 🚀 PHASE 3: REWARD VERIFICATION (STRICT TOWER CHECK)
    // =====================================================================
    private void registerStrictReceiver() {
        sentReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String taskId = intent.getStringExtra("taskId");
                String phone = intent.getStringExtra("phone");

                // 🛡️ CHECK 1: Did the Tower accept the message?
                if (getResultCode() == Activity.RESULT_OK) {
                    if (taskId != null && phone != null) {
                        processReward(phone, taskId);
                    }
                } else {
                    // ❌ Failed (Airplane mode, No Balance, Rejected)
                    releaseCpu();
                    handleSmartSleep("SMS Failed (Code: " + getResultCode() + ")");
                }
            }
        };
        
        // 🛡️ SECURITY: Only allow internal system broadcasts
        int flags = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ? Context.RECEIVER_NOT_EXPORTED : 0;
        registerReceiver(sentReceiver, new IntentFilter(SENT_ACTION), flags);
    }

    private void processReward(String phone, String taskId) {
        sendBroadcastUpdate("Verifying Delivery...", 80);

        final DocumentReference userRef = db.collection("users").document(userId);

        db.runTransaction((Transaction.Function<Void>) transaction -> {
            DocumentSnapshot userSnap = transaction.get(userRef);
            Double currentBalance = userSnap.getDouble("balance");
            if (currentBalance == null) currentBalance = 0.0;

            // 1. Update Money
            transaction.update(userRef, "balance", currentBalance + REWARD);
            transaction.update(userRef, "sms_count", FieldValue.increment(1));

            // 2. Create Immutable Audit Log
            DocumentReference logRef = userRef.collection("delivery_logs").document(taskId); // Use TaskID as DocID to prevent dupes
            Map<String, Object> log = new HashMap<>();
            log.put("phone", phone);
            log.put("status", "CONFIRMED_SENT");
            log.put("amount", REWARD);
            log.put("timestamp", FieldValue.serverTimestamp());
            transaction.set(logRef, log);
            
            return null;
        }).addOnSuccessListener(aVoid -> {
            releaseCpu(); // Done with this cycle
            if (isAutoMode) {
                // Tiny pause to let modem cool down
                new Handler(getMainLooper()).postDelayed(this::fetchAndClaimTask, 2000);
            } else {
                stopServiceSafely();
            }
        }).addOnFailureListener(e -> {
            releaseCpu();
            handleSmartSleep("Transaction Failed");
        });
    }

    // =====================================================================
    // 🧠 SMART UTILS & BATTERY MANAGEMENT
    // =====================================================================
    
    private void acquireCpu() {
        if (wakeLock != null && !wakeLock.isHeld()) {
            wakeLock.acquire(10 * 60 * 1000L /*10 mins timeout*/);
        }
    }

    private void releaseCpu() {
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }
    }

    private void handleSmartSleep(String reason) {
        sendBroadcastUpdate("Idle: " + reason, 0);
        
        if (!isAutoMode) { stopServiceSafely(); return; }

        // Exponential Backoff: 1s -> 2s -> 4s... -> 5mins
        currentRetryDelay = Math.min(currentRetryDelay * 2, MAX_RETRY_DELAY);
        
        updateNotification("Waiting " + (currentRetryDelay/1000) + "s (" + reason + ")");
        
        new Handler(getMainLooper()).postDelayed(this::fetchAndClaimTask, currentRetryDelay);
    }

    private void sendBroadcastUpdate(String log, int progress) {
        Intent intent = new Intent(ACTION_UPDATE_UI);
        intent.putExtra("log", log);
        intent.putExtra("progress", progress);
        sendBroadcast(intent);
    }

    private void updateNotification(String status) {
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm != null) nm.notify(1, getNotification("Mining Active", status));
    }

    private Notification getNotification(String title, String content) {
        Intent stopIntent = new Intent(this, SmsMiningService.class);
        stopIntent.setAction("STOP_SERVICE");
        PendingIntent stopPI = PendingIntent.getService(this, 0, stopIntent, PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(title)
                .setContentText(content)
                .setSmallIcon(R.drawable.ic_launcher)
                .setOngoing(true)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, "STOP", stopPI)
                .setSilent(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "Mining Service", NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("Shows current SMS Mining status");
            getSystemService(NotificationManager.class).createNotificationChannel(channel);
        }
    }

    private void stopServiceSafely() {
        isRunning = false;
        releaseCpu();
        stopSelf();
    }

    @Override
    public void onDestroy() {
        isRunning = false;
        releaseCpu();
        if (sentReceiver != null) unregisterReceiver(sentReceiver);
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) { return null; }
}
