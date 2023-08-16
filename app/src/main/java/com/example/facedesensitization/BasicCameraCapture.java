package com.example.facedesensitization;

public interface BasicCameraCapture {
    void setCameraId(int id);
    boolean initCamera();
    void setCameraFormat(int format);
    void setCameraScale(int width, int height);
    int getFrameLen();
    void setStreamCaptureCallback(StreamCaptureCallback cb);
}
