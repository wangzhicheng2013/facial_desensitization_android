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

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewFrame;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.*;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewListener2;
import org.opencv.imgproc.Imgproc;
import org.opencv.objdetect.CascadeClassifier;
import org.opencv.android.Utils;
import org.opencv.core.Point;
//import com.tbruyelle.rxpermissions.RxPermissions;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import android.util.Log;

import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;

import android.widget.TextView;
import android.widget.Toast;
import android.widget.ImageView;
public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    private static final int REQUEST_CAMERA_PERMISSION = 1;
    private ImageView mImageView;
    private ImageView mQuitButton;
    private CameraCaptureThread mCameraCaptureThread;
    public static ConcurrentLinkedDeque<byte[]> mQueue = new ConcurrentLinkedDeque<byte[]>();
    public static final int QUEUE_SIZE = 3;
    private CascadeClassifier mClassifier;
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
        if ((false == initLoadOpenCV()) || (false == initClassifier())) {
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
    }
    public boolean initLoadOpenCV() {
        boolean success = OpenCVLoader.initDebug();
        if (true == success) {
            Log.d(TAG, "initLoadOpenCV: openCV load success");
            return true;
        } else {
            Log.e(TAG, "initLoadOpenCV: openCV load failed");
            return false;
        }
    }
    public boolean initClassifier() {
        try {
            // 读取存放在raw的文件
            InputStream is = getResources()
                    .openRawResource(R.raw.lbpcascade_frontalface_improved);
            File cascadeDir = getDir("cascade", Context.MODE_PRIVATE);
            File cascadeFile = new File(cascadeDir,"lbpcascade_frontalface_improved.xml");
            FileOutputStream os = new FileOutputStream(cascadeFile);
            byte[] buffer = new byte[4096];
            int bytesRead;
            while((bytesRead = is.read(buffer))!=-1){
                os.write(buffer,0,bytesRead);
            }
            is.close();
            os.close();
            // 通过classifier来操作人脸检测， 在外部定义一个CascadeClassifier classifier，做全局变量使用
            mClassifier = new CascadeClassifier(cascadeFile.getAbsolutePath());
            cascadeFile.delete();
            cascadeDir.delete();
            return true;
        } catch (Exception e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
        }
        return false;
    }
    public Bitmap getFaceRectangle(Bitmap bitmap) {
        Mat mat = new Mat();
        Mat matdst = new Mat();
        Utils.bitmapToMat(bitmap, mat);
        // 把当前数据复制一份给matdst
        mat.copyTo(matdst);
        // 1.把图片转为灰度图 BGR2GRAY，注意是BGR
        Imgproc.cvtColor(mat, mat, Imgproc.COLOR_BGR2GRAY);
        // 2.定义MatOfRect用于接收人脸位置
        MatOfRect faces = new MatOfRect();
        // 3.开始人脸检测，把检测到的人脸数据存放在faces中
        mClassifier.detectMultiScale(mat, faces, 1.05, 3, 0, new Size(30, 30), new Size());
        List<Rect> faceList = faces.toList();
        Log.d(TAG, "face num:" + faceList.size());
        // 4.判断是否存在人脸
        if (faceList.size() > 0) {
            for (Rect rect : faceList) {
                // 5.根据得到的人脸位置绘制矩形框
                // rect.tl() 左上角
                // rect.br() 右下角
                Imgproc.rectangle(matdst, rect.tl(), rect.br(), new Scalar(255, 0, 0,255), 4);
            }
        }
        Bitmap resultBitmap = Bitmap.createBitmap(matdst.width(), matdst.height(), Bitmap.Config.ARGB_8888);
        Utils.matToBitmap(matdst, resultBitmap);
        mat.release();
        matdst.release();
        return resultBitmap;
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
    protected void onResume() {
        super.onResume();
        new Thread() {
            @Override
            public void run() {
                while (true) {
                    byte[] data = mQueue.poll();
                    if (data != null) {
                        Bitmap bitmap = YuvUtil.spToBitmap(data, 1920, 1080, 0, 1);
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                mImageView.setImageBitmap(getFaceRectangle(bitmap));
                            }
                        });
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