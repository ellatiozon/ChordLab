package com.example.chordlab;

/**
 * ChordLab: Polyphonic Note and Chord Detection System
 * * This file is a core component of the ChordLab backend architecture,
 * handling AI processing, multimodal sensor fusion, and/or state management.
 *
 * @author Mikhaella Mari D. Tiozon
 * @version 1.0
 * @since 2026-04-17
 * * Note: The algorithmic logic, machine learning integration, and database
 * architecture contained within this file are the original intellectual
 * property of the author.
 */

import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Outline;
import android.os.Bundle;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    private LinearLayout cardGuitar, cardPiano, cardUkelele;
    private LinearLayout selectedInstrumentCard = null;

    private String selectedInstrument = "Guitar";

    private DatabaseHelper myDb;
    private ProgressBar progressGuitar;
    private TextView tvProgressLevel, tvProgressExp;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        myDb = new DatabaseHelper(this);

        cardGuitar = findViewById(R.id.cardGuitar);
        cardPiano  = findViewById(R.id.cardPiano);
        cardUkelele = findViewById(R.id.cardUkelele);
        progressGuitar  = findViewById(R.id.progressGuitar);
        tvProgressLevel = findViewById(R.id.tvProgressLevel);
        tvProgressExp   = findViewById(R.id.tvProgressExp);

        TextView tvWelcomeName = findViewById(R.id.tvWelcomeName);
        SharedPreferences prefs = getSharedPreferences("UserSession", MODE_PRIVATE);
        String username = prefs.getString("username", "User");
        if (tvWelcomeName != null) {
            tvWelcomeName.setText("Welcome, " + username + "!");
        }

        cardGuitar.setOnClickListener(v -> selectInstrument(cardGuitar, "Guitar"));
        cardPiano.setOnClickListener(v  -> selectInstrument(cardPiano,  "Piano"));
        cardUkelele.setOnClickListener(v -> selectInstrument(cardUkelele, "Ukulele"));

        findViewById(R.id.cardPracticeMode).setOnClickListener(v -> {
            flashAndNavigate((LinearLayout) v, () -> startSession("PRACTICE"));
        });

        findViewById(R.id.cardFlashCards).setOnClickListener(v -> {
            flashAndNavigate((LinearLayout) v, () -> startSession("FLASHCARDS"));
        });

        findViewById(R.id.cardMetronome).setOnClickListener(v -> {
            flashAndNavigate((LinearLayout) v, () -> {
                startActivity(new Intent(this, MetronomeActivity.class));
            });
        });

        findViewById(R.id.ivProfileBtn).setOnClickListener(v -> {
            ProfileSheetFragment sheet = ProfileSheetFragment.newInstance();
            sheet.show(getSupportFragmentManager(), "profile");
        });

        setupCustomShadows();
    }

    @Override
    protected void onResume() {
        super.onResume();
        applyInstrumentHighlight();
        applyProgressDisplay();
    }

    private void startSession(String mode) {
        if (selectedInstrument.isEmpty()) {
            Toast.makeText(this, "Please select an instrument first!", Toast.LENGTH_SHORT).show();
            return;
        }

        Class<?> targetActivity;
        switch (selectedInstrument.toUpperCase()) {
            case "GUITAR":
                targetActivity = GuitarActivity.class;
                break;
            case "UKULELE":
                targetActivity = UkuleleActivity.class;
                break;
            default:
                targetActivity = PianoActivity.class;
                break;
        }

        Intent intent = new Intent(this, targetActivity);
        intent.putExtra("SESSION_MODE", mode);
        startActivity(intent);
    }

    private void selectInstrument(LinearLayout card, String instrumentName) {
        if (selectedInstrumentCard != null) {
            selectedInstrumentCard.setBackgroundResource(R.drawable.bg_instrument_normal);
        }
        card.setBackgroundResource(R.drawable.bg_instrument_selected);
        selectedInstrumentCard = card;

        selectedInstrument = instrumentName;

        SharedPreferences prefs = getSharedPreferences("UserSession", MODE_PRIVATE);
        prefs.edit().putString("instrument", instrumentName).apply();
        String username = prefs.getString("username", "");
        if (!username.isEmpty()) {
            String currentGoal = prefs.getString("dailyGoal", "20 mins");
            myDb.updateUserDetails(username, instrumentName, currentGoal);
        }
    }

    private void applyInstrumentHighlight() {
        SharedPreferences prefs = getSharedPreferences("UserSession", MODE_PRIVATE);
        String username = prefs.getString("username", "");

        cardGuitar.setBackgroundResource(R.drawable.bg_instrument_normal);
        cardPiano.setBackgroundResource(R.drawable.bg_instrument_normal);
        cardUkelele.setBackgroundResource(R.drawable.bg_instrument_normal);
        selectedInstrumentCard = null;

        String savedInstrument = "Guitar";
        Cursor cursor = myDb.getUserData(username);
        if (cursor != null && cursor.moveToFirst()) {
            int col = cursor.getColumnIndex("INSTRUMENT");
            if (col != -1) {
                String dbInstrument = cursor.getString(col);
                if (dbInstrument != null && !dbInstrument.isEmpty()) {
                    savedInstrument = dbInstrument;
                }
            }
            cursor.close();
        }

        prefs.edit().putString("instrument", savedInstrument).apply();
        selectedInstrument = savedInstrument; // Sync backend variable

        switch (savedInstrument) {
            case "Piano":
                cardPiano.setBackgroundResource(R.drawable.bg_instrument_selected);
                selectedInstrumentCard = cardPiano;
                break;
            case "Ukulele":
                cardUkelele.setBackgroundResource(R.drawable.bg_instrument_selected);
                selectedInstrumentCard = cardUkelele;
                break;
            default:
                cardGuitar.setBackgroundResource(R.drawable.bg_instrument_selected);
                selectedInstrumentCard = cardGuitar;
                break;
        }
    }

    private void applyProgressDisplay() {
        SharedPreferences prefs = getSharedPreferences("UserSession", MODE_PRIVATE);
        String username = prefs.getString("username", "");
        if (username.isEmpty()) return;

        int[] expLevel = myDb.getExpAndLevel(username);
        int exp   = expLevel[0];
        int level = expLevel[1];

        if(progressGuitar != null) progressGuitar.setProgress(exp);
        if(tvProgressLevel != null) tvProgressLevel.setText("Level " + level);
        if(tvProgressExp != null) tvProgressExp.setText(exp + " / 100 XP to Level " + (level + 1));
    }

    private void flashAndNavigate(LinearLayout card, Runnable navigateTo) {
        card.setBackgroundResource(R.drawable.bg_mode_selected);
        card.postDelayed(() -> {
            navigateTo.run();
            card.setBackgroundResource(R.drawable.bg_mode_normal);
        }, 200);
    }

    private void setupCustomShadows() {
        View profileBar = findViewById(R.id.profileBar);
        if (profileBar != null) {
            profileBar.setOutlineProvider(new ViewOutlineProvider() {
                @Override
                public void getOutline(View view, Outline outline) {
                    float radius = 40f * view.getResources().getDisplayMetrics().density;
                    outline.setRoundRect(0, 0, view.getWidth(), view.getHeight() + (int) radius, radius);
                }
            });
            profileBar.setClipToOutline(false);
        }
    }
}