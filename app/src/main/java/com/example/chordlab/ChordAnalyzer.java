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

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark;
import org.tensorflow.lite.Interpreter;
import java.io.FileInputStream;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.List;

public class ChordAnalyzer {

    private static Interpreter visionTflite;
    private static String currentVisionModelPath = "";

    private static Interpreter audioTflite;
    private static String currentAudioModelPath = "";

    private static final String[] UKULELE_LABELS = {
            "A Major", "A Minor", "C Major", "C Minor", "D Major",
            "D Minor", "E Minor", "F Major", "G Major", "G Minor"
    };

    private static void initVisionModel(Context context) {
        String modelPath = "ukulele_chord_model.tflite";
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

    private static void initAudioModel(Context context) {
        String modelPath = "ukulele_audio_model.tflite"; // Name of your TM Audio export
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

    public static DetectionResult detectChord(List<NormalizedLandmark> landmarks, String targetChordName, String instrument, Context context) {
        if (landmarks == null || landmarks.size() < 21) {
            return new DetectionResult("No Hand", "Show your hand clearly", false);
        }

        initVisionModel(context);

        float[][] input = new float[1][42];
        float wristX = landmarks.get(0).x();
        float wristY = landmarks.get(0).y();

        for (int i = 0; i < 21; i++) {
            input[0][i * 2] = landmarks.get(i).x() - wristX;
            input[0][i * 2 + 1] = landmarks.get(i).y() - wristY;
        }

        float[][] output = new float[1][UKULELE_LABELS.length];
        if (visionTflite != null) visionTflite.run(input, output);

        return processResults(output[0], targetChordName, 0.50f);
    }

    public static DetectionResult detectAudioChord(float[][] audioInput, String targetChordName, Context context) {
        initAudioModel(context);

        float[][] output = new float[1][UKULELE_LABELS.length];

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

    private static DetectionResult processResults(float[] probabilities, String targetChordName, float threshold) {
        int maxIdx = -1;
        float maxProb = 0.0f;
        for (int i = 0; i < probabilities.length; i++) {
            // Ensure we don't accidentally select a "Background Noise" class if it's the highest
            if (probabilities[i] > maxProb && i < UKULELE_LABELS.length) {
                maxProb = probabilities[i];
                maxIdx = i;
            }
        }

        if (maxIdx != -1 && maxProb > threshold) {
            String detectedName = UKULELE_LABELS[maxIdx];
            boolean isMatch = detectedName.equalsIgnoreCase(targetChordName);
            return new DetectionResult(detectedName, isMatch ? "Perfect!" : "Try again...", isMatch);
        }

        return new DetectionResult("Unknown", "...", false);
    }
}