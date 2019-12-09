/****************************************************************************
 Copyright (c) 2010-2013 cocos2d-x.org
 Copyright (c) 2013-2016 Chukong Technologies Inc.
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

#import "AppController.h"
#import "cocos2d.h"
#import "AppDelegate.h"
#import "RootViewController.h"
#import "SDKWrapper.h"
#import "platform/ios/CCEAGLView-ios.h"
#import "sys/utsname.h"
#import "IAPShare.h"
#import "WXApiManager.h"
#include "cocos/scripting/js-bindings/jswrapper/SeApi.h"
#import<AVFoundation/AVFoundation.h>


using namespace cocos2d;

@implementation AppController

Application* app = nullptr;
UIImageView * testImageView = nullptr;
UIApplication * appli = nullptr;
static AppController* _appController = nil;
@synthesize window;

#pragma mark -
#pragma mark Application lifecycle

- (BOOL)application:(UIApplication *)application didFinishLaunchingWithOptions:(NSDictionary *)launchOptions {
    _appController = self;
    [[SDKWrapper getInstance] application:application didFinishLaunchingWithOptions:launchOptions];
    [UIApplication sharedApplication].idleTimerDisabled = YES;
    // Add the view controller's view to the window and display.
    float scale = [[UIScreen mainScreen] scale];
    CGRect bounds = [[UIScreen mainScreen] bounds];
    window = [[UIWindow alloc] initWithFrame: bounds];
    
    testImageView = [[UIImageView alloc]initWithFrame:bounds];
    UIImage * image = [UIImage imageNamed:@"LaunchScreenBackground.png"];
    [testImageView setImage:image];
    [testImageView setContentMode:UIViewContentModeScaleAspectFill];
    testImageView.layer.zPosition = MAXFLOAT;
    [window addSubview:testImageView];
    [window bringSubviewToFront:testImageView];
    
    [testImageView release];
    appli = application;
    // cocos2d application instance
    app = new AppDelegate(bounds.size.width * scale, bounds.size.height * scale);
    app->setMultitouch(true);
    
    // Use RootViewController to manage CCEAGLView
    _viewController = [[RootViewController alloc]init];
#ifdef NSFoundationVersionNumber_iOS_7_0
    _viewController.automaticallyAdjustsScrollViewInsets = NO;
    _viewController.extendedLayoutIncludesOpaqueBars = NO;
    _viewController.edgesForExtendedLayout = UIRectEdgeAll;
#else
    _viewController.wantsFullScreenLayout = YES;
#endif
    // Set RootViewController to window
    if ( [[UIDevice currentDevice].systemVersion floatValue] < 6.0)
    {
        // warning: addSubView doesn't work on iOS6
        [window addSubview: _viewController.view];
    }
    else
    {
        // use this method on ios6
        [window setRootViewController:_viewController];
    }
    
    [window makeKeyAndVisible];
    
    [[UIApplication sharedApplication] setStatusBarHidden:YES];
    
    //run the cocos2d-x game scene
    app->start();
    [NSTimer scheduledTimerWithTimeInterval:2.0
                                     target:self
                                   selector:@selector(onLoadFinished)
                                   userInfo:nil
                                    repeats:NO];
    return YES;
}

// 获取设备token
+ (void)getDeviceToken {
    // 推送
    if ([[UIDevice currentDevice].systemVersion floatValue] >= 10.0) {
    UNUserNotificationCenter * center = [UNUserNotificationCenter currentNotificationCenter];
    [center setDelegate:self];
    [center requestAuthorizationWithOptions:(UNAuthorizationOptionBadge|UNAuthorizationOptionSound|UNAuthorizationOptionAlert) completionHandler:^(BOOL granted, NSError * _Nullable error) {
    }];
    }else if ([[UIDevice currentDevice].systemVersion floatValue] >= 8.0){
    UIUserNotificationType notificationTypes = UIUserNotificationTypeBadge |
    UIUserNotificationTypeSound |
    UIUserNotificationTypeAlert;
    UIUserNotificationSettings *settings = [UIUserNotificationSettings settingsForTypes:notificationTypes categories:nil];
    [appli registerUserNotificationSettings:settings];
    }else{//ios8一下
    UIRemoteNotificationType notificationTypes = UIRemoteNotificationTypeBadge |
    UIRemoteNotificationTypeSound |
    UIRemoteNotificationTypeAlert;
    [[UIApplication sharedApplication] registerForRemoteNotificationTypes:notificationTypes];
    }
    [[UIApplication sharedApplication] setApplicationIconBadgeNumber:0];
    // 注册获得device Token
     
    [appli registerForRemoteNotifications];
}

// 将得到的deviceToken传给SDK
- (void)application:(UIApplication *)application didRegisterForRemoteNotificationsWithDeviceToken:(NSData *)deviceToken{
    if ([[[UIDevice currentDevice] systemVersion] floatValue] >= 13) {
        if (![deviceToken isKindOfClass:[NSData class]]) {
            //记录获取token失败的描述
            return;
        }
        const unsigned *tokenBytes = (const unsigned *)[deviceToken bytes];
        NSString *strToken = [NSString stringWithFormat:@"%08x%08x%08x%08x%08x%08x%08x%08x",
                              ntohl(tokenBytes[0]), ntohl(tokenBytes[1]), ntohl(tokenBytes[2]),
                              ntohl(tokenBytes[3]), ntohl(tokenBytes[4]), ntohl(tokenBytes[5]),
                              ntohl(tokenBytes[6]), ntohl(tokenBytes[7])];
        std::string c_strToken = [strToken UTF8String];
        se::ScriptEngine::getInstance()->evalString((cocos2d::StringUtils::format("cc.PushManager.getInstance().setDeviceToken(\"%s\");",c_strToken.c_str()).c_str()));
        return;
    } else {
        NSString *token = [NSString
                       stringWithFormat:@"%@",deviceToken];
        token = [token stringByReplacingOccurrencesOfString:@"<" withString:@""];
        token = [token stringByReplacingOccurrencesOfString:@">" withString:@""];
        token = [token stringByReplacingOccurrencesOfString:@" " withString:@""];
        std::string c_strToken = [token UTF8String];
        se::ScriptEngine::getInstance()->evalString((cocos2d::StringUtils::format("cc.PushManager.getInstance().setDeviceToken(\"%s\");",c_strToken.c_str()).c_str()));
    }
}
 
// 注册deviceToken失败
- (void)application:(UIApplication *)application didFailToRegisterForRemoteNotificationsWithError:(NSError *)error{
    NSLog(@"error -- %@",error);
}

- (void)didReceiveNotificationRequest:(UNNotificationRequest *)request withContentHandler:(void (^)(UNNotificationContent * _Nonnull))contentHandler {
}

// 判断是否是刘海屏
+ (BOOL)isPhoneX {
   BOOL iPhoneX = NO;
     if (UIDevice.currentDevice.userInterfaceIdiom != UIUserInterfaceIdiomPhone) {//判断是否是手机
           return iPhoneX;
       }
       if (@available(iOS 11.0, *)) {
           UIWindow *mainWindow = [[[UIApplication sharedApplication] delegate] window];
           if (mainWindow.safeAreaInsets.bottom > 0.0) {
               iPhoneX = YES;
           }
       }
       return iPhoneX;
}

NSTimer* levelTimer;
AVAudioRecorder *recorder;
// 开始录音
+(BOOL)startRecord:(NSString *) path{
    if (recorder) {
        return NO;
    }
    /* 必须添加这句话，否则在模拟器可以，在真机上获取始终是0  */
    [[AVAudioSession sharedInstance]
     setCategory: AVAudioSessionCategoryPlayAndRecord error: nil];
    
    /* 不需要保存录音文件 */
    NSURL *url = [NSURL fileURLWithPath:path];
    NSDictionary *settings = [[NSDictionary alloc] initWithObjectsAndKeys:
      //采样率  8000/11025/22050/44100/96000（影响音频的质量）
    [NSNumber numberWithFloat: 8000.0],AVSampleRateKey,
    // 音频格式
    [NSNumber numberWithInt: kAudioFormatLinearPCM],AVFormatIDKey,
    //采样位数  8、16、24、32 默认为16
    [NSNumber numberWithInt:16],AVLinearPCMBitDepthKey,
    // 音频通道数 1 或 2
    [NSNumber numberWithInt: 1], AVNumberOfChannelsKey,
    //录音质量
    [NSNumber numberWithInt:AVAudioQualityHigh],AVEncoderAudioQualityKey,
    nil];
    
    NSError *error;
    recorder = [[AVAudioRecorder alloc] initWithURL:url settings:settings error:&error];
    if (recorder)
    {
        [recorder prepareToRecord];
        recorder.meteringEnabled = YES;
        [recorder record];
        levelTimer = [NSTimer scheduledTimerWithTimeInterval: 0.1 target: self selector: @selector(levelTimerCallback:) userInfo: nil repeats: YES];
        return YES;
    }
    else
    {
        NSLog(@"%@", [error description]);
        return NO;
    }
}

