package com.smsindia.app.service; // Make sure this matches your folder!

import java.util.List;
import retrofit2.Call;
import retrofit2.http.POST;
import retrofit2.http.Header;

public interface SupabaseApi {
    @POST("/rest/v1/rpc/get_one_task")
    Call<List<TaskModel>> getTask(
        @Header("apikey") String apiKey,
        @Header("Authorization") String auth
    );
}
