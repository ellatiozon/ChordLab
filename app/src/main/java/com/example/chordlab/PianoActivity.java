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
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.Locale;
import java.util.Random;

public class PianoActivity extends AppCompatActivity {

    private TextView txtTimer, txtScore, txtTarget, txtModeSubtitle, txtFeedback;
    private TextView txtGuideTitle, txtGuideDetails;
    private ImageView imgPianoDiagram;
    private ProgressBar timerProgress;
    private View practiceNavContainer, chordSubModeContainer;
    private Button btnPrevTarget, btnNextTarget, btnMajorMode, btnMinorMode;

    private CardView cardNote, cardChord;
    private TextToSpeech tts;
    private MediaPlayer successSound;

    private Boolean isChordMode = null;
    private boolean isMajorMode = true;
    private String sessionMode = "PRACTICE";

    private AudioRecord audioRecord;
    private boolean isListening = false;
    private Thread audioThread;

    private static final int SAMPLE_RATE = 16000;
    private static final int FFT_SIZE = 4096;
    private static final int AI_BUFFER_SIZE = 16000;

    private long deafUntilMs = 0;
    private int currentScore = 0;
    private String currentTarget = "";
    private CountDownTimer flashcardTimer;
    private final long TIME_LIMIT_MS = 13000;
    private boolean isPausedForNextCard = false;
    private boolean hasDinged = false;

    private int currentNoteIndex = 0;
    private int currentChordIndex = 0;

    private final String[] noteTargets = {"C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"};
    private final String[] majorChordTargets = {"C major", "C# major", "D major", "Eb major", "E major", "F major", "F# major", "G major", "G# major", "A major", "Bb major", "B major"};
    private final String[] minorChordTargets = {"C minor", "C# minor", "D minor", "Eb minor", "E minor", "F minor", "F# minor", "G minor", "G# minor", "A minor", "Bb minor", "B minor"};

    private View bgNoteGradient, bgChordGradient;

