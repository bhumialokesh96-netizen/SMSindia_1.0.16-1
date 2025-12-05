package com.smsindia.app.service; // Make sure this matches your folder!

import com.google.gson.annotations.SerializedName;

public class TaskModel {
    @SerializedName("task_id")
    public String id;

    @SerializedName("target_phone")
    public String phone;

    @SerializedName("msg_content")
    public String message;
}
