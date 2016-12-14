package com.example.oscilloscope;  
import java.io.IOException;
import java.util.UUID;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Paint;
import android.media.AudioFormat;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.SurfaceView;
import android.view.View;
import android.view.View.OnTouchListener;
import android.widget.ZoomControls;  
public class Oscilloscope extends Activity {  
    /** Called when the activity is first created. */  
    SurfaceView sfv;  
    ZoomControls zctlX,zctlY;  
  
    ClsOscilloscope clsOscilloscope=new ClsOscilloscope();  
      
    static final int frequency = 8000;//分辨率  
    static final int channelConfiguration = AudioFormat.CHANNEL_CONFIGURATION_MONO;  
    static final int audioEncoding = AudioFormat.ENCODING_PCM_16BIT;  
    static final int xMax = 16;//X轴缩小比例最大值,X轴数据量巨大，容易产生刷新延时  
    static final int xMin = 8;//X轴缩小比例最小值  
    static final int yMax = 10;//Y轴缩小比例最大值  
    static final int yMin = 1;//Y轴缩小比例最小值  
 
    public static final String PROTOCOL_SCHEME_RFCOMM = "btspp";
    private BluetoothAdapter mBluetoothAdapter;  // Bluetooth适配器
    private BluetoothServerSocket mServerSocket; // 服务端socket
    private BluetoothSocket socket;              // socket    
    private ServerThread mServerThread;          // 服务端线程
	
    Paint mPaint;  
    @Override  
    public void onCreate(Bundle savedInstanceState) {  
        super.onCreate(savedInstanceState);  
        setContentView(R.layout.activity_oscilloscope);  
        //画板和画笔  
        sfv = (SurfaceView) this.findViewById(R.id.SurfaceView01);   
        sfv.setOnTouchListener(new TouchEvent());  
        mPaint = new Paint();    
        mPaint.setColor(Color.GREEN);// 画笔为绿色    
        mPaint.setStrokeWidth(1);// 设置画笔粗细   
        
		mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        //示波器类库  
        clsOscilloscope.initOscilloscope(xMax/2, yMax/2, sfv.getHeight()/2);  
          
        //缩放控件，X轴的数据缩小的比率高些  
        zctlX = (ZoomControls)this.findViewById(R.id.zctlX);  
        zctlX.setOnZoomInClickListener(new View.OnClickListener() {  
            @Override  
            public void onClick(View v) {  
                if(clsOscilloscope.rateX>xMin)  
                    clsOscilloscope.rateX--;  
                setTitle("X轴缩小"+String.valueOf(clsOscilloscope.rateX)+"倍"  
                        +","+"Y轴缩小"+String.valueOf(clsOscilloscope.rateY)+"倍");  
            }  
        });  
        zctlX.setOnZoomOutClickListener(new View.OnClickListener() {  
            @Override  
            public void onClick(View v) {  
                if(clsOscilloscope.rateX<xMax)  
                    clsOscilloscope.rateX++;      
                setTitle("X轴缩小"+String.valueOf(clsOscilloscope.rateX)+"倍"  
                        +","+"Y轴缩小"+String.valueOf(clsOscilloscope.rateY)+"倍");  
            }  
        });  
        zctlY = (ZoomControls)this.findViewById(R.id.zctlY);  
        zctlY.setOnZoomInClickListener(new View.OnClickListener() {  
            @Override  
            public void onClick(View v) {  
                if(clsOscilloscope.rateY>yMin)  
                    clsOscilloscope.rateY--;  
                setTitle("X轴缩小"+String.valueOf(clsOscilloscope.rateX)+"倍"  
                        +","+"Y轴缩小"+String.valueOf(clsOscilloscope.rateY)+"倍");  
            }  
        });  
          
        zctlY.setOnZoomOutClickListener(new View.OnClickListener() {  
            @Override  
            public void onClick(View v) {  
                if(clsOscilloscope.rateY<yMax)  
                    clsOscilloscope.rateY++;      
                setTitle("X轴缩小"+String.valueOf(clsOscilloscope.rateX)+"倍"  
                        +","+"Y轴缩小"+String.valueOf(clsOscilloscope.rateY)+"倍");  
            }  
        });  
    }  
 
    @Override  
    protected void onDestroy() {  
        super.onDestroy();  
        android.os.Process.killProcess(android.os.Process.myPid());  
    }  

    /** 
     * 触摸屏动态设置波形图基线 
     */  
    class TouchEvent implements OnTouchListener{  
        @Override  
        public boolean onTouch(View v, MotionEvent event) {  
            clsOscilloscope.baseLine=(int)event.getY();  
            return true;  
        }  
    }  
    
	@Override
	protected void onResume() {
		// TODO Auto-generated method stub
		super.onResume();
		
		// 让蓝牙处于可见状态
		if (mBluetoothAdapter != null) {
			if (!mBluetoothAdapter.isEnabled()) {
				// 发送打开蓝牙的意图
        		Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
        		startActivityForResult(enableIntent, RESULT_FIRST_USER);
        		
        		// 设置蓝牙的可见性，最大值3600秒，默认120秒，0表示永远可见(作为客户端，可见性可以不设置，服务端必须要设置)
        		Intent displayIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
        		displayIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 0);
        		startActivity(displayIntent);
        		
        		// 直接打开
        		mBluetoothAdapter.enable();
			}
		}
		mServerThread = new ServerThread();
		mServerThread.start();
	}
    
    /**
     * 等待蓝牙客户端(Collector)接入线程
     */
    private class ServerThread extends Thread {
        public void run() {
            try {
                /* 创建一个蓝牙服务器
                 * 参数分别：服务器名称、UUID	 */
    			mServerSocket = mBluetoothAdapter.listenUsingRfcommWithServiceRecord(PROTOCOL_SCHEME_RFCOMM,
						UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"));		
                socket = mServerSocket.accept();
                clsOscilloscope.baseLine=sfv.getHeight()/2;  
                clsOscilloscope.Start(sfv, mPaint,socket);
                //启动接受数据
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}  