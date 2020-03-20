/****************************************************************************
 Copyright (c) 2015-2016 Chukong Technologies Inc.
 Copyright (c) 2017-2018 Xiamen Yaji Software Co., Ltd.

 http://www.cocos2d-x.org

 Permission is hereby granted, free of charge, to any person obtaining a copy
 of this software and associated documentation files (the "Software"), to deal
 in the Software without restriction, including without limitation the rights
 to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 copies of the Software, and to permit persons to whom the Software is
 furnished to do so, subject to the following conditions:

 The above copyright notice and this permission notice shall be included in
 all copies or substantial portions of the Software.

 THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 THE SOFTWARE.
 ****************************************************************************/
package org.cocos2dx.javascript;

import org.cocos2dx.lib.Cocos2dxActivity;
import org.cocos2dx.lib.Cocos2dxGLSurfaceView;
import org.cocos2dx.lib.Cocos2dxJavascriptJavaBridge;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.Bundle;

import android.content.Intent;
import android.content.res.Configuration;
import android.os.Handler;
import android.os.Message;
import android.support.v4.content.ContextCompat;
import android.text.TextUtils;
import android.util.Base64;
import android.util.Log;
import android.view.Window;
import android.view.WindowManager;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import com.alipay.sdk.app.PayTask;
import com.huawei.agconnect.config.AGConnectServicesConfig;
import com.huawei.hms.aaid.HmsInstanceId;
import com.tencent.mm.opensdk.modelmsg.SendMessageToWX;
import com.tencent.mm.opensdk.modelmsg.WXImageObject;
import com.tencent.mm.opensdk.modelmsg.WXMediaMessage;
import com.tencent.mm.opensdk.openapi.IWXAPI;
import com.tencent.mm.opensdk.openapi.WXAPIFactory;
import com.tencent.mm.opensdk.modelpay.PayReq;
import com.xiaomi.channel.commonutils.logger.LoggerInterface;
import com.xiaomi.mipush.sdk.Logger;
import com.xiaomi.mipush.sdk.MiPushClient;

import static android.net.ConnectivityManager.TYPE_MOBILE;
import static android.net.ConnectivityManager.TYPE_WIFI;


public class AppActivity extends Cocos2dxActivity {
    public static IWXAPI api;//微信API接口
    public static final String SYS_EMUI = "emui";
    public static final String SYS_MIUI = "miui";
    public static final String SYS_MEIZU = "meizu";
    public static final String SYS_OTHER = "other";
    public static AppActivity app;
    public static String PUSH_ACTION = "push_tag";
    private String MiPushId;
    private String MiPushKey;
    private MyReceiver receiver = null;
    private static MiPushHandler miPushHandler = null;
    private static AudioRecordDemo mAudioRecordDemo;
    private IntentFilter intentFilter;
    private NetworkChangeReceiver networkChangeReceiver;
    @SuppressLint("HandlerLeak")
    private static Handler mHandler = new Handler() {
        @SuppressWarnings("unused")
        public void handleMessage(Message msg) {
            app.chargeFinished();
            PayResult payResult = new PayResult((Map<String, String>) msg.obj);
            /**
             * 对于支付结果，请商户依赖服务端的异步通知结果。同步通知结果，仅作为支付结束的通知。
             */
            String resultInfo = payResult.getResult();// 同步返回需要验证的信息
            String resultStatus = payResult.getResultStatus();
            // 判断resultStatus 为9000则代表支付成功
            if (TextUtils.equals(resultStatus, "9000")) {
                app.chargeSuccess();
                // 该笔订单是否真实支付成功，需要依赖服务端的异步通知。
                //showAlert(PayDemoActivity.this, getString(R.string.pay_success) + payResult);
            } else {
                // 该笔订单真实的支付结果，需要依赖服务端的异步通知。
                //showAlert(PayDemoActivity.this, getString(R.string.pay_failed) + payResult);
            }
        };
    };

