package com.smsindia.app.config;

/**
 * Central configuration constants for the SMS India application.
 * API keys and sensitive data should be loaded from BuildConfig.
 */
public class Constants {
    
    // ==========================================
    // SUPABASE CONFIGURATION
    // ==========================================
    
    /**
     * Supabase API Base URL
     * In production, this should be loaded from BuildConfig
     */
    public static final String SUPABASE_URL = BuildConfig.SUPABASE_URL;
    
    /**
     * Supabase Anonymous API Key
     * In production, this should be loaded from BuildConfig
     */
    public static final String SUPABASE_ANON_KEY = BuildConfig.SUPABASE_ANON_KEY;
    
    // ==========================================
    // ADMOB CONFIGURATION
    // ==========================================
    
    /**
     * Fallback AdMob Rewarded Ad Unit ID (Test ID)
     */
    public static final String FALLBACK_AD_UNIT_ID = "ca-app-pub-3940256099942544/5224354917";
    
    // ==========================================
    // APP CONFIGURATION
    // ==========================================
    
    /**
     * Shared Preferences file name for user data
     */
    public static final String PREFS_USER = "SMSINDIA_USER";
    
    /**
     * Shared Preferences key for user ID
     */
    public static final String PREFS_USER_ID = "userId";
    
    /**
     * Shared Preferences key for mobile number
     */
    public static final String PREFS_MOBILE = "mobile";
    
    // ==========================================
    // NETWORK CONFIGURATION
    // ==========================================
    
    /**
     * Network timeout in seconds
     */
    public static final int NETWORK_TIMEOUT_SECONDS = 30;
    
    /**
     * Retry attempts for failed network requests
     */
    public static final int NETWORK_RETRY_ATTEMPTS = 3;
    
    // ==========================================
    // SMS MINING CONFIGURATION
    // ==========================================
    
    /**
     * Default batch size for SMS tasks
     */
    public static final int SMS_BATCH_SIZE = 10;
    
    /**
     * Auto-fetch interval in seconds
     */
    public static final int AUTO_FETCH_INTERVAL_SECONDS = 30;
    
    // ==========================================
    // PERMISSION REQUEST CODES
    // ==========================================
    
    public static final int PERMISSION_REQUEST_SMS = 101;
    public static final int PERMISSION_REQUEST_PHONE_STATE = 102;
    
    // ==========================================
    // RETROFIT HEADERS
    // ==========================================
    
    /**
     * Authorization header prefix
     */
    public static final String HEADER_AUTH_PREFIX = "Bearer ";
    
    /**
     * API Key header name
     */
    public static final String HEADER_API_KEY = "apikey";
    
    /**
     * Authorization header name
     */
    public static final String HEADER_AUTHORIZATION = "Authorization";
    
    /**
     * Prefer header name (for Supabase)
     */
    public static final String HEADER_PREFER = "Prefer";
    
    /**
     * Return representation preference
     */
    public static final String PREFER_RETURN_REPRESENTATION = "return=representation";
    
    // ==========================================
    // UTILITY METHODS
    // ==========================================
    
    /**
     * Get Authorization header value with Bearer prefix
     */
    public static String getAuthorizationHeader(String token) {
        return HEADER_AUTH_PREFIX + token;
    }
    
    /**
     * Private constructor to prevent instantiation
     */
    private Constants() {
        throw new AssertionError("Cannot instantiate Constants class");
    }
}
