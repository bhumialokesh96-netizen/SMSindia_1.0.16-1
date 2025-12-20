package com.smsindia.app.service;
import com.google.gson.annotations.SerializedName;
import java.util.List;

public class BatchClaimRequest {
    @SerializedName("p_user_id")
    final String userId;
    
    @SerializedName("p_task_ids")
    final List<String> taskIds;

    public BatchClaimRequest(String userId, List<String> taskIds) {
        this.userId = userId;
        this.taskIds = taskIds;
    }
}
