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
    // 1. USER AUTHENTICATION
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
    // 4. SMS MINING (NEW BATCH SYSTEM)
    // ==========================================

    // ✅ FETCH BATCH (Downloads 10 tasks & locks them)
       // ... other methods

    // 1. Fetch Work
    @POST("/rest/v1/rpc/fetch_batch_tasks")
    Call<List<TaskModel>> fetchBatchTasks(
        @Header("Authorization") String token, 
        @Header("apikey") String apiKey,
        @Body Map<String, Object> body 
    );

    // 2. Submit Results (Success + Failures)
    @POST("/rest/v1/rpc/submit_batch_results")
    Call<Void> submitBatchResults(
        @Header("Authorization") String token, 
        @Header("apikey") String apiKey,
        @Body BatchResultRequest body 
    );


    // (Old methods removed to fix build error)

    @GET("/rest/v1/sms_logs")
    Call<List<SmsLogModel>> getSmsLogs(
        @Header("apikey") String apiKey,
        @Header("Authorization") String auth,
        @Query("user_id") String userIdQuery,
        @Query("order") String order
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
