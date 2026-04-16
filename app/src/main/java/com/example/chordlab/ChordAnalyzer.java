package com.example.chordlab;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark;
import org.tensorflow.lite.Interpreter;
import java.io.FileInputStream;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.List;

public class ChordAnalyzer {

    // Vision Model
    private static Interpreter visionTflite;
    private static String currentVisionModelPath = "";

    // Audio Model
    private static Interpreter audioTflite;
    private static String currentAudioModelPath = "";

    // IMPORTANT: Make sure your TM Audio model classes match this order exactly!
    // (If you added "Background Noise", put it at the very end or beginning based on your TM export)
    private static final String[] UKULELE_LABELS = {
            "A Major", "A Minor", "C Major", "C Minor", "D Major",
            "D Minor", "E Minor", "F Major", "G Major", "G Minor"
    };

    // --- VISION INITIALIZATION ---
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

    // --- AUDIO INITIALIZATION ---
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

    // --- VISION DETECTION ---
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

    // --- AUDIO DETECTION ---
    // TM Audio models usually take a float array of 1-second audio samples (15600 length for 16kHz)
    public static DetectionResult detectAudioChord(float[][] audioInput, String targetChordName, Context context) {
        initAudioModel(context);

        // Output array size depends on how many classes you trained in TM (e.g., 10 chords + 1 background)
        // Adjust the size if you have a "Background Noise" class!
        float[][] output = new float[1][UKULELE_LABELS.length];

        if (audioTflite != null) {
            try {
                audioTflite.run(audioInput, output);
            } catch (Exception e) {
                // Failsafe in case the input shape from the mic doesn't perfectly match TM's expected shape yet
                e.printStackTrace();
                return new DetectionResult("Audio Error", "Waiting for sound...", false);
            }
        }

        // Higher threshold for audio (0.60) so it doesn't trigger on random noises
        return processResults(output[0], targetChordName, 0.60f);
    }

    // --- HELPER TO FIND WINNER ---
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