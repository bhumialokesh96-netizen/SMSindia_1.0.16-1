package com.smsindia.app.ui;

import android.content.SharedPreferences;
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
import com.smsindia.app.config.Constants;
import com.smsindia.app.service.SupabaseApi;
import com.smsindia.app.service.TransactionModel;

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

public class HistoryActivity extends AppCompatActivity {


    private RecyclerView recyclerView;
    private HistoryAdapter adapter;
    private List<TransactionModel> list;
    private SupabaseApi supabaseApi;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history);

        recyclerView = findViewById(R.id.recycler_history);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        
        list = new ArrayList<>();
        adapter = new HistoryAdapter(list);
        recyclerView.setAdapter(adapter);

        // Init Retrofit
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(Constants.SUPABASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build();
        supabaseApi = retrofit.create(SupabaseApi.class);

        SharedPreferences prefs = getSharedPreferences("SMSINDIA_USER", 0);
        // CRITICAL: Use UUID, not Phone
        String userIdUUID = prefs.getString("userId", "");

        if(!userIdUUID.isEmpty()) {
            loadHistory(userIdUUID);
        } else {
            Toast.makeText(this, "User ID missing. Please Relogin.", Toast.LENGTH_SHORT).show();
        }
    }

    private void loadHistory(String uuid) {
        // Query: user_id = UUID, ordered by created_at DESC
        supabaseApi.getTransactions(Constants.SUPABASE_ANON_KEY, "Bearer " + Constants.SUPABASE_ANON_KEY, "eq." + uuid, "created_at.desc")
            .enqueue(new Callback<List<TransactionModel>>() {
                @Override
                public void onResponse(Call<List<TransactionModel>> call, Response<List<TransactionModel>> response) {
                    if (response.isSuccessful() && response.body() != null) {
                        list.clear();
                        list.addAll(response.body());
                        
                        if (list.isEmpty()) {
                            Toast.makeText(HistoryActivity.this, "No transactions found", Toast.LENGTH_SHORT).show();
                        }
                        adapter.notifyDataSetChanged();
                    } else {
                        Toast.makeText(HistoryActivity.this, "Failed to load history", Toast.LENGTH_SHORT).show();
                    }
                }

                @Override
                public void onFailure(Call<List<TransactionModel>> call, Throwable t) {
                    Toast.makeText(HistoryActivity.this, "Network Error", Toast.LENGTH_SHORT).show();
                }
            });
    }

    // --- Adapter ---
    public class HistoryAdapter extends RecyclerView.Adapter<HistoryAdapter.ViewHolder> {
        List<TransactionModel> mList;
        
        // Date Formatters
        SimpleDateFormat inputFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault());
        SimpleDateFormat outputFormat = new SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault());

        public HistoryAdapter(List<TransactionModel> list) { mList = list; }

        @NonNull @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_history, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            TransactionModel model = mList.get(position);
            holder.title.setText(model.title);
            
            // Format Date
            if(model.createdAt != null) {
                try {
                    String cleanDate = model.createdAt.split("\\.")[0]; // Remove milliseconds
                    Date date = inputFormat.parse(cleanDate);
                    if (date != null) holder.date.setText(outputFormat.format(date));
                } catch (ParseException e) {
                    holder.date.setText("Just now");
                }
            }

            // Format Amount and Color
            if ("DEBIT".equalsIgnoreCase(model.type)) {
                holder.amount.setText("- ₹" + model.amount);
                holder.amount.setTextColor(Color.RED);
            } else {
                holder.amount.setText("+ ₹" + model.amount);
                holder.amount.setTextColor(Color.parseColor("#4CAF50")); // Green
            }
        }

        @Override
        public int getItemCount() { return mList.size(); }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView title, date, amount;
            ViewHolder(View v) {
                super(v);
                title = v.findViewById(R.id.tv_tx_title);
                date = v.findViewById(R.id.tv_tx_date);
                amount = v.findViewById(R.id.tv_tx_amount);
            }
        }
    }
}
