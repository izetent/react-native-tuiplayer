#import "Txplayer.h"

#import <React/RCTBridge.h>
#import <React/RCTConvert.h>

#import "RNEventDispatcher.h"
#import "RNShortEngine.h"
#import "RNShortVideoView.h"
#import "RNVodController.h"
#import "RNTransformer.h"
#import "RNViewRegistry.h"

@interface Txplayer ()

@property(nonatomic, strong) RNShortEngine *shortEngine;
@property(nonatomic, weak) RCTBridge *bridge;

@end

@implementation Txplayer

@synthesize bridge = _bridge;

- (void)runOnMain:(dispatch_block_t)block {
  if ([NSThread isMainThread]) {
    block();
  } else {
    dispatch_async(dispatch_get_main_queue(), block);
  }
}

- (instancetype)init {
  self = [super init];
  if (self) {
    _shortEngine = [RNShortEngine new];
  }
  return self;
}

- (void)setBridge:(RCTBridge *)bridge {
  _bridge = bridge;
  [RNEventDispatcher setupWithBridge:bridge];
}

- (void)setPlayerConfig:(NSDictionary *)config
                resolve:(RCTPromiseResolveBlock)resolve
                 reject:(RCTPromiseRejectBlock)reject {
  @try {
    [self.shortEngine setConfig:config ?: @{}];
    resolve(nil);
  } @catch (NSException *exception) {
    reject(@"E_PLAYER_CONFIG", exception.reason, nil);
  }
}

- (void)setMonetAppInfo:(double)appId
                 authId:(double)authId
      srAlgorithmType:(double)srAlgorithmType
               resolve:(RCTPromiseResolveBlock)resolve
                reject:(RCTPromiseRejectBlock)reject {
  @try {
    // Super resolution/Monet disabled for now to avoid TSR dependency.
    NSLog(@"[react-native-tuiplayer] setMonetAppInfo ignored: super resolution is disabled in this build.");
    resolve(nil);
  } @catch (NSException *exception) {
    reject(@"E_MONET", exception.reason, nil);
  }
}

- (void)createShortController:(RCTPromiseResolveBlock)resolve
                       reject:(RCTPromiseRejectBlock)reject {
  NSNumber *controllerId = [self.shortEngine createShortController];
  resolve(controllerId);
}

- (void)shortControllerSetModels:(double)controllerId
                         sources:(NSArray<NSDictionary *> *)sources
                         resolve:(RCTPromiseResolveBlock)resolve
                          reject:(RCTPromiseRejectBlock)reject {
  RNShortController *controller = [self controllerForId:@(controllerId) reject:reject];
  if (!controller) {
    return;
  }
  NSArray *models = [RNTransformer videoModelsFromArray:sources];
  [self runOnMain:^{
    NSNumber *result = [controller setModels:models];
    resolve(result);
  }];
}

- (void)shortControllerAppendModels:(double)controllerId
                            sources:(NSArray<NSDictionary *> *)sources
                            resolve:(RCTPromiseResolveBlock)resolve
                             reject:(RCTPromiseRejectBlock)reject {
  RNShortController *controller = [self controllerForId:@(controllerId) reject:reject];
  if (!controller) {
    return;
  }
  NSArray *models = [RNTransformer videoModelsFromArray:sources];
  [self runOnMain:^{
    NSNumber *result = [controller appendModels:models];
    resolve(result);
  }];
}

- (void)shortControllerBindVideoView:(double)controllerId
                             viewTag:(double)viewTag
                               index:(double)index
                             resolve:(RCTPromiseResolveBlock)resolve
                              reject:(RCTPromiseRejectBlock)reject {
  RNShortController *controller = [self controllerForId:@(controllerId) reject:reject];
  if (!controller) {
    return;
  }
  [self runOnMain:^{
    [controller bindVideoView:@(viewTag) index:(NSInteger)index];
    resolve(nil);
  }];
}

