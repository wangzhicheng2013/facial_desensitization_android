package com.example.facedesensitization;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.content.Context;
import com.example.facedesensitization.databinding.ActivityMainBinding;
import android.util.Log;
import android.graphics.BitmapFactory;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;

import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ImageView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    private static final int REQUEST_CAMERA_PERMISSION = 1;
    private ImageView mImageView;
    private ImageView mQuitButton;
    private RadioButton mCameraTestButton;
    private RadioButton mPictureTestButton;
    private Button mFaceCheckButton;
    private CameraCaptureThread mCameraCaptureThread;
    public static ConcurrentLinkedDeque<byte[]> mQueue = new ConcurrentLinkedDeque<byte[]>();
    public static final int QUEUE_SIZE = 3;
    public static final int CAMERA_TEST = 0;
    public static final int PICTURE_TEST = 1;
    public static int mTestType = -1;
    private boolean mFaceCheckEnabled = true;
    // Used to load the 'facedesensitization' library on application startup.
    static {
        System.loadLibrary("facedesensitization");
    }
    private ActivityMainBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        initView();
        if (false == checkPermissions()) {
            return;
        }
        if (false == OpenCVInstance.getInstance().initInstance(this)) {
            return;
        }
        mCameraCaptureThread = new CameraCaptureThread(this);
        if (true == mCameraCaptureThread.init()) {
            mCameraCaptureThread.start();
        }
    }
    private void initView() {
        mImageView = findViewById(R.id.image);
        mQuitButton = findViewById(R.id.quit_btn);
        mQuitButton.setOnClickListener(new View.OnClickListener() {
            @Override
            // jump to ID4 Demo Car MainActivity
            public void onClick(View view) {
                Log.d(TAG, "main activity exit!");
                finish();
                System.exit(0);
            }
        });
        mCameraTestButton = findViewById(R.id.camera_test);
        mPictureTestButton = findViewById(R.id.picture_test);
        mFaceCheckButton = findViewById(R.id.face_btn);
        mCameraTestButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (PICTURE_TEST == mTestType) {
                    mCameraTestButton.setChecked(false);
                    Toast.makeText(getApplicationContext(), "正在进行图片测试", Toast.LENGTH_LONG).show();
                    return;
                }
                mTestType = CAMERA_TEST;
                mPictureTestButton.setChecked(false);
            }
        });
        mPictureTestButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mTestType = PICTURE_TEST;
                mCameraTestButton.setChecked(false);
                testPictureFace();
            }
        });
        mFaceCheckButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (true == mFaceCheckEnabled) {
                    mFaceCheckEnabled = false;
                    mFaceCheckButton.setText("开启人脸检测");
                }
                else {
                    mFaceCheckEnabled = true;
                    mFaceCheckButton.setText("关闭人脸检测");
                }
            }
        });
    }
    private void testPictureFace() {
        final int []faces = {
            R.drawable.face_detect_0,
                    R.drawable.face_detect_1, R.drawable.face_detect_2, R.drawable.face_detect_3,
                    R.drawable.face_detect_4, R.drawable.face_detect_5, R.drawable.face_detect_6,
                    R.drawable.face_detect_7, R.drawable.face_detect_8, R.drawable.face_detect_9
        };
        new Thread() {
            @Override
            public void run() {
                for (int i = 0;i < faces.length;i++) {
                    Bitmap bitmap = BitmapFactory.decodeResource(getResources(), faces[i]);
                    if (null == bitmap) {
                        Log.e(TAG, "get null bitmap!");
                        continue;
                    }
                    synchronized (MainActivity.class) {
                        long curTimeMillis = System.currentTimeMillis();
                        Bitmap newbitmap = OpenCVInstance.getInstance().getFaceRectangle(bitmap, mFaceCheckEnabled);
                        long endTimeMillis = System.currentTimeMillis();
                        Log.d(TAG, "face detect elapse ms:" + (endTimeMillis - curTimeMillis));
                        // must start a UI thread to display
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                if (PICTURE_TEST == mTestType) {
                                    mImageView.setImageBitmap(newbitmap);
                                }
                            }
                        });
                    }
                    try {
                            Thread.sleep(1000);
                        } catch (Exception e) {
                            e.printStackTrace();
                    }
                }
                mTestType = -1;
            }
        }.start();
    }
    @RequiresApi(api = Build.VERSION_CODES.M)
    private void requestCameraPermission() {
        requestPermissions(new String[]{ Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE }, REQUEST_CAMERA_PERMISSION);
    }
    // 权限请求结果处理 权限通过 打开相机
    @RequiresApi(api = Build.VERSION_CODES.M)
    public boolean checkPermissions() {
        if (ContextCompat.checkSelfPermission(MainActivity.this, android.Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        != PackageManager.PERMISSION_GRANTED) {
            requestCameraPermission();
            return false;
        }
        return true;
    }
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults.length != 1 || grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "请授予相机权限！", Toast.LENGTH_SHORT).show();
            } else {
            }
        } else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }
    @Override
    protected void onStart() {
        super.onResume();
        new Thread() {
            @Override
            public void run() {
                while (true) {
                    byte[] data = mQueue.poll();
                    if (data != null) {
                        Bitmap bitmap = YuvUtil.spToBitmap(data, CameraCaptureThread.CAMERA_WIDTH, CameraCaptureThread.CAMERA_HEIGHT, 0, 1);    // NV12
                        synchronized (MainActivity.class) {
                            long curTimeMillis = System.currentTimeMillis();
                            Bitmap newbitmap = OpenCVInstance.getInstance().getFaceRectangle(bitmap, mFaceCheckEnabled);
                            long endTimeMillis = System.currentTimeMillis();
                            Log.d(TAG, "face detect from camera elapse ms:" + (endTimeMillis - curTimeMillis));
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    if (CAMERA_TEST == mTestType) {
                                        mImageView.setImageBitmap(newbitmap);  // very slow
                                    }
                                }
                            });
                        }
                    }
                    else {
                        try {
                            Thread.sleep(1000);
                        }
                        catch (Exception e) {
                            Log.e(TAG, e.toString());
                            e.printStackTrace();
                        }
                    }
                }
            }
        }.start();
    }
    /**
     * A native method that is implemented by the 'facedesensitization' native library,
     * which is packaged with this application.
     */
    public native String stringFromJNI();
}