// 停止录音
+(void)stopRecord{
    [levelTimer invalidate];
    levelTimer = nil;
    [recorder stop];
    recorder = nil;
}

/* 该方法确实会随环境音量变化而变化，但具体分贝值是否准确暂时没有研究 */
+(void)levelTimerCallback:(NSTimer *)timer {
    [recorder updateMeters];
    
    //averagePowerForChannel调用结果
    
    float avg = [recorder averagePowerForChannel:0];
    
    //比如把-60作为最低分贝
    
    float minValue = -60;
    
    //把60作为获取分配的范围
    
    float range = 60;
    
    //把100作为输出分贝范围
    
    float outRange = 100;
    
    //确保在最小值范围内
    
    if (avg < minValue)
        
    {
        
        avg = minValue;
        
    }
    
    //计算显示分贝
    
    float decibels = (avg + range) / range * outRange;
    se::ScriptEngine::getInstance()->evalString((cocos2d::StringUtils::format("cc.RecordManager.getInstance().onDbCallback(\"%f\");",decibels).c_str()));
}

// 获取设备型号然后手动转化为对应名称
+ (NSString *)getDeviceName
{
    // 需要#import "sys/utsname.h"
#warning 题主呕心沥血总结！！最全面！亲测！全网独此一份！！
    struct utsname systemInfo;
    uname(&systemInfo);
    NSString *deviceString = [NSString stringWithCString:systemInfo.machine encoding:NSUTF8StringEncoding];
    
    if ([deviceString isEqualToString:@"iPhone3,1"])    return @"iPhone 4";
    if ([deviceString isEqualToString:@"iPhone3,2"])    return @"iPhone 4";
    if ([deviceString isEqualToString:@"iPhone3,3"])    return @"iPhone 4";
    if ([deviceString isEqualToString:@"iPhone4,1"])    return @"iPhone 4S";
    if ([deviceString isEqualToString:@"iPhone5,1"])    return @"iPhone 5";
    if ([deviceString isEqualToString:@"iPhone5,2"])    return @"iPhone 5";
    if ([deviceString isEqualToString:@"iPhone5,3"])    return @"iPhone 5c";
    if ([deviceString isEqualToString:@"iPhone5,4"])    return @"iPhone 5c";
    if ([deviceString isEqualToString:@"iPhone6,1"])    return @"iPhone 5s";
    if ([deviceString isEqualToString:@"iPhone6,2"])    return @"iPhone 5s";
    if ([deviceString isEqualToString:@"iPhone7,1"])    return @"iPhone 6 Plus";
    if ([deviceString isEqualToString:@"iPhone7,2"])    return @"iPhone 6";
    if ([deviceString isEqualToString:@"iPhone8,1"])    return @"iPhone 6s";
    if ([deviceString isEqualToString:@"iPhone8,2"])    return @"iPhone 6s Plus";
    if ([deviceString isEqualToString:@"iPhone8,4"])    return @"iPhone SE";
    // 日行两款手机型号均为日本独占，可能使用索尼FeliCa支付方案而不是苹果支付
    if ([deviceString isEqualToString:@"iPhone9,1"])    return @"iPhone 7";
    if ([deviceString isEqualToString:@"iPhone9,2"])    return @"iPhone 7 Plus";
    if ([deviceString isEqualToString:@"iPhone9,3"])    return @"iPhone 7";
    if ([deviceString isEqualToString:@"iPhone9,4"])    return @"iPhone 7 Plus";
    if ([deviceString isEqualToString:@"iPhone10,1"])   return @"iPhone 8";
    if ([deviceString isEqualToString:@"iPhone10,4"])   return @"iPhone 8";
    if ([deviceString isEqualToString:@"iPhone10,2"])   return @"iPhone 8 Plus";
    if ([deviceString isEqualToString:@"iPhone10,5"])   return @"iPhone 8 Plus";
    if ([deviceString isEqualToString:@"iPhone10,3"])   return @"iPhone X";
    if ([deviceString isEqualToString:@"iPhone10,6"])   return @"iPhone X";
    if ([deviceString isEqualToString:@"iPhone11,2"])   return @"iPhone XS";
    if ([deviceString isEqualToString:@"iPhone11,4"])   return @"iPhone XS Max";
    if ([deviceString isEqualToString:@"iPhone11,6"])   return @"iPhone XS Max";
    if ([deviceString isEqualToString:@"iPhone11,8"])   return @"iPhone XR";
    if ([deviceString isEqualToString:@"iPhone12,1"])   return @"iPhone 11";
    if ([deviceString isEqualToString:@"iPhone12,3"])   return @"iPhone 11 Pro";
    if ([deviceString isEqualToString:@"iPhone12,5"])   return @"iPhone 11 Pro Max";
    
    if ([deviceString isEqualToString:@"iPad1,1"])      return @"iPad";
    if ([deviceString isEqualToString:@"iPad1,2"])      return @"iPad 3G";
    if ([deviceString isEqualToString:@"iPad2,1"])      return @"iPad 2";
    if ([deviceString isEqualToString:@"iPad2,2"])      return @"iPad 2";
    if ([deviceString isEqualToString:@"iPad2,3"])      return @"iPad 2";
    if ([deviceString isEqualToString:@"iPad2,4"])      return @"iPad 2";
    if ([deviceString isEqualToString:@"iPad2,5"])      return @"iPad Mini";
    if ([deviceString isEqualToString:@"iPad2,6"])      return @"iPad Mini";
    if ([deviceString isEqualToString:@"iPad2,7"])      return @"iPad Mini";
    if ([deviceString isEqualToString:@"iPad3,1"])      return @"iPad 3";
    if ([deviceString isEqualToString:@"iPad3,2"])      return @"iPad 3";
    if ([deviceString isEqualToString:@"iPad3,3"])      return @"iPad 3";
    if ([deviceString isEqualToString:@"iPad3,4"])      return @"iPad 4";
    if ([deviceString isEqualToString:@"iPad3,5"])      return @"iPad 4";
    if ([deviceString isEqualToString:@"iPad3,6"])      return @"iPad 4";
    if ([deviceString isEqualToString:@"iPad4,1"])      return @"iPad Air";
    if ([deviceString isEqualToString:@"iPad4,2"])      return @"iPad Air";
    if ([deviceString isEqualToString:@"iPad4,4"])      return @"iPad Mini 2";
    if ([deviceString isEqualToString:@"iPad4,5"])      return @"iPad Mini 2";
    if ([deviceString isEqualToString:@"iPad4,6"])      return @"iPad Mini 2";
    if ([deviceString isEqualToString:@"iPad4,7"])      return @"iPad Mini 3";
    if ([deviceString isEqualToString:@"iPad4,8"])      return @"iPad Mini 3";
    if ([deviceString isEqualToString:@"iPad4,9"])      return @"iPad Mini 3";
    if ([deviceString isEqualToString:@"iPad5,1"])      return @"iPad Mini 4";
    if ([deviceString isEqualToString:@"iPad5,2"])      return @"iPad Mini 4";
    if ([deviceString isEqualToString:@"iPad5,3"])      return @"iPad Air 2";
    if ([deviceString isEqualToString:@"iPad5,4"])      return @"iPad Air 2";
    if ([deviceString isEqualToString:@"iPad6,3"])      return @"iPad Pro 9.7";
    if ([deviceString isEqualToString:@"iPad6,4"])      return @"iPad Pro 9.7";
    if ([deviceString isEqualToString:@"iPad6,7"])      return @"iPad Pro 12.9";
    if ([deviceString isEqualToString:@"iPad6,8"])      return @"iPad Pro 12.9";
    if ([deviceString isEqualToString:@"iPad6,11"])    return @"iPad 5";
    if ([deviceString isEqualToString:@"iPad6,12"])    return @"iPad 5";
    if ([deviceString isEqualToString:@"iPad7,1"])     return @"iPad Pro 12.9 inch 2nd gen";
    if ([deviceString isEqualToString:@"iPad7,2"])     return @"iPad Pro 12.9 inch 2nd gen";
    if ([deviceString isEqualToString:@"iPad7,3"])     return @"iPad Pro 10.5 inch";
    if ([deviceString isEqualToString:@"iPad7,4"])     return @"iPad Pro 10.5 inch";
    if ([deviceString isEqualToString:@"iPad7,5"])     return @"iPad 6th generation";
    if ([deviceString isEqualToString:@"iPad7,6"])     return @"iPad 6th generation";
    if ([deviceString isEqualToString:@"iPad8,1"])     return @"iPad Pro";
    if ([deviceString isEqualToString:@"iPad8,2"])     return @"iPad Pro";
    if ([deviceString isEqualToString:@"iPad8,3"])     return @"iPad Pro";
    if ([deviceString isEqualToString:@"iPad8,4"])     return @"iPad Pro";
    if ([deviceString isEqualToString:@"iPad8,5"])     return @"iPad Pro";
    if ([deviceString isEqualToString:@"iPad8,6"])     return @"iPad Pro";
    if ([deviceString isEqualToString:@"iPad8,7"])     return @"iPad Pro";
    if ([deviceString isEqualToString:@"iPad8,8"])     return @"iPad Pro";
    if ([deviceString isEqualToString:@"iPad11,1"])     return @"iPad mini 5th";
    if ([deviceString isEqualToString:@"iPad11,2"])     return @"iPad mini 5th";
    if ([deviceString isEqualToString:@"iPad11,3"])     return @"iPad Air 3rd ";
    if ([deviceString isEqualToString:@"iPad11,4"])     return @"iPad Air 3rd ";
    
    
    if ([deviceString isEqualToString:@"i386"])         return @"Simulator";
    if ([deviceString isEqualToString:@"x86_64"])       return @"Simulator";
    
    return deviceString;
}

