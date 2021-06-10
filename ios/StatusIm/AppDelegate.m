/**
 * Copyright (c) Facebook, Inc. and its affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree. An additional grant
 * of patent rights can be found in the PATENTS file in the same directory.
 */

#import "AppDelegate.h"

#import <asl.h>
#import "ReactNativeConfig.h"
#import "React/RCTLog.h"
#import "RCTBundleURLProvider.h"
#import "RNSplashScreen.h"
#import "RCTLinkingManager.h"

#import <React/RCTBridge.h>
#import <React/RCTBundleURLProvider.h>
#import <React/RCTRootView.h>

#import <UserNotifications/UserNotifications.h>
#import <RNCPushNotificationIOS.h>

#import <TSBackgroundFetch/TSBackgroundFetch.h>

 /*
#if DEBUG
#import <FlipperKit/FlipperClient.h>
#import <FlipperKitLayoutPlugin/FlipperKitLayoutPlugin.h>
#import <FlipperKitUserDefaultsPlugin/FKUserDefaultsPlugin.h>
#import <FlipperKitNetworkPlugin/FlipperKitNetworkPlugin.h>
#import <SKIOSNetworkPlugin/SKIOSNetworkAdapter.h>
#import <FlipperKitReactPlugin/FlipperKitReactPlugin.h>
 */
 /*
static void InitializeFlipper(UIApplication *application) {
  FlipperClient *client = [FlipperClient sharedClient];
  SKDescriptorMapper *layoutDescriptorMapper = [[SKDescriptorMapper alloc] initWithDefaults];
  [client addPlugin:[[FlipperKitLayoutPlugin alloc] initWithRootNode:application withDescriptorMapper:layoutDescriptorMapper]];
  [client addPlugin:[[FKUserDefaultsPlugin alloc] initWithSuiteName:nil]];
  [client addPlugin:[FlipperKitReactPlugin new]];
  [client addPlugin:[[FlipperKitNetworkPlugin alloc] initWithNetworkAdapter:[SKIOSNetworkAdapter new]]];
  [client start];
       }
#endif
  */

@implementation AppDelegate 
{
    UIView *_blankView;
}

- (BOOL)application:(UIApplication *)application didFinishLaunchingWithOptions:(NSDictionary *)launchOptions
{
  #if DEBUG
    //InitializeFlipper(application);
  #endif
  signal(SIGPIPE, SIG_IGN);
  NSURL *jsCodeLocation;

  /* Set logging level from React Native */
  NSString *logLevel = [ReactNativeConfig envFor:@"LOG_LEVEL"];
  if([logLevel isEqualToString:@"error"]){
    RCTSetLogThreshold(RCTLogLevelError);
  }
  else if([logLevel isEqualToString:@"warn"]){
    RCTSetLogThreshold(RCTLogLevelWarning);
  }
  else if([logLevel isEqualToString:@"info"]){
    RCTSetLogThreshold(RCTLogLevelInfo);
  }
  else if([logLevel isEqualToString:@"debug"]){
    RCTSetLogThreshold(RCTLogLevelTrace);
  }

  NSDictionary *appDefaults = [NSDictionary
      dictionaryWithObject:[NSNumber numberWithBool:NO] forKey:@"BLANK_PREVIEW"];
  [[NSUserDefaults standardUserDefaults] registerDefaults:appDefaults];

  [[TSBackgroundFetch sharedInstance] didFinishLaunching];
  
  RCTBridge *bridge = [[RCTBridge alloc] initWithDelegate:self launchOptions:launchOptions];
  RCTRootView *rootView = [[RCTRootView alloc] initWithBridge:bridge
                                                      moduleName:@"StatusIm"
                                               initialProperties:nil];
  rootView.backgroundColor = [[UIColor alloc] initWithRed:1.0f green:1.0f blue:1.0f alpha:1];

  self.window = [[UIWindow alloc] initWithFrame:[UIScreen mainScreen].bounds];
  _blankView = [[UIView alloc]initWithFrame:self.window.frame];
  _blankView.backgroundColor = [UIColor whiteColor];
  _blankView.alpha = 0;

  UIViewController *rootViewController = [UIViewController new];
  rootViewController.view = rootView;
  self.window.rootViewController = rootViewController;
  [self.window makeKeyAndVisible];
  [RNSplashScreen show];

  UNUserNotificationCenter *center = [UNUserNotificationCenter currentNotificationCenter];
  center.delegate = self;
  
  return YES;
}

