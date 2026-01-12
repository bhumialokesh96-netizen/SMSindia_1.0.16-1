/// Application constants and configuration
class AppConstants {
  // SMS Earning Rates
  static const double smsEarningRate = 0.16; // ₹0.16 per SMS
  
  // API Configuration
  static const String supabaseUrl = String.fromEnvironment(
    'SUPABASE_URL',
    defaultValue: 'https://appfwrpynfxfpcvpavso.supabase.co',
  );
  
  static const String supabaseAnonKey = String.fromEnvironment(
    'SUPABASE_ANON_KEY',
    defaultValue: '',
  );
  
  // App Configuration
  static const String appName = 'SMS India';
  static const String appVersion = '1.0.20';
  static const int buildNumber = 20;
  
  // Platform Channel Names
  static const String smsMethodChannel = 'com.smsindia.app/sms';
  static const String smsEventChannel = 'com.smsindia.app/sms_events';
  
  // SMS Configuration
  static const int batchSize = 10;
  static const int smsDelaySeconds = 3;
  static const int maxRetries = 3;
  
  // Referral Configuration
  static const double referrerReward = 10.0; // ₹10 per referral
  static const double refereeReward = 5.0;   // ₹5 for referred user
  static const int refereeCoins = 50;        // 50 coins for referred user
  static const String companyReferralCode = '666666';
  
  // UI Configuration
  static const int animationDurationMs = 300;
  static const double cardElevation = 2.0;
  static const double borderRadius = 12.0;
  
  // Validation Rules
  static const int minPasswordLength = 6;
  static const int mobileNumberLength = 10;
  static const int otpLength = 6;
  
  // Timeouts
  static const Duration apiTimeout = Duration(seconds: 30);
  static const Duration shortDelay = Duration(milliseconds: 500);
  static const Duration mediumDelay = Duration(seconds: 2);
  static const Duration longDelay = Duration(seconds: 5);
}
