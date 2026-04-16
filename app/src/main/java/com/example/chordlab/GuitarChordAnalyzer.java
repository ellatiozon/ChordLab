package com.example.chordlab;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark;
import org.tensorflow.lite.Interpreter;

import java.io.FileInputStream;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.List;

public class GuitarChordAnalyzer {

    // Vision Model
    private static Interpreter visionTflite;
    private static String currentVisionModelPath = "";

    // Audio Model
    private static Interpreter audioTflite;
    private static String currentAudioModelPath = "";

    // 7 Classes based EXACTLY on your dataset export list
    private static final String[] GUITAR_LABELS = {
            "A Major", "A Minor", "C Major", "D Major", "D Minor", "E Minor", "G Major"
    };

    // --- VISION INITIALIZATION ---
    private static void initVisionModel(Context context) {
        String modelPath = "guitar_chord_model_v1.tflite";
        if (visionTflite != null && currentVisionModelPath.equals(modelPath)) return;

        try {
            AssetFileDescriptor fileDescriptor = context.getAssets().openFd(modelPath);
            FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
            FileChannel fileChannel = inputStream.getChannel();
            MappedByteBuffer buffer = fileChannel.map(FileChannel.MapMode.READ_ONLY,
                    fileDescriptor.getStartOffset(), fileDescriptor.getDeclaredLength());

            if (visionTflite != null) visionTflite.close();
            visionTflite = new Interpreter(buffer);
            currentVisionModelPath = modelPath;
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // --- AUDIO INITIALIZATION ---
    private static void initAudioModel(Context context) {
        // Change this to whatever you named your guitar audio model
        String modelPath = "guitar_audio_model_v1.tflite";
        if (audioTflite != null && currentAudioModelPath.equals(modelPath)) return;

        try {
            AssetFileDescriptor fileDescriptor = context.getAssets().openFd(modelPath);
            FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
            FileChannel fileChannel = inputStream.getChannel();
            MappedByteBuffer buffer = fileChannel.map(FileChannel.MapMode.READ_ONLY,
                    fileDescriptor.getStartOffset(), fileDescriptor.getDeclaredLength());

            if (audioTflite != null) audioTflite.close();
            audioTflite = new Interpreter(buffer);
            currentAudioModelPath = modelPath;
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // --- VISION DETECTION ---
    public static DetectionResult detectChord(List<NormalizedLandmark> landmarks, String targetChordName, Context context) {
        if (landmarks == null || landmarks.size() < 21) {
            return new DetectionResult("No Hand", "Show your hand clearly", false);
        }

        initVisionModel(context);

        float[][] input = new float[1][42];
        float wristX = landmarks.get(0).x();
        float wristY = landmarks.get(0).y();

        // Normalize wrist to (0,0)
        for (int i = 0; i < 21; i++) {
            input[0][i * 2] = landmarks.get(i).x() - wristX;
            input[0][i * 2 + 1] = landmarks.get(i).y() - wristY;
        }

        float[][] output = new float[1][GUITAR_LABELS.length];

        if (visionTflite != null) {
            try {
                visionTflite.run(input, output);
            } catch (Exception e) {
                e.printStackTrace();
                return new DetectionResult("Error", "Model shape mismatch", false);
            }
        }

        return processResults(output[0], targetChordName, 0.50f);
    }

    // --- AUDIO DETECTION ---
    public static DetectionResult detectAudioChord(float[][] audioInput, String targetChordName, Context context) {
        initAudioModel(context);

        float[][] output = new float[1][GUITAR_LABELS.length];

        if (audioTflite != null) {
            try {
                audioTflite.run(audioInput, output);
            } catch (Exception e) {
                e.printStackTrace();
                return new DetectionResult("Audio Error", "Waiting for sound...", false);
            }
        }

        return processResults(output[0], targetChordName, 0.60f);
    }

    // --- HELPER TO FIND WINNER ---
    private static DetectionResult processResults(float[] probabilities, String targetChordName, float threshold) {
        int maxIdx = -1;
        float maxProb = 0.0f;
        for (int i = 0; i < probabilities.length; i++) {
            if (probabilities[i] > maxProb) {
                maxProb = probabilities[i];
                maxIdx = i;
            }
        }

        if (maxIdx != -1 && maxProb > threshold) {
            String detectedName = GUITAR_LABELS[maxIdx];

            if (detectedName.equals("Background Noise")) {
                return new DetectionResult("Unknown", "Listening...", false);
            }

            boolean isMatch = detectedName.equalsIgnoreCase(targetChordName);
            return new DetectionResult(detectedName, isMatch ? "Perfect!" : "Try again...", isMatch);
        }

        return new DetectionResult("Unknown", "...", false);
    }
}