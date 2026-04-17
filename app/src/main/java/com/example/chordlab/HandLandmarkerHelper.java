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
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.os.SystemClock;
import android.util.Log;
import androidx.camera.core.ImageProxy;
import com.google.mediapipe.framework.image.BitmapImageBuilder;
import com.google.mediapipe.framework.image.MPImage;
import com.google.mediapipe.tasks.core.BaseOptions;
import com.google.mediapipe.tasks.vision.core.RunningMode;
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker;
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult;

public class HandLandmarkerHelper {

    private HandLandmarker handLandmarker;
    private final Context context;
    private final LandmarkerListener listener;

    public interface LandmarkerListener {
        void onError(String error);
        void onResults(HandLandmarkerResult result, long inferenceTime, int imageWidth, int imageHeight);
    }

    public HandLandmarkerHelper(Context context, LandmarkerListener listener) {
        this.context = context;
        this.listener = listener;
        setupHandLandmarker();
    }

    private void setupHandLandmarker() {
        try {
            BaseOptions baseOptions = BaseOptions.builder()
                    .setModelAssetPath("hand_landmarker.task")
                    .build();

            HandLandmarker.HandLandmarkerOptions options = HandLandmarker.HandLandmarkerOptions.builder()
                    .setBaseOptions(baseOptions)
                    .setRunningMode(RunningMode.LIVE_STREAM)
                    .setNumHands(1) // We only need to track the fretting hand!
                    .setMinHandDetectionConfidence(0.5f)
                    .setMinTrackingConfidence(0.5f)
                    .setResultListener(this::returnLivestreamResult)
                    .setErrorListener(this::returnLivestreamError)
                    .build();

            // 3. Turn it on
            handLandmarker = HandLandmarker.createFromOptions(context, options);
            Log.d("ChordLabAI", "AI Brain Successfully Loaded!");

        } catch (Exception e) {
            listener.onError("AI failed to initialize: " + e.getMessage());
        }
    }

    public void detectLiveStream(ImageProxy imageProxy) {
        if (handLandmarker == null) return;

        long frameTime = SystemClock.uptimeMillis();

        Bitmap bitmap = imageProxy.toBitmap();

        Matrix matrix = new Matrix();
        matrix.postRotate(imageProxy.getImageInfo().getRotationDegrees());
        Bitmap rotatedBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);

        MPImage mpImage = new BitmapImageBuilder(rotatedBitmap).build();

        handLandmarker.detectAsync(mpImage, frameTime);
    }
    private void returnLivestreamResult(HandLandmarkerResult result, MPImage image) {
        long inferenceTime = SystemClock.uptimeMillis() - result.timestampMs();
        listener.onResults(result, inferenceTime, image.getWidth(), image.getHeight());
    }
    private void returnLivestreamError(RuntimeException error) {
        listener.onError(error.getMessage());
    }

    public void close() {
        if (handLandmarker != null) {
            handLandmarker.close();
        }
    }
}