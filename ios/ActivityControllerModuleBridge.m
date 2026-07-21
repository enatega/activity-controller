#import <React/RCTBridgeModule.h>
#import <React/RCTEventEmitter.h>
#import <activity_controller/activity_controller-Swift.h>

@interface RCT_EXTERN_MODULE(ActivityController, RCTEventEmitter)

RCT_EXTERN_METHOD(areLiveActivitiesEnabled:(RCTPromiseResolveBlock)resolve
                  rejecter:(RCTPromiseRejectBlock)reject)

RCT_EXTERN_METHOD(startLiveActivity:(NSString *)rawData
                  resolver:(RCTPromiseResolveBlock)resolve
                  rejecter:(RCTPromiseRejectBlock)reject)

RCT_EXTERN_METHOD(updateLiveActivity:(NSString *)rawData
                  resolver:(RCTPromiseResolveBlock)resolve
                  rejecter:(RCTPromiseRejectBlock)reject)

RCT_EXTERN_METHOD(stopLiveActivity:(RCTPromiseResolveBlock)resolve
                  rejecter:(RCTPromiseRejectBlock)reject)

RCT_EXTERN_METHOD(isLiveActivityRunning:(RCTPromiseResolveBlock)resolve
                  rejecter:(RCTPromiseRejectBlock)reject)

RCT_EXTERN_METHOD(saveImageToAppGroup:(NSString *)rawData
                  appGroupId:(NSString *)appGroupId
                  resolver:(RCTPromiseResolveBlock)resolve
                  rejecter:(RCTPromiseRejectBlock)reject)

RCT_EXTERN_METHOD(cleanAppGroupImages:(double)maxAgeHours
                  appGroupId:(NSString *)appGroupId
                  resolver:(RCTPromiseResolveBlock)resolve
                  rejecter:(RCTPromiseRejectBlock)reject)

@end