- (BOOL)application:(UIApplication *)application
            openURL:(NSURL *)url
            options:(NSDictionary<UIApplicationOpenURLOptionsKey,id> *)options
{
  return [RCTLinkingManager application:application openURL:url options:options];
}


- (BOOL)application:(UIApplication *)application continueUserActivity:(NSUserActivity *)userActivity
 restorationHandler:(void (^)(NSArray * _Nullable))restorationHandler
{
  return [RCTLinkingManager application:application
                   continueUserActivity:userActivity
                     restorationHandler:restorationHandler];
}

- (NSURL *)sourceURLForBridge:(RCTBridge *)bridge
{
#if DEBUG
  return [[RCTBundleURLProvider sharedSettings] jsBundleURLForBundleRoot:@"index" fallbackResource:nil];
#else
  return [[NSBundle mainBundle] URLForResource:@"main" withExtension:@"jsbundle"];
#endif
}

- (void)applicationWillResignActive:(UIApplication *)application {
  if ([[NSUserDefaults standardUserDefaults] boolForKey:@"BLANK_PREVIEW"]) {
    [self.window addSubview:_blankView];
    [self.window bringSubviewToFront:_blankView];

    [UIView animateWithDuration:0.5 animations:^{
      _blankView.alpha = 1;
    }];
  } 
}

- (void)applicationDidBecomeActive:(UIApplication *)application {
  if ([[NSUserDefaults standardUserDefaults] boolForKey:@"BLANK_PREVIEW"]) {
    [UIView animateWithDuration:0.5 animations:^{
      _blankView.alpha = 0;
    } completion:^(BOOL finished) {
      [_blankView removeFromSuperview];
    }];
  }
}

// Required to register for notifications
- (void)application:(UIApplication *)application didRegisterUserNotificationSettings:(UIUserNotificationSettings *)notificationSettings
{
 [RNCPushNotificationIOS didRegisterUserNotificationSettings:notificationSettings];
}
// Required for the register event.
- (void)application:(UIApplication *)application didRegisterForRemoteNotificationsWithDeviceToken:(NSData *)deviceToken
{
 [RNCPushNotificationIOS didRegisterForRemoteNotificationsWithDeviceToken:deviceToken];
}
// Required for the registrationError event.
- (void)application:(UIApplication *)application didFailToRegisterForRemoteNotificationsWithError:(NSError *)error
{
 [RNCPushNotificationIOS didFailToRegisterForRemoteNotificationsWithError:error];
}
// IOS 10+ Required for localNotification event
- (void)userNotificationCenter:(UNUserNotificationCenter *)center
didReceiveNotificationResponse:(UNNotificationResponse *)response
         withCompletionHandler:(void (^)(void))completionHandler
{
  [RNCPushNotificationIOS didReceiveNotificationResponse:response];
  completionHandler();
}
// IOS 4-10 Required for the localNotification event.
- (void)application:(UIApplication *)application didReceiveLocalNotification:(UILocalNotification *)notification
{
 [RNCPushNotificationIOS didReceiveLocalNotification:notification];
}
// Manage notifications while app is in the foreground
- (void)userNotificationCenter:(UNUserNotificationCenter *)center willPresentNotification:(UNNotification *)notification withCompletionHandler:(void (^)(UNNotificationPresentationOptions options))completionHandler
{
  NSDictionary *userInfo = notification.request.content.userInfo;
  
  NSString *notificationType = userInfo[@"notificationType"]; // check your notification type
  if (![notificationType  isEqual: @"local-notification"]) { // we silence all notifications which are not local
    completionHandler(UNNotificationPresentationOptionNone);
    return;
  }
 
  completionHandler(UNAuthorizationOptionSound | UNAuthorizationOptionAlert | UNAuthorizationOptionBadge);
}

@end
