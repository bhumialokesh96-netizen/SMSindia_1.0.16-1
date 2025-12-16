package com.smsindia.app.service;

import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.PATCH;
import retrofit2.http.POST;
import retrofit2.http.Query;

// ✅ THIS IS THE MISSING LINE THAT CAUSED THE ERROR
import com.google.gson.internal.LinkedTreeMap;

public interface SupabaseApi {

    // ==========================================
    // 1. TASKS (Mining Logic)
    // ==========================================

    // Fetch a pending task (RPC Call)
    @POST("/rest/v1/rpc/get_one_task")
    Call<List<TaskModel>> getTask(
        @Header("apikey") String apiKey,
        @Header("Authorization") String auth
    );

    // Update task status (Sent/Failed/Reset)
    @PATCH("/rest/v1/sms_tasks")
    Call<Void> updateTask(
        @Header("apikey") String apiKey,
        @Header("Authorization") String auth,
        @Query("id") String query,      // Usage: "eq.TASK_UUID"
        @Body Map<String, Object> body
    );

    // ==========================================
    // 2. USERS (Login, Profile, Register)
    // ==========================================

    // Get User Profile (Balance, Coins, DeviceID)
    @GET("/rest/v1/users")
    Call<List<UserModel>> getUser(
        @Header("apikey") String apiKey,
        @Header("Authorization") String auth,
        @Query("phone") String phoneQuery // Usage: "eq.9876543210"
    );

    // Register New User
    @POST("/rest/v1/users")
    Call<Void> createUser(
        @Header("apikey") String apiKey,
        @Header("Authorization") String auth,
        @Header("Prefer") String prefer, // Send "return=minimal" to save bandwidth
        @Body Map<String, Object> body
    );

    // Update User (e.g., DeviceID, Bank Details, Coins)
    @PATCH("/rest/v1/users")
    Call<Void> updateUser(
        @Header("apikey") String apiKey,
        @Header("Authorization") String auth,
        @Query("phone") String phoneQuery, // Usage: "eq.9876543210"
        @Body Map<String, Object> body
    );

    // ==========================================
    // 3. REWARDS & MONEY (The Cost Saver 💰)
    // ==========================================

    // ✅ CRITICAL: Calls the SQL function 'claim_sms_reward'
    // Updates balance + inserts log in ONE server call. No Firestore needed.
    @POST("/rest/v1/rpc/claim_sms_reward")
    Call<Void> claimReward(
        @Header("apikey") String apiKey,
        @Header("Authorization") String auth,
        @Body Map<String, Object> body
    );

    // ==========================================
    // 4. WITHDRAWALS
    // ==========================================

    // Get Withdrawal History
    @GET("/rest/v1/withdrawals")
    Call<List<WithdrawModel>> getWithdrawals(
        @Header("apikey") String apiKey,
        @Header("Authorization") String auth,
        @Query("user_id") String userIdQuery, // Usage: "eq.USER_UUID"
        @Query("order") String order          // Usage: "created_at.desc"
    );

    // Request New Withdrawal
    @POST("/rest/v1/withdrawals")
    Call<Void> requestWithdrawal(
        @Header("apikey") String apiKey,
        @Header("Authorization") String auth,
        @Body Map<String, Object> body
    );

    // ==========================================
    // 5. CONFIG & DAILY BONUS (New Updates ✅)
    // ==========================================

    // Get App Config (WhatsApp Settings)
    @GET("/rest/v1/app_config")
    Call<List<AppConfigModel>> getConfig(
        @Header("apikey") String apiKey,
        @Header("Authorization") String auth,
        @Query("key") String keyQuery // Usage: "eq.whatsapp_config"
    );

    // Claim Daily Check-in (The SQL Function logic)
    @POST("/rest/v1/rpc/claim_daily_checkin")
    Call<Void> claimCheckIn(
        @Header("apikey") String apiKey,
        @Header("Authorization") String auth,
        @Body Map<String, Object> body
    );

    // 6. DELIVERY LOGS
    @GET("/rest/v1/sms_logs")
    Call<List<SmsLogModel>> getSmsLogs(
        @Header("apikey") String apiKey,
        @Header("Authorization") String auth,
        @Query("user_id") String userIdQuery, // Usage: "eq.UUID"
        @Query("order") String order          // Usage: "created_at.desc"
    );

    // 7. TRANSACTION HISTORY
    @GET("/rest/v1/transactions")
    Call<List<TransactionModel>> getTransactions(
        @Header("apikey") String apiKey,
        @Header("Authorization") String auth,
        @Query("user_id") String userIdQuery, // Usage: "eq.UUID"
        @Query("order") String order          // Usage: "created_at.desc"
    );

    // 8. ADMOB WATCH & EARN (The new feature)
    @POST("/rest/v1/rpc/watch_ad_reward")
    Call<LinkedTreeMap<String, Object>> watchAdReward(
        @Header("apikey") String apiKey, 
        @Header("Authorization") String token, 
        @Body Map<String, Object> body
    );

}
