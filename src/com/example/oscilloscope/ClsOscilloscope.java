package com.example.oscilloscope;  
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;

import android.bluetooth.BluetoothSocket;
import android.graphics.Canvas;  
import android.graphics.Color;  
import android.graphics.Paint;  
import android.graphics.Rect;
import android.util.Log;
import android.view.SurfaceView;  
public class ClsOscilloscope {  
    private ArrayList<short[]> inBuf = new ArrayList<short[]>();  
    private boolean isRecording = false;// 线程控制标记  
    /** 
     * X轴缩小的比例 
     */  
    public int rateX = 4;  
    /** 
     * Y轴缩小的比例 
     */  
    public int rateY = 4;  
    /** 
     * Y轴基线 
     */  
    public int baseLine = 0;  
    /** 
     * 初始化 
     */  
    public void initOscilloscope(int rateX, int rateY, int baseLine) {  
        this.rateX = rateX;  
        this.rateY = rateY;  
        this.baseLine = baseLine;  
    }  
    /** 
     * 开始 
     *  
     * @param recBufSize 
     *            AudioRecord的MinBufferSize 
     */  
    public void Start(SurfaceView sfv, Paint mPaint, BluetoothSocket socket) {  
        isRecording = true;  
        new ReadThread(socket).start();// 开始读取线程  
        new DrawThread(sfv, mPaint).start();// 开始绘制线程  
    }  

    /** 
     * 负责从蓝牙接受数据
     */  
    class ReadThread extends Thread {  
    	   short[] shortBuf;
    	   byte[] byteBuf;
           InputStream inputStream;
           public ReadThread(BluetoothSocket socket) {
               try {
                   inputStream = socket.getInputStream();
                   byteBuf = new byte[52]; // 26*2=52
                   shortBuf = new short[26]; // 每次采集的short[]的长度为26，详情见Audio的相关API
               } catch (Exception exp) {
               }

           }
           @Override
           public void run() {
               try {
                   while (true) {
                       inputStream.read(byteBuf);
                       Log.v("ReadThread", "从socket中读取的byteBuf的长度为:" + byteBuf.length);
                       Log.v("ReadThread", "从socket中读取的byteBuf中的数据为:" + Arrays.toString(byteBuf));
                       shortBuf = toShortArray(byteBuf);
                       synchronized (inBuf) {
                    	   inBuf.add(shortBuf);
                       }

                   }
               } catch (Exception exp) {
               }

           }
    };  
 
    /** 
     * 负责绘制inBuf中的数据 
     */  
    class DrawThread extends Thread {  
        private int oldX = 0;// 上次绘制的X坐标  
        private int oldY = 0;// 上次绘制的Y坐标  
        private SurfaceView sfv;// 画板  
        private int X_index = 0;// 当前画图所在屏幕X轴的坐标  
        private Paint mPaint;// 画笔  
        public DrawThread(SurfaceView sfv, Paint mPaint) {  
            this.sfv = sfv;  
            this.mPaint = mPaint;  
        }  
        public void run() {  
            while (isRecording) {  
                ArrayList<short[]> buf = new ArrayList<short[]>();  
                synchronized (inBuf) {  
                    if (inBuf.size() == 0)  
                        continue;  
                    buf = (ArrayList<short[]>) inBuf.clone();// 保存  
                    inBuf.clear();// 清除  
                }  
                for (int i = 0; i < buf.size(); i++) {  
                    short[] tmpBuf = buf.get(i);  
                    SimpleDraw(X_index, tmpBuf, rateY, baseLine);// 把缓冲区数据画出来  
                    X_index = X_index + tmpBuf.length;  
                    if (X_index > sfv.getWidth()) {  
                        X_index = 0;  
                    }  
                }  
            }  
        }  
        /** 
         * 绘制指定区域 
         *  
         * @param start 
         *            X轴开始的位置(全屏) 
         * @param buffer 
         *            缓冲区 
         * @param rate 
         *            Y轴数据缩小的比例 
         * @param baseLine 
         *            Y轴基线 
         */  
        void SimpleDraw(int start, short[] buffer, int rate, int baseLine) {  
            if (start == 0)  
                oldX = 0;  
            Canvas canvas = sfv.getHolder().lockCanvas(  
                    new Rect(start, 0, start + buffer.length, sfv.getHeight()));// 关键:获取画布  
            canvas.drawColor(Color.BLACK);// 清除背景  
            int y;  
            for (int i = 0; i < buffer.length; i++) {// 有多少画多少  
                int x = i + start;  
                y = buffer[i] / rate + baseLine;// 调节缩小比例，调节基准线  
                canvas.drawLine(oldX, oldY, x, y, mPaint);  
                oldX = x;  
                oldY = y;  
            }  
            sfv.getHolder().unlockCanvasAndPost(canvas);// 解锁画布，提交画好的图像  
        }  
    }  
    
    /**
     * byte数组转换为short数组
     */
    public static short[] toShortArray(byte[] src) {

        int count = src.length / 2;
        short[] dest = new short[count];
        for (int i = 0; i < count; i++) {
            dest[i] = (short) (src[i * 2] << 8 | src[2 * i + 1] & 0xff);
        }
        return dest;
    }
}  