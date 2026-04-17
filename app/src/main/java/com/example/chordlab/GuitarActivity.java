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

import android.Manifest;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.util.Log;
import android.view.View;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.core.content.ContextCompat;

import com.example.chordlab.databinding.ActivityGuitarBinding;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark;
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult;

import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class GuitarActivity extends AppCompatActivity implements HandLandmarkerHelper.LandmarkerListener {

    private ActivityGuitarBinding binding;
    private ExecutorService cameraExecutor;
    private HandLandmarkerHelper aiHelper;
    private MediaPlayer successSound;

    private String sessionMode = "PRACTICE";

    private final String[] guitarChords = {
            "A Major", "A Minor", "C Major", "D Major", "D Minor", "E Minor", "G Major"
    };
    private int currentChordIndex = 0;

    private int currentScore = 0;
    private long matchStartTime = 0;
    private boolean isChordLocked = false;
    private boolean hasDinged = false;

    private CountDownTimer flashcardTimer;
    private final long TIME_LIMIT_MS = 13000;

    private final ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) startCamera();
                else finish();
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityGuitarBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        sessionMode = getIntent().getStringExtra("SESSION_MODE");
        if (sessionMode == null) sessionMode = "PRACTICE";

        cameraExecutor = Executors.newSingleThreadExecutor();
        aiHelper = new HandLandmarkerHelper(this, this);
        successSound = MediaPlayer.create(this, R.raw.correct_answer);

        setupUI();

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA);
        }

        showSessionInstructions();
    }

    private void showSessionInstructions() {
        binding.instructionOverlay.setVisibility(View.VISIBLE);
        binding.instructionOverlay.setAlpha(1f);

        binding.instructionOverlay.setOnClickListener(v -> hideInstructions());

        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
            if (binding.instructionOverlay.getVisibility() == View.VISIBLE) {
                hideInstructions();
            }
        }, 3000);
    }

    private void hideInstructions() {
        binding.instructionOverlay.animate()
                .alpha(0f)
                .setDuration(400)
                .withEndAction(() -> {
                    binding.instructionOverlay.setVisibility(View.GONE);
                });
    }

    private void setupUI() {
        binding.btnBack.setOnClickListener(v -> finish());
        binding.overlayView.setVisibility(View.GONE);

        if (sessionMode.equals("FLASHCARDS")) {
            binding.txtModeSubtitle.setText("CURRENT CARD");
            binding.timerProgress.setVisibility(View.VISIBLE);
            binding.txtTimer.setVisibility(View.VISIBLE);
            binding.txtScore.setVisibility(View.VISIBLE);

            binding.txtGuideTitle.setVisibility(View.GONE);
            binding.txtGuideDetails.setVisibility(View.GONE);
            binding.practiceNavContainer.setVisibility(View.GONE);

            loadRandomFlashcard();
        } else {
            binding.txtModeSubtitle.setText("PLAY THIS CHORD");
            binding.timerProgress.setVisibility(View.GONE);
            binding.txtTimer.setVisibility(View.GONE);
            binding.txtScore.setVisibility(View.GONE);

            binding.txtGuideTitle.setVisibility(View.VISIBLE);
            binding.txtGuideDetails.setVisibility(View.VISIBLE);
            binding.practiceNavContainer.setVisibility(View.VISIBLE);

            binding.btnNextChord.setOnClickListener(v -> {
                currentChordIndex = (currentChordIndex + 1) % guitarChords.length;
                updateChordDisplay();
            });
            binding.btnPrevChord.setOnClickListener(v -> {
                currentChordIndex = (currentChordIndex - 1 + guitarChords.length) % guitarChords.length;
                updateChordDisplay();
            });

            updateChordDisplay();
        }
    }

    private int getChordDrawable(String chordName) {
        switch (chordName) {
            case "A Major": return R.drawable.a_major_guitar;
            case "A Minor": return R.drawable.a_minor_guitar;
            case "C Major": return R.drawable.c_major_guitar;
            case "D Major": return R.drawable.d_major_guitar;
            case "D Minor": return R.drawable.d_minor_guitar;
            case "E Minor": return R.drawable.e_minor_guitar;
            case "G Major": return R.drawable.g_major_guitar;
            default: return R.drawable.a_major_guitar;
        }
    }

    private String getChordGuideText(String chordName) {
        switch (chordName) {
            case "A Major": return "1. Index → 2nd fret, 4th string (D)\n2. Middle → 2nd fret, 3rd string (G)\n3. Ring → 2nd fret, 2nd string (B)\nMute the 6th (E) string.";
            case "A Minor": return "1. Index → 1st fret, 2nd string (B)\n2. Middle → 2nd fret, 4th string (D)\n3. Ring → 2nd fret, 3rd string (G)\nMute the 6th (E) string.";
            case "C Major": return "1. Index → 1st fret, 2nd string (B)\n2. Middle → 2nd fret, 4th string (D)\n3. Ring → 3rd fret, 5th string (A)\nMute the 6th (E) string.";
            case "D Major": return "1. Index → 2nd fret, 3rd string (G)\n2. Middle → 2nd fret, 1st string (high e)\n3. Ring → 3rd fret, 2nd string (B)\nMute the 6th and 5th strings.";
            case "D Minor": return "1. Index → 1st fret, 1st string (high e)\n2. Middle → 2nd fret, 3rd string (G)\n3. Ring → 3rd fret, 2nd string (B)\nMute the 6th and 5th strings.";
            case "E Minor": return "1. Middle → 2nd fret, 5th string (A)\n2. Ring → 2nd fret, 4th string (D)\nPlay all 6 strings open.";
            case "G Major": return "1. Middle → 2nd fret, 5th string (A)\n2. Ring → 3rd fret, 6th string (E)\n3. Pinky → 3rd fret, 1st string (high e)\nPlay all 6 strings.";
            default: return "Position your fingers securely on the frets.";
        }
    }

    private void updateChordDisplay() {
        String target = guitarChords[currentChordIndex];
        binding.txtCurrentChord.setText(target);
        binding.imgChordDiagram.setImageResource(getChordDrawable(target));
        binding.txtGuideDetails.setText(getChordGuideText(target));

        binding.txtFeedback.setText("Position your fingers...");
        binding.txtFeedback.setBackgroundColor(Color.parseColor("#FFF3E0"));
        binding.txtFeedback.setTextColor(Color.parseColor("#E65100"));

        isChordLocked = false;
        matchStartTime = 0;
        hasDinged = false;
    }

    private void loadRandomFlashcard() {
        currentChordIndex = new Random().nextInt(guitarChords.length);
        updateChordDisplay();
        startTimer();
    }

    private void startTimer() {
        if (flashcardTimer != null) flashcardTimer.cancel();

        flashcardTimer = new CountDownTimer(TIME_LIMIT_MS, 100) {
            @Override
            public void onTick(long millisUntilFinished) {
                float secondsLeft = millisUntilFinished / 1000f;
                binding.txtTimer.setText(String.format("%.1fs", secondsLeft));
                int progress = (int) ((millisUntilFinished * 100) / TIME_LIMIT_MS);
                binding.timerProgress.setProgress(progress);
            }

            @Override
            public void onFinish() {
                isChordLocked = true;
                binding.txtFeedback.setText("❌ Time's up!");
                binding.txtFeedback.setBackgroundColor(Color.parseColor("#FFCDD2"));
                binding.txtFeedback.setTextColor(Color.parseColor("#B71C1C"));
                binding.getRoot().postDelayed(() -> loadRandomFlashcard(), 1500);
            }
        }.start();
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(binding.viewFinder.getSurfaceProvider());

                ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build();

                imageAnalysis.setAnalyzer(cameraExecutor, imageProxy -> {
                    aiHelper.detectLiveStream(imageProxy);
                    imageProxy.close();
                });

                cameraProvider.unbindAll();
                cameraProvider.bindToLifecycle(this, CameraSelector.DEFAULT_FRONT_CAMERA, preview, imageAnalysis);
            } catch (ExecutionException | InterruptedException e) {
                Log.e("ChordLabAI", "Camera Error: " + e.getMessage());
            }
        }, ContextCompat.getMainExecutor(this));
    }

    @Override
    public void onResults(HandLandmarkerResult result, long inferenceTime, int imageWidth, int imageHeight) {
        if (isChordLocked) return;

        if (result.landmarks() != null && !result.landmarks().isEmpty()) {
            List<NormalizedLandmark> hand = result.landmarks().get(0);
            String target = guitarChords[currentChordIndex];

            DetectionResult resultObj = GuitarChordAnalyzer.detectChord(hand, target, this);

            runOnUiThread(() -> {
                binding.overlayView.setResults(result);

                if (resultObj.isMatch) {
                    if (matchStartTime == 0) matchStartTime = System.currentTimeMillis();
                    long holdTimeMs = System.currentTimeMillis() - matchStartTime;

                    if (holdTimeMs >= 3000) {
                        isChordLocked = true;
                        binding.txtFeedback.setText("✅ Locked! Perfect!");
                        binding.txtFeedback.setBackgroundColor(Color.parseColor("#C8E6C9"));
                        binding.txtFeedback.setTextColor(Color.parseColor("#2E7D32"));

                        if (!hasDinged) {
                            if (successSound != null) successSound.start();
                            awardXPAndChords(); // AWARD XP HERE
                            hasDinged = true;
                        }

                        if (sessionMode.equals("FLASHCARDS")) {
                            currentScore += 10;
                            binding.txtScore.setText("Score: " + currentScore);
                            handleFlashcardSuccess();
                        }
                    } else {
                        long secondsLeft = 3 - (holdTimeMs / 1000);
                        binding.txtFeedback.setText("✅ Good! Hold for " + secondsLeft + "s...");
                        binding.txtFeedback.setBackgroundColor(Color.parseColor("#E8F5E9"));
                        binding.txtFeedback.setTextColor(Color.parseColor("#2E7D32"));
                    }

                } else {
                    matchStartTime = 0;
                    binding.txtFeedback.setText("Looking for " + target + "...");
                    binding.txtFeedback.setBackgroundColor(Color.parseColor("#E1F5FE"));
                    binding.txtFeedback.setTextColor(Color.parseColor("#0277BD"));

                    hasDinged = false;
                }
            });
        } else {
            matchStartTime = 0;
            runOnUiThread(() -> {
                binding.overlayView.setResults(null);

                if (!isChordLocked) {
                    String target = guitarChords[currentChordIndex];
                    binding.txtFeedback.setText("Looking for " + target + "...");
                    binding.txtFeedback.setBackgroundColor(Color.parseColor("#E1F5FE"));
                    binding.txtFeedback.setTextColor(Color.parseColor("#0277BD"));
                    hasDinged = false;
                }
            });
        }
    }

    private void awardXPAndChords() {
        SharedPreferences prefs = getSharedPreferences("UserSession", MODE_PRIVATE);
        String username = prefs.getString("username", "");
        if (!username.isEmpty()) {
            DatabaseHelper db = new DatabaseHelper(this);
            db.addExp(username, 1);
            db.incrementChordsLearned(username);
        }
    }

    private void handleFlashcardSuccess() {
        if (flashcardTimer != null) flashcardTimer.cancel();
        binding.getRoot().postDelayed(this::loadRandomFlashcard, 1500);
    }

    @Override
    public void onError(String error) { Log.e("ChordLabAI", error); }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (flashcardTimer != null) flashcardTimer.cancel();
        if (successSound != null) successSound.release();
        cameraExecutor.shutdown();
        if (aiHelper != null) aiHelper.close();
    }
}