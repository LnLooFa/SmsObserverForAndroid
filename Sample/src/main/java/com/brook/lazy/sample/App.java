/**
 * 文 件 名:  App.java
 * 版    权:  Technologies Co., Ltd. Copyright YYYY-YYYY,  All rights reserved
 * 描    述:  <描述>
 * 修 改 人:  江钰锋 00501
 * 修改时间:  16/6/8
 * 跟踪单号:  <跟踪单号>
 * 修改单号:  <修改单号>
 * 修改内容:  <修改内容>
 */

package com.brook.lazy.sample;

import android.app.Application;

import com.brook.lazy.service.TraceServiceImpl;
import com.xdandroid.hellodaemon.DaemonEnv;

/**
 * <一句话功能简述>
 *
 * @author 江钰锋 00501
 * @version [版本号, 16/6/8]
 * @see [相关类/方法]
 * @since [产品/模块版本]
 */
public class App extends Application{
    @Override
    public void onCreate() {
        super.onCreate();
        //需要在 Application 的 onCreate() 中调用一次 DaemonEnv.initialize()
        DaemonEnv.initialize(this, TraceServiceImpl.class, DaemonEnv.DEFAULT_WAKE_UP_INTERVAL);
        TraceServiceImpl.sShouldStopService = false;
        DaemonEnv.startServiceMayBind(TraceServiceImpl.class);
    }
}
