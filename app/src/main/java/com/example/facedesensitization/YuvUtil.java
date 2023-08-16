package com.example.facedesensitization;

import android.graphics.ImageFormat;
import android.media.Image;
import android.graphics.Bitmap;
import java.nio.ByteBuffer;

public class YuvUtil {
    public static final int NoneType = -1;
    public static final int YUV420P = 0;
    public static final int YUV420SP = 1;
    public static final int NV21 = 2;
    public static final int NV12 = 3;

    public static byte[] getBytesFromImageAsType(Image image, int type, boolean black_white) {
        if (null == image) {
            return null;
        }
        // 获取源数据，如果是YUV格式的数据planes.length = 3
        // plane[i]里面的实际数据可能存在byte[].length <= capacity (缓冲区总大小)
        final Image.Plane[] planes = image.getPlanes();
        if (planes.length != 3) {
            return null;
        }
        // 数据有效宽度，一般的，图片width <= rowStride，这也是导致byte[].length <= capacity的原因
        // 所以只取width部分
        int width = image.getWidth();
        int height = image.getHeight();
        //此处用来装填最终的YUV数据，需要1.5倍的图片大小，因为Y U V 比例为 4:1:1
        byte[] yuvBytes = new byte[width * height * ImageFormat.getBitsPerPixel(ImageFormat.YUV_420_888) / 8];
        // 临时存储uv数据的
        byte[] uBytes = new byte[width * height / 4];
        byte[] vBytes = new byte[width * height / 4];
        if ((null == yuvBytes) || (null == uBytes) || (null == vBytes)) {
            return null;
        }
        // 源数组的装填到的位置
        int srcIndex = 0;
        // 目标数组的装填到的位置
        int dstIndex = 0;
        int uIndex = 0;
        int vIndex = 0;
        int pixelsStride = 0, rowStride = 0;
        for (int planeNo = 0;planeNo < planes.length;planeNo++) {
            pixelsStride = planes[planeNo].getPixelStride();
            rowStride = planes[planeNo].getRowStride();
            ByteBuffer buffer = planes[planeNo].getBuffer();
            byte[] bytes = new byte[buffer.capacity()];
            if (null == bytes) {
                return null;
            }
            buffer.get(bytes);
            srcIndex = 0;
            switch (planeNo) {
                case 0:
                    for (int i = 0; i < height; i++) {
                        System.arraycopy(bytes, srcIndex, yuvBytes, dstIndex, width);
                        srcIndex += rowStride;
                        dstIndex += width;
                    }
                    break;
                case 1:
                    for (int i = 0; i < height / 2; i++) {
                        for (int j = 0; j < width / 2; j++) {
                            uBytes[uIndex++] = bytes[srcIndex];
                            srcIndex += pixelsStride;
                        }
                        if (2 == pixelsStride) {
                            srcIndex += rowStride - width;
                        } else if (1 == pixelsStride) {
                            srcIndex += rowStride - width / 2;
                        }
                        else {
                            return null;
                        }
                    }
                    break;
                case 2:
                    for (int i = 0; i < height / 2; i++) {
                        for (int j = 0; j < width / 2; j++) {
                            vBytes[vIndex++] = bytes[srcIndex];
                            srcIndex += pixelsStride;
                        }
                        if (2 == pixelsStride) {
                            srcIndex += rowStride - width;
                        } else if (1 == pixelsStride) {
                            srcIndex += rowStride - width / 2;
                        }
                        else {
                            return null;
                        }
                    }
                    break;
            }
        }
        final byte char_128 = (byte) 128;
        switch (type) {
            case YUV420P:
                System.arraycopy(uBytes, 0, yuvBytes, dstIndex, uBytes.length);
                System.arraycopy(vBytes, 0, yuvBytes, dstIndex + uBytes.length, vBytes.length);
                break;
            case YUV420SP:
                if (true == black_white) {
                    for (int i = 0; i < vBytes.length; i++) {
                        yuvBytes[dstIndex++] = char_128;
                        yuvBytes[dstIndex++] = char_128;
                    }
                }
                else {
                    for (int i = 0; i < vBytes.length; i++) {
                        yuvBytes[dstIndex++] = uBytes[i];
                        yuvBytes[dstIndex++] = vBytes[i];
                    }
                }
                break;
            case NV21:
                if (true == black_white) {
                    for (int i = 0; i < vBytes.length; i++) {
                        yuvBytes[dstIndex++] = char_128;
                        yuvBytes[dstIndex++] = char_128;
                    }
                }
                else {
                    for (int i = 0; i < vBytes.length; i++) {
                        yuvBytes[dstIndex++] = vBytes[i];
                        yuvBytes[dstIndex++] = uBytes[i];
                    }
                }
                break;
            default:
                return null;
        }
        return yuvBytes;
    }
    public static void YUV420ToNV12(byte[] yuv420y, byte[] yuv420u, byte[] nv12) {
        System.arraycopy(yuv420y,0, nv12, 0, yuv420y.length);
        int offset = yuv420y.length;
        int bytes = yuv420u.length;   // uv分量字节数
        for(int i = 0;i < bytes;i++) {
            nv12[offset++] = yuv420u[i];
        }
    }
    public static Bitmap spToBitmap(byte[] data, int w, int h, int uOff, int vOff) {
        int plane = w * h;
        int[] colors = new int[plane];
        int yPos = 0, uvPos = plane;
        for(int j = 0; j < h; j++) {
            for(int i = 0; i < w; i++) {
                // YUV byte to RGB int
                final int y1 = data[yPos] & 0xff;
                final int u = (data[uvPos + uOff] & 0xff) - 128;
                final int v = (data[uvPos + vOff] & 0xff) - 128;
                final int y1192 = 1192 * y1;
                int r = (y1192 + 1634 * v);
                int g = (y1192 - 833 * v - 400 * u);
                int b = (y1192 + 2066 * u);

                r = (r < 0) ? 0 : ((r > 262143) ? 262143 : r);
                g = (g < 0) ? 0 : ((g > 262143) ? 262143 : g);
                b = (b < 0) ? 0 : ((b > 262143) ? 262143 : b);
                colors[yPos] = ((r << 6) & 0xff0000) |
                        ((g >> 2) & 0xff00) |
                        ((b >> 10) & 0xff);

                if((yPos++ & 1) == 1) uvPos += 2;
            }
            if((j & 1) == 0) uvPos -= w;
        }
        return Bitmap.createBitmap(colors, w, h, Bitmap.Config.RGB_565);
    }
    private static Bitmap pToBitmap(byte[] data, int w, int h, boolean uv) {
        int plane = w * h;
        int[] colors = new int[plane];
        int off = plane >> 2;
        int yPos = 0, uPos = plane + (uv ? 0 : off), vPos = plane + (uv ? off : 0);
        for(int j = 0; j < h; j++) {
            for(int i = 0; i < w; i++) {
                // YUV byte to RGB int
                final int y1 = data[yPos] & 0xff;
                final int u = (data[uPos] & 0xff) - 128;
                final int v = (data[vPos] & 0xff) - 128;
                final int y1192 = 1192 * y1;
                int r = (y1192 + 1634 * v);
                int g = (y1192 - 833 * v - 400 * u);
                int b = (y1192 + 2066 * u);

                r = (r < 0) ? 0 : ((r > 262143) ? 262143 : r);
                g = (g < 0) ? 0 : ((g > 262143) ? 262143 : g);
                b = (b < 0) ? 0 : ((b > 262143) ? 262143 : b);
                colors[yPos] = ((r << 6) & 0xff0000) |
                        ((g >> 2) & 0xff00) |
                        ((b >> 10) & 0xff);

                if((yPos++ & 1) == 1) {
                    uPos++;
                    vPos++;
                }
            }
            if((j & 1) == 0) {
                uPos -= (w >> 1);
                vPos -= (w >> 1);
            }
        }
        return Bitmap.createBitmap(colors, w, h, Bitmap.Config.RGB_565);
    }
    // NV21或NV12顺时针旋转90度
    public static void rotateSP90(byte[] src, byte[] dest, int w, int h) {
        int pos = 0;
        int k = 0;
        for (int i = 0; i <= w - 1; i++) {
            for (int j = h - 1; j >= 0; j--) {
                dest[k++] = src[j * w + i];
            }
        }

        pos = w * h;
        for (int i = 0; i <= w - 2; i += 2) {
            for (int j = h / 2 - 1; j >= 0; j--) {
                dest[k++] = src[pos + j * w + i];
                dest[k++] = src[pos + j * w + i + 1];
            }
        }
    }

