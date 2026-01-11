package com.smsindia.app.data.api;

import com.smsindia.app.data.model.*;

import com.google.gson.internal.LinkedTreeMap;
import java.util.Map;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.POST;

public interface WhatsAppApi {
    // Endpoint to request Pairing Code
    @POST("/app/login") 
    Call<LinkedTreeMap<String, Object>> login(@Body Map<String, Object> body);
}