- (void)shortControllerPreBindVideo:(double)controllerId
                             viewTag:(double)viewTag
                               index:(double)index
                             resolve:(RCTPromiseResolveBlock)resolve
                              reject:(RCTPromiseRejectBlock)reject {
  RNShortController *controller = [self controllerForId:@(controllerId) reject:reject];
  if (!controller) {
    return;
  }
  [self runOnMain:^{
    [controller preBindVideo:@(viewTag) index:(NSInteger)index];
    resolve(nil);
  }];
}

- (void)shortControllerSetVodStrategy:(double)controllerId
                             strategy:(NSDictionary *)strategy
                              resolve:(RCTPromiseResolveBlock)resolve
                               reject:(RCTPromiseRejectBlock)reject {
  RNShortController *controller = [self controllerForId:@(controllerId) reject:reject];
  if (!controller) {
    return;
  }
  TUIPlayerVodStrategyModel *model = [RNTransformer strategyFromDictionary:strategy ?: @{}];
  [self runOnMain:^{
    [controller setVodStrategy:model];
    resolve(nil);
  }];
}

- (void)shortControllerStartCurrent:(double)controllerId
                            resolve:(RCTPromiseResolveBlock)resolve
                             reject:(RCTPromiseRejectBlock)reject {
  RNShortController *controller = [self controllerForId:@(controllerId) reject:reject];
  if (!controller) {
    return;
  }
  [self runOnMain:^{
    resolve([controller startCurrent]);
  }];
}

- (void)shortControllerSetVideoLoop:(double)controllerId
                              isLoop:(BOOL)isLoop
                             resolve:(RCTPromiseResolveBlock)resolve
                              reject:(RCTPromiseRejectBlock)reject {
  RNShortController *controller = [self controllerForId:@(controllerId) reject:reject];
  if (!controller) {
    return;
  }
  [self runOnMain:^{
    [controller setVideoLoop:isLoop];
    resolve(nil);
  }];
}

- (void)shortControllerSwitchResolution:(double)controllerId
                              resolution:(double)resolution
                              switchType:(double)switchType
                                 resolve:(RCTPromiseResolveBlock)resolve
                                  reject:(RCTPromiseRejectBlock)reject {
  RNShortController *controller = [self controllerForId:@(controllerId) reject:reject];
  if (!controller) {
    return;
  }
  [self runOnMain:^{
    [controller switchResolution:(long)resolution switchType:(NSInteger)switchType];
    resolve(nil);
  }];
}

- (void)shortControllerRelease:(double)controllerId
                        resolve:(RCTPromiseResolveBlock)resolve
                         reject:(RCTPromiseRejectBlock)reject {
  RNShortController *controller = [self controllerForId:@(controllerId) reject:reject];
  if (!controller) {
    return;
  }
  [self runOnMain:^{
    [controller releaseController];
    resolve(nil);
  }];
}

- (void)vodPlayerStartPlay:(double)viewTag
                    source:(NSDictionary *)source
                   resolve:(RCTPromiseResolveBlock)resolve
                    reject:(RCTPromiseRejectBlock)reject {
  RNShortVideoView *view = [self videoViewForTag:@(viewTag) reject:reject];
  if (!view) {
    return;
  }
  TUIPlayerVideoModel *model = [RNTransformer videoModelFromDictionary:source ?: @{}];
  [self runOnMain:^{
    [view.vodController startPlayWithModel:model];
    resolve(nil);
  }];
}

- (void)vodPlayerPause:(double)viewTag
               resolve:(RCTPromiseResolveBlock)resolve
                reject:(RCTPromiseRejectBlock)reject {
  RNShortVideoView *view = [self videoViewForTag:@(viewTag) reject:reject];
  if (!view) {
    return;
  }
  [self runOnMain:^{
    [view.vodController pause];
    resolve(nil);
  }];
}

- (void)vodPlayerResume:(double)viewTag
                resolve:(RCTPromiseResolveBlock)resolve
                 reject:(RCTPromiseRejectBlock)reject {
  RNShortVideoView *view = [self videoViewForTag:@(viewTag) reject:reject];
  if (!view) {
    return;
  }
  [self runOnMain:^{
    [view.vodController resume];
    resolve(nil);
  }];
}

