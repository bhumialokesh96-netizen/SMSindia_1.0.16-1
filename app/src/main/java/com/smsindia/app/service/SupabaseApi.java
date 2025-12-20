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
    
    @GET("/rest/v1/users")
    Call<List<UserModel>> getUser(
        @Header("apikey") String apiKey,
        @Header("Authorization") String auth,
        @Query("phone") String phoneQuery
    );

    @POST("/rest/v1/users")
    Call<Void> createUser(
        @Header("apikey") String apiKey,
        @Header("Authorization") String auth,
        @Header("Prefer") String returnType, 
        @Body Map<String, Object> body
    );

    @PATCH("/rest/v1/users")
    Call<Void> updateUser(
        @Header("apikey") String apiKey,
        @Header("Authorization") String auth,
        @Query("phone") String phoneFilter,
        @Body Map<String, Object> body
    );

    // ==========================================
    // 2. APP CONFIGURATION
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
    
    @POST("/rest/v1/withdrawals")
    Call<Void> requestWithdrawal(
        @Header("apikey") String apiKey,
        @Header("Authorization") String auth,
        @Body Map<String, Object> body
    );

    @GET("/rest/v1/withdrawals")
    Call<List<WithdrawModel>> getWithdrawals(
        @Header("apikey") String apiKey,
        @Header("Authorization") String auth,
        @Query("user_id") String userIdQuery,
        @Query("order") String order
    );

    @GET("/rest/v1/transactions")
    Call<List<TransactionModel>> getTransactions(
        @Header("apikey") String apiKey,
        @Header("Authorization") String auth,
        @Query("user_id") String userIdQuery,
        @Query("order") String order
    );

    // ==========================================
    // 4. SMS MINING & TASKS (UPDATED FOR BATCHING)
    // ==========================================

    // ✅ NEW: Fetch 10 Tasks at once (High Speed)
    @POST("/rest/v1/rpc/fetch_batch_tasks")
    Call<List<TaskModel>> fetchBatchTasks(
        @Header("Authorization") String token, 
        @Header("apikey") String apiKey,
        @Body Map<String, Object> body // Sends {"p_user_id": "...", "p_size": 10}
    );

    // ✅ NEW: Claim Reward for 10 Tasks at once
    @POST("/rest/v1/rpc/claim_batch_reward")
    Call<Void> claimBatchReward(
        @Header("Authorization") String token, 
        @Header("apikey") String apiKey,
        @Body BatchClaimRequest body 
    );

    // --- OLD METHODS (Keep just in case) ---

    @GET("/rest/v1/sms_logs")
    Call<List<SmsLogModel>> getSmsLogs(
        @Header("apikey") String apiKey,
        @Header("Authorization") String auth,
        @Query("user_id") String userIdQuery,
        @Query("order") String order
    );

    @POST("/rest/v1/rpc/get_one_task")
    Call<List<TaskModel>> getOneTask(
        @Header("apikey") String apiKey,
        @Header("Authorization") String auth
    );

    @POST("/rest/v1/rpc/claim_sms_reward")
    Call<Void> claimReward(
        @Header("Authorization") String auth,
        @Header("apikey") String apiKey,
        @Body ClaimRequest body 
    );

    // ==========================================
    // 5. BONUS FEATURES
    // ==========================================

    @POST("/rest/v1/rpc/claim_daily_checkin")
    Call<Void> claimDailyCheckin(
        @Header("apikey") String apiKey,
        @Header("Authorization") String auth,
        @Body Map<String, Object> body
    );

    @POST("/rest/v1/rpc/watch_ad_reward")
    Call<LinkedTreeMap<String, Object>> watchAdReward(
        @Header("apikey") String apiKey,
        @Header("Authorization") String auth,
        @Body Map<String, Object> body
    );
}