// 获取系统名称
+ (NSString *)getSystemName{
    UIDevice *device = [[UIDevice alloc] init];
    NSString *systemName = device.systemName;
    return systemName;
}

// 获取系统版本
+ (NSString *)getSystemVersion{
    UIDevice *device = [[UIDevice alloc] init];
    NSString *systemVersion = device.systemVersion;
    return systemVersion;
}

- (void)removeSplashView {
    testImageView.layer.zPosition = 0;
    [testImageView removeFromSuperview];
    testImageView = nil;
}

-(void) onLoadFinished {
    [_appController removeSplashView];
    // set pointer to nil after using
    _appController = nil;
}

// 购买物品
+ (void)buyGoods:(BOOL)isProduction productIdentifier : (NSString *) productIdentifier orderId : (NSString *) orderId notifyUrl : (NSString *) notifyUrl
{
    [[IAPShare sharedHelper].iap clearSavedPurchasedProducts];
    NSSet* dataSet = [[NSSet alloc] initWithObjects:productIdentifier, nil];
    
    [IAPShare sharedHelper].iap = [[IAPHelper alloc] initWithProductIdentifiers:dataSet];
    
    [IAPShare sharedHelper].iap.production = isProduction;
    
    [[IAPShare sharedHelper].iap requestProductsWithCompletion:^(SKProductsRequest* request,SKProductsResponse* response)
     {
         if(response.products.count > 0) {
             SKProduct* product = [IAPShare sharedHelper].iap.products[0];
             
             [[IAPShare sharedHelper].iap buyProduct:product
                                        onCompletion:^(SKPaymentTransaction* trans){
                std::string c_orderId = [orderId UTF8String];
                                            if(trans.error)
                                            {se::ScriptEngine::getInstance()->evalString((cocos2d::StringUtils::format("cc.PayManager.getInstance().chargeFinished(\"%s\");",c_orderId.c_str()).c_str()));
                                                NSLog(@"Fail %@",[trans.error localizedDescription]);
                                            }
                                            else if(trans.transactionState == SKPaymentTransactionStatePurchased) {
                                                NSURL* receiptURL = [[NSBundle mainBundle]appStoreReceiptURL];
                                                    
                                                NSString* receipt = [[NSData dataWithContentsOfURL:receiptURL]base64EncodedStringWithOptions:0];

                                                std::string c_receiptString = [receipt UTF8String];
                                                std::string c_orderId = [orderId UTF8String];
                                                std::string c_notifyUrl = [notifyUrl UTF8String];
                                                se::ScriptEngine::getInstance()->evalString((cocos2d::StringUtils::format("cc.PayManager.getInstance().iOSChargeSuccess(\"%s\",\"%s\",\"%s\");",c_receiptString.c_str(),c_orderId.c_str(),c_notifyUrl.c_str()).c_str()));
                                                
                                                                                  }
                                            else if(trans.transactionState == SKPaymentTransactionStateFailed) {
                                                se::ScriptEngine::getInstance()->evalString((cocos2d::StringUtils::format("cc.PayManager.getInstance().chargeFinished(\"%s\");",c_orderId.c_str()).c_str()));
                                                NSLog(@"Fail");
                                            } else {
                                                se::ScriptEngine::getInstance()->evalString((cocos2d::StringUtils::format("cc.PayManager.getInstance().chargeFinished(\"%s\");",c_orderId.c_str()).c_str()));
                                            }
                                        }];//end of buy product
         }
     }];
}


