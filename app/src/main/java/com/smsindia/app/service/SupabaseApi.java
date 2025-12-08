package com.smsindia.app.service;

import java.util.List;
import java.util.Map;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.Header;
import retrofit2.http.PATCH;
import retrofit2.http.POST;
import retrofit2.http.Query;

public interface SupabaseApi {

    // ✅ MATCHES YOUR OLD CODE (Keeps using get_one_task)
    @POST("/rest/v1/rpc/get_one_task")
    Call<List<TaskModel>> getTask(
        @Header("apikey") String apiKey,
        @Header("Authorization") String auth
    );

    // ✅ NEW ADDITION: To mark as 'sent' or reset to 'pending'
    @PATCH("/rest/v1/sms_tasks")
    Call<Void> updateTask(
        @Header("apikey") String apiKey,
        @Header("Authorization") String auth,
        @Query("id") String taskId,     // Matches the real DB column "id"
        @Body Map<String, Object> body  // Matches status update
    );
}
