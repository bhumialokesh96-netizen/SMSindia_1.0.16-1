package com.smsindia.app.ui;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri; // ✅ Added for opening links
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import com.google.android.material.card.MaterialCardView;
import com.google.gson.internal.LinkedTreeMap;
import com.smsindia.app.LoginActivity;
import com.smsindia.app.R;
import com.smsindia.app.service.AppConfigModel; // ✅ Added for Config
import com.smsindia.app.service.SupabaseApi;
import com.smsindia.app.service.UserModel;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class ProfileFragment extends Fragment {

    private static final String SUPABASE_URL = "https://appfwrpynfxfpcvpavso.supabase.co";
    private static final String SUPABASE_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImFwcGZ3cnB5bmZ4ZnBjdnBhdnNvIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NjIwOTQ2MTQsImV4cCI6MjA3NzY3MDYxNH0.Z-BMBjME8MVK5MS2KBgcCDgR7kXvDEjtcHrVfIUvwZY";

    private TextView tvMobile, tvBalance, tvBankName, tvBankAc;
    private View layoutSavedBankView;
    
    private SupabaseApi supabaseApi;
    private String mobileNumber;
    private String userIdUUID; 
    
    private double currentBalance = 0.0;
    private boolean hasBankDetails = false;

    // Withdrawal Options
    private int selectedAmount = 0;
    private final int[] WITHDRAWAL_OPTIONS = {100, 200, 300, 500, 2000, 5000};

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_profile, container, false);

        // Initialize Retrofit
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(SUPABASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build();
        supabaseApi = retrofit.create(SupabaseApi.class);

        // Initialize Views
        tvMobile = v.findViewById(R.id.tv_profile_mobile);
        tvBalance = v.findViewById(R.id.tv_profile_balance);
        
        Button btnWithdraw = v.findViewById(R.id.btn_withdraw);
        Button btnHistory = v.findViewById(R.id.btn_withdraw_history);
        Button btnLogout = v.findViewById(R.id.btn_logout); 

        // ✅ NEW SUPPORT BUTTONS
        Button btnSupportWa = v.findViewById(R.id.btn_support_wa);
        Button btnSupportTg = v.findViewById(R.id.btn_support_tg);

        TextView btnAddBank = v.findViewById(R.id.btn_add_bank);
        
        layoutSavedBankView = v.findViewById(R.id.layout_saved_bank);
        tvBankName = v.findViewById(R.id.tv_bank_name);
        tvBankAc = v.findViewById(R.id.tv_bank_ac);

        // Get User Info
        SharedPreferences prefs = requireActivity().getSharedPreferences("SMSINDIA_USER", 0);
        mobileNumber = prefs.getString("mobile", "");
        userIdUUID = prefs.getString("userId", ""); 

        tvMobile.setText(mobileNumber);

        // --- CLICK LISTENERS ---
        btnAddBank.setOnClickListener(view -> showAddBankDialog());
        btnWithdraw.setOnClickListener(view -> requestWithdrawal());
        
        btnHistory.setOnClickListener(view -> {
            startActivity(new Intent(getActivity(), WithdrawalHistoryActivity.class));
        });

        if (btnLogout != null) {
            btnLogout.setOnClickListener(view -> showLogoutConfirmation());
        }

        // ✅ SUPPORT LISTENERS
        if (btnSupportWa != null) {
            btnSupportWa.setOnClickListener(view -> fetchAndOpenLink("support_whatsapp"));
        }
        if (btnSupportTg != null) {
            btnSupportTg.setOnClickListener(view -> fetchAndOpenLink("support_telegram"));
        }

        return v;
    }

    @Override
    public void onResume() {
        super.onResume();
        fetchUserData(); 
    }

    // ==========================================
    // 1. SUPPORT LOGIC (NEW)
    // ==========================================
    private void fetchAndOpenLink(String configKey) {
        // Show loading toast
        Toast.makeText(getContext(), "Opening...", Toast.LENGTH_SHORT).show();

        supabaseApi.getConfig(SUPABASE_KEY, "Bearer " + SUPABASE_KEY, "eq." + configKey)
            .enqueue(new Callback<List<AppConfigModel>>() {
                @Override
                public void onResponse(Call<List<AppConfigModel>> call, Response<List<AppConfigModel>> response) {
                    String url = "";
                    if (response.isSuccessful() && response.body() != null && !response.body().isEmpty()) {
                        Object val = response.body().get(0).value;
                        if (val instanceof LinkedTreeMap) {
                            LinkedTreeMap<?,?> map = (LinkedTreeMap<?,?>) val;
                            if (map.containsKey("url")) url = (String) map.get("url");
                        }
                    }

                    if (url.length() > 0 && !url.equals("#")) {
                        try {
                            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                            startActivity(intent);
                        } catch (Exception e) {
                            Toast.makeText(getContext(), "Could not open link", Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        Toast.makeText(getContext(), "Support link coming soon!", Toast.LENGTH_SHORT).show();
                    }
                }

                @Override
                public void onFailure(Call<List<AppConfigModel>> call, Throwable t) {
                    Toast.makeText(getContext(), "Network Error", Toast.LENGTH_SHORT).show();
                }
            });
    }

    // ==========================================
    // 2. LOGOUT LOGIC
    // ==========================================
    private void showLogoutConfirmation() {
        new AlertDialog.Builder(getContext())
            .setTitle("Logout")
            .setMessage("Are you sure you want to logout?")
            .setPositiveButton("LOGOUT", (dialog, which) -> {
                performLogout();
            })
            .setNegativeButton("CANCEL", null)
            .show();
    }

    private void performLogout() {
        if (getActivity() == null) return;

        SharedPreferences prefs = requireActivity().getSharedPreferences("SMSINDIA_USER", 0);
        SharedPreferences.Editor editor = prefs.edit();
        editor.clear(); 
        editor.apply();

        Intent intent = new Intent(getActivity(), LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
    }

    // ==========================================
    // 3. USER DATA & BANK LOGIC
    // ==========================================

    private void fetchUserData() {
        if (mobileNumber.isEmpty()) return;

        supabaseApi.getUser(SUPABASE_KEY, "Bearer " + SUPABASE_KEY, "eq." + mobileNumber)
            .enqueue(new Callback<List<UserModel>>() {
                @Override
                public void onResponse(Call<List<UserModel>> call, Response<List<UserModel>> response) {
                    if (response.isSuccessful() && response.body() != null && !response.body().isEmpty()) {
                        UserModel user = response.body().get(0);
                        
                        currentBalance = user.getBalance();
                        tvBalance.setText(String.format("₹ %.2f", currentBalance));

                        Object bankObj = user.bankDetails; 
                        
                        if (bankObj instanceof LinkedTreeMap) {
                            LinkedTreeMap<?,?> map = (LinkedTreeMap<?,?>) bankObj;
                            if (map.containsKey("bank_name")) {
                                hasBankDetails = true;
                                layoutSavedBankView.setVisibility(View.VISIBLE);
                                tvBankName.setText(String.valueOf(map.get("bank_name")));
                                tvBankAc.setText("AC: " + map.get("account_no"));
                            }
                        }
                    }
                }
                @Override public void onFailure(Call<List<UserModel>> call, Throwable t) {}
            });
    }

    private void showAddBankDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        View view = LayoutInflater.from(getContext()).inflate(R.layout.dialog_add_bank, null);
        builder.setView(view);
        AlertDialog dialog = builder.create();
        if (dialog.getWindow() != null) dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));

        EditText etName = view.findViewById(R.id.et_bank_name);
        EditText etAc = view.findViewById(R.id.et_bank_ac);
        EditText etIfsc = view.findViewById(R.id.et_bank_ifsc);
        Button btnSave = view.findViewById(R.id.btn_save_bank);

        btnSave.setOnClickListener(v -> {
            String name = etName.getText().toString().trim();
            String ac = etAc.getText().toString().trim();
            String ifsc = etIfsc.getText().toString().trim();

            if (name.isEmpty() || ac.isEmpty() || ifsc.isEmpty()) {
                Toast.makeText(getContext(), "Please fill all fields", Toast.LENGTH_SHORT).show();
                return;
            }
            saveBankDetails(name, ac, ifsc);
            dialog.dismiss();
        });
        dialog.show();
    }

    private void saveBankDetails(String name, String ac, String ifsc) {
        Map<String, Object> bankDetails = new HashMap<>();
        bankDetails.put("bank_name", name);
        bankDetails.put("account_no", ac);
        bankDetails.put("ifsc", ifsc);

        Map<String, Object> updateBody = new HashMap<>();
        updateBody.put("bank_details", bankDetails);

        supabaseApi.updateUser(SUPABASE_KEY, "Bearer " + SUPABASE_KEY, "eq." + mobileNumber, updateBody)
            .enqueue(new Callback<Void>() {
                @Override
                public void onResponse(Call<Void> call, Response<Void> response) {
                    if (response.isSuccessful()) {
                        Toast.makeText(getContext(), "Bank details saved!", Toast.LENGTH_SHORT).show();
                        fetchUserData(); 
                    } else {
                        Toast.makeText(getContext(), "Failed to save", Toast.LENGTH_SHORT).show();
                    }
                }
                @Override public void onFailure(Call<Void> call, Throwable t) {}
            });
    }

    // ==========================================
    // 4. WITHDRAWAL LOGIC
    // ==========================================

    private void requestWithdrawal() {
        if (!hasBankDetails) {
            Toast.makeText(getContext(), "Please add bank details first", Toast.LENGTH_LONG).show();
            showAddBankDialog();
            return;
        }
        showAmountSelectionDialog();
    }

    private void showAmountSelectionDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        View view = LayoutInflater.from(getContext()).inflate(R.layout.dialog_select_amount, null);
        builder.setView(view);
        AlertDialog dialog = builder.create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        GridLayout gridLayout = view.findViewById(R.id.grid_amounts);
        Button btnConfirm = view.findViewById(R.id.btn_confirm_withdraw);
        selectedAmount = 0;

        for (int amount : WITHDRAWAL_OPTIONS) {
            View itemView = LayoutInflater.from(getContext()).inflate(R.layout.item_amount_box, gridLayout, false);
            TextView tvVal = itemView.findViewById(R.id.tv_amount_val);
            MaterialCardView card = itemView.findViewById(R.id.card_amount);

            tvVal.setText("₹" + amount);

            itemView.setOnClickListener(v -> {
                selectedAmount = amount;
                btnConfirm.setEnabled(true);
                btnConfirm.setText("Withdraw ₹" + amount);
                btnConfirm.setBackgroundResource(R.drawable.bg_gold_3d);
                btnConfirm.setTextColor(Color.parseColor("#5D4037")); 

                for (int i = 0; i < gridLayout.getChildCount(); i++) {
                    View child = gridLayout.getChildAt(i);
                    MaterialCardView c = child.findViewById(R.id.card_amount);
                    TextView t = child.findViewById(R.id.tv_amount_val);
                    c.setCardBackgroundColor(Color.parseColor("#FFFFFF"));
                    c.setStrokeColor(Color.parseColor("#E0E0E0"));
                    t.setTextColor(Color.BLACK);
                }

                card.setCardBackgroundColor(Color.parseColor("#FFF8E1"));
                card.setStrokeColor(Color.parseColor("#FFC107"));
                tvVal.setTextColor(Color.parseColor("#FF8F00"));
            });

            gridLayout.addView(itemView);
        }

        btnConfirm.setOnClickListener(v -> {
            if (currentBalance < selectedAmount) {
                Toast.makeText(getContext(), "Insufficient Balance!", Toast.LENGTH_SHORT).show();
            } else {
                processWithdrawal(selectedAmount, dialog);
            }
        });

        dialog.show();
    }

    private void processWithdrawal(int amount, AlertDialog parentDialog) {
        if (userIdUUID.isEmpty()) {
            Toast.makeText(getContext(), "Relogin required", Toast.LENGTH_SHORT).show();
            return;
        }

        String paymentInfo = "";
        if (tvBankName.getText().toString().length() > 0) {
            paymentInfo = tvBankName.getText().toString() + " | " + tvBankAc.getText().toString();
        } else {
            paymentInfo = "No Bank Details";
        }

        Map<String, Object> req = new HashMap<>();
        req.put("user_id", userIdUUID);
        req.put("amount", amount);
        req.put("status", "PENDING"); 
        req.put("upi_id", paymentInfo); 

        Toast.makeText(getContext(), "Processing...", Toast.LENGTH_SHORT).show();

        supabaseApi.requestWithdrawal(SUPABASE_KEY, "Bearer " + SUPABASE_KEY, req)
            .enqueue(new Callback<Void>() {
                @Override
                public void onResponse(Call<Void> call, Response<Void> response) {
                    if (response.isSuccessful()) {
                        deductBalance(amount);
                        parentDialog.dismiss();
                        showSuccessPopup();
                    } else {
                        try {
                            String errorBody = response.errorBody().string();
                            Toast.makeText(getContext(), "Failed: " + errorBody, Toast.LENGTH_LONG).show();
                        } catch (Exception e) {
                            Toast.makeText(getContext(), "Request Failed", Toast.LENGTH_SHORT).show();
                        }
                    }
                }

                @Override
                public void onFailure(Call<Void> call, Throwable t) {
                    Toast.makeText(getContext(), "Network Error", Toast.LENGTH_SHORT).show();
                }
            });
    }

    private void deductBalance(int amount) {
        double newBalance = currentBalance - amount;
        Map<String, Object> update = new HashMap<>();
        update.put("balance", newBalance);

        supabaseApi.updateUser(SUPABASE_KEY, "Bearer " + SUPABASE_KEY, "eq." + mobileNumber, update)
            .enqueue(new Callback<Void>() {
                @Override public void onResponse(Call<Void> c, Response<Void> r) { fetchUserData(); }
                @Override public void onFailure(Call<Void> c, Throwable t) {}
            });
    }

    private void showSuccessPopup() {
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        View view = LayoutInflater.from(getContext()).inflate(R.layout.dialog_success, null);
        builder.setView(view);
        AlertDialog dialog = builder.create();
        
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        Button btnOk = view.findViewById(R.id.btn_close_success);
        btnOk.setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }
}