- (BOOL)application:(UIApplication *)application handleOpenURL:(NSURL *)url {
    return  [WXApi handleOpenURL:url delegate:[WXApiManager sharedManager]];
}

- (BOOL)application:(UIApplication *)application openURL:(NSURL *)url sourceApplication:(NSString *)sourceApplication annotation:(id)annotation {
    return [WXApi handleOpenURL:url delegate:[WXApiManager sharedManager]];
}

- (BOOL)application:(UIApplication *)application continueUserActivity:(NSUserActivity *)userActivity restorationHandler:(void(^)(NSArray<id<UIUserActivityRestoring>> * __nullable restorableObjects))restorationHandler {
    return [WXApi handleOpenUniversalLink:userActivity delegate:[WXApiManager sharedManager]];
}

// 初始化微信
+(void)initWeChat:(NSString *)appId universalLink:(NSString *)universalLink {
    [WXApi registerApp:appId universalLink:universalLink];
}

// 微信分享图片给好友
+ (bool) wxShareImageToFriend:(NSString *) imagePath{
    if (![WXApi isWXAppInstalled]) {
        return false;
    }
    
    WXMediaMessage *message = [WXMediaMessage message];
    UIImage *thumbImage = [UIImage imageWithData:[NSData dataWithContentsOfFile:imagePath]];
    CGSize reSize = {thumbImage.size.width / 3,thumbImage.size.height / 3};
    UIGraphicsBeginImageContext(CGSizeMake(reSize.width, reSize.height));
    [thumbImage drawInRect:CGRectMake(0, 0, reSize.width, reSize.height)];
    thumbImage = UIGraphicsGetImageFromCurrentImageContext();
    UIGraphicsEndImageContext();
    [message setThumbImage:thumbImage];
    
    WXImageObject *imageObject = [WXImageObject object];
    imageObject.imageData = [NSData dataWithContentsOfFile:imagePath];
    message.mediaObject = imageObject;
    SendMessageToWXReq* req = [[SendMessageToWXReq alloc] init];
    req.bText = NO;
    req.message = message;
    req.scene = WXSceneSession;
    [WXApi sendReq:req completion:^(BOOL success) {
        //<#code#>
    }];
    return true;
}

