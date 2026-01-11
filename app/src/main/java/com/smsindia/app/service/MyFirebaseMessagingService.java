package com.smsindia.app.service;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;
import com.smsindia.app.MainActivity;
import com.smsindia.app.R;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * ✅ Handles push notifications from Firebase
 * Use to trigger remote SMS syncs or alerts.
 */
public class MyFirebaseMessagingService extends FirebaseMessagingService {

    private static final String TAG = "MyFirebaseMessaging";
    private static final String CHANNEL_ID = "firebase_channel";
    private static final AtomicInteger notificationId = new AtomicInteger(1);

    @Override
    public void onMessageReceived(@NonNull RemoteMessage message) {
        super.onMessageReceived(message);
        
        String title = message.getNotification() != null ? 
            message.getNotification().getTitle() : getString(R.string.notification_title_default);
        String body = message.getNotification() != null ? 
            message.getNotification().getBody() : getString(R.string.notification_body_default);

        Log.d(TAG, "Message received - Title: " + title + ", Body: " + body);
        showNotification(title, body);
    }

    @Override
    public void onNewToken(@NonNull String token) {
        super.onNewToken(token);
        Log.d(TAG, "New FCM token: " + token);
        // Optional: Save token to Firestore for admin control
        // You can add logic here to send the token to your backend
    }

    private void showNotification(String title, String msg) {
        // Check notification permission for Android 13+ (API 33+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) 
                    != PackageManager.PERMISSION_GRANTED) {
                Log.w(TAG, getString(R.string.notification_permission_required));
                return;
            }
        }
        
        createChannel();
        
        try {
            Intent i = new Intent(this, MainActivity.class);
            i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            PendingIntent pi = PendingIntent.getActivity(
                this, 
                0, 
                i, 
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
            );

            NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_stat_name)
                    .setContentTitle(title)
                    .setContentText(msg)
                    .setAutoCancel(true)
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .setContentIntent(pi);

            NotificationManagerCompat nm = NotificationManagerCompat.from(this);
            // Use atomic counter instead of System.currentTimeMillis() to avoid collisions
            int id = notificationId.getAndIncrement();
            // Reset counter if it gets too large
            if (id > 10000) {
                notificationId.set(1);
            }
            nm.notify(id, builder.build());
            Log.d(TAG, "Notification shown with ID: " + id);
        } catch (SecurityException e) {
            Log.e(TAG, "SecurityException when showing notification", e);
        } catch (Exception e) {
            Log.e(TAG, "Error showing notification", e);
        }
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                NotificationChannel ch = new NotificationChannel(
                        CHANNEL_ID, 
                        getString(R.string.notification_channel_name), 
                        NotificationManager.IMPORTANCE_DEFAULT
                );
                ch.setDescription("Receives updates from Firebase Cloud Messaging");
                
                NotificationManager nm = getSystemService(NotificationManager.class);
                if (nm != null) {
                    nm.createNotificationChannel(ch);
                    Log.d(TAG, "Notification channel created");
                }
            } catch (Exception e) {
                Log.e(TAG, "Error creating notification channel", e);
            }
        }
    }
}