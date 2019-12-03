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

import android.annotation.SuppressLint;
import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.os.Bundle;

import android.content.Intent;
import android.content.res.Configuration;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.text.TextUtils;
import android.util.Log;
import android.view.Window;
import android.view.WindowManager;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Properties;
import com.alipay.sdk.app.PayTask;
import com.tencent.mm.opensdk.modelmsg.SendMessageToWX;
import com.tencent.mm.opensdk.modelmsg.WXImageObject;
import com.tencent.mm.opensdk.modelmsg.WXMediaMessage;
import com.tencent.mm.opensdk.openapi.IWXAPI;
import com.tencent.mm.opensdk.openapi.WXAPIFactory;
import com.tencent.mm.opensdk.modelpay.PayReq;


public class AppActivity extends Cocos2dxActivity {
    public static IWXAPI api;//微信API接口
    public static final String SYS_EMUI = "emui";
    public static final String SYS_MIUI = "miui";
    public static final String SYS_MEIZU = "meizu";
    public static final String SYS_OTHER = "other";
    public static AppActivity app;
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
     * 是否有刘海屏
     *
     * @return
     */
    public static boolean hasNotchInScreen() {

        // android  P 以上有标准 API 来判断是否有刘海屏
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            DisplayCutout displayCutout = app.getWindow().getDecorView().getRootWindowInsets().getDisplayCutout();
            if (displayCutout != null) {
                // 说明有刘海屏
                return true;
            }
        } else {
            // 通过其他方式判断是否有刘海屏  目前官方提供有开发文档的就 小米，vivo，华为（荣耀），oppo
            String manufacturer = Build.MANUFACTURER;

            if (Utils.isStringEmpty(manufacturer)) {
                return false;
            } else if (manufacturer.equalsIgnoreCase("HUAWEI")) {
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
        return false;
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
    public static int weChatPay(String appid, String partnerid, String prepayid, String packageValue,
                                String noncestr, String timeStamp, String sign, String orderId) {

        api = WXAPIFactory.createWXAPI(app, appid, true);
        api.registerApp(appid);

        // 判断是否安装了微信客户端
        if (!api.isWXAppInstalled()) {
            app.chargeFinished();
            return -1;
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
        return 1;
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
    public static int wxShareImageToFriend(String appid, String imagePath) {
        api = WXAPIFactory.createWXAPI(app, appid, true);
        api.registerApp(appid);
        // 判断是否安装了微信客户端
        if (!api.isWXAppInstalled()) {
            return -1;
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
        return 1;
    }

    // 微信分享图片到朋友圈
    public static int wxShareImageToWorld(String appid, String imagePath) {
        api = WXAPIFactory.createWXAPI(app, appid, true);
        api.registerApp(appid);
        // 判断是否安装了微信客户端
        if (!api.isWXAppInstalled()) {
            return -1;
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
        return 1;
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
}