    // NV21或NV12顺时针旋转270度
    public static void rotateSP270(byte[] src, byte[] dest, int w, int h) {
        int pos = 0;
        int k = 0;
        for (int i = w - 1; i >= 0; i--) {
            for (int j = 0; j <= h - 1; j++) {
                dest[k++] = src[j * w + i];
            }
        }

        pos = w * h;
        for (int i = w - 2; i >= 0; i -= 2) {
            for (int j = 0; j <= h / 2 - 1; j++) {
                dest[k++] = src[pos + j * w + i];
                dest[k++] = src[pos + j * w + i + 1];
            }
        }
    }

    // NV21或NV12顺时针旋转180度
    public static void rotateSP180(byte[] src, byte[] dest, int w, int h) {
        int pos = 0;
        int k = w * h - 1;
        while (k >= 0) {
            dest[pos++] = src[k--];
        }

        k = src.length - 2;
        while (pos < dest.length) {
            dest[pos++] = src[k];
            dest[pos++] = src[k + 1];
            k -= 2;
        }
    }

    // I420或YV12顺时针旋转90度
    public static void rotateP90(byte[] src, byte[] dest, int w, int h) {
        int pos = 0;
        //旋转Y
        int k = 0;
        for (int i = 0; i < w; i++) {
            for (int j = h - 1; j >= 0; j--) {
                dest[k++] = src[j * w + i];
            }
        }
        //旋转U
        pos = w * h;
        for (int i = 0; i < w / 2; i++) {
            for (int j = h / 2 - 1; j >= 0; j--) {
                dest[k++] = src[pos + j * w / 2 + i];
            }
        }

        //旋转V
        pos = w * h * 5 / 4;
        for (int i = 0; i < w / 2; i++) {
            for (int j = h / 2 - 1; j >= 0; j--) {
                dest[k++] = src[pos + j * w / 2 + i];
            }
        }
    }