    private View instructionOverlay;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_piano);

        sessionMode = getIntent().getStringExtra("SESSION_MODE");
        if (sessionMode == null) sessionMode = "PRACTICE";

        successSound = MediaPlayer.create(this, R.raw.correct_answer);

        initViews();
        setupModeSelection();
        setupChordSubModeSelection();
        setupUIForMode();

        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) tts.setLanguage(Locale.US);
        });

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            startAudioThread();
        } else {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, 101);
        }

        instructionOverlay = findViewById(R.id.instructionOverlay);
        showSessionInstructions();
    }

    //For alert instructions
    private void showSessionInstructions() {
        instructionOverlay.setVisibility(View.VISIBLE);
        instructionOverlay.setAlpha(1f);

        instructionOverlay.setOnClickListener(v -> hideInstructions());

        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
            if (instructionOverlay.getVisibility() == View.VISIBLE) {
                hideInstructions();
            }
        }, 3000);
    }

    private void hideInstructions() {
        instructionOverlay.animate()
                .alpha(0f)
                .setDuration(400)
                .withEndAction(() -> {
                    instructionOverlay.setVisibility(View.GONE);

                    // IMPORTANT: Start your game logic only now
                    if (sessionMode.equals("FLASHCARDS") && isChordMode != null) {
                        loadRandomFlashcard();
                    }
                });
    }

    private void initViews() {
        cardNote = findViewById(R.id.cardNoteMode);
        cardChord = findViewById(R.id.cardChordMode);
        chordSubModeContainer = findViewById(R.id.chordSubModeContainer);
        btnMajorMode = findViewById(R.id.btnMajorMode);
        btnMinorMode = findViewById(R.id.btnMinorMode);
        txtTimer = findViewById(R.id.txtTimer);
        timerProgress = findViewById(R.id.timerProgress);
        txtScore = findViewById(R.id.txtScore);
        txtTarget = findViewById(R.id.txtTarget);
        txtModeSubtitle = findViewById(R.id.txtModeSubtitle);
        txtFeedback = findViewById(R.id.txtFeedback);
        txtGuideTitle = findViewById(R.id.txtGuideTitle);
        txtGuideDetails = findViewById(R.id.txtGuideDetails);
        imgPianoDiagram = findViewById(R.id.imgPianoDiagram);
        practiceNavContainer = findViewById(R.id.practiceNavContainer);
        btnPrevTarget = findViewById(R.id.btnPrevTarget);
        btnNextTarget = findViewById(R.id.btnNextTarget);
        // Gradient Background Views
        bgNoteGradient = findViewById(R.id.bgNoteGradient);
        bgChordGradient = findViewById(R.id.bgChordGradient);
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());


    }

    private void setupUIForMode() {
        txtTarget.setText("Select Note or Chord");
        imgPianoDiagram.setVisibility(View.INVISIBLE);

        timerProgress.setVisibility(View.GONE);
        txtTimer.setVisibility(View.GONE);
        txtScore.setVisibility(View.GONE);
        txtGuideTitle.setVisibility(View.GONE);
        txtGuideDetails.setVisibility(View.GONE);
        practiceNavContainer.setVisibility(View.GONE);
        txtFeedback.setVisibility(View.GONE);

        if (sessionMode.equals("PRACTICE")) {
            btnNextTarget.setOnClickListener(v -> {
                if (isChordMode == null) return;
                if (isChordMode) {
                    String[] activeArray = isMajorMode ? majorChordTargets : minorChordTargets;
                    currentChordIndex = (currentChordIndex + 1) % activeArray.length;
                } else {
                    currentNoteIndex = (currentNoteIndex + 1) % noteTargets.length;
                }
                updateTargetDisplay();
            });

            btnPrevTarget.setOnClickListener(v -> {
                if (isChordMode == null) return;
                if (isChordMode) {
                    String[] activeArray = isMajorMode ? majorChordTargets : minorChordTargets;
                    currentChordIndex = (currentChordIndex - 1 + activeArray.length) % activeArray.length;
                } else {
                    currentNoteIndex = (currentNoteIndex - 1 + noteTargets.length) % noteTargets.length;
                }
                updateTargetDisplay();
            });
        }
    }

    private void setupModeSelection() {
        cardNote.setOnClickListener(v -> {
            isChordMode = false;

            bgNoteGradient.setBackgroundResource(R.drawable.active_gradient);
            bgChordGradient.setBackgroundResource(R.drawable.inactive_gradient);

            chordSubModeContainer.setVisibility(View.GONE);
            revealGameUI();
        });

        cardChord.setOnClickListener(v -> {
            isChordMode = true;

            bgChordGradient.setBackgroundResource(R.drawable.active_gradient);
            bgNoteGradient.setBackgroundResource(R.drawable.inactive_gradient);

            chordSubModeContainer.setVisibility(View.VISIBLE);
            revealGameUI();
        });
    }

    private void setupChordSubModeSelection() {
        btnMajorMode.setOnClickListener(v -> {
            isMajorMode = true;
            btnMajorMode.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#DB062F")));
            btnMinorMode.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#3E3129")));
            if (sessionMode.equals("FLASHCARDS")) loadRandomFlashcard(); else updateTargetDisplay();
        });

        btnMinorMode.setOnClickListener(v -> {
            isMajorMode = false;
            btnMinorMode.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#DB062F")));
            btnMajorMode.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#3E3129")));
            if (sessionMode.equals("FLASHCARDS")) loadRandomFlashcard(); else updateTargetDisplay();
        });
    }

    private void revealGameUI() {
        imgPianoDiagram.setVisibility(View.VISIBLE);
        txtFeedback.setVisibility(View.VISIBLE);

        if (sessionMode.equals("FLASHCARDS")) {
            txtModeSubtitle.setText("CURRENT CARD");
            timerProgress.setVisibility(View.VISIBLE);
            txtTimer.setVisibility(View.VISIBLE);
            txtScore.setVisibility(View.VISIBLE);
            loadRandomFlashcard();
        } else {
            txtModeSubtitle.setText("PLAY THIS TARGET");
            txtGuideTitle.setVisibility(View.VISIBLE);
            txtGuideDetails.setVisibility(View.VISIBLE);
            practiceNavContainer.setVisibility(View.VISIBLE);
            updateTargetDisplay();
        }
    }

    private void updateTargetDisplay() {
        if (isChordMode == null) return;
        String[] activeArray = isChordMode ? (isMajorMode ? majorChordTargets : minorChordTargets) : noteTargets;
        currentTarget = isChordMode ? activeArray[currentChordIndex] : activeArray[currentNoteIndex];

        txtTarget.setText(currentTarget);
        imgPianoDiagram.setImageResource(getTargetDrawable(currentTarget));
        txtGuideDetails.setText(isChordMode ? "Play the root, third, and fifth of " + currentTarget + "." : "Locate and press the " + currentTarget + " key.");

        txtFeedback.setText("Position your fingers...");
        txtFeedback.setBackgroundColor(Color.parseColor("#F8D77B")); // Jasmine
        txtFeedback.setTextColor(Color.parseColor("#DB062F")); // Fire Engine Red
        isPausedForNextCard = false;
        hasDinged = false;
    }

    private void loadRandomFlashcard() {
        if (isChordMode == null) return;
        String[] activeArray = isChordMode ? (isMajorMode ? majorChordTargets : minorChordTargets) : noteTargets;
        currentTarget = activeArray[new Random().nextInt(activeArray.length)];

        txtTarget.setText(currentTarget);
        imgPianoDiagram.setImageResource(getTargetDrawable(currentTarget));
        txtFeedback.setText("Listening...");
        txtFeedback.setBackgroundColor(Color.parseColor("#F8D77B"));
        txtFeedback.setTextColor(Color.parseColor("#DB062F"));

        isPausedForNextCard = false;
        hasDinged = false;

        if (tts != null) {
            String spoken = currentTarget.replace("#", " sharp").replace("Bb", "B flat").replace("Eb", "E flat");
            tts.speak(spoken, TextToSpeech.QUEUE_FLUSH, null, "flashcard");
            deafUntilMs = System.currentTimeMillis() + 1500;
        }
        startTimer();
    }

    private void startTimer() {
        if (flashcardTimer != null) flashcardTimer.cancel();
        flashcardTimer = new CountDownTimer(TIME_LIMIT_MS, 100) {
            @Override
            public void onTick(long millisUntilFinished) {
                txtTimer.setText(String.format("%.1fs", millisUntilFinished / 1000f));
                timerProgress.setProgress((int) ((millisUntilFinished * 100) / TIME_LIMIT_MS));
            }
            @Override
            public void onFinish() {
                isPausedForNextCard = true;
                txtFeedback.setText("❌ Time's up!");
                txtFeedback.setBackgroundColor(Color.parseColor("#FFCDD2"));
                txtFeedback.setTextColor(Color.parseColor("#B71C1C"));
                imgPianoDiagram.postDelayed(() -> loadRandomFlashcard(), 1500);
            }
        }.start();
    }

    private void handleFlashcardSuccess() {
        isPausedForNextCard = true;
        if (flashcardTimer != null) flashcardTimer.cancel();
        currentScore += 10;
        txtScore.setText("Score: " + currentScore);
        awardXPAndChords();
        txtFeedback.setText("✅ Speedrun Perfect!");
        txtFeedback.setBackgroundColor(Color.parseColor("#E8F5E9"));
        txtFeedback.setTextColor(Color.parseColor("#2E7D32"));
        if (successSound != null) successSound.start();
        imgPianoDiagram.postDelayed(this::loadRandomFlashcard, 1000);
    }

    private int getTargetDrawable(String targetName) {
        if (isChordMode != null && !isChordMode) {
            switch (targetName) {
                case "A": return R.drawable.a_note;
                case "A#": return R.drawable.asharp_bflat_note;
                case "B": return R.drawable.b_note;
                case "C": return R.drawable.c_note;
                case "C#":return R.drawable.csharp_dflat_note;
                case "D": return R.drawable.d_note;
                case "D#":return R.drawable.dsharp_eflat_note;
                case "E": return R.drawable.e_note;
                case "F": return R.drawable.f_note;
                case "F#":return R.drawable.fsharp_gflat_note;
                case "G": return R.drawable.g_note;
                case "G#": return R.drawable.gsharp_aflat_note;
                default: return R.drawable.c_note; // Safe fallback
            }
        }

        switch (targetName) {
            case "C major":  return R.drawable.c_major_piano;
            case "C# major": return R.drawable.csharp_major_piano;
            case "D major":  return R.drawable.d_major_piano;
            case "Eb major": return R.drawable.eflat_major_piano;
            case "E major":  return R.drawable.e_major_piano;
            case "F major":  return R.drawable.f_major_piano;
            case "F# major": return R.drawable.fsharp_major_piano;
            case "G major":  return R.drawable.g_major_piano;
            case "G# major": return R.drawable.gsharp_major_piano;
            case "A major":  return R.drawable.a_major_piano;
            case "Bb major": return R.drawable.bflat_major_piano;
            case "B major":  return R.drawable.b_major_piano;

            case "C minor":  return R.drawable.c_minor_piano;
            case "C# minor": return R.drawable.csharp_minor_piano;
            case "D minor":  return R.drawable.d_minor_piano;
            case "Eb minor": return R.drawable.eflat_minor_piano;
            case "E minor":  return R.drawable.e_minor_piano;
            case "F minor":  return R.drawable.f_minor_piano;
            case "F# minor": return R.drawable.fsharp_minor_piano;
            case "G minor":  return R.drawable.g_minor_piano;
            case "G# minor": return R.drawable.gsharp_minor_piano;
            case "A minor":  return R.drawable.a_minor_piano;
            case "Bb minor": return R.drawable.bflat_minor_piano;
            case "B minor":  return R.drawable.b_minor_piano;

            default: return R.drawable.c_major_piano;
        }
    }

    private void updateUIAndSpeak(String text) {
        if (text == null || currentTarget == null || currentTarget.isEmpty()) return;

        runOnUiThread(() -> {
            boolean isMatch = text.equalsIgnoreCase(currentTarget);

            if (!isMatch && isChordMode != null) {
                if (isChordMode) {
                    isMatch = text.toLowerCase().contains(currentTarget.toLowerCase().split(" ")[0]);
                } else {
                    isMatch = currentTarget.toLowerCase().startsWith(text.toLowerCase());
                }
            }

            if (isMatch) {
                if (sessionMode.equals("FLASHCARDS") && !isPausedForNextCard) {
                    handleFlashcardSuccess();
                } else if (sessionMode.equals("PRACTICE") && !hasDinged) {
                    txtFeedback.setText("✅ Perfect!");
                    txtFeedback.setBackgroundColor(Color.parseColor("#E8F5E9"));
                    txtFeedback.setTextColor(Color.parseColor("#2E7D32"));

                    if (successSound != null) successSound.start();
                    awardXPAndChords();
                    hasDinged = true;
                }
            } else {
                if (!isPausedForNextCard && !hasDinged) {
                    txtFeedback.setText("Listening for " + currentTarget + "...");
                    txtFeedback.setBackgroundColor(Color.parseColor("#F8D77B"));
                    txtFeedback.setTextColor(Color.parseColor("#DB062F"));
                }
            }
        });
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
    private void startAudioThread() { if (!isListening) { isListening = true; audioThread = new Thread(this::audioLoop); audioThread.start(); } }

    private void audioLoop() {
        short[] audioBuffer = new short[FFT_SIZE];
        float[] aiFullBuffer = new float[AI_BUFFER_SIZE];
        int aiPtr = 0;

        try {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                return;
            }

            int minBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);

            audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, Math.max(minBuf, AI_BUFFER_SIZE * 2));

            audioRecord.startRecording();
        } catch (Exception e) {
            Log.e("ChordLab_Audio", "Failed to start recording: " + e.getMessage());
            return;
        }

        be.tarsos.dsp.util.fft.FFT fft = new be.tarsos.dsp.util.fft.FFT(FFT_SIZE);

        while (isListening) {
            int read = audioRecord.read(audioBuffer, 0, FFT_SIZE);
            if (read > 0) {
                float[] frame = new float[FFT_SIZE];
                float sumSq = 0;

                for (int i = 0; i < read; i++) {
                    frame[i] = audioBuffer[i] / 32768.0f;
                    sumSq += frame[i] * frame[i];

                    aiFullBuffer[aiPtr] = frame[i];
                    aiPtr = (aiPtr + 1) % AI_BUFFER_SIZE;
                }

                float rms = (float) Math.sqrt(sumSq / read);

                if (System.currentTimeMillis() >= deafUntilMs && rms > 0.01f && !isPausedForNextCard) {

                    if (isChordMode != null && isChordMode) {
                        String targetFamily = "MAJOR"; // Default

                        String lowerTarget = currentTarget.toLowerCase();
                        if (lowerTarget.contains("#") || lowerTarget.contains("sharp") ||
                                lowerTarget.contains("b") || lowerTarget.contains("flat")) {
                            targetFamily = "ACCIDENTAL";
                        } else if (lowerTarget.contains("minor")) {
                            targetFamily = "MINOR";
                        }

                        String detectedChord = PianoChordAnalyzer.detect(aiFullBuffer, PianoActivity.this, targetFamily);
                        updateUIAndSpeak(detectedChord);

                    } else if (isChordMode != null) {
                        float[] amplitudes = new float[FFT_SIZE / 2];
                        fft.forwardTransform(frame);
                        fft.modulus(frame, amplitudes);

                        String detectedNote = findLoudestNote(amplitudes, SAMPLE_RATE, fft);
                        updateUIAndSpeak(detectedNote);
                    }
                }
            }
        }

        try {
            if (audioRecord != null) {
                audioRecord.stop();
                audioRecord.release();
            }
        } catch (Exception e) {
            Log.e("ChordLab_Audio", "Error stopping AudioRecord: " + e.getMessage());
        }
    }

    private String findLoudestNote(float[] amplitudes, int sampleRate, be.tarsos.dsp.util.fft.FFT fft) {
        int maxIdx = 0;
        for (int i = 1; i < amplitudes.length; i++) if (amplitudes[i] > amplitudes[maxIdx]) maxIdx = i;
        if (amplitudes[maxIdx] < 0.3f) return null;
        float freq = (float) fft.binToHz(maxIdx, sampleRate);
        for (String note : noteTargets) if (isNear(freq, getNoteFreq(note))) return note;
        return null;
    }

    private float getNoteFreq(String note) {
        switch(note) {
            case "C": return 261.63f; case "C#": return 277.18f; case "D": return 293.66f;
            case "D#": return 311.13f; case "E": return 329.63f; case "F": return 349.23f;
            case "F#": return 369.99f; case "G": return 392.00f; case "G#": return 415.30f;
            case "A": return 440.00f; case "A#": return 466.16f; case "B": return 493.88f;
            default: return 0;
        }
    }

    private boolean isNear(float detected, float target) { return Math.abs(detected - target) <= (target * 0.025f); }

    @Override
    protected void onDestroy() {
        isListening = false;
        if (audioRecord != null) { audioRecord.stop(); audioRecord.release(); }
        if (tts != null) { tts.stop(); tts.shutdown(); }
        if (flashcardTimer != null) flashcardTimer.cancel();
        if (successSound != null) successSound.release();
        super.onDestroy();
    }
}