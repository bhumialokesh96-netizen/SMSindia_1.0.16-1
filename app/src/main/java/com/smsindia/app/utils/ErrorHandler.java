package com.smsindia.app.utils;

import android.util.Log;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import retrofit2.Response;

/**
 * Centralized error handler for API responses.
 * Parses error bodies from Supabase and provides user-friendly error messages.
 */
public class ErrorHandler {
    
    private static final String TAG = "ErrorHandler";
    
    /**
     * Parse error from Retrofit response and return user-friendly message
     * @param response The failed API response
     * @return User-friendly error message
     */
    public static String getErrorMessage(Response<?> response) {
        if (response == null) {
            return "Unknown error occurred";
        }
        
        // Try to parse error body
        String errorBody = getErrorBody(response);
        
        // Parse Supabase error format
        String parsedMessage = parseSupabaseError(errorBody);
        if (parsedMessage != null) {
            return parsedMessage;
        }
        
        // Fallback to HTTP status code messages
        return getHttpStatusMessage(response.code());
    }
    
    /**
     * Get error body as string from response
     */
    private static String getErrorBody(Response<?> response) {
        try {
            if (response.errorBody() != null) {
                return response.errorBody().string();
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to read error body: " + e.getMessage());
        }
        return null;
    }
    
    /**
     * Parse Supabase error format
     * Common formats:
     * - {"msg": "Invalid login credentials"}
     * - {"message": "User already registered"}
     * - {"error": "invalid_grant", "error_description": "Invalid login credentials"}
     */
    private static String parseSupabaseError(String errorBody) {
        if (errorBody == null || errorBody.isEmpty()) {
            return null;
        }
        
        try {
            JsonObject json = JsonParser.parseString(errorBody).getAsJsonObject();
            
            // Check for error_description (Supabase auth format)
            if (json.has("error_description")) {
                String desc = json.get("error_description").getAsString();
                return mapSupabaseAuthError(desc);
            }
            
            // Check for msg field
            if (json.has("msg")) {
                return json.get("msg").getAsString();
            }
            
            // Check for message field
            if (json.has("message")) {
                String message = json.get("message").getAsString();
                return mapSupabaseError(message);
            }
            
            // Check for error field
            if (json.has("error")) {
                String error = json.get("error").getAsString();
                return mapSupabaseError(error);
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to parse error JSON: " + e.getMessage());
            
            // Try simple string matching for common errors
            String lowerBody = errorBody.toLowerCase();
            if (lowerBody.contains("already registered") || lowerBody.contains("already exists")) {
                return "Email or phone already registered. Please login.";
            }
            if (lowerBody.contains("invalid login") || lowerBody.contains("invalid credentials")) {
                return "Invalid phone number or password";
            }
            if (lowerBody.contains("user not found") || lowerBody.contains("not found")) {
                return "Account not found. Please sign up first.";
            }
            if (lowerBody.contains("weak password")) {
                return "Password is too weak. Use at least 6 characters.";
            }
        }
        
        return null;
    }
    
    /**
     * Map Supabase auth error descriptions to user-friendly messages
     */
    private static String mapSupabaseAuthError(String errorDesc) {
        if (errorDesc == null) return null;
        
        String lower = errorDesc.toLowerCase();
        
        if (lower.contains("invalid login credentials")) {
            return "Invalid phone number or password";
        }
        if (lower.contains("email not confirmed")) {
            return "Email not verified. Please check your email.";
        }
        if (lower.contains("user not found")) {
            return "Account not found. Please sign up first.";
        }
        if (lower.contains("invalid grant")) {
            return "Invalid credentials. Please try again.";
        }
        
        // Return original if no mapping found
        return errorDesc;
    }
    
    /**
     * Map generic Supabase errors to user-friendly messages
     */
    private static String mapSupabaseError(String error) {
        if (error == null) return null;
        
        String lower = error.toLowerCase();
        
        if (lower.contains("already registered") || lower.contains("already exists") || 
            lower.contains("duplicate")) {
            return "Email or phone already registered. Please login.";
        }
        if (lower.contains("not found")) {
            return "Account not found. Please sign up first.";
        }
        if (lower.contains("unauthorized") || lower.contains("forbidden")) {
            return "Access denied. Please login again.";
        }
        if (lower.contains("weak password")) {
            return "Password is too weak. Use at least 6 characters.";
        }
        if (lower.contains("invalid email")) {
            return "Invalid email address format";
        }
        if (lower.contains("invalid phone")) {
            return "Invalid phone number format";
        }
        
        // Return original if no mapping found
        return error;
    }
    
    /**
     * Get user-friendly message for HTTP status codes
     */
    private static String getHttpStatusMessage(int statusCode) {
        switch (statusCode) {
            case 400:
                return "Invalid request. Please check your input.";
            case 401:
                return "Invalid credentials. Please try again.";
            case 403:
                return "Access denied. Please login again.";
            case 404:
                return "Account not found. Please sign up first.";
            case 409:
                return "Account already exists. Please login.";
            case 422:
                return "Invalid data provided. Please check your input.";
            case 429:
                return "Too many requests. Please try again later.";
            case 500:
            case 502:
            case 503:
                return "Server error. Please try again later.";
            case 504:
                return "Request timeout. Please check your connection.";
            default:
                if (statusCode >= 500) {
                    return "Server error. Please try again later.";
                } else if (statusCode >= 400) {
                    return "Request failed. Please try again.";
                }
                return "Unknown error occurred. Please try again.";
        }
    }
    
    /**
     * Check if error indicates user already exists
     */
    public static boolean isUserAlreadyExistsError(Response<?> response) {
        String errorBody = getErrorBody(response);
        if (errorBody == null) {
            return response.code() == 409;
        }
        
        String lower = errorBody.toLowerCase();
        return lower.contains("already registered") || 
               lower.contains("already exists") || 
               lower.contains("duplicate") ||
               response.code() == 409;
    }
    
    /**
     * Check if error indicates invalid credentials
     */
    public static boolean isInvalidCredentialsError(Response<?> response) {
        String errorBody = getErrorBody(response);
        if (errorBody == null) {
            return response.code() == 401 || response.code() == 400;
        }
        
        String lower = errorBody.toLowerCase();
        return lower.contains("invalid login") || 
               lower.contains("invalid credentials") ||
               lower.contains("invalid grant") ||
               response.code() == 401;
    }
    
    /**
     * Check if error indicates user not found
     */
    public static boolean isUserNotFoundError(Response<?> response) {
        String errorBody = getErrorBody(response);
        if (errorBody == null) {
            return response.code() == 404;
        }
        
        String lower = errorBody.toLowerCase();
        return lower.contains("not found") || 
               lower.contains("user not found") ||
               response.code() == 404;
    }
}