- (void)vodPlayerSetRate:(double)viewTag
                    rate:(double)rate
                 resolve:(RCTPromiseResolveBlock)resolve
                  reject:(RCTPromiseRejectBlock)reject {
  RNShortVideoView *view = [self videoViewForTag:@(viewTag) reject:reject];
  if (!view) {
    return;
  }
  [self runOnMain:^{
    [view.vodController setRate:rate];
    resolve(nil);
  }];
}

- (void)vodPlayerSetMute:(double)viewTag
                   mute:(BOOL)mute
                resolve:(RCTPromiseResolveBlock)resolve
                 reject:(RCTPromiseRejectBlock)reject {
  RNShortVideoView *view = [self videoViewForTag:@(viewTag) reject:reject];
  if (!view) {
    return;
  }
  [self runOnMain:^{
    [view.vodController setMute:mute];
    resolve(nil);
  }];
}

- (void)vodPlayerSeekTo:(double)viewTag
                   time:(double)time
                resolve:(RCTPromiseResolveBlock)resolve
                 reject:(RCTPromiseRejectBlock)reject {
  RNShortVideoView *view = [self videoViewForTag:@(viewTag) reject:reject];
  if (!view) {
    return;
  }
  [self runOnMain:^{
    [view.vodController seekToTime:time];
    resolve(nil);
  }];
}

- (void)vodPlayerSwitchResolution:(double)viewTag
                       resolution:(double)resolution
                       switchType:(double)switchType
                          resolve:(RCTPromiseResolveBlock)resolve
                           reject:(RCTPromiseRejectBlock)reject {
  RNShortVideoView *view = [self videoViewForTag:@(viewTag) reject:reject];
  if (!view) {
    return;
  }
  [self runOnMain:^{
    [view.vodController switchResolution:(long)resolution switchType:(NSInteger)switchType];
    resolve(nil);
  }];
}

- (void)vodPlayerGetSupportResolution:(double)viewTag
                              resolve:(RCTPromiseResolveBlock)resolve
                               reject:(RCTPromiseRejectBlock)reject {
  RNShortVideoView *view = [self videoViewForTag:@(viewTag) reject:reject];
  if (!view) {
    return;
  }
  [self runOnMain:^{
    NSArray<TUIPlayerBitrateItem *> *bitrates = [view.vodController getSupportResolution];
    NSMutableArray *result = [NSMutableArray arrayWithCapacity:bitrates.count];
    for (TUIPlayerBitrateItem *item in bitrates) {
      [result addObject:@{
        @"index" : @(item.index),
        @"width" : @(item.width),
        @"height" : @(item.height),
        @"bitrate" : @(item.bitrate)
      }];
    }
    resolve(result);
  }];
}

- (void)vodPlayerSetMirror:(double)viewTag
                    mirror:(BOOL)mirror
                   resolve:(RCTPromiseResolveBlock)resolve
                    reject:(RCTPromiseRejectBlock)reject {
  RNShortVideoView *view = [self videoViewForTag:@(viewTag) reject:reject];
  if (!view) {
    return;
  }
  [self runOnMain:^{
    [view.vodController setMirror:mirror];
    resolve(nil);
  }];
}

- (void)vodPlayerSetRenderMode:(double)viewTag
                    renderMode:(double)renderMode
                       resolve:(RCTPromiseResolveBlock)resolve
                        reject:(RCTPromiseRejectBlock)reject {
  RNShortVideoView *view = [self videoViewForTag:@(viewTag) reject:reject];
  if (!view) {
    return;
  }
  [self runOnMain:^{
    if ([view.vodController respondsToSelector:@selector(setRenderMode:)]) {
      [view.vodController setRenderMode:(NSInteger)renderMode];
      resolve(nil);
      return;
    }
    reject(@"E_RENDER_MODE_UNSUPPORTED",
           @"Render mode control is not supported on this platform",
           nil);
  }];
}

