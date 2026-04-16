package com.example.chordlab;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;

public class DailyGoalActivity extends AppCompatActivity {

    // ── 10 goal pool (name, duration in minutes) ────────────────────────────
    private static final String[][] GOAL_POOL = {
            {"Warm Up Goals",           "5"},
            {"Practice C Major Chord",  "10"},
            {"Flash Card Review",       "10"},
            {"Metronome Drill",         "5"},
            {"Scale Run Practice",      "10"},
            {"Chord Transition Drill",  "10"},
            {"Finger Stretching",       "5"},
            {"Rhythm Clapping",         "5"},
            {"Song Section Practice",   "15"},
            {"Ear Training",            "10"},
            {"Sight Reading",           "10"},
            {"Improvisation Exercise",  "10"},
    };

    private static final int TASKS_PER_DAY = 4;

    // ── Views ────────────────────────────────────────────────────────────────
    private TextView tvProgressPercent, tvMinutesPracticed;
    private CircularProgressView circularProgress;
    private LinearLayout taskContainer;

    // ── State ────────────────────────────────────────────────────────────────
    private List<String[]> todayTasks;   // [name, minutes]
    private boolean[] taskCompleted;
    private SharedPreferences prefs;
    private String todayKey;
    private int totalGoalMinutes;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_daily_goal);

        prefs = getSharedPreferences("DailyGoalPrefs", MODE_PRIVATE);
        todayKey = getTodayKey();

        // ── Bind views ──
        tvProgressPercent  = findViewById(R.id.tvProgressPercent);
        tvMinutesPracticed = findViewById(R.id.tvMinutesPracticed);
        circularProgress   = findViewById(R.id.circularProgress);
        taskContainer      = findViewById(R.id.taskContainer);

        TextView tvCurrentStreak = findViewById(R.id.tvCurrentStreak);
        TextView tvBestStreak    = findViewById(R.id.tvBestStreak);

        // ── Back button ──
        findViewById(R.id.btnBackDailyGoal).setOnClickListener(v -> finish());

        // ── Load streak ──
        int currentStreak = prefs.getInt("currentStreak", 0);
        int bestStreak    = prefs.getInt("bestStreak",    0);
        tvCurrentStreak.setText(String.valueOf(currentStreak));
        tvBestStreak.setText(String.valueOf(bestStreak));

        // ── Load or generate today's tasks ──
        todayTasks     = loadOrGenerateTasks();
        taskCompleted  = loadCompletionState();
        totalGoalMinutes = getTotalMinutes();

        // ── Render tasks ──
        renderTasks();
        updateProgress();
    }

    // ── Generate or load today's 4 tasks ────────────────────────────────────
    private List<String[]> loadOrGenerateTasks() {
        List<String[]> tasks = new ArrayList<>();

        // Check if tasks already generated for today
        String savedTasks = prefs.getString(todayKey + "_tasks", null);

        if (savedTasks != null) {
            // Parse saved tasks  "name1|min1,name2|min2,..."
            String[] parts = savedTasks.split(",");
            for (String part : parts) {
                String[] pair = part.split("\\|");
                if (pair.length == 2) tasks.add(pair);
            }
        } else {
            // Pick 4 random tasks from pool
            List<String[]> pool = new ArrayList<>();
            for (String[] goal : GOAL_POOL) pool.add(goal);
            Collections.shuffle(pool, new Random());

            for (int i = 0; i < TASKS_PER_DAY && i < pool.size(); i++) {
                tasks.add(pool.get(i));
            }

            // Save for the day
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < tasks.size(); i++) {
                sb.append(tasks.get(i)[0]).append("|").append(tasks.get(i)[1]);
                if (i < tasks.size() - 1) sb.append(",");
            }
            prefs.edit().putString(todayKey + "_tasks", sb.toString()).apply();
        }

        return tasks;
    }

    // ── Load which tasks are completed ──────────────────────────────────────
    private boolean[] loadCompletionState() {
        boolean[] completed = new boolean[todayTasks.size()];
        Set<String> completedSet = prefs.getStringSet(
                todayKey + "_completed", new HashSet<>());
        for (int i = 0; i < todayTasks.size(); i++) {
            completed[i] = completedSet.contains(String.valueOf(i));
        }
        return completed;
    }

    // ── Save completion state ────────────────────────────────────────────────
    private void saveCompletionState() {
        Set<String> completedSet = new HashSet<>();
        for (int i = 0; i < taskCompleted.length; i++) {
            if (taskCompleted[i]) completedSet.add(String.valueOf(i));
        }
        prefs.edit().putStringSet(todayKey + "_completed", completedSet).apply();
    }

    // ── Render task cards ────────────────────────────────────────────────────
    private void renderTasks() {
        taskContainer.removeAllViews();

        for (int i = 0; i < todayTasks.size(); i++) {
            final int index = i;
            String[] task   = todayTasks.get(i);
            String name     = task[0];
            int minutes     = Integer.parseInt(task[1]);
            boolean done    = taskCompleted[i];

            View itemView = LayoutInflater.from(this)
                    .inflate(R.layout.item_task, taskContainer, false);

            TextView tvName   = itemView.findViewById(R.id.tvTaskName);
            TextView tvDetail = itemView.findViewById(R.id.tvTaskDetail);
            ImageView ivCheck = itemView.findViewById(R.id.ivTaskCheck);

            tvName.setText(name);
            tvDetail.setText(minutes + " min " + (done ? "Completed" : "Remaining"));
            tvName.setTextColor(done
                    ? getResources().getColor(R.color.accent_pink, null)
                    : getResources().getColor(R.color.text_dark, null));
            ivCheck.setImageResource(done
                    ? R.drawable.ic_check_done
                    : R.drawable.ic_check_empty);

            // Tap to toggle completion
            itemView.setOnClickListener(v -> {
                taskCompleted[index] = !taskCompleted[index];
                saveCompletionState();
                updateStreakIfAllDone();
                renderTasks();
                updateProgress();
            });

            taskContainer.addView(itemView);
        }
    }

    // ── Update circular progress and labels ──────────────────────────────────
    private void updateProgress() {
        int completedMinutes = 0;
        for (int i = 0; i < taskCompleted.length; i++) {
            if (taskCompleted[i]) {
                completedMinutes += Integer.parseInt(todayTasks.get(i)[1]);
            }
        }

        int percent = totalGoalMinutes == 0 ? 0
                : (int) ((completedMinutes / (float) totalGoalMinutes) * 100);
        percent = Math.min(100, percent);

        circularProgress.setProgress(percent);
        tvProgressPercent.setText(percent + " %");
        tvMinutesPracticed.setText(completedMinutes + " / " + totalGoalMinutes
                + " minutes practiced");
    }

    // ── Update streak if all tasks done ─────────────────────────────────────
    private void updateStreakIfAllDone() {
        boolean allDone = true;
        for (boolean b : taskCompleted) {
            if (!b) { allDone = false; break; }
        }

        if (allDone) {
            String lastCompleted = prefs.getString("lastCompletedDay", "");
            if (!lastCompleted.equals(todayKey)) {
                int streak = prefs.getInt("currentStreak", 0) + 1;
                int best   = Math.max(prefs.getInt("bestStreak", 0), streak);
                prefs.edit()
                        .putInt("currentStreak",    streak)
                        .putInt("bestStreak",        best)
                        .putString("lastCompletedDay", todayKey)
                        .apply();

                ((TextView) findViewById(R.id.tvCurrentStreak)).setText(String.valueOf(streak));
                ((TextView) findViewById(R.id.tvBestStreak)).setText(String.valueOf(best));
            }
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────
    private int getTotalMinutes() {
        int total = 0;
        for (String[] task : todayTasks) total += Integer.parseInt(task[1]);
        return total;
    }

    private String getTodayKey() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
    }
}