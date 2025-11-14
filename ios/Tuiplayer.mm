#import "Tuiplayer.h"
#import "react-native-tuiplayer-Swift.h"
#import <React/RCTUIManager.h>
#import <React/RCTUtils.h>
#import <TUIPlayerShortVideo/TUIShortVideoView.h>

@implementation Tuiplayer
- (void)initialize:(NSDictionary *)config
{
  [TuiplayerModule.shared initializeWith:config];
}

- (NSDictionary *)getShortVideoConstants
{
  NSMutableDictionary *constants = [NSMutableDictionary dictionary];
  constants[@"listPlayMode"] = @{
    @"MODE_ONE_LOOP": @(TUIPlayModeOneLoop),
    @"MODE_LIST_LOOP": @(TUIPlayModeListLoop),
    @"MODE_CUSTOM": @(TUIPlayModeCustomLoop),
  };
  constants[@"resolutionType"] = @{
    @"GLOBAL": @(-1),
    @"CURRENT": @(-2),
  };
  return constants;
}

- (void)performWithView:(double)viewTag
                resolve:(RCTPromiseResolveBlock)resolve
                 reject:(RCTPromiseRejectBlock)reject
               forError:(NSString *)message
                  block:(id (^)(TuiplayerShortVideoView *view))block
{
  NSNumber *tag = @(viewTag);
  RCTExecuteOnMainQueue(^{
    UIView *view = [self.bridge.uiManager viewForReactTag:tag];
    if (view == nil) {
      reject(@"E_TUIP_VIEW_NOT_FOUND", [NSString stringWithFormat:@"No view found for tag %@.", tag], nil);
      return;
    }
    if (![view isKindOfClass:[TuiplayerShortVideoView class]]) {
      reject(@"E_TUIP_INVALID_VIEW_TYPE", @"View is not TuiplayerShortVideoView.", nil);
      return;
    }
    TuiplayerShortVideoView *shortVideoView = (TuiplayerShortVideoView *)view;
    @try {
      id result = block(shortVideoView);
      resolve(result);
    } @catch (NSException *exception) {
      reject(@"E_TUIP_UNEXPECTED", message, [NSError errorWithDomain:@"Tuiplayer" code:0 userInfo:@{NSLocalizedDescriptionKey: exception.reason ?: message}]);
    }
  });
}

- (void)getCurrentShortVideoSource:(double)viewTag
                           resolve:(RCTPromiseResolveBlock)resolve
                            reject:(RCTPromiseRejectBlock)reject
{
  [self performWithView:viewTag
                resolve:^(id result) {
                  resolve(result);
                }
                 reject:reject
               forError:@"Failed to obtain the current short video source."
                  block:^id (TuiplayerShortVideoView *shortVideoView) {
                    NSDictionary *source = [shortVideoView currentSourceSnapshot];
                    NSNumber *index = [shortVideoView currentIndexValue];
                    if (source == nil && index == nil) {
                      return (id)nil;
                    }
                    NSMutableDictionary *payload = [NSMutableDictionary dictionary];
                    if (index != nil) {
                      payload[@"index"] = index;
                    }
                    if (source != nil) {
                      payload[@"source"] = source;
                    }
                    return payload;
                  }];
}

- (void)getShortVideoDataCount:(double)viewTag
                       resolve:(RCTPromiseResolveBlock)resolve
                        reject:(RCTPromiseRejectBlock)reject
{
  [self performWithView:viewTag
                resolve:^(id result) {
                  resolve(result);
                }
                 reject:reject
               forError:@"Failed to obtain short video data count."
                  block:^id (TuiplayerShortVideoView *shortVideoView) {
                    return [shortVideoView dataCount];
                  }];
}

- (void)getShortVideoDataByIndex:(double)viewTag
                          index:(double)index
                         resolve:(RCTPromiseResolveBlock)resolve
                          reject:(RCTPromiseRejectBlock)reject
{
  [self performWithView:viewTag
                resolve:^(id result) {
                  resolve(result);
                }
                 reject:reject
               forError:@"Failed to obtain short video data by index."
                  block:^id (TuiplayerShortVideoView *shortVideoView) {
                    return [shortVideoView dataSnapshot:@((NSInteger)index)];
                  }];
}

- (void)removeShortVideoData:(double)viewTag
                       index:(double)index
                      resolve:(RCTPromiseResolveBlock)resolve
                       reject:(RCTPromiseRejectBlock)reject
{
  [self performWithView:viewTag
                resolve:^(id result) {
                  resolve(nil);
                }
                 reject:reject
               forError:@"Failed to remove short video data."
                  block:^id (TuiplayerShortVideoView *shortVideoView) {
                    [shortVideoView removeData:@((NSInteger)index)];
                    return nil;
                  }];
}

