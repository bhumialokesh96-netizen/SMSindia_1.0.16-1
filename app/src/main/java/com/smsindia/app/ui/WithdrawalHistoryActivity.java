package com.smsindia.app.ui;

import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.smsindia.app.R;
import com.smsindia.app.service.SupabaseApi;
import com.smsindia.app.service.WithdrawModel;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class WithdrawalHistoryActivity extends AppCompatActivity {

    private static final String SUPABASE_URL = "https://appfwrpynfxfpcvpavso.supabase.co";
    private static final String SUPABASE_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImFwcGZ3cnB5bmZ4ZnBjdnBhdnNvIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NjIwOTQ2MTQsImV4cCI6MjA3NzY3MDYxNH0.Z-BMBjME8MVK5MS2KBgcCDgR7kXvDEjtcHrVfIUvwZY";

    private RecyclerView recyclerView;
    private WithdrawalAdapter adapter;
    private List<WithdrawModel> list;
    private SupabaseApi supabaseApi;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_withdrawal_history);

        recyclerView = findViewById(R.id.recycler_withdrawals);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        
        list = new ArrayList<>();
        adapter = new WithdrawalAdapter(list);
        recyclerView.setAdapter(adapter);

        // Init Retrofit
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(SUPABASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build();
        supabaseApi = retrofit.create(SupabaseApi.class);

        SharedPreferences prefs = getSharedPreferences("SMSINDIA_USER", 0);
        // CRITICAL: We need the UUID now, not the phone number
        String userUuid = prefs.getString("userId", ""); 

        if(!userUuid.isEmpty()) {
            loadHistory(userUuid);
        } else {
            Toast.makeText(this, "User ID missing. Please Relogin.", Toast.LENGTH_SHORT).show();
        }
    }

    private void loadHistory(String uuid) {
        // Query: user_id equals UUID, Order by created_at Descending
        supabaseApi.getWithdrawals(SUPABASE_KEY, "Bearer " + SUPABASE_KEY, "eq." + uuid, "created_at.desc")
            .enqueue(new Callback<List<WithdrawModel>>() {
                @Override
                public void onResponse(Call<List<WithdrawModel>> call, Response<List<WithdrawModel>> response) {
                    if (response.isSuccessful() && response.body() != null) {
                        list.clear();
                        list.addAll(response.body());
                        adapter.notifyDataSetChanged();
                    } else {
                        Toast.makeText(WithdrawalHistoryActivity.this, "Failed to load history", Toast.LENGTH_SHORT).show();
                    }
                }

                @Override
                public void onFailure(Call<List<WithdrawModel>> call, Throwable t) {
                    Toast.makeText(WithdrawalHistoryActivity.this, "Network Error", Toast.LENGTH_SHORT).show();
                }
            });
    }

    // --- Adapter ---
    public class WithdrawalAdapter extends RecyclerView.Adapter<WithdrawalAdapter.ViewHolder> {
        List<WithdrawModel> mList;
        
        // Date Formatters
        SimpleDateFormat inputFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()); // ISO from Supabase
        SimpleDateFormat outputFormat = new SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()); // Readable

        public WithdrawalAdapter(List<WithdrawModel> list) { mList = list; }

        @NonNull @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_withdrawal, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            WithdrawModel model = mList.get(position);
            
            holder.amount.setText(String.format("₹ %.2f", model.getAmount()));
            
            // Parse Date String from Supabase
            if(model.getCreatedAt() != null) {
                try {
                    // Fix slight format issues if milliseconds are present
                    String cleanDate = model.getCreatedAt().split("\\.")[0]; 
                    Date date = inputFormat.parse(cleanDate);
                    if (date != null) {
                        holder.date.setText(outputFormat.format(date));
                    }
                } catch (ParseException e) {
                    holder.date.setText("Unknown Date");
                }
            }

            // STATUS LOGIC (Reviewing -> Processing -> Completed)
            holder.status.setText(model.getStatus());

            int color;
            if ("Completed".equalsIgnoreCase(model.getStatus())) {
                color = Color.parseColor("#4CAF50"); // Green
            } else if ("Processing".equalsIgnoreCase(model.getStatus())) {
                color = Color.parseColor("#2196F3"); // Blue
            } else {
                color = Color.parseColor("#FF9800"); // Orange (Reviewing)
            }
            holder.status.setBackgroundTintList(ColorStateList.valueOf(color));
        }

        @Override
        public int getItemCount() { return mList.size(); }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView date, amount, status;
            ViewHolder(View v) {
                super(v);
                date = v.findViewById(R.id.tv_date);
                amount = v.findViewById(R.id.tv_amount);
                status = v.findViewById(R.id.tv_status);
            }
        }
    }
}