    // 支付结束
    public void chargeSuccess() {
        runOnGLThread(new Runnable() {
            @Override
            public void run() {
                Cocos2dxJavascriptJavaBridge.evalString("cc.PayManager.getInstance().chargeSuccess()");
            }
        });
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Workaround in https://stackoverflow.com/questions/16283079/re-launch-of-activity-on-home-button-but-only-the-first-time/16447508
        if (!isTaskRoot()) {
            // Android launched another instance of the root activity into an existing task
            //  so just quietly finish and go away, dropping the user back into the activity
            //  at the top of the stack (ie: the last state of this task)
            // Don't need to finish it again since it's finished in super.onCreate .
            return;
        }
        // DO OTHER INITIALIZATION BELOW
        SDKWrapper.getInstance().init(this);
        Window window = this.getWindow();
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        app = this;
        if (receiver == null){
            receiver = new MyReceiver();
            IntentFilter filter = new IntentFilter();
            filter.addAction(PUSH_ACTION);
            registerReceiver(receiver, filter);
        }
        if (miPushHandler == null) {
            miPushHandler = new MiPushHandler(getApplicationContext());
        }

        intentFilter = new IntentFilter();
        intentFilter.addAction("android.net.conn.CONNECTIVITY_CHANGE");
        networkChangeReceiver = new NetworkChangeReceiver();
        registerReceiver(networkChangeReceiver, intentFilter);
    }

