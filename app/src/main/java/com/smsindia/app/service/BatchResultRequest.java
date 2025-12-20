package com.smsindia.app.service;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class BatchResultRequest {
    @SerializedName("p_user_id")
    final String userId;
    
    @SerializedName("p_success_ids")
    final List<String> successIds;

    @SerializedName("p_failed_ids")
    final List<String> failedIds;

    public BatchResultRequest(String userId, List<String> successIds, List<String> failedIds) {
        this.userId = userId;
        this.successIds = successIds;
        this.failedIds = failedIds;
    }
}
