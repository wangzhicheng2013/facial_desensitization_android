package com.example.facedesensitization;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.util.Log;
import android.util.Size;
import android.view.Surface;
import android.view.TextureView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class Camera2Capture implements BasicCameraCapture {
    private static final String TAG = "Camera2Capture";
    public static final int YUV420 = ImageFormat.YUV_420_888;
    public static final int NV12 = 1;
    public static final int NV21 = 2;
    public static final int PREVIEW_FORMAT = YUV420;
    public static int IMAGE_CACHE_CAPACITY = 3;
    protected String mCameraId;
    protected StreamCaptureCallback mCaptureCallback;

    protected Context mContext;
    protected CameraManager mCameraManager = null;
    protected CameraDevice mCameraDevice = null;

    protected ImageReader mPreviewReader = null;

    // background thread to process image
    protected HandlerThread mBackgroundThread;
    protected Handler mBackgroundHandler;

    protected Size mPreviewSize = null;
    protected int mPreviewFormat = -1;
    protected int mCameraFormat = -1;
    protected int mCameraWidth = 0;
    protected int mCameraHeight = 0;
    protected byte mPreviewData[] = null;
    public Camera2Capture(Context context) {
        mContext = context;
    }
    protected boolean openCamera() {
        try {
            if (ContextCompat.checkSelfPermission(mContext, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                Log.e(TAG, "check camera permission failed!");
                return false;
            }
            mCameraManager.openCamera(mCameraId, mCameraStateCallback, mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
            Log.e(TAG, "open camera failed:" + e.toString());
            return false;
        }
        return true;
    }
    protected boolean initCameraManager() {
        mCameraManager = (CameraManager)mContext.getSystemService(Context.CAMERA_SERVICE);
        if (null == mCameraManager) {
            Log.e(TAG, "can not get camera manager!");
            return false;
        }
        return true;
    }
    protected boolean getPreviewFormat(StreamConfigurationMap streamConfigurationMap) {
        int[] formats = streamConfigurationMap.getOutputFormats();
        for (int format : formats) {
            Log.d(TAG, "camera support format:" + format);
        }
        for (int format : formats) {
            switch (format) {
                case PREVIEW_FORMAT:
                    mPreviewFormat = format;
                    Log.d(TAG, "camera preview format is:" + PREVIEW_FORMAT);
                    break;
            }
        }
        return -1 != mPreviewFormat;
    }
    public Size setOptimalPreviewSize(Size[] sizes, int previewViewWidth, int previewViewHeight) {
        List<Size> bigEnoughSizes = new ArrayList<>();
        List<Size> notBigEnoughSizes = new ArrayList<>();
        for (Size size : sizes) {
            if (size.getWidth() >= previewViewWidth && size.getHeight() >= previewViewHeight) {
                bigEnoughSizes.add(size);
            } else {
                notBigEnoughSizes.add(size);
            }
        }
        if (bigEnoughSizes.size() > 0) {
            return Collections.min(bigEnoughSizes, new Comparator<Size>() {
                @Override
                public int compare(Size lhs, Size rhs) {
                    return Long.signum((long) lhs.getWidth() * lhs.getHeight() -
                            (long) rhs.getWidth() * rhs.getHeight());
                }
            });
        } else if (notBigEnoughSizes.size() > 0) {
            return Collections.max(notBigEnoughSizes, new Comparator<Size>() {
                @Override
                public int compare(Size lhs, Size rhs) {
                    return Long.signum((long) lhs.getWidth() * lhs.getHeight() -
                            (long) rhs.getWidth() * rhs.getHeight());
                }
            });
        } else {
            Log.d(TAG, "No suitable preview size found!");
            return sizes[0];
        }
    }
    protected boolean getCameraInfo() {
        if (null == mCameraManager) {
            return false;
        }
        boolean cameraid_success = false;
        try {
            String[] cameraIdList = mCameraManager.getCameraIdList();
            if (0 == cameraIdList.length) {
                Log.e(TAG, "can not get camera id!");
                return false;
            }
            for (String cameraId : cameraIdList) {
                if (0 != cameraId.compareTo(mCameraId)) {
                    continue;
                }
                cameraid_success = true;
                Log.d(TAG, "get camera id:" + mCameraId);
                CameraCharacteristics cameraCharacteristics = mCameraManager.getCameraCharacteristics(cameraId);
                StreamConfigurationMap streamConfigurationMap = cameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                if (!getPreviewFormat(streamConfigurationMap)) {
                    Log.e(TAG, "can not get camera preview format!");
                    return false;
                }
                if (!getPreviewSize(streamConfigurationMap)) {
                    Log.e(TAG, "can not get camera preview size!");
                    return false;
                }
                break;
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
            Log.e(TAG, "get camera info exception:" + e.toString());
            return false;
        }
        return cameraid_success;
    }
    protected boolean getPreviewSize(StreamConfigurationMap streamConfigurationMap) {
        if (-1 == mPreviewFormat) {
            return false;
        }
        Size[] outputSizes = streamConfigurationMap.getOutputSizes(mPreviewFormat);
        for(Size size : outputSizes) {
            Log.d(TAG,"camera support preview size width:" + size.getWidth() + " height:" + size.getHeight());
        }
        mPreviewSize = setOptimalPreviewSize(outputSizes, mCameraWidth, mCameraHeight);
        Log.d(TAG,"best optimal preview width:" + mPreviewSize.getWidth() + " height:" + mPreviewSize.getHeight());
        return true;
    }
    protected boolean initImageReader() {
        Log.d(TAG, "camera preview width:" + mPreviewSize.getWidth() + " height:" + mPreviewSize.getHeight() + " format:" + mPreviewFormat);
        mPreviewReader = ImageReader.newInstance(mPreviewSize.getWidth(), mPreviewSize.getHeight(), mPreviewFormat, IMAGE_CACHE_CAPACITY);
        if (null == mPreviewReader) {
            Log.e(TAG, "ImageReader new instance failed...!");
            return false;
        }
        Log.d(TAG, "init image reader ok!");
        mPreviewReader.setOnImageAvailableListener(mPreviewImageAvailableListener, mBackgroundHandler);
        return true;
    }

    protected void freeImageReader() {
        if (null == mPreviewReader) {
            return;
        }
        mPreviewReader.close();
        mPreviewReader = null;
    }
    protected void initBackgroundThread() {
        mBackgroundThread = new HandlerThread("Camera2CaptureThread");
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper(), new Handler.Callback() {
            @Override
            public boolean handleMessage(@NonNull Message message) {
                if (message.what != 1) {
                    return false;
                }
                final int Y_SIZE = mPreviewReader.getWidth() * mPreviewReader.getHeight();
                final int SIZE = Y_SIZE * 3 / 2;
                if (null == mPreviewData) { // 8,989,056 - 5,992,704
                    mPreviewData = new byte[SIZE];
                }
                final Image img = (Image)message.obj;
                Log.d(TAG, "Capture Y size " + img.getPlanes()[0].getBuffer().remaining());
                Log.d(TAG, "Capture U size " + img.getPlanes()[1].getBuffer().remaining());
                Log.d(TAG, "Capture V size " + img.getPlanes()[2].getBuffer().remaining());
                Log.d(TAG, "Preview wdith:" + mPreviewReader.getWidth() + " height:" + mPreviewReader.getHeight());
                img.getPlanes()[0].getBuffer().get(mPreviewData, 0, img.getPlanes()[0].getBuffer().remaining());
                int remaining_size = mPreviewData.length - Y_SIZE;
                int index = (NV12 == mCameraFormat) ? 1 : 2;    // plane1:uvuv... plane2:vuvu...
                remaining_size = (remaining_size >= img.getPlanes()[index].getBuffer().remaining()) ? img.getPlanes()[index].getBuffer().remaining() : remaining_size;
                img.getPlanes()[index].getBuffer().get(mPreviewData, mPreviewReader.getWidth() * mPreviewReader.getHeight(), remaining_size);
                img.close();
                if (mCaptureCallback != null) {
                    mCaptureCallback.captureCallback(mPreviewData);
                }
                return true;
            }
        });
    }
    // when image is ready send it to the background thread to process
    protected ImageReader.OnImageAvailableListener mPreviewImageAvailableListener = new ImageReader.OnImageAvailableListener() {
        @Override
        public void onImageAvailable(ImageReader reader) {
            if (!mBackgroundHandler.hasMessages(1)) {
                final Image img = reader.acquireNextImage();
                Message msg = new Message();
                msg.what = 1;
                msg.obj = img;
                mBackgroundHandler.sendMessage(msg);
            }
        }
    };
    // open camera to process
    protected CameraDevice.StateCallback mCameraStateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice camera) {
            mCameraDevice = camera;
            try {
                mCameraDevice.createCaptureSession(Arrays.asList(mPreviewReader.getSurface()), new CameraCaptureSession.StateCallback() {
                    @Override
                    public void onConfigured(@NonNull CameraCaptureSession session) {
                        try {
                            CaptureRequest.Builder builder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                            builder.addTarget(mPreviewReader.getSurface());
                            CaptureRequest captureRequest = builder.build();
                            session.setRepeatingRequest(captureRequest, new CameraCaptureSession.CaptureCallback() {
                                @Override
                                public void onCaptureProgressed(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull CaptureResult partialResult) {
                                    super.onCaptureProgressed(session, request, partialResult);
                                }
                                @Override
                                public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
                                    super.onCaptureCompleted(session, request, result);
                                }
                            }, mBackgroundHandler);
                        } catch (CameraAccessException e) {
                            e.printStackTrace();
                        }
                    }
                    @Override
                    public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                    }
                }, mBackgroundHandler);
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
        }
        @Override
        public void onDisconnected(@NonNull CameraDevice camera) {
            releaseCamera();
            freeImageReader();
        }
        @Override
        public void onError(@NonNull CameraDevice camera, int error) {
            releaseCamera();
            freeImageReader();
        }
    };
    private void releaseCamera() {
        if (mCameraDevice != null) {
            mCameraDevice.close();
            mCameraDevice = null;
        }
    }
    @Override
    protected void finalize() throws Throwable {
        super.finalize();
        mBackgroundThread.quit();
        mBackgroundThread = null;
        mBackgroundHandler = null;
    }
    @Override
    public void setCameraId(int id) {
        mCameraId = String.valueOf(id);
    }
    @Override
    public boolean initCamera() {
        if (false == initCameraManager()) {
            return false;
        }
        if (false == getCameraInfo()) {
            Log.e(TAG, "get camera info failed!");
            return false;
        }
        if ((mCameraFormat != NV12) && (mCameraFormat != NV21)) {
            Log.e(TAG, "error camera format:" + mCameraFormat);
            return false;
        }
        if ((mPreviewSize.getWidth() != mCameraWidth) || (mPreviewSize.getHeight() != mCameraHeight)) {
            Log.e(TAG, "camera width:" + mCameraWidth + " camera height:" + mCameraHeight + " error!");
            //return false;
        }
        initBackgroundThread();
        if (false == initImageReader()) {
            return false;
        }
        if (false == openCamera()) {
            return false;
        }
        Log.d(TAG, "init camera ok!");
        return true;
    }
    @Override
    public void setCameraFormat(int format) {
        mCameraFormat = format;
    }
    @Override
    public void setCameraScale(int width, int height) {
        mCameraWidth = width;
        mCameraHeight = height;
    }
    @Override
    public int getFrameLen() {
        return mCameraWidth * mCameraHeight * 3 / 2;
    }
    @Override
    public void setStreamCaptureCallback(StreamCaptureCallback cb) {
        mCaptureCallback = cb;
    }
}
