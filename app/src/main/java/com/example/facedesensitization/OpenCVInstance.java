package com.example.facedesensitization;
import android.content.Context;
import android.graphics.Bitmap;
import android.util.Log;

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

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.List;

public class OpenCVInstance {
    private static final String TAG = "OpenCVInstance";
    private static OpenCVInstance mTools = new OpenCVInstance();
    private CascadeClassifier mClassifier;
    private Context mContext;
    private OpenCVInstance() {
    }
    public static OpenCVInstance getInstance() {
        return mTools;
    }
    public boolean initInstance(Context context) {
        mContext = context;
        return initLoadOpenCV() && initClassifier();
    }
    public Bitmap getFaceRectangle(Bitmap bitmap, boolean enabled) {
        if (false == enabled) {
            return bitmap;
        }
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
    private boolean initLoadOpenCV() {
        boolean success = OpenCVLoader.initDebug();
        if (true == success) {
            Log.d(TAG, "initLoadOpenCV: openCV load success");
            return true;
        } else {
            Log.e(TAG, "initLoadOpenCV: openCV load failed");
            return false;
        }
    }
    private boolean initClassifier() {
        try {
            // 读取存放在raw的文件
            InputStream is = mContext.getResources()
                    .openRawResource(R.raw.lbpcascade_frontalface_improved);
            File cascadeDir = mContext.getDir("cascade", Context.MODE_PRIVATE);
            File cascadeFile = new File(cascadeDir,"lbpcascade_frontalface_improved.xml");
            FileOutputStream os = new FileOutputStream(cascadeFile);
            byte[] buffer = new byte[4096];
            int bytesRead = 0;
            while((bytesRead = is.read(buffer)) != -1) {
                os.write(buffer, 0, bytesRead);
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
}
