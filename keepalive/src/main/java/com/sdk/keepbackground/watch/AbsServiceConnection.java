package com.sdk.keepbackground.watch;

import android.content.ComponentName;
import android.content.ServiceConnection;
import android.os.IBinder;
public abstract class AbsServiceConnection implements ServiceConnection {

    // 当前绑定的状态
    public boolean mConnectedState = false;

    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
        mConnectedState = true;
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        if (mConnectedState) {
            mConnectedState = false;
            onDisconnected(name);
        }
    }

    @Override
    public void onBindingDied(ComponentName name) {
        onServiceDisconnected(name);
    }

    public abstract void onDisconnected(ComponentName name);
}