- (void)vodPlayerSetStringOption:(double)viewTag
                           value:(NSString *)value
                              key:(id)key
                         resolve:(RCTPromiseResolveBlock)resolve
                          reject:(RCTPromiseRejectBlock)reject {
  RNShortVideoView *view = [self videoViewForTag:@(viewTag) reject:reject];
  if (!view) {
    return;
  }
  [self runOnMain:^{
    [view.vodController setStringOptionWithKey:key value:value ?: @""];
    resolve(nil);
  }];
}

- (void)vodPlayerSelectSubtitle:(double)viewTag
                     trackIndex:(double)trackIndex
                        resolve:(RCTPromiseResolveBlock)resolve
                         reject:(RCTPromiseRejectBlock)reject {
  RNShortVideoView *view = [self videoViewForTag:@(viewTag) reject:reject];
  if (!view) {
    return;
  }
  [self runOnMain:^{
    if ([view.vodController respondsToSelector:@selector(selectSubtitleTrack:)]) {
      [view.vodController selectSubtitleTrack:(NSInteger)trackIndex];
      resolve(nil);
      return;
    }
    reject(@"E_SUBTITLE_UNSUPPORTED", @"Subtitle selection is not supported on this platform", nil);
  }];
}

- (void)vodPlayerGetDuration:(double)viewTag
                     resolve:(RCTPromiseResolveBlock)resolve
                      reject:(RCTPromiseRejectBlock)reject {
  RNShortVideoView *view = [self videoViewForTag:@(viewTag) reject:reject];
  if (!view) {
    return;
  }
  [self runOnMain:^{
    resolve([view.vodController duration]);
  }];
}

- (void)vodPlayerGetCurrentPlayTime:(double)viewTag
                            resolve:(RCTPromiseResolveBlock)resolve
                             reject:(RCTPromiseRejectBlock)reject {
  RNShortVideoView *view = [self videoViewForTag:@(viewTag) reject:reject];
  if (!view) {
    return;
  }
  [self runOnMain:^{
    resolve([view.vodController currentPlayTime]);
  }];
}

- (void)vodPlayerIsPlaying:(double)viewTag
                   resolve:(RCTPromiseResolveBlock)resolve
                    reject:(RCTPromiseRejectBlock)reject {
  RNShortVideoView *view = [self videoViewForTag:@(viewTag) reject:reject];
  if (!view) {
    return;
  }
  [self runOnMain:^{
    resolve([view.vodController isPlaying]);
  }];
}

- (void)vodPlayerRelease:(double)viewTag
                 resolve:(RCTPromiseResolveBlock)resolve
                  reject:(RCTPromiseRejectBlock)reject {
  RNShortVideoView *view = [self videoViewForTag:@(viewTag) reject:reject];
  if (!view) {
    return;
  }
  [self runOnMain:^{
    [view.vodController releasePlayer];
    resolve(nil);
  }];
}

- (void)addListener:(NSString *)event {
  // Required for RN event emitter
}

- (void)removeListeners:(double)count {
}

- (RNShortController *)controllerForId:(NSNumber *)controllerId
                                  reject:(RCTPromiseRejectBlock)reject {
  RNShortController *controller = [self.shortEngine controllerForId:controllerId];
  if (!controller && reject) {
    reject(@"E_NO_CONTROLLER",
           [NSString stringWithFormat:@"Controller %@ not found", controllerId],
           nil);
  }
  return controller;
}

- (RNShortVideoView *)videoViewForTag:(NSNumber *)tag
                                 reject:(RCTPromiseRejectBlock)reject {
  RNShortVideoView *view = [RNViewRegistry viewForTag:tag];
  if (!view && reject) {
    reject(@"E_NO_VIEW", [NSString stringWithFormat:@"View %@ not found", tag], nil);
  }
  return view;
}

- (std::shared_ptr<facebook::react::TurboModule>)getTurboModule:
    (const facebook::react::ObjCTurboModule::InitParams &)params {
  return std::make_shared<facebook::react::NativeTxplayerSpecJSI>(params);
}

+ (NSString *)moduleName {
  return @"Txplayer";
}

@end
