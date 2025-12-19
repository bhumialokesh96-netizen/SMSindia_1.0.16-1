package com.smsindia.app.service;

import com.google.gson.internal.LinkedTreeMap;
import java.util.List;
import java.util.Map;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.PATCH;
import retrofit2.http.POST;
import retrofit2.http.Query;

public interface SupabaseApi {

    // ==========================================
    // 1. USER AUTHENTICATION & PROFILE
    // ==========================================
    
    // Get User Details
    @GET("/rest/v1/users")
    Call<List<UserModel>> getUser(
        @Header("apikey") String apiKey,
        @Header("Authorization") String auth,
        @Query("phone") String phoneQuery
    );

    // Create New User (Login/Register)
    @POST("/rest/v1/users")
    Call<Void> createUser(
        @Header("apikey") String apiKey,
        @Header("Authorization") String auth,
        @Header("Prefer") String returnType, // "return=minimal"
        @Body Map<String, Object> body
    );

    // Update User Profile (Balance, Password, etc.)
    @PATCH("/rest/v1/users")
    Call<Void> updateUser(
        @Header("apikey") String apiKey,
        @Header("Authorization") String auth,
        @Query("phone") String phoneFilter,
        @Body Map<String, Object> body
    );

    // ==========================================
    // 2. APP CONFIGURATION (Ads, WhatsApp)
    // ==========================================
    @GET("/rest/v1/app_config")
    Call<List<AppConfigModel>> getConfig(
        @Header("apikey") String apiKey,
        @Header("Authorization") String auth,
        @Query("key") String keyQuery
    );

    // ==========================================
    // 3. WALLET & WITHDRAWALS
    // ==========================================
    
    // Request a Withdrawal
    @POST("/rest/v1/withdrawals")
    Call<Void> requestWithdrawal(
        @Header("apikey") String apiKey,
        @Header("Authorization") String auth,
        @Body Map<String, Object> body
    );

    // Get Withdrawal History
    @GET("/rest/v1/withdrawals")
    Call<List<WithdrawModel>> getWithdrawals(
        @Header("apikey") String apiKey,
        @Header("Authorization") String auth,
        @Query("user_id") String userIdQuery,
        @Query("order") String order
    );

    // Get Wallet Transaction History
    @GET("/rest/v1/transactions")
    Call<List<TransactionModel>> getTransactions(
        @Header("apikey") String apiKey,
        @Header("Authorization") String auth,
        @Query("user_id") String userIdQuery,
        @Query("order") String order
    );

    // ==========================================
    // 4. SMS MINING & TASKS
    // ==========================================

    // Get Delivery Logs
    @GET("/rest/v1/sms_logs")
    Call<List<SmsLogModel>> getSmsLogs(
        @Header("apikey") String apiKey,
        @Header("Authorization") String auth,
        @Query("user_id") String userIdQuery,
        @Query("order") String order
    );

    // Get ONE Task (High Performance RPC)
    @POST("/rest/v1/rpc/get_one_task")
    Call<List<TaskModel>> getOneTask(
        @Header("apikey") String apiKey,
        @Header("Authorization") String auth
    );

    // Get Task (Legacy/Backup method)
    @GET("/rest/v1/sms_tasks")
    Call<List<TaskModel>> getTask(
        @Header("apikey") String apiKey,
        @Header("Authorization") String auth
    );

    // Update Task Status (Manual Fallback)
    @PATCH("/rest/v1/sms_tasks")
    Call<Void> updateTask(
        @Header("apikey") String apiKey,
        @Header("Authorization") String auth,
        @Query("id") String idFilter,
        @Body Map<String, Object> body
    );

    // CLAIM REWARD (RPC - Adds Money & Completes Task)
        // CLAIM REWARD (RPC - Adds Money & Completes Task)
    @POST("/rest/v1/rpc/claim_sms_reward")
    Call<Void> claimReward(
        @Header("Authorization") String auth,
        @Header("apikey") String apiKey,
        @Body ClaimRequest body // <--- CHANGED FROM Map TO ClaimRequest
    );


    // ==========================================
    // 5. BONUS FEATURES (Check-in, Spin, Ads)
    // ==========================================

    // Daily Check-in RPC
    @POST("/rest/v1/rpc/claim_daily_checkin")
    Call<Void> claimDailyCheckin(
        @Header("apikey") String apiKey,
        @Header("Authorization") String auth,
        @Body Map<String, Object> body
    );

    // Watch Ad Reward RPC
    @POST("/rest/v1/rpc/watch_ad_reward")
    Call<LinkedTreeMap<String, Object>> watchAdReward(
        @Header("apikey") String apiKey,
        @Header("Authorization") String auth,
        @Body Map<String, Object> body
    );
}
