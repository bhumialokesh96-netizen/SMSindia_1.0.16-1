package com.smsindia.app.services;

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
import android.os.CountDownTimer;
import android.os.IBinder;
import android.telephony.SmsManager;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Transaction;
import com.smsindia.app.R;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public class SmsMiningService extends Service {

    // --- CONFIGURATION ---
    public static final String ACTION_UPDATE_UI = "com.smsindia.UPDATE_UI";
    private static final String SENT_ACTION = "SMS_VERIFIED_SENT";
    private static final double REWARD = 0.16;
    private static final String CHANNEL_ID = "SMS_MINING_CHANNEL";

    // --- STATE ---
    private boolean isRunning = false;
    private boolean isAutoMode = false;
    private int selectedSubId = -1;
    private String userId;
    
    private FirebaseFirestore db;
    private CountDownTimer cooldownTimer;
    private BroadcastReceiver sentReceiver;

    @Override
    public void onCreate() {
        super.onCreate();
        db = FirebaseFirestore.getInstance();
        createNotificationChannel();
        registerInternalReceiver();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            if ("STOP_SERVICE".equals(intent.getAction())) {
                stopSelf();
                return START_NOT_STICKY;
            }

            selectedSubId = intent.getIntExtra("subId", -1);
            isAutoMode = intent.getBooleanExtra("autoMode", false);
            userId = intent.getStringExtra("userId");

            if (!isRunning) {
                isRunning = true;
                startForeground(1, getNotification("Mining Active", "Initializing Network..."));
                // Start the Loop
                fetchAndClaimTask(); 
            }
        }
        return START_STICKY;
    }

    // =====================================================================
    // 🚀 PHASE 1: FETCH & CLAIM (Concurrency Safe)
    // =====================================================================
    private void fetchAndClaimTask() {
        if (!isRunning) return;
        
        sendBroadcastUpdate("Scanning for tasks...", 0);
        updateNotification("Scanning...");

        // 1. Get a batch of 'pending' tasks (limit 10 to reduce traffic)
        db.collection("sms_tasks")
            .whereEqualTo("status", "pending")
            .limit(10) 
            .get()
            .addOnSuccessListener(snapshot -> {
                if (snapshot.isEmpty()) {
                    handleError("No Tasks Available. Retrying...");
                    return;
                }

                // 2. Randomly pick one to avoid collision with other users
                int randomIndex = new Random().nextInt(snapshot.size());
                DocumentSnapshot randomDoc = snapshot.getDocuments().get(randomIndex);
                
                // 3. Attempt to LOCK it
                attemptToLockTask(randomDoc.getId(), randomDoc.getString("phone"), randomDoc.getString("message"));
            })
            .addOnFailureListener(e -> handleError("Server Connection Failed"));
    }

    private void attemptToLockTask(String docId, String phone, String message) {
        DocumentReference taskRef = db.collection("sms_tasks").document(docId);

        db.runTransaction((Transaction.Function<Boolean>) transaction -> {
            DocumentSnapshot snapshot = transaction.get(taskRef);
            
            // SECURITY: Double Check status inside transaction
            String currentStatus = snapshot.getString("status");
            if (currentStatus == null || !currentStatus.equals("pending")) {
                return false; // ALREADY TAKEN by someone else
            }

            // LOCK IT: Set status to 'processing' so no one else gets it
            transaction.update(taskRef, "status", "processing");
            transaction.update(taskRef, "assigned_to", userId);
            transaction.update(taskRef, "start_time", FieldValue.serverTimestamp());
            
            return true; // Lock Acquired
            
        }).addOnSuccessListener(locked -> {
            if (locked) {
                // We own it. Send SMS.
                sendSecureSMS(phone, message, docId);
            } else {
                // Collision! Someone was faster. Retry immediately.
                fetchAndClaimTask();
            }
        }).addOnFailureListener(e -> handleError("Locking Failed"));
    }

    // =====================================================================
    // 🚀 PHASE 2: SEND SECURE SMS
    // =====================================================================
    private void sendSecureSMS(String phone, String message, String docId) {
        sendBroadcastUpdate("Sending to: " + phone, 10);
        updateNotification("Sending SMS...");

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
                Intent sent = new Intent(SENT_ACTION);
                sent.putExtra("phone", phone);
                sent.putExtra("docId", docId); // PASS ID TO RECEIVER

                // Unique Token to track specific message parts
                int uniqueToken = (int) System.currentTimeMillis() + i;
                
                PendingIntent pi = PendingIntent.getBroadcast(this, uniqueToken, sent, 
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                sentIntents.add(pi);
            }

            smsManager.sendMultipartTextMessage(phone, null, parts, sentIntents, null);

        } catch (Exception e) {
            handleError("SIM Card Error: " + e.getMessage());
            // Optional: Release lock here if needed
        }
    }

    // =====================================================================
    // 🚀 PHASE 3: VERIFY & REWARD (Atomic Banking)
    // =====================================================================
    private void registerInternalReceiver() {
        sentReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                int resultCode = getResultCode();
                String phone = intent.getStringExtra("phone");
                String docId = intent.getStringExtra("docId"); // Retrieve ID
                
                if (resultCode == Activity.RESULT_OK) {
                    // ✅ SUCCESS: SMS Left Phone
                    processRewardAndComplete(phone, docId);
                } else {
                    // ❌ FAILURE: Airplane Mode / Blocked
                    logFailure(phone, resultCode);
                    handleError("SMS Delivery Failed (Code " + resultCode + ")");
                }
            }
        };
        
        int flags = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ? Context.RECEIVER_NOT_EXPORTED : 0;
        registerReceiver(sentReceiver, new IntentFilter(SENT_ACTION), flags);
    }

    private void processRewardAndComplete(String phone, String docId) {
        sendBroadcastUpdate("Verifying & Crediting ₹" + REWARD + "...", 60);

        final DocumentReference userRef = db.collection("users").document(userId);
        final DocumentReference taskRef = db.collection("sms_tasks").document(docId);

        db.runTransaction((Transaction.Function<Void>) transaction -> {
            // 1. Get User Data
            DocumentSnapshot userSnap = transaction.get(userRef);
            Double currentBalance = userSnap.getDouble("balance");
            if (currentBalance == null) currentBalance = 0.0;

            // 2. Credit Money
            transaction.update(userRef, "balance", currentBalance + REWARD);
            transaction.update(userRef, "sms_count", FieldValue.increment(1));

            // 3. Create Audit Log
            DocumentReference logRef = userRef.collection("delivery_logs").document();
            Map<String, Object> log = new HashMap<>();
            log.put("phone", phone);
            log.put("status", "DELIVERED");
            log.put("amount", REWARD);
            log.put("timestamp", FieldValue.serverTimestamp());
            transaction.set(logRef, log);

            // 4. DELETE TASK (Prevents Re-sending)
            transaction.delete(taskRef);
            
            return null;
        }).addOnSuccessListener(aVoid -> {
            // Success!
            if (isAutoMode) {
                startCooldown();
            } else {
                sendBroadcastUpdate("Done! ₹" + REWARD + " Added.", 100);
                stopSelf();
            }
        }).addOnFailureListener(e -> {
            handleError("Transaction Failed. Check Internet.");
        });
    }

    private void logFailure(String phone, int errorCode) {
        Map<String, Object> log = new HashMap<>();
        log.put("phone", phone);
        log.put("status", "FAILED");
        log.put("errorCode", errorCode);
        log.put("timestamp", FieldValue.serverTimestamp());
        db.collection("users").document(userId).collection("delivery_logs").add(log);
    }

    // =====================================================================
    // 🚀 PHASE 4: COOLDOWN & UTILS
    // =====================================================================
    private void startCooldown() {
        updateNotification("Cooling down...");
        
        cooldownTimer = new CountDownTimer(15000, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                int seconds = (int) (millisUntilFinished / 1000);
                int progress = 50 + (int) ((15000 - millisUntilFinished) * 50 / 15000);
                sendBroadcastUpdate("Next SMS in: " + seconds + "s", progress);
            }

            @Override
            public void onFinish() {
                if (isRunning) fetchAndClaimTask(); // RESTART LOOP
            }
        }.start();
    }

    private void handleError(String error) {
        sendBroadcastUpdate("Error: " + error, 0);
        updateNotification("Waiting (Error)");
        
        // If Auto Mode, Retry in 30 seconds (Longer delay for safety)
        if (isAutoMode) {
            new android.os.Handler(getMainLooper()).postDelayed(this::fetchAndClaimTask, 30000);
        } else {
            stopSelf();
        }
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
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setOngoing(true)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, "STOP", stopPI)
                .setSilent(true)
                .build();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "Mining Service", NotificationManager.IMPORTANCE_LOW);
            getSystemService(NotificationManager.class).createNotificationChannel(channel);
        }
    }

    @Override
    public void onDestroy() {
        isRunning = false;
        if (cooldownTimer != null) cooldownTimer.cancel();
        if (sentReceiver != null) unregisterReceiver(sentReceiver);
        sendBroadcastUpdate("Service Stopped", 0);
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) { return null; }
}