// 微信图片分享到朋友圈
+ (bool) wxShareImageToWorld:(NSString *)imagePath{
    if (![WXApi isWXAppInstalled]) {
        return false;
    }
    
    WXMediaMessage *message = [WXMediaMessage message];
    UIImage *thumbImage = [UIImage imageWithData:[NSData dataWithContentsOfFile:imagePath]];
    CGSize reSize = {thumbImage.size.width / 3,thumbImage.size.height / 3};
    UIGraphicsBeginImageContext(CGSizeMake(reSize.width, reSize.height));
    [thumbImage drawInRect:CGRectMake(0, 0, reSize.width, reSize.height)];
    thumbImage = UIGraphicsGetImageFromCurrentImageContext();
    UIGraphicsEndImageContext();
    [message setThumbImage:thumbImage];
    
    WXImageObject *imageObject = [WXImageObject object];
    imageObject.imageData = [NSData dataWithContentsOfFile:imagePath];
    message.mediaObject = imageObject;
    SendMessageToWXReq* req = [[SendMessageToWXReq alloc] init];
    req.bText = NO;
    req.message = message;
    req.scene = WXSceneTimeline;
    [WXApi sendReq:req completion:^(BOOL success) {
        //<#code#>
    }];
    return true;
}

- (void)applicationWillResignActive:(UIApplication *)application {
    /*
     Sent when the application is about to move from active to inactive state. This can occur for certain types of temporary interruptions (such as an incoming phone call or SMS message) or when the user quits the application and it begins the transition to the background state.
     Use this method to pause ongoing tasks, disable timers, and throttle down OpenGL ES frame rates. Games should use this method to pause the game.
     */
    [[SDKWrapper getInstance] applicationWillResignActive:application];
}

