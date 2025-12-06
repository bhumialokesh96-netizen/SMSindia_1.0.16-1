package com.smsindia.app.ui;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.fragment.app.Fragment;

import com.google.android.material.card.MaterialCardView;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.WriteBatch;
import com.smsindia.app.R;

import java.util.HashMap;
import java.util.Map;

public class ProfileFragment extends Fragment {

    private TextView tvMobile, tvBalance, tvBankName, tvBankAc;
    private ImageView imgProfile;
    private View layoutSavedBankView;
    private FirebaseFirestore db;
    private String uid;
    private double currentBalance = 0.0;
    private boolean hasBankDetails = false;

    // Withdrawal Options
    private int selectedAmount = 0;
    private final int[] WITHDRAWAL_OPTIONS = {100, 200, 300, 500, 2000, 5000};

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_profile, container, false);

        // Initialize Views
        tvMobile = v.findViewById(R.id.tv_profile_mobile);
        tvBalance = v.findViewById(R.id.tv_profile_balance);
        imgProfile = v.findViewById(R.id.img_profile);
        
        Button btnWithdraw = v.findViewById(R.id.btn_withdraw);
        Button btnHistory = v.findViewById(R.id.btn_withdraw_history);
        TextView btnAddBank = v.findViewById(R.id.btn_add_bank);
        
        layoutSavedBankView = v.findViewById(R.id.layout_saved_bank);
        tvBankName = v.findViewById(R.id.tv_bank_name);
        tvBankAc = v.findViewById(R.id.tv_bank_ac);

        // Initialize Firebase
        db = FirebaseFirestore.getInstance();
        SharedPreferences prefs = requireActivity().getSharedPreferences("SMSINDIA_USER", 0);
        uid = prefs.getString("mobile", "");

        tvMobile.setText(uid);
        fetchUserData();

        // --- CLICK LISTENERS ---

        // 1. Bank & Withdraw
        btnAddBank.setOnClickListener(view -> showAddBankDialog());
        btnWithdraw.setOnClickListener(view -> requestWithdrawal());

        // 2. History Page
        btnHistory.setOnClickListener(view -> {
            try {
                Intent intent = new Intent(getActivity(), Class.forName("com.smsindia.app.ui.WithdrawalHistoryActivity"));
                startActivity(intent);
            } catch (ClassNotFoundException e) {
                Toast.makeText(getContext(), "History Page Coming Soon", Toast.LENGTH_SHORT).show();
            }
        });

        return v;
    }

    private void fetchUserData() {
        if (uid.isEmpty()) return;
        db.collection("users").document(uid).addSnapshotListener((snapshot, e) -> {
            if (e != null || snapshot == null || !snapshot.exists()) return;

            Double bal = snapshot.getDouble("balance");
            currentBalance = (bal != null) ? bal : 0.0;
            tvBalance.setText(String.format("₹ %.2f", currentBalance));

            String name = snapshot.getString("name");
            if (name != null && !name.isEmpty()) tvMobile.setText(name);

            if (snapshot.contains("bank_account")) {
                Map<String, Object> bankMap = (Map<String, Object>) snapshot.get("bank_account");
                if (bankMap != null) {
                    hasBankDetails = true;
                    layoutSavedBankView.setVisibility(View.VISIBLE);
                    tvBankName.setText((String) bankMap.get("bank_name"));
                    tvBankAc.setText("AC: " + bankMap.get("account_no"));
                }
            }
        });
    }

    // --- BANK DETAILS DIALOG ---
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
        Map<String, Object> bankData = new HashMap<>();
        bankData.put("bank_name", name);
        bankData.put("account_no", ac);
        bankData.put("ifsc", ifsc);

        db.collection("users").document(uid)
                .update("bank_account", bankData)
                .addOnSuccessListener(a -> Toast.makeText(getContext(), "Bank details saved!", Toast.LENGTH_SHORT).show());
    }

    // --- WITHDRAWAL LOGIC ---
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
        WriteBatch batch = db.batch();
        DocumentReference userRef = db.collection("users").document(uid);
        DocumentReference withdrawRef = db.collection("users").document(uid).collection("withdrawals").document();
        DocumentReference historyRef = db.collection("users").document(uid).collection("transactions").document();

        batch.update(userRef, "balance", FieldValue.increment(-amount));

        Map<String, Object> req = new HashMap<>();
        req.put("amount", amount);
        req.put("status", "Reviewing");
        req.put("timestamp", FieldValue.serverTimestamp());
        batch.set(withdrawRef, req);

        Map<String, Object> tx = new HashMap<>();
        tx.put("title", "Withdrawal Request");
        tx.put("amount", amount);
        tx.put("type", "DEBIT");
        tx.put("timestamp", FieldValue.serverTimestamp());
        batch.set(historyRef, tx);

        batch.commit().addOnSuccessListener(a -> {
            parentDialog.dismiss();
            showSuccessPopup(); 
        }).addOnFailureListener(e -> {
            Toast.makeText(getContext(), "Failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
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