- (void)removeShortVideoRange:(double)viewTag
                        index:(double)index
                        count:(double)count
                       resolve:(RCTPromiseResolveBlock)resolve
                        reject:(RCTPromiseRejectBlock)reject
{
  [self performWithView:viewTag
                resolve:^(id result) {
                  resolve(nil);
                }
                 reject:reject
               forError:@"Failed to remove short video data range."
                  block:^id (TuiplayerShortVideoView *shortVideoView) {
                    [shortVideoView removeRangeData:@((NSInteger)index) count:@((NSInteger)count)];
                    return nil;
                  }];
}

- (void)removeShortVideoDataByIndexes:(double)viewTag
                              indexes:(NSArray<NSNumber *> *)indexes
                              resolve:(RCTPromiseResolveBlock)resolve
                               reject:(RCTPromiseRejectBlock)reject
{
  [self performWithView:viewTag
                resolve:^(id result) {
                  resolve(nil);
                }
                 reject:reject
               forError:@"Failed to remove short video data by indexes."
                  block:^id (TuiplayerShortVideoView *shortVideoView) {
                    [shortVideoView removeDataByIndexes:indexes];
                    return nil;
                  }];
}

- (void)addShortVideoData:(double)viewTag
                   source:(NSDictionary *)source
                    index:(double)index
                  resolve:(RCTPromiseResolveBlock)resolve
                   reject:(RCTPromiseRejectBlock)reject
{
  [self performWithView:viewTag
                resolve:^(id result) {
                  resolve(nil);
                }
                 reject:reject
               forError:@"Failed to add short video data."
                  block:^id (TuiplayerShortVideoView *shortVideoView) {
                    [shortVideoView addData:source index:@((NSInteger)index)];
                    return nil;
                  }];
}

- (void)addShortVideoRange:(double)viewTag
                   sources:(NSArray<NSDictionary *> *)sources
                startIndex:(double)startIndex
                   resolve:(RCTPromiseResolveBlock)resolve
                    reject:(RCTPromiseRejectBlock)reject
{
  [self performWithView:viewTag
                resolve:^(id result) {
                  resolve(nil);
                }
                 reject:reject
               forError:@"Failed to add short video data range."
                  block:^id (TuiplayerShortVideoView *shortVideoView) {
                    [shortVideoView addRangeData:sources startIndex:@((NSInteger)startIndex)];
                    return nil;
                  }];
}

- (void)replaceShortVideoData:(double)viewTag
                       source:(NSDictionary *)source
                        index:(double)index
                      resolve:(RCTPromiseResolveBlock)resolve
                       reject:(RCTPromiseRejectBlock)reject
{
  [self performWithView:viewTag
                resolve:^(id result) {
                  resolve(nil);
                }
                 reject:reject
               forError:@"Failed to replace short video data."
                  block:^id (TuiplayerShortVideoView *shortVideoView) {
                    [shortVideoView replaceData:source index:@((NSInteger)index)];
                    return nil;
                  }];
}

- (void)replaceShortVideoRange:(double)viewTag
                       sources:(NSArray<NSDictionary *> *)sources
                    startIndex:(double)startIndex
                       resolve:(RCTPromiseResolveBlock)resolve
                        reject:(RCTPromiseRejectBlock)reject
{
  [self performWithView:viewTag
                resolve:^(id result) {
                  resolve(nil);
                }
                 reject:reject
               forError:@"Failed to replace short video data range."
                  block:^id (TuiplayerShortVideoView *shortVideoView) {
                    [shortVideoView replaceRangeData:sources startIndex:@((NSInteger)startIndex)];
                    return nil;
                  }];
}

- (void)callShortVideoVodPlayer:(double)viewTag
                       command:(NSString *)command
                       options:(NSDictionary *)options
                       resolve:(RCTPromiseResolveBlock)resolve
                        reject:(RCTPromiseRejectBlock)reject
{
  NSString *errorMessage =
      [NSString stringWithFormat:@"Failed to execute VOD player command: %@", command];
  [self performWithView:viewTag
                resolve:^(id result) {
                  resolve(result ?: [NSNull null]);
                }
                 reject:reject
               forError:errorMessage
                  block:^id (TuiplayerShortVideoView *shortVideoView) {
                    NSError *error = nil;
                    id value = [shortVideoView handleVodPlayerCommand:command
                                                              options:options
                                                                error:&error];
                    if (error) {
                      @throw [NSException exceptionWithName:@"TuiplayerVodCommand"
                                                     reason:error.localizedDescription
                                                   userInfo:@{ NSUnderlyingErrorKey : error }];
                    }
                    return value ?: [NSNull null];
                  }];
}

- (std::shared_ptr<facebook::react::TurboModule>)getTurboModule:
    (const facebook::react::ObjCTurboModule::InitParams &)params
{
    return std::make_shared<facebook::react::NativeTuiplayerSpecJSI>(params);
}

+ (NSString *)moduleName
{
  return @"Tuiplayer";
}

@end
