package com.smsindia.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import androidx.annotation.NonNull;
import io.flutter.embedding.android.FlutterActivity;
import io.flutter.embedding.engine.FlutterEngine;

/**
 * Main Flutter Activity
 * This replaces all existing Java UI Activities (MainActivity, LoginActivity, etc.)
 * All UI is now handled by Flutter
 */
public class FlutterMainActivity extends FlutterActivity {
    
    private SmsChannelHandler smsChannelHandler;
    private SmsBroadcastReceiver smsBroadcastReceiver;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Register broadcast receiver for SMS service updates
        smsBroadcastReceiver = new SmsBroadcastReceiver();
        IntentFilter filter = new IntentFilter();
        filter.addAction("com.smsindia.UPDATE_UI");
        filter.addAction("com.smsindia.BATCH_COMPLETE");
        registerReceiver(smsBroadcastReceiver, filter);
    }
    
    @Override
    public void configureFlutterEngine(@NonNull FlutterEngine flutterEngine) {
        super.configureFlutterEngine(flutterEngine);
        
        // Initialize SMS Channel Handler
        smsChannelHandler = new SmsChannelHandler(this, flutterEngine);
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (smsBroadcastReceiver != null) {
            unregisterReceiver(smsBroadcastReceiver);
        }
    }
    
    /**
     * Broadcast Receiver to listen for SMS service updates
     * Forwards updates to Flutter via Event Channel
     */
    private class SmsBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            
            if ("com.smsindia.UPDATE_UI".equals(action)) {
                String log = intent.getStringExtra("log");
                int progress = intent.getIntExtra("progress", 0);
                
                if (smsChannelHandler != null) {
                    smsChannelHandler.sendProgressUpdate(log, progress);
                }
            } else if ("com.smsindia.BATCH_COMPLETE".equals(action)) {
                int successCount = intent.getIntExtra("successCount", 0);
                double earned = intent.getDoubleExtra("earned", 0.0);
                
                if (smsChannelHandler != null) {
                    smsChannelHandler.sendBatchComplete(successCount, 0, earned);
                }
            }
        }
    }
}
