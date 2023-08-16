package com.example.facedesensitization;

import android.util.Log;

import java.io.FileOutputStream;
import java.io.IOException;

public class PublicTools {
    private static final String TAG = "PublicTools";
    public static boolean dumpImage(String imagePath, byte[] data) {
        FileOutputStream output = null;
        boolean success = false;
        try {
            output = new FileOutputStream(imagePath, true);
            output.write(data);
            Log.i(TAG, "file:" + imagePath + " write success!");
            success = true;
        } catch (IOException e) {
            Log.e(TAG, "write file error:" + e.toString());
        }
        finally {
            try {
                output.close();
            } catch (IOException e) {
                Log.e(TAG, "file close error:" + e.toString());
            }
        }
        return success;
    }
}
