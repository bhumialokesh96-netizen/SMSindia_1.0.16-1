package com.smsindia.app.ui;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.smsindia.app.R;
import com.smsindia.app.config.Constants;
import com.smsindia.app.data.model.SmsLogModel;
import com.smsindia.app.data.api.SupabaseApi;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class DeliveryLogActivity extends AppCompatActivity {


    private LinearLayout logsContainer;
    private SupabaseApi supabaseApi;
    private String userIdUUID;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_delivery_logs);

        logsContainer = findViewById(R.id.logs_container);

        // Init Retrofit
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(Constants.SUPABASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build();
        supabaseApi = retrofit.create(SupabaseApi.class);

        SharedPreferences prefs = getSharedPreferences("SMSINDIA_USER", MODE_PRIVATE);
        userIdUUID = prefs.getString("userId", "");

        if (userIdUUID.isEmpty()) {
            Toast.makeText(this, "User ID missing. Relogin required.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        loadLogs();
    }

    // ==========================================
    // HELPER METHOD: GET JWT TOKEN
    // ==========================================
    private String getAuthHeader() {
        SharedPreferences authPrefs = getSharedPreferences("SMS_AUTH", MODE_PRIVATE);
        String token = authPrefs.getString("jwt", null);
        return token != null ? "Bearer " + token : "Bearer " + Constants.SUPABASE_ANON_KEY;
    }

    private void loadLogs() {
        // Show loading state
        logsContainer.removeAllViews();
        addText("Loading history...", Color.GRAY);

        // Use JWT token for authorization
        String authHeader = getAuthHeader();

        // Fetch logs: WHERE user_id = UUID ORDER BY created_at DESC
        supabaseApi.getSmsLogs(Constants.SUPABASE_ANON_KEY, authHeader, "eq." + userIdUUID, "created_at.desc")
            .enqueue(new Callback<List<SmsLogModel>>() {
                @Override
                public void onResponse(Call<List<SmsLogModel>> call, Response<List<SmsLogModel>> response) {
                    logsContainer.removeAllViews(); // Clear loading text

                    if (response.isSuccessful() && response.body() != null) {
                        List<SmsLogModel> logs = response.body();
                        
                        if (logs.isEmpty()) {
                            addText("No logs found yet.", Color.BLACK);
                            return;
                        }

                        // Date Formatter
                        SimpleDateFormat isoFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault());
                        SimpleDateFormat displayFormat = new SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault());

                        for (SmsLogModel log : logs) {
                            String formattedDate = log.createdAt;
                            try {
                                // Fix ISO format parsing
                                if(formattedDate != null && formattedDate.contains(".")) {
                                    formattedDate = formattedDate.split("\\.")[0];
                                }
                                Date date = isoFormat.parse(formattedDate);
                                if (date != null) formattedDate = displayFormat.format(date);
                            } catch (ParseException e) {
                                formattedDate = "Just now";
                            }

                            String displayText = "Phone: " + log.phone + "\n" + formattedDate + "  •  " + log.status;
                            addText(displayText, Color.BLACK);
                            addDivider(); // Optional: Add a line between logs
                        }
                    } else {
                        addText("Failed to load data.", Color.RED);
                    }
                }

                @Override
                public void onFailure(Call<List<SmsLogModel>> call, Throwable t) {
                    logsContainer.removeAllViews();
                    addText("Network Error: " + t.getMessage(), Color.RED);
                }
            });
    }

    private void addText(String text, int color) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(15f);
        tv.setTextColor(color);
        tv.setPadding(30, 20, 30, 20);
        logsContainer.addView(tv);
    }

    private void addDivider() {
        android.view.View v = new android.view.View(this);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 
                2 // height in px
        );
        v.setLayoutParams(params);
        v.setBackgroundColor(Color.parseColor("#E0E0E0")); // Light Gray
        logsContainer.addView(v);
    }
}