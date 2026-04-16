package com.example.chordlab;

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

    // This interface lets the Helper shout back to the Activity when it finds a hand
// 1. Update the listener interface to include width and height
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
            // 1. Point to the brain file we downloaded
            BaseOptions baseOptions = BaseOptions.builder()
                    .setModelAssetPath("hand_landmarker.task")
                    .build();

            // 2. Configure the AI for live video (LIVE_STREAM)
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

        // MediaPipe requires a timestamp for live video streams
        long frameTime = SystemClock.uptimeMillis();

        // Convert the camera frame to a format MediaPipe understands (Bitmap -> MPImage)
        Bitmap bitmap = imageProxy.toBitmap();

        // Rotate the image to match the phone's orientation
        Matrix matrix = new Matrix();
        matrix.postRotate(imageProxy.getImageInfo().getRotationDegrees());
        Bitmap rotatedBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);

        MPImage mpImage = new BitmapImageBuilder(rotatedBitmap).build();

        // Send it to the brain asynchronously (so it doesn't freeze the screen)
        handLandmarker.detectAsync(mpImage, frameTime);
    }

    // MediaPipe triggers this when it finishes analyzing a frame
    // MediaPipe triggers this when it finishes analyzing a frame
    // 2. Update the result method to pass the image dimensions
    private void returnLivestreamResult(HandLandmarkerResult result, MPImage image) {
        long inferenceTime = SystemClock.uptimeMillis() - result.timestampMs();
        // Pass image.getWidth() and image.getHeight() back to the Activity
        listener.onResults(result, inferenceTime, image.getWidth(), image.getHeight());
    }
    // MediaPipe triggers this if something goes wrong
    private void returnLivestreamError(RuntimeException error) {
        listener.onError(error.getMessage());
    }

    public void close() {
        if (handLandmarker != null) {
            handLandmarker.close();
        }
    }
}