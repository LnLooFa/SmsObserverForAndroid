package com.brook.lazy.sample;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.text.TextUtils;
import android.util.Log;

public class KeepReceiver extends BroadcastReceiver {
    private static final String TAG = "KeepReceiver";
    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        Log.e(TAG, "onReceive:" + action);
        if (TextUtils.equals(action,Intent.ACTION_SCREEN_ON)) {
            KeepManager.getInstance().finishKeep();
        } else if (TextUtils.equals(action,Intent.ACTION_SCREEN_ON)){
            KeepManager.getInstance().startKeep(context);
        }
    }
}
