package com.brook.lazy.sms;

import android.app.Activity;
import android.content.Context;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;

/***
 * 短信接收观察者
 *
 * @author 江钰锋 0152
 * @version [版本号, 2015年9月17日]
 * @see [相关类/方法]
 * @since [产品/模块版本]
 */
public class SmsObserver extends ContentObserver {

    private Context mContext;
    public static final int MSG_RECEIVED_CODE = 1001;
    private SmsHandler mHandler;

    /***
     * 构造器
     * @param context
     * @param callback 短信接收器
     * @param smsFilter 短信过滤器
     */
    public SmsObserver(Activity context, SmsResponseCallback callback,SmsFilter smsFilter) {
        this(new SmsHandler(callback,smsFilter));
        this.mContext = context;
    }

    public SmsObserver(Activity context, SmsResponseCallback callback) {
        this(new SmsHandler(callback));
        this.mContext = context;
    }

    public SmsObserver(SmsHandler handler) {
        super(handler);
        this.mHandler = handler;
    }

    /***
     * 设置短信过滤器
     * @param smsFilter
     */
    public void setSmsFilter(SmsFilter smsFilter) {
        mHandler.setSmsFilter(smsFilter);
    }

    /***
     * 注册短信变化观察者
     *
     * @see [类、类#方法、类#成员]
     */
    public void registerSMSObserver() {
        Uri uri = Uri.parse("content://sms");
        if (mContext != null) {
            mContext.getContentResolver().registerContentObserver(uri,
                    true, this);
        }
    }

    /***
     * 注销短信变化观察者
     *
     * @see [类、类#方法、类#成员]
     */
    public void unregisterSMSObserver() {
        if (mContext != null) {
            mContext.getContentResolver().unregisterContentObserver(this);
        }
        if (mHandler != null) {
            mHandler = null;
        }
    }

    String currentId;
    @Override
    public void onChange(boolean selfChange, Uri uri) {
        super.onChange(selfChange, uri);
        Log.i(getClass().getName(), "短信的uri地址=======" + uri);
        if (uri.toString().equals("content://sms/raw")) {
            return;
        }
        Uri inboxUri = Uri.parse("content://sms/inbox");//收件箱
//        String[] projection = new String[] {"service_center","date","address","body","date_sent"};// "短信中心号码", "发送时间", "发送方号码", "短信内容"
//        String where = "(type=1) and date>" + lastTime;
        String order = new String("date desc");

//        String[] projection = new String[]{"DISTINCT address, body"};
//        String selection = new String("address IS NOT NULL) "); //this doesn't work
        try {
            Cursor c = mContext.getContentResolver().query(inboxUri, null, null,
                    null, order);

            if (c != null) {
                if(c.moveToFirst()){
                    String id = c.getString(c.getColumnIndex("_id"));
                    String address = c.getString(c.getColumnIndex("address"));
                    String body = c.getString(c.getColumnIndex("body"));
                    String date = c.getString(c.getColumnIndex("date"));
                    String person = c.getString(c.getColumnIndex("person"));
                    if(TextUtils.equals(currentId,id)){
                        c.close();
                        return;
                    }
                    currentId=id;
                    if (mHandler != null) {
                        mHandler.obtainMessage(MSG_RECEIVED_CODE, new String[]{id, address, body,date, TextUtils.isEmpty(person)?"未知":person})
                                .sendToTarget();
                    }
                    Log.i(getClass().getName(), "发件人为：" + address + " " + "短信内容为：" + body+"  _id: "+id);
                }
                c.close();
//                while (c.moveToNext()) {
//
//                }

            }
        } catch (SecurityException e) {
            Log.e(getClass().getName(), "获取短信权限失败", e);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