    // 开始录音
    public static boolean startRecord(String path) {
        if (ContextCompat.checkSelfPermission(app, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED){
            return false;
        }
        if (mAudioRecordDemo == null) {
            mAudioRecordDemo = new AudioRecordDemo(app);
        }

        return mAudioRecordDemo.getNoiseLevel(path);
    }

    // 停止录音
    public static void stopRecord() {
        if (mAudioRecordDemo != null) {
            mAudioRecordDemo.stop();
        }
    }

    // 录音结束
    public void recordEnd(final String path){
        runOnGLThread(new Runnable() {
            @Override
            public void run() {
                Cocos2dxJavascriptJavaBridge.evalString(String.format("cc.RecordManager.getInstance().onRecordEnd(\"%s\");",path));
            }
        });
    }

    // 录音回调
    public void recordCallback(final double volume) {
        runOnGLThread(new Runnable() {
            @Override
            public void run() {
                Cocos2dxJavascriptJavaBridge.evalString(String.format("cc.RecordManager.getInstance().onDbCallback(\"%f\");",
                        volume));
            }
        });
    }

    // 发送推送token给玩家
    public void sendPushTokenToUser(final String token){
        runOnGLThread(new Runnable() {
            @Override
            public void run() {
                Cocos2dxJavascriptJavaBridge.evalString(String.format("cc.PushManager.getInstance().setDeviceToken(\"%s\");",token));
            }
        });
    }

    // 获取小米推送token
    private static boolean getMiPushToken(String appId, String appKey){
        boolean hasPermission = true;
        if ( Build.VERSION.SDK_INT >= 23){
            if (ContextCompat.checkSelfPermission(app, Manifest.permission.RECORD_AUDIO)
                    != PackageManager.PERMISSION_GRANTED){
                app.requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 2);
                hasPermission = false;
            }
        }

        app.MiPushId = appId;
        app.MiPushKey = appKey;
        if (app.getSystemName() != SYS_MIUI && Build.VERSION.SDK_INT >= 23){
            if (ContextCompat.checkSelfPermission(app, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(app, Manifest.permission.READ_PHONE_STATE)
                    != PackageManager.PERMISSION_GRANTED){
                app.requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE,Manifest.permission.READ_PHONE_STATE}, 1);
                return false;
            }
        }
        app.initMiPush();
        return hasPermission;
    }

    // 初始化小米推送
    private void initMiPush(){
        if(shouldInit()) {
            MiPushClient.registerPush(app, this.MiPushId, this.MiPushKey);
        }
        //打开Log
        LoggerInterface newLogger = new LoggerInterface() {

            @Override
            public void setTag(String tag) {
                // ignore
            }

            @Override
            public void log(String content, Throwable t) {
            }

            @Override
            public void log(String content) {
            }
        };
        Logger.setLogger(app, newLogger);
    }
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions,
                                           int[] grantResults) {
        switch (requestCode) {
            case 1:
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED&& grantResults[1] == PackageManager.PERMISSION_GRANTED) {
                    if (Build.VERSION.SDK_INT >= 23) { //Build.VERSION_CODES.N
                        if (ContextCompat.checkSelfPermission(app, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                                == PackageManager.PERMISSION_GRANTED && ContextCompat.checkSelfPermission(app, Manifest.permission.READ_PHONE_STATE)
                                == PackageManager.PERMISSION_GRANTED) {
                            this.initMiPush();
                        }
                    }
                    //startOpenPhoto();
                }
                break;
        }
        runOnGLThread(new Runnable() {
            @Override
            public void run() {
                Cocos2dxJavascriptJavaBridge.evalString("cc.DeviceManager.getInstance().permissionRequestEnd()");
            }
        });
    }

    private boolean shouldInit() {
        ActivityManager am = ((ActivityManager) getSystemService(Context.ACTIVITY_SERVICE));
        List<ActivityManager.RunningAppProcessInfo> processInfos = am.getRunningAppProcesses();
        String mainProcessName = getPackageName();
        int myPid = android.os.Process.myPid();
        for (ActivityManager.RunningAppProcessInfo info : processInfos) {
            if (info.pid == myPid && mainProcessName.equals(info.processName)) {
                return true;
            }
        }
        return false;
    }


    @Override
    public Cocos2dxGLSurfaceView onCreateView() {
        Cocos2dxGLSurfaceView glSurfaceView = new Cocos2dxGLSurfaceView(this);
        // TestCpp should create stencil buffer
        glSurfaceView.setEGLConfigChooser(5, 6, 5, 0, 16, 8);
        SDKWrapper.getInstance().setGLSurfaceView(glSurfaceView, this);

        return glSurfaceView;
    }

    @Override
    protected void onResume() {
        super.onResume();
        SDKWrapper.getInstance().onResume();

    }

    /**
     * 获取token
     */
    public static boolean getHuaWeiToken(final String appId) {
        boolean hasPermission = true;
        if ( Build.VERSION.SDK_INT >= 23){
            if (ContextCompat.checkSelfPermission(app, Manifest.permission.RECORD_AUDIO)
                    != PackageManager.PERMISSION_GRANTED){
                app.requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 2);
                hasPermission = false;
            }
        }

        HmsInstanceId inst  = HmsInstanceId.getInstance(app.getApplicationContext());
        // get token
        new Thread() {
            @Override
            public void run() {
                try {
                    String getToken =  HmsInstanceId.getInstance(app.getApplicationContext()).getToken(appId, "HCM");
                    if (!TextUtils.isEmpty(getToken)) {
                        app.sendPushTokenToUser(getToken);
                        //TODO: Send token to your app server.
                    }
                } catch (Exception e) {
                    //Log.e(TAG, "getToken failed.", e);
                }
            }
        }.start();
        return hasPermission;
    }

    /**
     * 是否有刘海屏
     *
     * @return
     */
    public static boolean hasNotchInScreen() {

        String manufacturer = Build.MANUFACTURER;

        if (manufacturer.equalsIgnoreCase("HUAWEI")) {
            return hasNotchHw(app);
        } else if (manufacturer.equalsIgnoreCase("xiaomi")) {
            return hasNotchXiaoMi(app);
        } else if (manufacturer.equalsIgnoreCase("oppo")) {
            return hasNotchOPPO(app);
        } else if (manufacturer.equalsIgnoreCase("vivo")) {
            return hasNotchVIVO(app);
        } else {
            return false;
        }
    }

    /**
     * 判断vivo是否有刘海屏
     * https://swsdl.vivo.com.cn/appstore/developer/uploadfile/20180328/20180328152252602.pdf
     *
     * @param activity
     * @return
     */
    private static boolean hasNotchVIVO(Activity activity) {
        try {
            Class<?> c = Class.forName("android.util.FtFeature");
            Method get = c.getMethod("isFeatureSupport", int.class);
            return (boolean) (get.invoke(c, 0x20));
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * 判断oppo是否有刘海屏
     * https://open.oppomobile.com/wiki/doc#id=10159
     *
     * @param activity
     * @return
     */
    private static boolean hasNotchOPPO(Activity activity) {
        return activity.getPackageManager().hasSystemFeature("com.oppo.feature.screen.heteromorphism");
    }

    /**
     * 判断xiaomi是否有刘海屏
     * https://dev.mi.com/console/doc/detail?pId=1293
     *
     * @param activity
     * @return
     */
    private static boolean hasNotchXiaoMi(Activity activity) {
        try {
            Class<?> c = Class.forName("android.os.SystemProperties");
            Method get = c.getMethod("getInt", String.class, int.class);
            return (int) (get.invoke(c, "ro.miui.notch", 0)) == 1;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * 判断华为是否有刘海屏
     * https://devcenter-test.huawei.com/consumer/cn/devservice/doc/50114
     *
     * @param activity
     * @return
     */
    private static boolean hasNotchHw(Activity activity) {

        try {
            ClassLoader cl = activity.getClassLoader();
            Class HwNotchSizeUtil = cl.loadClass("com.huawei.android.util.HwNotchSizeUtil");
            Method get = HwNotchSizeUtil.getMethod("hasNotchInScreen");
            return (boolean) get.invoke(HwNotchSizeUtil);
        } catch (Exception e) {
            return false;
        }
    }

    // 获取操作系统
    public static String getSystemName(){
        String SYS = SYS_OTHER;
        String manufacturer = Build.MANUFACTURER;
        if ("xiaomi".equalsIgnoreCase(manufacturer)) {
            return SYS_MIUI;
        } else if ("huawei".equalsIgnoreCase(manufacturer)){
            return SYS_EMUI;
        } else if ("meizu".equalsIgnoreCase(manufacturer)){
            return SYS_MEIZU;
        }
        return SYS;
    }

    // 获取设备名
    public static String getDeviceName() {
        if (Build.DEVICE.equals("x86")){
            return Build.MANUFACTURER + " " + Build.MODEL;
        }
        return Build.DEVICE;
    }

    // 获取设备名
    public static String getSystemVersion() {
        return Build.VERSION.RELEASE;
    }

    // 支付结束
    public void chargeFinished() {
        runOnGLThread(new Runnable() {
            @Override
            public void run() {
                Cocos2dxJavascriptJavaBridge.evalString(String.format("cc.PayManager.getInstance().chargeFinished();"));
            }
        });
    }

    // 支付宝支付
    public static void aliPay(String info){
        final String orderInfo = info;   // 订单信息
        Runnable payRunnable = new Runnable() {

            @Override
            public void run() {
                PayTask alipay = new PayTask(app);
                Map <String,String> result = alipay.payV2(orderInfo,true);

                Message msg = new Message();
                msg.obj = result;
                mHandler.sendMessage(msg);
            }
        };
        // 必须异步调用
        Thread payThread = new Thread(payRunnable);
        payThread.start();
    }

    // 微信支付
    public static boolean weChatPay(String appid, String partnerid, String prepayid, String packageValue,
                                    String noncestr, String timeStamp, String sign, String orderId) {

        api = WXAPIFactory.createWXAPI(app, appid, true);
        api.registerApp(appid);

        // 判断是否安装了微信客户端
        if (!api.isWXAppInstalled()) {
            app.chargeFinished();
            return false;
        }

        PayReq req = new PayReq();
        req.appId           = appid;
        req.partnerId       = partnerid;
        req.prepayId        = prepayid;
        req.nonceStr        = noncestr;
        req.timeStamp       = timeStamp;
        req.packageValue    = packageValue;
        req.sign            = sign;
        req.extData         = orderId;
        api.sendReq(req);
        return true;
    }

    // 微信支付结束
    public void wxPayFinished(final int errorCode, final String extData) {
        chargeFinished();
        if (errorCode == 0) {
            chargeSuccess();
        }
    }
    private static String buildTransaction(final String type) {
        return (type == null) ? String.valueOf(System.currentTimeMillis()) : type + System.currentTimeMillis();
    }

    // 微信分享图片给好友
    public static boolean wxShareImageToFriend(String appid, String imagePath) {
        api = WXAPIFactory.createWXAPI(app, appid, true);
        api.registerApp(appid);
        // 判断是否安装了微信客户端
        if (!api.isWXAppInstalled()) {
            return false;
        }

        Bitmap imageBitmap = BitmapFactory.decodeFile(imagePath);

        WXImageObject imgObj = new WXImageObject(imageBitmap);

        WXMediaMessage msg = new WXMediaMessage();
        msg.mediaObject = imgObj;

        Bitmap thumbBmp = Bitmap.createScaledBitmap(imageBitmap, imageBitmap.getWidth() / 8, imageBitmap.getHeight() / 8, true);
        imageBitmap.recycle();
        msg.thumbData = Util.bmpToByteArray(thumbBmp, true);

        SendMessageToWX.Req req = new SendMessageToWX.Req();
        req.transaction = buildTransaction(imagePath);
        req.message = msg;
        req.scene = SendMessageToWX.Req.WXSceneSession;
        api.sendReq(req);
        return true;
    }

    // 微信分享图片到朋友圈
    public static boolean wxShareImageToWorld(String appid, String imagePath) {
        api = WXAPIFactory.createWXAPI(app, appid, true);
        api.registerApp(appid);
        // 判断是否安装了微信客户端
        if (!api.isWXAppInstalled()) {
            return false;
        }

        Bitmap imageBitmap = BitmapFactory.decodeFile(imagePath);

        WXImageObject imgObj = new WXImageObject(imageBitmap);

        WXMediaMessage msg = new WXMediaMessage();
        msg.mediaObject = imgObj;

        Bitmap thumbBmp = Bitmap.createScaledBitmap(imageBitmap, imageBitmap.getWidth() / 8, imageBitmap.getHeight() / 8, true);
        imageBitmap.recycle();
        msg.thumbData = Util.bmpToByteArray(thumbBmp, true);

        SendMessageToWX.Req req = new SendMessageToWX.Req();
        req.transaction = buildTransaction(imagePath);
        req.message = msg;
        req.scene = SendMessageToWX.Req.WXSceneTimeline;
        api.sendReq(req);
        return true;
    }

    // 读文件内容
    public static String getFileData(String path){
        String data = "";
        try{
            File  file = new File(path);
            FileInputStream inputFile = new FileInputStream(file);
            byte[] buffer = new byte[(int)file.length()];
            inputFile.read(buffer);
            inputFile.close();
            data = Base64.encodeToString(buffer,Base64.DEFAULT);
        }catch(Exception e){
            e.printStackTrace();
        }
        return data;
    }


    @Override
    protected void onPause() {
        super.onPause();
        SDKWrapper.getInstance().onPause();

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        SDKWrapper.getInstance().onDestroy();
        unregisterReceiver(networkChangeReceiver);

    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        SDKWrapper.getInstance().onActivityResult(requestCode, resultCode, data);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        SDKWrapper.getInstance().onNewIntent(intent);
    }

    @Override
    protected void onRestart() {
        super.onRestart();
        SDKWrapper.getInstance().onRestart();
    }

    @Override
    protected void onStop() {
        super.onStop();
        SDKWrapper.getInstance().onStop();
    }

    @Override
    public void onBackPressed() {
        SDKWrapper.getInstance().onBackPressed();
        super.onBackPressed();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        SDKWrapper.getInstance().onConfigurationChanged(newConfig);
        super.onConfigurationChanged(newConfig);
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        SDKWrapper.getInstance().onRestoreInstanceState(savedInstanceState);
        super.onRestoreInstanceState(savedInstanceState);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        SDKWrapper.getInstance().onSaveInstanceState(outState);
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onStart() {
        SDKWrapper.getInstance().onStart();
        super.onStart();
    }

    /**
     * MyReceiver
     */
    public class MyReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            Bundle bundle = intent.getExtras();
            if (bundle != null && bundle.getString("msg") != null) {
                if ("onNewToken".equals(bundle.getString("method"))) {
                    app.sendPushTokenToUser(bundle.getString("msg"));
                    //token = bundle.getString("msg");
                }
                //showLog(bundle.getString("method") + ":" + bundle.getString("msg"));
            }
        }
    }

    public static MiPushHandler getHandler() {
        return miPushHandler;
    }


    public static class MiPushHandler extends Handler {

        private Context context;

        public MiPushHandler(Context context) {
            this.context = context;
        }

        @Override
        public void handleMessage(Message msg) {
            String s = (String) msg.obj;
            app.sendPushTokenToUser(s);
        }
    }

    class NetworkChangeReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo networkInfo = connectivityManager.getActiveNetworkInfo();
            if (networkInfo != null && networkInfo.isAvailable()) {
                switch (networkInfo.getType()) {
                    case TYPE_MOBILE:
                        runOnGLThread(new Runnable() {
                            @Override
                            public void run() {
                                Cocos2dxJavascriptJavaBridge.evalString("cc.DeviceManager.getInstance().networkChange(0)");
                            }
                        });
                        break;
                    case TYPE_WIFI:

                        runOnGLThread(new Runnable() {
                            @Override
                            public void run() {
                                Cocos2dxJavascriptJavaBridge.evalString("cc.DeviceManager.getInstance().networkChange(1)");
                            }
                        });
                        break;
                    default:
                        break;
                }
            } else {
                runOnGLThread(new Runnable() {
                    @Override
                    public void run() {
                        Cocos2dxJavascriptJavaBridge.evalString("cc.DeviceManager.getInstance().networkChange(-1)");
                    }
                });
            }
        }
    }
}