    // I420或YV12顺时针旋转270度
    public static void rotateP270(byte[] src, byte[] dest, int w, int h) {
        int pos = 0;
        //旋转Y
        int k = 0;
        for (int i = w - 1; i >= 0; i--) {
            for (int j = 0; j < h; j++) {
                dest[k++] = src[j * w + i];
            }
        }
        //旋转U
        pos = w * h;
        for (int i = w / 2 - 1; i >= 0; i--) {
            for (int j = 0; j < h / 2; j++) {
                dest[k++] = src[pos + j * w / 2 + i];
            }
        }

        //旋转V
        pos = w * h * 5 / 4;
        for (int i = w / 2 - 1; i >= 0; i--) {
            for (int j = 0; j < h / 2; j++) {
                dest[k++] = src[pos + j * w / 2 + i];
            }
        }
    }

    // I420或YV12顺时针旋转180度
    public static void rotateP180(byte[] src, byte[] dest, int w, int h) {
        int pos = 0;
        int k = w * h - 1;
        while (k >= 0) {
            dest[pos++] = src[k--];
        }

        k = w * h * 5 / 4;
        while (k >= w * h) {
            dest[pos++] = src[k--];
        }

        k = src.length - 1;
        while (pos < dest.length) {
            dest[pos++] = src[k--];
        }
    }
}