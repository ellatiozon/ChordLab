package com.example.chordlab;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.util.Log;
import org.tensorflow.lite.Interpreter;
import java.io.FileInputStream;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.LinkedList;

public class PianoChordAnalyzer {
    private static Interpreter tflite;

    // --- STATE TRACKING ---
    private static String currentLoadedFamily = "";
    private static String[] currentLabels;

    // --- PROTOTYPE MODE: SNAPPY SLIDING WINDOW ---
    private static final int WINDOW_SIZE = 4;
    private static final int REQUIRED_VOTES = 2;
    private static LinkedList<String> slidingWindow = new LinkedList<>();

    // --- DEMO UX: LONGER COOLDOWN ---
    private static long lastDetectionTime = 0;
    private static final long COOLDOWN_MS = 1500;

    // --- RESTORED: THE 3 LABEL DICTIONARIES ---
    private static final String[] MAJOR_LABELS = {
            "A major", "B major", "Background Noise", "C major",
            "D major", "E major", "F major", "G major"
    };

    private static final String[] MINOR_LABELS = {
            "A minor", "B minor", "Background Noise", "C minor",
            "D minor", "E minor", "F minor", "G minor"
    };

    private static final String[] ACCIDENTAL_LABELS = {
            "Background Noise", "Bb major", "Bb minor", "C# major", "C# minor",
            "Eb major", "Eb minor", "F# major", "F# minor", "G# major", "G# minor"
    };

    public static String detect(float[] audioBuffer, Context context, String targetFamily) {

        // HOT-SWAP LOGIC: 3-Brain System
        if (tflite == null || !targetFamily.equals(currentLoadedFamily)) {
            loadBrain(context, targetFamily);
        }
        if (tflite == null) return null;

        float maxAmplitude = 0.0f;
        for (float val : audioBuffer) {
            float absVal = Math.abs(val);
            if (absVal > maxAmplitude) {
                maxAmplitude = absVal;
            }
        }

        if (maxAmplitude < 0.05f) {
            slidingWindow.add("Noise");
            enforceWindowSize();
            return null;
        }

        float[][] input = new float[1][audioBuffer.length];
        for (int i = 0; i < audioBuffer.length; i++) {
            input[0][i] = audioBuffer[i] / maxAmplitude;
        }

        // Dynamically sizes the output array to 8 or 11 depending on the loaded brain!
        float[][] output = new float[1][currentLabels.length];

        try {
            tflite.run(input, output);
        } catch (Exception e) {
            Log.e("ChordLab_AI", "Inference Crash: " + e.getMessage());
            return null;
        }

        int maxIdx = -1;
        int secondMaxIdx = -1;
        float maxProb = 0.0f;
        float secondMaxProb = 0.0f;

        for (int i = 0; i < output[0].length; i++) {
            if (output[0][i] > maxProb) {
                secondMaxProb = maxProb;
                secondMaxIdx = maxIdx;
                maxProb = output[0][i];
                maxIdx = i;
            } else if (output[0][i] > secondMaxProb) {
                secondMaxProb = output[0][i];
                secondMaxIdx = i;
            }
        }

        String currentWinner = currentLabels[maxIdx];
        float confidenceDelta = maxProb - secondMaxProb;

        boolean isNoise = currentWinner.equalsIgnoreCase("Background Noise");
        boolean isWeak = maxProb < 0.60f || confidenceDelta < 0.20f;

        if (isNoise || isWeak) {
            slidingWindow.add("Noise");
        } else {
            slidingWindow.add(currentWinner);
        }
        enforceWindowSize();

        if (System.currentTimeMillis() - lastDetectionTime < COOLDOWN_MS) {
            return null;
        }

        String topCandidate = slidingWindow.getLast();

        if (!topCandidate.equals("Noise")) {
            int voteCount = 0;
            for (String frame : slidingWindow) {
                if (frame.equals(topCandidate)) {
                    voteCount++;
                }
            }

            if (voteCount >= REQUIRED_VOTES) {
                slidingWindow.clear();
                lastDetectionTime = System.currentTimeMillis();
                Log.d("ChordLab_AI", "✅ PROTOTYPE DEMO SUCCESS: " + topCandidate);
                return topCandidate;
            }
        }

        return null;
    }

    private static void enforceWindowSize() {
        while (slidingWindow.size() > WINDOW_SIZE) {
            slidingWindow.removeFirst();
        }
    }

    // --- THE 3-WAY HOT-SWAP ENGINE ---
    private static void loadBrain(Context context, String targetFamily) {
        try {
            if (tflite != null) {
                tflite.close();
            }

            String modelFile = "";

            switch (targetFamily) {
                case "MINOR":
                    modelFile = "piano_chords_minors_spectrogram.tflite";
                    currentLabels = MINOR_LABELS;
                    break;
                case "ACCIDENTAL":
                    modelFile = "piano_chords_flats_sharps_spectrogram.tflite";
                    currentLabels = ACCIDENTAL_LABELS;
                    break;
                default: // "MAJOR"
                    modelFile = "piano_chords_majors_spectrogram.tflite";
                    currentLabels = MAJOR_LABELS;
                    break;
            }

            AssetFileDescriptor fd = context.getAssets().openFd(modelFile);
            FileInputStream fis = new FileInputStream(fd.getFileDescriptor());
            MappedByteBuffer bb = fis.getChannel().map(FileChannel.MapMode.READ_ONLY,
                    fd.getStartOffset(), fd.getDeclaredLength());

            tflite = new Interpreter(bb);
            currentLoadedFamily = targetFamily;

            slidingWindow.clear();

            Log.d("ChordLab_AI", "🧠 Swapped Brains! Now running: " + modelFile);
        } catch (Exception e) {
            Log.e("ChordLab_AI", "Model Load Error: " + e.getMessage());
        }
    }
}