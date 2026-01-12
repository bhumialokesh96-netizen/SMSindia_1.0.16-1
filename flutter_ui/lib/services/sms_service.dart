import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';
import 'dart:async';

/// Service to handle SMS operations via Platform Channel
/// Communicates with native Android SMS handling code
class SmsService extends ChangeNotifier {
  // Platform channel for SMS operations
  static const MethodChannel _channel = MethodChannel('com.smsindia.app/sms');
  static const EventChannel _eventChannel = EventChannel('com.smsindia.app/sms_events');
  
  // State
  bool _isServiceRunning = false;
  int _successCount = 0;
  int _failCount = 0;
  double _earned = 0.0;
  String _statusMessage = '';
  int _progress = 0;
  
  // SMS status stream
  Stream<Map<String, dynamic>>? _smsStatusStream;
  StreamSubscription<Map<String, dynamic>>? _streamSubscription;
  
  // Getters
  bool get isServiceRunning => _isServiceRunning;
  int get successCount => _successCount;
  int get failCount => _failCount;
  double get earned => _earned;
  String get statusMessage => _statusMessage;
  int get progress => _progress;
  
  SmsService() {
    _initializeEventChannel();
  }
  
  @override
  void dispose() {
    _streamSubscription?.cancel();
    super.dispose();
  }
  
  /// Initialize event channel to receive SMS status updates
  void _initializeEventChannel() {
    _smsStatusStream = _eventChannel
        .receiveBroadcastStream()
        .map((event) => Map<String, dynamic>.from(event as Map));
    
    _streamSubscription = _smsStatusStream?.listen((event) {
      final String type = event['type'] ?? '';
      
      switch (type) {
        case 'progress':
          _progress = event['progress'] ?? 0;
          _statusMessage = event['message'] ?? '';
          break;
        case 'batch_complete':
          _successCount = event['successCount'] ?? 0;
          _failCount = event['failCount'] ?? 0;
          _earned = (event['earned'] ?? 0.0).toDouble();
          _isServiceRunning = false;
          break;
        case 'service_started':
          _isServiceRunning = true;
          _statusMessage = 'SMS Mining Started';
          break;
        case 'service_stopped':
          _isServiceRunning = false;
          _statusMessage = 'SMS Mining Stopped';
          break;
      }
      
      notifyListeners();
    });
  }
  
  /// Check if SMS permissions are granted
  Future<bool> checkSmsPermissions() async {
    try {
      final bool result = await _channel.invokeMethod('checkSmsPermissions');
      return result;
    } on PlatformException catch (e) {
      debugPrint('Error checking SMS permissions: ${e.message}');
      return false;
    }
  }
  
  /// Request SMS permissions
  Future<bool> requestSmsPermissions() async {
    try {
      final bool result = await _channel.invokeMethod('requestSmsPermissions');
      return result;
    } on PlatformException catch (e) {
      debugPrint('Error requesting SMS permissions: ${e.message}');
      return false;
    }
  }
  
  /// Send a single SMS
  Future<bool> sendSms({
    required String phoneNumber,
    required String message,
  }) async {
    try {
      final bool result = await _channel.invokeMethod('sendSms', {
        'phoneNumber': phoneNumber,
        'message': message,
      });
      return result;
    } on PlatformException catch (e) {
      debugPrint('Error sending SMS: ${e.message}');
      return false;
    }
  }
  
  /// Start SMS mining service
  Future<bool> startSmsMining({
    required String userId,
    int? simSlot,
  }) async {
    try {
      final bool result = await _channel.invokeMethod('startSmsMining', {
        'userId': userId,
        'simSlot': simSlot ?? -1,
      });
      
      if (result) {
        _isServiceRunning = true;
        _statusMessage = 'Starting SMS Mining...';
        notifyListeners();
      }
      
      return result;
    } on PlatformException catch (e) {
      debugPrint('Error starting SMS mining: ${e.message}');
      return false;
    }
  }
  
  /// Stop SMS mining service
  Future<bool> stopSmsMining() async {
    try {
      final bool result = await _channel.invokeMethod('stopSmsMining');
      
      if (result) {
        _isServiceRunning = false;
        _statusMessage = 'Stopping SMS Mining...';
        notifyListeners();
      }
      
      return result;
    } on PlatformException catch (e) {
      debugPrint('Error stopping SMS mining: ${e.message}');
      return false;
    }
  }
  
  /// Get SMS service status
  Future<Map<String, dynamic>?> getServiceStatus() async {
    try {
      final Map<dynamic, dynamic> result = 
          await _channel.invokeMethod('getServiceStatus');
      return Map<String, dynamic>.from(result);
    } on PlatformException catch (e) {
      debugPrint('Error getting service status: ${e.message}');
      return null;
    }
  }
  
  /// Fetch available tasks count
  Future<int> getAvailableTasksCount() async {
    try {
      final int count = await _channel.invokeMethod('getAvailableTasksCount');
      return count;
    } on PlatformException catch (e) {
      debugPrint('Error getting tasks count: ${e.message}');
      return 0;
    }
  }
  
  /// Reset statistics
  void resetStats() {
    _successCount = 0;
    _failCount = 0;
    _earned = 0.0;
    _progress = 0;
    _statusMessage = '';
    notifyListeners();
  }
}
