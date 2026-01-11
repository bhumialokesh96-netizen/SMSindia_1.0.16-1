package com.smsindia.app.data.model;

import com.google.gson.annotations.SerializedName;

public class AppConfigModel {

    // (Optional) The Row ID in Supabase
    @SerializedName("id")
    public int id;

    // (Optional) The config name (e.g., "admob_config")
    // Helpful for debugging to know which config was loaded
    @SerializedName("key")
    public String key;

    // ✅ REQUIRED: This holds the JSON data (ad_ids, urls, etc.)
    // We use Object because the content inside changes dynamically
    @SerializedName("value")
    public Object value; 
}
