import 'package:flutter/foundation.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:http/http.dart' as http;
import 'dart:convert';

/// Authentication service
class AuthService extends ChangeNotifier {
  String? _userId;
  String? _mobile;
  String? _email;
  String? _token;
  bool _isAuthenticated = false;
  
  // Getters
  String? get userId => _userId;
  String? get mobile => _mobile;
  String? get email => _email;
  String? get token => _token;
  bool get isAuthenticated => _isAuthenticated;
  
  /// Initialize auth state from local storage
  Future<void> initialize() async {
    final prefs = await SharedPreferences.getInstance();
    _userId = prefs.getString('userId');
    _mobile = prefs.getString('mobile');
    _email = prefs.getString('email');
    _token = prefs.getString('token');
    _isAuthenticated = _userId != null && _mobile != null;
    notifyListeners();
  }
  
  /// Login user
  Future<bool> login(String mobile, String password) async {
    try {
      // TODO: Implement actual Supabase API call
      // This is a placeholder implementation for demonstration
      // Replace with actual authentication logic using:
      // - Supabase Auth API
      // - Token management
      // - Error handling
      
      // Simulate API call
      await Future.delayed(const Duration(seconds: 1));
      
      // On success, save credentials
      _userId = 'user123';
      _mobile = mobile;
      _email = 'user@example.com';
      _token = 'jwt_token';
      _isAuthenticated = true;
      
      // Save to local storage
      final prefs = await SharedPreferences.getInstance();
      await prefs.setString('userId', _userId!);
      await prefs.setString('mobile', _mobile!);
      await prefs.setString('email', _email!);
      await prefs.setString('token', _token!);
      
      notifyListeners();
      return true;
    } catch (e) {
      debugPrint('Login error: $e');
      return false;
    }
  }
  
  /// Register user
  Future<bool> register({
    required String mobile,
    required String email,
    required String password,
    String? referralCode,
  }) async {
    try {
      // TODO: Implement actual Supabase API call for registration
      // This is a placeholder - replace with real implementation
      await Future.delayed(const Duration(seconds: 1));
      
      // On success
      return await login(mobile, password);
    } catch (e) {
      debugPrint('Register error: $e');
      return false;
    }
  }
  
  /// Logout user
  Future<void> logout() async {
    _userId = null;
    _mobile = null;
    _email = null;
    _token = null;
    _isAuthenticated = false;
    
    final prefs = await SharedPreferences.getInstance();
    await prefs.clear();
    
    notifyListeners();
  }
  
  /// Check authentication status
  Future<bool> checkAuth() async {
    await initialize();
    return _isAuthenticated;
  }
}
