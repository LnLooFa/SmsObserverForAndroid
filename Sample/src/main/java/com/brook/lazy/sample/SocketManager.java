package com.brook.lazy.sample;

import java.io.IOException;
import java.net.Socket;
import java.util.concurrent.ExecutorService;

public class SocketManager {
    private volatile static SocketManager singleton;  //1:volatile修饰
    private ExecutorService mThreadPool;
    private Socket socket;
    private SocketManager (){}
    public static SocketManager getSingleton() {
        if (singleton == null) {  //2:减少不要同步，优化性能
            synchronized (SocketManager.class) {  // 3：同步，线程安全
                if (singleton == null) {
                    singleton = new SocketManager();  //4：创建singleton 对象
                }
            }
        }
        return singleton;
    }

    public Socket getSocket(String host, int port) throws IOException {
        if(socket == null || socket.isClosed()){
            socket =new Socket(host,port);
        }
        return socket;
    }


    public void clearData() throws IOException {
        if (socket != null && socket.isConnected()) {
            // 最终关闭整个Socket连接
            socket.close();
        }
    }
}
