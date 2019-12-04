package org.cocos2dx.javascript;

import android.content.Intent;
import android.util.Log;

import com.huawei.hms.push.HmsMessageService;
import com.huawei.hms.push.RemoteMessage;
import com.huawei.hms.push.SendException;

import org.cocos2dx.lib.Cocos2dxJavascriptJavaBridge;

import static org.cocos2dx.lib.Cocos2dxHelper.runOnGLThread;

public class HuaWeiPushService extends HmsMessageService {
    @Override
    public void onNewToken(final String s) {
        super.onNewToken(s);
        runOnGLThread(new Runnable() {
            @Override
            public void run() {
                Cocos2dxJavascriptJavaBridge.evalString(String.format("cc.PushManager.getInstance().setDeviceToken(%s);",s));
            }
        });
    }

    @Override
    public void onMessageReceived(RemoteMessage remoteMessage) {
        super.onMessageReceived(remoteMessage);

        if (remoteMessage.getData().length() > 0) {
           // Log.d(ConstantClass.PUSH_TAG, "Message data payload: " + remoteMessage.getData());
        }
        if (remoteMessage.getNotification() != null) {
           // Log.d(ConstantClass.PUSH_TAG, "Message Notification Body: " + remoteMessage.getNotification().getBody());
        }
    }

    @Override
    public void onMessageSent(String s) {
        super.onMessageSent(s);
       /* Intent intent = new Intent();
        intent.setAction(PushActivity.PUSH_ACTION);
        intent.putExtra("method", "onMessageSent");
        intent.putExtra("msg", s);

        sendBroadcast(intent);*/
    }

    @Override
    public void onSendError(String s, Exception e) {
        super.onSendError(s, e);
        /*Intent intent = new Intent();
        intent.setAction(PushActivity.PUSH_ACTION);
        intent.putExtra("method", "onSendError");
        intent.putExtra("msg", s + "onSendError called, message id:" + s + " ErrCode:"
            + ((SendException) e).getErrorCode() + " message:" + e.getMessage());

        sendBroadcast(intent);*/
    }
}
