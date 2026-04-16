package com.example.chordlab;

import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Color;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

public class DetailsActivity extends AppCompatActivity {

    // Backend Variables
    private String selectedInstrument = "Guitar";
    private String selectedGoal       = "20 mins";
    private DatabaseHelper myDb;

    // UI Elements
    private LinearLayout optionGuitar, optionUkulele, optionPiano;
    private Button goal10, goal20, goal30, goal60;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_details);

        myDb = new DatabaseHelper(this);

        // Bind Views
        optionGuitar  = findViewById(R.id.optionGuitar);
        optionUkulele = findViewById(R.id.optionUkulele);
        optionPiano   = findViewById(R.id.optionPiano);

        goal10 = findViewById(R.id.goal10);
        goal20 = findViewById(R.id.goal20);
        goal30 = findViewById(R.id.goal30);
        goal60 = findViewById(R.id.goal60);

        // --- DATA HYDRATION (Load Existing Data) ---
        loadExistingUserData();

        // Listeners
        optionGuitar.setOnClickListener(v  -> selectInstrument(optionGuitar,  "Guitar"));
        optionUkulele.setOnClickListener(v -> selectInstrument(optionUkulele, "Ukulele"));
        optionPiano.setOnClickListener(v   -> selectInstrument(optionPiano,   "Piano"));

        goal10.setOnClickListener(v -> selectGoal(goal10, "10 mins"));
        goal20.setOnClickListener(v -> selectGoal(goal20, "20 mins"));
        goal30.setOnClickListener(v -> selectGoal(goal30, "30 mins"));
        // Make sure the string matches your exact XML text "1 hr"
        goal60.setOnClickListener(v -> selectGoal(goal60, "1 hour"));

        findViewById(R.id.btnBackDetails).setOnClickListener(v -> finish());
        findViewById(R.id.btnSaveContinue).setOnClickListener(v -> saveAndContinue());
    }

    private void loadExistingUserData() {
        SharedPreferences prefs = getSharedPreferences("UserSession", MODE_PRIVATE);
        String username = prefs.getString("username", "");

        if (!username.isEmpty()) {
            Cursor cursor = myDb.getUserData(username);
            if (cursor != null && cursor.moveToFirst()) {
                int instrumentCol = cursor.getColumnIndex("INSTRUMENT");
                int goalCol       = cursor.getColumnIndex("DAILY_GOAL");

                if (instrumentCol != -1) {
                    String dbInstrument = cursor.getString(instrumentCol);
                    if (dbInstrument != null && !dbInstrument.isEmpty()) selectedInstrument = dbInstrument;
                }
                if (goalCol != -1) {
                    String dbGoal = cursor.getString(goalCol);
                    if (dbGoal != null && !dbGoal.isEmpty()) selectedGoal = dbGoal;
                }
                cursor.close();
            }
        }

        // Programmatically select the saved instrument
        switch (selectedInstrument) {
            case "Piano": selectInstrument(optionPiano, "Piano"); break;
            case "Ukulele": selectInstrument(optionUkulele, "Ukulele"); break;
            default: selectInstrument(optionGuitar, "Guitar"); break;
        }

        // Programmatically select the saved goal
        switch (selectedGoal) {
            case "10 mins": selectGoal(goal10, "10 mins"); break;
            case "30 mins": selectGoal(goal30, "30 mins"); break;
            case "1 hour": selectGoal(goal60, "1 hour"); break;
            default: selectGoal(goal20, "20 mins"); break;
        }
    }

    // ── FOOLPROOF INSTRUMENT SELECTION ──
    private void selectInstrument(LinearLayout selected, String name) {
        // 1. HARD RESET ALL
        optionGuitar.setBackgroundResource(R.drawable.bg_instrument_normal);
        updateInstrumentTextColor(optionGuitar, false);

        optionUkulele.setBackgroundResource(R.drawable.bg_instrument_normal);
        updateInstrumentTextColor(optionUkulele, false);

        optionPiano.setBackgroundResource(R.drawable.bg_instrument_normal);
        updateInstrumentTextColor(optionPiano, false);

        // 2. HIGHLIGHT SELECTED
        selected.setBackgroundResource(R.drawable.bg_instrument_selected);
        updateInstrumentTextColor(selected, true);

        selectedInstrument = name;
    }

    private void updateInstrumentTextColor(LinearLayout card, boolean isSelected) {
        try {
            LinearLayout inner = (LinearLayout) card.getChildAt(1);
            TextView title = (TextView) inner.getChildAt(0);
            TextView sub   = (TextView) inner.getChildAt(1);

            if (isSelected) {
                title.setTextColor(Color.parseColor("#FFFFFF"));
                sub.setTextColor(Color.parseColor("#FFD0E8"));
            } else {
                title.setTextColor(ContextCompat.getColor(this, R.color.accent_pink));
                sub.setTextColor(ContextCompat.getColor(this, R.color.text_gray));
            }
        } catch (Exception ignored) {}
    }

    // ── FOOLPROOF GOAL SELECTION ──
    private void selectGoal(Button selected, String goal) {
        // 1. HARD RESET ALL to high-contrast unselected state
        goal10.setBackgroundResource(R.drawable.bg_sig_normal);
        goal10.setTextColor(Color.parseColor("#424242"));

        goal20.setBackgroundResource(R.drawable.bg_sig_normal);
        goal20.setTextColor(Color.parseColor("#424242"));

        goal30.setBackgroundResource(R.drawable.bg_sig_normal);
        goal30.setTextColor(Color.parseColor("#424242"));

        goal60.setBackgroundResource(R.drawable.bg_sig_normal);
        goal60.setTextColor(Color.parseColor("#424242"));

        // 2. HIGHLIGHT SELECTED
        selected.setBackgroundResource(R.drawable.bg_sig_selected);
        selected.setTextColor(Color.WHITE);

        selectedGoal = goal;
    }

    private void saveAndContinue() {
        SharedPreferences prefs = getSharedPreferences("UserSession", MODE_PRIVATE);
        String currentUsername = prefs.getString("username", "");

        if (currentUsername.isEmpty()) {
            Toast.makeText(this, "Error: No user session found", Toast.LENGTH_SHORT).show();
            return;
        }

        // Save to SQLite
        boolean isUpdated = myDb.updateUserDetails(currentUsername, selectedInstrument, selectedGoal);

        if (isUpdated) {
            prefs.edit()
                    .putString("instrument", selectedInstrument)
                    .putString("dailyGoal",  selectedGoal)
                    .putBoolean("detailsComplete", true)
                    .apply();

            Toast.makeText(this, "Settings Saved!", Toast.LENGTH_SHORT).show();

            // Navigate to Dashboard
            Intent intent = new Intent(this, MainActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        } else {
            Toast.makeText(this, "Failed to save to database.", Toast.LENGTH_SHORT).show();
        }
    }
}