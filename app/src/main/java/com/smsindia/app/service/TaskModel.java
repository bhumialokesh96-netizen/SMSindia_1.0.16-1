package com.smsindia.app.service;

import com.google.gson.annotations.SerializedName;

public class TaskModel {
    @SerializedName("id") 
    private String id;

    @SerializedName("phone") 
    private String phone;

    @SerializedName("message") 
    private String message;

    @SerializedName("status") 
    private String status;
    
    // Constructor
    public TaskModel() {
    }
    
    public TaskModel(String id, String phone, String message, String status) {
        this.id = id;
        this.phone = phone;
        this.message = message;
        this.status = status;
    }
    
    // Getters
    public String getId() {
        return id;
    }
    
    public String getPhone() {
        return phone;
    }
    
    public String getMessage() {
        return message;
    }
    
    public String getStatus() {
        return status;
    }
    
    // Setters
    public void setId(String id) {
        this.id = id;
    }
    
    public void setPhone(String phone) {
        this.phone = phone;
    }
    
    public void setMessage(String message) {
        this.message = message;
    }
    
    public void setStatus(String status) {
        this.status = status;
    }
    
    @Override
    public String toString() {
        return "TaskModel{" +
                "id='" + id + '\'' +
                ", phone='" + phone + '\'' +
                ", message='" + message + '\'' +
                ", status='" + status + '\'' +
                '}';
    }
}
