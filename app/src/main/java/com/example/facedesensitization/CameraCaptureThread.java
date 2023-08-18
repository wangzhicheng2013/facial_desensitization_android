package com.example.facedesensitization;

import android.content.Context;
import android.util.Log;


public class CameraCaptureThread extends Thread implements StreamCaptureCallback {
    private static final String TAG = "CameraCaptureThread";
    private BasicCameraCapture mCameraCapture;
    private final int CAMERA_ID = 0;
    public static final int CAMERA_WIDTH = 1920;
    public static final int CAMERA_HEIGHT = 1080;
    private Context mContext;
    public CameraCaptureThread(Context context) {
        mContext = context;
        mCameraCapture = new Camera2Capture(context);
        mCameraCapture.setCameraId(CAMERA_ID);
        mCameraCapture.setCameraFormat(Camera2Capture.NV12);
        mCameraCapture.setCameraScale(CAMERA_WIDTH, CAMERA_HEIGHT);
        mCameraCapture.setStreamCaptureCallback(this);
    }
    public boolean init() {
        return initCamera();
    }
    public boolean initCamera() {
        return mCameraCapture.initCamera();
    }
    @Override
    public void captureCallback(byte[] data) {
        if ((MainActivity.mQueue.size() < MainActivity.QUEUE_SIZE)
                && (MainActivity.CAMERA_TEST == MainActivity.mTestType)) {
            MainActivity.mQueue.offer(data);
        }
        else {
            Log.d(TAG, "queue is full!");
        }
    }
    @Override
    public void run() {
        while (true) {
            try {
                Thread.sleep(10000);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
