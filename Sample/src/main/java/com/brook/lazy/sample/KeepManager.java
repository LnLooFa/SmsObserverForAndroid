package com.brook.lazy.sample;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

import java.lang.ref.WeakReference;

public class KeepManager {
    private static final KeepManager mInstance = new KeepManager();

    private KeepReceiver mKeepReceiver;

    private WeakReference<Activity> mKeepActivity;

    private KeepManager() {}

    public static KeepManager getInstance() {
        return mInstance;
    }

    public void registerKeep(Context context) {
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_SCREEN_OFF);

        mKeepReceiver = new KeepReceiver();
        context.registerReceiver(mKeepReceiver, filter);
    }

    public void unRegisterKeep(Context context){
        if (mKeepReceiver != null) {
            context.unregisterReceiver(mKeepReceiver);
        }
    }

    public void startKeep(Context context) {
        Intent intent = new Intent(context, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }

    public void finishKeep() {
        if (mKeepActivity != null) {
            Activity activity = mKeepActivity.get();
            if (activity != null) {
                activity.finish();
            }
            mKeepActivity = null;
        }
    }

    public void setKeepActivity(Activity activity) {
        this.mKeepActivity = new WeakReference<>(activity);
    }

}
