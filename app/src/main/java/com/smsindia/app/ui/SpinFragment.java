package com.smsindia.app.ui;

import android.animation.ObjectAnimator;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.fragment.app.Fragment;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.smsindia.app.R;
import java.util.Random;

public class SpinFragment extends Fragment {

    private LuckyWheelView wheelView;
    private Button btnSpin;
    private TextView tvTokens;
    private FirebaseFirestore db;
    private String uid;
    
    private long spinTokens = 0;
    private boolean isSpinning = false;

    // Wheel Data
    private Double[] rewardsValue = {0.6, 0.8, 10.0, 0.0, 100.0, 0.6};

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_spin, container, false);

        wheelView = v.findViewById(R.id.wheel_view);
        btnSpin = v.findViewById(R.id.btn_spin_now);
        tvTokens = v.findViewById(R.id.tv_spin_tokens);
        
        db = FirebaseFirestore.getInstance();
        SharedPreferences prefs = requireActivity().getSharedPreferences("SMSINDIA_USER", 0);
        uid = prefs.getString("mobile", "");

        fetchSpinTokens();

        btnSpin.setOnClickListener(view -> {
            if (isSpinning) return;
            if (spinTokens <= 0) {
                Toast.makeText(getContext(), "No Spin Tokens left! Refer friends to earn more.", Toast.LENGTH_LONG).show();
                return;
            }
            startRiggedSpin();
        });

        return v;
    }

    private void fetchSpinTokens() {
        if(uid.isEmpty()) return;
        db.collection("users").document(uid).addSnapshotListener((snapshot, e) -> {
            if (e != null || snapshot == null) return;
            Long tokens = snapshot.getLong("coins");
            spinTokens = (tokens != null) ? tokens : 0;
            tvTokens.setText(String.valueOf(spinTokens));
        });
    }

    private void startRiggedSpin() {
        isSpinning = true;
        btnSpin.setEnabled(false);
        btnSpin.setAlpha(0.6f);

        // Deduct Token
        db.collection("users").document(uid).update("coins", FieldValue.increment(-1));

        // --- PROBABILITY LOGIC ---
        int targetIndex;
        int rand = new Random().nextInt(100); 

        if (rand < 96) {
            targetIndex = (new Random().nextBoolean()) ? 0 : 5; // 0.6
        } else {
             int[] others = {1, 2, 3, 4};
             targetIndex = others[new Random().nextInt(others.length)];
        }
        
        float sectorAngle = 360f / 6f;
        float finalAngle = (360 - (targetIndex * sectorAngle)) + (360 * 10); 
        finalAngle -= (sectorAngle / 2); 

        ObjectAnimator animator = ObjectAnimator.ofFloat(wheelView, "rotation", 0f, finalAngle);
        animator.setDuration(4000);
        animator.setInterpolator(new DecelerateInterpolator());
        animator.start();

        Double reward = rewardsValue[targetIndex];

        animator.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                isSpinning = false;
                btnSpin.setEnabled(true);
                btnSpin.setAlpha(1.0f);
                handleWin(reward);
            }
        });
    }

    private void handleWin(Double reward) {
        if (reward > 0) {
            // Update Database
            db.collection("users").document(uid).update("balance", FieldValue.increment(reward));
            
            // Show Custom Dialog instead of Toast
            showWinDialog(reward);
            
        } else {
            Toast.makeText(getContext(), "Better Luck Next Time!", Toast.LENGTH_SHORT).show();
        }
    }

    // --- NEW DIALOG FUNCTION ---
    private void showWinDialog(Double amount) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        View view = LayoutInflater.from(getContext()).inflate(R.layout.dialog_spin_win, null);
        builder.setView(view);
        AlertDialog dialog = builder.create();
        
        // Make background transparent so rounded corners show
        if(dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        TextView tvAmount = view.findViewById(R.id.tv_win_amount);
        Button btnCollect = view.findViewById(R.id.btn_collect_win);

        tvAmount.setText("₹" + amount);

        btnCollect.setOnClickListener(v -> {
            dialog.dismiss();
        });

        dialog.setCancelable(false); // Force user to click Collect
        dialog.show();
    }
}
