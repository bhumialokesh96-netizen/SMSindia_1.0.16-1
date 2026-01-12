package com.smsindia.app;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.telephony.SmsManager;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.smsindia.app.workers.SmsMiningService;

import io.flutter.embedding.engine.FlutterEngine;
import io.flutter.plugin.common.EventChannel;
import io.flutter.plugin.common.MethodChannel;

import java.util.HashMap;
import java.util.Map;

/**
 * Platform Channel Handler for SMS Operations
 * Bridges Flutter UI with Native Android SMS functionality
 */
public class SmsChannelHandler {
    
    private static final String SMS_CHANNEL = "com.smsindia.app/sms";
    private static final String SMS_EVENT_CHANNEL = "com.smsindia.app/sms_events";
    
    private final Context context;
    private final MethodChannel methodChannel;
    private final EventChannel eventChannel;
    private EventChannel.EventSink eventSink;
    
    public SmsChannelHandler(Context context, @NonNull FlutterEngine flutterEngine) {
        this.context = context;
        
        // Initialize Method Channel
        methodChannel = new MethodChannel(flutterEngine.getDartExecutor().getBinaryMessenger(), SMS_CHANNEL);
        methodChannel.setMethodCallHandler(this::onMethodCall);
        
        // Initialize Event Channel
        eventChannel = new EventChannel(flutterEngine.getDartExecutor().getBinaryMessenger(), SMS_EVENT_CHANNEL);
        eventChannel.setStreamHandler(new EventChannel.StreamHandler() {
            @Override
            public void onListen(Object arguments, EventChannel.EventSink events) {
                eventSink = events;
            }

            @Override
            public void onCancel(Object arguments) {
                eventSink = null;
            }
        });
    }
    
    /**
     * Handle method calls from Flutter
     */
    private void onMethodCall(io.flutter.plugin.common.MethodCall call, @NonNull MethodChannel.Result result) {
        switch (call.method) {
            case "checkSmsPermissions":
                result.success(checkSmsPermissions());
                break;
                
            case "requestSmsPermissions":
                // This would typically be handled by the Activity
                result.success(checkSmsPermissions());
                break;
                
            case "sendSms":
                String phoneNumber = call.argument("phoneNumber");
                String message = call.argument("message");
                result.success(sendSms(phoneNumber, message));
                break;
                
            case "startSmsMining":
                String userId = call.argument("userId");
                Integer simSlot = call.argument("simSlot");
                result.success(startSmsMining(userId, simSlot != null ? simSlot : -1));
                break;
                
            case "stopSmsMining":
                result.success(stopSmsMining());
                break;
                
            case "getServiceStatus":
                result.success(getServiceStatus());
                break;
                
            case "getAvailableTasksCount":
                // This would need to query the backend
                result.success(0);
                break;
                
            default:
                result.notImplemented();
                break;
        }
    }
    
    /**
     * Check if SMS permissions are granted
     */
    private boolean checkSmsPermissions() {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) 
                == PackageManager.PERMISSION_GRANTED;
    }
    
    /**
     * Send a single SMS
     */
    private boolean sendSms(String phoneNumber, String message) {
        try {
            SmsManager smsManager = SmsManager.getDefault();
            smsManager.sendTextMessage(phoneNumber, null, message, null, null);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
    
    /**
     * Start SMS Mining Service
     */
    private boolean startSmsMining(String userId, int simSlot) {
        try {
            Intent intent = new Intent(context, SmsMiningService.class);
            intent.putExtra("userId", userId);
            intent.putExtra("subId", simSlot);
            context.startService(intent);
            
            // Notify Flutter
            sendEvent("service_started", new HashMap<String, Object>() {{
                put("userId", userId);
            }});
            
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
    
    /**
     * Stop SMS Mining Service
     */
    private boolean stopSmsMining() {
        try {
            Intent intent = new Intent(context, SmsMiningService.class);
            intent.setAction("STOP_SERVICE");
            context.startService(intent);
            
            // Notify Flutter
            sendEvent("service_stopped", new HashMap<>());
            
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
    
    /**
     * Get service status
     */
    private Map<String, Object> getServiceStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("isRunning", false); // Would need to track actual status
        status.put("tasksCompleted", 0);
        status.put("earned", 0.0);
        return status;
    }
    
    /**
     * Send event to Flutter
     */
    public void sendEvent(String type, Map<String, Object> data) {
        if (eventSink != null) {
            Map<String, Object> event = new HashMap<>();
            event.put("type", type);
            event.putAll(data);
            eventSink.success(event);
        }
    }
    
    /**
     * Send progress update to Flutter
     */
    public void sendProgressUpdate(String message, int progress) {
        sendEvent("progress", new HashMap<String, Object>() {{
            put("message", message);
            put("progress", progress);
        }});
    }
    
    /**
     * Send batch complete event to Flutter
     */
    public void sendBatchComplete(int successCount, int failCount, double earned) {
        sendEvent("batch_complete", new HashMap<String, Object>() {{
            put("successCount", successCount);
            put("failCount", failCount);
            put("earned", earned);
        }});
    }
}
