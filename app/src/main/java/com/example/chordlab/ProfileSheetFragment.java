package com.example.chordlab;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

public class ProfileSheetFragment extends BottomSheetDialogFragment {

    public static ProfileSheetFragment newInstance() {
        return new ProfileSheetFragment();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_profile_sheet, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        SharedPreferences prefs = requireActivity()
                .getSharedPreferences("UserSession", android.content.Context.MODE_PRIVATE);

        String username = prefs.getString("username", "Username");
        String email    = prefs.getString("email",    "username@gmail.com");

        // ── Read instrument and dailyGoal from DB (source of truth) ──
        DatabaseHelper myDb = new DatabaseHelper(requireContext());
        String instrument = "Guitar";  // fallback
        String dailyGoal  = "20 mins"; // fallback

        android.database.Cursor cursor = myDb.getUserData(username);
        if (cursor != null && cursor.moveToFirst()) {
            int instrumentCol = cursor.getColumnIndex("INSTRUMENT");
            int goalCol       = cursor.getColumnIndex("DAILY_GOAL");
            if (instrumentCol != -1) {
                String dbInstrument = cursor.getString(instrumentCol);
                if (dbInstrument != null && !dbInstrument.isEmpty()) instrument = dbInstrument;
            }
            if (goalCol != -1) {
                String dbGoal = cursor.getString(goalCol);
                if (dbGoal != null && !dbGoal.isEmpty()) dailyGoal = dbGoal;
            }
            cursor.close();
        }

        // Sync SharedPreferences with DB values
        prefs.edit()
                .putString("instrument", instrument)
                .putString("dailyGoal",  dailyGoal)
                .apply();

        // ── Populate views ──
        TextView tvUsername    = view.findViewById(R.id.tvUsername);
        TextView tvEmail       = view.findViewById(R.id.tvEmail);
        TextView tvInstrument  = view.findViewById(R.id.tvInstrumentStatus);
        ImageView ivInstrumentLogo = view.findViewById(R.id.tvInstrumentStatusLogo);
        TextView tvDailyGoal   = view.findViewById(R.id.tvDailyGoalCount);
        TextView tvGoalMinutes = view.findViewById(R.id.tvGoalMinutes);
        TextView tvChordsCount = view.findViewById(R.id.tvChordsCount);

        tvUsername.setText(username);
        tvEmail.setText(email);
        tvInstrument.setText(instrument);
        // --- FETCH THE CHORDS LEARNED ---
        int totalChordsLearned = myDb.getChordsLearned(username);
        tvChordsCount.setText(String.valueOf(totalChordsLearned));
        tvDailyGoal.setText("Daily\nGoal");
        tvGoalMinutes.setText(dailyGoal.equals("1 hr") ? "1 hour" : dailyGoal);

        if (ivInstrumentLogo != null) {
            switch (instrument) {
                case "Piano":   ivInstrumentLogo.setImageResource(R.drawable.ic_piano);   break;
                case "Ukulele": ivInstrumentLogo.setImageResource(R.drawable.ic_ukelele); break;
                default:        ivInstrumentLogo.setImageResource(R.drawable.ic_guitar);  break;
            }
        }

        // ── Button listeners (unchanged) ──
        // Make the Settings button open the DetailsActivity
        view.findViewById(R.id.btnSettings).setOnClickListener(v -> {
            dismiss(); // Close the bottom sheet
            Intent intent = new Intent(getActivity(), DetailsActivity.class);
            startActivity(intent);
        });

        view.findViewById(R.id.btnFaqs).setOnClickListener(v ->
                Toast.makeText(getContext(), "FAQs coming soon!", Toast.LENGTH_SHORT).show());

        view.findViewById(R.id.btnSupport).setOnClickListener(v ->
                Toast.makeText(getContext(), "Support coming soon!", Toast.LENGTH_SHORT).show());

        view.findViewById(R.id.btnLogOut).setOnClickListener(v -> {
            prefs.edit().clear().apply();
            new SessionManager(requireContext()).clearSession();
            dismiss();
            Intent intent = new Intent(getActivity(), LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
        });
    }
}