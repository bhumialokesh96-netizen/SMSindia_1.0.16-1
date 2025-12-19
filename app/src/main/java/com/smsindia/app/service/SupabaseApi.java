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

// Required for the AdMob response
import com.google.gson.internal.LinkedTreeMap;

public interface SupabaseApi {

    // ==========================================
    // 1. TASKS (Mining Logic)
    // ==========================================

    @POST("/rest/v1/rpc/get_one_task")
    Call<List<TaskModel>> getTask(
        @Header("apikey") String apiKey,
        @Header("Authorization") String auth
    );

    @PATCH("/rest/v1/sms_tasks")
    Call<Void> updateTask(
        @Header("apikey") String apiKey,
        @Header("Authorization") String auth,
        @Query("id") String query,
        @Body Map<String, Object> body
    );

    // ==========================================
    // 2. USERS (Login, Profile, Register)
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
        @Header("Prefer") String prefer,
        @Body Map<String, Object> body
    );

    @PATCH("/rest/v1/users")
    Call<Void> updateUser(
        @Header("apikey") String apiKey,
        @Header("Authorization") String auth,
        @Query("phone") String phoneQuery,
        @Body Map<String, Object> body
    );

    // ==========================================
    // 3. REWARDS & MONEY
    // ==========================================

    @POST("/rest/v1/rpc/claim_sms_reward")
    Call<Void> claimReward(
        @Header("apikey") String apiKey,
        @Header("Authorization") String auth,
        @Body Map<String, Object> body
    );

    // ==========================================
    // 4. WITHDRAWALS
    // ==========================================

    @GET("/rest/v1/withdrawals")
    Call<List<WithdrawModel>> getWithdrawals(
        @Header("apikey") String apiKey,
        @Header("Authorization") String auth,
        @Query("user_id") String userIdQuery,
        @Query("order") String order
    );

    @POST("/rest/v1/withdrawals")
    Call<Void> requestWithdrawal(
        @Header("apikey") String apiKey,
        @Header("Authorization") String auth,
        @Body Map<String, Object> body
    );

    // ==========================================
    // 5. CONFIG & DAILY BONUS
    // ==========================================

    // ✅ This fetches your AdMob IDs & WhatsApp Config
    // Ensure your table in Supabase is named 'app_config'
    @GET("/rest/v1/app_config")
    Call<List<AppConfigModel>> getConfig(
        @Header("apikey") String apiKey,
        @Header("Authorization") String auth,
        @Query("key") String keyQuery 
    );

    @POST("/rest/v1/rpc/claim_daily_checkin")
    Call<Void> claimCheckIn(
        @Header("apikey") String apiKey,
        @Header("Authorization") String auth,
        @Body Map<String, Object> body
    );

    // ==========================================
    // 6. LOGS & TRANSACTIONS
    // ==========================================

    @GET("/rest/v1/sms_logs")
    Call<List<SmsLogModel>> getSmsLogs(
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
    // 7. ADMOB WATCH & EARN (UPDATED)
    // ==========================================

    // Returns a Map/JSON containing { message: "...", progress: 5 }
    @POST("/rest/v1/rpc/watch_ad_reward")
    Call<LinkedTreeMap<String, Object>> watchAdReward(
        @Header("apikey") String apiKey, 
        @Header("Authorization") String token, 
        @Body Map<String, Object> body
    );
}
