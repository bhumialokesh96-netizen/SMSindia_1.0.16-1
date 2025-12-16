package com.smsindia.app.ui;

import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.smsindia.app.R;
import com.smsindia.app.service.SmsLogModel;
import com.smsindia.app.service.SupabaseApi;

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

    private static final String SUPABASE_URL = "https://appfwrpynfxfpcvpavso.supabase.co";
    private static final String SUPABASE_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImFwcGZ3cnB5bmZ4ZnBjdnBhdnNvIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NjIwOTQ2MTQsImV4cCI6MjA3NzY3MDYxNH0.Z-BMBjME8MVK5MS2KBgcCDgR7kXvDEjtcHrVfIUvwZY";

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
                .baseUrl(SUPABASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build();
        supabaseApi = retrofit.create(SupabaseApi.class);

        SharedPreferences prefs = getSharedPreferences("SMSINDIA_USER", MODE_PRIVATE);
        // CRITICAL: Get the UUID, not just the phone number
        userIdUUID = prefs.getString("userId", "");

        if (userIdUUID.isEmpty()) {
            Toast.makeText(this, "User ID missing. Relogin required.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        loadLogs();
    }

    private void loadLogs() {
        // Show loading state
        logsContainer.removeAllViews();
        addText("Loading history...", Color.GRAY);

        // Fetch logs: WHERE user_id = UUID ORDER BY created_at DESC
        supabaseApi.getSmsLogs(SUPABASE_KEY, "Bearer " + SUPABASE_KEY, "eq." + userIdUUID, "created_at.desc")
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