- (void)applicationDidBecomeActive:(UIApplication *)application {
    /*
     Restart any tasks that were paused (or not yet started) while the application was inactive. If the application was previously in the background, optionally refresh the user interface.
     */
    [[SDKWrapper getInstance] applicationDidBecomeActive:application];
}

- (void)applicationDidEnterBackground:(UIApplication *)application {
    /*
     Use this method to release shared resources, save user data, invalidate timers, and store enough application state information to restore your application to its current state in case it is terminated later.
     If your application supports background execution, called instead of applicationWillTerminate: when the user quits.
     */
    [[SDKWrapper getInstance] applicationDidEnterBackground:application];
    app->applicationDidEnterBackground();
    
}

- (void)applicationWillEnterForeground:(UIApplication *)application {
    /*
     Called as part of  transition from the background to the inactive state: here you can undo many of the changes made on entering the background.
     */
    [[SDKWrapper getInstance] applicationWillEnterForeground:application];
    app->applicationWillEnterForeground();
    
}

- (void)applicationWillTerminate:(UIApplication *)application
{
    [[SDKWrapper getInstance] applicationWillTerminate:application];
    delete app;
    app = nil;
    appli = nil;
}


#pragma mark -
#pragma mark Memory management

- (void)applicationDidReceiveMemoryWarning:(UIApplication *)application {
    /*
     Free up as much memory as possible by purging cached data objects that can be recreated (or reloaded from disk) later.
     */
}

@end
