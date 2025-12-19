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

    // --- AUTH & USER ---
    @GET("/rest/v1/users")
    Call<List<UserModel>> getUser(
        @Header("apikey") String apiKey,
        @Header("Authorization") String auth,
        @Query("phone") String phoneQuery
    );

    // --- CONFIG ---
    @GET("/rest/v1/app_config")
    Call<List<AppConfigModel>> getConfig(
        @Header("apikey") String apiKey,
        @Header("Authorization") String auth,
        @Query("key") String keyQuery
    );

    // --- ADMOB ---
    @POST("/rest/v1/rpc/watch_ad_reward")
    Call<LinkedTreeMap<String, Object>> watchAdReward(
        @Header("apikey") String apiKey,
        @Header("Authorization") String auth,
        @Body Map<String, Object> body
    );

    // --- WITHDRAWALS ---
    @GET("/rest/v1/withdrawals")
    Call<List<WithdrawModel>> getWithdrawals(
        @Header("apikey") String apiKey,
        @Header("Authorization") String auth,
        @Query("user_id") String userIdQuery,
        @Query("order") String order
    );

    // =================================================================
    // ✅ NEW METHODS (THESE WERE MISSING CAUSING THE ERROR)
    // =================================================================

    // 1. Daily Check-in (RPC)
    @POST("/rest/v1/rpc/claim_daily_checkin")
    Call<Void> claimDailyCheckin(
        @Header("apikey") String apiKey,
        @Header("Authorization") String auth,
        @Body Map<String, Object> body
    );

    // 2. Get One Task (High Performance RPC)
    @POST("/rest/v1/rpc/get_one_task")
    Call<List<TaskModel>> getOneTask(
        @Header("apikey") String apiKey,
        @Header("Authorization") String auth
    );

    // 3. Claim SMS Reward (RPC)
    @POST("/rest/v1/rpc/claim_sms_reward")
    Call<Void> claimReward(
        @Header("Authorization") String auth,
        @Header("apikey") String apiKey,
        @Body Map<String, Object> body
    );

    // 4. Update Task Status (For failures/retries)
    @PATCH("/rest/v1/sms_tasks")
    Call<Void> updateTask(
        @Header("apikey") String apiKey,
        @Header("Authorization") String auth,
        @Query("id") String idFilter,
        @Body Map<String, Object> body
    );
}
